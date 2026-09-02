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

package org.cometgui.domain.log;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.run.StageTag;

/**
 * One line of console output, as an immutable value.
 *
 * <p><strong>The timestamp arrives with the message.</strong> Nothing in this package calls {@code
 * Instant.now()}: the caller holds a {@link Clock}, which is one of the injection seams this phase
 * exists to install, and a test can therefore assert the exact instant on a message rather than
 * asserting that some instant was produced. {@link #recordedBy(Clock, StageTag, MessageSeverity,
 * String)} is the form that shows the seam at the call site.
 *
 * <p>The text is stored exactly as it was given, including an empty string. A tool that emits a
 * blank line has emitted a line, and a console that silently swallows it is misreporting the tool's
 * output; the console is free to render it as nothing at all, but the model does not decide that.
 *
 * @param timestamp when the line was recorded, from the caller's clock
 * @param stage the workflow stage that produced the line, or empty for a message that belongs to no
 *     stage -- application narration before a run starts, for instance
 * @param severity how much attention the line deserves
 * @param text the line itself, exactly as given, possibly empty but never {@code null}
 */
public record LogMessage(
        Instant timestamp, Optional<StageTag> stage, MessageSeverity severity, String text) {

    /**
     * Validates the message.
     *
     * @throws NullPointerException if any component is {@code null}, naming the component -- an
     *     absent stage is {@link Optional#empty()}, never a {@code null} Optional
     */
    public LogMessage {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(text, "text");
    }

    /**
     * A message recorded at a known instant.
     *
     * @param timestamp when the line was recorded
     * @param stage the stage that produced it, or {@code null} for a message belonging to no stage
     * @param severity how much attention the line deserves
     * @param text the line itself, possibly empty
     * @return the message
     * @throws NullPointerException if {@code timestamp}, {@code severity} or {@code text} is {@code
     *     null}
     */
    public static LogMessage at(
            Instant timestamp, StageTag stage, MessageSeverity severity, String text) {
        return new LogMessage(timestamp, Optional.ofNullable(stage), severity, text);
    }

    /**
     * A message recorded now, where "now" is whatever the supplied clock says.
     *
     * <p>This is the form the process service uses. The clock is a parameter so that the domain
     * never reads the system clock itself and so that a test can pin the instant with {@link
     * Clock#fixed(Instant, java.time.ZoneId)}.
     *
     * @param clock the caller's clock, read once
     * @param stage the stage that produced the line, or {@code null} for a message belonging to no
     *     stage
     * @param severity how much attention the line deserves
     * @param text the line itself, possibly empty
     * @return the message, timestamped from the clock
     * @throws NullPointerException if {@code clock}, {@code severity} or {@code text} is {@code
     *     null}
     */
    public static LogMessage recordedBy(
            Clock clock, StageTag stage, MessageSeverity severity, String text) {
        return at(Objects.requireNonNull(clock, "clock").instant(), stage, severity, text);
    }
}
