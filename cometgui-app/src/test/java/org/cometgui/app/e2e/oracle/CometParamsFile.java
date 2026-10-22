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

package org.cometgui.app.e2e.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@code comet.params} file as Comet reads it, read by the test's own code: no product parser is
 * on the path (P14-7), so canonical scenario step 17 -- "the generated {@code comet.params}
 * contains exactly the GUI-selected values" -- compares the file with the screen through a third
 * party.
 *
 * <h2>The rules, as Comet applies them</h2>
 *
 * <ul>
 *   <li>Everything from a {@code #} to the end of the line is a comment.
 *   <li>A line holding {@code =} outside the enzyme section is a parameter: the name is the text
 *       before the first {@code =}, trimmed, and the value is the text after it, up to the comment,
 *       trimmed -- kept <em>exactly</em> as written otherwise, so {@code 20.0} is not {@code 20}
 *       and {@code 15.994915 M 0 3 -1 0 0 0.0} keeps its single spaces.
 *   <li>The line {@code [COMET_ENZYME_INFO]} starts the enzyme table, which runs to the end of the
 *       file; each non-blank line of it is kept as written, without its line terminator.
 *   <li>A blank or comment-only line is nothing. Any other line is <em>unparsed</em>, and reported
 *       rather than ignored, so a test can require there to be none.
 * </ul>
 *
 * <p>A name given twice is kept, in {@link #duplicates()}, with the first value in {@link
 * #values()} -- a generated file should have none, and a test that asserts so needs to see them.
 *
 * @param values every parameter's value text, by name, in file order
 * @param lines the line number (from 1) on which each parameter was given
 * @param duplicates the names given more than once, each with every line it appears on
 * @param enzymes the enzyme table's lines, as written
 * @param unparsed the lines that are none of the above, each prefixed with its line number
 */
public record CometParamsFile(
        Map<String, String> values,
        Map<String, Integer> lines,
        Map<String, List<Integer>> duplicates,
        List<String> enzymes,
        List<String> unparsed) {

    /** The line that starts the enzyme table. */
    public static final String ENZYME_SECTION = "[COMET_ENZYME_INFO]";

    /** The parts, copied; map iteration keeps file order. */
    public CometParamsFile {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        lines = Collections.unmodifiableMap(new LinkedHashMap<>(lines));
        Map<String, List<Integer>> copied = new LinkedHashMap<>();
        duplicates.forEach((name, at) -> copied.put(name, List.copyOf(at)));
        duplicates = Collections.unmodifiableMap(copied);
        enzymes = List.copyOf(enzymes);
        unparsed = List.copyOf(unparsed);
    }

    /**
     * Reads a file as UTF-8.
     *
     * @param file the {@code comet.params}
     * @return what it says
     * @throws IOException if it cannot be read
     */
    public static CometParamsFile read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Reads a file's text.
     *
     * @param text the whole file
     * @return what it says
     */
    public static CometParamsFile parse(String text) {
        Objects.requireNonNull(text, "text");
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Integer> lines = new LinkedHashMap<>();
        Map<String, List<Integer>> duplicates = new LinkedHashMap<>();
        List<String> enzymes = new ArrayList<>();
        List<String> unparsed = new ArrayList<>();
        boolean inEnzymes = false;
        int number = 0;
        for (String raw : text.split("\n", -1)) {
            number++;
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (inEnzymes) {
                if (!line.isBlank()) {
                    enzymes.add(line);
                }
                continue;
            }
            int hash = line.indexOf('#');
            String content = (hash < 0 ? line : line.substring(0, hash)).strip();
            if (content.isEmpty()) {
                continue;
            }
            if (content.equals(ENZYME_SECTION)) {
                inEnzymes = true;
                continue;
            }
            int equals = content.indexOf('=');
            if (equals <= 0) {
                unparsed.add(number + ": " + line);
                continue;
            }
            String name = content.substring(0, equals).strip();
            String value = content.substring(equals + 1).strip();
            if (values.containsKey(name)) {
                duplicates
                        .computeIfAbsent(name, first -> new ArrayList<>(List.of(lines.get(name))))
                        .add(number);
                continue;
            }
            values.put(name, value);
            lines.put(name, number);
        }
        return new CometParamsFile(values, lines, duplicates, enzymes, unparsed);
    }

    /**
     * One parameter's value text.
     *
     * @param name the parameter
     * @return its value, exactly as written
     * @throws AssertionError if the file does not set it
     */
    public String value(String name) {
        String value = values.get(name);
        if (value == null) {
            throw new AssertionError(
                    "comet.params sets no " + name + "; it sets " + values.keySet());
        }
        return value;
    }
}
