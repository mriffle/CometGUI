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

package org.cometgui.results.parser;

import java.util.List;

/**
 * The weights Percolator learned in one cross-validation split, one value per feature in the order
 * of {@link LearnedWeights#featureNames()}.
 *
 * @param number the split's number, counting from 1 in file order
 * @param normalised the weights after Percolator's feature normalisation: the row the file gives
 *     first
 * @param raw the weights on the features' original scale: the row the file gives second
 */
public record WeightsSplit(int number, List<Double> normalised, List<Double> raw) {

    /**
     * A split.
     *
     * @throws IllegalArgumentException if {@code number} is below 1 or the two rows differ in width
     */
    public WeightsSplit {
        normalised = List.copyOf(normalised);
        raw = List.copyOf(raw);
        if (number < 1 || normalised.size() != raw.size()) {
            throw new IllegalArgumentException(
                    "split "
                            + number
                            + " has "
                            + normalised.size()
                            + " normalised and "
                            + raw.size()
                            + " raw weights");
        }
    }
}
