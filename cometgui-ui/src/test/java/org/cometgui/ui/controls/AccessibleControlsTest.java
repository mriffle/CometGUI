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

package org.cometgui.ui.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.cometgui.ui.testing.FxToolkit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The accessible-naming mechanism, including the half of it that cannot be seen without a skin.
 *
 * <p>The interesting test here is the last one. A {@code TextArea} is one control in the source and
 * several in the scene graph, because its skin builds a scroll pane and scroll bars of its own --
 * after the pane has been assembled, when CSS is applied. Those are controls that exist, so the
 * phase's fourth exit-gate item covers them, and nothing in this project is in a position to name
 * them at construction. If the mechanism that names them stopped working, no other test in this
 * module would notice.
 */
class AccessibleControlsTest {

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @Test
    @DisplayName("a name is set explicitly, and returned for chaining")
    void aNameIsSetExplicitly() {
        Button button = new Button("Run");
        assertSame(button, AccessibleControls.named(button, "start the search"));
        assertEquals("start the search", button.getAccessibleText());
    }

    @Test
    @DisplayName("a role is set where the default would mislead")
    void aRoleIsSetWhereTheDefaultWouldMislead() {
        Button button = new Button("Run");
        AccessibleControls.named(button, AccessibleRole.RADIO_BUTTON, "the Run section");
        assertEquals(AccessibleRole.RADIO_BUTTON, button.getAccessibleRole());
        assertEquals("the Run section", button.getAccessibleText());
    }

    @Test
    @DisplayName("a blank name is refused where it happens, not at enumeration time")
    void aBlankNameIsRefused() {
        Button button = new Button("Run");
        button.setId("run-button");
        IllegalArgumentException blank =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> AccessibleControls.named(button, "   "));
        assertTrue(
                blank.getMessage().contains("run-button"),
                () -> "the diagnostic must name the control, but said: " + blank.getMessage());
        assertThrows(
                NullPointerException.class, () -> AccessibleControls.named(button, (String) null));
    }

    @Test
    @DisplayName("controls a skin builds later are named as they appear")
    void controlsASkinBuildsLaterAreNamedAsTheyAppear() throws InterruptedException {
        TextArea area = new TextArea("a line of tool output");
        area.setId("probe-output");
        AccessibleControls.named(area, "probe output");

        FxToolkit.onFxThread(
                () -> {
                    Scene scene = new Scene(new VBox(area), 200, 80);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                });

        List<Control> built = controlsUnder(area);
        assertFalse(
                built.isEmpty(),
                "a TextArea's skin builds controls of its own; with none built this test proves"
                        + " nothing and the mechanism is untested");
        List<String> unnamed = new ArrayList<>();
        for (Control control : built) {
            String name = control.getAccessibleText();
            if (name == null || name.isBlank()) {
                unnamed.add(control.getClass().getSimpleName());
            }
        }
        assertEquals(List.of(), unnamed, "every control the skin built must carry a name");
        assertTrue(
                built.stream().anyMatch(c -> c.getAccessibleText().contains("within probe-output")),
                () ->
                        "a generated name must say what the control belongs to, but the names"
                                + " were: "
                                + built.stream().map(Control::getAccessibleText).toList());
    }

    /** Every control below this one: by construction, everything a skin built. */
    private static List<Control> controlsUnder(Parent root) {
        List<Control> found = new ArrayList<>();
        collect(root.getChildrenUnmodifiable(), found);
        return found;
    }

    private static void collect(List<Node> nodes, List<Control> found) {
        for (Node node : nodes) {
            if (node instanceof Control control) {
                found.add(control);
            }
            if (node instanceof Parent parent) {
                collect(List.copyOf(parent.getChildrenUnmodifiable()), found);
            }
        }
    }
}
