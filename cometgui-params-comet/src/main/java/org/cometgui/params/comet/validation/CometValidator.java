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

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.schema.ValidatorId;

/**
 * Validates a {@link CometParameters} model: every per-field rule the metadata names, every
 * parameter's curated bounds, and the cross-field rules -- the signed precursor tolerance pair
 * ({@code R-PARAM-04}), enzyme references, variable-modification tuples and their limits ({@code
 * R-PARAM-09}, {@code R-PARAM-10}), the workflow's required outputs ({@code R-CMT-01}), the decoy
 * prefix ({@code R-DEC-01}), AScorePro against the slots it can localise, {@code index_search_type}
 * against the database, and the parameters an import could not model ({@code R-PARAM-07}).
 *
 * <p>Where Comet releases judge a configuration differently, the rule is version-scoped and takes
 * its severity from the model's version record ({@link Rule#isVersionScoped()}); a rule that
 * depends on a release fact, such as which characters a residue token may hold, reads that fact
 * from the record too. No rule asks which version it is judging.
 *
 * <p>Every {@link ValidatorId} must have an implementation: a validator built without one is
 * refused, so a validator id the metadata names can never silently go unchecked.
 *
 * <p>What this does not check, because it needs the file system or data and belongs to the workflow
 * (Phase 08): that the database and spectra exist and are readable, that output paths are writable,
 * whether the FASTA already holds decoys ({@code R-DEC-02}) and whether the PIN holds both targets
 * and decoys ({@code R-DEC-04}). Path parameters are checked for form only.
 */
public final class CometValidator {

    private final Map<ValidatorId, FieldRule> rules;

    /**
     * A validator with an explicit set of field rules.
     *
     * @param rules one rule per validator id
     * @throws IllegalStateException naming each validator id without a rule
     */
    CometValidator(Map<ValidatorId, FieldRule> rules) {
        Set<ValidatorId> missing = EnumSet.allOf(ValidatorId.class);
        missing.removeAll(rules.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "no rule implements the validator(s) "
                            + missing.stream().map(ValidatorId::id).toList()
                            + "; a validator the metadata names would go unchecked");
        }
        this.rules = Collections.unmodifiableMap(new EnumMap<>(rules));
    }

    /**
     * The validator with this project's rules.
     *
     * @return the validator
     * @throws IllegalStateException if a validator id has no rule
     */
    public static CometValidator standard() {
        Map<ValidatorId, FieldRule> rules = new EnumMap<>(ValidatorId.class);
        rules.put(ValidatorId.CHOICE, ChoiceRule::check);
        rules.put(ValidatorId.PATH, PathRule::check);
        rules.put(ValidatorId.ORDERED_RANGE, OrderedRangeRule::check);
        rules.put(ValidatorId.SIGNED_TOLERANCE_PAIR, TolerancePairRule::check);
        rules.put(ValidatorId.ENZYME_IN_TABLE, EnzymeRules::checkReference);
        rules.put(ValidatorId.VARIABLE_MOD_TUPLE, VariableModRules::checkSlot);
        rules.put(ValidatorId.WORKFLOW_ENFORCED, WorkflowOutputs::check);
        return new CometValidator(rules);
    }

    /**
     * The validator ids this validator implements.
     *
     * @return every id with a rule
     */
    public Set<ValidatorId> implementedValidators() {
        return EnumSet.copyOf(rules.keySet());
    }

    /**
     * Validates a model.
     *
     * @param model the model
     * @return every finding
     */
    public ValidationReport validate(CometParameters model) {
        Objects.requireNonNull(model, "model");
        Findings findings = new Findings(model);
        for (ParameterEntry entry : model.entries()) {
            BoundsRule.check(entry, findings);
            for (ValidatorId validator : entry.definition().validators()) {
                rules.get(validator).check(model, entry, findings);
            }
        }
        TextTokenRule.check(model, findings);
        EnzymeRules.checkTable(model, findings);
        VariableModRules.checkLimits(model, findings);
        AScoreProRule.check(model, findings);
        IndexSearchTypeRule.check(model, findings);
        DecoyRule.check(model, findings);
        ImportedRules.check(model, findings);
        return findings.report();
    }
}
