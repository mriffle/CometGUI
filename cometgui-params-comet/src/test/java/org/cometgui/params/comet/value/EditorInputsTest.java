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

package org.cometgui.params.comet.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.cometgui.params.comet.value.EnzymeDefinition.Sense;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What structured editors hand the model as separate texts -- a custom enzyme's fields, a range's
 * two values -- read by the model so that no editor reads a number. Expected values typed by hand.
 */
class EditorInputsTest {

    @Nested
    @DisplayName("a custom enzyme from its fields")
    class CustomEnzyme {

        @Test
        @DisplayName("reads the number, strips white space, and takes - or nothing as none")
        void reads() {
            EnzymeDefinition gluC =
                    EnzymeDefinition.fromTexts(" 12 ", " Glu_C ", Sense.AFTER_RESIDUE, "DE", " P ");
            assertEquals(new EnzymeDefinition(12, "Glu_C", Sense.AFTER_RESIDUE, "DE", "P"), gluC);
            assertEquals(
                    "12. Glu_C                  1      DE          P",
                    EnzymeTableCodec.formatRow(gluC));
            EnzymeDefinition none =
                    EnzymeDefinition.fromTexts("0", "Nothing", Sense.BEFORE_RESIDUE, "-", "");
            assertEquals(new EnzymeDefinition(0, "Nothing", Sense.BEFORE_RESIDUE, "", ""), none);
            assertTrue(none.nonSpecific());
        }

        @Test
        @DisplayName("refuses what a row cannot hold, naming the field")
        void refusals() {
            assertEquals(
                    "custom enzyme, number: \"x\" is not a whole number",
                    refusal("x", "Glu_C", "DE", "P"));
            assertEquals(
                    "custom enzyme, number: \"-1\" is negative; enzyme numbers start at 0",
                    refusal("-1", "Glu_C", "DE", "P"));
            assertEquals(
                    "custom enzyme, number: \"\" is not a whole number",
                    refusal("  ", "Glu_C", "DE", "P"));
            assertEquals(
                    "custom enzyme, name: \"Glu C\" is not one word; Comet reads the name as one"
                            + " token",
                    refusal("12", "Glu C", "DE", "P"));
            assertEquals(
                    "custom enzyme, name: \"\" is not one word; Comet reads the name as one token",
                    refusal("12", " ", "DE", "P"));
            assertEquals(
                    "custom enzyme, cut residues: \"D E\" holds white space; write the residues as"
                            + " one token, such as KR",
                    refusal("12", "Glu_C", "D E", "P"));
            assertEquals(
                    "custom enzyme, no-cut residues: \"P Q\" holds white space; write the residues"
                            + " as one token, such as KR",
                    refusal("12", "Glu_C", "DE", "P Q"));
        }

        private String refusal(String number, String name, String cut, String noCut) {
            return assertThrows(
                            ValueSyntaxException.class,
                            () ->
                                    EnzymeDefinition.fromTexts(
                                            number, name, Sense.AFTER_RESIDUE, cut, noCut))
                    .getMessage();
        }

        @Test
        @DisplayName("the next number follows the highest; 0 for an empty table")
        void nextNumber() {
            EnzymeDefinition trypsin =
                    new EnzymeDefinition(1, "Trypsin", Sense.AFTER_RESIDUE, "KR", "P");
            EnzymeDefinition cut =
                    new EnzymeDefinition(0, "Cut_everywhere", Sense.BEFORE_RESIDUE, "", "");
            EnzymeDefinition gap = new EnzymeDefinition(7, "CNBr", Sense.AFTER_RESIDUE, "M", "");
            assertEquals(0, new EnzymeTable(List.of()).nextNumber());
            assertEquals(1, new EnzymeTable(List.of(cut)).nextNumber());
            assertEquals(2, new EnzymeTable(List.of(trypsin, cut)).nextNumber());
            assertEquals(8, new EnzymeTable(List.of(gap, trypsin)).nextNumber());
        }
    }

    @Nested
    @DisplayName("a range from its two values")
    class TwoValues {

        @Test
        @DisplayName("whole numbers: each read alone, texts back")
        void wholeNumbers() {
            IntegerRange range = IntegerRange.parse("peptide_length_range", " 7 ", "40");
            assertEquals(new IntegerRange(7, 40), range);
            assertEquals("7", range.firstText());
            assertEquals("40", range.secondText());
            assertEquals("7 40", range.text());
            assertEquals(
                    "peptide_length_range, first value: \"7 8\" is not one number",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> IntegerRange.parse("peptide_length_range", "7 8", "40"))
                            .getMessage());
            assertEquals(
                    "peptide_length_range, second value: \"\" is not one number",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> IntegerRange.parse("peptide_length_range", "7", ""))
                            .getMessage());
            assertEquals(
                    "scan_range, second value: \"9.5\" is not a whole number",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> IntegerRange.parse("scan_range", "1", "9.5"))
                            .getMessage());
            assertEquals(
                    "scan_range, first value: \"a\" is not a whole number",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> IntegerRange.parse("scan_range", "a", "9"))
                            .getMessage());
        }

        @Test
        @DisplayName("decimals: scale kept; a reversed pair is read, not judged")
        void decimals() {
            DecimalRange range = DecimalRange.parse("digest_mass_range", "600.00", " 5000.0");
            assertEquals(
                    new DecimalRange(new BigDecimal("600.00"), new BigDecimal("5000.0")), range);
            assertEquals("600.00", range.firstText());
            assertEquals("5000.0", range.secondText());
            assertEquals("600.00 5000.0", range.text());
            assertEquals(
                    new DecimalRange(new BigDecimal("126.0"), new BigDecimal("125.0")),
                    DecimalRange.parse("clear_mz_range", "126.0", "125.0"));
            assertEquals(
                    "clear_mz_range, first value: \"1,5\" is not a number",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> DecimalRange.parse("clear_mz_range", "1,5", "2"))
                            .getMessage());
            assertEquals(
                    "clear_mz_range, second value: \"x\" is not a number",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> DecimalRange.parse("clear_mz_range", "1", "x"))
                            .getMessage());
            assertEquals(
                    "clear_mz_range, second value: \"1 2\" is not one number",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> DecimalRange.parse("clear_mz_range", "1", "1 2"))
                            .getMessage());
        }
    }

    @Test
    @DisplayName("a residue token holds a character, terminal codes included")
    void holds() {
        VariableModification value =
                new VariableModification(
                        new BigDecimal("42.010565"),
                        "nK^",
                        0,
                        java.util.OptionalInt.empty(),
                        1,
                        -1,
                        0,
                        0,
                        List.of(new BigDecimal("0.0")));
        assertTrue(value.holds('n'));
        assertTrue(value.holds('K'));
        assertTrue(value.holds('^'));
        assertFalse(value.holds('c'));
        assertFalse(value.holds('M'));
        assertEquals(
                "Only peptides that contain the modification are analysed.",
                VariableModification.Requirement.REQUIRED.explanation());
    }
}
