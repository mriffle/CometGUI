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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Variable-modification tuples, slot by slot, and {@code R-PARAM-10}'s cross-validation against
 * {@code max_variable_mods_in_peptide} and {@code require_variable_mod}. Every tuple here is
 * CONSTRUCTED test input applied to the real {@code -q} model, whose {@code variable_mod01} is
 * Comet's own {@code 15.9949 M 0 3 -1 0 0 0.0} and whose other fourteen slots are unused.
 */
class VariableModRulesTest {

    private static final String SLOT = "variable_mod01";

    private static final String UNUSED = "0.0 X 0 3 -1 0 0 0.0";

    private static final ParameterCategory MODS = ParameterCategory.VARIABLE_MODS;

    private static Finding slot(String tuple) {
        return only(validate(with(SLOT, tuple)));
    }

    private static void clean(String... nameThenText) {
        assertEquals(
                List.of(),
                validate(with(nameThenText)).findings(),
                List.of(nameThenText)::toString);
    }

    @Nested
    @DisplayName("per slot")
    class PerSlot {

        @TestFactory
        @DisplayName("each of the fifteen slots is checked, attached to itself")
        Stream<DynamicTest> everySlot() {
            return IntStream.rangeClosed(1, 15)
                    .mapToObj(n -> String.format(Locale.ROOT, "variable_mod%02d", n))
                    .map(
                            name ->
                                    DynamicTest.dynamicTest(
                                            name,
                                            () -> {
                                                Finding finding =
                                                        only(
                                                                validate(
                                                                        with(
                                                                                name,
                                                                                "79.966331 STY 0"
                                                                                        + " 4,2 -1"
                                                                                        + " 0 0"
                                                                                        + " 0.0")));
                                                assertAttached(
                                                        finding,
                                                        Rule.VARMOD_COUNT_REVERSED,
                                                        MODS,
                                                        name);
                                            }));
        }

        @Test
        @DisplayName("residue tokens of 31 characters are read, of 32 are not -- in any slot")
        void residueLength() {
            String fits = "ACDEFGHIKLMNPQRSTVWYACDEFGHIKLM";
            assertEquals(31, fits.length());
            clean(SLOT, "15.9949 " + fits + " 0 3 -1 0 0 0.0");
            Finding active = slot("15.9949 " + fits + "N 0 3 -1 0 0 0.0");
            assertAttached(active, Rule.VARMOD_RESIDUES_TOO_LONG, MODS, SLOT);
            assertTrue(active.message().contains("has 32 characters"), active.message());
            Finding unused =
                    only(validate(with("variable_mod07", "0.0 " + fits + "N 0 3 -1 0 0 0.0")));
            assertAttached(unused, Rule.VARMOD_RESIDUES_TOO_LONG, MODS, "variable_mod07");
        }

        @Test
        @DisplayName("counts: negative is an error, min above max an error, max 0 a warning")
        void counts() {
            assertAttached(
                    slot("15.9949 M 0 -1 -1 0 0 0.0"), Rule.VARMOD_COUNT_NEGATIVE, MODS, SLOT);
            assertAttached(
                    slot("15.9949 M 0 -1,3 -1 0 0 0.0"), Rule.VARMOD_COUNT_NEGATIVE, MODS, SLOT);
            Finding reversed = slot("15.9949 M 0 3,2 -1 0 0 0.0");
            assertAttached(reversed, Rule.VARMOD_COUNT_REVERSED, MODS, SLOT);
            assertEquals(
                    SLOT
                            + " = 15.9949 M 0 3,2 -1 0 0 0.0: the minimum count 3 is above the"
                            + " maximum 2, so the modification can never be applied; give the"
                            + " smaller count first",
                    reversed.message());
            Finding zero = slot("15.9949 M 0 0 -1 0 0 0.0");
            assertAttached(zero, Rule.VARMOD_COUNT_ZERO, MODS, SLOT);
            assertEquals(Severity.WARNING, zero.severity());
            clean(SLOT, "15.9949 M 0 2,2 -1 0 0 0.0");
            clean(SLOT, "15.9949 M 0 0,1 -1 0 0 0.0");
            clean(SLOT, "15.9949 M 0 1 -1 0 0 0.0");
        }

        @Test
        @DisplayName("distance: -2, -1 and 0 up are documented; -3 is a warning")
        void distance() {
            clean(SLOT, "15.9949 M 0 3 -2 0 0 0.0");
            clean(SLOT, "15.9949 M 0 3 0 0 0 0.0");
            clean(SLOT, "28.0 c 0 3 8 1 0 0.0");
            Finding odd = slot("15.9949 M 0 3 -3 0 0 0.0");
            assertAttached(odd, Rule.VARMOD_DISTANCE_UNDOCUMENTED, MODS, SLOT);
            assertEquals(Severity.WARNING, odd.severity());
        }

