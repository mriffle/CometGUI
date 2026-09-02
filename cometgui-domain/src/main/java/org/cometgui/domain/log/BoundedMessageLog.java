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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.run.StageTag;

/**
 * The console's in-memory message buffer, capped so that it cannot exhaust the heap.
 *
 * <h2>Retention policy</h2>
 *
 * <p><strong>The newest N messages are retained, where N is the capacity; appending the N+1'th
 * message discards the oldest.</strong> Nothing else is ever discarded, and the capacity is fixed
 * at construction, so {@link #size()} can never exceed it however much output a tool produces. The
 * default is {@link #DEFAULT_CAPACITY}.
 *
 * <p>Discarding is not silent. {@link #discardedCount()} reports how many messages have been thrown
 * away, so the console can say "12,431 earlier lines discarded" above the buffer instead of
 * presenting a truncated log as if it were the whole one. This matters more than it looks: the run
 * that overflows the console is usually the run that failed, and a reader who cannot tell that the
 * beginning is missing will draw conclusions from output that is not there.
 *
 * <p>This is only half of {@code R-PROC-03}. The other half -- every line reaching the run's log
 * files on disk as it arrives -- belongs to the process service, and it is what makes discarding
 * acceptable here: nothing is lost, it is simply no longer in memory.
 *
 * <h2>Thread safety</h2>
 *
 * <p>This class is thread safe. Every method body runs inside {@code synchronized (lock)} on a
 * private monitor, which guards the deque and the discard count together.
 *
 * <p>The cheaper choice -- a bare {@link ArrayDeque} with no synchronisation -- is wrong here, and
 * not merely theoretically. The process service reads a tool's stdout and stderr on their own
 * threads and appends from both, while the JavaFX application thread reads the buffer to paint the
 * console. An unsynchronised {@code ArrayDeque} under two appenders can lose an element, can leave
 * the head and tail indices inconsistent, and can throw {@link
 * java.util.ConcurrentModificationException} at the reader -- so the failure would appear as a
 * crashed console or a wrong "discarded" figure in exactly the long, noisy run where the console
 * matters most. A private monitor is used rather than a public one so that no caller can deadlock
 * the log by locking the log object itself, and the whole of each method is inside it so that a
 * snapshot cannot observe a half-finished append.
 *
 * <p>The lock is uncontended in the common case and held only for a bounded number of operations:
 * an append is O(1), and a snapshot is a single pass over at most {@code capacity} elements. There
 * is deliberately no index by stage or by severity -- with a bounded buffer the scan is bounded
 * too, and an index would be a second thing to keep correct for no measurable gain.
 */
public final class BoundedMessageLog {

    /**
     * The default retention: the newest 10,000 messages.
     *
     * <p>Ten thousand lines is roughly a terminal's scrollback, it is enough to hold the whole of
     * an ordinary Comet or Percolator run, and at a few hundred bytes for a typical line it costs a
     * few megabytes -- while the tools this application drives can emit hundreds of megabytes, so
     * the difference between a cap and no cap is the difference between a few megabytes and the
     * heap.
     */
    public static final int DEFAULT_CAPACITY = 10_000;

    /** The monitor guarding {@link #messages} and {@link #discarded}. See the class comment. */
    private final Object lock = new Object();

    private final int capacity;

    private final ArrayDeque<LogMessage> messages = new ArrayDeque<>();

    private long discarded;

