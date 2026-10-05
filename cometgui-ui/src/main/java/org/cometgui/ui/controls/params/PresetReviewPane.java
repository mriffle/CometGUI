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
import java.util.function.IntFunction;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.DiffRowView;
import org.cometgui.ui.viewmodel.params.EditOutcome;
import org.cometgui.ui.viewmodel.params.PresetPreview;
import org.cometgui.ui.viewmodel.params.PresetRowViewModel;
import org.cometgui.ui.viewmodel.params.PresetsViewModel;

/**
 * A preset's preview as a reviewable diff, and the line saying what the last action did ({@code
 * AC-PAR-08}, exit gate item 3): one row per parameter the preview would change -- the parameter,
 * its current value and the preset's -- each with a check box, which release the preset was made
 * for, and its compatibility problems in words. <em>Apply all</em>, <em>Apply selected</em> and
 * <em>Cancel</em> are the only ways out; each says what it did in the status line.
 *
 * <p>Two controls show one: the search/acquisition preset choice ({@link PresetControl}), and the
 * Essentials fragment instrument choice, whose preview is scoped to the fragment rows of its
 * preset. Each has its own {@link PresetsViewModel} and its own identifiers ({@link Ids}). Every
 * decision is the view-model's: the rows, what a locked row may do, and what applying changes.
 */
public final class PresetReviewPane extends VBox {

    /**
     * The identifiers one preview pane's parts carry.
     *
     * @param review the preview's container
     * @param madeFor which release the preset was made for
     * @param problems the compatibility problems and conversions
     * @param applyAll the Apply all action
     * @param applySelected the Apply selected action
     * @param cancel the Cancel action
     * @param status the last action's outcome
     * @param row a row's check box, by position
     * @param rowCurrent a row's current value, by position
     * @param rowPreset a row's preset value, by position
     */
    public record Ids(
            String review,
            String madeFor,
            String problems,
            String applyAll,
            String applySelected,
            String cancel,
            String status,
            IntFunction<String> row,
            IntFunction<String> rowCurrent,
            IntFunction<String> rowPreset) {

        /** The search/acquisition preset choice's preview. */
        public static final Ids PRESET =
                new Ids(
                        UiIds.PRESET_REVIEW,
                        UiIds.PRESET_MADE_FOR,
                        UiIds.PRESET_PROBLEMS,
                        UiIds.PRESET_APPLY_ALL,
                        UiIds.PRESET_APPLY_SELECTED,
                        UiIds.PRESET_CANCEL,
                        UiIds.PRESET_STATUS,
                        UiIds::presetRow,
                        UiIds::presetRowCurrent,
                        UiIds::presetRowPreset);

        /** The fragment instrument choice's preview. */
        public static final Ids FRAGMENT =
                new Ids(
                        UiIds.FRAGMENT_REVIEW,
                        UiIds.FRAGMENT_MADE_FOR,
                        UiIds.FRAGMENT_PROBLEMS,
                        UiIds.FRAGMENT_APPLY_ALL,
                        UiIds.FRAGMENT_APPLY_SELECTED,
                        UiIds.FRAGMENT_CANCEL,
                        UiIds.FRAGMENT_STATUS,
                        UiIds::fragmentRow,
                        UiIds::fragmentRowCurrent,
                        UiIds::fragmentRowPreset);

        /** Validates presence. */
        public Ids {
            Objects.requireNonNull(review, "review");
            Objects.requireNonNull(madeFor, "madeFor");
            Objects.requireNonNull(problems, "problems");
            Objects.requireNonNull(applyAll, "applyAll");
            Objects.requireNonNull(applySelected, "applySelected");
            Objects.requireNonNull(cancel, "cancel");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(row, "row");
            Objects.requireNonNull(rowCurrent, "rowCurrent");
            Objects.requireNonNull(rowPreset, "rowPreset");
        }
    }

    private final PresetsViewModel presets;

    private final Ids ids;

    private final VBox review = new VBox(6);

    private final Label madeFor;

    private final Label problems;

    private final VBox rows = new VBox(4);

    private final Label status;

    /**
     * The preview pane of a presets' view-model.
     *
     * @param presets the view-model whose preview this pane shows
     * @param ids the identifiers of this pane's parts
     * @param firstStatus what the status line says before anything is previewed
     * @param afterCancel run after Cancel closed the preview: the choice takes the focus back
     * @param subscriptions where this build's listeners are registered
     */
    public PresetReviewPane(
            PresetsViewModel presets,
            Ids ids,
            String firstStatus,
            Runnable afterCancel,
            Subscriptions subscriptions) {
        this.presets = Objects.requireNonNull(presets, "presets");
        this.ids = Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(afterCancel, "afterCancel");
        setSpacing(6);

        status = Texts.label(ids.status(), firstStatus, "preset status");

        review.setId(ids.review());
        review.setPadding(new Insets(4, 0, 4, 16));
        madeFor = Texts.label(ids.madeFor(), "", "which release the preset was made for");
        problems = Texts.label(ids.problems(), "", "the preset's compatibility");
        Label header = new Label("Parameter -- current value -- preset value");
        named(header, "Columns of the preview: parameter, current value, preset value");
        Button applyAll =
                Texts.button(
                        ids.applyAll(),
                        "Apply all",
                        "Apply every change of the preview that can be applied");
        applyAll.setOnAction(event -> report(previewedName(), presets.applyAll()));
        Button applySelected =
                Texts.button(
                        ids.applySelected(),
                        "Apply selected",
                        "Apply exactly the ticked changes of the preview");
        applySelected.setOnAction(event -> report(previewedName(), presets.applySelected()));
        Button cancel =
                Texts.button(ids.cancel(), "Cancel", "Close the preview without changing anything");
        cancel.setOnAction(
                event -> {
                    presets.cancel();
                    status.setText("Cancelled: nothing was changed.");
                    afterCancel.run();
                });
        HBox actions = new HBox(8, applyAll, applySelected, cancel);
        review.getChildren().addAll(madeFor, problems, header, rows, actions);

        getChildren().addAll(review, status);
        subscriptions.onChange(presets.previewProperty(), this::refresh);
        refresh();
    }

    /**
     * Says in the status line what a new preview shows. Nothing has changed.
     *
     * @param shown the preview just made
     */
    public void previewing(PresetPreview shown) {
        Objects.requireNonNull(shown, "shown");
        String title = shown.title();
        status.setText(
                shown.rows().isEmpty()
                        ? "Previewing "
                                + title
                                + ": the configuration already holds every value it sets."
                        : "Previewing "
                                + title
                                + ": "
                                + shown.rows().size()
                                + (shown.rows().size() == 1 ? " change" : " changes")
                                + ". Nothing has changed yet.");
    }

    /**
     * Puts a sentence in the status line.
     *
     * @param text the sentence
     */
    public void say(String text) {
        status.setText(Objects.requireNonNull(text, "text"));
    }

    private String previewedName() {
        return presets.preview().map(PresetPreview::title).orElse("the preset");
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
        madeFor.setText(preview.title() + ": " + preview.madeFor() + ".");
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
        box.setId(ids.row().apply(index));
        named(box, "Apply " + view.label() + ": " + view.current() + " to " + view.other());
        box.setMinWidth(380);
        box.setSelected(row.isSelected());
        Label current = new Label(view.current());
        current.setId(ids.rowCurrent().apply(index));
        named(current, "Current value of " + view.label() + ": " + view.current());
        current.setMinWidth(120);
        Label other = new Label(view.other());
        other.setId(ids.rowPreset().apply(index));
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
