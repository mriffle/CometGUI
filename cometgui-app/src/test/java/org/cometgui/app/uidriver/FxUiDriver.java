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
 * <p>It is small on purpose. Every method here is used by a test; a driver method that no test
 * calls is a method whose behaviour on the fallback implementation is unproved, and an unproved
 * fallback is exactly the thing this interface exists to avoid.
 *
 * <h2>Two layers</h2>
 *
 * <p>Everything that can be said in strings, booleans, integers and lists of strings -- clicking,
 * typing, reading text, choosing an item, reading a table -- is declared by {@link
 * RemotableUiDriver}, which this interface extends, so that an end-to-end scenario written against
 * that subset runs unchanged in-process and, through a loopback bridge, against a packaged
 * application in another process (Phase 14, P14-3). What is declared here in addition cannot cross
 * a process boundary: a scene-graph {@link Node}, a {@link KeyCode}, work to run on the application
 * thread.
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
public interface FxUiDriver extends RemotableUiDriver {

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
     * Presses and releases one key, using synthetic input.
     *
     * @param code the key to press
     */
    void press(KeyCode code);

    /**
     * Presses a key while holding a modifier, using synthetic input.
     *
     * @param modifier the modifier to hold, such as {@code CONTROL} or {@code SHIFT}
     * @param code the key to press
     */
    void pressWith(KeyCode modifier, KeyCode code);

    /**
     * Runs work on the JavaFX application thread and waits for it.
     *
     * @param work what to run
     * @throws AssertionError if the work does not finish within the driver's timeout, or if the
     *     work itself failed an assertion -- rethrown as it was thrown, so that a check made on the
     *     application thread reads as the test failure it is
     * @throws IllegalStateException if the work threw anything else, with the cause attached
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
