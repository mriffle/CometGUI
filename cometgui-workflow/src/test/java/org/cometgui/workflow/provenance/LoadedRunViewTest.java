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

package org.cometgui.workflow.provenance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A fully populated sealed record, loaded: every part of the view against values typed here from
 * what {@link CompleteRun} wrote -- never against what the read model produced on an earlier run.
 */
class LoadedRunViewTest {

    @TempDir private Path tmp;

    private RunDirectories dirs;

    private CompleteRun run;

    private ProvenanceView view;

    @BeforeEach
    void load() throws IOException {
        dirs = RunDirectories.create(tmp);
        run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        LoadedRun loaded = reader().load(run.layout());
        assertEquals(RecordState.SEALED, loaded.state());
        assertEquals(run.layout(), loaded.layout());
        view = loaded.view();
    }

    @AfterEach
    void close() throws IOException {
        dirs.close();
    }

    static ProvenanceReader reader() {
        return new ProvenanceReader(
                SecretRedactor::patternsOnly, new CachingHashService(new StreamingHashService()));
    }

    @Test
    @DisplayName("a run loads from its directory exactly as from its layout")
    void loadsFromItsDirectory() throws IOException {
        LoadedRun fromPath = reader().load(run.layout().root());
        assertAll(
                () -> assertEquals(run.layout(), fromPath.layout()),
                () -> assertEquals(view, fromPath.view()),
                () -> assertEquals(Optional.of(run.manifest()), fromPath.manifest()));
    }

    @Test
    @DisplayName("summary: identity, status, times, tool versions, counts and the application")
    void summary() {
        ProvenanceView.Summary summary = view.summary();
        assertAll(
                () -> assertEquals("run-0001", summary.runId()),
                () -> assertEquals("project-p13", summary.projectId()),
                () -> assertEquals("completed", summary.status()),
                () -> assertEquals(RecordState.SEALED, summary.recordState()),
                () -> assertEquals("2026-10-13T09:00:01.000Z", summary.start()),
                () -> assertEquals(Optional.of("2026-10-13T09:12:35.567Z"), summary.end()),
                () -> assertEquals(Optional.of("0:12:34.567"), summary.duration()),
                () ->
                        assertEquals(
                                List.of("comet 2026.03.0", "percolator 3.07.1"),
                                summary.toolVersions()),
                () -> assertEquals(4, summary.inputCount()),
                () -> assertEquals(6, summary.outputCount()),
                () ->
                        assertEquals(
                                Optional.of(
                                        new ProvenanceView.Application(
                                                "0.13.0",
                                                "abc1234def",
                                                "Linux 6.8.0-137-generic",
                                                "amd64",
                                                "25.0.1",
                                                "en-US",
                                                "fr-FR",
                                                "Europe/Paris")),
                                summary.application()));
    }

