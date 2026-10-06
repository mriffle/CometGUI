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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.workflow.engine.ReuseCheck;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 08 exit gate item 8 against a real Comet 2026.03.0 run: after the run succeeded, one of its
 * input files changes, and a retry refuses to reuse the search, naming the file and both digests,
 * and offers the plan that runs the producer -- {@code run-comet} -- and everything downstream
 * again. Nothing is launched by the refusal.
 *
 * <p>Taking that offer then fails in {@code hash-inputs}: a run's inputs are fixed when it is
 * created ({@code R-RUN-06}), so the run never searches bytes other than those its {@code run.json}
 * records, and the message says to start a new run.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealChangedInputTest {

    private record Searched(
            RealProject project, PreparedRun run, Path comet, List<Path> spectra, Path fasta) {}

    private static Searched searched(Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = scratch.toRealPath();
        Path comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealComet.spectra(inputs);
        Path fasta = RealComet.subset(inputs.resolve("subset.fasta"));
        RealProject project = RealProject.create(root.resolve("project"));
        PreparedRun run =
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
        RunResult first = project.run(run);
        assertEquals(AttemptOutcome.SUCCEEDED, first.outcome(), () -> first.failures().toString());
        assertEquals(2, project.runner().launchesOf(comet).size());
        return new Searched(project, run, comet, spectra, fasta);
    }

    /** The refusal, its offer, and the offer taken: each with no Comet launched. */
    private static void assertRefusedAndOffered(
            Searched searched, Path changed, String role, String recordedSha256, String what)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        RealProject project = searched.project();
        String now = RealComet.sha256(changed);
        ReuseRefusedException refused =
                assertThrows(
                        ReuseRefusedException.class,
                        () ->
                                project.workflow()
                                        .start(
                                                project.engine(),
                                                searched.run(),
                                                StepStateListener.NONE));
        assertEquals(
                "recorded results cannot be reused because they no longer match the record:\n- "
                        + changed
                        + " (input file, role "
                        + role
                        + ", of step run-comet) has changed since it was recorded: recorded"
                        + " SHA-256 "
                        + recordedSha256
                        + ", now "
                        + now
                        + "\nthese steps must run again: run-comet, validate-comet-outputs,"
                        + " merge-pin, finalise-provenance",
                refused.getMessage());
        ReuseCheck check = refused.check();
        assertEquals(Set.of(EngineStep.RUN_COMET), check.offeredForced());
        assertEquals(
                Set.of(
                        EngineStep.RUN_COMET,
                        EngineStep.VALIDATE_COMET_OUTPUTS,
                        EngineStep.MERGE_PIN,
                        EngineStep.FINALISE_PROVENANCE),
                check.offered().orElseThrow().reExecuted());
        assertEquals(2, project.runner().launchesOf(searched.comet()).size(), "nothing launched");

        RunResult offered =
                project.engine()
                        .start(
                                searched.run().request().withForced(check.offeredForced()),
                                StepStateListener.NONE)
                        .await(RealProject.RUN_BOUND)
                        .orElseThrow();
        assertEquals(AttemptOutcome.FAILED, offered.outcome());
        assertEquals(StepState.FAILED, offered.states().get(EngineStep.HASH_INPUTS));
        assertEquals(StepState.NOT_STARTED, offered.states().get(EngineStep.RUN_COMET));
        assertEquals(
                "the "
                        + what
                        + " "
                        + changed
                        + " has changed since the run was recorded: recorded SHA-256 "
                        + recordedSha256
                        + ", now "
                        + now
                        + ". A run's inputs are fixed when it is created; start a new run to"
                        + " search the files as they are now",
                offered.failures().get(EngineStep.HASH_INPUTS));
        assertEquals(2, project.runner().launchesOf(searched.comet()).size(), "still nothing");
    }

    @Test
    @DisplayName(
            "gate 8: a spectrum file changed after a successful run refuses reuse, naming the file,"
                    + " and the offered plan re-executes run-comet and downstream")
    void gate8ChangedSpectrumFileRefusesReuse(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Searched searched = searched(scratch);
        try (RealProject project = searched.project()) {
            Path k4 = searched.spectra().get(1);
            Files.writeString(k4, "\n", StandardCharsets.US_ASCII, StandardOpenOption.APPEND);
            assertRefusedAndOffered(
                    searched, k4, "spectrum", RealComet.MZML_4_LF, "spectrum file 2");
        }
    }

    @Test
    @DisplayName(
            "gate 8: a FASTA changed after a successful run refuses reuse, naming the file, and the"
                    + " offered plan re-executes run-comet and downstream")
    void gate8ChangedFastaRefusesReuse(@TempDir Path scratch)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Searched searched = searched(scratch);
        try (RealProject project = searched.project()) {
            Files.writeString(
                    searched.fasta(),
                    ">sp|P00001|ADDED_LATER\nPEPTIDEK\n",
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.APPEND);
            assertRefusedAndOffered(
                    searched, searched.fasta(), "fasta", RealComet.SUBSET_SHA256, "database");
        }
    }
}
