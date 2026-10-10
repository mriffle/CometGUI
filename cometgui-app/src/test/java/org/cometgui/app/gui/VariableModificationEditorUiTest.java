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
import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.cometgui.app.gui.ParameterEditorApp.exists;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 07 exit-gate item 2 ({@code AC-PAR-05}): "A GUI test adds, edits, reorders and removes a
 * variable modification and asserts the serialised tuple after each step" -- and the residue and
 * terminus offer of the slot editor on both releases, since {@code ^} and {@code $} are Comet
 * 2026.03.0's alone.
 *
 * <p>Every expected tuple is typed out here; none is produced by the model or the editor. The walk
 * runs through both drivers -- TestFX and the TestFX-free robot -- and ends where it began, with
 * oxidised methionine in slot 1 and slot 2 unused, so the second driver starts from the same
 * configuration as the first.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VariableModificationEditorUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    private static final String SLOT_1 = "ess-variable_mod01-serialised";

    private static final String SLOT_2 = "ess-variable_mod02-serialised";

    private static ParameterEditorApp app;

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
    @DisplayName("add, edit, reorder and remove, with the serialised tuple after each step")
    void addEditReorderRemove(FxUiDriver driver) {
        assertAll(
                "before",
                () ->
                        assertEquals(
                                "Serialised: variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0",
                                driver.textOf(SLOT_1)),
                () ->
                        assertEquals(
                                "Serialised: variable_mod02 = 0.0 X 0 3 -1 0 0 0.0",
                                driver.textOf(SLOT_2)));

        // Add: a common modification goes into the first free slot.
        choose(
                driver,
                "ess-varmod-preset",
                "Phospho: +79.966331 on STY; max 3 per peptide; optional; neutral loss"
                        + " 97.976896");
        driver.clickOn("ess-varmod-add");
        assertAll(
                "after adding phosphorylation",
                () ->
                        assertEquals(
                                "Serialised: variable_mod02 = 79.966331 STY 0 3 -1 0 0 97.976896",
                                driver.textOf(SLOT_2)),
                () ->
                        assertEquals(
                                "Variable modification 2: Phospho: +79.966331 on STY; max 3 per"
                                        + " peptide; optional; neutral loss 97.976896",
                                driver.textOf("ess-variable_mod02")),
                () ->
                        assertEquals(
                                "Added Phospho in variable_mod02",
                                driver.textOf("ess-varmod-status")));

        // Edit: the maximum count, a residue, and the mass.
        enter(driver, "ess-variable_mod02-part-maximum-count", "2");
        assertEquals(
                "Serialised: variable_mod02 = 79.966331 STY 0 2 -1 0 0 97.976896",
                driver.textOf(SLOT_2),
                "after the maximum count is typed");
        driver.clickOn("ess-variable_mod02-residue-T");
        assertEquals(
                "Serialised: variable_mod02 = 79.966331 SY 0 2 -1 0 0 97.976896",
                driver.textOf(SLOT_2),
                "after threonine is cleared");
        enter(driver, "ess-variable_mod02-part-mass", "79.96633");
        assertAll(
                "after the mass is typed",
                () ->
                        assertEquals(
                                "Serialised: variable_mod02 = 79.96633 SY 0 2 -1 0 0 97.976896",
                                driver.textOf(SLOT_2)),
                () ->
                        assertEquals(
                                "Serialised: variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0",
                                driver.textOf(SLOT_1),
                                "slot 1 untouched"));

        // Reorder: the edited modification moves up into slot 1, oxidation down into slot 2.
        driver.clickOn("ess-variable_mod02-up");
        assertAll(
                "after moving slot 2 up",
                () ->
                        assertEquals(
                                "Serialised: variable_mod01 = 79.96633 SY 0 2 -1 0 0 97.976896",
                                driver.textOf(SLOT_1)),
                () ->
                        assertEquals(
                                "Serialised: variable_mod02 = 15.9949 M 0 3 -1 0 0 0.0",
                                driver.textOf(SLOT_2)));

        // Remove: slot 1 goes back to the release's unused value.
        driver.clickOn("ess-variable_mod01-remove");
        assertAll(
                "after removing slot 1",
                () ->
                        assertEquals(
                                "Serialised: variable_mod01 = 0.0 X 0 3 -1 0 0 0.0",
                                driver.textOf(SLOT_1)),
                () ->
                        assertEquals(
                                "Serialised: variable_mod02 = 15.9949 M 0 3 -1 0 0 0.0",
                                driver.textOf(SLOT_2)));

        // Back to where the walk began, for the next driver.
        driver.clickOn("ess-variable_mod02-up");
        assertAll(
                "after moving oxidation back to slot 1",
                () ->
                        assertEquals(
                                "Serialised: variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0",
                                driver.textOf(SLOT_1)),
                () ->
                        assertEquals(
                                "Serialised: variable_mod02 = 0.0 X 0 3 -1 0 0 0.0",
                                driver.textOf(SLOT_2)));
    }

    @Test
    @Order(2)
    @DisplayName("2026.03.0 offers ^ and $ and the protein N-terminal acetyl preset written with ^")
    void theDefaultReleaseOffersProteinTermini() {
        FxUiDriver driver = new TestFxUiDriver(app.application());
        assertEquals(
                "Comet 2026.03.0 (default)", ParameterEditorApp.comboText(driver, "param-release"));
        assertAll(
                "Comet 2026.03.0",
                () -> assertTrue(exists(driver, "ess-variable_mod01-terminus-peptide-n")),
                () -> assertTrue(exists(driver, "ess-variable_mod01-terminus-peptide-c")),
                () -> assertTrue(exists(driver, "ess-variable_mod01-terminus-protein-n")),
                () -> assertTrue(exists(driver, "ess-variable_mod01-terminus-protein-c")),
                () -> assertTrue(exists(driver, "ess-variable_mod01-residue-M")),
                () ->
                        assertTrue(
                                comboItems(driver, "ess-varmod-preset")
                                        .contains(
                                                "Acetyl: +42.010565 on protein N-terminus; max 1"
                                                        + " per peptide; optional")));

        // ^ selected on slot 1, beside M, through its own check box; then cleared.
        driver.clickOn("ess-variable_mod01-terminus-protein-n");
        assertEquals(
                "Serialised: variable_mod01 = 15.9949 ^M 0 3 -1 0 0 0.0", driver.textOf(SLOT_1));
        driver.clickOn("ess-variable_mod01-terminus-protein-n");
        assertEquals(
                "Serialised: variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0", driver.textOf(SLOT_1));
    }

    @Test
    @Order(3)
    @DisplayName("2026.02.2 offers n and c only, and no preset it cannot write")
    void theOlderReleaseOffersPeptideTerminiOnly() {
        FxUiDriver driver = new TestFxUiDriver(app.application());
        choose(driver, "param-release", "Comet 2026.02.2");
        assertAll(
                "Comet 2026.02.2",
                () ->
                        assertEquals(
                                "Comet 2026.02.2 is selected. Migrated: Comet 2026.03.0 ->"
                                        + " 2026.02.2: 118 parameters, 1 changed, 117 unchanged; 0"
                                        + " need your decision.",
                                driver.textOf("param-release-status")),
                () -> assertTrue(exists(driver, "ess-variable_mod01-terminus-peptide-n")),
                () -> assertTrue(exists(driver, "ess-variable_mod01-terminus-peptide-c")),
                () -> assertFalse(exists(driver, "ess-variable_mod01-terminus-protein-n")),
                () -> assertFalse(exists(driver, "ess-variable_mod01-terminus-protein-c")),
                () ->
                        assertEquals(
                                List.of(
                                        "Oxidation: +15.994915 on M; max 3 per peptide; optional",
                                        "Phospho: +79.966331 on STY; max 3 per peptide;"
                                                + " optional; neutral loss 97.976896",
                                        "Acetyl: +42.010565 on N-terminus, only at the protein"
                                                + " N-terminus; max 1 per peptide; optional",
                                        "Deamidation: +0.984016 on NQ; max 3 per peptide; optional",
                                        "Gln->pyro-Glu: -17.026549 on Q, only at the peptide"
                                                + " N-terminus; max 1 per peptide; optional"),
                                comboItems(driver, "ess-varmod-preset")),
                () ->
                        assertEquals(
                                "Serialised: variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0",
                                driver.textOf(SLOT_1)));

        // The 2026.02.2 form of protein N-terminal acetylation, added through the editor.
        choose(
                driver,
                "ess-varmod-preset",
                "Acetyl: +42.010565 on N-terminus, only at the protein N-terminus; max 1 per"
                        + " peptide; optional");
        driver.clickOn("ess-varmod-add");
        assertEquals(
                "Serialised: variable_mod02 = 42.010565 n 0 1 0 0 0 0.0", driver.textOf(SLOT_2));
    }
}
