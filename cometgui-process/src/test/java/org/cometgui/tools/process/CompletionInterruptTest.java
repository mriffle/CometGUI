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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.tools.process.fakes.FakeTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The completion thread and interruption: {@link StartedProcess} waits for the process and joins
 * its pumps <em>uninterruptibly</em>, so that the listener is always told of the real exit, and it
 * re-asserts an interrupt it absorbed rather than losing it -- but it never invents one. Both are
 * read where they matter, on the thread that calls {@link ProcessListener#onExit}.
 */
class CompletionInterruptTest {

    /** {@code SIGTERM}: a Unix process killed by signal N reports 128 + N. */
    private static final int EXIT_SIGTERM = 143;

    /** {@code SIGKILL}, as {@link ProcessHandle#destroyForcibly} sends it on Unix. */
    private static final int EXIT_SIGKILL = 137;

    /** A failure bound for real events; nothing here sleeps. */
    private static final long BOUND_SECONDS = 60;

    @Test
    @DisplayName(
            "a process that ends by itself: the listener is told on a thread nobody interrupted")
    void anUninterruptedCompletionStaysUninterrupted(@TempDir Path tmp)
            throws IOException, InterruptedException {
        ExitWitness witness = new ExitWitness();

        StartedProcess started =
                new ProcessService(Clock.systemUTC())
                        .start(FakeTools.command(tmp, "exit-code", "3"), witness);

        assertEquals(3, started.waitForExit());
        assertTrue(witness.exited.await(BOUND_SECONDS, TimeUnit.SECONDS), "never told of the exit");
        assertEquals(3, witness.exitCode);
        assertEquals(Boolean.FALSE, witness.interruptedAtExit, "an interrupt nobody made");
    }

    @Test
    @DisplayName(
            "the completion thread interrupted while it waits: it keeps waiting, reports the real"
                    + " exit, and the interrupt is still set when the listener is told")
    void anInterruptedCompletionKeepsWaitingAndKeepsTheInterrupt(@TempDir Path tmp)
            throws IOException, InterruptedException {
        ExitWitness witness = new ExitWitness();
        StartedProcess started =
                new ProcessService(Clock.systemUTC())
                        .start(FakeTools.command(tmp, "hang"), witness);
        try {
            assertTrue(
                    witness.hanging.await(BOUND_SECONDS, TimeUnit.SECONDS),
                    "the fake never said it was hanging");
            Thread completion = completionThreadOf(started);

            completion.interrupt();

            assertTrue(started.isAlive(), "the process ended by itself");
            started.requestCancellation();
            assertTrue(
                    witness.exited.await(BOUND_SECONDS, TimeUnit.SECONDS),
                    "never told of the exit");
        } finally {
            started.requestCancellation();
        }
        assertEquals(EXIT_SIGTERM, witness.exitCode, "the real exit, after the interrupt");
        assertEquals(Boolean.TRUE, witness.interruptedAtExit, "the absorbed interrupt was lost");
        assertEquals(EXIT_SIGTERM, started.waitForExit());
    }

    @Test
    @DisplayName(
            "the completion thread interrupted while a pump still drains -- a child holds standard"
                    + " error open after its parent died: the join resumes, and the interrupt is"
                    + " still set when the listener is told")
    void anInterruptedJoinKeepsTheInterrupt(@TempDir Path tmp)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        ExitWitness witness = new ExitWitness();
        StartedProcess started =
                new ProcessService(Clock.systemUTC())
                        .start(FakeTools.command(tmp, "hang-with-child"), witness);
        ProcessHandle parent = ProcessHandle.of(started.pid()).orElseThrow();
        ProcessHandle child = null;
        try {
            assertTrue(
                    witness.childAnnounced.await(BOUND_SECONDS, TimeUnit.SECONDS),
                    "the fake never announced its child");
            child = ProcessHandle.of(witness.childPid).orElseThrow();
            parent.destroyForcibly();
            parent.onExit().get(BOUND_SECONDS, TimeUnit.SECONDS);

            completionThreadOf(started).interrupt();

            assertEquals(
                    1,
                    witness.exited.getCount(),
                    "told of the exit while the child still held standard error open");
            child.destroyForcibly();
            assertTrue(
                    witness.exited.await(BOUND_SECONDS, TimeUnit.SECONDS),
                    "never told of the exit");
        } finally {
            started.requestCancellation();
            if (child != null) {
                child.destroyForcibly();
            }
        }
        assertEquals(EXIT_SIGKILL, witness.exitCode, "the parent's own exit");
        assertEquals(Boolean.TRUE, witness.interruptedAtExit, "the absorbed interrupt was lost");
    }

    private static Thread completionThreadOf(StartedProcess started) {
        String name = "cometgui-process-" + started.pid() + "-exit";
        List<Thread> found =
                Thread.getAllStackTraces().keySet().stream()
                        .filter(thread -> thread.getName().equals(name))
                        .toList();
        assertEquals(1, found.size(), "threads named " + name);
        return found.get(0);
    }

    /** Notes the exit, and whether the thread that reported it was interrupted at that moment. */
    private static final class ExitWitness implements ProcessListener {

        private final CountDownLatch hanging = new CountDownLatch(1);
        private final CountDownLatch childAnnounced = new CountDownLatch(1);
        private volatile long childPid = -1;
        private final CountDownLatch exited = new CountDownLatch(1);
        private volatile Boolean interruptedAtExit;
        private volatile int exitCode = -1;

        @Override
        public void onStandardOutput(String line) {
            if ("hanging".equals(line)) {
                hanging.countDown();
            } else if (line.startsWith("child ")) {
                childPid = Long.parseLong(line.substring("child ".length()));
                childAnnounced.countDown();
            }
        }

        @Override
        public void onStandardError(String line) {
            // not needed
        }

        @Override
        public void onExit(int code) {
            interruptedAtExit = Thread.currentThread().isInterrupted();
            exitCode = code;
            exited.countDown();
        }
    }
}
