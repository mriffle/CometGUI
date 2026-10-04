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

package org.cometgui.params.comet.schema;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;

/**
 * What a release does with one value written for an earlier (or other) release, when the value's
 * text alone would not carry its meaning: an entry of a version record's {@code valueMigrations}.
 *
 * <p>Decision C-2 of the Comet 2026.03.0 intake: a fact that differs between releases is data in a
 * version record, never an {@code if (version ...)}. Schema migration carries a value by its typed
 * meaning; where two releases give the <em>same</em> text different treatment, or different text
 * the same meaning, the <strong>target</strong> release's record says so, keyed by the release the
 * value was written {@link #from()}. An entry matches a value in one of two ways:
 *
 * <ul>
 *   <li>by {@link #parameter()} and {@link #value()}: one choice of an enumerated parameter, as the
 *       source release writes it -- Comet 2026.02.2's {@code index_search_type = 1};
 *   <li>by {@link #rule()}: a validation rule's stable identifier ({@code
 *       org.cometgui.params.comet.validation.Rule#id()}) whose finding the source release's model
 *       has on the parameter -- a variable-modification slot with a terminal distance below -2,
 *       which the validator, agreed with both real binaries, already recognises. The schema does
 *       not know the rules; migration refuses an identifier that names none.
 * </ul>
 *
 * <p>And it does one of three things ({@link Action}): writes the value as another with the same
 * meaning, reported with its reason; flags it for the user, holding the target's default; or
 * carries it unchanged, reported with a notice. Nothing an entry does is silent.
 *
 * @param from the release the value was written for
 * @param parameter the enumerated parameter, for an entry matched by value; empty for one matched
 *     by rule
 * @param value the source release's choice it matches; present exactly when {@code parameter} is
 * @param rule the validation rule whose finding it matches; present exactly when {@code parameter}
 *     is not
 * @param action what migration does with a matching value
 * @param field for an entry matched by rule that converts: the variable-modification field it
 *     rewrites; empty otherwise
 * @param becomes for {@link Action#CONVERT}: the target's text -- the whole value for an entry
 *     matched by value, the field's text for one matched by rule; empty otherwise
 * @param reason why, in words the user reads in the migration report
 * @param source the {@code https://} reference to the release behaviour the entry encodes
 */
public record ValueMigration(
        ToolVersion from,
        Optional<String> parameter,
        Optional<String> value,
        Optional<String> rule,
        Action action,
        Optional<VariableModField> field,
        Optional<String> becomes,
        String reason,
        String source) {

    /** What migration does with a value an entry matches. */
    public enum Action {

        /** Write it as {@link #becomes()}, which means the same in the target release. */
        CONVERT,

        /** The target has no equivalent: flag it, and hold the target's default instead. */
        NEEDS_ATTENTION,

        /** Carry it unchanged -- it means the same -- and report how the target treats it. */
        NOTICE
    }

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the entry is matched both or neither way, a value is
     *     given without its parameter, the target text is present for any action but {@link
     *     Action#CONVERT} or absent for it, a field is given for anything but a converting entry
     *     matched by rule, the rule is not a rule identifier, the reason is blank, or the source is
     *     not an {@code https://} reference
     */
    public ValueMigration {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(becomes, "becomes");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(source, "source");
        if (parameter.isPresent() == rule.isPresent()) {
            throw new IllegalArgumentException(
                    "a value migration is matched by a value or by a rule, exactly one");
        }
        if (parameter.isPresent() != value.isPresent()) {
            throw new IllegalArgumentException(
                    "a value migration matched by value names the parameter and the value");
        }
        if (becomes.isPresent() != (action == Action.CONVERT)) {
            throw new IllegalArgumentException(
                    "a value migration has a target text exactly when it converts; this one "
                            + action);
        }
        if (field.isPresent() != (rule.isPresent() && action == Action.CONVERT)) {
            throw new IllegalArgumentException(
                    "a value migration names a field exactly when it is matched by rule and"
                            + " converts");
        }
        rule.ifPresent(
                id -> {
                    if (!RuleSeverity.RULE_ID.matcher(id).matches()) {
                        throw new IllegalArgumentException(
                                "\"" + id + "\" is not a rule identifier such as family.what");
                    }
                });
        if (reason.isBlank()) {
            throw new IllegalArgumentException("a value migration needs a reason");
        }
        if (!source.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "the source of a value migration is not an https:// reference: " + source);
        }
    }

    /**
     * Whether this entry matches one value of one parameter, written for its source release.
     *
     * @param name the parameter
     * @param text the value, as the source release writes it
     * @return {@code true} for an entry matched by value that names both
     */
    public boolean matchesValue(String name, String text) {
        return parameter.filter(name::equals).isPresent() && value.filter(text::equals).isPresent();
    }

    /**
     * What the entry matches, in words.
     *
     * @return {@code "<parameter> = <value>"} or {@code "rule <id>"}
     */
    public String matches() {
        return parameter
                .map(name -> name + " = " + value.orElseThrow())
                .orElseGet(() -> "rule " + rule.orElseThrow());
    }
}
