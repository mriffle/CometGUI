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

package org.cometgui.ui.viewmodel.params;

import static org.cometgui.ui.viewmodel.params.Sessions.C02;
import static org.cometgui.ui.viewmodel.params.Sessions.C03;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.schema.VisibilityLevel;
import org.cometgui.params.comet.validation.Finding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A field shows its parameter as the SELECTED release has it (decision P7-2): every
 * version-dependent fact here is asserted on both releases, with hand-typed values.
 */
class FieldViewModelTest {

    @Test
    @DisplayName("index_search_type: 2026.03.0 offers -1 and defaults to it; 2026.02.2 does not")
    void indexSearchTypeChoicesAndDefault() {
        FieldViewModel newer = startingIn(C03).field("index_search_type");
        FieldViewModel older = startingIn(C02).field("index_search_type");

        assertEquals(
                List.of(
                        new ChoiceOption(
                                "-1",
                                "Not set: an index built on demand is a fragment-ion index (FI_DB),"
                                        + " and Comet never warns"),
                        new ChoiceOption("0", "Peptide index (PI_DB)"),
                        new ChoiceOption("1", "Fragment-ion index (FI_DB)")),
                newer.choices());
        assertEquals("-1", newer.defaultText());
        assertEquals("-1", newer.text());

        assertEquals(
                List.of(
                        new ChoiceOption("0", "Peptide index (PI_DB)"),
                        new ChoiceOption("1", "Fragment-ion index (FI_DB)")),
                older.choices());
        assertEquals("1", older.defaultText());
        assertEquals("1", older.text());
        assertEquals(
                "https://uwpr.github.io/Comet/parameters/parameters_202603/index_search_type.html",
                newer.helpUrl());
        assertEquals(
                "https://uwpr.github.io/Comet/parameters/parameters_202602/index_search_type.html",
                older.helpUrl());
        assertEquals(C03, newer.release());
        assertEquals(C02, older.release());
    }

    @Test
    @DisplayName("variable_mod01's help names ^ and $ for 2026.03.0 only")
    void helpOverride() {
        FieldViewModel newer = startingIn(C03).field("variable_mod01");
        FieldViewModel older = startingIn(C02).field("variable_mod01");
        assertEquals(
                "One variable modification as eight fields: mass difference, residues (letters"
                        + " A-Z; n and c for any peptide N- or C-terminus; ^ and $, new in Comet"
                        + " 2026.03.0, for the protein N- or C-terminus only), binary group,"
                        + " maximum or min,max count per peptide, distance from a terminus (-1"
                        + " none, -2 not the peptide C-terminal residue, or 0 and above; Comet"
                        + " 2026.03.0 refuses any other value), which terminus (0 protein N, 1"
                        + " protein C, 2 peptide N, 3 peptide C), required/exclusive, and zero, one"
                        + " or two fragment neutral losses. A mass of 0.0 leaves the slot unused.",
                newer.shortHelp());
        assertEquals(
                "One variable modification as eight fields: mass difference, residues (n and c for"
                        + " termini), binary group, maximum or min,max count per peptide, distance"
                        + " from a terminus, which terminus, required/exclusive, and zero, one or"
                        + " two fragment neutral losses. A mass of 0.0 leaves the slot unused.",
                older.shortHelp());
        assertEquals(
                "https://uwpr.github.io/Comet/parameters/parameters_202603/variable_modXX.html",
                newer.helpUrl());
        assertEquals(
                "https://uwpr.github.io/Comet/parameters/parameters_202602/variable_modXX.html",
                older.helpUrl());
    }

    @Test
    @DisplayName("output_txtfile's inline comment names each release")
    void inlineCommentOverride() {
        assertEquals(
                Optional.of("0=no, 1=yes  write tab-delimited txt file (2026.03.0 treats 2 as 1)"),
                startingIn(C03).field("output_txtfile").inlineComment());
        assertEquals(
                Optional.of("0=no, 1=yes  write tab-delimited txt file (2026.02.2 treats 2 as 1)"),
                startingIn(C02).field("output_txtfile").inlineComment());
        assertEquals(Optional.empty(), startingIn(C03).field("variable_mod01").inlineComment());
    }

