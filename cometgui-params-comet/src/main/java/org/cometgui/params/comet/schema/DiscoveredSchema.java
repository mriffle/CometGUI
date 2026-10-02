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

package org.cometgui.params.comet.schema;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a Comet binary says about its own parameters: names in the order it wrote them, their
 * default texts and inline comments, and the enzyme rows, from one {@code -q} or {@code -p} dump.
 *
 * @param mode whether the dump is complete or only the default set
 * @param marker the version the dump's first line names
 * @param parameters the declarations, in file order, each name once
 * @param enzymeRows the {@code [COMET_ENZYME_INFO]} rows, verbatim, in file order
 */
public record DiscoveredSchema(
        DiscoveryMode mode,
        CometVersionMarker marker,
        List<DiscoveredParameter> parameters,
        List<String> enzymeRows) {

    /** Takes immutable copies. */
    public DiscoveredSchema {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(marker, "marker");
        parameters = List.copyOf(parameters);
        enzymeRows = List.copyOf(enzymeRows);
    }

    /**
     * The declarations, immutable.
     *
     * @return the parameters
     */
    @Override
    public List<DiscoveredParameter> parameters() {
        return List.copyOf(parameters);
    }

    /**
     * The enzyme rows, immutable.
     *
     * @return the rows
     */
    @Override
    public List<String> enzymeRows() {
        return List.copyOf(enzymeRows);
    }

    /**
     * The declared names, in file order.
     *
     * @return the names
     */
    public List<String> names() {
        return parameters.stream().map(DiscoveredParameter::name).toList();
    }

    /**
     * One declared parameter.
     *
     * @param name the name
     * @return the parameter, or empty if the dump does not declare it
     */
    public Optional<DiscoveredParameter> parameter(String name) {
        Objects.requireNonNull(name, "name");
        return parameters.stream().filter(p -> p.name().equals(name)).findFirst();
    }
}
