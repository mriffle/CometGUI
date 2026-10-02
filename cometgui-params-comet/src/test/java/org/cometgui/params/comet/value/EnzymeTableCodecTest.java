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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.parser.ParamsLine;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.parser.ParamsText;
import org.cometgui.params.comet.schema.Choice;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Gate item 4, table half: the real table round-trips, a custom enzyme survives, no duplicates. */
class EnzymeTableCodecTest {

    private static ParamsText realDump() throws IOException {
        return ParamsLineReader.read(
                new String(
                        CometFixtures.bytes(
                                CometFixtures.COMET_2026_02_2,
                                CometFixtures.LINUX_X86_64,
                                CometFixtures.Mode.COMPLETE),
                        StandardCharsets.UTF_8));
    }

    private static EnzymeTable realTable() throws IOException {
        return EnzymeTableCodec.parse(realDump().enzymeRows());
    }

    private static List<ParamsLine.EnzymeRow> rows(String... lines) {
        String text = "[COMET_ENZYME_INFO]\n" + String.join("\n", lines) + "\n";
        return ParamsLineReader.read(text).enzymeRows();
    }

    @Nested
    @DisplayName("the real comet -q table")
    class Real {

        @Test
        @DisplayName("reads as twelve typed rows")
        void readsAsTypedRows() throws IOException {
            EnzymeTable table = realTable();
            assertEquals(12, table.rows().size());
            List<Integer> numbers = new ArrayList<>();
            table.rows().forEach(row -> numbers.add(row.number()));
            assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), numbers);
            assertEquals(
                    new EnzymeDefinition(
                            0, "Cut_everywhere", EnzymeDefinition.Sense.BEFORE_RESIDUE, "", ""),
                    table.byNumber(0).orElseThrow());
            assertTrue(table.byNumber(0).orElseThrow().nonSpecific());
            assertEquals(
                    new EnzymeDefinition(
                            1, "Trypsin", EnzymeDefinition.Sense.AFTER_RESIDUE, "KR", "P"),
                    table.byNumber(1).orElseThrow());
            assertFalse(table.byNumber(1).orElseThrow().nonSpecific());
            assertEquals(
                    new EnzymeDefinition(
                            2, "Trypsin/P", EnzymeDefinition.Sense.AFTER_RESIDUE, "KR", ""),
                    table.byNumber(2).orElseThrow());
            assertEquals(
                    new EnzymeDefinition(
                            4, "Lys_N", EnzymeDefinition.Sense.BEFORE_RESIDUE, "K", ""),
                    table.byNumber(4).orElseThrow());
            assertEquals(
                    new EnzymeDefinition(
                            10, "Chymotrypsin", EnzymeDefinition.Sense.AFTER_RESIDUE, "FWYL", "P"),
                    table.byNumber(10).orElseThrow());
            assertEquals(
                    new EnzymeDefinition(
                            11, "No_cut", EnzymeDefinition.Sense.AFTER_RESIDUE, "@", "@"),
                    table.byNumber(11).orElseThrow());
            assertFalse(table.byNumber(11).orElseThrow().nonSpecific());
            assertEquals(Optional.empty(), table.byNumber(12));
            assertTrue(table.contains(11));
            assertFalse(table.contains(12));
            assertFalse(table.contains(-1));
        }

        @Test
        @DisplayName("writes back byte for byte as Comet wrote it")
        void writesBackByteForByte() throws IOException {
            List<String> written = realDump().enzymeRows().stream().map(ParamsLine::text).toList();
            assertEquals(12, written.size());
            assertEquals(written, EnzymeTableCodec.format(realTable()));
            assertEquals("10. Chymotrypsin           1      FWYL        P", written.get(10));
        }

        @Test
        @DisplayName("and a second round trip is identical")
        void aSecondRoundTripIsIdentical() throws IOException {
            List<String> once = EnzymeTableCodec.format(realTable());
            List<String> twice =
                    EnzymeTableCodec.format(
                            EnzymeTableCodec.parse(rows(once.toArray(new String[0]))));
            assertEquals(once, twice);
        }

        @Test
        @DisplayName("the sense values are the ones the metadata labels")
        void senseValuesAreTheMetadatas() {
            List<String> curated =
                    MetadataLoader.loadBundled().enzymeTable().senseChoices().stream()
                            .map(Choice::value)
                            .toList();
            List<String> modelled = new ArrayList<>();
            for (EnzymeDefinition.Sense sense : EnzymeDefinition.Sense.values()) {
                modelled.add(Integer.toString(sense.code()));
                assertEquals(Optional.of(sense), EnzymeDefinition.Sense.fromCode(sense.code()));
            }
            assertEquals(curated, modelled);
            assertEquals(Optional.empty(), EnzymeDefinition.Sense.fromCode(2));
        }
    }

    @Nested
    @DisplayName("a custom enzyme (CONSTRUCTED rows)")
    class Custom {

        private final EnzymeDefinition gluC =
                new EnzymeDefinition(12, "Glu_C", EnzymeDefinition.Sense.AFTER_RESIDUE, "DE", "P");

        @Test
        @DisplayName("is added, written in Comet's columns, and survives a round trip")
        void survives() throws IOException {
            EnzymeTable table = realTable().with(gluC);
            List<String> lines = EnzymeTableCodec.format(table);
            assertEquals(13, lines.size());
            assertEquals("12. Glu_C                  1      DE          P", lines.get(12));
            EnzymeTable again = EnzymeTableCodec.parse(rows(lines.toArray(new String[0])));
            assertEquals(table, again);
            assertEquals(Optional.of(gluC), again.byNumber(12));
            assertEquals(lines, EnzymeTableCodec.format(again));
        }

        @Test
        @DisplayName("a name or residues wider than their column keep one space")
        void wideFieldsKeepOneSpace() {
            EnzymeDefinition wide =
                    new EnzymeDefinition(
                            1234,
                            "Very_long_custom_enzyme_name",
                            EnzymeDefinition.Sense.BEFORE_RESIDUE,
                            "ACDEFGHIKLMNPQ",
                            "");
            String line = EnzymeTableCodec.formatRow(wide);
            assertEquals("1234. Very_long_custom_enzyme_name 0      ACDEFGHIKLMNPQ -", line);
            assertEquals(wide, EnzymeTableCodec.parseRow(line));
        }

        @Test
        @DisplayName("can be removed again")
        void canBeRemoved() throws IOException {
            EnzymeTable table = realTable().with(gluC).without(12);
            assertEquals(realTable(), table);
            assertEquals(11, table.without(0).rows().size());
            assertFalse(table.without(0).contains(0));
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> assertEquals(List.of(), table.without(12).rows()));
            assertEquals("no enzyme row has number 12", failure.getMessage());
        }

        @Test
        @DisplayName("may not reuse a number")
        void mayNotReuseANumber() throws IOException {
            EnzymeDefinition clash =
                    new EnzymeDefinition(
                            3, "MyLysC", EnzymeDefinition.Sense.AFTER_RESIDUE, "K", "");
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> assertEquals(List.of(), realTable().with(clash).rows()));
            assertEquals(
                    "enzyme number 3 is defined twice, as Lys_C and as MyLysC",
                    failure.getMessage());
        }
    }

    @Nested
    @DisplayName("what is not a table is refused (CONSTRUCTED rows)")
    class Refusals {

        @Test
        @DisplayName("a table that defines a number twice, naming the line")
        void aDuplicateNumber() {
            ValueSyntaxException failure =
                    assertThrows(
                            ValueSyntaxException.class,
                            () ->
                                    EnzymeTableCodec.parse(
                                            rows(
                                                    "0.  Cut_everywhere 0 - -",
                                                    "1.  Trypsin 1 KR P",
                                                    "1.  Trypsin/P 1 KR -")));
            assertEquals("enzyme row at line 4", failure.subject());
            assertEquals("number", failure.field());
            assertEquals(
                    "enzyme row at line 4, number: enzyme number 1 is already defined, as"
                            + " Trypsin; Comet would silently use the later row",
                    failure.getMessage());
        }

        @Test
        void tooFewOrTooManyFields() {
            ValueSyntaxException four =
                    assertThrows(
                            ValueSyntaxException.class,
                            () -> EnzymeTableCodec.parseRow("1. Trypsin 1 KR"));
            assertEquals(
                    "enzyme row, the row: \"1. Trypsin 1 KR\" holds 4 fields; a row is number,"
                            + " name, sense, cut residues, no-cut residues",
                    four.getMessage());
            assertEquals(
                    "the row",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> EnzymeTableCodec.parseRow("1. Try psin 1 KR P"))
                            .field());
        }

        @Test
        void aNumberWithoutItsFullStop() {
            ValueSyntaxException failure =
                    assertThrows(
                            ValueSyntaxException.class,
                            () -> EnzymeTableCodec.parseRow("1 Trypsin 1 KR P"));
            assertEquals("number", failure.field());
            assertTrue(failure.getMessage().contains("not a number followed by a full stop"));
            assertThrows(
                    ValueSyntaxException.class, () -> EnzymeTableCodec.parseRow("-1. X 1 K P"));
            assertThrows(ValueSyntaxException.class, () -> EnzymeTableCodec.parseRow("a. X 1 K P"));
            assertEquals(
                    "number",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> EnzymeTableCodec.parseRow("99999999999. X 1 K P"))
                            .field());
        }

        @Test
        void aSenseThatIsNotZeroOrOne() {
            ValueSyntaxException two =
                    assertThrows(
                            ValueSyntaxException.class,
                            () -> EnzymeTableCodec.parseRow("1. Trypsin 2 KR P"));
            assertEquals("sense", two.field());
            assertEquals(
                    "enzyme row, sense: \"2\" is not 0 (cleave before) or 1 (cleave after)",
                    two.getMessage());
            assertEquals(
                    "sense",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> EnzymeTableCodec.parseRow("1. Trypsin C KR P"))
                            .field());
        }

        @Test
        void leadingAndTrailingWhiteSpaceAndACarriageReturnAreIgnored() {
            assertEquals(
                    new EnzymeDefinition(
                            5, "Arg_C", EnzymeDefinition.Sense.AFTER_RESIDUE, "R", "P"),
                    EnzymeTableCodec.parseRow("  5.\tArg_C 1 R   P  \r"));
        }
    }

    @Nested
    @DisplayName("a row's own rules")
    class Row {

        @Test
        void theComponentsAreChecked() {
            EnzymeDefinition.Sense after = EnzymeDefinition.Sense.AFTER_RESIDUE;
            assertEquals(
                    "enzyme number -1 is negative",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new EnzymeDefinition(-1, "X", after, "K", ""))
                            .getMessage());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EnzymeDefinition(1, "", after, "K", ""));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EnzymeDefinition(1, "Lys C", after, "K", ""));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EnzymeDefinition(1, "LysC", after, "K R", ""));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EnzymeDefinition(1, "LysC", after, "K", "P\t"));
            IllegalArgumentException dash =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new EnzymeDefinition(1, "LysC", after, "K", "-"));
            assertTrue(dash.getMessage().contains("or empty for none"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EnzymeDefinition(1, "LysC", after, "-", ""));
            assertEquals(0, new EnzymeDefinition(0, "X", after, "", "").number());
            assertTrue(new EnzymeDefinition(0, "X", after, "", "").nonSpecific());
            assertFalse(new EnzymeDefinition(0, "X", after, "", "P").nonSpecific());
            assertFalse(new EnzymeDefinition(0, "X", after, "K", "").nonSpecific());
        }

        @Test
        void theRowsAreAnImmutableCopy() throws IOException {
            List<EnzymeDefinition> rows = new ArrayList<>(realTable().rows());
            EnzymeTable table = new EnzymeTable(rows);
            rows.clear();
            assertEquals(12, table.rows().size());
            assertThrows(UnsupportedOperationException.class, () -> table.rows().clear());
        }
    }
}
