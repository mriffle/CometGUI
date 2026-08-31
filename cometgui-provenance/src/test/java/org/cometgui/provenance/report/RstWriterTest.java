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

package org.cometgui.provenance.report;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.provenance.report.RstWriter.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The reStructuredText primitives, and the misuse each of them refuses.
 *
 * <p>{@code ProvenanceReportWriterTest} pins two whole reports and is the primary assertion about
 * what this class produces. What it cannot reach is the behaviour that only a wrong caller
 * provokes: a heading with an emoji in it, a table row with the wrong number of cells, a field name
 * containing a colon, a write after the document is finished. Every one of those would produce a
 * document that either fails {@code sphinx-build -n -W} or -- worse, and this is why they throw
 * rather than being tidied up -- builds clean and says something other than what the run recorded.
 *
 * <p>Every expected string below is hand-typed. Nothing here compares the writer's output with the
 * writer's own idea of what it should have been.
 */
class RstWriterTest {

    /**
     * A writer with the pattern rules only, which is the weakest one the class permits.
     *
     * @return a fresh writer
     */
    private static RstWriter writer() {
        return RstWriter.redactingWith(SecretRedactor.patternsOnly());
    }

    /**
     * A registered secret, long enough for {@link SecretRegistry} to accept: it refuses anything
     * under eight characters, on the ground that a short one would match half the document.
     */
    private static final String SECRET = "s3cr3t-value-42";

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    @Nested
    @DisplayName("Layout")
    class Layout {

        @Test
        @DisplayName("puts a title between two rules of its own length")
        void putsATitleBetweenTwoRules() {
            assertEquals("=====\nTitle\n=====\n", writer().title("Title").finish());
        }

        @Test
        @DisplayName("separates a title from what precedes it, like any other block")
        void separatesATitleFromWhatPrecedesIt() {
            // A title is normally the first thing in a document, so its separation from what came
            // before is unobservable there and a defect could hide in it.  It is a block like any
            // other and is asserted as one.
            assertEquals(
                    "Before.\n\n=====\nTitle\n=====\n",
                    writer().paragraph("Before.").title("Title").finish());
        }

        @Test
        @DisplayName("underlines a section and a subsection with their own characters")
        void underlinesSectionsAndSubsections() {
            assertEquals(
                    "Section\n=======\n\nSub\n---\n",
                    writer().section("Section").subsection("Sub").finish());
        }

        @Test
        @DisplayName("gives a heading a rule exactly as long as itself, at every length")
        void givesEveryHeadingAnExactRule() {
            // Hand-typed at three lengths, because an off-by-one is the failure this project has
            // already met once and it is invisible at any single length.
            assertAll(
                    () -> assertEquals("A\n-\n", writer().subsection("A").finish()),
                    () -> assertEquals("AB\n--\n", writer().subsection("AB").finish()),
                    () ->
                            assertEquals(
                                    "Tool 12\n-------\n", writer().subsection("Tool 12").finish()));
        }

        @Test
        @DisplayName("separates blocks with one blank line and never separates two fields")
        void separatesBlocksButNotFields() {
            String document =
                    writer().section("S")
                            .fieldValue("One", "1")
                            .fieldValue("Two", "2")
                            .paragraph("After.")
                            .fieldValue("Three", "3")
                            .finish();

            assertEquals("S\n=\n\n:One: ``1``\n:Two: ``2``\n\nAfter.\n\n:Three: ``3``\n", document);
        }

        @Test
        @DisplayName("writes a paragraph one source line per argument")
        void writesAParagraphLineByLine() {
            assertEquals(
                    "first line\nsecond line\n",
                    writer().paragraph("first line", "second line").finish());
        }

        @Test
        @DisplayName("writes an absent field as (none), outside any literal")
        void writesAnAbsentFieldAsNone() {
            assertAll(
                    () -> assertEquals("(none)", RstWriter.ABSENT),
                    () -> assertEquals(":Stage: (none)\n", writer().fieldAbsent("Stage").finish()));
        }

        @Test
        @DisplayName("joins several values on one line with a comma")
        void joinsSeveralValues() {
            assertEquals(
                    ":Caps: ``a``, ``b``, ``c``\n",
                    writer().fieldValues("Caps", List.of("a", "b", "c")).finish());
        }

        @Test
        @DisplayName("writes a bullet list under its field name")
        void writesABulletList() {
            assertEquals(
                    ":Command:\n   * ``a``\n   * ``b``\n",
                    writer().fieldBullets("Command", List.of("a", "b")).finish());
        }

        @Test
        @DisplayName("writes a mapping in ascending key order, whatever order it was built in")
        void writesAMappingSorted() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("PATH", "/usr/bin");
            entries.put("HOME", "/home/ms");
            entries.put("COMET_PARAMS", "comet.params");

