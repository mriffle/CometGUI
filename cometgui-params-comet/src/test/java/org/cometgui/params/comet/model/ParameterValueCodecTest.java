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

package org.cometgui.params.comet.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.value.DecimalList;
import org.cometgui.params.comet.value.DecimalRange;
import org.cometgui.params.comet.value.IntegerRange;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One reader and one writer per kind, over the bundled metadata's definitions. */
class ParameterValueCodecTest {

    private static final CuratedMetadata METADATA = ParamsFiles.metadata();

    private static final ParameterValueCodec CODEC =
            ParameterValueCodec.forVersion(METADATA, ParamsFiles.COMET);

    private static ParameterDefinition definition(String name) {
        return METADATA.parameter(name).orElseThrow();
    }

    private static void roundTrips(String name, String text, ParameterValue expected) {
        ParameterValue value = CODEC.parse(definition(name), text);
        assertEquals(expected, value, name);
        assertEquals(text, CODEC.format(definition(name), value), name);
    }

    @Test
    @DisplayName("every kind reads to its variant and writes back to the same text")
    void everyKind() {
        roundTrips("num_threads", "-2", new ParameterValue.Whole(-2));
        roundTrips("isotope_error", "7", new ParameterValue.Whole(7));
        roundTrips("search_enzyme2_number", "0", new ParameterValue.Whole(0));
        roundTrips(
                "fragment_bin_tol", "1.0005", new ParameterValue.Decimal(new BigDecimal("1.0005")));
        roundTrips(
                "peptide_mass_tolerance_lower",
                "-20.00",
                new ParameterValue.Decimal(new BigDecimal("-20.00")));
        roundTrips("clip_nterm_methionine", "1", new ParameterValue.Flag(true));
        roundTrips("use_C_ions", "0", new ParameterValue.Flag(false));
        roundTrips("decoy_prefix", "REV_", new ParameterValue.Text("REV_"));
        roundTrips("activation_method", "ETD+SA", new ParameterValue.Text("ETD+SA"));
        roundTrips(
                "database_name",
                "/data/my files/human.fasta",
                new ParameterValue.Text("/data/my files/human.fasta"));
        roundTrips("peff_obo", "", new ParameterValue.Text(""));
        roundTrips(
                "precursor_charge", "2 4", new ParameterValue.WholeRange(new IntegerRange(2, 4)));
        roundTrips(
                "clear_mz_range",
                "125.5 131.75",
                new ParameterValue.DecimalPair(
                        new DecimalRange(new BigDecimal("125.5"), new BigDecimal("131.75"))));
        roundTrips(
                "mass_offsets",
                "0.0 1.003355",
                new ParameterValue.Decimals(
                        new DecimalList(
                                List.of(new BigDecimal("0.0"), new BigDecimal("1.003355")))));
        roundTrips("mass_offsets", "", new ParameterValue.Decimals(new DecimalList(List.of())));
        ParameterValue tuple =
                CODEC.parse(
                        definition("variable_mod15"),
                        "79.966331 STY 0 2,4 -1 0 0 97.976896,79.966331");
        assertEquals(
                "79.966331 STY 0 2,4 -1 0 0 97.976896,79.966331",
                CODEC.format(definition("variable_mod15"), tuple));
    }

    @Test
    @DisplayName("only notation is canonical: + and exponents are written out")
    void notation() {
        ParameterValue value = CODEC.parse(definition("fragment_bin_tol"), "+1.5e-2");
        assertEquals("0.015", CODEC.format(definition("fragment_bin_tol"), value));
    }

    @Test
    @DisplayName("unreadable text is refused naming the parameter")
    void unreadable() {
        Map<String, String> cases =
                Map.of(
                        "num_threads", "",
                        "fragment_bin_tol", "0,02",
                        "use_B_ions", "yes",
                        "output_sqtfile", "2",
                        "precursor_charge", "2",
                        "decoy_prefix", "a#b",
                        "variable_mod02", "0.0 X 0 3 -1 0 0",
                        "digest_mass_range", "600.0",
                        "mass_offsets", "1.0 abc",
                        "search_enzyme_number", "1.5");
        for (Map.Entry<String, String> each : cases.entrySet()) {
            ValueSyntaxException failure =
                    assertThrows(
                            ValueSyntaxException.class,
                            () -> CODEC.parse(definition(each.getKey()), each.getValue()),
                            each.getKey());
            assertEquals(each.getKey(), failure.subject(), failure.getMessage());
        }
        assertEquals(
                "fragment_bin_tol, value: \"0.02 0.03\" is not one number",
                assertThrows(
                                ValueSyntaxException.class,
                                () -> CODEC.parse(definition("fragment_bin_tol"), "0.02 0.03"))
                        .getMessage());
        assertEquals(
                "use_B_ions, value: \"yes\" is not 0 or 1",
                assertThrows(
                                ValueSyntaxException.class,
                                () -> CODEC.parse(definition("use_B_ions"), "yes"))
                        .getMessage());
    }

