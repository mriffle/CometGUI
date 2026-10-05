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

/**
 * Where a whole parameter set the editor adopts came from, which decides what happens to a
 * migration under review.
 *
 * <p>Every adopted set has the workflow's outputs enforced, whatever its source (decision P7-7). A
 * migration is adopted by {@link ParameterSession#adoptMigration}, not with one of these.
 */
public enum Adoption {

    /** A new configuration: the release's starting set. Starts afresh, so a review is dropped. */
    NEW(true),

    /** A parameter file read for the selected release. Starts afresh, so a review is dropped. */
    IMPORTED(true),

    /**
     * The Expert editor's raw text, parsed for the current release. An edit of the same
     * configuration, so a migration under review stays under review.
     */
    RAW_APPLIED(false),

    /**
     * A preset applied to the current configuration. An edit of the same configuration, so a
     * migration under review stays under review.
     */
    PRESET_APPLIED(false);

    private final boolean startsAfresh;

    Adoption(boolean startsAfresh) {
        this.startsAfresh = startsAfresh;
    }

    /**
     * Whether the adopted set replaces the configuration rather than editing it: it may be of
     * another offered release, and a migration review is dropped with the old configuration.
     *
     * @return {@code true} for {@link #NEW} and {@link #IMPORTED}
     */
    public boolean startsAfresh() {
        return startsAfresh;
    }
}