            assertEquals(
                    ":Environment:\n"
                            + "   * ``COMET_PARAMS`` = ``comet.params``\n"
                            + "   * ``HOME`` = ``/home/ms``\n"
                            + "   * ``PATH`` = ``/usr/bin``\n",
                    writer().fieldMapping("Environment", entries).finish());
        }

        @Test
        @DisplayName("writes an empty collection or map as (none) rather than an empty structure")
        void writesEmptyCollectionsAsNone() {
            assertAll(
                    () ->
                            assertEquals(
                                    ":A: (none)\n", writer().fieldValues("A", List.of()).finish()),
                    () ->
                            assertEquals(
                                    ":B: (none)\n", writer().fieldBullets("B", List.of()).finish()),
                    () ->
                            assertEquals(
                                    ":C: (none)\n", writer().fieldMapping("C", Map.of()).finish()));
        }

        @Test
        @DisplayName("writes a list-table with its widths, header and rows")
        void writesAListTable() {
            String document =
                    writer().listTable(
                                    List.of(new Column("Tool", 30), new Column("Version", 70)),
                                    List.of(List.of("comet", "2026.02.2")))
                            .finish();

            assertEquals(
                    ".. list-table::\n"
                            + "   :header-rows: 1\n"
                            + "   :widths: 30 70\n"
                            + "\n"
                            + "   * - Tool\n"
                            + "     - Version\n"
                            + "   * - ``comet``\n"
                            + "     - ``2026.02.2``\n",
                    document);
        }
    }

    @Nested
    @DisplayName("Redaction")
    class Redaction {

        @Test
        @DisplayName("runs over every value, wherever in the document it goes")
        void runsOverEveryValue() {
            // One assertion per shape a value can arrive in, because the point of putting
            // redaction in this class is that no shape can bypass it.
            RstWriter rst = RstWriter.redactingWith(SecretRedactor.with(SecretRegistry.of(SECRET)));
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put(SECRET, SECRET);

            String document =
                    rst.fieldValue("A", SECRET)
                            .fieldValues("B", List.of(SECRET))
                            .fieldBullets("C", List.of(SECRET))
                            .fieldMapping("D", environment)
                            .listTable(List.of(new Column("H", 100)), List.of(List.of(SECRET)))
                            .finish();

            assertAll(
                    () -> assertFalse(document.contains(SECRET), "a value survived redaction"),
                    () -> assertTrue(document.contains(":A: ``[REDACTED]``\n")),
                    () -> assertTrue(document.contains(":B: ``[REDACTED]``\n")),
                    () -> assertTrue(document.contains(":C:\n   * ``[REDACTED]``\n")),
                    () ->
                            assertTrue(
                                    document.contains(
                                            ":D:\n   * ``[REDACTED]`` = ``[REDACTED]``\n")),
                    () -> assertTrue(document.contains("   * - ``[REDACTED]``\n")));
        }

        @Test
        @DisplayName("never rewrites prose, which is this repository's text and not the run's")
        void neverRewritesProse() {
            // A section called [REDACTED] would be unreadable and no safer.  "password" is a
            // secret-looking word, and it stays.
            RstWriter rst =
                    RstWriter.redactingWith(SecretRedactor.with(SecretRegistry.of("Password")));

            assertEquals(
                    "Password\n========\n\n:Password: ``x``\n",
                    rst.section("Password").fieldValue("Password", "x").finish());
        }
    }

    @Nested
    @DisplayName("Misuse")
    class Misuse {

        @Test
        @DisplayName("refuses a heading that is empty or not printable ASCII")
        void refusesABadHeading() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a heading must not be empty",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> writer().section(""))
                                            .getMessage()),
                    // An emoji is two UTF-16 units and one printed character, so its underline
                    // would be one character too long -- silently wrong rather than loudly.
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> writer().section("Tool \uD83E\uDDEC")),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class, () -> writer().title("a\nb")),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> writer().subsection("a\u007fb")));
        }

        @Test
        @DisplayName("accepts the whole printable ASCII range in a heading, ends included")
        void acceptsThePrintableAsciiRange() {
            // The two boundaries of the range, so that a check written with the wrong comparison
            // shows up here rather than in a report nobody generated yet.
            assertEquals("A ~ Z\n-----\n", writer().subsection("A ~ Z").finish());
        }

        @Test
        @DisplayName("refuses a field name containing a colon, wherever in it the colon is")
        void refusesAColonInAFieldName() {
            // Both ends of the name, because a check written as "> 0" instead of ">= 0" accepts a
            // name whose colon is the very first character and is otherwise indistinguishable.
            assertAll(
                    () ->
                            assertEquals(
                                    "a field name must not contain a colon, but was: \"MD5:\"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> writer().fieldValue("MD5:", "x"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "a field name must not contain a colon, but was: \":MD5\"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> writer().fieldValue(":MD5", "x"))
                                            .getMessage()));
        }

        @Test
        @DisplayName("refuses a paragraph with no lines")
        void refusesAnEmptyParagraph() {
            assertEquals(
                    "a paragraph must have at least one line",
                    assertThrows(IllegalArgumentException.class, () -> writer().paragraph())
                            .getMessage());
        }

        @Test
        @DisplayName("refuses a table with no columns, no rows, or a row of the wrong width")
        void refusesAMalformedTable() {
            List<Column> one = List.of(new Column("H", 100));

            assertAll(
                    () ->
                            assertEquals(
                                    "a list-table must have at least one column",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            writer().listTable(
                                                                            List.of(),
                                                                            List.of(List.of("a"))))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "a list-table must have at least one body row; write a"
                                            + " paragraph instead",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> writer().listTable(one, List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "a list-table row must have 1 cell(s) to match its header, but"
                                            + " one had 2",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            writer().listTable(
                                                                            one,
                                                                            List.of(
                                                                                    List.of(
                                                                                            "a",
                                                                                            "b"))))
                                            .getMessage()));
        }

        @Test
        @DisplayName("refuses a column that is not one character wide or more")
        void refusesAZeroWidthColumn() {
            assertEquals(
                    "a column width must be at least 1, but was: 0",
                    assertThrows(IllegalArgumentException.class, () -> new Column("H", 0))
                            .getMessage());
        }

        @Test
        @DisplayName("refuses to finish an empty document")
        void refusesToFinishAnEmptyDocument() {
            assertEquals(
                    "the document is empty",
                    assertThrows(IllegalStateException.class, () -> writer().finish())
                            .getMessage());
        }

        @Test
        @DisplayName("refuses every kind of write once the document is finished")
        void refusesToWriteAfterFinishing() {
            RstWriter finished = writer().section("S");
            finished.finish();
            String expected = "the document is finished and cannot be added to";

            assertAll(
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> finished.section("Again"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> finished.title("Again"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> finished.fieldValue("A", "1"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> finished.finish())
                                            .getMessage()));
        }

        @Test
        @DisplayName("rejects a null argument by name, everywhere one can be passed")
        void rejectsNullArguments() {
            SecretRedactor nullRedactor = deliberateNull();
            String nullText = deliberateNull();
            List<String> nullList = deliberateNull();
            Map<String, String> nullMap = deliberateNull();
            List<Column> nullColumns = deliberateNull();
            String[] nullLines = deliberateNull();

            assertAll(
                    () ->
                            assertEquals(
                                    "redactor",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> RstWriter.redactingWith(nullRedactor))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "text",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().fieldValue("A", nullText))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "texts",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().fieldValues("A", nullList))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "texts",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().fieldBullets("A", nullList))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "entries",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().fieldMapping("A", nullMap))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "columns",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            writer().listTable(
                                                                            nullColumns,
                                                                            List.of(List.of("a"))))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "lines",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().paragraph(nullLines))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "a heading",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().section(nullText))
                                            .getMessage()));
        }

        @Test
        @DisplayName("rejects a null element inside a collection, a map or a row")
        void rejectsNullElements() {
            List<String> withNull = new java.util.ArrayList<>();
            withNull.add(deliberateNull());
            Map<String, String> mapWithNull = new LinkedHashMap<>();
            mapWithNull.put("A", deliberateNull());

            assertAll(
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () -> writer().fieldValues("A", withNull)),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () -> writer().fieldBullets("A", withNull)),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () -> writer().fieldMapping("A", mapWithNull)),
                    () ->
                            assertEquals(
                                    "cell",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            writer().listTable(
                                                                            List.of(
                                                                                    new Column(
                                                                                            "H",
                                                                                            1)),
                                                                            List.of(withNull)))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "line",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            writer().paragraph(
                                                                            "ok", deliberateNull()))
                                            .getMessage()));
        }

        @Test
        @DisplayName("refuses a column title that is not printable ASCII")
        void refusesABadColumnTitle() {
            assertEquals(
                    "a column title must be printable ASCII, so that an underline is exactly as"
                            + " long as what it underlines, but was: \"H\u00e9\"",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            writer().listTable(
                                                            List.of(new Column("H\u00e9", 100)),
                                                            List.of(List.of("a"))))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("Description")
    class Description {

        @Test
        @DisplayName("toString says how far the document has got and not a character of it")
        void toStringSaysHowFarAndNothingElse() {
            RstWriter rst =
                    RstWriter.redactingWith(
                            SecretRedactor.with(SecretRegistry.of("hunter2-not-a-real-password")));
            rst.fieldValue("Secret", "hunter2-not-a-real-password");

            // Hand-counted: ":Secret:" is 8, a space is 1, "``[REDACTED]``" is 14, the newline
            // is 1.
            assertAll(
                    () -> assertEquals("RstWriter[characters=24, finished=false]", rst.toString()),
                    () -> assertFalse(rst.toString().contains("hunter2")));
        }
    }
}
