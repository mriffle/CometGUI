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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.ui.controls.UiIds;

/**
 * The assumption a test of a fresh application rests on, stated as a value: the Tool Manager lists
 * no build as installed, and the application-data directory the test gave it is still not there.
 *
 * <p>A GUI test that is about a fresh application starts it over an application-data directory of
 * its own ({@link ParameterEditorApp#launchOver}), never through {@code main}, which reads this
 * machine's -- under this module's test configuration {@code $XDG_DATA_HOME/cometgui}, inside the
 * font stack under {@code tools/} -- and so makes whatever is installed there part of the test, and
 * on finding a Comet there creates and locks a project in it. Starting it elsewhere is half of the
 * isolation; this is the other half, the check that it held.
 */
final class FreshApplicationData {

    /**
     * What the Tool Manager says of a managed build nothing has installed: not installed, or one
     * this host cannot be offered at all.
     */
    static final Set<String> NOTHING_INSTALLED =
            Set.of(
                    "CometGUI-managed: not installed",
                    "CometGUI-managed: not published for this platform",
                    "CometGUI-managed: cannot run on this host");

    private FreshApplicationData() {}

    /**
     * What the Tool Manager says of each build it lists: the state line of every row, read from the
     * scene.
     *
     * @param driver the driver to ask
     * @return for example {@code "CometGUI-managed: not installed"}, one per row
     */
    static List<String> toolStates(FxUiDriver driver) {
        Node rows = driver.node(UiIds.TOOL_MANAGER_ROWS);
        return driver.callOnFxThread(
                () -> {
                    List<String> states = new ArrayList<>();
                    for (Node label : rows.lookupAll(".label")) {
                        String text = ((Labeled) label).getText();
                        if (text != null && text.startsWith("CometGUI-managed: ")) {
                            states.add(text);
                        }
                    }
                    return states;
                });
    }

    /**
     * Asserts that the running application is a fresh one over the directory the test gave it.
     *
     * @param application the running application
     * @param driver the driver to read the Tool Manager's rows with
     * @param applicationData the application-data directory the application was started over
     */
    static void assertFresh(
            RunningApplication application, FxUiDriver driver, Path applicationData) {
        application.awaitToolListRead();
        List<String> states = toolStates(driver);
        assertAll(
                "a fresh application over its own application-data directory",
                () ->
                        assertFalse(
                                states.isEmpty(),
                                "the Tool Manager lists the builds it offers on this host"),
                () ->
                        assertEquals(
                                List.of(),
                                states.stream()
                                        .filter(state -> !NOTHING_INSTALLED.contains(state))
                                        .toList(),
                                "builds the application found installed. This test is about a"
                                        + " fresh application, so it starts one over its own empty"
                                        + " application-data directory, "
                                        + applicationData
                                        + "; a build found installed came from somewhere else"),
                () ->
                        assertFalse(
                                Files.exists(applicationData),
                                "a fresh application with nothing installed creates nothing in"
                                        + " its application-data directory, "
                                        + applicationData));
    }
}
