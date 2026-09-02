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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import org.cometgui.domain.ports.ProcessListener;

/**
 * A {@link ProcessListener} that remembers everything, in the order it happened.
 *
 * <p>Two things make it useful and both are deliberate.
 *
 * <p><strong>One list, not three.</strong> Every callback appends to the same list, tagged {@code
 * out:}, {@code err:} or {@code exit:}. That is what makes "the exit is reported after the last
 * line of both streams" an assertion about a sequence rather than a hope, and it is why {@link
 * #events()} exists alongside the per-stream views.
 *
 * <p><strong>Latches, never sleeps.</strong> {@link #expect(String)} registers a line before the
 * process starts and returns a latch that the line's arrival counts down. A test waits on a real
 * event and gives it a generous failure bound, so a broken build fails instead of hanging and no
 * test is ever "synchronised" by guessing how long something takes (PHASE-03 exit gate item 6).
 *
 * <p>Thread safe: both pump threads and the completion thread call it at once.
 */
final class RecordingListener implements ProcessListener {

    private final List<String> events = new ArrayList<>();
    private final ConcurrentMap<String, CountDownLatch> awaited = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CountDownLatch> awaitedPrefixes = new ConcurrentHashMap<>();
    private final CountDownLatch exited = new CountDownLatch(1);

    /**
     * Registers a line whose arrival a test wants to wait for.
     *
     * <p>Call it before starting the process: a line that has already arrived will never count the
     * latch down again.
     *
     * @param line the exact line expected, on either stream
     * @return a latch counted down when that line arrives
     */
    CountDownLatch expect(String line) {
        return awaited.computeIfAbsent(line, awaitedLine -> new CountDownLatch(1));
    }

    /**
     * Registers a line PREFIX whose arrival a test wants to wait for.
     *
     * <p>{@link #expect(String)} needs the whole line, which is not always knowable in advance: the
     * {@code hang-with-child} scenario announces itself as {@code child <pid>}, and the pid is an
     * operating-system fact that exists only once the process is running. Waiting for the prefix is
     * still waiting for a real event, so the no-fixed-sleep rule is untouched.
     *
     * <p>Call it before starting the process, for the same reason {@link #expect(String)} says so.
     *
     * @param prefix the exact beginning of the expected line, on either stream
     * @return a latch counted down when a line beginning with {@code prefix} arrives
     */
    CountDownLatch expectPrefix(String prefix) {
        return awaitedPrefixes.computeIfAbsent(prefix, awaitedPrefix -> new CountDownLatch(1));
    }

    /**
     * The first recorded line beginning with {@code prefix}, on either stream, untagged.
     *
     * @param prefix the beginning to look for
     * @return that line, or empty if no line recorded so far begins with it
     */
    Optional<String> firstLineStartingWith(String prefix) {
        return events().stream()
                .filter(event -> event.startsWith("out:") || event.startsWith("err:"))
                .map(event -> event.substring("out:".length()))
                .filter(line -> line.startsWith(prefix))
                .findFirst();
    }

    /**
     * A latch counted down when the exit is reported.
     *
     * @return the latch
     */
    CountDownLatch exited() {
        return exited;
    }

    @Override
    public void onStandardOutput(String line) {
        record("out:" + line, line);
    }

    @Override
    public void onStandardError(String line) {
        record("err:" + line, line);
    }

    @Override
    public void onExit(int exitCode) {
        synchronized (events) {
            events.add("exit:" + exitCode);
        }
        exited.countDown();
    }

    /**
     * Everything that happened, tagged and in order.
     *
     * @return an immutable snapshot
     */
    List<String> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    /**
     * The standard output lines, in order, untagged.
     *
     * @return an immutable snapshot
     */
    List<String> standardOutput() {
        return linesTagged("out:");
    }

    /**
     * The standard error lines, in order, untagged.
     *
     * @return an immutable snapshot
     */
    List<String> standardError() {
        return linesTagged("err:");
    }

    /**
     * How many times the exit has been reported. The contract says exactly once.
     *
     * @return the number of {@code onExit} calls
     */
    long exitReports() {
        return events().stream().filter(event -> event.startsWith("exit:")).count();
    }

    private List<String> linesTagged(String tag) {
        return events().stream()
                .filter(event -> event.startsWith(tag))
                .map(event -> event.substring(tag.length()))
                .toList();
    }

    private void record(String tagged, String line) {
        synchronized (events) {
            events.add(tagged);
        }
        CountDownLatch waiting = awaited.get(line);
        if (waiting != null) {
            waiting.countDown();
        }
        awaitedPrefixes.forEach(
                (prefix, latch) -> {
                    if (line.startsWith(prefix)) {
                        latch.countDown();
                    }
                });
    }
}
