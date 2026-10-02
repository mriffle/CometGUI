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

package org.cometgui.params.comet.migration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;

/**
 * Everything a schema migration did, one entry per parameter of either version and per unknown
 * parameter -- nothing migrates without an entry here ({@code R-TOOL-09}).
 *
 * @param from the source model's Comet version
 * @param to the target Comet version
 * @param entries one entry per parameter: the target's modelled parameters in its schema order,
 *     then the source parameters the target lacks, then the source model's unknown parameters
 */
public record MigrationReport(ToolVersion from, ToolVersion to, List<MigrationEntry> entries) {

    /**
     * Validates the components and takes an immutable copy.
     *
     * @throws IllegalArgumentException if a parameter has two entries
     */
    public MigrationReport {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        entries = List.copyOf(entries);
        long distinct = entries.stream().map(MigrationEntry::parameter).distinct().count();
        if (distinct != entries.size()) {
            throw new IllegalArgumentException("a parameter has two migration entries");
        }
    }

    /**
     * The entries, immutable.
     *
     * @return every entry, in report order
     */
    @Override
    public List<MigrationEntry> entries() {
        return List.copyOf(entries);
    }

    /**
     * The entry for one parameter.
     *
     * @param parameter the name
     * @return its entry, or empty if neither version nor the source's unknown parameters hold it
     */
    public Optional<MigrationEntry> entry(String parameter) {
        Objects.requireNonNull(parameter, "parameter");
        return entries.stream().filter(e -> e.parameter().equals(parameter)).findFirst();
    }

    /**
     * The names with one outcome, in report order.
     *
     * @param outcome the outcome
     * @return the parameter names
     */
    public List<String> names(MigrationEntry.Outcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        return entries.stream()
                .filter(e -> e.outcome() == outcome)
                .map(MigrationEntry::parameter)
                .toList();
    }

    /**
     * The entries the user should review: everything but values carried over unchanged.
     *
     * @return the changes, in report order
     */
    public List<MigrationEntry> changes() {
        return entries.stream().filter(e -> e.outcome().isChange()).toList();
    }

    /**
     * The values the target could not hold, which the user must decide about.
     *
     * @return the entries needing attention, in report order
     */
    public List<MigrationEntry> needingAttention() {
        return entries.stream()
                .filter(e -> e.outcome() == MigrationEntry.Outcome.NEEDS_ATTENTION)
                .toList();
    }

    /**
     * The report in words: a headline, then one line per change.
     *
     * @return the text, one line per change
     */
    public String describe() {
        StringBuilder text =
                new StringBuilder("Comet ")
                        .append(from.text())
                        .append(" -> ")
                        .append(to.text())
                        .append(": ")
                        .append(entries.size())
                        .append(" parameters, ")
                        .append(changes().size())
                        .append(" changes, ")
                        .append(needingAttention().size())
                        .append(" needing attention");
        for (MigrationEntry entry : changes()) {
            text.append('\n')
                    .append(entry.outcome())
                    .append(' ')
                    .append(entry.parameter())
                    .append(": ")
                    .append(entry.explanation());
        }
        return text.toString();
    }
}
