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

package org.cometgui.ui.view.params;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.UiIds.Surface;
import org.cometgui.ui.controls.params.DecoySourceControl;
import org.cometgui.ui.controls.params.FieldControl;
import org.cometgui.ui.controls.params.PresetControl;
import org.cometgui.ui.controls.params.SpectrumInputsControl;
import org.cometgui.ui.controls.params.Subscriptions;
import org.cometgui.ui.controls.params.VariableModEditor;
import org.cometgui.ui.viewmodel.params.EssentialsGroup;
import org.cometgui.ui.viewmodel.params.FieldViewModel;
import org.cometgui.ui.viewmodel.params.FragmentOption;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.cometgui.ui.viewmodel.params.ToleranceViewModel;
import org.cometgui.ui.viewmodel.params.VariableModsViewModel;

/**
 * The Essentials level: the curated, task-ordered surface the session gives ({@link
 * ParameterEditorViewModel#session()}{@code .essentials()}), one group per {@code
 * EssentialsSection}, in the order a scientist sets up a search.
 *
 * <p>Most groups are their fields' typed controls. Some carry a structured control the
 * specification asks for: the spectrum files beside the database; the precursor window, units, type
 * and isotope offsets with the whole setting in words; the fragment bins with their instrument
 * choice; the search, second and sample enzyme selectors beside the termini and missed cleavages;
 * the decoy source as one control beside its prefix; and the variable-modification slot editor with
 * the per-peptide limit and the requirement beside it; and the search/acquisition preset choice,
 * applied only through its reviewable diff.
 *
 * <p>Built for one release; the editor builds a new one when the release changes.
 */
final class EssentialsView extends VBox {

    private final Map<String, Runnable> focusers = new HashMap<>();

    private final ParameterSession session;

    /**
     * The Essentials level of the session's release.
     *
     * @param session the session
     * @param editor the editor
     * @param inputs the spectrum inputs
     * @param mods the variable-modification view-model
     * @param subscriptions where this build's listeners are registered
     */
    EssentialsView(
            ParameterSession session,
            ParameterEditorViewModel editor,
            SpectrumInputsViewModel inputs,
            VariableModsViewModel mods,
            Subscriptions subscriptions) {
        Objects.requireNonNull(editor, "editor");
        this.session = Objects.requireNonNull(session, "session");
        setId(UiIds.PARAM_ESSENTIALS);
        setSpacing(14);
        setPadding(new Insets(4, 8, 4, 0));
        Set<String> shown = new LinkedHashSet<>();
        for (EssentialsGroup group : session.essentials()) {
            VBox box = new VBox(6);
            box.setId(UiIds.essentialsGroup(group.section()));
            Label title = new Label(group.title());
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.15em;");
            named(title, group.title() + " group");
            Label purpose = new Label(group.section().purpose());
            purpose.setWrapText(true);
            named(purpose, group.section().purpose());
            box.getChildren().addAll(title, purpose);
            switch (group.section()) {
                case INPUTS -> {
                    box.getChildren().add(new SpectrumInputsControl(inputs, subscriptions));
                    addFields(box, group, editor, subscriptions, shown);
                    box.getChildren().add(databaseStatus(inputs, subscriptions));
                }
                case SEARCH_PRESET ->
                        box.getChildren()
                                .add(new PresetControl(editor.presets(), session, subscriptions));
                case PRECURSOR -> {
                    addFields(box, group, editor, subscriptions, shown);
                    box.getChildren().add(precursorSummary(session, editor, subscriptions));
                }
                case FRAGMENT -> {
                    box.getChildren()
                            .add(fragmentChoice(editor.tolerance(), session, subscriptions));
                    addFields(box, group, editor, subscriptions, shown);
                }
                case DIGESTION -> {
                    for (FieldViewModel selector : editor.enzymes().selectors()) {
                        addField(box, selector, editor, subscriptions, shown);
                    }
                    addFields(box, group, editor, subscriptions, shown);
                }
                case VARIABLE_MODIFICATIONS -> {
                    VariableModEditor slots =
                            new VariableModEditor(
                                    Surface.ESSENTIALS, session, editor, mods, subscriptions, true);
                    for (FieldViewModel field : session.fields()) {
                        if (field.kind() == ValueKind.VARIABLE_MOD_TUPLE
                                || field.name().equals(mods.limitField().name())
                                || field.name().equals(mods.requireField().name())) {
                            focusers.put(field.name(), () -> slots.focus(field.name()));
                            shown.add(field.name());
                        }
                    }
                    box.getChildren().add(slots);
                }
                default -> addFields(box, group, editor, subscriptions, shown);
            }
            getChildren().add(box);
        }
    }

