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

package org.cometgui.params.comet.value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Zero or more decimals on one line, such as {@code mass_offsets = 0.0 42.0123}: the {@code
 * DECIMAL_LIST} kind. Empty is a value ({@code mass_offsets =}, Comet's default).
 *
 * <p>Comet 2026.02.2 splits the value on spaces and tabs, drops negative values and sorts the rest
 * ({@code Comet.cpp}, the {@code mass_offsets} handler). This type keeps the values in the order
 * and with the scale written; whether they are legal is the validation package's question. A token
 * that is not a number is refused here -- Comet's own loop for this parameter does not advance past
 * such a token.
 *
 * @param values the values, in the order written
 */
public record DecimalList(List<BigDecimal> values) {

    /** Takes an immutable copy. */
    public DecimalList {
        values = List.copyOf(values);
    }

    /**
     * The values, immutable.
     *
     * @return the values in order
     */
    @Override
    public List<BigDecimal> values() {
        return List.copyOf(values);
    }

    /**
     * Reads a list.
     *
     * @param name the parameter, for the diagnostic
     * @param text the value text, possibly empty
     * @return the list
     * @throws ValueSyntaxException naming the parameter and the position, if a token is not a
     *     number
     */
    public static DecimalList parse(String name, String text) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
        String[] tokens = Numbers.tokens(text);
        List<BigDecimal> values = new ArrayList<>();
        for (int index = 0; index < tokens.length; index++) {
            values.add(Numbers.decimal(name, "value " + (index + 1), tokens[index]));
        }
        return new DecimalList(values);
    }

    /**
     * The value text.
     *
     * @return the values separated by one space; empty for an empty list
     */
    public String text() {
        return String.join(" ", values.stream().map(Numbers::text).toList());
    }
}
