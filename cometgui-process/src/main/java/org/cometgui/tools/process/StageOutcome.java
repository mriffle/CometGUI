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

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.StageTag;

/**
 * Everything a provenance record and a user interface need to know about a finished stage, as one
 * immutable value.
 *
 * <h2>What is here, and what is deliberately not</h2>
 *
 * <p>The command is the <strong>redacted</strong> rendering and there is no other form of it here:
 * a value object that could be asked for the unredacted command is one bad log statement away from
 * printing a credential, and the redacted form is the only one a report, a log line or a console
 * pane should ever show ({@code R-SEC-03}).
 *
 * <p><strong>The environment is not a component at all.</strong> {@code ToolCommand.toString()}
 * prints variable names and never values, and this type must not do worse; the way not to do worse
 * is to not hold them. {@code R-PROC-04}'s "inherited variables that change tool behaviour shall be
 * recorded in provenance" is served by {@link ProcessRedactor#redactedEnvironment}, which the
 * provenance writer calls with the command it already has.
 *
 * <p>Both stream line counts are here because they are the cheapest possible answer to "did the
 * tool actually say anything, and on which stream", which is the first question about a stage that
 * exited 0 and produced no output file.
 *
 * <p>{@link #logWriteFailures()} is here because a log that could not be written is not allowed to
 * look like a log that was. It is zero for every healthy run.
 *
 * <h2>Cancelled, timed out, and the difference</h2>
 *
 * <p>{@link #cancellationRequested()} is true whenever anything asked the stage to stop, including
 * its own timeout. {@link #timedOut()} says the request came from the timeout rather than from the
 * user, so {@code cancellationRequested() && !timedOut()} is "the user cancelled this". A stage
 * that timed out reports both, and the constructor rejects the impossible combination of a timeout
 * that asked for no cancellation.
 *
 * <p>An {@link #exitCode()} on its own cannot express any of that: a tool killed by {@code SIGTERM}
 * reports 143 on Linux, which is a non-zero exit like any other and would otherwise be indis-
 * tinguishable from the tool deciding to fail.
 *
 * @param stage which stage this was
 * @param redactedDisplayCommand what was run, escaped and with credentials removed; not a shell
 *     command and never to be treated as one, see {@link ToolCommand#displayString()}
 * @param logFile the log file that was actually written, which is not necessarily {@code
 *     <stage>.log}: see {@link StageLogFile#create}
 * @param exitCode the code the operating system reported, exactly as it reported it
 * @param startedAt when the stage was started, from the run's injected clock
 * @param endedAt when it ended, from the same clock
 * @param standardOutputLines how many lines the tool wrote to standard output
 * @param standardErrorLines how many it wrote to standard error
 * @param cancellationRequested whether anything asked the stage to stop
 * @param timedOut whether what asked was the stage's own timeout
 * @param logWriteFailures how many lines could not be written to the log file; zero for a healthy
 *     run
 */
public record StageOutcome(
        StageTag stage,
        String redactedDisplayCommand,
        Path logFile,
        int exitCode,
        Instant startedAt,
        Instant endedAt,
        long standardOutputLines,
        long standardErrorLines,
        boolean cancellationRequested,
        boolean timedOut,
        long logWriteFailures) {

    /**
     * Validates the outcome.
     *
     * @throws NullPointerException if any reference component is null, naming the component
     * @throws IllegalArgumentException if a count is negative, or if {@code timedOut} is set while
     *     {@code cancellationRequested} is not -- a timeout that asked for no cancellation did not
     *     happen
     */
    public StageOutcome {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(redactedDisplayCommand, "redactedDisplayCommand");
        Objects.requireNonNull(logFile, "logFile");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
        requireNotNegative(standardOutputLines, "standardOutputLines");
        requireNotNegative(standardErrorLines, "standardErrorLines");
        requireNotNegative(logWriteFailures, "logWriteFailures");
        if (timedOut && !cancellationRequested) {
            throw new IllegalArgumentException(
                    "a stage that timed out was cancelled by the timeout, so timedOut without"
                            + " cancellationRequested cannot happen");
        }
    }

    /**
     * How long the stage ran, from the run's injected clock.
     *
     * <p>Derived rather than stored, so that it cannot disagree with the two instants it is made
     * of.
     *
     * <p>The clock is a wall clock, as {@code R-PROC-01} requires it to be for the timestamps to be
     * assertable, so this is not a guaranteed-monotonic measurement: a run whose duration looks
     * impossible is an NTP step or a daylight-saving change, not a process that travelled in time.
     * {@code StartedProcess} makes the same trade for the same reason.
     *
     * @return the duration between {@link #startedAt()} and {@link #endedAt()}
     */
    public Duration duration() {
        return Duration.between(startedAt, endedAt);
    }

    private static void requireNotNegative(long count, String name) {
        if (count < 0) {
            throw new IllegalArgumentException(name + " must not be negative, but was: " + count);
        }
    }
}
