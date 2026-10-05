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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.ui.controls.AccessibleControls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 07 exit-gate item 4 ({@code AC-PAR-07}, {@code R-PARAM-08}): "A raw Expert edit that fails
 * to parse leaves the typed model unchanged and reports the offending line."
 *
 * <p>The draft is edited with the keyboard: Ctrl+Home, the arrow keys to the line, Shift+End to
 * select it, and the replacement typed. The canonical text's line numbers are fixed by the writer's
 * layout, the same in both releases: line 8 is {@code num_threads}, line 27 {@code variable_mod02}.
 *
 * <ol>
 *   <li>Comet 2026.03.0, both drivers: the configuration is first moved away from the defaults
 *       through Essentials (precursor window and enzyme set by hand, fragment bins by the
 *       instrument preset, an acetylation slot), so that "unchanged" cannot be met by a reset. Line
 *       8 typed without its {@code =}. Apply is refused, the configuration's canonical text, every
 *       configured value and origin, and the typed field are unchanged, and the error with line 8
 *       and the line's text is reported in the apply status, the offending-lines list, the
 *       diagnostics and the line's own words. Then a valid edit, {@code num_threads = 6}: Apply
 *       shows what would change and changes nothing; Confirm changes it, and the typed field shows
 *       6.
 *   <li>The {@code ^} residue, which differs by release (R-PARAM-13): on Comet 2026.02.2 the same
 *       edit of {@code variable_mod02} is refused at line 27 and the slot is unchanged; on Comet
 *       2026.03.0 it reads, is confirmed, and the slot editor shows it.
 * </ol>
 *
 * <p>Every expected text is typed out.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExpertRawEditUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    private static final String MALFORMED_8 =
            "Error, line 8: line 8 is not a comment, a declaration or an enzyme row: not a comment,"
                    + " a blank line or a declaration: there is no '=' before any '#':"
                    + " \"num_threads 0\"";

    private static final String CARET_LINE = "variable_mod02 = 42.010565 ^ 0 1 -1 0 0 0.0";

    private static final String CARET_27 =
            "Error, line 27: line 27: variable_mod02, field 2 (residues): \"^\" holds '^', which"
                    + " Comet 2026.02.2 does not accept in a residue token; its residue alphabet is"
                    + " A-Z, n (N-terminus), c (C-terminus)";

    private static final String NOT_APPLIED =
            "The draft was not applied; the configuration is unchanged.\n";

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
            "a draft that fails to parse changes nothing and names line 8; a good one confirms")
    void aFailedParseChangesNothing(FxUiDriver driver) {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-expert");
        String defaults = driver.textOf("param-expert-canonical");
        configure(driver, true);
        driver.clickOn("param-mode-expert");
        String before = driver.textOf("param-expert-canonical");
        assertNotEquals(defaults, before, "the configuration is not the release's defaults");
        assertEquals(before, driver.textOf("param-expert-draft"), "the draft starts canonical");
        assertConfigured(driver, "before the raw edit", true);
        assertEquals("0", driver.textOf("adv-num_threads"));

        replaceLine(driver, 8, "num_threads 0");
        assertAll(
                "the draft, before applying",
                () -> assertEquals(MALFORMED_8, driver.textOf("param-expert-diagnostic-0")),
                () ->
                        assertEquals(
                                "Line 8, not readable -- " + MALFORMED_8,
                                driver.textOf("param-expert-line-8")),
                () ->
                        assertEquals(
                                "Line 8, not readable -- " + MALFORMED_8,
                                driver.accessibleTextOf("param-expert-line-8"),
                                "the line's state is read out, never colour alone"),
                () ->
                        assertEquals(
                                "Diagnostics: 1 finding in the draft. Activate one to move the"
                                        + " caret to its line.",
                                driver.textOf("param-expert-diagnostics-headline")));

        driver.clickOn("param-expert-apply");
        assertAll(
                "a draft that does not parse",
                () ->
                        assertEquals(
                                NOT_APPLIED + MALFORMED_8,
                                driver.textOf("param-expert-apply-status")),
                () ->
                        assertEquals(
                                "Offending lines:\nLine 8: num_threads 0",
                                driver.textOf("param-expert-offending")),
                () -> assertConfigured(driver, "after the refused raw edit", true),
                () -> assertUnchanged(before, driver.textOf("param-expert-canonical")),
                () -> assertEquals("0", driver.textOf("adv-num_threads"), "typed model unchanged"),
                () -> assertEquals("0", driver.textOf("ess-num_threads")),
                () -> assertFalse(driver.isVisible("param-expert-confirmation")));

        // The diagnostic moves the caret to its line.
        driver.clickOn("param-expert-diagnostic-0");
        TextArea draft = (TextArea) driver.node("param-expert-draft");
        assertEquals("param-expert-draft", driver.focusedNodeId());
        assertEquals(
                "num_threads 0",
                driver.callOnFxThread(
                        () ->
                                draft.getText()
                                        .substring(
                                                draft.getCaretPosition(),
                                                draft.getCaretPosition() + 13)));

        // A valid edit: checked, shown, and applied only on confirmation.
        replaceLine(driver, 8, "num_threads = 6");
        assertEquals(
                "Diagnostics: the draft reads without an error or a warning.",
                driver.textOf("param-expert-diagnostics-headline"));
        driver.clickOn("param-expert-apply");
        assertAll(
                "the confirmation step",
                () -> assertTrue(driver.isVisible("param-expert-confirmation")),
                () ->
                        assertEquals(
                                "Applying changes 1 value:\nSearch threads (num_threads): 0 -> 6",
                                driver.textOf("param-expert-changes")),
                () -> assertEquals("0", driver.textOf("adv-num_threads"), "not before confirming"),
                () -> assertEquals(before, driver.textOf("param-expert-canonical")));
        driver.clickOn("param-expert-confirm");
        assertAll(
                "confirmed",
                () ->
                        assertEquals(
                                "Applied: the configuration now holds the draft.",
                                driver.textOf("param-expert-apply-status")),
                () -> assertEquals("6", driver.textOf("adv-num_threads")),
                () -> assertEquals("6", driver.textOf("ess-num_threads")),
                () ->
                        assertEquals(
                                "Value from: Set by you", driver.textOf("adv-num_threads-origin")),
                () -> assertFalse(driver.isVisible("param-expert-confirmation")),
                () ->
                        assertEquals(
                                driver.textOf("param-expert-canonical"),
                                driver.textOf("param-expert-draft")));
        assertFalse(
                driver.callOnFxThread(
                        () ->
                                AccessibleControls.hasGeneratedName(
                                        (Control) driver.node("param-expert-line-8"))),
                "the line's words are its own name");

        driver.clickOn("param-reset-all");
        driver.clickOn("param-reset-all-confirm");
        assertEquals(defaults, driver.textOf("param-expert-canonical"), "started again");
        driver.clickOn("param-mode-essentials");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(2)
    @DisplayName("a ^ residue is refused at its line on 2026.02.2 and applied on 2026.03.0")
    void theCaretDependsOnTheRelease(FxUiDriver driver) {
        ParameterEditorApp.openEditor(driver);
        ParameterEditorApp.choose(driver, "param-release", "Comet 2026.02.2");
        configure(driver, false);
        driver.clickOn("param-mode-expert");
        String before = driver.textOf("param-expert-canonical");
        assertConfigured(driver, "on Comet 2026.02.2, before the raw edit", false);
        assertTrue(before.startsWith("# comet_version 2026.02 rev. 2 (6edec91)\n"), before);

        replaceLine(driver, 27, CARET_LINE);
        driver.clickOn("param-expert-apply");
        assertAll(
                "Comet 2026.02.2 refuses the ^ at line 27",
                () ->
                        assertEquals(
                                NOT_APPLIED + CARET_27, driver.textOf("param-expert-apply-status")),
                () ->
                        assertEquals(
                                "Offending lines:\nLine 27: " + CARET_LINE,
                                driver.textOf("param-expert-offending")),
                () ->
                        assertConfigured(
                                driver, "on Comet 2026.02.2, after the refused raw edit", false),
                () -> assertUnchanged(before, driver.textOf("param-expert-canonical")),
                () ->
                        assertEquals(
                                "Serialised: variable_mod02 = 0.0 X 0 3 -1 0 0 0.0",
                                driver.textOf("adv-variable_mod02-serialised"),
                                "the typed slot is unchanged"));

        driver.clickOn("param-expert-revert");
        ParameterEditorApp.choose(driver, "param-release", "Comet 2026.03.0 (default)");
        driver.clickOn("param-mode-expert");
        replaceLine(driver, 27, CARET_LINE);
        assertEquals(
                "Diagnostics: the draft reads without an error or a warning.",
                driver.textOf("param-expert-diagnostics-headline"));
        driver.clickOn("param-expert-apply");
        assertEquals(
                "Applying changes 1 value:\nVariable modification 2 (variable_mod02): 0.0 X 0 3 -1"
                        + " 0 0 0.0 -> 42.010565 ^ 0 1 -1 0 0 0.0",
                driver.textOf("param-expert-changes"));
        driver.clickOn("param-expert-confirm");
        assertEquals(
                "Serialised: variable_mod02 = 42.010565 ^ 0 1 -1 0 0 0.0",
                driver.textOf("adv-variable_mod02-serialised"),
                "Comet 2026.03.0 takes the protein N-terminus");

        driver.clickOn("param-reset-all");
        driver.clickOn("param-reset-all-confirm");
        driver.clickOn("param-mode-essentials");
    }

    /**
     * Puts the configuration in a state that is not the release's defaults, through Essentials: the
     * precursor window typed (origin USER), the second enzyme row chosen (USER), the fragment bins
     * set by the instrument choice (origin PRESET) and, on 2026.03.0, protein N-terminal
     * acetylation added to slot 2. A defect that reset the typed model would undo every one.
     */
    private static void configure(FxUiDriver driver, boolean acetylSlot) {
        driver.clickOn("param-mode-essentials");
        ParameterEditorApp.enter(driver, "ess-peptide_mass_tolerance_upper", "10");
        ParameterEditorApp.enter(driver, "ess-peptide_mass_tolerance_lower", "-10");
        ParameterEditorApp.choose(driver, "ess-search_enzyme_number", "2. Trypsin/P");
        ParameterEditorApp.choose(
                driver,
                "ess-fragment-setting",
                "Low-res precursor, low-res fragments / High-res precursor, low-res fragments");
        if (acetylSlot) {
            ParameterEditorApp.choose(
                    driver,
                    "ess-varmod-preset",
                    "Acetyl: +42.010565 on protein N-terminus; max 1 per peptide; optional");
            driver.clickOn("ess-varmod-add");
        }
    }

    /** Every value {@link #configure} set, with its origin, as the editor shows it; typed out. */
    private static void assertConfigured(FxUiDriver driver, String when, boolean acetylSlot) {
        List<String> expected =
                new ArrayList<>(
                        List.of(
                                "upper: 10 / Value from: Set by you",
                                "lower: -10 / Value from: Set by you",
                                "enzyme: 2. Trypsin/P / Value from: Set by you",
                                "bin width: 1.0005 / Value from: Set by a preset",
                                "bin offset: 0.4 / Value from: Set by a preset",
                                "slot 2: Serialised: variable_mod02 = "
                                        + (acetylSlot
                                                ? "42.010565 ^ 0 1 -1 0 0 0.0"
                                                : "0.0 X 0 3 -1 0 0 0.0")));
        List<String> shown =
                List.of(
                        "upper: "
                                + driver.textOf("adv-peptide_mass_tolerance_upper")
                                + " / "
                                + driver.textOf("adv-peptide_mass_tolerance_upper-origin"),
                        "lower: "
                                + driver.textOf("adv-peptide_mass_tolerance_lower")
                                + " / "
                                + driver.textOf("adv-peptide_mass_tolerance_lower-origin"),
                        "enzyme: "
                                + ParameterEditorApp.comboText(driver, "adv-search_enzyme_number")
                                + " / "
                                + driver.textOf("adv-search_enzyme_number-origin"),
                        "bin width: "
                                + driver.textOf("adv-fragment_bin_tol")
                                + " / "
                                + driver.textOf("adv-fragment_bin_tol-origin"),
                        "bin offset: "
                                + driver.textOf("adv-fragment_bin_offset")
                                + " / "
                                + driver.textOf("adv-fragment_bin_offset-origin"),
                        "slot 2: " + driver.textOf("adv-variable_mod02-serialised"));
        assertEquals(expected, shown, "the configured values and origins " + when);
    }

    /**
     * Replaces one line of the draft with the keyboard: to the top, down to the line, select to its
     * end, and type over it.
     *
     * <p>The text area moves the caret up, down, home and end by the lines its skin last laid out.
     * A person's keystrokes are frames apart; the robot's are not, so before each such key the
     * scene is laid out -- found by probing the robot driver, whose second Down otherwise moved
     * from where the click had left the caret rather than from the top.
     */
    private static void replaceLine(FxUiDriver driver, int number, String text) {
        driver.clickOn("param-expert-draft");
        Node area = driver.node("param-expert-draft");
        Runnable settle =
                () ->
                        driver.onFxThread(
                                () -> {
                                    area.getScene().getRoot().applyCss();
                                    area.getScene().getRoot().layout();
                                });
        settle.run();
        driver.pressWith(KeyCode.CONTROL, KeyCode.HOME);
        for (int line = 1; line < number; line++) {
            settle.run();
            driver.press(KeyCode.DOWN);
        }
        settle.run();
        driver.press(KeyCode.HOME);
        settle.run();
        driver.pressWith(KeyCode.SHIFT, KeyCode.END);
        settle.run();
        driver.type(text);
        String draft = driver.textOf("param-expert-draft");
        String canonical = driver.textOf("param-expert-canonical");
        assertEquals(
                text,
                draft.split("\n", -1)[number - 1],
                () ->
                        "line "
                                + number
                                + " of the draft; the lines that differ from the canonical"
                                + " text: "
                                + differing(canonical, draft));
    }

    /**
     * The configuration's canonical text is the one before, compared line by line so that a failure
     * names the lines that changed rather than printing two whole files.
     */
    private static void assertUnchanged(String before, String now) {
        assertEquals(
                List.of(),
                differing(before, now),
                "the configuration's canonical text changed; its lines that differ now read");
        assertEquals(before, now, "the canonical text, byte for byte");
    }

    private static List<String> differing(String canonical, String draft) {
        List<String> lines = new ArrayList<>();
        String[] old = canonical.split("\n", -1);
        String[] now = draft.split("\n", -1);
        for (int index = 0; index < Math.max(old.length, now.length); index++) {
            String was = index < old.length ? old[index] : "(none)";
            String is = index < now.length ? now[index] : "(none)";
            if (!was.equals(is)) {
                lines.add((index + 1) + ": " + is);
            }
        }
        return lines;
    }
}
