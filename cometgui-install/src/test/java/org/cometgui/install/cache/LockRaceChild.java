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

package org.cometgui.install.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * A second JVM that takes the install lock, holds it, and says when.
 *
 * <p>This exists because {@code R-TOOL-05} is about two <em>processes</em>. A {@link
 * java.nio.channels.FileLock} belongs to the JVM, so a second lock attempt on the same thread of
 * control raises {@link java.nio.channels.OverlappingFileLockException} instead of waiting; only a
 * real second process exercises the path a second CometGUI takes.
 *
 * <p>It writes a line to a shared journal when it enters and when it leaves the critical section,
 * so that the parent can prove the two sections did not overlap -- and, in the control run that
 * takes no lock, that they did. A test that could not observe an overlap could not fail if the lock
 * were removed.
 *
 * <p>Arguments: the lock file, the journal file, how long to hold the section for, and either
 * {@code lock} or {@code nolock}.
 */
public final class LockRaceChild {

    private LockRaceChild() {}

    /**
     * Runs the child.
     *
     * @param args the lock file, the journal file, the hold in milliseconds, and the mode
     * @throws IOException if the cache or the fixture cannot be read or written
     * @throws InterruptedException if the process is interrupted while it works
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        Path lockFile = Path.of(args[0]);
        Path journal = Path.of(args[1]);
        long holdMillis = Long.parseLong(args[2]);
        boolean takeTheLock = "lock".equals(args[3]);
        String name = args.length > 4 ? args[4] : String.valueOf(ProcessHandle.current().pid());
        if (takeTheLock) {
            try (InstallLock lock = InstallLock.acquire(lockFile)) {
                criticalSection(journal, name, holdMillis);
                System.out.println("waitedMillis=" + lock.waited().toMillis());
            }
        } else {
            criticalSection(journal, name, holdMillis);
            System.out.println("waitedMillis=0");
        }
        System.out.println("done=" + name);
    }

    private static void criticalSection(Path journal, String name, long holdMillis)
            throws IOException, InterruptedException {
        append(journal, "ENTER " + name + " " + System.currentTimeMillis());
        Thread.sleep(holdMillis);
        append(journal, "EXIT " + name + " " + System.currentTimeMillis());
    }

    private static void append(Path journal, String line) throws IOException {
        Files.writeString(
                journal,
                line.toUpperCase(Locale.ROOT) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
    }
}