        @Test
        @DisplayName("terminus: an unknown code is an error where a distance consults it")
        void terminus() {
            for (String terminus : List.of("0", "1", "2", "3")) {
                clean(SLOT, "42.010565 n 0 3 0 " + terminus + " 0 0.0");
            }
            Finding unknown = slot("42.010565 n 0 3 0 4 0 0.0");
            assertAttached(unknown, Rule.VARMOD_TERMINUS_UNDOCUMENTED, MODS, SLOT);
            assertTrue(unknown.message().contains("terminus code 4 is not one Comet knows"));
            assertAttached(
                    slot("42.010565 n 0 3 0 -1 0 0.0"),
                    Rule.VARMOD_TERMINUS_UNDOCUMENTED,
                    MODS,
                    SLOT);
            clean(SLOT, "42.010565 n 0 3 -1 4 0 0.0");
            clean(SLOT, "42.010565 n 0 3 -2 9 0 0.0");
        }

        @Test
        @DisplayName("requirement: 0, 1 and -1 are documented; others are a warning")
        void requirement() {
            for (String code : List.of("0", "1", "-1")) {
                clean(SLOT, "15.9949 M 0 3 -1 0 " + code + " 0.0");
            }
            Finding two = slot("15.9949 M 0 3 -1 0 2 0.0");
            assertAttached(two, Rule.VARMOD_REQUIREMENT_UNDOCUMENTED, MODS, SLOT);
            assertTrue(
                    two.message()
                            .endsWith("Comet treats it as 1 (required), so write that instead"));
            Finding minusTwo = slot("15.9949 M 0 3 -1 0 -2 0.0");
            assertAttached(minusTwo, Rule.VARMOD_REQUIREMENT_UNDOCUMENTED, MODS, SLOT);
            assertTrue(
                    minusTwo.message()
                            .endsWith("Comet treats it as 0 (optional), so write that instead"));
        }

        @Test
        @DisplayName("binary groups: 0 and positive groups are documented; negative a warning")
        void binaryGroups() {
            clean(SLOT, "6.0 R 1 3 -1 0 0 0.0", "variable_mod02", "4.0 K 1 3 -1 0 0 0.0");
            clean(SLOT, "10.0 R 2 3 -1 0 0 0.0");
            Finding negative = slot("6.0 R -1 3 -1 0 0 0.0");
            assertAttached(negative, Rule.VARMOD_BINARY_GROUP_NEGATIVE, MODS, SLOT);
            assertEquals(Severity.WARNING, negative.severity());
        }

        @Test
        @DisplayName("an unused slot (mass 0) is not checked for meaning")
        void unusedSlot() {
            clean("variable_mod05", "0.0 X -1 3,1 -9 7 5 0.0");
            clean("variable_mod05", "0.0 X 0 -4 -1 0 0 0.0");
        }

        @Test
        @DisplayName("Comet's documented examples are clean")
        void documentedExamples() {
            for (String tuple :
                    List.of(
                            "79.966331 STY 0 3 -1 0 0 97.976896",
                            "79.966331 STY 0 3 -1 0 0 97.976896,79.966331",
                            "79.966331 STY 0 3 -1 0 1 0.0",
                            "42.010565 nK 0 3 -1 0 0 0.0",
                            "15.994915 n 0 3 0 0 0 0.0",
                            "28.0 c 0 3 8 1 0 0.0",
                            "-17.026549 Q 0 1 0 2 0 0.0")) {
                clean(SLOT, tuple);
            }
        }
    }

    @Nested
    @DisplayName("R-PARAM-10")
    class Limits {

        @Test
        @DisplayName("require_variable_mod = 1 with no active slot is an error")
        void requiredWithoutSlot() {
            Finding finding = only(validate(with(SLOT, UNUSED, "require_variable_mod", "1")));
            assertAttached(
                    finding, Rule.VARMODS_REQUIRED_WITHOUT_SLOT, MODS, "require_variable_mod");
            assertTrue(finding.message().startsWith("require_variable_mod = 1 requires"));
            clean("require_variable_mod", "1");
            clean(SLOT, UNUSED);
        }

