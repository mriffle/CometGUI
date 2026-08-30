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

import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;

/**
 * What a GUI test is allowed to do to the running application: find a control by its stable
 * identifier, click it, move the keyboard focus, press a key, read what a control says, read what a
 * screen reader would say, ask whether something is on screen, and run work on the JavaFX
 * application thread.
 *
 * <h2>Why this interface exists</h2>
 *
 * <p>specification.rst, <em>JavaFX GUI automation</em>: TestFX's "compatibility with the selected
 * JDK and JavaFX versions shall be proven in an early spike rather than assumed. If it cannot
 * operate reliably in CI, the project shall retain the same test semantics behind a small {@code
 * FxUiDriver} abstraction and use a compatible robot or accessibility automation mechanism." Phase
 * 00 proved TestFX 4.0.18 does work here and recommended keeping the fallback as a first-class
 * citizen. So there are two implementations -- {@link TestFxUiDriver} and {@link RobotFxUiDriver}
 * -- and {@code SectionNavigationUiTest} and {@code KeyboardOnlyNavigationUiTest} run the same
 * navigation through both. A driver that had never been run would not be a fallback.
 *
 * <h2>What it deliberately does not offer</h2>
 *
 * <p>There is no method that takes a pixel coordinate and none that takes a CSS selector beyond an
 * identifier, because {@code R-TEST-04} forbids locating important controls "by pixel coordinates
 * or brittle CSS ancestry". A test says {@code clickOn(UiIds.navigationEntry(SectionId.RESULTS))};
 * it never says "the third button from the top".
 *
 * <p>There is no method that reaches into a view-model, fires an {@code ActionEvent} or calls a
 * controller. Everything an implementation does to the interface it does with synthetic input, the
 * way a user would, which is what makes exit-gate item 1 -- "reachable by mouse and by keyboard
 * alone" -- mean anything. Reading is a different matter: assertions read scene-graph state
 * directly, because a test that could only read through the same robot it typed with would be
 * asserting its own echo.
 *
 * <p>It is small on purpose. Every method here is used by a test in this work unit; a driver method
 * that no test calls is a method whose behaviour on the fallback implementation is unproved, and an
 * unproved fallback is exactly the thing this interface exists to avoid.
 *
 * <h2>Threading</h2>
 *
 * <p>Every method may be called from the test thread and blocks until the application thread has
 * finished with it. Implementations marshal the work themselves and rethrow whatever it threw, so a
 * test never writes {@code Platform.runLater} and never sees a failure swallowed by the FX thread's
 * exception handler. Synthetic input additionally waits until the events it generated have been
 * processed, so that an assertion made on the next line sees their effect: the waiting is on
 * observable state, never on a sleep.
 */
public interface FxUiDriver {

    /**
     * The node with the given stable identifier.
     *
     * @param id the identifier the interface set with {@code setId}, without a leading {@code #};
     *     {@code org.cometgui.ui.controls.UiIds} publishes every one of them
     * @return the node
     * @throws AssertionError if no node in the application carries that identifier, naming it
     */
    Node node(String id);

    /**
     * Whether the node with the given identifier is being shown to the user.
     *
     * <p>Not merely {@code Node.isVisible()}, which is a local flag: this answers the question a
     * test actually asks, which is whether the thing is on screen. A node inside an invisible
     * ancestor is not showing however visible it says it is, and the shell hides nine of its ten
     * section panes exactly that way.
     *
     * @param id the stable identifier
     * @return {@code true} if the node exists, is in a scene, and neither it nor any ancestor is
     *     invisible
     */
    boolean isVisible(String id);

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
     * Presses and releases one key, using synthetic input.
     *
     * @param code the key to press
     */
    void press(KeyCode code);

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
     *     exit-gate item 4 exists to catch, so it is reported as a value rather than as an
     *     exception
     * @throws AssertionError if no node carries that identifier
     */
    String accessibleTextOf(String id);

    /**
     * Runs work on the JavaFX application thread and waits for it.
     *
     * @param work what to run
     * @throws AssertionError if the work does not finish within the driver's timeout
     * @throws IllegalStateException if the work threw, with the cause attached
     */
    void onFxThread(Runnable work);

    /**
     * Computes a value on the JavaFX application thread and returns it.
     *
     * <p>This is how an assertion reads scene-graph state without touching it from the test thread.
     *
     * @param <T> the value's type
     * @param work what to compute
     * @return what {@code work} returned
     * @throws AssertionError if the work does not finish within the driver's timeout
     * @throws IllegalStateException if the work threw, with the cause attached
     */
    <T> T callOnFxThread(Supplier<T> work);
}
