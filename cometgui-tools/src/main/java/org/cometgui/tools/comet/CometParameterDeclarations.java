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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which parameter names a {@code comet.params} file Comet itself wrote declares.
 *
 * <p><strong>Not the parameter model.</strong> {@code R-PARAM-03}'s typed, versioned, validated
 * editor is phase 06's and lives in {@code org.cometgui.params.comet}; this reads names and nothing
 * else, because a capability probe only needs to know <em>which</em> parameters the binary said it
 * has. Values, types, tuples, defaults and validation are none of its business, and a probe that
 * parsed them would be a second parameter reader to keep in step with the first.
 *
 * <p>The file is one the binary wrote seconds earlier in the probe's own temporary directory, which
 * is why the reader is deliberately unforgiving: a line declares a parameter when it begins at
 * column one with a name and an {@code =}. Comet's own output puts comments after a {@code #} and
 * indents its section headers, so nothing else in the file looks like a declaration.
 */
public final class CometParameterDeclarations {

    /**
     * A declaration: a name at column one, optional spaces, then {@code =}.
     *
     * <p>Anchored at the start of the line on purpose. Comet's own output indents its section
     * headers and puts explanatory text after a {@code #}, and a pattern that searched anywhere in
     * the line would read a parameter name out of a comment that mentions one.
     */
    public static final Pattern DECLARATION = Pattern.compile("^([A-Za-z0-9_]+)[ \\t]*=");

    private CometParameterDeclarations() {}

    /**
     * Reads the declared parameter names out of a {@code comet.params} file.
     *
     * @param file the file Comet wrote
     * @return the names, in the order the file declares them, immutable
     * @throws IOException if the file cannot be read
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static Set<String> readFrom(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Set<String> declared = new LinkedHashSet<>();
        for (String line : lines) {
            Matcher matcher = DECLARATION.matcher(line);
            if (matcher.find()) {
                declared.add(matcher.group(1));
            }
        }
        return Collections.unmodifiableSet(declared);
    }
}
