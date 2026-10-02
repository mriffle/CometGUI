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

import static org.cometgui.params.comet.validation.Models.assertAttached;
import static org.cometgui.params.comet.validation.Models.only;
import static org.cometgui.params.comet.validation.Models.validate;
import static org.cometgui.params.comet.validation.Models.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ValidatorId;
import org.cometgui.params.comet.value.TolerancePair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Gate item 7: the signed precursor tolerance pair is graded by {@code R-PARAM-04}'s own rule,
 * {@code lower <= 0 <= upper} with a warning for a deliberate asymmetric or same-signed window, and
 * never by the generic ordering rule. Every window here is CONSTRUCTED test input applied to the
 * real {@code -q} model.
 */
class TolerancePairRuleTest {

    private static final String LOWER = TolerancePair.LOWER;

    private static final String UPPER = TolerancePair.UPPER;

    private static ValidationReport window(String lower, String upper) {
        return validate(with(LOWER, lower, UPPER, upper));
    }

    private static void assertPair(ValidationReport report, Rule rule) {
        assertAttached(only(report), rule, ParameterCategory.PRECURSOR_MASS, LOWER, UPPER);
    }

    @Test
    @DisplayName("-20.0 / 20.0, Comet's default, is clean")
    void defaultIsClean() {
        assertEquals(List.of(), window("-20.0", "20.0").findings());
    }

    @Test
    @DisplayName("-10 / 20 is asymmetric: a warning, by the pair rule")
    void asymmetric() {
        ValidationReport report = window("-10", "20");
        assertPair(report, Rule.PAIR_ASYMMETRIC);
        assertEquals(Severity.WARNING, report.findings().get(0).severity());
        assertFalse(report.hasErrors());
        assertTrue(
                report.findings()
                        .get(0)
                        .message()
                        .contains(LOWER + " = -10 and " + UPPER + " = 20"),
                report.findings().get(0).message());
    }

    @Test
    @DisplayName("5 / 20 is same-signed: a warning, by the pair rule")
    void positiveWindow() {
        ValidationReport report = window("5", "20");
        assertPair(report, Rule.PAIR_SAME_SIGNED);
        assertFalse(report.hasErrors());
    }

    @Test
    @DisplayName("-20 / -5 is same-signed: a warning, by the pair rule")
    void negativeWindow() {
        ValidationReport report = window("-20", "-5");
        assertPair(report, Rule.PAIR_SAME_SIGNED);
        assertFalse(report.hasErrors());
    }

    @Test
    @DisplayName("20 / -20 is reversed: an error, by the pair rule")
    void reversed() {
        ValidationReport report = window("20", "-20");
        assertPair(report, Rule.PAIR_REVERSED);
        assertTrue(report.hasErrors());
        assertEquals(Severity.ERROR, report.findings().get(0).severity());
        assertTrue(report.findings().get(0).message().contains("lower bound is above"));
    }

    @Test
    @DisplayName("reversed and same-signed (-5 / -20) is reported as reversed only")
    void reversedFirst() {
        assertPair(window("-5", "-20"), Rule.PAIR_REVERSED);
        assertPair(window("20", "5"), Rule.PAIR_REVERSED);
    }

    @Test
    @DisplayName("the boundaries: 0 / 0 and -7.50 / 7.5 are clean, 0 / 20 and -20 / 0 asymmetric")
    void boundaries() {
        assertEquals(List.of(), window("0", "0").findings());
        assertEquals(List.of(), window("-7.50", "7.5").findings());
        assertEquals(List.of(), window("-20", "20.000").findings());
        assertPair(window("0", "20"), Rule.PAIR_ASYMMETRIC);
        assertPair(window("-20", "0"), Rule.PAIR_ASYMMETRIC);
        assertPair(window("0.001", "20"), Rule.PAIR_SAME_SIGNED);
        assertPair(window("-20", "-0.001"), Rule.PAIR_SAME_SIGNED);
        assertPair(window("20.0001", "20"), Rule.PAIR_REVERSED);
        assertPair(window("-20.0001", "20"), Rule.PAIR_ASYMMETRIC);
    }

    /**
     * The two rules disagree on these windows, which is what makes the test able to tell them
     * apart: the generic "lower <= upper" accepts all of the first four silently and would call the
     * last its own {@code ordered_range.reversed}.
     */
    @TestFactory
    @DisplayName("the generic ordering rule never grades the pair")
    Stream<DynamicTest> genericRuleNeverFires() {
        return Stream.of(
                        new String[] {"-10", "20", "signed_tolerance_pair.asymmetric"},
                        new String[] {"5", "20", "signed_tolerance_pair.same_signed"},
                        new String[] {"-20", "-5", "signed_tolerance_pair.same_signed"},
                        new String[] {"0", "20", "signed_tolerance_pair.asymmetric"},
                        new String[] {"20", "-20", "signed_tolerance_pair.reversed"})
                .map(
                        w ->
                                DynamicTest.dynamicTest(
                                        w[0] + " / " + w[1],
                                        () -> {
                                            ValidationReport report = window(w[0], w[1]);
                                            for (Finding finding : report.findings()) {
                                                assertEquals(
                                                        Optional.of(
                                                                ValidatorId.SIGNED_TOLERANCE_PAIR),
                                                        finding.rule().family(),
                                                        finding::toString);
                                                assertFalse(
                                                        finding.rule()
                                                                .id()
                                                                .startsWith("ordered_range"));
                                                assertFalse(
                                                        finding.message()
                                                                .contains(
                                                                        "silently ignore the"
                                                                                + " range"));
                                            }
                                            assertEquals(
                                                    List.of(w[2]),
                                                    report.findings().stream()
                                                            .map(f -> f.rule().id())
                                                            .toList());
                                            assertEquals(List.of(), report.of(Rule.RANGE_REVERSED));
                                        }));
    }

    @Test
    @DisplayName("the metadata gives both members the pair rule and neither the generic one")
    void metadata() {
        for (String member : List.of(LOWER, UPPER)) {
            List<ValidatorId> validators =
                    Models.METADATA.parameter(member).orElseThrow().validators();
            assertEquals(List.of(ValidatorId.SIGNED_TOLERANCE_PAIR), validators, member);
        }
    }

    @Test
    @DisplayName("the verdict does not depend on the units: amu, mmu and ppm alike")
    void units() {
        for (String units : List.of("0", "1", "2")) {
            assertEquals(
                    List.of(),
                    validate(with("peptide_mass_units", units, LOWER, "-3", UPPER, "3")).findings(),
                    units);
            assertPair(
                    validate(with("peptide_mass_units", units, LOWER, "-1", UPPER, "3")),
                    Rule.PAIR_ASYMMETRIC);
            assertPair(
                    validate(with("peptide_mass_units", units, LOWER, "3", UPPER, "-1")),
                    Rule.PAIR_REVERSED);
        }
    }

    @Test
    @DisplayName("both members name the rule, and the pair is still reported once")
    void reportedOnce() {
        ValidationReport report = window("20", "-20");
        assertEquals(1, report.forParameter(LOWER).size());
        assertEquals(1, report.forParameter(UPPER).size());
        assertEquals(report.forParameter(LOWER), report.forParameter(UPPER));
    }
}
