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

package org.cometgui.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.cometgui.ui.testing.ViewModelSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The view-model layer is independent of the JavaFX toolkit, and this proves it over the whole
 * package rather than one class at a time.
 *
 * <p>The rest of this suite is the same evidence from the other direction: every test in this
 * package constructs and drives these classes with no toolkit started, no {@code Platform.startup}
 * and no display. If any of the constructs forbidden below appeared, those tests would stop being
 * ordinary unit tests -- and the failure would arrive as {@code Toolkit not initialized} in some
 * later phase's suite rather than as a clear statement of what was broken here.
 *
 * <p>JavaFX <em>properties and observable collections</em> are deliberately not forbidden. They
 * need neither a toolkit nor a display and they are how a view-model publishes state; it is the
 * scene graph, the stage, the application class and the FX application thread that must stay on the
 * other side of the boundary.
 */
class ViewModelIndependenceTest {

    /**
     * Constructs no view-model in this package may contain, each with the reason it is banned.
     *
     * <p>Matched against the source text -- imports, qualified names and Javadoc alike. A mention
     * in a comment is a false positive in principle, and the classes here are written not to have
     * one: the surrounding prose says "{@code Platform.runLater}" inside a {@code @code} tag, which
     * does not contain the {@code Platform.runLater(} form matched here, and refers to the scene
     * graph by name rather than by package.
     */
    private static final Map<String, String> FORBIDDEN =
            Map.of(
                    "javafx.scene",
                            "the scene graph belongs to the view: a view-model that built a Node"
                                    + " could not be tested without a toolkit",
                    "javafx.stage",
                            "a window belongs to the bootstrap and the view, never to a view-model",
                    "javafx.application",
                            "Platform and Application need a started toolkit; marshalling onto the"
                                    + " FX application thread is the view's job",
                    "javafx.fxml", "FXML is a view concern",
                    "Platform.runLater(",
                            "marshalling onto the FX application thread is the view's job; one call"
                                    + " would make this package untestable without a toolkit");

    @Test
    @DisplayName("no view-model source mentions the scene graph, a stage, or the FX thread")
    void noViewModelTouchesTheToolkit() {
        Map<String, String> sources = ViewModelSources.all();
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            for (Map.Entry<String, String> banned : FORBIDDEN.entrySet()) {
                if (source.getValue().contains(banned.getKey())) {
                    violations.add(
                            source.getKey()
                                    + " contains '"
                                    + banned.getKey()
                                    + "' -- "
                                    + banned.getValue());
                }
            }
        }
        assertEquals(List.of(), violations, "view-model layer is not toolkit-independent");
    }

    @Test
    @DisplayName("the scan actually reads this package's six classes and its package-info")
    void theScanIsNotVacuous() {
        Map<String, String> sources = ViewModelSources.all();
        assertEquals(
                List.of(
                        "ConsoleViewModel.java",
                        "HostBaselineViewModel.java",
                        "NavigationViewModel.java",
                        "NonNullProperty.java",
                        "SectionId.java",
                        "StageStepperViewModel.java",
                        "package-info.java"),
                List.copyOf(sources.keySet()),
                "a source scan that read the wrong or an empty directory would pass over anything");
        for (Map.Entry<String, String> source : sources.entrySet()) {
            assertTrue(
                    source.getValue().contains("package org.cometgui.ui.viewmodel;"),
                    source.getKey() + " is not in the view-model package");
        }
    }

    @Test
    @DisplayName("every view-model source carries the project's copyright line (D-009)")
    void everySourceCarriesTheCopyrightLine() {
        for (Map.Entry<String, String> source : ViewModelSources.all().entrySet()) {
            assertTrue(
                    source.getValue().contains(" * Copyright (C) 2026 The CometGUI authors."),
                    source.getKey() + " is missing the D-009 copyright line");
        }
    }
}
