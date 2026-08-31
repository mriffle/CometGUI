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

package org.cometgui.provenance.json;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every expected string in this file is hand-typed.
 *
 * <p>None of them was produced by running {@link CanonicalTimestamp} and copying its answer. The
 * instants are written as ISO text and the expected renderings are written as ISO text, and the two
 * were derived independently from what the format is specified to be: UTC, {@code
 * uuuu-MM-ddTHH:mm:ss.SSSZ}, truncated to milliseconds.
 */
class CanonicalTimestampTest {

    /** A locale whose numbering system is Thai digits, which are not ASCII. */
    private static final Locale THAI_DIGITS = Locale.forLanguageTag("th-TH-u-nu-thai");

    /** A locale whose numbering system is Arabic-Indic digits, which are not ASCII. */
    private static final Locale ARABIC_DIGITS = Locale.forLanguageTag("ar-EG-u-nu-arab");

    /** Turkish, whose dotless lower-case i breaks careless case folding. */
    private static final Locale TURKISH = Locale.of("tr", "TR");

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * <p>The same idiom, and the same reason, as {@code SecretRedactorTest.deliberateNull}:
     * SpotBugs at effort Max reports a null <em>literal</em> handed to a parameter that is
     * dereferenced on every path as {@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS}, which is the
     * test's own purpose reported as a defect. The argument that arrives is exactly as null as
     * before.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    @Nested
    @DisplayName("The shape")
    class Shape {

        @Test
        @DisplayName("is three fractional digits even when the instant has no nanoseconds")
        void isThreeDigitsForAWholeSecond() {
            assertEquals(
                    "2026-08-31T09:48:00.000Z",
                    CanonicalTimestamp.utcMillis(Instant.parse("2026-08-31T09:48:00Z")));
        }

        @Test
        @DisplayName("is the same shape for a whole second and for a fractional one")
        void isTheSameShapeWhateverTheClockDid() {
            String whole = CanonicalTimestamp.utcMillis(Instant.parse("2026-08-31T09:48:00Z"));
            String fractional =
                    CanonicalTimestamp.utcMillis(Instant.parse("2026-08-31T09:48:00.250Z"));

            // The property the class exists for, asserted as a property and not only as two
            // literals: a reader must not have to accept two grammars because a clock ticked.
            assertAll(
                    () -> assertEquals(24, whole.length()),
                    () -> assertEquals(24, fractional.length()),
                    () -> assertEquals('.', whole.charAt(19)),
                    () -> assertEquals('.', fractional.charAt(19)),
                    () -> assertEquals('Z', whole.charAt(23)),
                    () -> assertEquals('Z', fractional.charAt(23)));
        }

        @Test
        @DisplayName("truncates towards the past rather than rounding")
        void truncatesRatherThanRounds() {
            // .250999999 is nine tenths of a millisecond past .250.  Rounding would give .251 and
            // would record a moment that never happened; truncation is what the class documents.
            assertAll(
                    () ->
                            assertEquals(
                                    "2026-08-31T09:14:00.250Z",
                                    CanonicalTimestamp.utcMillis(
                                            Instant.parse("2026-08-31T09:14:00.250999999Z"))),
                    () ->
                            assertEquals(
                                    "2026-08-31T09:14:00.999Z",
                                    CanonicalTimestamp.utcMillis(
                                            Instant.parse("2026-08-31T09:14:00.999999999Z"))),
                    () ->
                            assertEquals(
                                    "2026-08-31T09:14:00.000Z",
                                    CanonicalTimestamp.utcMillis(
                                            Instant.parse("2026-08-31T09:14:00.000999999Z"))));
        }

        @Test
        @DisplayName("renders the epoch and the second before it")
        void rendersTheEpochAndTheSecondBeforeIt() {
            assertAll(
                    () ->
                            assertEquals(
                                    "1970-01-01T00:00:00.000Z",
                                    CanonicalTimestamp.utcMillis(Instant.EPOCH)),
                    () ->
                            assertEquals(
                                    "1969-12-31T23:59:59.000Z",
                                    CanonicalTimestamp.utcMillis(Instant.ofEpochSecond(-1L))),
                    () ->
                            assertEquals(
                                    "1969-12-31T23:59:59.500Z",
                                    CanonicalTimestamp.utcMillis(
                                            Instant.ofEpochSecond(-1L, 500_000_000L))));
        }

        @Test
        @DisplayName("names its own pattern, so the schema documentation quotes one string")
        void namesItsOwnPattern() {
            assertEquals("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", CanonicalTimestamp.PATTERN);
        }
    }

    @Nested
    @DisplayName("The zone")
    class Zone {

        @Test
        @DisplayName("is UTC even when the JVM default zone is fourteen hours away")
        void isUtcWhateverTheDefaultZoneIs() {
            TimeZone originalZone = TimeZone.getDefault();
            try {
                // Pacific/Kiritimati is UTC+14: an instant at 09:14 UTC is 23:14 the same day
                // there, so a writer that used the default zone would render a different hour and
                // no Z at all.
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

                assertEquals(
                        "2026-08-31T09:14:00.250Z",
                        CanonicalTimestamp.utcMillis(Instant.parse("2026-08-31T09:14:00.250Z")));
            } finally {
                TimeZone.setDefault(originalZone);
            }
        }

