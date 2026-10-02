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

package org.cometgui.params.comet.writer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code R-PARAM-12}: the canonical file is written once, and its digests are those of the file on
 * disk, computed by the real {@link StreamingHashService} and checked here against {@link
 * MessageDigest} over the bytes read back.
 */
class WriteOnceTest {

    private static final CanonicalParamsWriter WRITER =
            new CanonicalParamsWriter(ParamsFiles.build());

    private static CometParameters realModel() {
        return new CometParamsParser(ParamsFiles.metadata(), ParamsFiles.COMET)
                .parse(ParamsFiles.complete())
                .model()
                .orElseThrow();
    }

    private static String digest(String algorithm, byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }

    @Test
    @DisplayName("the returned digests are MD5 and SHA-256 of the file on disk")
    void digestsAreTheFiles(@TempDir Path directory) throws IOException, NoSuchAlgorithmException {
        Path target = directory.resolve("comet.params");
        WrittenParams written = WRITER.writeOnce(realModel(), target, new StreamingHashService());
        byte[] onDisk = Files.readAllBytes(target);
        assertEquals(target, written.path());
        assertEquals(onDisk.length, written.size());
        assertEquals(digest("MD5", onDisk), written.hashes().md5());
        assertEquals(digest("SHA-256", onDisk), written.hashes().sha256());
        assertArrayEquals(WRITER.bytes(realModel()), onDisk);
        assertTrue(onDisk.length > 10_000, "the file is the whole parameter file");
    }

    @Test
    @DisplayName("the hash service is handed the path after the whole file is on disk")
    void hashedFromDisk(@TempDir Path directory) throws IOException, NoSuchAlgorithmException {
        Path target = directory.resolve("comet.params");
        List<byte[]> seen = new ArrayList<>();
        HashService recording =
                path -> {
                    byte[] bytes = Files.readAllBytes(path);
                    seen.add(bytes);
                    try {
                        return new FileHashes(digest("MD5", bytes), digest("SHA-256", bytes));
                    } catch (NoSuchAlgorithmException impossible) {
                        throw new IOException(impossible);
                    }
                };
        WrittenParams written = WRITER.writeOnce(realModel(), target, recording);
        assertEquals(1, seen.size());
        assertArrayEquals(Files.readAllBytes(target), seen.get(0));
        assertEquals(digest("SHA-256", seen.get(0)), written.hashes().sha256());
    }

    @Test
    @DisplayName("an existing file is never overwritten: written once means once")
    void neverOverwrites(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("comet.params");
        Files.writeString(target, "already here\n", StandardCharsets.UTF_8);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> WRITER.writeOnce(realModel(), target, new StreamingHashService()));
        assertEquals("already here\n", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a refused model writes nothing and hashes nothing")
    void refusedWritesNothing(@TempDir Path directory) {
        Path target = directory.resolve("comet.params");
        CometParameters broken =
                realModel()
                        .withValue(
                                "sample_enzyme_number",
                                new ParameterValue.Whole(99),
                                ValueOrigin.USER);
        List<Path> hashed = new ArrayList<>();
        HashService recording =
                path -> {
                    hashed.add(path);
                    throw new IOException("must not be called");
                };
        ParamsWriteException refusal =
                assertThrows(
                        ParamsWriteException.class,
                        () -> WRITER.writeOnce(broken, target, recording));
        assertEquals("sample_enzyme_number", refusal.parameter());
        assertFalse(Files.exists(target));
        assertEquals(List.of(), hashed);
    }
}
