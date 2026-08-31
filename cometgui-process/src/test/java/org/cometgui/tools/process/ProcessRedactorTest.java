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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves what this class adds to the shared rule set, and only that.
 *
 * <p>The keyword list, the registry's minimum length and ordering, the PEM rule and the rest belong
 * to {@link SecretRedactor} in {@code org.cometgui.domain.secrets} and are tested there. Testing
 * them again here would be the same duplication that put two rule sets in the product in the first
 * place. What is proved here is the process-specific half: that an argument is redacted
 * <em>before</em> {@link ToolCommand#displayString()} escapes it, and that a run with no registered
 * credential pays nothing per line.
 *
 * <p><strong>Every expected string below is hand-typed, the marker included.</strong> The marker is
 * written as the literal {@code [REDACTED]} rather than as {@code SecretRedactor.REDACTION_MARKER}
 * on purpose: an assertion whose expected value is read out of the thing it is checking cannot
 * fail, and a later change to the shared constant must break these tests visibly rather than follow
 * them silently.
 */
class ProcessRedactorTest {

    /** A registered value long enough to be legal, distinctive enough to be traced. */
    private static final String TOKEN = "s3cr3t-t0k3n";

    /**
     * A working directory that is absolute, because {@link ToolCommand} requires one, and that is
     * never created, because nothing in this class touches the filesystem: redaction is pure logic
     * and {@link ToolCommand#displayString()} does not render the directory at all. Built by
     * resolving a relative name rather than written as an absolute literal, which is a hard-coded
     * path SpotBugs is right to object to in a portable test.
     */
    private static final Path WORKING_DIRECTORY = Path.of("run").toAbsolutePath();

    /**
     * A null the static analyser cannot see through.
     *
     * <p>Proving that a method rejects null means passing it null, and SpotBugs reports exactly
     * that as {@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS}, which is not in the project's
     * exclusion filter. Routing the null through a collection keeps the test without weakening a
     * shared gate.
     *
     * @param <T> whatever the call site needs
     * @return null
     */
    private static <T> T deliberateNull() {
        List<T> holder = new ArrayList<>(1);
        holder.add(null);
        return holder.get(0);
    }

    private static ProcessRedactor redactorFor(String... secrets) {
        return ProcessRedactor.with(SecretRegistry.of(secrets));
    }

    private static ToolCommand command(List<String> argv, Map<String, String> environment) {
        return new ToolCommand(argv, WORKING_DIRECTORY, environment);
    }

    @Test
    @DisplayName("one marker across the product, pinned as a hand-typed literal")
    void theMarkerIsTheOneSharedConstant() {
        assertEquals(
                "[REDACTED]",
                SecretRedactor.REDACTION_MARKER,
                "Two markers would print one string in the console log and another in the"
                        + " provenance record for the same secret in the same run. If this fails,"
                        + " the shared constant moved and every expectation in this module must"
                        + " move with it deliberately rather than silently.");
    }

    @Nested
    @DisplayName("with no registered value, which is every run of Comet, Percolator and PDV")
    class NoRegisteredSecret {

        private final ProcessRedactor redactor = ProcessRedactor.with(SecretRegistry.empty());

        @Test
        @DisplayName("a console line comes back as the identical reference, unscanned")
        void redactIsFree() {
            String line = "Comet version 2024.01 rev. 0, running 8 threads";

            assertSame(
                    line,
                    redactor.redact(line),
                    "a 500 MB flood must not pay a per-line cost for a feature no tool in the"
                            + " workflow uses; equality would not notice this being lost");
        }

        @Test
        @DisplayName("nothing is scanned at all, proved on a line the rules WOULD have rewritten")
        void nothingIsScannedAtAll() {
            String header = "Authorization: Bearer abc123def456ghi789";

            assertSame(
                    header,
                    redactor.redact(header),
                    "PIT caught the first version of this test: asserting reference identity on a"
                            + " line nothing matches passes whether the short-circuit is there or"
                            + " not, because the shared rules also return their argument when no"
                            + " rule fires. The line has to be one the rules would change.");
            assertEquals(
                    "Authorization: Bearer [REDACTED]",
                    redactorFor(TOKEN).redact(header),
                    "the same line, through a redactor that does scan, to show what the"
                            + " short-circuit is skipping -- an unregistered bearer token reaches"
                            + " the console log of a stage that was never given a credential, and"
                            + " that is the documented price of the per-line path being free");
        }

        @Test
        @DisplayName("the display command still gets the full rule set: it is rendered once")
        void theDisplayCommandIsStillRedacted() {
            ToolCommand comet =
                    command(List.of("/opt/comet", "-P", "comet.params"), Map.of("PATH", "/bin"));
            ToolCommand upload = command(List.of("/opt/tool", "--token", TOKEN), Map.of());

            assertEquals(
                    "[\"/opt/comet\", \"-P\", \"comet.params\"]",
                    redactor.redactedDisplayCommand(comet));
            assertEquals(
                    "[\"/opt/tool\", \"--token\", \"[REDACTED]\"]",
                    redactor.redactedDisplayCommand(upload),
                    "--token is a secret-bearing flag in the shared rules, so the argument after"
                            + " it goes even with nothing registered");
        }

        @Test
        @DisplayName("the environment still gets the full rule set, for the same reason")
        void theEnvironmentIsStillRedacted() {
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("PATH", "/opt/tools/bin");
            environment.put("LIMELIGHT_API_TOKEN", "whatever-this-is");

            assertEquals(
                    Map.of("PATH", "/opt/tools/bin", "LIMELIGHT_API_TOKEN", "[REDACTED]"),
                    redactor.redactedEnvironment(environment));
        }
    }

    @Nested
    @DisplayName("with a registered value")
    class RegisteredSecret {

        @Test
        @DisplayName("every occurrence in a console line is replaced, not merely the first")
        void everyOccurrenceInALine() {
            assertEquals(
                    "uploading with [REDACTED] then retrying with [REDACTED].",
                    redactorFor(TOKEN)
                            .redact(
                                    "uploading with "
                                            + TOKEN
                                            + " then retrying with "
                                            + TOKEN
                                            + "."));
        }

        @Test
        @DisplayName("a line with nothing in it comes back with its content intact")
        void nothingToDo() {
            assertEquals(
                    "Percolator finished: 12345 PSMs at q < 0.01",
                    redactorFor(TOKEN).redact("Percolator finished: 12345 PSMs at q < 0.01"));
        }

        @Test
        @DisplayName("a null line is refused, naming the argument")
        void nullLineIsRefused() {
            String noLine = deliberateNull();

            NullPointerException refused =
                    assertThrows(
                            NullPointerException.class, () -> redactorFor(TOKEN).redact(noLine));

            assertEquals("line", refused.getMessage());
        }
    }

    @Nested
    @DisplayName("the display command")
    class DisplayCommand {

        @Test
        @DisplayName(
                "the secret is removed before the escaping, not after -- the only order that works")
        void redactedBeforeEscaping() {
            String awkward = "ab\"cd\\efgh";
            ToolCommand upload = command(List.of("/opt/tool", "--api-token", awkward), Map.of());

            assertEquals(
                    "[\"/opt/tool\", \"--api-token\", \"ab\\\"cd\\\\efgh\"]",
                    upload.displayString(),
                    "the raw rendering escapes the quote and the backslash");
            assertFalse(
                    upload.displayString().contains(awkward),
                    "so a literal search over the RENDERED text cannot find the secret, and a"
                            + " redactor that ran after the escaping would print it in full");

            String rendered = redactorFor(awkward).redactedDisplayCommand(upload);

            assertEquals("[\"/opt/tool\", \"--api-token\", \"[REDACTED]\"]", rendered);
            SecretScan.assertNothingOfTheSecretSurvives(
                    awkward, rendered, "the redacted display command");
        }

        @Test
        @DisplayName(
                "--api-token is not a secret-bearing flag, so only the registry saves that"
                        + " argument, which is what makes the test above about the escaping")
        void theFlagRuleIsNotWhatCatchesIt() {
            String awkward = "ab\"cd\\efgh";
            ToolCommand upload = command(List.of("/opt/tool", "--api-token", awkward), Map.of());

            assertEquals(
                    "[\"/opt/tool\", \"--api-token\", \"ab\\\"cd\\\\efgh\"]",
                    ProcessRedactor.with(SecretRegistry.empty()).redactedDisplayCommand(upload));
        }

        @Test
        @DisplayName("a secret embedded in a longer argument leaves the rest of the argument")
        void anEmbeddedSecret() {
            ToolCommand upload =
                    command(
                            List.of("/opt/tool", "--url", "https://limelight/api?k=" + TOKEN),
                            Map.of());

            assertEquals(
                    "[\"/opt/tool\", \"--url\", \"https://limelight/api?k=[REDACTED]\"]",
                    redactorFor(TOKEN).redactedDisplayCommand(upload));
        }

        @Test
        @DisplayName("the environment is never part of the rendering")
        void theEnvironmentIsNotRendered() {
            ToolCommand upload =
                    command(List.of("/opt/tool"), Map.of("LIMELIGHT_API_TOKEN", TOKEN));

            String rendered = redactorFor(TOKEN).redactedDisplayCommand(upload);

            assertEquals("[\"/opt/tool\"]", rendered);
            assertFalse(rendered.contains("LIMELIGHT_API_TOKEN"));
            SecretScan.assertNothingOfTheSecretSurvives(
                    TOKEN, rendered, "the redacted display command");
        }

        @Test
        @DisplayName("a null command is refused, naming the argument")
        void nullIsRefused() {
            ToolCommand noCommand = deliberateNull();

            NullPointerException refused =
                    assertThrows(
                            NullPointerException.class,
                            () -> redactorFor(TOKEN).redactedDisplayCommand(noCommand));

            assertEquals("command", refused.getMessage());
        }
    }

    @Nested
    @DisplayName("the captured environment")
    class Environment {

        @Test
        @DisplayName("a secret-named variable loses its value whatever the value looks like")
        void secretNamedVariables() {
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("LIMELIGHT_API_TOKEN", "an-utterly-ordinary-looking-string");
            environment.put("HOME", "/home/scientist");

            assertEquals(
                    Map.of("LIMELIGHT_API_TOKEN", "[REDACTED]", "HOME", "/home/scientist"),
                    redactorFor(TOKEN).redactedEnvironment(environment));
        }

        @Test
        @DisplayName(
                "an innocuously named variable is scanned too: a token lives in PATH as"
                        + " easily as in TOKEN")
        void ordinaryVariablesAreScanned() {
            Map<String, String> environment = Map.of("PATH", "/opt/" + TOKEN + "/bin:/usr/bin");

            assertEquals(
                    Map.of("PATH", "/opt/[REDACTED]/bin:/usr/bin"),
                    redactorFor(TOKEN).redactedEnvironment(environment));
        }

        @Test
        @DisplayName("keys are never redacted: a provenance record has to say what was set")
        void keysSurvive() {
            Map<String, String> redacted =
                    redactorFor(TOKEN).redactedEnvironment(Map.of("LIMELIGHT_API_TOKEN", TOKEN));

            assertEquals(List.of("LIMELIGHT_API_TOKEN"), List.copyOf(redacted.keySet()));
            assertEquals("[REDACTED]", redacted.get("LIMELIGHT_API_TOKEN"));
        }

        @Test
        @DisplayName("the result is immutable")
        void theResultIsImmutable() {
            Map<String, String> redacted =
                    redactorFor(TOKEN).redactedEnvironment(Map.of("HOME", "/home/scientist"));

            assertThrows(UnsupportedOperationException.class, redacted::clear);
        }

        @Test
        @DisplayName("an empty environment gives an empty map")
        void anEmptyEnvironment() {
            assertEquals(Map.of(), redactorFor(TOKEN).redactedEnvironment(Map.of()));
        }

        @Test
        @DisplayName("a null map is refused, naming the argument")
        void nullMapIsRefused() {
            Map<String, String> noEnvironment = deliberateNull();

            NullPointerException refused =
                    assertThrows(
                            NullPointerException.class,
                            () -> redactorFor(TOKEN).redactedEnvironment(noEnvironment));

            assertEquals("environment", refused.getMessage());
        }

        @Test
        @DisplayName("a null value is refused, naming the variable but never its value")
        void nullValueIsRefused() {
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("HOME", deliberateNull());

            NullPointerException refused =
                    assertThrows(
                            NullPointerException.class,
                            () -> redactorFor(TOKEN).redactedEnvironment(environment));

            assertEquals("the value of the environment variable HOME", refused.getMessage());
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a rule set is required, and the message names it")
        void aNullRuleSetIsRefused() {
            SecretRedactor noRules = deliberateNull();

            NullPointerException refused =
                    assertThrows(NullPointerException.class, () -> new ProcessRedactor(noRules));

            assertEquals("rules", refused.getMessage());
        }

        @Test
        @DisplayName("a null registry is refused by the shared rule set")
        void aNullRegistryIsRefused() {
            SecretRegistry noRegistry = deliberateNull();

            assertThrows(NullPointerException.class, () -> ProcessRedactor.with(noRegistry));
        }

        @Test
        @DisplayName("a redactor built from a rule set that already holds values uses them")
        void builtFromARuleSetDirectly() {
            ProcessRedactor redactor =
                    new ProcessRedactor(SecretRedactor.patternsOnly().withSecret(TOKEN));

            assertEquals("saw [REDACTED] once", redactor.redact("saw " + TOKEN + " once"));
        }
    }
}
