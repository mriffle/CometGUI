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

/**
 * Receives each MS2 spectrum of a source file, in file order, as a reader streams it.
 *
 * <p>A visitor that throws stops the read; the exception reaches the reader's caller unchanged.
 */
@FunctionalInterface
public interface SpectrumVisitor {

    /**
     * Receives one spectrum.
     *
     * @param spectrum the spectrum
     * @throws IOException to stop reading
     */
    void visit(SourceSpectrum spectrum) throws IOException;
}
