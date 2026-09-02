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

package org.cometgui.tools.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.run.StageTag;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.tools.process.fakes.FakeTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PHASE-03 exit gate item 3: a fake emitting 500 MB of stdout completes, with a bounded heap and a
 * complete on-disk log.
 *
 * <p>Three separate claims, asserted separately, because a test that ran the flood and then checked
 * one of them would let the other two rot.
 *
 * <h2>1. It completes</h2>
 *
 * <p>Exit code exactly 0, reported through {@link StageOutcome}, with no cancellation and no
 * timeout. A run that died half way through would still leave a large, plausible log file.
 *
 * <h2>2. The heap stays bounded, and that is measured rather than asserted in prose</h2>
 *
 * <p>Retained heap is read before and after, after a full collection each time, exactly as {@code
 * BoundedMessageLogFloodTest} in {@code cometgui-domain} does for phase 02's own flood. The bound
 * is {@link #HEAP_GROWTH_LIMIT_BYTES}, hand-typed, and the arithmetic behind it is on that field.
 * The measurement is printed whether it passes or fails, because a number nobody can see is not
 * evidence.
 *
 * <h2>3. The on-disk log is complete, and "complete" is not a file size</h2>
 *
 * <p>A 700 MB file proves nothing on its own, and neither does comparing it with an expected file:
 * the second is a single assertion that fails identically for a missing line, a repeated line and a
 * changed byte, and it needs another 700 MB to compare against. What is asserted instead is:
 *
 * <ul>
 *   <li><strong>the number of stdout lines, from three independent derivations</strong> -- the
 *       count this test computes from the flood size and the line length, the count the fake itself
 *       reports on standard error as {@code lines <n>}, and a hand-typed literal. All three must
 *       agree with what is on disk;
 *   <li><strong>the exact text of the first line, the last line and six interior lines</strong>,
 *       each at a position chosen here;
 *   <li><strong>that the ordinals form a contiguous run from 0 with no gap and no repeat.</strong>
 *       This is the assertion that catches a <em>count-preserving</em> corruption -- line 42
 *       replaced by a repeat of line 41 -- which every check on size, and every check on a sampled
 *       element, passes. That exact defect shape has been injected twice in this phase and caught
 *       both times.
 * </ul>
 *
 * <p>And the other half of {@code R-PROC-03}: while the file kept everything, the in-memory console
 * kept exactly its capacity and counted the rest as discarded. The disk is the record; the console
 * is a view of it.
 *
 * <p><strong>There is no fixed sleep here</strong> (exit gate item 6). The single wait is the
 * outcome future, which completes when the stage does.
 */
class FiveHundredMegabyteFloodTest {

    /** 500 MiB: 524,288,000 bytes, hand-computed. */
    private static final long FLOOD_BYTES = 524_288_000L;

    /** The characters of one flood line, excluding its newline. */
    private static final int LINE_LENGTH = 100;

    /**
     * The number of lines that makes: 5,190,971, hand-typed.
     *
     * <p>A line costs {@code LINE_LENGTH + 1 = 101} bytes including its newline, and the fake emits
     * whole lines until at least {@link #FLOOD_BYTES} have gone out, so the count is the ceiling of
     * 524,288,000 / 101 -- 5,190,970 lines would be 524,288,-something short. This literal is one
     * of three independent derivations; see {@link
     * #theFloodCompletesWithABoundedHeapAndACompleteLog}.
     */
    private static final long EXPECTED_LINES = 5_190_971L;

    /**
     * The console's capacity for this run, stated here rather than taken from the domain default.
     */
    private static final int CONSOLE_CAPACITY = 10_000;

    /**
     * How many messages the console must report as discarded: 5,180,972, hand-computed.
     *
     * <p>{@link #EXPECTED_LINES} stdout lines plus the fake's one {@code lines <n>} summary on
     * standard error, minus the {@link #CONSOLE_CAPACITY} that are kept.
     */
    private static final long EXPECTED_DISCARDED = 5_180_972L;

    /**
     * The heap-growth bound, 32 MiB, and where the number comes from.
     *
     * <p>What a correct run retains is the console and nothing else that scales: 10,000 messages at
     * roughly 250 bytes each -- the {@code LogMessage} record, its {@code Instant}, the {@code
     * Optional} holding its stage, and the byte array behind a 100-character line -- is about
     * <strong>2.5 MB</strong>. Everything else in the path is fixed: one 8,192-character read
     * buffer per pump, one line splitter buffer capped at 65,536 characters, and the log file's
     * writer. Two measurements of exactly this run, taken while writing this test: 7,066,144 bytes
     * standalone, where the growth is measured from a cold JVM, and 1,978,184 bytes inside the
     * module's own suite, where the collector has already sized itself for the run.
     *
     * <p>What an <em>unbounded</em> console would retain is 5,190,972 messages at the same 250
     * bytes: about <strong>1.30 GB</strong>. So this bound is between four and seventeen times the
     * two measured figures -- enough that collector slack and JIT structures cannot push a correct
     * implementation over it -- and roughly forty times below the figure that removing the eviction
     * would reach. A bound so generous that nothing could exceed it would not be a gate.
     */
    private static final long HEAP_GROWTH_LIMIT_BYTES = 32L * 1024L * 1024L;

    /** The instant the fixed clock reports, so every log line's prefix is known in advance. */
    private static final Instant AT = Instant.parse("2026-08-31T19:04:51.250Z");

    /** Its rendering in a log line. Typed by hand. */
    private static final String AT_TEXT = "2026-08-31T19:04:51.250Z";

    /** What every line of the tool's standard output looks like before its text. Hand-typed. */
    private static final String STDOUT_PREFIX = AT_TEXT + " [stdout] ";

    /** The same for standard error. */
    private static final String STDERR_PREFIX = AT_TEXT + " [stderr] ";

    /** The same for the lines the process service writes itself. */
    private static final String SERVICE_PREFIX = AT_TEXT + " [cometgui] ";

    /**
     * The 89 padding characters after the ordinal and its space on a 100-character flood line.
     *
     * <p>Hand-typed, exactly as {@code FakeToolSelfTest} and {@code ProcessCancellationTest} type
     * it, so no expectation here is derived from another test or from the code under test.
     */
    private static final String FLOOD_PADDING =
            "01234567890123456789012345678901234567890123456789012345678901234567890123456789"
                    + "012345678";

    /** Digits in a flood line's zero-padded ordinal prefix. */
    private static final int ORDINAL_WIDTH = 10;

    /**
     * Interior positions whose full text is pinned, chosen here rather than derived.
     *
     * <p>42 is deliberate: the count-preserving corruption this phase has already met twice
     * replaced line 42 with a repeat of line 41.
     */
    private static final List<Long> PINNED_INTERIOR_ORDINALS =
            List.of(1L, 42L, 1_000L, 1_000_000L, 2_595_485L, 5_190_969L);

    private static final StageTag COMET = TestStage.named("comet");

    /**
     * The exact text of one flood line, from the format {@code fakes.FakeTool} documents.
     *
     * @param ordinal the line's zero-based number
     * @return the line, without its newline
     */
    private static String floodLine(long ordinal) {
        return String.format(Locale.ROOT, "%0" + ORDINAL_WIDTH + "d", ordinal)
                + " "
                + FLOOD_PADDING;
    }

    /**
     * The retained heap after a full collection, twice requested.
     *
     * <p>Through {@link java.lang.management.MemoryMXBean#gc()} rather than {@link System#gc()},
     * which SpotBugs reports as {@code DM_GC} at this project's threshold; the management API is
     * documented as equivalent and this project fixes findings rather than excluding them. Twice,
     * because the first collection can leave objects that only became unreachable during it.
     *
     * @return bytes of heap retained
     */
    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        ManagementFactory.getMemoryMXBean().gc();
        ManagementFactory.getMemoryMXBean().gc();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @Test
    @DisplayName("gate item 3: 500 MB of stdout, exit 0, a bounded heap and a complete log file")
    void theFloodCompletesWithABoundedHeapAndACompleteLog(@TempDir Path tmp)
            throws IOException, InterruptedException {
        assertEquals(
                "0000000000 " + FLOOD_PADDING,
                floodLine(0L),
                "the line builder is checked against a fully hand-typed literal before anything"
                        + " else is asserted with it");
        assertEquals("0000000042 " + FLOOD_PADDING, floodLine(42L), "and once more further in");
        assertEquals(
                EXPECTED_LINES,
                lineCountComputedHere(),
                "the hand-typed line count and the one computed from the flood size agree");

        BoundedMessageLog console = new BoundedMessageLog(CONSOLE_CAPACITY);
        StageRunner runner =
                new StageRunner(
                        new ProcessService(Clock.systemUTC()),
                        Clock.fixed(AT, ZoneOffset.UTC),
                        ProcessRedactor.with(SecretRegistry.empty()),
                        console::append,
                        tmp.resolve("logs"));

        long heapBefore = usedHeapBytes();
        long startedAtNanos = System.nanoTime();

        StageOutcome outcome =
                runner.start(
                                COMET,
                                FakeTools.command(
                                        tmp,
                                        "flood",
                                        Long.toString(FLOOD_BYTES),
                                        Integer.toString(LINE_LENGTH),
                                        "out"))
                        .awaitOutcome();

        long floodMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        long heapAfter = usedHeapBytes();
        long growth = heapAfter - heapBefore;

        // ---------------------------------------------------------- 1. it completes --

        assertEquals(0, outcome.exitCode(), "the fake finished the whole flood and exited cleanly");
        assertFalse(outcome.cancellationRequested(), "nothing asked it to stop");
        assertFalse(outcome.timedOut(), "and it did not run out of time");
        assertEquals(0L, outcome.logWriteFailures(), "every line reached the disk");
        assertEquals(EXPECTED_LINES, outcome.standardOutputLines());
        assertEquals(1L, outcome.standardErrorLines(), "the fake's summary line, and nothing else");

        // ------------------------------------------- 3. the log file, read back once --

        long auditStartedAtNanos = System.nanoTime();
        LogAudit audit = auditOf(outcome.logFile());
        long auditMillis = (System.nanoTime() - auditStartedAtNanos) / 1_000_000L;
        long logBytes = Files.size(outcome.logFile());

        System.out.printf(
                Locale.ROOT,
                "gate item 3 flood: bytes=%d lineLength=%d lines=%d logBytes=%d "
                        + "floodMillis=%d auditMillis=%d "
                        + "heapBefore=%d heapAfter=%d growth=%d limit=%d "
                        + "consoleSize=%d consoleDiscarded=%d%n",
                FLOOD_BYTES,
                LINE_LENGTH,
                audit.stdoutLines(),
                logBytes,
                floodMillis,
                auditMillis,
                heapBefore,
                heapAfter,
                growth,
                HEAP_GROWTH_LIMIT_BYTES,
                console.size(),
                console.discardedCount());

        assertEquals(
                List.of("lines " + EXPECTED_LINES),
                audit.stderrTexts(),
                "the fake counts its own lines and writes the total on the other stream; that"
                        + " number is derived by the FAKE, the expectation above by this TEST, and"
                        + " the two are required to agree");
        assertEquals(
                EXPECTED_LINES,
                audit.stdoutLines(),
                "every line the tool wrote is on disk: not the size of the file, the number of"
                        + " lines in it");
        assertEquals(
                -1L,
                audit.firstBadPosition(),
                () ->
                        "the ordinals are not a contiguous 0.."
                                + (EXPECTED_LINES - 1)
                                + " run: "
                                + audit.firstBadDescription());
        assertEquals(floodLine(0L), audit.firstStdout(), "the first line, in full");
        assertEquals(
                floodLine(EXPECTED_LINES - 1L), audit.lastStdout(), "and the last line, in full");
        for (Long ordinal : PINNED_INTERIOR_ORDINALS) {
            assertEquals(
                    floodLine(ordinal),
                    audit.sampled().get(ordinal),
                    () -> "the log line at position " + ordinal + " is not the one the fake wrote");
        }
        assertEquals(
                List.of(
                        "stage comet started in " + tmp,
                        "stage comet ended: exit code 0 after PT0S"),
                List.of(audit.serviceTexts().getFirst(), audit.serviceTexts().getLast()),
                "the file is a whole log: header first, footer last");
        assertEquals(3, audit.serviceTexts().size(), "two header lines and one footer");
        assertEquals(
                EXPECTED_LINES + 4L,
                audit.totalLines(),
                "every line in the file is accounted for: the flood, the summary, two headers and"
                        + " a footer, and nothing else");

        // ----------------------------------------------- 2. the heap stayed bounded --

        assertTrue(
                growth < HEAP_GROWTH_LIMIT_BYTES,
                () ->
                        "retained heap grew by "
                                + growth
                                + " bytes while "
                                + EXPECTED_LINES
                                + " lines were streamed, which is over the documented bound of "
                                + HEAP_GROWTH_LIMIT_BYTES
                                + " bytes (heap before "
                                + heapBefore
                                + ", after "
                                + heapAfter
                                + "). An unbounded console would retain about 1.3 GB here.");

        // --------------------------- and the console stayed bounded while disk did not --

        List<LogMessage> retained = console.snapshot();
        List<Long> retainedOrdinals = new ArrayList<>(retained.size());
        long retainedStderr = 0L;
        for (LogMessage message : retained) {
            if (message.severity() == MessageSeverity.STDERR) {
                retainedStderr++;
            } else {
                retainedOrdinals.add(Long.parseLong(message.text().substring(0, ORDINAL_WIDTH)));
            }
        }

        assertEquals(CONSOLE_CAPACITY, console.size(), "the console holds exactly its capacity");
        assertEquals(CONSOLE_CAPACITY, retained.size(), "and its snapshot holds exactly that");
        assertEquals(
                EXPECTED_DISCARDED,
                console.discardedCount(),
                "and reports every message it dropped");
        assertEquals(
                EXPECTED_LINES + 1L,
                console.size() + console.discardedCount(),
                "kept plus discarded is exactly what was appended: the flood and the summary");
        assertEquals(
                1L,
                retainedStderr,
                "the summary is written after the flood, so it is inside the retained window");
        assertEquals(
                EXPECTED_LINES - 1L,
                retainedOrdinals.getLast().longValue(),
                "what the console kept is the NEWEST output, ending at the very last line");
        assertEquals(
                EXPECTED_LINES - retainedOrdinals.size(),
                retainedOrdinals.getFirst().longValue(),
                "and it starts exactly one window back, so what was dropped was the oldest");
        assertEquals(
                -1,
                firstGapIn(retainedOrdinals),
                "with no gap and no repeat inside that window either");
    }

    /**
     * The line count, computed here from the flood size and the line length.
     *
     * <p>One of the three derivations the test requires to agree, and the only one that is
     * arithmetic rather than a literal or a number the fake reported.
     *
     * @return the number of lines a {@link #FLOOD_BYTES} flood of {@link #LINE_LENGTH}-character
     *     lines produces
     */
    private static long lineCountComputedHere() {
        long lineSize = LINE_LENGTH + 1L;
        return (FLOOD_BYTES + lineSize - 1L) / lineSize;
    }

    /**
     * The position of the first ordinal that is not one more than the one before it.
     *
     * @param ordinals the ordinals, in the order they were delivered
     * @return that position, or {@code -1} if the run is contiguous
     */
    private static int firstGapIn(List<Long> ordinals) {
        for (int position = 1; position < ordinals.size(); position++) {
            if (ordinals.get(position) != ordinals.get(position - 1) + 1L) {
                return position;
            }
        }
        return -1;
    }

    /**
     * Reads the whole stage log once, streaming, and reports what is in it.
     *
     * <p>Streaming rather than {@code readAllLines}: the file is around 700 MB, and a test that
     * needed to hold it in memory to check that the console did not would be an odd thing to write.
     * Nothing here allocates per line beyond the line itself -- the ordinal is parsed out of the
     * string in place and the padding is compared with {@code regionMatches} -- so the audit costs
     * a few seconds rather than a few minutes.
     *
     * @param file the stage log file
     * @return what was found
     * @throws IOException if the file cannot be read
     */
    private static LogAudit auditOf(Path file) throws IOException {
        long total = 0L;
        long stdout = 0L;
        long expectedOrdinal = 0L;
        long firstBadPosition = -1L;
        String firstBadDescription = "";
        String firstStdout = null;
        String lastStdout = null;
        Map<Long, String> sampled = new LinkedHashMap<>();
        List<String> stderrTexts = new ArrayList<>();
        List<String> serviceTexts = new ArrayList<>();
        int textStart = STDOUT_PREFIX.length();
        int paddingStart = textStart + ORDINAL_WIDTH + 1;
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8),
                        1 << 20)) {
            String line;
            while ((line = reader.readLine()) != null) {
                total++;
                if (line.startsWith(STDERR_PREFIX)) {
                    stderrTexts.add(line.substring(STDERR_PREFIX.length()));
                    continue;
                }
                if (line.startsWith(SERVICE_PREFIX)) {
                    serviceTexts.add(line.substring(SERVICE_PREFIX.length()));
                    continue;
                }
                if (!line.startsWith(STDOUT_PREFIX)) {
                    if (firstBadPosition < 0L) {
                        firstBadPosition = stdout;
                        firstBadDescription =
                                "a line with no recognised tag at file line " + total + ": " + line;
                    }
                    continue;
                }
                stdout++;
                String text = line.substring(textStart);
                if (firstStdout == null) {
                    firstStdout = text;
                }
                lastStdout = text;
                if (PINNED_INTERIOR_ORDINALS.contains(expectedOrdinal)) {
                    sampled.put(expectedOrdinal, text);
                }
                if (firstBadPosition < 0L
                        && !isTheFloodLineFor(line, expectedOrdinal, paddingStart)) {
                    firstBadPosition = expectedOrdinal;
                    firstBadDescription =
                            "position "
                                    + expectedOrdinal
                                    + " should be \""
                                    + floodLine(expectedOrdinal)
                                    + "\" but was \""
                                    + text
                                    + "\"";
                }
                expectedOrdinal++;
            }
        }
        return new LogAudit(
                total,
                stdout,
                firstBadPosition,
                firstBadDescription,
                String.valueOf(firstStdout),
                String.valueOf(lastStdout),
                Map.copyOf(sampled),
                List.copyOf(stderrTexts),
                List.copyOf(serviceTexts));
    }

    /**
     * Whether one log line is exactly the flood line for {@code ordinal}.
     *
     * <p>Compared in place, without building the expected line: at five million lines a {@code
     * String.format} per line would dominate the test's run time, and a {@code substring} per line
     * would allocate half a gigabyte of garbage to prove that the run allocated very little.
     *
     * @param line the whole log line, tag and timestamp included
     * @param ordinal the ordinal it should carry
     * @param paddingStart where the padding begins in {@code line}
     * @return true if the line is exactly right
     */
    private static boolean isTheFloodLineFor(String line, long ordinal, int paddingStart) {
        if (line.length() != paddingStart + FLOOD_PADDING.length()) {
            return false;
        }
        if (line.charAt(paddingStart - 1) != ' ') {
            return false;
        }
        int digitsStart = paddingStart - ORDINAL_WIDTH - 1;
        if (Long.parseLong(line, digitsStart, digitsStart + ORDINAL_WIDTH, 10) != ordinal) {
            return false;
        }
        return line.regionMatches(paddingStart, FLOOD_PADDING, 0, FLOOD_PADDING.length());
    }

    /**
     * What one pass over the stage log file found.
     *
     * @param totalLines every line in the file
     * @param stdoutLines the ones tagged {@code stdout}
     * @param firstBadPosition the position of the first wrong flood line, or {@code -1}
     * @param firstBadDescription what was wrong with it, or the empty string
     * @param firstStdout the text of the first {@code stdout} line
     * @param lastStdout the text of the last one
     * @param sampled the text at each pinned interior position
     * @param stderrTexts every line tagged {@code stderr}
     * @param serviceTexts every line the process service wrote itself
     */
    private record LogAudit(
            long totalLines,
            long stdoutLines,
            long firstBadPosition,
            String firstBadDescription,
            String firstStdout,
            String lastStdout,
            Map<Long, String> sampled,
            List<String> stderrTexts,
            List<String> serviceTexts) {}
}
