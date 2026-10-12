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

package org.cometgui.ui.viewmodel.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.ui.testing.ScriptedLimelightUpload;
import org.cometgui.ui.testing.ScriptedLimelightUpload.Preview;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;
import org.cometgui.workflow.limelight.LimelightUploadRefusedException;
import org.cometgui.workflow.limelight.LimelightUploadWatcher;
import org.cometgui.workflow.storage.ViewStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Limelight section's upload half over a scripted port, with the Results section real: nothing
 * is offered until the run has a conversion, with the reason shown; the server and project are
 * checked by the port's rules and the refusals shown; the key is admitted the moment it is taken
 * and appears in no text this view-model publishes; Upload checks and shows the destination and
 * sends nothing; only Send sends -- exactly the destination shown, once; Cancel and every change of
 * file, server, project, key or run withdraw it, including one still being checked; and every way
 * an upload ends is shown with the server's own words and identifiers.
 */
class LimelightUploadViewModelTest {

    private static final String RUN = "run-1";

    private static final Instant CREATED = Instant.parse("2026-10-12T08:00:00Z");

    /** A recognisable key; every test asserts it is in no published text. */
    private static final String KEY = "VMSEEDKEY-7f3a9c2e5b1d4e8f";

    private static final String SERVER = "https://limelight.example.org/limelight";

    private static final Path ROOT = Path.of("p").toAbsolutePath();

    private static final Path NEWER = ROOT.resolve("runs/run-1/outputs/limelight/limelight-2.xml");

    private static final Path OLDER = ROOT.resolve("runs/run-1/outputs/limelight/limelight-1.xml");

    private static final String SHA = "ab".repeat(32);

    @TempDir private Path work;

    private final Queue background = new Queue();

    private final Queue ui = new Queue();

    private ScriptedLimelightUpload port;

    private Runs runs;

    private ResultsViewModel results;

    private SimpleStringProperty converted;

    private LimelightUploadViewModel upload;

    @BeforeEach
    void build() throws IOException {
        port = new ScriptedLimelightUpload();
        Path psms =
                Files.writeString(
                        work.resolve("psms.tsv"),
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n"
                                + "a_101_2_1\t1.5\t0.001\t0.0001\tK.PEPTIDEA.R\tsp|P1|A\n",
                        StandardCharsets.UTF_8);
        runs = new Runs(work, psms);
        DisplayFiltersViewModel filters = new DisplayFiltersViewModel();
        ResultTableViewModel table =
                new ResultTableViewModel(filters, Runnable::run, Runnable::run);
        results =
                new ResultsViewModel(
                        runs, filters, table, new WeightsViewModel(), Runnable::run, Runnable::run);
        converted = new SimpleStringProperty("");
        upload = new LimelightUploadViewModel(port, results, converted, background, ui);
    }

    @AfterEach
    void close() {
        results.close();
    }

    // ----------------------------------------------------------------------------- fixtures --

    private void settle() {
        while (background.pending() + ui.pending() > 0) {
            background.drain();
            ui.drain();
        }
    }

    /** Opens the run in the Results section with these converted files. */
    private void open(Path... files) {
        port.files(RUN, List.of(files));
        upload.refresh();
        results.refresh();
        settle();
    }

    /** Everything an Upload needs: a file, the server, the project and a typed key. */
    private void ready() {
        open(NEWER, OLDER);
        upload.editServer(SERVER);
        upload.editProject("7");
        settle();
        assertTrue(upload.takeKey(KEY));
        settle();
    }

    private static Preview preview(Path file) {
        return new Preview(
                new RunId(RUN), SERVER, 7, SERVER + "/d/pg/project/7", file, 12_797_113L, SHA);
    }

    /** Ready, and Upload pressed: the destination is shown. */
    private Preview shown() {
        ready();
        Preview preview = preview(NEWER);
        port.prepare(() -> preview);
        assertTrue(upload.prepare());
        settle();
        assertTrue(upload.destinationShownProperty().get());
        return preview;
    }

    private static LimelightUploaded submitted(String keyNote) {
        return new LimelightUploaded(
                LimelightUploaded.Kind.SUBMITTED,
                "final-submit",
                "4100",
                "4100_abc",
                SERVER + "/d/pg/project/7",
                0,
                List.of(),
                "",
                ROOT.resolve("runs/run-1/logs/limelight-upload.log"),
                keyNote);
    }

    /** Every text this view-model publishes, by property name. */
    private Map<String, String> published() {
        Map<String, String> texts = new java.util.TreeMap<>();
        for (Method method : LimelightUploadViewModel.class.getMethods()) {
            if (method.getName().endsWith("Property") && method.getParameterCount() == 0) {
                try {
                    Object value = ((ObservableValue<?>) method.invoke(upload)).getValue();
                    texts.put(method.getName(), String.valueOf(value));
                } catch (ReflectiveOperationException unreadable) {
                    throw new AssertionError(unreadable);
                }
            }
        }
        return texts;
    }

