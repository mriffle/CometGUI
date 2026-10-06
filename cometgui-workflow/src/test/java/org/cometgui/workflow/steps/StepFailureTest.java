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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
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
import org.junit.jupiter.api.io.TempDir;

/**
 * The preparation steps' own checks inside a run, without a Comet process: each run is prepared
 * normally, and something changes after {@code validate-configuration} passed -- the moment the
 * run's later checks exist for. Each step fails with its message, typed out here, and Comet never
 * starts (the stand-in executable is not a program, and no launch is counted).
 */
class StepFailureTest {

    /** Something that changes a file. */
    @FunctionalInterface
    interface Tamper {
        void apply() throws IOException;
    }

    /** The real validate step, then a change. */
    record ValidateThenTamper(StepAction validate, Tamper tamper) implements StepAction {

        @Override
        public StepDeclaration declaration() {
            return validate.declaration();
        }

        @Override
        public boolean validates() {
            return true;
        }

        @Override
        public void validate(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            validate.validate(context);
            tamper.apply();
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            validate.execute(context);
        }
    }

    private static RunResult runTampered(RealProject project, PreparedRun prepared, Tamper tamper)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Map<EngineStep, StepAction> actions = new EnumMap<>(prepared.actions());
        actions.put(
                EngineStep.VALIDATE_CONFIGURATION,
                new ValidateThenTamper(actions.get(EngineStep.VALIDATE_CONFIGURATION), tamper));
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
        assertEquals(AttemptOutcome.FAILED, result.outcome());
        assertEquals(StepState.SUCCEEDED, result.states().get(EngineStep.VALIDATE_CONFIGURATION));
        assertEquals(StepState.NOT_STARTED, result.states().get(EngineStep.RUN_COMET));
        assertEquals(List.of(), project.runner().launches(), "Comet never started");
        return result;
    }

    private static PreparedRun prepare(RealProject project, FakeSearch fake)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        return project.prepare(
                fake.request(DecoySource.COMET_INTERNAL_CONCATENATED, IndexMode.NONE));
    }

    @Test
    @DisplayName(
            "resolve-comet: an executable replaced after it was selected is refused, naming both"
                    + " digests")
    void resolveRefusesAReplacedExecutable(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        String selected = fake.executableSha256();
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = prepare(project, fake);
            RunResult result =
                    runTampered(
                            project,
                            prepared,
                            () ->
                                    Files.writeString(
                                            fake.executable(),
                                            "replaced\n",
                                            StandardOpenOption.APPEND));
            assertEquals(StepState.FAILED, result.states().get(EngineStep.RESOLVE_COMET));
            assertEquals(
                    "the Comet executable "
                            + fake.executable()
                            + " has SHA-256 "
                            + RealComet.sha256(fake.executable())
                            + ", but Comet 2026.03.0 was selected at "
                            + selected
                            + "; it was replaced after it was selected, and is not run",
                    result.failures().get(EngineStep.RESOLVE_COMET));
        }
    }

    @Test
    @DisplayName("resolve-comet: an executable that can no longer be executed is refused")
    void resolveRefusesANonExecutable(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            RunResult result =
                    runTampered(
                            project,
                            prepare(project, fake),
                            () ->
                                    Files.setPosixFilePermissions(
                                            fake.executable(),
                                            PosixFilePermissions.fromString("rw-------")));
            assertEquals(
                    "the selected Comet executable "
                            + fake.executable()
                            + " does not exist or cannot be executed",
                    result.failures().get(EngineStep.RESOLVE_COMET));
        }
    }

    @Test
    @DisplayName("serialise-comet-params: an archived file changed after it was written is refused")
    void serialiseRefusesAChangedParameterFile(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = prepare(project, fake);
            Path params = prepared.layout().cometParamsFile();
            long size = Files.size(params);
            String recorded = prepared.parameters().hashes().sha256();
            RunResult result =
                    runTampered(
                            project,
                            prepared,
                            () -> {
                                byte[] bytes = Files.readAllBytes(params);
                                bytes[bytes.length - 2] ^= 1;
                                Files.write(params, bytes);
                            });
            assertEquals(StepState.FAILED, result.states().get(EngineStep.SERIALISE_COMET_PARAMS));
            assertEquals(
                    "the run's archived parameter file "
                            + params
                            + " has SHA-256 "
                            + RealComet.sha256(params)
                            + " ("
                            + size
                            + " bytes), but the run recorded "
                            + recorded
                            + " ("
                            + size
                            + " bytes) when it was written; a run's parameters are written once,"
                            + " so this run cannot continue",
                    result.failures().get(EngineStep.SERIALISE_COMET_PARAMS));
        }
    }

    @Test
    @DisplayName("serialise-comet-params: a truncated archived file is refused, naming both sizes")
    void serialiseRefusesATruncatedParameterFile(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = prepare(project, fake);
            Path params = prepared.layout().cometParamsFile();
            long size = Files.size(params);
            RunResult result =
                    runTampered(
                            project,
                            prepared,
                            () -> {
                                byte[] bytes = Files.readAllBytes(params);
                                Files.write(
                                        params, java.util.Arrays.copyOf(bytes, bytes.length / 2));
                            });
            String message = result.failures().get(EngineStep.SERIALISE_COMET_PARAMS);
            assertEquals(
                    "the run's archived parameter file "
                            + params
                            + " has SHA-256 "
                            + RealComet.sha256(params)
                            + " ("
                            + Files.size(params)
                            + " bytes), but the run recorded "
                            + prepared.parameters().hashes().sha256()
                            + " ("
                            + size
                            + " bytes) when it was written; a run's parameters are written once,"
                            + " so this run cannot continue",
                    message);
        }
    }

    @Test
    @DisplayName("serialise-comet-params: a deleted archived file is refused")
    void serialiseRefusesADeletedParameterFile(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = prepare(project, fake);
            Path params = prepared.layout().cometParamsFile();
            RunResult result = runTampered(project, prepared, () -> Files.delete(params));
            assertEquals(
                    "the run's archived parameter file " + params + " no longer exists",
                    result.failures().get(EngineStep.SERIALISE_COMET_PARAMS));
        }
    }

    @Test
    @DisplayName("hash-inputs: every changed input is named with both digests; a deleted one too")
    void hashInputsNamesEveryChangedInput(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = prepare(project, fake);
            Path first = fake.spectra().get(0);
            Path second = fake.spectra().get(1);
            String firstWas = RealComet.sha256(first);
            String secondWas = RealComet.sha256(second);
            RunResult result =
                    runTampered(
                            project,
                            prepared,
                            () -> {
                                Files.writeString(first, "x", StandardOpenOption.APPEND);
                                Files.writeString(second, "y", StandardOpenOption.APPEND);
                            });
            assertEquals(StepState.FAILED, result.states().get(EngineStep.HASH_INPUTS));
            assertEquals(
                    "the spectrum file 1 "
                            + first
                            + " has changed since the run was recorded: recorded SHA-256 "
                            + firstWas
                            + ", now "
                            + RealComet.sha256(first)
                            + "; the spectrum file 2 "
                            + second
                            + " has changed since the run was recorded: recorded SHA-256 "
                            + secondWas
                            + ", now "
                            + RealComet.sha256(second)
                            + ". A run's inputs are fixed when it is created; start a new run to"
                            + " search the files as they are now",
                    result.failures().get(EngineStep.HASH_INPUTS));
        }
    }

    @Test
    @DisplayName("hash-inputs: a deleted database is named")
    void hashInputsNamesADeletedDatabase(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = prepare(project, fake);
            RunResult result = runTampered(project, prepared, () -> Files.delete(fake.fasta()));
            assertEquals(
                    "the database "
                            + fake.fasta()
                            + " no longer exists. A run's inputs are fixed when it is created;"
                            + " start a new run to search the files as they are now",
                    result.failures().get(EngineStep.HASH_INPUTS));
            assertEquals(
                    StepState.SUCCEEDED, result.states().get(EngineStep.SERIALISE_COMET_PARAMS));
            assertEquals(StepState.SUCCEEDED, result.states().get(EngineStep.RESOLVE_COMET));
        }
    }

    @Test
    @DisplayName("a FASTA that gained a target passes validation and fails hash-inputs")
    void hashInputsRefusesAFastaThatGainedATarget(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = prepare(project, fake);
            RunResult result =
                    runTampered(
                            project,
                            prepared,
                            () ->
                                    Files.writeString(
                                            fake.fasta(),
                                            ">sp|P00003|THREE\nPEPTIDER\n",
                                            StandardCharsets.US_ASCII,
                                            StandardOpenOption.APPEND));
            assertEquals(StepState.FAILED, result.states().get(EngineStep.HASH_INPUTS));
        }
    }
}
