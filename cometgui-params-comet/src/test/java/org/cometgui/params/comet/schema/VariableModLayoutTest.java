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

package org.cometgui.params.comet.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The layout's own rules, over CONSTRUCTED layouts. */
class VariableModLayoutTest {

    private static VariableModLayout.Entry entry(VariableModField field, boolean pair) {
        return new VariableModLayout.Entry(field, field.kind(), pair);
    }

    private static String refused(VariableModLayout.Entry... entries) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () -> new VariableModLayout(List.of(entries), "https://example.org"))
                .getMessage();
    }

    @Test
    void theSmallestLayoutIsMassAndResidues() {
        VariableModLayout layout =
                new VariableModLayout(
                        List.of(
                                entry(VariableModField.RESIDUES, false),
                                entry(VariableModField.MASS, false)),
                        "https://example.org");
        assertEquals("residues, mass difference", layout.describe());
        assertEquals(
                Optional.of(entry(VariableModField.MASS, false)),
                layout.entry(VariableModField.MASS));
        assertEquals(Optional.empty(), layout.entry(VariableModField.COUNT));
        assertEquals("https://example.org", layout.source());
    }

    @Test
    void everyFieldWithBothPairs() {
        List<VariableModLayout.Entry> entries = new ArrayList<>();
        for (VariableModField field : VariableModField.values()) {
            entries.add(entry(field, field.pairable()));
        }
        VariableModLayout layout = new VariableModLayout(entries, "https://example.org");
        assertEquals(
                "mass difference, residues, binary group, count per peptide[,pair], terminal"
                        + " distance, terminus, required, neutral loss[,pair]",
                layout.describe());
        entries.clear();
        assertEquals(8, layout.fields().size());
        assertThrows(UnsupportedOperationException.class, () -> layout.fields().clear());
    }

    @Test
    void onlyTheCountAndTheNeutralLossArePairable() {
        for (VariableModField field : VariableModField.values()) {
            assertEquals(
                    field == VariableModField.COUNT || field == VariableModField.NEUTRAL_LOSS,
                    field.pairable(),
                    field.name());
        }
        assertEquals(VariableModField.Kind.RESIDUES, VariableModField.RESIDUES.kind());
        assertEquals(VariableModField.Kind.DECIMAL, VariableModField.NEUTRAL_LOSS.kind());
        assertEquals(VariableModField.Kind.INTEGER, VariableModField.REQUIRED.kind());
        assertEquals("terminal distance", VariableModField.TERMINAL_DISTANCE.label());
    }

    @Test
    void refusals() {
        assertEquals(
                "the layout has no MASS field; a tuple without its mass difference means nothing",
                refused());
        assertEquals(
                "the layout has no RESIDUES field; a tuple without its residues means nothing",
                refused(entry(VariableModField.MASS, false)));
        assertEquals(
                "field 3 (MASS) is listed twice",
                refused(
                        entry(VariableModField.MASS, false),
                        entry(VariableModField.RESIDUES, false),
                        entry(VariableModField.MASS, false)));
        assertEquals(
                "field 1 (MASS) is declared INTEGER, and a mass difference is DECIMAL",
                refused(
                        new VariableModLayout.Entry(
                                VariableModField.MASS, VariableModField.Kind.INTEGER, false)));
        assertEquals(
                "field 2 (RESIDUES) is given a comma pair, which no Comet release accepts there",
                refused(
                        entry(VariableModField.MASS, false),
                        entry(VariableModField.RESIDUES, true)));
        assertTrue(
                refused(entry(VariableModField.MASS, true)).startsWith("field 1 (MASS) is given"));
        assertFalse(
                new VariableModLayout(
                                List.of(
                                        entry(VariableModField.MASS, false),
                                        entry(VariableModField.RESIDUES, false),
                                        entry(VariableModField.COUNT, false)),
                                "https://example.org")
                        .fields()
                        .get(2)
                        .acceptsPair());
    }
}
