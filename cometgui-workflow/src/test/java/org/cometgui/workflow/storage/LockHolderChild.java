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

package org.cometgui.workflow.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import org.cometgui.domain.project.ProjectLayout;

/**
 * A second JVM that takes a project's lock: the other CometGUI of {@code R-RUN-05}.
 *
 * <p>A {@link java.nio.channels.FileLock} belongs to the JVM, so only a real second process
 * exercises the path a second CometGUI takes; a second thread would hit the in-process check
 * instead. Started by {@link ProjectLockProcessTest} through the project's process service.
 *
 * <p>Arguments: the mode, the project directory, and (for {@code hold}) a gate file.
 *
 * <ul>
 *   <li>{@code hold}: takes the lock, prints {@code HELD <pid> <host>}, then blocks on an exclusive
 *       lock of the gate file -- which the parent holds until it has finished asserting -- and then
 *       releases the project lock and prints {@code RELEASED}. No sleeping: the child waits on the
 *       parent's lock, not on a clock.
 *   <li>{@code halt}: takes the lock, prints {@code HELD <pid> <host>}, and halts the JVM without
 *       releasing anything -- a CometGUI that crashed holding its project.
 * </ul>
 */
public final class LockHolderChild {

    private LockHolderChild() {}

    /**
     * Runs the child.
     *
     * @param args the mode, the project directory, and the gate file
     * @throws IOException if the lock or the gate cannot be used
     */
    public static void main(String[] args) throws IOException {
        String mode = args[0];
        ProjectLayout project = new ProjectLayout(Path.of(args[1]));
        ProjectLock lock = ProjectLock.acquire(project, Clock.systemUTC());
        System.out.println("HELD " + lock.owner().pid() + " " + lock.owner().host());
        System.out.flush();
        if ("halt".equals(mode)) {
            Runtime.getRuntime().halt(0);
        }
        try (FileChannel gate = FileChannel.open(Path.of(args[2]), StandardOpenOption.WRITE);
                FileLock released = gate.lock()) {
            lock.close();
            System.out.println("RELEASED " + released.isValid());
        }
    }
}
