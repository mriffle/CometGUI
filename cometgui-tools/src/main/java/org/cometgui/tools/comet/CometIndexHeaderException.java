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

package org.cometgui.tools.comet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A file's Comet index header could not be read: the file is missing or unreadable, is not a
 * versioned Comet index, or its header holds something this reader cannot vouch for. The message
 * names the file and says which, and an index refused here is never searched unchecked.
 */
public final class CometIndexHeaderException extends IOException {

    private static final long serialVersionUID = 1L;

    /** The file. */
    private final transient Path file;

    /**
     * An exception about one file.
     *
     * @param file the file
     * @param problem what is wrong with it, completing the sentence "the index file F ..."
     * @param cause what the file system reported, or {@code null}
     */
    public CometIndexHeaderException(Path file, String problem, Throwable cause) {
        super("the index file " + Objects.requireNonNull(file, "file") + " " + problem, cause);
        this.file = file;
    }

    /**
     * The file whose header could not be read.
     *
     * @return the file, as it was named to the reader
     */
    public Path file() {
        return file;
    }
}
