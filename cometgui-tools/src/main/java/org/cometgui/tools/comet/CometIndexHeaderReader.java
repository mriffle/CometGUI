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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.params.CometIndexDescription;
import org.cometgui.domain.params.CometIndexDescription.Enzyme;
import org.cometgui.domain.params.CometIndexDescription.VariableMod;
import org.cometgui.domain.run.IndexMode;

/**
 * Reads the self-description of an existing Comet {@code .idx} file ({@code R-CMT-07}).
 *
 * <p>A versioned Comet index (Comet 2026.02.2 writes format 4, Comet 2026.03.0 format 5) begins
 * with a text header: a first line {@code Comet index database v<N>. Comet version <release>}, then
 * one {@code Key: value} line per recorded option, then an <strong>empty line</strong>, after which
 * come the protein names, padded with NUL bytes, and the binary index. Both releases' own readers
 * stop at that empty line ({@code CometPeptideIndex.cpp}, {@code ParsePeptideIndexHeader}, at
 * {@code v2026.03.0} and {@code v2026.02.2}), and so does this one: it reads at most {@value
 * #HEADER_LIMIT} bytes from the start of the file -- a real header is about 1.5 KB -- and nothing
 * after the empty line, so a multi-gigabyte index costs one small read.
 *
 * <p>The reader is strict on purpose, because what it returns is compared with a search before
 * Comet starts and a line it skipped would be an option nobody checked. It refuses, with a {@link
 * CometIndexHeaderException} naming the file: a missing or unreadable file; a first line that is
 * not a versioned Comet index's (an index written by Comet before the unified format, a FASTA, any
 * other file); a header with no empty line within the limit, or holding a NUL byte; a line whose
 * key it does not know (a newer Comet may record an option this reader cannot compare), a key given
 * twice, or a required key missing; and a value that is not what Comet writes for its key. Lines
 * may end in LF or CRLF.
 *
 * <p>The keys and their values, as Comet 2026.03.0 writes them ({@code WritePeptideIndex}): {@code
 * IndexSearchType: fragment ion index} or {@code peptide index}; {@code InputDB:} the FASTA as
 * given; {@code MassRange:} two decimals; {@code LengthRange:} two integers; {@code MassType:} two
 * integers; {@code DecoySearch:} an integer; {@code DecoyPrefix:} the prefix; {@code Enzyme:} and
 * {@code Enzyme2:} {@code name [sense cut nocut]}; {@code NumEnzymeTermini:}, {@code
 * AllowedMissedCleavage:} integers; {@code ClipNtermMethionine:} 0 or 1; {@code NumPeptides:} a
 * count; {@code StaticMod:} {@value CometIndexDescription#STATIC_MOD_COUNT} decimals; {@code
 * VariableMod:} one {@code residues:mass:loss:loss2:max} token per slot, format 5 adding {@code
 * :distance:terminus}; {@code ProteinModList:} 0 or 1; {@code RequireVariableMod:} one integer more
 * than there are slots; {@code MaxVariableModsInPeptide:} an integer. {@code InputDB:}, {@code
 * DecoyPrefix:}, {@code NumEnzymeTermini:}, {@code AllowedMissedCleavage:} and {@code
 * ClipNtermMethionine:} are optional -- format 4 has none of the last four -- and every other key
 * is required.
 */
public final class CometIndexHeaderReader {

    /** The most bytes read from the start of a file in search of the end of its header. */
    public static final int HEADER_LIMIT = 65536;

    /** The first line of a versioned Comet index. */
    static final Pattern FIRST_LINE =
            Pattern.compile("Comet index database v([0-9]{1,9})\\.\\s+Comet version (\\S.*)");

    /** A header line: a key, a colon, and the value. */
    private static final Pattern LINE = Pattern.compile("([A-Za-z0-9]+):(.*)");

    /** An enzyme: {@code name [sense cut nocut]}. */
    private static final Pattern ENZYME =
            Pattern.compile("\\s*(\\S+) \\[(-?[0-9]+) (\\S+) (\\S+)\\]\\s*");

    /** A decimal as Comet's {@code %lf} writes it. */
    private static final Pattern DECIMAL = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");

    /** An integer as Comet's {@code %d} writes it. */
    private static final Pattern INTEGER = Pattern.compile("-?[0-9]{1,9}");

