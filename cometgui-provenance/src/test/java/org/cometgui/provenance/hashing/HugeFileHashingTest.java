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

package org.cometgui.provenance.hashing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exit gate item 2 of phase 04: <em>a 2 GB temporary file hashes in one pass with bounded heap, and
 * both digests match independently computed values.</em>
 *
 * <p>Three separate claims live in that sentence, and each is asserted here as a number rather than
 * described in prose.
 *
 * <ul>
 *   <li><strong>Both digests are right at this size.</strong> The expected values are hand-typed
 *       literals, produced before this module held a hashing class -- see below.
 *   <li><strong>The heap stays bounded.</strong> Not "the JVM did not run out of memory", which a
 *       hasher that retained every chunk would also manage on a machine with a large heap, and not
 *       an allocation count either: <em>retained</em> heap, sampled after collection, throughout
 *       the run, against a documented bound. See {@link #RETAINED_HEAP_LIMIT_BYTES}.
 *   <li><strong>It is still one pass.</strong> One open of the file, and the file's length
 *       delivered to the digests exactly once over -- not twice. Correct digests cannot see the
 *       difference, so the {@link StreamingHashService.FileOpener} seam counts it.
 * </ul>
 *
 * <p><strong>Where the expected digests came from.</strong> Not from Java, and not from the class
 * under test. The 2 GiB corpus is defined in {@code handoffs/PHASE-04-worklog.rst}, and its two
 * digests were produced there by streaming a nine-line Python generator -- which never touches this
 * repository -- through GNU coreutils {@code md5sum}/{@code sha256sum} <em>and</em> through {@code
 * openssl dgst}. Two implementations that share no code with each other or with CometGUI agreed,
 * {@code wc -c} confirmed the byte count as 2147483648, and the agreed values were then typed in
 * here as {@code String} literals. An expected digest obtained by calling {@link
 * StreamingHashService} would agree with the implementation by construction and could not fail.
 *
 * <p><strong>Why this corpus.</strong> The block length, 65521, is prime, so it shares no factor
 * with any power-of-two read buffer. A defect that mishandles a chunk boundary therefore changes
 * the <em>content</em> it digests instead of landing on an identical repeat, and the file length is
 * not a multiple of the block, so the last block is partial. {@link
 * #theCorpusIsTheOneTheReferenceToolsWereGiven()} pins the generator against hand-typed sample
 * bytes, so a drifting generator fails loudly instead of quietly invalidating the digests above.
 *
 * <p><strong>This test runs in the ordinary suite.</strong> It is not tagged, not behind a system
 * property and not conditional on an environment variable: a gate item that does not run is not a
 * gate item. It costs roughly half a minute and about 2 GB of temporary disk, written under {@code
 * java.io.tmpdir} and deleted in a {@code finally} -- never inside the working tree, which {@link
 * #assertOutsideTheWorkingTree(Path)} asserts rather than assumes.
 */
class HugeFileHashingTest {

    // ---------------------------------------------------------------------------------------
    // The corpus, and the digests two independent tools computed over it.
    // ---------------------------------------------------------------------------------------

    /** The corpus length: exactly 2 GiB, and exactly 8192 whole 256 KiB reads. */
    private static final long CORPUS_SIZE_BYTES = 2_147_483_648L;

    /** The repeating block length. Prime, so it is coprime with every power-of-two buffer. */
    private static final int BLOCK_LENGTH = 65_521;

    /** MD5 of the 2 GiB corpus. Hand-typed; see the class Javadoc for its provenance. */
    private static final String MD5_TWO_GIBIBYTES = "222ed00f986369a06082191a1300d095";

    /** SHA-256 of the 2 GiB corpus. Hand-typed; see the class Javadoc for its provenance. */
    private static final String SHA256_TWO_GIBIBYTES =
            "afba2dcb851c0337d7f364e52c88ac7590c5e5b29c6a5c1739cfda4b59ad3be3";

    /**
     * The first sixteen bytes the generator must produce, typed out rather than computed.
     *
     * <p>These pin {@code block[j] = (j * 251 + 17) mod 256} against the definition in the work log
     * instead of against itself. Change the generator and this fails immediately, rather than the
     * digests failing later for a reason nobody can attribute.
     */
    private static final byte[] FIRST_SIXTEEN_BYTES = {
        17,
        12,
        7,
        2,
        (byte) 253,
        (byte) 248,
        (byte) 243,
        (byte) 238,
        (byte) 233,
        (byte) 228,
        (byte) 223,
        (byte) 218,
        (byte) 213,
        (byte) 208,
        (byte) 203,
        (byte) 198
    };

    /** The generator's last byte, {@code block[65520]}, typed out for the same reason. */
    private static final int LAST_BLOCK_BYTE = 97;

    /**
     * The value {@link StreamingHashService#BUFFER_SIZE} is expected to hold, typed out.
     *
     * <p>Every chunk count below is derived from <em>this</em> literal, never from the production
     * constant, so the expectations cannot move with the code. {@link
     * #hashesTwoGibibytesInOnePassWithBoundedHeap()} ties the two together by reflection.
     */
    private static final int BUFFER = 262_144;

    /** Whole buffers in the corpus: 2 GiB / 256 KiB, exactly, with no partial last read. */
    private static final int WHOLE_CHUNKS = 8_192;

    // ---------------------------------------------------------------------------------------
    // The heap bound, which is the part the gate turns on.
    // ---------------------------------------------------------------------------------------

    /**
     * The bound on <em>retained</em> heap growth while a 2 GiB file is hashed: 4 MiB, which is
     * sixteen times {@link StreamingHashService#BUFFER_SIZE}.
     *
     * <p><strong>What a correct pass retains at its peak.</strong> One {@code byte[]} of 262144
     * bytes, reused for every chunk; two {@link MessageDigest} states, a few hundred bytes each;
     * one open stream and its {@link Path}; and this test's own counting opener, whose totals are
     * scalar fields precisely so that the instrument does not show up in its own measurement. That
     * is a little over 256 KiB, and it does not depend on the length of the file. Measured in a
     * Surefire fork with the JaCoCo agent attached, the peak growth was 265072 bytes.
     *
     * <p><strong>Why the other fifteen sixteenths.</strong> The sampler cannot separate the
     * hasher's live objects from the harness's, and several things in a Surefire fork legitimately
     * survive a collection mid-run: the JaCoCo agent's per-class probe arrays, JIT and
     * compiler-thread structures promoted while a fourteen-second loop is optimised, JUnit's
     * descriptors for the executing test, and Surefire's output pumps. 4 MiB leaves a factor of
     * roughly fifteen for all of that.
     *
     * <p><strong>Why it is still tight enough to prove something.</strong> It is 1/512th of the
     * file. A hasher that read the file into memory would exceed it by a factor of 512; one that
     * kept a single chunk in 128 would exceed it by four; one that kept a single chunk in 64 --
     * {@link ChunkRetainingHasher}, the deliberate defect {@link
     * #aHasherThatRetainsEverySixtyFourthChunkFailsTheHeapBound()} runs -- retains 32 MiB and
     * exceeds it by eight, without the JVM ever running short of memory and masking the failure as
     * an {@code OutOfMemoryError}. A "bound" of a gigabyte would pass all three and prove nothing.
     */
    private static final long RETAINED_HEAP_LIMIT_BYTES = 4L * 1024L * 1024L;

    /**
     * The fewest post-collection samples a run must have taken for its peak to mean anything.
     *
     * <p>Without this the whole measurement is vacuous in the one way that matters: a watchdog that
     * never started, or that died on its first sample, reports a peak equal to its baseline, a
     * growth of zero, and a bound that passes while measuring nothing. Hashing 2 GiB takes about
     * fourteen seconds here and the watchdog samples every {@value #SAMPLE_INTERVAL_MILLIS} ms, so
     * a healthy run takes of the order of eighty samples; a machine four times faster still takes
     * more than twenty.
     */
    private static final int MINIMUM_SAMPLES = 8;

    /** Milliseconds between post-collection samples while the hash is running. */
    private static final long SAMPLE_INTERVAL_MILLIS = 100L;

    /** One chunk in this many is retained by the deliberate defect: 8192 / 64 = 128 chunks. */
    private static final int RETAIN_EVERY_NTH_CHUNK = 64;

    // ---------------------------------------------------------------------------------------
    // Gate item 2.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("2 GiB: both digests, one open, one pass over the bytes, bounded retained heap")
    void hashesTwoGibibytesInOnePassWithBoundedHeap() throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("cometgui-huge-file-hashing");
        assertOutsideTheWorkingTree(directory);
        Path corpus = directory.resolve("two-gibibytes.bin");
        try {
            long startedAtNanos = System.nanoTime();
            writeCorpus(corpus);
            long writtenAtNanos = System.nanoTime();

            // The size is asserted BEFORE the hash, so that a truncated or over-long corpus is
            // reported as the wrong file rather than as the wrong digest.
            long size = Files.size(corpus);
            assertEquals(CORPUS_SIZE_BYTES, size, "corpus size in bytes");
            assertArrayEquals(
                    FIRST_SIXTEEN_BYTES, firstBytesOf(corpus, 16), "first sixteen bytes on disk");

            CountingFileOpener opener = new CountingFileOpener();
            StreamingHashService hasher = new StreamingHashService(opener);

            long baseline = settledRetainedHeapBytes();
            RetainedHeapWatchdog watchdog = RetainedHeapWatchdog.startFrom(baseline);
            FileHashes hashes;
            long hashedAtNanos;
            try {
                hashes = hasher.hash(corpus);
                hashedAtNanos = System.nanoTime();
            } finally {
                watchdog.stop();
            }

            long writeMillis = (writtenAtNanos - startedAtNanos) / 1_000_000L;
            long hashMillis = (hashedAtNanos - writtenAtNanos) / 1_000_000L;
            System.out.printf(
                    "huge-file: bytes=%d writeMillis=%d hashMillis=%d totalMillis=%d "
                            + "opens=%d readCalls=%d bytesDelivered=%d "
                            + "heapBaseline=%d heapPeak=%d heapGrowth=%d heapLimit=%d samples=%d%n",
                    size,
                    writeMillis,
                    hashMillis,
                    writeMillis + hashMillis,
                    opener.openCount(),
                    opener.totalReadCalls(),
                    opener.totalBytesDelivered(),
                    watchdog.baselineBytes(),
                    watchdog.peakBytes(),
                    watchdog.peakGrowthBytes(),
                    RETAINED_HEAP_LIMIT_BYTES,
                    watchdog.sampleCount());

            assertAll(
                    // (1) both digests, against values no Java code in this project produced.
                    () -> assertEquals(MD5_TWO_GIBIBYTES, hashes.md5(), "MD5 of the 2 GiB corpus"),
                    () ->
                            assertEquals(
                                    SHA256_TWO_GIBIBYTES,
                                    hashes.sha256(),
                                    "SHA-256 of the 2 GiB corpus"),
                    // (2) bounded RETAINED heap -- the assertion this gate item turns on.
                    () -> assertRetainedHeapStayedBounded(watchdog),
                    // (3) one pass, still, at this size.  The open count is the only thing that
                    // separates a single pass from a discarded first one, and the byte total is
                    // the only thing that separates one pass from two over the same stream.
                    () -> assertEquals(1, opener.openCount(), "open() calls"),
                    () -> assertEquals(List.of(corpus), opener.openedPaths(), "paths opened"),
                    () -> assertEquals(1, opener.streamCount(), "streams handed to the hasher"),
                    () ->
                            assertEquals(
                                    CORPUS_SIZE_BYTES,
                                    opener.totalBytesDelivered(),
                                    "bytes delivered, summed over every stream opened"),
                    () -> assertEquals(1, opener.endOfStreamCount(), "reads returning -1"),
                    () -> assertEquals(1, opener.closeCount(), "close() calls"),
                    // ...and the pass had the shape of a bounded-buffer pass, not a slurp.
                    () -> assertEquals(BUFFER, opener.maximumRequestedLength(), "largest read"),
                    () ->
                            assertEquals(
                                    0,
                                    opener.requestsLongerThanOneBufferCount(),
                                    "reads asking for more than one buffer"),
                    () ->
                            assertEquals(
                                    0,
                                    opener.nonZeroOffsetCount(),
                                    "reads that did not fill the buffer from its start"),
                    () ->
                            assertEquals(
                                    0,
                                    opener.singleByteReadCount(),
                                    "single-byte read() calls (a byte-at-a-time pass)"),
                    // A short read is legal, so the count is bounded rather than pinned: at least
                    // one read per whole chunk plus the one that reports end of stream.
                    () ->
                            assertTrue(
                                    opener.totalReadCalls() >= WHOLE_CHUNKS + 1,
                                    () ->
                                            "read calls "
                                                    + opener.totalReadCalls()
                                                    + " is fewer than the "
                                                    + (WHOLE_CHUNKS + 1)
                                                    + " a bounded-buffer pass needs"),
                    // (4) the production constant really is the number every count above assumed.
                    // Read reflectively: BUFFER_SIZE is a compile-time constant, so a direct
                    // reference would be inlined and the assertion would compare 262144 with
                    // itself no matter what the field says.
                    () ->
                            assertEquals(
                                    BUFFER,
                                    (int)
                                            StreamingHashService.class
                                                    .getField("BUFFER_SIZE")
                                                    .get(null),
                                    "StreamingHashService.BUFFER_SIZE"));
        } finally {
            deleteRecursively(directory);
        }
        assertFalse(Files.exists(directory), "the temporary directory is deleted");
    }

    // ---------------------------------------------------------------------------------------
    // The negative control.  A bound nobody has seen fail is a bound nobody knows is doing
    // anything, and this project has shipped an inert gate more than once.  So the same harness
    // is run over the same file with a hasher that leaks, and the failure is asserted.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the bound is doing work: a hasher that keeps every 64th chunk fails it")
    void aHasherThatRetainsEverySixtyFourthChunkFailsTheHeapBound()
            throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("cometgui-huge-file-retaining");
        assertOutsideTheWorkingTree(directory);
        Path corpus = directory.resolve("two-gibibytes.bin");
        try {
            writeCorpus(corpus);
            assertEquals(CORPUS_SIZE_BYTES, Files.size(corpus), "corpus size in bytes");

            CountingFileOpener opener = new CountingFileOpener();
            ChunkRetainingHasher leaky = new ChunkRetainingHasher(opener);

            long baseline = settledRetainedHeapBytes();
            RetainedHeapWatchdog watchdog = RetainedHeapWatchdog.startFrom(baseline);
            FileHashes hashes;
            try {
                hashes = leaky.hash(corpus);
            } finally {
                watchdog.stop();
            }

            AssertionError failure =
                    assertThrows(
                            AssertionError.class,
                            () -> assertRetainedHeapStayedBounded(watchdog),
                            "the heap bound accepted a hasher retaining 32 MiB of chunks");

            System.out.printf(
                    "huge-file-control: keptChunks=%d keptBytes=%d "
                            + "heapBaseline=%d heapPeak=%d heapGrowth=%d heapLimit=%d samples=%d%n",
                    leaky.keptChunkCount(),
                    leaky.keptByteCount(),
                    watchdog.baselineBytes(),
                    watchdog.peakBytes(),
                    watchdog.peakGrowthBytes(),
                    RETAINED_HEAP_LIMIT_BYTES,
                    watchdog.sampleCount());

            assertAll(
                    // The defect is invisible to every correctness assertion there is: the leaky
                    // hasher reads the file once and returns exactly the right digests.  That is
                    // the whole argument for measuring the heap at all.
                    () -> assertEquals(MD5_TWO_GIBIBYTES, hashes.md5(), "MD5 from the leaky pass"),
                    () ->
                            assertEquals(
                                    SHA256_TWO_GIBIBYTES,
                                    hashes.sha256(),
                                    "SHA-256 from the leaky pass"),
                    () -> assertEquals(1, opener.openCount(), "open() calls in the leaky pass"),
                    () ->
                            assertEquals(
                                    CORPUS_SIZE_BYTES,
                                    opener.totalBytesDelivered(),
                                    "bytes delivered in the leaky pass"),
                    // It retained one chunk in 64, which is 128 chunks and 32 MiB.
                    () ->
                            assertEquals(
                                    WHOLE_CHUNKS / RETAIN_EVERY_NTH_CHUNK,
                                    leaky.keptChunkCount(),
                                    "chunks retained"),
                    () ->
                            assertEquals(
                                    (long) WHOLE_CHUNKS / RETAIN_EVERY_NTH_CHUNK * BUFFER,
                                    leaky.keptByteCount(),
                                    "bytes retained"),
                    // ...and the bound saw it, said so in bytes, and named the limit it broke.
                    () ->
                            assertTrue(
                                    failure.getMessage().contains("retained heap grew by"),
                                    () -> "failure message was: " + failure.getMessage()),
                    () ->
                            assertTrue(
                                    failure.getMessage()
                                            .contains(String.valueOf(RETAINED_HEAP_LIMIT_BYTES)),
                                    () -> "failure message was: " + failure.getMessage()),
                    // The peak really is the retention and not sampling noise: at least 16 MiB of
                    // the 32 MiB the leak holds was still live after a collection.
                    () ->
                            assertTrue(
                                    watchdog.peakGrowthBytes() >= 16L * 1024L * 1024L,
                                    () ->
                                            "retained heap grew by only "
                                                    + watchdog.peakGrowthBytes()
                                                    + " bytes, which is too little to be the "
                                                    + leaky.keptByteCount()
                                                    + " bytes the leak holds"));
        } finally {
            deleteRecursively(directory);
        }
    }

    // ---------------------------------------------------------------------------------------
    // The corpus is the one the reference tools were given.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the generator reproduces the block the reference tools hashed")
    void theCorpusIsTheOneTheReferenceToolsWereGiven() {
        byte[] block = corpusBlock();

        assertAll(
                () -> assertEquals(BLOCK_LENGTH, block.length, "block length"),
                () ->
                        assertArrayEquals(
                                FIRST_SIXTEEN_BYTES,
                                Arrays.copyOf(block, 16),
                                "the generator's first sixteen bytes"),
                () ->
                        assertEquals(
                                LAST_BLOCK_BYTE,
                                Byte.toUnsignedInt(block[BLOCK_LENGTH - 1]),
                                "block[65520]"),
                // 65521 is prime, so it is coprime with 262144 and with every other power of two:
                // no chunk boundary can ever land on the same offset within the block twice.
                () ->
                        assertEquals(
                                1,
                                greatestCommonDivisor(BLOCK_LENGTH, BUFFER),
                                "gcd(block length, buffer size)"),
                // ...and the file length is not a multiple of the block, so the last block is
                // partial, which is the case a generator that only ever emits whole blocks misses.
                () ->
                        assertEquals(
                                32_873L,
                                CORPUS_SIZE_BYTES % BLOCK_LENGTH,
                                "bytes in the final, partial block"),
                () ->
                        assertEquals(
                                (long) WHOLE_CHUNKS * BUFFER,
                                CORPUS_SIZE_BYTES,
                                "the corpus is a whole number of read buffers"));
    }

    // ---------------------------------------------------------------------------------------
    // Retained-heap measurement.
    // ---------------------------------------------------------------------------------------

    /**
     * Live heap bytes, measured <em>after</em> a collection.
     *
     * <p>{@link MemoryPoolMXBean#getCollectionUsage()} reports what each heap pool held when the
     * JVM last reclaimed it, which is exactly the "retained" in "bounded retained heap". The plain
     * {@code getHeapMemoryUsage()} includes garbage that has not been collected yet, and over a run
     * that allocates would report noise a hundred times larger than the thing being bounded.
     *
     * <p>A collection has to be asked for, or the figure is stale: this JVM runs G1 with a 30 GB
     * maximum heap, and a correct pass over a 2 GiB file allocates one 256 KiB buffer and nothing
     * else, so it can complete without G1 ever choosing to collect and without {@code
     * getCollectionUsage()} ever being updated. The request goes through {@link
     * java.lang.management.MemoryMXBean#gc()} rather than {@code System.gc()} -- the same API the
     * measurement itself uses, and, as it happens, the form SpotBugs does not report as {@code
     * DM_GC}, so no exclusion has to be added to make this file pass. It is issued twice because
     * the first collection can promote objects that the second then reclaims.
     */
    private static long retainedHeapBytes() {
        ManagementFactory.getMemoryMXBean().gc();
        ManagementFactory.getMemoryMXBean().gc();
        long total = 0L;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            MemoryUsage afterCollection = pool.getCollectionUsage();
            if (afterCollection != null) {
                total += afterCollection.getUsed();
            }
        }
        return total;
    }

    /**
     * A baseline that has stopped moving, so that growth measures the hash and not the run-up.
     *
     * <p>Writing 2 GiB immediately beforehand leaves a write buffer and a good deal of I/O
     * machinery to be reclaimed. Sampling until two consecutive readings agree to within 64 KiB
     * takes the baseline after that has settled; the loop is bounded so that a JVM which never
     * settles fails the sample-count assertion rather than hanging.
     */
    private static long settledRetainedHeapBytes() {
        long previous = retainedHeapBytes();
        for (int attempt = 0; attempt < 8; attempt++) {
            long current = retainedHeapBytes();
            if (Math.abs(current - previous) <= 65_536L) {
                return current;
            }
            previous = current;
        }
        return previous;
    }

    /**
     * Asserts the peak retained heap observed during a run stayed under {@link
     * #RETAINED_HEAP_LIMIT_BYTES}, and that the run was actually sampled.
     *
     * <p>The sample-count check comes first and is not decoration. A watchdog that never started,
     * or that died on its first sample, reports a peak equal to its baseline and a growth of zero,
     * and the bound below would then pass while measuring nothing at all.
     *
     * @param watchdog the finished watchdog whose samples are being judged
     */
    private static void assertRetainedHeapStayedBounded(RetainedHeapWatchdog watchdog) {
        assertNull(watchdog.samplerFailure(), "the retained-heap sampler failed");
        assertTrue(
                watchdog.sampleCount() >= MINIMUM_SAMPLES,
                () ->
                        "the retained-heap watchdog took only "
                                + watchdog.sampleCount()
                                + " samples, fewer than the "
                                + MINIMUM_SAMPLES
                                + " a measured run needs; its peak means nothing");
        assertTrue(
                watchdog.peakGrowthBytes() <= RETAINED_HEAP_LIMIT_BYTES,
                () ->
                        "retained heap grew by "
                                + watchdog.peakGrowthBytes()
                                + " bytes (baseline "
                                + watchdog.baselineBytes()
                                + " bytes, peak "
                                + watchdog.peakBytes()
                                + " bytes, over "
                                + watchdog.sampleCount()
                                + " post-collection samples) over the documented bound of "
                                + RETAINED_HEAP_LIMIT_BYTES
                                + " bytes");
    }

    /**
     * Samples retained heap on its own thread for as long as something else is running, and
     * remembers the largest reading.
     *
     * <p>A before-and-after pair would not do here. The thing being bounded is the <em>peak</em>,
     * and a hasher that read the whole file into memory and released it before returning would show
     * a before and an after that matched perfectly.
     */
    private static final class RetainedHeapWatchdog {

        private final long baselineBytes;
        private final AtomicLong peakBytes;
        private final AtomicLong samples = new AtomicLong();
        private final Thread sampler;
        private volatile boolean running = true;
        private volatile Throwable samplerFailure;

        private RetainedHeapWatchdog(long baselineBytes) {
            this.baselineBytes = baselineBytes;
            this.peakBytes = new AtomicLong(baselineBytes);
            this.sampler = new Thread(this::sampleUntilStopped, "retained-heap-watchdog");
            this.sampler.setDaemon(true);
        }

        static RetainedHeapWatchdog startFrom(long baselineBytes) {
            RetainedHeapWatchdog watchdog = new RetainedHeapWatchdog(baselineBytes);
            watchdog.sampler.start();
            return watchdog;
        }

        private void sampleUntilStopped() {
            try {
                while (running) {
                    sample();
                    Thread.sleep(SAMPLE_INTERVAL_MILLIS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException | Error e) {
                samplerFailure = e;
            }
        }

        private void sample() {
            long observed = retainedHeapBytes();
            peakBytes.accumulateAndGet(observed, Math::max);
            samples.incrementAndGet();
        }

        /** Stops sampling, and takes one final reading with the measured work still referenced. */
        void stop() throws InterruptedException {
            running = false;
            sampler.interrupt();
            sampler.join(30_000L);
            sample();
        }

        long baselineBytes() {
            return baselineBytes;
        }

        long peakBytes() {
            return peakBytes.get();
        }

        long peakGrowthBytes() {
            return peakBytes.get() - baselineBytes;
        }

        long sampleCount() {
            return samples.get();
        }

        Throwable samplerFailure() {
            return samplerFailure;
        }
    }

    // ---------------------------------------------------------------------------------------
    // The deliberate defect the bound exists to catch.
    // ---------------------------------------------------------------------------------------

    /**
     * {@link StreamingHashService}'s loop, byte for byte, plus one line that keeps every 64th
     * chunk.
     *
     * <p>It opens the file once, reads it once, updates both digests from every chunk and returns
     * the correct MD5 and SHA-256. Every assertion in {@link StreamingHashServiceTest} would pass
     * against it. What it also does is hold 128 chunks of 256 KiB -- 32 MiB for a 2 GiB file, and
     * 32 GiB for a 2 TiB one -- which is exactly the defect {@code R-PROV-01} forbids and exactly
     * the defect no digest comparison can see.
     *
     * <p>The proportion is chosen so that the leak is large enough to break a 4 MiB bound by a
     * factor of eight and small enough that the JVM never runs short of memory: if it died with an
     * {@code OutOfMemoryError} the assertion would not have been shown to fail, the JVM would have.
     */
    private static final class ChunkRetainingHasher {

        private final StreamingHashService.FileOpener opener;
        private final List<byte[]> kept = new ArrayList<>();

        ChunkRetainingHasher(StreamingHashService.FileOpener opener) {
            this.opener = opener;
        }

        FileHashes hash(Path path) throws IOException {
            MessageDigest md5 = StreamingHashService.newDigest("MD5");
            MessageDigest sha256 = StreamingHashService.newDigest("SHA-256");
            byte[] buffer = new byte[StreamingHashService.BUFFER_SIZE];
            int chunk = 0;
            try (InputStream stream = opener.open(path)) {
                int read;
                while ((read = stream.read(buffer, 0, buffer.length)) != -1) {
                    md5.update(buffer, 0, read);
                    sha256.update(buffer, 0, read);
                    if (chunk % RETAIN_EVERY_NTH_CHUNK == 0) {
                        kept.add(Arrays.copyOf(buffer, read));
                    }
                    chunk++;
                }
            }
            HexFormat hex = HexFormat.of();
            return new FileHashes(hex.formatHex(md5.digest()), hex.formatHex(sha256.digest()));
        }

        int keptChunkCount() {
            return kept.size();
        }

        long keptByteCount() {
            return kept.stream().mapToLong(chunk -> chunk.length).sum();
        }
    }

    // ---------------------------------------------------------------------------------------
    // The counting seam, and the corpus generator.
    // ---------------------------------------------------------------------------------------

    /**
     * The same {@link StreamingHashService.FileOpener} seam {@link StreamingHashServiceTest} uses,
     * with one difference: every total is a scalar field rather than a list.
     *
     * <p>{@code StreamingHashServiceTest} records each read into {@link List}s, which is the right
     * shape when the largest file is 786432 bytes. Here there are 8193 reads, and three lists of
     * boxed {@link Integer}s holding them would retain several hundred kilobytes of the four
     * megabytes this test is trying to bound -- the instrument would appear in its own measurement.
     * Counters retain nothing that grows with the file.
     */
    private static final class CountingFileOpener implements StreamingHashService.FileOpener {

        private final List<Path> openedPaths = new ArrayList<>();
        private int streams;
        private long readCalls;
        private long bytesDelivered;
        private long endOfStreamReads;
        private long singleByteReads;
        private long requestsLongerThanOneBuffer;
        private long nonZeroOffsets;
        private int maximumRequestedLength = -1;
        private int closes;

        @Override
        public InputStream open(Path path) throws IOException {
            openedPaths.add(path);
            // Files.newInputStream throws for a missing file, exactly as production does, and
            // leaves the stream count alone -- so an attempted open still counts as an open.
            InputStream opened = Files.newInputStream(path);
            streams++;
            return new CountingInputStream(this, opened);
        }

        private void recordRead(int offset, int length, int returned) {
            readCalls++;
            if (returned > 0) {
                bytesDelivered += returned;
            } else if (returned == -1) {
                endOfStreamReads++;
            }
            if (length > BUFFER) {
                requestsLongerThanOneBuffer++;
            }
            if (offset != 0) {
                nonZeroOffsets++;
            }
            if (length > maximumRequestedLength) {
                maximumRequestedLength = length;
            }
        }

        private void recordSingleByteRead() {
            singleByteReads++;
        }

        private void recordClose() {
            closes++;
        }

        int openCount() {
            return openedPaths.size();
        }

        List<Path> openedPaths() {
            return List.copyOf(openedPaths);
        }

        int streamCount() {
            return streams;
        }

        long totalReadCalls() {
            return readCalls;
        }

        long totalBytesDelivered() {
            return bytesDelivered;
        }

        long endOfStreamCount() {
            return endOfStreamReads;
        }

        long singleByteReadCount() {
            return singleByteReads;
        }

        long requestsLongerThanOneBufferCount() {
            return requestsLongerThanOneBuffer;
        }

        long nonZeroOffsetCount() {
            return nonZeroOffsets;
        }

        int maximumRequestedLength() {
            return maximumRequestedLength;
        }

        int closeCount() {
            return closes;
        }
    }

    /** A stream that reports every read and every close to the opener that handed it out. */
    private static final class CountingInputStream extends InputStream {

        private final CountingFileOpener owner;
        private final InputStream delegate;

        CountingInputStream(CountingFileOpener owner, InputStream delegate) {
            this.owner = owner;
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            owner.recordSingleByteRead();
            return delegate.read();
        }

        @Override
        public int read(byte[] destination, int offset, int length) throws IOException {
            int returned = delegate.read(destination, offset, length);
            owner.recordRead(offset, length, returned);
            return returned;
        }

        @Override
        public void close() throws IOException {
            owner.recordClose();
            delegate.close();
        }
    }

    /**
     * The 65521-byte block, {@code block[j] = (j * 251 + 17) mod 256}.
     *
     * <p>This is the definition in {@code handoffs/PHASE-04-worklog.rst}, transcribed. {@link
     * #theCorpusIsTheOneTheReferenceToolsWereGiven()} pins it against hand-typed sample bytes so
     * that a transcription error fails on its own terms rather than as a mysterious digest
     * mismatch.
     */
    private static byte[] corpusBlock() {
        byte[] block = new byte[BLOCK_LENGTH];
        for (int j = 0; j < BLOCK_LENGTH; j++) {
            block[j] = (byte) ((j * 251 + 17) % 256);
        }
        return block;
    }

    /**
     * Writes the 2 GiB corpus: the block, repeated, truncated to exactly {@link
     * #CORPUS_SIZE_BYTES}.
     *
     * <p>The write buffer holds a whole number of blocks, so each full write leaves the stream on a
     * block boundary and the pattern continues seamlessly across writes; the last write is a prefix
     * of the same buffer, which is what makes the final block partial. Writing a block at a time --
     * let alone a byte at a time -- would dominate the runtime of the test.
     *
     * @param corpus where to write it
     * @throws IOException if the file cannot be written
     */
    private static void writeCorpus(Path corpus) throws IOException {
        byte[] block = corpusBlock();
        int blocksPerWrite = 16;
        byte[] writeBuffer = new byte[BLOCK_LENGTH * blocksPerWrite];
        for (int repeat = 0; repeat < blocksPerWrite; repeat++) {
            System.arraycopy(block, 0, writeBuffer, repeat * BLOCK_LENGTH, BLOCK_LENGTH);
        }
        try (OutputStream out = Files.newOutputStream(corpus)) {
            long remaining = CORPUS_SIZE_BYTES;
            while (remaining > 0L) {
                int chunk = (int) Math.min(remaining, writeBuffer.length);
                out.write(writeBuffer, 0, chunk);
                remaining -= chunk;
            }
        }
    }

    private static byte[] firstBytesOf(Path file, int count) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(count);
        }
    }

    /**
     * Asserts a path is neither inside the module nor inside the repository above it.
     *
     * <p>Two gigabytes written into the working tree would be swept into a commit by anything less
     * careful than an explicit pathspec, and would survive a failed run. Surefire's working
     * directory is the module base directory, so its parent is the repository root.
     *
     * @param path the temporary directory this test is about to fill
     */
    private static void assertOutsideTheWorkingTree(Path path) {
        Path resolved = path.toAbsolutePath().normalize();
        Path module = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repository = module.getParent() == null ? module : module.getParent();
        Path temporaryRoot =
                Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

        assertAll(
                () ->
                        assertFalse(
                                resolved.startsWith(module),
                                () -> resolved + " is inside the module directory " + module),
                () ->
                        assertFalse(
                                resolved.startsWith(repository),
                                () -> resolved + " is inside the repository " + repository),
                () ->
                        assertTrue(
                                resolved.startsWith(temporaryRoot),
                                () -> resolved + " is not under java.io.tmpdir " + temporaryRoot));
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                Files.delete(entry);
            }
        }
        Files.delete(directory);
    }

    private static int greatestCommonDivisor(int a, int b) {
        int left = a;
        int right = b;
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }
}
