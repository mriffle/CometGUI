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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/**
 * {@code R-TOOL-05}'s lock: two CometGUI processes installing the same artefact take turns.
 *
 * <p><em>"Concurrent installation of the same artefact by two CometGUI processes shall be
 * serialised by a lock file or shall be made idempotent; a partially written cache entry shall
 * never be observed as complete."</em> This installer does both -- the second process waits here,
 * and then finds a complete entry and returns it without downloading anything -- because the two
 * protect different things. The lock stops two downloads and two extractions racing into one
 * directory; the idempotence means the loser's user gets the tool rather than an error.
 *
 * <h2>Two locks, because a {@link FileLock} is held by the JVM and not by the thread</h2>
 *
 * <p>This is the part that is easy to get wrong, and it is why this class is not one line. {@link
 * FileChannel#lock()} is owned by the <em>process</em>: a second attempt to lock the same region
 * from the same JVM throws {@link java.nio.channels.OverlappingFileLockException} immediately
 * rather than waiting. So a file lock alone serialises processes and makes concurrent threads fail
 * instead of queue -- and two threads in one JVM prove nothing whatever about the case {@code
 * R-TOOL-05} is about.
 *
 * <p>So: a JVM-wide monitor first, keyed by the lock file's absolute path, then the file lock.
 * Threads queue on the first, processes on the second, and both wait. The map of monitors is static
 * because the guarantee has to hold between two {@code ToolCache} instances in one application,
 * which is exactly what two windows of the same program would produce.
 *
 * <h2>A lock that is never observed to block has not been shown to work</h2>
 *
 * <p>This project has already paid for the other kind. {@code _build/cometgui-maven.lock} existed
 * for weeks, {@code STATUS.rst} told readers builds were serialised by it, and nothing anywhere
 * took it. So {@link #waited()} is part of the interface rather than a debugging aid: it is how a
 * test proves that one process actually waited for another, and an assertion on it is what would go
 * red if the lock were removed.
 *
 * <h2>The lock file is created and never deleted</h2>
 *
 * <p>Deleting it on release would race: a second process can be blocked on the very file being
 * unlinked, would then hold a lock on an inode nothing else can reach, and both would proceed. An
 * empty file per installed artefact is the cheaper thing to keep.
 *
 * <h2>One known limit, recorded rather than papered over</h2>
 *
 * <p>The JVM-wide monitor is keyed by the lock file's absolute, normalised path <em>as text</em>,
 * and normalising does not resolve symbolic links. Two different path strings that name one file
 * therefore get two monitors, and the second {@link FileChannel#lock()} raises {@link
 * java.nio.channels.OverlappingFileLockException} instead of waiting. It cannot arise from {@link
 * ToolCache}, which builds every lock path from one root, and the exception is honest -- but it is
 * a limit rather than a guarantee, so it is written down and tested.
 */
public final class InstallLock implements AutoCloseable {

    /**
     * One monitor per lock file, for this JVM.
     *
     * <p>A {@link Semaphore} of one permit rather than a {@link
     * java.util.concurrent.locks.ReentrantLock}, and the difference is deliberate: a reentrant
     * monitor would let one thread take this lock twice and release it once, and the second take
     * would then reach {@link FileChannel#lock()} on a region this JVM already holds, which throws
     * {@link java.nio.channels.OverlappingFileLockException} rather than waiting. A permit makes
     * "one install of this artefact at a time" mean the same thing to a thread as it does to a
     * process.
     *
     * <p>Static, and deliberately not cleaned up. An entry is keyed by an absolute path string; the
     * number of distinct artefacts a running CometGUI installs is small, and removing entries would
     * reintroduce the race the map exists to close -- two threads that computed different monitor
     * objects for one file do not serialise at all.
     */
    private static final ConcurrentMap<String, Semaphore> MONITORS = new ConcurrentHashMap<>();

    /** The lock file. */
    private final Path file;

    /** The JVM-wide monitor for that file, held for as long as this lock is. */
    private final Semaphore monitor;

    /** The open channel; closing it releases the lock even if the release below is skipped. */
    private final FileChannel channel;

