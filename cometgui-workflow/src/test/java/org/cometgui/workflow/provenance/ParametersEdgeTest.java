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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Parameters part when the archived file or the release cannot be used as they are. */
class ParametersEdgeTest {

    @TempDir private Path tmp;

    private RunDirectories dirs;

    @BeforeEach
    void create() throws IOException {
        dirs = RunDirectories.create(tmp);
    }

    @AfterEach
    void close() throws IOException {
        dirs.close();
    }

    /** A sealed run whose record lists only its comet.params, with the given settings. */
    private ProvenanceView sealed(String params, Map<String, String> settings, boolean dropRunJson)
            throws IOException {
        RunLayout layout = dirs.recordedRun("run-0009", RunDirectories.CREATED, params);
        dirs.attempt(
                layout, RunDirectories.STARTED, AttemptOutcome.SUCCEEDED, RunDirectories.ENDED);
        ProvenanceManifest manifest =
                ProvenanceManifest.current(
                        RunDirectories.runRecord(layout, ProvenanceStatus.COMPLETED),
                        RunDirectories.application(),
                        settings,
                        List.of(),
                        List.of(
                                RunDirectories.fileRecord(
                                        FileDirection.OUTPUT,
                                        "comet-params",
                                        layout.cometParamsFile(),
                                        ProvenanceStatus.COMPLETED)));
        RunDirectories.seal(layout, manifest, SecretRedactor.patternsOnly());
        if (dropRunJson) {
            Files.delete(layout.runFile());
        }
        return LoadedRunViewTest.reader().load(layout).view();
    }

    @Test
    @DisplayName("a preset value the codec cannot read is compared, and shown, as its text")
    void unreadablePresetValue() throws IOException {
        ProvenanceView view =
                sealed(
                        RunDirectories.cometParams(),
                        Map.of(
                                "comet.release", "2026.03.0",
                                "comet.preset.01.id", "odd",
                                "comet.preset.01.name", "Odd",
                                "comet.preset.01.origin", "user",
                                "comet.preset.01.comet-version", "2026.03.0",
                                "comet.preset.01.schema-version", "1",
                                "comet.preset.01.applied.01", "num_threads = lots",
                                "comet.preset.01.applied.02", "no separator here",
                                "comet.preset.01.applied.03", "fragment_bin_tol = 0.020"),
                        false);
        ProvenanceView.Parameter threads = LoadedRunViewTest.row(view.parameters(), "num_threads");
        ProvenanceView.Parameter bin = LoadedRunViewTest.row(view.parameters(), "fragment_bin_tol");
        assertAll(
                () -> assertEquals(Optional.of("lots"), threads.presetValue()),
                () -> assertTrue(threads.differsFromPreset()),
                () -> assertEquals(Optional.of("0.020"), bin.presetValue()),
                () ->
                        assertEquals(
                                List.of(
                                        "num_threads = lots",
                                        "no separator here",
                                        "fragment_bin_tol = 0.020"),
                                view.parameters().presets().get(0).applied()));
    }

    @Test
    @DisplayName("a release this build does not curate: the file is shown, no rows, and why")
    void uncuratedRelease() throws IOException {
        ProvenanceView view =
                sealed(RunDirectories.cometParams(), Map.of("comet.release", "2019.01.0"), false);
        ProvenanceView.Parameters parameters = view.parameters();
        assertAll(
                () -> assertEquals(Optional.of("2019.01.0"), parameters.cometRelease()),
                () -> assertTrue(parameters.cometParams().orElseThrow().text().isPresent()),
                () -> assertEquals(List.of(), parameters.cometParameters()),
                () -> assertEquals(1, parameters.notes().size()),
                () ->
                        assertTrue(
                                parameters
                                        .notes()
                                        .get(0)
                                        .startsWith(
                                                "Comet 2019.01.0 is not a release this build"
                                                        + " can read parameters for: "),
                                parameters.notes().get(0)));
    }

