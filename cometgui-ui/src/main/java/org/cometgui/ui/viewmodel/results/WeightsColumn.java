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

/**
 * A column the learned feature weights table can be sorted by. Each sorts on the summary's own
 * value -- the {@code double} or {@code int}, never the text shown.
 */
public enum WeightsColumn {

    /** The file's feature order: the default. */
    FILE_ORDER,

    /** The feature's name, by {@link String#compareTo}. */
    FEATURE,

    /** One split's normalised weight; which split is {@link WeightsSort#split()}. */
    SPLIT,

    /** The mean signed weight. */
    MEAN_SIGNED,

    /** The mean absolute weight. */
    MEAN_ABSOLUTE,

    /** The population standard deviation. */
    STANDARD_DEVIATION,

    /** The sign verdict, in the order all positive, all negative, mixed, all zero. */
    SIGN_CONSISTENCY,

    /** The rank by mean absolute weight; the unranked bias term last in both directions. */
    RANK
}