        @Test
        @DisplayName("a slot's minimum above max_variable_mods_in_peptide is an error at both")
        void minimumAboveLimit() {
            Finding finding = only(validate(with(SLOT, "15.9949 M 0 6,8 -1 0 0 0.0")));
            assertAttached(
                    finding,
                    Rule.VARMODS_MINIMUM_ABOVE_LIMIT,
                    MODS,
                    SLOT,
                    "max_variable_mods_in_peptide");
            assertEquals(
                    SLOT
                            + " = 15.9949 M 0 6,8 -1 0 0 0.0 needs at least 6 modified residues,"
                            + " but max_variable_mods_in_peptide = 5 allows at most 5 in a peptide,"
                            + " so the slot can never apply; lower its minimum or raise the limit",
                    finding.message());
            clean(SLOT, "15.9949 M 0 5,8 -1 0 0 0.0");
            clean(SLOT, "15.9949 M 0 8 -1 0 0 0.0");
            clean(SLOT, "15.9949 M 0 6,8 -1 0 0 0.0", "max_variable_mods_in_peptide", "6");
        }

        @Test
        @DisplayName(
                "max_variable_mods_in_peptide = 0 while a modification is required is an error")
        void requiredButNoneAllowed() {
            Finding byFlag =
                    only(
                            validate(
                                    with(
                                            "max_variable_mods_in_peptide",
                                            "0",
                                            "require_variable_mod",
                                            "1")));
            assertAttached(
                    byFlag,
                    Rule.VARMODS_REQUIRED_BUT_NONE_ALLOWED,
                    MODS,
                    "max_variable_mods_in_peptide",
                    "require_variable_mod");
            assertEquals(
                    "max_variable_mods_in_peptide = 0 allows no variable modification in a"
                            + " peptide, but one is required by require_variable_mod, so nothing"
                            + " can be identified; raise the limit",
                    byFlag.message());
            Finding bySlot =
                    only(
                            validate(
                                    with(
                                            "max_variable_mods_in_peptide",
                                            "0",
                                            "variable_mod03",
                                            "79.966331 STY 0 3 -1 0 1 0.0")));
            assertAttached(
                    bySlot,
                    Rule.VARMODS_REQUIRED_BUT_NONE_ALLOWED,
                    MODS,
                    "max_variable_mods_in_peptide",
                    "variable_mod03");
        }

        @Test
        @DisplayName("max_variable_mods_in_peptide = 0 with only optional slots is a warning")
        void noneAllowed() {
            Finding finding =
                    only(
                            validate(
                                    with(
                                            "max_variable_mods_in_peptide",
                                            "0",
                                            "variable_mod02",
                                            "79.966331 STY 0 3 -1 0 -1 0.0")));
            assertAttached(
                    finding,
                    Rule.VARMODS_NONE_ALLOWED,
                    MODS,
                    "max_variable_mods_in_peptide",
                    SLOT,
                    "variable_mod02");
            assertEquals(Severity.WARNING, finding.severity());
            clean("max_variable_mods_in_peptide", "0", SLOT, UNUSED);
            clean("max_variable_mods_in_peptide", "1");
        }

        @Test
        @DisplayName("a negative limit is the bounds rule's error and caps nothing")
        void negativeLimit() {
            for (String slot : List.of("15.9949 M 0 3 -1 0 0 0.0", "15.9949 M 0 1,3 -1 0 1 0.0")) {
                assertEquals(
                        List.of(Rule.BELOW_MINIMUM),
                        validate(with(SLOT, slot, "max_variable_mods_in_peptide", "-1"))
                                .findings()
                                .stream()
                                .map(Finding::rule)
                                .toList(),
                        slot);
            }
        }

        @Test
        @DisplayName("limit 0, required, no active slot: only the missing slot is reported")
        void requiredNothingActiveLimitZero() {
            assertEquals(
                    List.of(Rule.VARMODS_REQUIRED_WITHOUT_SLOT),
                    validate(
                                    with(
                                            SLOT,
                                            UNUSED,
                                            "require_variable_mod",
                                            "1",
                                            "max_variable_mods_in_peptide",
                                            "0"))
                            .findings()
                            .stream()
                            .map(Finding::rule)
                            .toList());
        }

        @Test
        @DisplayName("limit 0 with a minimum: both the minimum and the cap are reported")
        void zeroLimitWithMinimum() {
            assertEquals(
                    List.of(Rule.VARMODS_MINIMUM_ABOVE_LIMIT, Rule.VARMODS_NONE_ALLOWED),
                    validate(
                                    with(
                                            SLOT,
                                            "15.9949 M 0 1,3 -1 0 0 0.0",
                                            "max_variable_mods_in_peptide",
                                            "0"))
                            .findings()
                            .stream()
                            .map(Finding::rule)
                            .toList());
        }
    }
}