    @Test
    @DisplayName("the release comes from run.json when the record's settings do not name it")
    void releaseFromRunJson() throws IOException {
        ProvenanceView view = sealed(RunDirectories.cometParams(), Map.of(), false);
        assertEquals(Optional.of("2026.03.0"), view.parameters().cometRelease());
        assertEquals("8", LoadedRunViewTest.row(view.parameters(), "num_threads").value());
    }

    @Test
    @DisplayName("no release recorded anywhere: the file is shown and the rows are not")
    void noRelease() throws IOException {
        ProvenanceView view = sealed(RunDirectories.cometParams(), Map.of(), true);
        assertAll(
                () -> assertEquals(Optional.empty(), view.parameters().cometRelease()),
                () -> assertEquals(List.of(), view.parameters().cometParameters()),
                () ->
                        assertEquals(
                                List.of(
                                        "this run records no Comet release, so its comet.params"
                                                + " cannot be read for one"),
                                view.parameters().notes()));
    }

    @Test
    @DisplayName("a file the parser refuses: its diagnostics are the notes")
    void parserDiagnostics() throws IOException {
        ProvenanceView view =
                sealed(
                        RunDirectories.replaceLine(
                                RunDirectories.cometParams(), "num_threads", "num_threads = many"),
                        Map.of("comet.release", "2026.03.0"),
                        false);
        assertAll(
                () -> assertEquals(List.of(), view.parameters().cometParameters()),
                () -> assertTrue(view.parameters().notes().size() >= 1),
                () ->
                        assertTrue(
                                view.parameters().notes().stream()
                                        .anyMatch(note -> note.contains("num_threads")),
                                view.parameters().notes().toString()));
    }

    @Test
    @DisplayName("a file larger than the limit is reported, not read; its digest is still checked")
    void tooLarge() throws IOException {
        String big =
                RunDirectories.cometParams()
                        + "\n#"
                        + "x".repeat((int) ProvenanceReader.PARAMETER_TEXT_LIMIT)
                        + "\n";
        ProvenanceView view = sealed(big, Map.of("comet.release", "2026.03.0"), false);
        ProvenanceView.ArchivedText archived = view.parameters().cometParams().orElseThrow();
        long size = big.getBytes(StandardCharsets.UTF_8).length;
        assertAll(
                () -> assertEquals(DiskOutcome.MATCHES, archived.check()),
                () -> assertEquals(Optional.empty(), archived.text()),
                () ->
                        assertEquals(
                                Optional.of(
                                        "the file is "
                                                + size
                                                + " bytes, larger than the 1048576 bytes this"
                                                + " section shows"),
                                archived.problem()),
                () ->
                        assertEquals(
                                List.of(
                                        "the archived comet.params is not shown, so its parameters"
                                                + " are not listed"),
                                view.parameters().notes()));
    }

    @Test
    @DisplayName("a file of exactly the limit is still shown")
    void exactlyTheLimit() throws IOException {
        String base = RunDirectories.cometParams() + "\n#";
        int pad = (int) ProvenanceReader.PARAMETER_TEXT_LIMIT - base.length() - 1;
        String exact = base + "y".repeat(pad) + "\n";
        assertEquals(
                ProvenanceReader.PARAMETER_TEXT_LIMIT,
                exact.getBytes(StandardCharsets.UTF_8).length);
        ProvenanceView view = sealed(exact, Map.of("comet.release", "2026.03.0"), false);
        assertEquals(Optional.of(exact), view.parameters().cometParams().orElseThrow().text());
    }

