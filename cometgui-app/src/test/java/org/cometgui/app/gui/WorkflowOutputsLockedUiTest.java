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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 07 exit-gate item 5 ({@code AC-PAR-09}, {@code R-CMT-01}): "Disabling a workflow-required
 * output is impossible while the dependent stage is enabled, and the reason is shown."
 *
 * <p>Both outputs -- pepXML, which PDV and the Limelight export read, and the Percolator input --
 * are tried with the mouse and with the keyboard, on Essentials and on Advanced, through both
 * drivers, and by resetting their Advanced category. Each attempt leaves the check box ticked and
 * disabled, the reason on screen in text, and -- proved through the file the editor writes -- the
 * value at 1. Every expected text is typed out.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowOutputsLockedUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    /** Each output: its parameter, then the reason typed out as the specification words it. */
    private static final List<List<String>> OUTPUTS =
            List.of(
                    List.of(
                            "output_pepxmlfile",
                            "Required by CometGUI workflow: PDV, which shows the spectra, and the"
                                    + " Limelight export both read the pepXML file"),
                    List.of(
                            "output_percolatorfile",
                            "Required by CometGUI workflow: Percolator rescoring reads the .pin"
                                    + " file"));

    private static ParameterEditorApp app;

    @TempDir private static Path saveDirectory;

    @BeforeAll
    static void launch() {
        app = ParameterEditorApp.launch(BUILD);
        ParameterEditorApp.openEditor(new TestFxUiDriver(app.application()));
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
    @DisplayName("on Essentials, a click and a key press cannot switch either output off")
    void essentialsOutputsCannotBeSwitchedOff(FxUiDriver driver) {
        driver.clickOn("param-mode-essentials");
        for (List<String> output : OUTPUTS) {
            String id = "ess-" + output.get(0);
            assertLocked(driver, id, output.get(1), "before any attempt");
            driver.clickOn(id);
            assertLocked(driver, id, output.get(1), "after a click on it");
            driver.clickOn(id + "-reset");
            assertLocked(driver, id, output.get(1), "after a click on its reset");
        }

        // The keyboard: Tab from the thread count, the control before the outputs, never lands on
        // either output or its reset, and Space where the focus lands switches nothing off.
        driver.clickOn("ess-num_threads");
        driver.tab();
        assertEquals("ess-num_threads-reset", driver.focusedNodeId());
        driver.tab();
        String landed = driver.focusedNodeId();
        for (List<String> output : OUTPUTS) {
            assertNotEquals("ess-" + output.get(0), landed, "a locked output is no tab stop");
            assertNotEquals("ess-" + output.get(0) + "-reset", landed, "nor is its reset");
        }
        driver.press(KeyCode.SPACE);
        for (List<String> output : OUTPUTS) {
            assertLocked(driver, "ess-" + output.get(0), output.get(1), "after Tab and Space");
        }
    }

    @Test
    @Order(2)
    @DisplayName(
            "on Advanced, neither a click, a key press nor the category's reset switches one off")
    void advancedOutputsCannotBeSwitchedOff() {
        FxUiDriver driver = new TestFxUiDriver(app.application());
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-output-toggle");
        for (List<String> output : OUTPUTS) {
            String id = "adv-" + output.get(0);
            driver.clickOn(id);
            assertLocked(driver, id, output.get(1), "after a click on it");
        }
        driver.clickOn("adv-output_sqtfile");
        assertTrue(
                driver.callOnFxThread(((CheckBox) driver.node("adv-output_sqtfile"))::isSelected),
                "an output nothing requires can be switched on: the lock is not on every box");
        driver.clickOn("adv-category-output-reset");
        driver.clickOn("adv-category-output-reset-confirm");
        assertTrue(
                !driver.callOnFxThread(((CheckBox) driver.node("adv-output_sqtfile"))::isSelected),
                "the category reset put output_sqtfile back to its default");
        for (List<String> output : OUTPUTS) {
            assertLocked(
                    driver, "adv-" + output.get(0), output.get(1), "after the category's reset");
        }
        driver.clickOn("param-mode-essentials");
    }

    @Test
    @Order(3)
    @DisplayName("the configuration still holds both outputs at 1: the saved file says so")
    void theConfigurationHoldsBothOutputsOn() throws IOException {
        FxUiDriver driver = new TestFxUiDriver(app.application());
        Path saved = saveDirectory.resolve("after-the-attempts.params");
        app.chooser().saveTo(saved);
        driver.clickOn("param-save");
        List<String> lines = Files.readAllLines(saved, StandardCharsets.UTF_8);
        assertAll(
                () ->
                        assertTrue(
                                lines.stream()
                                        .anyMatch(
                                                line -> line.startsWith("output_pepxmlfile = 1 ")),
                                "output_pepxmlfile = 1 in " + saved),
                () ->
                        assertTrue(
                                lines.stream()
                                        .anyMatch(
                                                line ->
                                                        line.startsWith(
                                                                "output_percolatorfile = 1 ")),
                                "output_percolatorfile = 1 in " + saved));
    }

    private static void assertLocked(FxUiDriver driver, String id, String reason, String when) {
        CheckBox box = (CheckBox) driver.node(id);
        Button reset = (Button) driver.node(id + "-reset");
        assertAll(
                id + ", " + when,
                () -> assertTrue(driver.callOnFxThread(box::isSelected), "still ticked"),
                () -> assertTrue(driver.callOnFxThread(box::isDisabled), "disabled for change"),
                () -> assertTrue(driver.callOnFxThread(reset::isDisabled), "reset disabled"),
                () -> assertEquals(reason, driver.textOf(id + "-lock"), "the reason, in text"),
                () -> assertTrue(driver.isVisible(id + "-lock"), "the reason is on screen"),
                () ->
                        assertEquals(
                                "Value from: Required by CometGUI workflow",
                                driver.textOf(id + "-origin")),
                () ->
                        assertTrue(
                                driver.callOnFxThread(box::getAccessibleHelp).contains(reason),
                                "a screen reader hears the reason too"));
    }
}
