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

package org.cometgui.params.comet.validation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.schema.ParameterCategory;

/**
 * One thing a validator found, attached to the parameters responsible and their category so that
 * the editor can show it at the control ("Errors shall be attached to the responsible field and
 * category").
 *
 * @param rule the rule that found it; fixes the severity and the identifier
 * @param parameters the responsible parameters, the first being the one to show it at; empty only
 *     for a finding about the file as a whole, such as its version marker, or about an enzyme row
 *     no parameter references
 * @param category the category of the first parameter; empty for a parameter the schema does not
 *     model and for a finding about the file as a whole
 * @param message what is wrong, with the value, and what would be valid
 */
public record Finding(
        Rule rule, List<String> parameters, Optional<ParameterCategory> category, String message) {

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the message is blank
     */
    public Finding {
        Objects.requireNonNull(rule, "rule");
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("a finding needs a message");
        }
    }

    /**
     * The responsible parameters, immutable.
     *
     * @return the parameter names
     */
    @Override
    public List<String> parameters() {
        return List.copyOf(parameters);
    }

    /**
     * The severity, which is the rule's.
     *
     * @return the severity
     */
    public Severity severity() {
        return rule.severity();
    }

    /**
     * Whether this finding blocks a run.
     *
     * @return {@code true} for an error
     */
    public boolean isError() {
        return severity() == Severity.ERROR;
    }

    /**
     * Whether a parameter is one of the responsible ones.
     *
     * @param name the parameter name
     * @return {@code true} if the finding is attached to it
     */
    public boolean concerns(String name) {
        return parameters.contains(name);
    }
}
