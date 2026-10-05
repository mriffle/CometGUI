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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.UiIds.Surface;
import org.cometgui.ui.controls.params.EnzymeTableEditor;
import org.cometgui.ui.controls.params.FieldControl;
import org.cometgui.ui.controls.params.Subscriptions;
import org.cometgui.ui.controls.params.VariableModEditor;
import org.cometgui.ui.viewmodel.params.AdvancedCategory;
import org.cometgui.ui.viewmodel.params.FieldViewModel;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.VariableModsViewModel;

/**
 * The Advanced level: every parameter of the release, grouped in the specification's fourteen
 * categories, each category shown or hidden by its own switch (a toggle button, reachable by Tab
 * and operated with Space) and each with a reset that asks to confirm before it changes anything.
 *
 * <p>Every field is its typed control; the variable-modification slots are the slot editor, as in
 * Essentials; the digestion category also holds the custom-enzyme editor over the enzyme table. A
 * hidden category's controls stay in the scene, hidden, so that a summary entry can show the
 * category and move the focus into it.
 *
 * <p>Built for one release; the editor builds a new one when the release changes.
 */
final class AdvancedView extends VBox {

    private final Map<String, Runnable> focusers = new HashMap<>();

    private final Map<String, ParameterCategory> categoryOf = new HashMap<>();

    private final Map<ParameterCategory, ToggleButton> toggles =
            new EnumMap<>(ParameterCategory.class);

    /**
     * The Advanced level of the session's release.
     *
     * @param session the session
     * @param editor the editor
     * @param mods the variable-modification view-model
     * @param subscriptions where this build's listeners are registered
     */
    AdvancedView(
            ParameterSession session,
            ParameterEditorViewModel editor,
            VariableModsViewModel mods,
            Subscriptions subscriptions) {
        Objects.requireNonNull(editor, "editor");
        setId(UiIds.PARAM_ADVANCED);
        setSpacing(10);
        setPadding(new Insets(4, 8, 4, 0));
        for (AdvancedCategory category : session.advanced()) {
            getChildren().add(category(category, session, editor, mods, subscriptions));
        }
    }

    /**
     * Shows the category holding a parameter and moves the keyboard focus to its control.
     *
     * @param name the parameter
     * @return whether this level has it
     */
    boolean focus(String name) {
        ParameterCategory category = categoryOf.get(name);
        Runnable focuser = focusers.get(name);
        if (category == null || focuser == null) {
            return false;
        }
        toggles.get(category).setSelected(true);
        focuser.run();
        return true;
    }

    private VBox category(
            AdvancedCategory category,
            ParameterSession session,
            ParameterEditorViewModel editor,
            VariableModsViewModel mods,
            Subscriptions subscriptions) {
        ParameterCategory id = category.category();
        List<FieldViewModel> fields = category.fields();
        String release = session.release().text();
        VBox box = new VBox(6);
        box.setId(UiIds.advancedCategory(id));

        ToggleButton toggle = new ToggleButton();
        toggle.setId(UiIds.advancedCategoryToggle(id));
        named(toggle, category.title() + " category, " + fields.size() + " parameters");
        toggle.setAccessibleHelp("Shows or hides the parameters of this category.");
        toggles.put(id, toggle);

        Button reset = new Button("Reset category...");
        reset.setId(UiIds.advancedCategoryReset(id));
        named(reset, "Reset every parameter of " + category.title() + " (asks to confirm)");
        Button confirm =
                new Button(
                        "Reset "
                                + fields.size()
                                + " parameters to the Comet "
                                + release
                                + " defaults");
        confirm.setId(UiIds.advancedCategoryResetConfirm(id));
        named(confirm, confirm.getText() + " in " + category.title());
        Button cancel = new Button("Keep the values");
        cancel.setId(UiIds.advancedCategoryResetCancel(id));
        named(cancel, "Keep the values of " + category.title());
        Label resetStatus = new Label("");
        named(resetStatus, "reset of " + category.title());
        showConfirmation(false, reset, confirm, cancel);
        reset.setOnAction(event -> showConfirmation(true, reset, confirm, cancel));
        cancel.setOnAction(
                event -> {
                    showConfirmation(false, reset, confirm, cancel);
                    resetStatus.setText("Nothing was reset.");
                });
        confirm.setOnAction(
                event -> {
                    List<String> done = session.resetCategory(id);
                    showConfirmation(false, reset, confirm, cancel);
                    resetStatus.setText(
                            done.size()
                                    + " parameters reset to the Comet "
                                    + release
                                    + " defaults; locked outputs kept.");
                    reset.requestFocus();
                });
        HBox header = new HBox(8, toggle, reset, confirm, cancel, resetStatus);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8);
        content.setPadding(new Insets(0, 0, 0, 16));
        boolean slotsShown = false;
        for (FieldViewModel field : fields) {
            categoryOf.put(field.name(), id);
            if (field.kind() == ValueKind.VARIABLE_MOD_TUPLE) {
                if (!slotsShown) {
                    VariableModEditor slots =
                            new VariableModEditor(
                                    Surface.ADVANCED, session, editor, mods, subscriptions, false);
                    content.getChildren().add(slots);
                    for (FieldViewModel slot : fields) {
                        if (slot.kind() == ValueKind.VARIABLE_MOD_TUPLE) {
                            focusers.put(slot.name(), () -> slots.focus(slot.name()));
                        }
                    }
                    slotsShown = true;
                }
                continue;
            }
            FieldControl control =
                    new FieldControl(field, Surface.ADVANCED, session, editor, subscriptions);
            focusers.put(field.name(), control::focus);
            content.getChildren().add(control);
        }
        if (id == ParameterCategory.DIGESTION_ENZYMES) {
            content.getChildren()
                    .add(new EnzymeTableEditor(editor.enzymes(), session, subscriptions));
        }
        Runnable show =
                () -> {
                    boolean open = toggle.isSelected();
                    toggle.setText(
                            (open ? "Hide " : "Show ")
                                    + category.title()
                                    + " ("
                                    + fields.size()
                                    + " parameters)");
                    content.setVisible(open);
                    content.setManaged(open);
                };
        toggle.selectedProperty().addListener((observable, before, after) -> show.run());
        show.run();
        box.getChildren().addAll(header, content);
        return box;
    }

    private static void showConfirmation(
            boolean asking, Button reset, Button confirm, Button cancel) {
        reset.setVisible(!asking);
        reset.setManaged(!asking);
        for (Button answer : List.of(confirm, cancel)) {
            answer.setVisible(asking);
            answer.setManaged(asking);
        }
        if (asking) {
            confirm.requestFocus();
        }
    }
}
