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

package org.cometgui.tools.comet;

import java.util.Objects;

/**
 * A name and its text, in document order: a {@code search_score}'s {@code name} and {@code value},
 * or one attribute of a {@link PepXmlSequenceVariant}.
 *
 * @param name the name
 * @param value the text, verbatim
 */
public record PepXmlNameValue(String name, String value) {

    /**
     * Validates the pair.
     *
     * @throws NullPointerException if either component is {@code null}
     */
    public PepXmlNameValue {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
    }
}
