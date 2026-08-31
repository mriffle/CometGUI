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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * The text of one line in a stage log file. Pure: no clock, no file, no state.
 *
 * <h2>The format</h2>
 *
 * <pre>
 * 2026-08-31T19:04:51.250Z [cometgui] stage comet started in /runs/2026-08-31/work
 * 2026-08-31T19:04:51.250Z [cometgui] command ["/opt/comet/comet", "-Pcomet.params", "a.mzML"]
 * 2026-08-31T19:04:51.312Z [stdout] Comet version 2024.01 rev. 0
 * 2026-08-31T19:04:51.480Z [stderr] Search 12% complete
 * 2026-08-31T19:06:03.007Z [cometgui] stage comet ended: exit code 0 after PT1M11.757S
 * </pre>
 *
 * <p>Three decisions are visible there and each of them is load-bearing.
 *
 * <p><strong>Every line carries the stream it came from.</strong> The process service keeps
 * standard output and standard error independent all the way from the pipe to the listener, and a
 * merged file that could not say which stream a line arrived on would throw that away at the last
 * step -- exactly when the reader needs it, which is when a tool has died without explaining
 * itself. The tags {@code stdout} and {@code stderr} are the same length, so the text of every tool
 * line starts at the same column and a reader can split the file at a fixed offset. {@code
 * cometgui} marks the few lines the service wrote itself; a tool line can never be mistaken for
 * one, because the tag is chosen here rather than taken from the text.
 *
 * <p><strong>Every line carries a timestamp, taken from the injected clock</strong> ({@code
 * R-PROC-01}), not from {@code Instant.now()}. It is always rendered in UTC, whatever zone the
 * clock reports, because a run's log is read later, elsewhere, next to a provenance record that is
 * also in UTC; a local timestamp with no offset is the one form that cannot be compared with
 * anything. The fraction is <em>truncated</em> to milliseconds, so the width is fixed at 24
 * characters and two lines a microsecond apart can share a timestamp -- their order in the file is
 * still their order.
 *
 * <p><strong>The line terminator is always {@code \n}</strong>, on every platform. A stage log is
 * an artefact of a run that may be read on a different machine from the one that produced it, and a
 * file whose line endings depend on where it was written is one more thing that can differ between
 * two runs that should be identical. See {@link StageLogFile}, which writes it.
 *
 * <p>Every method here is pure and total, which is the point of the class: rendering is the only
 * part of writing a log file that can be proved exhaustively without a disk or a thread.
 */
final class StageLogFormat {

    /** Marks a line the process service wrote rather than one a tool wrote. */
    static final String SERVICE_TAG = "cometgui";

    /**
     * The timestamp: {@code 2026-08-31T19:04:51.250Z}, fixed width, always UTC.
     *
     * <p>{@code uuuu} rather than {@code yyyy} so the year is the proleptic year rather than the
     * year of an era, which is the form that round-trips.
     */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC);

    private StageLogFormat() {
        throw new AssertionError("StageLogFormat is a helper, not a type to instantiate");
    }

    /**
     * One log line, without its terminator.
     *
     * @param at when the line was recorded, from the run's clock
     * @param tag {@code stdout}, {@code stderr} or {@link #SERVICE_TAG}
     * @param text the line itself, already redacted; may be empty
     * @return the rendered line
     * @throws NullPointerException if any argument is null
     */
    static String line(Instant at, String tag, String text) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(text, "text");
        return TIMESTAMP.format(at) + " [" + tag + "] " + text;
    }

    /**
     * The first header line: which stage started, and where it ran.
     *
     * @param stageId the validated stage identifier
     * @param workingDirectory the directory the tool was started in ({@code R-PROC-04})
     * @return the text, without timestamp or tag
     * @throws NullPointerException if any argument is null
     */
    static String startedText(String stageId, Path workingDirectory) {
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        return "stage " + stageId + " started in " + workingDirectory;
    }

    /**
     * The second header line: what was run, redacted.
     *
     * <p>The argument is expected to have been through {@link
     * ProcessRedactor#redactedDisplayCommand}, which is also what escapes it. Nothing here escapes
     * or quotes anything, so that there is exactly one implementation of that in the product.
     *
     * @param redactedDisplayCommand the rendered, redacted argument array
     * @return the text, without timestamp or tag
     * @throws NullPointerException if the argument is null
     */
    static String commandText(String redactedDisplayCommand) {
        Objects.requireNonNull(redactedDisplayCommand, "redactedDisplayCommand");
        return "command " + redactedDisplayCommand;
    }

    /**
     * The footer line: how the stage ended.
     *
     * <p>A cancelled stage and a stage that ran out of time are named separately, because they mean
     * different things to whoever reads the log: one is something the user did, the other is
     * something the configuration did. A stage that timed out reports both, since the timeout
     * cancels it.
     *
     * @param stageId the validated stage identifier
     * @param exitCode the code the operating system reported
     * @param duration how long the stage ran, from the run's clock
     * @param cancellationRequested whether anything asked the stage to stop
     * @param timedOut whether what asked was the stage's own timeout
     * @return the text, without timestamp or tag
     * @throws NullPointerException if {@code stageId} or {@code duration} is null
     */
    static String endedText(
            String stageId,
            int exitCode,
            Duration duration,
            boolean cancellationRequested,
            boolean timedOut) {
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(duration, "duration");
        StringBuilder text = new StringBuilder(64);
        text.append("stage ")
                .append(stageId)
                .append(" ended: exit code ")
                .append(exitCode)
                .append(" after ")
                .append(duration);
        if (cancellationRequested) {
            text.append(", cancellation requested");
        }
        if (timedOut) {
            text.append(", timed out");
        }
        return text.toString();
    }

    /**
     * The line written instead of a footer when the tool never started at all.
     *
     * <p>A log file that exists and says why nothing followed is worth much more than an empty one,
     * or than none: the header above it already names the command and the working directory, which
     * are what the reader is about to want.
     *
     * @param stageId the validated stage identifier
     * @param failure the failure, already redacted
     * @return the text, without timestamp or tag
     * @throws NullPointerException if any argument is null
     */
    static String couldNotStartText(String stageId, String failure) {
        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(failure, "failure");
        return "stage " + stageId + " could not be started: " + failure;
    }
}
