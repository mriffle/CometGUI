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

package org.cometgui.workflow.provenance;

/**
 * What a file on disk said when it was hashed again and compared with its record.
 *
 * <p>Four outcomes, never fewer: a file that is gone and a file that cannot be read are different
 * findings with different remedies, and neither is "differs".
 */
public enum DiskOutcome {

    /** Both digests computed now equal both digests recorded. */
    MATCHES("matches"),

    /** The file was read and at least one digest is not the recorded one. */
    DIFFERS("differs"),

    /** No file exists at the recorded path. */
    MISSING("missing"),

    /** Something exists at the recorded path and could not be read as a file. */
    UNREADABLE("unreadable");

    private final String word;

    DiskOutcome(String word) {
        this.word = word;
    }

    /**
     * The word the Provenance section shows.
     *
     * @return the display word, lower case
     */
    public String word() {
        return word;
    }
}
