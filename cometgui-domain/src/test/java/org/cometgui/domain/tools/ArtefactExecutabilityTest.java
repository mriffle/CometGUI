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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link ArtefactExecutability}.
 *
 * <p>{@code D-004} is the whole subject: on Apple silicon the only XML-capable macOS Percolator is
 * an x86-64 build, so {@code macos-aarch64} running a {@code macos-x86-64} artefact must be
 * eligible, and nothing else may be. The table below therefore grades every pair of platforms the
 * product knows -- thirty-six of them -- and requires exactly one to be translated.
 */
class ArtefactExecutabilityTest {

    private static HostPlatform platform(String id) {
        return HostPlatform.fromId(id);
    }

    @Test
    @DisplayName("the three verdicts are the three that exist")
    void theThreeVerdictsArePinned() {
        List<String> names = new ArrayList<>();
        for (ArtefactExecutability verdict : ArtefactExecutability.values()) {
            names.add(verdict.name());
        }

        assertEquals(List.of("NATIVE", "TRANSLATED_ROSETTA_2", "INCOMPATIBLE"), names);
    }

    @ParameterizedTest(name = "[{index}] {0} running {1}")
    @CsvSource({
        "linux-x86-64, linux-x86-64",
        "linux-aarch64, linux-aarch64",
        "macos-x86-64, macos-x86-64",
        "macos-aarch64, macos-aarch64",
        "windows-x86-64, windows-x86-64",
        "windows-aarch64, windows-aarch64"
    })
    @DisplayName("a host runs its own platform's artefact natively")
    void samePlatformIsNative(String host, String artefact) {
        assertEquals(
                ArtefactExecutability.NATIVE,
                ArtefactExecutability.of(platform(host), platform(artefact)));
    }

    @Test
    @DisplayName("Apple silicon runs a macOS x86-64 artefact through Rosetta 2")
    void appleSiliconTranslatesMacOsX8664() {
        assertEquals(
                ArtefactExecutability.TRANSLATED_ROSETTA_2,
                ArtefactExecutability.of(platform("macos-aarch64"), platform("macos-x86-64")));
    }

    @Test
    @DisplayName("Rosetta 2 translates in one direction only")
    void translationDoesNotRunBackwards() {
        assertEquals(
                ArtefactExecutability.INCOMPATIBLE,
                ArtefactExecutability.of(platform("macos-x86-64"), platform("macos-aarch64")));
    }

    @ParameterizedTest(name = "[{index}] {0} cannot run {1}")
    @CsvSource({
        "linux-x86-64, windows-x86-64",
        "linux-x86-64, windows-aarch64",
        "linux-x86-64, macos-x86-64",
        "linux-x86-64, linux-aarch64",
        "linux-aarch64, linux-x86-64",
        "windows-x86-64, linux-x86-64",
        "windows-x86-64, macos-x86-64",
        "windows-aarch64, windows-x86-64",
        "macos-aarch64, linux-x86-64",
        "macos-aarch64, windows-x86-64",
        "macos-x86-64, windows-x86-64",
        "macos-x86-64, linux-x86-64"
    })
    @DisplayName("nothing else translates: a different operating system or a wider architecture")
    void everythingElseIsIncompatible(String host, String artefact) {
        assertEquals(
                ArtefactExecutability.INCOMPATIBLE,
                ArtefactExecutability.of(platform(host), platform(artefact)));
    }

    @Test
    @DisplayName("across every pair of platforms, exactly one is translated")
    void exactlyOnePairIsTranslated() {
        List<String> translated = new ArrayList<>();
        List<String> nativePairs = new ArrayList<>();
        for (HostOperatingSystem hostOs : HostOperatingSystem.values()) {
            for (HostArchitecture hostArch : HostArchitecture.values()) {
                for (HostOperatingSystem artefactOs : HostOperatingSystem.values()) {
                    for (HostArchitecture artefactArch : HostArchitecture.values()) {
                        HostPlatform host = new HostPlatform(hostOs, hostArch);
                        HostPlatform artefact = new HostPlatform(artefactOs, artefactArch);
                        ArtefactExecutability verdict = ArtefactExecutability.of(host, artefact);
                        if (verdict == ArtefactExecutability.TRANSLATED_ROSETTA_2) {
                            translated.add(host.id() + " runs " + artefact.id());
                        } else if (verdict == ArtefactExecutability.NATIVE) {
                            nativePairs.add(host.id() + " runs " + artefact.id());
                        }
                    }
                }
            }
        }

        assertAll(
                () ->
                        assertEquals(
                                List.of("macos-aarch64 runs macos-x86-64"),
                                translated,
                                "Rosetta 2 must be the only translation the product claims"),
                () -> assertEquals(6, nativePairs.size(), "one native pair per platform"));
    }

    @Test
    @DisplayName("a runnable verdict is anything but incompatible")
    void runnability() {
        assertAll(
                () -> assertTrue(ArtefactExecutability.NATIVE.isRunnable()),
                () -> assertTrue(ArtefactExecutability.TRANSLATED_ROSETTA_2.isRunnable()),
                () -> assertFalse(ArtefactExecutability.INCOMPATIBLE.isRunnable()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("everyPlatform")
    @DisplayName(
            "a null platform is rejected by name, whichever real platform is on the other side")
    void nullPlatformsAreRejectedByName(HostPlatform real) {
        /*
         * Graded against every platform on the other side rather than one. The null checks do not
         * depend on the other argument, so pinning it would leave that axis untested -- the shape
         * that let a blank-note rule be switched off for a single enum constant in
         * DeclaredCapability and still pass 108 tests. It also proves neither null quietly becomes
         * INCOMPATIBLE, which would read as a legitimate verdict.
         */
        HostPlatform absent = Nulls.of(HostPlatform.class);

        assertAll(
                () ->
                        assertEquals(
                                "host",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> ArtefactExecutability.of(absent, real))
                                        .getMessage(),
                                "artefact " + real.id()),
                () ->
                        assertEquals(
                                "artefactPlatform",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> ArtefactExecutability.of(real, absent))
                                        .getMessage(),
                                "host " + real.id()));
    }

    /**
     * Every platform the product knows, built from the two enums rather than listed.
     *
     * @return the six operating-system and architecture pairs
     */
    static Stream<HostPlatform> everyPlatform() {
        List<HostPlatform> platforms = new ArrayList<>();
        for (HostOperatingSystem operatingSystem : HostOperatingSystem.values()) {
            for (HostArchitecture architecture : HostArchitecture.values()) {
                platforms.add(new HostPlatform(operatingSystem, architecture));
            }
        }
        return platforms.stream();
    }
}
