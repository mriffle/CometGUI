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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link MinimumHostRequirements}.
 *
 * <p>The values used are the real ones, read with {@code readelf -V} from the artefacts in this
 * phase's mirror: {@code GLIBC_2.34} and {@code GLIBCXX_3.4.29} for the Percolator 3.07.1 portable
 * archive, {@code GLIBC_2.38} and {@code GLIBCXX_3.4.32} for the 3.09 Debian payload, {@code
 * GLIBC_2.14} and {@code GLIBCXX_3.4.21} for the 3.06.5 archive, and the four Visual C++ runtime
 * DLLs the Windows portable zip imports and does not ship.
 *
 * <p><strong>Each rejection is graded with the other three components both absent and both
 * present.</strong> None of the four validations depends on the other fields, so asserting one of
 * them only against an otherwise-empty record would leave that axis untested -- the shape that let
 * a blank-note rule be switched off for a single enum constant in {@link DeclaredCapability}.
 *
 * <p><strong>What this file does not test, deliberately.</strong> There is no "does this host
 * satisfy these requirements" rule here: the record carries the floors and nothing in this module
 * compares them to a host. That comparison, and the exact-equality boundary that decides whether a
 * host with precisely glibc 2.34 is offered Percolator 3.07.1, live in {@code
 * org.cometgui.install.probe.HostRequirementCheck}. {@link #theGlibcFloorIsAVersion()} and {@link
 * #theGlibcxxFloorIsAVersion()} exercise {@link GlibcVersion#isAtLeast} to show the floors are
 * comparable values rather than text; that is phase 02's class, not this record's contract, and it
 * is here as documentation of intent rather than as coverage of this unit.
 */
class MinimumHostRequirementsTest {

    private static final List<String> VISUAL_CPP_RUNTIME =
            List.of("MSVCP140.dll", "VCRUNTIME140.dll", "VCRUNTIME140_1.dll", "VCOMP140.DLL");

    /** The other three components, absent in one context and present in the other. */
    private static final List<Optional<GlibcVersion>> GLIBC_CONTEXTS =
            List.of(Optional.empty(), Optional.of(GlibcVersion.parse("2.34")));

    private static final List<Optional<GlibcVersion>> GLIBCXX_CONTEXTS =
            List.of(Optional.empty(), Optional.of(GlibcVersion.parse("3.4.29")));

    private static final List<Optional<String>> MACOS_CONTEXTS =
            List.of(Optional.empty(), Optional.of("12.7"));

    private static final List<List<String>> LIBRARY_CONTEXTS =
            List.of(List.of(), List.of("MSVCP140.dll"));

    @Test
    @DisplayName("a Linux glibc floor is kept as a comparable version, not as text")
    void theGlibcFloorIsAVersion() {
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(
                        Optional.of(GlibcVersion.parse("2.34")),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());

        assertAll(
                () ->
                        assertEquals(
                                GlibcVersion.of(2, 34, 0),
                                requirements.minimumGlibc().orElseThrow()),
                () ->
                        assertTrue(
                                GlibcVersion.parse("2.36")
                                        .isAtLeast(requirements.minimumGlibc().orElseThrow()),
                                "Debian 12's glibc 2.36 meets the 3.07.1 floor"),
                () ->
                        assertFalse(
                                GlibcVersion.parse("2.36").isAtLeast(GlibcVersion.parse("2.38")),
                                "Debian 12's glibc 2.36 does not meet the 3.09 .deb floor"),
                () -> assertFalse(requirements.isEmpty()));
    }

    @Test
    @DisplayName("the C++ runtime floor is a separate, comparable version of its own")
    void theGlibcxxFloorIsAVersion() {
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(
                        Optional.empty(),
                        Optional.of(GlibcVersion.parse("3.4.29")),
                        Optional.empty(),
                        List.of());

        assertAll(
                () -> assertEquals(Optional.empty(), requirements.minimumGlibc()),
                () ->
                        assertEquals(
                                GlibcVersion.of(3, 4, 29),
                                requirements.minimumGlibcxx().orElseThrow()),
                () ->
                        assertTrue(
                                GlibcVersion.parse("3.4.30")
                                        .isAtLeast(requirements.minimumGlibcxx().orElseThrow()),
                                "Debian 12's GLIBCXX_3.4.30 meets the 3.07.1 C++ floor"),
                () ->
                        assertFalse(
                                GlibcVersion.parse("3.4.30")
                                        .isAtLeast(GlibcVersion.parse("3.4.32")),
                                "Debian 12's GLIBCXX_3.4.30 does not meet the 3.09 .deb C++ floor,"
                                        + " which is the line the loader reports FIRST"),
                () -> assertFalse(requirements.isEmpty()));
    }

    @Test
    @DisplayName("the two Linux floors are independent: 3.09's C++ floor is unmet where glibc's is")
    void theTwoLinuxFloorsAreIndependent() {
        MinimumHostRequirements payload309 =
                new MinimumHostRequirements(
                        Optional.of(GlibcVersion.parse("2.38")),
                        Optional.of(GlibcVersion.parse("3.4.32")),
                        Optional.empty(),
                        List.of());

        assertAll(
                () ->
                        assertEquals(
                                GlibcVersion.of(2, 38, 0), payload309.minimumGlibc().orElseThrow()),
                () ->
                        assertEquals(
                                GlibcVersion.of(3, 4, 32),
                                payload309.minimumGlibcxx().orElseThrow()),
                () ->
                        assertFalse(
                                payload309
                                        .minimumGlibc()
                                        .orElseThrow()
                                        .equals(payload309.minimumGlibcxx().orElseThrow()),
                                "the two floors are different numbers and neither implies the"
                                        + " other"));
    }

    @Test
    @DisplayName("the Windows Visual C++ runtime is declared as required host libraries")
    void theWindowsRuntimeIsDeclared() {
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(
                        Optional.empty(), Optional.empty(), Optional.empty(), VISUAL_CPP_RUNTIME);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "MSVCP140.dll",
                                        "VCRUNTIME140.dll",
                                        "VCRUNTIME140_1.dll",
                                        "VCOMP140.DLL"),
                                requirements.requiredHostLibraries()),
                () -> assertFalse(requirements.isEmpty()));
    }

    @Test
    @DisplayName("a macOS floor is kept as upstream states it")
    void theMacOsFloorIsKept() {
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(
                        Optional.empty(), Optional.empty(), Optional.of(" 12.7 "), List.of());

        assertAll(
                () -> assertEquals("12.7", requirements.minimumMacOsVersion().orElseThrow()),
                () -> assertFalse(requirements.isEmpty()));
    }

    @Test
    @DisplayName("an artefact that declares nothing has empty requirements")
    void noneIsEmpty() {
        MinimumHostRequirements none = MinimumHostRequirements.none();

        assertAll(
                () -> assertTrue(none.isEmpty()),
                () -> assertEquals(Optional.empty(), none.minimumGlibc()),
                () -> assertEquals(Optional.empty(), none.minimumGlibcxx()),
                () -> assertEquals(Optional.empty(), none.minimumMacOsVersion()),
                () -> assertEquals(List.of(), none.requiredHostLibraries()),
                () ->
                        assertEquals(
                                none,
                                new MinimumHostRequirements(
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        List.of())));
    }

    @Test
    @DisplayName("a record is empty only when all four components are")
    void isEmptyIsGradedOnEveryComponent() {
        assertAll(
                () ->
                        assertTrue(
                                new MinimumHostRequirements(
                                                Optional.empty(),
                                                Optional.empty(),
                                                Optional.empty(),
                                                List.of())
                                        .isEmpty()),
                () ->
                        assertFalse(
                                new MinimumHostRequirements(
                                                Optional.of(GlibcVersion.parse("2.34")),
                                                Optional.empty(),
                                                Optional.empty(),
                                                List.of())
                                        .isEmpty(),
                                "a glibc floor alone is a requirement"),
                () ->
                        assertFalse(
                                new MinimumHostRequirements(
                                                Optional.empty(),
                                                Optional.of(GlibcVersion.parse("3.4.29")),
                                                Optional.empty(),
                                                List.of())
                                        .isEmpty(),
                                "a C++ runtime floor alone is a requirement"),
                () ->
                        assertFalse(
                                new MinimumHostRequirements(
                                                Optional.empty(),
                                                Optional.empty(),
                                                Optional.of("12.7"),
                                                List.of())
                                        .isEmpty(),
                                "a macOS floor alone is a requirement"),
                () ->
                        assertFalse(
                                new MinimumHostRequirements(
                                                Optional.empty(),
                                                Optional.empty(),
                                                Optional.empty(),
                                                List.of("MSVCP140.dll"))
                                        .isEmpty(),
                                "a required library alone is a requirement"));
    }

    @Test
    @DisplayName("a library list is stripped and kept in the order it was declared")
    void librariesAreStrippedAndOrdered() {
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Arrays.asList(" VCRUNTIME140.dll ", "MSVCP140.dll"));

        assertEquals(
                List.of("VCRUNTIME140.dll", "MSVCP140.dll"), requirements.requiredHostLibraries());
    }

    @Test
    @DisplayName("the list handed out is a copy, so a later change to the caller's list is unseen")
    void theListIsCopied() {
        List<String> mutable = new ArrayList<>(List.of("MSVCP140.dll"));
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(
                        Optional.empty(), Optional.empty(), Optional.empty(), mutable);

        mutable.add("SOMETHING_ELSE.dll");

        assertAll(
                () -> assertEquals(List.of("MSVCP140.dll"), requirements.requiredHostLibraries()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> requirements.requiredHostLibraries().add("X.dll")));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a present but blank macOS floor is rejected whatever else is declared")
    void aBlankMacOsFloorIsRejected(String blank) {
        List<Executable> assertions = new ArrayList<>();
        for (Optional<GlibcVersion> glibc : GLIBC_CONTEXTS) {
            for (Optional<GlibcVersion> glibcxx : GLIBCXX_CONTEXTS) {
                for (List<String> libraries : LIBRARY_CONTEXTS) {
                    assertions.add(
                            () ->
                                    assertEquals(
                                            "minimumMacOsVersion must not be blank when it is"
                                                    + " present; leave it absent instead",
                                            assertThrows(
                                                            IllegalArgumentException.class,
                                                            () ->
                                                                    new MinimumHostRequirements(
                                                                            glibc,
                                                                            glibcxx,
                                                                            Optional.of(blank),
                                                                            libraries))
                                                    .getMessage(),
                                            "glibc="
                                                    + glibc
                                                    + " glibcxx="
                                                    + glibcxx
                                                    + " libraries="
                                                    + libraries));
                }
            }
        }

        assertAll(assertions);
    }

    @Test
    @DisplayName("a blank or null library is rejected whatever else is declared, naming its slot")
    void aBlankLibraryIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Optional<GlibcVersion> glibc : GLIBC_CONTEXTS) {
            for (Optional<GlibcVersion> glibcxx : GLIBCXX_CONTEXTS) {
                for (Optional<String> macOs : MACOS_CONTEXTS) {
                    String where = context(glibc, glibcxx, macOs);
                    assertions.add(
                            () ->
                                    assertEquals(
                                            "requiredHostLibraries[1] must not be blank",
                                            rejectionOf(
                                                    glibc,
                                                    glibcxx,
                                                    macOs,
                                                    Arrays.asList("MSVCP140.dll", "  ")),
                                            where));
                    assertions.add(
                            () ->
                                    assertEquals(
                                            "requiredHostLibraries[0] must not be null",
                                            rejectionOf(
                                                    glibc,
                                                    glibcxx,
                                                    macOs,
                                                    Arrays.asList(null, "MSVCP140.dll")),
                                            where));
                }
            }
        }

        assertAll(assertions);
    }

    @Test
    @DisplayName("a library named twice is rejected whatever else is declared, quoting it")
    void aDuplicateLibraryIsRejected() {
        List<Executable> assertions = new ArrayList<>();
        for (Optional<GlibcVersion> glibc : GLIBC_CONTEXTS) {
            for (Optional<GlibcVersion> glibcxx : GLIBCXX_CONTEXTS) {
                for (Optional<String> macOs : MACOS_CONTEXTS) {
                    String where = context(glibc, glibcxx, macOs);
                    assertions.add(
                            () ->
                                    assertEquals(
                                            "requiredHostLibraries names \"MSVCP140.dll\" more"
                                                    + " than once",
                                            rejectionOf(
                                                    glibc,
                                                    glibcxx,
                                                    macOs,
                                                    Arrays.asList(
                                                            "MSVCP140.dll", " MSVCP140.dll ")),
                                            where));
                }
            }
        }

        assertAll(assertions);
    }

    private static String rejectionOf(
            Optional<GlibcVersion> glibc,
            Optional<GlibcVersion> glibcxx,
            Optional<String> macOs,
            List<String> libraries) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () -> new MinimumHostRequirements(glibc, glibcxx, macOs, libraries))
                .getMessage();
    }

    private static String context(
            Optional<GlibcVersion> glibc, Optional<GlibcVersion> glibcxx, Optional<String> macOs) {
        return "glibc=" + glibc + " glibcxx=" + glibcxx + " macOs=" + macOs;
    }

    @Test
    @DisplayName("a null part is rejected by name whatever else is declared")
    void nullPartsAreRejectedByName() {
        Optional<GlibcVersion> absentGlibc = Nulls.of(Optional.class);
        Optional<GlibcVersion> absentGlibcxx = Nulls.of(Optional.class);
        Optional<String> absentMacOs = Nulls.of(Optional.class);
        List<String> absentLibraries = Nulls.of(List.class);
        List<Executable> assertions = new ArrayList<>();
        for (Optional<GlibcVersion> glibc : GLIBC_CONTEXTS) {
            for (Optional<GlibcVersion> glibcxx : GLIBCXX_CONTEXTS) {
                for (Optional<String> macOs : MACOS_CONTEXTS) {
                    for (List<String> libraries : LIBRARY_CONTEXTS) {
                        assertions.add(
                                () ->
                                        assertEquals(
                                                "minimumGlibc",
                                                assertThrows(
                                                                NullPointerException.class,
                                                                () ->
                                                                        new MinimumHostRequirements(
                                                                                absentGlibc,
                                                                                glibcxx,
                                                                                macOs,
                                                                                libraries))
                                                        .getMessage()));
                        assertions.add(
                                () ->
                                        assertEquals(
                                                "minimumGlibcxx",
                                                assertThrows(
                                                                NullPointerException.class,
                                                                () ->
                                                                        new MinimumHostRequirements(
                                                                                glibc,
                                                                                absentGlibcxx,
                                                                                macOs,
                                                                                libraries))
                                                        .getMessage()));
                        assertions.add(
                                () ->
                                        assertEquals(
                                                "minimumMacOsVersion",
                                                assertThrows(
                                                                NullPointerException.class,
                                                                () ->
                                                                        new MinimumHostRequirements(
                                                                                glibc,
                                                                                glibcxx,
                                                                                absentMacOs,
                                                                                libraries))
                                                        .getMessage()));
                        assertions.add(
                                () ->
                                        assertEquals(
                                                "requiredHostLibraries",
                                                assertThrows(
                                                                NullPointerException.class,
                                                                () ->
                                                                        new MinimumHostRequirements(
                                                                                glibc,
                                                                                glibcxx,
                                                                                macOs,
                                                                                absentLibraries))
                                                        .getMessage()));
                    }
                }
            }
        }

        assertAll(assertions);
    }
}
