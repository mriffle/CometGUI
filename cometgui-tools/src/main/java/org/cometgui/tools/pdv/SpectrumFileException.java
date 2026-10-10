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

package org.cometgui.tools.pdv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A spectrum file, or one spectrum in it, cannot be read or written faithfully.
 *
 * <p>Raised rather than worked around: a spectrum CometGUI cannot decode exactly -- numpress, an
 * unknown compression, an integer array, a length that does not match its declaration -- is refused
 * naming the file and the spectrum, never approximated. The message always starts with the file,
 * and with the spectrum's native id and index when the problem is in one spectrum.
 */
public final class SpectrumFileException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient Path file;

    /**
     * A problem with the file as a whole.
     *
     * @param file the spectrum file
     * @param problem what is wrong
     */
    SpectrumFileException(Path file, String problem) {
        super(Objects.requireNonNull(file, "file") + ": " + problem);
        this.file = file;
    }

    /**
     * A problem with the file as a whole, caused by another exception.
     *
     * @param file the spectrum file
     * @param problem what is wrong
     * @param cause the underlying failure
     */
    SpectrumFileException(Path file, String problem, Throwable cause) {
        super(Objects.requireNonNull(file, "file") + ": " + problem, cause);
        this.file = file;
    }

    /**
     * A problem with one spectrum.
     *
     * @param file the spectrum file
     * @param nativeId the spectrum's native id
     * @param index the spectrum's 0-based index
     * @param problem what is wrong
     */
    SpectrumFileException(Path file, String nativeId, int index, String problem) {
        this(file, "spectrum \"" + nativeId + "\" (index " + index + "): " + problem);
    }

    /**
     * The file the problem is in.
     *
     * @return the file
     */
    public Path file() {
        return file;
    }
}
