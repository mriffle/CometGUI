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

package org.cometgui.params.comet.schema;

import java.util.Objects;

/**
 * One allowed value of an enumerated parameter, with what it means.
 *
 * @param value the value exactly as {@code comet.params} spells it, such as {@code 2} or {@code
 *     ETD+SA}
 * @param label what the value means, for the combo box and the reference page
 */
public record Choice(String value, String label) {

    /** Validates the components. */
    public Choice {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
    }
}
