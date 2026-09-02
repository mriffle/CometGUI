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

package org.cometgui.domain.tools;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link HostPlatform}.
 *
 * <p>The {@code os.name}/{@code os.arch} pairs below are pinned literals: they are what a JVM
 * actually reports on each platform in the specification's matrix, and the point of the detector
 * taking them as arguments is that all five are reachable from a test on any machine. Nothing here
 * reads a system property, so this file behaves identically on the Linux host it runs on and on the
 * Windows and macOS machines this project has never had.
 */
class HostPlatformTest {

    @Nested
    @DisplayName("of(osName, osArch)")
    class Detection {

        @ParameterizedTest(name = "[{index}] os.name={0} os.arch={1} -> {2}")
        @CsvSource({
            "Linux, amd64, linux-x86-64",
            "Linux, x86_64, linux-x86-64",
            "Linux, aarch64, linux-aarch64",
            "Mac OS X, x86_64, macos-x86-64",
            "Mac OS X, aarch64, macos-aarch64",
            "Mac OS X, arm64, macos-aarch64",
            "Darwin, aarch64, macos-aarch64",
            "Windows 10, amd64, windows-x86-64",
            "Windows 11, amd64, windows-x86-64",
            "Windows Server 2022, x86_64, windows-x86-64",
            "Windows 11, aarch64, windows-aarch64"
        })
        @DisplayName("the values a JVM really reports resolve to the platform they mean")
        void realSystemPropertiesResolve(String osName, String osArch, String expectedId) {
            Optional<HostPlatform> detected = HostPlatform.of(osName, osArch);

            assertAll(
                    () -> assertTrue(detected.isPresent(), osName + " / " + osArch),
                    () -> assertEquals(expectedId, detected.orElseThrow().id()));
        }

        @ParameterizedTest(name = "[{index}] os.name={0} os.arch={1} -> {2}")
        @CsvSource({
            "Linux, amd64, LINUX",
            "Mac OS X, aarch64, MACOS",
            "Windows 11, amd64, WINDOWS"
        })
        @DisplayName("the five tier-1 pairs land on the right operating system constant")
        void theOperatingSystemIsRight(String osName, String osArch, String expected) {
            assertEquals(
                    HostOperatingSystem.valueOf(expected),
                    HostPlatform.of(osName, osArch).orElseThrow().operatingSystem());
        }

        @ParameterizedTest(name = "[{index}] os.arch={1} -> {2}")
        @CsvSource({
            "Linux, amd64, X86_64",
            "Linux, x86_64, X86_64",
            "Linux, x64, X86_64",
            "Linux, aarch64, AARCH64",
            "Mac OS X, arm64, AARCH64"
        })
        @DisplayName("every architecture spelling a JVM uses lands on the right constant")
        void theArchitectureIsRight(String osName, String osArch, String expected) {
            assertEquals(
                    HostArchitecture.valueOf(expected),
                    HostPlatform.of(osName, osArch).orElseThrow().architecture());
        }

        @ParameterizedTest(name = "[{index}] os.name={0} os.arch={1}")
        @CsvSource({
            "FreeBSD, amd64",
            "SunOS, sparcv9",
            "AIX, ppc64",
            "Linux, i386",
            "Linux, ppc64le",
            "Linux, armv7l",
            "Windows 11, ia64",
            "'', amd64",
            "Linux, ''",
            "HP-UX, itanium"
        })
        @DisplayName("an unrecognised pair is empty, never a guess")
        void unrecognisedPairsAreEmpty(String osName, String osArch) {
            assertEquals(
                    Optional.empty(),
                    HostPlatform.of(osName, osArch),
                    osName + " / " + osArch + " must not be guessed at");
        }

        @Test
        @DisplayName("surrounding whitespace and letter case do not change the answer")
        void detectionIsForgivingAboutFormNotAboutValue() {
            assertAll(
                    () ->
                            assertEquals(
                                    "linux-x86-64",
                                    HostPlatform.of("  LINUX  ", " AMD64 ").orElseThrow().id()),
                    () ->
                            assertEquals(
                                    "macos-aarch64",
                                    HostPlatform.of("mac os x", "ARM64").orElseThrow().id()));
        }

        @Test
        @DisplayName("a null property value is rejected by name, not treated as unrecognised")
        void nullPropertiesAreRejectedByName() {
            assertAll(
                    () ->
                            assertEquals(
                                    "osName",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            HostPlatform.of(
                                                                    Nulls.of(String.class),
                                                                    "amd64"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "osArch",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            HostPlatform.of(
                                                                    "Linux",
                                                                    Nulls.of(String.class)))
                                            .getMessage()));
        }
    }

    @Nested
    @DisplayName("identifiers")
    class Identifiers {

