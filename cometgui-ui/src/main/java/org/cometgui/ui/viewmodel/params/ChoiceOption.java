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

/**
 * One choice of an enumerated parameter, as the selected release lists it: the words a scientist
 * reads and the token {@code comet.params} holds ("Recognition rather than recall").
 *
 * @param token the serialised value, such as {@code 2}
 * @param label what it means, such as {@code ppm}
 */
public record ChoiceOption(String token, String label) {

    /** Validates presence. */
    public ChoiceOption {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(label, "label");
    }

    /**
     * The label with the serialised token after it, for advanced help.
     *
     * @return for example {@code ppm [2]}
     */
    public String withToken() {
        return label + " [" + token + "]";
    }
}
