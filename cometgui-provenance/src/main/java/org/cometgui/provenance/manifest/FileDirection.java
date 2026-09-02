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
 * Whether a recorded file went into the run or came out of it.
 *
 * <p>The specification's hash requirements cover "every regular input and output file used or
 * created by a run", and the two are not interchangeable: an input's hash proves what the tools
 * read, an output's hash proves what they produced. A manifest that recorded a set of files without
 * saying which were which could not answer either question.
 *
 * <p>The wire name is an explicit field for the same two reasons as {@link ProvenanceStatus}: a
 * locale-sensitive {@code name().toLowerCase()} spells {@code INPUT} as {@code "ınput"} under a
 * Turkish default locale, and deriving the on-disk token from the Java constant would let a rename
 * change the format.
 */
public enum FileDirection {

    /** A file the run read: spectra, a FASTA, a parameter file. */
    INPUT("input"),

    /** A file the run wrote: a search result, a converted document, an archived log. */
    OUTPUT("output");

    private final String wireName;

    FileDirection(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The token written to and read from the provenance documents.
     *
     * @return the lower-case wire name, never {@code null}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves a wire name read from a provenance document back to its constant.
     *
     * <p>Exact match, for the reason given on {@link ProvenanceStatus#fromWireName(String)}.
     *
     * @param wire the token to resolve
     * @return the matching direction
     * @throws NullPointerException if {@code wire} is {@code null}
     * @throws IllegalArgumentException if no direction has that wire name, with a message naming
     *     the rejected value and listing what is accepted
     */
    public static FileDirection fromWireName(String wire) {
        Objects.requireNonNull(wire, "wire");
        for (FileDirection direction : values()) {
            if (direction.wireName.equals(wire)) {
                return direction;
            }
        }
        throw new IllegalArgumentException(
                "no file direction has the wire name \""
                        + wire
                        + "\"; expected one of [input, output]");
    }
}
