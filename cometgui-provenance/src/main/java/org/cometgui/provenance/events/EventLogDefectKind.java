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

/**
 * The kinds of damage {@link ProvenanceEventLogReader} can find in an event log.
 *
 * <p>Three, and the first two are deliberately distinct. A torn final line is the ordinary
 * signature of a crash: the process died between the write and the newline, so the record is
 * incomplete and there is nothing wrong with the log except that it stops there. A malformed line
 * in the middle is something else entirely -- a file that was edited, transferred in text mode,
 * concatenated, or hit by a filesystem writing a block of zeroes -- and a reader that reported both
 * as "a bad line" would leave a scientist unable to tell "the run was killed" from "this file has
 * been altered".
 *
 * <p><strong>These constants have no wire name, unlike {@link ProvenanceEventType}.</strong> A
 * defect is never written to a log; it is produced by reading one. There is no on-disk token to
 * keep stable across a rename, so there is nothing here for a wire name to protect, and adding one
 * would imply a persistence contract that does not exist.
 */
public enum EventLogDefectKind {

    /**
     * The file ends with bytes that no newline terminates: the process died mid-append.
     *
     * <p>The bytes are not parsed, even if they look complete. Without the terminator there is no
     * way to know whether the record ends there or whether the rest of it never reached the disk,
     * and a reader that guessed would occasionally recover an event whose last field had been
     * silently cut short.
     */
    TORN_FINAL_LINE,

    /**
     * A complete line -- one that ends in a newline -- that is not a record this application wrote.
     *
     * <p>Includes a line that is not valid UTF-8, an empty line, and the healed remains of a torn
     * record that a later run terminated so that its damage could not swallow the records appended
     * after it.
     */
    MALFORMED_LINE,

    /**
     * A sequence number that is not one more than the previous one.
     *
     * <p>This is the defect a log cannot show any other way. Whole events can be lost without
     * leaving a mark -- a filesystem that dropped a block, a file that was trimmed by hand -- and
     * the remaining lines are each perfectly well formed. The count of recovered events is then
     * wrong and nothing says so, which is precisely the "useful history" {@code R-PROV-05} is
     * about. A gap is reported at the line where the discontinuity appears, and reading continues
     * from the number actually found, so one lost record produces one defect rather than one per
     * surviving line.
     */
    SEQUENCE_GAP
}
