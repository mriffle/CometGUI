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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.workflow.engine.ReuseCheck;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.testing.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Preparing a run, without a Comet process: what {@code run.json} records, the parameter file
 * written once, the plan, the settings and the actions -- and a blocked search leaving nothing.
 */
class CometWorkflowTest {

    @Test
    @DisplayName("the plan: everything up to finalise-provenance, the index step only with a mode")
    void plans() {
        assertEquals(
                "Plan[validate-configuration, resolve-comet, serialise-comet-params, hash-inputs,"
                        + " run-comet, validate-comet-outputs, merge-pin, finalise-provenance]",
                CometWorkflow.planFor(IndexMode.NONE).toString());
        assertEquals(
                "Plan[validate-configuration, resolve-comet, serialise-comet-params, hash-inputs,"
                        + " build-comet-index, run-comet, validate-comet-outputs, merge-pin,"
                        + " finalise-provenance]",
                CometWorkflow.planFor(IndexMode.PEPTIDE).toString());
        assertEquals(
                CometWorkflow.planFor(IndexMode.PEPTIDE).steps(),
                CometWorkflow.planFor(IndexMode.FRAGMENT_ION).steps());
    }

    @Test
    @DisplayName(
            "prepare records the run: identity, parameter file written once, settings, actions")
    void prepareRecordsTheRun(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            SearchRequest request =
                    fake.request(DecoySource.COMET_INTERNAL_CONCATENATED, IndexMode.NONE);
            PreparedRun prepared = project.prepare(request);

            byte[] canonical = new CanonicalParamsWriter(RealComet.BUILD).bytes(request.model());
            Path params = prepared.layout().cometParamsFile();
            assertTrue(java.util.Arrays.equals(canonical, Files.readAllBytes(params)));
            assertEquals(params, prepared.parameters().path());
            assertEquals(RealComet.sha256(params), prepared.parameters().hashes().sha256());
            assertEquals(canonical.length, prepared.parameters().size());

            RunIdentity identity = project.store().read(prepared.layout()).identity();
            assertEquals(prepared.identity(), identity);
            assertEquals("2026.03.0", identity.cometRelease());
            assertEquals(IndexMode.NONE, identity.indexMode());
            assertEquals(DatabaseDelivery.PARAMETER_FILE, identity.databaseDelivery());
            assertEquals("parameters/comet.params", identity.parameters().path());
            assertEquals(prepared.parameters().hashes(), identity.parameters().hashes());
            List<SpectrumInput> spectra = identity.spectra();
            assertEquals(2, spectra.size());
            assertEquals(1, spectra.get(0).position());
            assertEquals("k562_3", spectra.get(0).base());
            assertEquals(fake.spectra().get(1), spectra.get(1).file().path());
            assertEquals(
                    RealComet.sha256(fake.spectra().get(1)),
                    spectra.get(1).file().hashes().sha256());
            assertEquals(Files.size(fake.spectra().get(1)), spectra.get(1).file().size());
            assertEquals(fake.fasta(), identity.fasta().path());
            assertEquals(RealComet.sha256(fake.fasta()), identity.fasta().hashes().sha256());

            assertEquals(
                    Map.of(
                            "comet.release", "2026.03.0",
                            "comet.index-mode", "none",
                            "comet.database-delivery", "parameter-file",
                            "comet.params-sha256", RealComet.sha256(params)),
                    prepared.settings());
            assertEquals(CometWorkflow.planFor(IndexMode.NONE).steps(), prepared.plan().steps());
            assertEquals(Set.copyOf(prepared.plan().steps()), prepared.actions().keySet());
            assertEquals(Optional.empty(), prepared.indexFile());
            assertFalse(prepared.buildsIndex());
            assertEquals(
                    Optional.of(true),
                    Optional.of(
                            prepared.actions().get(EngineStep.VALIDATE_CONFIGURATION).validates()));
            assertFalse(prepared.actions().get(EngineStep.RUN_COMET).validates());

            RunRequest attempt = prepared.request();
            assertEquals(prepared.inputs().values(), attempt.inputs().values());
            assertEquals(Set.of(), attempt.forced());
            assertEquals(prepared.settings(), attempt.settings());
            assertEquals(prepared.layout(), attempt.layout());
            assertEquals(
                    project.workflow().inputsOf(request).values(),
                    prepared.inputs().values(),
                    "an unchanged configuration has the recorded inputs");

            ReuseCheck fresh = project.workflow().preview(project.engine(), prepared, request);
            assertTrue(fresh.accepted());
            assertEquals(
                    Set.of(
                            EngineStep.SERIALISE_COMET_PARAMS,
                            EngineStep.RUN_COMET,
                            EngineStep.VALIDATE_COMET_OUTPUTS,
                            EngineStep.MERGE_PIN,
                            EngineStep.FINALISE_PROVENANCE),
                    fresh.preview().reExecuted(),
                    "nothing is recorded yet, so everything runs");
            assertEquals(List.of(), project.runner().launches());
        }
    }

    @Test
    @DisplayName("prepare with an index mode: the cache entry, the key setting, -D delivery")
    void prepareWithAnIndexMode(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared =
                    project.prepare(
                            fake.request(
                                    DecoySource.COMET_INTERNAL_CONCATENATED, IndexMode.PEPTIDE));
            assertTrue(prepared.buildsIndex());
            Path index = prepared.indexFile().orElseThrow();
            String key = String.valueOf(RealComet.parentOf(index).getFileName());
            assertEquals(
                    fake.project().resolve("index-cache").resolve(key).resolve("db.fasta.idx"),
                    index);
            assertEquals(key, prepared.settings().get("comet.index-cache-key"));
            assertEquals("peptide", prepared.settings().get("comet.index-mode"));
            assertEquals("command-line", prepared.settings().get("comet.database-delivery"));
            assertEquals(DatabaseDelivery.COMMAND_LINE, prepared.identity().databaseDelivery());
            assertTrue(prepared.actions().containsKey(EngineStep.BUILD_COMET_INDEX));
            assertFalse(Files.exists(RealComet.parentOf(index)), "nothing is built before the run");
        }
    }

    @Test
    @DisplayName("a blocked search creates nothing; the exception carries the report")
    void blocked(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            RunBlockedException blocked =
                    assertThrows(
                            RunBlockedException.class,
                            () ->
                                    project.prepare(
                                            fake.request(
                                                    DecoySource.FASTA_CONTAINS_DECOYS,
                                                    IndexMode.NONE)));
            assertTrue(blocked.report().blocked());
            assertEquals(blocked.report().message(), blocked.getMessage());
            assertEquals(List.of(), project.runDirectories());
        }
    }

    @Test
    @DisplayName("a report blocks on a problem or a validator error, and says which")
    void reports() {
        ValidationReport clean = new ValidationReport(List.of());
        PreRunReport nothing =
                new PreRunReport(List.of(), clean, org.cometgui.domain.params.PreRunFacts.none());
        assertFalse(nothing.blocked());
        assertEquals("nothing blocks the run", nothing.message());
        IllegalArgumentException notBlocking =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            throw new RunBlockedException(nothing);
                        });
        assertEquals("a report that blocks nothing does not block a run", notBlocking.getMessage());
        PreRunReport problems =
                new PreRunReport(
                        List.of("one", "two"),
                        clean,
                        org.cometgui.domain.params.PreRunFacts.none());
        assertTrue(problems.blocked());
        assertEquals("the run cannot start:\n- one\n- two", problems.message());
        assertEquals(List.of("one", "two"), problems.problems());
    }

    @Test
    @DisplayName("a selection and a request validate their parts")
    void values() {
        Path exe = TestPaths.absolute("bin/comet");
        ToolVersion release = ToolVersion.parse("2026.03.0");
        CometSelection upper =
                new CometSelection(exe, release, "AB".repeat(32), true, Optional.empty());
        assertEquals("ab".repeat(32), upper.sha256());
        assertEquals(
                exe,
                new CometSelection(
                                TestPaths.absolute("bin/../bin/comet"),
                                release,
                                "a".repeat(64),
                                true,
                                Optional.empty())
                        .executable());
        assertEquals(
                "the Comet executable must be an absolute path, not \"comet\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CometSelection(
                                                Path.of("comet"),
                                                release,
                                                "a".repeat(64),
                                                true,
                                                Optional.empty()))
                        .getMessage());
        assertEquals(
                "a SHA-256 is 64 hexadecimal characters, not \"xyz\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CometSelection(
                                                exe, release, "xyz", true, Optional.empty()))
                        .getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CometSelection(exe, release, "g".repeat(64), true, Optional.empty()));
        java.util.List<Path> spectra =
                new java.util.ArrayList<>(List.of(TestPaths.absolute("a.mzML")));
        SearchRequest request =
                new SearchRequest(IndexCacheKeyTest.model(), spectra, upper, IndexMode.NONE);
        spectra.clear();
        assertEquals(List.of(TestPaths.absolute("a.mzML")), request.spectra());
        java.util.List<Path> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        assertEquals(
                "spectra contains null",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new SearchRequest(
                                                IndexCacheKeyTest.model(),
                                                withNull,
                                                upper,
                                                IndexMode.NONE))
                        .getMessage());
    }

    @Test
    @DisplayName("the recorded parameters of a prepared run are the canonical bytes, in UTF-8")
    void canonicalBytes(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared =
                    project.prepare(
                            fake.request(DecoySource.COMET_INTERNAL_SEPARATE, IndexMode.NONE));
            String text =
                    Files.readString(prepared.layout().cometParamsFile(), StandardCharsets.UTF_8);
            assertTrue(text.contains("\ndecoy_search = 2"), text);
            assertTrue(text.contains("\ndatabase_name = " + fake.fasta()), text);
            assertTrue(text.contains("\noutput_percolatorfile = 1"), text);
            assertEquals(
                    Plan.covering(Set.of(EngineStep.FINALISE_PROVENANCE)).steps(),
                    prepared.plan().steps());
        }
    }

    @Test
    @DisplayName("inputsOf refuses a spectrum file that does not exist")
    void inputsOfMissing(@TempDir Path directory) throws IOException {
        FakeSearch fake = FakeSearch.create(directory);
        CometWorkflow workflow = new CometWorkflow(FakeSearch.hashes(), RealComet.BUILD);
        SearchRequest missing =
                new SearchRequest(
                        fake.model(DecoySource.COMET_INTERNAL_CONCATENATED),
                        List.of(fake.root().resolve("gone.mzML")),
                        fake.selection(),
                        IndexMode.NONE);
        assertThrows(java.nio.file.NoSuchFileException.class, () -> workflow.inputsOf(missing));
    }
}
