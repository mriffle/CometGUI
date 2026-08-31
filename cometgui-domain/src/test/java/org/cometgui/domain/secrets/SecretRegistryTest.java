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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SecretRegistry}.
 *
 * <p><strong>Where every expected value in this file came from.</strong> Every expected string,
 * every expected number and every expected message below was typed out by hand. Nothing here asks
 * {@link SecretRegistry} or {@link SecretRedactor} what it produced and then asserts that it
 * produced it: an expectation computed by the code under test agrees with that code by construction
 * and cannot fail, however wrong the code is. In particular the marker is written out as the
 * literal {@code [REDACTED]} rather than referenced through {@link
 * SecretRedactor#REDACTION_MARKER}, so that changing the constant breaks these tests instead of
 * silently moving them.
 */
class SecretRegistryTest {

    // -----------------------------------------------------------------------------------------
    // Hand-typed fixtures.  These are the phase-04 seeded corpus values used by later units.
    // -----------------------------------------------------------------------------------------

    /** Exactly eight characters, counted by hand: a b c d e f g h. */
    private static final String EIGHT_CHARACTERS = "abcdefgh";

    /** Exactly seven characters, counted by hand: a b c d e f g. */
    private static final String SEVEN_CHARACTERS = "abcdefg";

    /** A seeded-corpus password, 27 characters. */
    private static final String PASSWORD = "hunter2-not-a-real-password";

    /** A seeded-corpus API key, 27 characters. */
    private static final String API_KEY = "ll_live_9f8e7d6c5b4a39281706";

    /** A seeded-corpus token, 24 characters. */
    private static final String TOKEN = "tok_live_abcdef0123456789";

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * <p>See the identical helper in {@code SecretRedactorTest}: proving that a method rejects
     * {@code null} means passing it {@code null}, and SpotBugs at effort Max reports the null
     * literal rather than the defect. The repository fixes findings in code rather than filtering
     * them, so the null arrives through a value.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    @Nested
    @DisplayName("The minimum length is a documented, enforced floor")
    class MinimumLength {

        @Test
        @DisplayName("is eight characters")
        void isEightCharacters() {
            assertEquals(8, SecretRegistry.MINIMUM_SECRET_LENGTH);
        }

        @Test
        @DisplayName("accepts a value of exactly the minimum length")
        void acceptsExactlyTheMinimum() {
            SecretRegistry registry = SecretRegistry.of(EIGHT_CHARACTERS);

            assertEquals(1, registry.size());
            assertEquals(
                    "path /tmp/[REDACTED]/run",
                    SecretRedactor.with(registry).redactText("path /tmp/abcdefgh/run"));
        }

        @Test
        @DisplayName("refuses a value one character short, by length and not by name")
        void refusesOneCharacterShort() {
            SecretTooShortException refused =
                    assertThrows(
                            SecretTooShortException.class,
                            () -> SecretRegistry.of(SEVEN_CHARACTERS));

            assertEquals(7, refused.offeredLength());
            assertEquals(
                    "a registered secret must be at least 8 characters long, but the value"
                            + " offered was 7 characters long; the value itself is deliberately"
                            + " not named here",
                    refused.getMessage());
        }

        @Test
        @DisplayName("never names the refused value in the message")
        void neverNamesTheRefusedValue() {
            SecretTooShortException refused =
                    assertThrows(SecretTooShortException.class, () -> SecretRegistry.of("swordf"));

            assertFalse(
                    refused.getMessage().contains("swordf"),
                    "the rejection message disclosed the value it rejected: "
                            + refused.getMessage());
            assertEquals(6, refused.offeredLength());
        }

        @Test
        @DisplayName("reports a short run of spaces as too short, not as blank")
        void shortWhitespaceIsTooShort() {
            SecretTooShortException refused =
                    assertThrows(SecretTooShortException.class, () -> SecretRegistry.of("   "));

            assertEquals(3, refused.offeredLength());
        }

        @Test
        @DisplayName("refuses a long run of whitespace as blank")
        void longWhitespaceIsBlank() {
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> SecretRegistry.of("            "));

            assertEquals(
                    "a registered secret must not consist only of whitespace",
                    refused.getMessage());
        }
    }

    @Nested
    @DisplayName("The registry does not print what it holds")
    class DoesNotLeak {

        @Test
        @DisplayName("the empty registry describes itself by count")
        void emptyDescribesItselfByCount() {
            assertEquals("SecretRegistry[secretCount=0]", SecretRegistry.empty().toString());
            assertEquals(0, SecretRegistry.empty().size());
        }

        @Test
        @DisplayName("a populated registry describes itself by count and nothing else")
        void populatedDescribesItselfByCount() {
            SecretRegistry registry = SecretRegistry.of(PASSWORD, API_KEY, TOKEN);

            assertEquals("SecretRegistry[secretCount=3]", registry.toString());
        }

        @Test
        @DisplayName("no registered value appears anywhere in the description")
        void noValueAppearsInTheDescription() {
            SecretRegistry registry = SecretRegistry.of(PASSWORD, API_KEY, TOKEN);
            String description = registry.toString();

            for (String secret : List.of(PASSWORD, API_KEY, TOKEN)) {
                assertFalse(
                        description.contains(secret),
                        "SecretRegistry.toString() disclosed a registered secret: " + description);
            }
        }

        @Test
        @DisplayName("the redactor's description discloses nothing either")
        void theRedactorDescribesItselfByCount() {
            SecretRedactor redactor =
                    SecretRedactor.with(SecretRegistry.of(PASSWORD, API_KEY, TOKEN));

            assertEquals("SecretRedactor[SecretRegistry[secretCount=3]]", redactor.toString());
        }
    }

    @Nested
    @DisplayName("Building a registry")
    class Building {

        @Test
        @DisplayName("empty() is a shared instance and holds nothing")
        void emptyIsShared() {
            assertSame(SecretRegistry.empty(), SecretRegistry.empty());
            assertEquals(0, SecretRegistry.empty().size());
        }

        @Test
        @DisplayName("of() with no values holds nothing")
        void ofNothingHoldsNothing() {
            assertEquals(0, SecretRegistry.of().size());
        }

        @Test
        @DisplayName("copyOf takes any collection")
        void copyOfTakesACollection() {
            List<String> values = new ArrayList<>(List.of(PASSWORD, API_KEY));

            SecretRegistry registry = SecretRegistry.copyOf(values);
            values.clear();

            assertEquals(2, registry.size());
            assertEquals(
                    "password is [REDACTED]",
                    SecretRedactor.with(registry).redactText("password is " + PASSWORD));
        }

        @Test
        @DisplayName("collapses duplicates")
        void collapsesDuplicates() {
            assertEquals(1, SecretRegistry.of(PASSWORD, PASSWORD, PASSWORD).size());
        }

        @Test
        @DisplayName("with() returns a new registry and leaves the original alone")
        void withReturnsANewRegistry() {
            SecretRegistry first = SecretRegistry.of(PASSWORD);

            SecretRegistry second = first.with(API_KEY);

            assertNotSame(first, second);
            assertEquals(1, first.size());
            assertEquals(2, second.size());
            assertEquals(
                    "key is " + API_KEY,
                    SecretRedactor.with(first).redactText("key is " + API_KEY),
                    "the original registry learned a value it was never given");
            assertEquals(
                    "key is [REDACTED]",
                    SecretRedactor.with(second).redactText("key is " + API_KEY));
        }

        @Test
        @DisplayName("withSecret on the redactor does the same")
        void withSecretOnTheRedactor() {
            SecretRedactor redactor = SecretRedactor.patternsOnly().withSecret(PASSWORD);

            assertEquals(1, redactor.registry().size());
            assertEquals("pw [REDACTED] done", redactor.redactText("pw " + PASSWORD + " done"));
            assertEquals(
                    "pw " + PASSWORD + " done",
                    SecretRedactor.patternsOnly().redactText("pw " + PASSWORD + " done"),
                    "patternsOnly() acquired a registered value from somewhere");
        }

        @Test
        @DisplayName("rejects a null collection, a null array and a null value, by name")
        void rejectsNulls() {
            assertEquals(
                    "values",
                    assertThrows(NullPointerException.class, () -> SecretRegistry.copyOf(null))
                            .getMessage());
            assertEquals(
                    "values",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> SecretRegistry.of((String[]) null))
                            .getMessage());
            List<String> withANull = new ArrayList<>();
            withANull.add(null);
            assertEquals(
                    "secret",
                    assertThrows(NullPointerException.class, () -> SecretRegistry.copyOf(withANull))
                            .getMessage());
            assertEquals(
                    "secret",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            assertNotNull(
                                                    SecretRegistry.of(PASSWORD)
                                                            .with(deliberateNull())))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("Longest first, so a longer secret cannot be left half-redacted")
    class ReplacementOrder {

        @Test
        @DisplayName("replaces the longer of two overlapping secrets first")
        void replacesTheLongerFirst() {
            SecretRedactor redactor =
                    SecretRedactor.with(SecretRegistry.of("abcdefgh", "abcdefghij"));

            assertEquals("value=[REDACTED]", redactor.redactText("value=abcdefghij"));
        }

        @Test
        @DisplayName("two equal-length secrets that overlap are applied in alphabetical order")
        void equalLengthOverlappingSecretsHaveAFixedOrder() {
            // AAAABBBBCCCC contains both secrets, and they overlap on BBBB, so whichever is
            // replaced first destroys the other's occurrence and the two orders give different
            // documents.  Only the alphabetical tie-break makes the answer the same either way;
            // without it the output depends on the order the credentials happened to be
            // registered in, which is untestable and different on every run.
            SecretRedactor oneWay = SecretRedactor.with(SecretRegistry.of("AAAABBBB", "BBBBCCCC"));
            SecretRedactor theOther =
                    SecretRedactor.with(SecretRegistry.of("BBBBCCCC", "AAAABBBB"));

            assertEquals("value=[REDACTED]CCCC", oneWay.redactText("value=AAAABBBBCCCC"));
            assertEquals("value=[REDACTED]CCCC", theOther.redactText("value=AAAABBBBCCCC"));
        }

        @Test
        @DisplayName("does not depend on the order the values were registered in")
        void doesNotDependOnRegistrationOrder() {
            SecretRedactor oneWay =
                    SecretRedactor.with(SecretRegistry.of("abcdefghij", "abcdefgh"));
            SecretRedactor theOther =
                    SecretRedactor.with(SecretRegistry.of("abcdefgh", "abcdefghij"));

            assertEquals("value=[REDACTED]", oneWay.redactText("value=abcdefghij"));
            assertEquals("value=[REDACTED]", theOther.redactText("value=abcdefghij"));
        }
    }
}
