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

package org.cometgui.tools.testing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;

/**
 * A {@link ProcessRunner} that plays a scripted answer instead of starting anything.
 *
 * <p>For the tests that grade a probe's <em>rules</em> -- what it concludes from what a tool
 * printed and wrote -- rather than the tools themselves. The real binaries are run by the tests
 * whose names say so, and those are the ones that prove the probe works; this is what makes it
 * affordable to grade the same rule over a dozen shapes of answer, including shapes no real tool
 * has been observed to produce and which a mutation would introduce.
 *
 * <p>It records every command it was given, so a test can assert the argument array the probe built
 * -- {@code -X} before the file, {@code -Z} where it belongs -- which is the part of a probe no
 * amount of output inspection can check.
 */
public final class ScriptedRunner implements ProcessRunner {

    /** One scripted answer to one invocation. */
    public interface Answer {

        /**
         * Plays the answer.
         *
         * @param command what the probe asked for
         * @param listener where the output and exit code go
         * @throws IOException to simulate a process that could not be started at all
         */
        void play(ToolCommand command, ProcessListener listener) throws IOException;
    }

    private final List<Answer> answers = new ArrayList<>();
    private final List<ToolCommand> commands = new ArrayList<>();
    private int played;
    private Cancellable lastProcess;

    /**
     * Adds the next answer.
     *
     * @param answer what the next invocation gets
     * @return this runner
     */
    public ScriptedRunner then(Answer answer) {
        answers.add(Objects.requireNonNull(answer, "answer"));
        return this;
    }

    /**
     * Adds an answer that prints lines and exits.
     *
     * @param exitCode the exit code
     * @param standardError the lines to write to standard error
     * @param standardOutput the lines to write to standard output
     * @return this runner
     */
    public ScriptedRunner thenPrints(
            int exitCode, List<String> standardError, List<String> standardOutput) {
        return then(
                (command, listener) -> {
                    standardError.forEach(listener::onStandardError);
                    standardOutput.forEach(listener::onStandardOutput);
                    listener.onExit(exitCode);
                });
    }

    /**
     * Adds an answer that does some work on the file system, then prints lines and exits.
     *
     * @param work what the "tool" does, given the command it was asked to run
     * @param exitCode the exit code
     * @param standardError the lines to write to standard error
     * @return this runner
     */
    public ScriptedRunner thenWrites(
            Consumer<ToolCommand> work, int exitCode, List<String> standardError) {
        return then(
                (command, listener) -> {
                    work.accept(command);
                    standardError.forEach(listener::onStandardError);
                    listener.onExit(exitCode);
                });
    }

    /**
     * Adds an answer that never finishes, so the caller's timeout decides.
     *
     * @return this runner
     */
    public ScriptedRunner thenNeverFinishes() {
        return then((command, listener) -> {});
    }

    /**
     * Adds an answer that refuses to start.
     *
     * @param message the failure message
     * @return this runner
     */
    public ScriptedRunner thenFailsToStart(String message) {
        return then(
                (command, listener) -> {
                    throw new IOException(message);
                });
    }

    /**
     * Every command this runner was asked to start, in order.
     *
     * @return the commands
     */
    public List<ToolCommand> commands() {
        return List.copyOf(commands);
    }

    /**
     * How many answers were played.
     *
     * @return the count
     */
    public int played() {
        return played;
    }

    /**
     * The handle returned by the most recent start, so a test can see whether it was cancelled.
     *
     * @return the handle, or {@code null} if nothing was started
     */
    public Cancellable lastProcess() {
        return lastProcess;
    }

    @Override
    public RunningProcess start(ToolCommand command, ProcessListener listener) throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(listener, "listener");
        commands.add(command);
        if (played >= answers.size()) {
            throw new AssertionError(
                    "the probe started "
                            + (played + 1)
                            + " process(es) and only "
                            + answers.size()
                            + " answer(s) were scripted; the last command was "
                            + command.argv());
        }
        Answer answer = answers.get(played);
        played++;
        Cancellable process = new Cancellable();
        lastProcess = process;
        answer.play(command, listener);
        return process;
    }

    /** A handle that records whether cancellation was asked for. */
    public static final class Cancellable implements RunningProcess {

        private volatile boolean cancelled;

        @Override
        public boolean isAlive() {
            return !cancelled;
        }

        @Override
        public int waitForExit() {
            throw new AssertionError(
                    "ToolRunner waits on the listener's exit callback, not on the handle; a test"
                            + " double that answered here would let the runner take a route the"
                            + " product does not");
        }

        @Override
        public void requestCancellation() {
            cancelled = true;
        }

        /**
         * Whether cancellation was requested.
         *
         * @return {@code true} once {@link #requestCancellation()} has been called
         */
        public boolean wasCancelled() {
            return cancelled;
        }
    }
}
