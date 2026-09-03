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

package org.cometgui.app.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;

/**
 * The real process service with a note taken of every launch, so that {@code "no process was
 * launched"} is an assertion rather than an assumption.
 *
 * <p>Gate item 2's second half is exactly that sentence, and the only honest way to hold it is at
 * the one seam a process can be started through: {@code R-PROC-02}'s ArchUnit rule confines {@link
 * ProcessBuilder} to {@code org.cometgui.tools.process}, and everything else in the product reaches
 * it through {@link ProcessRunner}. A recorder here therefore sees every launch there is -- the
 * loadability probe's, the identity probe's, the capability probe's and the JAR launcher's.
 *
 * <p>It <strong>delegates</strong> rather than standing in: the same test has to watch four real
 * tools install and probe successfully, and a runner that answered instead of running would prove
 * nothing about either half.
 */
final class RecordingProcessRunner implements ProcessRunner {

    private final ProcessRunner delegate;
    private final List<List<String>> launched = Collections.synchronizedList(new ArrayList<>());

    RecordingProcessRunner(ProcessRunner delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * Every argument array this product asked to run, in order.
     *
     * @return the launches
     */
    List<List<String>> launched() {
        return List.copyOf(launched);
    }

    /**
     * The executables that were launched, without their arguments, for a readable assertion.
     *
     * @return the first element of every argument array, in order
     */
    List<String> executables() {
        List<String> names = new ArrayList<>();
        for (List<String> argv : launched()) {
            names.add(argv.isEmpty() ? "" : argv.get(0));
        }
        return names;
    }

    @Override
    public RunningProcess start(ToolCommand command, ProcessListener listener) throws IOException {
        launched.add(List.copyOf(command.argv()));
        return delegate.start(command, listener);
    }
}
