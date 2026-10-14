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

package org.cometgui.ui.viewmodel.provenance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.ProvenanceViews;
import org.cometgui.ui.testing.ScriptedProvenance;
import org.cometgui.workflow.provenance.DiskOutcome;
import org.cometgui.workflow.provenance.DiskVerification;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RecordState;
import org.cometgui.workflow.provenance.RunCondition;
import org.cometgui.workflow.provenance.RunListing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Provenance section's view-model over a scripted port and two queued executors: the run list
 * with every condition in words, the seven parts laid out exactly, every action's success and
 * failure, stale answers dropped, and -- in every test -- the port reached only while the
 * background queue runs, nothing published until the interface queue runs.
 *
 * <p>Every expected text is typed out here; none is built by the code under test.
 */
class ProvenanceViewModelTest {

    private static final RunListing COMPLETED =
            ProvenanceViews.listing("20261014T080000Z-r1", RunCondition.COMPLETED, true);

    private static final RunListing RUNNING =
            ProvenanceViews.listing("20261014T090000Z-r2", RunCondition.RUNNING, false);

    private static final RunListing DAMAGED =
            ProvenanceViews.unreadable("20261014T100000Z-r3", "run.json: no such file");

    private final Queue background = new Queue();

    private final Queue ui = new Queue();

    private final Editors.ScriptedChooser chooser = new Editors.ScriptedChooser();

    private ScriptedProvenance port;

    private ProvenanceViewModel section;

    @BeforeEach
    void build() {
        port = new ScriptedProvenance(() -> background.running);
        section = new ProvenanceViewModel(port, chooser, background, ui);
    }

    @AfterEach
    void everyPortCallWasMadeOnTheBackgroundExecutor() {
        List<ScriptedProvenance.Call> outside =
                port.calls().stream().filter(call -> !call.allowed()).toList();
        assertEquals(
                List.of(),
                outside,
                "the port was called outside the background executor -- on the interface thread,"
                        + " which then stalls for as long as the call blocks");
    }

    // ----------------------------------------------------------------------------- fixtures --

    private void settle() {
        while (background.pending() + ui.pending() > 0) {
            background.drain();
            ui.drain();
        }
    }

    private static ScriptedProvenance.Record sealed(RunListing run) {
        return new ScriptedProvenance.Record(
                run, ProvenanceViews.failedRun(), Optional.empty(), true);
    }

    /** Lists the three runs and opens the first, a sealed failed run. */
    private void openSealed() {
        port.runs(() -> List.of(COMPLETED, RUNNING, DAMAGED)).opens(sealed(COMPLETED));
        section.refresh();
        settle();
        assertTrue(section.choose(0));
        settle();
    }

    private List<String> methods() {
        return port.calls().stream().map(ScriptedProvenance.Call::method).toList();
    }

    private static DiskVerification.Check check(
            String role, String path, DiskOutcome outcome, String found) {
        return new DiskVerification.Check(
                "output",
                role,
                path,
                Path.of("verified", role),
                outcome,
                "aa",
                "bb",
                Optional.ofNullable(found),
                Optional.ofNullable(found),
                outcome == DiskOutcome.UNREADABLE
                        ? Optional.of("it is a directory")
                        : Optional.empty());
    }

    // ------------------------------------------------------------------------- the run list --

    @Nested
    @DisplayName("the run list")
    class RunList {

        @Test
        @DisplayName("nothing is read until refresh, and then only on the background executor")
        void nothingIsReadUntilRefresh() {
            assertAll(
                    () -> assertEquals(List.of(), port.calls()),
                    () ->
                            assertEquals(
                                    "The project's runs have not been read yet.",
                                    section.runsStatusProperty().get()),
                    () ->
                            assertEquals(
                                    "No run is chosen. Choose one of the project's runs above to"
                                            + " see its provenance.",
                                    section.recordTextProperty().get()),
                    () -> assertEquals(-1, section.chosenIndexProperty().get()),
                    () -> assertFalse(section.recordShownProperty().get()),
                    () -> assertFalse(section.exportEnabledProperty().get()),
                    () -> assertFalse(section.verifyEnabledProperty().get()));

            port.runs(() -> List.of(COMPLETED, RUNNING, DAMAGED));
            section.refresh();
            assertEquals(List.of(), port.calls(), "nothing is read on the calling thread");
            assertEquals("Reading the project's runs.", section.runsStatusProperty().get());
            background.drain();
            assertEquals(List.of("runs"), methods());
            assertEquals(List.of(), section.runsProperty().get(), "not before the ui queue runs");
            ui.drain();
            assertAll(
                    () ->
                            assertEquals(
                                    List.of(
                                            "r1 -- created 2026-10-14T08:00:00Z, completed, sealed"
                                                    + " record",
                                            "r2 -- created 2026-10-14T08:00:00Z, running, no"
                                                    + " sealed record",
                                            "20261014T100000Z-r3 -- run.json unreadable: run.json:"
                                                    + " no such file"),
                                    section.runsProperty().get()),
                    () ->
                            assertEquals(
                                    "This project has 3 runs.",
                                    section.runsStatusProperty().get()));
        }

