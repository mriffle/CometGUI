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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code R-PARAM-05}: the parser preserves the imported file's comment structure -- the marker
 * line, the block comments between parameters, inline trailing comments, and the comments around
 * the enzyme table -- over the REAL {@code -q} output of Comet 2026.02.2 and over a CONSTRUCTED
 * file.
 */
class ImportedCommentsTest {

    private static final CometParamsParser PARSER =
            new CometParamsParser(ParamsFiles.metadata(), ParamsFiles.COMET);

    @Nested
    @DisplayName("the real -q output")
    class Real {

        private final ImportedComments comments = PARSER.parse(ParamsFiles.complete()).comments();

        @Test
        @DisplayName("the marker line, and the header block above the first parameter")
        void markerAndHeader() {
            assertEquals(
                    Optional.of("# comet_version 2026.02 rev. 2 (6edec91)"), comments.marker());
            assertEquals(
                    List.of(
                            "# Comet MS/MS search engine parameters file.",
                            "# Everything following the '#' symbol is treated as a comment.",
                            "#"),
                    comments.of("database_name").orElseThrow().above());
        }

        @Test
        @DisplayName("Comet's '# search enzyme' section header belongs to search_enzyme_number")
        void sectionHeader() {
            ImportedComments.ParameterComments enzyme =
                    comments.of("search_enzyme_number").orElseThrow();
            assertEquals(List.of("", "#", "# search enzyme", "#"), enzyme.above());
            assertEquals(
                    Optional.of("choose from list at end of this params file"), enzyme.inline());
            assertEquals(List.of(), enzyme.continuation());
            assertEquals(List.of(), comments.of("search_enzyme2_number").orElseThrow().above());
        }

        @Test
        @DisplayName("the indented line under sample_enzyme_number continues its comment")
        void continuation() {
            ImportedComments.ParameterComments sample =
                    comments.of("sample_enzyme_number").orElseThrow();
            assertEquals(
                    Optional.of(
                            "specifies the sample enzyme which is possibly different than the one"
                                    + " applied to the search;"),
                    sample.inline());
            assertEquals(
                    List.of(
                            " ".repeat(39)
                                    + "# used by PeptideProphet to calculate NTT & NMC in pepXML"
                                    + " output (default=1 for trypsin)."),
                    sample.continuation());
            assertEquals(List.of(), comments.of("num_enzyme_termini").orElseThrow().above());
        }

        @Test
        @DisplayName("the variable-modification block and a parameter with no inline comment")
        void variableModBlock() {
            ImportedComments.ParameterComments first = comments.of("variable_mod01").orElseThrow();
            assertEquals(6, first.above().size());
            assertEquals(
                    "# Up to 15 variable_mod entries are supported for a standard search; manually"
                            + " add additional entries as needed",
                    first.above().get(2));
            assertEquals(Optional.empty(), first.inline());
            assertEquals(Optional.empty(), comments.of("use_A_ions").orElseThrow().inline());
        }

        @Test
        @DisplayName("the comments before the enzyme table, and the blank line after it")
        void enzymeTable() {
            assertEquals(
                    List.of(
                            "",
                            "#",
                            "# COMET_ENZYME_INFO _must_ be at the end of this parameters file",
                            "# Enzyme entries can be added/deleted/edited",
                            "#"),
                    comments.beforeEnzymeTable());
            assertEquals(List.of(""), comments.inEnzymeTable());
        }

        @Test
        @DisplayName("every comment and blank line of the file is kept somewhere, none twice")
        void nothingDropped() {
            List<String> expected = new ArrayList<>();
            for (ParamsLine line : ParamsLineReader.read(ParamsFiles.complete()).lines()) {
                if (line instanceof ParamsLine.Comment || line instanceof ParamsLine.Blank) {
                    expected.add(line.text());
                }
            }
            List<String> kept = new ArrayList<>();
            for (ImportedComments.ParameterComments each : comments.parameters().values()) {
                kept.addAll(each.above());
                kept.addAll(each.continuation());
            }
            kept.addAll(comments.beforeEnzymeTable());
            kept.addAll(comments.inEnzymeTable());
            assertEquals(expected, kept);
            assertEquals(118, comments.parameters().size());
            long inline =
                    comments.parameters().values().stream()
                            .filter(c -> c.inline().isPresent())
                            .count();
            assertEquals(
                    ParamsLineReader.read(ParamsFiles.complete()).declarations().stream()
                            .filter(d -> d.inlineComment().isPresent())
                            .count(),
                    inline);
        }
    }