    /** A log retaining the newest {@link #DEFAULT_CAPACITY} messages. */
    public BoundedMessageLog() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * A log retaining the newest {@code capacity} messages.
     *
     * <p>The deque is not pre-sized to the capacity: a capacity is a limit a caller is entitled to
     * set very high, and pre-sizing would turn "at most this many" into "allocate this many now",
     * which is the opposite of what this class is for.
     *
     * @param capacity how many messages to retain, at least 1
     * @throws IllegalArgumentException if {@code capacity} is less than 1, naming the value
     */
    public BoundedMessageLog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException(
                    "a bounded message log must retain at least 1 message, but the capacity was: "
                            + capacity);
        }
        this.capacity = capacity;
    }

    /**
     * The retention limit: the number of messages this log keeps.
     *
     * @return the capacity, always at least 1
     */
    public int capacity() {
        return capacity;
    }

    /**
     * How many messages are retained right now.
     *
     * @return the size, never greater than {@link #capacity()}
     */
    public int size() {
        synchronized (lock) {
            return messages.size();
        }
    }

    /**
     * How many messages have been discarded to stay within the capacity.
     *
     * <p>A {@code long} because a tool that overflows a 10,000-line buffer can easily overflow an
     * {@code int}'s worth of lines as well, and a count that wraps to a negative number is worse
     * than no count at all. {@link #clear()} resets it.
     *
     * @return the number discarded since construction or the last {@link #clear()}
     */
    public long discardedCount() {
        synchronized (lock) {
            return discarded;
        }
    }

    /**
     * Appends one message, discarding the oldest if the log is already full.
     *
     * @param message the message to append
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public void append(LogMessage message) {
        Objects.requireNonNull(message, "message");
        synchronized (lock) {
            if (messages.size() == capacity) {
                messages.removeFirst();
                discarded++;
            }
            messages.addLast(message);
        }
    }

    /**
     * Forgets every retained message and resets the discard count to zero.
     *
     * <p>The count is reset deliberately. It exists to tell the reader that what they are looking
     * at is incomplete, and above a console the user has just emptied on purpose there is nothing
     * incomplete to report -- keeping the old figure would state that lines are missing from a view
     * that is showing everything it has.
     */
    public void clear() {
        synchronized (lock) {
            messages.clear();
            discarded = 0;
        }
    }

    /**
     * The retained messages, oldest first.
     *
     * <p>A copy, not a view: appending to or clearing the log afterwards does not change a snapshot
     * already taken. That is what lets the JavaFX thread render one without holding the lock, and
     * what makes a test able to compare two points in time.
     *
     * @return an immutable list of the retained messages, oldest first
     */
    public List<LogMessage> snapshot() {
        synchronized (lock) {
            return List.copyOf(messages);
        }
    }

    /**
     * The retained messages of at least the given severity, oldest first.
     *
     * @param minimumSeverity the least severity to include; {@link MessageSeverity#INFO} includes
     *     everything
     * @return an immutable list, oldest first, possibly empty
     * @throws NullPointerException if {@code minimumSeverity} is {@code null}
     */
    public List<LogMessage> snapshotAtLeast(MessageSeverity minimumSeverity) {
        Objects.requireNonNull(minimumSeverity, "minimumSeverity");
        List<LogMessage> matched = new ArrayList<>();
        synchronized (lock) {
            for (LogMessage message : messages) {
                if (message.severity().atLeast(minimumSeverity)) {
                    matched.add(message);
                }
            }
        }
        return List.copyOf(matched);
    }

    /**
     * The retained messages of one workflow stage, of at least the given severity, oldest first.
     *
     * <p>This is the console's stage filter, which the specification's information architecture
     * requires. Messages belonging to no stage are not included: "show me the Comet stage" means
     * the Comet stage, not the Comet stage plus everything unattributed.
     *
     * <p>Stages are matched by {@link StageTag#id()} rather than by object identity or {@code
     * equals}. The workflow module's stage enumeration is one implementation of {@link StageTag}
     * and a view model's or a test's stand-in is another; two tags naming the same stage identify
     * the same stage, whatever classes they happen to be.
     *
     * @param stage the stage to filter by
     * @param minimumSeverity the least severity to include; {@link MessageSeverity#INFO} includes
     *     everything
     * @return an immutable list, oldest first, possibly empty
     * @throws NullPointerException if either argument is {@code null}
     */
    public List<LogMessage> snapshotForStage(StageTag stage, MessageSeverity minimumSeverity) {
        String wanted = Objects.requireNonNull(stage, "stage").id();
        Objects.requireNonNull(minimumSeverity, "minimumSeverity");
        List<LogMessage> matched = new ArrayList<>();
        synchronized (lock) {
            for (LogMessage message : messages) {
                if (message.severity().atLeast(minimumSeverity) && isFrom(message, wanted)) {
                    matched.add(message);
                }
            }
        }
        return List.copyOf(matched);
    }

    private static boolean isFrom(LogMessage message, String stageId) {
        return message.stage().isPresent() && stageId.equals(message.stage().get().id());
    }
}
