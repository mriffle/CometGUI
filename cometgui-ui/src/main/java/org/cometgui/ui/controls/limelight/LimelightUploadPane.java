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

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.params.Texts;
import org.cometgui.ui.viewmodel.limelight.LimelightUploadViewModel;

/**
 * The Limelight section's upload half, shown in the conversion half's upload area ({@link
 * LimelightPane#showUpload}): which converted file, the server, the project and the submit key;
 * Upload, which checks the destination and shows it -- the server, the project and its page, the
 * file, its size and its SHA-256 -- and sends nothing; "Send to Limelight" and Cancel; the upload's
 * live log and progress; and the server's tracking id, importer directory, the project page and the
 * log's path.
 *
 * <p><strong>The key field.</strong> A {@link PasswordField}, which never shows what is typed. Its
 * text is bound to nothing: when the field is committed (Enter, leaving it, or Upload) its text is
 * handed to the view-model once -- which admits it to the session at once -- and the field is
 * cleared, so the key is held as a credential and nowhere in the scene.
 *
 * <p>Every control only calls the view-model, and is disabled while the view-model says it cannot
 * be used, so a keyboard user never reaches one that would only refuse.
 */
public final class LimelightUploadPane extends VBox {

    /**
     * The upload half over its view-model.
     *
     * @param upload the upload half's state and actions
     */
    public LimelightUploadPane(LimelightUploadViewModel upload) {
        super(6);
        Objects.requireNonNull(upload, "upload");
        setId(UiIds.LIMELIGHT_UPLOAD_CONTENT);

        ComboBox<String> file =
                new ComboBox<>(
                        FXCollections.observableArrayList(upload.fileChoicesProperty().get()));
        file.setId(UiIds.LIMELIGHT_UPLOAD_FILE);
        named(file, "Limelight XML file to upload, newest first");
        file.setValue(upload.chosenFileProperty().get());
        upload.fileChoicesProperty()
                .addListener(
                        (observable, before, after) -> {
                            file.getItems().setAll(after);
                            file.setValue(upload.chosenFileProperty().get());
                        });
        upload.chosenFileProperty()
                .addListener((observable, before, after) -> file.setValue(after));
        file.setOnAction(
                event -> {
                    String chosen = file.getValue();
                    if (chosen != null && !chosen.equals(upload.chosenFileProperty().get())) {
                        upload.chooseFile(chosen);
                    }
                    // the box shows what the section holds, never only what was picked
                    file.setValue(upload.chosenFileProperty().get());
                });
        file.disableProperty()
                .bind(
                        upload.inputsEnabledProperty()
                                .not()
                                .or(
                                        Bindings.createBooleanBinding(
                                                () -> upload.fileChoicesProperty().get().isEmpty(),
                                                upload.fileChoicesProperty())));
        Label filesStatus =
                Texts.label(
                        UiIds.LIMELIGHT_UPLOAD_FILES_STATUS,
                        upload.filesStatusProperty().get(),
                        "which files can be uploaded");
        follow(filesStatus, upload.filesStatusProperty());

        TextField server =
                field(
                        UiIds.LIMELIGHT_UPLOAD_SERVER,
                        "Limelight server address",
                        upload.serverTextProperty());
        server.setPromptText("https://limelight.example.org/limelight");
        server.setAccessibleHelp(LimelightUploadViewModel.SERVER_HELP);
        commit(server, () -> upload.editServer(server.getText()));
        server.disableProperty().bind(upload.inputsEnabledProperty().not());
        Label serverStatus =
                Texts.label(
                        UiIds.LIMELIGHT_UPLOAD_SERVER_STATUS,
                        upload.serverStatusProperty().get(),
                        "the server address");
        follow(serverStatus, upload.serverStatusProperty());

        TextField project =
                field(
                        UiIds.LIMELIGHT_UPLOAD_PROJECT,
                        "Limelight project id",
                        upload.projectTextProperty());
        project.setPromptText("the number in the project's address");
        project.setAccessibleHelp(LimelightUploadViewModel.PROJECT_HELP);
        commit(project, () -> upload.editProject(project.getText()));
        project.disableProperty().bind(upload.inputsEnabledProperty().not());
        Label projectStatus =
                Texts.label(
                        UiIds.LIMELIGHT_UPLOAD_PROJECT_STATUS,
                        upload.projectStatusProperty().get(),
                        "the project id");
        follow(projectStatus, upload.projectStatusProperty());

        PasswordField key = new PasswordField();
        key.setId(UiIds.LIMELIGHT_UPLOAD_KEY);
        named(key, "Limelight submit-import key, never shown");
        key.setPromptText("submit-import key");
        key.setAccessibleHelp(
                "Your Limelight submit-import key. What you type is never shown or written"
                        + " anywhere; it is taken when you press Enter, leave the field or press"
                        + " Upload, and the field is then emptied.");
        Runnable takeKey =
                () -> {
                    String typed = key.getText();
                    if (!typed.isEmpty()) {
                        key.clear();
                        upload.takeKey(typed);
                    }
                };
        commit(key, takeKey);
        // the view-model hears only whether the field holds something, never what
        key.textProperty()
                .addListener(
                        (observable, before, after) -> upload.keyFieldFilled(!after.isEmpty()));
        key.disableProperty().bind(upload.inputsEnabledProperty().not());
        Label keyStatus =
                Texts.label(
                        UiIds.LIMELIGHT_UPLOAD_KEY_STATUS,
                        upload.keyStatusProperty().get(),
                        "which submit key is used");
        follow(keyStatus, upload.keyStatusProperty());

        CheckBox remember = new CheckBox("Remember the key for this server");
        remember.setId(UiIds.LIMELIGHT_UPLOAD_REMEMBER);
        named(remember, "Remember the typed submit key for this server after a submitted upload");
        remember.accessibleHelpProperty().bind(upload.storeTextProperty());
        remember.setSelected(upload.rememberKeyProperty().get());
        remember.disableProperty().bind(upload.rememberEnabledProperty().not());
        remember.setOnAction(
                event -> {
                    upload.setRemember(remember.isSelected());
                    remember.setSelected(upload.rememberKeyProperty().get());
                });
        upload.rememberKeyProperty()
                .addListener((observable, before, after) -> remember.setSelected(after));
        Button forget =
                Texts.button(
                        UiIds.LIMELIGHT_UPLOAD_FORGET,
                        "Forget the kept key",
                        "Forget the submit key kept for this server");
        forget.setOnAction(event -> upload.forgetKey());
        forget.disableProperty().bind(upload.forgetEnabledProperty().not());
        Label store =
                Texts.label(
                        UiIds.LIMELIGHT_UPLOAD_STORE,
                        upload.storeTextProperty().get(),
                        "where a kept key is held");
        follow(store, upload.storeTextProperty());

        Button prepare =
                Texts.button(
                        UiIds.LIMELIGHT_UPLOAD_PREPARE,
                        "Upload...",
                        "Upload: check the destination and show it; nothing is sent yet");
        prepare.setOnAction(
                event -> {
                    takeKey.run();
                    upload.prepare();
                });
        prepare.disableProperty().bind(upload.prepareEnabledProperty().not());
        prepare.accessibleHelpProperty().bind(upload.prepareRefusalProperty());
        Label refusal =
                Texts.label(
                        UiIds.LIMELIGHT_UPLOAD_REFUSAL,
                        upload.prepareRefusalProperty().get(),
                        "a destination can be checked");
        follow(refusal, upload.prepareRefusalProperty());

        Label confirmServer =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_CONFIRM_SERVER,
                        upload.confirmServerProperty(),
                        "no destination is shown");
        Label confirmProject =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_CONFIRM_PROJECT,
                        upload.confirmProjectProperty(),
                        "no project is shown");
        Label confirmPage =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_CONFIRM_PAGE,
                        upload.confirmPageProperty(),
                        "no project page is shown");
        Label confirmFile =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_CONFIRM_FILE,
                        upload.confirmFileProperty(),
                        "no file is shown");
        Label confirmSize =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_CONFIRM_SIZE,
                        upload.confirmSizeProperty(),
                        "no size is shown");
        Label confirmSha256 =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_CONFIRM_SHA256,
                        upload.confirmSha256Property(),
                        "no SHA-256 is shown");
        Button send =
                Texts.button(
                        UiIds.LIMELIGHT_UPLOAD_SEND,
                        "Send to Limelight",
                        "Send to Limelight: upload the file shown to the server and project shown");
        send.setAccessibleHelp(LimelightUploadViewModel.NO_CANCEL);
        send.setOnAction(event -> upload.send());
        send.disableProperty().bind(upload.sendEnabledProperty().not());
        Button cancel =
                Texts.button(
                        UiIds.LIMELIGHT_UPLOAD_CANCEL,
                        "Cancel",
                        "Cancel: withdraw the destination shown; nothing is sent");
        cancel.setOnAction(event -> upload.cancel());
        cancel.disableProperty().bind(upload.cancelEnabledProperty().not());
        VBox destination =
                new VBox(
                        4,
                        caption("Destination: nothing is sent until you press Send to Limelight"),
                        new HBox(8, caption("Server"), confirmServer),
                        new HBox(8, caption("Project"), confirmProject),
                        new HBox(8, caption("Project page"), confirmPage),
                        new HBox(8, caption("File"), confirmFile),
                        new HBox(8, caption("Size"), confirmSize),
                        new HBox(8, caption("SHA-256"), confirmSha256),
                        new HBox(8, send, cancel));
        destination.setId(UiIds.LIMELIGHT_UPLOAD_DESTINATION);
        destination.visibleProperty().bind(upload.destinationShownProperty());
        destination.managedProperty().bind(upload.destinationShownProperty());

        ProgressBar progress = new ProgressBar();
        progress.setId(UiIds.LIMELIGHT_UPLOAD_PROGRESS);
        named(progress, "How much of the file has been sent to Limelight");
        progress.progressProperty().bind(upload.progressProperty());
        TextArea log = new TextArea(upload.logProperty().get());
        log.setId(UiIds.LIMELIGHT_UPLOAD_LOG);
        named(log, "the Limelight upload's log of the last upload");
        log.setEditable(false);
        log.setWrapText(true);
        log.setPrefRowCount(6);
        upload.logProperty()
                .addListener(
                        (observable, before, after) -> {
                            log.setText(after);
                            log.positionCaret(after.length());
                        });
        Label outcome =
                Texts.label(
                        UiIds.LIMELIGHT_UPLOAD_OUTCOME,
                        upload.outcomeProperty().get(),
                        "nothing has been uploaded");
        follow(outcome, upload.outcomeProperty());
        Label trackingId =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_TRACKING_ID,
                        upload.trackingIdProperty(),
                        "no tracking id yet");
        Label importerSubDir =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_IMPORTER_SUBDIR,
                        upload.importerSubDirProperty(),
                        "no importer directory yet");
        Label projectPage =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_PROJECT_PAGE,
                        upload.projectPageProperty(),
                        "no project page yet");
        Label logPath =
                shown(
                        UiIds.LIMELIGHT_UPLOAD_LOG_PATH,
                        upload.logPathProperty(),
                        "no upload log yet");

        getChildren()
                .setAll(
                        List.of(
                                caption("Converted file"),
                                file,
                                filesStatus,
                                new HBox(8, caption("Limelight server"), server),
                                serverStatus,
                                new HBox(8, caption("Project id"), project),
                                projectStatus,
                                new HBox(8, caption("Submit-import key"), key),
                                keyStatus,
                                new HBox(8, remember, forget),
                                store,
                                prepare,
                                refusal,
                                destination,
                                progress,
                                log,
                                outcome,
                                new HBox(8, caption("Tracking id (uploadKey)"), trackingId),
                                new HBox(8, caption("Importer directory"), importerSubDir),
                                new HBox(8, caption("Project page"), projectPage),
                                logPath));
    }

    private static TextField field(String id, String name, ObservableValue<String> shown) {
        TextField field = new TextField(shown.getValue());
        field.setId(id);
        named(field, name);
        shown.addListener(
                (observable, before, after) -> {
                    if (!field.getText().equals(after)) {
                        field.setText(after);
                    }
                });
        return field;
    }

    /** Commits a field's text on Enter and when it loses the focus. */
    private static void commit(TextField field, Runnable commit) {
        field.setOnAction(event -> commit.run());
        field.focusedProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!after) {
                                commit.run();
                            }
                        });
    }

    private static Label shown(String id, ObservableValue<String> text, String fallback) {
        Label label = Texts.label(id, text.getValue(), fallback);
        follow(label, text);
        return label;
    }

    private static Label caption(String shown) {
        Label caption = new Label(shown);
        named(caption, shown);
        return caption;
    }

    private static void follow(Label label, ObservableValue<String> shown) {
        shown.addListener((observable, before, after) -> label.setText(after));
    }
}
