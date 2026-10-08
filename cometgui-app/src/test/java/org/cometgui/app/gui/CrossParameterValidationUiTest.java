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

import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 07 exit-gate item 6 ({@code AC-PAR-10}): "An invalid cross-parameter configuration blocks
 * Run, attaches the error to the field, appears in the summary and is reachable by keyboard."
 *
 * <p>Two configurations, each invalid only in combination: a precursor window whose lower bound is
 * above its upper bound ({@code R-PARAM-04}), on Essentials; and a variable modification required
 * while no slot holds one ({@code R-PARAM-10}), from Advanced. For each: the Run control is
 * disabled with the reason in text, the error is stated at the field, the summary lists it, and
 * from the summary entry the keyboard alone -- Tab to reach it, Enter to activate it -- moves the
 * focus to the field. Fixing it re-enables the parameters' half of the Run control; the engine's
 * half stays, because this application's Tool Manager has no Comet installed. (With the engine
 * ready, {@code RunReadinessUiTest} proves the parameters' half alone disables Run.) Both drivers;
 * every text typed out.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrossParameterValidationUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    /**
     * The engine's half in this application: its Tool Manager has no Comet installed (the test's
     * application data directory holds none), so the workflow engine cannot run anything -- and the
     * parameters' half is what this class proves. Phase 08 replaced Phase 07's "the engine is not
     * built" with this, the engine's own reason; Phase 09 added the Percolator section's, since a
     * run now includes Percolator and no Percolator is installed there either (the resolved
     * default, the manifest's newest Linux build, is installable but not installed).
     */
    private static final String ENGINE =
            "The workflow engine cannot start this search:\nComet 2026.03.0 is not installed, and"
                    + " the parameters are for that release: install it in the Tool Manager"
                    + " section, or register a Comet 2026.03.0 already on this computer there.\n"
                    + "Percolator 3.07.1, the default Percolator for the enabled downstream stages,"
                    + " is not installed: install it in the Tool Manager section, register a local"
                    + " Percolator binary in the Percolator section, or choose an installed build"
                    + " there.";

    private static final String READY = "The parameters do not block a run.";

    private static ParameterEditorApp app;

    @BeforeAll
    static void launch() {
        app = ParameterEditorApp.launch(BUILD);
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    static Stream<FxUiDriver> drivers() {
        return Stream.of(
                new TestFxUiDriver(app.application()), new RobotFxUiDriver(app.application()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(1)
    @DisplayName(
            "a reversed precursor window blocks Run, at the field, in the summary, by keyboard")
    void aReversedPrecursorWindow(FxUiDriver driver) {
        assertRunReady(driver);
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        enter(driver, "ess-peptide_mass_tolerance_lower", "10");
        enter(driver, "ess-peptide_mass_tolerance_upper", "-10");

        String error =
                "peptide_mass_tolerance_lower = 10 and peptide_mass_tolerance_upper = -10: the"
                        + " lower bound is above the upper bound, so no precursor can match and"
                        + " Comet refuses to search; the lower bound is normally negative, for"
                        + " example -20.0 and 20.0";
        Node lower = driver.node("ess-peptide_mass_tolerance_lower");
        String entry =
                "Error -- Precursor tolerance, lower bound (peptide_mass_tolerance_lower),"
                        + " Precursor mass and isotope handling: "
                        + error;
        assertAll(
                "the reversed window, on the editor",
                () ->
                        assertEquals(
                                "Error: " + error,
                                driver.textOf("ess-peptide_mass_tolerance_lower-state"),
                                "the error is attached to the field, in text"),
                () ->
                        assertEquals(
                                "Error: " + error,
                                driver.textOf("ess-peptide_mass_tolerance_upper-state"),
                                "and to the other field of the pair"),
                () ->
                        assertTrue(
                                driver.callOnFxThread(lower::getAccessibleHelp)
                                        .contains("Validation: Error: " + error),
                                "and in what a screen reader hears for the field"),
                () ->
                        assertEquals(
                                "Validation: 1 error and 0 warnings.",
                                driver.textOf("param-summary-headline")),
                () -> assertEquals(entry, driver.textOf("param-summary-entry-0")));

        reachTheSummaryEntryByKeyboard(driver, "param-summary-entry-0");
        driver.press(KeyCode.ENTER);
        assertEquals(
                "ess-peptide_mass_tolerance_lower",
                driver.focusedNodeId(),
                "Enter on the summary entry moves the focus to the field");

        driver.clickOn("nav-run");
        assertAll(
                "the Run section",
                () -> assertTrue(isDisabled(driver, "run-start"), "Run is disabled"),
                () -> assertTrue(driver.textOf("run-parameters").contains(entry)),
                () ->
                        assertTrue(
                                driver.textOf("run-parameters")
                                        .startsWith("The parameters block a run:\n")),
                () -> assertEquals(ENGINE, RunSection.awaitEngineAnswer(driver)));

        // Fixed: upper first (10 to 10 is a window, not a reversed one), then lower.
        ParameterEditorApp.openEditor(driver);
        enter(driver, "ess-peptide_mass_tolerance_upper", "10");
        enter(driver, "ess-peptide_mass_tolerance_lower", "-10");
        assertEquals("Validation: No errors or warnings.", driver.textOf("param-summary-headline"));
        assertRunReady(driver);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(2)
    @DisplayName("a required variable modification with no slot in use, from Advanced")
    void aRequiredModificationWithNoSlot(FxUiDriver driver) {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-variable_mods-toggle");
        driver.clickOn("adv-require_variable_mod");
        driver.clickOn("adv-variable_mod01-remove");

        String error =
                "require_variable_mod = 1 requires every peptide to carry a variable modification,"
                        + " but no variable_mod slot is active (every mass difference is 0), so"
                        + " nothing can be identified; configure a modification or set it to 0";
        assertAll(
                "required with no slot",
                () ->
                        assertEquals(
                                "Error: " + error, driver.textOf("adv-require_variable_mod-state")),
                () ->
                        assertEquals(
                                "Error -- Require a variable modification (require_variable_mod),"
                                        + " Variable modifications: "
                                        + error,
                                driver.textOf("param-summary-entry-0")),
                () -> assertEquals("Error: " + error, driver.textOf("adv-varmod-cross")));

        // Hide the category, so that following the summary has to show it again.
        driver.clickOn("adv-category-variable_mods-toggle");
        assertFalse(driver.isVisible("adv-require_variable_mod"));
        reachTheSummaryEntryByKeyboard(driver, "param-summary-entry-0");
        driver.press(KeyCode.ENTER);
        assertAll(
                "after Enter on the summary entry",
                () -> assertEquals("adv-require_variable_mod", driver.focusedNodeId()),
                () -> assertTrue(driver.isVisible("adv-require_variable_mod"), "shown again"));

        driver.clickOn("nav-run");
        assertTrue(isDisabled(driver, "run-start"), "Run is disabled");
        assertTrue(driver.textOf("run-parameters").contains(error));

        // Fixed: the requirement switched off again, and oxidation put back for the next run.
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("adv-require_variable_mod");
        ParameterEditorApp.choose(
                driver,
                "adv-varmod-preset",
                "Oxidation: +15.994915 on M; max 3 per peptide; optional");
        driver.clickOn("adv-varmod-add");
        assertEquals(
                "Serialised: variable_mod01 = 15.994915 M 0 3 -1 0 0 0.0",
                driver.textOf("adv-variable_mod01-serialised"));
        assertRunReady(driver);
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
    }

    /**
     * Moves the focus to a summary entry with Tab alone, from the editor's first control, and fails
     * if Tab never gets there.
     */
    private static void reachTheSummaryEntryByKeyboard(FxUiDriver driver, String entryId) {
        driver.clickOn("nav-comet-parameters");
        List<String> visited = new ArrayList<>();
        for (int press = 0; press < 40 && !entryId.equals(driver.focusedNodeId()); press++) {
            driver.tab();
            visited.add(driver.focusedNodeId());
        }
        assertEquals(
                entryId,
                driver.focusedNodeId(),
                () -> "Tab never reached #" + entryId + "; it visited " + visited);
    }

    private static void assertRunReady(FxUiDriver driver) {
        driver.clickOn("nav-run");
        assertAll(
                "the Run control with nothing wrong in the parameters",
                () -> assertEquals(READY, driver.textOf("run-parameters")),
                () -> assertEquals(ENGINE, RunSection.awaitEngineAnswer(driver)),
                () -> assertTrue(isDisabled(driver, "run-start"), "still no engine"));
    }

    private static boolean isDisabled(FxUiDriver driver, String id) {
        Node node = driver.node(id);
        return driver.callOnFxThread(node::isDisabled);
    }
}
