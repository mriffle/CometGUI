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
import java.util.regex.Pattern;

/**
 * How one Comet release judges what one version-scoped validation rule checks: an entry of a
 * version record's {@code ruleSeverities}.
 *
 * <p>Decision C-2 of the Comet 2026.03.0 intake: a validation fact that differs between releases --
 * whether a release refuses, warns about or silently accepts a configuration -- is data in that
 * release's {@link CometVersionRecord}, never an {@code if (version ...)} in a rule. The rule is
 * named by its stable identifier ({@code org.cometgui.params.comet.validation.Rule#id()}); the
 * schema does not know the rules, so the validator, which does, refuses an entry for a rule it does
 * not have or one whose severity is fixed, and refuses a version-scoped rule that a release's
 * record does not state.
 *
 * @param rule the rule's stable identifier, for example {@code
 *     variable_mod_tuple.distance_undocumented}
 * @param level what a finding of the rule is for this release
 * @param source the {@code https://} reference to the release's behaviour the level encodes
 */
public record RuleSeverity(String rule, Level level, String source) {

    /** A rule identifier: lower-case words joined by underscores, a dot, and more such words. */
    public static final Pattern RULE_ID = Pattern.compile("[a-z][a-z_]*\\.[a-z][a-z_]*");

    /** What a finding of a version-scoped rule is for one release. */
    public enum Level {

        /** The finding is an error: it blocks a run. */
        ERROR,

        /** The finding is a warning. */
        WARNING,

        /** The release has nothing to report here, so the rule finds nothing for it. */
        OFF
    }

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the rule is not a rule identifier or the source is not an
     *     {@code https://} reference
     */
    public RuleSeverity {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        if (!RULE_ID.matcher(rule).matches()) {
            throw new IllegalArgumentException(
                    "\"" + rule + "\" is not a rule identifier such as family.what_it_checks");
        }
        if (!source.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "the source of " + rule + " is not an https:// reference: " + source);
        }
    }
}