        @Test
        @DisplayName("is pinned into the shared formatter, not read from the JVM when it loads")
        void isPinnedIntoTheSharedFormatter() throws ReflectiveOperationException {
            // THE TWO TESTS AROUND THIS ONE CANNOT SEE ONE PARTICULAR DEFECT, AND THAT IS WHY THIS
            // EXISTS.  The formatter is a static final field, so a version that read
            // ZoneId.systemDefault() in the static INITIALISER is fixed before any test can call
            // TimeZone.setDefault -- and on a host whose default zone is already UTC, which is
            // every host with no TZ in its environment, it renders exactly the right string.  It
            // was injected to check: all 79 tests passed.  Reading the field is the only way to
            // tell "pinned to UTC" from "happened to load on a UTC machine", so the field is read.
            Field field = CanonicalTimestamp.class.getDeclaredField("UTC_MILLIS");
            field.setAccessible(true);
            DateTimeFormatter formatter = (DateTimeFormatter) field.get(null);

            assertAll(
                    // ZoneOffset.UTC, not ZoneId.of("UTC"): TimeZone.getDefault().toZoneId()
                    // returns the latter on a UTC host, and the two are not equal.
                    () -> assertEquals(ZoneOffset.UTC, formatter.getZone()),
                    () -> assertEquals(Locale.ROOT, formatter.getLocale()),
                    () -> assertEquals(DecimalStyle.STANDARD, formatter.getDecimalStyle()));
        }

        @Test
        @DisplayName("is UTC even when the default zone is behind and would change the date")
        void isUtcEvenWhenTheDefaultZoneWouldChangeTheDate() {
            TimeZone originalZone = TimeZone.getDefault();
            try {
                // Pacific/Midway is UTC-11: 09:14 UTC is 22:14 on the PREVIOUS day there.
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"));

                assertEquals(
                        "2026-08-31T09:14:00.250Z",
                        CanonicalTimestamp.utcMillis(Instant.parse("2026-08-31T09:14:00.250Z")));
            } finally {
                TimeZone.setDefault(originalZone);
            }
        }
    }

    @Nested
    @DisplayName("The digits")
    class Digits {

        @Test
        @DisplayName("stay ASCII under a Thai-digit, an Arabic-digit and a Turkish default locale")
        void stayAsciiUnderHostileDefaultLocales() {
            Locale originalDefault = Locale.getDefault();
            Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
            try {
                Instant moment = Instant.parse("2026-08-31T09:14:00.250Z");

                Locale.setDefault(THAI_DIGITS);
                String thai = CanonicalTimestamp.utcMillis(moment);
                Locale.setDefault(ARABIC_DIGITS);
                String arabic = CanonicalTimestamp.utcMillis(moment);
                Locale.setDefault(TURKISH);
                String turkish = CanonicalTimestamp.utcMillis(moment);

                assertAll(
                        () -> assertEquals("2026-08-31T09:14:00.250Z", thai),
                        () -> assertEquals("2026-08-31T09:14:00.250Z", arabic),
                        () -> assertEquals("2026-08-31T09:14:00.250Z", turkish));
            } finally {
                Locale.setDefault(originalDefault);
                Locale.setDefault(Locale.Category.FORMAT, originalFormat);
            }
        }

        @Test
        @DisplayName("would not be ASCII if the locale reached the formatter, which is the point")
        void aLocaleAwareFormatterWouldNotBeAscii() {
            Locale originalDefault = Locale.getDefault();
            Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
            try {
                Locale.setDefault(THAI_DIGITS);

                // The control that stops the test above from passing vacuously.  If Thai digits
                // were not reachable from a default-locale formatter on this JVM, the assertions
                // above would prove nothing at all.  java.text.NumberFormat is the locale-aware
                // path this project keeps out of the number path, and it does produce them.
                String localeAware = java.text.NumberFormat.getIntegerInstance().format(202L);

                // U+0E52 U+0E50 U+0E52, the Thai digits two, zero, two.  Three digits rather than
                // four so that no grouping separator joins them.
                assertEquals("๒๐๒", localeAware);
            } finally {
                Locale.setDefault(originalDefault);
                Locale.setDefault(Locale.Category.FORMAT, originalFormat);
            }
        }
    }

    @Nested
    @DisplayName("The class itself")
    class TheClassItself {

        @Test
        @DisplayName("rejects a null instant, naming the parameter")
        void rejectsANullInstant() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> CanonicalTimestamp.utcMillis(deliberateNull()));

            assertEquals("instant", thrown.getMessage());
        }

        @Test
        @DisplayName("cannot be instantiated, even reflectively")
        void cannotBeInstantiated() throws NoSuchMethodException {
            Constructor<CanonicalTimestamp> constructor =
                    CanonicalTimestamp.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            InvocationTargetException thrown =
                    assertThrows(InvocationTargetException.class, constructor::newInstance);

            assertAll(
                    () -> assertInstanceOf(AssertionError.class, thrown.getCause()),
                    () ->
                            assertEquals(
                                    "CanonicalTimestamp is a utility class and is never"
                                            + " instantiated",
                                    thrown.getCause().getMessage()));
        }
    }
}
