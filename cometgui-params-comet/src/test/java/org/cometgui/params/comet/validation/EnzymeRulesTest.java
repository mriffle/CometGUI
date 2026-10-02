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

package org.cometgui.params.comet.validation;

import static org.cometgui.params.comet.validation.Models.assertAttached;
import static org.cometgui.params.comet.validation.Models.only;
import static org.cometgui.params.comet.validation.Models.validate;
import static org.cometgui.params.comet.validation.Models.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.EnzymeTable;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.params.comet.writer.ParamsWriteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enzyme references and enzyme rows. The table is the REAL {@code -q} table; every custom row is
 * CONSTRUCTED test input.
 */
class EnzymeRulesTest {

    private static final List<String> REFERENCES =
            List.of("search_enzyme_number", "search_enzyme2_number", "sample_enzyme_number");

    private static CometParameters withRow(CometParameters model, EnzymeDefinition row) {
        return model.withEnzymeTable(model.enzymeTable().with(row));
    }

    private static String repeat(char c, int times) {
        return String.valueOf(c).repeat(times);
    }

    @Test
    @DisplayName("each enzyme reference absent from the table is an error at that parameter")
    void missingReference() {
        for (String name : REFERENCES) {
            Finding finding = only(validate(with(name, "42")));
            assertAttached(
                    finding, Rule.ENZYME_NOT_IN_TABLE, ParameterCategory.DIGESTION_ENZYMES, name);
            assertEquals(
                    name
                            + " = 42 names enzyme 42, which is not in the [COMET_ENZYME_INFO]"
                            + " table (its numbers are [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);"
                            + " choose one of its enzymes, or add a row numbered 42",
                    finding.message());
        }
    }

    @Test
    @DisplayName("the validator finds before writing what the writer refuses to write")
    void agreesWithTheWriter() {
        CometParameters model = with("sample_enzyme_number", "12");
        assertEquals(
                List.of("sample_enzyme_number"),
                validate(model).errors().stream().flatMap(f -> f.parameters().stream()).toList());
        ParamsWriteException refused =
                assertThrows(
                        ParamsWriteException.class,
                        () -> new CanonicalParamsWriter(ParamsFiles.build()).write(model));
        assertEquals("sample_enzyme_number", refused.parameter());
    }

    @Test
    @DisplayName("a custom enzyme, added and selected, is clean")
    void customEnzyme() {
        CometParameters model =
                withRow(
                                with(),
                                new EnzymeDefinition(
                                        12,
                                        "Glu_C",
                                        EnzymeDefinition.Sense.AFTER_RESIDUE,
                                        "DE",
                                        "P"))
                        .withText("search_enzyme_number", "12", ValueOrigin.USER);
        assertEquals(List.of(), validate(model).findings());
    }

    @Test
    @DisplayName("a referenced row with a field longer than Comet reads is an error at its users")
    void referencedRowTooLong() {
        EnzymeDefinition longName =
                new EnzymeDefinition(
                        12, repeat('N', 48), EnzymeDefinition.Sense.AFTER_RESIDUE, "K", "");
        CometParameters model =
                withRow(with(), longName)
                        .withText("search_enzyme_number", "12", ValueOrigin.USER)
                        .withText("sample_enzyme_number", "12", ValueOrigin.USER);
        Finding finding = only(validate(model));
        assertAttached(
                finding,
                Rule.ENZYME_ROW_UNREADABLE,
                ParameterCategory.DIGESTION_ENZYMES,
                "search_enzyme_number",
                "sample_enzyme_number");
        assertEquals(
                "enzyme row 12 ("
                        + repeat('N', 48)
                        + "): its name is 48 characters long, and Comet reads at most 47, so"
                        + " Comet would misread the row, and it is selected by"
                        + " search_enzyme_number and sample_enzyme_number; shorten it",
                finding.message());
        EnzymeDefinition longResidues =
                new EnzymeDefinition(
                        12,
                        "Many",
                        EnzymeDefinition.Sense.AFTER_RESIDUE,
                        repeat('K', 20),
                        repeat('P', 20));
        Finding residues =
                only(
                        validate(
                                withRow(with(), longResidues)
                                        .withText(
                                                "search_enzyme2_number", "12", ValueOrigin.USER)));
        assertAttached(
                residues,
                Rule.ENZYME_ROW_UNREADABLE,
                ParameterCategory.DIGESTION_ENZYMES,
                "search_enzyme2_number");
        assertTrue(
                residues.message()
                        .contains(
                                "its cut residues is 20 characters long, and Comet reads at most"
                                        + " 19; its no-cut residues is 20 characters long"),
                residues.message());
    }

    @Test
    @DisplayName("fields exactly as long as Comet reads are clean")
    void atTheLimits() {
        EnzymeDefinition fits =
                new EnzymeDefinition(
                        12,
                        repeat('N', 47),
                        EnzymeDefinition.Sense.BEFORE_RESIDUE,
                        repeat('K', 19),
                        repeat('P', 19));
        assertEquals(
                List.of(),
                validate(
                                withRow(with(), fits)
                                        .withText("search_enzyme_number", "12", ValueOrigin.USER))
                        .findings());
    }

    @Test
    @DisplayName("an unreferenced row that is too long is a warning in the enzyme category")
    void unreferencedRowTooLong() {
        EnzymeDefinition longName =
                new EnzymeDefinition(
                        12, repeat('N', 48), EnzymeDefinition.Sense.AFTER_RESIDUE, "K", "");
        Finding finding = only(validate(withRow(with(), longName)));
        assertEquals(Rule.ENZYME_ROW_UNREADABLE_UNUSED, finding.rule());
        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(List.of(), finding.parameters());
        assertEquals(Optional.of(ParameterCategory.DIGESTION_ENZYMES), finding.category());
        assertTrue(finding.message().endsWith("if it were selected; shorten it before using it"));
    }

    @Test
    @DisplayName("rows not numbered 0, 1, 2 ... are a warning, reported once")
    void numbering() {
        CometParameters gap = with().withEnzymeTable(with().enzymeTable().without(5));
        Finding finding = only(validate(gap));
        assertEquals(Rule.ENZYME_NUMBERING, finding.rule());
        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(List.of(), finding.parameters());
        assertEquals(Optional.of(ParameterCategory.DIGESTION_ENZYMES), finding.category());
        assertEquals(
                "the enzyme rows are numbered [0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11]; Comet's"
                        + " documentation asks for 0, 1, 2 ... in order, so renumber them",
                finding.message());
        EnzymeTable reordered =
                new EnzymeTable(
                        List.of(
                                with().enzymeTable().byNumber(1).orElseThrow(),
                                with().enzymeTable().byNumber(0).orElseThrow()));
        assertEquals(
                List.of(Rule.ENZYME_NUMBERING),
                validate(
                                with().withEnzymeTable(reordered)
                                        .withText("sample_enzyme_number", "1", ValueOrigin.USER))
                        .findings()
                        .stream()
                        .map(Finding::rule)
                        .toList());
    }
}
