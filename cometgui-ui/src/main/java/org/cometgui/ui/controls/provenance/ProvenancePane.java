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

package org.cometgui.ui.controls.provenance;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.params.Texts;
import org.cometgui.ui.viewmodel.provenance.ProvenanceViewModel;

/**
 * The Provenance section: every run of the project, and for the chosen one the seven parts the
 * specification's <em>Provenance UI</em> names -- Summary, Tools, Inputs/outputs, Parameters,
 * Timeline, Logs and Warnings -- with Copy MD5 and Copy SHA-256 for the selected tool or file, Copy
 * command for the selected execution, Open file location for the selected tool, file or log, Export
 * JSON, Export RST (with Replace once a refusal to overwrite has been read), and Verify against
 * disk with its Cancel.
 *
 * <p>Every control only calls the view-model; nothing is read, parsed, hashed, written or launched
 * from here. The one thing this pane does itself is put the text a copy returned on the system
 * clipboard, which only the interface can reach. Every row list is a list a keyboard user moves
 * through with the arrow keys, each row read out as the text it shows; the actions below it act on
 * the selected row and are disabled while none is selected.
 */
public final class ProvenancePane extends ScrollPane {

    private final ProvenanceViewModel provenance;

    private final ComboBox<String> run = new ComboBox<>();

    /** Set while the pane moves the run selector itself, so that it does not choose again. */
    private boolean updating;

