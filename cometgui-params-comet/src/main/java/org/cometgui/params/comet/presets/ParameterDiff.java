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

package org.cometgui.params.comet.presets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.EnzymeTableCodec;

/**
 * The diff between two parameter sets of one Comet version -- what Expert mode shows against "the
 * selected preset or defaults" and "the last saved or run configuration".
 *
 * <p>Rows, in this order: every modelled parameter whose value text differs, in schema order; then
 * every enzyme-table row that differs or exists on one side only, by number; then every unknown
 * parameter whose text differs or that exists on one side only, the current side's first in its
 * order, then the other's. Texts are compared as the canonical writer writes them, so two sets that
 * write the same bytes have no rows. Origins are not compared: a value is the same whoever set it.
 */
public final class ParameterDiff {

    private ParameterDiff() {}

    /**
     * The diff between two parameter sets.
     *
     * @param current the current set
     * @param other the set to compare it with
     * @return the rows, empty when the two would write the same file
     * @throws IllegalArgumentException if the sets are of different Comet versions -- comparing
     *     those is a migration ({@code org.cometgui.params.comet.migration.SchemaMigration})
     */
    public static List<DiffRow> between(CometParameters current, CometParameters other) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(other, "other");
        if (!current.version().equals(other.version())) {
            throw new IllegalArgumentException(
                    "the sets are for Comet "
                            + current.version().text()
                            + " and Comet "
                            + other.version().text()
                            + "; migrate one to the other's version before comparing them");
        }
        List<DiffRow> rows = new ArrayList<>();
        for (ParameterEntry entry : current.entries()) {
            String name = entry.name();
            String mine = current.text(name);
            String theirs = other.text(name);
            if (!mine.equals(theirs)) {
                rows.add(
                        new DiffRow(
                                DiffRow.Kind.PARAMETER,
                                name,
                                Optional.of(mine),
                                Optional.of(theirs)));
            }
        }
        Map<Integer, String> mineRows = enzymeRows(current);
        Map<Integer, String> theirRows = enzymeRows(other);
        TreeSet<Integer> numbers = new TreeSet<>(mineRows.keySet());
        numbers.addAll(theirRows.keySet());
        for (Integer number : numbers) {
            Optional<String> mine = Optional.ofNullable(mineRows.get(number));
            Optional<String> theirs = Optional.ofNullable(theirRows.get(number));
            if (!mine.equals(theirs)) {
                rows.add(new DiffRow(DiffRow.Kind.ENZYME_ROW, number.toString(), mine, theirs));
            }
        }
        Map<String, String> mineUnknown = unknowns(current);
        Map<String, String> theirUnknown = unknowns(other);
        List<String> names = new ArrayList<>(mineUnknown.keySet());
        theirUnknown.keySet().stream().filter(n -> !mineUnknown.containsKey(n)).forEach(names::add);
        for (String name : names) {
            Optional<String> mine = Optional.ofNullable(mineUnknown.get(name));
            Optional<String> theirs = Optional.ofNullable(theirUnknown.get(name));
            if (!mine.equals(theirs)) {
                rows.add(new DiffRow(DiffRow.Kind.UNKNOWN_PARAMETER, name, mine, theirs));
            }
        }
        return List.copyOf(rows);
    }

    private static Map<Integer, String> enzymeRows(CometParameters model) {
        Map<Integer, String> rows = new LinkedHashMap<>();
        for (EnzymeDefinition row : model.enzymeTable().rows()) {
            rows.put(row.number(), EnzymeTableCodec.formatRow(row));
        }
        return rows;
    }

    private static Map<String, String> unknowns(CometParameters model) {
        Map<String, String> unknowns = new LinkedHashMap<>();
        for (UnknownParameter unknown : model.unknownParameters()) {
            unknowns.put(unknown.name(), unknown.value());
        }
        return unknowns;
    }
}
