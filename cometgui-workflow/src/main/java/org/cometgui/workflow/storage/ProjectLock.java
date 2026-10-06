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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongPredicate;
import org.cometgui.domain.project.LockOwner;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.project.UnsupportedSchemaVersionException;

/**
 * {@code R-RUN-05}: one CometGUI at a time per project, with a stale-lock recovery that names the
 * owner.
 *
 * <h2>How the lock works</h2>
 *
 * <ol>
 *   <li><strong>In this process first.</strong> A static map keyed by the lock file's real path
 *       refuses a second holder in the same JVM -- another window, another call -- before the file
 *       is touched. A {@link FileLock} belongs to the JVM, so the file alone cannot tell two
 *       holders in one process apart.
 *   <li><strong>The file.</strong> {@code project.lock} is created {@code CREATE_NEW}, or opened if
 *       it exists, and locked with {@link FileChannel#tryLock(long, long, boolean)} -- never a
 *       blocking lock: a second CometGUI is refused at once, not frozen.
 *   <li><strong>Refused while live.</strong> If the operating system says another process holds the
 *       lock, the attempt is refused naming whoever the file records.
 *   <li><strong>Stale, when the owner is gone.</strong> If the operating-system lock is free but
 *       the file still records an owner -- the owner died without releasing -- the record is
 *       judged: another host is <em>never</em> broken (this computer cannot see that process); a
 *       process on this host that is still running is not stale either; a process that is no longer
 *       running is stale, and the lock is taken over with {@link #recovered()} naming whose it was.
 *   <li><strong>The record.</strong> The holder writes its pid, host and start time into the file
 *       and forces it to disk; {@link #close()} empties the file and then releases the lock, so an
 *       empty file means "released".
 * </ol>
 *
 * <h2>Two deliberate details</h2>
 *
 * <p><strong>The locked region is one byte at {@link #LOCKED_REGION_START}, not the
 * record.</strong> On Windows a byte-range lock is mandatory, so locking the bytes of the record
 * would stop a second CometGUI from reading who holds the project -- the one thing its refusal
 * needs to say. A POSIX lock beyond the end of a file is legal and works the same.
 *
 * <p><strong>The record is written in place, not by rename.</strong> Every other document in a
 * project is replaced atomically through {@code AtomicDocumentWriter}, but the lock lives on the
 * file's inode: renaming a new file over it would leave the holder locking a file nobody else can
 * open. The record is about a hundred bytes and written with one positional write followed by a
 * force; a record torn by a crash at that instant is unreadable, and an unreadable record is
 * refused, never silently broken -- the user deletes the file, as the message says.
 *
 * <p><strong>Nothing else in the process may open {@code project.lock}.</strong> On POSIX systems
 * the operating system releases every lock a process holds on a file when the process closes
 * <em>any</em> descriptor of that file, so reading the record through a second channel would
 * silently drop the lock. That is why the record is read through the lock's own channel, and why a
 * second holder in the same JVM is refused by the in-process map before the file is opened.
 *
 * <p>Only one process's view is trusted for liveness: {@link ProcessHandle}, on this host. A lock
 * file on a network share used from two computers is protected by the host check, not by the
 * operating-system lock, which network file systems do not reliably propagate.
 */
public final class ProjectLock implements AutoCloseable {

    /** Where the locked byte is: past any record, so the record stays readable on Windows. */
    public static final long LOCKED_REGION_START = 1L << 30;

    /** The longest lock record this reader opens; a real one is about a hundred bytes. */
    static final int MAX_RECORD_BYTES = 64 * 1024;

    /** The host name recorded when the computer's name cannot be determined. */
    static final String UNKNOWN_HOST = "unknown-host";

    /** Every lock this JVM holds, by lock-file real path, with its owner. */
    private static final ConcurrentMap<String, LockOwner> HELD = new ConcurrentHashMap<>();

    private final ProjectLayout project;

    private final Path file;

    private final FileChannel channel;

    private final FileLock fileLock;

    private final LockOwner owner;

    private final LockOwner recovered;

    /** Set once by {@link #close()}; atomic rather than synchronized, so no monitor is exposed. */
    private final AtomicBoolean closed = new AtomicBoolean();

    private ProjectLock(
            ProjectLayout project,
            Path file,
            FileChannel channel,
            FileLock fileLock,
            LockOwner owner,
            LockOwner recovered) {
        this.project = project;
        this.file = file;
        this.channel = channel;
        this.fileLock = fileLock;
        this.owner = owner;
        this.recovered = recovered;
    }

    /**
     * Locks a project for this process.
     *
     * @param project the project, whose directory must exist
     * @param clock the clock the lock's start time is read from ({@code R-PROC-01})
     * @return the held lock, which the caller closes
     * @throws ProjectLockedException if the project is locked, naming the owner where recorded
     * @throws InvalidDocumentException if the lock file records something unreadable; it is left as
     *     it is
     * @throws UnsupportedSchemaVersionException if the lock file is of a newer format
     * @throws IOException if the project directory does not exist or the file cannot be used
     */
    public static ProjectLock acquire(ProjectLayout project, Clock clock) throws IOException {
        return acquire(
                project,
                ProcessHandle.current().pid(),
                hostName(() -> InetAddress.getLocalHost().getHostName()),
                clock,
                ProjectLock::isAlive);
    }