    /**
     * The pane over its view-model.
     *
     * @param provenance the section's state and actions
     */
    public ProvenancePane(ProvenanceViewModel provenance) {
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        setId(UiIds.PROVENANCE_PANE);
        named(this, "the Provenance section's content");
        setFitToWidth(true);

        run.setId(UiIds.PROVENANCE_RUN);
        named(run, "The run whose provenance is shown");
        run.setMaxWidth(Double.MAX_VALUE);
        run.getSelectionModel()
                .selectedIndexProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating && after.intValue() >= 0) {
                                provenance.choose(after.intValue());
                            }
                        });
        provenance.runsProperty().addListener((observable, before, after) -> showRuns());
        provenance.chosenIndexProperty().addListener((observable, before, after) -> showRuns());
        showRuns();
        Button refresh =
                Texts.button(
                        UiIds.PROVENANCE_REFRESH,
                        "Read the runs again",
                        "Read the project's runs again");
        refresh.setOnAction(event -> provenance.refresh());
        Label runsStatus =
                text(
                        UiIds.PROVENANCE_RUNS_STATUS,
                        provenance.runsStatusProperty(),
                        "the state of the run list");
        Label record =
                text(UiIds.PROVENANCE_RECORD, provenance.recordTextProperty(), "no run is shown");
        Label summary =
                text(
                        UiIds.PROVENANCE_SUMMARY,
                        provenance.summaryTextProperty(),
                        "no run's summary is shown");

        ListView<String> tools =
                rows(
                        UiIds.PROVENANCE_TOOLS,
                        "The tools the run used: name, version, managed or local, path, MD5,"
                                + " SHA-256 and capabilities",
                        provenance.toolsProperty(),
                        5);
        Button toolMd5 =
                copy(
                        UiIds.PROVENANCE_TOOL_COPY_MD5,
                        "Copy MD5",
                        "Copy the selected tool's MD5",
                        tools,
                        provenance::copyToolMd5);
        Button toolSha256 =
                copy(
                        UiIds.PROVENANCE_TOOL_COPY_SHA256,
                        "Copy SHA-256",
                        "Copy the selected tool's SHA-256",
                        tools,
                        provenance::copyToolSha256);
        Button toolLocation =
                act(
                        UiIds.PROVENANCE_TOOL_OPEN_LOCATION,
                        "Open file location",
                        "Show the selected tool's executable in the file manager",
                        tools,
                        provenance::openToolLocation);

        ListView<String> files =
                rows(
                        UiIds.PROVENANCE_FILES,
                        "The run's input and output files: role, status, path, size, MD5 and"
                                + " SHA-256; a partial file says PARTIAL first",
                        provenance.filesProperty(),
                        6);
        Button fileMd5 =
                copy(
                        UiIds.PROVENANCE_FILE_COPY_MD5,
                        "Copy MD5",
                        "Copy the selected file's MD5",
                        files,
                        provenance::copyFileMd5);
        Button fileSha256 =
                copy(
                        UiIds.PROVENANCE_FILE_COPY_SHA256,
                        "Copy SHA-256",
                        "Copy the selected file's SHA-256",
                        files,
                        provenance::copyFileSha256);
        Button fileLocation =
                act(
                        UiIds.PROVENANCE_FILE_OPEN_LOCATION,
                        "Open file location",
                        "Show the selected file in the file manager",
                        files,
                        provenance::openFileLocation);

        Label cometParamsStatus =
                text(
                        UiIds.PROVENANCE_COMET_PARAMS_STATUS,
                        provenance.cometParamsStatusProperty(),
                        "no archived Comet parameter file is shown");
        TextArea cometParams = new TextArea(provenance.cometParamsTextProperty().get());
        cometParams.setId(UiIds.PROVENANCE_COMET_PARAMS);
        named(cometParams, "The exact Comet parameter file the run executed, read-only");
        cometParams.setEditable(false);
        cometParams.setPrefRowCount(10);
        provenance
                .cometParamsTextProperty()
                .addListener((observable, before, after) -> cometParams.setText(after));
        Label percolatorSettings =
                text(
                        UiIds.PROVENANCE_PERCOLATOR_SETTINGS,
                        provenance.percolatorSettingsTextProperty(),
                        "no Percolator settings are shown");
        ListView<String> parameters =
                rows(
                        UiIds.PROVENANCE_PARAMETERS,
                        "Each Comet parameter: its value, origin, release default and preset"
                                + " value, with every difference named",
                        provenance.parametersProperty(),
                        6);
        Label presets =
                text(
                        UiIds.PROVENANCE_PRESETS,
                        provenance.presetsTextProperty(),
                        "no presets are shown");
        Label notes =
                text(
                        UiIds.PROVENANCE_PARAMETER_NOTES,
                        provenance.parameterNotesProperty(),
                        "no notes about the parameters");

        ListView<String> executions =
                rows(
                        UiIds.PROVENANCE_EXECUTIONS,
                        "The run's process executions in order: stage, outcome, exit status,"
                                + " times and command",
                        provenance.executionsProperty(),
                        5);
        Button command =
                copy(
                        UiIds.PROVENANCE_EXECUTION_COPY_COMMAND,
                        "Copy command",
                        "Copy the selected execution's command",
                        executions,
                        provenance::copyCommand);
        ListView<String> events =
                rows(
                        UiIds.PROVENANCE_EVENTS,
                        "The run's event log, in order",
                        provenance.eventsProperty(),
                        6);

        ListView<String> logs =
                rows(
                        UiIds.PROVENANCE_LOGS,
                        "The run's archived process logs, with their paths and digests",
                        provenance.logsProperty(),
                        4);
        Button logLocation =
                act(
                        UiIds.PROVENANCE_LOG_OPEN_LOCATION,
                        "Open file location",
                        "Show the selected log in the file manager",
                        logs,
                        provenance::openLogLocation);

        ListView<String> warnings =
                rows(
                        UiIds.PROVENANCE_WARNINGS,
                        "The run's warnings, grouped by where each came from",
                        provenance.warningsProperty(),
                        4);
        Label actionStatus =
                text(
                        UiIds.PROVENANCE_ACTION_STATUS,
                        provenance.actionStatusProperty(),
                        "nothing has been copied or opened");

        Button exportJson =
                Texts.button(
                        UiIds.PROVENANCE_EXPORT_JSON,
                        "Export provenance JSON",
                        "Export the shown run's provenance record as JSON");
        exportJson.setOnAction(event -> provenance.exportJson());
        exportJson.disableProperty().bind(provenance.exportEnabledProperty().not());
        Button exportRst =
                Texts.button(
                        UiIds.PROVENANCE_EXPORT_RST,
                        "Export provenance RST",
                        "Export the shown run's provenance report as RST");
        exportRst.setOnAction(event -> provenance.exportRst());
        exportRst.disableProperty().bind(provenance.exportEnabledProperty().not());
        Button replace =
                Texts.button(
                        UiIds.PROVENANCE_EXPORT_REPLACE,
                        "Replace the existing file",
                        "Replace the existing file the last export was refused for");
        replace.setOnAction(event -> provenance.replaceExisting());
        replace.disableProperty().bind(provenance.replaceOfferedProperty().not());
        Label exportStatus =
                text(
                        UiIds.PROVENANCE_EXPORT_STATUS,
                        provenance.exportStatusProperty(),
                        "nothing has been exported");

        Button verify =
                Texts.button(
                        UiIds.PROVENANCE_VERIFY,
                        "Verify against disk",
                        "Hash every file the record lists again and compare it with the record");
        verify.setOnAction(event -> provenance.verify());
        verify.disableProperty().bind(provenance.verifyEnabledProperty().not());
        Button cancelVerify =
                Texts.button(
                        UiIds.PROVENANCE_VERIFY_CANCEL,
                        "Cancel the verification",
                        "Stop the running verification against disk");
        cancelVerify.setOnAction(event -> provenance.cancelVerification());
        cancelVerify.disableProperty().bind(provenance.verifyingProperty().not());
        Label verifyStatus =
                text(
                        UiIds.PROVENANCE_VERIFY_STATUS,
                        provenance.verifyStatusProperty(),
                        "nothing has been verified");
        ListView<String> verification =
                rows(
                        UiIds.PROVENANCE_VERIFICATION,
                        "What the last verification found, one row per file, the outcome first",
                        provenance.verificationProperty(),
                        6);

        VBox content =
                new VBox(
                        6,
                        caption("Run"),
                        run,
                        new HBox(8, refresh, runsStatus),
                        record,
                        caption("Summary"),
                        summary,
                        caption("Tools"),
                        tools,
                        new HBox(8, toolMd5, toolSha256, toolLocation),
                        caption("Inputs and outputs"),
                        files,
                        new HBox(8, fileMd5, fileSha256, fileLocation),
                        caption("Parameters"),
                        cometParamsStatus,
                        cometParams,
                        percolatorSettings,
                        parameters,
                        presets,
                        notes,
                        caption("Timeline"),
                        executions,
                        command,
                        events,
                        caption("Logs"),
                        logs,
                        logLocation,
                        caption("Warnings"),
                        warnings,
                        actionStatus,
                        caption("Export"),
                        new HBox(8, exportJson, exportRst, replace),
                        exportStatus,
                        caption("Verify against disk"),
                        new HBox(8, verify, cancelVerify),
                        verifyStatus,
                        verification);
        setContent(content);
    }

    private void showRuns() {
        updating = true;
        try {
            List<String> listed = provenance.runsProperty().get();
            if (!run.getItems().equals(listed)) {
                run.getItems().setAll(listed);
            }
            int chosen = provenance.chosenIndexProperty().get();
            if (chosen < 0) {
                run.getSelectionModel().clearSelection();
            } else {
                run.getSelectionModel().select(chosen);
            }
        } finally {
            updating = false;
        }
    }

    /** A copy action on a row list's selected row; the copied text goes on the clipboard. */
    private Button copy(
            String id,
            String shown,
            String name,
            ListView<String> list,
            IntFunction<Optional<String>> copy) {
        Button button = Texts.button(id, shown, name);
        button.setOnAction(
                event ->
                        copy.apply(list.getSelectionModel().getSelectedIndex())
                                .ifPresent(this::toClipboard));
        button.disableProperty().bind(list.getSelectionModel().selectedIndexProperty().lessThan(0));
        return button;
    }

    /** Any other action on a row list's selected row. */
    private static Button act(
            String id, String shown, String name, ListView<String> list, IntPredicate action) {
        Button button = Texts.button(id, shown, name);
        button.setOnAction(event -> action.test(list.getSelectionModel().getSelectedIndex()));
        button.disableProperty().bind(list.getSelectionModel().selectedIndexProperty().lessThan(0));
        return button;
    }

    private void toClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        try {
            Clipboard.getSystemClipboard().setContent(content);
        } catch (RuntimeException unavailable) {
            provenance.clipboardRefused(String.valueOf(unavailable.getMessage()));
        }
    }

    /** A list of rows that follows a view-model list, each row read out as the text it shows. */
    private static ListView<String> rows(
            String id, String name, ObservableValue<List<String>> shown, int visibleRows) {
        ListView<String> list = new ListView<>();
        list.setId(id);
        named(list, name);
        list.setPrefHeight(visibleRows * 64.0);
        list.setCellFactory(view -> new RowCell());
        list.getItems().setAll(shown.getValue());
        shown.addListener((observable, before, after) -> list.getItems().setAll(after));
        return list;
    }

    private static Label text(String id, ObservableValue<String> shown, String fallback) {
        Label label = Texts.label(id, shown.getValue(), fallback);
        shown.addListener((observable, before, after) -> label.setText(after));
        return label;
    }

    private static Label caption(String shown) {
        Label caption = new Label(shown);
        named(caption, shown);
        return caption;
    }

    /** A row: its text wrapped, and read out as exactly that text. */
    private static final class RowCell extends ListCell<String> {

        RowCell() {
            setWrapText(true);
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            String shown = empty || item == null ? null : item;
            setText(shown);
            setAccessibleText(shown == null ? "no row" : shown);
        }
    }
}
