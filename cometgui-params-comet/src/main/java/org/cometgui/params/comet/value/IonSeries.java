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

/**
 * The fragment-ion series Comet can score, each switched by one {@code use_X_ions} parameter: the
 * specification's bit-like family of booleans, presented as named check boxes rather than numeric
 * flags.
 *
 * <p>The family is the curated metadata's {@code ION_SERIES_FLAG} parameters for the version; this
 * enum names them in the order {@code comet -q} writes them, and a test holds the two equal.
 */
public enum IonSeries {

    /** a ions. */
    A("use_A_ions"),

    /** b ions. */
    B("use_B_ions"),

    /** c ions. */
    C("use_C_ions"),

    /** x ions. */
    X("use_X_ions"),

    /** y ions. */
    Y("use_Y_ions"),

    /** z ions. */
    Z("use_Z_ions"),

    /** z+1 ions (z-dot). */
    Z1("use_Z1_ions");

    private final String parameter;

    IonSeries(String parameter) {
        this.parameter = parameter;
    }

    /**
     * The parameter that switches the series.
     *
     * @return for example {@code use_B_ions}
     */
    public String parameter() {
        return parameter;
    }
}