    /**
     * Locks a project as a given process on a given host -- the seam the tests use to stand for
     * another computer, or for a process whose liveness they choose.
     *
     * @param project the project
     * @param pid the process taking the lock
     * @param host the host it runs on
     * @param clock the clock
     * @param alive whether a process on this host is still running
     * @return the held lock
     * @throws IOException as {@link #acquire(ProjectLayout, Clock)}
     */
    static ProjectLock acquire(
            ProjectLayout project, long pid, String host, Clock clock, LongPredicate alive)
            throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(alive, "alive");
        LockOwner self = new LockOwner(pid, host, clock.instant());
        Path file = project.root().toRealPath().resolve(ProjectLayout.LOCK_FILE_NAME);
        String key = file.toString();
        LockOwner holder = HELD.putIfAbsent(key, self);
        if (holder != null) {
            throw new ProjectLockedException(
                    ProjectLockedException.Reason.HELD_IN_THIS_PROCESS,
                    holder,
                    "the project "
                            + project.root()
                            + " is already open in this CometGUI ("
                            + holder.describe()
                            + ")");
        }
        FileChannel channel = null;
        try {
            boolean created = true;
            try {
                channel =
                        FileChannel.open(
                                file,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.READ,
                                StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException exists) {
                created = false;
                channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
            }
            FileLock fileLock = tryLock(channel);
            if (fileLock == null) {
                throw heldElsewhere(project, recordOfLiveHolder(channel, file));
            }
            LockOwner previous =
                    created ? null : judge(project, file, readRecord(channel, file), self, alive);
            writeRecord(channel, self);
            return new ProjectLock(project, file, channel, fileLock, self, previous);
        } catch (IOException | RuntimeException | Error failure) {
            HELD.remove(key, self);
            if (channel != null) {
                closeAfter(channel, failure);
            }
            throw failure;
        }
    }

    /**
     * Closes a channel after a failed acquisition, attaching a failure to close to the failure that
     * is propagating rather than replacing it.
     *
     * @param channel the channel to close
     * @param failure the failure that ended the acquisition
     */
    static void closeAfter(FileChannel channel, Throwable failure) {
        try {
            channel.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock(LOCKED_REGION_START, 1, false);
        } catch (OverlappingFileLockException heldByThisJvm) {
            // Only reachable when one file is reached by two real paths (a hard link); the map
            // above already refuses the same path. Either way: someone holds it.
            return null;
        }
    }

    /**
     * Judges the record left in a lock file whose operating-system lock was free.
     *
     * @return the stale owner being taken over, or {@code null} if the file recorded none
     */
    private static LockOwner judge(
            ProjectLayout project,
            Path file,
            Optional<LockOwner> record,
            LockOwner self,
            LongPredicate alive)
            throws ProjectLockedException {
        if (record.isEmpty()) {
            return null;
        }
        LockOwner recorded = record.get();
        if (!recorded.host().equals(self.host())) {
            throw new ProjectLockedException(
                    ProjectLockedException.Reason.OWNER_ON_ANOTHER_HOST,
                    recorded,
                    "the project "
                            + project.root()
                            + " is locked by "
                            + recorded.describe()
                            + ". A lock taken on another computer is never broken from this one,"
                            + " because this computer cannot tell whether that process is still"
                            + " running; if it is not, close CometGUI there or delete "
                            + file
                            + " by hand.");
        }
        if (recorded.pid() != self.pid() && alive.test(recorded.pid())) {
            throw new ProjectLockedException(
                    ProjectLockedException.Reason.OWNER_STILL_RUNNING,
                    recorded,
                    "the project "
                            + project.root()
                            + " is locked by "
                            + recorded.describe()
                            + ", which no longer holds the lock but is still running on this"
                            + " computer; if that process is not CometGUI, delete "
                            + file
                            + " by hand.");
        }
        return recorded;
    }

    private static ProjectLockedException heldElsewhere(ProjectLayout project, LockOwner holder) {
        return new ProjectLockedException(
                ProjectLockedException.Reason.HELD_BY_ANOTHER_PROCESS,
                holder,
                "the project "
                        + project.root()
                        + " is open in another CometGUI"
                        + (holder == null
                                ? ", which has not yet recorded who it is"
                                : ": " + holder.describe())
                        + ". Close it there first.");
    }

    /**
     * Reads the record of a process that holds the lock right now. It may be mid-write, so an
     * unreadable record here means "not yet recorded", not a damaged file.
     */
    private static LockOwner recordOfLiveHolder(FileChannel channel, Path file) throws IOException {
        try {
            return readRecord(channel, file).orElse(null);
        } catch (InvalidDocumentException | UnsupportedSchemaVersionException unreadable) {
            return null;
        }
    }

