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

package org.cometgui.app.uidriver;

import java.util.List;

/**
 * The part of {@link FxUiDriver} that can be called from another process: every argument and every
 * result is a {@code String}, a {@code boolean}, an {@code int} or a {@code List<String>} (Phase 14
 * design decision P14-3).
 *
 * <h2>Why the subset exists</h2>
 *
 * <p>The end-to-end scenarios are written once, against this interface, and run in two tiers. In
 * Tier A the implementation is one of the in-process drivers, {@link TestFxUiDriver} or {@link
 * RobotFxUiDriver}, which implement it through {@link FxUiDriver}. In Tier B the application is a
 * packaged artefact in another process, and the implementation is a client that forwards each call
 * over a loopback bridge to a {@link RobotFxUiDriver} running inside that process. A scene-graph
 * {@code Node}, a {@code KeyCode} or a lambda cannot cross that boundary; a string can. So nothing
 * that cannot be written down as text belongs here, and there is no second implementation of what a
 * click or a selection means: the bridge's far end is this same code.
 *
 * <h2>Rules for adding a method</h2>
 *
 * <ul>
 *   <li>Arguments and result are {@code String}, {@code boolean}, {@code int}, {@code void} or
 *       {@code List<String>} -- nothing else, including no {@code null} meaning something other
 *       than "absent". {@code RemotableUiDriverShapeTest} fails on anything else.
 *   <li>Controls are named by their stable identifier ({@code R-TEST-04}): never by position, text
 *       on screen or CSS ancestry.
 *   <li>Actions use synthetic input, the way a user acts; reads read scene-graph state on the
 *       application thread. Neither ever calls a view-model or a controller.
 *   <li>It is implemented once, in {@code AbstractFxUiDriver} where the two drivers agree, and is
 *       proved against the real application through both drivers before anything relies on it.
 * </ul>
 *
 * <h2>Threading</h2>
 *
 * <p>As for {@link FxUiDriver}: every method may be called from the test thread and blocks until
 * the application thread has finished, and synthetic input waits until the events it generated have
 * been processed.
 */
public interface RemotableUiDriver {

    /**
     * Whether a node with the given identifier exists anywhere in the application's scene, shown or
     * not.
     *
     * @param id the stable identifier, without a leading {@code #}
     * @return {@code true} if the scene holds such a node
     */
    boolean exists(String id);

    /**
     * Whether the node with the given identifier is being shown to the user.
     *
     * <p>Not merely {@code Node.isVisible()}, which is a local flag: this answers the question a
     * test actually asks, which is whether the thing is on screen. A node inside an invisible
     * ancestor is not showing however visible it says it is, and the shell hides eight of its nine
     * section panes exactly that way.
     *
     * @param id the stable identifier
     * @return {@code true} if the node exists, is in a scene, and neither it nor any ancestor is
     *     invisible
     */
    boolean isVisible(String id);

    /**
     * Whether the node with the given identifier is disabled: {@code Node.isDisabled()}, which is
     * {@code true} when it or any ancestor is disabled -- the state in which a click on it does
     * nothing.
     *
     * @param id the stable identifier
     * @return {@code true} if a user cannot operate it
     * @throws AssertionError if no node carries that identifier
     */
    boolean isDisabled(String id);

    /**
     * Clicks the node with the given identifier with the primary mouse button, using synthetic
     * input.
     *
     * <p>A real pointer movement to the node's own screen bounds and a real button press, not a
     * fired {@code ActionEvent}: exit-gate item 1 is about reachability by mouse, and firing the
     * handler would prove only that the handler works.
     *
     * @param id the stable identifier of the node to click
     * @throws AssertionError if no node carries that identifier
     */
    void clickOn(String id);

    /**
     * Types text into whatever has the keyboard focus, one character at a time, using synthetic
     * input: what a scientist typing would produce, and nothing else -- no text is set directly.
     *
     * @param text the characters to type: letters, digits, space and {@code - _ . / , + = ^ :}
     * @throws AssertionError if the text holds a character this driver cannot type, naming it
     */
    void type(String text);

