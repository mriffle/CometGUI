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

package org.cometgui.params.comet.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.DeclarationLines;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The line classifier, over Comet's real output and over CONSTRUCTED lines that each exercise one
 * rule. Constructed lines are written inline and are never presented as Comet's output.
 */
class ParamsLineReaderTest {

    private static String fixture(CometFixtures.Mode mode) throws IOException {
        return new String(
                CometFixtures.bytes(
                        CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64, mode),
                StandardCharsets.UTF_8);
    }

    private static ParamsLine only(String constructed) {
        List<ParamsLine> lines = ParamsLineReader.read(constructed).lines();
        assertEquals(1, lines.size(), lines::toString);
        return lines.get(0);
    }

    @Nested
    @DisplayName("over Comet 2026.02.2's real -q output")
    class RealOutput {

        @Test
        @DisplayName("every line is kept, in order, and none is malformed")
        void everyLineIsKept() throws IOException {
            String text = fixture(CometFixtures.Mode.COMPLETE);
            ParamsText parsed = ParamsLineReader.read(text);
            List<String> raw = text.lines().toList();
            assertEquals(raw.size(), parsed.lines().size());
            for (int index = 0; index < raw.size(); index++) {
                assertEquals(raw.get(index), parsed.lines().get(index).text());
                assertEquals(index + 1, parsed.lines().get(index).number());
            }
            assertEquals(List.of(), parsed.malformed());
            assertTrue(parsed.endsWithNewline());
        }

        @Test
        @DisplayName("118 declarations, the same names the independent test-side rule counts")
        void declarationsMatchTheIndependentCount() throws IOException {
            String text = fixture(CometFixtures.Mode.COMPLETE);
            List<String> names =
                    ParamsLineReader.read(text).declarations().stream()
                            .map(ParamsLine.Declaration::name)
                            .toList();
            assertEquals(118, names.size());
            assertEquals(DeclarationLines.names(text.lines().toList()), names);
        }

        @Test
        @DisplayName("96 declarations in the -p output")
        void defaultsDeclare96() throws IOException {
            assertEquals(
                    96,
                    ParamsLineReader.read(fixture(CometFixtures.Mode.DEFAULTS))
                            .declarations()
                            .size());
        }

        @Test
        @DisplayName("the marker, the header and the twelve enzyme rows are where Comet put them")
        void markerHeaderAndRows() throws IOException {
            ParamsText parsed = ParamsLineReader.read(fixture(CometFixtures.Mode.COMPLETE));
            ParamsLine first = parsed.lines().get(0);
            assertInstanceOf(ParamsLine.VersionMarker.class, first);
            assertEquals("# comet_version 2026.02 rev. 2 (6edec91)", first.text());
            long headers =
                    parsed.lines().stream()
                            .filter(ParamsLine.EnzymeHeader.class::isInstance)
                            .count();
            assertEquals(1, headers);
            List<ParamsLine.EnzymeRow> rows = parsed.enzymeRows();
            assertEquals(12, rows.size());
            assertEquals("0.  Cut_everywhere         0      -           -", rows.get(0).text());
            assertEquals("11. No_cut                 1      @           @", rows.get(11).text());
        }

        @Test
        @DisplayName("values, empty values and inline comments are separated as written")
        void valuesAndComments() throws IOException {
            List<ParamsLine.Declaration> declarations =
                    ParamsLineReader.read(fixture(CometFixtures.Mode.COMPLETE)).declarations();
            ParamsLine.Declaration decoy = find(declarations, "decoy_search");
            assertEquals("0", decoy.value());
            assertEquals(
                    Optional.of(
                            "0=no (default), 1=internal decoy concatenated, 2=internal decoy"
                                    + " separate"),
                    decoy.inlineComment());
            ParamsLine.Declaration obo = find(declarations, "peff_obo");
            assertEquals("", obo.value());
            assertEquals(Optional.of("path to PSI Mod or Unimod OBO file"), obo.inlineComment());
            ParamsLine.Declaration mod = find(declarations, "variable_mod01");
            assertEquals("15.9949 M 0 3 -1 0 0 0.0", mod.value());
            assertEquals(Optional.empty(), mod.inlineComment());
            assertEquals("5 50", find(declarations, "peptide_length_range").value());
        }

        @Test
        @DisplayName("the indented continuation comment under sample_enzyme_number is a comment")
        void theIndentedCommentIsAComment() throws IOException {
            ParamsText parsed = ParamsLineReader.read(fixture(CometFixtures.Mode.COMPLETE));
            ParamsLine.Declaration sample = find(parsed.declarations(), "sample_enzyme_number");
            ParamsLine next = parsed.lines().get(sample.number());
            assertInstanceOf(ParamsLine.Comment.class, next);
            assertTrue(next.text().startsWith("      "), next.text());
        }