    @Test
    @DisplayName("tools: one row per record, in record order, with digests, badge and command")
    void tools() {
        List<ProvenanceView.Tool> tools = view.tools();
        assertEquals(3, tools.size());
        ProvenanceView.Tool comet = tools.get(0);
        String exe = run.cometExe().toString();
        String params = run.layout().cometParamsFile().toString();
        assertAll(
                () -> assertEquals(1, comet.order()),
                () -> assertEquals("comet", comet.name()),
                () -> assertEquals("2026.03.0", comet.version()),
                () -> assertEquals(Optional.of("v2026.03.0"), comet.releaseTag()),
                () -> assertEquals("managed", comet.origin()),
                () -> assertEquals(exe, comet.path()),
                () -> assertEquals(run.cometExe(), comet.location()),
                () -> assertEquals(RunDirectories.ABC_MD5, comet.md5()),
                () -> assertEquals(RunDirectories.ABC_SHA256, comet.sha256()),
                () ->
                        assertEquals(
                                Optional.of("comet-2026.03.0-linux-x86_64"),
                                comet.artefactIdentity()),
                () -> assertEquals(List.of(), comet.capabilities()),
                () -> assertEquals(Optional.of("comet-01"), comet.stageId()),
                () -> assertEquals(List.of(), comet.warnings()),
                () ->
                        assertEquals(
                                "[\""
                                        + exe
                                        + "\", \"-P"
                                        + params
                                        + "\", \"-N"
                                        + run.layout().root().resolve("outputs/comet/k562_1")
                                        + "\", \""
                                        + dirs.inputFile("k562_1.mzML")
                                        + "\"]",
                                comet.command()));
        assertEquals(Optional.of("comet-02"), tools.get(1).stageId());
        ProvenanceView.Tool percolator = tools.get(2);
        assertAll(
                () -> assertEquals(3, percolator.order()),
                () -> assertEquals("percolator", percolator.name()),
                () -> assertEquals(RunDirectories.MESSAGE_DIGEST_MD5, percolator.md5()),
                () -> assertEquals(RunDirectories.MESSAGE_DIGEST_SHA256, percolator.sha256()),
                () -> assertEquals(List.of("pin-input", "xml-output"), percolator.capabilities()),
                () ->
                        assertEquals(
                                List.of(
                                        CompleteRun.ADVISORY,
                                        CompleteRun.WEIGHTS,
                                        "the log on stderr was truncated at 1 GiB"),
                                percolator.warnings()),
                () ->
                        assertTrue(
                                percolator
                                        .command()
                                        .startsWith(
                                                "[\""
                                                        + run.percolatorExe()
                                                        + "\", \"--seed\", \"1\","
                                                        + " \"--results-psms\""),
                                percolator.command()));
    }

    @Test
    @DisplayName("files: every record, with size, time, digests and status")
    void files() throws IOException {
        List<ProvenanceView.File> files = view.files();
        assertEquals(10, files.size());
        ProvenanceView.File first = files.get(0);
        Path spectrum = dirs.inputFile("k562_1.mzML");
        assertAll(
                () -> assertEquals("input", first.direction()),
                () -> assertEquals("spectrum", first.role()),
                () -> assertEquals(spectrum.toString(), first.path()),
                () -> assertEquals(spectrum, first.location()),
                () -> assertEquals(23L, first.sizeBytes(), "\"content of k562_1.mzML\\n\""),
                () -> assertEquals("2026-10-13T09:05:00.250Z", first.modified()),
                () -> assertEquals(RunDirectories.digests(spectrum).md5(), first.md5()),
                () -> assertEquals(RunDirectories.digests(spectrum).sha256(), first.sha256()),
                () -> assertEquals("completed", first.status()),
                () -> assertFalse(first.partial()));
        assertEquals(
                List.of(
                        "input spectrum",
                        "input spectrum",
                        "input fasta",
                        "output comet-params",
                        "output pepxml",
                        "output pin",
                        "output pepxml",
                        "output pin",
                        "input percolator-settings",
                        "output percolator-psms"),
                files.stream().map(file -> file.direction() + " " + file.role()).toList());
    }

    @Test
    @DisplayName("timeline: executions in start order with command and duration; events in order")
    void timeline() {
        List<ProvenanceView.Execution> executions = view.timeline().executions();
        assertEquals(
                List.of(
                        Optional.of("comet-02"),
                        Optional.of("comet-01"),
                        Optional.of("percolator")),
                executions.stream().map(ProvenanceView.Execution::stageId).toList(),
                "comet-02 started 500 ms before comet-01");
        ProvenanceView.Execution comet2 = executions.get(0);
        ProvenanceView.Execution percolator = executions.get(2);
        assertAll(
                () -> assertEquals("comet", comet2.tool()),
                () -> assertEquals("2026.03.0", comet2.version()),
                () -> assertEquals("2026-10-13T09:00:01.500Z", comet2.start()),
                () -> assertEquals("2026-10-13T09:05:30.000Z", comet2.end()),
                () -> assertEquals("0:05:28.500", comet2.duration()),
                () -> assertEquals(328_500L, comet2.durationMillis()),
                () -> assertEquals(0, comet2.exitCode()),
                () -> assertEquals("completed", comet2.status()),
                () -> assertEquals(run.layout().root().toString(), comet2.workingDirectory()),
                () -> assertEquals("", comet2.environment()),
                () -> assertTrue(comet2.command().contains("k562_2.mzML\"]"), comet2.command()),
                () -> assertEquals("OMP_NUM_THREADS=3", percolator.environment()),
                () -> assertEquals("0:06:30.125", percolator.duration()));
        List<ProvenanceView.Event> events = view.timeline().events();
        assertEquals(
                List.of("run.started", "stage.finished", "run.finished", "pdv.launched"),
                events.stream().map(ProvenanceView.Event::type).toList());
        assertEquals(
                new ProvenanceView.Event(
                        1L,
                        "2026-10-13T09:00:05.000Z",
                        "run.started",
                        List.of("attempt = 1", "run.id = run-0001")),
                events.get(0));
        assertEquals(
                List.of("run.id = run-0001", "tool = pdv", "tool.version = 2.1.4"),
                events.get(3).payload());
        assertEquals(4L, events.get(3).sequence());
    }