    /**
     * Reads the lock record through the open channel.
     *
     * @return the owner, or empty for an empty file
     */
    static Optional<LockOwner> readRecord(FileChannel channel, Path file) throws IOException {
        long size = channel.size();
        if (size == 0) {
            return Optional.empty();
        }
        if (size > MAX_RECORD_BYTES) {
            throw new InvalidDocumentException(
                    file.toString(),
                    DocumentFields.ROOT,
                    file
                            + " is not valid: the document is "
                            + size
                            + " bytes, larger than the "
                            + MAX_RECORD_BYTES
                            + " a lock record can be");
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) size);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, buffer.position()) < 0) {
                break;
            }
        }
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return Optional.of(
                LockJson.parse(DocumentFields.decode(file.toString(), bytes), file.toString()));
    }

    /**
     * Replaces the record with the owner's, then forces it to the device: truncate, write, force,
     * in that order.
     *
     * @param channel the lock file's channel
     * @param owner the owner to record
     * @throws IOException if the record cannot be written
     */
    static void writeRecord(FileChannel channel, LockOwner owner) throws IOException {
        ByteBuffer bytes = ByteBuffer.wrap(LockJson.render(owner).getBytes(StandardCharsets.UTF_8));
        channel.truncate(0);
        while (bytes.hasRemaining()) {
            channel.write(bytes, bytes.position());
        }
        channel.force(true);
    }

    /**
     * Whether a process on this host is running.
     *
     * @param pid the process id
     * @return {@code true} if the operating system reports it alive
     */
    static boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    /** Looks up this computer's name. */
    @FunctionalInterface
    interface HostLookup {

        /**
         * The name.
         *
         * @return the host name
         * @throws IOException if it cannot be determined
         */
        String lookup() throws IOException;
    }

    /**
     * This computer's name, or {@link #UNKNOWN_HOST} when it cannot be determined or is blank.
     *
     * @param lookup how to look it up
     * @return the name to record
     */
    static String hostName(HostLookup lookup) {
        try {
            String name = lookup.lookup();
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (IOException | RuntimeException ignored) {
            // A computer without a resolvable name still gets a lock; the record says so.
        }
        return UNKNOWN_HOST;
    }

    /**
     * The project this lock is on.
     *
     * @return the project
     */
    public ProjectLayout project() {
        return project;
    }

    /**
     * The lock file.
     *
     * @return its real path
     */
    public Path file() {
        return file;
    }

    /**
     * Who holds this lock: this process.
     *
     * @return the owner recorded in the file
     */
    public LockOwner owner() {
        return owner;
    }

    /**
     * The stale owner this lock was taken over from, if it was.
     *
     * @return the previous owner, which was no longer running; empty for an ordinary acquisition
     */
    public Optional<LockOwner> recovered() {
        return Optional.ofNullable(recovered);
    }

    /**
     * What to tell the user about a recovery, if there was one.
     *
     * @return the notice, naming whose lock it was; empty for an ordinary acquisition
     */
    public Optional<String> recoveryNotice() {
        return recovered()
                .map(
                        previous ->
                                "recovered a stale lock on "
                                        + project.root()
                                        + " left by "
                                        + previous.describe()
                                        + ", which is no longer running");
    }

    /**
     * Whether this lock is still held.
     *
     * @return {@code true} until {@link #close()}, and only while the operating system agrees
     */
    public boolean held() {
        return !closed.get() && fileLock.isValid();
    }

    /**
     * Requires this lock to be held, and to be on the given project.
     *
     * <p>Every storage operation that changes a project takes the lock as an argument and calls
     * this, so a write without the lock is a programming error that fails loudly.
     *
     * @param expected the project about to be changed
     * @throws IllegalStateException if the lock is released or is on another project
     */
    public void requireHeldFor(ProjectLayout expected) {
        Objects.requireNonNull(expected, "expected");
        if (!held()) {
            throw new IllegalStateException("the lock on " + project.root() + " has been released");
        }
        if (!project.equals(expected)) {
            throw new IllegalStateException(
                    "the lock held is on " + project.root() + ", not on " + expected.root());
        }
    }

    /**
     * Releases the lock: empties the record, then releases the operating-system lock, then this
     * process's claim. Closing twice does nothing.
     *
     * @throws IOException if the record cannot be emptied or the file closed; the lock is released
     *     in any case
     */
    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            emptyRecord(channel);
        } finally {
            try {
                channel.close();
            } finally {
                HELD.remove(file.toString(), owner);
            }
        }
    }

    /**
     * Empties the record and forces the emptiness to the device, so that a crash after release does
     * not resurrect the record: truncate, then force.
     *
     * @param channel the lock file's channel
     * @throws IOException if the file cannot be truncated or forced
     */
    static void emptyRecord(FileChannel channel) throws IOException {
        channel.truncate(0);
        channel.force(true);
    }

    /**
     * Describes the lock.
     *
     * @return the file and the owner
     */
    @Override
    public String toString() {
        return "ProjectLock[file="
                + file
                + ", owner="
                + owner.describe()
                + ", held="
                + held()
                + "]";
    }
}
