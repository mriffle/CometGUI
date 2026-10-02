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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.schema.ParameterCategory;

/**
 * Collects the findings of one validation, attaching each to the category of its first parameter
 * and keeping each finding once (a rule over two parameters, such as the precursor pair, is reached
 * from both).
 */
final class Findings {

    private final CometParameters model;

    private final Set<Finding> found = new LinkedHashSet<>();

    Findings(CometParameters model) {
        this.model = model;
    }

    /**
     * Records a finding about modelled parameters.
     *
     * @param rule the rule
     * @param parameters the responsible parameters, first the one to show it at
     * @param message the message
     */
    void add(Rule rule, List<String> parameters, String message) {
        Optional<ParameterCategory> category =
                model.entry(parameters.get(0)).map(entry -> entry.definition().category());
        found.add(new Finding(rule, parameters, category, message));
    }

    /**
     * Records a finding about one modelled parameter.
     *
     * @param rule the rule
     * @param parameter the responsible parameter
     * @param message the message
     */
    void add(Rule rule, String parameter, String message) {
        add(rule, List.of(parameter), message);
    }

    /**
     * Records a finding about a parameter the schema does not model, or about the file as a whole,
     * with no category.
     *
     * @param rule the rule
     * @param parameters the responsible parameters; may be empty
     * @param message the message
     */
    void addUncategorised(Rule rule, List<String> parameters, String message) {
        found.add(new Finding(rule, parameters, Optional.empty(), message));
    }

    /**
     * Records a finding attached to a category but to no single parameter, such as one about an
     * enzyme row nothing references.
     *
     * @param rule the rule
     * @param category the category
     * @param message the message
     */
    void addToCategory(Rule rule, ParameterCategory category, String message) {
        found.add(new Finding(rule, List.of(), Optional.of(category), message));
    }

    ValidationReport report() {
        return new ValidationReport(new ArrayList<>(found));
    }
}
