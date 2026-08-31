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

package org.cometgui.provenance.events;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.secrets.SecretRedactor;

/**
 * An append-only provenance log: one event per line, each one on the storage device before the
 * append is reported complete.
 *
 * <p>{@code R-PROV-05} offers two ways to survive a crash -- "appendable events, or atomically
 * updated state" -- and this class is the first of them. {@code
 * org.cometgui.provenance.io.AtomicDocumentWriter} is the second, and the two are for different
 * jobs: a manifest is one document that is replaced as a whole, while a log is a history that grows
 * while the run is happening and must be readable at every instant in between. <b>A log is
 * therefore not written with the atomic writer</b>, because write-temp-then-rename rewrites the
 * whole file for every record, costs the length of the log per event, and -- the part that matters
 * -- would leave the run's history in a temporary file that the crash deletes.
 *
 * <p><strong>Three properties, and each one is the answer to a way this goes wrong.</strong>
 *
 * <ol>
 *   <li><b>One record per line, terminated by {@code \n}.</b> A crash can then damage only the last
 *       line; everything before it parses exactly as it did before. A single top-level JSON array
 *       would be unreadable in full, because the closing bracket is written last and a dead process
 *       never writes it. See {@link EventLineFormat}.
 *   <li><b>Every record is forced to the device before {@link #append} returns.</b> Not buffered,
 *       not flushed on close, not flushed when the operating system feels like it. An event log
 *       still sitting in a buffer when the JVM dies has no history in it at all, and that failure
 *       is invisible in every test that does not cut the power -- the file is byte-identical either
 *       way. There is deliberately no {@code BufferedOutputStream} between this class and the file:
 *       a buffer is precisely the thing that loses the tail. The force is on the {@link
 *       EventLogSync} seam so that a test can count the calls and read the file's length at the
 *       moment each one happened, which pins the order as well as the fact.
 *   <li><b>Sequence numbers are gap-free and strictly increasing.</b> Whole events can be lost
 *       without leaving a mark in the bytes, so the number is what lets {@link
 *       ProvenanceEventLogReader} say that something is missing. The log assigns them; a caller
 *       cannot choose one.
 * </ol>
 *
 * <p><strong>Reopening a log after a crash, and why the tear is healed.</strong> {@link
 * #openAppend} on an existing file recovers it first and continues from the highest sequence number
 * it found, so a resumed run does not write a second event 4. If the file does not end with a
 * newline -- the signature of a process that died mid-append -- a newline is written and forced
 * before anything else. Without that, the first new record would be concatenated onto the torn one
 * and the two would be read back as a single malformed line: the damage from the old crash would
 * have eaten a record from the new run. Healing keeps damage confined to the line it happened on.
 * The same check runs before an append that follows a failed one, for the same reason.
 *
 * <p><strong>Redaction happens here, once, on the way out.</strong> Every payload goes through
 * {@link SecretRedactor#redactEnvironment}, which is the shared rule set that also cleans a
 * captured process environment: the key names survive untouched, a value under a secret-looking
 * name is replaced whole, and every other value goes through the pattern rules and the registry.
 * Because the payload is one open map rather than fields spread over seven record types, adding an
 * event type cannot add a field that bypasses this call. {@link #append} returns the event <em>as
 * written</em>, so a caller that logs what it recorded cannot print the unredacted form by
 * accident.
 *
 * <p><strong>One writer per file, any number of threads.</strong> Every method body runs inside
 * {@code synchronized (lock)} on a private monitor -- private rather than the log object itself, so
 * that no caller can deadlock a run by locking the log -- which keeps the sequence counter, the
 * write and the force one indivisible step. The process service reads a tool's stdout and stderr on
 * their own threads and both will record events, so this is not theoretical: two unsynchronised
 * appends could hand out the same sequence number or interleave their bytes.
 *
 * <p><strong>What it does not make safe, stated plainly so that a later phase meets a documented
 * boundary rather than a bug.</strong> Two {@code ProvenanceEventLog} instances open on the same
 * path -- two in this JVM, or one in each of two processes -- each keep their own sequence counter,
 * so the file ends up with two events numbered 4 and none numbered 5, and {@link
 * ProvenanceEventLogReader} reports that as a sequence gap it cannot unpick. The monitor here is a
 * JVM object and cannot see another process. A run that needs several processes to write one log
 * needs a lock file around the whole open-append-close cycle, and that is a design decision for the
 * phase that needs it, not something this class can paper over: open one log per file, and pass the
 * instance around.
 */
public final class ProvenanceEventLog implements AutoCloseable {

    /** The line terminator as bytes, for the newline that heals a torn record. */
    private static final byte[] TERMINATOR_BYTES = {(byte) EventLineFormat.LINE_TERMINATOR};

    /** The monitor guarding {@link #nextSequence}, {@link #previousAppendFailed} and the file. */
    private final Object lock = new Object();

