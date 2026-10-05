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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.migration.MigrationEntry;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.presets.ModificationPreset;
import org.cometgui.params.comet.schema.TerminalCode;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.Severity;
import org.cometgui.params.comet.value.VariableModChoice;
import org.cometgui.params.comet.value.VariableModPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The variable-modification editor at view-model level (gate item 2, AC-PAR-05, R-PARAM-09/-10).
 * Every expected tuple is the canonical text typed by hand; release-dependent facts run on both
 * offered releases.
 */
class VariableModsViewModelTest {

    private static final String UNUSED = "0.0 X 0 3 -1 0 0 0.0";

    private static final String DEFAULT_OXIDATION = "15.9949 M 0 3 -1 0 0 0.0";

    static List<String> releases() {
        return Sessions.releases();
    }

    private static ModificationPreset preset(VariableModsViewModel editor, String id) {
        return editor.presets().stream()
                .filter(preset -> preset.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> serialised(VariableModsViewModel editor, String... slots) {
        List<String> texts = new ArrayList<>();
        for (String slot : slots) {
            texts.add(editor.slot(slot).serialised());
        }
        return texts;
    }

    @ParameterizedTest(name = "Comet {0}")
    @MethodSource("releases")
    @DisplayName("gate item 2: add, edit, reorder and remove, the serialised tuple after each step")
    void addEditReorderRemove(String release) {
        ParameterSession session = startingIn(ToolVersion.parse(release));
        VariableModsViewModel editor = new VariableModsViewModel(session);
        assertEquals(
                List.of(DEFAULT_OXIDATION, UNUSED, UNUSED),
                serialised(editor, "variable_mod01", "variable_mod02", "variable_mod03"));

        // add: the first unused slot is variable_mod02
        assertEquals(EditOutcome.applied(), editor.add(preset(editor, "phospho-sty")));
        assertEquals(
                List.of(DEFAULT_OXIDATION, "79.966331 STY 0 3 -1 0 0 0.0", UNUSED),
                serialised(editor, "variable_mod01", "variable_mod02", "variable_mod03"));
        assertEquals("79.966331 STY 0 3 -1 0 0 0.0", session.model().text("variable_mod02"));
        assertEquals(ValueOrigin.USER, session.model().origin("variable_mod02"));

        // edit: a minimum count, a neutral loss, and Y cleared from the residue multi-select
        assertEquals(
                EditOutcome.applied(),
                editor.setPart("variable_mod02", VariableModPart.MINIMUM_COUNT, "1"));
        assertEquals("79.966331 STY 0 1,3 -1 0 0 0.0", editor.slot("variable_mod02").serialised());
        assertEquals(
                EditOutcome.applied(),
                editor.setPart("variable_mod02", VariableModPart.NEUTRAL_LOSS, "97.976896"));
        assertEquals(
                "79.966331 STY 0 1,3 -1 0 0 97.976896", editor.slot("variable_mod02").serialised());
        assertEquals(EditOutcome.applied(), editor.setResidue("variable_mod02", 'Y', false));
        assertEquals(
                "79.966331 ST 0 1,3 -1 0 0 97.976896", editor.slot("variable_mod02").serialised());
        assertEquals(
                "+79.966331 on ST; 1 to 3 per peptide; optional; neutral loss 97.976896",
                editor.slot("variable_mod02").summary());

        // reorder: up into slot 1, then assigned to slot 5
        assertEquals(EditOutcome.applied(), editor.moveUp("variable_mod02"));
        assertEquals(
                List.of("79.966331 ST 0 1,3 -1 0 0 97.976896", DEFAULT_OXIDATION, UNUSED),
                serialised(editor, "variable_mod01", "variable_mod02", "variable_mod03"));
        assertEquals(ValueOrigin.USER, session.model().origin("variable_mod01"));
        assertEquals(ValueOrigin.USER, session.model().origin("variable_mod02"));
        assertEquals(EditOutcome.applied(), editor.moveTo("variable_mod01", "variable_mod05"));
        assertEquals(
                List.of(UNUSED, DEFAULT_OXIDATION, "79.966331 ST 0 1,3 -1 0 0 97.976896"),
                serialised(editor, "variable_mod01", "variable_mod02", "variable_mod05"));
        assertEquals(EditOutcome.applied(), editor.moveDown("variable_mod02"));
        assertEquals(
                List.of(UNUSED, UNUSED, DEFAULT_OXIDATION),
                serialised(editor, "variable_mod01", "variable_mod02", "variable_mod03"));

        // remove: slot 5 back to the release's unused value
        assertEquals(EditOutcome.applied(), editor.remove("variable_mod05"));
        assertEquals(
                List.of(UNUSED, UNUSED, DEFAULT_OXIDATION, UNUSED, UNUSED),
                serialised(
                        editor,
                        "variable_mod01",
                        "variable_mod02",
                        "variable_mod03",
                        "variable_mod04",
                        "variable_mod05"));
        assertEquals(ValueOrigin.USER, session.model().origin("variable_mod05"));
        assertEquals(List.of(), session.report().findings());
    }

    @Nested
    @DisplayName("the release decides the slots, parts, residues and presets")
    class PerRelease {

        @ParameterizedTest(name = "Comet {0}")
        @MethodSource("org.cometgui.ui.viewmodel.params.VariableModsViewModelTest#releases")
        @DisplayName(
                "fifteen slots, ten parts each, every letter; the first slot Comet's oxidation")
        void slotsAndParts(String release) {
            VariableModsViewModel editor =
                    new VariableModsViewModel(startingIn(ToolVersion.parse(release)));
            assertEquals(15, editor.slots().size());
            assertEquals("variable_mod15", editor.slots().get(14).name());
            assertEquals(15, editor.slots().get(14).number());
            assertEquals(
                    List.of(
                            VariableModPart.MASS,
                            VariableModPart.RESIDUES,
                            VariableModPart.BINARY_GROUP,
                            VariableModPart.MINIMUM_COUNT,
                            VariableModPart.MAXIMUM_COUNT,
                            VariableModPart.TERMINAL_DISTANCE,
                            VariableModPart.TERMINUS,
                            VariableModPart.REQUIRED,
                            VariableModPart.NEUTRAL_LOSS,
                            VariableModPart.SECOND_NEUTRAL_LOSS),
                    editor.parts());
            assertEquals(26, editor.residueLetters().size());
            assertEquals(Character.valueOf('A'), editor.residueLetters().get(0));
            assertEquals(Character.valueOf('Z'), editor.residueLetters().get(25));
            VariableModSlotView first = editor.slot("variable_mod01");
            assertTrue(first.active());
            assertEquals("+15.9949 on M; max 3 per peptide; optional", first.summary());
            assertEquals(
                    "Variable modification 1: +15.9949 on M; max 3 per peptide; optional",
                    first.heading());
            assertTrue(first.selects('M'));
            assertFalse(first.selects('S'));
            assertEquals(Optional.empty(), first.preset());
            assertFalse(first.needsAttention());
            List<String> texts = new ArrayList<>();
            for (VariableModPartView part : first.parts()) {
                texts.add(part.text());
            }
            assertEquals(List.of("15.9949", "M", "0", "", "3", "-1", "0", "0", "0.0", ""), texts);
            VariableModPartView required = first.part(VariableModPart.REQUIRED).orElseThrow();
            assertEquals("required, optional or exclusive", required.label());
            assertEquals(
                    List.of("optional", "required", "exclusive"),
                    required.choices().stream().map(VariableModChoice::words).toList());
            assertTrue(required.explanation().startsWith("0 optional, 1 required"));
            VariableModSlotView second = editor.slot("variable_mod02");
            assertFalse(second.active());
            assertEquals("unused (mass difference 0.0)", second.summary());
            assertSame(editor.slots(), editor.slotsProperty().get());
        }

        @Test
        @DisplayName("^ and $ are offered for 2026.03.0, and not for 2026.02.2")
        void terminusCodes() {
            assertEquals(
                    List.of(
                            TerminalCode.PEPTIDE_N,
                            TerminalCode.PEPTIDE_C,
                            TerminalCode.PROTEIN_N,
                            TerminalCode.PROTEIN_C),
                    new VariableModsViewModel(startingIn(C03)).terminusCodes());
            assertEquals(
                    List.of(TerminalCode.PEPTIDE_N, TerminalCode.PEPTIDE_C),
                    new VariableModsViewModel(startingIn(C02)).terminusCodes());
        }

        @Test
        @DisplayName("2026.03.0 sets ^ in a slot; 2026.02.2 refuses it at the field and blocks Run")
        void proteinTerminusInASlot() {
            ParameterSession c03 = startingIn(C03);
            VariableModsViewModel newer = new VariableModsViewModel(c03);
            assertEquals(EditOutcome.applied(), newer.setResidue("variable_mod01", '^', true));
            assertEquals("15.9949 ^M 0 3 -1 0 0 0.0", newer.slot("variable_mod01").serialised());

            ParameterSession c02 = startingIn(C02);
            VariableModsViewModel older = new VariableModsViewModel(c02);
            EditOutcome refused = older.setResidue("variable_mod01", '^', true);
            String message =
                    "variable_mod01, residues: '^' is not offered: Comet 2026.02.2 does not accept"
                            + " it in a residue token; its residue alphabet is A-Z, n"
                            + " (N-terminus), c (C-terminus)";
            assertEquals(EditOutcome.refused(message), refused);
            assertEquals(DEFAULT_OXIDATION, older.slot("variable_mod01").serialised());
            assertEquals(Optional.of(message), older.field("variable_mod01").refusal());
            assertEquals(
                    List.of("variable_mod01"),
                    c02.pendingRefusals().stream().map(FieldViewModel::name).toList());
            assertEquals(EditOutcome.applied(), older.setResidue("variable_mod01", 'K', true));
            assertEquals(Optional.empty(), older.field("variable_mod01").refusal());
            assertEquals(List.of(), c02.pendingRefusals());
        }

        @Test
        @DisplayName("2026.03.0 offers six presets; 2026.02.2 five, not the one written with ^")
        void presetsPerRelease() {
            assertEquals(
                    List.of(
                            "oxidation-m",
                            "phospho-sty",
                            "acetyl-protein-n-term",
                            "acetyl-protein-n-term-caret",
                            "deamidation-nq",
                            "gln-pyro-glu"),
                    new VariableModsViewModel(startingIn(C03))
                            .presets().stream().map(ModificationPreset::id).toList());
            assertEquals(
                    List.of(
                            "oxidation-m",
                            "phospho-sty",
                            "acetyl-protein-n-term",
                            "deamidation-nq",
                            "gln-pyro-glu"),
                    new VariableModsViewModel(startingIn(C02))
                            .presets().stream().map(ModificationPreset::id).toList());
        }

        @Test
        @DisplayName("a preset the release cannot hold is refused with the model's reason")
        void presetTheReleaseCannotHold() {
            ModificationPreset caret =
                    preset(
                            new VariableModsViewModel(startingIn(C03)),
                            "acetyl-protein-n-term-caret");
            ParameterSession c02 = startingIn(C02);
            VariableModsViewModel older = new VariableModsViewModel(c02);
            assertEquals(
                    EditOutcome.refused(
                            "Acetyl cannot be added to a Comet 2026.02.2 configuration: \"^\""
                                    + " holds '^', which Comet 2026.02.2 does not accept in a"
                                    + " residue token; its residue alphabet is A-Z, n"
                                    + " (N-terminus), c (C-terminus), so it cannot be written"),
                    older.add(caret));
            assertEquals(UNUSED, older.slot("variable_mod02").serialised());
        }

        @Test
        @DisplayName("a slot equal to a preset is named after it")
        void namedAfterAPreset() {
            VariableModsViewModel editor = new VariableModsViewModel(startingIn(C03));
            editor.add(preset(editor, "acetyl-protein-n-term-caret"));
            VariableModSlotView slot = editor.slot("variable_mod02");
            assertEquals(
                    "Acetyl: +42.010565 on protein N-terminus; max 1 per peptide; optional",
                    slot.summary());
            assertEquals("acetyl-protein-n-term-caret", slot.preset().orElseThrow().id());
            assertEquals("42.010565 ^ 0 1 -1 0 0 0.0", slot.serialised());
        }

        @Test
        @DisplayName("a release switch rebuilds the slots for the new release")
        void releaseSwitch() {
            ParameterSession session = startingIn(C03);
            VariableModsViewModel editor = new VariableModsViewModel(session);
            assertEquals(4, editor.terminusCodes().size());
            assertEquals(EditOutcome.applied(), session.selectRelease(C02));
            assertEquals(2, editor.terminusCodes().size());
            assertEquals(5, editor.presets().size());
            assertEquals(15, editor.slots().size());
            assertEquals(C02, editor.field("variable_mod01").release());
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a part's unreadable text is refused at the slot's field, configuration kept")
        void unreadablePart() {
            ParameterSession session = startingIn(C03);
            VariableModsViewModel editor = new VariableModsViewModel(session);
            String message =
                    "variable_mod01, maximum count per peptide: \"two\" is not a whole number";
            assertEquals(
                    EditOutcome.refused(message),
                    editor.setPart("variable_mod01", VariableModPart.MAXIMUM_COUNT, "two"));
            assertEquals(DEFAULT_OXIDATION, editor.slot("variable_mod01").serialised());
            FieldViewModel field = editor.field("variable_mod01");
            assertEquals(FieldState.ERROR, field.state());
            assertEquals("two", field.text());
            assertEquals(
                    "Error: not applied, the configuration still holds \""
                            + DEFAULT_OXIDATION
                            + "\". "
                            + message,
                    field.stateText());
            assertEquals(
                    List.of("variable_mod01"),
                    session.pendingRefusals().stream().map(FieldViewModel::name).toList());
        }

        @Test
        @DisplayName("a choice sets its code; clearing the last residue is refused")
        void choicesAndLastResidue() {
            VariableModsViewModel editor = new VariableModsViewModel(startingIn(C02));
            VariableModChoice exclusive =
                    editor.slot("variable_mod01")
                            .part(VariableModPart.REQUIRED)
                            .orElseThrow()
                            .choices()
                            .get(2);
            assertEquals(
                    EditOutcome.applied(),
                    editor.choose("variable_mod01", VariableModPart.REQUIRED, exclusive));
            assertEquals("15.9949 M 0 3 -1 0 -1 0.0", editor.slot("variable_mod01").serialised());
            assertEquals(
                    EditOutcome.refused(
                            "variable_mod01, residues: a modification applies to at least one"
                                    + " residue or terminus; remove the modification to leave the"
                                    + " slot unused"),
                    editor.setResidue("variable_mod01", 'M', false));
        }

        @Test
        @DisplayName(
                "the first slot cannot move up, the last cannot move down; to itself is nothing")
        void edges() {
            ParameterSession session = startingIn(C03);
            VariableModsViewModel editor = new VariableModsViewModel(session);
            assertEquals(
                    EditOutcome.refused("variable_mod01 is the first slot; it cannot move up"),
                    editor.moveUp("variable_mod01"));
            assertEquals(
                    EditOutcome.refused("variable_mod15 is the last slot; it cannot move down"),
                    editor.moveDown("variable_mod15"));
            assertEquals(EditOutcome.applied(), editor.moveTo("variable_mod01", "variable_mod01"));
            assertEquals(EditOutcome.applied(), editor.moveDown("variable_mod14"));
            assertEquals(ValueOrigin.COMET_DEFAULT, session.model().origin("variable_mod14"));
            assertEquals(
                    "Comet 2026.03.0 has no variable-modification slot variable_mod16",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> editor.remove("variable_mod16"))
                            .getMessage());
        }

        @Test
        @DisplayName("adding to a configuration whose fifteen slots are used is refused")
        void full() {
            ParameterSession session = startingIn(C02);
            VariableModsViewModel editor = new VariableModsViewModel(session);
            ModificationPreset deamidation = preset(editor, "deamidation-nq");
            for (int slot = 2; slot <= 15; slot++) {
                assertEquals(EditOutcome.applied(), editor.add(deamidation));
            }
            assertEquals(Optional.empty(), editor.firstFreeSlot());
            assertEquals(
                    EditOutcome.refused(
                            "Deamidation cannot be added: all 15 variable-modification slots hold"
                                    + " a modification; remove one first"),
                    editor.add(deamidation));
            assertEquals(EditOutcome.applied(), editor.remove("variable_mod09"));
            assertEquals(Optional.of("variable_mod09"), editor.firstFreeSlot());
        }
    }

    @Nested
    @DisplayName("R-PARAM-10: the limit and the requirement beside the slots")
    class Cross {

        @Test
        @DisplayName(
                "require_variable_mod with no active slot is the model's error, shown with them")
        void requiredWithoutSlot() {
            ParameterSession session = startingIn(C03);
            VariableModsViewModel editor = new VariableModsViewModel(session);
            assertEquals("max_variable_mods_in_peptide", editor.limitField().name());
            assertEquals("require_variable_mod", editor.requireField().name());
            assertEquals(List.of(), editor.crossFindings());
            // a finding elsewhere in the report is not one of these
            assertEquals(EditOutcome.applied(), session.edit("peptide_length_range", "30 7"));
            assertTrue(session.report().hasErrors());
            assertEquals(List.of(), editor.crossFindings());
            assertEquals(EditOutcome.applied(), editor.remove("variable_mod01"));
            assertEquals(EditOutcome.applied(), editor.requireField().setText("1"));
            List<Finding> findings = editor.crossFindings();
            assertEquals(1, findings.size());
            assertEquals(Rule.VARMODS_REQUIRED_WITHOUT_SLOT, findings.get(0).rule());
            assertEquals(Severity.ERROR, findings.get(0).severity());
            assertEquals(List.of("require_variable_mod"), findings.get(0).parameters());
            editor.add(preset(editor, "oxidation-m"));
            assertEquals(List.of(), editor.crossFindings());
        }

        @Test
        @DisplayName("a minimum count above the limit is reported at the slot and the limit")
        void minimumAboveLimit() {
            ParameterSession session = startingIn(C02);
            VariableModsViewModel editor = new VariableModsViewModel(session);
            assertEquals(EditOutcome.applied(), editor.limitField().setText("2"));
            assertEquals(
                    EditOutcome.applied(),
                    editor.setPart("variable_mod01", VariableModPart.MINIMUM_COUNT, "3"));
            List<Finding> findings = editor.crossFindings();
            assertEquals(
                    List.of(Rule.VARMODS_MINIMUM_ABOVE_LIMIT),
                    findings.stream().map(Finding::rule).toList());
            assertEquals(
                    List.of("variable_mod01", "max_variable_mods_in_peptide"),
                    findings.get(0).parameters());
            assertEquals(
                    findings,
                    editor.field("variable_mod01").findings().stream()
                            .filter(f -> f.rule() == Rule.VARMODS_MINIMUM_ABOVE_LIMIT)
                            .toList());
        }
    }

    @Nested
    @DisplayName("a migration under review: unresolved slots are not moved or filled")
    class Review {

        /** variable_mod01 and variable_mod02 each need attention after 2026.02.2 -> 2026.03.0. */
        private ParameterSession migrated() {
            ParameterSession session = startingIn(C02);
            // CONSTRUCTED edits: a terminus outside 0-3 at a distance, which 2026.03.0 refuses
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            session.edit("variable_mod02", "79.966331 STY 0 3 2 4 0 0.0");
            assertEquals(EditOutcome.applied(), session.selectRelease(C03));
            assertEquals(
                    List.of("variable_mod01", "variable_mod02"),
                    session.unresolved().stream().map(MigrationEntry::parameter).toList());
            return session;
        }

        @Test
        @DisplayName("moving to or from an unresolved slot is refused and resolves nothing")
        void movesRefused() {
            ParameterSession session = migrated();
            VariableModsViewModel editor = new VariableModsViewModel(session);
            assertTrue(editor.slot("variable_mod01").needsAttention());
            assertTrue(editor.slot("variable_mod02").needsAttention());
            assertFalse(editor.slot("variable_mod03").needsAttention());
            assertEquals(
                    EditOutcome.refused(
                            "variable_mod01 needs your decision in the migration review before a"
                                    + " modification can be moved to or from it: set its value or"
                                    + " accept it first"),
                    editor.moveDown("variable_mod01"));
            assertEquals(
                    EditOutcome.refused(
                            "variable_mod02 needs your decision in the migration review before a"
                                    + " modification can be moved to or from it: set its value or"
                                    + " accept it first"),
                    editor.moveTo("variable_mod03", "variable_mod02"));
            assertEquals(2, session.unresolved().size());
            assertEquals(DEFAULT_OXIDATION, editor.slot("variable_mod01").serialised());
        }

        @Test
        @DisplayName("an added modification skips an unused slot that needs attention")
        void addSkipsUnresolved() {
            ParameterSession session = migrated();
            VariableModsViewModel editor = new VariableModsViewModel(session);
            assertEquals(UNUSED, editor.slot("variable_mod02").serialised());
            assertEquals(Optional.of("variable_mod03"), editor.firstFreeSlot());
            assertEquals(EditOutcome.applied(), editor.add(preset(editor, "oxidation-m")));
            assertEquals("15.994915 M 0 3 -1 0 0 0.0", editor.slot("variable_mod03").serialised());
            assertEquals(UNUSED, editor.slot("variable_mod02").serialised());
            assertEquals(2, session.unresolved().size());
            for (int slot = 4; slot <= 15; slot++) {
                editor.add(preset(editor, "oxidation-m"));
            }
            assertEquals(
                    EditOutcome.refused(
                            "Oxidation cannot be added: all 15 variable-modification slots hold a"
                                    + " modification; remove one first. Unused but waiting for your"
                                    + " decision in the migration review: variable_mod02"),
                    editor.add(preset(editor, "oxidation-m")));
        }

        @Test
        @DisplayName("editing or removing an unresolved slot is the scientist's decision on it")
        void editAndRemoveResolve() {
            ParameterSession session = migrated();
            VariableModsViewModel editor = new VariableModsViewModel(session);
            assertEquals(EditOutcome.applied(), editor.remove("variable_mod01"));
            assertEquals(
                    List.of("variable_mod02"),
                    session.unresolved().stream().map(MigrationEntry::parameter).toList());
            assertFalse(editor.slot("variable_mod01").needsAttention());
            assertEquals(
                    EditOutcome.applied(),
                    editor.setPart("variable_mod02", VariableModPart.MASS, "79.966331"));
            assertEquals(List.of(), session.unresolved());
            assertEquals(EditOutcome.applied(), editor.moveTo("variable_mod02", "variable_mod01"));
            assertEquals("79.966331 X 0 3 -1 0 0 0.0", editor.slot("variable_mod01").serialised());
        }

        @Test
        @DisplayName("accepting the entry in the review lets the slot move")
        void acceptedEntryMoves() {
            ParameterSession session = migrated();
            VariableModsViewModel editor = new VariableModsViewModel(session);
            session.resolve("variable_mod01");
            assertFalse(editor.slot("variable_mod01").needsAttention());
            assertEquals(EditOutcome.applied(), editor.moveTo("variable_mod01", "variable_mod05"));
            assertEquals(DEFAULT_OXIDATION, editor.slot("variable_mod05").serialised());
        }
    }
}
