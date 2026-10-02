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

import java.util.Objects;
import java.util.Optional;

/**
 * One row of the {@code [COMET_ENZYME_INFO]} table: {@code number. name sense cut no-cut}.
 *
 * <p>Comet's documentation for the 2026.02 release: the number identifies the row and is what
 * {@code search_enzyme_number}, {@code search_enzyme2_number} and {@code sample_enzyme_number}
 * refer to; the name may not contain spaces; the sense says on which side of a cut residue the
 * enzyme cleaves; the cut residues are where it cleaves, the no-cut residues the flanking residues
 * that prevent it, and {@code -} means none. Comet 2026.02.2 treats a row whose cut and no-cut
 * residues are both {@code -} as no enzyme at all (non-specific cleavage).
 *
 * <p>"None" is held here as the empty string and written as {@code -}; any other residue text,
 * including Comet's {@code @} for "no residue" in {@code No_cut}, is kept as written. Whether a
 * custom row is sensible -- residue letters, numbering from 0 without gaps -- is the validation
 * package's question.
 *
 * @param number the row number
 * @param name the enzyme's name, one token
 * @param sense which side of a cut residue it cleaves
 * @param cutResidues the residues it cleaves at, as written; empty for none
 * @param noCutResidues the flanking residues that prevent cleavage, as written; empty for none
 */
public record EnzymeDefinition(
        int number, String name, Sense sense, String cutResidues, String noCutResidues) {

    /** What Comet's row writes for an empty residue set. */
    public static final String NONE = "-";

    /** The documented values of a row's sense field. */
    public enum Sense {

        /** {@code 0}: cleaves N-terminal to (before) the cut residues. */
        BEFORE_RESIDUE(0),

        /** {@code 1}: cleaves C-terminal to (after) the cut residues. */
        AFTER_RESIDUE(1);

        private final int code;

        Sense(int code) {
            this.code = code;
        }

        /**
         * The value written in the row.
         *
         * @return {@code 0} or {@code 1}
         */
        public int code() {
            return code;
        }

        /**
         * The sense a code names.
         *
         * @param code the value written
         * @return the sense, or empty for any other value
         */
        public static Optional<Sense> fromCode(int code) {
            for (Sense sense : values()) {
                if (sense.code == code) {
                    return Optional.of(sense);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the number is negative, the name is empty or holds white
     *     space, or a residue text holds white space or is {@code -} (write none as the empty
     *     string)
     */
    public EnzymeDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sense, "sense");
        Objects.requireNonNull(cutResidues, "cutResidues");
        Objects.requireNonNull(noCutResidues, "noCutResidues");
        if (number < 0) {
            throw new IllegalArgumentException("enzyme number " + number + " is negative");
        }
        if (name.isEmpty() || hasWhiteSpace(name)) {
            throw new IllegalArgumentException(
                    "enzyme name \"" + name + "\" must be one token with no white space");
        }
        for (String residues : new String[] {cutResidues, noCutResidues}) {
            if (hasWhiteSpace(residues) || NONE.equals(residues)) {
                throw new IllegalArgumentException(
                        "residues \""
                                + residues
                                + "\" must be letters with no white space, or empty for none");
            }
        }
    }

    private static boolean hasWhiteSpace(String text) {
        return text.chars().anyMatch(Character::isWhitespace);
    }

    /**
     * Whether the row means no enzyme: no cut residues and no no-cut residues, written {@code - -},
     * which Comet 2026.02.2 takes as non-specific cleavage.
     *
     * @return {@code true} for a row like {@code Cut_everywhere}
     */
    public boolean nonSpecific() {
        return cutResidues.isEmpty() && noCutResidues.isEmpty();
    }
}
