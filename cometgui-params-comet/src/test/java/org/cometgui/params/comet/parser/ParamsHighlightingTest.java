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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.parser.ParamsHighlighting.Line;
import org.cometgui.params.comet.parser.ParamsHighlighting.LineKind;
import org.cometgui.params.comet.parser.ParamsHighlighting.Token;
import org.cometgui.params.comet.parser.ParamsHighlighting.TokenKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The Expert editor's line classes: every span typed by hand over CONSTRUCTED lines, one per kind,
 * and the bundled {@code comet -q} file of each offered release classified without a malformed
 * line.
 */
class ParamsHighlightingTest {

    private static final String CONSTRUCTED =
            "# comet_version 2026.03 rev. 0 (fa08489)\n"
                    + "# a comment\n"
                    + "\n"
                    + "  num_threads =  8   # threads\r\n"
                    + "peff_obo =\n"
                    + "decoy_prefix = DECOY_ #\n"
                    + "not a line\n"
                    + "[COMET_ENZYME_INFO]\n"
                    + "1.  Trypsin  1  KR  P\n"
                    + "x = 1";

    private static Token token(TokenKind kind, int start, int end) {
        return new Token(kind, start, end);
    }

    @Nested
    @DisplayName("over CONSTRUCTED lines, one of each kind")
    class Constructed {

        private final List<Line> lines = ParamsHighlighting.of(CONSTRUCTED);

        @Test
        @DisplayName("one line per line of the text, each of the reader's kind")
        void kinds() {
            assertEquals(
                    List.of(
                            LineKind.VERSION_MARKER,
                            LineKind.COMMENT,
                            LineKind.BLANK,
                            LineKind.DECLARATION,
                            LineKind.DECLARATION,
                            LineKind.DECLARATION,
                            LineKind.MALFORMED,
                            LineKind.ENZYME_HEADER,
                            LineKind.ENZYME_ROW,
                            LineKind.MALFORMED),
                    lines.stream().map(Line::kind).toList());
            assertEquals(
                    List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                    lines.stream().map(Line::number).toList());
            assertEquals("  num_threads =  8   # threads\r", lines.get(3).text());
        }

        @Test
        @DisplayName("whole-line kinds are one span over the line; a blank line has none")
        void wholeLines() {
            assertEquals(List.of(token(TokenKind.VERSION_MARKER, 0, 40)), lines.get(0).tokens());
            assertEquals(List.of(token(TokenKind.COMMENT, 0, 11)), lines.get(1).tokens());
            assertEquals(List.of(), lines.get(2).tokens());
            assertEquals(List.of(token(TokenKind.ENZYME_HEADER, 0, 19)), lines.get(7).tokens());
            assertEquals(List.of(token(TokenKind.ENZYME_ROW, 0, 21)), lines.get(8).tokens());
        }

        @Test
        @DisplayName("a declaration's name, value and inline comment, never the \\r")
        void declarations() {
            assertEquals(
                    List.of(
                            token(TokenKind.NAME, 2, 13),
                            token(TokenKind.VALUE, 17, 18),
                            token(TokenKind.INLINE_COMMENT, 21, 30)),
                    lines.get(3).tokens());
            Line threads = lines.get(3);
            assertEquals(
                    List.of("num_threads", "8", "# threads"),
                    threads.tokens().stream().map(threads::textOf).toList());
            // an empty value is no span
            assertEquals(List.of(token(TokenKind.NAME, 0, 8)), lines.get(4).tokens());
            assertEquals(
                    List.of(
                            token(TokenKind.NAME, 0, 12),
                            token(TokenKind.VALUE, 15, 21),
                            token(TokenKind.INLINE_COMMENT, 22, 23)),
                    lines.get(5).tokens());
            assertEquals(Optional.empty(), lines.get(5).problem());
        }

        @Test
        @DisplayName("a value that also ends the name is found after the '='")
        void valueRepeatsTheName() {
            Line line = ParamsHighlighting.of("va=a#a").get(0);
            assertEquals(
                    List.of(
                            token(TokenKind.NAME, 0, 2),
                            token(TokenKind.VALUE, 3, 4),
                            token(TokenKind.INLINE_COMMENT, 4, 6)),
                    line.tokens());
        }

        @Test
        @DisplayName("a malformed line is one span and carries the reader's reason")
        void malformed() {
            assertEquals(List.of(token(TokenKind.MALFORMED, 0, 10)), lines.get(6).tokens());
            assertEquals(
                    Optional.of(
                            "not a comment, a blank line or a declaration: there is no '=' before"
                                    + " any '#'"),
                    lines.get(6).problem());
            assertEquals(List.of(token(TokenKind.MALFORMED, 0, 5)), lines.get(9).tokens());
            assertEquals(
                    Optional.of(
                            "after [COMET_ENZYME_INFO] only enzyme rows (\"number. name sense cut"
                                    + " no-cut\"), comments and blank lines may follow; Comet reads"
                                    + " no parameter declared here"),
                    lines.get(9).problem());
        }

