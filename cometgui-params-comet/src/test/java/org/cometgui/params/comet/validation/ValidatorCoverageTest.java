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

import static org.cometgui.params.comet.validation.Models.validate;
import static org.cometgui.params.comet.validation.Models.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValidatorId;
import org.cometgui.params.comet.value.TolerancePair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Acceptance condition 2: every validator id the metadata names has an implementation, and every
 * (parameter, validator) pair the metadata declares is actually checked -- shown by giving each
 * parameter a CONSTRUCTED value its validator must reject and finding that validator's rule
 * attached to it.
 */
class ValidatorCoverageTest {

    private static final CometValidator VALIDATOR = CometValidator.standard();

    /**
     * A model in which one parameter breaks one of its validators.
     *
     * @param p the parameter
     * @param validator one of its validators
     * @return the model
     */
    private static CometParameters breaking(ParameterDefinition p, ValidatorId validator) {
        return switch (validator) {
            case CHOICE -> with(p.name(), ChoiceAndBoundsTest.notAChoice(p));
            case PATH -> with(p.name(), "/data/a\u0000b.fasta");
            case ORDERED_RANGE -> with(p.name(), p.kind().isWholeNumbers() ? "4 2" : "4.0 2.0");
            case SIGNED_TOLERANCE_PAIR -> with(TolerancePair.LOWER, "5", TolerancePair.UPPER, "-5");
            case ENZYME_IN_TABLE -> with(p.name(), "42");
            case VARIABLE_MOD_TUPLE -> with(p.name(), "15.9949 M 0 3,1 -1 0 0 0.0");
            case WORKFLOW_ENFORCED -> with(p.name(), "0");
        };
    }

    @Test
    @DisplayName("the standard validator implements every validator id there is")
    void everyIdImplemented() {
        assertEquals(EnumSet.allOf(ValidatorId.class), VALIDATOR.implementedValidators());
    }

    @Test
    @DisplayName("every validator id the bundled metadata names is implemented, with its counts")
    void everyMetadataIdImplemented() {
        Map<ValidatorId, Long> named =
                Models.METADATA.parameters().stream()
                        .flatMap(p -> p.validators().stream())
                        .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        assertTrue(VALIDATOR.implementedValidators().containsAll(named.keySet()));
        Map<ValidatorId, Long> expected = new EnumMap<>(ValidatorId.class);
        expected.put(ValidatorId.CHOICE, 18L);
        expected.put(ValidatorId.VARIABLE_MOD_TUPLE, 15L);
        expected.put(ValidatorId.PATH, 5L);
        expected.put(ValidatorId.ORDERED_RANGE, 5L);
        expected.put(ValidatorId.ENZYME_IN_TABLE, 3L);
        expected.put(ValidatorId.SIGNED_TOLERANCE_PAIR, 2L);
        expected.put(ValidatorId.WORKFLOW_ENFORCED, 2L);
        assertEquals(expected, named);
    }

    @Test
    @DisplayName("a validator missing a rule is refused, naming the id")
    void missingRuleRefused() {
        for (ValidatorId missing : ValidatorId.values()) {
            Map<ValidatorId, FieldRule> rules = new EnumMap<>(ValidatorId.class);
            for (ValidatorId id : ValidatorId.values()) {
                if (id != missing) {
                    rules.put(id, (model, entry, findings) -> {});
                }
            }
            IllegalStateException refused =
                    assertThrows(IllegalStateException.class, () -> new CometValidator(rules));
            assertEquals(
                    "no rule implements the validator(s) ["
                            + missing.id()
                            + "]; a validator the metadata names would go unchecked",
                    refused.getMessage());
        }
    }

    @Test
    @DisplayName("the implemented set is a copy")
    void implementedIsACopy() {
        Set<ValidatorId> implemented = VALIDATOR.implementedValidators();
        implemented.clear();
        assertEquals(EnumSet.allOf(ValidatorId.class), VALIDATOR.implementedValidators());
    }

    @TestFactory
    @DisplayName("every (parameter, validator) the metadata declares is checked")
    Stream<DynamicTest> everyDeclarationChecked() {
        return Models.METADATA.parametersFor(Models.COMET).stream()
                .flatMap(
                        p ->
                                p.validators().stream()
                                        .map(
                                                v ->
                                                        DynamicTest.dynamicTest(
                                                                p.name() + " / " + v.id(),
                                                                () -> assertChecked(p, v))));
    }

    private static void assertChecked(ParameterDefinition p, ValidatorId v) {
        List<Finding> found = validate(breaking(p, v)).forParameter(p.name());
        assertTrue(
                found.stream()
                        .anyMatch(f -> f.rule().family().equals(Optional.of(v)) && f.isError()),
                () -> "no " + v.id() + " error at " + p.name() + ": " + found);
    }

    @Test
    @DisplayName("a rule's family is a validator only for that validator's rules")
    void families() {
        for (Rule rule : Rule.values()) {
            String prefix = rule.id().substring(0, rule.id().indexOf('.'));
            boolean isValidator = ValidatorId.fromId(prefix).isPresent();
            assertEquals(
                    isValidator ? ValidatorId.fromId(prefix) : Optional.empty(),
                    rule.family(),
                    rule.id());
        }
        assertFalse(Rule.BELOW_MINIMUM.family().isPresent());
    }
}
