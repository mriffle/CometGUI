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
import javafx.scene.control.ToggleButton;
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
 * Phase 07 exit-gate item 8: "Parameter search finds a parameter by name, by display name, by help
 * text and by alias."
 *
 * <p>Four queries, each of which matches its parameters through exactly one attribute (the queries
 * unit 5 proved on the view-model, {@code ParameterSearchViewModelTest.ByAttribute}): {@code
 * allowed_missed} by name, {@code Enzymatic termini} by display name, {@code placeholder} by help
 * text and {@code semi-tryptic} by alias. For each, the results the launched application lists and
 * the reason each gives are typed out in full. Activating a result -- Tab to it and Enter, or a
 * click -- shows the Advanced level with the parameter's category open and moves the focus to the
 * field. The Modified-only filter is shown narrowing an empty query to the one value the workflow
 * changes. Both drivers; every identifier and text typed out.
 *
 * <p>Search by help text searches the SELECTED release's help (decision P7-2), and two further
 * methods prove it through the launched application, one per release: {@code not set} is in
 * 2026.03.0's help of {@code index_search_type} only, and {@code is ignored} in 2026.02.2's help of
 * {@code spectral_library_ms_level} only, so each query finds its parameter on the release whose
 * help says it and nothing on the other (the queries unit 5 proved on the view-model, {@code
 * ParameterSearchViewModelTest.releaseHelp}). A search that read one release's help for every
 * release turns exactly one of the two red; each method stays on its own release, so the other
 * stays green.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParameterSearchUiTest {

    /** What the release selector shows for each offered release. */
    private static final String DEFAULT_RELEASE = "Comet 2026.03.0 (default)";

    private static final String OLDER_RELEASE = "Comet 2026.02.2";

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

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
    @DisplayName("finds by name, display name, help text and alias, and opens the field")
    void findsByEachAttribute(FxUiDriver driver) {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        assertEquals(
                "Type to find a parameter, or tick a filter.",
                driver.textOf("param-search-headline"));

        // By name: only the parameter's name contains "allowed_missed".
        search(driver, "allowed_missed");
        assertResults(
                driver,
                "by name",
                List.of("Allowed missed cleavages (allowed_missed_cleavage) -- Matched by name"));
        // Keyboard: Tab from the query to the result, Enter on it.
        reachByTab(driver, "param-search-result-0");
        driver.press(KeyCode.ENTER);
        assertOpened(
                driver, "adv-allowed_missed_cleavage", "adv-category-digestion_enzymes-toggle");

        // By display name: "Enzymatic termini" is num_enzyme_termini's display name only.
        search(driver, "Enzymatic termini");
        assertResults(
                driver,
                "by display name",
                List.of("Enzymatic termini (num_enzyme_termini) -- Matched by display name"));

        // By help text: "placeholder" is in two parameters' help and nowhere else.
        search(driver, "placeholder");
        assertResults(
                driver,
                "by help text",
                List.of(
                        "Sequence database (FASTA) (database_name) -- Matched by help text",
                        "Spectral library file (spectral_library_name) -- Matched by help text"));
        driver.clickOn("param-search-result-1");
        assertOpened(driver, "adv-spectral_library_name", "adv-category-ms1_realtime-toggle");

        // By alias: "semi-tryptic" is one of num_enzyme_termini's curated aliases.
        search(driver, "semi-tryptic");
        assertResults(
                driver,
                "by alias",
                List.of(
                        "Enzymatic termini (num_enzyme_termini) -- Matched by alias"
                                + " \"semi-tryptic\""));
        driver.clickOn("param-search-result-0");
        assertOpened(driver, "adv-num_enzyme_termini", "adv-category-digestion_enzymes-toggle");

        // A filter on its own: the one value the workflow changes from Comet's default.
        search(driver, "");
        driver.clickOn("param-search-filter-modified");
        assertResults(
                driver,
                "Modified only, no query",
                List.of(
                        "Write Percolator input (PIN) (output_percolatorfile) -- Listed by the"
                                + " filters"));
        driver.clickOn("param-search-filter-modified");
        assertEquals(
                "Type to find a parameter, or tick a filter.",
                driver.textOf("param-search-headline"));
        driver.clickOn("param-mode-essentials");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(2)
    @DisplayName("on 2026.03.0, help text is 2026.03.0's: \"not set\" finds index_search_type")
    void helpTextOfTheDefaultRelease(FxUiDriver driver) {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        assertEquals(DEFAULT_RELEASE, ParameterEditorApp.comboText(driver, "param-release"));

        // 2026.03.0's help of index_search_type: "-1, the default, means not set and builds a
        // fragment-ion index". 2026.02.2's help of it has no "not set".
        search(driver, "not set");
        assertResults(
                driver,
                "Comet 2026.03.0, help text \"not set\"",
                List.of(
                        "Index type for an index built on demand (index_search_type) -- Matched"
                                + " by help text"));
        // 2026.02.2's help of spectral_library_ms_level says a value "is ignored by that
        // release"; 2026.03.0's help of it does not, and nothing else's does.
        search(driver, "is ignored");
        assertNothingFound(driver, "Comet 2026.03.0, help text \"is ignored\"");
        search(driver, "");
        driver.clickOn("param-mode-essentials");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @Order(3)
    @DisplayName(
            "on 2026.02.2, help text is 2026.02.2's: \"is ignored\" finds"
                    + " spectral_library_ms_level")
    void helpTextOfTheOlderRelease(FxUiDriver driver) {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        ParameterEditorApp.choose(driver, "param-release", OLDER_RELEASE);
        assertEquals(
                "Comet 2026.02.2 is selected. Migrated: Comet 2026.03.0 -> 2026.02.2: 118"
                        + " parameters, 1 changed, 117 unchanged; 0 need your decision.",
                driver.textOf("param-release-status"));

        search(driver, "is ignored");
        assertResults(
                driver,
                "Comet 2026.02.2, help text \"is ignored\"",
                List.of(
                        "Spectral library MS level (spectral_library_ms_level) -- Matched by help"
                                + " text"));
        search(driver, "not set");
        assertNothingFound(driver, "Comet 2026.02.2, help text \"not set\"");

        // Back to the default release and its own starting set, for whatever runs next.
        search(driver, "");
        ParameterEditorApp.choose(driver, "param-release", DEFAULT_RELEASE);
        driver.clickOn("param-reset-all");
        driver.clickOn("param-reset-all-confirm");
        driver.clickOn("param-search-filter-modified");
        assertResults(
                driver,
                "Modified only, back on 2026.03.0 after starting again",
                List.of(
                        "Write Percolator input (PIN) (output_percolatorfile) -- Listed by the"
                                + " filters"));
        driver.clickOn("param-search-filter-modified");
        driver.clickOn("param-mode-essentials");
    }

    /** A query that lists no parameter, said in words. */
    private static void assertNothingFound(FxUiDriver driver, String what) {
        assertAll(
                what,
                () -> assertFalse(ParameterEditorApp.exists(driver, "param-search-result-0")),
                () -> assertEquals("0 parameters found.", driver.textOf("param-search-headline")));
    }

    /** Replaces the query with typed text (or clears it), as a user does. */
    private static void search(FxUiDriver driver, String query) {
        driver.typeInto("param-search", query);
        if (query.isEmpty()) {
            driver.press(KeyCode.BACK_SPACE);
        }
        assertEquals(query, driver.textOf("param-search"));
    }

    /** The results listed, exactly, and no more. */
    private static void assertResults(FxUiDriver driver, String what, List<String> expected) {
        List<String> shown = new ArrayList<>();
        for (int index = 0; ParameterEditorApp.exists(driver, "param-search-result-" + index); ) {
            shown.add(driver.textOf("param-search-result-" + index));
            index++;
        }
        String count =
                expected.size()
                        + (expected.size() == 1 ? " parameter found." : " parameters found.");
        assertAll(
                what,
                () -> assertEquals(expected, shown),
                () -> assertEquals(count, driver.textOf("param-search-headline")),
                () ->
                        assertEquals(
                                expected.get(0),
                                driver.accessibleTextOf("param-search-result-0"),
                                "the reason is in what a screen reader hears, too"));
    }

    private static void reachByTab(FxUiDriver driver, String id) {
        List<String> visited = new ArrayList<>();
        for (int press = 0; press < 15 && !id.equals(driver.focusedNodeId()); press++) {
            driver.tab();
            visited.add(driver.focusedNodeId());
        }
        assertEquals(id, driver.focusedNodeId(), () -> "Tab never reached #" + id + ": " + visited);
    }

    private static void assertOpened(FxUiDriver driver, String field, String categoryToggle) {
        ToggleButton toggle = (ToggleButton) driver.node(categoryToggle);
        assertAll(
                "after activating the result for #" + field,
                () -> assertEquals(field, driver.focusedNodeId(), "the field has the focus"),
                () -> assertTrue(driver.isVisible(field), "and is shown"),
                () -> assertTrue(driver.isVisible("param-advanced"), "on the Advanced level"),
                () -> assertFalse(driver.isVisible("param-essentials")),
                () -> assertTrue(driver.callOnFxThread(toggle::isSelected), "category open"));
    }
}
