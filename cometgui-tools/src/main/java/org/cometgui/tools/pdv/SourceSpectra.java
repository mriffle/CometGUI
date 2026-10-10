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

/** Reads a run's spectrum source in whichever supported format it is. */
public final class SourceSpectra {

    private SourceSpectra() {}

    /**
     * Streams every MS2 spectrum of a source file, in file order.
     *
     * @param file the source file
     * @param format its format, as {@link PdvSpectrumSupport#check} decided it
     * @param visitor receives each spectrum
     * @throws SpectrumFileException if a spectrum cannot be read exactly
     * @throws IOException if the file cannot be read, or the visitor throws
     * @throws IllegalArgumentException if the format is one CometGUI does not read, which {@link
     *     PdvSpectrumSupport} refuses before any read is attempted
     * @throws NullPointerException if any argument is {@code null}
     */
    public static void read(Path file, SpectrumFileFormat format, SpectrumVisitor visitor)
            throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(visitor, "visitor");
        switch (format) {
            case MZML -> MzMlSpectrumReader.read(file, visitor);
            case MGF -> MgfSpectrumReader.read(file, visitor);
            default ->
                    throw new IllegalArgumentException(
                            "CometGUI reads no "
                                    + format.displayName()
                                    + " spectra; such a file is refused before it is read");
        }
    }
}
