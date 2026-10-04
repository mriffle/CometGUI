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
                    Severity.ERROR,
                    List.of("peptide_mass_tolerance_lower", "peptide_mass_tolerance_upper"),
                    Optional.of(ParameterCategory.PRECURSOR_MASS),
                    "reversed");

    private static final Finding WARNING =
            new Finding(
                    Rule.VARMOD_COUNT_ZERO,
                    Severity.WARNING,
                    List.of("variable_mod01"),
                    Optional.of(ParameterCategory.VARIABLE_MODS),
                    "zero");

    private static final Finding FILE =
            new Finding(
                    Rule.IMPORT_DIAGNOSTIC,
                    Severity.WARNING,
                    List.of(),
                    Optional.empty(),
                    "marker");

    @Test
    @DisplayName("a finding has its rule's fixed severity and needs a message")
    void finding() {
        assertEquals(Severity.ERROR, ERROR.severity());
        assertTrue(ERROR.isError());
        assertEquals(Severity.WARNING, WARNING.severity());
        assertFalse(WARNING.isError());
        assertTrue(ERROR.concerns("peptide_mass_tolerance_upper"));
        assertFalse(ERROR.concerns("variable_mod01"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Finding(
                                Rule.PAIR_REVERSED,
                                Severity.ERROR,
                                List.of(),
                                Optional.empty(),
                                " "));
        IllegalArgumentException fixed =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new Finding(
                                        Rule.PAIR_REVERSED,
                                        Severity.WARNING,
                                        List.of(),
                                        Optional.empty(),
                                        "m"));
        assertEquals(
                "signed_tolerance_pair.reversed is always ERROR, not WARNING", fixed.getMessage());
        List<String> names = new ArrayList<>(List.of("a"));
        Finding copied = new Finding(Rule.PATH_EMPTY, Severity.ERROR, names, Optional.empty(), "m");
        names.add("b");
        assertEquals(List.of("a"), copied.parameters());
        assertThrows(UnsupportedOperationException.class, () -> copied.parameters().add("c"));
    }

    @Test
    @DisplayName("a finding of a version-scoped rule carries the severity it was given")
    void versionScopedFinding() {
        for (Severity severity : Severity.values()) {
            Finding finding =
                    new Finding(
                            Rule.VARMOD_DISTANCE_UNDOCUMENTED,
                            severity,
                            List.of("variable_mod01"),
                            Optional.of(ParameterCategory.VARIABLE_MODS),
                            "distance");
            assertEquals(severity, finding.severity());
            assertEquals(severity == Severity.ERROR, finding.isError());
        }
        assertThrows(
                NullPointerException.class,
                () ->
                        new Finding(
                                Rule.VARMOD_DISTANCE_UNDOCUMENTED,
                                null,
                                List.of(),
                                Optional.empty(),
                                "m"));
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
        assertEquals(Optional.of(Severity.WARNING), Rule.PAIR_ASYMMETRIC.fixedSeverity());
        assertEquals(Optional.of(Severity.WARNING), Rule.PAIR_SAME_SIGNED.fixedSeverity());
        assertEquals(Optional.of(Severity.ERROR), Rule.PAIR_REVERSED.fixedSeverity());
        assertEquals(Optional.of(Severity.ERROR), Rule.UNAVAILABLE_IN_VERSION.fixedSeverity());
        assertEquals(Optional.of(Severity.WARNING), Rule.UNKNOWN_PARAMETER.fixedSeverity());
        assertEquals(
                Optional.of(Severity.ERROR), Rule.VARMOD_RESIDUE_NOT_IN_RELEASE.fixedSeverity());
        assertEquals(Optional.of(Severity.ERROR), Rule.VARMODS_ASCOREPRO_SLOT.fixedSeverity());
        for (Rule rule : Rule.values()) {
            assertTrue(
                    org.cometgui.params.comet.schema.RuleSeverity.RULE_ID
                            .matcher(rule.id())
                            .matches(),
                    rule.id());
            assertEquals(Optional.of(rule), Rule.byId(rule.id()));
            assertEquals(rule.isVersionScoped(), rule.fixedSeverity().isEmpty(), rule.id());
        }
        assertEquals(Optional.empty(), Rule.byId("variable_mod_tuple.no_such_rule"));
    }

    @Test
    @DisplayName("exactly the rules Comet releases judge differently are version-scoped")
    void versionScopedRules() {
        List<Rule> scoped = new ArrayList<>();
        for (Rule rule : Rule.values()) {
            if (rule.isVersionScoped()) {
                scoped.add(rule);
            }
        }
        assertEquals(
                List.of(Rule.VARMOD_DISTANCE_UNDOCUMENTED, Rule.INDEX_SEARCH_TYPE_IGNORED), scoped);
        assertEquals(
                "variable_mod_tuple.distance_undocumented", Rule.VARMOD_DISTANCE_UNDOCUMENTED.id());
        assertEquals("index_search_type.ignored_without_idx", Rule.INDEX_SEARCH_TYPE_IGNORED.id());
        assertEquals(
                "variable_mod_tuple.residue_not_in_release",
                Rule.VARMOD_RESIDUE_NOT_IN_RELEASE.id());
        assertEquals("variable_mods.ascorepro_slot_unsupported", Rule.VARMODS_ASCOREPRO_SLOT.id());
    }
}
