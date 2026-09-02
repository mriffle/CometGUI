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

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.testfx.api.FxRobot;

/**
 * The {@link FxUiDriver} on TestFX 4.0.18, which is the implementation phase 00 chose.
 *
 * <p>docs/feasibility/gui-automation-spike.rst, <em>TestFX verdict</em>: "TestFX 4.0.18 works
 * against JDK 25.0.4.1+1 / JavaFX 25.0.4+1, headless via Monocle and headed via GTK." The nine JVM
 * options it needs are in cometgui-app/pom.xml, and {@code reuseForks=false} is there for the trap
 * the spike documents -- a TestFX robot's clicks stop arriving when another test class has already
 * owned the toolkit in the same JVM, and the tempting "fix" is to weaken the assertion.
 *
 * <p><strong>What is used, and what is not.</strong> Only {@link FxRobot}: its identifier lookup,
 * its {@code clickOn} and its {@code push}. TestFX's own {@code FxToolkit} does not start the
 * application here -- {@link RunningApplication#launchedByMain()} does, through the product's own
 * {@code main} -- so what this class contributes is synthetic input and nothing else. That keeps
 * the two drivers comparable: they drive the same application, started the same way, and differ
 * only in how a click and a key press are made.
 *
 * <p>No TestFX type appears in {@link FxUiDriver}, which is the whole point of the interface: a
 * test names {@code clickOn("nav-results")}, not a robot.
 */
public final class TestFxUiDriver extends AbstractFxUiDriver {

    private final FxRobot robot = new FxRobot();

    /**
     * A driver over a running application.
     *
     * @param application the application to drive
     * @throws NullPointerException if {@code application} is {@code null}
     */
    public TestFxUiDriver(RunningApplication application) {
        super(Objects.requireNonNull(application, "application"));
    }

    @Override
    Node lookup(String id) {
        return robot.lookup("#" + id).<Node>tryQuery().orElse(null);
    }

    @Override
    public void clickOn(String id) {
        node(id);
        robot.clickOn("#" + id);
        barrier();
    }

    @Override
    public void press(KeyCode code) {
        robot.push(code);
        barrier();
    }

    @Override
    public void tab() {
        press(KeyCode.TAB);
    }

    @Override
    public void shiftTab() {
        robot.push(KeyCode.SHIFT, KeyCode.TAB);
        barrier();
    }

    @Override
    public String toString() {
        return "TestFX 4.0.18 (org.testfx.api.FxRobot)";
    }
}
