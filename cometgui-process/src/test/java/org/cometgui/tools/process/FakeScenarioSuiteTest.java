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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.cometgui.domain.run.StageTag;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.tools.process.fakes.FakeTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The specification's fake-executable scenarios that nothing else in this phase drives.
 *
 * <p>PHASE-03 exit gate item 1 is "every fake-executable scenario in the specification has a
 * passing test", and the specification's list is: stdout/stderr interleaving; exit 0 with outputs;
 * non-zero exit; child process creation; a hanging process and cancellation; huge stdout/stderr
 * volume; missing output despite exit 0; malformed output; a partial file followed by failure;
 * delayed output creation; and paths containing spaces and Unicode. {@code
 * SpecificationScenarioCoverageTest} holds that list as hand-typed constants and refuses to pass
 * unless a named test covers each of them; this class holds the ones that were still missing.
 *
 * <p><strong>Everything here runs through the real process service.</strong> {@code
 * FakeToolSelfTest} proves the fakes behave as documented and says so in its own Javadoc -- it
 * launches them with a bare {@link ProcessBuilder} -- which is a statement about the fixtures and
 * not about the product. The gate item is about the service, so every scenario below is driven by a
 * real {@link StageRunner} over a real {@link ProcessService}, and the assertions are on what the
 * caller is given: the outcome, the console and the stage log file.
 *
 * <p><strong>What these four scenarios have in common</strong> is worth stating, because it is the
 * reason the specification asks for them at all: <em>the exit code is not the answer</em>. A tool
 * that exits 0 having written nothing, a tool that exits 0 having written rubbish, and a tool that
 * exits 3 having left half a file behind are all ordinary in this field, and all three are
 * indistinguishable from success to a caller that only reads {@link StageOutcome#exitCode()}. The
 * process service's job is to make the difference observable; these tests assert that it does.
 *
 * <p><strong>Every expected value is hand-typed</strong> or computed by the test from the fake's
 * documented contract. Nothing is derived by calling the code under test.
 *
 * <p><strong>There is no fixed sleep here and there must never be one</strong> (exit gate item 6,
 * enforced mechanically by {@code NoFixedSleepScanTest}). Waiting is done on a real event: the
 * outcome future, or a marker line arriving at the sink.
 */
class FakeScenarioSuiteTest {

    /** A failure bound for something that is supposed to happen on its own. Never a delay. */
    private static final int FAILURE_BOUND_SECONDS = 60;

    /** The instant every fixed-clock test sees, so a whole log line can be written out by hand. */
    private static final Instant AT = Instant.parse("2026-08-31T19:04:51.250Z");

    /** Its rendering in a log line. Typed by hand. */
    private static final String AT_TEXT = "2026-08-31T19:04:51.250Z";

    /** A clock that never moves: every timestamp in a log file is then known in advance. */
    private static final Clock FIXED = Clock.fixed(AT, ZoneOffset.UTC);

    private static final StageTag COMET = TestStage.named("comet");

    /** The characters of one flood line, excluding its newline. */
    private static final int FLOOD_LINE_LENGTH = 100;

    /**
     * The 89 padding characters after the ordinal and its space on a 100-character flood line.
     *
     * <p>Hand-typed, exactly as {@code FakeToolSelfTest} and {@code ProcessCancellationTest} type
     * it, so no expectation here is derived from another test or from the code under test.
     */
    private static final String FLOOD_PADDING =
            "01234567890123456789012345678901234567890123456789012345678901234567890123456789"
                    + "012345678";

    /** The exact 29 bytes {@code malformed-output} writes. Hand-typed, newline included: none. */
    private static final String MALFORMED_TEXT = "<<<not the expected format>>>";

    /** The repeating payload {@code partial-then-fail} writes. Hand-typed. */
    private static final String PATTERN = "0123456789";

    private static StageRunner runner(Path logs, RunMessageSink sink) {
        return new StageRunner(
                new ProcessService(Clock.systemUTC()),
                FIXED,
                ProcessRedactor.with(SecretRegistry.empty()),
                sink,
                logs);
    }

    private static void awaitMarker(CountDownLatch latch, String what) throws InterruptedException {
        assertTrue(
                latch.await(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS),
                "the stage never produced " + what + " within the failure bound");
    }

    private static List<String> linesOf(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private static List<String> tagged(List<String> lines, String tag) {
        String prefix = AT_TEXT + " [" + tag + "] ";
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .toList();
    }

    private static List<String> entriesOf(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> String.valueOf(entry.getFileName())).sorted().toList();
        }
    }

    /**
     * The exact text of one flood line, from the format {@code fakes.FakeTool} documents.
     *
     * @param ordinal the line's zero-based number
     * @return the line, without its newline
     */
    private static String floodLine(long ordinal) {
        return String.format(Locale.ROOT, "%010d", ordinal) + " " + FLOOD_PADDING;
    }

    /**
     * How many lines a flood of {@code totalBytes} produces, computed by this test.
     *
     * <p>The fake writes whole lines of {@code lineLength + 1} bytes -- the newline is part of the
     * cost -- until at least {@code totalBytes} have gone out, so the count is the ceiling of the
     * division and never the floor.
     *
     * @param totalBytes the flood's requested size
     * @param lineLength the characters in a line, excluding its newline
     * @return the number of lines
     */
    private static long floodLineCount(long totalBytes, int lineLength) {
        long lineSize = lineLength + 1L;
        return (totalBytes + lineSize - 1L) / lineSize;
    }

    /**
     * The first ordinal that is not exactly its own position, or {@code -1} if every one is.
     *
     * <p>This is what catches a <em>count-preserving</em> corruption -- line 42 replaced by a
     * repeat of line 41 -- which an assertion on the number of lines plus a sampled element cannot
     * see. That defect shape has been injected twice in this phase and caught both times; it is
     * assumed it will be injected again.
     *
     * @param lines the flood lines, in the order they were delivered
     * @return the position of the first wrong line, or {@code -1}
     */
    private static int firstOrdinalOutOfSequence(List<String> lines) {
        for (int position = 0; position < lines.size(); position++) {
            if (!lines.get(position).equals(floodLine(position))) {
                return position;
            }
        }
        return -1;
    }

    // ================================================ exit 0, with real outputs ==

    @Nested
    @DisplayName("exit 0 with outputs")
    class ExitZeroWithOutputs {

        @Test
        @DisplayName("every promised file is on disk with its exact bytes, and the log names each")
        void exitZeroWithOutputsWritesEveryFileAndSaysSo(@TempDir Path tmp)
                throws IOException, InterruptedException {
            RecordingSink sink = new RecordingSink();

            StageOutcome outcome =
                    runner(tmp.resolve("logs"), sink)
                            .start(
                                    COMET,
                                    FakeTools.command(
                                            tmp,
                                            "write-files",
                                            "results.pin=SpecId Label ScanNr",
                                            "sub/search.pep.xml=<msms_pipeline_analysis/>"))
                            .awaitOutcome();

            assertEquals(0, outcome.exitCode(), "the tool succeeded");
            assertEquals(2L, outcome.standardOutputLines());
            assertEquals(0L, outcome.standardErrorLines());
            assertEquals(0L, outcome.logWriteFailures());
            assertFalse(outcome.cancellationRequested());

            assertEquals(
                    "SpecId Label ScanNr\n",
                    Files.readString(tmp.resolve("results.pin"), StandardCharsets.UTF_8),
                    "the fake writes the text it was given plus exactly one newline");
            assertEquals(
                    "<msms_pipeline_analysis/>\n",
                    Files.readString(
                            tmp.resolve("sub").resolve("search.pep.xml"), StandardCharsets.UTF_8),
                    "and it creates the parent directory a tool would create");
            assertEquals(20L, Files.size(tmp.resolve("results.pin")));
            assertEquals(26L, Files.size(tmp.resolve("sub").resolve("search.pep.xml")));

            assertEquals(
                    List.of("wrote results.pin", "wrote sub/search.pep.xml"),
                    tagged(linesOf(outcome.logFile()), "stdout"),
                    "the stage log carries the tool's own account of what it wrote, in order");
            assertEquals(List.of("wrote results.pin", "wrote sub/search.pep.xml"), sink.texts());
        }
    }

    // ============================================ exit 0, and nothing was written ==

    @Nested
    @DisplayName("missing output despite exit 0")
    class MissingOutputDespiteExitZero {

        @Test
        @DisplayName("the tool exits 0, says it wrote the file, and the file is not there")
        void missingOutputDespiteExitZeroLeavesNothingOnDisk(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path work = Files.createDirectories(tmp.resolve("work"));
            RecordingSink sink = new RecordingSink();

            StageOutcome outcome =
                    runner(tmp.resolve("logs"), sink)
                            .start(COMET, FakeTools.command(work, "missing-output", "results.pin"))
                            .awaitOutcome();

            assertEquals(
                    0,
                    outcome.exitCode(),
                    "this is the whole point of the scenario: the exit code says success");
            assertEquals(1L, outcome.standardOutputLines());
            assertEquals(0L, outcome.standardErrorLines());
            assertFalse(
                    Files.exists(work.resolve("results.pin")),
                    "and the file the tool named does not exist");
            assertEquals(
                    List.of(),
                    entriesOf(work),
                    "nothing at all was created: a caller that inferred success from exit 0 would"
                            + " carry on to a stage whose input is missing");
            assertEquals(
                    List.of("pretending to write results.pin"),
                    tagged(linesOf(outcome.logFile()), "stdout"));
        }
    }

    // ===================================================== exit 0, and it is rubbish ==

    @Nested
    @DisplayName("malformed output")
    class MalformedOutput {

        @Test
        @DisplayName("exit 0 and a file that exists, is the wrong shape, and has no final newline")
        void malformedOutputArrivesExactlyAndTheExitIsZero(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path work = Files.createDirectories(tmp.resolve("work"));
            RecordingSink sink = new RecordingSink();

            StageOutcome outcome =
                    runner(tmp.resolve("logs"), sink)
                            .start(
                                    COMET,
                                    FakeTools.command(work, "malformed-output", "results.pin"))
                            .awaitOutcome();

            Path produced = work.resolve("results.pin");

            assertEquals(0, outcome.exitCode(), "again the exit code says nothing is wrong");
            assertTrue(Files.exists(produced), "the file exists, so an existence check passes too");
            assertEquals(29L, Files.size(produced), "29 bytes, hand-counted");
            assertEquals(
                    MALFORMED_TEXT,
                    Files.readString(produced, StandardCharsets.UTF_8),
                    "and its content is not a PIN header, a pepXML prologue or anything else a"
                            + " later stage could parse");
            assertFalse(
                    Files.readString(produced, StandardCharsets.UTF_8).endsWith("\n"),
                    "it does not even end in a newline; a line-oriented reader gets one truncated"
                            + " record and no indication that anything is missing");
            assertEquals(
                    List.of("wrote malformed results.pin"),
                    tagged(linesOf(outcome.logFile()), "stdout"),
                    "the only trace of the problem is in the tool's own output, which is exactly"
                            + " why the stage log has to keep it");
        }
    }

    // ============================================ half a file, and then a failure ==

    @Nested
    @DisplayName("a partial file followed by failure")
    class PartialFileThenFailure {

        /** How many bytes the tool gets written before it gives up. Hand-chosen. */
        private static final int PARTIAL_BYTES = 4096;

        /** What it exits with. Not 1, so a normalising implementation is visible. */
        private static final int FAILURE_EXIT = 3;

        @Test
        @DisplayName("the half-written file SURVIVES the failure, with its exact byte count")
        void aPartialFileSurvivesTheFailureWithItsExactByteCount(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path work = Files.createDirectories(tmp.resolve("work"));
            RecordingSink sink = new RecordingSink();

            StageOutcome outcome =
                    runner(tmp.resolve("logs"), sink)
                            .start(
                                    COMET,
                                    FakeTools.command(
                                            work,
                                            "partial-then-fail",
                                            "results.pin",
                                            Integer.toString(PARTIAL_BYTES),
                                            Integer.toString(FAILURE_EXIT)))
                            .awaitOutcome();

            Path partial = work.resolve("results.pin");
            byte[] written = Files.readAllBytes(partial);

            assertEquals(
                    FAILURE_EXIT,
                    outcome.exitCode(),
                    "reported exactly, not normalised to 1 and not turned into a cancellation");
            assertFalse(outcome.cancellationRequested(), "nobody asked it to stop; it failed");
            assertFalse(outcome.timedOut());
            assertTrue(
                    Files.exists(partial),
                    "the half-written file is still there. A workflow that deleted it would"
                            + " destroy the only evidence of how far the tool got, and a workflow"
                            + " that mistook it for a finished file would read truncated data.");
            assertEquals(
                    PARTIAL_BYTES,
                    written.length,
                    "exactly as many bytes as the tool managed, to the byte");
            assertEquals(
                    expectedPartialContent(),
                    new String(written, StandardCharsets.US_ASCII),
                    "and every byte of it is the pattern the fake writes, so the file is"
                            + " truncated rather than corrupted");
            assertEquals(
                    List.of("partial " + PARTIAL_BYTES),
                    tagged(linesOf(outcome.logFile()), "stdout"),
                    "the tool's last word is on disk in the stage log, which is where somebody"
                            + " diagnosing the failure will look");
            assertEquals(
                    AT_TEXT + " [cometgui] stage comet ended: exit code 3 after PT0S",
                    linesOf(outcome.logFile()).getLast(),
                    "and the footer records the failure rather than a cancellation");
        }

        /**
         * The exact bytes the fake writes: {@link #PATTERN}, repeated and then cut.
         *
         * <p>Built here from the fake's documented contract, not read back from what it produced.
         *
         * @return {@link #PARTIAL_BYTES} characters of the repeating pattern
         */
        private static String expectedPartialContent() {
            String repeated = PATTERN.repeat(PARTIAL_BYTES / PATTERN.length() + 1);
            String expected = repeated.substring(0, PARTIAL_BYTES);
            assertEquals("0123456789", expected.substring(0, 10), "hand-typed spot check");
            assertEquals("012345", expected.substring(PARTIAL_BYTES - 6), "and at the far end");
            return expected;
        }
    }

    // ==================================================== the file arrives late ==

    @Nested
    @DisplayName("delayed output creation")
    class DelayedOutputCreation {

        /**
         * Lines the fake writes before it creates its file.
         *
         * <p>Large enough that the tool cannot have finished while the sink holds line 100: with
         * the pump parked, at most one read buffer plus a pipe capacity -- about 72 KB on Linux --
         * can have left the tool, and 20,000 lines is around 240 KB. The tool is therefore blocked
         * writing, which is what makes "the file does not exist yet" a fact rather than a race.
         */
        private static final int PRE_LINES = 20_000;

        @Test
        @DisplayName("the file is absent while the tool runs and present, with content, at the end")
        void theOutputFileAppearsOnlyAfterTheToolAnnouncesIt(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path work = Files.createDirectories(tmp.resolve("work"));
            Path late = work.resolve("late.txt");
            RecordingSink sink = new RecordingSink();
            CountDownLatch atLine100 = sink.gateOn("working 100");

            RunningStage stage =
                    runner(tmp.resolve("logs"), sink)
                            .start(
                                    COMET,
                                    FakeTools.command(
                                            work,
                                            "delayed-output",
                                            "late.txt",
                                            Integer.toString(PRE_LINES)));
            try {
                awaitMarker(atLine100, "its hundred-and-first line of output");

                assertTrue(stage.isAlive(), "the tool is blocked writing to a pipe nobody drains");
                assertFalse(
                        Files.exists(late),
                        "a caller that looked for the output file at this moment would find"
                                + " nothing, and it would be wrong to conclude the stage failed");
                assertEquals(List.of(), entriesOf(work), "the directory is still empty");
            } finally {
                sink.releaseGate();
            }

            StageOutcome outcome = stage.awaitOutcome();

            assertEquals(0, outcome.exitCode());
            assertEquals(PRE_LINES + 1L, outcome.standardOutputLines());
            assertTrue(Files.exists(late), "and now it is there");
            assertEquals(List.of("late.txt"), entriesOf(work));
            assertEquals(
                    "done",
                    Files.readString(late, StandardCharsets.UTF_8),
                    "with the content the fake documents, and no trailing newline");
            assertEquals(4L, Files.size(late));

            List<String> stdout = tagged(linesOf(outcome.logFile()), "stdout");
            assertEquals(PRE_LINES + 1, stdout.size());
            assertEquals("working 0", stdout.get(0));
            assertEquals("working 19999", stdout.get(PRE_LINES - 1));
            assertEquals(
                    "created late.txt 4",
                    stdout.get(PRE_LINES),
                    "the fake reads that size back OUT OF THE FILE, so this line cannot exist"
                            + " before the file does: the ordering is a fact, not a comment");
        }
    }

    // ================================================ huge volume, on both streams ==

    @Nested
    @DisplayName("huge stdout/stderr volume")
    class HugeVolume {

        /**
         * Eight million bytes on standard error: 79,208 lines of 101 bytes.
         *
         * <p>The 500 MB stdout flood exit gate item 3 asks for is {@code
         * FloodOfFiveHundredMegabytes Test}. What this adds is the <em>other</em> stream, which
         * that one cannot cover: standard error has its own pump, its own decoder and its own tag
         * in the log file, and a service that merged the two or that drained only one would still
         * pass a stdout-only flood.
         */
        private static final long ERROR_FLOOD_BYTES = 8_000_000L;

        /** Two million bytes to each stream at once: 19,802 lines on each. */
        private static final long BOTH_FLOOD_BYTES = 2_000_000L;

        @Test
        @DisplayName("eight million bytes on stderr arrive complete, in order and on that stream")
        void aHugeStandardErrorVolumeArrivesCompleteAndSeparate(@TempDir Path tmp)
                throws IOException, InterruptedException {
            long expectedLines = floodLineCount(ERROR_FLOOD_BYTES, FLOOD_LINE_LENGTH);
            RecordingSink sink = new RecordingSink();

            StageOutcome outcome =
                    runner(tmp.resolve("logs"), sink)
                            .start(
                                    COMET,
                                    FakeTools.command(
                                            tmp,
                                            "flood",
                                            Long.toString(ERROR_FLOOD_BYTES),
                                            Integer.toString(FLOOD_LINE_LENGTH),
                                            "err"))
                            .awaitOutcome();

            assertEquals(79_208L, expectedLines, "hand-checked: 8000000 bytes of 101-byte lines");
            assertEquals(0, outcome.exitCode());
            assertEquals(expectedLines, outcome.standardErrorLines());
            assertEquals(
                    1L,
                    outcome.standardOutputLines(),
                    "for a stderr flood the fake puts its summary on stdout, and that one line is"
                            + " all stdout should ever carry");

            List<String> lines = linesOf(outcome.logFile());
            List<String> stderr = tagged(lines, "stderr");
            List<String> stdout = tagged(lines, "stdout");

            assertEquals(
                    List.of("lines " + expectedLines),
                    stdout,
                    "the fake's own count, which must agree with the count this test computed"
                            + " from the flood size -- two independent derivations");
            assertEquals(expectedLines, stderr.size());
            assertEquals(floodLine(0L), stderr.get(0));
            assertEquals(floodLine(expectedLines - 1L), stderr.get(stderr.size() - 1));
            assertEquals(
                    -1,
                    firstOrdinalOutOfSequence(stderr),
                    "every ordinal is exactly its own position, so nothing was dropped, repeated"
                            + " or reordered");
        }

        @Test
        @DisplayName("a flood down BOTH streams at once keeps them separate and complete")
        void aHugeVolumeOnBothStreamsStaysSeparate(@TempDir Path tmp)
                throws IOException, InterruptedException {
            long expectedLines = floodLineCount(BOTH_FLOOD_BYTES, FLOOD_LINE_LENGTH);
            RecordingSink sink = new RecordingSink();

            StageOutcome outcome =
                    runner(tmp.resolve("logs"), sink)
                            .start(
                                    COMET,
                                    FakeTools.command(
                                            tmp,
                                            "flood",
                                            Long.toString(BOTH_FLOOD_BYTES),
                                            Integer.toString(FLOOD_LINE_LENGTH),
                                            "both"))
                            .awaitOutcome();

            assertEquals(19_802L, expectedLines, "hand-checked: 2000000 bytes of 101-byte lines");
            assertEquals(0, outcome.exitCode());
            assertEquals(expectedLines, outcome.standardOutputLines());
            assertEquals(expectedLines, outcome.standardErrorLines());

            List<String> lines = linesOf(outcome.logFile());
            List<String> stdout = tagged(lines, "stdout");
            List<String> stderr = tagged(lines, "stderr");

            assertEquals(expectedLines, stdout.size(), "every stdout line is on disk");
            assertEquals(expectedLines, stderr.size(), "and every stderr line, separately");
            assertEquals(
                    2L * expectedLines + 3L,
                    lines.size(),
                    "both floods, two header lines and one footer, and nothing else: no summary"
                            + " is written for a both-streams flood, because a summary on the"
                            + " stream under test would pollute it");
            assertEquals(-1, firstOrdinalOutOfSequence(stdout), "stdout is intact and in order");
            assertEquals(-1, firstOrdinalOutOfSequence(stderr), "and so is stderr, independently");
        }
    }
}
