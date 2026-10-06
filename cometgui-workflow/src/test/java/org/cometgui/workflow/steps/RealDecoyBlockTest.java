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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 08 exit gate items 4 and 5 against the real Comet 2026.03.0 and the real FASTA: both decoy
 * blocks ({@code R-DEC-02}) stop the run BEFORE COMET STARTS. "Before Comet starts" is proved, not
 * assumed: the real process service sits behind a counting decorator, and after each block it has
 * recorded no launch at all, the project holds no run directory, and so no Comet log exists. Each
 * message is typed out here and names the decoy configuration.
 *
 * <p>The same block inside the engine: a FASTA that gains a decoy after its run was prepared fails
 * the run's {@code validate-configuration} step in {@code VALIDATING}, and nothing launches.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealDecoyBlockTest {

    @TempDir private static Path scratch;

    private static Path comet;

    private static Path inputs;

    private static List<Path> spectra;

    private static Path targets;

    private static Path decoys;

    private static RealProject project;

    @BeforeAll
    static void stage() throws IOException {
        Path root = scratch.toRealPath();
        comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        inputs = Files.createDirectories(root.resolve("inputs"));
        spectra = RealComet.spectra(inputs);
        targets = RealComet.subset(inputs.resolve("targets.fasta"));
        decoys = RealComet.decoySubset(inputs.resolve("target-decoy.fasta"));
        project = RealProject.create(root.resolve("project"));
    }

    @AfterAll
    static void unlock() throws IOException {
        if (project != null) {
            project.close();
        }
    }

    private static SearchRequest request(Path fasta, DecoySource source) {
        return new SearchRequest(
                RealComet.model(RealComet.NEWER, fasta, source, 4),
                spectra,
                RealComet.selection(RealComet.NEWER, comet),
                IndexMode.NONE);
    }

    /** Refused by readiness and by prepare with this message; nothing created, nothing launched. */
    private static void assertBlockedBeforeComet(SearchRequest request, String expected)
            throws IOException {
        PreRunReport readiness = project.workflow().check(project.project(), request);
        assertTrue(readiness.blocked());
        assertEquals(List.of(), readiness.problems());
        assertEquals(expected, readiness.message());
        RunBlockedException blocked =
                assertThrows(RunBlockedException.class, () -> project.prepare(request));
        assertEquals(expected, blocked.getMessage());
        assertEquals(List.of(), project.runDirectories(), "a blocked run leaves no directory");
        assertEquals(List.of(), project.runner().launches(), "a blocked run launches nothing");
    }

    @Test
    @DisplayName(
            "gate 4: a FASTA with no decoys and decoy_search = 0 is blocked before Comet starts,"
                    + " naming the decoy configuration")
    void gate4NoDecoysAnywhereBlocksBeforeCometStarts() throws IOException {
        assertBlockedBeforeComet(
                request(targets, DecoySource.FASTA_CONTAINS_DECOYS),
                "the run cannot start:\n- [decoy.none_anywhere] decoy_search = 0 (no internal"
                        + " decoys) and "
                        + targets
                        + " holds no entry whose accession begins with DECOY_ (0 of 1000"
                        + " records): Percolator would have no negative examples; set decoy_search"
                        + " to 1 or 2 so that Comet makes decoys, or choose a FASTA whose decoys"
                        + " begin with DECOY_");
    }

    @Test
    @DisplayName(
            "gate 5: a FASTA already holding decoys with decoy_search = 1, and with 2, is blocked"
                    + " before Comet starts, naming the decoy configuration")
    void gate5DoubleDecoysBlockBeforeCometStarts() throws IOException {
        String doubled =
                " and "
                        + decoys
                        + " already holds 1000 entries whose accession begins with DECOY_ (1000"
                        + " of 2000 records; the first is DECOY_sp|A0A075B6H9|LV469_HUMAN): Comet"
                        + " would make decoys of those decoys too, so decoys would be counted"
                        + " twice; set decoy_search to 0 to use the FASTA's own decoys, or choose a"
                        + " FASTA of targets only";
        assertBlockedBeforeComet(
                request(decoys, DecoySource.COMET_INTERNAL_CONCATENATED),
                "the run cannot start:\n- [decoy.double_decoys] decoy_search = 1 (Comet's internal"
                        + " decoys, concatenated)"
                        + doubled);
        assertBlockedBeforeComet(
                request(decoys, DecoySource.COMET_INTERNAL_SEPARATE),
                "the run cannot start:\n- [decoy.double_decoys] decoy_search = 2 (Comet's internal"
                        + " decoys, reported separately)"
                        + doubled);
    }

    @Test
    @DisplayName(
            "inside the engine: a FASTA that gains a decoy after the run was prepared fails"
                    + " validate-configuration in VALIDATING, and no Comet starts")
    void theRunsOwnValidateStepBlocksBeforeComet()
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path changing = inputs.resolve("changing.fasta");
        Files.copy(targets, changing);
        try (RealProject own = RealProject.create(scratch.toRealPath().resolve("project-engine"))) {
            assertValidateStepBlocks(own, changing);
        }
    }

    private static void assertValidateStepBlocks(RealProject own, Path changing)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        PreparedRun prepared =
                own.prepare(request(changing, DecoySource.COMET_INTERNAL_CONCATENATED));
        Files.writeString(
                changing,
                ">DECOY_sp|P00000|LATE_DECOY added after the run was prepared\nKEDITPEP\n",
                StandardCharsets.US_ASCII,
                StandardOpenOption.APPEND);
        RealProject.Transitions transitions = new RealProject.Transitions();

        RunResult result = own.run(prepared, transitions);

        assertEquals(AttemptOutcome.FAILED, result.outcome());
        assertEquals(StepState.FAILED, result.states().get(EngineStep.VALIDATE_CONFIGURATION));
        assertEquals(StepState.NOT_STARTED, result.states().get(EngineStep.RUN_COMET));
        List<StepState> validate = new ArrayList<>();
        for (StepTransition transition : transitions.all()) {
            if (transition.step() == EngineStep.VALIDATE_CONFIGURATION) {
                validate.add(transition.to());
            }
        }
        assertEquals(List.of(StepState.VALIDATING, StepState.FAILED), validate);
        assertEquals(
                "the run cannot start:\n- [decoy.double_decoys] decoy_search = 1 (Comet's internal"
                        + " decoys, concatenated) and "
                        + changing
                        + " already holds 1 entry whose accession begins with DECOY_ (1 of 1001"
                        + " records; the first is DECOY_sp|P00000|LATE_DECOY): Comet would make"
                        + " decoys of those decoys too, so decoys would be counted twice; set"
                        + " decoy_search to 0 to use the FASTA's own decoys, or choose a FASTA of"
                        + " targets only",
                result.failures().get(EngineStep.VALIDATE_CONFIGURATION));
        assertEquals(List.of(), own.runner().launches(), "no process was started");
        assertEquals(List.of(), RunEvidence.listing(prepared.layout().logsDirectory()));
        assertEquals(List.of(), RunEvidence.listing(prepared.layout().cometOutputDirectory()));
    }
}
