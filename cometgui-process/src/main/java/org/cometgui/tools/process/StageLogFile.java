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

package org.cometgui.tools.process;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One stage's log file on disk: the half of {@code R-PROC-03} that is not the console's cap.
 *
 * <h2>Every line is flushed, and that is a requirement rather than a preference</h2>
 *
 * <p>{@code R-PROC-03} says process output is written to the run's log files <em>as it
 * arrives</em>. Buffering a stage's output and writing it when the stage ends would satisfy every
 * test that reads the file afterwards and would fail the run that matters most: a search that was
 * killed, ran out of disk, or brought the machine down leaves nothing at all, and the last thing
 * the tool said is exactly what the user needs. So {@link #append} flushes.
 *
 * <p><strong>The cost, stated rather than hidden.</strong> That is one {@code write} system call
 * per line instead of one per few kilobytes -- for a tool emitting hundreds of megabytes in short
 * lines, hundreds of thousands of extra system calls. It is not an {@code fsync}: the bytes reach
 * the operating system, not the platter, so the cost is a system call rather than a disk seek, and
 * a reader on the same machine -- including the test that proves this works -- sees them at once.
 * If phase 03's 500 MB flood measurement shows this is too expensive, the finding is to be reported
 * and the requirement re-opened, not quietly traded away for a buffer.
 *
 * <h2>Two threads, one file</h2>
 *
 * <p>Both of a stage's pump threads write here. Every write of a line and its terminator happens
 * inside {@code synchronized (lock)} on a private monitor, so a line can never be interleaved into
 * the middle of another line. Each stream's own lines keep their order because each pump is one
 * thread; the order <em>between</em> the two streams is genuinely nondeterministic, is a property
 * of the tool and the operating system rather than of this class, and nothing anywhere should
 * assert it.
 *
 * <h2>A failure to write must not end the run</h2>
 *
 * <p>{@link #append} never throws. A full disk on line 400,000 of a two-hour search must not kill
 * the pump that is reading the tool, lose the rest of the output, or take down the console with it;
 * the same reasoning as {@link GuardedListener}, applied to the other end. Failures are
 * <strong>counted and described</strong> -- see {@link #failureCount()} and {@link #firstFailure()}
 * -- and {@link StageOutcome#logWriteFailures()} carries the count out to the caller, so a run
 * whose log is incomplete says so instead of looking complete.
 *
 * <h2>Re-running a stage never destroys the previous attempt</h2>
 *
 * <p>See {@link #create}. A retry is a normal thing for a user to do, and the first attempt's log
 * is frequently the one that explains why there was a second.
 */
final class StageLogFile implements Closeable {

    /**
     * How many attempts of one stage can coexist in one log directory: {@value}.
     *
     * <p>{@code comet.log} plus {@code comet.1.log} through {@code comet.99.log}. A hundred manual
     * retries of one stage into one run directory is not a workflow, it is a symptom, and failing
     * loudly at that point is better than numbering for ever.
     */
    static final int MAXIMUM_ATTEMPTS = 100;

    /**
     * What a stage identifier may contain before it becomes a file name.
     *
     * <p><strong>This is the file-writing path, so the identifier is validated rather than
     * trusted.</strong> Today every stage identifier comes from an enumeration in the workflow
     * module and none of them could possibly be dangerous. The check is here because that is a fact
     * about today's callers, not a property of this class, and the first phase that lets a stage
     * identifier come from a project file, a command line or a saved run would turn {@code
     * ../../.ssh/authorized_keys} into a path this class opens for writing. Letters, digits, {@code
     * -} and {@code _}; no separator, no {@code .}, and therefore no {@code ..}; not blank; at most
     * 64 characters, which is comfortably inside every file-name limit this application can meet.
     */
    private static final Pattern SAFE_STAGE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** The monitor guarding {@link #writer}, {@link #failures} and {@link #firstFailure}. */
    private final Object lock = new Object();

    private final Path file;

    private final Writer writer;

    private long failures;

    private String firstFailure;

    /**
     * Wraps an already-open writer. Used by {@link #create}, and by tests that need a writer which
     * fails.
     *
     * @param file the path {@code writer} writes to, reported by {@link #file()}
     * @param writer the writer, taken over by this object and closed by {@link #close()}
     * @throws NullPointerException if either argument is null
     */
    StageLogFile(Path file, Writer writer) {
        this.file = Objects.requireNonNull(file, "file");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /**
     * Opens the log file for a stage, without ever overwriting an earlier attempt's.
     *
     * <p>The first attempt is {@code <stageId>.log}. If that exists -- because the user is
     * re-running the stage -- the next free {@code <stageId>.<n>.log} is used, {@code n} counting
     * from 1, and {@link #file()} reports which one was actually opened so that the outcome can
     * name it. Truncating the previous attempt is not an option: it is usually the log that says
     * why there is a second attempt.
     *
     * <p>The file is created with {@link StandardOpenOption#CREATE_NEW}, so the choice of a free
     * name is made by the file system rather than by a check followed by an open. Two stages
     * started at the same instant in the same directory therefore get different files instead of
     * one of them silently inheriting the other's.
     *
     * @param directory where the run's logs go; created, with its parents, if it does not exist
     * @param stageId the stage identifier, validated against {@link #SAFE_STAGE_ID}
     * @return the opened file, empty, with nothing written to it yet
     * @throws IOException if the directory cannot be created, if the file cannot be opened, or if
     *     {@value #MAXIMUM_ATTEMPTS} names are already taken
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if {@code stageId} is not a safe file-name token
     */
    static StageLogFile create(Path directory, String stageId) throws IOException {
        Objects.requireNonNull(directory, "directory");
        String safeStageId = checkedStageId(stageId);
        Files.createDirectories(directory);
        for (int attempt = 0; attempt < MAXIMUM_ATTEMPTS; attempt++) {
            Path candidate = directory.resolve(candidateName(safeStageId, attempt));
            try {
                OutputStream bytes =
                        Files.newOutputStream(
                                candidate, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return new StageLogFile(
                        candidate,
                        new BufferedWriter(new OutputStreamWriter(bytes, StandardCharsets.UTF_8)));
            } catch (FileAlreadyExistsException earlierAttempt) {
                /* An earlier attempt of the same stage. Keep it, and take the next name. */
            }
        }
        throw new IOException(
                "the first "
                        + MAXIMUM_ATTEMPTS
                        + " log file names for stage "
                        + safeStageId
                        + " are all taken in "
                        + directory
                        + "; refusing to overwrite an earlier attempt's log");
    }

    /**
     * Checks that a stage identifier can safely become a file name, and returns it.
     *
     * @param stageId the identifier to check
     * @return {@code stageId}, unchanged
     * @throws NullPointerException if {@code stageId} is null
     * @throws IllegalArgumentException if it is blank, too long, or contains anything but letters,
     *     digits, {@code -} and {@code _} -- the message quotes the rejected value
     */
    static String checkedStageId(String stageId) {
        Objects.requireNonNull(stageId, "stageId");
        if (!SAFE_STAGE_ID.matcher(stageId).matches()) {
            throw new IllegalArgumentException(
                    "a stage identifier becomes a log file name, so it must be 1 to 64 characters"
                            + " of letters, digits, '-' and '_' only, but was: \""
                            + stageId
                            + "\"");
        }
        return stageId;
    }

    /**
     * The name of the {@code attempt}'th log file of a stage.
     *
     * @param stageId the validated stage identifier
     * @param attempt 0 for the first attempt, 1 for the next, and so on
     * @return {@code comet.log} for attempt 0, {@code comet.1.log} for attempt 1
     */
    static String candidateName(String stageId, int attempt) {
        return attempt == 0 ? stageId + ".log" : stageId + "." + attempt + ".log";
    }

    /**
     * Which file this actually is, including the {@code .1} an earlier attempt forced.
     *
     * @return the path, never null
     */
    Path file() {
        return file;
    }

    /**
     * Writes one line and flushes it, never throwing.
     *
     * @param at when the line was recorded, from the run's clock
     * @param tag {@code stdout}, {@code stderr} or {@link StageLogFormat#SERVICE_TAG}
     * @param text the line, already redacted
     * @return true if the line reached the operating system; false if the write failed, in which
     *     case the failure has been counted and described
     * @throws NullPointerException if any argument is null
     */
    boolean append(Instant at, String tag, String text) {
        String rendered = StageLogFormat.line(at, tag, text);
        /* One variable and one return, rather than a `return true` and a `return false`: a
         * constant return cannot be mutated into anything different, so a mutation testing tool
         * reports it as a survivor it can never kill and the report gets one entry less honest. */
        boolean written = false;
        synchronized (lock) {
            try {
                writer.write(rendered);
                writer.write('\n');
                writer.flush();
                written = true;
            } catch (IOException notWritten) {
                record(notWritten);
            }
        }
        return written;
    }

    /**
     * Writes the last line and closes the file.
     *
     * <p>One call rather than two, so that the close cannot be forgotten -- or deleted by a
     * mutation testing tool -- while the file still looks complete.
     *
     * @param at when the line was recorded, from the run's clock
     * @param tag {@code stdout}, {@code stderr} or {@link StageLogFormat#SERVICE_TAG}
     * @param text the line, already redacted
     * @return true if the line reached the operating system
     * @throws NullPointerException if any argument is null
     */
    boolean finish(Instant at, String tag, String text) {
        boolean written = append(at, tag, text);
        close();
        return written;
    }

    /**
     * Closes the file, never throwing. Idempotent.
     *
     * <p>A close failure is counted like a write failure: it means the last buffered bytes may not
     * have reached the file, which is exactly the kind of quiet incompleteness this class refuses
     * to let a run get away with.
     */
    @Override
    public void close() {
        synchronized (lock) {
            try {
                writer.close();
            } catch (IOException notClosed) {
                record(notClosed);
            }
        }
    }

    /**
     * How many writes to this file have failed.
     *
     * @return the count, zero for a healthy run
     */
    long failureCount() {
        synchronized (lock) {
            return failures;
        }
    }

    /**
     * The first write failure, rendered as text.
     *
     * <p>Text rather than the exception: a throwable is mutable, and what a diagnostic needs is its
     * type and message.
     *
     * @return the first failure's {@code toString()}, or empty if nothing has failed
     */
    Optional<String> firstFailure() {
        synchronized (lock) {
            return Optional.ofNullable(firstFailure);
        }
    }

    private void record(IOException failure) {
        failures++;
        if (firstFailure == null) {
            firstFailure = String.valueOf(failure);
        }
    }
}
