/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 3, as published
 * by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * The full licence is the LICENSE file at the root of this repository. If it
 * is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepFailedException;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 08 exit gate item 3, second half: a feature-column mismatch FAILS THE MERGE STAGE -- the
 * engine records {@code merge-pin} as {@code FAILED}, with a message naming both files -- in a real
 * run. Both PINs are Comet 2026.03.0's own; after the real search the second file's header has two
 * feature columns swapped, which the per-file validation accepts (each header is well formed on its
 * own) and only the merge's cross-file check can refuse.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealMergeMismatchTest {

    /** The real search, then {@code deltLCn} and {@code deltCn} swapped in the second PIN. */
    private record SwapAfterSearch(StepAction search, Path pin) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return search.declaration();
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            search.execute(context);
            List<String> lines = Files.readAllLines(pin, StandardCharsets.ISO_8859_1);
            String header = lines.get(0);
            String swapped = header.replace("\tdeltLCn\tdeltCn\t", "\tdeltCn\tdeltLCn\t");
            if (swapped.equals(header)) {
                throw new AssertionError("the real PIN header has no deltLCn, deltCn: " + header);
            }
            lines.set(0, swapped);
            Files.write(
                    pin, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    @Test
    @DisplayName("gate 3: a feature-column mismatch fails the merge-pin stage, naming both files")
    void gate3FeatureColumnMismatchFailsTheStage(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = scratch.toRealPath();
        Path comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        try (RealProject project = RealProject.create(root.resolve("project"))) {
            PreparedRun prepared =
                    project.prepare(
                            new SearchRequest(
                                    RealComet.model(
                                            RealComet.NEWER,
                                            fasta,
                                            DecoySource.COMET_INTERNAL_CONCATENATED,
                                            4),
                                    spectra,
                                    RealComet.selection(RealComet.NEWER, comet),
                                    IndexMode.NONE));
            Path first = prepared.layout().pinFile("k562_3");
            Path second = prepared.layout().pinFile("k562_4");
            Map<EngineStep, StepAction> actions = new EnumMap<>(prepared.actions());
            actions.put(
                    EngineStep.RUN_COMET,
                    new SwapAfterSearch(actions.get(EngineStep.RUN_COMET), second));
            RunRequest request =
                    new RunRequest(
                            project.store(),
                            project.lock(),
                            prepared.layout(),
                            prepared.plan(),
                            prepared.inputs(),
                            Set.of(),
                            actions,
                            RealProject.application(),
                            prepared.settings());

            RunResult result =
                    project.engine()
                            .start(request, StepStateListener.NONE)
                            .await(RealProject.RUN_BOUND)
                            .orElseThrow();

            String expected =
                    "cannot merge the PIN files "
                            + first
                            + " and "
                            + second
                            + ": their feature columns differ, first at feature column 4"
                            + " (\"deltLCn\" in the first, \"deltCn\" in the second; 23 and 23"
                            + " feature columns). Percolator needs every file to have the same"
                            + " features in the same order";
            assertEquals(AttemptOutcome.FAILED, result.outcome());
            assertEquals(StepState.SUCCEEDED, result.states().get(EngineStep.RUN_COMET));
            assertEquals(
                    StepState.SUCCEEDED, result.states().get(EngineStep.VALIDATE_COMET_OUTPUTS));
            assertEquals(StepState.FAILED, result.states().get(EngineStep.MERGE_PIN));
            assertEquals(
                    StepState.NOT_STARTED, result.states().get(EngineStep.FINALISE_PROVENANCE));
            assertEquals(expected, result.failures().get(EngineStep.MERGE_PIN));
            Map<String, String> finished =
                    RunEvidence.finished(prepared.layout(), EngineStep.MERGE_PIN);
            RunEvidence.assertDetail(finished, "state", "failed");
            RunEvidence.assertDetail(finished, "message", expected);
            assertFalse(Files.exists(prepared.layout().mergedPinFile()), "no merged file");
            assertEquals(2, project.runner().launchesOf(comet).size());
        }
    }
}
