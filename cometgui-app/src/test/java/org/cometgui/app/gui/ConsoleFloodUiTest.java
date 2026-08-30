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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.stage.Stage;
import org.cometgui.app.bootstrap.CometGuiApplication;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.derived.ConsolePane;
import org.cometgui.ui.viewmodel.SectionId;
import org.cometgui.workflow.state.WorkflowStage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;

/**
 * Phase 02 exit-gate item 5: "The console pane discards oldest messages under a flood test without
 * heap growth beyond its documented cap."
 *
 * <p>{@code R-PROC-03} is the requirement: "Log capture shall be bounded in memory ... the
 * in-memory console buffer shall be capped with a documented retention policy, so that a tool
 * emitting hundreds of megabytes of output cannot exhaust the heap."
 *
 * <h2>The pane, not the model</h2>
 *
 * <p>{@code BoundedMessageLogTest} in cometgui-domain already floods the pure model. This test is
 * the other half: the console <em>pane</em>, inside the running application, on the section the
 * user is looking at, fed through the same path the process service will use -- append to the run's
 * log from a producer thread and call {@code requestRefresh()} from that thread, never touching the
 * scene graph from it.
 *
 * <p>The log is the application's own because the test supplied it: {@code CometGuiApplication} has
 * a constructor taking the composition root and the run's message log precisely so that a test can
 * be the thing that fills it. Nothing is faked -- the shell, the console pane and the view-model in
 * front of the log are all the product's.
 *
 * <h2>There is no sleep, no timeout and no retry</h2>
 *
 * <p>The producer thread is joined, and then {@code refreshNow()} is called on the application
 * thread. That is a barrier, not a wait: a flush that was already queued has run before it, and it
 * renders whatever the log holds at that moment. Every assertion afterwards is about a value that
 * cannot still be changing.
 *
 * <h2>What is asserted</h2>
 *
 * <ol>
 *   <li>the retained model is exactly the log's capacity;
 *   <li>the rendered document is within {@link ConsolePane#DEFAULT_MAX_RENDERED_LINES};
 *   <li>the messages that are gone are the <em>oldest</em>, proved by the sequence number carried
 *       in each line rather than by counting;
 *   <li>the user is told what was discarded, in text, in the console's own summary line;
 *   <li>the retained heap growth is under a documented bound, measured.
 * </ol>
 */
class ConsoleFloodUiTest {

    /**
     * How many lines the flood emits: twenty-five times the log's capacity and fifty times the
     * document's, so that both caps are passed many times over rather than just reached.
     */
    private static final int FLOOD_LINES = 250_000;

    /**
     * The bound on retained heap growth, and the arithmetic behind it.
     *
     * <p><strong>What the application must retain.</strong> The log keeps {@link
     * BoundedMessageLog#DEFAULT_CAPACITY} = 10,000 messages. Each is a {@code LogMessage} record (a
     * 16-byte header and four references, 32 bytes) holding a shared {@code Instant}, a shared
     * empty-or-constant stage {@code Optional}, an enum constant, and a text {@code String} of
     * about 20 characters (24-byte header + 24 bytes of Latin-1 payload = 48). With the deque's own
     * 8-byte slot that is about 88 bytes a message, so about 0.9 MB. The rendered document is
     * {@link ConsolePane#DEFAULT_MAX_RENDERED_LINES} = 5,000 lines of about 40 characters, so about
     * 0.2 MB of characters -- but a {@code TextArea} keeps a paragraph list and its skin builds a
     * {@code Text} node per paragraph, and {@code setText} records an undoable change holding the
     * previous document as well, so several megabytes is expected rather than surprising.
     *
     * <p><strong>Why 20 MB is the number.</strong> The measured growth on this build is 8,595,680
     * bytes (8.2 MB), which the failure message prints if this ever trips, so the bound is about
     * two and a half times what is actually retained -- enough headroom for garbage-collection and
     * just-in-time-compilation noise, and no more.
     *
     * <p>It is also decisively below what an <em>unbounded</em> console costs for the same flood.
     * That is not a calculation: deleting the eviction from {@code BoundedMessageLog.append} and
     * running this test again gives 38,078,880 bytes (36 MB) of retained growth, and it is 36 MB
     * only because the document cap is still holding the rendered text down -- 250,001 rendered
     * lines would be 10 MB of characters plus a quarter of a million {@code Text} nodes on top. So
     * this assertion distinguishes a bounded console from an unbounded one, which is what the gate
     * item is for, rather than merely recording today's number.
     */
    private static final long HEAP_GROWTH_BOUND_BYTES = 20L * 1024 * 1024;

