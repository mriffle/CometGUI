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

package org.cometgui.tools.comet;

import java.util.Objects;

/**
 * The decoy configuration a search ran with, as plain values, so that a PIN without decoys can be
 * refused with a message naming it ({@code R-DEC-04}).
 *
 * <p>Plain values because this module cannot see the parameter model: the caller reads them from it
 * -- the {@code decoy_search} value, the words for what that value means (the ones the decoy rules
 * already use, for example {@code Comet's internal decoys, concatenated}), and the project's one
 * {@code decoy_prefix} ({@code R-DEC-03}).
 *
 * @param decoySearch the {@code decoy_search} value
 * @param meaning what that value means, in words
 * @param prefix the decoy prefix
 */
public record PinDecoyConfiguration(int decoySearch, String meaning, String prefix) {

    /**
     * Validates the configuration.
     *
     * @throws NullPointerException if a text is {@code null}
     * @throws IllegalArgumentException if the meaning or the prefix is blank
     */
    public PinDecoyConfiguration {
        Objects.requireNonNull(meaning, "meaning");
        Objects.requireNonNull(prefix, "prefix");
        if (meaning.isBlank()) {
            throw new IllegalArgumentException("the meaning of decoy_search must be given");
        }
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("the decoy prefix must not be blank");
        }
    }

    /**
     * The configuration in words, for a message.
     *
     * @return for example {@code decoy_search = 1 (Comet's internal decoys, concatenated),
     *     decoy_prefix = "DECOY_"}
     */
    public String describe() {
        return "decoy_search = "
                + decoySearch
                + " ("
                + meaning
                + "), decoy_prefix = \""
                + prefix
                + "\"";
    }
}
