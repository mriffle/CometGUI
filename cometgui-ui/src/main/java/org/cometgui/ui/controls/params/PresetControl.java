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

package org.cometgui.ui.controls.params;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.DiffRowView;
import org.cometgui.ui.viewmodel.params.EditOutcome;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.PresetPreview;
import org.cometgui.ui.viewmodel.params.PresetRowViewModel;
import org.cometgui.ui.viewmodel.params.PresetsViewModel;

/**
 * The search/acquisition preset choice of Essentials, applied only through a reviewable diff
 * ({@code AC-PAR-08}, exit gate item 3).
 *
 * <p>Choosing a preset and asking for its preview changes nothing: the preview shows one row per
 * parameter the preset would change -- the parameter, its current value and the preset's -- each
 * with a check box, which release the preset was made for, and its compatibility problems in words.
 * <em>Apply all</em>, <em>Apply selected</em> and <em>Cancel</em> are the only ways out; each says
 * what it did in the status line. Every decision is the {@link PresetsViewModel}'s: the rows, what
 * a locked row may do, and what applying changes.
 */
public final class PresetControl extends VBox {

    private final PresetsViewModel presets;

    private final ComboBox<Preset> choice = new ComboBox<>();

    private final VBox review = new VBox(6);

    private final Label madeFor;

    private final Label problems;

    private final VBox rows = new VBox(4);

    private final Label status;

