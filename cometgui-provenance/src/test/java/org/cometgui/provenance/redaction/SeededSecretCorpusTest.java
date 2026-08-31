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

package org.cometgui.provenance.redaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The phase-04 seeded secret corpus, in every carrier, through {@link SecretRedactor}.
 *
 * <p>This is the unit-level half of exit gate item 6: "a seeded corpus of secrets (tokens,
 * passwords, bearer headers, credential-bearing URLs) appears nowhere in JSON, RST or logs". Later
 * units grep the generated artefacts for these exact ten strings; this file proves the redactor
 * they will be calling removes every one of them from every shape they can arrive in, before any of
 * those artefacts exist.
 *
 * <p><strong>Two assertions per carrier, and both are needed.</strong> First the full expected
 * output, typed out by hand -- which fails when a secret survives and equally when something that
 * was not a secret is destroyed. Then the absence sweep over the concatenation of every carrier,
 * which is the assertion that would still catch a secret leaking through a carrier nobody thought
 * to write an expected string for. Absence alone would be satisfied by a redactor that returned the
 * empty string, which is why it is second and not first.
 *
 * <p><strong>The corpus values are hand-transcribed</strong> from the work unit's brief, character
 * for character, and no expected string below is built by calling the redactor or by concatenating
 * {@link SecretRedactor#REDACTION_MARKER}.
 */
class SeededSecretCorpusTest {

    // -----------------------------------------------------------------------------------------
    // The ten seeded secrets, hand-transcribed.
    // -----------------------------------------------------------------------------------------

    private static final String AWS_SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String GITHUB_TOKEN = "ghp_S3cr3tT0k3nExampleValue0123456789ab";
    private static final String JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                    + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String PASSWORD = "hunter2-not-a-real-password";
    private static final String URL_PASSWORD = "Tr0ub4dor-26-3";
    private static final String LIMELIGHT_KEY = "ll_live_9f8e7d6c5b4a39281706";
    private static final String PASSPHRASE = "correct-horse-battery-staple";
    private static final String SWORDFISH = "swordfish-42";
    private static final String LIVE_TOKEN = "tok_live_abcdef0123456789";
    private static final String AWS_ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE";

    /** All ten, in the order the work unit lists them. */
    private static final List<String> CORPUS =
            List.of(
                    "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    "ghp_S3cr3tT0k3nExampleValue0123456789ab",
                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                            + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    "hunter2-not-a-real-password",
                    "Tr0ub4dor-26-3",
                    "ll_live_9f8e7d6c5b4a39281706",
                    "correct-horse-battery-staple",
                    "swordfish-42",
                    "tok_live_abcdef0123456789",
                    "AKIAIOSFODNN7EXAMPLE");

    /** How a production run is configured: every credential it holds is registered. */
    private static SecretRedactor loaded() {
        return SecretRedactor.with(SecretRegistry.copyOf(CORPUS));
    }

    /** One rendered artefact, with the name of the carrier it came from. */
    private record Carrier(String name, String rendered) {}

    // -----------------------------------------------------------------------------------------
    // The carriers.
    // -----------------------------------------------------------------------------------------

    private static Map<String, String> environment() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("AWS_SECRET_ACCESS_KEY", AWS_SECRET);
        environment.put("GITHUB_TOKEN", GITHUB_TOKEN);
        environment.put("LIMELIGHT_API_KEY", LIMELIGHT_KEY);
        environment.put("PERCOLATOR_PASSWORD", PASSPHRASE);
        environment.put("COMET_PARAMS", "/data/comet.params");
        return environment;
    }

    private static List<String> argv() {
        return List.of(
                "/opt/limelight/bin/upload",
                "--password",
                PASSWORD,
                "--password=" + PASSWORD,
                "--api-key",
                AWS_ACCESS_KEY_ID,
                "-k",
                AWS_SECRET,
                "--input",
                "/data/HeLa_1ug_rep1.mzML");
    }

    private static String credentialUrl() {
        return "https://limelight-user:" + URL_PASSWORD + "@limelight.example.org/api/upload";
    }

    private static List<String> logLines() {
        return List.of(
                "GET /api/upload -> Authorization: Bearer " + JWT,
                "connecting with password: " + SWORDFISH,
                "response body {\"token\":\"" + LIVE_TOKEN + "\"}",
                "limelight key ll_live_9f8e7d6c5b4a39281706 accepted",
                "github token " + GITHUB_TOKEN + " accepted");
    }

    // -----------------------------------------------------------------------------------------
    // Full expected outputs, hand-typed.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("the environment carrier comes out exactly as written here")
    void environmentCarrier() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("AWS_SECRET_ACCESS_KEY", "[REDACTED]");
        expected.put("GITHUB_TOKEN", "[REDACTED]");
        expected.put("LIMELIGHT_API_KEY", "[REDACTED]");
        expected.put("PERCOLATOR_PASSWORD", "[REDACTED]");
        expected.put("COMET_PARAMS", "/data/comet.params");

        assertEquals(expected, loaded().redactEnvironment(environment()));
    }

    @Test
    @DisplayName("the argument-array carrier comes out exactly as written here")
    void argvCarrier() {
        assertEquals(
                List.of(
                        "/opt/limelight/bin/upload",
                        "--password",
                        "[REDACTED]",
                        "--password=[REDACTED]",
                        "--api-key",
                        "[REDACTED]",
                        "-k",
                        "[REDACTED]",
                        "--input",
                        "/data/HeLa_1ug_rep1.mzML"),
                loaded().redactArgv(argv()));
    }

    @Test
    @DisplayName("the credential-URL carrier keeps its scheme, user and host")
    void credentialUrlCarrier() {
        assertEquals(
                "https://limelight-user:[REDACTED]@limelight.example.org/api/upload",
                loaded().redactText(credentialUrl()));
    }

    @Test
    @DisplayName("every log line comes out exactly as written here")
    void logLineCarrier() {
        SecretRedactor redactor = loaded();

        assertEquals(
                "GET /api/upload -> Authorization: Bearer [REDACTED]",
                redactor.redactText("GET /api/upload -> Authorization: Bearer " + JWT));
        assertEquals(
                "connecting with password: [REDACTED]",
                redactor.redactText("connecting with password: " + SWORDFISH));
        assertEquals(
                "response body {\"token\":\"[REDACTED]\"}",
                redactor.redactText("response body {\"token\":\"" + LIVE_TOKEN + "\"}"));
        assertEquals(
                "limelight key [REDACTED] accepted",
                redactor.redactText("limelight key ll_live_9f8e7d6c5b4a39281706 accepted"));
        assertEquals(
                "github token [REDACTED] accepted",
                redactor.redactText("github token " + GITHUB_TOKEN + " accepted"));
    }

    // -----------------------------------------------------------------------------------------
    // The sweep.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("not one of the ten seeded secrets survives any carrier")
    void notOneSecretSurvivesAnyCarrier() {
        List<Carrier> redacted = redactEveryCarrier(loaded());
        StringBuilder everything = new StringBuilder();
        for (Carrier carrier : redacted) {
            everything.append(carrier.rendered()).append('\n');
        }
        String artefacts = everything.toString();

        assertTrue(
                artefacts.contains("[REDACTED]"),
                "the redacted artefacts contain no marker at all, so nothing was redacted");

        List<String> leaks = new ArrayList<>();
        for (int secret = 0; secret < CORPUS.size(); secret++) {
            for (Carrier carrier : redacted) {
                if (carrier.rendered().contains(CORPUS.get(secret))) {
                    // The secret itself is never named, for the reason this whole package exists.
                    leaks.add(
                            "corpus secret #"
                                    + secret
                                    + " (length "
                                    + CORPUS.get(secret).length()
                                    + ") survived the "
                                    + carrier.name()
                                    + " carrier");
                }
            }
        }

        assertTrue(leaks.isEmpty(), "the seeded corpus leaked: " + leaks);
    }

    @Test
    @DisplayName("the pattern rules alone cover every carrier a pattern is meant to cover")
    void patternsAloneCoverWhatTheyClaim() {
        List<Carrier> redacted = redactEveryCarrier(SecretRedactor.patternsOnly());

        List<String> leaks = new ArrayList<>();
        for (Carrier carrier : redacted) {
            if (patternRulesAreResponsibleFor(carrier.name())) {
                for (int secret = 0; secret < CORPUS.size(); secret++) {
                    if (carrier.rendered().contains(CORPUS.get(secret))) {
                        leaks.add(
                                "corpus secret #"
                                        + secret
                                        + " survived the "
                                        + carrier.name()
                                        + " carrier with the pattern rules alone");
                    }
                }
            }
        }

        assertTrue(leaks.isEmpty(), "a pattern rule stopped covering its carrier: " + leaks);
    }

    @Test
    @DisplayName("the two carriers only the registry can cover are named, and it does cover them")
    void onlyTheRegistryCoversTheseTwo() {
        assertEquals(
                "-k wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                SecretRedactor.patternsOnly().redactText("-k " + AWS_SECRET),
                "a pattern rule started catching a bare value after a single-letter flag;"
                        + " the documented boundary has moved and the class Javadoc is now wrong");
        assertEquals(
                "limelight key ll_live_9f8e7d6c5b4a39281706 accepted",
                SecretRedactor.patternsOnly()
                        .redactText("limelight key " + LIMELIGHT_KEY + " accepted"),
                "a pattern rule started catching a bare vendor token;"
                        + " the documented boundary has moved and the class Javadoc is now wrong");
        assertEquals("-k [REDACTED]", loaded().redactText("-k " + AWS_SECRET));
        assertEquals(
                "limelight key [REDACTED] accepted",
                loaded().redactText("limelight key " + LIMELIGHT_KEY + " accepted"));
    }

    /**
     * Whether the pattern half of the rule set is supposed to cover a carrier on its own.
     *
     * <p>The two log lines that carry a bare vendor token with no surrounding syntax are the
     * registry's job by design; see the class documentation on {@link SecretRedactor}. Naming them
     * here rather than quietly excluding them is the point: if a later change makes a pattern cover
     * one of them, {@link #onlyTheRegistryCoversTheseTwo()} fails and says so.
     *
     * @param carrier the carrier's name
     * @return whether pattern rules alone must clear it
     */
    private static boolean patternRulesAreResponsibleFor(String carrier) {
        return !"argv".equals(carrier) && !"log line 3".equals(carrier);
    }

    private static List<Carrier> redactEveryCarrier(SecretRedactor redactor) {
        List<Carrier> carriers = new ArrayList<>();
        StringBuilder environmentText = new StringBuilder();
        for (Map.Entry<String, String> variable :
                redactor.redactEnvironment(environment()).entrySet()) {
            environmentText.append(variable.getKey()).append('=').append(variable.getValue());
            environmentText.append('\n');
        }
        carriers.add(new Carrier("environment", environmentText.toString()));
        carriers.add(new Carrier("argv", String.join(" ", redactor.redactArgv(argv()))));
        carriers.add(new Carrier("credential URL", redactor.redactText(credentialUrl())));
        List<String> lines = logLines();
        for (int line = 0; line < lines.size(); line++) {
            carriers.add(new Carrier("log line " + line, redactor.redactText(lines.get(line))));
        }
        return carriers;
    }
}
