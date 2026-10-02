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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.params.comet.schema.Choice;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValidatorId;
import org.cometgui.params.comet.schema.ValueKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The {@code choice} validator and the curated bounds, over every parameter that has them -- the
 * cases are generated from the bundled metadata, so a parameter given a choice list or a bound
 * later is tested without anyone remembering to add it. Values are CONSTRUCTED edits of the real
 * {@code -q} model.
 */
class ChoiceAndBoundsTest {

    /**
     * A value text a parameter's choices do not hold.
     *
     * @param definition an enumerated parameter
     * @return the text
     */
    static String notAChoice(ParameterDefinition definition) {
        if (definition.kind() == ValueKind.STRING_ENUM) {
            return "NOT_A_CHOICE";
        }
        int largest =
                definition.choices().stream()
                        .mapToInt(choice -> Integer.parseInt(choice.value()))
                        .max()
                        .orElseThrow();
        return Integer.toString(largest + 100);
    }

    /**
     * A value text whose every number is one number.
     *
     * @param definition a bounded parameter
     * @param number the number
     * @return the text, for example {@code 7} or {@code 7 7}
     */
    static String allNumbers(ParameterDefinition definition, BigDecimal number) {
        String one = number.toPlainString();
        return switch (definition.kind()) {
            case INTEGER_RANGE, DECIMAL_RANGE -> one + " " + one;
            default -> one;
        };
    }

    private static Stream<ParameterDefinition> parameters() {
        return Models.METADATA.parametersFor(Models.COMET).stream();
    }

    @TestFactory
    @DisplayName("every choice parameter: an unlisted value is an error, every listed one is not")
    Stream<DynamicTest> choices() {
        return parameters()
                .filter(p -> p.validators().contains(ValidatorId.CHOICE))
                .map(
                        p ->
                                DynamicTest.dynamicTest(
                                        p.name(),
                                        () -> {
                                            String bad = notAChoice(p);
                                            Finding finding = only(validate(with(p.name(), bad)));
                                            assertAttached(
                                                    finding,
                                                    Rule.CHOICE_NOT_LISTED,
                                                    p.category(),
                                                    p.name());
                                            assertTrue(
                                                    finding.message()
                                                            .startsWith(
                                                                    p.name()
                                                                            + " = "
                                                                            + bad
                                                                            + " is not one of its"
                                                                            + " values; use one"
                                                                            + " of "),
                                                    finding.message());
                                            for (Choice choice : p.choices()) {
                                                assertTrue(
                                                        finding.message()
                                                                .contains(
                                                                        choice.value()
                                                                                + " ("
                                                                                + choice.label()
                                                                                + ")"),
                                                        choice.value());
                                                assertEquals(
                                                        List.of(),
                                                        validate(with(p.name(), choice.value()))
                                                                .of(Rule.CHOICE_NOT_LISTED),
                                                        choice.value());
                                            }
                                        }));
    }

    @Test
    @DisplayName("there are 18 choice parameters, every enumerated one")
    void choiceParameters() {
        List<ParameterDefinition> enumerated =
                parameters().filter(p -> p.kind().isEnumeration()).toList();
        assertEquals(18, enumerated.size());
        for (ParameterDefinition p : enumerated) {
            assertTrue(p.validators().contains(ValidatorId.CHOICE), p.name());
        }
    }

    @TestFactory
    @DisplayName("every bounded parameter: below its minimum and above its maximum are errors")
    Stream<DynamicTest> bounds() {
        return parameters()
                .filter(p -> p.minimum().isPresent() || p.maximum().isPresent())
                .map(
                        p ->
                                DynamicTest.dynamicTest(
                                        p.name(),
                                        () -> {
                                            p.minimum()
                                                    .map(BigDecimal::new)
                                                    .ifPresent(
                                                            min ->
                                                                    bound(
                                                                            p,
                                                                            min,
                                                                            min.subtract(
                                                                                    BigDecimal.ONE),
                                                                            Rule.BELOW_MINIMUM));
                                            p.maximum()
                                                    .map(BigDecimal::new)
                                                    .ifPresent(
                                                            max ->
                                                                    bound(
                                                                            p,
                                                                            max,
                                                                            max.add(BigDecimal.ONE),
                                                                            Rule.ABOVE_MAXIMUM));
                                        }));
    }

    private static void bound(
            ParameterDefinition p, BigDecimal limit, BigDecimal beyond, Rule rule) {
        List<Finding> found =
                validate(with(p.name(), allNumbers(p, beyond))).forParameter(p.name()).stream()
                        .filter(f -> f.rule() == rule)
                        .toList();
        int numbers =
                p.kind() == ValueKind.INTEGER_RANGE || p.kind() == ValueKind.DECIMAL_RANGE ? 2 : 1;
        assertEquals(numbers, found.size(), () -> p.name() + " " + beyond + ": " + found);
        for (Finding finding : found) {
            assertAttached(finding, rule, p.category(), p.name());
            assertTrue(finding.message().contains(beyond.toPlainString()), finding.message());
        }
        assertEquals(
                List.of(),
                validate(with(p.name(), allNumbers(p, limit))).forParameter(p.name()).stream()
                        .filter(f -> f.rule() == rule)
                        .toList(),
                () -> p.name() + " at its bound " + limit);
    }

    @Test
    @DisplayName("bounds messages name the value, which number it is, and what is valid")
    void boundMessages() {
        assertEquals(
                "fragment_bin_tol: the value 0.001 is below the minimum 0.01; use 0.01 or more",
                only(validate(with("fragment_bin_tol", "0.001"))).message());
        assertEquals(
                "num_threads: the value 129 is above the maximum 128; use 128 or less",
                only(validate(with("num_threads", "129"))).message());
        assertEquals(
                List.of(
                        "peptide_length_range: the second value 51 is above the maximum 50; use"
                                + " 50 or less"),
                validate(with("peptide_length_range", "5 51")).findings().stream()
                        .map(Finding::message)
                        .toList());
        assertEquals(
                List.of(
                        "peptide_length_range: the first value 0 is below the minimum 1; use 1 or"
                                + " more"),
                validate(with("peptide_length_range", "0 5")).findings().stream()
                        .map(Finding::message)
                        .toList());
        Finding list = only(validate(with("mass_offsets", "0.0 1.003 -1.003")));
        assertAttached(list, Rule.BELOW_MINIMUM, ParameterCategory.PRECURSOR_MASS, "mass_offsets");
        assertEquals(
                "mass_offsets: value 3, -1.003 is below the minimum 0.0; use 0.0 or more",
                list.message());
        assertEquals(List.of(), validate(with("mass_offsets", "0.0 1.003")).findings());
    }

    @Test
    @DisplayName("a parameter with no bound and no validator is never reported")
    void unboundedIsFree() {
        ParameterDefinition free = Models.METADATA.parameter("num_threads").orElseThrow();
        assertEquals(Optional.empty(), free.minimum());
        assertEquals(List.of(), validate(with("num_threads", "-5")).findings());
        assertFalse(validate(with("num_threads", "-5")).hasErrors());
    }
}
