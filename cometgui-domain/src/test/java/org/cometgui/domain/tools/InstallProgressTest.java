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

import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link InstallProgress}.
 *
 * <p>The negative-total convention is the point of this file. It is inherited from {@code
 * org.cometgui.domain.ports.DownloadProgressListener} rather than invented, and a caller that
 * divides by it without checking shows a negative fraction -- so {@link
 * InstallProgress#hasKnownTotal()} is asserted at the boundary, not merely somewhere.
 */
class InstallProgressTest {

    private static final ToolVersion VERSION = ToolVersion.parse("3.07.1");

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

    @Test
    @DisplayName("a negative byte count is rejected, naming the field and the value")
    void aNegativeByteCountIsRejected() {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new InstallProgress(
                                        ToolName.COMET,
                                        VERSION,
                                        InstallPhase.DOWNLOADING,
                                        -1L,
                                        100L));

        assertEquals("bytesTransferred must not be negative, but was: -1", rejected.getMessage());
    }

    @Test
    @DisplayName("zero bytes transferred is accepted: an install starts there")
    void zeroBytesIsAccepted() {
        assertEquals(
                0L,
                new InstallProgress(ToolName.COMET, VERSION, InstallPhase.DOWNLOADING, 0L, 100L)
                        .bytesTransferred());
    }

    @Test
    @DisplayName("a null part is rejected by name")
    void nullPartsAreRejectedByName() {
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
                                                                InstallPhase.DOWNLOADING,
                                                                0L,
                                                                0L))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "version",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new InstallProgress(
                                                                ToolName.COMET,
                                                                Nulls.of(ToolVersion.class),
                                                                InstallPhase.DOWNLOADING,
                                                                0L,
                                                                0L))
                                        .getMessage()),
                () ->
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
                                        .getMessage()));
    }
}
