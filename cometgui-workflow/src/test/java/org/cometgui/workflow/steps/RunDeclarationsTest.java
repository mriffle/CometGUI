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

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.tools.comet.PinDecoyConfiguration;
import org.cometgui.workflow.engine.DeclaredFile;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.ToolIdentity;
import org.cometgui.workflow.state.InputKind;
import org.cometgui.workflow.state.InputValue;
import org.cometgui.workflow.state.StepInputs;
import org.cometgui.workflow.testing.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every Comet step's declaration, the derived paths and the recorded inputs, computed from a run
 * identity alone -- pure, with every expected file typed out.
 */
class RunDeclarationsTest {

    static final Path RUN = TestPaths.absolute("p/runs/20261006T120000Z-run-0001");

    static final Path IN = TestPaths.absolute("in");

    static final FileHashes H1 = new FileHashes("1".repeat(32), "1".repeat(64));

    static final FileHashes H2 = new FileHashes("2".repeat(32), "2".repeat(64));

    static final FileHashes HF = new FileHashes("3".repeat(32), "3".repeat(64));

    static final FileHashes HP = new FileHashes("4".repeat(32), "4".repeat(64));

    static final FileHashes HC = new FileHashes("5".repeat(32), "5".repeat(64));

    static final String KEY = "c".repeat(64);

    static CometRun run(DecoySource decoys, IndexMode mode, String database, boolean build) {
        Path fasta = IN.resolve(database);
        CometParameters model =
                RealComet.model(RealComet.NEWER, fasta, decoys, 3)
                        .withText("decoy_prefix", "REV_", ValueOrigin.USER);
        RecordedInput first = new RecordedInput(IN.resolve("k562_3.mzML"), 10, Instant.EPOCH, H1);
        RecordedInput second = new RecordedInput(IN.resolve("K562_3.mzXML"), 20, Instant.EPOCH, H2);
        RunIdentity identity =
                new RunIdentity(
                        new RunId("run-0001"),
                        new ProjectId("p"),
                        Instant.parse("2026-10-06T12:00:00Z"),
                        RealComet.NEWER,
                        RunIdentity.spectraOf(List.of(first, second)),
                        new RecordedInput(fasta, 30, Instant.EPOCH, HF),
                        new ArchivedFile("parameters/comet.params", 40, HP),
                        mode,
                        mode == IndexMode.NONE
                                ? DatabaseDelivery.PARAMETER_FILE
                                : DatabaseDelivery.COMMAND_LINE);
        ProjectLayout project = new ProjectLayout(TestPaths.absolute("p"));
        Optional<IndexCacheEntry> entry =
                mode == IndexMode.NONE
                        ? Optional.empty()
                        : Optional.of(IndexCacheEntry.of(project, KEY, database));
        ToolIdentity tool =
                new ToolIdentity(
                        "comet",
                        RealComet.NEWER,
                        Optional.of("v" + RealComet.NEWER),
                        TestPaths.absolute("bin/comet"),
                        HC,
                        true,
                        Optional.empty(),
                        Set.of(),
                        List.of());
        return new CometRun(
                project,
                new RunLayout(RUN),
                identity,
                model,
                new CometSelection(
                        TestPaths.absolute("bin/comet"),
                        ToolVersion.parse(RealComet.NEWER),
                        HC.sha256(),
                        true,
                        Optional.empty()),
                tool,
                entry,
                build,
                FakeSearch.hashes(),
                FakeSearch.checks(FakeSearch.hashes(), false));
    }

    static CometRun plain(DecoySource decoys) {
        return run(decoys, IndexMode.NONE, "db.fasta", false);
    }

    private static Path out(String name) {
        return RUN.resolve("outputs/comet").resolve(name);
    }

