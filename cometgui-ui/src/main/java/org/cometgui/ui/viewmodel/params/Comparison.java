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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The configuration compared with another set of the same release, as Expert mode shows it: the
 * model's {@code ParameterDiff} rows, or why there is nothing to compare with.
 *
 * @param against what the configuration is compared with, in words
 * @param rows the rows, current side first; empty when the two would write the same file or there
 *     is nothing to compare with
 * @param unavailable why there is nothing to compare with, or empty when there is
 */
public record Comparison(String against, List<DiffRowView> rows, Optional<String> unavailable) {

    /**
     * Validates presence and takes an immutable copy.
     *
     * @throws IllegalArgumentException if an unavailable comparison has rows
     */
    public Comparison {
        Objects.requireNonNull(against, "against");
        rows = List.copyOf(rows);
        Objects.requireNonNull(unavailable, "unavailable");
        if (unavailable.isPresent() && !rows.isEmpty()) {
            throw new IllegalArgumentException("a comparison that cannot be made has no rows");
        }
    }

    /**
     * A comparison that cannot be made.
     *
     * @param against what it would have compared with
     * @param why the reason
     * @return the comparison
     */
    static Comparison unavailable(String against, String why) {
        return new Comparison(against, List.of(), Optional.of(why));
    }
}
