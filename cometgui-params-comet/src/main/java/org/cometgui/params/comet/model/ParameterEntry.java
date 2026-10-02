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

package org.cometgui.params.comet.model;

import java.util.Objects;
import org.cometgui.params.comet.schema.ParameterDefinition;

/**
 * One modelled parameter in a {@link CometParameters}: its definition, its typed value and where
 * that value came from.
 *
 * @param definition the curated definition
 * @param value the typed value, of the variant the definition's kind holds
 * @param origin where the value came from
 */
public record ParameterEntry(
        ParameterDefinition definition, ParameterValue value, ValueOrigin origin) {

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the value is not of the variant the kind holds
     */
    public ParameterEntry {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(origin, "origin");
        if (!ParameterValueCodec.fits(definition.kind(), value)) {
            throw new IllegalArgumentException(ParameterValueCodec.mismatch(definition, value));
        }
    }

    /**
     * The parameter's name.
     *
     * @return the name as Comet spells it
     */
    public String name() {
        return definition.name();
    }
}
