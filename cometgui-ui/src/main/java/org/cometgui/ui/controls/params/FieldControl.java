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
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.UiIds.Surface;
import org.cometgui.ui.viewmodel.params.ChoiceOption;
import org.cometgui.ui.viewmodel.params.EnzymeOption;
import org.cometgui.ui.viewmodel.params.FieldViewModel;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.RangeViewModel;

/**
 * One parameter's typed control, with everything the specification's <em>Typed control
 * requirements</em> ask a control to say: its display name, its help (tooltip and accessible help,
 * with an enumeration's serialised tokens), where its value came from, whether and why it is
 * locked, and its validation state -- all in text.
 *
 * <h2>The control follows the kind</h2>
 *
 * <ul>
 *   <li>on/off parameters (a flag or an ion series): a check box over the model's own flag ({@link
 *       FieldViewModel#isOn()}, {@link FieldViewModel#setOn(boolean)});
 *   <li>enumerations: a combo box of the release's choices by their labels;
 *   <li>an enzyme number: a combo box of the configuration's enzyme table rows;
 *   <li>a range: two text fields under one label, committed together;
 *   <li>a file path: a text field and a chooser button, the full path in the tooltip and the
 *       accessible help, never truncated;
 *   <li>everything else: a text field.
 * </ul>
 *
 * <p>A text field commits when Enter is pressed or the focus leaves it, by handing its text to the
 * field's view-model, which hands it to the model. A refused edit leaves the configuration as it
 * was and the model's own message is shown at the field, in the state line, until it is replaced.
 *
 * <h2>Locked outputs</h2>
 *
 * <p>A field the workflow requires ({@link FieldViewModel#isLocked()}) is shown with its value,
 * disabled for change, and with its lock reason as visible text. The session refuses an edit of it
 * as well, so the lock does not rest on the view alone.
 *
 * <h2>Refreshing</h2>
 *
 * <p>The control re-reads its field after every change of the configuration, and after every change
 * of the field's own state. Whichever fires first, the session's getters already answer for the new
 * configuration: it replaces its state before it publishes anything.
 */
public final class FieldControl extends VBox {

    private final FieldViewModel field;

    private final ParameterEditorViewModel editor;

    private final Label label;

    private final Label origin;

    private final Label lock;

    private final Label state;

    private final Button reset;

    private final Control input;

    private TextField second;

    private Button choose;

    private boolean updating;

    private boolean dirty;

    /**
     * The control of one parameter.
     *
     * @param field the parameter's field
     * @param surface where the control is shown, which its identifiers name
     * @param session the session, whose configuration the control follows
     * @param editor the editor, for the enzyme table, the ranges and the file chooser
     * @param subscriptions where this build's listeners are registered
     * @throws IllegalArgumentException for a variable-modification slot, which the slot editor
     *     shows
     */
    public FieldControl(
            FieldViewModel field,
            Surface surface,
            ParameterSession session,
            ParameterEditorViewModel editor,
            Subscriptions subscriptions) {
        this.field = Objects.requireNonNull(field, "field");
        this.editor = Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(surface, "surface");
        if (field.kind() == ValueKind.VARIABLE_MOD_TUPLE) {
            throw new IllegalArgumentException(
                    field.name() + " is a variable-modification slot; the slot editor shows it");
        }
        String name = field.name();
        setSpacing(2);

        label = new Label(field.displayName());
        label.setId(UiIds.parameterLabel(surface, name));
        label.setMinWidth(240);
        label.setPrefWidth(240);
        label.setWrapText(true);
        named(label, field.displayName());

        input = buildInput(surface);
        input.setId(UiIds.parameterControl(surface, name));
        named(input, field.displayName());
        label.setLabelFor(input);

        reset =
                Texts.button(
                        UiIds.parameterReset(surface, name),
                        "Reset",
                        "Reset "
                                + field.displayName()
                                + " to the Comet "
                                + field.release().text()
                                + " default");
        reset.setOnAction(
                event -> {
                    field.reset();
                    refresh();
                });

        HBox line = new HBox(8, label);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getChildren().add(input);
        if (second != null) {
            second.setId(UiIds.parameterSecond(surface, name));
            named(second, field.displayName() + ", second value");
            line.getChildren().add(second);
        }
        if (choose != null) {
            choose.setId(UiIds.parameterChoose(surface, name));
            line.getChildren().add(choose);
        }
        line.getChildren().add(reset);

        origin =
                Texts.label(
                        UiIds.parameterOrigin(surface, name),
                        originText(),
                        "origin of " + field.displayName());
        lock = Texts.label(UiIds.parameterLock(surface, name), lockText(), "lock");
        state =
                Texts.label(
                        UiIds.parameterState(surface, name),
                        field.stateText(),
                        "validation state of " + field.displayName());

        getChildren().addAll(line, origin, lock, state);

        subscriptions.onChange(session.modelProperty(), this::refresh);
        subscriptions.onChange(field.stateTextProperty(), this::refresh);
        subscriptions.onChange(field.textProperty(), this::refresh);
        subscriptions.onChange(field.lockedProperty(), this::refresh);
        refresh();
    }

