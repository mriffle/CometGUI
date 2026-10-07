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

package org.cometgui.results.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.results.parser.PercolatorOutputException.Problem;
import org.cometgui.results.testing.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The result table reader over the constructed table ({@code constructed/psms-unknown-q.tsv},
 * hand-written: see {@code constructed/CONSTRUCTED.txt}) and over small tables written inline for
 * each refusal. Every expected value is typed by hand from the fixture.
 */
class ResultTableReaderTest {

    static final String CONSTRUCTED_TABLE = "constructed/psms-unknown-q.tsv";
    static final String CONSTRUCTED_TABLE_SHA256 =
            "ea58b3b63f9031bf53b5675d117b1e7203137a0ebd2399c9def824334267b6c7";

    static final String HEADER = "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds";

    @TempDir private Path directory;

    static Path constructedTable() {
        return Fixtures.verified(CONSTRUCTED_TABLE, CONSTRUCTED_TABLE_SHA256);
    }

    private Path write(String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Nested
    @DisplayName("the constructed table: unknown q-values and several proteins")
    class Constructed {

        @Test
        @DisplayName("15 rows: 7 known q-values, 8 unknown -- counted, none dropped")
        void counts() throws IOException {
            ResultTable table = ResultTableReader.readAll(constructedTable());
            assertEquals(new ResultTableCounts(15, 7, 8), table.counts());
            assertEquals(15, table.rows().size());
            assertEquals(
                    List.of("c04", "c05", "c06", "c07", "c10", "c11", "c14", "c15"),
                    table.unknownQValueRows().stream().map(ResultRow::psmId).toList());
        }

        @Test
        @DisplayName("each q-value's text is kept as written, with the status it earns")
        void qValueStatuses() throws IOException {
            List<ResultRow> rows = ResultTableReader.readAll(constructedTable()).rows();
            List<String> texts = rows.stream().map(row -> row.qValue().text()).toList();
            assertEquals(
                    List.of(
                            "0.001",
                            "0.01",
                            "0.0100001",
                            "NaN",
                            "",
                            "inf",
                            "0,005",
                            "0",
                            "1",
                            "1.5",
                            "-0.01",
                            "1e-3",
                            "0.010",
                            "Infinity",
                            "nan"),
                    texts);
            assertEquals(
                    List.of(
                            QValue.Status.KNOWN,
                            QValue.Status.KNOWN,
                            QValue.Status.KNOWN,
                            QValue.Status.UNPARSABLE,
                            QValue.Status.MISSING,
                            QValue.Status.UNPARSABLE,
                            QValue.Status.UNPARSABLE,
                            QValue.Status.KNOWN,
                            QValue.Status.KNOWN,
                            QValue.Status.OUT_OF_RANGE,
                            QValue.Status.OUT_OF_RANGE,
                            QValue.Status.KNOWN,
                            QValue.Status.KNOWN,
                            QValue.Status.UNPARSABLE,
                            QValue.Status.UNPARSABLE),
                    rows.stream().map(row -> row.qValue().status()).toList());
            assertEquals(0.001, rows.get(11).qValue().value());
            assertEquals(0.01, rows.get(12).qValue().value());
        }

        @Test
        @DisplayName("a multi-protein row keeps every protein, in order")
        void everyProtein() throws IOException {
            List<ResultRow> rows = ResultTableReader.readAll(constructedTable()).rows();
            assertAll(
                    () ->
                            assertEquals(
                                    List.of("sp|P1|A", "sp|P2|B", "sp|P3|C"),
                                    rows.get(0).proteinIds()),
                    () -> assertEquals(List.of("sp|P4|D"), rows.get(1).proteinIds()),
                    () -> assertEquals(List.of("sp|P5|E", "sp|P6|F"), rows.get(2).proteinIds()),
                    () ->
                            assertEquals(
                                    List.of("decoy_sp|P12|L", "decoy_sp|P13|M"),
                                    rows.get(8).proteinIds()),
                    () -> assertEquals("K.KKKKKKK.R", rows.get(8).peptide()));
        }

        @Test
        @DisplayName("score and PEP: read where decimal, NaN where not, text kept either way")
        void scoreAndPep() throws IOException {
            List<ResultRow> rows = ResultTableReader.readAll(constructedTable()).rows();
            assertEquals(2.5, rows.get(0).score());
            assertEquals(1e-05, rows.get(0).posteriorErrorProbability());
            assertEquals("1e-05", rows.get(0).posteriorErrorProbabilityText());
            assertEquals(-0.5, rows.get(14).score());
            assertEquals("nan", rows.get(8).posteriorErrorProbabilityText());
            assertTrue(Double.isNaN(rows.get(8).posteriorErrorProbability()));
        }

        @Test
        @DisplayName("forEach streams the same rows in order and returns the same counts")
        void streaming() throws IOException {
            List<String> seen = new ArrayList<>();
            ResultTableCounts counts =
                    ResultTableReader.forEach(constructedTable(), row -> seen.add(row.psmId()));
            assertEquals(new ResultTableCounts(15, 7, 8), counts);
            assertEquals(15, seen.size());
            assertEquals("c01", seen.get(0));
            assertEquals("c15", seen.get(14));
        }

        @Test
        @DisplayName("next() row by row: counts grow as rows are read, then null at the end")
        void rowByRow() throws IOException {
            try (ResultTableReader reader = ResultTableReader.open(constructedTable())) {
                assertEquals(new ResultTableCounts(0, 0, 0), reader.counts());
                assertEquals("c01", reader.next().psmId());
                assertEquals(new ResultTableCounts(1, 1, 0), reader.counts());
                for (int row = 2; row <= 4; row++) {
                    reader.next();
                }
                assertEquals(new ResultTableCounts(4, 3, 1), reader.counts());
                for (int row = 5; row <= 15; row++) {
                    reader.next();
                }
                assertNull(reader.next());
                assertNull(reader.next());
                assertEquals(new ResultTableCounts(15, 7, 8), reader.counts());
            }
        }
    }

    @Test
    @DisplayName("a closed reader reads nothing more: close releases the file")
    void closedReader() throws IOException {
        Path file = constructedTable();
        ResultTableReader reader = ResultTableReader.open(file);
        assertEquals("c01", reader.next().psmId());
        reader.close();
        PercolatorOutputException refused =
                assertThrows(PercolatorOutputException.class, reader::next);
        assertEquals(Problem.UNREADABLE, refused.problem());
        assertEquals(
                "The Percolator result table "
                        + file
                        + " could not be read as UTF-8 text: java.io.IOException: Stream closed",
                refused.getMessage());
    }

    @Nested
    @DisplayName("columns found by name, not position or version")
    class ByName {

        @Test
        @DisplayName("reordered columns plus an unknown one are read by their names")
        void reordered() throws IOException {
            Path file =
                    write(
                            "reordered.tsv",
                            "peptide\tq-value\textra\tPSMId\tposterior_error_prob\tscore"
                                    + "\tproteinIds\n"
                                    + "K.AAK.R\t0.02\tX\tp1\t0.3\t1.25\tsp|A\tsp|B\n");
            ResultTable table = ResultTableReader.readAll(file);
            ResultRow row = table.rows().get(0);
            assertAll(
                    () -> assertEquals("p1", row.psmId()),
                    () -> assertEquals("K.AAK.R", row.peptide()),
                    () -> assertEquals("0.02", row.qValue().text()),
                    () -> assertEquals(1.25, row.score()),
                    () -> assertEquals(0.3, row.posteriorErrorProbability()),
                    () -> assertEquals(List.of("sp|A", "sp|B"), row.proteinIds()),
                    () -> assertEquals(1, table.header().positionOf(ResultColumn.Q_VALUE)),
                    () -> assertTrue(table.header().proteinsContinueToEndOfRow()));
        }

        @Test
        @DisplayName("proteinIds not last: one protein per row, and a wider row is refused")
        void proteinsNotLast() throws IOException {
            String header = "PSMId\tproteinIds\tscore\tq-value\tposterior_error_prob\tpeptide\n";
            Path ok = write("ok.tsv", header + "p1\tsp|A\t1\t0.5\t0.1\tK.A.R\n");
            ResultTable table = ResultTableReader.readAll(ok);
            assertFalse(table.header().proteinsContinueToEndOfRow());
            assertEquals(List.of("sp|A"), table.rows().get(0).proteinIds());
            Path wide = write("wide.tsv", header + "p1\tsp|A\t1\t0.5\t0.1\tK.A.R\tsp|B\n");
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class, () -> ResultTableReader.readAll(wide));
            assertEquals(Problem.ROW_WIDTH, refused.problem());
            assertEquals(
                    "Line 2 of the Percolator result table "
                            + wide
                            + " has 7 tab-separated fields, but its header names 6 columns",
                    refused.getMessage());
        }

        @Test
        @DisplayName("a header with no rows is a table of none")
        void headerOnly() throws IOException {
            ResultTable table = ResultTableReader.readAll(write("empty-table.tsv", HEADER + "\n"));
            assertEquals(new ResultTableCounts(0, 0, 0), table.counts());
            assertEquals(List.of(), table.rows());
        }

        @Test
        @DisplayName("CRLF line endings are read like LF")
        void crlf() throws IOException {
            Path file = write("crlf.tsv", HEADER + "\r\np1\t1\t0.01\t0.1\tK.A.R\tsp|A\r\n");
            ResultRow row = ResultTableReader.readAll(file).rows().get(0);
            assertEquals(List.of("sp|A"), row.proteinIds());
            assertEquals("0.01", row.qValue().text());
        }
    }