    /**
     * The preset choice and its preview.
     *
     * @param presets the presets' view-model
     * @param session the session, whose configuration the preview follows
     * @param subscriptions where this build's listeners are registered
     */
    public PresetControl(
            PresetsViewModel presets, ParameterSession session, Subscriptions subscriptions) {
        this.presets = Objects.requireNonNull(presets, "presets");
        Objects.requireNonNull(session, "session");
        setSpacing(6);

        Label label = new Label("Search/acquisition preset");
        label.setMinWidth(240);
        named(label, "Search/acquisition preset");
        choice.setId(UiIds.PRESET_CHOICE);
        named(choice, "Search/acquisition preset");
        label.setLabelFor(choice);
        choice.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(Preset preset) {
                        return preset == null ? "" : preset.displayName();
                    }

                    @Override
                    public Preset fromString(String text) {
                        return null;
                    }
                });
        choice.getItems().setAll(presets.presets());
        if (!choice.getItems().isEmpty()) {
            choice.setValue(choice.getItems().get(0));
        }
        choice.setAccessibleHelp(
                "A preset is a set of changes. Preview shows what it would change, and nothing"
                        + " changes until you apply all or some of it.");
        Button preview =
                Texts.button(
                        UiIds.PRESET_PREVIEW,
                        "Preview changes...",
                        "Preview what the chosen preset would change; nothing changes yet");
        preview.setOnAction(event -> previewChosen());
        HBox line = new HBox(8, label, choice, preview);
        line.setAlignment(Pos.CENTER_LEFT);

        status =
                Texts.label(
                        UiIds.PRESET_STATUS,
                        "No preset applied. Choose one and preview it to see what it changes.",
                        "preset status");

        review.setId(UiIds.PRESET_REVIEW);
        review.setPadding(new Insets(4, 0, 4, 16));
        madeFor = Texts.label(UiIds.PRESET_MADE_FOR, "", "which release the preset was made for");
        problems = Texts.label(UiIds.PRESET_PROBLEMS, "", "the preset's compatibility");
        Label header = new Label("Parameter -- current value -- preset value");
        named(header, "Columns of the preview: parameter, current value, preset value");
        Button applyAll =
                Texts.button(
                        UiIds.PRESET_APPLY_ALL,
                        "Apply all",
                        "Apply every change of the preview that can be applied");
        applyAll.setOnAction(event -> report(previewedName(), presets.applyAll()));
        Button applySelected =
                Texts.button(
                        UiIds.PRESET_APPLY_SELECTED,
                        "Apply selected",
                        "Apply exactly the ticked changes of the preview");
        applySelected.setOnAction(event -> report(previewedName(), presets.applySelected()));
        Button cancel =
                Texts.button(
                        UiIds.PRESET_CANCEL,
                        "Cancel",
                        "Close the preview without changing anything");
        cancel.setOnAction(
                event -> {
                    presets.cancel();
                    status.setText("Cancelled: nothing was changed.");
                    choice.requestFocus();
                });
        HBox actions = new HBox(8, applyAll, applySelected, cancel);
        review.getChildren().addAll(madeFor, problems, header, rows, actions);

        getChildren().addAll(line, review, status);
        subscriptions.onChange(presets.previewProperty(), this::refresh);
        refresh();
    }

    private void previewChosen() {
        Preset chosen = choice.getValue();
        if (chosen == null) {
            status.setText("Choose a preset to preview.");
            return;
        }
        PresetPreview shown = presets.preview(chosen);
        status.setText(
                shown.rows().isEmpty()
                        ? "Previewing "
                                + chosen.displayName()
                                + ": the configuration already holds every value it sets."
                        : "Previewing "
                                + chosen.displayName()
                                + ": "
                                + shown.rows().size()
                                + (shown.rows().size() == 1 ? " change" : " changes")
                                + ". Nothing has changed yet.");
    }

    private String previewedName() {
        return presets.preview().map(p -> p.preset().displayName()).orElse("the preset");
    }

    private void report(String name, EditOutcome outcome) {
        if (outcome.accepted()) {
            List<String> applied = new ArrayList<>();
            for (DiffRowView row : presets.lastAppliedRows()) {
                applied.add(row.label() + " " + row.current() + " -> " + row.other());
            }
            status.setText(
                    "Applied "
                            + applied.size()
                            + (applied.size() == 1 ? " change" : " changes")
                            + " of "
                            + name
                            + ": "
                            + String.join("; ", applied)
                            + ".");
        } else {
            status.setText("Nothing applied: " + outcome.refusal().orElse(""));
        }
    }

    private void refresh() {
        Optional<PresetPreview> shown = presets.preview();
        review.setVisible(shown.isPresent());
        review.setManaged(shown.isPresent());
        rows.getChildren().clear();
        if (shown.isEmpty()) {
            return;
        }
        PresetPreview preview = shown.get();
        madeFor.setText(preview.preset().displayName() + ": " + preview.madeFor() + ".");
        List<String> compatibility = new ArrayList<>(preview.problems());
        compatibility.addAll(preview.conversions());
        problems.setText(
                compatibility.isEmpty()
                        ? "Compatibility: no problems; every change can be applied to this"
                                + " release."
                        : "Compatibility:\n" + String.join("\n", compatibility));
        if (preview.rows().isEmpty()) {
            Label none = new Label("No changes: the configuration already holds these values.");
            named(none, none.getText());
            rows.getChildren().add(none);
        }
        for (int index = 0; index < preview.rows().size(); index++) {
            rows.getChildren().add(row(index, preview.rows().get(index)));
        }
    }

    private HBox row(int index, PresetRowViewModel row) {
        DiffRowView view = row.view();
        CheckBox box = new CheckBox(view.label());
        box.setId(UiIds.presetRow(index));
        named(box, "Apply " + view.label() + ": " + view.current() + " to " + view.other());
        box.setMinWidth(380);
        box.setSelected(row.isSelected());
        Label current = new Label(view.current());
        current.setId(UiIds.presetRowCurrent(index));
        named(current, "Current value of " + view.label() + ": " + view.current());
        current.setMinWidth(120);
        Label other = new Label(view.other());
        other.setId(UiIds.presetRowPreset(index));
        named(other, "Preset value of " + view.label() + ": " + view.other());
        HBox line = new HBox(8, box, current, other);
        line.setAlignment(Pos.CENTER_LEFT);
        row.lockReason()
                .ifPresent(
                        reason -> {
                            box.setDisable(true);
                            Label lock = new Label(reason);
                            named(lock, view.label() + ": " + reason);
                            line.getChildren().add(lock);
                        });
        box.setOnAction(
                event -> {
                    EditOutcome outcome = row.setSelected(box.isSelected());
                    outcome.refusal().ifPresent(status::setText);
                    box.setSelected(row.isSelected());
                });
        return line;
    }
}
