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
 * A FASTA file could not be scanned for decoys: it is missing, unreadable, empty, or not a FASTA
 * file. The message names the file and says which.
 */
public final class FastaScanException extends IOException {

    private static final long serialVersionUID = 1L;

    /** The file. */
    private final transient Path file;

    /**
     * An exception about one file.
     *
     * @param file the file
     * @param problem what is wrong with it, completing the sentence "the FASTA file F ..."
     * @param cause what the file system reported, or {@code null}
     */
    public FastaScanException(Path file, String problem, Throwable cause) {
        super("the FASTA file " + Objects.requireNonNull(file, "file") + " " + problem, cause);
        this.file = file;
    }

    /**
     * The file that could not be scanned.
     *
     * @return the file, as it was named to the scanner
     */
    public Path file() {
        return file;
    }
}
