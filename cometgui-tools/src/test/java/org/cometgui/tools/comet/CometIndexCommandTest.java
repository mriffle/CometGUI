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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.cometgui.domain.run.IndexMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link CometIndexCommand}: the argument arrays hand-typed, the link made with NIO, and the
 * refusals. The real binary builds through it in {@link CometAdapterRealBinaryTest}.
 */
class CometIndexCommandTest {

    static final Path COMET = TestPaths.absolute("tools/comet/comet");

    static final Path PARAMS = TestPaths.absolute("projects/p/runs/r 1/parameters/comet.params");

    static final Path CACHE = TestPaths.absolute("projects/p/index-cache/key 1");

    static final Path FASTA = TestPaths.absolute("data/human proteome/UP000005640_9606.fasta");

    private static CometIndexCommand of(IndexMode mode) {
        return new CometIndexCommand(COMET, PARAMS, mode, CACHE, FASTA);
    }

    @Test
    @DisplayName("-i: -P, -i, -D naming the link inside the cache; run in the cache")
    void fragmentIon() {
        CometIndexCommand index = of(IndexMode.FRAGMENT_ION);
        assertEquals(
                List.of(
                        "/tools/comet/comet",
                        "-P/projects/p/runs/r 1/parameters/comet.params",
                        "-i",
                        "-D/projects/p/index-cache/key 1/UP000005640_9606.fasta"),
                index.command().argv());
        assertEquals(CACHE, index.command().workingDirectory());
        assertEquals(Map.of("LANG", "C.UTF-8"), index.command().environment());
        assertEquals(
                TestPaths.absolute("projects/p/index-cache/key 1/UP000005640_9606.fasta"),
                index.databaseLink());
        assertEquals(
                TestPaths.absolute("projects/p/index-cache/key 1/UP000005640_9606.fasta.idx"),
                index.indexFile());
    }

    @Test
    @DisplayName("-j: the peptide index has the same shape with -j")
    void peptide() {
        assertEquals(
                List.of(
                        "/tools/comet/comet",
                        "-P/projects/p/runs/r 1/parameters/comet.params",
                        "-j",
                        "-D/projects/p/index-cache/key 1/UP000005640_9606.fasta"),
                of(IndexMode.PEPTIDE).command().argv());
    }

    @Test
    @DisplayName("NONE builds no index and is refused")
    void noneRefused() {
        assertEquals(
                "index mode none builds no index, so it has no command",
                assertThrows(IllegalArgumentException.class, () -> of(IndexMode.NONE))
                        .getMessage());
    }