    @Test
    @DisplayName("CONSTRUCTED: comments come back verbatim, CRLF removed, in table and out")
    void constructedVerbatim() {
        String text =
                "# my own header\r\n"
                        + "# comet_version 2026.02 rev. 2 (6edec91)\r\n"
                        + "\r\n"
                        + "#   indented note about threads  \r\n"
                        + "num_threads = 4   #  four, please \r\n"
                        + "    # continues the threads comment\r\n"
                        + "# about the prefix\r\n"
                        + "decoy_prefix = REV_ #\r\n"
                        + "[COMET_ENZYME_INFO]\r\n"
                        + "# inside the table\r\n"
                        + "1.  Trypsin                1      KR          P\r\n"
                        + "\r\n"
                        + "# at the very end\r\n";
        ParseResult result = PARSER.parse(text);
        ImportedComments comments = result.comments();
        assertTrue(result.succeeded(), result.diagnostics()::toString);
        assertEquals(Optional.of("# comet_version 2026.02 rev. 2 (6edec91)"), comments.marker());
        ImportedComments.ParameterComments threads = comments.of("num_threads").orElseThrow();
        assertEquals(
                List.of("# my own header", "", "#   indented note about threads  "),
                threads.above());
        assertEquals(Optional.of("four, please"), threads.inline());
        assertEquals(List.of("    # continues the threads comment"), threads.continuation());
        ImportedComments.ParameterComments prefix = comments.of("decoy_prefix").orElseThrow();
        assertEquals(List.of("# about the prefix"), prefix.above());
        assertEquals(Optional.of(""), prefix.inline());
        assertEquals(List.of(), comments.beforeEnzymeTable());
        assertEquals(
                List.of("# inside the table", "", "# at the very end"), comments.inEnzymeTable());
        assertEquals(
                List.of("num_threads", "decoy_prefix"),
                List.copyOf(comments.parameters().keySet()));
        assertEquals(Optional.empty(), comments.of("database_name"));
    }

    @Test
    @DisplayName("CONSTRUCTED: an unindented comment under a declaration is the next one's block")
    void unindentedIsNotContinuation() {
        String text =
                "# comet_version 2026.02 rev. 2 (6edec91)\n"
                        + "num_threads = 4\n"
                        + "# about the prefix\n"
                        + "decoy_prefix = REV_\n"
                        + "[COMET_ENZYME_INFO]\n"
                        + "1.  Trypsin                1      KR          P\n";
        ImportedComments comments = PARSER.parse(text).comments();
        assertEquals(List.of(), comments.of("num_threads").orElseThrow().continuation());
        assertEquals(
                List.of("# about the prefix"), comments.of("decoy_prefix").orElseThrow().above());
    }

    @Test
    @DisplayName("CONSTRUCTED: a failed parse still reports the comments it read")
    void failedParseKeepsComments() {
        String text = ParamsFiles.completeReplacing("num_threads", "num_threads = many   # oops");
        ParseResult result = PARSER.parse(text);
        assertFalse(result.succeeded());
        assertEquals(
                Optional.of("oops"), result.comments().of("num_threads").orElseThrow().inline());
        assertEquals(
                Optional.of("# comet_version 2026.02 rev. 2 (6edec91)"),
                result.comments().marker());
    }

    @Test
    @DisplayName("CONSTRUCTED: a comment above the marker is not an unknown parameter's comment")
    void markerEndsAnUnknownsComments() {
        String text =
                "# above the marker\n"
                        + "# comet_version 2026.02 rev. 2 (6edec91)\n"
                        + "some_knob = 1\n"
                        + "[COMET_ENZYME_INFO]\n"
                        + "1.  Trypsin                1      KR          P\n";
        ParseResult result = PARSER.parse(text);
        assertEquals(List.of(), result.model().orElseThrow().unknownParameters().get(0).comments());
    }

    @Test
    @DisplayName("CONSTRUCTED: no marker, no marker line; unknown parameters are not listed here")
    void noMarkerAndUnknown() {
        String text =
                "num_threads = 4\n"
                        + "# about the knob\n"
                        + "some_knob = 1 # knob\n"
                        + "[COMET_ENZYME_INFO]\n"
                        + "1.  Trypsin                1      KR          P\n";
        ParseResult result = PARSER.parse(text);
        assertEquals(Optional.empty(), result.comments().marker());
        assertEquals(Optional.empty(), result.comments().of("some_knob"));
        assertEquals(
                List.of("# about the knob"),
                result.model().orElseThrow().unknownParameters().get(0).comments());
    }
}
