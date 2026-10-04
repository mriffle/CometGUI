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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The release matrix is read from {@code manifests/tools.json}, and every Comet version in it has
 * real fixtures that verify.
 *
 * <p>The failing cases use a <em>constructed</em> manifest and a <em>copied</em> fixture tree in a
 * temporary directory; the real manifest and the real fixtures are never edited.
 */
class FixtureMatrixTest {

    private static final String SHA = "0".repeat(64);

    /** A constructed manifest row; the URL and digest are placeholders, never fetched or run. */
    private static String row(String version, String os, String arch) {
        return "{\"tool\": \"comet\", \"version\": \""
                + version
                + "\", \"releaseTag\": \"v"
                + version
                + "\", \"os\": \""
                + os
                + "\", \"arch\": \""
                + arch
                + "\", \"url\": \"https://example.invalid/comet\", \"sha256\": \""
                + SHA
                + "\"}";
    }

    private static Path manifest(Path directory, String... rows) throws IOException {
        Path file = directory.resolve("tools.json");
        Files.writeString(
                file,
                "{\"schemaVersion\": 1, \"artefacts\": [" + String.join(", ", rows) + "]}",
                StandardCharsets.UTF_8);
        return file;
    }

    /** A copy of the real 2026.02.2 linux-x86-64 fixtures under a temporary root. */
    private static Path copiedFixtures(Path directory) throws IOException {
        Path root = Files.createDirectories(directory.resolve("fixtures"));
        Path source =
                CometFixtures.directory(
                        CometFixtures.root(),
                        CometFixtures.COMET_2026_02_2,
                        CometFixtures.LINUX_X86_64);
        Path target =
                Files.createDirectories(
                        CometFixtures.directory(
                                root, CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64));
        for (String name :
                List.of(
                        CometFixtures.Mode.COMPLETE.fileName(),
                        CometFixtures.Mode.DEFAULTS.fileName(),
                        CometFixtures.SHA256SUMS)) {
            Files.copy(source.resolve(name), target.resolve(name));
        }
        return root;
    }

    @Test
    @DisplayName("every Comet version in the real manifest has linux-x86-64 fixtures that verify")
    void theRealManifestIsCovered() throws IOException {
        Path manifest = CometManifest.repositoryManifest();
        FixtureMatrix.Report report = FixtureMatrix.check(manifest, CometFixtures.root());

        FixtureMatrix.assertCovered(report);

        List<String> expectedVerified = new ArrayList<>();
        List<String> expectedUnexecuted = new ArrayList<>();
        for (CometManifest.Row row : CometManifest.cometRows(manifest)) {
            if (row.isLinuxX8664()) {
                expectedVerified.add(row.version() + " " + row.platform());
            } else {
                expectedUnexecuted.add(row.version() + " " + row.platform());
            }
        }
        assertFalse(expectedVerified.isEmpty(), "the manifest has no linux/x86-64 Comet row");
        assertEquals(expectedVerified.size(), report.verified().size(), report.toString());
        assertEquals(
                expectedUnexecuted.size(),
                report.neverCaptured().size(),
                "every non-Linux-x86-64 row is reported as never captured: " + report);
        for (int index = 0; index < expectedUnexecuted.size(); index++) {
            String line = report.neverCaptured().get(index);
            assertTrue(
                    line.contains(expectedUnexecuted.get(index))
                            && line.endsWith("never captured, because never executed here"),
                    line);
        }
        /* The report is the record of what was NOT done; print it into the surefire output. */
        report.verified().forEach(line -> System.out.println("VERIFIED       " + line));
        report.neverCaptured().forEach(line -> System.out.println("NOT CAPTURED   " + line));
    }

    @Test
    @DisplayName("every fixture directory verifies, including 2026.03.0 before the manifest has it")
    void everyFixtureDirectoryVerifies() throws IOException {
        assertEquals(List.of(), FixtureMatrix.verifyEveryDirectory(CometFixtures.root()));
        assertTrue(
                CometFixtures.versions(CometFixtures.root())
                        .containsAll(
                                List.of(
                                        CometFixtures.COMET_2026_02_2,
                                        CometFixtures.COMET_2026_03_0)),
                "both captured releases are fixture directories");
    }