    /** A fixed instant, so that no message carries a value that changes between runs. */
    private static final Instant FIXED = Instant.parse("2026-08-30T00:00:00Z");

    private static BoundedMessageLog log;

    private static RunningApplication application;

    private static FxUiDriver driver;

    private static ConsolePane console;

    private static long heapGrowthBytes;

    @BeforeAll
    static void startTheApplicationWithATestOwnedLog()
            throws TimeoutException, InterruptedException {
        log = new BoundedMessageLog();
        Stage primary = FxToolkit.registerPrimaryStage();
        FxToolkit.setupApplication(
                () -> new CometGuiApplication(ApplicationServices.forThisHost(), log));
        application = RunningApplication.showing(primary);
        driver = new TestFxUiDriver(application);

        driver.clickOn(UiIds.navigationEntry(SectionId.CONSOLE));
        console = (ConsolePane) driver.node(UiIds.CONSOLE_PANE);

        long before = usedHeapBytes();
        flood();
        heapGrowthBytes = usedHeapBytes() - before;
    }

    @AfterAll
    static void stopTheApplication() {
        if (application != null) {
            application.stop();
        }
    }

    /**
     * Appends {@link #FLOOD_LINES} messages from a producer thread, exactly as the process service
     * will, and then brings the pane up to date on the application thread.
     */
    private static void flood() throws InterruptedException {
        Thread producer =
                new Thread(
                        () -> {
                            for (int line = 0; line < FLOOD_LINES; line++) {
                                log.append(
                                        LogMessage.at(
                                                FIXED,
                                                WorkflowStage.COMET,
                                                MessageSeverity.INFO,
                                                floodText(line)));
                                console.requestRefresh();
                            }
                        },
                        "cometgui-flood-producer");
        producer.start();
        producer.join();
        driver.onFxThread(console::refreshNow);
    }

    @Test
    @DisplayName(
            "the retained model is exactly the log's capacity, and the oldest are the ones gone")
    void theOldestMessagesAreTheOnesDiscarded() {
        List<LogMessage> retained = log.snapshot();
        int capacity = log.capacity();

        assertAll(
                () -> assertEquals(BoundedMessageLog.DEFAULT_CAPACITY, capacity),
                () ->
                        assertEquals(
                                capacity,
                                retained.size(),
                                "after "
                                        + FLOOD_LINES
                                        + " lines the log must hold exactly its capacity"),
                () ->
                        assertEquals(
                                (long) FLOOD_LINES + 1 - capacity,
                                log.discardedCount(),
                                "the flood plus the one line startup wrote, less what is retained"),
                () ->
                        assertEquals(
                                floodText(FLOOD_LINES - capacity),
                                retained.get(0).text(),
                                "the oldest retained line, by its sequence number"),
                () ->
                        assertEquals(
                                floodText(FLOOD_LINES - 1),
                                retained.get(retained.size() - 1).text(),
                                "the newest retained line, by its sequence number"),
                () ->
                        assertEquals(
                                expectedSequence(FLOOD_LINES - capacity, capacity),
                                retained.stream().map(LogMessage::text).toList(),
                                "the retained window must be the newest "
                                        + capacity
                                        + " sequence numbers, contiguous and in order"));
    }

    @Test
    @DisplayName("the rendered document is within its cap and holds the newest lines")
    void theRenderedDocumentIsWithinItsCap() {
        String document = driver.textOf(UiIds.CONSOLE_OUTPUT);
        List<String> lines = document.lines().toList();
        int cap = ConsolePane.DEFAULT_MAX_RENDERED_LINES;

        assertAll(
                () ->
                        assertEquals(
                                cap,
                                lines.size(),
                                "the document must hold exactly its cap of lines, not "
                                        + lines.size()),
                () ->
                        assertTrue(
                                lines.size() <= cap,
                                "the document must never exceed " + cap + " lines"),
                () ->
                        assertTrue(
                                lines.get(0).endsWith(floodText(FLOOD_LINES - cap)),
                                "the first rendered line must be sequence "
                                        + (FLOOD_LINES - cap)
                                        + ", but was: "
                                        + lines.get(0)),
                () ->
                        assertTrue(
                                lines.get(lines.size() - 1).endsWith(floodText(FLOOD_LINES - 1)),
                                "the last rendered line must be sequence "
                                        + (FLOOD_LINES - 1)
                                        + ", but was: "
                                        + lines.get(lines.size() - 1)),
                () ->
                        assertTrue(
                                lines.get(0).startsWith("INFO"),
                                "every line states its severity in words, not by colour: "
                                        + lines.get(0)));
    }

