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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link PinHeader}: the column rule, with every refusal's words. */
class PinHeaderTest {

    private static String refusal(String line) {
        return assertThrows(IllegalArgumentException.class, () -> PinHeader.parse(line))
                .getMessage();
    }

    @Test
    @DisplayName("Comet's 28 columns: 23 features between ScanNr and Peptide")
    void real() {
        PinHeader header = PinHeader.parse(PinText.HEADER);
        assertEquals(PinText.COLUMNS, header.columns());
        assertEquals(
                List.of(
                        "ExpMass",
                        "CalcMass",
                        "lnrSp",
                        "deltLCn",
                        "deltCn",
                        "lnExpect",
                        "Xcorr",
                        "Sp",
                        "IonFrac",
                        "Mass",
                        "PepLen",
                        "Charge1",
                        "Charge2",
                        "Charge3",
                        "Charge4",
                        "Charge5",
                        "Charge6",
                        "enzN",
                        "enzC",
                        "enzInt",
                        "lnNumSP",
                        "dM",
                        "absdM"),
                header.featureColumns());
    }

    @Test
    @DisplayName("the smallest header: one feature")
    void smallest() {
        assertEquals(
                List.of("f"),
                PinHeader.parse("SpecId\tLabel\tScanNr\tf\tPeptide\tProteins").featureColumns());
    }

    @Test
    @DisplayName("too few columns are refused, with the count")
    void tooFew() {
        assertEquals(
                "its header has 5 columns, fewer than SpecId, Label, ScanNr, one feature, Peptide"
                        + " and Proteins",
                refusal("SpecId\tLabel\tScanNr\tPeptide\tProteins"));
        assertEquals(
                "its header has 1 column, fewer than SpecId, Label, ScanNr, one feature, Peptide"
                        + " and Proteins",
                refusal("<?xml version=\"1.0\"?>"));
    }

    @Test
    @DisplayName("a leading column missing or out of place is refused")
    void leading() {
        assertEquals(
                "its header begins [Label, SpecId, ScanNr] where a PIN header begins [SpecId,"
                        + " Label, ScanNr]",
                refusal("Label\tSpecId\tScanNr\tf\tPeptide\tProteins"));
        assertEquals(
                "its header begins [SpecId, Label, ExpMass] where a PIN header begins [SpecId,"
                        + " Label, ScanNr]",
                refusal("SpecId\tLabel\tExpMass\tf\tPeptide\tProteins"));
    }

    @Test
    @DisplayName("a trailing column missing or out of place is refused")
    void trailing() {
        assertEquals(
                "its header ends [Proteins, Peptide] where a PIN header ends [Peptide, Proteins]",
                refusal("SpecId\tLabel\tScanNr\tf\tProteins\tPeptide"));
        assertEquals(
                "its header ends [Peptide, Protein] where a PIN header ends [Peptide, Proteins]",
                refusal("SpecId\tLabel\tScanNr\tf\tPeptide\tProtein"));
    }

    @Test
    @DisplayName("an empty column name, and a name given twice, are refused")
    void names() {
        assertEquals(
                "its header's column 5 has no name",
                refusal("SpecId\tLabel\tScanNr\tf\t\tPeptide\tProteins"));
        assertEquals(
                "its header names the column \"f\" twice",
                refusal("SpecId\tLabel\tScanNr\tf\tg\tf\tPeptide\tProteins"));
    }

    @Test
    @DisplayName("the columns are copied, not shared")
    void copies() {
        PinHeader header = PinHeader.parse(PinText.HEADER);
        assertThrows(UnsupportedOperationException.class, () -> header.columns().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> header.featureColumns().add("x"));
    }
}
