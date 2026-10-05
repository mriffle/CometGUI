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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javafx.scene.control.CheckBox;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 07 exit-gate item 3 ({@code AC-PAR-08}): "Applying a preset shows a diff, applying a subset
 * applies exactly that subset, and cancelling changes nothing."
 *
 * <p>The low-low preset (Comet's low-resolution precursor and fragment example) previewed against
 * Comet 2026.03.0's own defaults from Essentials. The preview's eight rows -- parameter, current
 * value, preset value -- are typed out below from Comet's two {@code comet -q} files and the preset
 * file, not read from the application. Cancelling leaves the configuration's canonical text
 * identical and every origin as it was. Then two rows of the eight are applied: the canonical text
 * differs from before in exactly those two declarations, the six unticked parameters still hold
 * Comet's defaults with Comet's origin, and the two applied ones say they were set by a preset.
 * Both drivers; the configuration is started again from the defaults at the end of each.
 */
class PresetPreviewUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    private static final String LOW_LOW = "Low-res precursor, low-res fragments";

    /** Each row: the check box's text, the current value, the preset's value. */
    private static final List<List<String>> ROWS =
            List.of(
                    List.of(
                            "Precursor tolerance, upper bound (peptide_mass_tolerance_upper)",
                            "20.0",
                            "3.0"),
                    List.of(
                            "Precursor tolerance, lower bound (peptide_mass_tolerance_lower)",
                            "-20.0",
                            "-3.0"),
                    List.of("Precursor tolerance units (peptide_mass_units)", "2", "0"),
                    List.of("Precursor tolerance applies to (precursor_tolerance_type)", "1", "0"),
                    List.of("Precursor isotope offsets (isotope_error)", "2", "0"),
                    List.of("Fragment bin width (fragment_bin_tol)", "0.02", "1.0005"),
                    List.of("Fragment bin offset (fragment_bin_offset)", "0.0", "0.4"),
                    List.of("Flanking-bin scoring (theoretical_fragment_ions)", "0", "1"));

    /** The parameters the preview names, by row. */
    private static final List<String> NAMES =
            List.of(
                    "peptide_mass_tolerance_upper",
                    "peptide_mass_tolerance_lower",
                    "peptide_mass_units",
                    "precursor_tolerance_type",
                    "isotope_error",
                    "fragment_bin_tol",
                    "fragment_bin_offset",
                    "theoretical_fragment_ions");

    private static final String DEFAULT_ORIGIN = "Value from: Comet 2026.03.0 default";

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
    @DisplayName("a preset shows its diff; cancel changes nothing; a subset applies exactly that")
    void previewCancelAndApplyASubset(FxUiDriver driver) {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        String before = canonicalText(driver);
        assertOrigins(driver, "before anything", Map.of());
        driver.clickOn("param-mode-essentials");

        // Preview: the diff, and nothing changed.
        ParameterEditorApp.choose(driver, "ess-preset-choice", LOW_LOW);
        driver.clickOn("ess-preset-preview");
        assertAll(
                "the preview",
                () -> assertTrue(driver.isVisible("ess-preset-review")),
                () -> assertEquals(ROWS, shownRows(driver)),
                () ->
                        assertEquals(
                                LOW_LOW
                                        + ": Made for Comet 2026.02.2; this configuration is for"
                                        + " Comet 2026.03.0.",
                                driver.textOf("ess-preset-made-for")),
                () ->
                        assertEquals(
                                "Compatibility: no problems; every change can be applied to this"
                                        + " release.",
                                driver.textOf("ess-preset-problems")),
                () ->
                        assertEquals(
                                "Previewing " + LOW_LOW + ": 8 changes. Nothing has changed yet.",
                                driver.textOf("ess-preset-status")));
        assertEquals(before, canonicalText(driver), "previewing changes nothing");
        driver.clickOn("param-mode-essentials");

        // Cancel: nothing changes, origins included.
        driver.clickOn("ess-preset-cancel");
        assertFalse(driver.isVisible("ess-preset-review"));
        assertEquals("Cancelled: nothing was changed.", driver.textOf("ess-preset-status"));
        assertEquals(before, canonicalText(driver), "cancelling changes nothing");
        assertOrigins(driver, "after cancelling", Map.of());

        // Apply a subset: the upper bound and the fragment bin width only.
        driver.clickOn("param-mode-essentials");
        driver.clickOn("ess-preset-preview");
        for (int row : List.of(1, 2, 3, 4, 6, 7)) {
            driver.clickOn("ess-preset-row-" + row);
        }
        List<Boolean> ticked = new ArrayList<>();
        for (int row = 0; row < ROWS.size(); row++) {
            CheckBox box = (CheckBox) driver.node("ess-preset-row-" + row);
            ticked.add(driver.callOnFxThread(box::isSelected));
        }
        assertEquals(List.of(true, false, false, false, false, true, false, false), ticked);
        driver.clickOn("ess-preset-apply-selected");
        assertEquals(
                "Applied 2 changes of "
                        + LOW_LOW
                        + ": Precursor tolerance, upper bound (peptide_mass_tolerance_upper) 20.0"
                        + " -> 3.0; Fragment bin width (fragment_bin_tol) 0.02 -> 1.0005.",
                driver.textOf("ess-preset-status"));
        assertFalse(driver.isVisible("ess-preset-review"));
        assertEquals(
                List.of("fragment_bin_tol = 1.0005", "peptide_mass_tolerance_upper = 3.0"),
                changedDeclarations(before, canonicalText(driver)),
                "exactly the two ticked parameters changed in the configuration");
        assertOrigins(
                driver,
                "after applying two rows",
                Map.of(
                        "peptide_mass_tolerance_upper", "Value from: Set by a preset",
                        "fragment_bin_tol", "Value from: Set by a preset"));
        assertAll(
                "the applied values at their Essentials fields",
                () -> assertEquals("3.0", driver.textOf("ess-peptide_mass_tolerance_upper")),
                () -> assertEquals("-20.0", driver.textOf("ess-peptide_mass_tolerance_lower")),
                () -> assertEquals("1.0005", driver.textOf("ess-fragment_bin_tol")),
                () -> assertEquals("0.0", driver.textOf("ess-fragment_bin_offset")));

        // Start again for the next driver.
        driver.clickOn("param-reset-all");
        driver.clickOn("param-reset-all-confirm");
        assertEquals(before, canonicalText(driver));
        driver.clickOn("param-mode-essentials");
    }

    /** The preview's rows as shown: each check box's text, the current and the preset value. */
    private static List<List<String>> shownRows(FxUiDriver driver) {
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; ParameterEditorApp.exists(driver, "ess-preset-row-" + row); row++) {
            rows.add(
                    List.of(
                            driver.textOf("ess-preset-row-" + row),
                            driver.textOf("ess-preset-row-" + row + "-current"),
                            driver.textOf("ess-preset-row-" + row + "-preset")));
        }
        return rows;
    }

    /** The configuration's canonical text, as the Expert level shows it. */
    private static String canonicalText(FxUiDriver driver) {
        driver.clickOn("param-mode-expert");
        return driver.textOf("param-expert-canonical");
    }

    /**
     * Every declaration whose line differs between two canonical texts, without its inline comment
     * and padding, sorted. The comparison is the test's; the expected list is typed out.
     */
    private static List<String> changedDeclarations(String before, String after) {
        List<String> old = before.lines().toList();
        List<String> now = after.lines().toList();
        assertEquals(old.size(), now.size(), "the same lines");
        List<String> changed = new ArrayList<>();
        for (int index = 0; index < old.size(); index++) {
            if (!old.get(index).equals(now.get(index))) {
                String line = now.get(index);
                int comment = line.indexOf('#');
                changed.add((comment < 0 ? line : line.substring(0, comment)).strip());
            }
        }
        changed.sort(null);
        return changed;
    }

    /** Every previewed parameter's origin on Advanced: Comet's default unless named otherwise. */
    private static void assertOrigins(FxUiDriver driver, String when, Map<String, String> set) {
        List<String> expected = new ArrayList<>();
        List<String> shown = new ArrayList<>();
        for (String name : NAMES) {
            expected.add(name + ": " + set.getOrDefault(name, DEFAULT_ORIGIN));
            shown.add(name + ": " + driver.textOf("adv-" + name + "-origin"));
        }
        assertEquals(expected, shown, "origins " + when);
    }
}
