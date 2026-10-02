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

package org.cometgui.params.comet.presets;

import java.util.Objects;
import java.util.Optional;

/**
 * One parameter a preset sets: its name and the value text, in the parameter's own {@code
 * comet.params} syntax, exactly as it would follow {@code name = }.
 *
 * @param parameter the parameter name
 * @param value the value text; read by the parameter's codec when the preset is loaded or applied
 * @param citation where the value comes from -- required of a built-in preset, which cites Comet's
 *     own documentation; empty for a user's preset
 */
public record PresetDelta(String parameter, String value, Optional<String> citation) {

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the parameter name is blank, or a citation is blank
     */
    public PresetDelta {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(citation, "citation");
        if (parameter.isBlank()) {
            throw new IllegalArgumentException("a preset delta needs a parameter name");
        }
        if (citation.isPresent() && citation.get().isBlank()) {
            throw new IllegalArgumentException(
                    "the citation of the " + parameter + " delta is blank; leave it empty instead");
        }
    }
}
