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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;

/**
 * The one way this user interface gives a control an accessible name, and the mechanism that keeps
 * the promise true for the controls JavaFX creates behind our backs.
 *
 * <p><strong>What the phase's exit gate asks for.</strong> "Every control that exists has an
 * accessible name; a test enumerates them and fails on a missing one." The specification's
 * <em>Accessibility</em> principle is the reason: "Every interactive control requires an accessible
 * label ... custom JavaFX controls shall expose appropriate accessibility attributes."
 *
 * <h2>Explicit, never inferred</h2>
 *
 * <p>{@link #named(Control, String)} calls {@link Control#setAccessibleText(String)} and rejects a
 * blank name. It is deliberately not enough to let a {@code Labeled} fall back to its own text:
 * that fallback covers a {@code Label} and a {@code Button} and covers nothing else, so a rule
 * resting on it would be silent exactly where it matters -- a text field, a toggle whose meaning is
 * its position in a group, an icon-only action. Requiring the explicit attribute on every control
 * is both the strictest rule and the cheapest one to keep true, because a control with no name
 * cannot be created by an accident of omission: it is created by not calling this method.
 *
 * <h2>The controls nobody wrote</h2>
 *
 * <p>A {@code TextArea} is one control in the source and four in the scene graph: its skin builds a
 * {@code ScrollPane}, which builds two {@code ScrollBar}s, and all three are {@link Control}s that
 * a scene-graph enumeration finds. Nothing in this project constructs them, so nothing in this
 * project can name them at the point of construction -- and they do not exist at all until CSS is
 * applied and the skin is built, which happens long after the pane is assembled.
 *
 * <p>So {@link #named(Control, String)} also starts watching that control's children. Anything that
 * later appears underneath a named control and has no accessible name of its own is given a
 * generated one describing what it is and what it belongs to -- "scroll bar within console-output".
 * The watch is recursive and permanent, so a scroll bar that appears only when the console
 * overflows, three minutes into a run, is named the moment it is added.
 *
 * <p><strong>This is not a sweep over the whole scene, and that is the point.</strong> Only the
 * descendants of a control that was itself explicitly named are ever given a generated name. A
 * control that a view forgot to name is not a descendant of anything -- it is a child of a layout
 * pane -- so it is never reached, never generated for, and fails the enumeration exactly as it
 * should. A sweep over the whole scene would have made the gate unfailable, which is the one
 * outcome worse than not having it.
 */
public final class AccessibleControls {

    /**
     * Marks a {@link Parent} whose children are already being watched, so that a node reached twice
     * does not accumulate a second listener.
     */
    private static final Object WATCHED_KEY = new Object();

    private AccessibleControls() {}

    /**
     * Gives a control its accessible name and starts naming anything its skin later adds.
     *
     * @param <C> the control's type, so this can be used inline where the control is created
     * @param control the control to name
     * @param accessibleText what a screen reader should say for it: a noun phrase describing the
     *     control, not its position or its colour
     * @return {@code control}, for chaining
     * @throws NullPointerException if {@code control} or {@code accessibleText} is {@code null}
     * @throws IllegalArgumentException if {@code accessibleText} is blank, which is the failure the
     *     gate exists to catch and so is rejected where it happens rather than at enumeration time
     */
    public static <C extends Control> C named(C control, String accessibleText) {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(accessibleText, "accessibleText");
        if (accessibleText.isBlank()) {
            throw new IllegalArgumentException(
                    "a blank accessible name is not a name: "
                            + control.getClass().getSimpleName()
                            + " with id "
                            + control.getId());
        }
        control.setAccessibleText(accessibleText);
        watch(control);
        return control;
    }

    /**
     * Gives a control its accessible name and an accessible role, for the cases where the control's
     * default role does not describe what it actually is.
     *
     * @param <C> the control's type
     * @param control the control to name
     * @param role the role a screen reader should announce
     * @param accessibleText what a screen reader should say for it
     * @return {@code control}, for chaining
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code accessibleText} is blank
     */
    public static <C extends Control> C named(
            C control, AccessibleRole role, String accessibleText) {
        Objects.requireNonNull(control, "control")
                .setAccessibleRole(Objects.requireNonNull(role, "role"));
        return named(control, accessibleText);
    }

    /**
     * Watches a parent's children, naming the controls that appear under it now and later.
     *
     * @param parent the parent to watch; watching it twice is a no-op
     */
    private static void watch(Parent parent) {
        if (parent.getProperties().putIfAbsent(WATCHED_KEY, Boolean.TRUE) != null) {
            return;
        }
        ObservableList<Node> children = parent.getChildrenUnmodifiable();
        nameGenerated(children);
        children.addListener(
                (ListChangeListener<Node>)
                        change -> {
                            while (change.next()) {
                                nameGenerated(change.getAddedSubList());
                            }
                        });
    }

    /**
     * Gives every unnamed control among these nodes a generated name, and watches every parent.
     *
     * @param nodes the nodes that have just appeared underneath a named control
     */
    private static void nameGenerated(List<? extends Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof Control control
                    && (control.getAccessibleText() == null
                            || control.getAccessibleText().isBlank())) {
                control.setAccessibleText(generatedNameFor(control));
            }
            if (node instanceof Parent parent) {
                watch(parent);
            }
        }
    }

    /**
     * A name for a control this project did not create: what it is, and what it belongs to.
     *
     * @param control a control built by some other control's skin
     * @return for example {@code "scroll bar within console-output"}
     */
    private static String generatedNameFor(Control control) {
        String kind = spacedTypeName(control.getClass().getSimpleName());
        String owner = nearestIdentifiedAncestor(control);
        return owner == null ? kind : kind + " within " + owner;
    }

    /**
     * Turns a Java type name into words: {@code ScrollBar} becomes {@code scroll bar}.
     *
     * @param typeName a simple class name, never empty for a named class
     * @return the same name in lower case with a space before each internal capital
     */
    private static String spacedTypeName(String typeName) {
        StringBuilder spaced = new StringBuilder(typeName.length() + 4);
        for (int i = 0; i < typeName.length(); i++) {
            char letter = typeName.charAt(i);
            if (i > 0 && Character.isUpperCase(letter)) {
                spaced.append(' ');
            }
            spaced.append(letter);
        }
        return spaced.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * The identifier of the nearest ancestor that has one, which is the control the skin belongs to
     * for anything this method is asked about.
     *
     * @param node the node to look up from, exclusive
     * @return the ancestor's identifier, or {@code null} if no ancestor carries one
     */
    private static String nearestIdentifiedAncestor(Node node) {
        for (Node above = node.getParent(); above != null; above = above.getParent()) {
            if (above.getId() != null && !above.getId().isBlank()) {
                return above.getId();
            }
        }
        return null;
    }
}
