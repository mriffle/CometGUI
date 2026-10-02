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

import java.util.Objects;
import java.util.Optional;

/**
 * The fourteen Advanced-mode groups the specification names (<em>Parameter editor levels</em>), in
 * its order.
 *
 * <p>The metadata file lists the same fourteen with their display names, so that the documentation
 * generator, which cannot read Java, has them too; {@link MetadataLoader} rejects a file whose list
 * differs from this one in any id, name or position.
 */
public enum ParameterCategory {

    /** Database and PEFF. */
    DATABASE_PEFF("database_peff", "Database and PEFF"),

    /** CPU and execution. */
    CPU_EXECUTION("cpu_execution", "CPU and execution"),

    /** Precursor mass and isotope handling. */
    PRECURSOR_MASS("precursor_mass", "Precursor mass and isotope handling"),

    /** Digestion and enzymes, including the second enzyme. */
    DIGESTION_ENZYMES("digestion_enzymes", "Digestion and enzymes"),

    /** Fragment-ion scoring. */
    FRAGMENT_SCORING("fragment_scoring", "Fragment-ion scoring"),

    /** Fragment-ion and peptide-index search options. */
    FRAGMENT_INDEX("fragment_index", "Fragment-ion and peptide-index search options"),

    /** Spectrum, scan and charge filters. */
    SPECTRUM_FILTERS("spectrum_filters", "Spectrum, scan and charge filters"),

    /** Spectral pre-processing. */
    SPECTRAL_PROCESSING("spectral_processing", "Spectral pre-processing"),

    /** Search ranges and peptide constraints. */
    SEARCH_RANGES("search_ranges", "Search ranges and peptide constraints"),

    /** Output options. */
    OUTPUT("output", "Output options"),

    /** MS1 and real-time-search options, where supported. */
    MS1_REALTIME("ms1_realtime", "MS1 and real-time-search options"),

    /** Static modifications. */
    STATIC_MODS("static_mods", "Static modifications"),

    /** Variable modifications. */
    VARIABLE_MODS("variable_mods", "Variable modifications"),

    /** Miscellaneous and version-specific options. */
    MISC("misc", "Miscellaneous and version-specific options");

    private final String id;
    private final String displayName;

    ParameterCategory(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /**
     * The identifier the metadata file uses.
     *
     * @return the identifier
     */
    public String id() {
        return id;
    }

    /**
     * The heading the editor and the documentation show.
     *
     * @return the display name
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Resolves a metadata identifier, exactly.
     *
     * @param id the identifier
     * @return the category, or empty if none has that identifier
     */
    public static Optional<ParameterCategory> fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (ParameterCategory category : values()) {
            if (category.id.equals(id)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
