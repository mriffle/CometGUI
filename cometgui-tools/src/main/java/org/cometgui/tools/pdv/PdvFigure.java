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

import java.util.Objects;

/**
 * One verified figure PDV drew.
 *
 * @param spectrumId the requested native id the figure is for
 * @param peptide the peptide PDV annotated, read from the start of the figure's file name, which
 *     PDV writes as {@code <peptide><native id>.png}
 * @param figure the figure, parsed and verified
 */
public record PdvFigure(String spectrumId, String peptide, PngFigure figure) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PdvFigure {
        Objects.requireNonNull(spectrumId, "spectrumId");
        Objects.requireNonNull(peptide, "peptide");
        Objects.requireNonNull(figure, "figure");
    }
}