        @Test
        @DisplayName("an empty text has no line")
        void empty() {
            assertEquals(List.of(), ParamsHighlighting.of(""));
        }
    }

    @ParameterizedTest(name = "Comet {0}")
    @CsvSource({"2026.03.0, 205, 192", "2026.02.2, 199, 186"})
    @DisplayName("each release's own -q file: 118 declarations, nothing malformed")
    void bundled(String release, int lineCount, int enzymeHeader) {
        String text =
                new String(
                        ReleaseDefaults.bundledFile(ToolVersion.parse(release)),
                        StandardCharsets.UTF_8);
        List<Line> lines = ParamsHighlighting.of(text);
        assertEquals(lineCount, lines.size());
        assertEquals(LineKind.VERSION_MARKER, lines.get(0).kind());
        assertEquals(
                118, lines.stream().filter(line -> line.kind() == LineKind.DECLARATION).count());
        assertEquals(0, lines.stream().filter(line -> line.kind() == LineKind.MALFORMED).count());
        assertEquals(12, lines.stream().filter(l -> l.kind() == LineKind.ENZYME_ROW).count());
        assertEquals(LineKind.ENZYME_HEADER, lines.get(enzymeHeader - 1).kind());
        Line database = lines.get(4);
        assertEquals("database_name = /some/path/db.fasta", database.text());
        assertEquals(
                List.of(token(TokenKind.NAME, 0, 13), token(TokenKind.VALUE, 16, 35)),
                database.tokens());
    }

    @Nested
    @DisplayName("the release a file declares")
    class DeclaredRelease {

        @Test
        @DisplayName("the first marker's release")
        void named() {
            assertEquals(
                    Optional.of(ToolVersion.parse("2026.03.0")),
                    ParamsHighlighting.declaredRelease(CONSTRUCTED));
            assertEquals(
                    Optional.of(ToolVersion.parse("2026.02.2")),
                    ParamsHighlighting.declaredRelease(
                            "# comet_version 2026.02 rev. 2\r\n"
                                    + "# comet_version 2026.03 rev. 0\n"));
            assertEquals(
                    Optional.of(ToolVersion.parse("2024.01.0")),
                    ParamsHighlighting.declaredRelease(
                            "# a comment first\n# comet_version 2024.01 rev. 0 (7b8d2ae)\n"));
        }

        @Test
        @DisplayName("none without a marker, or with one that names no version")
        void none() {
            assertEquals(Optional.empty(), ParamsHighlighting.declaredRelease("num_threads = 8\n"));
            assertEquals(
                    Optional.empty(),
                    ParamsHighlighting.declaredRelease("# comet_version banana\nnum_threads = 8"));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("an empty or negative span")
        void spans() {
            assertEquals(
                    "a NAME span runs from 3 to 3",
                    assertThrows(IllegalArgumentException.class, () -> token(TokenKind.NAME, 3, 3))
                            .getMessage());
            assertEquals(
                    "a VALUE span runs from -1 to 2",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> token(TokenKind.VALUE, -1, 2))
                            .getMessage());
            assertEquals(0, token(TokenKind.VALUE, 0, 1).start());
        }

        @Test
        @DisplayName("a span past the line, and a problem on the wrong kind")
        void lines() {
            assertEquals(
                    "line 4: a NAME span runs past the line",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new Line(
                                                    4,
                                                    "abc",
                                                    LineKind.DECLARATION,
                                                    List.of(token(TokenKind.NAME, 0, 4)),
                                                    Optional.empty()))
                            .getMessage());
            assertEquals(
                    3,
                    new Line(
                                    4,
                                    "abc",
                                    LineKind.DECLARATION,
                                    List.of(token(TokenKind.NAME, 0, 3)),
                                    Optional.empty())
                            .tokens()
                            .get(0)
                            .end());
            assertEquals(
                    "line 2: only a malformed line has a problem, and it has one",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new Line(
                                                    2,
                                                    "abc",
                                                    LineKind.COMMENT,
                                                    List.of(),
                                                    Optional.of("why")))
                            .getMessage());
            assertEquals(
                    "line 2: only a malformed line has a problem, and it has one",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new Line(
                                                    2,
                                                    "abc",
                                                    LineKind.MALFORMED,
                                                    List.of(),
                                                    Optional.empty()))
                            .getMessage());
        }
    }
}