    /** Moves the keyboard focus to the input, when a summary entry asks for this parameter. */
    public void focus() {
        input.requestFocus();
    }

    private Control buildInput(Surface surface) {
        return switch (field.kind()) {
            case BOOLEAN_FLAG, ION_SERIES_FLAG -> checkBox();
            case INTEGER_ENUM, STRING_ENUM -> choices();
            case ENZYME_REFERENCE -> enzymes();
            case INTEGER_RANGE, DECIMAL_RANGE -> range();
            case FILE_PATH -> path();
            default -> textField(field.kind() == ValueKind.STRING ? 200 : 140);
        };
    }

    private CheckBox checkBox() {
        CheckBox box = new CheckBox("On");
        box.setOnAction(
                event -> {
                    field.setOn(box.isSelected());
                    refresh();
                });
        return box;
    }

    private ComboBox<ChoiceOption> choices() {
        ComboBox<ChoiceOption> box = new ComboBox<>();
        box.getItems().setAll(field.choices());
        box.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(ChoiceOption option) {
                        return option == null ? "" : option.label();
                    }

                    @Override
                    public ChoiceOption fromString(String text) {
                        return null;
                    }
                });
        box.valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating && after != null) {
                                field.choose(after);
                                refresh();
                            }
                        });
        return box;
    }

    private ComboBox<EnzymeOption> enzymes() {
        ComboBox<EnzymeOption> box = new ComboBox<>();
        box.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(EnzymeOption option) {
                        return option == null ? "" : option.label();
                    }

                    @Override
                    public EnzymeOption fromString(String text) {
                        return null;
                    }
                });
        box.valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating && after != null) {
                                editor.enzymes().select(field.name(), after);
                                refresh();
                            }
                        });
        return box;
    }

    private TextField range() {
        TextField first = committing(new TextField(), 100);
        second = committing(new TextField(), 100);
        return first;
    }

    private TextField path() {
        TextField text = textField(420);
        choose = new Button("Choose...");
        named(choose, "Choose the file for " + field.displayName());
        choose.setOnAction(
                event -> {
                    editor.chooseFileFor(field);
                    refresh();
                });
        return text;
    }

    private TextField textField(double width) {
        return committing(new TextField(), width);
    }

    private TextField committing(TextField text, double width) {
        text.setPrefColumnCount(1);
        text.setPrefWidth(width);
        HBox.setHgrow(text, Priority.NEVER);
        text.textProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating) {
                                dirty = true;
                            }
                        });
        text.setOnAction(event -> commit());
        text.focusedProperty()
                .addListener(
                        (observable, wasFocused, focused) -> {
                            if (!focused && dirty) {
                                commit();
                            }
                        });
        return text;
    }

    /** Hands the typed text to the field's view-model, which hands it to the model. */
    private void commit() {
        dirty = false;
        TextField first = (TextField) input;
        if (second != null) {
            RangeViewModel range = editor.ranges().range(field.name());
            range.set(first.getText(), second.getText());
        } else {
            field.setText(first.getText());
        }
        // Shown again explicitly: a property that ends equal to what it was fires nothing, and a
        // refused edit leaves the model untouched. The field says what the configuration holds.
        refresh();
    }

    /** Puts the control in step with its field and the configuration. */
    private void refresh() {
        updating = true;
        try {
            showValue();
            boolean locked = field.isLocked();
            input.setDisable(locked);
            if (second != null) {
                second.setDisable(locked);
            }
            if (choose != null) {
                choose.setDisable(locked);
            }
            reset.setDisable(locked);
            origin.setText(originText());
            lock.setText(lockText());
            lock.setVisible(locked);
            lock.setManaged(locked);
            state.setText(field.stateText());
            String help = help();
            input.setAccessibleHelp(help);
            Tooltip tooltip = input.getTooltip();
            if (tooltip == null) {
                input.setTooltip(new Tooltip(help));
            } else {
                tooltip.setText(help);
            }
        } finally {
            updating = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void showValue() {
        switch (field.kind()) {
            case BOOLEAN_FLAG, ION_SERIES_FLAG -> ((CheckBox) input).setSelected(field.isOn());
            case INTEGER_ENUM, STRING_ENUM -> {
                ComboBox<ChoiceOption> box = (ComboBox<ChoiceOption>) input;
                Optional<ChoiceOption> held =
                        box.getItems().stream()
                                .filter(option -> option.token().equals(field.text()))
                                .findFirst();
                box.setValue(held.orElse(null));
                box.setPromptText(
                        held.isPresent()
                                ? ""
                                : "\"" + field.text() + "\" is not one of the documented choices");
            }
            case ENZYME_REFERENCE -> {
                ComboBox<EnzymeOption> box = (ComboBox<EnzymeOption>) input;
                List<EnzymeOption> options = editor.enzymes().options();
                if (!box.getItems().equals(options)) {
                    box.getItems().setAll(options);
                }
                Optional<EnzymeOption> held = editor.enzymes().selected(field.name());
                box.setValue(held.orElse(null));
                box.setPromptText(
                        held.isPresent()
                                ? ""
                                : "Enzyme " + field.text() + " is not in the enzyme table");
            }
            case INTEGER_RANGE, DECIMAL_RANGE -> {
                if (!dirty && field.refusal().isEmpty()) {
                    RangeViewModel range = editor.ranges().range(field.name());
                    ((TextField) input).setText(range.firstText());
                    second.setText(range.secondText());
                }
            }
            default -> {
                if (!dirty) {
                    ((TextField) input).setText(field.text());
                }
            }
        }
    }

    private String originText() {
        return "Value from: " + field.originText();
    }

    private String lockText() {
        return field.lockReason().orElse("Not locked: you can change this value.");
    }

    /**
     * The help a tooltip and a screen reader give: the scientific meaning, the serialised tokens of
     * an enumeration, the default, the documentation page, the full path of a file, where the value
     * came from, any lock, and the validation state.
     */
    private String help() {
        List<String> lines = new ArrayList<>();
        lines.add(field.displayName() + " (" + field.name() + "). " + field.shortHelp());
        if (!field.choices().isEmpty()) {
            lines.add(
                    "Choices, with the value written to comet.params in brackets: "
                            + String.join(
                                    "; ",
                                    field.choices().stream().map(ChoiceOption::withToken).toList())
                            + ".");
        }
        if (field.kind() == ValueKind.BOOLEAN_FLAG || field.kind() == ValueKind.ION_SERIES_FLAG) {
            lines.add("Written to comet.params as 1 when on and 0 when off.");
        }
        if (field.kind() == ValueKind.FILE_PATH) {
            lines.add(
                    "Path: "
                            + (field.text().isEmpty() ? "none" : field.text())
                            + "; Choose... opens a file chooser.");
        }
        lines.add("Comet " + field.release().text() + " default: " + field.defaultText() + ".");
        if (field.isExpert()) {
            lines.add("An expert parameter: few searches need to change it.");
        }
        lines.add(originText() + ".");
        field.lockReason().ifPresent(reason -> lines.add(reason + "."));
        lines.add("Validation: " + field.stateText());
        lines.add("Documentation: " + field.helpUrl());
        return String.join("\n", lines);
    }
}
