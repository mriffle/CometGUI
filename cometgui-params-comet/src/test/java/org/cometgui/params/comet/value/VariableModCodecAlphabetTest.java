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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.schema.ResidueAlphabet;
import org.cometgui.params.comet.schema.VariableModField;
import org.cometgui.params.comet.schema.VariableModLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The codec holds the residue token to the release's {@link ResidueAlphabet}, read from the version
 * record, both when reading and when writing; the diagnostic names the release. The CONSTRUCTED
 * layouts show the alphabet is data the codec follows, not a rule in its code.
 */
class VariableModCodecAlphabetTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static final String OLD_ALPHABET = "A-Z, n (N-terminus), c (C-terminus)";

    private static VariableModCodec codec(String release) {
        return VariableModCodec.forVersion(METADATA, ToolVersion.parse(release));
    }

    private static VariableModification value(String residues) {
        return new VariableModification(
                new BigDecimal("42.010565"),
                residues,
                0,
                OptionalInt.empty(),
                1,
                -1,
                0,
                0,
                List.of(new BigDecimal("0.0")));
    }

    private static VariableModCodec constructed(String release, String characters) {
        List<VariableModLayout.Entry> entries =
                List.of(
                        new VariableModLayout.Entry(
                                VariableModField.MASS, VariableModField.Kind.DECIMAL, false),
                        new VariableModLayout.Entry(
                                VariableModField.RESIDUES, VariableModField.Kind.RESIDUES, false));
        VariableModLayout layout =
                new VariableModLayout(
                        entries,
                        "https://example.org/constructed",
                        new ResidueAlphabet(characters, "https://example.org/constructed"));
        return release == null
                ? new VariableModCodec(layout, List.of("variable_mod01"))
                : new VariableModCodec(release, layout, List.of("variable_mod01"));
    }

    @Test
    @DisplayName("2026.03.0 reads and writes ^ and $; 2026.02.2 and 2024.01.0 refuse both ways")
    void versionScoped() {
        VariableModification acetyl =
                codec("2026.03.0").parse("variable_mod07", "42.010565 ^ 0 1 -1 0 0 0.0");
        assertEquals("^", acetyl.residues());
        assertEquals("42.010565 ^ 0 1 -1 0 0 0.0", codec("2026.03.0").format(acetyl));
        for (String release : List.of("2026.02.2", "2024.01.0")) {
            ValueSyntaxException reading =
                    assertThrows(
                            ValueSyntaxException.class,
                            () ->
                                    codec(release)
                                            .parse("variable_mod07", "42.010565 ^ 0 1 -1 0 0 0.0"));
            assertEquals("variable_mod07", reading.subject());
            assertEquals("field 2 (residues)", reading.field());
            assertEquals(
                    "variable_mod07, field 2 (residues): \"^\" holds '^', which Comet "
                            + release
                            + " does not accept in a residue token; its residue alphabet is "
                            + OLD_ALPHABET,
                    reading.getMessage());
            IllegalArgumentException writing =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> codec(release).format(value("nK$")));
            assertEquals(
                    "\"nK$\" holds '$', which Comet "
                            + release
                            + " does not accept in a residue token; its residue alphabet is "
                            + OLD_ALPHABET
                            + ", so it cannot be written",
                    writing.getMessage());
        }
    }

    @Test
    @DisplayName("a character no release can mean is the value's own refusal, before the alphabet")
    void aCharacterNoReleaseCanMean() {
        ValueSyntaxException failure =
                assertThrows(
                        ValueSyntaxException.class,
                        () -> codec("2026.03.0").parse("variable_mod01", "1.0 K# 0 3 -1 0 0 0.0"));
        assertEquals(
                "variable_mod01, field 2 (residues): \"K#\" holds '#'; a residue token is letters"
                        + " A-Z and the terminal codes n, c, ^ and $",
                failure.getMessage());
    }

    @Test
    @DisplayName("CONSTRUCTED alphabets: the codec accepts exactly what the data lists")
    void theAlphabetIsData() {
        VariableModCodec narrow = constructed("Comet 2099.01.0", "KM^");
        assertEquals("^K", narrow.parse("variable_mod01", "1.0 ^K").residues());
        assertEquals("1.0 M^", narrow.format(narrow.parse("variable_mod01", "1.0 M^")));
        ValueSyntaxException letter =
                assertThrows(
                        ValueSyntaxException.class, () -> narrow.parse("variable_mod01", "1.0 X"));
        assertEquals(
                "variable_mod01, field 2 (residues): \"X\" holds 'X', which Comet 2099.01.0 does"
                        + " not accept in a residue token; its residue alphabet is KM, ^ (protein"
                        + " N-terminus)",
                letter.getMessage());
        assertThrows(ValueSyntaxException.class, () -> narrow.parse("variable_mod01", "1.0 n"));
        assertEquals(
                "\"$\" holds '$', which Comet 2099.01.0 does not accept in a residue token; its"
                        + " residue alphabet is KM, ^ (protein N-terminus), so it cannot be"
                        + " written",
                assertThrows(IllegalArgumentException.class, () -> narrow.format(value("$")))
                        .getMessage());
        VariableModCodec unnamed = constructed(null, "STY");
        assertEquals(
                "\"K\" holds 'K', which this Comet version does not accept in a residue token; its"
                        + " residue alphabet is STY, so it cannot be written",
                assertThrows(IllegalArgumentException.class, () -> unnamed.format(value("K")))
                        .getMessage());
    }
}
