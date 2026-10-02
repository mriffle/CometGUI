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

/**
 * The versioned schema for one installed Comet build: what the binary declared, the curated
 * metadata, and where the two disagree.
 *
 * @param comet the build the schema is for
 * @param discovered what the binary declared, and whether that is complete
 * @param metadata the curated metadata
 * @param drift the comparison of the two
 */
public record CometParameterSchema(
        CometToolIdentity comet,
        DiscoveredSchema discovered,
        CuratedMetadata metadata,
        DriftReport drift) {

    /** Validates the components. */
    public CometParameterSchema {
        Objects.requireNonNull(comet, "comet");
        Objects.requireNonNull(discovered, "discovered");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(drift, "drift");
    }

    /**
     * Whether the schema came from a complete or a partial dump.
     *
     * @return the discovery mode
     */
    public DiscoveryMode mode() {
        return discovered.mode();
    }

    /**
     * The curated definitions of the parameters this build declared, in the order it declared them.
     * A declared parameter without metadata is absent here and present in {@link #drift()}.
     *
     * @return the definitions
     */
    public List<ParameterDefinition> definitions() {
        return discovered.parameters().stream()
                .flatMap(p -> metadata.parameter(p.name(), drift.version()).stream())
                .toList();
    }
}
