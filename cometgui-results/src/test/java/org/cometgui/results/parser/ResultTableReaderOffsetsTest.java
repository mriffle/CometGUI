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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.cometgui.results.parser.PercolatorOutputException.Problem;
import org.cometgui.results.testing.Fixtures;
import org.cometgui.results.testing.OpenFiles;
import org.cometgui.results.testing.RealK562;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Where each row lies in the file, and a row read back from those bytes (Phase 10 unit 3, for the
 * disk-backed store's index, {@code R-RES-03}). Every expected offset is typed by hand from the
 * inline tables, or found by searching the file's own bytes; the line splitting is held to {@link
 * BufferedReader#readLine}, which the reader used before it tracked offsets.
 */
class ResultTableReaderOffsetsTest {

    private static final String HEADER =
            "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds";

    @TempDir private Path directory;

    private Path write(String name, byte[] bytes) throws IOException {
        Path file = directory.resolve(name);
        Files.write(file, bytes);
        return file;
    }

    @Test
    @DisplayName(
            "offsets and lengths by hand: LF, CRLF and CR line ends, a multi-byte row, and a last"
                    + " row without a terminator")
    void offsetsByHand() throws IOException {
        // Header 59 bytes + LF = 60. Row 1 "a_1_2_1\t1\t0.01\t0\tK.A.R\tp" is 24 bytes + CRLF.
        // Row 2 has "\u00e9" (2 bytes in UTF-8): 25 bytes + CR. Row 3, 24 bytes, no terminator.
        String text =
                HEADER
                        + "\n"
                        + "a_1_2_1\t1\t0.01\t0\tK.A.R\tp\r\n"
                        + "a_2_2_1\t1\t0.02\t0\tK.\u00e9.R\tp\r"
                        + "a_3_2_1\t1\t0.03\t0\tK.A.R\tp";
        Path file = write("ends.tsv", text.getBytes(StandardCharsets.UTF_8));
        assertEquals(59, HEADER.length());
        try (ResultTableReader reader = ResultTableReader.open(file)) {
            assertThrows(IllegalStateException.class, reader::lastRowOffset);
            assertThrows(IllegalStateException.class, reader::lastRowLength);
            ResultRow first = reader.next();
            assertEquals("a_1_2_1", first.psmId());
            assertEquals(60, reader.lastRowOffset());
            assertEquals(24, reader.lastRowLength());
            ResultRow second = reader.next();
            assertEquals("K.\u00e9.R", second.peptide());
            assertEquals(60 + 26, reader.lastRowOffset());
            assertEquals(25, reader.lastRowLength());
            ResultRow third = reader.next();
            assertEquals(List.of("p"), third.proteinIds());
            assertEquals(60 + 26 + 26, reader.lastRowOffset());
            assertEquals(24, reader.lastRowLength());
            assertNull(reader.next());
            assertEquals(60 + 26 + 26, reader.lastRowOffset(), "the last row's, after the end");
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "real/percolator-3.07.1/psms.tsv",
                "real/percolator-3.09/decoy-peptides.tsv",
                "constructed/psms-shuffled.tsv",
                "constructed/psms-unknown-q.tsv"
            })
    @DisplayName(
            "every row of the checked-in tables: its bytes at its offset are its line, and read"
                    + " back they parse to the row the stream gave")
    void readBackEqualsStream(String relative) throws IOException {
        Path file = Fixtures.verified(relative, sha256Of(relative));
        readBackEveryRow(file);
    }

    @Test
    @DisplayName("every row of the real K562 3.07.1 PSM table (3897 rows) reads back equal")
    void readBackK562() throws IOException {
        assertEquals(3897, readBackEveryRow(RealK562.Table.V3071_TARGET_PSMS.path()));
    }

    private static int readBackEveryRow(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        List<String> lines;
        try (BufferedReader expected =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            lines = expected.lines().toList();
        }
        int rows = 0;
        try (ResultTableReader reader = ResultTableReader.open(file)) {
            for (ResultRow row = reader.next(); row != null; row = reader.next()) {
                rows++;
                int offset = Math.toIntExact(reader.lastRowOffset());
                byte[] own = Arrays.copyOfRange(bytes, offset, offset + reader.lastRowLength());
                assertEquals(
                        lines.get(rows),
                        new String(own, StandardCharsets.UTF_8),
                        file + " line " + row.line());
                assertTrue(
                        offset + own.length == bytes.length
                                || bytes[offset + own.length] == '\n'
                                || bytes[offset + own.length] == '\r',
                        "a terminator after line " + row.line());
                assertEquals(row, ResultTableReader.row(file, reader.header(), row.line(), own));
            }
        }
        assertEquals(lines.size() - 1, rows);
        return rows;
    }

    @Test
    @DisplayName(
            "line splitting matches BufferedReader.readLine on every arrangement of ends: blank"
                    + " lines, CR CR LF, a lone CR at a buffer boundary, a trailing blank")
    void splittingMatchesBufferedReader() throws IOException {
        List<String> cases =
                List.of(
                        "a\nb\n",
                        "a\r\nb",
                        "a\r\r\nb\n\n",
                        "a\rb\r",
                        "\n\n",
                        "",
                        "x",
                        "a\n\nb",
                        "éè\r\n中\n");
        for (String text : cases) {
            assertSplitLikeBufferedReader(text.getBytes(StandardCharsets.UTF_8));
        }
        // A CR as the last byte of the first 65 536-byte buffer, its LF the first of the next.
        byte[] boundary = new byte[(1 << 16) + 5];
        Arrays.fill(boundary, (byte) 'z');
        boundary[(1 << 16) - 1] = '\r';
        boundary[1 << 16] = '\n';
        boundary[(1 << 16) + 3] = '\r';
        assertSplitLikeBufferedReader(boundary);
    }

    private static void assertSplitLikeBufferedReader(byte[] bytes) throws IOException {
        List<String> expected;
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            expected = reader.lines().toList();
        }
        List<String> actual = new ArrayList<>();
        List<Long> starts = new ArrayList<>();
        try (Utf8Lines lines = new Utf8Lines(new ByteArrayInputStream(bytes))) {
            for (String line = lines.next(); line != null; line = lines.next()) {
                actual.add(line);
                starts.add(lines.lastLineStart());
                String fromBytes =
                        new String(
                                bytes,
                                Math.toIntExact(lines.lastLineStart()),
                                lines.lastLineLength(),
                                StandardCharsets.UTF_8);
                assertEquals(line, fromBytes, "the bytes at the offset are the line");
            }
            assertNull(lines.next(), "still the end");
        }
        assertEquals(expected, actual, bytes.length + " bytes");
        for (int index = 1; index < starts.size(); index++) {
            assertTrue(starts.get(index) > starts.get(index - 1), "offsets increase");
        }
    }

    @Test
    @DisplayName(
            "a row read back is refused as the stream would refuse it: not UTF-8, blank, or of the"
                    + " wrong width, each naming the file and line")
    void readBackRefusals() throws IOException {
        Path file = write("t.tsv", (HEADER + "\na_1_2_1\t1\t0.01\t0\tK.A.R\tp\n").getBytes());
        ResultTableHeader header;
        try (ResultTableReader reader = ResultTableReader.open(file)) {
            header = reader.header();
        }
        PercolatorOutputException notUtf8 =
                assertThrows(
                        PercolatorOutputException.class,
                        () ->
                                ResultTableReader.row(
                                        file, header, 2, new byte[] {'a', (byte) 0xE9, '\t'}));
        assertEquals(Problem.UNREADABLE, notUtf8.problem());
        assertTrue(notUtf8.getCause() instanceof MalformedInputException);
        PercolatorOutputException blank =
                assertThrows(
                        PercolatorOutputException.class,
                        () -> ResultTableReader.row(file, header, 7, new byte[0]));
        assertEquals(Problem.BLANK_LINE, blank.problem());
        assertEquals(
                "Line 7 of the Percolator result table " + file + " is blank", blank.getMessage());
        PercolatorOutputException narrow =
                assertThrows(
                        PercolatorOutputException.class,
                        () ->
                                ResultTableReader.row(
                                        file,
                                        header,
                                        9,
                                        "a\t1\t0.1".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Problem.ROW_WIDTH, narrow.problem());
        assertTrue(narrow.getMessage().startsWith("Line 9 "), narrow.getMessage());
        assertThrows(
                NullPointerException.class,
                () -> ResultTableReader.row(null, header, 2, new byte[1]));
        assertThrows(
                NullPointerException.class,
                () -> ResultTableReader.row(file, null, 2, new byte[1]));
    }

    @Test
    @DisplayName(
            "closing releases the stream: the line splitter closes what it reads, and a table"
                    + " refused at its header is closed before the refusal reaches the caller")
    void closesWhatItOpens() throws IOException {
        boolean[] closed = {false};
        ByteArrayInputStream stream =
                new ByteArrayInputStream("a\nb\n".getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public void close() throws IOException {
                        closed[0] = true;
                        super.close();
                    }
                };
        Utf8Lines lines = new Utf8Lines(stream);
        assertEquals("a", lines.next());
        lines.close();
        assertTrue(closed[0], "the stream is closed");
        IOException afterClose = assertThrows(IOException.class, lines::next);
        assertEquals("Stream closed", afterClose.getMessage());
        Path noPeptide =
                write(
                        "no-peptide.tsv",
                        "PSMId\tscore\tq-value\tposterior_error_prob\tproteinIds\n"
                                .getBytes(StandardCharsets.UTF_8));
        PercolatorOutputException refused =
                assertThrows(
                        PercolatorOutputException.class, () -> ResultTableReader.open(noPeptide));
        assertEquals(Problem.MISSING_COLUMN, refused.problem());
        assertFalse(
                OpenFiles.descriptorTo(noPeptide.toString()), "the refused table is not left open");
    }

    private static String sha256Of(String relative) {
        return switch (relative) {
            case "real/percolator-3.07.1/psms.tsv" ->
                    "6e782dd7bbba9f16bce1c6556bc86032e7a9804b6494694db61573d88340ed53";
            case "real/percolator-3.09/decoy-peptides.tsv" ->
                    "9074109fa81a2ea2362de7d69894f0b660a0204baefc9f0f13172fac29d0b36c";
            case "constructed/psms-shuffled.tsv" ->
                    "3754547ea1ca3ff35b67913a8249b98831e924c0a84029c2a9769fc581a6f5d4";
            case "constructed/psms-unknown-q.tsv" ->
                    "ea58b3b63f9031bf53b5675d117b1e7203137a0ebd2399c9def824334267b6c7";
            default -> throw new IllegalArgumentException(relative);
        };
    }
}
