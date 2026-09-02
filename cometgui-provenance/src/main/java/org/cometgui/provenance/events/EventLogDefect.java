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

import java.util.Objects;

/**
 * One thing that was wrong with an event log, and where it was.
 *
 * <p>A recovery that returned only the events it could read would be a silent shrug: the caller
 * could not tell a log that ended cleanly from one whose last record was cut in half, nor a
 * complete history from one with a hole in the middle. This record is the other half of {@link
 * RecoveredEventLog}, and it exists so that "what was wrong" is an inspectable value rather than a
 * line in a log file somewhere.
 *
 * <p>Both coordinates are given because they answer different questions. The line number is what a
 * person uses to look at the damage in an editor; the byte offset is what a tool uses to truncate,
 * skip or repair the file, and it is the only one of the two that survives a line whose bytes are
 * not valid text.
 *
 * <p><strong>The detail never quotes the file.</strong> See {@link EventLineFormat}: the bytes of a
 * damaged log are not necessarily bytes this application wrote and redacted, so a message that
 * echoed them could carry a credential out of a file and into a UI. Every detail below names an
 * offset, a count or an expectation.
 *
 * @param kind what sort of damage this is
 * @param lineNumber which line it was found on, counting from 1
 * @param byteOffset the offset in the file at which the damaged line starts, counting from 0
 * @param detail what was wrong, in words, quoting no content from the file
 */
public record EventLogDefect(
        EventLogDefectKind kind, long lineNumber, long byteOffset, String detail) {

    /**
     * Validates the defect.
     *
     * @throws NullPointerException if {@code kind} or {@code detail} is {@code null}
     * @throws IllegalArgumentException if {@code lineNumber} is below 1, if {@code byteOffset} is
     *     negative, or if {@code detail} is blank
     */
    public EventLogDefect {
        Objects.requireNonNull(kind, "kind");
        if (lineNumber < 1) {
            throw new IllegalArgumentException(
                    "a defect's line number counts from 1, but was: " + lineNumber);
        }
        if (byteOffset < 0) {
            throw new IllegalArgumentException(
                    "a defect's byte offset counts from 0, but was: " + byteOffset);
        }
        Objects.requireNonNull(detail, "detail");
        if (detail.isBlank()) {
            throw new IllegalArgumentException("a defect's detail must say what was wrong");
        }
    }
}
