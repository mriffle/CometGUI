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

package org.cometgui.results.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link ForwardCopier}: ranges copied verbatim across its 64 KiB buffer's edges. */
class ForwardCopierTest {

    @TempDir private Path work;

    @Test
    @DisplayName("ranges inside, across and beyond one buffer are copied byte for byte")
    void rangesAcrossBufferEdges() throws IOException {
        byte[] bytes = new byte[200_000];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31 + i / 251);
        }
        Path file = Files.write(work.resolve("bytes"), bytes);
        long[][] ranges = {
            {0, 10},
            {10, 10},
            {10, 65_530},
            {65_530, 65_540},
            {65_540, 131_072},
            {131_072, 131_073},
            {140_000, 199_999},
            {199_999, 200_000}
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ForwardCopier copier = new ForwardCopier(file, channel);
            for (long[] range : ranges) {
                copier.copy(range[0], range[1], out);
                expected.write(bytes, (int) range[0], (int) (range[1] - range[0]));
            }
            copier.copy(5, 70_000, out);
            expected.write(bytes, 5, 70_000 - 5);
        }
        assertArrayEquals(expected.toByteArray(), out.toByteArray());
    }

    @Test
    @DisplayName("a range past the end of the file is refused, naming the file and the offset")
    void pastTheEnd() throws IOException {
        Path file = Files.write(work.resolve("short"), new byte[100]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ForwardCopier copier = new ForwardCopier(file, channel);
            EOFException ended = assertThrows(EOFException.class, () -> copier.copy(90, 110, out));
            assertEquals(
                    "The Percolator result table "
                            + file
                            + " ended at byte 100 while being exported",
                    ended.getMessage());
        }
        assertArrayEquals(new byte[10], out.toByteArray(), "the bytes before the end");
    }
}
