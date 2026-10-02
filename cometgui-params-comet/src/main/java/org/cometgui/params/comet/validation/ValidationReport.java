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
import org.cometgui.params.comet.schema.ParameterCategory;

/**
 * Everything the validators found in one model, in a stable order: per parameter in the model's
 * order, then the cross-field rules, then what the import left behind.
 *
 * <p>{@link #hasErrors()} is what blocks a run (Phase 08); {@link #forParameter(String)} and {@link
 * #forCategory(ParameterCategory)} are what the editor shows at a control and a category heading
 * (Phase 07).
 *
 * @param findings every finding, each once
 */
public record ValidationReport(List<Finding> findings) {

    /** Takes an immutable copy. */
    public ValidationReport {
        findings = List.copyOf(findings);
    }

    /**
     * Every finding, immutable.
     *
     * @return the findings
     */
    @Override
    public List<Finding> findings() {
        return List.copyOf(findings);
    }

    /**
     * Whether any finding is an error: the configuration must not be run.
     *
     * @return {@code true} if there is at least one error
     */
    public boolean hasErrors() {
        return findings.stream().anyMatch(Finding::isError);
    }

    /**
     * The errors.
     *
     * @return the findings of severity {@link Severity#ERROR}, in report order
     */
    public List<Finding> errors() {
        return findings.stream().filter(Finding::isError).toList();
    }

    /**
     * The warnings.
     *
     * @return the findings of severity {@link Severity#WARNING}, in report order
     */
    public List<Finding> warnings() {
        return findings.stream().filter(finding -> !finding.isError()).toList();
    }

    /**
     * The findings attached to one parameter.
     *
     * @param name the parameter name
     * @return the findings that name it, in report order
     */
    public List<Finding> forParameter(String name) {
        Objects.requireNonNull(name, "name");
        return findings.stream().filter(finding -> finding.concerns(name)).toList();
    }

    /**
     * The findings attached to one category.
     *
     * @param category the category
     * @return the findings in it, in report order
     */
    public List<Finding> forCategory(ParameterCategory category) {
        Objects.requireNonNull(category, "category");
        return findings.stream()
                .filter(finding -> finding.category().filter(category::equals).isPresent())
                .toList();
    }

    /**
     * The findings of one rule.
     *
     * @param rule the rule
     * @return its findings, in report order
     */
    public List<Finding> of(Rule rule) {
        Objects.requireNonNull(rule, "rule");
        return findings.stream().filter(finding -> finding.rule() == rule).toList();
    }
}
