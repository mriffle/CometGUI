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

package org.cometgui.app.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A reader of {@code provenance.rst} written for the gate tests from the layout {@code
 * ProvenanceReportWriter} documents, sharing no code with it: sections underlined with {@code =},
 * subsections with {@code -}; a field list per section ({@code :Name: ``value``}, {@code (none)}
 * where there is no value, several literals separated by commas, or a bullet list under the field
 * -- {@code * ``value``}, or {@code * ``NAME`` = ``value``} for an environment); and at most one
 * {@code list-table} per section, read row by row. A value is an inline literal or, where
 * reStructuredText cannot carry one, a double-quoted string with backslash escapes.
 *
 * <p>Sections are keyed by title, a subsection by {@code section/subsection}. A field is read as
 * the list of its values: one for a single value, none for {@code (none)}, and each element of a
 * comma-separated or bulleted list. An environment entry reads as {@code NAME=value}.
 */
final class IndependentRst {

    /** What the report writes for a field with no value. */
    static final String ABSENT = "(none)";

    private static final Pattern FIELD = Pattern.compile("^:([^:]+):(?: (.*))?$");

    private static final Pattern BULLET = Pattern.compile("^   \\* (.*)$");

    private static final Pattern ROW_START = Pattern.compile("^   \\* - (.*)$");

    private static final Pattern ROW_CELL = Pattern.compile("^     - (.*)$");

    /** Each section's fields, in order, by section key. */
    private final Map<String, Map<String, List<String>>> fields = new LinkedHashMap<>();

    /** Each section's table rows, the header (plain text) first, by section key. */
    private final Map<String, List<List<String>>> tables = new LinkedHashMap<>();

    private IndependentRst() {}

    /** Each section's fields, in order, by section key. */
    Map<String, Map<String, List<String>>> fields() {
        return fields;
    }

    /** Each section's table rows, the header first, by section key. */
    Map<String, List<List<String>>> tables() {
        return tables;
    }

    /**
     * Reads a report.
     *
     * @param text the whole document
     * @return what it holds
     * @throws IllegalArgumentException naming the line that does not fit the layout
     */
    static IndependentRst parse(String text) {
        IndependentRst report = new IndependentRst();
        String[] lines = text.split("\n", -1);
        String section = null;
        String key = null;
        String field = null;
        boolean inTable = false;
        int index = -1;
        while (++index < lines.length) {
            String line = lines[index];
            String next = index + 1 < lines.length ? lines[index + 1] : "";
            if (!line.isEmpty()
                    && next.equals("=".repeat(line.length()))
                    && !line.startsWith("=")) {
                boolean overlined = index > 0 && lines[index - 1].equals(next);
                if (!overlined) {
                    section = line;
                    key = line;
                    report.fields.put(key, new LinkedHashMap<>());
                }
                index++;
                field = null;
                inTable = false;
                continue;
            }
            if (!line.isEmpty() && next.equals("-".repeat(line.length())) && section != null) {
                key = section + "/" + line;
                report.fields.put(key, new LinkedHashMap<>());
                index++;
                field = null;
                inTable = false;
                continue;
            }
            if (key == null || line.isEmpty() || line.equals("=".repeat(line.length()))) {
                continue;
            }
            if (".. list-table::".equals(line)) {
                inTable = true;
                report.tables.put(key, new ArrayList<>());
                field = null;
                continue;
            }
            if (inTable) {
                Matcher start = ROW_START.matcher(line);
                Matcher cell = ROW_CELL.matcher(line);
                List<List<String>> rows = report.tables.get(key);
                if (start.matches()) {
                    List<String> row = new ArrayList<>();
                    row.add(rows.isEmpty() ? start.group(1) : cell(start.group(1)));
                    rows.add(row);
                } else if (cell.matches() && !rows.isEmpty()) {
                    rows.get(rows.size() - 1)
                            .add(rows.size() == 1 ? cell.group(1) : cell(cell.group(1)));
                } else if (!line.startsWith("   :")) {
                    throw new IllegalArgumentException("not a table line: " + line);
                }
                continue;
            }
            Matcher named = FIELD.matcher(line);
            if (named.matches()) {
                field = named.group(1);
                String rest = named.group(2);
                List<String> values = new ArrayList<>();
                if (rest != null && !rest.equals(ABSENT)) {
                    values.addAll(literals(rest));
                }
                if (report.fields.get(key).put(field, values) != null) {
                    throw new IllegalArgumentException("the field " + field + " twice in " + key);
                }
                continue;
            }
            Matcher bullet = BULLET.matcher(line);
            if (bullet.matches() && field != null) {
                List<String> parts = literals(bullet.group(1));
                String value =
                        parts.size() == 1
                                ? parts.get(0)
                                : parts.size() == 2 && bullet.group(1).contains("`` = ")
                                        ? parts.get(0) + "=" + parts.get(1)
                                        : null;
                if (value == null) {
                    throw new IllegalArgumentException("not a bullet value: " + line);
                }
                report.fields.get(key).get(field).add(value);
                continue;
            }
            if (section == null) {
                continue;
            }
            if (report.fields.get(key).isEmpty() && !report.tables.containsKey(key)) {
                continue; // a paragraph before the first field
            }
            if (line.startsWith("No ")
                    || line.startsWith("Generated")
                    || line.startsWith("Values")) {
                continue;
            }
            throw new IllegalArgumentException("a line outside the layout in " + key + ": " + line);
        }
        return report;
    }

    private static String cell(String text) {
        List<String> values = literals(text);
        if (values.size() != 1) {
            throw new IllegalArgumentException("a cell holds one value, not: " + text);
        }
        return values.get(0);
    }

    /** The values of a text holding one or more literals or quoted strings, comma-separated. */
    private static List<String> literals(String text) {
        List<String> values = new ArrayList<>();
        int at = 0;
        while (at < text.length()) {
            if (text.startsWith("``", at)) {
                int end = text.indexOf("``", at + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("an unclosed literal: " + text);
                }
                values.add(text.substring(at + 2, end));
                at = end + 2;
            } else if (text.charAt(at) == '"') {
                StringBuilder value = new StringBuilder();
                at++;
                while (text.charAt(at) != '"') {
                    char c = text.charAt(at++);
                    if (c != '\\') {
                        value.append(c);
                        continue;
                    }
                    char escape = text.charAt(at++);
                    switch (escape) {
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> {
                            value.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                            at += 4;
                        }
                        default -> value.append(escape);
                    }
                }
                values.add(value.toString());
                at++;
            } else {
                throw new IllegalArgumentException("not a literal at " + at + ": " + text);
            }
            if (text.startsWith(", ", at) || text.startsWith(" = ", at)) {
                at += text.startsWith(", ", at) ? 2 : 3;
            } else if (at != text.length()) {
                throw new IllegalArgumentException("text after a literal: " + text);
            }
        }
        return values;
    }
}
