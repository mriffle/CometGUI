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
 * What a provenance event says happened.
 *
 * <p>The seven kinds below are the ones a run can emit before any stage exists to emit them, which
 * is the point of phase 04: the model is built first "so that no stage can be built without
 * recording itself". A stage that starts, invokes a tool, hashes a file, warns, and finishes has a
 * constant here for every one of those moments, so a later phase records its work by choosing a
 * constant rather than by inventing a vocabulary.
 *
 * <p><strong>Why the wire name is a field and not {@code name().toLowerCase()}.</strong> The
 * argument is recorded in full on {@link org.cometgui.provenance.manifest.ProvenanceStatus} and it
 * applies here word for word: {@code "RUN_STARTED".toLowerCase()} spells itself with a dotless
 * {@code ı} under a Turkish default locale, and deriving the on-disk token from the Java constant
 * would let an ordinary rename silently change the format of every event log ever written. It would
 * also have to invent a rule for turning {@code RUN_STARTED} into two words, and a rule is one more
 * thing that can differ between the writer and the reader. Here the two names are independent, and
 * the tests pin every wire name as a hand-typed literal.
 *
 * <p>The wire names are dotted, lower case and two-segment -- {@code run.started}, {@code
 * stage.finished} -- matching the shape {@code
 * org.cometgui.provenance.manifest.ProvenanceSchema#SETTINGS_KEY_PATTERN} already fixes for the
 * other open namespace in a provenance document. A reader can therefore group an event log by its
 * first segment without a table.
 */
public enum ProvenanceEventType {

    /** The run began: the moment everything later in the log is relative to. */
    RUN_STARTED("run.started"),

    /** One stage of the workflow began -- the search, the rescoring, a conversion. */
    STAGE_STARTED("stage.started"),

    /**
     * One stage of the workflow ended, however it ended. The outcome belongs in the payload; the
     * type says only that the stage is no longer running.
     */
    STAGE_FINISHED("stage.finished"),

    /**
     * A tool was launched: the executable, its version and the argument array that {@code
     * AC-PRV-03} requires to be recorded exactly.
     */
    TOOL_INVOKED("tool.invoked"),

    /** A file's MD5 and SHA-256 were computed, so that the digests are in the log as they land. */
    FILE_HASHED("file.hashed"),

    /**
     * Something the run survived but a scientist must be told about -- a capability that was not
     * available, an input that was accepted with a caveat.
     */
    WARNING_RAISED("warning.raised"),

    /**
     * The run ended, carrying the terminal {@link
     * org.cometgui.provenance.manifest.ProvenanceStatus} it ended in.
     *
     * <p>An event of this type is required to carry that status; see {@link
     * ProvenanceEvent#STATUS_KEY}. A log whose last line says only "the run finished" cannot answer
     * the question {@code AC-PRV-06} asks of a failed run.
     */
    RUN_FINISHED("run.finished");

    private final String wireName;

    ProvenanceEventType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The token written to and read from the event log.
     *
     * <p>Stable across renames of the Java constant, and identical on every machine regardless of
     * the JVM default locale; see the class documentation for why both matter.
     *
     * @return the dotted lower-case wire name, never {@code null}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves a wire name read from an event log back to its constant.
     *
     * <p>Exact match, deliberately: no trimming, no case folding, no "close enough", for the reason
     * given on {@code ProvenanceStatus.fromWireName}. A line whose type is {@code "Run.Started"}
     * was not written by this application.
     *
     * @param wire the token to resolve
     * @return the matching type
     * @throws NullPointerException if {@code wire} is {@code null}
     * @throws IllegalArgumentException if no type has that wire name, with a message naming the
     *     rejected value and listing what is accepted
     */
    public static ProvenanceEventType fromWireName(String wire) {
        Objects.requireNonNull(wire, "wire");
        for (ProvenanceEventType type : values()) {
            if (type.wireName.equals(wire)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "no provenance event type has the wire name \""
                        + wire
                        + "\"; expected one of [run.started, stage.started, stage.finished,"
                        + " tool.invoked, file.hashed, warning.raised, run.finished]");
    }
}
