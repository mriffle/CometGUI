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

package org.cometgui.params.comet.migration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.validation.CometValidator;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.Severity;
import org.cometgui.params.comet.validation.ValidationReport;

/**
 * A migration under the scientist's review ({@code R-PARAM-13}: "an entry needing the scientist's
 * attention blocks a run until resolved"): the {@link MigrationResult}, and which of its {@link
 * MigrationEntry.Outcome#NEEDS_ATTENTION} entries the scientist has acknowledged.
 *
 * <p>A {@code NEEDS_ATTENTION} entry is a value the target release could not hold with its meaning;
 * the migrated set holds the target's default in its place -- for {@code variable_mod01} that is an
 * <em>active</em> methionine oxidation the scientist never chose. So until the entry is resolved,
 * {@link #validate(CometParameters)} reports it as an error ({@link
 * Rule#MIGRATION_NEEDS_ATTENTION}) at its parameter and category, in the same {@link
 * ValidationReport} as every other finding, and a run is blocked exactly as by any other error.
 *
 * <p><strong>Resolved</strong> means either of two things, and nothing else:
 *
 * <ol>
 *   <li><em>acknowledged</em>: the scientist accepted the value the set holds, by {@link
 *       #resolve(String)} -- recorded here, whatever the value is later; or
 *   <li><em>set by the scientist</em>: in the model being validated, the parameter's origin is
 *       {@link ValueOrigin#USER}. Migration gives every such parameter origin {@link
 *       ValueOrigin#COMET_DEFAULT}, so {@code USER} there means a value the scientist entered after
 *       the migration. This is read from the model on every validation, not recorded: a later reset
 *       to the default ({@link CometParameters#resetToDefault(String)}), a preset ({@link
 *       ValueOrigin#PRESET}) or any origin but {@code USER} leaves an unacknowledged entry
 *       unresolved again. A caller must therefore give origin {@code USER} only to a value the
 *       scientist set on that parameter.
 * </ol>
 *
 * <p>Immutable: {@link #resolve(String)} returns a new review.
 *
 * @param result the migration under review
 * @param acknowledged the names of the {@code NEEDS_ATTENTION} entries the scientist has
 *     acknowledged
 */
public record MigrationReview(MigrationResult result, Set<String> acknowledged) {

    /**
     * Validates the components and takes an immutable copy of the acknowledged names.
     *
     * @throws IllegalArgumentException naming an acknowledged name that is not a {@code
     *     NEEDS_ATTENTION} entry of the report
     */
    public MigrationReview {
        Objects.requireNonNull(result, "result");
        acknowledged = Set.copyOf(acknowledged);
        for (String name : acknowledged) {
            if (!needsAttention(result.report(), name)) {
                throw new IllegalArgumentException(notAnEntry(result.report(), name));
            }
        }
    }

    /**
     * A review of a migration in which nothing has been resolved yet.
     *
     * @param result the migration
     * @return the review
     */
    public static MigrationReview of(MigrationResult result) {
        return new MigrationReview(result, Set.of());
    }

    /**
     * The acknowledged names, immutable.
     *
     * @return the names the scientist has acknowledged
     */
    @Override
    public Set<String> acknowledged() {
        return Set.copyOf(acknowledged);
    }

    /**
     * The migration's report.
     *
     * @return the report
     */
    public MigrationReport report() {
        return result.report();
    }

    /**
     * The scientist accepts the value the set holds for one entry that needs attention.
     *
     * @param name the entry's parameter
     * @return a new review with the entry acknowledged; this one is unchanged
     * @throws IllegalArgumentException naming the parameter, if it is not an entry of the report
     *     that needs attention, or if it has already been acknowledged
     */
    public MigrationReview resolve(String name) {
        Objects.requireNonNull(name, "name");
        if (!needsAttention(result.report(), name)) {
            throw new IllegalArgumentException(notAnEntry(result.report(), name));
        }
        if (acknowledged.contains(name)) {
            throw new IllegalArgumentException(
                    name + " has already been resolved in this migration's review");
        }
        Set<String> more = new LinkedHashSet<>(acknowledged);
        more.add(name);
        return new MigrationReview(result, more);
    }

    /**
     * The entries that still need the scientist: neither acknowledged nor set by the scientist in
     * {@code model}.
     *
     * @param model the set being validated, of the migration's target version
     * @return the unresolved entries, in report order
     * @throws IllegalArgumentException if the model is not of the migration's target version
     */
    public List<MigrationEntry> unresolved(CometParameters model) {
        requireTarget(model);
        return result.report().needingAttention().stream()
                .filter(entry -> !acknowledged.contains(entry.parameter()))
                .filter(entry -> !setByScientist(model, entry.parameter()))
                .toList();
    }

    /**
     * Validates a migrated set: every finding of {@link CometValidator#standard()}, then one {@link
     * Rule#MIGRATION_NEEDS_ATTENTION} error per {@linkplain #unresolved(CometParameters)
     * unresolved} entry, in report order. Each is attached to the entry's parameter and that
     * parameter's category, or to the name alone with no category if the model does not model it.
     *
     * <p>This is the one place a migration's review and validation meet: a caller shows this report
     * and does not combine reports itself.
     *
     * @param model the set to validate, of the migration's target version; usually the migrated
     *     model as the scientist has since edited it
     * @return one report
     * @throws IllegalArgumentException if the model is not of the migration's target version
     */
    public ValidationReport validate(CometParameters model) {
        List<MigrationEntry> open = unresolved(model);
        List<Finding> findings =
                new ArrayList<>(CometValidator.standard().validate(model).findings());
        for (MigrationEntry entry : open) {
            String name = entry.parameter();
            Optional<ParameterEntry> modelled = model.entry(name);
            Optional<ParameterCategory> category =
                    modelled.map(held -> held.definition().category());
            String now = modelled.isPresent() ? model.text(name) : entry.targetText();
            findings.add(
                    new Finding(
                            Rule.MIGRATION_NEEDS_ATTENTION,
                            Severity.ERROR,
                            List.of(name),
                            category,
                            message(entry, now)));
        }
        return new ValidationReport(findings);
    }

    private String message(MigrationEntry entry, String now) {
        MigrationReport report = result.report();
        return entry.parameter()
                + " needs your decision: migrating from Comet "
                + report.from().text()
                + " to Comet "
                + report.to().text()
                + " could not keep its value "
                + entry.sourceText().orElse("")
                + " and put Comet "
                + report.to().text()
                + "'s default "
                + entry.targetText()
                + " in its place; the set now holds "
                + now
                + ". Set a value, or accept this one, before running. Why: "
                + entry.explanation();
    }

    private void requireTarget(CometParameters model) {
        Objects.requireNonNull(model, "model");
        if (!model.version().equals(result.report().to())) {
            throw new IllegalArgumentException(
                    "this review is of a migration to Comet "
                            + result.report().to().text()
                            + ", not of a Comet "
                            + model.version().text()
                            + " set");
        }
    }

    private static boolean setByScientist(CometParameters model, String name) {
        return model.entry(name).filter(held -> held.origin() == ValueOrigin.USER).isPresent();
    }

    private static boolean needsAttention(MigrationReport report, String name) {
        return report.needingAttention().stream().anyMatch(e -> e.parameter().equals(name));
    }

    private static String notAnEntry(MigrationReport report, String name) {
        return name
                + " is not an entry of the migration from Comet "
                + report.from().text()
                + " to Comet "
                + report.to().text()
                + " that needs attention, so there is nothing to resolve";
    }
}
