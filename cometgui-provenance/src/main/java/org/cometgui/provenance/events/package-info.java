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

/**
 * The provenance event stream: an append-only log of what a run did, written so that a run which
 * dies still leaves a readable history.
 *
 * <p>{@code R-PROV-05} allows provenance to be written "incrementally as appendable events, or as
 * atomically updated state, so that a crash still leaves useful history". This package is the first
 * of those two; {@code org.cometgui.provenance.io} is the second. The difference is what each one
 * is for: a manifest is a document that is replaced as a whole when a run ends, while a log is a
 * history that has to be complete and readable at every instant <em>during</em> the run, including
 * the instant the process is killed.
 *
 * <p>Three decisions carry that promise, and each is documented where it is implemented.
 *
 * <ul>
 *   <li><b>One event per line.</b> {@link org.cometgui.provenance.events.EventLineFormat} writes a
 *       newline-terminated JSON object per record. A crash can then damage only the last line. A
 *       single top-level JSON array could not be read at all, because the closing bracket is
 *       written last and a dead process never writes it.
 *   <li><b>Every record is forced to the device before the append returns.</b> {@link
 *       org.cometgui.provenance.events.ProvenanceEventLog} does not buffer, and the force sits on a
 *       seam so that a test can count it: an unforced log is byte-identical to a forced one and
 *       differs only when the power goes out.
 *   <li><b>Damage is data, not an exception.</b> {@link
 *       org.cometgui.provenance.events.ProvenanceEventLogReader} recovers every complete record
 *       from a torn or doctored file and reports what was wrong as a list of {@link
 *       org.cometgui.provenance.events.EventLogDefect}s, including a sequence gap, which is the one
 *       kind of loss that leaves no trace in the bytes.
 * </ul>
 *
 * <p>Every payload value is cleaned by the shared {@link
 * org.cometgui.domain.secrets.SecretRedactor} on the way out, in one place, so that adding an event
 * type cannot open a leak path.
 *
 * <p>Filled by phase 04 (hashing and provenance core), work unit 8.
 */
package org.cometgui.provenance.events;
