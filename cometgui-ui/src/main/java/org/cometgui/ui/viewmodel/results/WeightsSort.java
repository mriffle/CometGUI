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

package org.cometgui.ui.viewmodel.results;

import java.util.Objects;

/**
 * The learned feature weights table's order.
 *
 * @param column the column
 * @param split for {@link WeightsColumn#SPLIT}, which split, from 0; otherwise 0
 * @param descending whether largest first
 */
public record WeightsSort(WeightsColumn column, int split, boolean descending) {

    /** The file's feature order. */
    public static final WeightsSort FILE_ORDER =
            new WeightsSort(WeightsColumn.FILE_ORDER, 0, false);

    /**
     * A sort.
     *
     * @throws IllegalArgumentException if the split is negative, or given for another column
     * @throws NullPointerException if {@code column} is {@code null}
     */
    public WeightsSort {
        Objects.requireNonNull(column, "column");
        if (split < 0 || (split != 0 && column != WeightsColumn.SPLIT)) {
            throw new IllegalArgumentException(
                    "a split is given, from 0, only to sort by a split, not " + split);
        }
    }
}