    /** The cross-process lock. */
    private final FileLock fileLock;

    /** How long the acquisition blocked. */
    private final Duration waited;

    /** True once {@link #close()} has run, so that closing twice is not an error. */
    private volatile boolean released;

    private InstallLock(
            Path file, Semaphore monitor, FileChannel channel, FileLock fileLock, Duration waited) {
        this.file = file;
        this.monitor = monitor;
        this.channel = channel;
        this.fileLock = fileLock;
        this.waited = waited;
    }

    /**
     * Takes the lock, waiting for as long as another thread or another process holds it.
     *
     * @param lockFile the lock file; its parent directories are created if they do not exist
     * @return the held lock, which the caller must close
     * @throws IOException if the lock file or its parent cannot be created or opened
     * @throws NullPointerException if {@code lockFile} is {@code null}
     */
    public static InstallLock acquire(Path lockFile) throws IOException {
        Objects.requireNonNull(lockFile, "lockFile");
        Path resolved = lockFile.toAbsolutePath().normalize();
        // An absolute path has a parent unless it IS the file system root, and the root is not a
        // lock file. Stated as a requirement rather than as a branch nothing can take.
        Files.createDirectories(
                Objects.requireNonNull(resolved.getParent(), "a lock file has a parent directory"));
        long startedAt = System.nanoTime();
        Semaphore monitor =
                MONITORS.computeIfAbsent(resolved.toString(), key -> new Semaphore(1, true));
        monitor.acquireUninterruptibly();
        FileChannel channel;
        try {
            channel =
                    FileChannel.open(resolved, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException | Error notOpened) {
            monitor.release();
            throw notOpened;
        }
        /*
         * A finally rather than a catch, so that the cleanup has no exception type to name and no
         * branch a test cannot take: either the lock was granted, or the channel is closed and the
         * monitor released before whatever went wrong propagates.
         */
        boolean granted = false;
        try {
            FileLock fileLock = channel.lock();
            granted = true;
            return new InstallLock(
                    resolved,
                    monitor,
                    channel,
                    fileLock,
                    Duration.ofNanos(System.nanoTime() - startedAt));
        } finally {
            if (!granted) {
                channel.close();
                monitor.release();
            }
        }
    }

    /**
     * The lock file this lock is held on.
     *
     * @return the absolute, normalised path
     */
    public Path file() {
        return file;
    }

    /**
     * How long {@link #acquire(Path)} blocked before the lock was granted.
     *
     * <p>Near zero for an uncontended lock. A duration comparable with another process's critical
     * section is the evidence that the two serialised -- see the class documentation for why that
     * evidence is part of the interface rather than a debugging aid.
     *
     * @return the wait, never negative
     */
    public Duration waited() {
        return waited;
    }

    /**
     * Whether this lock is still held.
     *
     * <p>Answered by the lock itself rather than by a flag this class sets, so that "released"
     * means the operating system agrees.
     *
     * @return {@code true} until {@link #close()} has run
     */
    public boolean held() {
        return fileLock.isValid();
    }

    /**
     * Releases the lock, first for other processes and then for other threads.
     *
     * <p>Closing the channel is what releases the file lock -- {@link
     * java.nio.channels.FileLock#release()} would be a second way of saying the same thing, and two
     * ways of saying one thing is how they come to disagree. The monitor is released in a {@code
     * finally}: a failure to close the channel must still let other threads through, or every later
     * install in this JVM would block for ever on a lock no process holds.
     *
     * <p>Closing twice does nothing.
     *
     * @throws IOException if the channel cannot be closed
     */
    @Override
    public void close() throws IOException {
        if (released) {
            return;
        }
        released = true;
        try {
            channel.close();
        } finally {
            monitor.release();
        }
    }

    /**
     * Describes the lock without disclosing anything but the file it is on.
     *
     * @return a description for a log line or an exception message
     */
    @Override
    public String toString() {
        return "InstallLock[file="
                + file
                + ", held="
                + held()
                + ", waitedMillis="
                + waited.toMillis()
                + "]";
    }
}
