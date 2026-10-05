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

import java.nio.file.Path;
import java.util.Objects;

/**
 * One spectrum file the scientist chose, with what the file system says about it.
 *
 * @param path the full path, never shortened (the specification: do not truncate the full path in
 *     accessible text or tooltips)
 * @param status existence and readability
 */
public record InputFile(Path path, FileStatus status) {

    /** Validates presence. */
    public InputFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(status, "status");
    }

    /**
     * The status and the full path, in one line.
     *
     * @return for example {@code Found and readable: /data/run1.mzML}; the status alone when there
     *     is no path to show
     */
    public String text() {
        String shown = path.toString();
        return shown.isEmpty() ? status.words() : status.words() + ": " + shown;
    }
}
