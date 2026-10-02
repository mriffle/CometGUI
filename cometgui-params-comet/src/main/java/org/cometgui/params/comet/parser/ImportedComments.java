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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The comment structure of an imported {@code comet.params} file, preserved as it was read ({@code
 * R-PARAM-05}): the {@code # comet_version} marker line, the block comments between parameters,
 * each modelled parameter's inline trailing comment, and the comment lines before and inside the
 * enzyme table.
 *
 * <p>Lines are kept verbatim, without their line terminator (a CRLF file's {@code \r} removed);
 * blank lines inside a block are kept as empty strings so the block's shape survives. The canonical
 * writer does not re-emit these -- it regenerates the marker and writes the curated inline comments
 * -- but nothing imported is lost: the editor's Expert mode and a diff can show them. Unknown
 * parameters carry their own comments on {@link org.cometgui.params.comet.model.UnknownParameter},
 * because those are written back.
 *
 * @param marker the imported {@code # comet_version} line, if the file had one
 * @param parameters for every modelled parameter the file declared, by name in file order, its
 *     block comment and inline comment
 * @param beforeEnzymeTable the comment and blank lines between the last declaration and {@code
 *     [COMET_ENZYME_INFO]}
 * @param inEnzymeTable the comment and blank lines after {@code [COMET_ENZYME_INFO]}, in order
 */
public record ImportedComments(
        Optional<String> marker,
        Map<String, ParameterComments> parameters,
        List<String> beforeEnzymeTable,
        List<String> inEnzymeTable) {

    /**
     * The comments of one declared parameter.
     *
     * @param above every comment and blank line between the previous declaration (or the start of
     *     the file) and this one, verbatim, except the previous declaration's continuation lines;
     *     the version marker line is not included
     * @param inline the comment after the value on the declaration's line, trimmed, if there was
     *     one; a bare {@code #} gives an empty string
     * @param continuation indented whole-line comments directly under the declaration, verbatim --
     *     Comet's own {@code -q} output continues {@code sample_enzyme_number}'s inline comment on
     *     such a line
     */
    public record ParameterComments(
            List<String> above, Optional<String> inline, List<String> continuation) {

        /** Takes immutable copies. */
        public ParameterComments {
            above = List.copyOf(above);
            Objects.requireNonNull(inline, "inline");
            continuation = List.copyOf(continuation);
        }

        /**
         * The continuation lines, immutable.
         *
         * @return the lines
         */
        @Override
        public List<String> continuation() {
            return List.copyOf(continuation);
        }

        /**
         * The lines above the declaration, immutable.
         *
         * @return the lines
         */
        @Override
        public List<String> above() {
            return List.copyOf(above);
        }
    }

    /** Takes immutable copies, keeping the parameters' file order. */
    public ImportedComments {
        Objects.requireNonNull(marker, "marker");
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        beforeEnzymeTable = List.copyOf(beforeEnzymeTable);
        inEnzymeTable = List.copyOf(inEnzymeTable);
    }

    /**
     * The parameters' comments, immutable, in file order.
     *
     * @return the comments by parameter name
     */
    @Override
    public Map<String, ParameterComments> parameters() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * The lines before the enzyme table, immutable.
     *
     * @return the lines
     */
    @Override
    public List<String> beforeEnzymeTable() {
        return List.copyOf(beforeEnzymeTable);
    }

    /**
     * The lines inside the enzyme table, immutable.
     *
     * @return the lines
     */
    @Override
    public List<String> inEnzymeTable() {
        return List.copyOf(inEnzymeTable);
    }

    /**
     * The comments of one declared modelled parameter.
     *
     * @param name the parameter name
     * @return its comments, or empty if the file did not declare it (or it is not modelled)
     */
    public Optional<ParameterComments> of(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(parameters.get(name));
    }
}
