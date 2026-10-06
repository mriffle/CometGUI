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
 * A file Comet wrote -- a pepXML or a PIN file -- or the merged PIN made from them is missing,
 * empty, truncated or malformed, or cannot be merged. The message always names the file, and a
 * merge failure between two files names both.
 */
public final class CometOutputException extends IOException {

    private static final long serialVersionUID = 1L;

    /** The file the problem is about. */
    private final transient Path file;

    /**
     * An exception about one file.
     *
     * @param file the file the problem is about
     * @param message the whole message, which names the file
     * @param cause what the file system or the parser reported, or {@code null}
     */
    CometOutputException(Path file, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * The file the problem is about; for a merge mismatch, the second of the two files.
     *
     * @return the file, as it was named to the validator or the merge
     */
    public Path file() {
        return file;
    }
}
