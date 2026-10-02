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
import java.util.Optional;

/**
 * One parameter as a Comet binary declared it in its own dump.
 *
 * @param name the name
 * @param defaultText the value text it wrote, trimmed; possibly empty
 * @param inlineComment the comment it wrote after the value, trimmed, if any
 * @param lineNumber where in the dump, 1-based
 */
public record DiscoveredParameter(
        String name, String defaultText, Optional<String> inlineComment, int lineNumber) {

    /** Validates the components. */
    public DiscoveredParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(defaultText, "defaultText");
        Objects.requireNonNull(inlineComment, "inlineComment");
    }
}
