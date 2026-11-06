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

package org.cometgui.app.performance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalInt;
import java.util.SplittableRandom;
import org.cometgui.app.config.RunWiring;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Performance (d): hashing a multi-gigabyte file through the product's hasher -- {@link
 * RunWiring#hasher()}, the one hasher the workflow engine digests every spectrum file with: a cache
 * over the single-pass {@code StreamingHashService}, which reads the file once and feeds MD5 and
 * SHA-256 from the same bytes ({@code R-PROV-01}). Reported as throughput, MiB per second.
 *
 * <p><strong>The file is generated here, never committed and never real data</strong>: {@code
 * hash.bytes} bytes of a seeded pseudo-random sequence, written into the runner's work directory,
 * its MD5 and SHA-256 computed with the JDK's {@link MessageDigest} while it is written -- an
 * independent digest of the same bytes, with no product code in it -- and deleted afterwards. Each
 * sample is one call on a fresh hasher, so the cache never answers; every sample's digests must
 * equal the independent ones, so a hasher that skipped bytes would be red here before it could be
 * fast. The file was just written, so it is read from the page cache: what is measured is the
 * hasher's own cost, the part a code change can make worse, not the disk's.
 *
 * <p>Opt-in (P15-5).
 */
@EnabledIfSystemProperty(
        named = PerfRecorder.ENABLE,
        matches = "true",
        disabledReason = PerfRecorder.DISABLED)
class PerformanceHashing {

    /** The measurement's identifier in the data. */
    static final String ID = "hash-throughput";

    private static final double MIB = 1024.0 * 1024.0;

    private Path work;

    @AfterEach
    void cleanUp() throws IOException {
        PerfRecorder.delete(work);
    }

    @Test
    @DisplayName("a multi-gigabyte generated file hashed by the product's one hasher")
    void hashAMultiGigabyteFile() throws IOException, NoSuchAlgorithmException {
        OptionalInt repeats = PerfRecorder.repeats(ID);
        if (repeats.isEmpty()) {
            return;
        }
        long bytes = PerfRecorder.longParameter("hash.bytes");
        work = PerfRecorder.work("hashing");
        Path file = work.resolve("generated.bin");
        long generateStart = System.nanoTime();
        FileHashes independent = generate(file, bytes);
        double generateMillis = PerfRecorder.millisSince(generateStart);

        PerfRecorder.Measurement measurement = PerfRecorder.start(ID, "MiB/s");
        measurement.evidence("bytes", bytes);
        measurement.evidence("generatedInMillis", Math.round(generateMillis));
        measurement.evidence("independentSha256", independent.sha256());
        int wrong = 0;
        for (int sample = 0; sample < repeats.getAsInt(); sample++) {
            HashService hasher = RunWiring.hasher();
            long start = System.nanoTime();
            FileHashes hashes = hasher.hash(file);
            double millis = PerfRecorder.millisSince(start);
            measurement.sample(bytes / MIB / (millis / 1000.0));
            if (!hashes.equals(independent)) {
                wrong++;
                measurement.evidence("wrongDigests" + sample, hashes.toString());
            }
        }
        measurement.checkEquals(
                "every sample's MD5 and SHA-256 equal the independent digests", 0, wrong);
        measurement.checkEquals("the file's size", bytes, Files.size(file));
        measurement.finish();
    }

    /** Writes the file and returns its digests, computed by the JDK alone as it is written. */
    private static FileHashes generate(Path file, long bytes)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        SplittableRandom random = new SplittableRandom(20261106L);
        ByteBuffer buffer = ByteBuffer.allocate(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
        try (FileChannel channel =
                FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            long remaining = bytes;
            while (remaining > 0) {
                buffer.clear();
                while (buffer.remaining() >= Long.BYTES) {
                    buffer.putLong(random.nextLong());
                }
                int length = (int) Math.min(buffer.capacity(), remaining);
                md5.update(buffer.array(), 0, length);
                sha256.update(buffer.array(), 0, length);
                buffer.flip();
                buffer.limit(length);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                remaining -= length;
            }
        }
        return new FileHashes(
                HexFormat.of().formatHex(md5.digest()), HexFormat.of().formatHex(sha256.digest()));
    }
}
