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

package org.cometgui.ui.viewmodel.params;

/**
 * What the file system says about an input file, in words. Existence and readability only: the
 * format and the decoy scan are checked before a run (Phase 08).
 */
public enum FileStatus {

    /** The file exists and can be read. */
    READABLE("Found and readable"),

    /** Nothing exists at the path. */
    MISSING("Not found"),

    /** The file exists but cannot be read. */
    UNREADABLE("Found but cannot be read"),

    /** The path names a folder. */
    DIRECTORY("A folder, not a file"),

    /** The text is not a path this system can use. */
    NOT_A_PATH("Not a usable path"),

    /** No file has been given. */
    NONE("No file chosen");

    private final String words;

    FileStatus(String words) {
        this.words = words;
    }

    /**
     * The status in words.
     *
     * @return for example {@code Not found}
     */
    public String words() {
        return words;
    }
}
