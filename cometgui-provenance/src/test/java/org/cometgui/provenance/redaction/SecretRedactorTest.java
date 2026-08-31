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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SecretRedactor}.
 *
 * <p><strong>Where every expected value in this file came from.</strong> Every expected output
 * string below was typed out in full, by hand. None of them is built by calling the redactor, by
 * concatenating {@link SecretRedactor#REDACTION_MARKER} into a template, or by transforming the
 * input in Java. The marker appears as the literal {@code [REDACTED]} throughout, so that a change
 * to the constant fails these tests rather than quietly following them.
 *
 * <p><strong>Why the assertions are on the whole string and not on absence.</strong> Asserting only
 * that the secret is gone is necessary and not sufficient: a redactor that returned the empty
 * string, or that replaced the entire line with the marker, would satisfy every absence check ever
 * written and would destroy the provenance record it was protecting. So each case asserts the
 * complete expected output -- which fails on under-redaction and on over-redaction alike -- and
 * then, separately, asserts the secret is absent.
 *
 * <p>The groups follow the work unit's acceptance conditions: (1) the marker itself, (2) the rule
 * set as published data, (3) credential URLs, (4) authentication headers, (5) secret-named
 * assignments, (6) well-known token shapes, (7) the literal registry and what only it can catch,
 * (8) environments, (9) argument arrays and the long-flag constraint, (10) idempotence, (11)
 * ordinary content surviving byte-identical, (12) null handling, (13) sharing between threads.
 */
class SecretRedactorTest {

    // -----------------------------------------------------------------------------------------
    // The phase-04 seeded corpus, hand-transcribed.  Later units grep generated artefacts for
    // these exact strings, so they are typed here character for character.
    // -----------------------------------------------------------------------------------------

    /** An AWS secret access key; forty characters, two of them slashes. */
    private static final String AWS_SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    /** A GitHub personal access token in the modern prefixed format. */
    private static final String GITHUB_TOKEN = "ghp_S3cr3tT0k3nExampleValue0123456789ab";

    /** A JWT: three base64url segments separated by dots. */
    private static final String JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                    + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    /** A password. */
    private static final String PASSWORD = "hunter2-not-a-real-password";

    /** The password that appears inside the credential-bearing URL. */
    private static final String URL_PASSWORD = "Tr0ub4dor-26-3";

    /** A Limelight API key. */
    private static final String LIMELIGHT_KEY = "ll_live_9f8e7d6c5b4a39281706";

    /** A passphrase. */
    private static final String PASSPHRASE = "correct-horse-battery-staple";

    /** A short, word-like password that no pattern could recognise on its own. */
    private static final String SWORDFISH = "swordfish-42";

    /** A vendor token with a prefix this class deliberately does not pattern-match. */
    private static final String LIVE_TOKEN = "tok_live_abcdef0123456789";

    /** An AWS access key id: {@code AKIA} followed by sixteen uppercase alphanumerics. */
    private static final String AWS_ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE";

    /**
     * The base64 body of a PEM private key: the secret material inside the delimiters.
     *
     * <p>Deliberately synthetic. It uses the base64 alphabet and the line width a real key has, so
     * that the rule is exercised on the shape it will meet, and it spells out in plain words that
     * it is a fixture, so that nobody reading this file ever has to wonder whether a real key was
     * committed to the repository.
     */
    private static final String PEM_BODY =
            "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample\n"
                    + "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/\n"
                    + "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==";

    /** A redactor holding every corpus value, which is how a real run is configured. */
    private static SecretRedactor loaded() {
        return SecretRedactor.with(
                SecretRegistry.of(
                        AWS_SECRET,
                        GITHUB_TOKEN,
                        JWT,
                        PASSWORD,
                        URL_PASSWORD,
                        LIMELIGHT_KEY,
                        PASSPHRASE,
                        SWORDFISH,
                        LIVE_TOKEN,
                        AWS_ACCESS_KEY_ID,
                        PEM_BODY));
    }

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * <p>A test that proves a method rejects {@code null} has to pass it {@code null}. SpotBugs
     * runs at effort Max here and reports a null <em>literal</em> handed to a parameter the callee
     * dereferences on every path as NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS -- the test's whole
     * purpose reported as the bug. The repository's exclusion policy says a finding is fixed in the
     * code rather than filtered away, so the null arrives through a value instead of a literal. The
     * argument reaching the method under test is exactly as null as it was before.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    @Nested
    @DisplayName("The marker")
    class Marker {

        @Test
        @DisplayName("is exactly [REDACTED]")
        void isExactlyRedacted() {
            assertEquals("[REDACTED]", SecretRedactor.REDACTION_MARKER);
        }

        @Test
        @DisplayName("contains no asterisk, because ** opens strong emphasis in reStructuredText")
        void containsNoAsterisk() {
            assertFalse(
                    SecretRedactor.REDACTION_MARKER.contains("*"),
                    "an asterisk in the marker breaks provenance.rst under sphinx-build -n -W:"
                            + " the marker was "
                            + SecretRedactor.REDACTION_MARKER);
        }

        @Test
        @DisplayName("is ten characters long")
        void isTenCharactersLong() {
            assertEquals(10, SecretRedactor.REDACTION_MARKER.length());
        }
    }

    @Nested
    @DisplayName("The rule set is published data, not private lore")
    class PublishedRuleSet {

        @Test
        @DisplayName("the secret-name keywords are exactly these twelve")
        void keywordsAreExactlyThese() {
            assertEquals(
                    Set.of(
                            "token",
                            "secret",
                            "password",
                            "passwd",
                            "credential",
                            "apikey",
                            "auth",
                            "session",
                            "cookie",
                            "privatekey",
                            "passphrase",
                            "accesskey"),
                    SecretRedactor.secretNameKeywords());
        }

        @Test
        @DisplayName("pwd is deliberately absent, because PWD is the working directory")
        void pwdIsAbsent() {
            assertFalse(SecretRedactor.secretNameKeywords().contains("pwd"));
            assertFalse(SecretRedactor.isSecretName("PWD"));
        }

        @Test
        @DisplayName("the secret-bearing long flags are exactly these seventeen")
        void flagsAreExactlyThese() {
            assertEquals(
                    Set.of(
                            "--password",
                            "--passwd",
                            "--pass",
                            "--passphrase",
                            "--token",
                            "--auth-token",
                            "--access-token",
                            "--session-token",
                            "--api-key",
                            "--apikey",
                            "--api_key",
                            "--secret",
                            "--client-secret",
                            "--secret-key",
                            "--credential",
                            "--credentials",
                            "--private-key"),
                    SecretRedactor.secretBearingLongFlags());
        }

        @Test
        @DisplayName(
                "no single-letter flag is in the positional rule, because AC-PRV-03 forbids it")
        void noSingleLetterFlags() {
            Set<String> flags = SecretRedactor.secretBearingLongFlags();

            for (String ambiguous : List.of("-p", "-k", "-s", "-t", "-P", "-a", "-c")) {
                assertFalse(
                        flags.contains(ambiguous),
                        "a single-letter flag entered the positional rule: " + ambiguous);
            }
        }

        @Test
        @DisplayName("both published sets are unmodifiable")
        void publishedSetsAreUnmodifiable() {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> SecretRedactor.secretNameKeywords().add("nonsense"));
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> SecretRedactor.secretBearingLongFlags().add("--nonsense"));
        }

        @Test
        @DisplayName("isSecretName ignores case and separators alike")
        void isSecretNameIgnoresCaseAndSeparators() {
            assertTrue(SecretRedactor.isSecretName("GITHUB_TOKEN"));
            assertTrue(SecretRedactor.isSecretName("github_token"));
            assertTrue(SecretRedactor.isSecretName("GitHubToken"));
            assertTrue(SecretRedactor.isSecretName("github-token"));
            assertTrue(SecretRedactor.isSecretName("github.token"));
            assertTrue(SecretRedactor.isSecretName("LIMELIGHT_API_KEY"));
            assertTrue(SecretRedactor.isSecretName("apiKey"));
            assertTrue(
                    SecretRedactor.isSecretName("upload api key"),
                    "a space is a separator too: a manifest settings key can contain one");
            assertTrue(SecretRedactor.isSecretName("Proxy-Authorization"));
            assertFalse(SecretRedactor.isSecretName("PATH"));
            assertFalse(SecretRedactor.isSecretName("peptide_mass_tolerance"));
            assertFalse(SecretRedactor.isSecretName("database_name"));
        }
    }

    @Nested
    @DisplayName("Credential-bearing URLs keep their scheme, user and host")
    class CredentialUrls {

        @Test
        @DisplayName("the password goes and nothing else does")
        void thePasswordGoesAndNothingElseDoes() {
            String redacted =
                    SecretRedactor.patternsOnly()
                            .redactText(
                                    "https://limelight-user:Tr0ub4dor-26-3"
                                            + "@limelight.example.org/api/upload");

            assertEquals(
                    "https://limelight-user:[REDACTED]@limelight.example.org/api/upload", redacted);
            assertFalse(redacted.contains(URL_PASSWORD));
        }

        @Test
        @DisplayName("works mid-sentence and for other schemes")
        void worksMidSentenceAndForOtherSchemes() {
            assertEquals(
                    "uploading to ftp://svc:[REDACTED]@ftp.example.org/incoming now",
                    SecretRedactor.patternsOnly()
                            .redactText(
                                    "uploading to ftp://svc:s3cr3t-passphrase"
                                            + "@ftp.example.org/incoming now"));
        }

        @Test
        @DisplayName("a URL with a port and no credentials is untouched")
        void aPortIsNotAPassword() {
            assertEquals(
                    "https://limelight.example.org:8443/api/upload",
                    SecretRedactor.patternsOnly()
                            .redactText("https://limelight.example.org:8443/api/upload"));
        }

        @Test
        @DisplayName("a URL with no credentials at all is untouched")
        void aPlainUrlIsUntouched() {
            assertEquals(
                    "see https://github.com/mriffle/CometGUI for the source",
                    SecretRedactor.patternsOnly()
                            .redactText("see https://github.com/mriffle/CometGUI for the source"));
        }
    }

    @Nested
    @DisplayName("Authentication headers")
    class AuthenticationHeaders {

        @Test
        @DisplayName("Bearer keeps its scheme and loses its token")
        void bearerKeepsItsScheme() {
            String redacted =
                    SecretRedactor.patternsOnly()
                            .redactText(
                                    "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                                            + ".eyJzdWIiOiIxIn0"
                                            + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk");

            assertEquals("Authorization: Bearer [REDACTED]", redacted);
            assertFalse(redacted.contains(JWT));
        }

        @Test
        @DisplayName("matching is case-insensitive on both the header and the scheme")
        void matchingIsCaseInsensitive() {
            assertEquals(
                    "authorization: bearer [REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText(
                                    "authorization: bearer"
                                            + " ghp_S3cr3tT0k3nExampleValue0123456789ab"));
        }

        @Test
        @DisplayName("Basic and Proxy-Authorization are covered by the same rule")
        void basicAndProxyAreCovered() {
            assertEquals(
                    "Proxy-Authorization: Basic [REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("Proxy-Authorization: Basic dXNlcjpwYXNzd29yZA=="));
        }

        @Test
        @DisplayName("a header with no scheme keyword still loses its credential")
        void aHeaderWithNoSchemeIsStillRedacted() {
            assertEquals(
                    "Authorization: [REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("Authorization: dXNlcjpwYXNzd29yZA=="));
        }

        @Test
        @DisplayName("a bare Bearer token in prose is redacted too")
        void aBareBearerTokenIsRedacted() {
            assertEquals(
                    "header was Bearer [REDACTED] here",
                    SecretRedactor.patternsOnly()
                            .redactText(
                                    "header was Bearer"
                                            + " ghp_S3cr3tT0k3nExampleValue0123456789ab here"));
        }
    }

    @Nested
    @DisplayName("Assignments whose name looks secret")
    class SecretNamedAssignments {

        @Test
        @DisplayName("colon form")
        void colonForm() {
            assertEquals(
                    "password: [REDACTED]",
                    SecretRedactor.patternsOnly().redactText("password: swordfish-42"));
        }

        @Test
        @DisplayName("equals form, with the name's own case and underscores preserved")
        void equalsForm() {
            assertEquals(
                    "PERCOLATOR_PASSWORD=[REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("PERCOLATOR_PASSWORD=correct-horse-battery-staple"));
        }

        @Test
        @DisplayName("JSON form keeps its quotes so the document stays JSON")
        void jsonForm() {
            assertEquals(
                    "{\"token\":\"[REDACTED]\"}",
                    SecretRedactor.patternsOnly()
                            .redactText("{\"token\":\"tok_live_abcdef0123456789\"}"));
        }

        @Test
        @DisplayName("single-quoted form keeps its quotes too")
        void singleQuotedForm() {
            assertEquals(
                    "'api_key': '[REDACTED]'",
                    SecretRedactor.patternsOnly()
                            .redactText("'api_key': 'll_live_9f8e7d6c5b4a39281706'"));
        }

        @Test
        @DisplayName("the inline long-flag form")
        void inlineLongFlagForm() {
            assertEquals(
                    "--password=[REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("--password=hunter2-not-a-real-password"));
        }

        @Test
        @DisplayName("spacing around the separator is preserved exactly")
        void spacingIsPreserved() {
            assertEquals(
                    "api.key   =   [REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("api.key   =   ll_live_9f8e7d6c5b4a39281706"));
        }

        @Test
        @DisplayName("a secret nested inside a harmless assignment is still found")
        void aNestedSecretIsStillFound() {
            assertEquals(
                    "url:https://h/?password=[REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("url:https://h/?password=hunter2-not-a-real-password"));
        }

        @Test
        @DisplayName("two secret assignments on one line are both redacted, and the gap survives")
        void twoSecretAssignmentsOnOneLine() {
            assertEquals(
                    "password=[REDACTED] token=[REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("password=swordfish-42 token=tok_live_abcdef0123456789"));
        }

        @Test
        @DisplayName("an ordinary name is not touched, and neither is a Windows path")
        void anOrdinaryNameIsNotTouched() {
            assertEquals(
                    "INPUT=C:\\data\\HeLa_1ug_rep1.mzML",
                    SecretRedactor.patternsOnly().redactText("INPUT=C:\\data\\HeLa_1ug_rep1.mzML"));
        }
    }

    @Nested
    @DisplayName("Token shapes that are recognisable with no context")
    class TokenShapes {

        @Test
        @DisplayName("a GitHub personal access token")
        void gitHubToken() {
            assertEquals(
                    "run used [REDACTED] as its token",
                    SecretRedactor.patternsOnly()
                            .redactText(
                                    "run used ghp_S3cr3tT0k3nExampleValue0123456789ab"
                                            + " as its token"));
        }

        @Test
        @DisplayName("the other GitHub prefixes and the fine-grained form")
        void otherGitHubPrefixes() {
            assertEquals(
                    "[REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("gho_S3cr3tT0k3nExampleValue0123456789ab"));
            assertEquals(
                    "[REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("ghs_S3cr3tT0k3nExampleValue0123456789ab"));
            assertEquals(
                    "[REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText("github_pat_11ABCDEFG0abcdefghijklmnop"));
        }

        @Test
        @DisplayName("an AWS access key id")
        void awsAccessKeyId() {
            assertEquals(
                    "key id [REDACTED] in region us-west-2",
                    SecretRedactor.patternsOnly()
                            .redactText("key id AKIAIOSFODNN7EXAMPLE in region us-west-2"));
        }

        @Test
        @DisplayName("a Slack token")
        void slackToken() {
            assertEquals(
                    "[REDACTED]",
                    SecretRedactor.patternsOnly().redactText("xoxb-1234567890-abcdefghijkl"));
        }

        @Test
        @DisplayName("a bare JWT")
        void bareJwt() {
            assertEquals(
                    "token [REDACTED] expired",
                    SecretRedactor.patternsOnly()
                            .redactText(
                                    "token eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                                            + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk"
                                            + " expired"));
        }

        @Test
        @DisplayName("a SHA-256 digest is not a token shape")
        void aDigestIsNotAToken() {
            assertEquals(
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    SecretRedactor.patternsOnly()
                            .redactText(
                                    "e3b0c44298fc1c149afbf4c8996fb924"
                                            + "27ae41e4649b934ca495991b7852b855"));
        }
    }

    @Nested
    @DisplayName("The registry catches what no pattern can")
    class LiteralRegistry {

        @Test
        @DisplayName(
                "a bare AWS secret key is invisible to the patterns and visible to the registry")
        void onlyTheRegistrySeesABareSecret() {
            String carrier = "aws credentials wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY loaded";

            assertEquals(
                    "aws credentials wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY loaded",
                    SecretRedactor.patternsOnly().redactText(carrier),
                    "a pattern rule started catching this; the boundary the class documents moved");
            assertEquals("aws credentials [REDACTED] loaded", loaded().redactText(carrier));
        }

        @Test
        @DisplayName("a registered value is replaced whatever syntax surrounds it")
        void aRegisteredValueIsReplacedAnywhere() {
            assertEquals(
                    "[REDACTED],[REDACTED];(<[REDACTED]>)",
                    loaded().redactText("swordfish-42,swordfish-42;(<swordfish-42>)"));
        }

        @Test
        @DisplayName("the registry runs behind the patterns, not instead of them")
        void theRegistryRunsBehindThePatterns() {
            assertEquals(
                    "https://limelight-user:[REDACTED]@limelight.example.org/api/upload",
                    loaded().redactText(
                                    "https://limelight-user:Tr0ub4dor-26-3"
                                            + "@limelight.example.org/api/upload"));
        }

        @Test
        @DisplayName("a registered value survives a pattern rule that would otherwise chew it up")
        void theRegistryRunsBeforeThePatternsToo() {
            // The assignment rule finds a name whose normalised form contains "privatekey" and an
            // "=" right after it inside this base64 run, and rewrites the padding.  If the literal
            // pass ran only after the patterns, the registered value would no longer be present to
            // match, and sixty-two characters of key material would survive in the record.
            String base64 = "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==";
            SecretRedactor redactor = SecretRedactor.patternsOnly().withSecret(base64);

            assertEquals(
                    "body [REDACTED] end",
                    redactor.redactText(
                            "body ThisIsNotARealPrivateKeyItIsAFixture"
                                    + "ForCometGUIPhase04Tests012== end"));
        }

        @Test
        @DisplayName("the redactor publishes its registry, which still prints nothing")
        void theRedactorPublishesItsRegistry() {
            SecretRedactor redactor = loaded();

            assertEquals(11, redactor.registry().size());
            assertSame(redactor.registry(), redactor.registry());
            assertEquals("SecretRegistry[secretCount=11]", redactor.registry().toString());
        }
    }

    @Nested
    @DisplayName("PEM private-key blocks, delimiters and all")
    class PemPrivateKeys {

        @Test
        @DisplayName("the whole block goes, and the lines around it do not")
        void theWholeBlockGoes() {
            String captured =
                    "2026-08-31 upload key follows\n"
                            + "-----BEGIN RSA PRIVATE KEY-----\n"
                            + PEM_BODY
                            + "\n-----END RSA PRIVATE KEY-----\n"
                            + "done";

            String redacted = SecretRedactor.patternsOnly().redactText(captured);

            assertEquals("2026-08-31 upload key follows\n[REDACTED]\ndone", redacted);
            assertFalse(redacted.contains(PEM_BODY));
            assertFalse(
                    redacted.contains("BEGIN"),
                    "the delimiter survived, which still announces that a key was in play");
        }

        @Test
        @DisplayName("every labelled form and the bare form are covered")
        void everyLabelledFormIsCovered() {
            SecretRedactor redactor = SecretRedactor.patternsOnly();

            assertEquals(
                    "[REDACTED]",
                    redactor.redactText(
                            "-----BEGIN EC PRIVATE KEY-----\nMIH=\n-----END EC PRIVATE KEY-----"));
            assertEquals(
                    "[REDACTED]",
                    redactor.redactText(
                            "-----BEGIN DSA PRIVATE KEY-----\nMIH=\n"
                                    + "-----END DSA PRIVATE KEY-----"));
            assertEquals(
                    "[REDACTED]",
                    redactor.redactText(
                            "-----BEGIN OPENSSH PRIVATE KEY-----\nMIH=\n"
                                    + "-----END OPENSSH PRIVATE KEY-----"));
            assertEquals(
                    "[REDACTED]",
                    redactor.redactText(
                            "-----BEGIN ENCRYPTED PRIVATE KEY-----\nMIH=\n"
                                    + "-----END ENCRYPTED PRIVATE KEY-----"));
            assertEquals(
                    "[REDACTED]",
                    redactor.redactText(
                            "-----BEGIN PRIVATE KEY-----\nMIH=\n-----END PRIVATE KEY-----"));
        }

        @Test
        @DisplayName("a legacy encrypted key keeps its Proc-Type headers inside the redaction")
        void aLegacyEncryptedKeyIsCoveredHeadersAndAll() {
            assertEquals(
                    "key: [REDACTED]",
                    SecretRedactor.patternsOnly()
                            .redactText(
                                    "key: -----BEGIN RSA PRIVATE KEY-----\n"
                                            + "Proc-Type: 4,ENCRYPTED\n"
                                            + "DEK-Info: AES-128-CBC,0123456789ABCDEF\n"
                                            + "\n"
                                            + "MIH=\n"
                                            + "-----END RSA PRIVATE KEY-----"));
        }

        @Test
        @DisplayName("a certificate is public and is left alone")
        void aCertificateIsLeftAlone() {
            String certificate =
                    "-----BEGIN CERTIFICATE-----\n"
                            + "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample\n"
                            + "-----END CERTIFICATE-----";

            assertEquals(certificate, SecretRedactor.patternsOnly().redactText(certificate));
        }

        @Test
        @DisplayName("a lower-case imitation of the delimiter is not a PEM block")
        void aLowerCaseDelimiterIsNotPem() {
            String imitation =
                    "-----begin rsa private key-----\nMIH=\n-----end rsa private key-----";

            assertEquals(imitation, SecretRedactor.patternsOnly().redactText(imitation));
        }

        @Test
        @DisplayName("a block with no END is left exactly as it arrived")
        void anUnterminatedBlockIsLeftAlone() {
            String truncated = "log tail\n-----BEGIN RSA PRIVATE KEY-----\nMIH=\nMIH=";

            assertEquals(truncated, SecretRedactor.patternsOnly().redactText(truncated));
        }

        @Test
        @DisplayName("redacting an already-redacted block changes nothing")
        void redactingTheMarkerAgainChangesNothing() {
            assertEquals(
                    "2026-08-31 upload key follows\n[REDACTED]\ndone",
                    SecretRedactor.patternsOnly()
                            .redactText("2026-08-31 upload key follows\n[REDACTED]\ndone"));
        }

        @Test
        @DisplayName("two megabytes after an unterminated BEGIN finish in well under a second")
        void oneUnterminatedBeginOverALargeInputIsLinear() {
            // The realistic denial of service: a log captured a key and was then truncated, so a
            // BEGIN delimiter is followed by megabytes of text and no END. An unbounded body would
            // scan all of it; the bound caps the work at a constant.
            String haystack = truncatedKeyFollowedByFiller(2_000_000);

            String redacted =
                    assertTimeoutPreemptively(
                            Duration.ofSeconds(5),
                            () -> SecretRedactor.patternsOnly().redactText(haystack));

            assertSame(haystack, redacted, "nothing should have matched, so nothing should differ");
        }

        @Test
        @DisplayName("sixty thousand unterminated BEGIN delimiters finish in well under a second")
        void manyUnterminatedBeginsAreLinear() {
            // The harsher case, and the one the five-dash guard exists for: every anchor would
            // scan its whole bound if the body could cross the next delimiter. It cannot, so each
            // anchor fails immediately and the whole scan stays linear in the input.
            StringBuilder builder = new StringBuilder(2_100_000);
            while (builder.length() < 2_000_000) {
                builder.append("-----BEGIN RSA PRIVATE KEY-----\n");
            }
            String haystack = builder.toString();

            String redacted =
                    assertTimeoutPreemptively(
                            Duration.ofSeconds(5),
                            () -> SecretRedactor.patternsOnly().redactText(haystack));

            assertSame(haystack, redacted, "nothing should have matched, so nothing should differ");
        }

        private String truncatedKeyFollowedByFiller(int atLeast) {
            StringBuilder builder = new StringBuilder(atLeast + 4096);
            builder.append("-----BEGIN RSA PRIVATE KEY-----\n");
            while (builder.length() < atLeast) {
                builder.append("MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKey\n");
            }
            return builder.toString();
        }
    }

    @Nested
    @DisplayName("Environments are redacted by name, and the names survive")
    class Environments {

        private Map<String, String> capturedEnvironment() {
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("AWS_SECRET_ACCESS_KEY", AWS_SECRET);
            environment.put("GITHUB_TOKEN", GITHUB_TOKEN);
            environment.put("github_token", GITHUB_TOKEN);
            environment.put("LIMELIGHT_API_KEY", LIMELIGHT_KEY);
            environment.put("PERCOLATOR_PASSWORD", PASSPHRASE);
            environment.put("DATABASE_URL", "postgres://svc:" + PASSWORD + "@db.example.org/runs");
            environment.put("PWD", "/data/run-2026-08-31");
            environment.put("PATH", "/usr/local/bin:/usr/bin");
            environment.put("COMET_PARAMS", "/data/comet.params");
            return environment;
        }

        private Map<String, String> expectedEnvironment() {
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("AWS_SECRET_ACCESS_KEY", "[REDACTED]");
            expected.put("GITHUB_TOKEN", "[REDACTED]");
            expected.put("github_token", "[REDACTED]");
            expected.put("LIMELIGHT_API_KEY", "[REDACTED]");
            expected.put("PERCOLATOR_PASSWORD", "[REDACTED]");
            expected.put("DATABASE_URL", "postgres://svc:[REDACTED]@db.example.org/runs");
            expected.put("PWD", "/data/run-2026-08-31");
            expected.put("PATH", "/usr/local/bin:/usr/bin");
            expected.put("COMET_PARAMS", "/data/comet.params");
            return expected;
        }

        @Test
        @DisplayName("every entry comes out exactly as written here")
        void everyEntryComesOutAsWritten() {
            assertEquals(
                    expectedEnvironment(),
                    SecretRedactor.patternsOnly().redactEnvironment(capturedEnvironment()));
        }

        @Test
        @DisplayName("the names are never redacted, and keep their order")
        void namesAreNeverRedactedAndKeepTheirOrder() {
            Map<String, String> redacted =
                    SecretRedactor.patternsOnly().redactEnvironment(capturedEnvironment());

            assertEquals(
                    List.of(
                            "AWS_SECRET_ACCESS_KEY",
                            "GITHUB_TOKEN",
                            "github_token",
                            "LIMELIGHT_API_KEY",
                            "PERCOLATOR_PASSWORD",
                            "DATABASE_URL",
                            "PWD",
                            "PATH",
                            "COMET_PARAMS"),
                    List.copyOf(redacted.keySet()));
        }

        @Test
        @DisplayName("a secret-named variable loses a value no pattern would have caught")
        void aSecretNamedVariableLosesAnyValue() {
            assertEquals(
                    Map.of("PERCOLATOR_PASSWORD", "[REDACTED]"),
                    SecretRedactor.patternsOnly()
                            .redactEnvironment(Map.of("PERCOLATOR_PASSWORD", "swordfish-42")));
        }

        @Test
        @DisplayName("an empty environment stays empty")
        void anEmptyEnvironmentStaysEmpty() {
            assertEquals(Map.of(), SecretRedactor.patternsOnly().redactEnvironment(Map.of()));
        }

        @Test
        @DisplayName("the returned map is unmodifiable")
        void theReturnedMapIsUnmodifiable() {
            Map<String, String> redacted =
                    SecretRedactor.patternsOnly().redactEnvironment(Map.of("PATH", "/usr/bin"));

            assertThrows(UnsupportedOperationException.class, () -> redacted.put("EXTRA", "value"));
        }
    }

    @Nested
    @DisplayName("Argument arrays, and the flags that may not grow")
    class ArgumentArrays {

        private List<String> capturedArgv() {
            return List.of(
                    "/opt/limelight/bin/upload",
                    "--server",
                    "https://limelight.example.org",
                    "--password",
                    "hunter2-not-a-real-password",
                    "--api-key=ll_live_9f8e7d6c5b4a39281706",
                    "-k",
                    "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    "-p",
                    "/data/comet.params",
                    "--input",
                    "/data/HeLa_1ug_rep1.mzML");
        }

        @Test
        @DisplayName("every element comes out exactly as written here")
        void everyElementComesOutAsWritten() {
            assertEquals(
                    List.of(
                            "/opt/limelight/bin/upload",
                            "--server",
                            "https://limelight.example.org",
                            "--password",
                            "[REDACTED]",
                            "--api-key=[REDACTED]",
                            "-k",
                            "[REDACTED]",
                            "-p",
                            "/data/comet.params",
                            "--input",
                            "/data/HeLa_1ug_rep1.mzML"),
                    loaded().redactArgv(capturedArgv()));
        }

        @Test
        @DisplayName("a single-letter flag never redacts the argument after it")
        void aSingleLetterFlagNeverRedactsTheNextArgument() {
            assertEquals(
                    List.of("/opt/comet/comet", "-P", "/data/comet.params", "-p", "secret-looking"),
                    SecretRedactor.patternsOnly()
                            .redactArgv(
                                    List.of(
                                            "/opt/comet/comet",
                                            "-P",
                                            "/data/comet.params",
                                            "-p",
                                            "secret-looking")));
        }

        @Test
        @DisplayName("the flag itself is recorded, only its argument goes")
        void theFlagItselfIsRecorded() {
            assertEquals(
                    List.of("--token", "[REDACTED]", "--verbose"),
                    SecretRedactor.patternsOnly()
                            .redactArgv(
                                    List.of(
                                            "--token",
                                            "ghp_S3cr3tT0k3nExampleValue0123456789ab",
                                            "--verbose")));
        }

        @Test
        @DisplayName("a flag comparison ignores case")
        void aFlagComparisonIgnoresCase() {
            assertEquals(
                    List.of("--PASSWORD", "[REDACTED]"),
                    SecretRedactor.patternsOnly()
                            .redactArgv(List.of("--PASSWORD", "hunter2-not-a-real-password")));
        }

        @Test
        @DisplayName("two secret flags in a row do not shift the redaction along")
        void twoSecretFlagsInARow() {
            assertEquals(
                    List.of("--password", "[REDACTED]", "/data/HeLa_1ug_rep1.mzML"),
                    SecretRedactor.patternsOnly()
                            .redactArgv(
                                    List.of("--password", "--token", "/data/HeLa_1ug_rep1.mzML")));
        }

        @Test
        @DisplayName("a secret flag at the very end has nothing to redact")
        void aSecretFlagAtTheEnd() {
            assertEquals(
                    List.of("/opt/comet/comet", "--password"),
                    SecretRedactor.patternsOnly()
                            .redactArgv(List.of("/opt/comet/comet", "--password")));
        }

        @Test
        @DisplayName("an empty argument array stays empty")
        void anEmptyArgvStaysEmpty() {
            assertEquals(List.of(), SecretRedactor.patternsOnly().redactArgv(List.of()));
        }

        @Test
        @DisplayName("the returned list is unmodifiable")
        void theReturnedListIsUnmodifiable() {
            List<String> redacted =
                    SecretRedactor.patternsOnly().redactArgv(List.of("/opt/comet/comet"));

            assertThrows(UnsupportedOperationException.class, () -> redacted.add("extra"));
        }
    }

    @Nested
    @DisplayName("Redaction is idempotent")
    class Idempotence {

        /** Outputs of the rules above, typed out again here rather than computed. */
        private List<String> alreadyRedacted() {
            return List.of(
                    "https://limelight-user:[REDACTED]@limelight.example.org/api/upload",
                    "Authorization: Bearer [REDACTED]",
                    "Proxy-Authorization: Basic [REDACTED]",
                    "authorization: bearer [REDACTED]",
                    "header was Bearer [REDACTED] here",
                    "password: [REDACTED]",
                    "PERCOLATOR_PASSWORD=[REDACTED]",
                    "{\"token\":\"[REDACTED]\"}",
                    "'api_key': '[REDACTED]'",
                    "--password=[REDACTED]",
                    "run used [REDACTED] as its token",
                    "aws credentials [REDACTED] loaded");
        }

        @Test
        @DisplayName("redacting already-redacted text changes nothing")
        void redactingRedactedTextChangesNothing() {
            SecretRedactor redactor = loaded();

            for (String redacted : alreadyRedacted()) {
                assertEquals(
                        redacted,
                        redactor.redactText(redacted),
                        "a second pass altered already-redacted text");
            }
        }

        @Test
        @DisplayName("a second pass over a live secret yields the same string as the first")
        void aSecondPassOverALiveSecretIsStable() {
            SecretRedactor redactor = loaded();
            String once =
                    redactor.redactText(
                            "Authorization: Bearer ghp_S3cr3tT0k3nExampleValue0123456789ab");

            assertEquals("Authorization: Bearer [REDACTED]", once);
            assertEquals("Authorization: Bearer [REDACTED]", redactor.redactText(once));
        }

        @Test
        @DisplayName("environments and argument arrays are idempotent too")
        void environmentsAndArgvAreIdempotent() {
            SecretRedactor redactor = loaded();

            assertEquals(
                    Map.of("GITHUB_TOKEN", "[REDACTED]"),
                    redactor.redactEnvironment(Map.of("GITHUB_TOKEN", "[REDACTED]")));
            assertEquals(
                    List.of("--password", "[REDACTED]"),
                    redactor.redactArgv(List.of("--password", "[REDACTED]")));
        }
    }

    @Nested
    @DisplayName("Ordinary provenance content survives byte-identical")
    class OrdinaryContentSurvives {

        /** Real content of the kind this record is made of, typed out by hand. */
        private List<String> ordinaryContent() {
            return List.of(
                    "/data/HeLa_1ug_rep1.mzML",
                    "/data/uniprot_human_20260601.fasta",
                    "C:\\data\\HeLa_1ug_rep1.mzML",
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    "d41d8cd98f00b204e9800998ecf8427e",
                    "peptide_mass_tolerance = 20.00",
                    "variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0",
                    "database_name = /data/uniprot_human_20260601.fasta",
                    "decoy_search = 1",
                    "num_threads = 0",
                    "output_percolatorfile = 1",
                    "Comet version 2026.02.2",
                    "Percolator version 3.07.1",
                    "started 2026-08-31T12:34:56Z",
                    "https://limelight.example.org:8443/api/upload",
                    "q-value <= 0.01 for 12345 PSMs",
                    "PWD=/data/run-2026-08-31");
        }

        @Test
        @DisplayName("nothing in it is altered by a loaded redactor")
        void nothingIsAltered() {
            SecretRedactor redactor = loaded();

            for (String content : ordinaryContent()) {
                assertEquals(content, redactor.redactText(content), "ordinary content was altered");
            }
        }

        @Test
        @DisplayName("a whole comet.params block comes through unchanged")
        void aWholeParamsBlockIsUnchanged() {
            String params =
                    "database_name = /data/uniprot_human_20260601.fasta\n"
                            + "decoy_search = 1\n"
                            + "num_threads = 0\n"
                            + "peptide_mass_tolerance = 20.00\n"
                            + "peptide_mass_units = 2\n"
                            + "search_enzyme_number = 1\n"
                            + "allowed_missed_cleavage = 2\n"
                            + "variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0\n"
                            + "add_C_cysteine = 57.021464\n"
                            + "output_percolatorfile = 1\n";

            assertEquals(
                    "database_name = /data/uniprot_human_20260601.fasta\n"
                            + "decoy_search = 1\n"
                            + "num_threads = 0\n"
                            + "peptide_mass_tolerance = 20.00\n"
                            + "peptide_mass_units = 2\n"
                            + "search_enzyme_number = 1\n"
                            + "allowed_missed_cleavage = 2\n"
                            + "variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0\n"
                            + "add_C_cysteine = 57.021464\n"
                            + "output_percolatorfile = 1\n",
                    loaded().redactText(params));
        }

        @Test
        @DisplayName("text with nothing to redact is returned as the very same object")
        void unchangedTextIsTheSameObject() {
            String path = "/data/HeLa_1ug_rep1.mzML";

            assertSame(path, SecretRedactor.patternsOnly().redactText(path));
        }
    }

    @Nested
    @DisplayName("Nulls are rejected by name")
    class NullHandling {

        @Test
        @DisplayName("redactText, redactEnvironment and redactArgv name their own parameter")
        void entryPointsNameTheirParameter() {
            SecretRedactor redactor = SecretRedactor.patternsOnly();

            assertEquals(
                    "text",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> redactor.redactText(deliberateNull()))
                            .getMessage());
            assertEquals(
                    "environment",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> redactor.redactEnvironment(deliberateNull()))
                            .getMessage());
            assertEquals(
                    "argv",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> redactor.redactArgv(deliberateNull()))
                            .getMessage());
        }

        @Test
        @DisplayName("a null environment name and a null environment value are named separately")
        void nullEnvironmentEntriesAreNamed() {
            SecretRedactor redactor = SecretRedactor.patternsOnly();
            Map<String, String> nullName = new LinkedHashMap<>();
            nullName.put(null, "value");
            Map<String, String> nullValue = new LinkedHashMap<>();
            nullValue.put("COMET_PARAMS", null);

            assertEquals(
                    "an environment variable name",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> redactor.redactEnvironment(nullName))
                            .getMessage());
            assertEquals(
                    "the value of the environment variable COMET_PARAMS",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> redactor.redactEnvironment(nullValue))
                            .getMessage());
        }

        @Test
        @DisplayName("a null argument names its index")
        void aNullArgumentNamesItsIndex() {
            SecretRedactor redactor = SecretRedactor.patternsOnly();
            List<String> argv = new ArrayList<>();
            argv.add("/opt/comet/comet");
            argv.add("-P");
            argv.add(null);

            assertEquals(
                    "argv[2]",
                    assertThrows(NullPointerException.class, () -> redactor.redactArgv(argv))
                            .getMessage());
        }

        @Test
        @DisplayName("isSecretName and with() name their parameters")
        void staticEntryPointsNameTheirParameters() {
            assertEquals(
                    "name",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> SecretRedactor.isSecretName(null))
                            .getMessage());
            assertEquals(
                    "registry",
                    assertThrows(NullPointerException.class, () -> SecretRedactor.with(null))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("One instance is safe to share between threads")
    class Sharing {

        @Test
        @DisplayName("sixteen threads redacting the same corpus all get the same answers")
        void sixteenThreadsAgree() throws InterruptedException {
            SecretRedactor redactor = loaded();
            int threads = 16;
            int iterations = 200;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(threads);
            AtomicInteger disagreements = new AtomicInteger();
            List<String> wrong = new ArrayList<>();

            for (int thread = 0; thread < threads; thread++) {
                Thread.ofPlatform()
                        .start(
                                () -> {
                                    try {
                                        start.await();
                                        for (int pass = 0; pass < iterations; pass++) {
                                            check(
                                                    redactor.redactText(
                                                            "Authorization: Bearer"
                                                                    + " ghp_S3cr3tT0k3nExampleValue"
                                                                    + "0123456789ab"),
                                                    "Authorization: Bearer [REDACTED]",
                                                    disagreements,
                                                    wrong);
                                            check(
                                                    redactor.redactText(
                                                            "https://limelight-user:Tr0ub4dor-26-3"
                                                                    + "@limelight.example.org"
                                                                    + "/api/upload"),
                                                    "https://limelight-user:[REDACTED]"
                                                            + "@limelight.example.org/api/upload",
                                                    disagreements,
                                                    wrong);
                                            check(
                                                    redactor.redactText(
                                                            "peptide_mass_tolerance = 20.00"),
                                                    "peptide_mass_tolerance = 20.00",
                                                    disagreements,
                                                    wrong);
                                        }
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                    } finally {
                                        finished.countDown();
                                    }
                                });
            }
            start.countDown();

            assertTrue(
                    finished.await(60, TimeUnit.SECONDS), "the redaction threads did not finish");
            assertEquals(0, disagreements.get(), "concurrent redaction disagreed: " + wrong);
        }

        private void check(
                String actual, String expected, AtomicInteger disagreements, List<String> wrong) {
            if (!expected.equals(actual)) {
                disagreements.incrementAndGet();
                synchronized (wrong) {
                    wrong.add("expected <" + expected + "> but was <" + actual + ">");
                }
            }
        }
    }
}