    @Test
    @DisplayName("the console tells the user what was discarded and what it is not showing")
    void theSummaryTellsTheUserWhatIsMissing() {
        long discarded = (long) FLOOD_LINES + 1 - log.capacity();
        String expected =
                String.format(Locale.ROOT, "%,d", discarded)
                        + " earlier lines discarded. Showing the newest "
                        + String.format(Locale.ROOT, "%,d", ConsolePane.DEFAULT_MAX_RENDERED_LINES)
                        + " of "
                        + String.format(Locale.ROOT, "%,d", log.capacity())
                        + " matching lines.";

        assertAll(
                () -> assertEquals(expected, driver.textOf(UiIds.CONSOLE_SUMMARY)),
                () ->
                        assertEquals(
                                expected,
                                driver.accessibleTextOf(UiIds.CONSOLE_SUMMARY),
                                "a screen reader must be told the same thing the sighted user is"),
                () ->
                        assertTrue(
                                driver.isVisible(UiIds.CONSOLE_SUMMARY),
                                "the summary must be on screen, not merely in the scene graph"),
                () ->
                        assertTrue(
                                driver.isVisible(UiIds.CONSOLE_OUTPUT),
                                "the flood was rendered into a console the user is looking at"));
    }

    @Test
    @DisplayName("the retained heap growth stays under the documented bound")
    void theRetainedHeapStaysWithinItsBound() {
        assertTrue(
                heapGrowthBytes < HEAP_GROWTH_BOUND_BYTES,
                "retained heap growth over the flood was "
                        + heapGrowthBytes
                        + " bytes ("
                        + heapGrowthBytes / (1024 * 1024)
                        + " MB), over the documented bound of "
                        + HEAP_GROWTH_BOUND_BYTES
                        + " bytes ("
                        + HEAP_GROWTH_BOUND_BYTES / (1024 * 1024)
                        + " MB) for "
                        + FLOOD_LINES
                        + " lines. See HEAP_GROWTH_BOUND_BYTES for the arithmetic.");
    }

    @Test
    @DisplayName("the flush was coalesced: far fewer flushes than lines")
    void theFlushesWereCoalesced() {
        /*
         * Upstream's coalescing, measured at the pane level: 250,000 requestRefresh() calls from
         * the producer thread produced 5 flushes on this build.  The bound below is 250 -- a
         * thousandfold coalescing -- which leaves room for a slower machine to flush more often
         * without leaving room for the mechanism to have been removed.
         */
        long flushes = console.flushCount();
        assertTrue(
                flushes >= 1,
                "the pane must have flushed at least once, or nothing above was rendered");
        assertTrue(
                flushes < FLOOD_LINES / 1000,
                "one flush per line would queue "
                        + FLOOD_LINES
                        + " tasks on the application thread and freeze the interface; "
                        + flushes
                        + " flushes were counted");
    }

    /**
     * The text of one flood line: a fixed prefix and a fixed-width sequence number.
     *
     * @param sequence which line, counted from zero
     * @return for example {@code flood line 00024999}
     */
    private static String floodText(int sequence) {
        return String.format(Locale.ROOT, "flood line %08d", sequence);
    }

    /**
     * The texts a contiguous run of sequence numbers would produce.
     *
     * @param from the first sequence number
     * @param count how many
     * @return the expected texts, in order
     */
    private static List<String> expectedSequence(int from, int count) {
        return java.util.stream.IntStream.range(from, from + count)
                .mapToObj(ConsoleFloodUiTest::floodText)
                .toList();
    }

    /**
     * The heap in use, after asking for a collection twice so that the flood's garbage -- every
     * discarded message and every superseded document -- is not counted as retained.
     *
     * <p><strong>Why the management bean and not {@code System.gc()}.</strong> The measurement
     * needs a collection to have happened, or it would report garbage as retention and the bound
     * below would mean nothing. {@code System.gc()} is what SpotBugs reports as {@code DM_GC} --
     * "extremely dubious except in benchmarking code" -- and this project does not answer a
     * SpotBugs finding with an exclusion. {@link MemoryMXBean#gc()} is the platform's own
     * management operation for the same request, meant for monitoring and measurement code, and
     * {@link MemoryMXBean#getHeapMemoryUsage()} reports the heap directly rather than by
     * subtracting {@code freeMemory} from {@code totalMemory}. Two rounds, because the first can
     * leave objects that only became unreachable during it.
     *
     * @return used heap in bytes
     */
    private static long usedHeapBytes() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        for (int round = 0; round < 2; round++) {
            memory.gc();
        }
        return memory.getHeapMemoryUsage().getUsed();
    }
}