    @Test
    @DisplayName("the curated facts of an Essentials enum: name, label, help, kind, choices")
    void curatedFacts() {
        FieldViewModel units = startingIn(C03).field("peptide_mass_units");
        assertEquals("peptide_mass_units", units.name());
        assertEquals("Precursor tolerance units", units.displayName());
        assertEquals("Units of the two precursor tolerance bounds.", units.shortHelp());
        assertEquals(ParameterCategory.PRECURSOR_MASS, units.category());
        assertEquals(VisibilityLevel.ESSENTIALS, units.visibility());
        assertFalse(units.isExpert());
        assertEquals(ValueKind.INTEGER_ENUM, units.kind());
        assertEquals(Optional.of("0=amu, 1=mmu, 2=ppm"), units.inlineComment());
        assertEquals(
                List.of(
                        new ChoiceOption("0", "amu (daltons)"),
                        new ChoiceOption("1", "mmu (millidaltons)"),
                        new ChoiceOption("2", "ppm")),
                units.choices());
        assertEquals("ppm [2]", units.choices().get(2).withToken());
        assertEquals("peptide_mass_units", units.definition().name());
        assertEquals("2", units.text());
        assertEquals("2", units.textProperty().get());
        assertEquals("Comet 2026.03.0 default", units.originText());
        assertEquals(ValueOrigin.COMET_DEFAULT, units.originProperty().get());
        assertTrue(startingIn(C03).field("index_search_type").isExpert());
        assertEquals(List.of(), startingIn(C03).field("num_threads").choices());
    }

    @ParameterizedTest(name = "Comet {0}: the default origin names the release")
    @CsvSource({"2026.03.0, Comet 2026.03.0 default", "2026.02.2, Comet 2026.02.2 default"})
    void defaultOriginNamesTheRelease(String release, String words) {
        assertEquals(
                words, startingIn(ToolVersion.parse(release)).field("num_threads").originText());
    }

    @ParameterizedTest(name = "Comet {0}: a terminal distance below -2 is {1}")
    @CsvSource({"2026.03.0, ERROR, Error, true", "2026.02.2, WARNING, Warning, false"})
    void distanceBelowMinusTwo(String release, FieldState state, String prefix, boolean blocks) {
        ParameterSession session = startingIn(ToolVersion.parse(release));
        RunReadinessViewModel readiness = new RunReadinessViewModel(session);
        readiness.showEngineReasons(List.of());
        assertTrue(readiness.runEnabled());

        // CONSTRUCTED value: distance -3
        assertEquals(
                EditOutcome.applied(), session.edit("variable_mod01", "15.9949 M 0 3 -3 0 0 0.0"));

        FieldViewModel slot = session.field("variable_mod01");
        assertEquals(1, slot.findings().size());
        Finding finding = slot.findings().get(0);
        assertEquals("variable_mod_tuple.distance_undocumented", finding.rule().id());
        assertEquals(state, slot.state());
        assertEquals(state, slot.stateProperty().get());
        assertEquals(
                prefix
                        + ": variable_mod01 = 15.9949 M 0 3 -3 0 0 0.0: terminal distance -3 is not"
                        + " a documented value, and Comet treats it as -1 (no constraint); use -2,"
                        + " -1 or 0 and above",
                slot.stateText());
        assertEquals(slot.stateText(), slot.stateTextProperty().get());
        assertEquals(List.of(finding), slot.findingsProperty().get());
        assertEquals(blocks, readiness.parametersBlockRun());
        assertEquals(!blocks, readiness.runEnabled());
        assertEquals(blocks ? 1 : 0, readiness.blockingReasons().size());
    }