    static final String INDEX_SEARCH_TYPE = "IndexSearchType";
    static final String INPUT_DB = "InputDB";
    static final String MASS_RANGE = "MassRange";
    static final String LENGTH_RANGE = "LengthRange";
    static final String MASS_TYPE = "MassType";
    static final String DECOY_SEARCH = "DecoySearch";
    static final String DECOY_PREFIX = "DecoyPrefix";
    static final String ENZYME_KEY = "Enzyme";
    static final String ENZYME2_KEY = "Enzyme2";
    static final String ENZYME_TERMINI = "NumEnzymeTermini";
    static final String MISSED_CLEAVAGE = "AllowedMissedCleavage";
    static final String CLIP_METHIONINE = "ClipNtermMethionine";
    static final String PEPTIDES = "NumPeptides";
    static final String STATIC_MOD = "StaticMod";
    static final String VARIABLE_MOD = "VariableMod";
    static final String PROTEIN_MOD_LIST = "ProteinModList";
    static final String REQUIRE_VARIABLE_MOD = "RequireVariableMod";
    static final String MAX_VARIABLE_MODS = "MaxVariableModsInPeptide";

    /** The keys every header has. */
    static final Set<String> REQUIRED =
            Set.of(
                    INDEX_SEARCH_TYPE,
                    MASS_RANGE,
                    LENGTH_RANGE,
                    MASS_TYPE,
                    DECOY_SEARCH,
                    ENZYME_KEY,
                    ENZYME2_KEY,
                    PEPTIDES,
                    STATIC_MOD,
                    VARIABLE_MOD,
                    PROTEIN_MOD_LIST,
                    REQUIRE_VARIABLE_MOD,
                    MAX_VARIABLE_MODS);

    /** The keys a header may have. */
    static final Set<String> OPTIONAL =
            Set.of(INPUT_DB, DECOY_PREFIX, ENZYME_TERMINI, MISSED_CLEAVAGE, CLIP_METHIONINE);

    private CometIndexHeaderReader() {}

    /**
     * Reads an index file's header.
     *
     * @param index the {@code .idx} file
     * @return what the header says
     * @throws CometIndexHeaderException if the file is missing or unreadable, is not a versioned
     *     Comet index, or its header is not one this reader can vouch for
     */
    public static CometIndexDescription read(Path index) throws CometIndexHeaderException {
        Objects.requireNonNull(index, "index");
        byte[] head;
        try (InputStream in = Files.newInputStream(index)) {
            head = in.readNBytes(HEADER_LIMIT);
        } catch (NoSuchFileException missing) {
            throw new CometIndexHeaderException(index, "does not exist", missing);
        } catch (AccessDeniedException denied) {
            throw new CometIndexHeaderException(index, "cannot be read: permission denied", denied);
        } catch (IOException unreadable) {
            throw new CometIndexHeaderException(
                    index, "cannot be read: " + unreadable.getMessage(), unreadable);
        }
        return parse(index, head);
    }

    /**
     * Parses the first bytes of an index file.
     *
     * @param index the file, for messages and the description
     * @param head at most {@value #HEADER_LIMIT} bytes from the start of the file
     * @return what the header says
     * @throws CometIndexHeaderException if the bytes do not begin with a header this reader can
     *     vouch for
     */
    static CometIndexDescription parse(Path index, byte[] head) throws CometIndexHeaderException {
        List<String> lines = headerLines(index, head);
        String first = lines.get(0);
        Matcher matcher = firstLine(index, first);
        Map<String, String> values = new LinkedHashMap<>();
        for (int number = 2; number <= lines.size(); number++) {
            String line = lines.get(number - 1);
            Matcher keyed = LINE.matcher(line);
            if (!keyed.matches()) {
                throw failure(
                        index,
                        "line "
                                + number
                                + ", \""
                                + shown(line)
                                + "\", is not a"
                                + " \"Key: value\" header line");
            }
            String key = keyed.group(1);
            if (!REQUIRED.contains(key) && !OPTIONAL.contains(key)) {
                throw failure(
                        index,
                        "line "
                                + number
                                + " records \""
                                + key
                                + ":\", which this reader does not know, so CometGUI cannot"
                                + " check it against the search");
            }
            if (values.put(key, keyed.group(2)) != null) {
                throw failure(index, "line " + number + " records \"" + key + ":\" a second time");
            }
        }
        for (String key : REQUIRED) {
            if (!values.containsKey(key)) {
                throw failure(index, "has no \"" + key + ":\" line in its header");
            }
        }
        Header header = new Header(index, values);
        List<VariableMod> slots = header.variableMods();
        return new CometIndexDescription(
                index,
                Integer.parseInt(matcher.group(1)),
                first,
                matcher.group(2).strip(),
                header.type(),
                header.optional(INPUT_DB).map(String::strip).filter(text -> !text.isEmpty()),
                new CometIndexDescription.MassRange(
                        header.decimals(MASS_RANGE, 2).get(0),
                        header.decimals(MASS_RANGE, 2).get(1)),
                new CometIndexDescription.LengthRange(
                        header.integers(LENGTH_RANGE, 2).get(0),
                        header.integers(LENGTH_RANGE, 2).get(1)),
                header.integers(MASS_TYPE, 2).get(0),
                header.integers(MASS_TYPE, 2).get(1),
                header.integer(DECOY_SEARCH),
                header.optional(DECOY_PREFIX).map(String::strip).filter(text -> !text.isEmpty()),
                header.enzyme(ENZYME_KEY),
                header.enzyme(ENZYME2_KEY),
                header.optionalInteger(ENZYME_TERMINI),
                header.optionalInteger(MISSED_CLEAVAGE),
                header.optional(CLIP_METHIONINE).isPresent()
                        ? Optional.of(header.flag(CLIP_METHIONINE))
                        : Optional.empty(),
                header.count(PEPTIDES),
                header.decimals(STATIC_MOD, CometIndexDescription.STATIC_MOD_COUNT),
                slots,
                header.flag(PROTEIN_MOD_LIST),
                header.integers(REQUIRE_VARIABLE_MOD, slots.size() + 1).get(0),
                header.integer(MAX_VARIABLE_MODS));
    }

