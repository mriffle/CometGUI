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

package org.cometgui.domain.run;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether, and how, a run searches a prebuilt Comet index rather than the FASTA itself (design
 * decision P8-8).
 */
public enum IndexMode {

    /** No index: Comet searches the FASTA. */
    NONE("none", null),

    /** A fragment-ion index, built with {@code comet -i}. */
    FRAGMENT_ION("fragment-ion", "-i"),

    /** A peptide index, built with {@code comet -j}. */
    PEPTIDE("peptide", "-j");

    private final String wireName;

    private final String buildFlag;

    IndexMode(String wireName, String buildFlag) {
        this.wireName = wireName;
        this.buildFlag = buildFlag;
    }

    /**
     * The name {@code run.json} records.
     *
     * @return {@code none}, {@code fragment-ion} or {@code peptide}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * The Comet option that builds this kind of index.
     *
     * @return {@code -i} or {@code -j}; empty for {@link #NONE}
     */
    public Optional<String> buildFlag() {
        return Optional.ofNullable(buildFlag);
    }

    /**
     * The mode a recorded name stands for.
     *
     * @param wireName the recorded name
     * @return the mode
     * @throws NullPointerException if {@code wireName} is {@code null}
     * @throws IllegalArgumentException if no mode has that name
     */
    public static IndexMode fromWireName(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        for (IndexMode mode : values()) {
            if (mode.wireName.equals(wireName)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("not an index mode: \"" + wireName + "\"");
    }
}
