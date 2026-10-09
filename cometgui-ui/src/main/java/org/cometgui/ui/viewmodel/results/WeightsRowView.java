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

import java.util.List;
import java.util.Objects;

/**
 * One feature's row of the learned feature weights table, as text ({@code R-PERC-08}). Numbers are
 * written under {@link java.util.Locale#ROOT}: each split's weight with {@value
 * WeightsViewModel#SPLIT_DECIMALS} decimal places -- the precision Percolator writes them with --
 * and each statistic with {@value WeightsViewModel#STATISTIC_DECIMALS}. The weights export keeps
 * full precision.
 *
 * @param feature the feature's name, as the file gives it
 * @param bias whether this is the bias term {@code m0}, listed and never ranked
 * @param note what the row is, for the bias term; otherwise empty
 * @param splits the normalised weight of each split, in split order
 * @param meanSigned the mean signed weight
 * @param meanAbsolute the mean absolute weight
 * @param standardDeviation the population standard deviation
 * @param signConsistency the sign verdict in words, with the count of each sign
 * @param rank the rank by mean absolute weight; empty for the bias term
 */
public record WeightsRowView(
        String feature,
        boolean bias,
        String note,
        List<String> splits,
        String meanSigned,
        String meanAbsolute,
        String standardDeviation,
        String signConsistency,
        String rank) {

    /**
     * A row.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public WeightsRowView {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(note, "note");
        splits = List.copyOf(splits);
        Objects.requireNonNull(meanSigned, "meanSigned");
        Objects.requireNonNull(meanAbsolute, "meanAbsolute");
        Objects.requireNonNull(standardDeviation, "standardDeviation");
        Objects.requireNonNull(signConsistency, "signConsistency");
        Objects.requireNonNull(rank, "rank");
    }
}
