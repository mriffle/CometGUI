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

package org.cometgui.install.probe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link IdentityProbe}'s ordering rule. */
class IdentityProbeTest {

    private static final String PERCOLATOR_BANNER =
            "Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18";

    @Test
    @DisplayName("standard error is searched first, so a banner there is found")
    void standardErrorIsSearchedFirst() {
        assertEquals(
                "3.07.1",
                IdentityProbe.identify(
                                VersionBanner.percolator(), List.of(PERCOLATOR_BANNER), List.of())
                        .orElseThrow()
                        .text());
    }

    @Test
    @DisplayName("standard output is searched too, because Comet prints its banner there as well")
    void standardOutputIsSearchedAsWell() {
        assertEquals(
                "2026.02.2",
                IdentityProbe.identify(
                                VersionBanner.comet(),
                                List.of(),
                                List.of(" Comet version \"2026.02 rev. 2 (6edec91)\""))
                        .orElseThrow()
                        .text());
    }

    @Test
    @DisplayName("where both streams carry one, standard error wins")
    void standardErrorWinsOverStandardOutput() {
        assertEquals(
                "3.07.1",
                IdentityProbe.identify(
                                VersionBanner.percolator(),
                                List.of(PERCOLATOR_BANNER),
                                List.of("Percolator version 3.09, Build Date x"))
                        .orElseThrow()
                        .text());
    }

    @Test
    @DisplayName("a probe reading standard output alone sees nothing on Percolator's real output")
    void aStandardOutputOnlyProbeSeesNothing() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(),
                                VersionBanner.percolator().readFrom(List.of()),
                                "Percolator writes 0 bytes to standard output for --help, so a"
                                        + " stdout-only probe searches an empty list"),
                () ->
                        assertEquals(
                                "3.07.1",
                                IdentityProbe.identify(
                                                VersionBanner.percolator(),
                                                List.of(PERCOLATOR_BANNER),
                                                List.of())
                                        .orElseThrow()
                                        .text(),
                                "and reading both streams finds it"));
    }

    @Test
    @DisplayName("neither stream carrying a banner is empty, not a guess")
    void noBannerAnywhereIsEmpty() {
        assertEquals(
                Optional.empty(),
                IdentityProbe.identify(
                        VersionBanner.percolator(),
                        List.of("Error: too few arguments."),
                        List.of("Invoke with -h option for help")));
    }

    @Test
    @DisplayName("the two streams concatenate with standard error first")
    void errorFirstConcatenates() {
        assertEquals(
                List.of("e1", "e2", "o1"),
                IdentityProbe.errorFirst(List.of("e1", "e2"), List.of("o1")));
    }

    @Test
    @DisplayName("the utility class cannot be instantiated, even by reflection")
    void theUtilityClassIsNotInstantiable() throws ReflectiveOperationException {
        var constructor = IdentityProbe.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertEquals(
                "IdentityProbe is a utility class and is never instantiated",
                assertThrows(
                                java.lang.reflect.InvocationTargetException.class,
                                constructor::newInstance)
                        .getCause()
                        .getMessage());
    }

    @Test
    @DisplayName("the probe rejects a null argument by name")
    void nullArgumentsAreRejectedByName() {
        assertAll(
                () ->
                        assertEquals(
                                "banner",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        IdentityProbe.identify(
                                                                Nulls.of(VersionBanner.class),
                                                                List.of(),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "standardError",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        IdentityProbe.identify(
                                                                VersionBanner.comet(),
                                                                Nulls.of(List.class),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "standardOutput",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        IdentityProbe.identify(
                                                                VersionBanner.comet(),
                                                                List.of(),
                                                                Nulls.of(List.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "standardError",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        IdentityProbe.errorFirst(
                                                                Nulls.of(List.class), List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "standardOutput",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        IdentityProbe.errorFirst(
                                                                List.of(), Nulls.of(List.class)))
                                        .getMessage()));
    }
}
