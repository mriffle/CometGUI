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

package org.cometgui.domain.log;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.cometgui.domain.run.StageTag;
import org.cometgui.domain.testing.FakeStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The flood: phase 02's exit gate item 5, and the half of {@code R-PROC-03} this module owns.
 *
 * <p>A million messages are appended to a default-capacity log from four threads at once, and three
 * things are then proved rather than assumed.
 *
 * <ul>
 *   <li><strong>The cap holds exactly.</strong> {@link BoundedMessageLog#size()} is the capacity,
 *       and size plus {@link BoundedMessageLog#discardedCount()} is the number appended. That
 *       arithmetic is what distinguishes a bounded log from an unbounded one, and it is asserted
 *       instead of building a million-message {@link java.util.ArrayList} as a control -- which
 *       would prove the same thing by nearly exhausting the heap of whatever machine ran it.
 *   <li><strong>What was dropped was the oldest.</strong> The last {@code capacity} messages are
 *       appended by this thread alone, after the four flooding threads have finished, and each
 *       carries its own sequence number; the retained messages must be exactly those, in order,
 *       from the first to the last. A log that kept an arbitrary bounded subset would pass a size
 *       check and fail this one.
 *   <li><strong>The heap did not grow.</strong> Measured, not asserted in prose: see {@link
 *       #HEAP_GROWTH_LIMIT_BYTES} for the arithmetic.
 * </ul>
 *
 * <p>The threads are not decoration. They are the only reason the {@code synchronized} blocks in
 * {@link BoundedMessageLog} can be claimed to work: an unsynchronised {@link java.util.ArrayDeque}
 * under four appenders loses elements and corrupts its indices, and both show up here as a wrong
 * size, a wrong discard count or an exception out of a worker.
 */
class BoundedMessageLogFloodTest {

    /** Total appends in the flood test. The requirement is "at least 1,000,000". */
    private static final int TOTAL_APPENDED = 1_000_000;

    private static final int CAPACITY = BoundedMessageLog.DEFAULT_CAPACITY;

    /** The single-threaded tail: exactly enough messages to fill the log by itself. */
    private static final int TAIL_APPENDED = CAPACITY;

    private static final int FLOOD_APPENDED = TOTAL_APPENDED - TAIL_APPENDED;

    private static final int FLOOD_THREADS = 4;

    private static final int PER_THREAD = FLOOD_APPENDED / FLOOD_THREADS;

    /**
     * The heap-growth bound, 32 MiB, and where that number comes from.
     *
     * <p>One retained message costs, on a 64-bit JVM with compressed references: 32 bytes for the
     * {@link LogMessage} record (header plus four references), 24 for its {@link Instant}, 16 for
     * the {@link java.util.Optional} holding its stage, 24 for the {@link String} header and about
     * 136 for the byte array behind a ~115-character line, plus 4 bytes for the deque slot. Call it
     * 250 bytes, generously rounded up.
     *
     * <p>So the log this test floods retains 10,000 x 250 B = <strong>about 2.5 MB</strong>, while
     * the same million messages in an unbounded list would retain 1,000,000 x 250 B = <strong>about
     * 250 MB</strong>. 32 MiB sits between the two with a wide margin on both sides: it is thirteen
     * times the expected retained size, so garbage-collector slack, JIT structures and the four
     * worker threads cannot push a correct implementation over it; and it is nearly eight times
     * below the unbounded figure, so an implementation that failed to discard could not slip under
     * it. There is no sleep, no retry and no timeout involved -- the measurement is taken once,
     * before and after, with an explicit collection in between.
     */
    private static final long HEAP_GROWTH_LIMIT_BYTES = 32L * 1024L * 1024L;

    /** Padding that makes a message a realistic console line rather than a toy. */
    private static final String FILLER =
            "spectrum scan=00000 charge=2 xcorr=1.2345 deltacn=0.5678 peptide=SAMPLERPEPTIDEK";

    private static final Instant EPOCH = Instant.parse("2026-08-30T00:00:00Z");

    private static final StageTag COMET = FakeStage.named("comet");

    @Test
    @DisplayName("a million messages from four threads: the oldest are discarded, the heap is not")
    void aFloodDiscardsTheOldestAndDoesNotGrowTheHeap()
            throws InterruptedException, ExecutionException {
        BoundedMessageLog log = new BoundedMessageLog();
        List<StageTag> workerStages = workerStages();

        long heapBefore = usedHeapBytes();
        long startedAtNanos = System.nanoTime();

        runConcurrently(
                FLOOD_THREADS,
                worker -> {
                    StageTag stage = workerStages.get(worker);
                    for (int index = 0; index < PER_THREAD; index++) {
                        log.append(message(stage, (long) worker * PER_THREAD + index, "flood"));
                    }
                });

        for (int index = 0; index < TAIL_APPENDED; index++) {
            log.append(message(COMET, (long) FLOOD_APPENDED + index, "tail"));
        }

        long elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        long heapAfter = usedHeapBytes();
        long growth = heapAfter - heapBefore;
        List<LogMessage> retained = log.snapshot();

        System.out.printf(
                "flood: appended=%d capacity=%d size=%d discarded=%d "
                        + "heapBefore=%d heapAfter=%d growth=%d limit=%d elapsedMillis=%d%n",
                TOTAL_APPENDED,
                CAPACITY,
                log.size(),
                log.discardedCount(),
                heapBefore,
                heapAfter,
                growth,
                HEAP_GROWTH_LIMIT_BYTES,
                elapsedMillis);

        assertAll(
                () ->
                        assertEquals(
                                FLOOD_APPENDED, PER_THREAD * FLOOD_THREADS, "flood splits evenly"),
                () -> assertEquals(CAPACITY, log.size(), "size is exactly the capacity"),
                () -> assertEquals(CAPACITY, retained.size(), "the snapshot holds exactly that"),
                () ->
                        assertEquals(
                                (long) TOTAL_APPENDED - CAPACITY,
                                log.discardedCount(),
                                "discarded is appended minus capacity"),
                () ->
                        assertEquals(
                                (long) TOTAL_APPENDED,
                                log.size() + log.discardedCount(),
                                "every append is either retained or counted"),
                () ->
                        assertEquals(
                                textOf("tail", FLOOD_APPENDED),
                                retained.get(0).text(),
                                "the oldest retained message is the first of the tail"),
                () ->
                        assertEquals(
                                textOf("tail", TOTAL_APPENDED - 1L),
                                retained.get(retained.size() - 1).text(),
                                "the newest retained message is the last one appended"),
                () -> assertEquals("", firstOutOfOrder(retained)),
                () ->
                        assertTrue(
                                growth < HEAP_GROWTH_LIMIT_BYTES,
                                () ->
                                        "retained heap grew by "
                                                + growth
                                                + " bytes across "
                                                + TOTAL_APPENDED
                                                + " appends, which is over the documented bound of "
                                                + HEAP_GROWTH_LIMIT_BYTES
                                                + " bytes (heap before "
                                                + heapBefore
                                                + ", after "
                                                + heapAfter
                                                + ")"));
    }

    @Test
    @DisplayName("four threads appending inside the capacity lose nothing and reorder nothing")
    void concurrentAppendsWithinTheCapacityLoseNothing()
            throws InterruptedException, ExecutionException {
        int perThread = 25_000;
        int threads = 4;
        BoundedMessageLog log = new BoundedMessageLog(threads * perThread);
        List<StageTag> workerStages = workerStages();

        runConcurrently(
                threads,
                worker -> {
                    StageTag stage = workerStages.get(worker);
                    for (int index = 0; index < perThread; index++) {
                        log.append(
                                LogMessage.at(
                                        EPOCH.plusMillis(index),
                                        stage,
                                        MessageSeverity.INFO,
                                        "seq=" + index + " from a worker"));
                    }
                });

        List<String> wrong = new ArrayList<>();
        for (int worker = 0; worker < threads; worker++) {
            List<LogMessage> mine =
                    log.snapshotForStage(workerStages.get(worker), MessageSeverity.INFO);
            if (mine.size() != perThread) {
                wrong.add(
                        "worker "
                                + worker
                                + " appended "
                                + perThread
                                + " but "
                                + mine.size()
                                + " are retained");
                continue;
            }
            for (int index = 0; index < perThread; index++) {
                int sequence = sequenceOf(mine.get(index));
                if (sequence != index) {
                    wrong.add("worker " + worker + " message " + index + " is seq=" + sequence);
                    break;
                }
            }
        }

        assertAll(
                () -> assertEquals(threads * perThread, log.size()),
                () -> assertEquals(0L, log.discardedCount()),
                () -> assertEquals(List.of(), wrong));
    }

    private static List<StageTag> workerStages() {
        List<StageTag> stages = new ArrayList<>(FLOOD_THREADS);
        for (int worker = 0; worker < FLOOD_THREADS; worker++) {
            stages.add(FakeStage.named("worker-" + worker));
        }
        return stages;
    }

    private static LogMessage message(StageTag stage, long sequence, String source) {
        Clock clock = Clock.fixed(EPOCH.plusMillis(sequence), ZoneOffset.UTC);
        return LogMessage.recordedBy(clock, stage, MessageSeverity.INFO, textOf(source, sequence));
    }

    private static String textOf(String source, long sequence) {
        return source + " seq=" + sequence + " " + FILLER;
    }

    private static String firstOutOfOrder(List<LogMessage> retained) {
        for (int index = 0; index < retained.size(); index++) {
            String expected = textOf("tail", (long) FLOOD_APPENDED + index);
            String actual = retained.get(index).text();
            if (!expected.equals(actual)) {
                return "position "
                        + index
                        + " should be \""
                        + expected
                        + "\" but was \""
                        + actual
                        + "\"";
            }
        }
        return "";
    }

    private static int sequenceOf(LogMessage message) {
        String text = message.text();
        int start = text.indexOf("seq=") + "seq=".length();
        return Integer.parseInt(text.substring(start, text.indexOf(' ', start)));
    }

    /**
     * Runs {@code body} once per worker, on that many threads, and waits for all of them.
     *
     * <p>The latch makes the overlap real: every worker has arrived before any of them appends, so
     * the appends genuinely interleave rather than happening to run one after another.
     *
     * @param threads how many workers
     * @param body what one worker does, given its index
     * @throws InterruptedException if this thread is interrupted while waiting for the workers
     * @throws ExecutionException wrapping whatever a worker threw, so that a concurrency failure
     *     fails the test rather than disappearing into a dead thread
     */
    private static void runConcurrently(int threads, WorkerBody body)
            throws InterruptedException, ExecutionException {
        CountDownLatch ready = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>(threads);
            for (int worker = 0; worker < threads; worker++) {
                int index = worker;
                tasks.add(
                        () -> {
                            ready.countDown();
                            ready.await();
                            body.run(index);
                            return index;
                        });
            }
            for (Future<Integer> finished : pool.invokeAll(tasks)) {
                finished.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** What one worker thread does. */
    @FunctionalInterface
    private interface WorkerBody {
        void run(int worker);
    }

    /**
     * The heap in use after a full collection: {@code totalMemory - freeMemory}, twice collected.
     *
     * <p>The collection is requested through {@link java.lang.management.MemoryMXBean#gc()}, which
     * the platform documents as equivalent to {@link System#gc()}, because SpotBugs reports a
     * direct {@code System.gc()} as {@code DM_GC} -- "extremely dubious except in benchmarking
     * code" -- at this project's threshold, and the project's rule is that a SpotBugs finding is
     * fixed in the code rather than excluded. The management API says the same thing without the
     * finding. It is requested twice because the first collection can leave objects that only
     * became unreachable during it.
     *
     * @return bytes of heap retained after collection
     */
    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        ManagementFactory.getMemoryMXBean().gc();
        ManagementFactory.getMemoryMXBean().gc();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
