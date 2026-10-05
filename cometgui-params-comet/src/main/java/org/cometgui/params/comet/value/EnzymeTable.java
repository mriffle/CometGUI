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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code [COMET_ENZYME_INFO]} table as a typed, immutable list of rows in file order.
 *
 * <p>A table never holds two rows with one number. Comet 2026.02.2 itself does not refuse that: it
 * reads every row, and for a referenced number the <em>last</em> matching row silently wins ({@code
 * Comet.cpp}, the enzyme loop after {@code [COMET_ENZYME_INFO]}). A file whose table means
 * something other than what it appears to say is refused here instead.
 *
 * @param rows the rows, in file order
 */
public record EnzymeTable(List<EnzymeDefinition> rows) {

    /**
     * Validates that no number is used twice, and takes an immutable copy.
     *
     * @throws IllegalArgumentException naming the number and both enzymes, if one is
     */
    public EnzymeTable {
        rows = List.copyOf(rows);
        for (int index = 0; index < rows.size(); index++) {
            for (int earlier = 0; earlier < index; earlier++) {
                if (rows.get(earlier).number() == rows.get(index).number()) {
                    throw duplicate(rows.get(earlier), rows.get(index));
                }
            }
        }
    }

    private static IllegalArgumentException duplicate(
            EnzymeDefinition first, EnzymeDefinition second) {
        return new IllegalArgumentException(
                "enzyme number "
                        + first.number()
                        + " is defined twice, as "
                        + first.name()
                        + " and as "
                        + second.name());
    }

    /**
     * The rows in file order, immutable.
     *
     * @return the rows
     */
    @Override
    public List<EnzymeDefinition> rows() {
        return List.copyOf(rows);
    }

    /**
     * The row with a number.
     *
     * @param number the enzyme number, as a parameter such as {@code search_enzyme_number} holds it
     * @return the row, or empty if the table has none with that number
     */
    public Optional<EnzymeDefinition> byNumber(int number) {
        return rows.stream().filter(row -> row.number() == number).findFirst();
    }

    /**
     * Whether a number is defined.
     *
     * @param number the enzyme number
     * @return {@code true} if a row has it
     */
    public boolean contains(int number) {
        return byNumber(number).isPresent();
    }

    /**
     * The number a new row takes: one more than the highest number in the table, so that rows stay
     * numbered "starting at 0 and increasing by 1" as Comet's page asks when they already are.
     *
     * @return the number; 0 for an empty table
     */
    public int nextNumber() {
        int highest = -1;
        for (EnzymeDefinition row : rows) {
            highest = Math.max(highest, row.number());
        }
        return highest + 1;
    }

    /**
     * This table with one more row at the end, such as a custom enzyme.
     *
     * @param row the new row
     * @return a new table
     * @throws IllegalArgumentException if its number is already defined
     */
    public EnzymeTable with(EnzymeDefinition row) {
        Objects.requireNonNull(row, "row");
        List<EnzymeDefinition> added = new ArrayList<>(rows);
        added.add(row);
        return new EnzymeTable(added);
    }

    /**
     * This table without the row with a number.
     *
     * @param number the enzyme number
     * @return a new table
     * @throws IllegalArgumentException if no row has that number
     */
    public EnzymeTable without(int number) {
        if (!contains(number)) {
            throw new IllegalArgumentException("no enzyme row has number " + number);
        }
        return new EnzymeTable(rows.stream().filter(row -> row.number() != number).toList());
    }
}
