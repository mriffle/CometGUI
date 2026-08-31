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

package org.cometgui.tools.process;

import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;

/**
 * Applies the product's one secret-redaction rule set to the three things the process service shows
 * or records: a console line, the rendered command, and the captured environment.
 *
 * <h2>The rules are not here, and that is deliberate</h2>
 *
 * <p>{@code R-SEC-03} is enforced by {@link SecretRedactor} in {@code org.cometgui.domain.secrets},
 * which owns the keyword list, the registry of literal values, the PEM private-key rule and the
 * rest. This class holds no rules of its own. Phase 03 and phase 04 each wrote a rule set before
 * they could see each other's, the two lists diverged by a keyword within hours -- a value named
 * {@code ...SIGNATURE...} would have been redacted in the console log and not in the provenance
 * record -- and that is exactly the drift {@code R-SEC-03} exists to prevent. The shared rules were
 * moved into {@code cometgui-domain}, the only module both siblings depend on. <strong>A rule added
 * here rather than there re-creates the defect.</strong>
 *
 * <p>What is left is genuinely process-specific, and it is two things.
 *
 * <h2>1. Arguments are redacted before they are escaped, never after</h2>
 *
 * <p>{@link #redactedDisplayCommand(ToolCommand)} redacts each argument and then hands the redacted
 * arguments to {@link ToolCommand#displayString()}, rather than redacting the string {@code
 * displayString} produces. The order matters and getting it wrong is a silent leak: {@code
 * displayString} escapes backslashes, quotes and control characters, so a token containing a {@code
 * "} arrives in the rendered text as {@code \"} and a literal search for the registered value no
 * longer matches it. The secret would then be printed, escaped, in full. Redacting first means no
 * argument can contribute the secret to the rendering at all, and escaping can only expand what is
 * left.
 *
 * <p>The safety property of the rendering itself is inherited, not re-implemented: {@code
 * displayString} writes the argv as a quoted, escaped array -- {@code ["/opt/comet", "-P",
 * "comet.params"]} -- which is not a shell command and cannot become one, so nothing this class
 * returns can be pasted into a terminal and run. This class does not duplicate that escaping and
 * must never grow its own copy of it.
 *
 * <h2>2. A run with no registered secret pays nothing per line</h2>
 *
 * <p>Comet, Percolator and PDV take no credential; phase 12's Limelight upload is the only tool in
 * the product that will ever register one. {@link #redact(String)} therefore returns <strong>the
 * argument itself, by reference</strong> when the registry is empty, and does no scanning at all. A
 * stage emitting 500 MB of stdout must not pay a per-line cost for a feature no tool in the
 * workflow uses; a scan that is free when unused can honestly be left switched on for every run,
 * and one that is not could not be. There is a test asserting the identity of the returned
 * reference, because an assertion on equality would not notice the guarantee being lost.
 *
 * <p><strong>The price of that, stated rather than hidden:</strong> with no registered value, a
 * console line does not get the pattern rules either -- a bare bearer token printed by a tool that
 * was never given a credential goes to the log unredacted. That is the narrow case the phase
 * accepted. It does not extend to the two renderings {@code R-SEC-03} actually names: {@link
 * #redactedDisplayCommand(ToolCommand)} and {@link #redactedEnvironment(Map)} apply the full rule
 * set <em>unconditionally</em>, because each is produced once per stage rather than once per line,
 * so nothing is saved by skipping them and a credential on a command line or in an environment is
 * the case that actually happens.
 *
 * <p>Immutable and therefore thread safe: one redactor is shared by both pump threads of a running
 * stage and by whatever writes the provenance record afterwards.
 */
public final class ProcessRedactor {

    private final SecretRedactor rules;

    /**
     * Whether the rule set has any literal value registered, decided once at construction.
     *
     * <p>Read on every console line, so it is a field rather than a call into the registry.
     */
    private final boolean anySecretRegistered;

    /**
     * A redactor applying the given rule set.
     *
     * @param rules the product's shared secret rules, from {@code org.cometgui.domain.secrets}
     * @throws NullPointerException if {@code rules} is {@code null}
     */
    public ProcessRedactor(SecretRedactor rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.anySecretRegistered = rules.registry().size() > 0;
    }

    /**
     * A redactor over the shared rules plus the literal values in the given registry.
     *
     * <p>The ordinary construction. {@code ProcessRedactor.with(SecretRegistry.empty())} is what
     * every stage that runs Comet, Percolator or PDV gets, and it is the case that costs nothing.
     *
     * @param registry the literal credential values this run wants scrubbed; may be empty
     * @return a redactor over those values and the shared rules
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public static ProcessRedactor with(SecretRegistry registry) {
        return new ProcessRedactor(SecretRedactor.with(registry));
    }

    /**
     * Cleans one console line.
     *
     * <p>With no registered value this returns the argument unchanged and scans nothing; see the
     * class documentation for why, and for what that costs. Otherwise the full shared rule set is
     * applied through {@link SecretRedactor#redactText(String)}.
     *
     * @param line the line to clean
     * @return the cleaned line; the identical reference when no value is registered
     * @throws NullPointerException if {@code line} is {@code null}
     */
    public String redact(String line) {
        Objects.requireNonNull(line, "line");
        if (!anySecretRegistered) {
            return line;
        }
        return rules.redactText(line);
    }

    /**
     * The command rendered for a log line, a console pane or a provenance report, with credentials
     * removed.
     *
     * <p>The result is <strong>not a shell command and must never be treated as one</strong>; that
     * property belongs to {@link ToolCommand#displayString()} and is inherited here. Each argument
     * is redacted before the array is rendered, for the reason given in the class documentation.
     * The rule set is applied whatever the registry holds.
     *
     * <p>The environment plays no part: {@code displayString} renders the argument array only, and
     * the environment's redacted form is {@link #redactedEnvironment(Map)}'s business. The empty
     * map handed to the intermediate command is there because {@link ToolCommand} requires one, not
     * because anything is being hidden.
     *
     * @param command the command to render
     * @return the escaped argument array with credentials replaced by the shared marker
     * @throws NullPointerException if {@code command} is {@code null}
     * @throws IllegalArgumentException if redaction leaves an argument blank, which {@link
     *     ToolCommand} refuses -- a loud failure rather than a silently mangled command
     */
    public String redactedDisplayCommand(ToolCommand command) {
        Objects.requireNonNull(command, "command");
        return new ToolCommand(
                        rules.redactArgv(command.argv()), command.workingDirectory(), Map.of())
                .displayString();
    }

    /**
     * The captured environment as it may be written to a log or a provenance record.
     *
     * <p>A deliberate delegation to {@link SecretRedactor#redactEnvironment(Map)} rather than a
     * second implementation: it exists so that the process service has one redaction seam to hold
     * rather than two objects, and so that {@code R-PROC-04}'s environment capture and this phase's
     * command rendering cannot drift apart. Variable <em>names</em> are never redacted -- a
     * provenance record has to be able to say which variables were set -- and every value is
     * cleaned, whether or not its name looks secret.
     *
     * @param environment the environment to clean
     * @return an immutable map with the same names, in the same order, and cleaned values
     * @throws NullPointerException if the map is {@code null}, or if any name or value in it is
     *     {@code null}; the message names the offending variable and never its value
     */
    public Map<String, String> redactedEnvironment(Map<String, String> environment) {
        return rules.redactEnvironment(environment);
    }
}