    @Test
    @DisplayName("the derived paths, decoy configuration and thread count")
    void derived() {
        CometRun run = plain(DecoySource.COMET_INTERNAL_SEPARATE);
        assertEquals(List.of(IN.resolve("k562_3.mzML"), IN.resolve("K562_3.mzXML")), run.spectra());
        assertEquals(
                List.of("k562_3", "K562_3_2"), run.outputs().stream().map(o -> o.base()).toList());
        assertEquals(IN.resolve("db.fasta"), run.database());
        assertEquals(IN.resolve("db.fasta"), run.searched());
        assertEquals(DecoySource.COMET_INTERNAL_SEPARATE, run.decoySource());
        assertEquals(
                new PinDecoyConfiguration(
                        2, "Comet's internal decoys, reported separately", "REV_"),
                run.decoys());
        assertEquals(3, run.threads());
        CometRun indexed =
                run(DecoySource.COMET_INTERNAL_CONCATENATED, IndexMode.PEPTIDE, "db.fasta", true);
        assertEquals(
                TestPaths.absolute("p/index-cache/" + KEY + "/db.fasta.idx"), indexed.searched());
        IllegalStateException noSource =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new CometRun(
                                                indexed.project(),
                                                indexed.layout(),
                                                indexed.identity(),
                                                indexed.model()
                                                        .withValue(
                                                                "decoy_search",
                                                                new org.cometgui.params.comet.model
                                                                        .ParameterValue.Whole(7),
                                                                ValueOrigin.USER),
                                                indexed.comet(),
                                                indexed.tool(),
                                                indexed.cacheEntry(),
                                                true,
                                                indexed.hashes(),
                                                indexed.checks())
                                        .decoySource());
        assertEquals("decoy_search = 7 is no decoy source", noSource.getMessage());
    }

    @Test
    @DisplayName("a run has a cache entry exactly when it has an index mode")
    void entryExactlyWithAnIndexMode() {
        CometRun plain = plain(DecoySource.COMET_INTERNAL_CONCATENATED);
        CometRun indexed =
                run(DecoySource.COMET_INTERNAL_CONCATENATED, IndexMode.PEPTIDE, "db.fasta", true);
        IllegalArgumentException withEntry =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new CometRun(
                                        plain.project(),
                                        plain.layout(),
                                        plain.identity(),
                                        plain.model(),
                                        plain.comet(),
                                        plain.tool(),
                                        indexed.cacheEntry(),
                                        false,
                                        plain.hashes(),
                                        plain.checks()));
        assertEquals(
                "a run has an index cache entry exactly when it has an index mode; mode none,"
                        + " entry "
                        + indexed.cacheEntry(),
                withEntry.getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CometRun(
                                indexed.project(),
                                indexed.layout(),
                                indexed.identity(),
                                indexed.model(),
                                indexed.comet(),
                                indexed.tool(),
                                Optional.empty(),
                                false,
                                indexed.hashes(),
                                indexed.checks()));
    }

    @Test
    @DisplayName("serialise, hash-inputs and merge-pin declare exactly their files")
    void simpleSteps() {
        CometRun run = plain(DecoySource.COMET_INTERNAL_CONCATENATED);
        assertEquals(
                new StepDeclaration(
                        List.of(
                                DeclaredFile.output(
                                        "comet-params", RUN.resolve("parameters/comet.params"))),
                        List.of()),
                RunDeclarations.serialise(run));
        assertEquals(
                new StepDeclaration(
                        List.of(
                                DeclaredFile.input("spectrum", IN.resolve("k562_3.mzML")),
                                DeclaredFile.input("spectrum", IN.resolve("K562_3.mzXML")),
                                DeclaredFile.input("fasta", IN.resolve("db.fasta"))),
                        List.of()),
                RunDeclarations.hashInputs(run));
        assertEquals(
                new StepDeclaration(
                        List.of(
                                DeclaredFile.input("pin", out("k562_3.pin")),
                                DeclaredFile.input("pin", out("K562_3_2.pin")),
                                DeclaredFile.output(
                                        "merged-pin", RUN.resolve("inputs/pin/merged.pin"))),
                        List.of()),
                RunDeclarations.mergePin(run));
    }

    @Test
    @DisplayName(
            "run-comet and validate-comet-outputs: two outputs per file, three with separate"
                    + " decoys")
    void searchSteps() {
        CometRun concatenated = plain(DecoySource.COMET_INTERNAL_CONCATENATED);
        assertEquals(
                new StepDeclaration(
                        List.of(
                                DeclaredFile.input("spectrum", IN.resolve("k562_3.mzML")),
                                DeclaredFile.input("spectrum", IN.resolve("K562_3.mzXML")),
                                DeclaredFile.input("fasta", IN.resolve("db.fasta")),
                                DeclaredFile.input(
                                        "comet-params", RUN.resolve("parameters/comet.params")),
                                DeclaredFile.output("pepxml", out("k562_3.pep.xml")),
                                DeclaredFile.output("pin", out("k562_3.pin")),
                                DeclaredFile.output("pepxml", out("K562_3_2.pep.xml")),
                                DeclaredFile.output("pin", out("K562_3_2.pin"))),
                        List.of("comet-01", "comet-02")),
                RunDeclarations.runComet(concatenated));
        CometRun separate = plain(DecoySource.COMET_INTERNAL_SEPARATE);
        assertEquals(
                new StepDeclaration(
                        List.of(
                                DeclaredFile.input("pepxml", out("k562_3.pep.xml")),
                                DeclaredFile.input("pin", out("k562_3.pin")),
                                DeclaredFile.input("decoy-pepxml", out("k562_3.decoy.pep.xml")),
                                DeclaredFile.input("pepxml", out("K562_3_2.pep.xml")),
                                DeclaredFile.input("pin", out("K562_3_2.pin")),
                                DeclaredFile.input("decoy-pepxml", out("K562_3_2.decoy.pep.xml"))),
                        List.of()),
                RunDeclarations.validateOutputs(separate));
        assertEquals(10, RunDeclarations.runComet(separate).files().size());
        assertEquals(
                DeclaredFile.output("decoy-pepxml", out("k562_3.decoy.pep.xml")),
                RunDeclarations.runComet(separate).files().get(6));
    }

    @Test
    @DisplayName(
            "an index run: the build declares its invocation only when it builds; the search"
                    + " reads the index")
    void indexSteps() {
        Path index = TestPaths.absolute("p/index-cache/" + KEY + "/db.fasta.idx");
        CometRun build =
                run(
                        DecoySource.COMET_INTERNAL_CONCATENATED,
                        IndexMode.FRAGMENT_ION,
                        "db.fasta",
                        true);
        assertEquals(
                new StepDeclaration(
                        List.of(
                                DeclaredFile.input(
                                        "comet-params", RUN.resolve("parameters/comet.params")),
                                DeclaredFile.input("fasta", IN.resolve("db.fasta")),
                                DeclaredFile.output("comet-index", index)),
                        List.of("comet-index")),
                RunDeclarations.buildIndex(build));
        CometRun reuse =
                run(
                        DecoySource.COMET_INTERNAL_CONCATENATED,
                        IndexMode.FRAGMENT_ION,
                        "db.fasta",
                        false);
        assertEquals(List.of(), RunDeclarations.buildIndex(reuse).invocationIds());
        assertEquals(
                DeclaredFile.input("comet-index", index),
                RunDeclarations.runComet(build).files().get(2));
        IllegalStateException noIndex =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RunDeclarations.buildIndex(
                                        plain(DecoySource.COMET_INTERNAL_CONCATENATED)));
        assertEquals("a run without an index mode has no index step", noIndex.getMessage());

        CometRun chosen =
                run(DecoySource.COMET_INTERNAL_CONCATENATED, IndexMode.NONE, "db.fasta.idx", false);
        assertEquals("comet-index", RunDeclarations.databaseRole(chosen));
        assertEquals(
                DeclaredFile.input("comet-index", IN.resolve("db.fasta.idx")),
                RunDeclarations.hashInputs(chosen).files().get(2));
        assertEquals(
                DeclaredFile.input("comet-index", IN.resolve("db.fasta.idx")),
                RunDeclarations.runComet(chosen).files().get(2));
    }

    @Test
    @DisplayName("the recorded inputs: every Phase 08 input kind, from the identity")
    void recordedInputs() {
        CometRun run =
                run(DecoySource.COMET_INTERNAL_CONCATENATED, IndexMode.PEPTIDE, "db.fasta", true);
        StepInputs inputs = RunInputs.recorded(run.identity(), HC.sha256());
        Map<InputKind, InputValue> expected =
                Map.of(
                        InputKind.SPECTRUM_FILES,
                        InputValue.files(
                                List.of(
                                        new InputValue.NamedFile("k562_3.mzML", H1),
                                        new InputValue.NamedFile("K562_3.mzXML", H2))),
                        InputKind.FASTA,
                        InputValue.file("db.fasta", HF),
                        InputKind.COMET_PARAMETERS,
                        new InputValue.Bytes(HP.sha256()),
                        InputKind.COMET_INDEX_MODE,
                        InputValue.text("peptide"),
                        InputKind.COMET_TOOL,
                        InputValue.tool(RealComet.NEWER, HC.sha256()));
        assertEquals(expected, inputs.values());
        List<String> kinds = new ArrayList<>();
        inputs.values().keySet().forEach(kind -> kinds.add(kind.id()));
        assertEquals(
                List.of(
                        "spectrum-files",
                        "fasta",
                        "comet-parameters",
                        "comet-index-mode",
                        "comet-tool"),
                kinds);
    }

    @Test
    @DisplayName("an input label for a detail key has at least two digits")
    void inputLabels() {
        assertEquals("input-01", SearchSteps.inputLabel(1));
        assertEquals("input-09", SearchSteps.inputLabel(9));
        assertEquals("input-10", SearchSteps.inputLabel(10));
        assertEquals("input-123", SearchSteps.inputLabel(123));
    }
}
