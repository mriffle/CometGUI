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

package org.cometgui.ui.controls.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.ui.controls.AccessibleControls;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.testing.ScriptedLimelightUpload;
import org.cometgui.ui.viewmodel.limelight.LimelightUploadViewModel;
import org.cometgui.ui.viewmodel.limelight.LimelightUploaded;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The upload half's pane shows its view-model and acts only through it: the key field is a password
 * field, taken once and emptied, and the key is in no text of the pane; Upload shows the
 * destination and sends nothing; Cancel withdraws it; a changed field withdraws it; only Send
 * sends; the destination box is shown only with a destination; and every identified control has a
 * name of its own.
 */
class LimelightUploadPaneTest {

    private static final RunId RUN = new RunId("run-1");

    private static final String KEY = "PANESEEDKEY-0c4e1a7b9d2f";

    private static final String SERVER = "https://limelight.example.org/limelight";

    private static final Path ROOT = Path.of("p").toAbsolutePath();

    private static final Path NEWER = ROOT.resolve("runs/run-1/outputs/limelight/limelight-2.xml");

    private static final Path OLDER = ROOT.resolve("runs/run-1/outputs/limelight/limelight-1.xml");

    private ScriptedLimelightUpload port;

    private LimelightUploadViewModel upload;

    private Scene scene;

    @BeforeAll
    static void toolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @BeforeEach
    void open() throws InterruptedException {
        port = new ScriptedLimelightUpload();
        port.files(RUN.value(), List.of(NEWER, OLDER));
        port.prepare(
                () ->
                        new ScriptedLimelightUpload.Preview(
                                RUN,
                                SERVER,
                                7,
                                SERVER + "/d/pg/project/7",
                                NEWER,
                                1234L,
                                "cd".repeat(32)));
        DisplayFiltersViewModel filters = new DisplayFiltersViewModel();
        ResultsViewModel results =
                new ResultsViewModel(
                        new OneRun(),
                        filters,
                        new ResultTableViewModel(filters, Runnable::run, Runnable::run),
                        new WeightsViewModel(),
                        Runnable::run,
                        Runnable::run);
        upload =
                new LimelightUploadViewModel(
                        port, results, new SimpleStringProperty(""), Runnable::run, Runnable::run);
        scene =
                FxToolkit.callOnFxThread(
                        () -> {
                            Scene built = new Scene(new LimelightUploadPane(upload), 1000, 900);
                            upload.refresh();
                            results.refresh();
                            built.getRoot().applyCss();
                            built.getRoot().layout();
                            return built;
                        });
    }

    private <T extends Node> T node(String id) {
        @SuppressWarnings("unchecked")
        T found = (T) scene.lookup("#" + id);
        assertNotNull(found, id);
        return found;
    }

    private String text(String id) throws InterruptedException {
        Node found = node(id);
        return FxToolkit.callOnFxThread(
                () ->
                        found instanceof Labeled labeled
                                ? labeled.getText()
                                : ((TextInputControl) found).getText());
    }

    private void commit(String id, String typed) throws InterruptedException {
        TextField field = node(id);
        FxToolkit.onFxThread(
                () -> {
                    field.setText(typed);
                    field.fireEvent(new ActionEvent());
                });
    }

    private void press(String id) throws InterruptedException {
        Button button = node(id);
        FxToolkit.onFxThread(button::fire);
    }

    private boolean disabled(String id) throws InterruptedException {
        Node found = node(id);
        return FxToolkit.callOnFxThread(found::isDisabled);
    }

    private boolean visible(String id) throws InterruptedException {
        Node found = node(id);
        return FxToolkit.callOnFxThread(found::isVisible);
    }