    @Test
    @DisplayName("relative paths, and a FASTA with no file name, are refused")
    void refusals() {
        Path relative = Path.of("x/y");
        assertEquals(
                "the Comet executable must be an absolute path, not \"x/y\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CometIndexCommand(
                                                relative, PARAMS, IndexMode.PEPTIDE, CACHE, FASTA))
                        .getMessage());
        assertEquals(
                "the parameter file must be an absolute path, not \"x/y\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CometIndexCommand(
                                                COMET, relative, IndexMode.PEPTIDE, CACHE, FASTA))
                        .getMessage());
        assertEquals(
                "the index cache directory must be an absolute path, not \"x/y\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CometIndexCommand(
                                                COMET, PARAMS, IndexMode.PEPTIDE, relative, FASTA))
                        .getMessage());
        assertEquals(
                "the FASTA file must be an absolute path, not \"x/y\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CometIndexCommand(
                                                COMET, PARAMS, IndexMode.PEPTIDE, CACHE, relative))
                        .getMessage());
        assertEquals(
                "the FASTA path / has no file name",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CometIndexCommand(
                                                COMET,
                                                PARAMS,
                                                IndexMode.PEPTIDE,
                                                CACHE,
                                                TestPaths.absolute("")))
                        .getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName(
            "the link is created inside the cache, points at the FASTA, and nothing else moves")
    void linkCreated(@TempDir Path directory) throws IOException {
        Path data = Files.createDirectories(directory.resolve("data"));
        Path fasta = Files.writeString(data.resolve("db.fasta"), ">p1\nPEPTIDE\n");
        Path cache = Files.createDirectories(directory.resolve("cache"));
        CometIndexCommand index =
                new CometIndexCommand(COMET, PARAMS, IndexMode.FRAGMENT_ION, cache, fasta);

        assertEquals(cache.resolve("db.fasta"), index.linkDatabase());
        assertTrue(Files.isSymbolicLink(cache.resolve("db.fasta")));
        assertEquals(fasta, Files.readSymbolicLink(cache.resolve("db.fasta")));
        assertEquals(">p1\nPEPTIDE\n", Files.readString(cache.resolve("db.fasta")));
        try (Stream<Path> beside = Files.list(data)) {
            assertEquals(List.of(fasta), beside.toList());
        }
        // a retry in the same cache keeps its own link
        assertEquals(cache.resolve("db.fasta"), index.linkDatabase());
        assertEquals(fasta, Files.readSymbolicLink(cache.resolve("db.fasta")));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("a link to another FASTA, or a file, at the link's name is refused and kept")
    void conflictsRefused(@TempDir Path directory) throws IOException {
        Path fasta = Files.writeString(directory.resolve("db.fasta"), ">p1\nPEPTIDE\n");
        Path other = Files.writeString(directory.resolve("other.fasta"), ">p2\nPEPTIDES\n");
        Path linked = Files.createDirectories(directory.resolve("linked"));
        Files.createSymbolicLink(linked.resolve("db.fasta"), other);
        FileAlreadyExistsException toOther =
                assertThrows(
                        FileAlreadyExistsException.class,
                        () ->
                                new CometIndexCommand(
                                                COMET, PARAMS, IndexMode.PEPTIDE, linked, fasta)
                                        .linkDatabase());
        assertEquals(
                linked.resolve("db.fasta")
                        + " -> "
                        + fasta
                        + ": the index cache already holds a link of this name to "
                        + other,
                toOther.getMessage());
        assertEquals(other, Files.readSymbolicLink(linked.resolve("db.fasta")));

        Path filed = Files.createDirectories(directory.resolve("filed"));
        Files.writeString(filed.resolve("db.fasta"), "not a link");
        FileAlreadyExistsException toFile =
                assertThrows(
                        FileAlreadyExistsException.class,
                        () ->
                                new CometIndexCommand(
                                                COMET, PARAMS, IndexMode.PEPTIDE, filed, fasta)
                                        .linkDatabase());
        assertEquals(
                filed.resolve("db.fasta")
                        + " -> "
                        + fasta
                        + ": the index cache already holds a file of this name that is not a link",
                toFile.getMessage());
        assertEquals("not a link", Files.readString(filed.resolve("db.fasta")));
        assertFalse(Files.isSymbolicLink(filed.resolve("db.fasta")));
    }

    @Test
    @DisplayName("a file system without symbolic links fails naming the link, never copying")
    void unsupportedFileSystem(@TempDir Path directory) throws IOException {
        URI zip = URI.create("jar:" + directory.resolve("cache.zip").toUri());
        try (FileSystem archive = FileSystems.newFileSystem(zip, Map.of("create", "true"))) {
            Path top = archive.getRootDirectories().iterator().next();
            Path cache = Files.createDirectories(top.resolve("cache"));
            Path fasta = top.resolve("db.fasta");
            Files.writeString(fasta, ">p1\nPEPTIDE\n");
            IOException refused =
                    assertThrows(
                            IOException.class,
                            () ->
                                    new CometIndexCommand(
                                                    COMET,
                                                    PARAMS,
                                                    IndexMode.FRAGMENT_ION,
                                                    cache,
                                                    fasta)
                                            .linkDatabase());
            assertEquals(
                    "cannot create the symbolic link /cache/db.fasta to the FASTA /db.fasta: this"
                            + " file system does not support symbolic links, and Comet would"
                            + " otherwise write its index beside the FASTA",
                    refused.getMessage());
            assertInstanceOf(UnsupportedOperationException.class, refused.getCause());
            assertFalse(Files.exists(cache.resolve("db.fasta")));
        }
    }
}
