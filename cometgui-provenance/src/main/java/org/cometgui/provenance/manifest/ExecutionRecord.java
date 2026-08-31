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

package org.cometgui.provenance.manifest;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.ToolCommand;

/**
 * One external process, from the argument array it was launched with to the code it exited with.
 *
 * <p>{@code AC-PRV-05} requires start, end, duration and exit code for every process, and {@code
 * AC-PRV-03} requires the exact argument array. The array, the working directory and the
 * environment are not re-modelled here: they are a {@link ToolCommand}, which is the type the
 * process service is handed and which already rejects a relative working directory, a blank
 * argument and a malformed environment name. Recording anything else would mean the manifest could
 * describe a launch that could not have happened.
 *
 * <p><strong>Duration is derived, never stored.</strong> {@link #duration()} subtracts the two
 * instants on demand. A stored duration is a third number that has to agree with the other two, and
 * the day it does not -- a clock adjustment, a copy-paste, a serialiser that rounds one field and
 * not another -- the manifest contains a contradiction with no way to tell which half is wrong.
 * There is nothing to keep in step if there is nothing to keep.
 *
 * <p><strong>Why the logs are optional and the exit code is not.</strong> A process that was killed
 * before it opened its output files has no archived stdout, and a manifest that had to invent a
 * path for one would be lying about a file that does not exist. An exit code, by contrast, always
 * exists by the time an execution is recorded: a cancelled process still reports one, and {@code
 * AC-PRV-05} asks for it unconditionally.
 *
 * @param command the exact argument array, working directory and environment the process was
 *     launched with
 * @param start when the process was started
 * @param end when it was observed to have finished; never before {@code start}
 * @param exitCode the process's exit status, which is negative or above 128 on some platforms when
 *     a process is signalled and is therefore not range-checked here
 * @param stdout the archived standard-output log, absent if none was captured
 * @param stderr the archived standard-error log, absent if none was captured
 * @param status how the process ended -- {@link ProvenanceStatus#COMPLETED}, {@link
 *     ProvenanceStatus#FAILED} or {@link ProvenanceStatus#CANCELLED}
 */
public record ExecutionRecord(
        ToolCommand command,
        Instant start,
        Instant end,
        int exitCode,
        Optional<LogRecord> stdout,
        Optional<LogRecord> stderr,
        ProvenanceStatus status) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if any reference component is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}, with a message
     *     printing both instants
     */
    public ExecutionRecord {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        ManifestChecks.requireNotBefore(start, end);
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        Objects.requireNonNull(status, "status");
    }

    /**
     * How long the process ran, computed from {@link #start()} and {@link #end()}.
     *
     * <p>Never negative, because the constructor rejects an end before a start.
     *
     * @return the elapsed time, never {@code null}
     */
    public Duration duration() {
        return Duration.between(start, end);
    }

    /**
     * Describes the execution without disclosing any environment value.
     *
     * <p>The generated {@code toString} would be safe today only because {@link
     * ToolCommand#toString()} happens to print environment <em>names</em> only. Stating it here
     * makes the guarantee local to this type and testable on this type, so that a change to the
     * domain record cannot quietly turn a provenance log line into a place a token appears. The
     * derived duration is included because that is the number a reader of a log line wants and the
     * one the generated form would omit.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "ExecutionRecord[command="
                + command
                + ", start="
                + start
                + ", end="
                + end
                + ", duration="
                + duration()
                + ", exitCode="
                + exitCode
                + ", stdout="
                + stdout
                + ", stderr="
                + stderr
                + ", status="
                + status
                + "]";
    }
}