        private ParamsLine.Declaration find(List<ParamsLine.Declaration> all, String name) {
            return all.stream()
                    .filter(d -> d.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no " + name));
        }
    }

    @Nested
    @DisplayName("over constructed lines, one rule each")
    class Constructed {

        @Test
        void aDeclarationWithACommentIsSplit() {
            ParamsLine.Declaration line =
                    assertInstanceOf(
                            ParamsLine.Declaration.class, only("  num_threads =  8   # cores"));
            assertEquals("num_threads", line.name());
            assertEquals("8", line.value());
            assertEquals(Optional.of("cores"), line.inlineComment());
            assertEquals("  num_threads =  8   # cores", line.text());
        }

        @Test
        void aBareHashGivesAnEmptyCommentNotNoComment() {
            ParamsLine.Declaration line =
                    assertInstanceOf(ParamsLine.Declaration.class, only("decoy_prefix = DECOY_ #"));
            assertEquals(Optional.of(""), line.inlineComment());
        }

        @Test
        void anEmptyValueIsAValue() {
            ParamsLine.Declaration line =
                    assertInstanceOf(ParamsLine.Declaration.class, only("peff_obo ="));
            assertEquals("", line.value());
            assertEquals(Optional.empty(), line.inlineComment());
        }

        @Test
        void aValueMayContainAnEqualsSign() {
            ParamsLine.Declaration line =
                    assertInstanceOf(ParamsLine.Declaration.class, only("decoy_prefix = a=b"));
            assertEquals("a=b", line.value());
        }

        @Test
        void aNameWithASpaceIsMalformed() {
            ParamsLine.Malformed line =
                    assertInstanceOf(ParamsLine.Malformed.class, only("num threads = 8"));
            assertTrue(line.reason().contains("\"num threads\""), line.reason());
            assertEquals(1, line.number());
        }

        @Test
        void anEmptyNameIsMalformed() {
            for (String constructed : new String[] {" = 8", "= 8"}) {
                ParamsLine.Malformed line =
                        assertInstanceOf(ParamsLine.Malformed.class, only(constructed));
                assertTrue(line.reason().contains("is not a parameter name"), line.reason());
            }
        }

        @Test
        void aCommentStraightAfterTheEqualsSignLeavesAnEmptyValue() {
            ParamsLine.Declaration line =
                    assertInstanceOf(ParamsLine.Declaration.class, only("peff_obo =# none"));
            assertEquals("", line.value());
            assertEquals(Optional.of("none"), line.inlineComment());
        }

        @Test
        void leadingBlankLinesDoNotEndTheText() {
            ParamsText parsed = ParamsLineReader.read("\n\na = 1\n");
            assertEquals(3, parsed.lines().size());
            assertEquals("a", parsed.declarations().get(0).name());
            assertEquals(3, parsed.declarations().get(0).number());
        }

        @Test
        void aNameStartingWithADigitIsMalformed() {
            assertInstanceOf(ParamsLine.Malformed.class, only("1st = 8"));
        }

        @Test
        void textWithoutAnEqualsSignIsMalformed() {
            ParamsLine.Malformed line =
                    assertInstanceOf(ParamsLine.Malformed.class, only("num_threads 8"));
            assertTrue(line.reason().contains("no '='"), line.reason());
        }

        @Test
        void aHashBeforeTheEqualsSignIsMalformed() {
            ParamsLine.Malformed line =
                    assertInstanceOf(ParamsLine.Malformed.class, only("num_threads # = 8"));
            assertTrue(line.reason().contains("no '=' before any '#'"), line.reason());
        }

        @Test
        void aCommentMayBeIndented() {
            assertInstanceOf(ParamsLine.Comment.class, only("   # indented"));
            assertInstanceOf(ParamsLine.Comment.class, only("#"));
        }

        @Test
        void aMarkerIsNotAPlainComment() {
            assertInstanceOf(
                    ParamsLine.VersionMarker.class, only("# comet_version 2026.02 rev. 2"));
            assertInstanceOf(ParamsLine.Comment.class, only("#comet_version 2026.02 rev. 2"));
            assertInstanceOf(ParamsLine.Comment.class, only(" # comet_version 2026.02 rev. 2"));
        }

        @Test
        void whiteSpaceOnlyIsBlank() {
            assertInstanceOf(ParamsLine.Blank.class, only(" \t"));
            ParamsText parsed = ParamsLineReader.read("\n");
            assertEquals(1, parsed.lines().size());
            assertInstanceOf(ParamsLine.Blank.class, parsed.lines().get(0));
            assertEquals("", parsed.lines().get(0).text());
        }