    /**
     * The header's lines, the first line first, without the empty line that ends them.
     *
     * @throws CometIndexHeaderException if the bytes are empty, do not begin with a versioned Comet
     *     index's first line, hold a NUL byte before the end of the header, or hold no empty line
     */
    private static List<String> headerLines(Path index, byte[] head)
            throws CometIndexHeaderException {
        if (head.length == 0) {
            throw new CometIndexHeaderException(index, "is empty, not a Comet index", null);
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int at = 0; at < head.length; at++) {
            if (head[at] != '\n') {
                continue;
            }
            String line = text(head, start, at);
            if (lines.isEmpty()) {
                firstLine(index, line);
            } else if (line.isEmpty()) {
                return lines;
            }
            if (line.indexOf('\0') >= 0) {
                throw failure(
                        index,
                        "line "
                                + (lines.size() + 1)
                                + " holds a NUL byte, before the empty line that ends a Comet index"
                                + " header");
            }
            lines.add(line);
            start = at + 1;
        }
        if (lines.isEmpty()) {
            firstLine(index, text(head, 0, head.length));
        }
        throw failure(
                index,
                "it has no empty line ending it within the first " + HEADER_LIMIT + " bytes");
    }

    /** One line of the bytes, without a carriage return before its line feed. */
    private static String text(byte[] head, int start, int end) {
        int stop = end > start && head[end - 1] == '\r' ? end - 1 : end;
        return new String(head, start, stop - start, StandardCharsets.ISO_8859_1);
    }

    /**
     * Matches a versioned Comet index's first line.
     *
     * @throws CometIndexHeaderException naming the line if it is not one
     */
    private static Matcher firstLine(Path index, String line) throws CometIndexHeaderException {
        Matcher matcher = FIRST_LINE.matcher(line);
        if (!matcher.matches()) {
            throw new CometIndexHeaderException(
                    index,
                    "is not a versioned Comet index: its first line is \""
                            + shown(line)
                            + "\", and a versioned Comet index begins \"Comet index database"
                            + " v<N>.  Comet version <release>\"; rebuild the index from its"
                            + " FASTA",
                    null);
        }
        return matcher;
    }

    private static CometIndexHeaderException failure(Path index, String problem) {
        return new CometIndexHeaderException(
                index, "has a header CometGUI cannot read: " + problem, null);
    }

    /** At most the first 80 characters of a text, for a message, with control characters shown. */
    private static String shown(String text) {
        StringBuilder shown = new StringBuilder();
        for (int index = 0; index < text.length() && index < 80; index++) {
            char c = text.charAt(index);
            shown.append(c < ' ' || c == 127 ? '?' : c);
        }
        return text.length() > 80 ? shown + "..." : shown.toString();
    }

    /** The header's values by key, read into their types. */
    private record Header(Path index, Map<String, String> values) {

        Optional<String> optional(String key) {
            return Optional.ofNullable(values.get(key));
        }

        private String value(String key) {
            return values.get(key);
        }

        private CometIndexHeaderException wrong(String key, String expected) {
            return failure(
                    index,
                    "\""
                            + key
                            + ":"
                            + shown(value(key))
                            + "\" is not "
                            + expected
                            + ", which is what Comet writes there");
        }

        IndexMode type() throws CometIndexHeaderException {
            return switch (value(INDEX_SEARCH_TYPE).strip()) {
                case "fragment ion index" -> IndexMode.FRAGMENT_ION;
                case "peptide index" -> IndexMode.PEPTIDE;
                default ->
                        throw wrong(
                                INDEX_SEARCH_TYPE, "\"fragment ion index\" or \"peptide index\"");
            };
        }

