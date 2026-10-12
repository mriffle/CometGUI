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

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.params.Texts;
import org.cometgui.ui.viewmodel.limelight.LimelightViewModel;

/**
 * The Limelight section: the run chosen in the Results section; whether it can be converted, with
 * the workflow's explanation and the action offered when it cannot; the converter's state and its
 * install; the converter's settings -- the Limelight q-value cutoff, the output path, decoy import,
 * the independent-decoy prefix, open-modification mode, and the resolved FASTA and pepXML directory
 * shown read-only; every prerequisite not met, by name; Convert, the converter's live log and the
 * validated result. Below the conversion is the upload area ({@link #uploadSlot()}), which the
 * upload half of the section fills.
 *
 * <p>Every control only calls the view-model; nothing is parsed, hashed or launched from here.
 * Every control is disabled while the view-model says it cannot be used, so a keyboard user never
 * reaches one that would only refuse.
 */
public final class LimelightPane extends ScrollPane {

    private final VBox upload;

    /**
     * The pane over its view-model.
     *
     * @param limelight the section's state and actions
     */
    public LimelightPane(LimelightViewModel limelight) {
        Objects.requireNonNull(limelight, "limelight");
        setId(UiIds.LIMELIGHT_PANE);
        named(this, "the Limelight section's content");
        setFitToWidth(true);

        Label run =
                Texts.label(
                        UiIds.LIMELIGHT_RUN,
                        limelight.runTextProperty().get(),
                        "the run chosen in the Results section");
        follow(run, limelight.runTextProperty());
        Button refresh =
                Texts.button(
                        UiIds.LIMELIGHT_REFRESH,
                        "Check again",
                        "Check the Limelight converter and the chosen run again");
        refresh.setOnAction(event -> limelight.refresh());

        Label compatibility =
                Texts.label(
                        UiIds.LIMELIGHT_COMPATIBILITY,
                        limelight.compatibilityTextProperty().get(),
                        "whether the run can be converted");
        follow(compatibility, limelight.compatibilityTextProperty());
        Button action = new Button(limelight.actionTextProperty().get());
        action.setId(UiIds.LIMELIGHT_ACTION);
        named(action, "the action offered for a run whose Percolator wrote no XML");
        action.textProperty().bind(limelight.actionTextProperty());
        action.accessibleTextProperty()
                .bind(
                        Bindings.createStringBinding(
                                () ->
                                        limelight.actionTextProperty().get().isBlank()
                                                ? "no action is offered for this run"
                                                : limelight.actionTextProperty().get(),
                                limelight.actionTextProperty()));
        action.accessibleHelpProperty().bind(limelight.actionExplanationProperty());
        action.visibleProperty().bind(limelight.actionVisibleProperty());
        action.managedProperty().bind(limelight.actionVisibleProperty());
        action.disableProperty().bind(limelight.actionEnabledProperty().not());
        action.setOnAction(event -> limelight.act());
        Label actionExplanation =
                Texts.label(
                        UiIds.LIMELIGHT_ACTION_EXPLANATION,
                        limelight.actionExplanationProperty().get(),
                        "no action is offered");
        follow(actionExplanation, limelight.actionExplanationProperty());
        Label actionOutcome =
                Texts.label(
                        UiIds.LIMELIGHT_ACTION_OUTCOME,
                        limelight.actionOutcomeProperty().get(),
                        "no action has been taken");
        follow(actionOutcome, limelight.actionOutcomeProperty());
        Button rerunCancel =
                Texts.button(
                        UiIds.LIMELIGHT_RERUN_CANCEL,
                        "Cancel the Percolator rerun",
                        "Cancel the Percolator rerun this section started");
        rerunCancel.setOnAction(event -> limelight.cancelRerun());
        rerunCancel.disableProperty().bind(limelight.rerunCancelEnabledProperty().not());

        Label converter =
                Texts.label(
                        UiIds.LIMELIGHT_CONVERTER_STATUS,
                        limelight.converterTextProperty().get(),
                        "the Limelight converter's state");
        follow(converter, limelight.converterTextProperty());
        ProgressBar progress = new ProgressBar();
        progress.setId(UiIds.LIMELIGHT_INSTALL_PROGRESS);
        named(progress, "Progress of the install the Limelight section started");
        progress.progressProperty().bind(limelight.installProgressProperty());
        progress.visibleProperty().bind(limelight.installingProperty());
        progress.managedProperty().bind(limelight.installingProperty());
        Button cancelInstall =
                Texts.button(
                        UiIds.LIMELIGHT_INSTALL_CANCEL,
                        "Cancel the install",
                        "Cancel the install the Limelight section started");
        cancelInstall.setOnAction(event -> limelight.cancelInstall());
        cancelInstall.disableProperty().bind(limelight.installingProperty().not());

        TextField cutoff =
                field(
                        UiIds.LIMELIGHT_Q_CUTOFF,
                        "Limelight q-value cutoff, the converter's own value and not a display"
                                + " filter",
                        limelight.qCutoffTextProperty());
        commit(cutoff, () -> limelight.editCutoff(cutoff.getText()));
        cutoff.disableProperty().bind(limelight.settingsEnabledProperty().not());
        cutoff.setAccessibleHelp(
                "The default PSM q-value filter Limelight starts from, written into the Limelight"
                        + " XML. It filters nothing at conversion, and it is independent of the"
                        + " PSM and peptide display filters of the Results section.");

        TextField output =
                field(
                        UiIds.LIMELIGHT_OUTPUT,
                        "Output Limelight XML path; empty for a new file in the run's outputs",
                        limelight.outputTextProperty());
        output.setPromptText("a new file under the run's outputs/limelight/");
        commit(output, () -> limelight.editOutput(output.getText()));
        output.disableProperty().bind(limelight.settingsEnabledProperty().not());
        Label outputStatus =
                Texts.label(
                        UiIds.LIMELIGHT_OUTPUT_STATUS,
                        limelight.outputStatusProperty().get(),
                        "where the next conversion writes");
        follow(outputStatus, limelight.outputStatusProperty());

        CheckBox decoys = new CheckBox("Import decoys");
        decoys.setId(UiIds.LIMELIGHT_IMPORT_DECOYS);
        named(decoys, "Import decoys, offered only when the run's Percolator output holds decoys");
        decoys.accessibleHelpProperty().bind(limelight.decoyTextProperty());
        decoys.setSelected(limelight.importDecoysProperty().get());
        decoys.disableProperty().bind(limelight.decoyEnabledProperty().not());
        decoys.setOnAction(event -> limelight.setImportDecoys(decoys.isSelected()));
        limelight
                .importDecoysProperty()
                .addListener((observable, before, after) -> decoys.setSelected(after));
        Label decoyStatus =
                Texts.label(
                        UiIds.LIMELIGHT_DECOY_STATUS,
                        limelight.decoyTextProperty().get(),
                        "whether decoys can be imported");
        follow(decoyStatus, limelight.decoyTextProperty());

        TextField prefix =
                field(
                        UiIds.LIMELIGHT_DECOY_PREFIX,
                        "Independent-decoy prefix, optional",
                        limelight.decoyPrefixTextProperty());
        prefix.setPromptText("none");
        commit(prefix, () -> limelight.editDecoyPrefix(prefix.getText()));
        prefix.disableProperty().bind(limelight.settingsEnabledProperty().not());

        CheckBox openMod = new CheckBox("Open-modification mode");
        openMod.setId(UiIds.LIMELIGHT_OPEN_MOD);
        named(openMod, "Open-modification mode");
        openMod.setSelected(limelight.openModProperty().get());
        openMod.disableProperty().bind(limelight.settingsEnabledProperty().not());
        openMod.setOnAction(event -> limelight.setOpenMod(openMod.isSelected()));
        limelight
                .openModProperty()
                .addListener((observable, before, after) -> openMod.setSelected(after));

        Label settings =
                Texts.label(
                        UiIds.LIMELIGHT_SETTINGS_STATUS,
                        limelight.settingsStatusProperty().get(),
                        "the Limelight settings");
        follow(settings, limelight.settingsStatusProperty());
        Label fasta =
                Texts.label(
                        UiIds.LIMELIGHT_FASTA,
                        limelight.fastaTextProperty().get(),
                        "the resolved FASTA, read-only");
        follow(fasta, limelight.fastaTextProperty());
        Label pepXml =
                Texts.label(
                        UiIds.LIMELIGHT_PEPXML_DIRECTORY,
                        limelight.pepXmlTextProperty().get(),
                        "the resolved pepXML directory, read-only");
        follow(pepXml, limelight.pepXmlTextProperty());

        Label refusals =
                Texts.label(
                        UiIds.LIMELIGHT_REFUSALS,
                        limelight.refusalsTextProperty().get(),
                        "the conversion's prerequisites");
        follow(refusals, limelight.refusalsTextProperty());
        Button convert =
                Texts.button(
                        UiIds.LIMELIGHT_CONVERT,
                        "Convert to Limelight XML",
                        "Convert the chosen run to Limelight XML, installing the converter first"
                                + " if it is not installed");
        convert.setOnAction(event -> limelight.convert());
        convert.disableProperty().bind(limelight.convertEnabledProperty().not());
        convert.accessibleHelpProperty().bind(limelight.convertRefusalProperty());
        Label convertRefusal =
                Texts.label(
                        UiIds.LIMELIGHT_CONVERT_REFUSAL,
                        limelight.convertRefusalProperty().get(),
                        "the run can be converted");
        follow(convertRefusal, limelight.convertRefusalProperty());

        TextArea log = new TextArea(limelight.logProperty().get());
        log.setId(UiIds.LIMELIGHT_LOG);
        named(log, "the Limelight converter's log of the last conversion");
        log.setEditable(false);
        log.setWrapText(true);
        log.setPrefRowCount(8);
        limelight
                .logProperty()
                .addListener(
                        (observable, before, after) -> {
                            log.setText(after);
                            log.positionCaret(after.length());
                        });
        Label outcome =
                Texts.label(
                        UiIds.LIMELIGHT_OUTCOME,
                        limelight.outcomeProperty().get(),
                        "nothing has been converted");
        follow(outcome, limelight.outcomeProperty());
        Label resultFile =
                Texts.label(
                        UiIds.LIMELIGHT_RESULT_FILE,
                        limelight.resultFileProperty().get(),
                        "no valid Limelight XML yet");
        follow(resultFile, limelight.resultFileProperty());
        Label resultPsms =
                Texts.label(
                        UiIds.LIMELIGHT_RESULT_PSMS,
                        limelight.resultPsmsProperty().get(),
                        "no PSM count yet");
        follow(resultPsms, limelight.resultPsmsProperty());
        Label resultSha256 =
                Texts.label(
                        UiIds.LIMELIGHT_RESULT_SHA256,
                        limelight.resultSha256Property().get(),
                        "no SHA-256 yet");
        follow(resultSha256, limelight.resultSha256Property());

        Label pending =
                Texts.label(
                        UiIds.LIMELIGHT_UPLOAD_PENDING,
                        "Uploading Limelight XML to a Limelight server is not available yet.",
                        "uploading is not available yet");
        upload = new VBox(6, caption("Upload to Limelight"), pending);
        upload.setId(UiIds.LIMELIGHT_UPLOAD);

        VBox content =
                new VBox(
                        6,
                        caption("Selected in the Results section"),
                        new HBox(8, run, refresh),
                        caption("Can this run be converted?"),
                        compatibility,
                        new HBox(8, action, rerunCancel),
                        actionExplanation,
                        actionOutcome,
                        caption("The Limelight converter"),
                        converter,
                        new HBox(8, progress, cancelInstall),
                        caption("Converter settings"),
                        new HBox(8, caption("Limelight q-value cutoff"), cutoff),
                        new HBox(8, caption("Output Limelight XML"), output),
                        outputStatus,
                        decoys,
                        decoyStatus,
                        new HBox(8, caption("Independent-decoy prefix"), prefix),
                        openMod,
                        settings,
                        fasta,
                        pepXml,
                        caption("Convert"),
                        refusals,
                        convert,
                        convertRefusal,
                        log,
                        outcome,
                        new HBox(8, caption("File"), resultFile),
                        new HBox(8, caption("PSMs"), resultPsms),
                        new HBox(8, caption("SHA-256"), resultSha256),
                        upload);
        setContent(content);
    }

    /**
     * Puts the upload half's controls into the upload area below the conversion, in place of the
     * note that uploading is not available yet. The area keeps its heading and its identifier.
     *
     * @param content the upload half's controls
     */
    public void showUpload(Node content) {
        Objects.requireNonNull(content, "content");
        upload.getChildren().setAll(upload.getChildren().get(0), content);
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

    private static Label caption(String shown) {
        Label caption = new Label(shown);
        named(caption, shown);
        return caption;
    }

    private static void follow(Label label, ObservableValue<String> shown) {
        shown.addListener((observable, before, after) -> label.setText(after));
    }
}
