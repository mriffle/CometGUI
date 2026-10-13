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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.events.EventLogDefectKind;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every state a run directory can be in opens, and opens as what it is ({@code P13-5}): unsealed,
 * unreadable, torn, failed, cancelled -- never an empty run, and with no file left open.
 */
class ProvenanceStatesTest {

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

    /** A run that crashed while Comet ran: run.json and events.log, no provenance.json. */
    static RunLayout unsealed(RunDirectories dirs) throws IOException {
        RunLayout layout =
                dirs.recordedRun("run-0002", RunDirectories.CREATED, RunDirectories.cometParams());
        dirs.attempt(layout, RunDirectories.STARTED, AttemptOutcome.RUNNING, null);
        RunDirectories.events(
                layout,
                Instant.parse("2026-10-13T09:00:02Z"),
                List.of(
                        event(
                                ProvenanceEventType.RUN_STARTED,
                                "run.id",
                                "run-0002",
                                "attempt",
                                "1"),
                        event(
                                ProvenanceEventType.STAGE_STARTED,
                                "stage",
                                "run-comet",
                                "state",
                                "running"),
                        event(
                                ProvenanceEventType.TOOL_INVOKED,
                                "stage",
                                "comet-01",
                                "tool",
                                "comet",
                                "tool.version",
                                "2026.03.0",
                                "exit",
                                "0",
                                "status",
                                "completed")));
        return layout;
    }

    @Test
    @DisplayName("an unsealed run is shown from run.json and events.log, and says so")
    void unsealedRun() throws IOException {
        RunLayout layout = unsealed(dirs);

        LoadedRun loaded = LoadedRunViewTest.reader().load(layout);

        ProvenanceView view = loaded.view();
        ProvenanceView.Summary summary = view.summary();
        assertAll(
                () -> assertEquals(RecordState.NO_SEALED_RECORD, loaded.state()),
                () -> assertEquals(Optional.empty(), loaded.manifest()),
                () -> assertFalse(loaded.canExport()),
                () -> assertEquals("run-0002", summary.runId()),
                () -> assertEquals("project-p13", summary.projectId()),
                () -> assertEquals("running", summary.status()),
                () -> assertEquals(RecordState.NO_SEALED_RECORD, summary.recordState()),
                () -> assertEquals("2026-10-13T09:00:01.000Z", summary.start()),
                () -> assertEquals(Optional.empty(), summary.end()),
                () -> assertEquals(Optional.empty(), summary.duration()),
                () -> assertEquals(Optional.empty(), summary.application()),
                () -> assertEquals(List.of(), view.tools()),
                () -> assertEquals(List.of(), view.files()),
                () -> assertEquals(List.of(), view.logs()),
                () -> assertEquals(List.of(), view.timeline().executions()),
                () ->
                        assertEquals(
                                List.of("run.started", "stage.started", "tool.invoked"),
                                view.timeline().events().stream()
                                        .map(ProvenanceView.Event::type)
                                        .toList()),
                () ->
                        assertEquals(
                                List.of(
                                        "exit = 0",
                                        "stage = comet-01",
                                        "status = completed",
                                        "tool = comet",
                                        "tool.version = 2026.03.0"),
                                view.timeline().events().get(2).payload()),
                () ->
                        assertEquals(
                                List.of(
                                        new ProvenanceView.Warning(
                                                WarningSource.NO_SEALED_RECORD,
                                                layout.provenanceJsonFile().toString(),
                                                "this run has no sealed provenance record: it"
                                                        + " crashed before finalising, or is still"
                                                        + " running, so it is shown from run.json"
                                                        + " and events.log alone")),
                                view.warnings()));
        ProvenanceView.Parameters parameters = view.parameters();
        assertAll(
                () ->
                        assertEquals(
                                DiskOutcome.MATCHES,
                                parameters.cometParams().orElseThrow().check(),
                                "the archived file named by run.json, checked against run.json"),
                () -> assertEquals(Optional.of("2026.03.0"), parameters.cometRelease()),
                () ->
                        assertEquals(
                                new ProvenanceView.Parameter(
                                        "num_threads",
                                        "8",
                                        Optional.empty(),
                                        Optional.of("0"),
                                        true,
                                        Optional.empty(),
                                        false),
                                LoadedRunViewTest.row(parameters, "num_threads"),
                                "no origin was recorded, so none is shown"),
                () -> assertEquals(Optional.empty(), parameters.percolatorSettingsFile()));
        NothingToExportException refused =
                assertThrows(
                        NothingToExportException.class,
                        () -> loaded.exportJson(tmp.resolve("out.json"), false));
        assertEquals(
                "run 20261013T090000Z-run-0002 has no sealed provenance record (its"
                        + " provenance.json does not exist), so there is nothing to export or"
                        + " verify",
                refused.getMessage());
        assertThrows(
                NothingToExportException.class,
                () -> loaded.exportRst(tmp.resolve("out.rst"), false));
        assertThrows(NothingToExportException.class, () -> loaded.verifyAgainstDisk(() -> false));
        assertFalse(Files.exists(tmp.resolve("out.json")));
        assertFalse(Files.exists(tmp.resolve("out.rst")));
    }

