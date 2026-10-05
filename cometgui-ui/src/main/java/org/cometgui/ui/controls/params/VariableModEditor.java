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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.params.comet.presets.ModificationPreset;
import org.cometgui.params.comet.schema.TerminalCode;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.value.VariableModChoice;
import org.cometgui.params.comet.value.VariableModPart;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.UiIds.Surface;
import org.cometgui.ui.viewmodel.params.EditOutcome;
import org.cometgui.ui.viewmodel.params.FieldViewModel;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.VariableModPartView;
import org.cometgui.ui.viewmodel.params.VariableModSlotView;
import org.cometgui.ui.viewmodel.params.VariableModsViewModel;

/**
 * The variable-modification editor ({@code R-PARAM-09}, {@code R-PARAM-10}): every slot of the
 * selected release, each with its summary in words, its serialised value, a control per part of the
 * release's tuple layout, the residue multi-select over the release's alphabet with the terminus
 * choices the release offers, and move-up, move-down and remove; above them, the common
 * modifications to add; and beside them, the per-peptide limit and the requirement switch with
 * their cross-validation in words.
 *
 * <p>Nothing here reads a tuple. Every word and text a slot shows is its {@link
 * VariableModSlotView}, made by the model; every edit is one call on {@link VariableModsViewModel},
 * which hands one part's text, one residue or one terminal code to the model.
 */
public final class VariableModEditor extends VBox {

    private final Surface surface;

    private final VariableModsViewModel mods;

    private final Map<String, SlotEditor> slots = new LinkedHashMap<>();

    private final Label status;

    private final Label cross;

    private final ComboBox<ModificationPreset> presets;

    private final Map<String, FieldControl> limits = new LinkedHashMap<>();

    /**
     * The editor of every slot of the session's release.
     *
     * @param surface where the editor is shown, which its identifiers name
     * @param session the session
     * @param editor the parameter editor
     * @param mods the variable-modification view-model over the session
     * @param subscriptions where this build's listeners are registered
     * @param withLimits whether to show {@code max_variable_mods_in_peptide} and {@code
     *     require_variable_mod} here; Advanced shows them among its own category's fields instead
     */
    public VariableModEditor(
            Surface surface,
            ParameterSession session,
            ParameterEditorViewModel editor,
            VariableModsViewModel mods,
            Subscriptions subscriptions,
            boolean withLimits) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.mods = Objects.requireNonNull(mods, "mods");
        setSpacing(8);

        presets = new ComboBox<>();
        presets.setId(UiIds.variableModPreset(surface));
        named(presets, "Common modification to add");
        presets.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(ModificationPreset preset) {
                        return preset == null ? "" : preset.summary();
                    }