        @Test
        void emptyTextHasNoLines() {
            ParamsText parsed = ParamsLineReader.read("");
            assertEquals(List.of(), parsed.lines());
            assertFalse(parsed.endsWithNewline());
        }

        @Test
        void aMissingFinalNewlineIsRecorded() {
            ParamsText parsed = ParamsLineReader.read("a = 1\nb = 2");
            assertEquals(2, parsed.declarations().size());
            assertFalse(parsed.endsWithNewline());
            assertEquals("b", parsed.declarations().get(1).name());
            assertEquals(2, parsed.declarations().get(1).number());
        }

        @Test
        void aCarriageReturnIsKeptInTheTextAndIgnoredInTheShape() {
            ParamsText parsed =
                    ParamsLineReader.read(
                            "# comet_version 2026.02 rev. 2\r\n"
                                    + "num_threads = 8\r\n\r\n"
                                    + "[COMET_ENZYME_INFO]\r\n");
            List<ParamsLine> lines = parsed.lines();
            assertInstanceOf(ParamsLine.VersionMarker.class, lines.get(0));
            ParamsLine.Declaration declaration =
                    assertInstanceOf(ParamsLine.Declaration.class, lines.get(1));
            assertEquals("8", declaration.value());
            assertEquals("num_threads = 8\r", declaration.text());
            assertInstanceOf(ParamsLine.Blank.class, lines.get(2));
            assertInstanceOf(ParamsLine.EnzymeHeader.class, lines.get(3));
        }

        @Test
        void theTableHeaderMayHaveTrailingBlanksButNotLeadingOnes() {
            assertInstanceOf(ParamsLine.EnzymeHeader.class, only("[COMET_ENZYME_INFO]  "));
            assertInstanceOf(ParamsLine.Malformed.class, only(" [COMET_ENZYME_INFO]"));
        }

        @Test
        void afterTheHeaderOnlyRowsCommentsAndBlanksAreAccepted() {
            String constructed =
                    "[COMET_ENZYME_INFO]\n"
                            + "0.  Cut_everywhere 0 - -\n"
                            + "# a comment\n"
                            + "\n"
                            + "12. Custom\t1\tKR\tP\n"
                            + "num_threads = 8\n"
                            + "13. Short 1 K\n"
                            + "[COMET_ENZYME_INFO]\n"
                            + "x. Bad 1 K P\n";
            List<ParamsLine> lines = ParamsLineReader.read(constructed).lines();
            assertInstanceOf(ParamsLine.EnzymeHeader.class, lines.get(0));
            assertInstanceOf(ParamsLine.EnzymeRow.class, lines.get(1));
            assertInstanceOf(ParamsLine.Comment.class, lines.get(2));
            assertInstanceOf(ParamsLine.Blank.class, lines.get(3));
            assertInstanceOf(ParamsLine.EnzymeRow.class, lines.get(4));
            ParamsLine.Malformed declaration =
                    assertInstanceOf(ParamsLine.Malformed.class, lines.get(5));
            assertTrue(declaration.reason().contains("Comet reads no parameter declared here"));
            assertEquals(6, declaration.number());
            assertInstanceOf(ParamsLine.Malformed.class, lines.get(6));
            ParamsLine.Malformed second =
                    assertInstanceOf(ParamsLine.Malformed.class, lines.get(7));
            assertTrue(second.reason().contains("a second [COMET_ENZYME_INFO]"), second.reason());
            assertInstanceOf(ParamsLine.Malformed.class, lines.get(8));
        }

        @Test
        void aDeclarationBeforeTheHeaderIsADeclaration() {
            List<ParamsLine> lines =
                    ParamsLineReader.read("num_threads = 8\n[COMET_ENZYME_INFO]\n").lines();
            assertInstanceOf(ParamsLine.Declaration.class, lines.get(0));
        }

        @Test
        void theViewsFilterByShapeAndAreImmutable() {
            ParamsText parsed =
                    ParamsLineReader.read("a = 1\nbad\n[COMET_ENZYME_INFO]\n1. T 1 K P\n");
            assertEquals(1, parsed.declarations().size());
            assertEquals(1, parsed.malformed().size());
            assertEquals(1, parsed.enzymeRows().size());
            assertEquals(4, parsed.lines().size());
            assertThrows(UnsupportedOperationException.class, () -> parsed.lines().clear());
        }

        @Test
        void nullIsRefused() {
            assertThrows(NullPointerException.class, () -> ParamsLineReader.read(null));
        }
    }
}