    @Test
    @DisplayName("an unsealed run whose attempt failed shows its end, duration and outcome")
    void unsealedFailedAttempt() throws IOException {
        RunLayout layout =
                dirs.recordedRun("run-0004", RunDirectories.CREATED, RunDirectories.cometParams());
        dirs.attempt(layout, RunDirectories.STARTED, AttemptOutcome.FAILED, RunDirectories.ENDED);
        RunDirectories.events(
                layout,
                RunDirectories.STARTED,
                List.of(event(ProvenanceEventType.RUN_STARTED, "run.id", "run-0004")));

        ProvenanceView view = LoadedRunViewTest.reader().load(layout).view();

        assertAll(
                () -> assertEquals("failed", view.summary().status()),
                () -> assertEquals("2026-10-13T09:00:01.000Z", view.summary().start()),
                () -> assertEquals(Optional.of("2026-10-13T09:12:35.567Z"), view.summary().end()),
                () -> assertEquals(Optional.of("0:12:34.567"), view.summary().duration()),
                () ->
                        assertEquals(
                                List.of(WarningSource.NO_SEALED_RECORD, WarningSource.RUN_OUTCOME),
                                view.warnings().stream()
                                        .map(ProvenanceView.Warning::source)
                                        .toList()),
                () ->
                        assertEquals(
                                "the run failed; outputs it left are marked partial",
                                view.warnings().get(1).message()));
    }

    @Test
    @DisplayName("a run prepared and never started: created time, 'not started', no event log")
    void neverStarted() throws IOException {
        RunLayout layout =
                dirs.recordedRun("run-0005", RunDirectories.CREATED, RunDirectories.cometParams());

        ProvenanceView view = LoadedRunViewTest.reader().load(layout).view();

        assertAll(
                () -> assertEquals("not started", view.summary().status()),
                () -> assertEquals("2026-10-13T09:00:00.000Z", view.summary().start()),
                () -> assertEquals(Optional.empty(), view.summary().end()),
                () ->
                        assertEquals(
                                List.of(
                                        WarningSource.NO_SEALED_RECORD,
                                        WarningSource.EVENT_LOG_UNREADABLE),
                                view.warnings().stream()
                                        .map(ProvenanceView.Warning::source)
                                        .toList()));
    }

    @Test
    @DisplayName("with neither record nor run.json, the run id comes from the event log")
    void eventsAlone() throws IOException {
        RunLayout layout = unsealed(dirs);
        Files.delete(layout.runFile());

        ProvenanceView view = LoadedRunViewTest.reader().load(layout).view();

        assertAll(
                () -> assertEquals("run-0002", view.summary().runId()),
                () -> assertEquals("unknown", view.summary().projectId()),
                () -> assertEquals("unknown", view.summary().status()),
                () -> assertEquals("unknown", view.summary().start()),
                () -> assertEquals(3, view.timeline().events().size()),
                () -> assertEquals(Optional.empty(), view.parameters().cometParams()),
                () ->
                        assertEquals(
                                List.of(
                                        WarningSource.NO_SEALED_RECORD,
                                        WarningSource.RUN_JSON_UNREADABLE),
                                view.warnings().stream()
                                        .map(ProvenanceView.Warning::source)
                                        .toList()));
    }

    @Test
    @DisplayName("with nothing naming the run at all, its id is 'unknown', never blank")
    void nothingNamesTheRun() throws IOException {
        RunLayout layout =
                dirs.recordedRun("run-0006", RunDirectories.CREATED, RunDirectories.cometParams());
        Files.delete(layout.runFile());

        ProvenanceView view = LoadedRunViewTest.reader().load(layout).view();

        assertEquals("unknown", view.summary().runId());
        assertEquals(List.of("this run lists no archived comet.params"), view.parameters().notes());
    }

    @Test
    @DisplayName("a record that is not JSON is 'record unreadable' with the reader's position")
    void unparsableRecord() throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        String whole = Files.readString(run.layout().provenanceJsonFile(), StandardCharsets.UTF_8);
        Files.writeString(
                run.layout().provenanceJsonFile(), whole.substring(0, 200), StandardCharsets.UTF_8);

