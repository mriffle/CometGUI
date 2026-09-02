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
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link InstallProgress}.
 *
 * <p>The negative-total convention is the point of this file. It is inherited from {@code
 * org.cometgui.domain.ports.DownloadProgressListener} rather than invented, and a caller that
 * divides by it without checking shows a negative fraction -- so {@link
 * InstallProgress#hasKnownTotal()} is asserted at the boundary, not merely somewhere.
 *
 * <p><strong>The rejection and the boundary are graded over every phase and every tool.</strong>
 * Neither rule depends on either: {@code bytesTransferred} may not be negative during a probe any
 * more than during a download. Pinning the phase at {@code DOWNLOADING} would leave the phase axis
 * untested and would let a rule ANDed with a phase pass -- the shape that let a blank-note rule be
 * switched off for a single enum constant in {@link DeclaredCapability}.
 */
class InstallProgressTest {

    private static final ToolVersion VERSION = ToolVersion.parse("3.07.1");

    private static final List<Long> NEGATIVE_TOTALS = List.of(-1L, -100L, Long.MIN_VALUE);

    private static final List<Long> KNOWN_TOTALS = List.of(0L, 1L, 99_000_000L);

    @Test
    @DisplayName("a report keeps every part it was given")
    void keepsItsParts() {
        InstallProgress progress =
                new InstallProgress(
                        ToolName.PERCOLATOR, VERSION, InstallPhase.DOWNLOADING, 4096L, 946_000L);

        assertAll(
                () -> assertEquals(ToolName.PERCOLATOR, progress.tool()),
                () -> assertEquals(VERSION, progress.version()),
                () -> assertEquals(InstallPhase.DOWNLOADING, progress.phase()),
                () -> assertEquals(4096L, progress.bytesTransferred()),
                () -> assertEquals(946_000L, progress.totalBytes()),
                () -> assertTrue(progress.hasKnownTotal()));
    }

    @ParameterizedTest(name = "[{index}] totalBytes={0}")
    @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
    @DisplayName("a negative total means the server declared none, and is not a known total")
    void aNegativeTotalIsNotKnown(long totalBytes) {
        InstallProgress progress =
                new InstallProgress(
                        ToolName.PDV, VERSION, InstallPhase.DOWNLOADING, 1024L, totalBytes);

        assertAll(
                () -> assertFalse(progress.hasKnownTotal()),
                () -> assertEquals(totalBytes, progress.totalBytes()));
    }

    @ParameterizedTest(name = "[{index}] totalBytes={0}")
    @ValueSource(longs = {0L, 1L, 99_000_000L})
    @DisplayName("a total of zero or more is a known total, so the boundary is at zero")
    void zeroIsAKnownTotal(long totalBytes) {
        InstallProgress progress =
                new InstallProgress(
                        ToolName.PDV, VERSION, InstallPhase.DOWNLOADING, 0L, totalBytes);

        assertTrue(progress.hasKnownTotal());
    }

    @ParameterizedTest(name = "[{index}] phase={0}")
    @EnumSource(InstallPhase.class)
    @DisplayName("the known-total boundary is the same in every install phase")
    void theKnownTotalBoundaryHoldsInEveryPhase(InstallPhase phase) {
        List<Executable> assertions = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            for (long negative : NEGATIVE_TOTALS) {
                assertions.add(
                        () ->
                                assertFalse(
                                        new InstallProgress(tool, VERSION, phase, 0L, negative)
                                                .hasKnownTotal(),
                                        tool.id() + " " + phase.name() + " total " + negative));
            }
            for (long known : KNOWN_TOTALS) {
                assertions.add(
                        () ->
                                assertTrue(
                                        new InstallProgress(tool, VERSION, phase, 0L, known)
                                                .hasKnownTotal(),
                                        tool.id() + " " + phase.name() + " total " + known));
            }
        }

        assertAll(assertions);
    }

    @ParameterizedTest(name = "[{index}] phase={0}")
    @EnumSource(InstallPhase.class)
    @DisplayName("a negative byte count is rejected in every phase, naming the field and the value")
    void aNegativeByteCountIsRejectedInEveryPhase(InstallPhase phase) {
        List<Executable> assertions = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            for (long negative : NEGATIVE_TOTALS) {
                assertions.add(
                        () ->
                                assertEquals(
                                        "bytesTransferred must not be negative, but was: "
                                                + negative,
                                        assertThrows(
                                                        IllegalArgumentException.class,
                                                        () ->
                                                                new InstallProgress(
                                                                        tool, VERSION, phase,
                                                                        negative, 100L))
                                                .getMessage(),
                                        tool.id() + " " + phase.name()));
            }
        }

        assertAll(assertions);
    }

    @ParameterizedTest(name = "[{index}] phase={0}")
    @EnumSource(InstallPhase.class)
    @DisplayName("zero bytes transferred is accepted in every phase: an install starts there")
    void zeroBytesIsAcceptedInEveryPhase(InstallPhase phase) {
        assertEquals(
                0L,
                new InstallProgress(ToolName.COMET, VERSION, phase, 0L, 100L).bytesTransferred());
    }

    @ParameterizedTest(name = "[{index}] phase={0}")
    @EnumSource(InstallPhase.class)
    @DisplayName("a null part is rejected by name in every phase")
    void nullPartsAreRejectedByName(InstallPhase phase) {
        assertAll(
                () ->
                        assertEquals(
                                "tool",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new InstallProgress(
                                                                Nulls.of(ToolName.class),
                                                                VERSION,
                                                                phase,
                                                                0L,
                                                                0L))
                                        .getMessage(),
                                phase.name()),
                () ->
                        assertEquals(
                                "version",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new InstallProgress(
                                                                ToolName.COMET,
                                                                Nulls.of(ToolVersion.class),
                                                                phase,
                                                                0L,
                                                                0L))
                                        .getMessage(),
                                phase.name()));
    }

    @Test
    @DisplayName("a null phase is rejected by name")
    void aNullPhaseIsRejectedByName() {
        assertEquals(
                "phase",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new InstallProgress(
                                                ToolName.COMET,
                                                VERSION,
                                                Nulls.of(InstallPhase.class),
                                                0L,
                                                0L))
                        .getMessage());
    }
}