        @ParameterizedTest(name = "[{index}] {0} {1} -> {2}")
        @CsvSource({
            "LINUX, X86_64, linux-x86-64",
            "LINUX, AARCH64, linux-aarch64",
            "MACOS, X86_64, macos-x86-64",
            "MACOS, AARCH64, macos-aarch64",
            "WINDOWS, X86_64, windows-x86-64",
            "WINDOWS, AARCH64, windows-aarch64"
        })
        @DisplayName("the identifier is the two halves joined by a hyphen")
        void identifiersArePinned(String os, String architecture, String expectedId) {
            HostPlatform platform =
                    new HostPlatform(
                            HostOperatingSystem.valueOf(os),
                            HostArchitecture.valueOf(architecture));

            assertAll(
                    () -> assertEquals(expectedId, platform.id()),
                    () -> assertEquals(expectedId, platform.toString()));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({
            "linux-x86-64, LINUX, X86_64",
            "linux-aarch64, LINUX, AARCH64",
            "macos-x86-64, MACOS, X86_64",
            "macos-aarch64, MACOS, AARCH64",
            "windows-x86-64, WINDOWS, X86_64"
        })
        @DisplayName("a manifest identifier resolves back to the platform it names")
        void identifiersResolve(String id, String os, String architecture) {
            assertEquals(
                    new HostPlatform(
                            HostOperatingSystem.valueOf(os),
                            HostArchitecture.valueOf(architecture)),
                    HostPlatform.fromId(id));
        }

        @Test
        @DisplayName("an identifier with no hyphen is rejected, quoting what was rejected")
        void anIdentifierWithoutAHyphenIsRejected() {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class, () -> HostPlatform.fromId("linux"));

            assertEquals(
                    "not a platform id: \"linux\" (expected an operating system and an"
                            + " architecture joined by a hyphen, such as linux-x86-64)",
                    rejected.getMessage());
        }

        @Test
        @DisplayName("an identifier that begins with the hyphen is rejected by its empty half")
        void aLeadingHyphenIsRejectedByTheEmptyHalf() {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class, () -> HostPlatform.fromId("-x86-64"));

            assertEquals(
                    "no operating system has the id \"\"; expected one of [linux, macos,"
                            + " windows]",
                    rejected.getMessage());
        }

        @Test
        @DisplayName("a JVM spelling in a manifest identifier is rejected by its own half")
        void aJvmSpellingIsRejectedByTheHalfThatIsWrong() {
            assertAll(
                    () ->
                            assertEquals(
                                    "no architecture has the id \"amd64\"; expected one of"
                                            + " [x86-64, aarch64]",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> HostPlatform.fromId("linux-amd64"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "no operating system has the id \"osx\"; expected one of"
                                            + " [linux, macos, windows]",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> HostPlatform.fromId("osx-x86-64"))
                                            .getMessage()));
        }

        @Test
        @DisplayName("a null identifier is rejected as null, not as unknown")
        void nullIsRejectedByName() {
            NullPointerException rejected =
                    assertThrows(
                            NullPointerException.class,
                            () -> HostPlatform.fromId(Nulls.of(String.class)));

            assertEquals("id", rejected.getMessage());
        }

        @Test
        @DisplayName("every detected platform's identifier resolves back to the same platform")
        void detectionAndResolutionAgree() {
            for (HostOperatingSystem operatingSystem : HostOperatingSystem.values()) {
                for (HostArchitecture architecture : HostArchitecture.values()) {
                    HostPlatform platform = new HostPlatform(operatingSystem, architecture);

                    assertEquals(platform, HostPlatform.fromId(platform.id()));
                }
            }
        }
    }

    @Nested
    @DisplayName("value semantics")
    class ValueSemantics {

        @Test
        @DisplayName("a null half is rejected by name")
        void rejectsNullComponents() {
            assertAll(
                    () ->
                            assertEquals(
                                    "operatingSystem",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new HostPlatform(
                                                                    Nulls.of(
                                                                            HostOperatingSystem
                                                                                    .class),
                                                                    HostArchitecture.X86_64))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "architecture",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new HostPlatform(
                                                                    HostOperatingSystem.LINUX,
                                                                    Nulls.of(
                                                                            HostArchitecture
                                                                                    .class)))
                                            .getMessage()));
        }

        @Test
        @DisplayName("two platforms are equal exactly when both halves are")
        void equality() {
            HostPlatform linuxX8664 =
                    new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

            assertAll(
                    () ->
                            assertEquals(
                                    linuxX8664,
                                    new HostPlatform(
                                            HostOperatingSystem.LINUX, HostArchitecture.X86_64)),
                    () ->
                            assertNotEquals(
                                    linuxX8664,
                                    new HostPlatform(
                                            HostOperatingSystem.LINUX, HostArchitecture.AARCH64)),
                    () ->
                            assertNotEquals(
                                    linuxX8664,
                                    new HostPlatform(
                                            HostOperatingSystem.MACOS, HostArchitecture.X86_64)));
        }
    }
}
