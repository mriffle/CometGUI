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

package org.cometgui.ui.viewmodel.params;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One parameter a search found, and why: which of its attributes the query matched.
 *
 * @param name the parameter name; the field to move focus to, for a modelled parameter
 * @param field the parameter's field, or empty for an unknown parameter of the configuration
 * @param label what the result list shows
 * @param matchedBy the attributes the query matched, in {@link SearchMatch} order; empty for an
 *     empty query, which lists every parameter the filters admit
 * @param aliases the aliases the query matched, in the metadata's order
 */
public record SearchHit(
        String name,
        Optional<FieldViewModel> field,
        String label,
        List<SearchMatch> matchedBy,
        List<String> aliases) {

    /**
     * Validates presence and takes immutable copies.
     *
     * @throws IllegalArgumentException if aliases are named without {@link SearchMatch#ALIAS}
     */
    public SearchHit {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(label, "label");
        matchedBy = List.copyOf(matchedBy);
        aliases = List.copyOf(aliases);
        if (aliases.isEmpty() == matchedBy.contains(SearchMatch.ALIAS)) {
            throw new IllegalArgumentException(
                    name + ": a match by alias names the aliases matched, and only it does");
        }
    }

    /**
     * Whether the parameter is one the selected release does not model.
     *
     * @return {@code true} for an unknown parameter
     */
    public boolean unknown() {
        return field.isEmpty();
    }

    /**
     * Why the parameter was found, in words.
     *
     * @return for example {@code Matched by display name, alias "missed cleavage"}; {@code Listed
     *     by the filters} for an empty query
     */
    public String why() {
        if (matchedBy.isEmpty()) {
            return "Listed by the filters";
        }
        List<String> parts = new ArrayList<>();
        for (SearchMatch match : matchedBy) {
            parts.add(
                    match == SearchMatch.ALIAS
                            ? match.words() + " \"" + String.join("\", \"", aliases) + "\""
                            : match.words());
        }
        return "Matched by " + String.join(", ", parts);
    }
}
