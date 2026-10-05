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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
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
 * The static-modification table (specification, <em>Typed control requirements</em>: "A
 * residue/terminus-oriented table with modification mass, name where user-supplied, and
 * reset/default state"; Phase 07 unit 10), on Essentials and on Advanced.
 *
 * <p>Every row is read by the identifiers of its cells and held to hand-typed text: the residue or
 * terminus in words, in Comet's own order (termini first); the mass; the name (Comet has none, so
 * the parameter's display name); the default state with where the value came from; the validation
 * state. A mass typed into a row changes exactly that row's declaration in the configuration's
 * canonical text -- so a row writing to another parameter is seen -- and its state says so; a mass
 * the model refuses is stated in text at the row and changes nothing; the row's reset puts the
 * default back. Both drivers; the configuration is started again at the end of each.
 */
class StaticModificationTableUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    /**
     * The Essentials rows, in order: the parameter, the residue or terminus in words, and Comet
     * 2026.03.0's default mass as its {@code comet -q} file writes it (0.0 for a terminus, 0.0000
     * for a residue).
     */
    private static final List<List<String>> ESSENTIALS_ROWS =
            List.of(
                    List.of("add_Cterm_peptide", "peptide C-terminus", "0.0"),
                    List.of("add_Nterm_peptide", "peptide N-terminus", "0.0"),
                    List.of("add_Cterm_protein", "protein C-terminus", "0.0"),
                    List.of("add_Nterm_protein", "protein N-terminus", "0.0"),
                    List.of("add_G_glycine", "glycine (G)", "0.0000"),
                    List.of("add_A_alanine", "alanine (A)", "0.0000"),
                    List.of("add_S_serine", "serine (S)", "0.0000"),
                    List.of("add_P_proline", "proline (P)", "0.0000"),
                    List.of("add_V_valine", "valine (V)", "0.0000"),
                    List.of("add_T_threonine", "threonine (T)", "0.0000"),
                    List.of("add_C_cysteine", "cysteine (C)", "57.021464"),
                    List.of("add_L_leucine", "leucine (L)", "0.0000"),
                    List.of("add_I_isoleucine", "isoleucine (I)", "0.0000"),
                    List.of("add_N_asparagine", "asparagine (N)", "0.0000"),
                    List.of("add_D_aspartic_acid", "aspartic acid (D)", "0.0000"),
                    List.of("add_Q_glutamine", "glutamine (Q)", "0.0000"),
                    List.of("add_K_lysine", "lysine (K)", "0.0000"),
                    List.of("add_E_glutamic_acid", "glutamic acid (E)", "0.0000"),
                    List.of("add_M_methionine", "methionine (M)", "0.0000"),
                    List.of("add_H_histidine", "histidine (H)", "0.0000"),
                    List.of("add_F_phenylalanine", "phenylalanine (F)", "0.0000"),
                    List.of("add_U_selenocysteine", "selenocysteine (U)", "0.0000"),
                    List.of("add_R_arginine", "arginine (R)", "0.0000"),
                    List.of("add_Y_tyrosine", "tyrosine (Y)", "0.0000"),
                    List.of("add_W_tryptophan", "tryptophan (W)", "0.0000"),
                    List.of("add_O_pyrrolysine", "pyrrolysine (O)", "0.0000"));

    /**
     * The four user-definable residues Advanced adds after them, with their display names, which
     * the metadata words differently.
     */
    private static final List<List<String>> ADVANCED_ONLY =
            List.of(
                    List.of(
                            "add_B_user_amino_acid",
                            "user amino acid (B)",
                            "0.0000",
                            "User-defined residue B: mass"),
                    List.of(
                            "add_J_user_amino_acid",
                            "user amino acid (J)",
                            "0.0000",
                            "User-defined residue J: mass"),
                    List.of(
                            "add_X_user_amino_acid",
                            "user amino acid (X)",
                            "0.0000",
                            "User-defined residue X: mass"),
                    List.of(
                            "add_Z_user_amino_acid",
                            "user amino acid (Z)",
                            "0.0000",
                            "User-defined residue Z: mass"));

    private static final String DEFAULT = "Default -- Comet 2026.03.0 default";

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
    @DisplayName("one row per residue and terminus: mass, name, default state, reset, state")
    void theTable(FxUiDriver driver) {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        assertTrue(ParameterEditorApp.exists(driver, "ess-static-mods"), "#ess-static-mods");

        // The rows, in order, with their masses, names and states.
        assertEquals(expectedRows(ESSENTIALS_ROWS), shownRows(driver, "ess", ESSENTIALS_ROWS));
        assertEquals(
                List.of(
                        "cysteine (C)",
                        "57.021464",
                        "Static modification: cysteine (C)",
                        DEFAULT,
                        "No problems."),
                row(driver, "ess", "add_C_cysteine"),
                "carbamidomethyl cysteine is Comet's default");

        String before = canonicalText(driver);
        driver.clickOn("param-mode-essentials");

        // A mass typed into the lysine row: that row's declaration, and nothing else.
        ParameterEditorApp.enter(driver, "ess-add_K_lysine", "229.162932");
        assertEquals(
                List.of(
                        "lysine (K)",
                        "229.162932",
                        "Static modification: lysine (K)",
                        "Changed from default 0.0000 -- Set by you",
                        "No problems."),
                row(driver, "ess", "add_K_lysine"),
                "the lysine row after its mass was typed");
        String edited = canonicalText(driver);
        assertEquals(
                List.of("add_K_lysine = 229.162932"),
                changedDeclarations(before, edited),
                "the lysine row changed add_K_lysine alone");
        driver.clickOn("param-mode-essentials");

        // A mass the model refuses: stated at the row, nothing changed.
        ParameterEditorApp.enter(driver, "ess-add_S_serine", "heavy");
        assertEquals(
                List.of(
                        "serine (S)",
                        "heavy",
                        "Static modification: serine (S)",
                        DEFAULT,
                        "Error: not applied, the configuration still holds \"0.0000\"."
                                + " add_S_serine, value: \"heavy\" is not a number"),
                row(driver, "ess", "add_S_serine"));
        assertEquals(edited, canonicalText(driver), "a refused mass changes nothing");
        driver.clickOn("param-mode-essentials");
        driver.clickOn("ess-add_S_serine-reset");
        assertEquals(
                List.of(
                        "serine (S)",
                        "0.0000",
                        "Static modification: serine (S)",
                        DEFAULT,
                        "No problems."),
                row(driver, "ess", "add_S_serine"),
                "the reset drops the refused text");

        // Advanced: the same configuration, all thirty rows, edited there too.
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-static_mods-toggle");
        List<List<String>> all = new ArrayList<>(ESSENTIALS_ROWS);
        all.addAll(ADVANCED_ONLY);
        List<List<String>> expected = expectedRows(all);
        int lysine = 16;
        expected.set(
                lysine,
                List.of(
                        "17 lysine (K)",
                        "229.162932",
                        "Static modification: lysine (K)",
                        "Changed from default 0.0000 -- Set by you",
                        "No problems."));
        assertEquals(expected, shownRows(driver, "adv", all));
        ParameterEditorApp.enter(driver, "adv-add_C_cysteine", "57.02146");
        driver.clickOn("adv-add_K_lysine-reset");
        assertEquals(
                List.of("add_C_cysteine = 57.02146"),
                changedDeclarations(before, canonicalText(driver)),
                "after the Advanced edit and the lysine reset");
        driver.clickOn("param-mode-essentials");
        assertAll(
                "Essentials shows the Advanced edits",
                () ->
                        assertEquals(
                                List.of(
                                        "cysteine (C)",
                                        "57.02146",
                                        "Static modification: cysteine (C)",
                                        "Changed from default 57.021464 -- Set by you",
                                        "No problems."),
                                row(driver, "ess", "add_C_cysteine")),
                () ->
                        assertEquals(
                                List.of(
                                        "lysine (K)",
                                        "0.0000",
                                        "Static modification: lysine (K)",
                                        DEFAULT,
                                        "No problems."),
                                row(driver, "ess", "add_K_lysine")));

        // Start again for the next driver.
        driver.clickOn("param-mode-advanced");
        driver.clickOn("adv-category-static_mods-toggle");
        driver.clickOn("param-reset-all");
        driver.clickOn("param-reset-all-confirm");
        driver.clickOn("param-mode-essentials");
    }

    /**
     * The rows a table should show at the defaults: the grid line, the words; the default mass; the
     * display name; default; no problems.
     */
    private static List<List<String>> expectedRows(List<List<String>> rows) {
        List<List<String>> expected = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            String words = rows.get(index).get(1);
            expected.add(
                    List.of(
                            (index + 1) + " " + words,
                            rows.get(index).get(2),
                            rows.get(index).size() > 3
                                    ? rows.get(index).get(3)
                                    : "Static modification: " + words,
                            DEFAULT,
                            "No problems."));
        }
        return expected;
    }

    /** Each row as shown, its first cell prefixed with the grid line the row is on. */
    private static List<List<String>> shownRows(
            FxUiDriver driver, String surface, List<List<String>> rows) {
        List<List<String>> shown = new ArrayList<>();
        for (List<String> row : rows) {
            String parameter = row.get(0);
            Node label = driver.node(surface + "-" + parameter + "-label");
            Integer line = driver.callOnFxThread(() -> GridPane.getRowIndex(label));
            List<String> cells = new ArrayList<>(row(driver, surface, parameter));
            cells.set(0, line + " " + cells.get(0));
            shown.add(cells);
        }
        return shown;
    }

    /** One row's cells: residue or terminus, mass, name, default state, validation state. */
    private static List<String> row(FxUiDriver driver, String surface, String parameter) {
        String id = surface + "-" + parameter;
        return List.of(
                driver.textOf(id + "-label"),
                driver.textOf(id),
                driver.textOf(id + "-name"),
                driver.textOf(id + "-origin"),
                driver.textOf(id + "-state"));
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
