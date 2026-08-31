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

package org.cometgui.provenance.manifest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ApplicationRecord}.
 *
 * <p><strong>The locale group is a test of {@code R-PROV-04}, not a test of a getter.</strong> The
 * requirement is that "the recorded locale shall be the JVM default locale in effect during the
 * run". Asserting that a record hands back the locale it was constructed with proves nothing about
 * that; what proves it is changing the JVM default to a locale nobody's machine is set to, calling
 * the factory the product calls, and finding that locale in the result. Turkish is chosen
 * deliberately -- it is the locale whose lower-casing rules break naive string handling -- and the
 * default is restored in a {@code finally} so that no later test inherits it.
 *
 * <p>The <em>where each fact lands</em> group goes through the package-private seam with four
 * hand-typed, obviously synthetic property values. Without it, the only available assertion would
 * compare the record against the same {@code System.getProperty} calls the production code makes,
 * and a defect that swapped {@code os.name} with {@code os.version} would agree with itself.
 */
class ApplicationRecordTest {

    private static final Locale TURKISH = Locale.of("tr", "TR");
    private static final ZoneId CHATHAM = ZoneId.of("Pacific/Chatham");

    private static UnaryOperator<String> properties(Map<String, String> values) {
        return values::get;
    }

    private static Map<String, String> completeProperties() {
        Map<String, String> values = new HashMap<>();
        values.put("os.name", "Frobnitz OS");
        values.put("os.version", "9.4-alpha");
        values.put("os.arch", "sparc64");
        values.put("java.version", "25.0.4.1");
        return values;
    }

    @Nested
    @DisplayName("R-PROV-04: the JVM default locale in effect during the run")
    class TheRecordedLocale {

        @Test
        @DisplayName("the factory records the default locale that was set when it was called")
        void theFactoryRecordsTheDefaultLocaleInEffect() {
            Locale originalLocale = Locale.getDefault();
            try {
                Locale.setDefault(TURKISH);

                ApplicationRecord captured = ApplicationRecord.capture("0.1.0", "e97d863");

                assertAll(
                        () -> assertEquals(TURKISH, captured.locale()),
                        () -> assertEquals("tr", captured.locale().getLanguage()),
                        () -> assertEquals("TR", captured.locale().getCountry()),
                        () -> assertNotEquals(Locale.ENGLISH, captured.locale()),
                        () -> assertNotEquals(Locale.US, captured.locale()));
            } finally {
                Locale.setDefault(originalLocale);
            }
        }

        @Test
        @DisplayName("it is read at each call, so two calls under two locales differ")
        void itIsReadAtEachCall() {
            Locale originalLocale = Locale.getDefault();
            try {
                Locale.setDefault(TURKISH);
                ApplicationRecord underTurkish = ApplicationRecord.capture("0.1.0", "e97d863");

                Locale.setDefault(Locale.JAPAN);
                ApplicationRecord underJapanese = ApplicationRecord.capture("0.1.0", "e97d863");

                assertAll(
                        () -> assertEquals(TURKISH, underTurkish.locale()),
                        () -> assertEquals(Locale.JAPAN, underJapanese.locale()),
                        () -> assertNotEquals(underTurkish.locale(), underJapanese.locale()));
            } finally {
                Locale.setDefault(originalLocale);
            }
        }

        @Test
        @DisplayName("the time zone is read at call time too")
        void theTimeZoneIsReadAtCallTime() {
            TimeZone originalZone = TimeZone.getDefault();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));

                ApplicationRecord captured = ApplicationRecord.capture("0.1.0", "e97d863");

