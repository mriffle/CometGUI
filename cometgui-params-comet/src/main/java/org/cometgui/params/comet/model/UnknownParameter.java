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

package org.cometgui.params.comet.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A parameter an imported file declared that the curated schema does not model for the selected
 * Comet version. It is kept exactly as imported and written back ({@code R-PARAM-07}): never
 * silently dropped, removed only by an explicit {@link CometParameters#withoutUnknown(String)}.
 *
 * @param name the name as declared
 * @param value the value text as declared, trimmed; may be empty
 * @param inlineComment the comment after the value on its line, trimmed, if the line had one
 * @param comments the whole-line comments directly above the declaration -- up to the nearest blank
 *     line, declaration or version marker -- as written, without line terminators
 * @param line the 1-based line it was declared on in the imported file
 */
public record UnknownParameter(
        String name,
        String value,
        Optional<String> inlineComment,
        List<String> comments,
        int line) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Validates that the parameter can be written back and read back unchanged.
     *
     * @throws IllegalArgumentException if the name is not a parameter name, the value holds a
     *     {@code #}, a line break or white space at an end, the inline comment holds a line break
     *     or white space at an end, or a comment line is not a comment
     */
    public UnknownParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(inlineComment, "inlineComment");
        comments = List.copyOf(comments);
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("\"" + name + "\" is not a parameter name");
        }
        ParameterValueCodec.unwritableText(value)
                .ifPresent(
                        problem -> {
                            throw new IllegalArgumentException(
                                    "the value of " + name + " cannot be written: " + problem);
                        });
        inlineComment.ifPresent(
                text -> {
                    if (text.contains("\n") || text.contains("\r") || !text.equals(text.strip())) {
                        throw new IllegalArgumentException(
                                "the inline comment of "
                                        + name
                                        + " must be one line with no white space at either end");
                    }
                });
        for (String comment : comments) {
            if (!comment.stripLeading().startsWith("#")
                    || comment.contains("\n")
                    || comment.contains("\r")) {
                throw new IllegalArgumentException(
                        "\"" + comment + "\" above " + name + " is not one comment line");
            }
        }
    }

    /**
     * The comment lines above the declaration, immutable.
     *
     * @return the lines, in file order
     */
    @Override
    public List<String> comments() {
        return List.copyOf(comments);
    }
}