    @Test
    @DisplayName("a file that is not UTF-8 is reported as such")
    void notUtf8() throws IOException {
        ProvenanceView view =
                sealed(RunDirectories.cometParams(), Map.of("comet.release", "2026.03.0"), false);
        assertTrue(view.parameters().cometParams().orElseThrow().text().isPresent());
        RunDirectories again = RunDirectories.create(Files.createDirectory(tmp.resolve("again")));
        try {
            RunLayout layout =
                    again.recordedRun(
                            "run-0010", RunDirectories.CREATED, RunDirectories.cometParams());
            Files.write(
                    layout.cometParamsFile(), new byte[] {'a', ' ', '=', ' ', (byte) 0xC3, '\n'});
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            RunDirectories.runRecord(layout, ProvenanceStatus.COMPLETED),
                            RunDirectories.application(),
                            Map.of(),
                            List.of(),
                            List.of(
                                    RunDirectories.fileRecord(
                                            FileDirection.OUTPUT,
                                            "comet-params",
                                            layout.cometParamsFile(),
                                            ProvenanceStatus.COMPLETED)));
            RunDirectories.seal(layout, manifest, SecretRedactor.patternsOnly());
            ProvenanceView.ArchivedText archived =
                    LoadedRunViewTest.reader()
                            .load(layout)
                            .view()
                            .parameters()
                            .cometParams()
                            .orElseThrow();
            assertAll(
                    () -> assertEquals(DiskOutcome.MATCHES, archived.check()),
                    () -> assertEquals(Optional.empty(), archived.text()),
                    () ->
                            assertEquals(
                                    Optional.of("the file is not UTF-8 text"), archived.problem()));
        } finally {
            again.close();
        }
    }

    @Test
    @DisplayName("a path that cannot be hashed is unreadable, with a warning")
    void unreadable() throws IOException {
        ProvenanceView ok = sealed(RunDirectories.cometParams(), Map.of(), false);
        Path params = ok.parameters().cometParams().orElseThrow().location();
        RunLayout layout =
                new RunLayout(dirs.project().runsDirectory().resolve("20261013T090000Z-run-0009"));
        assertEquals(layout.cometParamsFile(), params);
        Files.delete(params);
        Files.createDirectory(params);
        ProvenanceView view = LoadedRunViewTest.reader().load(layout).view();
        ProvenanceView.ArchivedText archived = view.parameters().cometParams().orElseThrow();
        ProvenanceView.Warning warning =
                view.warnings().stream()
                        .filter(w -> w.source() == WarningSource.PARAMETER_FILE_CHANGED)
                        .findFirst()
                        .orElseThrow();
        assertAll(
                () -> assertEquals(DiskOutcome.UNREADABLE, archived.check()),
                () -> assertEquals(Optional.empty(), archived.foundSha256()),
                () ->
                        assertTrue(
                                archived.problem()
                                        .orElseThrow()
                                        .startsWith("the file could not be read: ")),
                () ->
                        assertEquals(
                                "the archived comet-params file could not be read: "
                                        + archived.problem().orElseThrow(),
                                warning.message()));
    }

    @Test
    @DisplayName("durations are H:MM:SS.mmm with no locale near the digits")
    void durations() {
        assertAll(
                () -> assertEquals("0:00:00.000", DisplayText.duration(0)),
                () -> assertEquals("0:00:00.999", DisplayText.duration(999)),
                () -> assertEquals("0:00:01.000", DisplayText.duration(1_000)),
                () -> assertEquals("0:01:00.000", DisplayText.duration(60_000)),
                () -> assertEquals("1:02:03.456", DisplayText.duration(3_723_456)),
                () -> assertEquals("26:00:00.001", DisplayText.duration(93_600_001)));
    }

    @Test
    @DisplayName("a run being executed with no attempt yet recorded is running")
    void executingBeforeTheFirstAttempt() throws IOException {
        RunLayout layout =
                dirs.recordedRun("run-0011", RunDirectories.CREATED, RunDirectories.cometParams());
        assertAll(
                () ->
                        assertEquals(
                                RunCondition.RUNNING,
                                ProvenanceReader.condition(dirs.store().read(layout), true)),
                () ->
                        assertEquals(
                                RunCondition.NOT_STARTED,
                                ProvenanceReader.condition(dirs.store().read(layout), false)));
    }
}
