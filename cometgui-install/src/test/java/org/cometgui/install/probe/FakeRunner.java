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

package org.cometgui.install.probe;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;

/**
 * A process runner that does one scripted thing, for the four probe paths a real binary cannot be
 * made to take on demand: a start failure nothing recognises, a process that never ends, a
 * cancellation that is observed, and a probing thread that is interrupted.
 *
 * <p>Everything a real binary <em>can</em> demonstrate is demonstrated with a real binary, in
 * {@link RealBinaryProbeTest}. This exists for the cases where the alternative would be a test that
 * waits on the operating system's goodwill.
 */
final class FakeRunner {

    private FakeRunner() {}

    /**
     * A runner whose {@code start} fails with the given exception.
     *
     * @param failure what to throw
     * @return the runner
     */
    static ProcessRunner failingWith(IOException failure) {
        return (command, listener) -> {
            throw failure;
        };
    }

    /**
     * A runner that delivers output and an exit code before {@code start} even returns.
     *
     * @param standardError the lines to deliver on standard error
     * @param standardOutput the lines to deliver on standard output
     * @param exitCode the code to report
     * @return the runner
     */
    static ProcessRunner emitting(
            List<String> standardError, List<String> standardOutput, int exitCode) {
        return (command, listener) -> {
            standardOutput.forEach(listener::onStandardOutput);
            standardError.forEach(listener::onStandardError);
            listener.onExit(exitCode);
            return new Handle(new AtomicBoolean(), listener, false, exitCode);
        };
    }

    /** A runner whose process never ends of its own accord. */
    static final class NeverEnding implements ProcessRunner {

        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final boolean interruptTheCaller;
        private final boolean endOnCancellation;

        /**
         * Creates the runner.
         *
         * @param endOnCancellation whether a cancellation makes the process report an exit
         * @param interruptTheCaller whether starting interrupts the thread that started it, which
         *     is how the probe's interrupt path is reached without a race
         */
        NeverEnding(boolean endOnCancellation, boolean interruptTheCaller) {
            this.endOnCancellation = endOnCancellation;
            this.interruptTheCaller = interruptTheCaller;
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener) {
            if (interruptTheCaller) {
                Thread.currentThread().interrupt();
            }
            return new Handle(cancelled, listener, endOnCancellation, 143);
        }

        /**
         * Whether the probe asked the process to stop.
         *
         * @return {@code true} once cancellation was requested
         */
        boolean wasCancelled() {
            return cancelled.get();
        }
    }

    /** The handle the fake runner hands back. */
    private static final class Handle implements RunningProcess {

        private final AtomicBoolean cancelled;
        private final ProcessListener listener;
        private final boolean endOnCancellation;
        private final int exitCode;

        Handle(
                AtomicBoolean cancelled,
                ProcessListener listener,
                boolean endOnCancellation,
                int exitCode) {
            this.cancelled = cancelled;
            this.listener = listener;
            this.endOnCancellation = endOnCancellation;
            this.exitCode = exitCode;
        }

        @Override
        public boolean isAlive() {
            return !cancelled.get();
        }

        @Override
        public int waitForExit() {
            return exitCode;
        }

        @Override
        public void requestCancellation() {
            cancelled.set(true);
            if (endOnCancellation) {
                listener.onExit(exitCode);
            }
        }
    }
}