    @Test
    @DisplayName("an error and a warning on one field are both stated, error first in report order")
    void severalFindings() {
        ParameterSession session = startingIn(C03);
        // CONSTRUCTED window: lower above upper (error), stated on both members
        session.edit("peptide_mass_tolerance_lower", "30.0");
        FieldViewModel lower = session.field("peptide_mass_tolerance_lower");
        FieldViewModel upper = session.field("peptide_mass_tolerance_upper");
        assertEquals(FieldState.ERROR, lower.state());
        assertEquals(FieldState.ERROR, upper.state());
        assertEquals(lower.findings(), upper.findings());
        assertEquals("signed_tolerance_pair.reversed", lower.findings().get(0).rule().id());
        assertTrue(lower.stateText().startsWith("Error: "), lower.stateText());

        session.edit("peptide_mass_tolerance_lower", "5.0");
        assertEquals(FieldState.WARNING, lower.state());
        assertEquals("signed_tolerance_pair.same_signed", lower.findings().get(0).rule().id());
    }

    @Test
    @DisplayName("every origin is stated in words")
    void originWords() {
        ParameterSession session = startingIn(C03);
        session.adopt(
                session.model()
                        .withOrigin("num_threads", ValueOrigin.IMPORTED)
                        .withOrigin("allowed_missed_cleavage", ValueOrigin.PRESET)
                        .withOrigin("decoy_prefix", ValueOrigin.USER),
                Adoption.IMPORTED);
        assertEquals("Imported from a parameter file", session.field("num_threads").originText());
        assertEquals("Set by a preset", session.field("allowed_missed_cleavage").originText());
        assertEquals("Set by you", session.field("decoy_prefix").originText());
        assertEquals(
                "Required by CometGUI workflow", session.field("output_pepxmlfile").originText());
        assertEquals("Comet 2026.03.0 default", session.field("decoy_search").originText());
    }

    @Test
    @DisplayName("a refusal is published on its property and cleared by a reset")
    void refusalProperty() {
        ParameterSession session = startingIn(C03);
        FieldViewModel threads = session.field("num_threads");
        threads.setText("all of them");
        assertTrue(threads.refusalProperty().get().isPresent());
        assertEquals("all of them", threads.text());
        assertEquals(EditOutcome.applied(), threads.reset());
        assertEquals(Optional.empty(), threads.refusal());
        assertEquals("0", threads.text());
        assertFalse(threads.lockedProperty().get());
        assertEquals(Optional.empty(), threads.lockReasonProperty().get());
    }

    @Test
    @DisplayName("an on/off parameter is read from and set to the model's flag, origin USER")
    void flags() {
        ParameterSession session = startingIn(C03);
        FieldViewModel nl = session.field("use_NL_ions");
        FieldViewModel b = session.field("use_B_ions");
        assertFalse(nl.isOn());
        assertTrue(b.isOn());
        assertEquals(EditOutcome.applied(), nl.setOn(true));
        assertTrue(nl.isOn());
        assertEquals("1", session.model().text("use_NL_ions"));
        assertEquals(ValueOrigin.USER, session.model().origin("use_NL_ions"));
        assertEquals(EditOutcome.applied(), b.setOn(false));
        assertEquals("0", session.model().text("use_B_ions"));
    }

    @Test
    @DisplayName("a locked flag is refused with its reason and stays on")
    void lockedFlag() {
        ParameterSession session = startingIn(C03);
        FieldViewModel pin = session.field("output_percolatorfile");
        EditOutcome refused = pin.setOn(false);
        assertEquals(
                Optional.of(
                        "Write Percolator input (PIN) cannot be changed while the stage that needs"
                                + " it is enabled. Required by CometGUI workflow: Percolator"
                                + " rescoring reads the .pin file."),
                refused.refusal());
        assertTrue(pin.isOn());
    }

    @Test
    @DisplayName("isOn and setOn refuse a parameter that is not on/off, naming its kind")
    void notAFlag() {
        FieldViewModel threads = startingIn(C03).field("num_threads");
        IllegalStateException read = assertThrows(IllegalStateException.class, threads::isOn);
        assertEquals("num_threads is a INTEGER parameter, not an on/off one", read.getMessage());
        assertThrows(IllegalStateException.class, () -> threads.setOn(true));
        assertEquals("0", threads.text());
    }
}
