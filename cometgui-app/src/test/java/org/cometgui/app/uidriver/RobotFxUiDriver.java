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
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;

/**
 * The {@link FxUiDriver} on {@code javafx.scene.robot.Robot} alone: the fallback, kept working
 * rather than kept in reserve.
 *
 * <p>docs/feasibility/gui-automation-spike.rst names this mechanism as the project's fallback and
 * proves it rather than proposing it -- {@code ToolkitFallbackTest} moved the pointer with {@code
 * localToScreen}, clicked, and typed for real under Monocle Headless, with {@code javap} showing
 * zero TestFX references on its code path. The reasoning there applies here: "TestFX 4.0.18 dates
 * from February 2024 and its Monocle shim from the same month; the next JavaFX that breaks it will
 * break it in CI, on a Friday."
 *
 * <p><strong>There is no TestFX on this class's code path.</strong> It imports {@code
 * javafx.scene.robot.Robot}, finds nodes with {@code Scene.lookup} and computes a pointer target
 * from the node's own screen bounds. If TestFX stopped working tomorrow, {@code
 * SectionNavigationUiTest} and {@code KeyboardOnlyNavigationUiTest} would still have a passing
 * parameter, and the phase's first two exit-gate items would still have evidence.
 *
 * <p><strong>Everything happens on the application thread.</strong> {@code Robot} may only be used
 * there. The synthetic events it posts are then processed by the same thread, so each input method
 * ends with a barrier and an assertion on the next line sees the result -- no sleep, no retry.
 */
public final class RobotFxUiDriver extends AbstractFxUiDriver {

    private final Robot robot;

    /**
     * A driver over a running application, with a robot created on the application thread.
     *
     * @param application the application to drive
     * @throws NullPointerException if {@code application} is {@code null}
     */
    public RobotFxUiDriver(RunningApplication application) {
        super(Objects.requireNonNull(application, "application"));
        this.robot = callOnFxThread(Robot::new);
    }

    @Override
    Node lookup(String id) {
        return application().scene().lookup("#" + id);
    }

    @Override
    public void clickOn(String id) {
        Node target = node(id);
        onFxThread(
                () -> {
                    Point2D centre = centreOf(target);
                    robot.mouseMove(centre);
                    robot.mouseClick(MouseButton.PRIMARY);
                });
        barrier();
    }

    @Override
    public void press(KeyCode code) {
        onFxThread(() -> robot.keyType(code));
        barrier();
    }

    @Override
    public void tab() {
        press(KeyCode.TAB);
    }

    @Override
    public void shiftTab() {
        onFxThread(
                () -> {
                    robot.keyPress(KeyCode.SHIFT);
                    robot.keyType(KeyCode.TAB);
                    robot.keyRelease(KeyCode.SHIFT);
                });
        barrier();
    }

    @Override
    public String toString() {
        return "JavaFX robot (javafx.scene.robot.Robot, no TestFX)";
    }
}