    @Test
    @DisplayName("logs: each archived log once, with both digests and where it is")
    void logs() throws IOException {
        List<ProvenanceView.Log> logs = view.logs();
        assertEquals(3, logs.size());
        ProvenanceView.Log first = logs.get(0);
        assertAll(
                () -> assertEquals("comet", first.tool()),
                () -> assertEquals(Optional.of("comet-01"), first.stageId()),
                () -> assertEquals("stdout and stderr", first.streams()),
                () -> assertEquals(run.cometLog1().toString(), first.path()),
                () -> assertEquals(run.cometLog1(), first.location()),
                () -> assertEquals(RunDirectories.digests(run.cometLog1()).md5(), first.md5()),
                () ->
                        assertEquals(
                                RunDirectories.digests(run.cometLog1()).sha256(), first.sha256()),
                () -> assertEquals(run.percolatorLog(), logs.get(2).location()));
    }

    @Test
    @DisplayName("warnings: every Percolator setting source once, then the tool's own warning")
    void warnings() {
        assertEquals(
                List.of(
                        new ProvenanceView.Warning(
                                WarningSource.VERSION_ADVISORY, "xml-final", CompleteRun.ADVISORY),
                        new ProvenanceView.Warning(
                                WarningSource.SKIPPED_VERSION,
                                "Percolator 3.09 managed",
                                "Percolator 3.09 managed was skipped: "
                                        + CompleteRun.SKIP_REASON
                                        + " (missing: xml-output)"),
                        new ProvenanceView.Warning(
                                WarningSource.SKIPPED_VERSION,
                                "Percolator 3.08 local",
                                "Percolator 3.08 local was skipped: it is a local build without"
                                        + " XML (missing: xml-output pin-input)"),
                        new ProvenanceView.Warning(
                                WarningSource.OPTION_NOT_EMITTED,
                                "--weights",
                                "the option --weights was not passed to Percolator: "
                                        + CompleteRun.NOT_EMITTED_REASON),
                        new ProvenanceView.Warning(
                                WarningSource.OPTION_NOT_EMITTED,
                                "--no-analytics",
                                "the option --no-analytics was not passed to Percolator: this"
                                        + " build has no such option"),
                        new ProvenanceView.Warning(
                                WarningSource.WEIGHTS_FALLBACK,
                                "percolator.weights-warning",
                                CompleteRun.WEIGHTS),
                        new ProvenanceView.Warning(
                                WarningSource.TOOL_WARNING,
                                "percolator percolator",
                                "the log on stderr was truncated at 1 GiB")),
                view.warnings());
    }