    @Nested
    @DisplayName("refusals, each naming the file")
    class Refusals {

        @ParameterizedTest
        @EnumSource(ResultColumn.class)
        @DisplayName("each required column, removed in turn, is named in the refusal")
        void missingColumn(ResultColumn removed) throws IOException {
            List<String> columns = new ArrayList<>(List.of(HEADER.split("\t")));
            columns.remove(removed.headerName());
            Path file = write("missing.tsv", String.join("\t", columns) + "\n");
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class, () -> ResultTableReader.open(file));
            assertEquals(Problem.MISSING_COLUMN, refused.problem());
            assertEquals(file, refused.file());
            assertEquals(
                    "The Percolator result table "
                            + file
                            + " has no '"
                            + removed.headerName()
                            + "' column. Its header names "
                            + columns
                            + "; a PSM or peptide table needs [PSMId, score, q-value,"
                            + " posterior_error_prob, peptide, proteinIds]",
                    refused.getMessage());
        }

        @Test
        @DisplayName("a needed column named twice is refused")
        void duplicateColumn() throws IOException {
            Path file = write("duplicate.tsv", HEADER + "\tq-value\n");
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class, () -> ResultTableReader.open(file));
            assertEquals(Problem.DUPLICATE_COLUMN, refused.problem());
            assertTrue(
                    refused.getMessage()
                            .startsWith(
                                    "The Percolator result table "
                                            + file
                                            + " names the 'q-value' column more than once"),
                    refused.getMessage());
        }

        @Test
        @DisplayName("an unrelated column named twice is harmless")
        void duplicateUnrelatedColumn() throws IOException {
            Path file =
                    write(
                            "extra.tsv",
                            "x\t" + HEADER.replace("proteinIds", "x\tproteinIds") + "\n");
            try (ResultTableReader reader = ResultTableReader.open(file)) {
                assertEquals(7, reader.header().positionOf(ResultColumn.PROTEIN_IDS));
            }
        }

        @Test
        @DisplayName("a missing file")
        void missingFile() {
            Path file = directory.resolve("absent.tsv");
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class, () -> ResultTableReader.open(file));
            assertEquals(Problem.MISSING_FILE, refused.problem());
            assertEquals(
                    "The Percolator result table " + file + " does not exist",
                    refused.getMessage());
            assertTrue(refused.getCause() instanceof NoSuchFileException);
        }

        @Test
        @DisplayName("an empty file")
        void emptyFile() throws IOException {
            Path file = write("empty.tsv", "");
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class, () -> ResultTableReader.open(file));
            assertEquals(Problem.EMPTY_FILE, refused.problem());
            assertEquals(
                    "The Percolator result table "
                            + file
                            + " is empty: it has not even a header line",
                    refused.getMessage());
        }

        @Test
        @DisplayName("a directory is unreadable, not missing")
        void directoryIsUnreadable() {
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class,
                            () -> ResultTableReader.readAll(directory));
            assertEquals(Problem.UNREADABLE, refused.problem());
            assertTrue(
                    refused.getMessage()
                            .startsWith(
                                    "The Percolator result table "
                                            + directory
                                            + " could not be read as UTF-8 text: "),
                    refused.getMessage());
        }

        @Test
        @DisplayName("a row far into the file that is not UTF-8 is refused mid-stream")
        void notUtf8() throws IOException {
            Path file = directory.resolve("latin1.tsv");
            StringBuilder rows = new StringBuilder(HEADER).append('\n');
            for (int row = 0; row < 1000; row++) {
                rows.append("p").append(row).append("\t1\t0.1\t0.1\tK.A.R\tsp|A\n");
            }
            byte[] head = (rows + "p\t1\t0.1\t0.1\tK.A.R\tsp|").getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[head.length + 2];
            System.arraycopy(head, 0, bytes, 0, head.length);
            bytes[head.length] = (byte) 0xE9;
            bytes[head.length + 1] = '\n';
            Files.write(file, bytes);
            List<String> seen = new ArrayList<>();
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class,
                            () -> ResultTableReader.forEach(file, row -> seen.add(row.psmId())));
            assertEquals(Problem.UNREADABLE, refused.problem());
            assertEquals(
                    "The Percolator result table "
                            + file
                            + " could not be read as UTF-8 text: "
                            + refused.getCause(),
                    refused.getMessage());
            assertTrue(refused.getCause() instanceof MalformedInputException);
            assertTrue(seen.size() > 100, "refused after " + seen.size() + " rows, not at open");
        }

        @Test
        @DisplayName("a header that is not UTF-8 is refused")
        void headerNotUtf8() throws IOException {
            Path file = directory.resolve("bad-header.tsv");
            Files.write(file, new byte[] {'P', (byte) 0xFF, '\n'});
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class, () -> ResultTableReader.open(file));
            assertEquals(Problem.UNREADABLE, refused.problem());
            assertTrue(
                    refused.getMessage()
                            .startsWith(
                                    "The Percolator result table "
                                            + file
                                            + " could not be read as UTF-8 text: "),
                    refused.getMessage());
        }

        @Test
        @DisplayName("a short row is refused, naming its line and both widths")
        void shortRow() throws IOException {
            Path file =
                    write(
                            "short.tsv",
                            HEADER + "\np1\t1\t0.1\t0.1\tK.A.R\tsp|A\np2\t1\t0.1\t0.1\tK.A.R\n");
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class, () -> ResultTableReader.readAll(file));
            assertEquals(Problem.ROW_WIDTH, refused.problem());
            assertEquals(
                    "Line 3 of the Percolator result table "
                            + file
                            + " has 5 tab-separated fields, but its header names 6 columns and only"
                            + " proteins may follow them",
                    refused.getMessage());
        }

        @Test
        @DisplayName("a blank line is refused, naming its line")
        void blankLine() throws IOException {
            Path file = write("blank.tsv", HEADER + "\np1\t1\t0.1\t0.1\tK.A.R\tsp|A\n\n");
            List<String> seen = new ArrayList<>();
            PercolatorOutputException refused =
                    assertThrows(
                            PercolatorOutputException.class,
                            () -> ResultTableReader.forEach(file, row -> seen.add(row.psmId())));
            assertEquals(Problem.BLANK_LINE, refused.problem());
            assertEquals(
                    "Line 3 of the Percolator result table " + file + " is blank",
                    refused.getMessage());
            assertEquals(List.of("p1"), seen, "rows before the refusal were visited");
        }
    }

    @Nested
    @DisplayName("value types")
    class Values {

        @Test
        @DisplayName("counts must add up")
        void countsAddUp() {
            assertThrows(IllegalArgumentException.class, () -> new ResultTableCounts(3, 2, 2));
            assertThrows(IllegalArgumentException.class, () -> new ResultTableCounts(1, 2, -1));
            assertThrows(IllegalArgumentException.class, () -> new ResultTableCounts(1, -1, 2));
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class, () -> new ResultTableCounts(4, 2, 1));
            assertEquals(
                    "every row has a known or an unknown q-value, never both or neither: rows 4,"
                            + " known 2, unknown 1",
                    refused.getMessage());
            assertEquals(3, new ResultTableCounts(3, 2, 1).rows());
            assertEquals(0, new ResultTableCounts(0, 0, 0).rows());
        }

        @Test
        @DisplayName("a table whose counts disagree with its rows is refused")
        void tableCounts() throws IOException {
            ResultTable table = ResultTableReader.readAll(constructedTable());
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new ResultTable(
                                            table.file(),
                                            table.header(),
                                            table.rows().subList(0, 14),
                                            table.counts()));
            assertEquals("the counts say 15 rows, but 14 were read", refused.getMessage());
        }

        @Test
        @DisplayName("a row's protein list cannot be changed afterwards")
        void rowIsImmutable() throws IOException {
            List<String> proteins = new ArrayList<>(List.of("sp|A"));
            ResultRow row =
                    new ResultRow(
                            2, "p", "1", 1.0, QValue.of("0.1"), "0.2", 0.2, "K.A.R", proteins);
            proteins.add("sp|B");
            assertEquals(List.of("sp|A"), row.proteinIds());
            assertThrows(UnsupportedOperationException.class, () -> row.proteinIds().add("x"));
        }

        @Test
        @DisplayName("the exception keeps its file and problem")
        void exceptionCarriesFileAndProblem() {
            Path file = Path.of("x.tsv");
            PercolatorOutputException refused =
                    new PercolatorOutputException(file, Problem.EMPTY_FILE, "m", null);
            assertSame(file, refused.file());
            assertSame(Problem.EMPTY_FILE, refused.problem());
            assertEquals("m", refused.getMessage());
        }
    }
}
