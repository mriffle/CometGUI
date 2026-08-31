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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;

/**
 * A {@link RunMessageSink} that remembers every message, and can hold one line hostage.
 *
 * <p>Three things make it useful, and all three exist to keep tests synchronised by events rather
 * than by guessing how long something takes (PHASE-03 exit gate item 6).
 *
 * <p><strong>Latches, never sleeps.</strong> {@link #expect(String)} registers a line before the
 * stage starts and hands back a latch its arrival counts down.
 *
 * <p><strong>A gate.</strong> {@link #gateOn(String)} makes the sink block inside {@code append}
 * when one particular line arrives, and stay blocked until {@link #releaseGate()}. That is what
 * lets a test freeze a running stage at a known line -- with the pump thread parked in the sink --
 * and inspect the log file on disk while the tool is still running. It is a real event, not a
 * delay: the stage stops at that line and nowhere else.
 *
 * <p><strong>Everything is recorded in order</strong>, so an assertion can be about a sequence.
 *
 * <p>Thread safe: both pump threads append at once.
 */
final class RecordingSink implements RunMessageSink {

    /** A bound so a broken build fails instead of hanging. Never the mechanism. */
    private static final int FAILURE_BOUND_SECONDS = 60;

    private final List<LogMessage> messages = new ArrayList<>();
    private final ConcurrentMap<String, CountDownLatch> awaited = new ConcurrentHashMap<>();
    private final CountDownLatch gateReached = new CountDownLatch(1);
    private final CountDownLatch gateReleased = new CountDownLatch(1);

    private volatile String gatedText;

    /**
     * Registers a line whose arrival a test wants to wait for.
     *
     * <p>Call it before starting the stage: a line that has already arrived will never count the
     * latch down again.
     *
     * @param text the exact message text expected
     * @return a latch counted down when that text arrives
     */
    CountDownLatch expect(String text) {
        return awaited.computeIfAbsent(text, expected -> new CountDownLatch(1));
    }

    /**
     * Makes the sink block when {@code text} arrives, until {@link #releaseGate()} is called.
     *
     * @param text the exact message text to stop on
     * @return a latch counted down when the sink has been reached and is blocked
     */
    CountDownLatch gateOn(String text) {
        gatedText = Objects.requireNonNull(text, "text");
        return gateReached;
    }

    /** Lets the gated line, and everything behind it, through. */
    void releaseGate() {
        gateReleased.countDown();
    }

    @Override
    public void append(LogMessage message) {
        synchronized (messages) {
            messages.add(message);
        }
        CountDownLatch waiting = awaited.get(message.text());
        if (waiting != null) {
            waiting.countDown();
        }
        if (Objects.equals(message.text(), gatedText)) {
            gateReached.countDown();
            try {
                if (!gateReleased.await(FAILURE_BOUND_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "the gate on \""
                                    + gatedText
                                    + "\" was never released; the test that opened it is broken");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Every message, in the order it arrived.
     *
     * @return an immutable snapshot
     */
    List<LogMessage> messages() {
        synchronized (messages) {
            return List.copyOf(messages);
        }
    }

    /**
     * The text of every message, in order.
     *
     * @return an immutable snapshot
     */
    List<String> texts() {
        return messages().stream().map(LogMessage::text).toList();
    }

    /**
     * The text of every message of one severity, in order.
     *
     * @param severity the severity to filter by, exactly
     * @return an immutable snapshot
     */
    List<String> textsOf(MessageSeverity severity) {
        return messages().stream()
                .filter(message -> message.severity() == severity)
                .map(LogMessage::text)
                .toList();
    }
}
