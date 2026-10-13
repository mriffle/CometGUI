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

import static org.cometgui.workflow.provenance.RunDirectories.event;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every warning source the read model knows, planted and found. The view and states tests cover the
 * record's own sources; this class covers the event log's and the run directory's, and its census
 * test proves that between them every {@link WarningSource} constant is produced by some planted
 * source -- a constant nothing produces is a warning no scientist would ever see.
 */
class ProvenanceWarningsTest {

    @TempDir private Path tmp;

    private RunDirectories dirs;

    @BeforeEach
    void create() throws IOException {
        dirs = RunDirectories.create(tmp);
    }

    @AfterEach
    void close() throws IOException {
        dirs.close();
        for (RunDirectories other : opened) {
            other.close();
        }
    }

    private static List<ProvenanceView.Warning> of(
            List<ProvenanceView.Warning> warnings, WarningSource source) {
        return warnings.stream().filter(warning -> warning.source() == source).toList();
    }

    /** A complete run with events a failed step, a warning and two Limelight failures left. */
    private static ProvenanceView withEventWarnings(RunDirectories dirs) throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        RunDirectories.events(
                run.layout(),
                Instant.parse("2026-10-13T09:20:00Z"),
                List.of(
                        event(
                                ProvenanceEventType.STAGE_FINISHED,
                                "stage",
                                "run-percolator",
                                "state",
                                "failed",
                                "message",
                                "invocation percolator exited with code 1"),
                        event(
                                ProvenanceEventType.WARNING_RAISED,
                                "stage",
                                "merge-pin",
                                "message",
                                "3 PSMs had no protein"),
                        event(ProvenanceEventType.WARNING_RAISED, "stage", "merge-pin"),
                        event(
                                ProvenanceEventType.LIMELIGHT_CONVERSION_FAILED,
                                "run.id",
                                CompleteRun.RUN_ID,
                                "limelight.failure",
                                "no-schema",
                                "message",
                                "the converter found no Limelight schema"),
                        event(
                                ProvenanceEventType.LIMELIGHT_UPLOAD_FAILED,
                                "run.id",
                                CompleteRun.RUN_ID,
                                "upload.failure",
                                "refused-by-server",
                                "upload.http-status",
                                "403"),
                        event(
                                ProvenanceEventType.LIMELIGHT_UPLOAD_FAILED,
                                "run.id",
                                CompleteRun.RUN_ID,
                                "upload.failure",
                                "refused-by-server",
                                "limelight.server-message",
                                "project 12 is closed")));
        return LoadedRunViewTest.reader().load(run.layout()).view();
    }

    @Test
    @DisplayName("a step failure, warning.raised and Limelight failures from the log")
    void eventWarnings() throws IOException {
        List<ProvenanceView.Warning> warnings = withEventWarnings(dirs).warnings();
        assertEquals(
                List.of(
                        new ProvenanceView.Warning(
                                WarningSource.STEP_FAILURE,
                                "run-percolator",
                                "invocation percolator exited with code 1")),
                of(warnings, WarningSource.STEP_FAILURE));
        assertEquals(
                List.of(
                        new ProvenanceView.Warning(
                                WarningSource.WARNING_EVENT,
                                "warning.raised #6",
                                "3 PSMs had no protein"),
                        new ProvenanceView.Warning(
                                WarningSource.WARNING_EVENT,
                                "warning.raised #7",
                                "stage = merge-pin")),
                of(warnings, WarningSource.WARNING_EVENT));
        assertEquals(
                List.of(
                        new ProvenanceView.Warning(
                                WarningSource.LIMELIGHT_FAILURE,
                                "limelight.unconverted #8",
                                "the Limelight conversion did not succeed (no-schema): the"
                                        + " converter found no Limelight schema"),
                        new ProvenanceView.Warning(
                                WarningSource.LIMELIGHT_FAILURE,
                                "limelight.unuploaded #9",
                                "the Limelight upload did not succeed (refused-by-server): HTTP"
                                        + " status 403"),
                        new ProvenanceView.Warning(
                                WarningSource.LIMELIGHT_FAILURE,
                                "limelight.unuploaded #10",
                                "the Limelight upload did not succeed (refused-by-server):"
                                        + " project 12 is closed")),
                of(warnings, WarningSource.LIMELIGHT_FAILURE));
    }

    /** The Comet advisory and the two discrepancies, typed out. */
    private static final String COMET_ADVISORY =
            "This Comet was registered from a file on this machine.";

    private static final String COMET_DISCREPANCY =
            "Comet 2026.03.0: the artefact manifest claims PIN_OUTPUT (unverified evidence), but"
                    + " the probe run when this build was installed did not find it";

    private static final String PERCOLATOR_DISCREPANCY =
            "Percolator 3.07.1: the artefact manifest claims XML_DECOY_OUTPUT"
                    + " (inferred-from-artefact-bytes evidence), but the probe did not find it";

    /**
     * A complete run re-sealed with a Comet advisory and a Comet and a Percolator manifest/probe
     * discrepancy in its settings, as {@code ToolWarnings} records them.
     */
    private static ProvenanceView withToolWarningSettings(RunDirectories dirs) throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        ProvenanceManifest sealed = run.manifest();
        Map<String, String> settings = new TreeMap<>(sealed.settings());
        settings.put("comet.advisory.comet.local-binary-is-unverified", COMET_ADVISORY);
        settings.put("comet.probe-discrepancy.01", COMET_DISCREPANCY);
        settings.put("percolator.probe-discrepancy.01", PERCOLATOR_DISCREPANCY);
        RunDirectories.seal(
                run.layout(),
                ProvenanceManifest.current(
                        sealed.run(),
                        sealed.application(),
                        settings,
                        sealed.tools(),
                        sealed.files()),
                SecretRedactor.patternsOnly());
        return LoadedRunViewTest.reader().load(run.layout()).view();
    }

    @Test
    @DisplayName(
            "a Comet advisory is a version advisory; a Comet or Percolator discrepancy is a"
                    + " manifest/probe discrepancy, subject its key, in key order")
    void toolWarningSettings() throws IOException {
        List<ProvenanceView.Warning> warnings = withToolWarningSettings(dirs).warnings();
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        new ProvenanceView.Warning(
                                                WarningSource.VERSION_ADVISORY,
                                                "comet.local-binary-is-unverified",
                                                COMET_ADVISORY),
                                        new ProvenanceView.Warning(
                                                WarningSource.VERSION_ADVISORY,
                                                "xml-final",
                                                CompleteRun.ADVISORY)),
                                of(warnings, WarningSource.VERSION_ADVISORY)),
                () ->
                        assertEquals(
                                List.of(
                                        new ProvenanceView.Warning(
                                                WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                                "comet.probe-discrepancy.01",
                                                COMET_DISCREPANCY),
                                        new ProvenanceView.Warning(
                                                WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                                "percolator.probe-discrepancy.01",
                                                PERCOLATOR_DISCREPANCY)),
                                of(warnings, WarningSource.MANIFEST_PROBE_DISCREPANCY)));
    }

    private static ProvenanceView withChangedParameterFiles(RunDirectories dirs)
            throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        Files.writeString(
                run.layout().cometParamsFile(),
                "num_threads = 1\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        Files.delete(run.settingsFile());
        return LoadedRunViewTest.reader().load(run.layout()).view();
    }

    @Test
    @DisplayName("an archived parameter file changed or gone is a warning naming the file")
    void parameterFilesChanged() throws IOException {
        ProvenanceView view = withChangedParameterFiles(dirs);
        Path params =
                dirs.project()
                        .runsDirectory()
                        .resolve("20261013T090000Z-run-0001/parameters/comet.params");
        String recorded = view.parameters().cometParams().orElseThrow().recordedSha256();
        String found = RunDirectories.digests(params).sha256();
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        new ProvenanceView.Warning(
                                                WarningSource.PARAMETER_FILE_CHANGED,
                                                params.toString(),
                                                "the archived comet-params file no longer has the"
                                                        + " digests the run recorded: SHA-256"
                                                        + " recorded "
                                                        + recorded
                                                        + ", found "
                                                        + found),
                                        new ProvenanceView.Warning(
                                                WarningSource.PARAMETER_FILE_CHANGED,
                                                params.resolveSibling("percolator-settings.json")
                                                        .toString(),
                                                "the archived percolator-settings file is no longer"
                                                        + " on disk")),
                                of(view.warnings(), WarningSource.PARAMETER_FILE_CHANGED)),
                () ->
                        assertEquals(
                                DiskOutcome.DIFFERS,
                                view.parameters().cometParams().orElseThrow().check()),
                () ->
                        assertTrue(
                                view.parameters()
                                        .cometParams()
                                        .orElseThrow()
                                        .text()
                                        .orElseThrow()
                                        .endsWith("num_threads = 1\n"),
                                "the text shown is the file as it is now, flagged as changed"),
                () ->
                        assertEquals(
                                DiskOutcome.MISSING,
                                view.parameters().percolatorSettingsFile().orElseThrow().check()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                view.parameters().percolatorSettingsFile().orElseThrow().text()));
    }

    private static ProvenanceView withDamagedLog(RunDirectories dirs) throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        // Line 5 malformed (it ends in a newline but is not a record); line 6 skips to seq 9.
        Files.writeString(
                run.layout().eventLogFile(),
                "not a record\n"
                        + "{\"seq\":9,\"time\":\"2026-10-13T09:30:00.000Z\","
                        + "\"type\":\"warning.raised\","
                        + "\"payload\":{\"message\":\"late\"}}\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        return LoadedRunViewTest.reader().load(run.layout()).view();
    }

    @Test
    @DisplayName("a malformed line and a sequence gap are each a warning with line and byte")
    void eventLogDefects() throws IOException {
        List<ProvenanceView.Warning> defects =
                of(withDamagedLog(dirs).warnings(), WarningSource.EVENT_LOG_DEFECT);
        assertEquals(2, defects.size(), defects.toString());
        assertAll(
                () -> assertEquals("events.log line 5", defects.get(0).subject()),
                () ->
                        assertTrue(
                                defects.get(0).message().startsWith("a malformed line at line 5,"),
                                defects.get(0).message()),
                () -> assertEquals("events.log line 6", defects.get(1).subject()),
                () ->
                        assertTrue(
                                defects.get(1)
                                        .message()
                                        .startsWith("a gap in the sequence numbers at line 6,"),
                                defects.get(1).message()));
    }

    private static ProvenanceView withUnreadableEventLog(RunDirectories dirs) throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        Files.delete(run.layout().eventLogFile());
        Files.createDirectory(run.layout().eventLogFile());
        return LoadedRunViewTest.reader().load(run.layout()).view();
    }

    @Test
    @DisplayName("an event log that cannot be read at all is a warning, and the record still shows")
    void unreadableEventLog() throws IOException {
        ProvenanceView view = withUnreadableEventLog(dirs);
        List<ProvenanceView.Warning> warnings =
                of(view.warnings(), WarningSource.EVENT_LOG_UNREADABLE);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).subject().endsWith("events.log"), warnings.get(0).subject());
        assertEquals("completed", view.summary().status());
    }

    @Test
    @DisplayName("census: every warning source is produced by a planted source")
    void census() throws IOException {
        Set<WarningSource> seen = EnumSet.noneOf(WarningSource.class);
        List<ProvenanceView> views = new ArrayList<>();
        views.add(withEventWarnings(fresh("a")));
        views.add(withChangedParameterFiles(fresh("b")));
        views.add(withDamagedLog(fresh("c")));
        views.add(withUnreadableEventLog(fresh("d")));
        views.add(
                LoadedRunViewTest.reader().load(ProvenanceStatesTest.unsealed(fresh("e"))).view());
        views.add(failedWithUnreadableRecordAndRunJson(fresh("f")));
        views.add(withToolWarningSettings(fresh("g")));
        for (ProvenanceView view : views) {
            for (ProvenanceView.Warning warning : view.warnings()) {
                seen.add(warning.source());
            }
        }
        assertEquals(EnumSet.allOf(WarningSource.class), seen);
    }

    private final List<RunDirectories> opened = new ArrayList<>();

    private RunDirectories fresh(String name) throws IOException {
        RunDirectories created = RunDirectories.create(Files.createDirectory(tmp.resolve(name)));
        opened.add(created);
        return created;
    }

    /** A failed run whose provenance.json does not parse and whose run.json is gone. */
    private static ProvenanceView failedWithUnreadableRecordAndRunJson(RunDirectories other)
            throws IOException {
        CompleteRun failed =
                CompleteRun.create(other, SecretRedactor.patternsOnly(), ProvenanceStatus.FAILED);
        ProvenanceView sealedView = LoadedRunViewTest.reader().load(failed.layout()).view();
        RunLayout layout = failed.layout();
        Files.writeString(layout.provenanceJsonFile(), "[", StandardCharsets.UTF_8);
        Files.delete(layout.runFile());
        ProvenanceView broken = LoadedRunViewTest.reader().load(layout).view();
        List<ProvenanceView.Warning> both = new ArrayList<>(sealedView.warnings());
        both.addAll(broken.warnings());
        return new ProvenanceView(
                broken.summary(),
                broken.tools(),
                broken.files(),
                broken.parameters(),
                broken.timeline(),
                broken.logs(),
                both);
    }

    @Test
    @DisplayName("the source words are the ones the section shows")
    void words() {
        assertEquals(
                List.of(
                        "no sealed record",
                        "record unreadable",
                        "run.json unreadable",
                        "event log unreadable",
                        "event log defect",
                        "run outcome",
                        "step failure",
                        "partial file",
                        "version advisory",
                        "manifest/probe discrepancy",
                        "skipped version",
                        "option not emitted",
                        "weights fallback",
                        "tool warning",
                        "warning event",
                        "limelight failure",
                        "parameter file changed"),
                List.of(WarningSource.values()).stream().map(WarningSource::word).toList());
        assertEquals(
                List.of("sealed record", "no sealed record", "record unreadable"),
                List.of(RecordState.values()).stream().map(RecordState::word).toList());
        assertEquals(
                List.of("matches", "differs", "missing", "unreadable"),
                List.of(DiskOutcome.values()).stream().map(DiskOutcome::word).toList());
    }
}
