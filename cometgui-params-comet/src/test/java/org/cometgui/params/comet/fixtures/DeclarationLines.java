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

package org.cometgui.params.comet.fixtures;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts parameter declarations in a Comet parameter file by a line rule -- <strong>not a
 * parser</strong>.
 *
 * <p>The rule: a name made of ASCII letters, digits and underscores starting at column one, then
 * optional blanks (spaces or tabs), then {@code =}. It is the rule Phase 00 counted 118 and 96 with
 * ({@code ^[A-Za-z0-9_]* *=}), tightened only to require a non-empty name. It exists so the {@code
 * R-PARAM-01} facts can be checked against the fixtures before the real parser exists; the parser
 * (Phase 06 unit 4) must agree with it and is not built on it.
 */
public final class DeclarationLines {

    private static final Pattern DECLARATION = Pattern.compile("^([A-Za-z0-9_]+)[ \\t]*=");

    private DeclarationLines() {}

    /**
     * The declared names, in file order, duplicates kept.
     *
     * @param lines the file's lines
     * @return each name that a line declares
     */
    public static List<String> names(List<String> lines) {
        List<String> names = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = DECLARATION.matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return List.copyOf(names);
    }
}
