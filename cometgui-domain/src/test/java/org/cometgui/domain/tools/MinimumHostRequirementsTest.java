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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link MinimumHostRequirements}.
 *
 * <p>The values used are the real ones: {@code GLIBC_2.34} for the Percolator 3.07.1 portable
 * archive, {@code GLIBC_2.38} for the 3.09 Debian payload, {@code GLIBC_2.14} for the 3.06.5
 * archive, and the four Visual C++ runtime DLLs the Windows portable zip imports and does not ship.
 */
class MinimumHostRequirementsTest {

    private static final List<String> VISUAL_CPP_RUNTIME =
            List.of("MSVCP140.dll", "VCRUNTIME140.dll", "VCRUNTIME140_1.dll", "VCOMP140.DLL");

    @Test
    @DisplayName("a Linux glibc floor is kept as a comparable version, not as text")
    void theGlibcFloorIsAVersion() {
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(
                        Optional.of(GlibcVersion.parse("2.34")), Optional.empty(), List.of());

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
    @DisplayName("the Windows Visual C++ runtime is declared as required host libraries")
    void theWindowsRuntimeIsDeclared() {
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(Optional.empty(), Optional.empty(), VISUAL_CPP_RUNTIME);

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
                new MinimumHostRequirements(Optional.empty(), Optional.of(" 12.7 "), List.of());

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
                () -> assertEquals(Optional.empty(), none.minimumMacOsVersion()),
                () -> assertEquals(List.of(), none.requiredHostLibraries()),
                () ->
                        assertEquals(
                                none,
                                new MinimumHostRequirements(
                                        Optional.empty(), Optional.empty(), List.of())));
    }

    @Test
    @DisplayName("a library list is stripped and kept in the order it was declared")
    void librariesAreStrippedAndOrdered() {
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(
                        Optional.empty(),
                        Optional.empty(),
                        Arrays.asList(" VCRUNTIME140.dll ", "MSVCP140.dll"));

        assertEquals(
                List.of("VCRUNTIME140.dll", "MSVCP140.dll"), requirements.requiredHostLibraries());
    }

    @Test
    @DisplayName(
            "the list handed out is a copy, so a later change to the caller's list is invisible")
    void theListIsCopied() {
        List<String> mutable = new ArrayList<>(List.of("MSVCP140.dll"));
        MinimumHostRequirements requirements =
                new MinimumHostRequirements(Optional.empty(), Optional.empty(), mutable);

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
    @DisplayName("a present but blank macOS floor is rejected, naming the field")
    void aBlankMacOsFloorIsRejected(String blank) {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new MinimumHostRequirements(
                                        Optional.empty(), Optional.of(blank), List.of()));

        assertEquals(
                "minimumMacOsVersion must not be blank when it is present; leave it absent"
                        + " instead",
                rejected.getMessage());
    }

    @Test
    @DisplayName("a blank or null library is rejected, naming its position")
    void aBlankLibraryIsRejected() {
        assertAll(
                () ->
                        assertEquals(
                                "requiredHostLibraries[1] must not be blank",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new MinimumHostRequirements(
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                Arrays.asList(
                                                                        "MSVCP140.dll", "  ")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "requiredHostLibraries[0] must not be null",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new MinimumHostRequirements(
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                Arrays.asList(
                                                                        null, "MSVCP140.dll")))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a library named twice is rejected, quoting the duplicate")
    void aDuplicateLibraryIsRejected() {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new MinimumHostRequirements(
                                        Optional.empty(),
                                        Optional.empty(),
                                        Arrays.asList("MSVCP140.dll", " MSVCP140.dll ")));

        assertEquals(
                "requiredHostLibraries names \"MSVCP140.dll\" more than once",
                rejected.getMessage());
    }

    @Test
    @DisplayName("a null part is rejected by name")
    void nullPartsAreRejectedByName() {
        assertAll(
                () ->
                        assertEquals(
                                "minimumGlibc",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new MinimumHostRequirements(
                                                                Nulls.of(Optional.class),
                                                                Optional.empty(),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "minimumMacOsVersion",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new MinimumHostRequirements(
                                                                Optional.empty(),
                                                                Nulls.of(Optional.class),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "requiredHostLibraries",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new MinimumHostRequirements(
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                Nulls.of(List.class)))
                                        .getMessage()));
    }
}
