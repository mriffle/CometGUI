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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The one place that knows how a {@code comet.params} line is shaped.
 *
 * <p>It classifies every line into a {@link ParamsLine} and interprets nothing: schema discovery
 * reads names and default texts from it today, and the typed parser is built on it. The rules
 * follow what Comet 2026.02.2's own reader ({@code LoadParameters} in {@code Comet.cpp}) does, made
 * strict where Comet is silently lenient, so that nothing is ever dropped:
 *
 * <ul>
 *   <li>A line starting {@code # comet_version } is the {@link ParamsLine.VersionMarker}.
 *   <li>A line whose first non-blank character is {@code #} is a {@link ParamsLine.Comment}.
 *   <li>Before {@code [COMET_ENZYME_INFO]}, a line with {@code =} before any {@code #} is a {@link
 *       ParamsLine.Declaration}: the name is the text before the first {@code =}, trimmed, and must
 *       be a parameter name; the value runs to the first {@code #}. Comet would read {@code a b =
 *       1} as {@code a}; here it is malformed.
 *   <li>The exact line {@code [COMET_ENZYME_INFO]} starts the table. After it, Comet reads no more
 *       parameters, so a declaration there is malformed rather than silently ignored; a row must be
 *       {@code number.} followed by four fields.
 *   <li>Anything else is {@link ParamsLine.Malformed}, with its line number and a reason.
 * </ul>
 *
 * <p>Lines are split on {@code \n} only. A {@code \r} before it stays in {@link ParamsLine#text()}
 * and is ignored when classifying, so a CRLF file classifies as its LF twin does and its bytes are
 * still recoverable.
 */
public final class ParamsLineReader {

    /** The first characters of the version marker line. */
    public static final String MARKER_PREFIX = "# comet_version ";

    /** The line that starts the enzyme table. */
    public static final String ENZYME_HEADER = "[COMET_ENZYME_INFO]";

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Pattern ENZYME_ROW =
            Pattern.compile("[0-9]+\\.[ \\t]+\\S+[ \\t]+\\S+[ \\t]+\\S+[ \\t]+\\S+[ \\t]*");

    private ParamsLineReader() {}

    /**
     * Classifies every line of a text.
     *
     * @param text the whole file, decoded
     * @return its lines, in order, none dropped
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static ParamsText read(String text) {
        Objects.requireNonNull(text, "text");
        List<ParamsLine> lines = new ArrayList<>();
        boolean inEnzymeTable = false;
        int start = 0;
        int number = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            String raw = end < 0 ? text.substring(start) : text.substring(start, end);
            number++;
            ParamsLine line = classify(number, raw, inEnzymeTable);
            if (line instanceof ParamsLine.EnzymeHeader) {
                inEnzymeTable = true;
            }
            lines.add(line);
            start = end < 0 ? text.length() : end + 1;
        }
        return new ParamsText(lines, text.endsWith("\n"));
    }

    private static ParamsLine classify(int number, String raw, boolean inEnzymeTable) {
        String content = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
        if (content.isBlank()) {
            return new ParamsLine.Blank(number, raw);
        }
        if (content.startsWith(MARKER_PREFIX)) {
            return new ParamsLine.VersionMarker(number, raw);
        }
        if (content.stripLeading().startsWith("#")) {
            return new ParamsLine.Comment(number, raw);
        }
        if (content.stripTrailing().equals(ENZYME_HEADER)) {
            return inEnzymeTable
                    ? new ParamsLine.Malformed(
                            number,
                            raw,
                            "a second " + ENZYME_HEADER + " line; the table has already started")
                    : new ParamsLine.EnzymeHeader(number, raw);
        }
        return inEnzymeTable ? enzymeRow(number, raw, content) : declaration(number, raw, content);
    }

    private static ParamsLine enzymeRow(int number, String raw, String content) {
        if (ENZYME_ROW.matcher(content).matches()) {
            return new ParamsLine.EnzymeRow(number, raw);
        }
        return new ParamsLine.Malformed(
                number,
                raw,
                "after "
                        + ENZYME_HEADER
                        + " only enzyme rows (\"number. name sense cut no-cut\"), comments and"
                        + " blank lines may follow; Comet reads no parameter declared here");
    }

    private static ParamsLine declaration(int number, String raw, String content) {
        int equals = content.indexOf('=');
        int hash = content.indexOf('#');
        /*
         * TWO KNOWN EQUIVALENT MUTANTS, recorded rather than left for someone to rediscover. PIT
         * turns `hash >= 0` into `hash > 0` and `hash < equals` into `hash <= equals`, and both
         * survive because neither boundary is reachable: a line whose first character is '#' was
         * already classified as a comment by classify(), so hash is never 0 here, and one
         * character cannot be both '#' and '=', so hash never equals equals.
         */
        if (equals < 0 || (hash >= 0 && hash < equals)) {
            return new ParamsLine.Malformed(
                    number,
                    raw,
                    "not a comment, a blank line or a declaration: there is no '=' before any"
                            + " '#'");
        }
        String name = content.substring(0, equals).strip();
        if (!NAME.matcher(name).matches()) {
            return new ParamsLine.Malformed(
                    number,
                    raw,
                    "\"" + name + "\" before '=' is not a parameter name (letters, digits, '_')");
        }
        String rest = content.substring(equals + 1);
        int comment = rest.indexOf('#');
        String value = (comment < 0 ? rest : rest.substring(0, comment)).strip();
        Optional<String> inline =
                comment < 0 ? Optional.empty() : Optional.of(rest.substring(comment + 1).strip());
        return new ParamsLine.Declaration(number, raw, name, value, inline);
    }
}