    /** Every text in the pane: each control's text, prompt, accessible name and help. */
    private List<String> everyText() throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> {
                    List<String> texts = new ArrayList<>();
                    collectTexts(scene.getRoot(), texts);
                    return texts;
                });
    }

    private static void collectTexts(Node node, List<String> texts) {
        if (node instanceof Labeled labeled) {
            texts.add(String.valueOf(labeled.getText()));
        }
        if (node instanceof TextInputControl input) {
            texts.add(String.valueOf(input.getText()));
            texts.add(String.valueOf(input.getPromptText()));
        }
        if (node instanceof ComboBox<?> box) {
            texts.add(String.valueOf(box.getValue()));
            texts.add(String.valueOf(box.getItems()));
        }
        texts.add(String.valueOf(node.getAccessibleText()));
        texts.add(String.valueOf(node.getAccessibleHelp()));
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTexts(child, texts);
            }
        }
    }

    private void ready() throws InterruptedException {
        commit(UiIds.LIMELIGHT_UPLOAD_SERVER, SERVER);
        commit(UiIds.LIMELIGHT_UPLOAD_PROJECT, "7");
        commit(UiIds.LIMELIGHT_UPLOAD_KEY, KEY);
    }

    @Test
    @DisplayName(
            "the key field is a password field; committed, it is admitted once and emptied, and no"
                    + " text of the pane holds the key")
    void theKeyField() throws InterruptedException {
        PasswordField key = node(UiIds.LIMELIGHT_UPLOAD_KEY);
        assertInstanceOf(PasswordField.class, key);
        assertTrue(FxToolkit.callOnFxThread(() -> !key.textProperty().isBound()));
        ready();
        List<String> texts = everyText();
        assertAll(
                () -> assertEquals(List.of(KEY), port.admitted()),
                () -> assertEquals("", FxToolkit.callOnFxThread(key::getText), "emptied"),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.KEY_TYPED,
                                text(UiIds.LIMELIGHT_UPLOAD_KEY_STATUS)),
                () -> assertTrue(texts.size() > 100, "the walk read " + texts.size()),
                () ->
                        assertEquals(
                                List.of(),
                                texts.stream().filter(shown -> shown.contains(KEY)).toList()));
        FxToolkit.onFxThread(() -> key.fireEvent(new ActionEvent()));
        assertEquals(List.of(KEY), port.admitted(), "an empty field is not taken again");
    }

    @Test
    @DisplayName(
            "Upload shows the destination and sends nothing; Cancel withdraws it; a changed field"
                    + " withdraws it; only Send sends")
    void uploadThenSend() throws InterruptedException {
        assertAll(
                () -> assertTrue(disabled(UiIds.LIMELIGHT_UPLOAD_PREPARE)),
                () -> assertFalse(visible(UiIds.LIMELIGHT_UPLOAD_DESTINATION)),
                () -> assertTrue(disabled(UiIds.LIMELIGHT_UPLOAD_SEND)),
                () -> assertTrue(disabled(UiIds.LIMELIGHT_UPLOAD_CANCEL)),
                () -> assertTrue(disabled(UiIds.LIMELIGHT_UPLOAD_REMEMBER)),
                () -> assertTrue(disabled(UiIds.LIMELIGHT_UPLOAD_FORGET)),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.SERVER_HELP,
                                text(UiIds.LIMELIGHT_UPLOAD_REFUSAL)));
        ready();
        assertFalse(disabled(UiIds.LIMELIGHT_UPLOAD_PREPARE));
        assertFalse(disabled(UiIds.LIMELIGHT_UPLOAD_REMEMBER));
        press(UiIds.LIMELIGHT_UPLOAD_PREPARE);
        assertAll(
                () -> assertEquals(1, port.prepares().size()),
                () -> assertEquals(List.of(), port.sends()),
                () -> assertTrue(visible(UiIds.LIMELIGHT_UPLOAD_DESTINATION)),
                () -> assertEquals(SERVER, text(UiIds.LIMELIGHT_UPLOAD_CONFIRM_SERVER)),
                () -> assertEquals("7", text(UiIds.LIMELIGHT_UPLOAD_CONFIRM_PROJECT)),
                () ->
                        assertEquals(
                                SERVER + "/d/pg/project/7",
                                text(UiIds.LIMELIGHT_UPLOAD_CONFIRM_PAGE)),
                () -> assertEquals(NEWER.toString(), text(UiIds.LIMELIGHT_UPLOAD_CONFIRM_FILE)),
                () -> assertEquals("1234 bytes", text(UiIds.LIMELIGHT_UPLOAD_CONFIRM_SIZE)),
                () -> assertEquals("cd".repeat(32), text(UiIds.LIMELIGHT_UPLOAD_CONFIRM_SHA256)),
                () -> assertFalse(disabled(UiIds.LIMELIGHT_UPLOAD_SEND)),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.CHECK_AND_SEND,
                                text(UiIds.LIMELIGHT_UPLOAD_OUTCOME)));

        press(UiIds.LIMELIGHT_UPLOAD_CANCEL);
        assertAll(
                () -> assertFalse(visible(UiIds.LIMELIGHT_UPLOAD_DESTINATION)),
                () -> assertEquals(List.of(), port.sends()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.CANCELLED,
                                text(UiIds.LIMELIGHT_UPLOAD_OUTCOME)));

        press(UiIds.LIMELIGHT_UPLOAD_PREPARE);
        commit(UiIds.LIMELIGHT_UPLOAD_PROJECT, "8");
        assertAll(
                () -> assertFalse(visible(UiIds.LIMELIGHT_UPLOAD_DESTINATION)),
                () -> assertTrue(disabled(UiIds.LIMELIGHT_UPLOAD_SEND)),
                () -> assertEquals(List.of(), port.sends()),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.DESTINATION_CHANGED,
                                text(UiIds.LIMELIGHT_UPLOAD_OUTCOME)));

        port.send(
                () ->
                        new LimelightUploaded(
                                LimelightUploaded.Kind.SUBMITTED,
                                "final-submit",
                                "4100",
                                "4100_x",
                                SERVER + "/d/pg/project/7",
                                0,
                                List.of(),
                                "",
                                ROOT.resolve("l.log"),
                                ""));
        press(UiIds.LIMELIGHT_UPLOAD_PREPARE);
        press(UiIds.LIMELIGHT_UPLOAD_SEND);
        assertAll(
                () -> assertEquals(1, port.sends().size()),
                () -> assertEquals("4100", text(UiIds.LIMELIGHT_UPLOAD_TRACKING_ID)),
                () -> assertEquals("4100_x", text(UiIds.LIMELIGHT_UPLOAD_IMPORTER_SUBDIR)),
                () ->
                        assertEquals(
                                SERVER + "/d/pg/project/7",
                                text(UiIds.LIMELIGHT_UPLOAD_PROJECT_PAGE)),
                () ->
                        assertEquals(
                                "The upload's log: " + ROOT.resolve("l.log"),
                                text(UiIds.LIMELIGHT_UPLOAD_LOG_PATH)),
                () ->
                        assertTrue(
                                text(UiIds.LIMELIGHT_UPLOAD_OUTCOME)
                                        .startsWith("Submitted to Limelight")),
                () -> assertEquals("", text(UiIds.LIMELIGHT_UPLOAD_LOG)));
    }

    @Test
    @DisplayName("Upload pressed with the key still in its field takes the key first")
    void uploadTakesTheKey() throws InterruptedException {
        commit(UiIds.LIMELIGHT_UPLOAD_SERVER, SERVER);
        commit(UiIds.LIMELIGHT_UPLOAD_PROJECT, "7");
        PasswordField key = node(UiIds.LIMELIGHT_UPLOAD_KEY);
        Button prepare = node(UiIds.LIMELIGHT_UPLOAD_PREPARE);
        FxToolkit.onFxThread(
                () -> {
                    key.setText(KEY);
                    prepare.fire();
                });
        assertAll(
                () -> assertEquals(List.of(KEY), port.admitted()),
                () -> assertEquals("", FxToolkit.callOnFxThread(key::getText)),
                () -> assertEquals(1, port.prepares().size()),
                () -> assertTrue(visible(UiIds.LIMELIGHT_UPLOAD_DESTINATION)),
                () -> assertEquals(List.of(), port.sends()));
    }

    @Test
    @DisplayName("the files are offered newest first, and a choice goes through the view-model")
    void theFiles() throws InterruptedException {
        @SuppressWarnings("unchecked")
        ComboBox<String> file = (ComboBox<String>) (ComboBox<?>) node(UiIds.LIMELIGHT_UPLOAD_FILE);
        assertAll(
                () ->
                        assertEquals(
                                List.of(NEWER.toString(), OLDER.toString()),
                                FxToolkit.callOnFxThread(() -> List.copyOf(file.getItems()))),
                () -> assertEquals(NEWER.toString(), FxToolkit.callOnFxThread(file::getValue)),
                () -> assertFalse(disabled(UiIds.LIMELIGHT_UPLOAD_FILE)),
                () ->
                        assertEquals(
                                "2 Limelight XML files were converted from run run-1, newest"
                                        + " first.",
                                text(UiIds.LIMELIGHT_UPLOAD_FILES_STATUS)));
        FxToolkit.onFxThread(() -> file.setValue(OLDER.toString()));
        assertEquals(OLDER.toString(), upload.chosenFileProperty().get());
    }

    @Test
    @DisplayName("remember and forget go through the view-model")
    void rememberAndForget() throws InterruptedException {
        port.keep(SERVER, KEY);
        commit(UiIds.LIMELIGHT_UPLOAD_SERVER, SERVER);
        assertFalse(disabled(UiIds.LIMELIGHT_UPLOAD_FORGET));
        press(UiIds.LIMELIGHT_UPLOAD_FORGET);
        assertEquals(List.of(SERVER), port.forgotten());
        commit(UiIds.LIMELIGHT_UPLOAD_KEY, KEY);
        CheckBox remember = node(UiIds.LIMELIGHT_UPLOAD_REMEMBER);
        FxToolkit.onFxThread(remember::fire);
        assertAll(
                () -> assertTrue(upload.rememberKeyProperty().get()),
                () -> assertTrue(FxToolkit.callOnFxThread(remember::isSelected)));
    }

    @Test
    @DisplayName("every identified control has a name of its own")
    void everyControlIsNamed() throws InterruptedException {
        List<String> unnamed =
                FxToolkit.callOnFxThread(
                        () -> {
                            List<String> found = new ArrayList<>();
                            collect(scene.getRoot(), found);
                            return found;
                        });
        TextArea log = node(UiIds.LIMELIGHT_UPLOAD_LOG);
        assertAll(
                () -> assertEquals(List.of(), unnamed),
                () -> assertFalse(FxToolkit.callOnFxThread(log::isEditable)),
                () ->
                        assertEquals(
                                LimelightUploadViewModel.PROJECT_HELP,
                                FxToolkit.callOnFxThread(
                                        this.<TextField>node(UiIds.LIMELIGHT_UPLOAD_PROJECT)
                                                ::getAccessibleHelp)));
    }

    private static void collect(Node node, List<String> unnamed) {
        // "list-view" is the identifier ComboBoxListViewSkin gives the list it builds
        if (node instanceof Control control
                && control.getId() != null
                && !"list-view".equals(control.getId())) {
            String name = control.getAccessibleText();
            if (name == null || name.isBlank() || AccessibleControls.hasGeneratedName(control)) {
                unnamed.add(control.getId());
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, unnamed);
            }
        }
    }

    /** A Results port listing one run, whose stores this test never needs. */
    private static final class OneRun implements ResultsPort {

        @Override
        public List<ResultsRun> runs() {
            return List.of(
                    new ResultsRun(
                            RUN,
                            Instant.parse("2026-10-12T08:00:00Z"),
                            AttemptOutcome.SUCCEEDED,
                            EnumSet.of(TableKind.TARGET_PSMS),
                            false));
        }

        @Override
        public OpenedResults open(RunId run) throws IOException {
            throw new IOException("no stores in this test");
        }

        @Override
        public void close(OpenedResults opened) {
            // nothing is opened
        }

        @Override
        public void saveViewState(RunId run, DisplayFilters filters) {
            // nothing is saved
        }

        @Override
        public TableExport exportTable(
                RunId run, TableKind kind, QValueFilter filter, Category category)
                throws IOException {
            throw new IOException("no exports");
        }

        @Override
        public WeightsExport exportWeights(RunId run, WeightsSummary weights) throws IOException {
            throw new IOException("no exports");
        }
    }
}
