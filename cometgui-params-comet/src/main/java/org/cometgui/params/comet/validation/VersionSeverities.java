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
import java.util.Map;
import java.util.Optional;
import org.cometgui.params.comet.schema.CometVersionRecord;
import org.cometgui.params.comet.schema.RuleSeverity;

/**
 * The severities of the version-scoped rules for one Comet release, read from its version record
 * (decision C-2 of the Comet 2026.03.0 intake): no rule asks which version it is judging.
 *
 * <p>The record is held to the rule catalogue here, because the schema does not know the rules:
 * every version-scoped {@link Rule} must be stated, and a stated identifier must be a
 * version-scoped rule. A record that breaks either is refused with an {@link
 * IllegalStateException}, so a release can never be judged by a default nobody chose.
 */
final class VersionSeverities {

    private final Map<Rule, Optional<Severity>> scoped;

    private VersionSeverities(Map<Rule, Optional<Severity>> scoped) {
        this.scoped = Collections.unmodifiableMap(scoped);
    }

    /**
     * Reads a release's severities.
     *
     * @param record the release's version record
     * @return its severities
     * @throws IllegalStateException if the record names a rule that does not exist or is not
     *     version-scoped, or does not state a version-scoped rule
     */
    static VersionSeverities of(CometVersionRecord record) {
        String release = "Comet " + record.version().text() + "'s version record";
        for (String id : record.ruleSeverities().keySet()) {
            Optional<Rule> named = Rule.byId(id);
            if (named.isEmpty()) {
                throw new IllegalStateException(
                        release + " states a severity for \"" + id + "\", which is not a rule");
            }
            Rule rule = named.get();
            if (!rule.isVersionScoped()) {
                throw new IllegalStateException(
                        release
                                + " states a severity for "
                                + id
                                + ", whose severity is fixed ("
                                + rule.fixedSeverity().orElseThrow()
                                + ") in every release");
            }
        }
        Map<Rule, Optional<Severity>> scoped = new EnumMap<>(Rule.class);
        for (Rule rule : Rule.values()) {
            if (rule.isVersionScoped()) {
                RuleSeverity stated =
                        record.ruleSeverity(rule.id())
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        release
                                                                + " states no severity for the"
                                                                + " version-scoped rule "
                                                                + rule.id()
                                                                + "; every release must state"
                                                                + " one"));
                scoped.put(rule, severity(stated.level()));
            }
        }
        return new VersionSeverities(scoped);
    }

    private static Optional<Severity> severity(RuleSeverity.Level level) {
        return switch (level) {
            case ERROR -> Optional.of(Severity.ERROR);
            case WARNING -> Optional.of(Severity.WARNING);
            case OFF -> Optional.empty();
        };
    }

    /**
     * The severity a finding of a rule has for this release.
     *
     * @param rule the rule
     * @return the rule's fixed severity, or the release's for a version-scoped rule; empty when the
     *     release has nothing to report for that rule
     */
    Optional<Severity> of(Rule rule) {
        return rule.fixedSeverity().or(() -> scoped.get(rule));
    }
}
