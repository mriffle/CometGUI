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

package org.cometgui.workflow.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.tools.process.RunMessageSink;

/** The console stand-in: keeps every line and completes futures when a matching line arrives. */
final class CapturingSink implements RunMessageSink {

    private final Object lock = new Object();

    private final List<LogMessage> messages = new ArrayList<>();

    private final List<Waiter> waiters = new ArrayList<>();

    @Override
    public void append(LogMessage message) {
        synchronized (lock) {
            messages.add(message);
            for (Waiter waiter : waiters) {
                if (waiter.wanted().test(message.text())) {
                    waiter.seen().complete(message.text());
                }
            }
        }
    }

    /** A future completed by the first line, already received or not, matching a predicate. */
    CompletableFuture<String> when(Predicate<String> wanted) {
        CompletableFuture<String> seen = new CompletableFuture<>();
        synchronized (lock) {
            for (LogMessage message : messages) {
                if (wanted.test(message.text())) {
                    seen.complete(message.text());
                }
            }
            waiters.add(new Waiter(wanted, seen));
        }
        return seen;
    }

    List<String> lines() {
        synchronized (lock) {
            List<String> lines = new ArrayList<>();
            for (LogMessage message : messages) {
                lines.add(message.text());
            }
            return lines;
        }
    }

    private record Waiter(Predicate<String> wanted, CompletableFuture<String> seen) {}
}
