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

package org.cometgui.domain.secrets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The phase-04 seeded secret corpus, in every carrier, through {@link SecretRedactor}.
 *
 * <p>This is the unit-level half of exit gate item 6: "a seeded corpus of secrets (tokens,
 * passwords, bearer headers, credential-bearing URLs) appears nowhere in JSON, RST or logs". Later
 * units grep the generated artefacts for the first ten of these exact strings; this file proves the
 * redactor they will be calling removes every one of them from every shape they can arrive in,
 * before any of those artefacts exist.
 *
 * <p><strong>WHAT A SWEEP LIKE THIS IS BLIND TO, AND WHY EVERY CARRIER BELOW IS THE SIZE IT
 * IS.</strong> Unit 11 builds the artefact-level grep on this same corpus, so these two warnings
 * are the ones to inherit rather than rediscover. An "is the secret still in the output" check
 * cannot see:
 *
 * <ol>
 *   <li><b>A partial rewrite of the secret.</b> {@code contains} is defeated by one changed
 *       character. When the PEM rule was deleted to prove it could fail, an earlier rule rewrote
 *       the {@code =} padding at the end of the key body; 99% of the key was still there, the key
 *       had leaked, and {@code contains(body)} was false. The corpus therefore holds a long
 *       secret's individual <em>lines</em>, so that a partial mutation still leaves whole units of
 *       secret material to find.
 *   <li><b>A leak conditioned on the input's size.</b> A redactor that gave up on short inputs --
 *       {@code if (text.length() < 32) return text;} as a "fast path" -- leaks in clear and is
 *       invisible to a corpus whose carriers all happen to be long. Every carrier here was once
 *       long: the shortest was 38 characters, and that defect shipped past this file with 8 tests
 *       green. <b>Carrier LENGTH is therefore part of this corpus's coverage, not an accident of
 *       how the examples were written.</b> {@link #shortCarriers()} exists solely to hold one
 *       deliberately small carrier per rule family, the smallest being twelve characters. Do not
 *       "tidy" them into realistic-looking longer examples.
 *   <li><b>A leak conditioned on the OCCURRENCE COUNT.</b> A rule that clears the first match and
 *       leaves the rest -- {@code replaceAll} changed to {@code replaceFirst}, one character -- is
 *       invisible to a corpus in which no carrier holds its secret more than once. Every carrier
 *       here did exactly that, and the defect passed 83 domain tests and 22 provenance secrecy
 *       tests with a secret-leaking change live in the shared rule set. It is not an exotic input:
 *       {@code --token X --retry-token X}, an environment dump, or a log line that repeats a URL
 *       all carry one secret twice. <b>Occurrence COUNT is therefore part of this corpus's coverage
 *       too</b>, and {@link #repeatedCarriers()} holds one carrier per rule family with its secret
 *       in it twice.
 *       <p><b>Those carriers must be swept with {@link SecretRedactor#patternsOnly()}, and this is
 *       the part that is easy to get wrong.</b> {@link #loaded()} registers the whole corpus, and
 *       {@code SecretRegistry.redactIn} uses {@code String.replace}, which clears EVERY occurrence
 *       -- so the literal pass that runs first in {@code redactText} repairs a broken pattern rule
 *       before that rule is ever reached, and the defect above stays invisible. Measured, not
 *       reasoned: with {@code replaceFirst} injected, the token-shape carrier reads {@code
 *       [REDACTED] AKIAIOSFODNN7EXAMPLE} under the pattern rules alone and {@code [REDACTED]
 *       [REDACTED]} under a loaded registry. {@link #patternsAloneCoverWhatTheyClaim()} is the
 *       sweep that sees it; a "simplification" of that test to use {@link #loaded()} would silently
 *       remove the only thing that catches this.
 * </ol>
 *
 * <p>The general lesson is that a sweep proves the absence of a string, not the presence of
 * redaction, and it is only as strong as the shapes and sizes of the inputs it is given. That is
 * why every carrier also carries a hand-typed full-output assertion.
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
    // The ten seeded secrets from the work unit's brief, hand-transcribed, plus the three
    // lines of the PEM private-key body the phase orchestrator added at rework.
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

    /**
     * The base64 body of a PEM private key, added by the phase orchestrator's rework of this unit.
     *
     * <p>It is not one of the ten the work unit pinned -- those ten are what later units grep the
     * generated JSON, RST and logs for -- but a private key is a credential carrier the gate's
     * example list happened not to name, and leaving it outside this sweep would be a hole. It is
     * deliberately synthetic; see the identical fixture in {@code SecretRedactorTest}.
     *
     * <p><strong>{@link #CORPUS} holds its three lines separately, not this joined form, and the
     * reason is a defect this file had until it was measured.</strong> The sweep asks whether a
     * secret still {@code contains}s in the output, and a whole-body check is defeated by a
     * one-character change: when the PEM rule was deleted to prove it could fail, the assignment
     * rule reached the base64 and replaced the {@code =} padding at the end of the last line, so
     * the body was 99% intact -- the key had leaked -- and {@code contains(body)} was false. The
     * sweep passed vacuously. Line by line, a partial mutation still leaves whole lines of key
     * material to find, and the sweep sees them.
     *
     * <p>The fixture also spells the words {@code PrivateKey} inside its base64 on purpose. That is
     * what makes the assignment rule bite if the PEM rule is ever moved out of first place in
     * {@code redactText}, so the rule ordering is pinned by a test rather than by a comment.
     */
    private static final String PEM_BODY =
            "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample\n"
                    + "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/\n"
                    + "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==";

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
                    "AKIAIOSFODNN7EXAMPLE",
                    "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample",
                    "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/",
                    "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==");

    /** How a production run is configured: every credential it holds is registered. */
    private static SecretRedactor loaded() {
        return SecretRedactor.with(SecretRegistry.copyOf(CORPUS));
    }

    /** One rendered artefact, with the name of the carrier it came from. */
    private record Carrier(String name, String rendered) {}

    /**
     * The carriers no pattern rule can clear, each for a reason worth stating.
     *
     * <ul>
     *   <li>{@code argv} -- holds {@code -k <secret>}, and a single-letter flag never makes the
     *       next argument a credential; {@code AC-PRV-03} requires the argument array to be
     *       recorded exactly.
     *   <li>{@code short argv} -- the same thing, in two elements.
     *   <li>{@code log line 3} -- a bare vendor token with no surrounding syntax and no published
     *       prefix worth pattern-matching.
     *   <li>{@code short bare value} -- a bare password with no syntax around it at all.
     *   <li>{@code short unnamed assignment} -- {@code pw=} is not a secret-looking name; see
     *       {@link #shortCarriers()}.
     * </ul>
     */
    private static final Set<String> CARRIERS_ONLY_THE_REGISTRY_CLEARS =
            Set.of(
                    "argv",
                    "short argv",
                    "log line 3",
                    "short bare value",
                    "short unnamed assignment",
                    // The repeated carriers inherit their family's classification: duplicating a
                    // carrier cannot make a pattern rule start or stop covering it.
                    "short argv twice",
                    "short bare value twice",
                    "short unnamed assignment twice");

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

    /**
     * One deliberately small carrier per rule family, name to raw input.
     *
     * <p>These exist because of blind spot (2) in the class documentation, and their size is the
     * whole point: the longest is 23 characters and the shortest is 12, so a redactor that
     * short-circuits on small inputs cannot hide behind them. Each one carries a corpus secret
     * through a different rule, in the smallest text that rule can appear in.
     *
     * <p>{@code pw=} is here on purpose alongside {@code auth=}. {@code pw} is <em>not</em> one of
     * {@link SecretRedactor#secretNameKeywords()} -- neither is {@code pass} -- so the pattern
     * rules do not clear it and only the registry does. That is a real boundary of the name list
     * and it is better recorded as a carrier than left as a surprise.
     *
     * @return each short carrier's name and the text to run through the redactor
     */
    private static Map<String, String> shortCarriers() {
        Map<String, String> carriers = new LinkedHashMap<>();
        carriers.put("short assignment", "auth=" + SWORDFISH);
        carriers.put("short bearer", "Bearer " + SWORDFISH);
        carriers.put("short credential URL", "ftp://u:" + SWORDFISH + "@h/");
        carriers.put("short token shape", AWS_ACCESS_KEY_ID);
        carriers.put("short unnamed assignment", "pw=" + SWORDFISH);
        carriers.put("short bare value", SWORDFISH);
        return carriers;
    }

    /**
     * The smallest argument array that can carry a credential: two elements, fifteen characters.
     */
    private static List<String> shortArgv() {
        return List.of("-k", SWORDFISH);
    }

    /**
     * Every short carrier with its secret in it <strong>twice</strong>, one per rule family.
     *
     * <p>These exist because of blind spot (3) in the class documentation. Each is built by
     * repeating its own entry from {@link #shortCarriers()} with a single space between the two
     * copies, rather than being written out again here, and that is deliberate twice over: it makes
     * the shortness guard {@link #everyShortCarrierIsActuallyShort()} apply to these by
     * construction, and it means a later edit to a short carrier cannot leave its repeated twin
     * testing a different rule. {@link #everyRepeatedCarrierIsItsShortCarrierTwice()} pins that
     * relationship so the two cannot drift apart.
     *
     * @return each repeated carrier's name and the text to run through the redactor
     */
    private static Map<String, String> repeatedCarriers() {
        Map<String, String> repeated = new LinkedHashMap<>();
        for (Map.Entry<String, String> carrier : shortCarriers().entrySet()) {
            repeated.put(
                    carrier.getKey() + " twice", carrier.getValue() + " " + carrier.getValue());
        }
        return repeated;
    }

    /** The smallest argument array carrying one credential in two separate elements. */
    private static List<String> repeatedArgv() {
        return List.of("-k", SWORDFISH, "-k", SWORDFISH);
    }

    /**
     * Two differently-named variables holding one credential.
     *
     * <p>A map cannot repeat a key, so "twice" for an environment means two names that are both
     * secret-looking carrying the same value -- which is what a shell that exports both {@code
     * GITHUB_TOKEN} and {@code GH_TOKEN} actually produces.
     *
     * @return the environment
     */
    private static Map<String, String> repeatedEnvironment() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("GITHUB_TOKEN", GITHUB_TOKEN);
        environment.put("GH_TOKEN", GITHUB_TOKEN);
        environment.put("COMET_PARAMS", "/data/comet.params");
        return environment;
    }

    /** A log entry carrying two whole PEM blocks, which is the long family's repeated carrier. */
    private static String repeatedPemBlocks() {
        return "-----BEGIN RSA PRIVATE KEY-----\n"
                + PEM_BODY
                + "\n-----END RSA PRIVATE KEY-----\n"
                + "-----BEGIN RSA PRIVATE KEY-----\n"
                + PEM_BODY
                + "\n-----END RSA PRIVATE KEY-----";
    }

    private static String credentialUrl() {
        return "https://limelight-user:" + URL_PASSWORD + "@limelight.example.org/api/upload";
    }

    private static String pemLogEntry() {
        return "2026-08-31T09:15:00Z upload: key material follows\n"
                + "-----BEGIN RSA PRIVATE KEY-----\n"
                + PEM_BODY
                + "\n-----END RSA PRIVATE KEY-----\n"
                + "2026-08-31T09:15:01Z upload: done";
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
    @DisplayName("every short carrier comes out exactly as written here")
    void shortCarriersComeOutAsWritten() {
        SecretRedactor redactor = loaded();

        assertEquals("auth=[REDACTED]", redactor.redactText("auth=swordfish-42"));
        assertEquals("Bearer [REDACTED]", redactor.redactText("Bearer swordfish-42"));
        assertEquals("ftp://u:[REDACTED]@h/", redactor.redactText("ftp://u:swordfish-42@h/"));
        assertEquals("[REDACTED]", redactor.redactText("AKIAIOSFODNN7EXAMPLE"));
        assertEquals("pw=[REDACTED]", redactor.redactText("pw=swordfish-42"));
        assertEquals("[REDACTED]", redactor.redactText("swordfish-42"));
        assertEquals(List.of("-k", "[REDACTED]"), redactor.redactArgv(shortArgv()));
    }

    @Test
    @DisplayName("no short carrier is long enough for a size-conditioned defect to hide behind")
    void everyShortCarrierIsActuallyShort() {
        // The property this file was missing.  Asserted rather than trusted, because the carriers
        // are ordinary-looking strings and nothing else would notice them growing.
        for (Map.Entry<String, String> carrier : shortCarriers().entrySet()) {
            assertTrue(
                    carrier.getValue().length() < 32,
                    "the \""
                            + carrier.getKey()
                            + "\" carrier has grown to "
                            + carrier.getValue().length()
                            + " characters; see blind spot (2) in this class's documentation");
        }
        for (String argument : shortArgv()) {
            assertTrue(
                    argument.length() < 32,
                    "a short argv element has grown to " + argument.length() + " characters");
        }
        assertEquals(12, SWORDFISH.length());
    }

    @Test
    @DisplayName("every repeated carrier loses BOTH occurrences, not just the first")
    void repeatedCarriersComeOutAsWritten() {
        // Blind spot (3).  Every expected string below is typed from the RULE -- each occurrence
        // of the secret becomes the marker and everything else survives -- and not captured from
        // the redactor.
        //
        // THE PATTERN FAMILIES ARE SWEPT WITH patternsOnly() ON PURPOSE.  loaded() registers the
        // whole corpus and the literal pass runs FIRST in redactText, clearing every occurrence
        // with String.replace before any pattern rule is reached; under it a rule that cleared
        // only its first match would look perfect.  Do not "simplify" these to loaded().
        SecretRedactor patterns = SecretRedactor.patternsOnly();

        assertAll(
                () ->
                        assertEquals(
                                "auth=[REDACTED] auth=[REDACTED]",
                                patterns.redactText("auth=swordfish-42 auth=swordfish-42")),
                () ->
                        assertEquals(
                                "Bearer [REDACTED] Bearer [REDACTED]",
                                patterns.redactText("Bearer swordfish-42 Bearer swordfish-42")),
                () ->
                        assertEquals(
                                "ftp://u:[REDACTED]@h/ ftp://u:[REDACTED]@h/",
                                patterns.redactText(
                                        "ftp://u:swordfish-42@h/ ftp://u:swordfish-42@h/")),
                () ->
                        assertEquals(
                                "[REDACTED] [REDACTED]",
                                patterns.redactText("AKIAIOSFODNN7EXAMPLE AKIAIOSFODNN7EXAMPLE")),
                () ->
                        assertEquals(
                                "[REDACTED]\n[REDACTED]", patterns.redactText(repeatedPemBlocks())),
                // The name rule needs no registry: a variable called *TOKEN is a credential
                // whatever its value looks like, and both of these are.
                () ->
                        assertEquals(
                                Map.of(
                                        "GITHUB_TOKEN",
                                        "[REDACTED]",
                                        "GH_TOKEN",
                                        "[REDACTED]",
                                        "COMET_PARAMS",
                                        "/data/comet.params"),
                                patterns.redactEnvironment(repeatedEnvironment())));
    }

    @Test
    @DisplayName("the repeated carriers only the registry can clear lose both occurrences too")
    void repeatedRegistryOnlyCarriersComeOutAsWritten() {
        // The other half: a bare value, an unnamed assignment and a single-letter flag are not
        // covered by any pattern rule, so these prove SecretRegistry.redactIn clears every
        // occurrence rather than the first.  Here loaded() is the redactor under test, not a
        // masking hazard.
        SecretRedactor redactor = loaded();

        assertAll(
                () ->
                        assertEquals(
                                "[REDACTED] [REDACTED]",
                                redactor.redactText("swordfish-42 swordfish-42")),
                () ->
                        assertEquals(
                                "pw=[REDACTED] pw=[REDACTED]",
                                redactor.redactText("pw=swordfish-42 pw=swordfish-42")),
                () ->
                        assertEquals(
                                List.of("-k", "[REDACTED]", "-k", "[REDACTED]"),
                                redactor.redactArgv(repeatedArgv())));
    }

    @Test
    @DisplayName("every repeated carrier really is its short carrier twice, so it stays short")
    void everyRepeatedCarrierIsItsShortCarrierTwice() {
        // The property that keeps blind spots (2) and (3) from pulling against each other.  If a
        // repeated carrier were written out by hand it could quietly grow into the realistic
        // long example the class documentation warns against, and the size guard would not see
        // it because it only looks at shortCarriers().  Asserted rather than trusted.
        Map<String, String> shorts = shortCarriers();
        Map<String, String> repeated = repeatedCarriers();

        assertEquals(shorts.size(), repeated.size());
        for (Map.Entry<String, String> carrier : shorts.entrySet()) {
            String twice = repeated.get(carrier.getKey() + " twice");
            assertEquals(
                    carrier.getValue() + " " + carrier.getValue(),
                    twice,
                    "the \""
                            + carrier.getKey()
                            + "\" repeated carrier is no longer its short"
                            + " carrier twice, so it no longer inherits the size guard");
            assertTrue(
                    twice.contains(SWORDFISH) || twice.contains(AWS_ACCESS_KEY_ID),
                    "the \"" + carrier.getKey() + "\" repeated carrier carries no corpus secret");
        }
    }

    @Test
    @DisplayName("the PEM-block carrier loses the key and keeps the log lines around it")
    void pemBlockCarrier() {
        assertEquals(
                "2026-08-31T09:15:00Z upload: key material follows\n"
                        + "[REDACTED]\n"
                        + "2026-08-31T09:15:01Z upload: done",
                loaded().redactText(pemLogEntry()));
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
    @DisplayName("not one of the thirteen seeded secrets survives any carrier, long or short")
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
     * Whether the pattern half of the rule set is supposed to clear a carrier on its own.
     *
     * <p>An explicit list, so that the boundary between the two halves is written down rather than
     * inferred. If a later change makes a pattern rule cover one of these, {@link
     * #onlyTheRegistryCoversTheseTwo()} fails and says so; if a change makes a pattern rule stop
     * covering anything else, {@link #patternsAloneCoverWhatTheyClaim()} does.
     *
     * @param carrier the carrier's name
     * @return whether pattern rules alone must clear it
     */
    private static boolean patternRulesAreResponsibleFor(String carrier) {
        return !CARRIERS_ONLY_THE_REGISTRY_CLEARS.contains(carrier);
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
        carriers.add(new Carrier("pem block", redactor.redactText(pemLogEntry())));
        List<String> lines = logLines();
        for (int line = 0; line < lines.size(); line++) {
            carriers.add(new Carrier("log line " + line, redactor.redactText(lines.get(line))));
        }
        for (Map.Entry<String, String> carrier : shortCarriers().entrySet()) {
            carriers.add(new Carrier(carrier.getKey(), redactor.redactText(carrier.getValue())));
        }
        carriers.add(new Carrier("short argv", String.join(" ", redactor.redactArgv(shortArgv()))));
        // Blind spot (3): one carrier per rule family with its secret in it twice.  These reach
        // patternsAloneCoverWhatTheyClaim() as well as the absence sweep, and that is the test
        // that catches a rule which clears only its first match.
        for (Map.Entry<String, String> carrier : repeatedCarriers().entrySet()) {
            carriers.add(new Carrier(carrier.getKey(), redactor.redactText(carrier.getValue())));
        }
        carriers.add(
                new Carrier(
                        "short argv twice", String.join(" ", redactor.redactArgv(repeatedArgv()))));
        carriers.add(new Carrier("pem block twice", redactor.redactText(repeatedPemBlocks())));
        StringBuilder repeatedEnvironmentText = new StringBuilder();
        for (Map.Entry<String, String> variable :
                redactor.redactEnvironment(repeatedEnvironment()).entrySet()) {
            repeatedEnvironmentText
                    .append(variable.getKey())
                    .append('=')
                    .append(variable.getValue());
            repeatedEnvironmentText.append('\n');
        }
        carriers.add(new Carrier("environment twice", repeatedEnvironmentText.toString()));
        return carriers;
    }
}