        @Test
        @DisplayName("no runs, one run, and a list that cannot be read, each said in words")
        void countsAndFailures() {
            section.refresh();
            settle();
            assertEquals("This project has no runs yet.", section.runsStatusProperty().get());
            port.runs(() -> List.of(COMPLETED));
            section.refresh();
            settle();
            assertEquals("This project has 1 run.", section.runsStatusProperty().get());
            port.runs(
                    () -> {
                        throw new IOException("runs/ is not a directory");
                    });
            section.refresh();
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "The project's runs could not be read: runs/ is not a"
                                            + " directory",
                                    section.runsStatusProperty().get()),
                    () ->
                            assertEquals(
                                    1,
                                    section.runsProperty().get().size(),
                                    "the last list read stays"));
            port.runs(
                    () -> {
                        throw new IllegalStateException();
                    });
            section.refresh();
            settle();
            assertEquals(
                    "The project's runs could not be read: IllegalStateException",
                    section.runsStatusProperty().get());
        }

        @Test
        @DisplayName("an older listing that arrives after a newer one is dropped, either way")
        void anOlderListingIsDropped() {
            port.runs(() -> List.of(COMPLETED));
            section.refresh();
            port.runs(() -> List.of(COMPLETED, RUNNING));
            section.refresh();
            background.runLast();
            ui.drain();
            assertEquals(2, section.runsProperty().get().size());
            port.runs(() -> List.of(COMPLETED));
            background.drain();
            ui.drain();
            assertEquals(
                    2,
                    section.runsProperty().get().size(),
                    "the first refresh's answer arrived last and must not replace the newer list");

            port.runs(
                    () -> {
                        throw new IOException("late failure");
                    });
            section.refresh();
            port.runs(() -> List.of(RUNNING));
            section.refresh();
            background.runLast();
            ui.drain();
            background.drain();
            ui.drain();
            assertEquals("This project has 1 run.", section.runsStatusProperty().get());
        }

        @Test
        @DisplayName("a position that names no run chooses nothing and reads nothing")
        void aPositionThatNamesNoRun() {
            port.runs(() -> List.of(COMPLETED));
            section.refresh();
            settle();
            assertAll(
                    () -> assertFalse(section.choose(-1)),
                    () -> assertFalse(section.choose(1)),
                    () -> assertEquals(List.of("runs"), methods()),
                    () -> assertEquals(-1, section.chosenIndexProperty().get()));
        }

        @Test
        @DisplayName(
                "a refresh keeps the chosen run, reads it again only when what the list says"
                        + " about it changed, and says when it has gone")
        void theChosenRunAcrossRefreshes() {
            port.runs(() -> List.of(RUNNING)).opens(sealed(RUNNING));
            section.refresh();
            settle();
            section.choose(0);
            settle();
            section.refresh();
            settle();
            assertEquals(List.of("runs", "open", "runs"), methods(), "unchanged: not read again");

            RunListing ended =
                    new RunListing(
                            RUNNING.directoryName(),
                            RUNNING.directory(),
                            RUNNING.runId(),
                            RUNNING.created(),
                            RunCondition.COMPLETED,
                            true,
                            Optional.empty());
            port.runs(() -> List.of(COMPLETED, ended));
            section.refresh();
            settle();
            assertAll(
                    () -> assertEquals(List.of("runs", "open", "runs", "runs", "open"), methods()),
                    () -> assertEquals(1, section.chosenIndexProperty().get()),
                    () -> assertTrue(section.recordShownProperty().get()));

            port.runs(() -> List.of(COMPLETED));
            section.refresh();
            settle();
            assertAll(
                    () -> assertEquals(-1, section.chosenIndexProperty().get()),
                    () -> assertFalse(section.recordShownProperty().get()),
                    () -> assertEquals(List.of(), section.toolsProperty().get()),
                    () ->
                            assertEquals(
                                    "The run that was chosen, 20261014T090000Z-r2, is no longer"
                                            + " listed.",
                                    section.recordTextProperty().get()));
        }
    }

    // ---------------------------------------------------------------------------- the parts --

    @Nested
    @DisplayName("the seven parts")
    class Parts {

        @Test
        @DisplayName("a sealed failed run: every part laid out, exactly")
        void everyPart() {
            port.runs(() -> List.of(COMPLETED)).opens(sealed(COMPLETED));
            section.refresh();
            settle();
            section.choose(0);
            assertEquals(
                    "Reading the chosen run's provenance.", section.recordTextProperty().get());
            background.drain();
            assertEquals(List.of("runs", "open"), methods());
            assertEquals(
                    List.of(),
                    section.toolsProperty().get(),
                    "nothing is shown before the ui queue runs");
            ui.drain();
            assertAll(
                    () -> assertEquals(0, section.chosenIndexProperty().get()),
                    () -> assertTrue(section.recordShownProperty().get()),
                    () ->
                            assertEquals(
                                    "Sealed record: this run's provenance.json was read whole and"
                                            + " parsed.",
                                    section.recordTextProperty().get()),
                    () ->
                            assertEquals(
                                    "Run r1 of project cometgui-default\n"
                                            + "Status: failed\n"
                                            + "Record: sealed record\n"
                                            + "Started: 2026-10-14T08:00:00Z\n"
                                            + "Ended: 2026-10-14T08:00:05.250Z\n"
                                            + "Duration: 0:00:05.250\n"
                                            + "Tool versions: comet 2026.03.0, percolator 3.07.1\n"
                                            + "Input files: 1; output files: 1\n"
                                            + "Application: CometGUI 0.1.0 (build abc1234), Linux"
                                            + " 6.8, amd64, Java 25.0.1, locale en-GB, format"
                                            + " locale en-GB, time zone Europe/London",
                                    section.summaryTextProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "1. comet 2026.03.0 -- managed, release tag"
                                                    + " v2026.03.0, stage comet-01\n"
                                                    + "Path: /cache/comet\n"
                                                    + "MD5: 0123456789abcdef0123456789abcdef\n"
                                                    + "SHA-256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n"
                                                    + "Capabilities: pin-output, pepxml-output\n"
                                                    + "Artefact: https://example.org/comet"
                                                    + " sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n"
                                                    + "Warnings: advisory one",
                                            "2. percolator 3.07.1 -- local\n"
                                                    + "Path: /cache/percolator\n"
                                                    + "MD5: fedcba9876543210fedcba9876543210\n"
                                                    + "SHA-256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                                    + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\n"
                                                    + "Capabilities: none recorded"),
                                    section.toolsProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "input spectrum\n"
                                                    + "Status: completed\n"
                                                    + "Path: /data/a.mzML\n"
                                                    + "Size: 1234567 bytes; modified"
                                                    + " 2026-10-01T00:00:00Z\n"
                                                    + "MD5: 11111111111111111111111111111111\n"
                                                    + "SHA-256: 11111111111111111111111111111111"
                                                    + "11111111111111111111111111111111",
                                            "PARTIAL -- output pepxml\n"
                                                    + "Status: partial (incomplete: the stage that"
                                                    + " wrote it did not complete)\n"
                                                    + "Path: /run/outputs/comet/a.pep.xml\n"
                                                    + "Size: 42 bytes; modified"
                                                    + " 2026-10-14T08:00:05Z\n"
                                                    + "MD5: 22222222222222222222222222222222\n"
                                                    + "SHA-256: 22222222222222222222222222222222"
                                                    + "22222222222222222222222222222222"),
                                    section.filesProperty().get()),
                    () ->
                            assertEquals(
                                    "# comet_version 2026.03.0\nnum_threads = 4\n",
                                    section.cometParamsTextProperty().get()),
                    () ->
                            assertEquals(
                                    "The archived Comet parameter file,"
                                            + " /run/parameters/comet.params: recorded SHA-256"
                                            + " cccc; on disk now it matches. Written for Comet"
                                            + " 2026.03.0.",
                                    section.cometParamsStatusProperty().get()),
                    () ->
                            assertEquals(
                                    "This run lists no archived Percolator settings file.\n"
                                            + "percolator.seed = 1",
                                    section.percolatorSettingsTextProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "num_threads = 4; origin: preset; release default: 0"
                                                    + " (differs); preset value: 8 (differs)",
                                            "decoy_search = 1; origin: not recorded; release"
                                                    + " default: not known"),
                                    section.parametersProperty().get()),
                    () ->
                            assertEquals(
                                    "Preset 1: Lab threads (lab-threads, user, written for Comet"
                                            + " 2026.03.0, preset schema 1)\n"
                                            + "  set num_threads = 8",
                                    section.presetsTextProperty().get()),
                    () -> assertEquals("a note", section.parameterNotesProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "comet-01: comet 2026.03.0 -- failed, exit status 134\n"
                                                    + "Started 2026-10-14T08:00:01Z, ended"
                                                    + " 2026-10-14T08:00:05Z, took 0:00:04.000\n"
                                                    + "Command: "
                                                    + ProvenanceViews.COMET_COMMAND
                                                    + "\nWorking directory: /run\n"
                                                    + "Environment: LANG=C.UTF-8",
                                            "percolator: percolator 3.07.1 -- completed, exit"
                                                    + " status 0\n"
                                                    + "Started 2026-10-14T08:00:05Z, ended"
                                                    + " 2026-10-14T08:00:05Z, took 0:00:00.000\n"
                                                    + "Command: "
                                                    + ProvenanceViews.PERCOLATOR_COMMAND
                                                    + "\nWorking directory: /run\n"
                                                    + "Environment: nothing added or overridden"),
                                    section.executionsProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "1 2026-10-14T08:00:00Z run.started: attempt = 1",
                                            "2 2026-10-14T08:00:05Z run.finished"),
                                    section.eventsProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "comet, stage comet-01 (stdout and stderr)\n"
                                                    + "Path: /run/logs/comet-01.log\n"
                                                    + "MD5: 33333333333333333333333333333333\n"
                                                    + "SHA-256: 33333333333333333333333333333333"
                                                    + "33333333333333333333333333333333"),
                                    section.logsProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "Run outcome (1)\n- run: the run failed",
                                            "Partial file (2)\n"
                                                    + "- /run/outputs/comet/a.pep.xml: this output"
                                                    + " is partial\n"
                                                    + "- /run/outputs/comet/a.pin: this output is"
                                                    + " partial"),
                                    section.warningsProperty().get()),
                    () -> assertTrue(section.exportEnabledProperty().get()),
                    () -> assertTrue(section.verifyEnabledProperty().get()),
                    () -> assertFalse(section.verifyingProperty().get()));
        }

        @Test
        @DisplayName(
                "a run with no sealed record opens and says so; nothing can be exported or"
                        + " verified, and the port is not asked")
        void noSealedRecord() {
            port.runs(() -> List.of(RUNNING))
                    .opens(
                            new ScriptedProvenance.Record(
                                    RUNNING,
                                    ProvenanceViews.failedRun(
                                            RecordState.NO_SEALED_RECORD, "running"),
                                    Optional.empty(),
                                    false));
            section.refresh();
            settle();
            section.choose(0);
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "No sealed record: this run has no provenance.json -- it is"
                                            + " still running, or the application or the computer"
                                            + " stopped before the record was written -- so it is"
                                            + " shown from its run.json and events.log alone.",
                                    section.recordTextProperty().get()),
                    () ->
                            assertTrue(
                                    section.summaryTextProperty()
                                            .get()
                                            .endsWith(
                                                    "Application: not recorded, because the run"
                                                            + " has no sealed record")),
                    () -> assertFalse(section.exportEnabledProperty().get()),
                    () -> assertFalse(section.verifyEnabledProperty().get()),
                    () -> assertFalse(section.exportJson()),
                    () ->
                            assertEquals(
                                    "Nothing was exported: this run has no sealed provenance"
                                            + " record, so there is nothing to export or verify.",
                                    section.exportStatusProperty().get()),
                    () -> assertFalse(section.verify()),
                    () ->
                            assertEquals(
                                    "Nothing was verified: this run has no sealed provenance"
                                            + " record, so there is nothing to export or verify.",
                                    section.verifyStatusProperty().get()),
                    () -> assertEquals(List.of("runs", "open"), methods()),
                    () -> assertEquals(List.of(), chooser.asked(), "no file was asked for"));
        }

        @Test
        @DisplayName("an unreadable record opens, saying so with the reader's message")
        void unreadableRecord() {
            port.runs(() -> List.of(COMPLETED))
                    .opens(
                            new ScriptedProvenance.Record(
                                    COMPLETED,
                                    ProvenanceViews.failedRun(
                                            RecordState.RECORD_UNREADABLE, "failed"),
                                    Optional.of("files[2].sha256: not 64 hexadecimal digits"),
                                    false));
            section.refresh();
            settle();
            section.choose(0);
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "Record unreadable: this run's provenance.json exists but"
                                            + " could not be read, so the run is shown from its"
                                            + " run.json and events.log alone. The reader said:"
                                            + " files[2].sha256: not 64 hexadecimal digits",
                                    section.recordTextProperty().get()),
                    () -> assertFalse(section.exportRst()),
                    () ->
                            assertEquals(
                                    "Nothing was exported: this run's provenance record could not"
                                            + " be read: files[2].sha256: not 64 hexadecimal"
                                            + " digits",
                                    section.exportStatusProperty().get()));
        }

        @Test
        @DisplayName("a run that cannot be opened says so, naming the run")
        void aRunThatCannotBeOpened() {
            port.runs(() -> List.of(COMPLETED)).openFails(new IOException("no such run directory"));
            section.refresh();
            settle();
            section.choose(0);
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "The provenance of 20261014T080000Z-r1 could not be read: no"
                                            + " such run directory",
                                    section.recordTextProperty().get()),
                    () -> assertFalse(section.recordShownProperty().get()));
        }

        @Test
        @DisplayName(
                "the answer for a run chosen before is dropped when another was chosen since,"
                        + " whichever arrives first")
        void aStaleOpeningIsDropped() {
            port.runs(() -> List.of(COMPLETED, RUNNING));
            section.refresh();
            settle();
            section.choose(0);
            section.choose(1);
            port.opens(
                    new ScriptedProvenance.Record(
                            RUNNING,
                            ProvenanceViews.failedRun(RecordState.NO_SEALED_RECORD, "running"),
                            Optional.empty(),
                            false));
            background.runLast();
            port.opens(sealed(COMPLETED));
            background.drain();
            ui.drain();
            assertAll(
                    () -> assertEquals(1, section.chosenIndexProperty().get()),
                    () ->
                            assertTrue(
                                    section.recordTextProperty()
                                            .get()
                                            .startsWith("No sealed record"),
                                    "the run chosen last is shown, not the one whose answer came"
                                            + " last: "
                                            + section.recordTextProperty().get()));

            port.openFails(new IOException("late"));
            section.choose(0);
            port.opens(sealed(COMPLETED));
            section.choose(1);
            background.runLast();
            port.openFails(new IOException("late"));
            background.drain();
            ui.drain();
            assertEquals(
                    "Sealed record: this run's provenance.json was read whole and parsed.",
                    section.recordTextProperty().get(),
                    "a stale failure must not replace the record shown");
        }
    }

    // ----------------------------------------------------------------------------- the copies --

    @Nested
    @DisplayName("Copy MD5, Copy SHA-256 and Copy command")
    class Copies {

        @Test
        @DisplayName("each copies exactly the recorded value and says what it copied")
        void exactValues() {
            openSealed();
            int callsBefore = port.calls().size();
            assertAll(
                    () ->
                            assertEquals(
                                    Optional.of(ProvenanceViews.COMET_SHA256),
                                    section.copyToolSha256(0)),
                    () ->
                            assertEquals(
                                    "Copied the SHA-256 of comet 2026.03.0's executable: "
                                            + ProvenanceViews.COMET_SHA256,
                                    section.actionStatusProperty().get()),
                    () ->
                            assertEquals(
                                    Optional.of(ProvenanceViews.PERCOLATOR_MD5),
                                    section.copyToolMd5(1)),
                    () ->
                            assertEquals(
                                    "Copied the MD5 of percolator 3.07.1's executable: "
                                            + ProvenanceViews.PERCOLATOR_MD5,
                                    section.actionStatusProperty().get()),
                    () ->
                            assertEquals(
                                    Optional.of(ProvenanceViews.SPECTRUM_MD5),
                                    section.copyFileMd5(0)),
                    () ->
                            assertEquals(
                                    "Copied the MD5 of the input spectrum file: "
                                            + ProvenanceViews.SPECTRUM_MD5,
                                    section.actionStatusProperty().get()),
                    () ->
                            assertEquals(
                                    Optional.of(ProvenanceViews.PEPXML_SHA256),
                                    section.copyFileSha256(1)),
                    () ->
                            assertEquals(
                                    "Copied the SHA-256 of the output pepxml file: "
                                            + ProvenanceViews.PEPXML_SHA256,
                                    section.actionStatusProperty().get()),
                    () ->
                            assertEquals(
                                    Optional.of(ProvenanceViews.COMET_COMMAND),
                                    section.copyCommand(0)),
                    () ->
                            assertEquals(
                                    "Copied the command of comet-01 (comet 2026.03.0): "
                                            + ProvenanceViews.COMET_COMMAND,
                                    section.actionStatusProperty().get()),
                    () ->
                            assertEquals(
                                    Optional.of(ProvenanceViews.PERCOLATOR_COMMAND),
                                    section.copyCommand(1)),
                    () ->
                            assertEquals(
                                    "Copied the command of percolator (percolator 3.07.1): "
                                            + ProvenanceViews.PERCOLATOR_COMMAND,
                                    section.actionStatusProperty().get()),
                    () ->
                            assertEquals(
                                    callsBefore,
                                    port.calls().size(),
                                    "a copy reads nothing through the port"));
        }

        @Test
        @DisplayName("nothing at the position, or no run shown: nothing copied, and said so")
        void nothingSelected() {
            assertEquals(Optional.empty(), section.copyToolMd5(0));
            assertEquals(
                    "Nothing was copied: nothing is selected.",
                    section.actionStatusProperty().get());
            openSealed();
            assertAll(
                    () -> assertEquals(Optional.empty(), section.copyToolSha256(2)),
                    () -> assertEquals(Optional.empty(), section.copyToolMd5(-1)),
                    () -> assertEquals(Optional.empty(), section.copyFileMd5(2)),
                    () -> assertEquals(Optional.empty(), section.copyFileSha256(-1)),
                    () -> assertEquals(Optional.empty(), section.copyCommand(2)),
                    () ->
                            assertEquals(
                                    "Nothing was copied: nothing is selected.",
                                    section.actionStatusProperty().get()));
            section.clipboardRefused("no clipboard owner");
            assertEquals(
                    "Nothing was copied: the clipboard refused the text: no clipboard owner",
                    section.actionStatusProperty().get());
        }
    }

    // ------------------------------------------------------------------- Open file location --

    @Nested
    @DisplayName("Open file location")
    class Locations {

        @Test
        @DisplayName(
                "a tool's, a file's and a log's location, each through the port on the"
                        + " background executor, with the port's words")
        void eachLocation() {
            openSealed();
            port.locates(() -> "Started xdg-open /cache to show comet.");
            assertTrue(section.openToolLocation(0));
            assertEquals(
                    "Starting the file manager to show comet's executable.",
                    section.actionStatusProperty().get());
            background.drain();
            assertEquals(
                    "Starting the file manager to show comet's executable.",
                    section.actionStatusProperty().get(),
                    "not before the ui queue runs");
            ui.drain();
            assertEquals(
                    "Started xdg-open /cache to show comet.", section.actionStatusProperty().get());
            assertTrue(section.openFileLocation(1));
            settle();
            assertTrue(section.openLogLocation(0));
            settle();
            List<String> asked =
                    port.calls().stream()
                            .filter(call -> call.method().equals("openLocation"))
                            .map(ScriptedProvenance.Call::argument)
                            .toList();
            assertEquals(
                    List.of(
                            ProvenanceViews.COMET.toString(),
                            ProvenanceViews.PEPXML.toString(),
                            ProvenanceViews.COMET_LOG.toString()),
                    asked);
        }

        @Test
        @DisplayName("a file manager that cannot be started is a stated failure, not a no-op")
        void aMissingLauncher() {
            openSealed();
            port.locates(
                    () -> {
                        throw new IOException("xdg-open is not installed on this computer");
                    });
            section.openLogLocation(0);
            settle();
            assertEquals(
                    "The location of the log of comet-01 was not shown: no file manager could be"
                            + " started: xdg-open is not installed on this computer",
                    section.actionStatusProperty().get());
            section.openFileLocation(0);
            settle();
            assertEquals(
                    "The location of the input spectrum file was not shown: no file manager could"
                            + " be started: xdg-open is not installed on this computer",
                    section.actionStatusProperty().get());
        }

        @Test
        @DisplayName("nothing at the position: nothing started, and said so")
        void nothingSelected() {
            openSealed();
            assertAll(
                    () -> assertFalse(section.openToolLocation(5)),
                    () ->
                            assertEquals(
                                    "Nothing was done: no tool is selected.",
                                    section.actionStatusProperty().get()),
                    () -> assertFalse(section.openFileLocation(-1)),
                    () ->
                            assertEquals(
                                    "Nothing was done: no file is selected.",
                                    section.actionStatusProperty().get()),
                    () -> assertFalse(section.openLogLocation(1)),
                    () ->
                            assertEquals(
                                    "Nothing was done: no log is selected.",
                                    section.actionStatusProperty().get()));
            assertEquals(List.of("runs", "open"), methods());
        }

        @Test
        @DisplayName("the answer for a run no longer shown is dropped")
        void aStaleAnswerIsDropped() {
            openSealed();
            section.openToolLocation(0);
            background.drain();
            section.choose(1);
            ui.drain();
            assertEquals("", section.actionStatusProperty().get());
            settle();
        }
    }

    // ---------------------------------------------------------------------------- the exports --

    @Nested
    @DisplayName("Export JSON and Export RST")
    class Exports {

        @Test
        @DisplayName(
                "the chooser is asked with the format's title and name, the port writes on the"
                        + " background executor, and the file written is stated")
        void exportJson() {
            openSealed();
            Path target = Path.of("exports/r1.json");
            chooser.saveTo(target);
            port.exports(() -> target);
            assertTrue(section.exportJson());
            assertAll(
                    () ->
                            assertEquals(
                                    List.of("export:Export provenance JSON:provenance-r1.json"),
                                    chooser.asked()),
                    () ->
                            assertEquals(
                                    "Exporting provenance JSON to exports/r1.json.",
                                    section.exportStatusProperty().get()),
                    () -> assertFalse(section.exportEnabledProperty().get(), "one at a time"),
                    () -> assertFalse(section.exportRst(), "refused while one is written"));
            background.drain();
            assertTrue(methods().contains("exportJson"));
            ui.drain();
            assertAll(
                    () ->
                            assertEquals(
                                    "Exported provenance JSON to exports/r1.json.",
                                    section.exportStatusProperty().get()),
                    () -> assertTrue(section.exportEnabledProperty().get()),
                    () ->
                            assertEquals(
                                    List.of("exports/r1.json"),
                                    port.calls().stream()
                                            .filter(call -> call.method().startsWith("export"))
                                            .map(ScriptedProvenance.Call::argument)
                                            .toList()));
        }

        @Test
        @DisplayName("Export RST asks for an .rst file and writes through exportRst")
        void exportRst() {
            openSealed();
            Path target = Path.of("exports/r1.rst");
            chooser.saveTo(target);
            port.exports(() -> target);
            assertTrue(section.exportRst());
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    List.of("export:Export provenance RST:provenance-r1.rst"),
                                    chooser.asked()),
                    () -> assertEquals(List.of("runs", "open", "exportRst"), methods()),
                    () ->
                            assertEquals(
                                    "Exported provenance RST to exports/r1.rst.",
                                    section.exportStatusProperty().get()));
        }

        @Test
        @DisplayName("a chooser that is cancelled exports nothing")
        void cancelled() {
            openSealed();
            assertFalse(section.exportJson());
            assertAll(
                    () ->
                            assertEquals(
                                    "Nothing was exported: no file was chosen.",
                                    section.exportStatusProperty().get()),
                    () -> assertEquals(List.of("runs", "open"), methods()));
        }

        @Test
        @DisplayName("an existing file is refused, Replace is offered, and Replace writes over it")
        void anExistingFileAndReplace() {
            openSealed();
            Path target = Path.of("exports/r1.json");
            chooser.saveTo(target);
            port.exports(
                    () -> {
                        throw new FileAlreadyExistsException(
                                target.toString(),
                                null,
                                "a file already exists here; confirm replacing it to export over"
                                        + " it");
                    });
            assertFalse(section.replaceExisting(), "nothing to replace yet");
            section.exportJson();
            settle();
            assertAll(
                    () -> assertTrue(section.replaceOfferedProperty().get()),
                    () ->
                            assertEquals(
                                    "Nothing was exported: exports/r1.json already exists (a"
                                            + " file already exists here; confirm replacing it to"
                                            + " export over it). Press Replace to export provenance"
                                            + " JSON over it, or export again and choose another"
                                            + " file.",
                                    section.exportStatusProperty().get()));
            port.exports(() -> target);
            assertTrue(section.replaceExisting());
            assertEquals(
                    "Replacing exports/r1.json with provenance JSON.",
                    section.exportStatusProperty().get());
            assertFalse(section.replaceOfferedProperty().get());
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    List.of("exports/r1.json", "exports/r1.json replacing"),
                                    port.calls().stream()
                                            .filter(call -> call.method().equals("exportJson"))
                                            .map(ScriptedProvenance.Call::argument)
                                            .toList()),
                    () ->
                            assertEquals(
                                    "Exported provenance JSON to exports/r1.json.",
                                    section.exportStatusProperty().get()),
                    () -> assertFalse(section.replaceExisting(), "nothing left to replace"));
        }

        @Test
        @DisplayName(
                "a replacement refused too -- the run's own record -- is stated, and Replace is"
                        + " not offered again")
        void aRefusedReplacement() {
            openSealed();
            Path target = Path.of("run/provenance/provenance.rst");
            chooser.saveTo(target);
            port.exports(
                    () -> {
                        throw new FileAlreadyExistsException(
                                target.toString(),
                                null,
                                "this is the run's own sealed record, which an export never"
                                        + " replaces");
                    });
            section.exportRst();
            settle();
            assertTrue(section.replaceExisting());
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "Nothing was exported to run/provenance/provenance.rst: this"
                                            + " is the run's own sealed record, which an export"
                                            + " never replaces.",
                                    section.exportStatusProperty().get()),
                    () -> assertFalse(section.replaceOfferedProperty().get()),
                    () -> assertTrue(section.exportEnabledProperty().get()));
            port.exports(
                    () -> {
                        throw new FileAlreadyExistsException(target.toString());
                    });
            chooser.saveTo(target);
            section.exportRst();
            settle();
            assertEquals(
                    "Nothing was exported: run/provenance/provenance.rst already exists (a file"
                            + " already exists there). Press Replace to export provenance RST over"
                            + " it, or export again and choose another file.",
                    section.exportStatusProperty().get());
        }

        @Test
        @DisplayName("any other failure is stated with the file and the reason")
        void aFailure() {
            openSealed();
            chooser.saveTo(Path.of("read-only/r1.json"));
            port.exports(
                    () -> {
                        throw new IOException("/read-only: permission denied");
                    });
            section.exportJson();
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "Nothing was exported to read-only/r1.json: /read-only:"
                                            + " permission denied",
                                    section.exportStatusProperty().get()),
                    () -> assertTrue(section.exportEnabledProperty().get()),
                    () -> assertFalse(section.replaceOfferedProperty().get()));
        }

        @Test
        @DisplayName("an export's answer for a run no longer shown is dropped")
        void aStaleExportIsDropped() {
            openSealed();
            chooser.saveTo(Path.of("exports/r1.json"));
            port.exports(() -> Path.of("exports/r1.json"));
            section.exportJson();
            background.drain();
            section.choose(1);
            ui.drain();
            assertEquals("", section.exportStatusProperty().get());
            settle();
            assertEquals("", section.exportStatusProperty().get());
        }
    }

    // --------------------------------------------------------------------- verify against disk --

    @Nested
    @DisplayName("Verify against disk")
    class Verify {

        @Test
        @DisplayName("every file matching, stated per file and in one sentence")
        void everyFileMatches() {
            openSealed();
            port.verifies(
                    () ->
                            new DiskVerification(
                                    List.of(
                                            check(
                                                    "pepxml",
                                                    "/run/a.pep.xml",
                                                    DiskOutcome.MATCHES,
                                                    "bb"),
                                            check("pin", "/run/a.pin", DiskOutcome.MATCHES, "bb")),
                                    true));
            assertTrue(section.verify());
            assertAll(
                    () -> assertTrue(section.verifyingProperty().get()),
                    () -> assertFalse(section.verifyEnabledProperty().get()),
                    () -> assertFalse(section.verify(), "one at a time"),
                    () ->
                            assertEquals(
                                    "Verifying every file the record lists against the disk.",
                                    section.verifyStatusProperty().get()));
            settle();
            assertAll(
                    () -> assertFalse(section.verifyingProperty().get()),
                    () -> assertTrue(section.verifyEnabledProperty().get()),
                    () ->
                            assertEquals(
                                    "Every file the record lists matches its record on disk: 2"
                                            + " files checked.",
                                    section.verifyStatusProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "MATCHES -- output pepxml: /run/a.pep.xml",
                                            "MATCHES -- output pin: /run/a.pin"),
                                    section.verificationProperty().get()),
                    () -> assertFalse(section.cancelVerification(), "nothing left to cancel"));
            section.verify();
            assertEquals(
                    List.of(),
                    section.verificationProperty().get(),
                    "a new verification clears the last one's rows at once");
            settle();
        }

        @Test
        @DisplayName("findings: a differing file shows both digests; missing and unreadable")
        void findings() {
            openSealed();
            port.verifies(
                    () ->
                            new DiskVerification(
                                    List.of(
                                            check(
                                                    "pepxml",
                                                    "/run/a.pep.xml",
                                                    DiskOutcome.DIFFERS,
                                                    "cc"),
                                            check("pin", "/run/a.pin", DiskOutcome.MISSING, null),
                                            check("log", "/run/l", DiskOutcome.UNREADABLE, null),
                                            check("mzid", "/run/m", DiskOutcome.MATCHES, "bb")),
                                    true));
            section.verify();
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "Not every file matches its record: of 4 files, 1 match, 1"
                                            + " differ, 1 missing, 1 unreadable.",
                                    section.verifyStatusProperty().get()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "DIFFERS -- output pepxml: /run/a.pep.xml\n"
                                                    + "Recorded MD5 aa, found cc\n"
                                                    + "Recorded SHA-256 bb, found cc",
                                            "MISSING -- output pin: /run/a.pin",
                                            "UNREADABLE -- output log: /run/l\n"
                                                    + "it is a directory",
                                            "MATCHES -- output mzid: /run/m"),
                                    section.verificationProperty().get()));
        }

        @Test
        @DisplayName("Cancel reaches the port's stop switch, and the cancelled result says so")
        void cancel() {
            openSealed();
            port.verifies(
                    () ->
                            new DiskVerification(
                                    List.of(check("pin", "/run/a.pin", DiskOutcome.MATCHES, "bb")),
                                    false));
            assertFalse(section.cancelVerification(), "nothing to cancel yet");
            section.verify();
            background.drain();
            assertFalse(port.lastCancel().getAsBoolean(), "not stopped before Cancel");
            assertTrue(section.cancelVerification());
            assertAll(
                    () -> assertTrue(port.lastCancel().getAsBoolean(), "the switch the port got"),
                    () ->
                            assertEquals(
                                    "Stopping the verification before its next file.",
                                    section.verifyStatusProperty().get()));
            ui.drain();
            assertAll(
                    () ->
                            assertEquals(
                                    "The verification was cancelled after checking 1 file, so not"
                                            + " every file was checked: 1 match, 0 differ, 0"
                                            + " missing, 0 unreadable.",
                                    section.verifyStatusProperty().get()),
                    () -> assertFalse(section.verifyingProperty().get()));
        }

        @Test
        @DisplayName("a verification the port refuses is stated")
        void aFailure() {
            openSealed();
            port.verifies(
                    () -> {
                        throw new IOException("run r1 has no sealed provenance record");
                    });
            section.verify();
            settle();
            assertAll(
                    () ->
                            assertEquals(
                                    "The files could not be verified: run r1 has no sealed"
                                            + " provenance record",
                                    section.verifyStatusProperty().get()),
                    () -> assertTrue(section.verifyEnabledProperty().get()));
        }

        @Test
        @DisplayName("choosing another run stops the running verification and drops its answer")
        void aStaleVerificationIsDropped() {
            openSealed();
            port.verifies(
                    () ->
                            new DiskVerification(
                                    List.of(check("pin", "/run/a.pin", DiskOutcome.MATCHES, "bb")),
                                    true));
            section.verify();
            background.drain();
            section.choose(1);
            assertTrue(port.lastCancel().getAsBoolean(), "the old verification was told to stop");
            ui.drain();
            assertAll(
                    () ->
                            assertEquals(
                                    ProvenanceViewModel.NOT_VERIFIED,
                                    section.verifyStatusProperty().get()),
                    () -> assertEquals(List.of(), section.verificationProperty().get()),
                    () -> assertFalse(section.verifyingProperty().get()));
            settle();

            port.verifies(
                    () -> {
                        throw new IOException("late");
                    });
            section.choose(0);
            settle();
            section.verify();
            background.drain();
            section.choose(1);
            ui.drain();
            assertEquals(ProvenanceViewModel.NOT_VERIFIED, section.verifyStatusProperty().get());
            settle();
        }
    }

    @Test
    @DisplayName(
            "choosing another run clears every part and every action's state at once, before its"
                    + " answer arrives; an export in flight no longer holds the exports")
    void choosingAnotherRunClearsEverything() {
        openSealed();
        section.copyToolMd5(0);
        chooser.saveTo(Path.of("exports", "r1.json"));
        port.exports(
                () -> {
                    throw new FileAlreadyExistsException("exports/r1.json");
                });
        section.exportJson();
        settle();
        port.verifies(
                () ->
                        new DiskVerification(
                                List.of(check("pin", "/run/a.pin", DiskOutcome.MATCHES, "bb")),
                                true));
        section.verify();
        settle();
        assertTrue(section.replaceOfferedProperty().get(), "the fixture offers Replace");

        section.choose(1);
        assertAll(
                "everything of the run before is gone at once",
                () -> assertFalse(section.recordShownProperty().get()),
                () -> assertEquals(ProvenanceViewModel.OPENING, section.recordTextProperty().get()),
                () -> assertEquals("", section.summaryTextProperty().get()),
                () -> assertEquals(List.of(), section.toolsProperty().get()),
                () -> assertEquals(List.of(), section.filesProperty().get()),
                () -> assertEquals("", section.cometParamsTextProperty().get()),
                () -> assertEquals("", section.cometParamsStatusProperty().get()),
                () -> assertEquals("", section.percolatorSettingsTextProperty().get()),
                () -> assertEquals(List.of(), section.parametersProperty().get()),
                () -> assertEquals("", section.presetsTextProperty().get()),
                () -> assertEquals("", section.parameterNotesProperty().get()),
                () -> assertEquals(List.of(), section.executionsProperty().get()),
                () -> assertEquals(List.of(), section.eventsProperty().get()),
                () -> assertEquals(List.of(), section.logsProperty().get()),
                () -> assertEquals(List.of(), section.warningsProperty().get()),
                () -> assertEquals("", section.actionStatusProperty().get()),
                () -> assertEquals("", section.exportStatusProperty().get()),
                () -> assertFalse(section.replaceOfferedProperty().get()),
                () ->
                        assertEquals(
                                ProvenanceViewModel.NOT_VERIFIED,
                                section.verifyStatusProperty().get()),
                () -> assertEquals(List.of(), section.verificationProperty().get()),
                () -> assertFalse(section.exportEnabledProperty().get()),
                () -> assertFalse(section.verifyEnabledProperty().get()));
        settle();
    }

    @Test
    @DisplayName(
            "an export in flight for the run chosen before neither holds the exports nor reports")
    void anExportInFlightHoldsNothing() {
        openSealed();
        chooser.saveTo(Path.of("exports", "r1.rst"));
        port.exports(() -> Path.of("exports", "r1.rst"));
        section.exportRst();
        assertFalse(section.exportEnabledProperty().get(), "an export is in flight");
        section.choose(1);
        settle();
        assertAll(
                "the run now shown can be exported: the old export holds nothing",
                () -> assertTrue(section.recordShownProperty().get()),
                () -> assertTrue(section.exportEnabledProperty().get()),
                () -> assertEquals("", section.exportStatusProperty().get()));
    }

    @Test
    @DisplayName("with no run shown, an export and a verification say so")
    void nothingShown() {
        assertAll(
                () -> assertFalse(section.exportJson()),
                () ->
                        assertEquals(
                                "Nothing was exported: no run's provenance is shown.",
                                section.exportStatusProperty().get()),
                () -> assertFalse(section.verify()),
                () ->
                        assertEquals(
                                "Nothing was verified: no run's provenance is shown.",
                                section.verifyStatusProperty().get()),
                () -> assertFalse(section.replaceExisting()),
                () -> assertEquals(List.of(), port.calls()),
                () -> assertEquals(List.of(), chooser.asked()));
    }

    @Test
    @DisplayName("an empty view: the parts say there is nothing, rather than showing nothing")
    void emptyParts() {
        ProvenanceView full = ProvenanceViews.failedRun();
        ProvenanceView empty =
                new ProvenanceView(
                        full.summary(),
                        List.of(),
                        List.of(),
                        new ProvenanceView.Parameters(
                                Optional.empty(),
                                Optional.of(
                                        new ProvenanceView.ArchivedText(
                                                "comet-params",
                                                "/run/parameters/comet.params",
                                                Path.of("run/parameters/comet.params"),
                                                "cccc",
                                                DiskOutcome.DIFFERS,
                                                Optional.of("dddd"),
                                                Optional.empty(),
                                                Optional.of("the text is not shown"))),
                                Optional.empty(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()),
                        new ProvenanceView.Timeline(List.of(), List.of()),
                        List.of(),
                        List.of());
        port.runs(() -> List.of(COMPLETED))
                .opens(new ScriptedProvenance.Record(COMPLETED, empty, Optional.empty(), true));
        section.refresh();
        settle();
        section.choose(0);
        settle();
        assertAll(
                () -> assertEquals("", section.cometParamsTextProperty().get()),
                () ->
                        assertEquals(
                                "The archived Comet parameter file, /run/parameters/comet.params:"
                                        + " recorded SHA-256 cccc; on disk now it differs (SHA-256"
                                        + " dddd). the text is not shown.",
                                section.cometParamsStatusProperty().get()),
                () ->
                        assertEquals(
                                "This run lists no archived Percolator settings file.\n"
                                        + "No Percolator setting is recorded for this run.",
                                section.percolatorSettingsTextProperty().get()),
                () ->
                        assertEquals(
                                "No preset is recorded as applied to this run's parameters.",
                                section.presetsTextProperty().get()),
                () -> assertEquals("", section.parameterNotesProperty().get()),
                () -> assertEquals(List.of(), section.warningsProperty().get()));
    }

    /** An executor that holds its tasks until the test runs them, and says when it is running. */
    private static final class Queue implements Executor {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        private boolean running;

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        int pending() {
            return tasks.size();
        }

        void runLast() {
            run(tasks.pollLast());
        }

        void drain() {
            while (!tasks.isEmpty()) {
                run(tasks.pollFirst());
            }
        }

        private void run(Runnable task) {
            boolean was = running;
            running = true;
            try {
                task.run();
            } finally {
                running = was;
            }
        }
    }
}