    /** Where the log is written; absolute, so that it does not move with the working directory. */
    private final Path path;

    /** The one rule set that removes credentials; see the class documentation. */
    private final SecretRedactor redactor;

    /** Where an event's timestamp comes from. */
    private final Clock clock;

    /** How a record reaches the storage device. */
    private final EventLogSync sync;

    /** Open for the life of the log, in append mode. */
    private final FileChannel channel;

    /** The number the next appended event will carry. */
    private long nextSequence;

    /** Whether the previous append threw, so that the tail may be a partly written record. */
    private boolean previousAppendFailed;

    private ProvenanceEventLog(
            Path path,
            SecretRedactor redactor,
            Clock clock,
            EventLogSync sync,
            FileChannel channel,
            long nextSequence) {
        this.path = path;
        this.redactor = redactor;
        this.clock = clock;
        this.sync = sync;
        this.channel = channel;
        this.nextSequence = nextSequence;
    }

    /**
     * Opens a log for appending, creating it if it does not exist and continuing it if it does.
     *
     * <p>Timestamps come from the system UTC clock. See {@link #openAppend(Path, SecretRedactor,
     * Clock)} for the overload that takes one.
     *
     * @param path the log file
     * @param redactor the rule set every payload value is cleaned by
     * @return an open log, positioned after everything already in the file
     * @throws IOException if the file cannot be read, created or opened
     * @throws NullPointerException if either argument is {@code null}
     */
    public static ProvenanceEventLog openAppend(Path path, SecretRedactor redactor)
            throws IOException {
        return openAppend(path, redactor, Clock.systemUTC());
    }

    /**
     * Opens a log for appending, taking its timestamps from a given clock.
     *
     * <p>Public because a test that pins a log's exact bytes needs the timestamps to be its own
     * choice, and because a caller that already has a run clock should use it rather than a second
     * source of time.
     *
     * @param path the log file
     * @param redactor the rule set every payload value is cleaned by
     * @param clock where each event's timestamp comes from
     * @return an open log, positioned after everything already in the file
     * @throws IOException if the file cannot be read, created or opened
     * @throws NullPointerException if any argument is {@code null}
     */
    public static ProvenanceEventLog openAppend(Path path, SecretRedactor redactor, Clock clock)
            throws IOException {
        return openAppend(path, redactor, clock, EventLogSync.TO_DEVICE);
    }

    /**
     * Opens a log through a given durability seam.
     *
     * <p>This is the whole implementation; the public overloads only choose the arguments. It is
     * package-private because the seam is not a configuration point (see {@link EventLogSync}).
     *
     * @param path the log file
     * @param redactor the rule set every payload value is cleaned by
     * @param clock where each event's timestamp comes from
     * @param sync how a record reaches the storage device
     * @return an open log
     * @throws IOException if the file cannot be read, created or opened
     * @throws NullPointerException if any argument is {@code null}
     */
    static ProvenanceEventLog openAppend(
            Path path, SecretRedactor redactor, Clock clock, EventLogSync sync) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(redactor, "redactor");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(sync, "sync");
        Path absolute = path.toAbsolutePath();
        long nextSequence = ProvenanceEvent.FIRST_SEQUENCE;
        if (Files.exists(absolute)) {
            nextSequence =
                    ProvenanceEventLogReader.recover(absolute, redactor).highestSequence() + 1;
            if (!endsWithTerminator(absolute)) {
                terminateTornRecord(absolute, sync);
            }
        }
        // Opened after the healing, and not before, so that a failure to heal leaves no channel
        // to clean up: the only descriptor in play at that point belongs to a try-with-resources.
        FileChannel channel =
                FileChannel.open(
                        absolute,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
        return new ProvenanceEventLog(absolute, redactor, clock, sync, channel, nextSequence);
    }

