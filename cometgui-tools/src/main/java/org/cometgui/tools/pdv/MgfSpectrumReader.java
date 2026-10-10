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

package org.cometgui.tools.pdv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streams the spectra of an MGF file, refusing anything it cannot carry over exactly.
 *
 * <p>Every entry is taken as an MS2 spectrum. Its {@code TITLE} is its native id, its ordinal is
 * its index (0-based) and its position (1-based), the first field of {@code PEPMASS} is its
 * precursor m/z as text, {@code CHARGE} its charge ({@code 2+}, {@code 3-} or a bare {@code 2}) and
 * {@code RTINSECONDS} its retention time. A peak line is exactly two decimal numbers, read as
 * {@code double}s. Other entry parameters are ignored, because none of them changes a peak.
 *
 * <p>Refused, naming the file and the line: an entry with no {@code TITLE} (nothing could find it
 * again); a peak line with a third column (a fragment charge, which an MGF CometGUI writes could
 * not keep); anything that is not a decimal number where one belongs ({@code NaN}, {@code Infinity}
 * and hexadecimal included, which {@link Double#parseDouble} would otherwise accept); several
 * charges; an unterminated entry; and a global {@code CHARGE}, {@code PEPMASS}, {@code TITLE} or
 * {@code RTINSECONDS} before the first entry, which would set a default this reader does not apply.
 * The file must be UTF-8 (ASCII included).
 */
public final class MgfSpectrumReader {

    /** The line that opens an entry. */
    static final String BEGIN = "BEGIN IONS";

    /** The line that closes an entry. */
    static final String END = "END IONS";

    /** A {@code KEY=value} parameter line. */
    static final Pattern PARAMETER = Pattern.compile("([A-Za-z][A-Za-z0-9_]*)=(.*)");

    /** A decimal number, and nothing else {@link Double#parseDouble} would accept. */
    static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private static final Pattern CHARGE = Pattern.compile("([0-9]+)([+-]?)");

    private static final Set<String> SPECTRUM_PARAMETERS =
            Set.of("TITLE", "PEPMASS", "CHARGE", "RTINSECONDS");

    private MgfSpectrumReader() {}

    /**
     * Whether a stripped, non-empty line is an MGF comment.
     *
     * @param text the line, stripped
     * @return {@code true} for a line starting {@code #}, {@code ;}, {@code !} or {@code /}
     */
    static boolean isComment(String text) {
        char first = text.charAt(0);
        return first == '#' || first == ';' || first == '!' || first == '/';
    }

    /**
     * Streams every entry of an MGF file, in file order.
     *
     * @param file the MGF file
     * @param visitor receives each entry
     * @throws SpectrumFileException if an entry cannot be read exactly, naming the file and line
     * @throws IOException if the file cannot be read, or the visitor throws
     * @throws NullPointerException if either argument is {@code null}
     */
    public static void read(Path file, SpectrumVisitor visitor) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(visitor, "visitor");
        int lineNumber = 0;
        try (BufferedReader lines =
                new BufferedReader(
                        new InputStreamReader(
                                Files.newInputStream(file),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            Entry entry = null;
            int ordinal = 0;
            String line;
            while (true) {
                try {
                    line = lines.readLine();
                } catch (CharacterCodingException notUtf8) {
                    throw new SpectrumFileException(
                            file, "is not UTF-8 text after line " + lineNumber, notUtf8);
                }
                if (line == null) {
                    break;
                }
                lineNumber++;
                String text = line.strip();
                if (text.isEmpty() || isComment(text)) {
                    continue;
                }
                if (entry == null) {
                    if (BEGIN.equals(text)) {
                        entry = new Entry(lineNumber);
                    } else {
                        global(file, lineNumber, text);
                    }
                } else if (BEGIN.equals(text)) {
                    throw new SpectrumFileException(
                            file,
                            "line "
                                    + lineNumber
                                    + ": BEGIN IONS inside the entry opened on line "
                                    + entry.firstLine);
                } else if (END.equals(text)) {
                    visitor.visit(entry.finish(file, ordinal));
                    ordinal++;
                    entry = null;
                } else {
                    entry.line(file, lineNumber, text);
                }
            }
            if (entry != null) {
                throw new SpectrumFileException(
                        file,
                        "the entry opened on line "
                                + entry.firstLine
                                + " has no END IONS before the end of the file");
            }
        }
    }

    private static void global(Path file, int lineNumber, String text)
            throws SpectrumFileException {
        Matcher parameter = PARAMETER.matcher(text);
        if (!parameter.matches()) {
            throw new SpectrumFileException(
                    file,
                    "line "
                            + lineNumber
                            + ": \""
                            + text
                            + "\" is outside any entry and is neither a parameter nor BEGIN IONS");
        }
        String key = parameter.group(1).toUpperCase(Locale.ROOT);
        if (SPECTRUM_PARAMETERS.contains(key)) {
            throw new SpectrumFileException(
                    file,
                    "line "
                            + lineNumber
                            + ": a global "
                            + key
                            + "= before the first entry sets a default for every entry, which"
                            + " this reader does not apply");
        }
    }

    /** One entry as it is read. */
    private static final class Entry {
        private final int firstLine;
        private final DoubleList mz = new DoubleList();
        private final DoubleList intensity = new DoubleList();
        private String title;
        private String precursor;
        private OptionalInt charge = OptionalInt.empty();
        private RetentionTime retentionTime;

        Entry(int firstLine) {
            this.firstLine = firstLine;
        }

        void line(Path file, int lineNumber, String text) throws SpectrumFileException {
            Matcher parameter = PARAMETER.matcher(text);
            if (parameter.matches()) {
                parameter(file, lineNumber, parameter.group(1), parameter.group(2));
                return;
            }
            String[] fields = text.split("\\s+");
            if (fields.length != 2) {
                throw new SpectrumFileException(
                        file,
                        "line "
                                + lineNumber
                                + ": the peak line \""
                                + text
                                + "\" has "
                                + fields.length
                                + " fields; exactly two (m/z and intensity) can be carried over");
            }
            mz.add(number(file, lineNumber, fields[0], "m/z"));
            intensity.add(number(file, lineNumber, fields[1], "intensity"));
        }

        private void parameter(Path file, int lineNumber, String key, String value)
                throws SpectrumFileException {
            switch (key.toUpperCase(Locale.ROOT)) {
                case "TITLE" -> {
                    once(file, lineNumber, key, title);
                    title = value;
                }
                case "PEPMASS" -> {
                    once(file, lineNumber, key, precursor);
                    String[] fields = value.strip().split("\\s+");
                    if (!DECIMAL.matcher(fields[0]).matches()) {
                        throw new SpectrumFileException(
                                file,
                                "line "
                                        + lineNumber
                                        + ": PEPMASS \""
                                        + value
                                        + "\" does not start with a decimal m/z");
                    }
                    precursor = fields[0];
                }
                case "CHARGE" -> {
                    once(file, lineNumber, key, charge.isPresent() ? "" : null);
                    Matcher matcher = CHARGE.matcher(value.strip());
                    if (!matcher.matches()) {
                        throw new SpectrumFileException(
                                file,
                                "line "
                                        + lineNumber
                                        + ": CHARGE \""
                                        + value
                                        + "\" is not one charge such as 2+");
                    }
                    int magnitude = Integer.parseInt(matcher.group(1));
                    charge = OptionalInt.of("-".equals(matcher.group(2)) ? -magnitude : magnitude);
                }
                case "RTINSECONDS" -> {
                    once(file, lineNumber, key, retentionTime == null ? null : "");
                    try {
                        retentionTime = new RetentionTime(value.strip(), RetentionTime.Unit.SECOND);
                    } catch (IllegalArgumentException notANumber) {
                        throw new SpectrumFileException(
                                file,
                                "line " + lineNumber + ": RTINSECONDS: " + notANumber.getMessage());
                    }
                }
                default -> {
                    // no other parameter changes a peak
                }
            }
        }

        private static void once(Path file, int lineNumber, String key, String previous)
                throws SpectrumFileException {
            if (previous != null) {
                throw new SpectrumFileException(
                        file, "line " + lineNumber + ": " + key + " appears twice in one entry");
            }
        }

        private static double number(Path file, int lineNumber, String text, String what)
                throws SpectrumFileException {
            if (!DECIMAL.matcher(text).matches()) {
                throw new SpectrumFileException(
                        file,
                        "line "
                                + lineNumber
                                + ": the "
                                + what
                                + " \""
                                + text
                                + "\" is not a decimal number");
            }
            double value = Double.parseDouble(text);
            if (Double.isInfinite(value)) {
                throw new SpectrumFileException(
                        file,
                        "line "
                                + lineNumber
                                + ": the "
                                + what
                                + " \""
                                + text
                                + "\" is beyond the range of a 64-bit float");
            }
            return value;
        }

        SourceSpectrum finish(Path file, int ordinal) throws SpectrumFileException {
            if (title == null || title.isEmpty()) {
                throw new SpectrumFileException(
                        file,
                        "the entry opened on line "
                                + firstLine
                                + " has no TITLE, so nothing could find its spectrum again");
            }
            return new SourceSpectrum(
                    title,
                    ordinal,
                    ordinal + 1,
                    SourceSpectrum.scanNumberOf(title),
                    Optional.ofNullable(precursor),
                    charge,
                    Optional.ofNullable(retentionTime),
                    new PeakList(
                            mz.toArray(),
                            PeakEncoding.FLOAT64,
                            intensity.toArray(),
                            PeakEncoding.FLOAT64));
        }
    }

    /** A growable array of doubles. */
    private static final class DoubleList {
        private double[] values = new double[64];
        private int size;

        void add(double value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        double[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
