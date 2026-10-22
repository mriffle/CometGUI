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

package org.cometgui.app.e2e.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.cometgui.app.testing.OwnDigests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The digest oracle is {@link OwnDigests}: the test's own streaming {@code MessageDigest} loop,
 * which imports nothing from {@code org.cometgui} (P13-7, reused here under P14-7). Proved against
 * the published test vectors of RFC 1321 and FIPS 180-2, typed out -- including the million-byte
 * one, which crosses the loop's 64 KiB buffer fifteen times and ends part-way through it.
 */
class OwnDigestsOracleTest {

    @TempDir private Path directory;

    private OwnDigests of(byte[] bytes) throws IOException {
        Path file = Files.write(directory.resolve("file-" + bytes.length), bytes);
        return OwnDigests.of(file);
    }

    @Test
    @DisplayName("\"abc\": MD5 9001...7f72, SHA-256 ba78...15ad")
    void abc() throws IOException {
        assertEquals(
                new OwnDigests(
                        3,
                        "900150983cd24fb0d6963f7d28e17f72",
                        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
                of(new byte[] {'a', 'b', 'c'}));
    }

    @Test
    @DisplayName("the empty file: MD5 d41d...427e, SHA-256 e3b0...b855")
    void empty() throws IOException {
        assertEquals(
                new OwnDigests(
                        0,
                        "d41d8cd98f00b204e9800998ecf8427e",
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                of(new byte[0]));
    }

    @Test
    @DisplayName("a million 'a's, across the buffer: MD5 7707...6f21, SHA-256 cdc7...2cd0")
    void aMillionAs() throws IOException {
        byte[] million = new byte[1_000_000];
        Arrays.fill(million, (byte) 'a');
        assertEquals(
                new OwnDigests(
                        1_000_000,
                        "7707d6ae4e027c70eea2a935c2296f21",
                        "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"),
                of(million));
    }
}
