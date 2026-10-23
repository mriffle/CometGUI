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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two failure paths of the specification's list, through the real workflow and engine with stand-in
 * tools (Phase 14, gate item 4):
 *
 * <ul>
 *   <li><strong>"Comet exits zero but the PIN is missing"</strong>: a stand-in Comet that writes
 *       each file's pepXML, no PIN, and exits 0. The run fails at {@code run-comet}, naming the
 *       first PIN that does not exist; nothing after it starts, Percolator is never launched, and
 *       the record says what was and was not written.
 *   <li><strong>"a source input deleted after selection"</strong>, before the run exists: a
 *       spectrum file chosen and then deleted before Run. The run is refused before it is created,
 *       naming the file, and nothing is launched or written. (The same file deleted after the run
 *       was created is {@code StepFailureTest#hashInputsNamesADeletedSpectrumFile}.)
 * </ul>
 *
 * <p>Every expected text is typed out. No file outside this module is read.
 */
class RunFailurePathTest {

    /**
     * A Comet that behaves well in every way but one: it reads its {@code -N} base, writes {@code
     * <base>.pep.xml} and exits 0 -- and never writes {@code <base>.pin}. {@code printf} is a
     * built-in of the shell, so the empty environment the process service gives a tool is enough.
     */
    private static final String COMET_WITHOUT_PIN =
            "#!/bin/sh\n"
                    + "base=''\n"
                    + "for argument in \"$@\"; do\n"
                    + "  case \"$argument\" in\n"
                    + "    -N*) base=\"${argument#-N}\" ;;\n"
                    + "  esac\n"
                    + "done\n"
                    + "[ -n \"$base\" ] || exit 9\n"
                    + "printf '<?xml version=\"1.0\"?>\\n<msms_pipeline_analysis/>\\n'"
                    + " > \"$base.pep.xml\"\n"
                    + "exit 0\n";

    private static SearchRequest withPercolator(FakeSearch fake, Path percolator)
            throws IOException {
        ToolOffer offer =
                FakePercolator.offer(
                        percolator, "3.07.1", ToolOrigin.MANAGED, FakePercolator.EVERY, List.of());
        return new SearchRequest(
                fake.model(DecoySource.COMET_INTERNAL_CONCATENATED),
                fake.spectra(),
                fake.selection(),
                IndexMode.NONE,
                Optional.of(
                        FakePercolator.choice(
                                offer, List.of(offer), Set.of(), PercolatorSettings.defaults())));
    }

    @Test
    @DisplayName(
            "Comet exits 0 but writes no PIN: run-comet fails naming the PIN, nothing downstream"
                    + " starts, Percolator is never launched")
    void cometExitsZeroWithoutAPin(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch fake = FakeSearch.create(directory);
        Files.writeString(fake.executable(), COMET_WITHOUT_PIN, StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(
                fake.executable(), PosixFilePermissions.fromString("rwx------"));
        Path percolator =
                FakePercolator.write(
                        fake.root().resolve("bin/percolator"), FakePercolator.Behaviour.normal());
        try (RealProject project = RealProject.create(fake.project())) {
            PreparedRun prepared = project.prepare(withPercolator(fake, percolator));
            RunResult result = project.run(prepared);
            Path comet = prepared.layout().cometOutputDirectory();
            Path firstPin = prepared.layout().pinFile("k562_3");
            ProvenanceManifest manifest = RunEvidence.manifest(prepared.layout());
            assertAll(
                    () -> assertEquals(AttemptOutcome.FAILED, result.outcome()),
                    () -> assertEquals(StepState.FAILED, result.states().get(EngineStep.RUN_COMET)),
                    () ->
                            assertEquals(
                                    "run-comet reported success, but its declared output file,"
                                            + " role pin "
                                            + firstPin
                                            + " does not exist",
                                    result.failures().get(EngineStep.RUN_COMET),
                                    "the step that ran the tool fails, naming the missing PIN"),
                    () ->
                            assertEquals(
                                    Map.of(
                                            EngineStep.VALIDATE_COMET_OUTPUTS,
                                                    StepState.NOT_STARTED,
                                            EngineStep.MERGE_PIN, StepState.NOT_STARTED,
                                            EngineStep.RUN_PERCOLATOR, StepState.NOT_STARTED,
                                            EngineStep.PARSE_PERCOLATOR, StepState.NOT_STARTED),
                                    statesOf(
                                            result,
                                            EngineStep.VALIDATE_COMET_OUTPUTS,
                                            EngineStep.MERGE_PIN,
                                            EngineStep.RUN_PERCOLATOR,
                                            EngineStep.PARSE_PERCOLATOR),
                                    "nothing after run-comet started"),
                    () ->
                            assertEquals(
                                    2,
                                    project.runner().launchesOf(fake.executable()).size(),
                                    "Comet really ran, once per spectrum file"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    project.runner().launchesOf(percolator),
                                    "Percolator was never launched"),
                    () ->
                            assertEquals(
                                    List.of("k562_3.pep.xml", "k562_4.pep.xml"),
                                    RunEvidence.listing(comet),
                                    "what the stand-in wrote, and no PIN"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    pinsUnder(prepared.layout().root()),
                                    "no PIN anywhere in the run, merged or not"),
                    () ->
                            assertEquals(
                                    expectedOutputs(prepared),
                                    outputsOf(manifest),
                                    "the record: the pepXML written, partial; no record of a"
                                            + " PIN that was never created"));
        }
    }

    @Test
    @DisplayName(
            "a spectrum file deleted after it was chosen, before Run: refused before the run"
                    + " exists, naming the file; nothing launched or written")
    void aSpectrumFileDeletedBeforeRun(@TempDir Path directory)
            throws IOException, InterruptedException {
        FakeSearch fake = FakeSearch.create(directory);
        Path percolator =
                FakePercolator.write(
                        fake.root().resolve("bin/percolator"), FakePercolator.Behaviour.normal());
        try (RealProject project = RealProject.create(fake.project())) {
            SearchRequest chosen = withPercolator(fake, percolator);
            Path deleted = fake.spectra().get(1);
            Files.delete(deleted);
            RunBlockedException refused =
                    assertThrows(RunBlockedException.class, () -> project.prepare(chosen));
            assertAll(
                    () ->
                            assertEquals(
                                    List.of(
                                            "spectrum file 2 ("
                                                    + deleted
                                                    + ") does not exist or cannot be read"),
                                    refused.report().problems(),
                                    "the deleted file, by position and path, and nothing else"),
                    () ->
                            assertEquals(
                                    "the run cannot start:\n- spectrum file 2 ("
                                            + deleted
                                            + ") does not exist or cannot be read",
                                    refused.getMessage()),
                    () -> assertEquals(List.of(), project.runner().launches(), "nothing launched"),
                    () -> assertEquals(List.of(), project.runDirectories(), "and no run created"));
        }
    }

    private static Map<EngineStep, StepState> statesOf(RunResult result, EngineStep... steps) {
        Map<EngineStep, StepState> states = new TreeMap<>();
        for (EngineStep step : steps) {
            states.put(step, result.states().get(step));
        }
        return states;
    }

    private static List<String> pinsUnder(Path root) throws IOException {
        try (Stream<Path> walked = Files.walk(root)) {
            return walked.filter(path -> String.valueOf(path.getFileName()).endsWith(".pin"))
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
    }

    /**
     * The Comet outputs the record holds, as path to role and status, typed out: each pepXML the
     * stand-in wrote, marked {@code partial} because its step failed ({@code R-PROV-01}). The PINs
     * have no record at all -- the specification records every file "used or created by a run", and
     * these were never created; their absence is stated by the step's failure instead.
     */
    private static Map<String, String> expectedOutputs(PreparedRun prepared) {
        Path comet = prepared.layout().cometOutputDirectory();
        Map<String, String> expected = new TreeMap<>();
        expected.put(comet.resolve("k562_3.pep.xml").toString(), "pepxml partial");
        expected.put(comet.resolve("k562_4.pep.xml").toString(), "pepxml partial");
        return expected;
    }

    /** Every output the record holds for the Comet output directory, as path to role and status. */
    private static Map<String, String> outputsOf(ProvenanceManifest manifest) {
        Map<String, String> outputs = new TreeMap<>();
        for (FileRecord file : manifest.files()) {
            if (file.direction() == FileDirection.OUTPUT
                    && (file.role().equals("pepxml") || file.role().equals("pin"))) {
                outputs.put(file.path().toString(), file.role() + " " + file.status().wireName());
            }
        }
        return outputs;
    }
}
