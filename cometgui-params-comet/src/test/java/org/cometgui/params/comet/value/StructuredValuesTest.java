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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.parser.ParamsLine;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValueKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The other structured kinds -- the signed tolerance pair, the two-value ranges, the mass-offset
 * list and the ion-series family -- over the values the real {@code comet -q} output holds, plus
 * CONSTRUCTED values for the forms it does not.
 */
class StructuredValuesTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static final Map<String, String> REAL = new LinkedHashMap<>();

    @BeforeAll
    static void readTheRealDump() throws IOException {
        String text =
                new String(
                        CometFixtures.bytes(
                                CometFixtures.COMET_2026_02_2,
                                CometFixtures.LINUX_X86_64,
                                CometFixtures.Mode.COMPLETE),
                        StandardCharsets.UTF_8);
        for (ParamsLine.Declaration declaration : ParamsLineReader.read(text).declarations()) {
            REAL.put(declaration.name(), declaration.value());
        }
    }

    @Test
    @DisplayName("the values below come from the real dump's 118 declarations")
    void theRealDumpIsRead() {
        assertEquals(118, REAL.size());
        assertEquals("15.9949 M 0 3 -1 0 0 0.0", REAL.get("variable_mod01"));
    }

    private static List<String> namesOfKind(ValueKind kind) {
        return METADATA.parametersFor(ToolVersion.parse("2026.02.2")).stream()
                .filter(p -> p.kind() == kind)
                .map(ParameterDefinition::name)
                .toList();
    }

    @Nested
    @DisplayName("the signed precursor tolerance pair")
    class Pair {

        @Test
        @DisplayName("reads the real defaults with the lower bound's sign kept")
        void readsTheRealDefaults() {
            TolerancePair pair =
                    TolerancePair.parse(
                            REAL.get(TolerancePair.LOWER), REAL.get(TolerancePair.UPPER));
            assertEquals(new BigDecimal("-20.0"), pair.lower());
            assertEquals(new BigDecimal("20.0"), pair.upper());
            assertEquals("-20.0", pair.lowerText());
            assertEquals("20.0", pair.upperText());
            assertEquals(
                    namesOfKind(ValueKind.TOLERANCE_PAIR_MEMBER),
                    List.of(TolerancePair.UPPER, TolerancePair.LOWER));
        }

        @Test
        @DisplayName("keeps any sign and scale written (CONSTRUCTED)")
        void keepsSignAndScale() {
            TolerancePair open = TolerancePair.parse("-10.0", "200.0");
            assertEquals("-10.0", open.lowerText());
            assertEquals("200.0", open.upperText());
            TolerancePair sameSigned = TolerancePair.parse("5.250", "10");
            assertEquals("5.250", sameSigned.lowerText());
            assertEquals("10", sameSigned.upperText());
            TolerancePair inverted = TolerancePair.parse("3", "-3");
            assertEquals(new BigDecimal("3"), inverted.lower());
            assertEquals(new BigDecimal("-3"), inverted.upper());
        }

        @Test
        @DisplayName("refuses a member that is not one decimal, naming the parameter")
        void refusesNonDecimals() {
            ValueSyntaxException lower =
                    assertThrows(
                            ValueSyntaxException.class, () -> TolerancePair.parse("-20,0", "20.0"));
            assertEquals(TolerancePair.LOWER, lower.subject());
            ValueSyntaxException upper =
                    assertThrows(
                            ValueSyntaxException.class,
                            () -> TolerancePair.parse("-20.0", "20 30"));
            assertEquals(TolerancePair.UPPER, upper.subject());
            assertEquals(
                    "peptide_mass_tolerance_upper, value: \"20 30\" is not one decimal number",
                    upper.getMessage());
            assertThrows(ValueSyntaxException.class, () -> TolerancePair.parse("", "20.0"));
        }
    }

    @Nested
    @DisplayName("two values on one line")
    class Ranges {

        @Test
        @DisplayName("the real integer ranges read and write back unchanged")
        void realIntegerRanges() {
            List<String> names = namesOfKind(ValueKind.INTEGER_RANGE);
            assertEquals(List.of("scan_range", "precursor_charge", "peptide_length_range"), names);
            for (String name : names) {
                assertEquals(REAL.get(name), IntegerRange.parse(name, REAL.get(name)).text(), name);
            }
            assertEquals(
                    new IntegerRange(5, 50),
                    IntegerRange.parse("peptide_length_range", REAL.get("peptide_length_range")));
        }

        @Test
        @DisplayName("the real decimal ranges read and write back unchanged")
        void realDecimalRanges() {
            List<String> names = namesOfKind(ValueKind.DECIMAL_RANGE);
            assertEquals(List.of("digest_mass_range", "clear_mz_range"), names);
            for (String name : names) {
                assertEquals(REAL.get(name), DecimalRange.parse(name, REAL.get(name)).text(), name);
            }
            DecimalRange digest =
                    DecimalRange.parse("digest_mass_range", REAL.get("digest_mass_range"));
            assertEquals(new BigDecimal("600.0"), digest.first());
            assertEquals(new BigDecimal("5000.0"), digest.second());
        }

        @Test
        @DisplayName("upstream's examples read in either order (CONSTRUCTED from upstream docs)")
        void upstreamExamples() {
            assertEquals(new IntegerRange(2000, 0), IntegerRange.parse("scan_range", "2000 0"));
            assertEquals("0 2", IntegerRange.parse("precursor_charge", "0   2").text());
            assertEquals(
                    "112.5 121.5", DecimalRange.parse("clear_mz_range", "112.5\t121.5").text());
        }

        @Test
        @DisplayName("one value, three values and non-numbers are refused, naming the field")
        void refusals() {
            ValueSyntaxException one =
                    assertThrows(
                            ValueSyntaxException.class,
                            () -> IntegerRange.parse("peptide_length_range", "5"));
            assertEquals(
                    "peptide_length_range, the range: \"5\" holds 1 values, not two",
                    one.getMessage());
            assertThrows(
                    ValueSyntaxException.class, () -> IntegerRange.parse("scan_range", "1 2 3"));
            assertThrows(
                    ValueSyntaxException.class, () -> DecimalRange.parse("clear_mz_range", ""));
            assertEquals(
                    "first value",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> IntegerRange.parse("scan_range", "a 2"))
                            .field());
            assertEquals(
                    "second value",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> IntegerRange.parse("scan_range", "1 2.5"))
                            .field());
            assertEquals(
                    "first value",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> DecimalRange.parse("digest_mass_range", "x 2.0"))
                            .field());
            assertEquals(
                    "second value",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> DecimalRange.parse("digest_mass_range", "1.0 y"))
                            .field());
        }
    }

    @Nested
    @DisplayName("mass_offsets, a list")
    class OffsetList {

        @Test
        @DisplayName("the real empty default is an empty list, written back empty")
        void theRealDefaultIsEmpty() {
            assertEquals(List.of("mass_offsets"), namesOfKind(ValueKind.DECIMAL_LIST));
            assertEquals("", REAL.get("mass_offsets"));
            DecimalList empty = DecimalList.parse("mass_offsets", REAL.get("mass_offsets"));
            assertEquals(List.of(), empty.values());
            assertEquals("", empty.text());
        }

        @Test
        @DisplayName("values keep their order and scale (CONSTRUCTED from upstream docs)")
        void valuesKeepOrderAndScale() {
            DecimalList list = DecimalList.parse("mass_offsets", "42.0123 0.0  48.3812\t82.030");
            assertEquals(
                    List.of(
                            new BigDecimal("42.0123"),
                            new BigDecimal("0.0"),
                            new BigDecimal("48.3812"),
                            new BigDecimal("82.030")),
                    list.values());
            assertEquals("42.0123 0.0 48.3812 82.030", list.text());
            List<BigDecimal> mutable = new ArrayList<>(List.of(BigDecimal.ONE));
            DecimalList copy = new DecimalList(mutable);
            mutable.add(BigDecimal.TEN);
            assertEquals(List.of(BigDecimal.ONE), copy.values());
        }

        @Test
        @DisplayName("a token that is not a number is refused, naming its position")
        void aNonNumberIsRefused() {
            ValueSyntaxException failure =
                    assertThrows(
                            ValueSyntaxException.class,
                            () -> DecimalList.parse("mass_offsets", "42.0 abc"));
            assertEquals("mass_offsets, value 2: \"abc\" is not a number", failure.getMessage());
        }
    }

    @Nested
    @DisplayName("the ion-series family")
    class Ions {

        @Test
        @DisplayName("is exactly the metadata's ION_SERIES_FLAG parameters, in -q order")
        void isTheMetadatasFamily() {
            List<String> modelled = new ArrayList<>();
            for (IonSeries series : IonSeries.values()) {
                modelled.add(series.parameter());
            }
            assertEquals(namesOfKind(ValueKind.ION_SERIES_FLAG), modelled);
            assertEquals(
                    ValueKind.BOOLEAN_FLAG,
                    METADATA.parameter(IonSeriesSelection.NEUTRAL_LOSS_PARAMETER)
                            .orElseThrow()
                            .kind());
        }

        @Test
        @DisplayName("reads the real defaults as b and y, and writes them back in -q order")
        void readsTheRealDefaults() {
            IonSeriesSelection selection = IonSeriesSelection.parse(REAL);
            assertEquals(EnumSet.of(IonSeries.B, IonSeries.Y), selection.series());
            assertFalse(selection.neutralLossPeaks());
            Map<String, String> written = selection.format();
            List<String> order = new ArrayList<>(written.keySet());
            assertEquals(
                    List.of(
                            "use_A_ions",
                            "use_B_ions",
                            "use_C_ions",
                            "use_X_ions",
                            "use_Y_ions",
                            "use_Z_ions",
                            "use_Z1_ions",
                            "use_NL_ions"),
                    order);
            for (Map.Entry<String, String> entry : written.entrySet()) {
                assertEquals(REAL.get(entry.getKey()), entry.getValue(), entry.getKey());
            }
        }

        @Test
        @DisplayName("every series and the loss flag switch independently (CONSTRUCTED)")
        void everyFlagSwitches() {
            for (IonSeries only : IonSeries.values()) {
                Map<String, String> values = new LinkedHashMap<>(REAL);
                for (IonSeries series : IonSeries.values()) {
                    values.put(series.parameter(), series == only ? "1" : "0");
                }
                values.put(IonSeriesSelection.NEUTRAL_LOSS_PARAMETER, "1");
                IonSeriesSelection selection = IonSeriesSelection.parse(values);
                assertEquals(Set.of(only), selection.series());
                assertTrue(selection.neutralLossPeaks());
                assertEquals("1", selection.format().get(only.parameter()));
                assertEquals(
                        "1", selection.format().get(IonSeriesSelection.NEUTRAL_LOSS_PARAMETER));
                assertEquals(selection, IonSeriesSelection.parse(selection.format()));
            }
        }

        @Test
        @DisplayName("a missing member or a value other than 0 or 1 is refused, naming it")
        void refusals() {
            Map<String, String> missing = new LinkedHashMap<>(REAL);
            missing.remove("use_Z1_ions");
            ValueSyntaxException absent =
                    assertThrows(
                            ValueSyntaxException.class, () -> IonSeriesSelection.parse(missing));
            assertEquals("use_Z1_ions", absent.subject());
            Map<String, String> two = new LinkedHashMap<>(REAL);
            two.put("use_NL_ions", "2");
            ValueSyntaxException bad =
                    assertThrows(ValueSyntaxException.class, () -> IonSeriesSelection.parse(two));
            assertEquals("use_NL_ions, value: \"2\" is not 0 or 1", bad.getMessage());
            Map<String, String> spaced = new LinkedHashMap<>(REAL);
            spaced.put("use_A_ions", " 1 ");
            assertTrue(IonSeriesSelection.parse(spaced).series().contains(IonSeries.A));
        }

        @Test
        @DisplayName("the selection is an immutable copy")
        void immutable() {
            Set<IonSeries> mutable = EnumSet.of(IonSeries.B);
            IonSeriesSelection selection = new IonSeriesSelection(mutable, false);
            mutable.add(IonSeries.Y);
            assertEquals(Set.of(IonSeries.B), selection.series());
            assertThrows(
                    UnsupportedOperationException.class, () -> selection.series().add(IonSeries.A));
            assertThrows(
                    UnsupportedOperationException.class, () -> selection.format().put("x", "1"));
            assertEquals(Set.of(), new IonSeriesSelection(Set.of(), true).series());
        }
    }
}