        LoadedRun loaded = LoadedRunViewTest.reader().load(run.layout());

        String problem = loaded.recordProblem().orElseThrow();
        assertAll(
                () -> assertEquals(RecordState.RECORD_UNREADABLE, loaded.state()),
                () -> assertEquals(Optional.empty(), loaded.manifest()),
                () ->
                        assertTrue(
                                problem.startsWith(
                                        "the provenance manifest is not well-formed JSON: "),
                                problem),
                () -> assertTrue(problem.contains("line"), problem),
                () ->
                        assertEquals(
                                new ProvenanceView.Warning(
                                        WarningSource.RECORD_UNREADABLE,
                                        run.layout().provenanceJsonFile().toString(),
                                        "the provenance record could not be read: " + problem),
                                loaded.view().warnings().get(0)),
                () ->
                        assertEquals(
                                "completed",
                                loaded.view().summary().status(),
                                "shown from run.json, not as an empty run"),
                () -> assertEquals("run-0001", loaded.view().summary().runId()),
                () -> assertEquals(4, loaded.view().timeline().events().size()),
                () -> assertTrue(loaded.view().parameters().cometParams().isPresent()));
        NothingToExportException refused =
                assertThrows(
                        NothingToExportException.class,
                        () -> loaded.exportRst(tmp.resolve("out.rst"), false));
        assertTrue(
                refused.getMessage()
                        .startsWith(
                                "run 20261013T090000Z-run-0001 has a provenance record that could"
                                        + " not be read, so there is nothing to export or verify:"
                                        + " the provenance manifest is not well-formed JSON"),
                refused.getMessage());
    }

    @Test
    @DisplayName("a record the model refuses names the member, not the value")
    void invalidRecord() throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        String whole = Files.readString(run.layout().provenanceJsonFile(), StandardCharsets.UTF_8);
        String broken = whole.replaceFirst("\"status\": \"completed\"", "\"status\": \"finished\"");
        assertFalse(whole.equals(broken), "the injection must change the document");
        Files.writeString(run.layout().provenanceJsonFile(), broken, StandardCharsets.UTF_8);

        LoadedRun loaded = LoadedRunViewTest.reader().load(run.layout());

        String problem = loaded.recordProblem().orElseThrow();
        assertAll(
                () -> assertEquals(RecordState.RECORD_UNREADABLE, loaded.state()),
                () -> assertTrue(problem.contains("\"run.status\""), problem),
                () -> assertFalse(problem.contains("finished"), problem));
    }

    @Test
    @DisplayName("a torn event log keeps every whole event and reports the tear as a warning")
    void tornEventLog() throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        long before = Files.size(run.layout().eventLogFile());
        Files.writeString(
                run.layout().eventLogFile(),
                "{\"seq\":5,\"time\":\"2026-10-13T09:1",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);

        LoadedRun loaded = LoadedRunViewTest.reader().load(run.layout());

        assertEquals(4, loaded.view().timeline().events().size());
        assertEquals(EventLogDefectKind.TORN_FINAL_LINE, loaded.events().defects().get(0).kind());
        ProvenanceView.Warning tear =
                loaded.view().warnings().stream()
                        .filter(warning -> warning.source() == WarningSource.EVENT_LOG_DEFECT)
                        .findFirst()
                        .orElseThrow();
        assertAll(
                () -> assertEquals("events.log line 5", tear.subject()),
                () ->
                        assertTrue(
                                tear.message()
                                        .startsWith(
                                                "a torn final line at line 5, byte "
                                                        + before
                                                        + ": "),
                                tear.message()),
                () -> assertEquals(RecordState.SEALED, loaded.state()));
    }

    @Test
    @DisplayName(
            "a failed run shows the failure in summary, timeline and warnings, outputs partial")
    void failedRun() throws IOException {
        CompleteRun run =
                CompleteRun.create(dirs, SecretRedactor.patternsOnly(), ProvenanceStatus.FAILED);

        ProvenanceView view = LoadedRunViewTest.reader().load(run.layout()).view();

        ProvenanceView.Execution percolator = view.timeline().executions().get(2);
        assertAll(
                () -> assertEquals("failed", view.summary().status()),
                () -> assertEquals("percolator", percolator.tool()),
                () -> assertEquals(1, percolator.exitCode()),
                () -> assertEquals("failed", percolator.status()),
                () ->
                        assertEquals(
                                List.of(
                                        false, false, false, false, false, false, false, true,
                                        false, true),
                                view.files().stream().map(ProvenanceView.File::partial).toList()),
                () -> assertEquals("partial", view.files().get(7).status()),
                () ->
                        assertEquals(
                                new ProvenanceView.Warning(
                                        WarningSource.RUN_OUTCOME,
                                        "run",
                                        "the run failed; outputs it left are marked partial"),
                                view.warnings().get(0)),
                () ->
                        assertEquals(
                                new ProvenanceView.Warning(
                                        WarningSource.PARTIAL_FILE,
                                        run.pin2().toString(),
                                        "the output file "
                                                + run.pin2()
                                                + " (pin) is recorded as partial"),
                                view.warnings().get(1)));
    }

    @Test
    @DisplayName("a cancelled run shows the cancellation, exit 143, outputs partial")
    void cancelledRun() throws IOException {
        CompleteRun run =
                CompleteRun.create(dirs, SecretRedactor.patternsOnly(), ProvenanceStatus.CANCELLED);

        ProvenanceView view = LoadedRunViewTest.reader().load(run.layout()).view();

        assertAll(
                () -> assertEquals("cancelled", view.summary().status()),
                () -> assertEquals(143, view.timeline().executions().get(2).exitCode()),
                () -> assertEquals("cancelled", view.timeline().executions().get(2).status()),
                () ->
                        assertEquals(
                                "the run was cancelled; outputs it left are marked partial",
                                view.warnings().get(0).message()),
                () ->
                        assertEquals(
                                2,
                                view.warnings().stream()
                                        .filter(w -> w.source() == WarningSource.PARTIAL_FILE)
                                        .count()));
    }

    @Test
    @DisplayName("a run directory that does not exist is an error, not an empty run")
    void noSuchRun() {
        RunLayout gone = new RunLayout(tmp.resolve("project/runs/20261013T090000Z-gone"));
        assertThrows(NoSuchFileException.class, () -> LoadedRunViewTest.reader().load(gone));
    }

    @Test
    @DisplayName("a run with no run.json and no events.log still opens from its record")
    void recordAlone() throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        Files.delete(run.layout().runFile());
        Files.delete(run.layout().eventLogFile());

        LoadedRun loaded = LoadedRunViewTest.reader().load(run.layout());

        List<ProvenanceView.Warning> warnings = loaded.view().warnings();
        assertAll(
                () -> assertEquals(RecordState.SEALED, loaded.state()),
                () -> assertEquals("completed", loaded.view().summary().status()),
                () -> assertEquals(WarningSource.RUN_JSON_UNREADABLE, warnings.get(0).source()),
                () -> assertEquals(run.layout().runFile().toString(), warnings.get(0).subject()),
                () -> assertEquals(WarningSource.EVENT_LOG_UNREADABLE, warnings.get(1).source()),
                () -> assertEquals("this run has no events.log", warnings.get(1).message()),
                () -> assertTrue(loaded.events().events().isEmpty()));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("nothing the load or the list read is open afterwards (/proc/self/fd)")
    void holdsNoDescriptor() throws IOException {
        CompleteRun run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
        RunLayout crashed = unsealed(dirs);
        RunLayout layout = run.layout();
        List<Path> files =
                List.of(
                        layout.provenanceJsonFile(),
                        layout.eventLogFile(),
                        layout.runFile(),
                        layout.cometParamsFile(),
                        run.settingsFile(),
                        crashed.eventLogFile(),
                        crashed.runFile(),
                        crashed.cometParamsFile(),
                        dirs.project().runsDirectory());
        // Control: the scan sees a descriptor that really is open.
        try (var held = Files.newInputStream(layout.provenanceJsonFile())) {
            assertEquals(1L, descriptorsFor(layout.provenanceJsonFile()));
        }

        ProvenanceReader reader = LoadedRunViewTest.reader();
        LoadedRun loaded = reader.load(layout);
        reader.load(crashed);
        reader.list(dirs.project(), Set.of());
        loaded.verifyAgainstDisk(() -> false);

        for (Path file : files) {
            assertEquals(0L, descriptorsFor(file), file + " is still open");
        }
        assertEquals(RecordState.SEALED, loaded.state());
    }

    static long descriptorsFor(Path path) throws IOException {
        Path real = path.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors =
                Files.newDirectoryStream(Paths.get("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                Path refersTo;
                try {
                    refersTo = Files.readSymbolicLink(descriptor);
                } catch (NoSuchFileException | NotLinkException closedMeanwhile) {
                    continue;
                }
                if (real.equals(refersTo)) {
                    count++;
                }
            }
        }
        return count;
    }
}
