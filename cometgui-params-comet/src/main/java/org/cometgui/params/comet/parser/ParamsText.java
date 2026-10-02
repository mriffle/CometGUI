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

import java.util.List;
import java.util.Objects;

/**
 * A whole {@code comet.params} text as classified lines, in file order.
 *
 * @param lines every line, in order, none dropped
 * @param endsWithNewline whether the text's last character was {@code \n}; Comet's own output does,
 *     and a writer that wants to reproduce a file needs to know
 */
public record ParamsText(List<ParamsLine> lines, boolean endsWithNewline) {

    /** Takes an immutable copy of the lines. */
    public ParamsText {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    /**
     * Every line, immutable and in order.
     *
     * @return the lines
     */
    @Override
    public List<ParamsLine> lines() {
        return List.copyOf(lines);
    }

    /**
     * The declarations, in file order, duplicates included.
     *
     * @return the declaration lines
     */
    public List<ParamsLine.Declaration> declarations() {
        return lines.stream()
                .filter(ParamsLine.Declaration.class::isInstance)
                .map(ParamsLine.Declaration.class::cast)
                .toList();
    }

    /**
     * The enzyme-table rows, in file order.
     *
     * @return the rows
     */
    public List<ParamsLine.EnzymeRow> enzymeRows() {
        return lines.stream()
                .filter(ParamsLine.EnzymeRow.class::isInstance)
                .map(ParamsLine.EnzymeRow.class::cast)
                .toList();
    }

    /**
     * The lines that fit no accepted shape, in file order.
     *
     * @return the malformed lines
     */
    public List<ParamsLine.Malformed> malformed() {
        return lines.stream()
                .filter(ParamsLine.Malformed.class::isInstance)
                .map(ParamsLine.Malformed.class::cast)
                .toList();
    }
}