    /** Asserts the key is in no published text, and that the walk saw the texts it should. */
    private void assertNoKeyPublished() {
        Map<String, String> texts = published();
        assertTrue(texts.size() >= 30, () -> "only " + texts.size() + " properties were read");
        List<String> carrying = new ArrayList<>();
        for (Map.Entry<String, String> text : texts.entrySet()) {
            if (text.getValue().contains(KEY) || text.getValue().contains(KEY.substring(0, 12))) {
                carrying.add(text.getKey());
            }
        }
        assertEquals(List.of(), carrying, "properties carrying the key");
    }

    // -------------------------------------------------------------------------------- tests --

    @Test
    @DisplayName("with no run open nothing is offered, Upload is refused and nothing is checked")
    void noRun() {
        settle();
        assertAll(
                () ->
                        assertEquals(
                                LimelightUploadViewModel.NO_RUN
                                        + " Open a run there, convert it above, then upload it.",
                                upload.filesStatusProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.NO_RUN,
                                upload.prepareRefusalProperty().get()),
                () -> assertFalse(upload.prepareEnabledProperty().get()),
                () -> assertEquals(List.of(), upload.fileChoicesProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.NOTHING_SENT,
                                upload.outcomeProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.SERVER_HELP,
                                upload.serverStatusProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.PROJECT_HELP,
                                upload.projectStatusProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.NO_KEY, upload.keyStatusProperty().get()),
                () -> assertTrue(upload.inputsEnabledProperty().get()),
                () -> assertFalse(upload.cancel()));
        assertFalse(upload.prepare());
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "Nothing was checked and nothing was sent: "
                                        + LimelightUploadViewModel.NO_RUN,
                                upload.outcomeProperty().get()),
                () -> assertEquals(List.of(), port.prepares()),
                () -> assertEquals(0, port.fileReads()));
    }

    @Test
    @DisplayName(
            "nothing is offered until the run has a conversion, with the reason; a conversion reads"
                    + " the files again, newest first and chosen")
    void nothingOfferedUntilAConversionExists() {
        open();
        assertAll(
                () ->
                        assertEquals(
                                "Nothing can be uploaded from run run-1 yet: no Limelight XML has"
                                        + " been converted from it. Convert it above first.",
                                upload.filesStatusProperty().get()),
                () ->
                        assertEquals(
                                upload.filesStatusProperty().get(),
                                upload.prepareRefusalProperty().get()),
                () -> assertEquals(List.of(), upload.fileChoicesProperty().get()),
                () -> assertEquals("", upload.chosenFileProperty().get()));
        int reads = port.fileReads();

        port.files(RUN, List.of(OLDER));
        converted.set(OLDER.toString());
        settle();
        assertAll(
                () -> assertEquals(reads + 1, port.fileReads()),
                () ->
                        assertEquals(
                                "1 Limelight XML file was converted from run run-1, newest first.",
                                upload.filesStatusProperty().get()),
                () -> assertEquals(OLDER.toString(), upload.chosenFileProperty().get()));

        port.files(RUN, List.of(NEWER, OLDER));
        converted.set(NEWER.toString());
        settle();
        assertAll(
                () ->
                        assertEquals(
                                List.of(NEWER.toString(), OLDER.toString()),
                                upload.fileChoicesProperty().get()),
                () ->
                        assertEquals(
                                "2 Limelight XML files were converted from run run-1, newest"
                                        + " first.",
                                upload.filesStatusProperty().get()),
                () -> assertEquals(NEWER.toString(), upload.chosenFileProperty().get()));

        assertTrue(upload.chooseFile(OLDER.toString()));
        assertFalse(upload.chooseFile("/not/offered.xml"));
        assertEquals(OLDER.toString(), upload.chosenFileProperty().get());
        upload.refresh();
        settle();
        assertEquals(
                OLDER.toString(),
                upload.chosenFileProperty().get(),
                "the same files read again keep the choice");
    }

    @Test
    @DisplayName("the files that cannot be read say why, and Upload is refused with it")
    void filesUnreadable() {
        port.filesFail("events.log is unreadable");
        open();
        assertAll(
                () ->
                        assertEquals(
                                "Which Limelight XML files run run-1's conversions produced could"
                                        + " not be read: events.log is unreadable",
                                upload.filesStatusProperty().get()),
                () ->
                        assertEquals(
                                upload.filesStatusProperty().get(),
                                upload.prepareRefusalProperty().get()));
    }

    @Test
    @DisplayName("the files are said to be read while they are")
    void filesBeingRead() {
        port.files(RUN, List.of(NEWER));
        results.refresh();
        assertEquals(
                "Reading which Limelight XML files run run-1's conversions produced.",
                upload.filesStatusProperty().get());
        settle();
        assertEquals(NEWER.toString(), upload.chosenFileProperty().get());
    }

    @Test
    @DisplayName("the server is checked by the port's rule: refused with its reason, normalised")
    void theServer() {
        open(NEWER);
        upload.editServer("http://limelight.example.org/limelight");
        assertEquals("Checking the server address.", upload.serverStatusProperty().get());
        assertEquals("Checking the server address.", upload.prepareRefusalProperty().get());
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "This server address cannot be used: plain http would send the"
                                        + " submit key across the network unencrypted; use https",
                                upload.serverStatusProperty().get()),
                () ->
                        assertEquals(
                                upload.serverStatusProperty().get(),
                                upload.prepareRefusalProperty().get()),
                () ->
                        assertEquals(
                                "http://limelight.example.org/limelight",
                                upload.serverTextProperty().get()));
        upload.editServer(SERVER + "/");
        settle();
        assertEquals("Uploads go to " + SERVER + ".", upload.serverStatusProperty().get());
        upload.editServer("  ");
        settle();
        assertAll(
                () ->
                        assertEquals(
                                LimelightUploadViewModel.SERVER_HELP,
                                upload.serverStatusProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.SERVER_HELP,
                                upload.prepareRefusalProperty().get()));
    }

    @Test
    @DisplayName("the project is a positive whole number, typed, and its help says why it is typed")
    void theProject() {
        open(NEWER);
        upload.editServer(SERVER);
        upload.editProject("0");
        assertEquals("Checking the project id.", upload.projectStatusProperty().get());
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "This project id cannot be used: a Limelight project id is a"
                                        + " positive whole number, not \"0\"",
                                upload.projectStatusProperty().get()),
                () ->
                        assertEquals(
                                upload.projectStatusProperty().get(),
                                upload.prepareRefusalProperty().get()),
                () -> assertEquals("0", upload.projectTextProperty().get()),
                () ->
                        assertTrue(
                                LimelightUploadViewModel.PROJECT_HELP.contains(
                                        "never to a submit key")));
        upload.editProject("7");
        assertEquals("Checking the project id.", upload.prepareRefusalProperty().get());
        settle();
        assertAll(
                () -> assertEquals("Project 7.", upload.projectStatusProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.NO_KEY,
                                upload.prepareRefusalProperty().get()));
        upload.editProject("");
        settle();
        assertEquals(LimelightUploadViewModel.PROJECT_HELP, upload.projectStatusProperty().get());
    }

    @Test
    @DisplayName(
            "the key is admitted the moment it is taken, the redactor knows it, and no published"
                    + " text carries it")
    void theKeyIsAdmittedAndNeverPublished() {
        open(NEWER);
        assertFalse(upload.takeKey(""), "an empty field is not a key");
        assertTrue(upload.takeKey(KEY));
        settle();
        assertAll(
                () -> assertEquals(List.of(KEY), port.admitted()),
                () -> assertEquals("[REDACTED]", port.secrets().redactor().redactText(KEY)),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.KEY_TYPED,
                                upload.keyStatusProperty().get()),
                () -> assertTrue(upload.rememberEnabledProperty().get()));
        assertNoKeyPublished();

        assertFalse(upload.takeKey("short"), "the registry's minimum length refuses it");
        assertAll(
                () ->
                        assertTrue(
                                upload.keyStatusProperty()
                                        .get()
                                        .startsWith(
                                                "The submit key was not taken: a registered secret"
                                                        + " must be at least 8 characters long"),
                                upload.keyStatusProperty().get()),
                () ->
                        assertTrue(
                                upload.keyStatusProperty()
                                        .get()
                                        .endsWith(LimelightUploadViewModel.NO_KEY)),
                () -> assertFalse(upload.keyStatusProperty().get().contains("short")),
                () -> assertFalse(upload.rememberEnabledProperty().get()),
                () -> assertEquals(List.of(KEY), port.admitted()));
    }

    @Test
    @DisplayName(
            "Upload checks and shows the exact destination and sends nothing; Send and Cancel are"
                    + " offered")
    void uploadSendsNothing() {
        shown();
        assertAll(
                () -> assertEquals(1, port.prepares().size()),
                () -> assertEquals(NEWER, port.prepares().get(0).file()),
                () -> assertEquals(SERVER, port.prepares().get(0).server()),
                () -> assertEquals("7", port.prepares().get(0).project()),
                () -> assertEquals(new RunId(RUN), port.prepares().get(0).run().id()),
                () -> assertEquals(List.of(), port.sends(), "Upload sent nothing"),
                () -> assertEquals(SERVER, upload.confirmServerProperty().get()),
                () -> assertEquals("7", upload.confirmProjectProperty().get()),
                () -> assertEquals(SERVER + "/d/pg/project/7", upload.confirmPageProperty().get()),
                () -> assertEquals(NEWER.toString(), upload.confirmFileProperty().get()),
                () -> assertEquals("12797113 bytes", upload.confirmSizeProperty().get()),
                () -> assertEquals(SHA, upload.confirmSha256Property().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.CHECK_AND_SEND,
                                upload.outcomeProperty().get()),
                () -> assertTrue(upload.sendEnabledProperty().get()),
                () -> assertTrue(upload.cancelEnabledProperty().get()),
                () -> assertTrue(upload.prepareEnabledProperty().get()));
        assertNoKeyPublished();
    }

    @Test
    @DisplayName("while a destination is checked, Upload says so and Cancel can withdraw it")
    void checking() {
        ready();
        port.prepare(() -> preview(NEWER));
        assertTrue(upload.prepare());
        assertAll(
                () ->
                        assertEquals(
                                LimelightUploadViewModel.CHECKING, upload.outcomeProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.CHECKING,
                                upload.prepareRefusalProperty().get()),
                () -> assertTrue(upload.cancelEnabledProperty().get()),
                () -> assertFalse(upload.sendEnabledProperty().get()));
        assertTrue(upload.cancel());
        settle();
        assertAll(
                () ->
                        assertEquals(
                                LimelightUploadViewModel.CANCELLED, upload.outcomeProperty().get()),
                () -> assertFalse(upload.destinationShownProperty().get()),
                () -> assertFalse(upload.send()),
                () -> assertEquals(List.of(), port.sends()));
    }

    @Test
    @DisplayName(
            "Send sends exactly the destination shown, once, with the typed key; the log, progress"
                    + " and the server's identifiers are shown")
    void sendSendsTheShownDestinationOnce() {
        Preview preview = shown();
        port.send(
                () -> {
                    LimelightUploadWatcher watcher = port.sends().get(0).watcher();
                    watcher.line("Upload to Limelight: " + SERVER + ", project 7");
                    watcher.bytesSent(50, 200);
                    watcher.line("Submitted.");
                    return submitted("");
                });
        assertTrue(upload.send());
        assertAll(
                "while sending",
                () -> assertTrue(upload.sendingProperty().get()),
                () -> assertFalse(upload.inputsEnabledProperty().get()),
                () -> assertFalse(upload.sendEnabledProperty().get()),
                () -> assertFalse(upload.cancelEnabledProperty().get()),
                () -> assertTrue(upload.destinationShownProperty().get()),
                () ->
                        assertEquals(
                                "An upload is being sent.", upload.prepareRefusalProperty().get()),
                () ->
                        assertEquals(
                                "Sending "
                                        + NEWER
                                        + " to "
                                        + SERVER
                                        + ", project 7. "
                                        + LimelightUploadViewModel.NO_CANCEL,
                                upload.outcomeProperty().get()),
                () -> assertFalse(upload.send(), "a second press sends nothing more"),
                () -> assertFalse(upload.cancel()),
                () -> assertFalse(upload.prepare()),
                () -> assertFalse(upload.takeKey("another-key-123")),
                () -> assertFalse(upload.chooseFile(OLDER.toString())),
                () -> assertFalse(upload.setRemember(true)));
        upload.editServer("https://elsewhere.example.org");
        assertEquals(SERVER, upload.serverTextProperty().get(), "inputs are locked while sending");
        background.drain();
        assertEquals(1, port.sends().size());
        ScriptedLimelightUpload.Send sent = port.sends().get(0);
        assertAll(
                () -> assertSame(preview, sent.confirmed()),
                () -> assertEquals(KEY, sent.typed().orElseThrow().reveal()),
                () -> assertFalse(sent.remember()));
        settle();
        assertAll(
                "after",
                () ->
                        assertEquals(
                                "Upload to Limelight: " + SERVER + ", project 7\nSubmitted.",
                                upload.logProperty().get()),
                () ->
                        assertEquals(
                                "Submitted to Limelight: the server queued the import of "
                                        + NEWER
                                        + " into project 7 on "
                                        + SERVER
                                        + ". Its tracking id (uploadKey) is 4100. The import runs"
                                        + " on the server; follow it on the project's page, "
                                        + SERVER
                                        + "/d/pg/project/7.",
                                upload.outcomeProperty().get()),
                () -> assertEquals("4100", upload.trackingIdProperty().get()),
                () -> assertEquals("4100_abc", upload.importerSubDirProperty().get()),
                () -> assertEquals(SERVER + "/d/pg/project/7", upload.projectPageProperty().get()),
                () ->
                        assertEquals(
                                "The upload's log: "
                                        + ROOT.resolve("runs/run-1/logs/limelight-upload.log"),
                                upload.logPathProperty().get()),
                () -> assertEquals(1.0, upload.progressProperty().get()),
                () -> assertFalse(upload.sendingProperty().get()),
                () -> assertTrue(upload.inputsEnabledProperty().get()),
                () -> assertFalse(upload.destinationShownProperty().get()),
                () -> assertEquals("", upload.confirmServerProperty().get()),
                () -> assertFalse(upload.send(), "the destination was used up"),
                () -> assertEquals(1, port.sends().size()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.NO_KEY,
                                upload.keyStatusProperty().get(),
                                "the typed key was used once and dropped"),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.NO_KEY,
                                upload.prepareRefusalProperty().get()));
        assertNoKeyPublished();
    }

    @Test
    @DisplayName(
            "a filled key field lets Upload be pressed -- the pane takes the key first -- but"
                    + " nothing is sent without a key")
    void aFilledKeyField() {
        open(NEWER);
        upload.editServer(SERVER);
        upload.editProject("7");
        settle();
        assertEquals(LimelightUploadViewModel.NO_KEY, upload.prepareRefusalProperty().get());
        upload.keyFieldFilled(true);
        assertEquals("", upload.prepareRefusalProperty().get());
        port.prepare(() -> preview(NEWER));
        assertTrue(upload.prepare());
        settle();
        assertFalse(upload.send(), "no key was taken");
        assertAll(
                () ->
                        assertEquals(
                                "Nothing was sent: " + LimelightUploadViewModel.NO_KEY,
                                upload.outcomeProperty().get()),
                () -> assertEquals(List.of(), port.sends()),
                () -> assertEquals(List.of(), port.admitted()));
        upload.keyFieldFilled(false);
        assertEquals(LimelightUploadViewModel.NO_KEY, upload.prepareRefusalProperty().get());
    }

    @Test
    @DisplayName("the bytes sent move the progress bar while the upload runs")
    void progress() {
        shown();
        port.send(
                () -> {
                    LimelightUploadWatcher watcher = port.sends().get(0).watcher();
                    watcher.bytesSent(50, 200);
                    watcher.bytesSent(10, 0);
                    watcher.bytesSent(300, 200);
                    watcher.bytesSent(20, 200);
                    return submitted("");
                });
        upload.send();
        assertEquals(0.0, upload.progressProperty().get());
        background.drain();
        ui.runFirst();
        assertEquals(0.25, upload.progressProperty().get());
        ui.runFirst();
        assertEquals(1.0, upload.progressProperty().get(), "an empty file is all sent");
        ui.runFirst();
        ui.runFirst();
        assertEquals(0.1, upload.progressProperty().get());
        ui.runFirst();
        assertEquals(1.0, upload.progressProperty().get(), "submitted: all of it");
    }

    @Test
    @DisplayName("the log keeps the newest lines")
    void theLogIsBounded() {
        shown();
        port.send(
                () -> {
                    LimelightUploadWatcher watcher = port.sends().get(0).watcher();
                    for (int line = 0; line < LimelightUploadViewModel.LOG_LINES + 2; line++) {
                        watcher.line("line " + line);
                    }
                    return submitted("");
                });
        upload.send();
        settle();
        String[] lines = upload.logProperty().get().split("\n");
        assertAll(
                () -> assertEquals(LimelightUploadViewModel.LOG_LINES, lines.length),
                () -> assertEquals("line 2", lines[0]),
                () -> assertEquals("line " + (LimelightUploadViewModel.LOG_LINES + 1), lines[499]));
    }

    @Test
    @DisplayName(
            "a changed server, project, file, key or run withdraws the destination shown; nothing"
                    + " is sent")
    void aChangeWithdrawsTheDestination() {
        List<Runnable> changes =
                List.of(
                        () -> upload.editServer("https://other.example.org/limelight"),
                        () -> upload.editProject("8"),
                        () -> upload.chooseFile(OLDER.toString()),
                        () -> upload.takeKey("a-new-key-0123456"));
        for (Runnable change : changes) {
            rebuild();
            shown();
            change.run();
            settle();
            assertAll(
                    () -> assertFalse(upload.destinationShownProperty().get()),
                    () -> assertFalse(upload.sendEnabledProperty().get()),
                    () -> assertEquals("", upload.confirmServerProperty().get()),
                    () -> assertEquals("", upload.confirmProjectProperty().get()),
                    () -> assertEquals("", upload.confirmPageProperty().get()),
                    () -> assertEquals("", upload.confirmFileProperty().get()),
                    () -> assertEquals("", upload.confirmSizeProperty().get()),
                    () -> assertEquals("", upload.confirmSha256Property().get()),
                    () ->
                            assertEquals(
                                    LimelightUploadViewModel.DESTINATION_CHANGED,
                                    upload.outcomeProperty().get()));
            assertFalse(upload.send());
            settle();
            assertEquals(List.of(), port.sends());
        }
    }

    /** A fresh section, for a test that repeats itself. */
    private void rebuild() {
        results.close();
        try {
            build();
        } catch (IOException unbuildable) {
            throw new AssertionError(unbuildable);
        }
    }

    @Test
    @DisplayName("the same text committed again withdraws nothing")
    void theSameTextChangesNothing() {
        shown();
        upload.editServer(SERVER);
        upload.editProject("7");
        assertTrue(upload.chooseFile(NEWER.toString()));
        settle();
        assertTrue(upload.destinationShownProperty().get());
        assertEquals(LimelightUploadViewModel.CHECK_AND_SEND, upload.outcomeProperty().get());
    }

    @Test
    @DisplayName("choosing another run withdraws the destination and reads that run's files")
    void anotherRun() {
        shown();
        ResultsRun other =
                new ResultsRun(
                        new RunId("run-2"),
                        CREATED.plusSeconds(60),
                        AttemptOutcome.SUCCEEDED,
                        EnumSet.of(TableKind.TARGET_PSMS),
                        false);
        runs.add(other);
        port.files("run-2", List.of());
        results.refresh();
        settle();
        results.choose(other);
        settle();
        assertAll(
                () -> assertFalse(upload.destinationShownProperty().get()),
                () -> assertFalse(upload.send()),
                () -> assertEquals(List.of(), port.sends()),
                () -> assertEquals("", upload.chosenFileProperty().get()),
                () ->
                        assertTrue(
                                upload.filesStatusProperty()
                                        .get()
                                        .startsWith("Nothing can be uploaded from run run-2 yet")));
    }

    @Test
    @DisplayName("a destination still being checked when an input changes is dropped on arrival")
    void aCheckOvertakenByAChangeIsDropped() {
        ready();
        port.prepare(() -> preview(NEWER));
        upload.prepare();
        upload.editProject("8");
        settle();
        assertAll(
                () -> assertFalse(upload.destinationShownProperty().get()),
                () -> assertEquals("", upload.confirmProjectProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.DESTINATION_CHANGED,
                                upload.outcomeProperty().get()),
                () -> assertFalse(upload.send()),
                () -> assertEquals(List.of(), port.sends()));
    }

    @Test
    @DisplayName("Cancel withdraws the destination shown and nothing is sent")
    void cancel() {
        shown();
        assertTrue(upload.cancel());
        assertAll(
                () ->
                        assertEquals(
                                LimelightUploadViewModel.CANCELLED, upload.outcomeProperty().get()),
                () -> assertFalse(upload.destinationShownProperty().get()),
                () -> assertFalse(upload.cancelEnabledProperty().get()),
                () -> assertFalse(upload.cancel()),
                () -> assertFalse(upload.send()));
        settle();
        assertEquals(List.of(), port.sends());
        assertTrue(upload.prepareEnabledProperty().get(), "the key is still held for a new Upload");
    }

    @Test
    @DisplayName("a destination the workflow refuses is shown with its reason")
    void aRefusedDestination() {
        ready();
        port.prepare(
                () -> {
                    throw new LimelightUploadRefusedException(
                            LimelightUploadRefusedException.Reason.FILE_CHANGED,
                            NEWER + " has changed since its conversion recorded it");
                });
        upload.prepare();
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "The destination was not accepted, and nothing was sent: "
                                        + NEWER
                                        + " has changed since its conversion recorded it",
                                upload.outcomeProperty().get()),
                () -> assertFalse(upload.destinationShownProperty().get()),
                () -> assertFalse(upload.cancelEnabledProperty().get()),
                () -> assertTrue(upload.prepareEnabledProperty().get()));
    }

    @Test
    @DisplayName("a refusal shows the server's step, status, flags and words; no tracking id")
    void aRefusal() {
        shown();
        port.send(
                () ->
                        new LimelightUploaded(
                                LimelightUploaded.Kind.REFUSED,
                                "auth-test",
                                "",
                                "",
                                SERVER + "/d/pg/project/7",
                                200,
                                List.of("userSubmitImportProgramKeyNotFound", "projectIdNotFound"),
                                "the key is not known to this server",
                                ROOT.resolve("logs/limelight-upload.1.log"),
                                ""));
        upload.send();
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "The Limelight server refused the upload at step auth-test (HTTP"
                                        + " 200), and no import was queued. Its flags:"
                                        + " userSubmitImportProgramKeyNotFound,"
                                        + " projectIdNotFound. The server said: the key is not"
                                        + " known to this server",
                                upload.outcomeProperty().get()),
                () -> assertEquals("", upload.trackingIdProperty().get()),
                () -> assertEquals("", upload.importerSubDirProperty().get()),
                () ->
                        assertEquals(
                                "The upload's log: " + ROOT.resolve("logs/limelight-upload.1.log"),
                                upload.logPathProperty().get()));
    }

    @Test
    @DisplayName("a refusal with no flag and no message says so")
    void aBareRefusal() {
        shown();
        port.send(
                () ->
                        new LimelightUploaded(
                                LimelightUploaded.Kind.REFUSED,
                                "final-submit",
                                "",
                                "",
                                SERVER + "/d/pg/project/7",
                                200,
                                List.of(),
                                "",
                                ROOT.resolve("l.log"),
                                ""));
        upload.send();
        settle();
        assertEquals(
                "The Limelight server refused the upload at step final-submit (HTTP 200), and no"
                        + " import was queued. Its flags: none. The server said: (no message)",
                upload.outcomeProperty().get());
    }

    @Test
    @DisplayName("an upload with no answer says where and what went wrong")
    void aFailure() {
        shown();
        port.send(
                () ->
                        new LimelightUploaded(
                                LimelightUploaded.Kind.FAILED,
                                "upload-file",
                                "",
                                "",
                                SERVER + "/d/pg/project/7",
                                0,
                                List.of(),
                                "the connection failed: reset",
                                ROOT.resolve("l.log"),
                                ""));
        upload.send();
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "The upload failed at step upload-file with no answer from the"
                                        + " Limelight server, and no import was queued: the"
                                        + " connection failed: reset",
                                upload.outcomeProperty().get()),
                () -> assertEquals("", upload.trackingIdProperty().get()),
                () -> assertEquals(0.0, upload.progressProperty().get()));
    }

    @Test
    @DisplayName(
            "an upload refused before sending says nothing was sent; one whose record failed says"
                    + " it did not complete")
    void sendThrows() {
        shown();
        port.send(
                () -> {
                    throw new LimelightUploadRefusedException(
                            LimelightUploadRefusedException.Reason.RUN_EXECUTING,
                            "run run-1 is executing");
                });
        upload.send();
        settle();
        assertEquals("Nothing was sent: run run-1 is executing", upload.outcomeProperty().get());

        upload.takeKey(KEY);
        port.prepare(() -> preview(NEWER));
        upload.prepare();
        settle();
        port.send(
                () -> {
                    throw new IOException("the event log cannot be written");
                });
        upload.send();
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "The upload did not complete: the event log cannot be written",
                                upload.outcomeProperty().get()),
                () -> assertFalse(upload.sendingProperty().get()),
                () -> assertEquals(2, port.sends().size()));
    }

    @Test
    @DisplayName(
            "remember is offered only with a typed key, passed to the port, and a key kept for the"
                    + " server is then used without being typed or shown")
    void rememberAndAKeptKey() {
        open(NEWER);
        upload.editServer(SERVER);
        upload.editProject("7");
        settle();
        assertFalse(upload.setRemember(true), "no key was typed");
        assertFalse(upload.rememberKeyProperty().get());
        upload.takeKey(KEY);
        assertTrue(upload.setRemember(true));
        assertTrue(upload.rememberKeyProperty().get());
        port.prepare(() -> preview(NEWER));
        upload.prepare();
        settle();
        String kept = "The key is kept for " + SERVER + ".";
        port.send(
                () -> {
                    port.keep(SERVER, KEY);
                    return submitted(kept);
                });
        upload.send();
        settle();
        assertAll(
                () -> assertTrue(port.sends().get(0).remember()),
                () ->
                        assertTrue(
                                upload.outcomeProperty()
                                        .get()
                                        .endsWith("/d/pg/project/7. " + kept)),
                () -> assertFalse(upload.rememberKeyProperty().get()),
                () -> assertFalse(upload.rememberEnabledProperty().get()),
                () ->
                        assertEquals(
                                kept
                                        + " A submit key for "
                                        + SERVER
                                        + " is kept on this computer; it is used without being"
                                        + " shown.",
                                upload.keyStatusProperty().get()),
                () -> assertTrue(upload.forgetEnabledProperty().get()),
                () -> assertEquals("", upload.prepareRefusalProperty().get()));

        port.prepare(() -> preview(NEWER));
        upload.prepare();
        settle();
        port.send(() -> submitted(""));
        upload.send();
        settle();
        assertAll(
                () -> assertEquals(Optional.empty(), port.sends().get(1).typed()),
                () -> assertFalse(port.sends().get(1).remember()));
        assertNoKeyPublished();
    }

    @Test
    @DisplayName("remember switched off again is passed as off")
    void rememberOff() {
        shown();
        assertTrue(upload.setRemember(true));
        assertTrue(upload.setRemember(false));
        port.send(() -> submitted(""));
        upload.send();
        settle();
        assertFalse(port.sends().get(0).remember());
    }

    @Test
    @DisplayName(
            "a key kept for the server is found when the server is typed, and can be forgotten")
    void forget() {
        port.keep(SERVER, KEY);
        open(NEWER);
        assertFalse(upload.forgetKey(), "no server yet");
        upload.editServer(SERVER);
        upload.editProject("7");
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "A submit key for "
                                        + SERVER
                                        + " is kept on this computer; it is used without being"
                                        + " shown.",
                                upload.keyStatusProperty().get()),
                () -> assertTrue(upload.forgetEnabledProperty().get()),
                () -> assertTrue(upload.prepareEnabledProperty().get()));
        port.prepare(() -> preview(NEWER));
        upload.prepare();
        settle();
        assertTrue(upload.forgetKey());
        settle();
        assertAll(
                () -> assertEquals(List.of(SERVER), port.forgotten()),
                () ->
                        assertEquals(
                                "The submit key kept for "
                                        + SERVER
                                        + " was forgotten. "
                                        + LimelightUploadViewModel.NO_KEY,
                                upload.keyStatusProperty().get()),
                () -> assertFalse(upload.forgetEnabledProperty().get()),
                () -> assertFalse(upload.destinationShownProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.NO_KEY,
                                upload.prepareRefusalProperty().get()));
        assertFalse(upload.forgetKey());
    }

    @Test
    @DisplayName("a store that cannot forget or be read says so, naming the server only")
    void storeFailures() {
        port.keep(SERVER, KEY);
        open(NEWER);
        upload.editServer(SERVER);
        settle();
        port.forgetFails("the keyring did not answer");
        assertTrue(upload.forgetKey());
        settle();
        assertTrue(
                upload.keyStatusProperty()
                        .get()
                        .startsWith(
                                "The submit key kept for "
                                        + SERVER
                                        + " could not be forgotten: the keyring did not answer"),
                upload.keyStatusProperty().get());

        port.keptFails("locked");
        upload.editServer("https://other.example.org");
        settle();
        assertEquals(
                "Whether a submit key is kept for https://other.example.org could not be read:"
                        + " locked "
                        + LimelightUploadViewModel.NO_KEY,
                upload.keyStatusProperty().get());
    }

    @Test
    @DisplayName("where a kept key is held is the port's explanation, or why it is not known")
    void theStore() {
        assertEquals(LimelightUploadViewModel.STORE_NOT_READ, upload.storeTextProperty().get());
        port.store("The key is kept for this session only: secret-tool is not installed.");
        upload.refresh();
        settle();
        assertEquals(
                "The key is kept for this session only: secret-tool is not installed.",
                upload.storeTextProperty().get());
        port.storeFails("no process service");
        upload.refresh();
        settle();
        assertEquals(
                "Where a kept submit key is held could not be decided: no process service",
                upload.storeTextProperty().get());
    }

    @Test
    @DisplayName("an executing run is refused, with the reason")
    void anExecutingRun() {
        open(NEWER);
        upload.editServer(SERVER);
        upload.editProject("7");
        upload.takeKey(KEY);
        settle();
        runs.executing(true);
        results.refresh();
        settle();
        assertEquals(
                "Run run-1 is executing: its results are not final. Upload when it has ended.",
                upload.prepareRefusalProperty().get());
        assertFalse(upload.prepare());
        assertEquals(List.of(), port.prepares());
    }

    @Test
    @DisplayName("a refresh reads again whether a key is kept for the server")
    void refreshReadsTheKeyAgain() {
        open(NEWER);
        upload.editServer(SERVER);
        settle();
        assertEquals(LimelightUploadViewModel.NO_KEY, upload.keyStatusProperty().get());
        port.keep(SERVER, KEY);
        upload.refresh();
        settle();
        assertTrue(upload.forgetEnabledProperty().get());
    }

    @Test
    @DisplayName("an answer about an older server is dropped")
    void anOldServerAnswerIsDropped() {
        port.keep(SERVER, KEY);
        open(NEWER);
        upload.editServer(SERVER);
        upload.editServer("https://other.example.org");
        background.runLast();
        background.runFirst();
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "Uploads go to https://other.example.org.",
                                upload.serverStatusProperty().get()),
                () -> assertFalse(upload.forgetEnabledProperty().get()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.NO_KEY, upload.keyStatusProperty().get()));
    }

    @Test
    @DisplayName("an answer about an older project is dropped")
    void anOldProjectAnswerIsDropped() {
        open(NEWER);
        upload.editProject("8");
        upload.editProject("9");
        background.runLast();
        background.runFirst();
        settle();
        assertEquals("Project 9.", upload.projectStatusProperty().get());
    }

    @Test
    @DisplayName("files read for a run no longer chosen are dropped")
    void anOldFileAnswerIsDropped() {
        results.refresh();
        converted.set(OLDER.toString());
        port.files(RUN, List.of(OLDER));
        background.runLast();
        port.files(RUN, List.of(NEWER, OLDER));
        background.runFirst();
        settle();
        assertAll(
                () -> assertEquals(2, port.fileReads()),
                () -> assertEquals(List.of(OLDER.toString()), upload.fileChoicesProperty().get()));
    }

    // ------------------------------------------------------------------------------- helpers --

    private static final class Queue implements Executor {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        int pending() {
            return tasks.size();
        }

        void runFirst() {
            tasks.pollFirst().run();
        }

        void runLast() {
            tasks.pollLast().run();
        }

        void drain() {
            while (!tasks.isEmpty()) {
                runFirst();
            }
        }
    }

    /** A Results port with one run and its target PSM table, and runs a test adds. */
    private static final class Runs implements ResultsPort {

        private final Path project;

        private final Path psms;

        private final List<ResultsRun> extra = new ArrayList<>();

        private boolean executing;

        Runs(Path project, Path psms) {
            this.project = project;
            this.psms = psms;
        }

        void executing(boolean now) {
            executing = now;
        }

        void add(ResultsRun run) {
            extra.add(run);
        }

        @Override
        public List<ResultsRun> runs() {
            List<ResultsRun> all = new ArrayList<>();
            all.add(
                    new ResultsRun(
                            new RunId(RUN),
                            CREATED,
                            executing ? AttemptOutcome.RUNNING : AttemptOutcome.SUCCEEDED,
                            EnumSet.of(TableKind.TARGET_PSMS),
                            executing));
            all.addAll(extra);
            return all;
        }

        @Override
        public OpenedResults open(RunId run) throws IOException {
            RunLayout layout = new RunLayout(project.resolve("runs").resolve(run.value()));
            Map<TableKind, ResultStore> stores = new EnumMap<>(TableKind.class);
            stores.put(
                    TableKind.TARGET_PSMS,
                    ResultStores.open(
                            psms,
                            TableKind.TARGET_PSMS,
                            layout.resultIndexDirectory(),
                            new StreamingHashService()));
            return new OpenedResults(
                    run, stores, Optional.empty(), Map.of(), ViewStateStore.read(layout));
        }

        @Override
        public void close(OpenedResults results) {
            for (ResultStore store : results.stores().values()) {
                try {
                    store.close();
                } catch (IOException ignored) {
                    // closed already
                }
            }
        }

        @Override
        public void saveViewState(RunId run, DisplayFilters filters) {
            // nothing is saved here
        }

        @Override
        public TableExport exportTable(
                RunId run, TableKind kind, QValueFilter filter, Category category)
                throws IOException {
            throw new IOException("no exports here");
        }

        @Override
        public WeightsExport exportWeights(RunId run, WeightsSummary weights) throws IOException {
            throw new IOException("no exports here");
        }
    }
}