    /**
     * Closes off the record a dead process left half-written, so that damage stays on one line.
     *
     * <p>Forced like any other append: a healing newline that is still in the operating system's
     * cache when this process dies too would leave the next run facing the same merged line.
     *
     * @param log the log file, known to end in something other than a terminator
     * @param sync how the newline reaches the storage device
     * @throws IOException if the newline cannot be written or forced
     */
    private static void terminateTornRecord(Path log, EventLogSync sync) throws IOException {
        try (FileChannel healer =
                FileChannel.open(log, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            writeFully(healer, TERMINATOR_BYTES);
            sync.force(healer);
        }
    }

    /**
     * Appends one event, and does not return until its bytes are on the storage device.
     *
     * <p>The sequence number and the timestamp are the log's to assign. The payload is cleaned by
     * {@link SecretRedactor#redactEnvironment} before anything is written, and the event returned
     * is the cleaned one -- what the file now says, not what the caller asked for.
     *
     * @param type what happened
     * @param payload the details; keys must not be blank and values must not be {@code null}. May
     *     be empty.
     * @return the event as it was written, with its assigned sequence number and timestamp
     * @throws IOException if the record cannot be written or cannot be forced to the device; the
     *     log then remains usable and the next append terminates whatever the failed one left
     *     behind
     * @throws java.nio.channels.ClosedChannelException if the log has been closed
     * @throws NullPointerException if either argument is {@code null}, or if any payload key or
     *     value is
     * @throws IllegalArgumentException if a payload key is blank, or if {@code type} is {@link
     *     ProvenanceEventType#RUN_FINISHED} and the payload does not carry a terminal status under
     *     {@link ProvenanceEvent#STATUS_KEY}
     */
    public ProvenanceEvent append(ProvenanceEventType type, Map<String, String> payload)
            throws IOException {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        synchronized (lock) {
            return appendLocked(type, payload);
        }
    }

    /**
     * Appends one event with the monitor already held.
     *
     * @param type what happened
     * @param payload the details, not yet redacted
     * @return the event as it was written
     * @throws IOException if the record cannot be written or forced
     */
    private ProvenanceEvent appendLocked(ProvenanceEventType type, Map<String, String> payload)
            throws IOException {
        ProvenanceEvent event =
                new ProvenanceEvent(
                        nextSequence, clock.instant(), type, redactor.redactEnvironment(payload));
        String line = EventLineFormat.toLine(event);
        String record = healingPrefix() + line + EventLineFormat.LINE_TERMINATOR;
        byte[] bytes = record.getBytes(StandardCharsets.UTF_8);
        previousAppendFailed = true;
        writeFully(channel, bytes);
        // The number is spent the moment the bytes exist, which is why this sits between the
        // write and the force rather than after both.  If the force then fails, the record is
        // probably on the disk and certainly in the file, so reusing its number would produce a
        // log with two events at the same position -- damage that no reader can unpick.  Spending
        // it instead means a record that did not survive shows up as a gap, which is the defect
        // the reader is built to report.
        nextSequence++;
        sync.force(channel);
        previousAppendFailed = false;
        return event;
    }

    /**
     * The file this log is written to.
     *
     * @return the absolute path, never {@code null}
     */
    public Path path() {
        return path;
    }

    /**
     * The sequence number the next appended event will carry.
     *
     * <p>{@link ProvenanceEvent#FIRST_SEQUENCE} for a new log; one more than the highest number
     * already in the file for a log that was reopened after a crash.
     *
     * @return the next sequence number
     */
    public long nextSequence() {
        synchronized (lock) {
            return nextSequence;
        }
    }

    /**
     * Closes the file.
     *
     * <p>Nothing is written here and nothing needs to be: every record was forced as it was
     * appended, so a log that is never closed -- because the JVM died -- holds exactly the same
     * events as one that was. Closing releases the descriptor and makes a later {@link #append}
     * fail loudly instead of writing into a log nobody is reading.
     *
     * @throws IOException if the file cannot be closed
     */
    @Override
    public void close() throws IOException {
        synchronized (lock) {
            channel.close();
        }
    }

    /**
     * Describes the log without disclosing anything it has written.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        synchronized (lock) {
            return "ProvenanceEventLog[path=" + path + ", nextSequence=" + nextSequence + "]";
        }
    }

    /**
     * The newline that must precede this record, if the previous append left a torn one.
     *
     * <p>Checked against the file rather than assumed, because an append can fail with the record
     * fully written -- a failure to force is the obvious case -- and prepending a newline then
     * would leave an empty line, which is damage this class would have invented.
     *
     * @return {@code "\n"} if the file's last byte is not already a terminator, otherwise the empty
     *     string
     * @throws IOException if the file cannot be read
     */
    private String healingPrefix() throws IOException {
        if (previousAppendFailed && !endsWithTerminator(path)) {
            return String.valueOf(EventLineFormat.LINE_TERMINATOR);
        }
        return "";
    }

    /**
     * Writes every byte, however many calls that takes.
     *
     * <p>{@link FileChannel#write(ByteBuffer)} is permitted to write fewer bytes than it is given.
     * A single call whose result is ignored would produce a torn record on exactly the run where it
     * happened, and no test that does not force a short write would ever see it.
     *
     * @param channel the channel to write to
     * @param bytes the record to write
     * @throws IOException if the channel cannot be written to
     */
    private static void writeFully(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * Whether the file's last byte is a line terminator, so that a new record can start cleanly.
     *
     * <p>An empty file counts as terminated: there is no torn record in a file with nothing in it.
     *
     * @param path the file to inspect
     * @return {@code true} if the file is empty or ends with {@code \n}
     * @throws IOException if the file cannot be read
     */
    private static boolean endsWithTerminator(Path path) throws IOException {
        long size = Files.size(path);
        if (size == 0) {
            return true;
        }
        try (InputStream in = Files.newInputStream(path)) {
            in.skipNBytes(size - 1);
            return Arrays.equals(TERMINATOR_BYTES, in.readNBytes(1));
        }
    }
}
