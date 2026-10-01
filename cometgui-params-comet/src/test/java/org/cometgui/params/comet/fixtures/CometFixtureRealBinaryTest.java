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

package org.cometgui.params.comet.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The checked-in fixtures are, byte for byte, what the pinned binary writes today.
 *
 * <p>For every Comet version in {@code manifests/tools.json} that has a {@code linux}/{@code
 * x86-64} row, the binary is staged out of the gitignored mirror, checked against the manifest's
 * SHA-256, and run with {@code -q} and with {@code -p} -- each in its own empty directory, through
 * {@code ProcessService}. The {@code comet.params.new} it writes must equal the fixture exactly. A
 * fixture edited or typed by hand therefore fails the build here, and a missing mirror, a checksum
 * mismatch, a non-zero exit or a missing output file fails it too: none of them skips.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project; the"
                        + " Windows and macOS binaries have never been run here, so their -q/-p"
                        + " output has never been captured or compared")
class CometFixtureRealBinaryTest {

    private static List<CometManifest.Row> executableRows() throws IOException {
        List<CometManifest.Row> rows = new ArrayList<>();
        for (CometManifest.Row row : CometManifest.cometRows(CometManifest.repositoryManifest())) {
            if (row.isLinuxX8664()) {
                rows.add(row);
            }
        }
        return rows;
    }

    @Test
    @DisplayName("the manifest gives this host at least one Comet binary to compare against")
    void thereIsSomethingToCompare() throws IOException {
        assertFalse(
                executableRows().isEmpty(),
                "manifests/tools.json has no linux/x86-64 Comet row, so the comparison below would"
                        + " run zero times and pass");
    }

    @TestFactory
    @DisplayName("fresh -q and -p output equals the checked-in fixture, byte for byte")
    List<DynamicTest> freshOutputEqualsTheFixture(@TempDir Path scratch) throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (CometManifest.Row row : executableRows()) {
            for (CometFixtures.Mode mode : CometFixtures.Mode.values()) {
                tests.add(
                        DynamicTest.dynamicTest(
                                "Comet " + row.version() + " " + row.platform() + " " + mode,
                                () -> compare(scratch, row, mode)));
            }
        }
        return tests;
    }

    private static void compare(Path scratch, CometManifest.Row row, CometFixtures.Mode mode)
            throws IOException, InterruptedException {
        Path base = Files.createTempDirectory(scratch, "run-");
        Path binary =
                UpstreamMirror.stage(
                        UpstreamMirror.repositoryRoot(), row, base.resolve("bin").resolve("comet"));
        Path run = Files.createDirectory(base.resolve("run"));

        byte[] fresh = ParameterFileCapture.capture(binary, mode, run);
        byte[] fixture = CometFixtures.bytes(row.version(), row.directoryName(), mode);

        assertEquals(
                -1L,
                firstDifference(fixture, fresh),
                () ->
                        "the checked-in "
                                + mode.fileName()
                                + " for Comet "
                                + row.version()
                                + " "
                                + row.platform()
                                + " ("
                                + fixture.length
                                + " bytes) is not what "
                                + mode.argument()
                                + " writes today ("
                                + fresh.length
                                + " bytes); first difference at byte offset "
                                + firstDifference(fixture, fresh)
                                + ". A fixture is real output, never edited: re-capture it.");
    }

    /** The first offset at which the arrays differ, their common length if one is a prefix. */
    static long firstDifference(byte[] expected, byte[] actual) {
        return Arrays.mismatch(expected, actual);
    }
}