    @Test
    @DisplayName(
            "parameters: the exact archived files, Percolator settings, origins, defaults, preset")
    void parameters() throws IOException {
        ProvenanceView.Parameters parameters = view.parameters();
        ProvenanceView.ArchivedText comet = parameters.cometParams().orElseThrow();
        String exact = Files.readString(run.layout().cometParamsFile(), StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(Optional.of("2026.03.0"), parameters.cometRelease()),
                () -> assertEquals("comet-params", comet.role()),
                () -> assertEquals(DiskOutcome.MATCHES, comet.check()),
                () -> assertEquals(Optional.of(exact), comet.text()),
                () -> assertTrue(exact.contains("num_threads = 8\n")),
                () -> assertEquals(Optional.empty(), comet.problem()),
                () ->
                        assertEquals(
                                RunDirectories.digests(run.layout().cometParamsFile()).sha256(),
                                comet.recordedSha256()),
                () -> assertEquals(Optional.of(comet.recordedSha256()), comet.foundSha256()),
                () ->
                        assertEquals(
                                Optional.of("{\"seed\": \"1\"}\n"),
                                parameters.percolatorSettingsFile().orElseThrow().text()),
                () -> assertEquals(List.of(), parameters.notes()));
        assertEquals(
                List.of(
                        "percolator.advisory.xml-final",
                        "percolator.not-emitted.01.option",
                        "percolator.not-emitted.01.reason",
                        "percolator.not-emitted.02.option",
                        "percolator.not-emitted.02.reason",
                        "percolator.seed",
                        "percolator.skipped.01.missing",
                        "percolator.skipped.01.reason",
                        "percolator.skipped.01.version",
                        "percolator.skipped.02.missing",
                        "percolator.skipped.02.reason",
                        "percolator.skipped.02.version",
                        "percolator.version",
                        "percolator.weights-warning"),
                parameters.percolatorSettings().stream().map(ProvenanceView.Setting::key).toList());
        assertEquals(
                new ProvenanceView.Setting("percolator.seed", "1"),
                parameters.percolatorSettings().get(5));
        assertAll(
                () ->
                        assertEquals(
                                new ProvenanceView.Parameter(
                                        "num_threads",
                                        "8",
                                        Optional.of("user"),
                                        Optional.of("0"),
                                        true,
                                        Optional.of("8"),
                                        false),
                                row(parameters, "num_threads")),
                () ->
                        assertEquals(
                                new ProvenanceView.Parameter(
                                        "peptide_mass_tolerance_upper",
                                        "10.0",
                                        Optional.of("user"),
                                        Optional.of("20.0"),
                                        true,
                                        Optional.empty(),
                                        false),
                                row(parameters, "peptide_mass_tolerance_upper")),
                () ->
                        assertEquals(
                                new ProvenanceView.Parameter(
                                        "fragment_bin_tol",
                                        "0.02",
                                        Optional.of("preset"),
                                        Optional.of("0.02"),
                                        false,
                                        Optional.of("1.0005"),
                                        true),
                                row(parameters, "fragment_bin_tol")),
                () ->
                        assertEquals(
                                new ProvenanceView.Parameter(
                                        "output_percolatorfile",
                                        "1",
                                        Optional.of("workflow-enforced"),
                                        Optional.of("0"),
                                        true,
                                        Optional.empty(),
                                        false),
                                row(parameters, "output_percolatorfile")),
                () ->
                        assertEquals(
                                new ProvenanceView.Parameter(
                                        "decoy_prefix",
                                        "DECOY_",
                                        Optional.of("comet-default"),
                                        Optional.of("DECOY_"),
                                        false,
                                        Optional.empty(),
                                        false),
                                row(parameters, "decoy_prefix")),
                () ->
                        assertEquals(
                                "database_name = /data/human.fasta",
                                "database_name = " + row(parameters, "database_name").value()));
        assertEquals(
                List.of(
                        new ProvenanceView.Preset(
                                1,
                                "high-res",
                                "High resolution",
                                "built-in",
                                "2026.03.0",
                                "1",
                                List.of("fragment_bin_tol = 1.0005", "num_threads = 8"))),
                parameters.presets());
        assertEquals(
                List.of("fragment_bin_tol = 1.0005", "num_threads = 8"),
                parameters.presets().get(0).applied());
    }

    static ProvenanceView.Parameter row(ProvenanceView.Parameters parameters, String name) {
        return parameters.cometParameters().stream()
                .filter(row -> row.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " is not listed"));
    }
}
