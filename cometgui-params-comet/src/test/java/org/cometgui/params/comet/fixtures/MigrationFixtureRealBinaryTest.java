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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The migration fixture is, byte for byte, what the pinned Comet 2024.01.0 binary writes today.
 *
 * <p>The binary is staged from the gitignored mirror {@link MigrationFixtures#MIRROR}, its SHA-256
 * checked against {@link MigrationFixtures#ROW} <em>before</em> it is run, and run with {@code -q}
 * and {@code -p} through {@code ProcessService}, each in its own empty directory. A missing binary
 * fails with instructions for refilling the mirror; it never skips.
 */
class MigrationFixtureRealBinaryTest {

    @ParameterizedTest(name = "Comet 2024.01.0 linux/x86-64 {0}")
    @EnumSource(CometFixtures.Mode.class)
    @EnabledOnOs(
            value = OS.LINUX,
            disabledReason =
                    "only the linux/x86-64 binary can be executed on this project's host; the"
                            + " migration fixture was captured there and nowhere else")
    @DisplayName("fresh output of the pinned older binary equals the migration fixture")
    void freshOutputEqualsTheFixture(CometFixtures.Mode mode, @TempDir Path scratch)
            throws IOException, InterruptedException {
        Path binary =
                UpstreamMirror.stage(
                        UpstreamMirror.repositoryRoot(),
                        MigrationFixtures.MIRROR,
                        MigrationFixtures.ROW,
                        scratch.resolve("bin").resolve("comet"),
                        MigrationFixtures.PINNED_BY,
                        MigrationFixtures.REFILL);
        assertEquals(MigrationFixtures.ROW.sha256(), UpstreamMirror.sha256(binary));
        Path run = Files.createDirectory(scratch.resolve("run"));

        byte[] fresh = ParameterFileCapture.capture(binary, mode, run);
        byte[] fixture = MigrationFixtures.bytes(mode);

        assertEquals(
                -1L,
                Arrays.mismatch(fixture, fresh),
                () ->
                        "the migration fixture "
                                + mode.fileName()
                                + " ("
                                + fixture.length
                                + " bytes) is not what Comet 2024.01.0 "
                                + mode.argument()
                                + " writes today ("
                                + fresh.length
                                + " bytes); first difference at byte "
                                + Arrays.mismatch(fixture, fresh));
    }

    @Test
    @DisplayName("a missing older binary fails, naming the mirror and how to refill it")
    void aMissingBinaryFails(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("manifests"));
        AssertionError failure =
                assertThrows(
                        AssertionError.class,
                        () ->
                                UpstreamMirror.stage(
                                        root,
                                        MigrationFixtures.MIRROR,
                                        MigrationFixtures.ROW,
                                        root.resolve("bin/comet"),
                                        MigrationFixtures.PINNED_BY,
                                        MigrationFixtures.REFILL));
        String message = failure.getMessage();
        assertTrue(message.contains("v2024.01.0__comet.linux.exe"), message);
        assertTrue(message.contains(MigrationFixtures.MIRROR), message);
        assertTrue(message.contains(MigrationFixtures.ROW.sha256()), message);
        assertTrue(message.contains("fails rather than skips"), message);
    }

    @Test
    @DisplayName("an older binary with another SHA-256 is refused before it is run")
    void aWrongBinaryIsNotRun(@TempDir Path root) throws IOException {
        Path mirror = Files.createDirectories(root.resolve(MigrationFixtures.MIRROR));
        Files.writeString(
                mirror.resolve("v2024.01.0__comet.linux.exe"),
                "#!/bin/sh\necho CONSTRUCTED impostor\n",
                StandardCharsets.UTF_8);
        AssertionError failure =
                assertThrows(
                        AssertionError.class,
                        () ->
                                UpstreamMirror.stage(
                                        root,
                                        MigrationFixtures.MIRROR,
                                        MigrationFixtures.ROW,
                                        root.resolve("bin/comet"),
                                        MigrationFixtures.PINNED_BY,
                                        MigrationFixtures.REFILL));
        assertTrue(
                failure.getMessage()
                        .contains(
                                "but "
                                        + MigrationFixtures.PINNED_BY
                                        + " pins "
                                        + MigrationFixtures.ROW.sha256()),
                failure.getMessage());
    }

    @Test
    @DisplayName("the fixture directory's SHA256SUMS lists both dumps and verifies")
    void theChecksumsVerify() throws IOException {
        assertEquals(
                java.util.List.of(), FixtureMatrix.verifyDirectory(MigrationFixtures.directory()));
    }

    @Test
    @DisplayName("the older release is not in the release matrix, and its fixtures are not in it")
    void theOlderReleaseIsNotOffered() throws IOException {
        assertFalse(
                CometManifest.cometVersions(CometManifest.repositoryManifest())
                        .contains(MigrationFixtures.VERSION),
                "Comet "
                        + MigrationFixtures.VERSION
                        + " is a migration fixture only; manifests/tools.json is the product's"
                        + " install matrix");
        Path matrixRoot = CometFixtures.root();
        assertFalse(
                Files.exists(matrixRoot.resolve(MigrationFixtures.VERSION)),
                "the migration fixture must not sit in the release-matrix fixture tree");
        FixtureMatrix.assertCovered(
                FixtureMatrix.check(CometManifest.repositoryManifest(), matrixRoot));
    }
}
