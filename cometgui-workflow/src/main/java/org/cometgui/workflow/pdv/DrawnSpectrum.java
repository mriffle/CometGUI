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

package org.cometgui.workflow.pdv;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One annotated spectrum PDV drew for a PSM, verified by parsing the figure (design decision
 * P11-6).
 *
 * @param psmId the PSM, as Percolator names it
 * @param nativeId the spectrum's native id in its source file, which PDV was asked to draw
 * @param spectrumFile the run's spectrum file the spectrum is in
 * @param pepXml the Comet pepXML PDV read the PSM from
 * @param peptide the peptide PDV annotated, as it named the figure
 * @param figure the PNG PDV wrote, under the run's {@code pdv/} directory
 * @param width the figure's width in pixels
 * @param height the figure's height in pixels
 */
public record DrawnSpectrum(
        String psmId,
        String nativeId,
        Path spectrumFile,
        Path pepXml,
        String peptide,
        Path figure,
        int width,
        int height) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public DrawnSpectrum {
        Objects.requireNonNull(psmId, "psmId");
        Objects.requireNonNull(nativeId, "nativeId");
        Objects.requireNonNull(spectrumFile, "spectrumFile");
        Objects.requireNonNull(pepXml, "pepXml");
        Objects.requireNonNull(peptide, "peptide");
        Objects.requireNonNull(figure, "figure");
    }
}