    @Test
    @DisplayName("a changed byte in a fixture directory the manifest does not name is caught")
    void aChangedByteOutsideTheManifestFails(@TempDir Path directory) throws IOException {
        Path root = copiedFixtures(directory);
        Path unlisted =
                Files.createDirectories(
                        CometFixtures.directory(root, "2099.01.0", CometFixtures.LINUX_X86_64));
        Path source =
                CometFixtures.directory(
                        root, CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64);
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) {
                Files.copy(file, unlisted.resolve(file.getFileName()));
            }
        }
        Files.createDirectories(root.resolve("2099.02.0"));
        assertEquals(
                List.of(root.resolve("2099.02.0") + " holds no platform directory"),
                FixtureMatrix.verifyEveryDirectory(root));
        Path fixture = unlisted.resolve(CometFixtures.Mode.DEFAULTS.fileName());
        byte[] bytes = Files.readAllBytes(fixture);
        bytes[10] = (byte) (bytes[10] ^ 0x01);
        Files.write(fixture, bytes);
        List<String> problems = FixtureMatrix.verifyEveryDirectory(root);
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(
                problems.stream()
                        .anyMatch(
                                problem ->
                                        problem.contains("2099.01.0")
                                                && problem.contains("comet-p.params has SHA-256")),
                problems.toString());
    }

    @Test
    @DisplayName("a Comet version in the manifest with no fixtures fails the check, naming it")
    void aVersionWithoutFixturesFails(@TempDir Path directory) throws IOException {
        Path manifest =
                manifest(
                        directory,
                        row(CometFixtures.COMET_2026_02_2, "linux", "x86-64"),
                        row("2099.01.0", "linux", "x86-64"),
                        row("2099.01.0", "windows", "x86-64"));

        FixtureMatrix.Report report = FixtureMatrix.check(manifest, CometFixtures.root());
        AssertionError failure =
                assertThrows(AssertionError.class, () -> FixtureMatrix.assertCovered(report));

        assertEquals(1, report.problems().size(), report.toString());
        assertTrue(
                failure.getMessage().contains("Comet 2099.01.0 for linux/x86-64")
                        && failure.getMessage().contains("there are no fixtures for it"),
                failure.getMessage());
        assertEquals(
                List.of(
                        "Comet 2099.01.0 windows/x86-64: -q/-p output never captured, because"
                                + " never executed here"),
                report.neverCaptured());
    }

    @Test
    @DisplayName("a Comet version with no linux/x86-64 row at all fails: nothing could capture it")
    void aVersionWithNoCapturablePlatformFails(@TempDir Path directory) throws IOException {
        Path manifest =
                manifest(
                        directory,
                        row(CometFixtures.COMET_2026_02_2, "linux", "x86-64"),
                        row("2099.02.0", "macos", "aarch64"));

        FixtureMatrix.Report report = FixtureMatrix.check(manifest, CometFixtures.root());

        assertEquals(1, report.problems().size(), report.toString());
        assertTrue(report.problems().get(0).contains("no linux/x86-64 row"), report.toString());
    }

    @Test
    @DisplayName("a manifest with no Comet row at all is not a covered matrix")
    void anEmptyMatrixFails(@TempDir Path directory) throws IOException {
        FixtureMatrix.Report report =
                FixtureMatrix.check(manifest(directory), CometFixtures.root());

        assertEquals(1, report.problems().size(), report.toString());
        assertTrue(report.problems().get(0).contains("names no Comet artefact"));
    }

    @Test
    @DisplayName("one changed byte in a fixture copy is caught by its SHA256SUMS")
    void aChangedByteFails(@TempDir Path directory) throws IOException {
        Path root = copiedFixtures(directory);
        Path fixtures =
                CometFixtures.directory(
                        root, CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64);
        Path fixture = fixtures.resolve(CometFixtures.Mode.COMPLETE.fileName());
        byte[] bytes = Files.readAllBytes(fixture);
        bytes[100] = (byte) (bytes[100] ^ 0x01);
        Files.write(fixture, bytes);

        List<String> problems = FixtureMatrix.verifyDirectory(fixtures);

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(
                problems.get(0).contains("comet-q.params has SHA-256")
                        && problems.get(0).contains("has changed since it was captured"),
                problems.get(0));
    }

    @Test
    @DisplayName(
            "the untouched copy verifies, so the failures above are the change and not the copy")
    void theUntouchedCopyVerifies(@TempDir Path directory) throws IOException {
        Path root = copiedFixtures(directory);

        assertEquals(
                List.of(),
                FixtureMatrix.verifyDirectory(
                        CometFixtures.directory(
                                root, CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64)));
    }

    @Test
    @DisplayName("a fixture directory without SHA256SUMS fails")
    void aMissingSumsFileFails(@TempDir Path directory) throws IOException {
        Path root = copiedFixtures(directory);
        Path fixtures =
                CometFixtures.directory(
                        root, CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64);
        Files.delete(fixtures.resolve(CometFixtures.SHA256SUMS));

        assertEquals(
                List.of(fixtures + " has no SHA256SUMS"), FixtureMatrix.verifyDirectory(fixtures));
    }

    @Test
    @DisplayName("a SHA256SUMS that leaves one dump out fails, and so does a missing dump")
    void anUnlistedOrMissingDumpFails(@TempDir Path directory) throws IOException {
        Path root = copiedFixtures(directory);
        Path fixtures =
                CometFixtures.directory(
                        root, CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64);
        Path sums = fixtures.resolve(CometFixtures.SHA256SUMS);
        List<String> kept =
                Files.readAllLines(sums, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.endsWith("comet-p.params"))
                        .toList();
        Files.write(sums, kept, StandardCharsets.UTF_8);
        Files.delete(fixtures.resolve("comet-p.params"));

        List<String> problems = FixtureMatrix.verifyDirectory(fixtures);

        assertEquals(
                List.of(
                        fixtures + " has no comet-p.params",
                        sums + " does not list comet-p.params"),
                problems);
    }

    @Test
    @DisplayName("a malformed or duplicated SHA256SUMS line fails")
    void aMalformedSumsLineFails(@TempDir Path directory) throws IOException {
        Path root = copiedFixtures(directory);
        Path fixtures =
                CometFixtures.directory(
                        root, CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64);
        Path sums = fixtures.resolve(CometFixtures.SHA256SUMS);
        List<String> lines = new ArrayList<>(Files.readAllLines(sums, StandardCharsets.UTF_8));
        lines.add(lines.get(0));
        lines.add("not a checksum line");
        Files.write(sums, lines, StandardCharsets.UTF_8);

        List<String> problems = FixtureMatrix.verifyDirectory(fixtures);

        assertEquals(
                List.of(
                        sums + " lists comet-p.params twice",
                        sums + " line 4 is not a sha256sum line: \"not a checksum line\""),
                problems);
    }

    @Test
    @DisplayName("a fixture directory for a non-Linux platform is verified, not excused")
    void anotherPlatformsFixturesAreVerifiedToo(@TempDir Path directory) throws IOException {
        Path root = copiedFixtures(directory);
        Path other =
                Files.createDirectories(
                        CometFixtures.directory(
                                root, CometFixtures.COMET_2026_02_2, "macos-aarch64"));
        Files.writeString(other.resolve("comet-q.params"), "constructed, not Comet output\n");
        Path manifest =
                manifest(
                        directory,
                        row(CometFixtures.COMET_2026_02_2, "linux", "x86-64"),
                        row(CometFixtures.COMET_2026_02_2, "macos", "aarch64"));

        FixtureMatrix.Report report = FixtureMatrix.check(manifest, root);

        assertEquals(
                List.of(other + " has no comet-p.params", other + " has no SHA256SUMS"),
                report.problems());
        assertEquals(List.of(), report.neverCaptured());
    }
}