    /**
     * Clicks a text control, selects everything it holds with the keyboard (Ctrl+A), and types text
     * in its place. Does not commit: the test presses Enter or Tab when it means to.
     *
     * @param id the stable identifier of the text control
     * @param text what to type
     * @throws AssertionError if no node carries that identifier, or a character cannot be typed
     */
    void typeInto(String id, String text);

    /**
     * Presses and releases one key, named as {@code javafx.scene.input.KeyCode} names it.
     *
     * @param key the key's {@code KeyCode} constant name, for example {@code "ENTER"}, {@code
     *     "DOWN"} or {@code "ESCAPE"}
     * @throws AssertionError if no {@code KeyCode} has that name, naming it
     */
    void pressKey(String key);

    /**
     * Presses a key while holding a modifier, both named as {@code KeyCode} names them.
     *
     * @param modifier the modifier's constant name, for example {@code "CONTROL"} or {@code
     *     "SHIFT"}
     * @param key the key's constant name
     * @throws AssertionError if either name is not a {@code KeyCode}, naming it
     */
    void pressKeyWith(String modifier, String key);

    /** Presses and releases Tab, moving the focus to the next focus stop. */
    void tab();

    /** Presses and releases Shift+Tab, moving the focus to the previous focus stop. */
    void shiftTab();

    /**
     * The identifier of the node that currently has the keyboard focus.
     *
     * @return the focus owner's identifier, or {@code null} if nothing is focused or the focused
     *     node has no identifier -- both of which are failures worth reporting as themselves rather
     *     than as an exception
     */
    String focusedNodeId();

    /**
     * What a control says: the text of a {@code Labeled} or of a text input control.
     *
     * @param id the stable identifier
     * @return the control's text, never {@code null}
     * @throws AssertionError if no node carries that identifier, or if it is not a control with
     *     text
     */
    String textOf(String id);

    /**
     * What a screen reader would say for a control.
     *
     * @param id the stable identifier
     * @return the node's accessible text, or {@code null} if it has none -- which is the failure
     *     accessibility gates exist to catch, so it is reported as a value rather than as an
     *     exception
     * @throws AssertionError if no node carries that identifier
     */
    String accessibleTextOf(String id);

    /**
     * Chooses one item of a combo box by the text it shows for that item, the way a user does with
     * the keyboard: a click opens it, the arrow keys move to the item, and Enter closes it.
     *
     * @param id the stable identifier of the combo box
     * @param item the item's text, as the combo box shows it
     * @throws AssertionError if no node carries that identifier, if it is not a non-editable combo
     *     box, if it is disabled, if it offers no item with that text (naming what it offers), or
     *     if after the keystrokes it does not show that item
     */
    void select(String id, String item);

    /**
     * The text a combo box shows for its current value.
     *
     * @param id the stable identifier of the combo box
     * @return the shown text; empty when nothing is chosen
     * @throws AssertionError if no node carries that identifier or it is not a combo box
     */
    String selectedTextOf(String id);

    /**
     * The texts a combo box shows for the items it offers, in order.
     *
     * @param id the stable identifier of the combo box
     * @return the offered items' texts
     * @throws AssertionError if no node carries that identifier or it is not a combo box
     */
    List<String> itemsOf(String id);

    /**
     * The headings of a table's visible columns, left to right: a column's own text, or -- where
     * the column's heading is a control, as the product's sortable headings are -- that control's
     * text.
     *
     * @param id the stable identifier of the {@code TableView}
     * @return one heading per visible column
     * @throws AssertionError if no node carries that identifier, or it is not a table
     */
    List<String> columnsOf(String id);

    /**
     * The rows a table or list holds, as text: every item it holds, not only those scrolled into
     * view.
     *
     * <p>For a {@code TableView}, one string per item: the text of each visible column's cell, left
     * to right, separated by a tab ({@code \t}). For a {@code ListView}, one string per item: the
     * text the list's own cell shows for it. A cell shows nothing for a {@code null} value; that is
     * read as the empty string.
     *
     * @param id the stable identifier of the {@code TableView} or {@code ListView}
     * @return the rows, in the order the control holds them
     * @throws AssertionError if no node carries that identifier, it is neither a table nor a list,
     *     or a cell's text cannot be read without rendering a graphic (naming the column)
     */
    List<String> rowsOf(String id);
}