                    @Override
                    public ModificationPreset fromString(String text) {
                        return null;
                    }
                });
        presets.getItems().setAll(mods.presets());
        if (!presets.getItems().isEmpty()) {
            presets.setValue(presets.getItems().get(0));
        }
        presets.setAccessibleHelp(
                "The common modifications Comet "
                        + session.release().text()
                        + " can hold. Add puts the chosen one into the first free slot.");
        Button add =
                Texts.button(
                        UiIds.variableModAdd(surface),
                        "Add",
                        "Add the chosen common modification to the first free slot");
        add.setOnAction(event -> addChosen());
        Label presetLabel = new Label("Common modification");
        named(presetLabel, "Common modification");
        presetLabel.setLabelFor(presets);
        HBox adding = new HBox(8, presetLabel, presets, add);
        adding.setAlignment(Pos.CENTER_LEFT);

        status =
                Texts.label(
                        UiIds.variableModStatus(surface),
                        "No change made here yet.",
                        "variable modification editor status");
        cross =
                Texts.label(
                        UiIds.variableModCross(surface),
                        crossText(),
                        "variable modifications against the limit and the requirement");
        getChildren().addAll(adding, status);
        if (withLimits) {
            for (FieldViewModel field : List.of(mods.limitField(), mods.requireField())) {
                FieldControl control =
                        new FieldControl(field, surface, session, editor, subscriptions);
                limits.put(field.name(), control);
                getChildren().add(control);
            }
        }
        getChildren().add(cross);

        for (VariableModSlotView view : mods.slots()) {
            SlotEditor slot = new SlotEditor(view);
            slots.put(view.name(), slot);
            getChildren().add(slot);
        }
        subscriptions.onChange(mods.slotsProperty(), this::refresh);
        refresh();
    }

    /**
     * Moves the keyboard focus to a slot's mass field, or to one of the two parameters beside the
     * slots, when a summary entry asks for it.
     *
     * @param name a slot's parameter name, or one of the two
     * @return whether this editor has it
     */
    public boolean focus(String name) {
        SlotEditor slot = slots.get(name);
        if (slot != null) {
            slot.mass().requestFocus();
            return true;
        }
        FieldControl limit = limits.get(name);
        if (limit != null) {
            limit.focus();
            return true;
        }
        return false;
    }

    private void addChosen() {
        ModificationPreset chosen = presets.getValue();
        if (chosen == null) {
            status.setText("Choose a common modification to add.");
            return;
        }
        Optional<String> target = mods.firstFreeSlot();
        EditOutcome outcome = mods.add(chosen);
        status.setText(
                outcome.refusal()
                        .orElseGet(() -> "Added " + chosen.name() + " in " + target.orElse("")));
        refresh();
    }

    private void report(String action, EditOutcome outcome) {
        status.setText(outcome.refusal().orElse(action));
        refresh();
    }

    private void refresh() {
        for (SlotEditor slot : slots.values()) {
            slot.refresh();
        }
        cross.setText(crossText());
    }

    private String crossText() {
        List<Finding> findings = mods.crossFindings();
        if (findings.isEmpty()) {
            return "The slots agree with the per-peptide limit and the requirement.";
        }
        List<String> lines = new ArrayList<>();
        for (Finding finding : findings) {
            lines.add((finding.isError() ? "Error: " : "Warning: ") + finding.message());
        }
        return String.join("\n", lines);
    }

    /** One slot's editor. */
    private final class SlotEditor extends VBox {

        private final String name;

        private final Label heading;

        private final Label serialised;

        private final Label origin;

        private final Label state;

        private final Button up;

        private final Button down;

        private final Button remove;

        private final Map<Character, CheckBox> residues = new LinkedHashMap<>();

        private final Map<VariableModPart, TextField> texts = new LinkedHashMap<>();

        private final Map<VariableModPart, ComboBox<VariableModChoice>> coded =
                new LinkedHashMap<>();

        private final Map<TextField, Boolean> dirty = new LinkedHashMap<>();

        private boolean updating;

        SlotEditor(VariableModSlotView view) {
            this.name = view.name();
            setSpacing(4);
            setPadding(new Insets(6, 0, 6, 12));
            heading =
                    Texts.label(UiIds.parameterControl(surface, name), view.heading(), view.name());
            up =
                    Texts.button(
                            UiIds.variableModUp(surface, name),
                            "Move up",
                            "Move " + view.displayName() + " up one slot");
            up.setOnAction(event -> report("Moved " + name + " up.", mods.moveUp(name)));
            down =
                    Texts.button(
                            UiIds.variableModDown(surface, name),
                            "Move down",
                            "Move " + view.displayName() + " down one slot");
            down.setOnAction(event -> report("Moved " + name + " down.", mods.moveDown(name)));
            remove =
                    Texts.button(
                            UiIds.variableModRemove(surface, name),
                            "Remove",
                            "Remove the modification in " + view.displayName());
            remove.setOnAction(event -> report("Removed " + name + ".", mods.remove(name)));
            HBox top = new HBox(8, heading, up, down, remove);
            top.setAlignment(Pos.CENTER_LEFT);

            serialised =
                    Texts.label(
                            UiIds.variableModSerialised(surface, name),
                            serialisedText(view),
                            "serialised value of " + name);

            FlowPane residueBoxes = new FlowPane(6, 4);
            for (char letter : mods.residueLetters()) {
                CheckBox box = new CheckBox(String.valueOf(letter));
                box.setId(UiIds.variableModResidue(surface, name, letter));
                named(box, "Residue " + letter + " in " + view.displayName());
                box.setOnAction(event -> setResidue(letter, box.isSelected()));
                residues.put(letter, box);
                residueBoxes.getChildren().add(box);
            }
            for (TerminalCode code : mods.terminusCodes()) {
                CheckBox box = new CheckBox(code.words() + " (" + code.code() + ")");
                box.setId(UiIds.variableModTerminus(surface, name, code));
                named(box, code.words() + " in " + view.displayName());
                box.setOnAction(event -> setResidue(code.code(), box.isSelected()));
                residues.put(code.code(), box);
                residueBoxes.getChildren().add(box);
            }
            Label residuesLabel = new Label("Residues and termini");
            named(residuesLabel, "Residues and termini of " + view.displayName());

            FlowPane partControls = new FlowPane(10, 4);
            for (VariableModPartView part : view.parts()) {
                if (part.part() == VariableModPart.RESIDUES) {
                    continue;
                }
                partControls.getChildren().add(partControl(view, part));
            }

            origin =
                    Texts.label(
                            UiIds.parameterOrigin(surface, name),
                            "",
                            "origin of " + view.displayName());
            state =
                    Texts.label(
                            UiIds.parameterState(surface, name),
                            "",
                            "validation state of " + view.displayName());
            getChildren()
                    .addAll(
                            top,
                            serialised,
                            residuesLabel,
                            residueBoxes,
                            partControls,
                            origin,
                            state);
        }

        private HBox partControl(VariableModSlotView view, VariableModPartView part) {
            Label label = new Label(part.label());
            named(label, part.label());
            String id = UiIds.variableModPart(surface, name, part.part());
            String help = part.label() + " of " + view.displayName() + ". " + part.explanation();
            HBox box = new HBox(4, label);
            box.setAlignment(Pos.CENTER_LEFT);
            if (part.choices().isEmpty()) {
                TextField text = new TextField();
                text.setId(id);
                text.setPrefWidth(110);
                named(text, part.label() + " of " + view.displayName());
                text.setAccessibleHelp(help);
                text.setTooltip(new Tooltip(help));
                text.textProperty()
                        .addListener(
                                (observable, before, after) -> {
                                    if (!updating) {
                                        dirty.put(text, true);
                                    }
                                });
                text.setOnAction(event -> commit(part.part(), text));
                text.focusedProperty()
                        .addListener(
                                (observable, was, focused) -> {
                                    if (!focused && dirty.getOrDefault(text, false)) {
                                        commit(part.part(), text);
                                    }
                                });
                texts.put(part.part(), text);
                label.setLabelFor(text);
                box.getChildren().add(text);
            } else {
                ComboBox<VariableModChoice> choice = new ComboBox<>();
                choice.setId(id);
                choice.getItems().setAll(part.choices());
                choice.setConverter(
                        new StringConverter<>() {
                            @Override
                            public String toString(VariableModChoice option) {
                                return option == null
                                        ? ""
                                        : option.words() + " [" + option.token() + "]";
                            }

                            @Override
                            public VariableModChoice fromString(String text) {
                                return null;
                            }
                        });
                named(choice, part.label() + " of " + view.displayName());
                choice.setAccessibleHelp(help);
                choice.setTooltip(new Tooltip(help));
                choice.valueProperty()
                        .addListener(
                                (observable, before, after) -> {
                                    if (!updating && after != null) {
                                        report(
                                                "Changed the " + part.label() + " of " + name + ".",
                                                mods.choose(name, part.part(), after));
                                    }
                                });
                coded.put(part.part(), choice);
                label.setLabelFor(choice);
                box.getChildren().add(choice);
            }
            return box;
        }

        TextField mass() {
            return texts.get(VariableModPart.MASS);
        }

        private void commit(VariableModPart part, TextField text) {
            dirty.put(text, false);
            report(
                    "Changed the " + part.label() + " of " + name + ".",
                    mods.setPart(name, part, text.getText()));
        }

        private void setResidue(char character, boolean selected) {
            report(
                    (selected ? "Selected " : "Cleared ") + character + " in " + name + ".",
                    mods.setResidue(name, character, selected));
        }

        void refresh() {
            VariableModSlotView view = mods.slot(name);
            FieldViewModel field = mods.field(name);
            updating = true;
            try {
                heading.setText(view.heading());
                serialised.setText(serialisedText(view));
                for (Map.Entry<Character, CheckBox> box : residues.entrySet()) {
                    box.getValue().setSelected(view.selects(box.getKey()));
                }
                for (VariableModPartView part : view.parts()) {
                    TextField text = texts.get(part.part());
                    if (text != null && !dirty.getOrDefault(text, false)) {
                        text.setText(part.text());
                    }
                    ComboBox<VariableModChoice> choice = coded.get(part.part());
                    if (choice != null) {
                        choice.setValue(
                                part.choices().stream()
                                        .filter(option -> option.token().equals(part.text()))
                                        .findFirst()
                                        .orElse(null));
                    }
                }
                int index = view.number() - 1;
                up.setDisable(index == 0);
                down.setDisable(index == mods.slots().size() - 1);
                remove.setDisable(!view.active());
                origin.setText("Value from: " + field.originText());
                state.setText(field.stateText());
                String help = view.heading() + "\nValidation: " + field.stateText();
                heading.setAccessibleHelp(help);
                TextField mass = mass();
                if (mass != null) {
                    mass.setAccessibleHelp(
                            "Mass difference of "
                                    + view.displayName()
                                    + ". "
                                    + help
                                    + "\n"
                                    + serialisedText(view));
                }
            } finally {
                updating = false;
            }
        }

        private String serialisedText(VariableModSlotView view) {
            return "Serialised: " + name + " = " + view.serialised();
        }
    }
}
