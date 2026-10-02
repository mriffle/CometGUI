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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The result model: findings, the report, and the rule vocabulary. */
class ReportModelTest {

    private static final Finding ERROR =
            new Finding(
                    Rule.PAIR_REVERSED,
                    List.of("peptide_mass_tolerance_lower", "peptide_mass_tolerance_upper"),
                    Optional.of(ParameterCategory.PRECURSOR_MASS),
                    "reversed");

    private static final Finding WARNING =
            new Finding(
                    Rule.VARMOD_COUNT_ZERO,
                    List.of("variable_mod01"),
                    Optional.of(ParameterCategory.VARIABLE_MODS),
                    "zero");

    private static final Finding FILE =
            new Finding(Rule.IMPORT_DIAGNOSTIC, List.of(), Optional.empty(), "marker");

    @Test
    @DisplayName("a finding takes its severity from its rule and needs a message")
    void finding() {
        assertEquals(Severity.ERROR, ERROR.severity());
        assertTrue(ERROR.isError());
        assertEquals(Severity.WARNING, WARNING.severity());
        assertFalse(WARNING.isError());
        assertTrue(ERROR.concerns("peptide_mass_tolerance_upper"));
        assertFalse(ERROR.concerns("variable_mod01"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Finding(Rule.PAIR_REVERSED, List.of(), Optional.empty(), " "));
        List<String> names = new ArrayList<>(List.of("a"));
        Finding copied = new Finding(Rule.PATH_EMPTY, names, Optional.empty(), "m");
        names.add("b");
        assertEquals(List.of("a"), copied.parameters());
        assertThrows(UnsupportedOperationException.class, () -> copied.parameters().add("c"));
    }

    @Test
    @DisplayName("the report separates errors from warnings and finds by parameter and category")
    void report() {
        ValidationReport report = new ValidationReport(List.of(WARNING, ERROR, FILE));
        assertTrue(report.hasErrors());
        assertEquals(List.of(ERROR), report.errors());
        assertEquals(List.of(WARNING, FILE), report.warnings());
        assertEquals(List.of(ERROR), report.forParameter("peptide_mass_tolerance_lower"));
        assertEquals(List.of(WARNING), report.forParameter("variable_mod01"));
        assertEquals(List.of(), report.forParameter("database_name"));
        assertEquals(List.of(ERROR), report.forCategory(ParameterCategory.PRECURSOR_MASS));
        assertEquals(List.of(WARNING), report.forCategory(ParameterCategory.VARIABLE_MODS));
        assertEquals(List.of(), report.forCategory(ParameterCategory.OUTPUT));
        assertEquals(List.of(FILE), report.of(Rule.IMPORT_DIAGNOSTIC));
        assertEquals(List.of(), report.of(Rule.PATH_NUL));
        assertFalse(new ValidationReport(List.of(WARNING, FILE)).hasErrors());
        assertFalse(new ValidationReport(List.of()).hasErrors());
        assertThrows(UnsupportedOperationException.class, () -> report.findings().clear());
    }

    @Test
    @DisplayName("rule identifiers are unique, lower-case and dotted")
    void ruleIds() {
        Set<String> ids = new HashSet<>();
        Pattern shape = Pattern.compile("[a-z_]+\\.[a-z_]+");
        for (Rule rule : Rule.values()) {
            assertTrue(ids.add(rule.id()), rule.id());
            assertTrue(shape.matcher(rule.id()).matches(), rule.id());
        }
        assertEquals("signed_tolerance_pair.asymmetric", Rule.PAIR_ASYMMETRIC.id());
        assertEquals(Severity.WARNING, Rule.PAIR_ASYMMETRIC.severity());
        assertEquals(Severity.WARNING, Rule.PAIR_SAME_SIGNED.severity());
        assertEquals(Severity.ERROR, Rule.PAIR_REVERSED.severity());
        assertEquals(Severity.ERROR, Rule.UNAVAILABLE_IN_VERSION.severity());
        assertEquals(Severity.WARNING, Rule.UNKNOWN_PARAMETER.severity());
    }
}
