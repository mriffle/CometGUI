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

import static org.cometgui.app.gui.ParameterEditorApp.choose;
import static org.cometgui.app.gui.ParameterEditorApp.comboItems;
import static org.cometgui.app.gui.ParameterEditorApp.controlsUnder;
import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.cometgui.app.gui.ParameterEditorApp.exists;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Control;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValueKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Phase 07 exit-gate item 7: "Every parameter control has an accessible name, and validation state
 * is conveyed in text" -- walked for every parameter control of Essentials and of every Advanced
 * category, on both offered releases; and the version-dependent controls on both releases.
 *
 * <h2>What "every" is held to</h2>
 *
 * <p>The Essentials parameters are typed out here (61: the curated 57 and the two enzyme selectors
 * and two limits Essentials shows beside them). The Advanced parameters are every parameter the
 * release models -- read from the model's metadata, which is this test's input and not the code
 * under test -- and their number is typed out (118 on each release), so a walk that found fewer
 * fails. Besides the named controls, every {@code Control} under each level is enumerated and
 * required to have a non-blank accessible name, so a control nobody listed cannot hide.
 *
 * <h2>What "in text" is held to</h2>
 *
 * <p>Every parameter's state label states its state in words -- {@code No problems.}, or lines
 * beginning {@code Error: } or {@code Warning: } -- and its control's accessible help repeats it,
 * so a screen reader hears what the screen shows. With a reversed precursor window, the two fields
 * of the pair are asserted to say {@code Error: } in both.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParameterControlsAccessibilityUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    /** Every parameter the Essentials level shows a control for, typed out. */
    private static final List<String> ESSENTIALS =
            List.of(
                    "database_name",
                    "peptide_mass_tolerance_lower",
                    "peptide_mass_tolerance_upper",
                    "peptide_mass_units",
                    "precursor_tolerance_type",
                    "isotope_error",
                    "fragment_bin_tol",
                    "fragment_bin_offset",
                    "search_enzyme_number",
                    "search_enzyme2_number",
                    "sample_enzyme_number",
                    "num_enzyme_termini",
                    "allowed_missed_cleavage",
                    "add_Cterm_peptide",
                    "add_Nterm_peptide",
                    "add_Cterm_protein",
                    "add_Nterm_protein",
                    "add_G_glycine",
                    "add_A_alanine",
                    "add_S_serine",
                    "add_P_proline",
                    "add_V_valine",
                    "add_T_threonine",
                    "add_C_cysteine",
                    "add_L_leucine",
                    "add_I_isoleucine",
                    "add_N_asparagine",
                    "add_D_aspartic_acid",
                    "add_Q_glutamine",
                    "add_K_lysine",
                    "add_E_glutamic_acid",
                    "add_M_methionine",
                    "add_H_histidine",
                    "add_F_phenylalanine",
                    "add_U_selenocysteine",
                    "add_R_arginine",
                    "add_Y_tyrosine",
                    "add_W_tryptophan",
                    "add_O_pyrrolysine",
                    "variable_mod01",
                    "variable_mod02",
                    "variable_mod03",
                    "variable_mod04",
                    "variable_mod05",
                    "variable_mod06",
                    "variable_mod07",
                    "variable_mod08",
                    "variable_mod09",
                    "variable_mod10",
                    "variable_mod11",
                    "variable_mod12",
                    "variable_mod13",
                    "variable_mod14",
                    "variable_mod15",
                    "max_variable_mods_in_peptide",
                    "require_variable_mod",
                    "decoy_search",
                    "decoy_prefix",
                    "num_threads",
                    "output_pepxmlfile",
                    "output_percolatorfile");

    /** The switch of each of the fourteen Advanced categories, typed out. */
    private static final List<String> CATEGORY_TOGGLES =
            List.of(
                    "adv-category-database_peff-toggle",
                    "adv-category-cpu_execution-toggle",
                    "adv-category-precursor_mass-toggle",
                    "adv-category-digestion_enzymes-toggle",
                    "adv-category-fragment_scoring-toggle",
                    "adv-category-fragment_index-toggle",
                    "adv-category-spectrum_filters-toggle",
                    "adv-category-spectral_processing-toggle",
                    "adv-category-search_ranges-toggle",
                    "adv-category-output-toggle",
                    "adv-category-ms1_realtime-toggle",
                    "adv-category-static_mods-toggle",
                    "adv-category-variable_mods-toggle",
                    "adv-category-misc-toggle");

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    @BeforeAll
    static void launch() {
        app = ParameterEditorApp.launch(BUILD);
        driver = new TestFxUiDriver(app.application());
        ParameterEditorApp.openEditor(driver);
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "Comet 2026.03.0: every parameter control is named and states its validation in text")
    void theDefaultRelease() {
        assertEquals(
                "Comet 2026.03.0 (default)", ParameterEditorApp.comboText(driver, "param-release"));
        walk("2026.03.0");
    }

    @Test
    @Order(2)
    @DisplayName("a field with a finding states it in text, on Essentials and on Advanced")
    void aFindingIsStatedInText() {
        driver.clickOn("param-mode-essentials");
        enter(driver, "ess-peptide_mass_tolerance_lower", "10");
        enter(driver, "ess-peptide_mass_tolerance_upper", "-10");
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-precursor_mass-toggle");
        List<String> failures = new ArrayList<>();
        for (String id :
                List.of(
                        "ess-peptide_mass_tolerance_lower",
                        "ess-peptide_mass_tolerance_upper",
                        "adv-peptide_mass_tolerance_lower",
                        "adv-peptide_mass_tolerance_upper")) {
            String state = driver.textOf(id + "-state");
            if (!state.startsWith(
                    "Error: peptide_mass_tolerance_lower = 10 and peptide_mass_tolerance_upper ="
                            + " -10: the lower bound is above the upper bound")) {
                failures.add(id + " states \"" + state + "\"");
            }
            checkStateInText(id, failures);
        }
        assertEquals(List.of(), failures);
        driver.clickOn("param-mode-essentials");
        enter(driver, "ess-peptide_mass_tolerance_upper", "20.0");
        enter(driver, "ess-peptide_mass_tolerance_lower", "-20.0");
        assertEquals("Validation: No errors or warnings.", driver.textOf("param-summary-headline"));
    }

    @Test
    @Order(3)
    @DisplayName("index_search_type offers -1 on 2026.03.0 only")
    void indexSearchTypeOnTheDefaultRelease() {
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-fragment_index-toggle");
        assertEquals(
                List.of(
                        "Not set: an index built on demand is a fragment-ion index (FI_DB), and"
                                + " Comet never warns",
                        "Peptide index (PI_DB)",
                        "Fragment-ion index (FI_DB)"),
                comboItems(driver, "adv-index_search_type"));
        assertTrue(exists(driver, "ess-variable_mod01-terminus-protein-n"));
    }

    @Test
    @Order(4)
    @DisplayName(
            "Comet 2026.02.2: every parameter control is named and states its validation in text")
    void theOlderRelease() {
        choose(driver, "param-release", "Comet 2026.02.2");
        walk("2026.02.2");
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-fragment_index-toggle");
        assertAll(
                "the version-dependent controls on 2026.02.2",
                () ->
                        assertEquals(
                                List.of("Peptide index (PI_DB)", "Fragment-ion index (FI_DB)"),
                                comboItems(driver, "adv-index_search_type")),
                () ->
                        assertTrue(
                                !exists(driver, "ess-variable_mod01-terminus-protein-n"),
                                "no ^ on 2026.02.2"));
    }

    /** Walks Essentials and every Advanced category of the selected release. */
    private static void walk(String release) {
        List<String> failures = new ArrayList<>();

        driver.clickOn("param-mode-essentials");
        unnamedControls("param-essentials", failures);
        for (String name : ESSENTIALS) {
            checkParameter("ess-" + name, kindOf(name, release), failures);
        }

        driver.clickOn("param-mode-advanced");
        for (String toggle : CATEGORY_TOGGLES) {
            ParameterEditorApp.showCategory(driver, toggle);
        }
        unnamedControls("param-advanced", failures);
        List<ParameterDefinition> every = METADATA.parametersFor(ToolVersion.parse(release));
        assertEquals(118, every.size(), "Comet " + release + " models 118 parameters");
        for (ParameterDefinition parameter : every) {
            checkParameter("adv-" + parameter.name(), parameter.kind(), failures);
        }
        for (String toggle : CATEGORY_TOGGLES) {
            driver.clickOn(toggle);
        }
        driver.clickOn("param-mode-essentials");
        assertEquals(
                List.of(), failures, "Comet " + release + ": " + failures.size() + " failures");
    }

    private static ValueKind kindOf(String name, String release) {
        return METADATA.parameter(name, ToolVersion.parse(release)).orElseThrow().kind();
    }

    /** Every control under a level, each required to carry a non-blank accessible name. */
    private static void unnamedControls(String levelId, List<String> failures) {
        List<Control> controls = controlsUnder(driver, levelId);
        assertTrue(
                controls.size() > 300, "#" + levelId + " holds " + controls.size() + " controls");
        for (Control control : controls) {
            String name = driver.callOnFxThread(control::getAccessibleText);
            if (name == null || name.isBlank()) {
                failures.add(
                        control.getClass().getSimpleName()
                                + " "
                                + driver.callOnFxThread(control::getId)
                                + " under #"
                                + levelId
                                + " has no accessible name");
            }
        }
    }

    private static void checkParameter(String id, ValueKind kind, List<String> failures) {
        if (!exists(driver, id)) {
            failures.add("#" + id + " does not exist");
            return;
        }
        String name = driver.accessibleTextOf(id);
        if (name == null || name.isBlank()) {
            failures.add("#" + id + " has no accessible name");
        }
        if (kind != ValueKind.VARIABLE_MOD_TUPLE && !exists(driver, id + "-label")) {
            failures.add("#" + id + " has no label");
        }
        checkStateInText(id, failures);
    }

    /** The state label says the state in words, and the control's accessible help repeats it. */
    private static void checkStateInText(String id, List<String> failures) {
        if (!exists(driver, id + "-state")) {
            failures.add("#" + id + " has no state label");
            return;
        }
        String state = driver.textOf(id + "-state");
        boolean inWords =
                "No problems.".equals(state)
                        || state.startsWith("Error: ")
                        || state.startsWith("Warning: ");
        if (!inWords) {
            failures.add("#" + id + "-state does not state a validation state: \"" + state + "\"");
        }
        Node control = driver.node(id);
        String help = driver.callOnFxThread(control::getAccessibleHelp);
        if (help == null || !help.contains("Validation: " + state)) {
            failures.add("#" + id + "'s accessible help does not carry its state: " + help);
        }
    }
}
