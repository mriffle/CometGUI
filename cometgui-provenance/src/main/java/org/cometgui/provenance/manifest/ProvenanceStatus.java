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

import java.util.Objects;

/**
 * The state a run, a process or a file was left in.
 *
 * <p>{@code R-PROV-01} requires a partial file from a failed or cancelled stage to be kept and
 * <em>marked</em>, and {@code AC-PRV-06} requires a failed or cancelled run to retain useful
 * provenance. Both need a vocabulary that distinguishes "this is the whole thing" from "this is
 * what existed when the run stopped", which is what this enum is. A manifest without it can only
 * record that a file exists, which is the same statement for a complete result and for the first
 * eight megabytes of one.
 *
 * <p><strong>Why the wire name is a field and not {@code name().toLowerCase()}.</strong> Two
 * separate defects, both of which this shape makes impossible.
 *
 * <ul>
 *   <li><em>Locale.</em> {@code "RUNNING".toLowerCase()} is {@code "running"} in most locales and
 *       {@code "runnıng"} -- with a dotless i -- when the JVM default locale is Turkish, because
 *       Turkish lowercases {@code I} to a dotless {@code ı}. {@code R-PROV-04} exists because the
 *       JVM default locale reaches serialisation, and a status that spells itself differently
 *       depending on the machine that wrote it is precisely that defect. {@code Locale.ROOT} would
 *       fix the locale but not the second problem.
 *   <li><em>Renaming.</em> A Java constant is a name in this codebase; a wire name is a token in
 *       every manifest ever written. Deriving one from the other means an ordinary rename silently
 *       changes the on-disk format and orphans every existing document. Here the two are
 *       independent, and the tests pin the wire names as hand-typed literals so that a rename
 *       cannot take one with it.
 * </ul>
 */
public enum ProvenanceStatus {

    /** The run, process or file is still in progress; nothing about it is final yet. */
    RUNNING("running"),

    /** Finished, successfully, and the artefact is whole. */
    COMPLETED("completed"),

    /**
     * The artefact exists but is not whole -- the {@code partial} marking {@code R-PROV-01}
     * requires for a file left behind by a stage that did not finish.
     */
    PARTIAL("partial"),

    /** Ended in an error: a non-zero exit, an unreadable input, a rejected parameter. */
    FAILED("failed"),

    /** Stopped because the user asked it to stop, which is not the same thing as a failure. */
    CANCELLED("cancelled");

    private final String wireName;

    ProvenanceStatus(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The token written to and read from the provenance documents.
     *
     * <p>Stable across renames of the Java constant, and identical on every machine regardless of
     * the JVM default locale; see the class documentation for why both matter.
     *
     * @return the lower-case wire name, never {@code null}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves a wire name read from a provenance document back to its constant.
     *
     * <p>Exact match, deliberately: no trimming, no case folding, no "close enough". A document
     * containing {@code "Completed"} was not written by this application, and quietly accepting it
     * would mean a manifest whose status could not be re-derived from its own bytes.
     *
     * @param wire the token to resolve
     * @return the matching status
     * @throws NullPointerException if {@code wire} is {@code null}
     * @throws IllegalArgumentException if no status has that wire name, with a message naming the
     *     rejected value and listing what is accepted
     */
    public static ProvenanceStatus fromWireName(String wire) {
        Objects.requireNonNull(wire, "wire");
        for (ProvenanceStatus status : values()) {
            if (status.wireName.equals(wire)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "no provenance status has the wire name \""
                        + wire
                        + "\"; expected one of [running, completed, partial, failed, cancelled]");
    }
}
