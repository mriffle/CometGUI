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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code R-CMT-06}, the row counts: a merged PIN that does not hold every row of the per-file PINs
 * FAILS THE MERGE STAGE in a real run, with a message stating how many rows were read and how many
 * the written file holds.
 *
 * <p>The search is Comet 2026.03.0's own over the two K562 files. The row is <em>really</em> lost:
 * the merge step writes through a stream that drops one line on its way to the disk, so the file
 * the product wrote and closed is one row short, exactly as a faulty disk or a defect in the copy
 * would leave it. Nothing tells the product; it must find out by counting what it wrote.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealMergeRowLossTest {

    /** The line of the merged file that never reaches the disk; line 0 is the header. */
    private static final int LOST_LINE = 2;

    /** A disk that loses one whole line of what is written to it. */
    private static class OneLineLost extends FilterOutputStream {

        private final int line;

        private int current;

        OneLineLost(OutputStream out, int line) {
            super(out);
            this.line = line;
        }

        @Override
        public void write(int value) throws IOException {
            if (current != line) {
                out.write(value);
            }
            if (value == '\n') {
                current++;
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            for (int index = offset; index < offset + length; index++) {
                write(bytes[index]);
            }
        }
    }

    /** The data rows of a PIN, counted here: its lines less the header. */
    private static long dataRows(Path pin) throws IOException {
        try (Stream<String> lines = Files.lines(pin, StandardCharsets.ISO_8859_1)) {
            return lines.count() - 1;
        }
    }

    @Test
    @DisplayName("a row lost while the merged PIN is written fails merge-pin, stating both counts")
    void aLostRowFailsTheStage(@TempDir Path scratch)
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
            // What reached the disk, read when the merge closed the file it wrote: the proof that
            // a row was lost for real, not merely reported lost.
            List<Long> rowsOnDisk = new ArrayList<>();
            Map<EngineStep, StepAction> actions = new EnumMap<>(prepared.actions());
            SearchSteps.MergePin merge = (SearchSteps.MergePin) actions.get(EngineStep.MERGE_PIN);
            actions.put(
                    EngineStep.MERGE_PIN,
                    merge.writingThrough(
                            file ->
                                    new OneLineLost(Files.newOutputStream(file), LOST_LINE) {
                                        @Override
                                        public void close() throws IOException {
                                            super.close();
                                            rowsOnDisk.add(dataRows(file));
                                        }
                                    }));
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

            long read = dataRows(first) + dataRows(second);
            assertTrue(read > LOST_LINE, "the real search wrote rows to lose one of: " + read);
            assertEquals(List.of(read - 1), rowsOnDisk, "the rows that reached the disk");
            String expected =
                    "the merged PIN file "
                            + prepared.layout().mergedPinFile()
                            + " was not written completely: "
                            + read
                            + " data rows were read from the 2 per-file PINs, but the file that"
                            + " was written holds "
                            + (read - 1)
                            + ". The merged file was discarded, so Percolator is not run on an"
                            + " incomplete PIN";
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
            try (Stream<Path> left = Files.list(prepared.layout().pinInputsDirectory())) {
                assertEquals(List.of(), left.toList(), "nor the file that was written");
            }
            assertEquals(2, project.runner().launchesOf(comet).size());
        }
    }
}