        private List<String> tokens(String key) {
            String text = value(key).strip();
            return text.isEmpty() ? List.of() : List.of(text.split("\\s+"));
        }

        List<BigDecimal> decimals(String key, int count) throws CometIndexHeaderException {
            List<String> tokens = tokens(key);
            if (tokens.size() != count) {
                throw wrong(key, count + " decimal number(s)");
            }
            List<BigDecimal> numbers = new ArrayList<>();
            for (String token : tokens) {
                if (!DECIMAL.matcher(token).matches()) {
                    throw wrong(key, count + " decimal number(s)");
                }
                numbers.add(new BigDecimal(token));
            }
            return numbers;
        }

        List<Integer> integers(String key, int count) throws CometIndexHeaderException {
            List<String> tokens = tokens(key);
            if (tokens.size() != count) {
                throw wrong(key, count + " integer(s)");
            }
            List<Integer> numbers = new ArrayList<>();
            for (String token : tokens) {
                if (!INTEGER.matcher(token).matches()) {
                    throw wrong(key, count + " integer(s)");
                }
                numbers.add(Integer.parseInt(token));
            }
            return numbers;
        }

        int integer(String key) throws CometIndexHeaderException {
            return integers(key, 1).get(0);
        }

        OptionalInt optionalInteger(String key) throws CometIndexHeaderException {
            return values.containsKey(key) ? OptionalInt.of(integer(key)) : OptionalInt.empty();
        }

        boolean flag(String key) throws CometIndexHeaderException {
            int value = integer(key);
            if (value != 0 && value != 1) {
                throw wrong(key, "0 or 1");
            }
            return value == 1;
        }

        long count(String key) throws CometIndexHeaderException {
            String text = value(key).strip();
            if (!text.matches("[0-9]{1,18}")) {
                throw wrong(key, "a count");
            }
            return Long.parseLong(text);
        }

        Enzyme enzyme(String key) throws CometIndexHeaderException {
            Matcher matcher = ENZYME.matcher(value(key));
            if (!matcher.matches() || !INTEGER.matcher(matcher.group(2)).matches()) {
                throw wrong(key, "an enzyme written \"name [sense cut nocut]\"");
            }
            return new Enzyme(
                    matcher.group(1),
                    Integer.parseInt(matcher.group(2)),
                    matcher.group(3),
                    matcher.group(4));
        }

        List<VariableMod> variableMods() throws CometIndexHeaderException {
            List<String> tokens = tokens(VARIABLE_MOD);
            if (tokens.isEmpty()) {
                throw wrong(VARIABLE_MOD, "one residues:mass:loss:loss2:max token per slot");
            }
            List<String> requirements = tokens(REQUIRE_VARIABLE_MOD);
            if (requirements.size() != tokens.size() + 1) {
                throw wrong(REQUIRE_VARIABLE_MOD, (tokens.size() + 1) + " integer(s)");
            }
            List<VariableMod> slots = new ArrayList<>();
            for (int slot = 0; slot < tokens.size(); slot++) {
                String[] fields = tokens.get(slot).split(":", -1);
                boolean positioned = fields.length == 7;
                if ((fields.length != 5 && !positioned)
                        || fields[0].isEmpty()
                        || !DECIMAL.matcher(fields[1]).matches()
                        || !DECIMAL.matcher(fields[2]).matches()
                        || !DECIMAL.matcher(fields[3]).matches()
                        || !INTEGER.matcher(fields[4]).matches()
                        || (positioned
                                && (!INTEGER.matcher(fields[5]).matches()
                                        || !INTEGER.matcher(fields[6]).matches()))) {
                    throw wrong(
                            VARIABLE_MOD,
                            "residues:mass:loss:loss2:max[:distance:terminus] in slot "
                                    + (slot + 1));
                }
                String requirement = requirements.get(slot + 1);
                if (!INTEGER.matcher(requirement).matches()) {
                    throw wrong(REQUIRE_VARIABLE_MOD, (tokens.size() + 1) + " integer(s)");
                }
                slots.add(
                        new VariableMod(
                                fields[0],
                                new BigDecimal(fields[1]),
                                new BigDecimal(fields[2]),
                                new BigDecimal(fields[3]),
                                Integer.parseInt(fields[4]),
                                positioned
                                        ? OptionalInt.of(Integer.parseInt(fields[5]))
                                        : OptionalInt.empty(),
                                positioned
                                        ? OptionalInt.of(Integer.parseInt(fields[6]))
                                        : OptionalInt.empty(),
                                Integer.parseInt(requirement)));
            }
            return slots;
        }
    }
}
