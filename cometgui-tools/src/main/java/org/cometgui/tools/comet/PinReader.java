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

package org.cometgui.tools.comet;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Streams one PIN file, row by row, checking each row as it goes: the one PIN parser, shared by
 * {@link CometPinValidator} and {@link PinMerger} so that the file a merge accepts is the file
 * validation accepts.
 *
 * <p>Rows are returned as their <strong>text</strong>, never re-tokenised, so a merge can write
 * them out byte for byte. The file is decoded as ISO-8859-1, which maps each byte to one character
 * and back, so no byte is altered whatever the file's encoding.
 *
 * <h2>Lines</h2>
 *
 * <p>A line ends at LF. One CR immediately before the LF is part of the terminator, not of the row
 * -- so a CRLF file reads exactly as its LF form does -- and is dropped. Comet writes LF. A last
 * line with no LF is a <strong>truncated</strong> file -- Comet ends every row, the header
 * included, with one -- and is refused even when its fields happen to be complete. A file that lost
 * whole rows at a line boundary cannot be told from a shorter search by its content alone; that is
 * what the pepXML's own count and Comet's exit code are for.
 *
 * <h2>Rows</h2>
 *
 * <p>Fields are separated by tabs. A row has <strong>at least</strong> as many fields as the header
 * has columns: in Comet's PIN the {@code Proteins} column holds the first protein, and each further
 * protein a peptide maps to is <strong>one more tab-separated field</strong> beyond the header --
 * measured on 2026.03.0, where 52 of 6 472 rows over two files have 29 or 30 fields against a
 * 28-column header (for example {@code DECOY_sp|Q06730|ZN33A_HUMAN} then {@code
 * DECOY_sp|Q06732|ZN33B_HUMAN}). Fewer fields than columns is a broken row. In each row: {@code
 * SpecId} is not empty; {@code Label} is {@code 1} (target) or {@code -1} (decoy); {@code ScanNr}
 * is a non-negative integer; every feature is a finite decimal ({@link #DECIMAL}; every feature
 * value of the real files matches it); {@code Peptide} and every protein field are not empty.
 *
 * <h2>The decoy prefix, when one is given</h2>
 *
 * <p>Opened with a decoy prefix ({@link #open(Path, String)}), the reader also counts, as it checks
 * each row's protein fields, the decoy rows that name <em>at least one</em> protein beginning with
 * the prefix ({@link #decoysCarryingPrefix()}) and the target rows <em>every</em> protein of which
 * begins with it ({@link #targetsOnlyPrefixed()}). It judges nothing by them: what is inconsistent
 * is {@link CometPinValidator#validateBeforePercolator}'s to say. Opened without one, both stay
 * zero.
 */
final class PinReader implements Closeable {

    /**
     * A finite decimal: what Comet writes for a feature with {@code %d}, {@code %f} or {@code %e}.
     */
    static final Pattern DECIMAL =
            Pattern.compile("-?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][-+]?[0-9]+)?");

    /** A scan number. */
    private static final Pattern SCAN = Pattern.compile("[0-9]+");

    /** What {@link InputStream#read()} returns at the end of the stream. */
    private static final int END = -1;

    /** The label of a target row. */
    static final String TARGET = "1";

    /** The label of a decoy row. */
    static final String DECOY = "-1";

    private final Path file;

    private final InputStream in;

    private final PinHeader header;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);

    /** The decoy prefix the protein fields are counted against, or {@code null} for none. */
    private final String decoyPrefix;

    private long decoysCarryingPrefix;

    private long targetsOnlyPrefixed;

    private long firstTargetOnlyPrefixedLine;

    private long lineNumber;

    private long targets;

    private long decoys;

    private PinReader(Path file, InputStream in, String decoyPrefix) throws CometOutputException {
        this.file = file;
        this.in = in;
        this.decoyPrefix = decoyPrefix;
        String first = readLine();
        if (first == null) {
            throw problem("is empty: Comet wrote no header", null);
        }
        try {
            this.header = PinHeader.parse(first);
        } catch (IllegalArgumentException malformed) {
            throw problem("is not a PIN file: " + malformed.getMessage(), malformed);
        }
    }

    /**
     * Opens a PIN file and reads its header.
     *
     * @param file the file
     * @return a reader positioned at the first data row
     * @throws CometOutputException if the file is missing, not a regular file, empty, unreadable,
     *     truncated within its header, or its header breaks the column rule
     */
    static PinReader open(Path file) throws CometOutputException {
        return openCounting(file, null);
    }

    /**
     * Opens a PIN file and reads its header, counting each row's proteins against a decoy prefix
     * (see the class documentation).
     *
     * @param file the file
     * @param decoyPrefix the decoy prefix; not empty
     * @return a reader positioned at the first data row
     * @throws CometOutputException as {@link #open(Path)}
     * @throws NullPointerException if {@code decoyPrefix} is {@code null}
     * @throws IllegalArgumentException if {@code decoyPrefix} is empty, which every protein would
     *     begin with
     */
    static PinReader open(Path file, String decoyPrefix) throws CometOutputException {
        if (Objects.requireNonNull(decoyPrefix, "decoyPrefix").isEmpty()) {
            throw new IllegalArgumentException(
                    "an empty decoy prefix begins every protein name, so it marks nothing");
        }
        return openCounting(file, decoyPrefix);
    }

    private static PinReader openCounting(Path file, String decoyPrefix)
            throws CometOutputException {
        if (Files.isDirectory(file)) {
            throw new CometOutputException(
                    file, "the PIN file " + file + " is a directory, not a file", null);
        }
        InputStream in;
        try {
            in = new BufferedInputStream(Files.newInputStream(file), 65536);
        } catch (NoSuchFileException missing) {
            throw new CometOutputException(
                    file,
                    "the PIN file " + file + " does not exist: Comet did not write it",
                    missing);
        } catch (IOException unreadable) {
            throw new CometOutputException(
                    file,
                    "the PIN file " + file + " cannot be read: " + unreadable.getMessage(),
                    unreadable);
        }
        try {
            return new PinReader(file, in, decoyPrefix);
        } catch (CometOutputException refused) {
            throw closedAfter(in, refused);
        }
    }

    /**
     * Closes a file that was refused, keeping the refusal as the exception that is thrown.
     *
     * @param resource the open file
     * @param refused why it was refused
     * @return {@code refused}, carrying any failure to close as a suppressed exception
     */
    static CometOutputException closedAfter(Closeable resource, CometOutputException refused) {
        try {
            resource.close();
        } catch (IOException alsoFailed) {
            refused.addSuppressed(alsoFailed);
        }
        return refused;
    }

    /**
     * The file's header.
     *
     * @return the header
     */
    PinHeader header() {
        return header;
    }

    /**
     * Reads and checks the next data row.
     *
     * @return the row's text, without its terminator; {@code null} at the end of the file
     * @throws CometOutputException if the row is broken or the file truncated, naming the file and
     *     the line
     */
    String nextRow() throws CometOutputException {
        String row = readLine();
        if (row != null) {
            check(row);
        }
        return row;
    }

    /**
     * The data rows read so far.
     *
     * @return {@code targets() + decoys()}
     */
    long rows() {
        return targets + decoys;
    }

    /**
     * The target rows read so far.
     *
     * @return the rows labelled {@code 1}
     */
    long targets() {
        return targets;
    }

    /**
     * The decoy rows read so far.
     *
     * @return the rows labelled {@code -1}
     */
    long decoys() {
        return decoys;
    }

    /**
     * The decoy rows read so far that name at least one protein beginning with the decoy prefix.
     *
     * @return the count; zero when the reader was opened without a prefix
     */
    long decoysCarryingPrefix() {
        return decoysCarryingPrefix;
    }

    /**
     * The target rows read so far every protein of which begins with the decoy prefix.
     *
     * @return the count; zero when the reader was opened without a prefix
     */
    long targetsOnlyPrefixed() {
        return targetsOnlyPrefixed;
    }

    /**
     * The line of the first target row every protein of which begins with the decoy prefix.
     *
     * @return the line number, counting the header as line 1; zero when there is none
     */
    long firstTargetOnlyPrefixedLine() {
        return firstTargetOnlyPrefixedLine;
    }

    private void check(String row) throws CometOutputException {
        String[] fields = row.split("\t", -1);
        int columns = header.columns().size();
        if (fields.length < columns) {
            throw rowProblem(
                    "has "
                            + fields.length
                            + (fields.length == 1 ? " field" : " fields")
                            + " where the header has "
                            + columns
                            + " columns");
        }
        if (fields[0].isEmpty()) {
            throw rowProblem("has an empty SpecId");
        }
        boolean target = TARGET.equals(fields[1]);
        if (target) {
            targets++;
        } else if (DECOY.equals(fields[1])) {
            decoys++;
        } else {
            throw rowProblem("has the Label \"" + fields[1] + "\", which is neither 1 nor -1");
        }
        if (!SCAN.matcher(fields[2]).matches()) {
            throw rowProblem("has the ScanNr \"" + fields[2] + "\", which is not a scan number");
        }
        int peptide = columns - PinHeader.TRAILING.size();
        for (int index = PinHeader.LEADING.size(); index < peptide; index++) {
            if (!DECIMAL.matcher(fields[index]).matches()) {
                throw rowProblem(
                        "has \""
                                + fields[index]
                                + "\" in the feature column "
                                + header.columns().get(index)
                                + ", which is not a finite decimal");
            }
        }
        if (fields[peptide].isEmpty()) {
            throw rowProblem("has an empty Peptide");
        }
        // Proteins: the header's last column and every field beyond it.
        int prefixed = 0;
        for (int index = columns - 1; index < fields.length; index++) {
            if (fields[index].isEmpty()) {
                throw rowProblem("has an empty protein in field " + (index + 1));
            }
            if (decoyPrefix != null && fields[index].startsWith(decoyPrefix)) {
                prefixed++;
            }
        }
        countPrefixed(target, prefixed, fields.length - (columns - 1));
    }

    private void countPrefixed(boolean target, int prefixed, int proteins) {
        if (target) {
            if (prefixed == proteins) {
                targetsOnlyPrefixed++;
                if (firstTargetOnlyPrefixedLine == 0) {
                    firstTargetOnlyPrefixedLine = lineNumber;
                }
            }
        } else if (prefixed > 0) {
            decoysCarryingPrefix++;
        }
    }

    /**
     * Reads one line, without its terminator.
     *
     * @return the line, or {@code null} at the end of the file
     * @throws CometOutputException if the file cannot be read, or its last line has no LF
     */
    private String readLine() throws CometOutputException {
        buffer.reset();
        int read;
        try {
            read = in.read();
            while (read != END && read != '\n') {
                buffer.write(read);
                read = in.read();
            }
        } catch (IOException unreadable) {
            throw problem("cannot be read: " + unreadable.getMessage(), unreadable);
        }
        if (read == END && buffer.size() == 0) {
            return null;
        }
        lineNumber++;
        if (read == END) {
            throw problem(
                    "ends without a line terminator: line "
                            + lineNumber
                            + " is incomplete, so the file is truncated",
                    null);
        }
        String line = buffer.toString(StandardCharsets.ISO_8859_1);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private CometOutputException rowProblem(String problem) {
        return problem("line " + lineNumber + " " + problem, null);
    }

    private CometOutputException problem(String problem, Throwable cause) {
        return new CometOutputException(file, "the PIN file " + file + " " + problem, cause);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
