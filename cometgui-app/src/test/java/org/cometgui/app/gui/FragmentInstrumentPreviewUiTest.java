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
import java.util.stream.Stream;
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
 * {@code AC-PAR-08} for the Essentials fragment instrument choice (exit-gate item 3, Phase 07 unit
 * 10): "Preset application shows a diff before changing anything." The choice applies the fragment
 * rows of a built-in preset, so choosing it must show those rows and change nothing until they are
 * applied.
 *
 * <p>The configuration is first moved away from Comet 2026.03.0's defaults by hand -- the fragment
 * bin offset (a row of the preview, so its current value is 0.1) and the upper precursor bound and
 * the thread count (rows of none) -- so that "nothing changed" cannot be met by a reset. The three
 * rows -- parameter, current value, preset value -- are typed out from Comet's {@code comet -q}
 * file and the preset file. Choosing changes nothing; Cancel changes nothing, origins included;
 * Apply selected changes exactly the two ticked declarations; Apply all then the remaining one.
 * Both drivers; the configuration is started again from the defaults at the end of each.
 */
class FragmentInstrumentPreviewUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    private static final String LOW_RES =
            "Low-res precursor, low-res fragments / High-res precursor, low-res fragments";

    private static final String TITLE = LOW_RES + " (fragment ions only)";

    /** Each row: the check box's text, the current value, the preset's value. */
    private static final List<List<String>> ROWS =
            List.of(
                    List.of("Fragment bin width (fragment_bin_tol)", "0.02", "1.0005"),
                    List.of("Fragment bin offset (fragment_bin_offset)", "0.1", "0.4"),
                    List.of("Flanking-bin scoring (theoretical_fragment_ions)", "0", "1"));

    /** The values set by hand before the preview, with their origins on Advanced. */
    private static final List<String> BY_HAND =
            List.of(
                    "fragment_bin_tol: 0.02 / Value from: Comet 2026.03.0 default",
                    "fragment_bin_offset: 0.1 / Value from: Set by you",
                    "theoretical_fragment_ions: Value from: Comet 2026.03.0 default",
                    "peptide_mass_tolerance_upper: 10 / Value from: Set by you",
                    "num_threads: 4 / Value from: Set by you");

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
    @DisplayName(
            "the instrument choice shows its diff; cancel changes nothing; apply sets the rows")
    void instrumentChoiceIsPreviewed(FxUiDriver driver) {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        assertEquals(
                "High-res precursor, high-res fragments",
                ParameterEditorApp.comboText(driver, "ess-fragment-setting"),
                "the defaults are the high-res setting");
        ParameterEditorApp.enter(driver, "ess-fragment_bin_offset", "0.1");
        ParameterEditorApp.enter(driver, "ess-peptide_mass_tolerance_upper", "10");
        ParameterEditorApp.enter(driver, "ess-num_threads", "4");
        assertEquals("", ParameterEditorApp.comboText(driver, "ess-fragment-setting"));
        String before = canonicalText(driver);
        assertEquals(BY_HAND, configured(driver), "the values set by hand");
        driver.clickOn("param-mode-essentials");
        assertFalse(driver.isVisible("ess-fragment-review"), "no preview before a choice");

        // Choose: nothing changed, and the diff.
        ParameterEditorApp.choose(driver, "ess-fragment-setting", LOW_RES);
        assertNothingChanged(before, canonicalText(driver), "choosing changes nothing");
        assertEquals(BY_HAND, configured(driver), "the values after choosing");
        driver.clickOn("param-mode-essentials");
        assertAll(
                "the preview",
                () -> assertTrue(driver.isVisible("ess-fragment-review")),
                () -> assertEquals(ROWS, shownRows(driver)),
                () ->
                        assertEquals(
                                TITLE
                                        + ": Made for Comet 2026.02.2; this configuration is for"
                                        + " Comet 2026.03.0.",
                                driver.textOf("ess-fragment-made-for")),
                () ->
                        assertEquals(
                                "Compatibility: no problems; every change can be applied to this"
                                        + " release.",
                                driver.textOf("ess-fragment-problems")),
                () ->
                        assertEquals(
                                "Previewing " + TITLE + ": 3 changes. Nothing has changed yet.",
                                driver.textOf("ess-fragment-status")),
                () -> assertEquals("0.1", driver.textOf("ess-fragment_bin_offset")),
                () -> assertEquals("0.02", driver.textOf("ess-fragment_bin_tol")),
                () ->
                        assertEquals(
                                "Fragment ions: Not one of the built-in instrument settings",
                                driver.textOf("ess-fragment-setting-words")));

        // Cancel: nothing changes, origins included, and the choice says what is configured.
        driver.clickOn("ess-fragment-cancel");
        assertFalse(driver.isVisible("ess-fragment-review"));
        assertEquals("Cancelled: nothing was changed.", driver.textOf("ess-fragment-status"));
        assertEquals("", ParameterEditorApp.comboText(driver, "ess-fragment-setting"));
        assertNothingChanged(before, canonicalText(driver), "cancelling changes nothing");
        assertEquals(BY_HAND, configured(driver), "the values after cancelling");
        driver.clickOn("param-mode-essentials");

        // Apply selected: the bin width and the flanking bins, not the offset.
        ParameterEditorApp.choose(driver, "ess-fragment-setting", LOW_RES);
        driver.clickOn("ess-fragment-row-1");
        driver.clickOn("ess-fragment-apply-selected");
        assertEquals(
                "Applied 2 changes of "
                        + TITLE
                        + ": Fragment bin width (fragment_bin_tol) 0.02 -> 1.0005; Flanking-bin"
                        + " scoring (theoretical_fragment_ions) 0 -> 1.",
                driver.textOf("ess-fragment-status"));
        assertFalse(driver.isVisible("ess-fragment-review"));
        String subset = canonicalText(driver);
        assertEquals(
                List.of("fragment_bin_tol = 1.0005", "theoretical_fragment_ions = 1"),
                changedDeclarations(before, subset),
                "exactly the two ticked rows changed");
        assertEquals(
                List.of(
                        "fragment_bin_tol: 1.0005 / Value from: Set by a preset",
                        "fragment_bin_offset: 0.1 / Value from: Set by you",
                        "theoretical_fragment_ions: Value from: Set by a preset",
                        "peptide_mass_tolerance_upper: 10 / Value from: Set by you",
                        "num_threads: 4 / Value from: Set by you"),
                configured(driver),
                "the values after applying two rows");
        driver.clickOn("param-mode-essentials");

        // Apply all: the one row left.
        ParameterEditorApp.choose(driver, "ess-fragment-setting", LOW_RES);
        assertEquals(
                List.of(List.of("Fragment bin offset (fragment_bin_offset)", "0.1", "0.4")),
                shownRows(driver));
        driver.clickOn("ess-fragment-apply-all");
        assertEquals(
                "Applied 1 change of "
                        + TITLE
                        + ": Fragment bin offset (fragment_bin_offset) 0.1 -> 0.4.",
                driver.textOf("ess-fragment-status"));
        assertEquals(
                List.of("fragment_bin_offset = 0.4"),
                changedDeclarations(subset, canonicalText(driver)),
                "exactly the one row changed");
        driver.clickOn("param-mode-essentials");
        assertAll(
                "the choice now names the configuration's setting",
                () ->
                        assertEquals(
                                LOW_RES,
                                ParameterEditorApp.comboText(driver, "ess-fragment-setting")),
                () ->
                        assertEquals(
                                "Fragment ions: As in: " + LOW_RES,
                                driver.textOf("ess-fragment-setting-words")),
                () -> assertEquals("0.4", driver.textOf("ess-fragment_bin_offset")));

        // Start again for the next driver.
        driver.clickOn("param-reset-all");
        driver.clickOn("param-reset-all-confirm");
        driver.clickOn("param-mode-essentials");
    }

    /** The values the test sets by hand, with their origins, as Advanced shows them. */
    private static List<String> configured(FxUiDriver driver) {
        driver.clickOn("param-mode-advanced");
        return List.of(
                "fragment_bin_tol: "
                        + driver.textOf("adv-fragment_bin_tol")
                        + " / "
                        + driver.textOf("adv-fragment_bin_tol-origin"),
                "fragment_bin_offset: "
                        + driver.textOf("adv-fragment_bin_offset")
                        + " / "
                        + driver.textOf("adv-fragment_bin_offset-origin"),
                "theoretical_fragment_ions: "
                        + driver.textOf("adv-theoretical_fragment_ions-origin"),
                "peptide_mass_tolerance_upper: "
                        + driver.textOf("adv-peptide_mass_tolerance_upper")
                        + " / "
                        + driver.textOf("adv-peptide_mass_tolerance_upper-origin"),
                "num_threads: "
                        + driver.textOf("adv-num_threads")
                        + " / "
                        + driver.textOf("adv-num_threads-origin"));
    }

    /** The canonical text is the one before; a failure names the declarations that changed. */
    private static void assertNothingChanged(String before, String now, String what) {
        assertEquals(List.of(), changedDeclarations(before, now), what + "; changed:");
        assertEquals(before, now, what + ", byte for byte");
    }

    /** The preview's rows as shown: each check box's text, the current and the preset value. */
    private static List<List<String>> shownRows(FxUiDriver driver) {
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; ParameterEditorApp.exists(driver, "ess-fragment-row-" + row); row++) {
            rows.add(
                    List.of(
                            driver.textOf("ess-fragment-row-" + row),
                            driver.textOf("ess-fragment-row-" + row + "-current"),
                            driver.textOf("ess-fragment-row-" + row + "-preset")));
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
}