                assertAll(
                        () -> assertEquals(CHATHAM, captured.zoneId()),
                        () -> assertEquals("Pacific/Chatham", captured.zoneId().getId()),
                        () -> assertNotEquals(ZoneId.of("UTC"), captured.zoneId()));
            } finally {
                TimeZone.setDefault(originalZone);
            }
        }
    }

    @Nested
    @DisplayName("where each fact lands")
    class Mapping {

        @Test
        @DisplayName("each system property lands in its own component, none swapped")
        void eachPropertyLandsInItsOwnComponent() {
            ApplicationRecord captured =
                    ApplicationRecord.capture(
                            "1.2.3",
                            "deadbeef",
                            properties(completeProperties()),
                            TURKISH,
                            CHATHAM);

            assertAll(
                    () -> assertEquals("1.2.3", captured.cometGuiVersion()),
                    () -> assertEquals("deadbeef", captured.buildIdentifier()),
                    () -> assertEquals("Frobnitz OS", captured.osName()),
                    () -> assertEquals("9.4-alpha", captured.osVersion()),
                    () -> assertEquals("sparc64", captured.architecture()),
                    () -> assertEquals("25.0.4.1", captured.jvmVersion()),
                    () -> assertSame(TURKISH, captured.locale()),
                    () -> assertSame(CHATHAM, captured.zoneId()));
        }

        @Test
        @DisplayName("the public factory really reads this JVM, not a constant")
        void thePublicFactoryReadsThisJvm() {
            ApplicationRecord captured = ApplicationRecord.capture("0.1.0", "e97d863");

            assertAll(
                    () -> assertEquals(System.getProperty("os.name"), captured.osName()),
                    () -> assertEquals(System.getProperty("os.version"), captured.osVersion()),
                    () -> assertEquals(System.getProperty("os.arch"), captured.architecture()),
                    () -> assertEquals(System.getProperty("java.version"), captured.jvmVersion()),
                    () -> assertEquals("0.1.0", captured.cometGuiVersion()),
                    () -> assertEquals("e97d863", captured.buildIdentifier()));
        }

        @Test
        @DisplayName("a missing system property fails loudly and names the property")
        void aMissingPropertyFailsLoudly() {
            Map<String, String> missingArch = completeProperties();
            missingArch.remove("os.arch");

            IllegalStateException thrown =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    ApplicationRecord.capture(
                                            "1.2.3",
                                            "deadbeef",
                                            properties(missingArch),
                                            TURKISH,
                                            CHATHAM));

            assertEquals(
                    "the system property \"os.arch\" is not set, so this JVM cannot describe"
                            + " itself",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("a missing os.name is reported as os.name, not as the next property")
        void aMissingOsNameIsReportedAsOsName() {
            Map<String, String> missingName = completeProperties();
            missingName.remove("os.name");

            IllegalStateException thrown =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    ApplicationRecord.capture(
                                            "1.2.3",
                                            "deadbeef",
                                            properties(missingName),
                                            TURKISH,
                                            CHATHAM));

            assertEquals(
                    "the system property \"os.name\" is not set, so this JVM cannot describe"
                            + " itself",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("a null property lookup is rejected, naming the argument")
        void aNullPropertyLookupIsRejected() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () ->
                                    ApplicationRecord.capture(
                                            "1.2.3", "deadbeef", null, TURKISH, CHATHAM));

            assertEquals("systemProperty", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Rejections {

        @Test
        @DisplayName("every text component must carry information, and the message names it")
        void everyTextComponentMustCarryInformation() {
            assertAll(
                    () ->
                            assertEquals(
                                    "cometGuiVersion must not be blank, but was: \"\"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new ApplicationRecord(
                                                                    "", "e97d863", "Linux", "6.8.0",
                                                                    "amd64", "25", TURKISH,
                                                                    CHATHAM))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "buildIdentifier must not be blank, but was: \" \"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new ApplicationRecord(
                                                                    "0.1.0", " ", "Linux", "6.8.0",
                                                                    "amd64", "25", TURKISH,
                                                                    CHATHAM))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "osName must not be blank, but was: \"\"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new ApplicationRecord(
                                                                    "0.1.0", "e97d863", "", "6.8.0",
                                                                    "amd64", "25", TURKISH,
                                                                    CHATHAM))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "osVersion must not be blank, but was: \"\"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new ApplicationRecord(
                                                                    "0.1.0", "e97d863", "Linux", "",
                                                                    "amd64", "25", TURKISH,
                                                                    CHATHAM))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "architecture must not be blank, but was: \"\"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new ApplicationRecord(
                                                                    "0.1.0", "e97d863", "Linux",
                                                                    "6.8.0", "", "25", TURKISH,
                                                                    CHATHAM))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "jvmVersion must not be blank, but was: \"\"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new ApplicationRecord(
                                                                    "0.1.0", "e97d863", "Linux",
                                                                    "6.8.0", "amd64", "", TURKISH,
                                                                    CHATHAM))
                                            .getMessage()));
        }

        @Test
        @DisplayName("the locale and the time zone are required, and the message names them")
        void theLocaleAndZoneAreRequired() {
            assertAll(
                    () ->
                            assertEquals(
                                    "locale",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ApplicationRecord(
                                                                    "0.1.0", "e97d863", "Linux",
                                                                    "6.8.0", "amd64", "25", null,
                                                                    CHATHAM))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "zoneId",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ApplicationRecord(
                                                                    "0.1.0", "e97d863", "Linux",
                                                                    "6.8.0", "amd64", "25", TURKISH,
                                                                    null))
                                            .getMessage()));
        }
    }
}
