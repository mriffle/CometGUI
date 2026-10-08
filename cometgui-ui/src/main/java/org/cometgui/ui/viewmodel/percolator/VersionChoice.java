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

package org.cometgui.ui.viewmodel.percolator;

import java.util.Objects;
import org.cometgui.domain.tools.ToolOffer;

/**
 * One entry of the Percolator version selector: an installed build that can run, as the selector
 * shows it, with the resolved default marked.
 *
 * @param key identifies the build across re-reads of the Tool Manager: version, origin and path
 * @param offer the Tool Manager's offer
 * @param label what the selector shows, for example {@code Percolator 3.07.1 (managed) -- the
 *     resolved default}
 * @param resolvedDefault whether it is the resolution's default
 */
public record VersionChoice(String key, ToolOffer offer, String label, boolean resolvedDefault) {

    /**
     * Validates.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the key or the label is blank
     */
    public VersionChoice {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(label, "label");
        if (key.isBlank() || label.isBlank()) {
            throw new IllegalArgumentException("a version choice has a key and a label");
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
