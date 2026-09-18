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

package org.cometgui.app.testing;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.ProcessService;

/**
 * Every argument array the application launched, in order, in front of a real process service.
 *
 * <p><strong>It records and then launches.</strong> A recorder that could not start a process would
 * make "no process was launched" true of the recorder rather than of the product -- the shape this
 * project calls a check that cannot go red -- so the same class is used for both halves of gate
 * item 2: the install that succeeds really runs Comet, Percolator and a second virtual machine
 * through it, and the install of a corrupted artefact must leave its record empty.
 *
 * <p>The record is the {@code argv} array itself, not a count. A count cannot tell "nothing ran"
 * from "something else ran", and the argument array is also the thing {@code R-PROC-02} is about: a
 * list, never a command string.
 */
public final class RecordingProcessRunner implements ProcessRunner {

    private final ProcessRunner delegate;

    private final List<List<String>> launched = Collections.synchronizedList(new ArrayList<>());

    /**
     * A recorder in front of a given runner.
     *
     * @param delegate what actually starts the process
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public RecordingProcessRunner(ProcessRunner delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * A recorder in front of the product's own process service.
     *
     * @param clock the clock the service stamps its records with
     * @return the recorder
     */
    public static RecordingProcessRunner inFrontOf(Clock clock) {
        return new RecordingProcessRunner(new ProcessService(clock));
    }

    @Override
    public RunningProcess start(ToolCommand command, ProcessListener listener) throws IOException {
        Objects.requireNonNull(command, "command");
        launched.add(command.argv());
        return delegate.start(command, listener);
    }

    /**
     * The argument arrays that were launched, oldest first.
     *
     * @return the arrays, each the executable followed by its arguments
     */
    public List<List<String>> launched() {
        return List.copyOf(launched);
    }

    /**
     * The launched argument arrays with each executable reduced to its file name.
     *
     * <p>The absolute path of a staged binary contains a temporary directory, so it cannot be
     * written into an expected value by hand; the file name and every argument after it can.
     *
     * @return the arrays, executable file name first
     */
    public List<List<String>> launchedByFileName() {
        List<List<String>> named = new ArrayList<>();
        for (List<String> argv : launched()) {
            List<String> copy = new ArrayList<>(argv);
            String executable = copy.get(0);
            int slash = executable.lastIndexOf('/');
            copy.set(0, slash < 0 ? executable : executable.substring(slash + 1));
            named.add(List.copyOf(copy));
        }
        return List.copyOf(named);
    }
}
