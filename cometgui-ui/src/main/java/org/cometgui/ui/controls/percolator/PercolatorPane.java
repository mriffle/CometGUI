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

package org.cometgui.ui.controls.percolator;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.params.Texts;
import org.cometgui.ui.viewmodel.percolator.PercolatorRerunViewModel;
import org.cometgui.ui.viewmodel.percolator.PercolatorViewModel;
import org.cometgui.ui.viewmodel.percolator.SettingState;
import org.cometgui.ui.viewmodel.percolator.VersionChoice;

/**
 * The Percolator section's content (decision P9-12): the version selector with the resolved default
 * marked, the badge, the Limelight-conversion switch and what it did, the reasons and the
 * advisories, Limelight's availability with its remedies, local-binary registration, the display
 * filters, the Advanced settings and the compatible-version rerun.
 *
 * <p>A thin view: every text it shows is a view-model's, and every action calls a view-model
 * method. Nothing here resolves, parses, hashes or decides; the view-models do their slow work off
 * the JavaFX thread.
 */
public final class PercolatorPane extends ScrollPane {

    private final PercolatorViewModel section;

    private final ComboBox<VersionChoice> version;

    private final Map<PercolatorSetting, TextField> settingFields =
            new EnumMap<>(PercolatorSetting.class);

    private final Map<PercolatorSetting, Label> settingStates =
            new EnumMap<>(PercolatorSetting.class);

    /** Set while the view moves the selector itself, so that the move is not taken as a choice. */
    private boolean updating;

