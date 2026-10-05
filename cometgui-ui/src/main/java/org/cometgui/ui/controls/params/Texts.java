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

import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Labels and buttons whose accessible text follows their text: every piece of state the editor
 * states in words is also what a screen reader reads, and it is never blank.
 */
final class Texts {

    private Texts() {}

    /**
     * A wrapping label with an identifier, whose accessible text is always its text -- or the
     * fallback while the text is blank, so the label never loses its name.
     *
     * @param id the stable identifier
     * @param text the first text
     * @param fallback what a screen reader reads while the text is blank
     * @return the label
     */
    static Label label(String id, String text, String fallback) {
        Label label = new Label(text);
        label.setId(id);
        label.setWrapText(true);
        named(label, text.isBlank() ? fallback : text);
        label.accessibleTextProperty()
                .bind(
                        Bindings.createStringBinding(
                                () ->
                                        label.getText() == null || label.getText().isBlank()
                                                ? fallback
                                                : label.getText(),
                                label.textProperty()));
        return label;
    }

    /**
     * A button with an identifier and an accessible name saying what it acts on.
     *
     * @param id the stable identifier
     * @param text what the button shows
     * @param accessibleText what a screen reader reads: the action and its object
     * @return the button
     */
    static Button button(String id, String text, String accessibleText) {
        Button button = new Button(text);
        button.setId(id);
        named(button, accessibleText);
        return button;
    }
}
