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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.ports.ProcessListener;

/**
 * A {@link ProcessListener} that cannot break the process service.
 *
 * <p>Every callback is made inside a {@code catch (Throwable)}. That is deliberate and it is not
 * defensive programming for its own sake: the listener is supplied by a caller -- a console pane, a
 * log-file writer, a provenance recorder -- and it runs on the pump thread. A listener that throws
 * on one line would otherwise kill the pump, and the rest of a long search's output would be lost
 * along with the exit notification. Losing a run's log because a console listener threw is the
 * wrong failure.
 *
 * <p>The failure is <strong>counted and described</strong> rather than discarded, and {@link
 * #failureCount()} is reported through {@code StartedProcess.listenerFailureCount()}, so a
 * misbehaving listener is visible as a number rather than being silently absorbed.
 *
 * <p>Thread safe: the two pump threads and the completion thread all call through one instance.
 */
final class GuardedListener implements ProcessListener {

    private final ProcessListener delegate;
    private final AtomicLong failures = new AtomicLong();
    private final AtomicReference<String> firstFailure = new AtomicReference<>();

    /**
     * Wraps a caller's listener.
     *
     * @param delegate the listener to protect the service from
     * @throws NullPointerException if {@code delegate} is null
     */
    GuardedListener(ProcessListener delegate) {
        this.delegate = Objects.requireNonNull(delegate, "listener");
    }

    @Override
    public void onStandardOutput(String line) {
        try {
            delegate.onStandardOutput(line);
        } catch (Throwable listenerFailure) {
            record(listenerFailure);
        }
    }

    @Override
    public void onStandardError(String line) {
        try {
            delegate.onStandardError(line);
        } catch (Throwable listenerFailure) {
            record(listenerFailure);
        }
    }

    @Override
    public void onExit(int exitCode) {
        try {
            delegate.onExit(exitCode);
        } catch (Throwable listenerFailure) {
            record(listenerFailure);
        }
    }

    /**
     * How many callbacks the listener has thrown out of.
     *
     * @return the count, zero for a well-behaved listener
     */
    long failureCount() {
        return failures.get();
    }

    /**
     * The first failure, rendered as text.
     *
     * <p>Text rather than the {@link Throwable} itself: a throwable is mutable and handing one back
     * would publish it, and what a diagnostic needs is the type and message.
     *
     * @return the first failure's {@code toString()}, or empty if the listener has never thrown
     */
    Optional<String> firstFailureDescription() {
        return Optional.ofNullable(firstFailure.get());
    }

    private void record(Throwable listenerFailure) {
        failures.incrementAndGet();
        firstFailure.compareAndSet(null, String.valueOf(listenerFailure));
    }
}