    @Test
    @DisplayName("a value of another variant is not formatted")
    void wrongVariant() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                CODEC.format(
                                        definition("decoy_prefix"), new ParameterValue.Whole(3)));
        assertEquals(
                "decoy_prefix is of kind STRING and cannot hold a Whole value",
                failure.getMessage());
    }

    @Test
    @DisplayName("fits: each kind takes exactly its variant")
    void fits() {
        Map<ValueKind, ParameterValue> variant =
                Map.ofEntries(
                        Map.entry(ValueKind.INTEGER, new ParameterValue.Whole(1)),
                        Map.entry(ValueKind.INTEGER_ENUM, new ParameterValue.Whole(1)),
                        Map.entry(ValueKind.ENZYME_REFERENCE, new ParameterValue.Whole(1)),
                        Map.entry(ValueKind.DECIMAL, new ParameterValue.Decimal(BigDecimal.ONE)),
                        Map.entry(
                                ValueKind.TOLERANCE_PAIR_MEMBER,
                                new ParameterValue.Decimal(BigDecimal.ONE)),
                        Map.entry(ValueKind.BOOLEAN_FLAG, new ParameterValue.Flag(true)),
                        Map.entry(ValueKind.ION_SERIES_FLAG, new ParameterValue.Flag(true)),
                        Map.entry(ValueKind.STRING, new ParameterValue.Text("a")),
                        Map.entry(ValueKind.STRING_ENUM, new ParameterValue.Text("a")),
                        Map.entry(ValueKind.FILE_PATH, new ParameterValue.Text("a")),
                        Map.entry(
                                ValueKind.INTEGER_RANGE,
                                new ParameterValue.WholeRange(new IntegerRange(1, 2))),
                        Map.entry(
                                ValueKind.DECIMAL_RANGE,
                                new ParameterValue.DecimalPair(
                                        new DecimalRange(BigDecimal.ONE, BigDecimal.TEN))),
                        Map.entry(
                                ValueKind.DECIMAL_LIST,
                                new ParameterValue.Decimals(new DecimalList(List.of()))),
                        Map.entry(
                                ValueKind.VARIABLE_MOD_TUPLE,
                                CODEC.parse(
                                        definition("variable_mod01"), "15.9949 M 0 3 -1 0 0 0.0")));
        assertEquals(ValueKind.values().length, variant.size());
        for (ValueKind kind : ValueKind.values()) {
            for (Map.Entry<ValueKind, ParameterValue> other : variant.entrySet()) {
                boolean same = other.getValue().getClass() == variant.get(kind).getClass();
                assertEquals(
                        same,
                        ParameterValueCodec.fits(kind, other.getValue()),
                        kind + " / " + other.getKey());
            }
        }
    }

    @Test
    @DisplayName("text that would not read back is refused: #, line breaks, padding")
    void unwritableText() {
        assertEquals(Optional.empty(), ParameterValueCodec.unwritableText("a b"));
        assertTrue(ParameterValueCodec.unwritableText("a#b").orElseThrow().contains("'#'"));
        assertTrue(ParameterValueCodec.unwritableText("a\nb").orElseThrow().contains("line"));
        assertTrue(ParameterValueCodec.unwritableText("a\rb").orElseThrow().contains("line"));
        assertTrue(ParameterValueCodec.unwritableText(" a").orElseThrow().contains("white"));
        assertTrue(ParameterValueCodec.unwritableText("#a").orElseThrow().contains("'#'"));
        assertTrue(ParameterValueCodec.unwritableText("\na").orElseThrow().contains("line"));
        assertTrue(ParameterValueCodec.unwritableText("\ra").orElseThrow().contains("line"));
        assertTrue(ParameterValueCodec.unwritableText("a ").orElseThrow().contains("white"));
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> new ParameterValue.Text("x#"));
        assertTrue(failure.getMessage().startsWith("the text \"x#\" cannot be a parameter value"));
        assertFalse(new ParameterValue.Text("").text().length() > 0);
    }
}