    /**
     * The section's content.
     *
     * @param section the section's view-model
     * @param rerun the compatible-version rerun's view-model
     */
    public PercolatorPane(PercolatorViewModel section, PercolatorRerunViewModel rerun) {
        this.section = Objects.requireNonNull(section, "section");
        Objects.requireNonNull(rerun, "rerun");
        setId(UiIds.PERCOLATOR_PANE);
        named(this, "the Percolator section's content");
        setFitToWidth(true);

        VBox content = new VBox(6);

        Label offers =
                Texts.label(
                        UiIds.PERCOLATOR_OFFERS,
                        section.offersStatusProperty().get(),
                        "the Percolator builds on this computer");
        follow(offers, section.offersStatusProperty());

        version = new ComboBox<>();
        version.setId(UiIds.PERCOLATOR_VERSION);
        named(version, "Percolator version");
        version.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(VersionChoice choice) {
                        return choice == null ? "" : choice.label();
                    }

                    @Override
                    public VersionChoice fromString(String text) {
                        return null;
                    }
                });
        version.valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating && after != null) {
                                section.choose(after);
                            }
                        });
        Button useDefault =
                Texts.button(
                        UiIds.PERCOLATOR_USE_DEFAULT,
                        "Use the default",
                        "Use the resolved default Percolator");
        useDefault.setOnAction(event -> section.useDefault());
        section.choicesProperty().addListener((observable, before, after) -> showChoices());
        section.selectedChoiceProperty().addListener((observable, before, after) -> showChoices());
        showChoices();

        Label selection =
                Texts.label(
                        UiIds.PERCOLATOR_SELECTION,
                        section.selectionProperty().get(),
                        "which Percolator runs");
        follow(selection, section.selectionProperty());
        Label badge =
                Texts.label(
                        UiIds.PERCOLATOR_BADGE,
                        section.badgeProperty().get(),
                        "the selected Percolator's capabilities and status");
        follow(badge, section.badgeProperty());
        Label installable =
                Texts.label(
                        UiIds.PERCOLATOR_INSTALLABLE,
                        section.installableProperty().get(),
                        "no Percolator build waits to be installed");
        follow(installable, section.installableProperty());
        Label advisories =
                Texts.label(
                        UiIds.PERCOLATOR_ADVISORIES,
                        section.advisoriesProperty().get(),
                        "the selected Percolator's advisories");
        follow(advisories, section.advisoriesProperty());

        CheckBox limelight = new CheckBox(DownstreamStage.LIMELIGHT_CONVERSION.label());
        limelight.setId(UiIds.PERCOLATOR_LIMELIGHT);
        named(limelight, "Limelight conversion, which needs Percolator XML");
        limelight.setAccessibleHelp(
                "Switching Limelight conversion on makes the default Percolator one observed to"
                        + " write the Percolator XML the Limelight converter reads.");
        limelight.setSelected(section.limelightEnabled());
        limelight.setOnAction(event -> section.setLimelightEnabled(limelight.isSelected()));
        section.limelightEnabledProperty()
                .addListener((observable, before, after) -> limelight.setSelected(after));
        Label limelightStatus =
                Texts.label(
                        UiIds.PERCOLATOR_LIMELIGHT_STATUS,
                        section.limelightStatusProperty().get(),
                        "whether Limelight conversion can run");
        follow(limelightStatus, section.limelightStatusProperty());
        Label notice =
                Texts.label(
                        UiIds.PERCOLATOR_NOTICE,
                        section.noticeProperty().get(),
                        "no change to the default Percolator yet");
        follow(notice, section.noticeProperty());
        Label reason =
                Texts.label(
                        UiIds.PERCOLATOR_REASON,
                        section.reasonProperty().get(),
                        "why the default Percolator is the default");
        follow(reason, section.reasonProperty());
        Label skipped =
                Texts.label(
                        UiIds.PERCOLATOR_SKIPPED,
                        section.skippedProperty().get(),
                        "newer Percolator builds passed over");
        follow(skipped, section.skippedProperty());

        Button register =
                Texts.button(
                        UiIds.PERCOLATOR_REGISTER,
                        "Register a local Percolator binary...",
                        "Register a local Percolator binary");
        register.setOnAction(event -> section.register());
        register.setDisable(!section.registerEnabledProperty().get());
        section.registerEnabledProperty()
                .addListener((observable, before, after) -> register.setDisable(!after));
        Label registered =
                Texts.label(
                        UiIds.PERCOLATOR_REGISTER_STATUS,
                        section.registrationStatusProperty().get(),
                        "local Percolator registration");
        follow(registered, section.registrationStatusProperty());

        TextField psm =
                filterField(
                        UiIds.PERCOLATOR_PSM_FILTER,
                        "PSM q-value filter",
                        section.psmFilterTextProperty().get());
        psm.setOnAction(event -> section.editPsmFilter(psm.getText()));
        commitOnFocusLoss(psm, () -> section.editPsmFilter(psm.getText()));
        section.psmFilterTextProperty()
                .addListener((observable, before, after) -> showText(psm, after));
        TextField peptide =
                filterField(
                        UiIds.PERCOLATOR_PEPTIDE_FILTER,
                        "peptide q-value filter",
                        section.peptideFilterTextProperty().get());
        peptide.setOnAction(event -> section.editPeptideFilter(peptide.getText()));
        commitOnFocusLoss(peptide, () -> section.editPeptideFilter(peptide.getText()));
        section.peptideFilterTextProperty()
                .addListener((observable, before, after) -> showText(peptide, after));
        Label filters =
                Texts.label(
                        UiIds.PERCOLATOR_FILTERS_STATUS,
                        section.filtersStatusProperty().get(),
                        "what the display filters do");
        follow(filters, section.filtersStatusProperty());

        ToggleButton advancedToggle = new ToggleButton("Advanced settings");
        advancedToggle.setId(UiIds.PERCOLATOR_ADVANCED_TOGGLE);
        named(advancedToggle, "Show the Advanced Percolator settings");
        VBox advanced = new VBox(4);
        advanced.setId(UiIds.PERCOLATOR_ADVANCED);
        advanced.visibleProperty().bind(advancedToggle.selectedProperty());
        advanced.managedProperty().bind(advancedToggle.selectedProperty());
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            advanced.getChildren().add(settingRow(setting));
        }
        section.settingStatesProperty()
                .addListener((observable, before, after) -> showSettings(after));
        showSettings(section.settingStatesProperty().get());

        Label rerunPreview =
                Texts.label(
                        UiIds.PERCOLATOR_RERUN_PREVIEW,
                        rerun.previewProperty().get(),
                        "the Percolator rerun preview");
        follow(rerunPreview, rerun.previewProperty());
        Button rerunStart =
                Texts.button(
                        UiIds.PERCOLATOR_RERUN,
                        rerun.actionTextProperty().get(),
                        "Rerun Percolator from the last run");
        rerunStart.textProperty().bind(rerun.actionTextProperty());
        rerunStart.setOnAction(event -> rerun.start());
        rerunStart.setDisable(!rerun.actionEnabledProperty().get());
        rerun.actionEnabledProperty()
                .addListener((observable, before, after) -> rerunStart.setDisable(!after));
        Button rerunCancel =
                Texts.button(
                        UiIds.PERCOLATOR_RERUN_CANCEL,
                        "Cancel the rerun",
                        "Cancel the running Percolator rerun");
        rerunCancel.setOnAction(event -> rerun.cancel());
        rerunCancel.setDisable(!rerun.cancelEnabledProperty().get());
        rerun.cancelEnabledProperty()
                .addListener((observable, before, after) -> rerunCancel.setDisable(!after));
        Label rerunOutcome =
                Texts.label(
                        UiIds.PERCOLATOR_RERUN_OUTCOME,
                        rerun.outcomeProperty().get(),
                        "the Percolator rerun's outcome");
        follow(rerunOutcome, rerun.outcomeProperty());

        content.getChildren()
                .addAll(
                        caption("Percolator build"),
                        offers,
                        new HBox(8, version, useDefault),
                        selection,
                        badge,
                        installable,
                        advisories,
                        caption("Downstream stages"),
                        limelight,
                        limelightStatus,
                        notice,
                        caption("Why this build"),
                        reason,
                        skipped,
                        caption("Local Percolator binary"),
                        register,
                        registered,
                        caption("Result filters"),
                        new HBox(8, caption("PSM q-value filter"), psm),
                        new HBox(8, caption("Peptide q-value filter"), peptide),
                        filters,
                        advancedToggle,
                        advanced,
                        caption("Rerun Percolator from the last run"),
                        rerunPreview,
                        new HBox(8, rerunStart, rerunCancel),
                        rerunOutcome);
        setContent(content);
    }

    private VBox settingRow(PercolatorSetting setting) {
        TextField field = new TextField();
        field.setId(UiIds.percolatorSetting(setting));
        named(field, setting.label() + ", a Percolator learning setting");
        field.setOnAction(event -> section.editSetting(setting, field.getText()));
        commitOnFocusLoss(field, () -> section.editSetting(setting, field.getText()));
        Label state =
                Texts.label(
                        UiIds.percolatorSettingState(setting),
                        setting.description(),
                        setting.label() + " setting's state");
        settingFields.put(setting, field);
        settingStates.put(setting, state);
        return new VBox(2, new HBox(8, caption(setting.label()), field), state);
    }

    private void showSettings(List<SettingState> states) {
        for (SettingState state : states) {
            TextField field = settingFields.get(state.setting());
            showText(field, state.text());
            field.setDisable(!state.editable());
            settingStates.get(state.setting()).setText(state.state());
            field.setAccessibleHelp(state.state());
        }
    }

    private void showChoices() {
        updating = true;
        try {
            List<VersionChoice> offered = section.choicesProperty().get();
            if (!version.getItems().equals(offered)) {
                version.getItems().setAll(offered);
            }
            Optional<VersionChoice> selected = section.selectedChoiceProperty().get();
            version.setValue(selected.orElse(null));
            version.setDisable(offered.isEmpty());
        } finally {
            updating = false;
        }
    }

    private static TextField filterField(String id, String name, String text) {
        TextField field = new TextField(text);
        field.setId(id);
        named(field, name + ", a display filter that never reruns Percolator");
        return field;
    }

    /** Commits a field's text when it loses the focus, if the text was edited. */
    private static void commitOnFocusLoss(TextField field, Runnable commit) {
        field.focusedProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!after) {
                                commit.run();
                            }
                        });
    }

    private static void showText(TextField field, String text) {
        if (!field.getText().equals(text)) {
            field.setText(text);
        }
    }

    private static Label caption(String text) {
        Label caption = new Label(text);
        named(caption, text);
        return caption;
    }

    private static void follow(Label label, ObservableValue<String> text) {
        text.addListener((observable, before, after) -> label.setText(after));
    }
}