    /**
     * Moves the keyboard focus to a parameter's control, if this level shows it.
     *
     * @param name the parameter
     * @return whether this level shows it
     */
    boolean focus(String name) {
        Runnable focuser = focusers.get(name);
        if (focuser == null) {
            return false;
        }
        focuser.run();
        return true;
    }

    private void addFields(
            VBox box,
            EssentialsGroup group,
            ParameterEditorViewModel editor,
            Subscriptions subscriptions,
            Set<String> shown) {
        for (FieldViewModel field : group.fields()) {
            addField(box, field, editor, subscriptions, shown);
        }
    }

    private void addField(
            VBox box,
            FieldViewModel field,
            ParameterEditorViewModel editor,
            Subscriptions subscriptions,
            Set<String> shown) {
        if (!shown.add(field.name())) {
            return;
        }
        if (field.name().equals(DecoySource.PARAMETER)) {
            DecoySourceControl decoys =
                    new DecoySourceControl(session, Surface.ESSENTIALS, subscriptions);
            focusers.put(field.name(), decoys::focus);
            box.getChildren().add(decoys);
            return;
        }
        FieldControl control =
                new FieldControl(field, Surface.ESSENTIALS, session, editor, subscriptions);
        focusers.put(field.name(), control::focus);
        box.getChildren().add(control);
    }

    private static Label databaseStatus(
            SpectrumInputsViewModel inputs, Subscriptions subscriptions) {
        Label status = new Label();
        status.setId(UiIds.DATABASE_STATUS);
        status.setWrapText(true);
        Runnable show =
                () -> {
                    String text = "Database file: " + inputs.database().text();
                    status.setText(text);
                    status.setAccessibleText(text);
                };
        named(status, "Database file");
        show.run();
        subscriptions.onChange(inputs.databaseProperty(), show);
        return status;
    }

    private static Label precursorSummary(
            ParameterSession session,
            ParameterEditorViewModel editor,
            Subscriptions subscriptions) {
        ToleranceViewModel tolerance = editor.tolerance();
        Label summary = new Label();
        summary.setId(UiIds.PRECURSOR_SUMMARY);
        summary.setWrapText(true);
        Runnable show =
                () -> {
                    String text = "Precursor setting: " + tolerance.summary();
                    summary.setText(text);
                    summary.setAccessibleText(text);
                };
        named(summary, "Precursor setting");
        show.run();
        subscriptions.onChange(session.modelProperty(), show);
        return summary;
    }

    private static VBox fragmentChoice(
            ToleranceViewModel tolerance, ParameterSession session, Subscriptions subscriptions) {
        ComboBox<FragmentOption> choice = new ComboBox<>();
        choice.setId(UiIds.FRAGMENT_SETTING);
        named(choice, "Fragment ion setting, by instrument");
        choice.getItems().setAll(tolerance.fragmentOptions());
        choice.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(FragmentOption option) {
                        return option == null ? "" : option.words();
                    }

                    @Override
                    public FragmentOption fromString(String text) {
                        return null;
                    }
                });
        choice.setAccessibleHelp(
                "Sets the fragment bins as one of Comet's example parameter files does: "
                        + String.join(
                                "; ",
                                tolerance.fragmentOptions().stream()
                                        .map(o -> o.words() + " (" + o.valuesText() + ")")
                                        .toList())
                        + ".");
        Label words = new Label();
        words.setId(UiIds.FRAGMENT_SETTING_WORDS);
        words.setWrapText(true);
        named(words, "Fragment ion setting");
        boolean[] updating = {false};
        Runnable show =
                () -> {
                    updating[0] = true;
                    try {
                        choice.setValue(tolerance.fragmentMatch().orElse(null));
                        String text = "Fragment ions: " + tolerance.fragmentWords();
                        words.setText(text);
                        words.setAccessibleText(text);
                    } finally {
                        updating[0] = false;
                    }
                };
        choice.valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating[0] && after != null) {
                                tolerance.chooseFragment(after);
                                show.run();
                            }
                        });
        show.run();
        subscriptions.onChange(session.modelProperty(), show);
        Label label = new Label("Instrument setting");
        label.setMinWidth(240);
        named(label, "Instrument setting");
        label.setLabelFor(choice);
        HBox line = new HBox(8, label, choice);
        line.setAlignment(Pos.CENTER_LEFT);
        return new VBox(2, line, words);
    }
}
