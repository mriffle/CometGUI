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

package org.cometgui.install.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.testing.Nulls;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The cache layout, and {@code R-TOOL-04} answered from a directory.
 *
 * <p>The verification is graded over every state it can reach and over the axes the rule does not
 * depend on -- which file went wrong, and whether the file is missing, the wrong length or the
 * wrong bytes -- because a rule asserted at one point on an axis it does not depend on can be
 * switched off everywhere else with nothing going red.
 */
class ToolCacheTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    @TempDir private Path temporary;

    private ToolCache cache() {
        return new ToolCache(temporary.resolve("root"), new StreamingHashService());
    }

    @Test
    @DisplayName("the layout is the specification's sketch, with the version normalised")
    void theLayoutIsTheSpecificationsSketch() {
        ToolCache cache = cache();
        Path root = temporary.resolve("root");

        assertEquals(root.resolve("tools"), cache.toolsRoot());
        assertEquals(root.resolve("cache"), cache.workingRoot());
        assertEquals(
                root.resolve("tools")
                        .resolve("percolator")
                        .resolve("3.7.1")
                        .resolve("linux-x86-64"),
                cache.toolDirectory(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX));
        assertEquals(
                root.resolve("cache").resolve("downloads").resolve("pdv__2.7__linux-x86-64"),
                cache.downloadDirectory(ToolName.PDV, ToolVersion.parse("2.7.0"), LINUX));
        assertEquals(
                root.resolve("cache")
                        .resolve("locks")
                        .resolve("comet__2026.2.2__linux-x86-64.lock"),
                cache.lockFile(ToolName.COMET, ToolVersion.parse("2026.02.2"), LINUX));
        assertEquals(
                root.resolve("cache").resolve("staging").resolve("percolator__3.9__linux-x86-64"),
                cache.stagingRoot(ToolName.PERCOLATOR, ToolVersion.parse("3.09"), LINUX));
    }

    @ParameterizedTest
    @CsvSource({"3.09,3.09.0", "3.07.1,3.7.1", "2026.02.2,2026.2.2"})
    @DisplayName("two spellings of one version are one directory, because they are one version")
    void versionsThatAreEqualMapToOneDirectory(String first, String second) {
        ToolCache cache = cache();
        assertEquals(
                ToolVersion.parse(first),
                ToolVersion.parse(second),
                "these two spellings are the same version by ToolVersion's own equals");
        assertEquals(
                cache.toolDirectory(ToolName.PERCOLATOR, ToolVersion.parse(first), LINUX),
                cache.toolDirectory(ToolName.PERCOLATOR, ToolVersion.parse(second), LINUX),
                "so an install made under one spelling must not be invisible under the other");
    }

    @Test
    @DisplayName("a complete directory verifies, and every recorded digest is checked")
    void aCompleteDirectoryVerifies() throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path directory = install(cache, "the binary", "the schema", 2);

        InstallationCheck check =
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);

        assertEquals(InstallationState.INSTALLED, check.state(), check::detail);
        assertTrue(check.installed());
        assertEquals(directory, check.directory());
        assertEquals("percolator 3.07.1 linux-x86-64", check.requireMarker().describe());
    }

    @Test
    @DisplayName("no directory at all is NOT_PRESENT, and names the path it looked at")
    void noDirectoryIsNotPresent() throws IOException, InterruptedException {
        ToolCache cache = cache();
        InstallationCheck check =
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);
        assertEquals(InstallationState.NOT_PRESENT, check.state());
        assertTrue(check.detail().contains("3.7.1"), check::detail);
        assertTrue(check.marker().isEmpty());
    }

    @Test
    @DisplayName("a directory with no marker is an interrupted install, not an install")
    void aDirectoryWithNoMarkerIsNotAnInstall() throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path directory = install(cache, "the binary", "the schema", 2);
        Files.delete(directory.resolve(InstallationMarker.FILE_NAME));

        InstallationCheck check =
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);

        assertEquals(InstallationState.NO_MARKER, check.state());
        assertFalse(check.installed());
        assertTrue(check.detail().contains(InstallationMarker.FILE_NAME), check::detail);
    }

    @Test
    @DisplayName("a marker that is not a marker is unreadable, and says which field")
    void anUnreadableMarkerIsReportedAsOne() throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path directory = install(cache, "the binary", "the schema", 2);
        Files.writeString(
                directory.resolve(InstallationMarker.FILE_NAME),
                "{\"schemaVersion\": 1, \"tool\": \"percolator\"}",
                StandardCharsets.UTF_8);

        InstallationCheck check =
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);

        assertEquals(InstallationState.MARKER_UNREADABLE, check.state());
        assertTrue(check.detail().contains("\"version\""), check::detail);
    }

    @Test
    @DisplayName("a valid marker for another artefact is a moved entry, not a corrupt one")
    void aMarkerDescribingAnotherArtefactIsReportedSeparately()
            throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path directory = install(cache, "the binary", "the schema", 2);
        InstallationMarker other =
                markerFor(ToolName.COMET, "2026.02.2", "the binary", "the schema", 2);
        Files.writeString(
                directory.resolve(InstallationMarker.FILE_NAME),
                other.toJson(),
                StandardCharsets.UTF_8);

        InstallationCheck check =
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);

        assertEquals(InstallationState.MARKER_DESCRIBES_ANOTHER_ARTEFACT, check.state());
        assertTrue(
                check.detail().contains("comet 2026.02.2") && check.detail().contains("percolator"),
                check::detail);
    }

    @Test
    @DisplayName("a directory that has lost a file it does not record is caught by the count")
    void aLostFileIsCaughtByTheEntryCount() throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path directory = install(cache, "the binary", "the schema", 3);
        Files.writeString(directory.resolve("lib/extra.so"), "an unrecorded library");
        assertEquals(
                InstallationState.INSTALLED,
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX).state());

        Files.delete(directory.resolve("lib/extra.so"));

        InstallationCheck check =
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);
        assertEquals(InstallationState.CONTENT_COUNT_MISMATCH, check.state());
        assertTrue(
                check.detail().contains("records 3") && check.detail().contains("holds 2"),
                check::detail);
    }

    @Test
    @DisplayName("a recorded file that is gone is FILE_MISSING, and names it")
    void aMissingRecordedFileIsNamed() throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path directory = install(cache, "the binary", "the schema", 2);
        Files.delete(directory.resolve("share/xml/schema.xsd"));
        Files.writeString(
                directory.resolve("share/xml/decoy.xsd"), "so that the count still agrees");

        InstallationCheck check =
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);

        assertEquals(InstallationState.FILE_MISSING, check.state());
        assertTrue(check.detail().contains("share/xml/schema.xsd"), check::detail);
    }

    @ParameterizedTest
    @CsvSource({
        "bin/percolator, a binary of exactly the same length!",
        "share/xml/schema.xsd, a schema of exactly the same length"
    })
    @DisplayName("a recorded file whose bytes changed makes the entry not installed, either file")
    void aChangedFileMakesTheEntryNotInstalled(String path, String replacement)
            throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path directory = install(cache, "the binary", "the schema", 2);
        Path file = directory.resolve(path);
        long lengthBefore = Files.size(file);
        Files.writeString(file, replacement);

        InstallationCheck check =
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);

        assertNotEquals(lengthBefore, Files.size(file), "this case is about a LENGTH change");
        assertEquals(
                InstallationState.CHECKSUM_MISMATCH,
                check.state(),
                "R-TOOL-04's second half: the checksums must still match");
        assertTrue(check.detail().contains(path), check::detail);
    }

    @Test
    @DisplayName("a file swapped for bytes of the same length is still caught, by the digest")
    void aSwappedFileOfTheSameLengthIsCaughtByTheDigest() throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path directory = install(cache, "the binary", "the schema", 2);
        Path file = directory.resolve("bin/percolator");
        Files.writeString(file, "THE BINARY");

        InstallationCheck check =
                cache.verify(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);

        assertEquals(10, Files.size(file), "the same length as \"the binary\"");
        assertEquals(InstallationState.CHECKSUM_MISMATCH, check.state());
        assertTrue(
                check.detail().contains("hashes to") && check.detail().contains("R-TOOL-04"),
                check::detail);
    }

    @Test
    @DisplayName("the marker is written by a rename, so a reader never sees half of one")
    void theMarkerIsWrittenAtomically() throws IOException, InterruptedException {
        Path directory = Files.createDirectories(temporary.resolve("entry"));
        InstallationMarker marker = markerFor(ToolName.PERCOLATOR, "3.07.1", "one", "two", 2);

        Path written = ToolCache.writeMarker(directory, marker);

        assertEquals(directory.resolve(InstallationMarker.FILE_NAME), written);
        assertEquals(marker, InstallationMarker.parse(Files.readString(written)));
        try (var entries = Files.list(directory)) {
            assertEquals(
                    List.of(InstallationMarker.FILE_NAME),
                    entries.map(path -> String.valueOf(path.getFileName())).toList(),
                    "the temporary file it renamed from must not be left behind");
        }
    }

    @Test
    @DisplayName("counting the payload ignores the marker and follows no link")
    void countingThePayloadIgnoresTheMarker() throws IOException, InterruptedException {
        Path directory = Files.createDirectories(temporary.resolve("entry"));
        Files.createDirectories(directory.resolve("bin"));
        Files.writeString(directory.resolve("bin/tool"), "tool");
        Files.createSymbolicLink(directory.resolve("bin/alias"), Path.of("tool"));
        assertEquals(2, ToolCache.countPayloadEntries(directory), "a link counts as an entry");

        ToolCache.writeMarker(directory, markerFor(ToolName.PERCOLATOR, "3.07.1", "one", "two", 2));

        assertEquals(
                2,
                ToolCache.countPayloadEntries(directory),
                "and the marker itself never counts, or the figure would change the moment it was"
                        + " written");
    }

    @Test
    @DisplayName("the cache refuses to delete anything outside its own root")
    void discardRefusesAnythingOutsideTheRoot() throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path outside = Files.createDirectories(temporary.resolve("not-the-cache"));
        Files.writeString(outside.resolve("precious.txt"), "somebody else's file");

        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> cache.discard(outside));

        assertTrue(
                refused.getMessage().contains(outside.toString())
                        && refused.getMessage().contains(cache.root().toString()),
                () -> "the refusal names both paths: " + refused.getMessage());
        assertTrue(Files.exists(outside.resolve("precious.txt")), "and nothing was deleted");
        assertThrows(
                IllegalArgumentException.class,
                () -> cache.discard(cache.root()),
                "and the root itself is not a thing to delete either");
    }

    @Test
    @DisplayName("discarding a tree removes it without following a link out of it")
    void discardFollowsNoLink() throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path outside = Files.createDirectories(temporary.resolve("outside"));
        Path precious = outside.resolve("precious.txt");
        Files.writeString(precious, "somebody else's file");
        Path doomed = Files.createDirectories(cache.root().resolve("tools").resolve("doomed"));
        Files.createSymbolicLink(doomed.resolve("link"), outside);

        cache.discard(doomed);

        assertFalse(Files.exists(doomed), "the tree is gone");
        assertTrue(Files.exists(precious), "and what the link pointed at is not");
    }

    @Test
    @DisplayName("a staging directory is created inside its artefact's own staging root")
    void stagingDirectoriesAreCreatedPerArtefact() throws IOException, InterruptedException {
        ToolCache cache = cache();
        Path first =
                cache.createStagingDirectory(
                        ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);
        Path second =
                cache.createStagingDirectory(
                        ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);

        assertNotEquals(first, second, "two attempts must not share a directory");
        assertEquals(
                cache.stagingRoot(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX),
                first.getParent());
        assertTrue(Files.isDirectory(first) && Files.isDirectory(second));
    }

    @Test
    @DisplayName("the cache rejects nulls, naming the argument")
    void nullsAreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new ToolCache(Nulls.of(Path.class), new StreamingHashService()));
        assertThrows(
                NullPointerException.class,
                () ->
                        new ToolCache(
                                temporary, Nulls.of(org.cometgui.domain.ports.HashService.class)));
        ToolCache cache = cache();
        assertThrows(
                NullPointerException.class,
                () ->
                        cache.toolDirectory(
                                Nulls.of(ToolName.class), ToolVersion.parse("1.0"), LINUX));
        assertThrows(
                NullPointerException.class,
                () -> cache.verify(Nulls.of(org.cometgui.install.registry.ArtefactRecord.class)));
    }

    /*
     * Builds a complete cache entry by hand rather than by running the installer: a verification
     * checked against an entry the installer wrote would agree with the installer about any mistake
     * the installer makes.
     */
    private Path install(ToolCache cache, String binary, String schema, int entryCount)
            throws IOException {
        Path directory =
                cache.toolDirectory(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), LINUX);
        Files.createDirectories(directory.resolve("bin"));
        Files.createDirectories(directory.resolve("share/xml"));
        Files.createDirectories(directory.resolve("lib"));
        Files.writeString(directory.resolve("bin/percolator"), binary);
        Files.writeString(directory.resolve("share/xml/schema.xsd"), schema);
        ToolCache.writeMarker(
                directory, markerFor(ToolName.PERCOLATOR, "3.07.1", binary, schema, entryCount));
        return directory;
    }

    private static InstallationMarker markerFor(
            ToolName tool, String version, String binary, String schema, int entryCount) {
        return new InstallationMarker(
                InstallationMarker.SCHEMA_VERSION,
                tool,
                ToolVersion.parse(version),
                LINUX,
                "rel-t",
                URI.create("https://example.invalid/artefact.zip"),
                946303,
                CacheFixtures.hashesOf(CacheFixtures.bytes("the archive")),
                "2026-09-02T11:22:33.444Z",
                "bin/percolator",
                entryCount,
                List.of(ToolCapability.XML_OUTPUT),
                List.of(
                        recorded("bin/percolator", binary),
                        recorded("share/xml/schema.xsd", schema)));
    }

    private static RecordedFile recorded(String path, String content) {
        byte[] bytes = CacheFixtures.bytes(content);
        FileHashes hashes = CacheFixtures.hashesOf(bytes);
        return new RecordedFile(path, bytes.length, hashes);
    }
}
