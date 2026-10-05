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

import java.util.Objects;
import org.cometgui.params.comet.model.DecoySource;

/**
 * One choice of the single Essentials decoy control ({@code R-DEC-01}): a decoy source, the value
 * of {@code decoy_search} it is written as, and the selected release's words for that value.
 *
 * @param source the decoy source
 * @param token the {@code decoy_search} value, such as {@code 1}
 * @param label the release's label for that value
 */
public record DecoyOption(DecoySource source, String token, String label) {

    /** Validates presence. */
    public DecoyOption {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(label, "label");
    }
}
