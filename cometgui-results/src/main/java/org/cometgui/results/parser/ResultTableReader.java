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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reads a Percolator PSM or peptide table -- target or decoy, the four have one shape -- one row at
 * a time.
 *
 * <p>The header is the first line; columns are found by the names in {@link ResultColumn}, never by
 * position or by Percolator version. Each further line is one row. Rows are read in file order and
 * never held by the reader, so a caller that indexes a large table on disk ({@code R-RES-03}) can
 * stream it: {@link #open} and {@link #next}, or {@link #forEach}. {@link #readAll} collects every
 * row for a table known to be small.
 *
 * <p>For such an index the reader also says where each row lies in the file -- {@link
 * #lastRowOffset} and {@link #lastRowLength}, in bytes -- and {@link #row(Path, ResultTableHeader,
 * long, byte[])} parses one row read back from those bytes, by the same rules as {@link #next}, so
 * a row read back is the row the stream gave. Lines end at {@code \n}, {@code \r} or {@code \r\n},
 * as {@link java.io.BufferedReader#readLine} ends them.
 *
 * <p>A row whose q-value is missing or unparsable is kept and counted ({@code R-RES-02}); see
 * {@link QValue}. A file that is absent, empty, not UTF-8, missing a needed column, or has a blank
 * line or a row of the wrong width is refused with a {@link PercolatorOutputException} naming the
 * file and, for a row, its line. Nothing here writes to, locks or alters the file.
 */
public final class ResultTableReader implements Closeable {

    private final Path file;
    private final Utf8Lines reader;
    private final ResultTableHeader header;
    private long line = 1;
    private long rows;
    private long knownQValues;

    private ResultTableReader(Path file, Utf8Lines reader, ResultTableHeader header) {
        this.file = file;
        this.reader = reader;
        this.header = header;
    }

    /**
     * Opens a table and reads its header.
     *
     * @param file the table
     * @return a reader positioned before the first row; the caller closes it
     * @throws PercolatorOutputException if the file is missing, empty, unreadable or its header
     *     lacks a needed column
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static ResultTableReader open(Path file) throws PercolatorOutputException {
        Objects.requireNonNull(file, "file");
        Utf8Lines reader;
        try {
            InputStream in = Files.newInputStream(file);
            reader = new Utf8Lines(in);
        } catch (NoSuchFileException missing) {
            throw new PercolatorOutputException(
                    file,
                    PercolatorOutputException.Problem.MISSING_FILE,
                    "The Percolator result table " + file + " does not exist",
                    missing);
        } catch (IOException unreadable) {
            throw unreadable(file, unreadable);
        }
        try {
            String first = readLine(reader, file);
            if (first == null) {
                throw new PercolatorOutputException(
                        file,
                        PercolatorOutputException.Problem.EMPTY_FILE,
                        "The Percolator result table "
                                + file
                                + " is empty: it has not even a header line",
                        null);
            }
            return new ResultTableReader(file, reader, ResultTableHeader.parse(first, file));
        } catch (PercolatorOutputException refused) {
            closeQuietly(reader, refused);
            throw refused;
        }
    }

    /**
     * Streams every row of a table to a visitor.
     *
     * @param file the table
     * @param visitor called once per row, in file order
     * @return the counts over the whole file
     * @throws PercolatorOutputException if the file is refused; rows before the refused line have
     *     already been visited
     */
    public static ResultTableCounts forEach(Path file, Consumer<? super ResultRow> visitor)
            throws PercolatorOutputException {
        Objects.requireNonNull(visitor, "visitor");
        try (ResultTableReader table = open(file)) {
            for (ResultRow row = table.next(); row != null; row = table.next()) {
                visitor.accept(row);
            }
            return table.counts();
        }
    }

    /**
     * Reads a whole table into memory: for small tables only.
     *
     * @param file the table
     * @return the header, every row and the counts
     * @throws PercolatorOutputException if the file is refused
     */
    public static ResultTable readAll(Path file) throws PercolatorOutputException {
        List<ResultRow> rows = new ArrayList<>();
        try (ResultTableReader table = open(file)) {
            for (ResultRow row = table.next(); row != null; row = table.next()) {
                rows.add(row);
            }
            return new ResultTable(file, table.header(), rows, table.counts());
        }
    }

    /**
     * The table's header.
     *
     * @return the header read by {@link #open}
     */
    public ResultTableHeader header() {
        return header;
    }

    /**
     * Reads the next row.
     *
     * @return the row, or {@code null} after the last
     * @throws PercolatorOutputException if the line is blank, of the wrong width or unreadable
     */
    public ResultRow next() throws PercolatorOutputException {
        String text = readLine(reader, file);
        if (text == null) {
            return null;
        }
        line++;
        ResultRow row = parse(file, header, line, text);
        rows++;
        if (row.qValue().isKnown()) {
            knownQValues++;
        }
        return row;
    }

    /**
     * Where the row {@link #next} last returned starts in the file.
     *
     * @return the offset of its first byte from the start of the file
     * @throws IllegalStateException if {@link #next} has returned no row
     */
    public long lastRowOffset() {
        requireRow();
        return reader.lastLineStart();
    }

    /**
     * How many bytes the row {@link #next} last returned holds.
     *
     * @return its length in bytes, its line terminator excluded
     * @throws IllegalStateException if {@link #next} has returned no row
     */
    public int lastRowLength() {
        requireRow();
        return reader.lastLineLength();
    }

    /**
     * Parses one row read back from the file -- the bytes at {@link #lastRowOffset} for {@link
     * #lastRowLength} -- by exactly the rules {@link #next} applies to it.
     *
     * @param file the table the bytes came from, named in any refusal
     * @param header the table's header, as {@link #open} read it
     * @param line the row's line number, counting the header as line 1
     * @param utf8 the row's bytes, without a line terminator
     * @return the row
     * @throws PercolatorOutputException if the bytes are not UTF-8, are empty, or hold the wrong
     *     number of fields for the header
     * @throws NullPointerException if a reference is {@code null}
     */
    public static ResultRow row(Path file, ResultTableHeader header, long line, byte[] utf8)
            throws PercolatorOutputException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(header, "header");
        String text;
        try {
            text = Utf8Lines.decode(utf8, 0, utf8.length);
        } catch (CharacterCodingException notUtf8) {
            throw unreadable(file, notUtf8);
        }
        return parse(file, header, line, text);
    }

    private void requireRow() {
        if (rows == 0) {
            throw new IllegalStateException("no row of " + file + " has been read yet");
        }
    }

    private static ResultRow parse(Path file, ResultTableHeader header, long line, String text)
            throws PercolatorOutputException {
        if (text.isEmpty()) {
            throw new PercolatorOutputException(
                    file,
                    PercolatorOutputException.Problem.BLANK_LINE,
                    "Line " + line + " of the Percolator result table " + file + " is blank",
                    null);
        }
        String[] fields = text.split("\t", -1);
        int width = header.columns().size();
        boolean widthOk =
                header.proteinsContinueToEndOfRow()
                        ? fields.length >= width
                        : fields.length == width;
        if (!widthOk) {
            throw new PercolatorOutputException(
                    file,
                    PercolatorOutputException.Problem.ROW_WIDTH,
                    "Line "
                            + line
                            + " of the Percolator result table "
                            + file
                            + " has "
                            + fields.length
                            + " tab-separated fields, but its header names "
                            + width
                            + " columns"
                            + (header.proteinsContinueToEndOfRow()
                                    ? " and only proteins may follow them"
                                    : ""),
                    null);
        }
        int proteinStart = header.positionOf(ResultColumn.PROTEIN_IDS);
        List<String> proteins =
                header.proteinsContinueToEndOfRow()
                        ? Arrays.asList(fields).subList(proteinStart, fields.length)
                        : List.of(fields[proteinStart]);
        String scoreText = fields[header.positionOf(ResultColumn.SCORE)];
        String pepText = fields[header.positionOf(ResultColumn.POSTERIOR_ERROR_PROBABILITY)];
        QValue qValue = QValue.of(fields[header.positionOf(ResultColumn.Q_VALUE)]);
        return new ResultRow(
                line,
                fields[header.positionOf(ResultColumn.PSM_ID)],
                scoreText,
                DecimalText.parse(scoreText),
                qValue,
                pepText,
                DecimalText.parse(pepText),
                fields[header.positionOf(ResultColumn.PEPTIDE)],
                proteins);
    }

    /**
     * The counts over the rows read so far; over the whole file once {@link #next} has returned
     * {@code null}.
     *
     * @return the counts
     */
    public ResultTableCounts counts() {
        return new ResultTableCounts(rows, knownQValues, rows - knownQValues);
    }

    @Override
    public void close() throws PercolatorOutputException {
        try {
            reader.close();
        } catch (IOException failed) {
            throw unreadable(file, failed);
        }
    }

    private static String readLine(Utf8Lines reader, Path file) throws PercolatorOutputException {
        try {
            return reader.next();
        } catch (IOException failed) {
            throw unreadable(file, failed);
        }
    }

    /*
     * No line number. When the reader was a BufferedReader it decoded ahead of the line it
     * returned, so a byte that is not UTF-8 could surface while an earlier line was being read;
     * Utf8Lines decodes one line at a time, but the message is kept as it was, and it also serves
     * a row read back by offset, whose line is the caller's claim rather than the reader's count.
     */
    private static PercolatorOutputException unreadable(Path file, IOException cause) {
        return new PercolatorOutputException(
                file,
                PercolatorOutputException.Problem.UNREADABLE,
                "The Percolator result table "
                        + file
                        + " could not be read as UTF-8 text: "
                        + cause,
                cause);
    }

    private static void closeQuietly(Utf8Lines reader, PercolatorOutputException refused) {
        try {
            reader.close();
        } catch (IOException alsoFailed) {
            refused.addSuppressed(alsoFailed);
        }
    }
}
