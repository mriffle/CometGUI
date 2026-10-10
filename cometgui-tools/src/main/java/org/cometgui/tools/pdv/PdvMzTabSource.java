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

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One spectrum file of a run and the Comet output searched from it: one {@code ms_run} of the
 * mzTab.
 *
 * @param psmIdBase the base every Percolator {@code PSMId} from this file starts with -- Comet's
 *     {@code -N}, which Comet writes verbatim at the start of each PIN {@code SpecId}
 * @param spectrumFile the spectrum file Comet searched
 * @param format its format, as {@link PdvSpectrumSupport#check} decided it
 * @param pepXml Comet's pepXML for it, {@code <base>.pep.xml}: every target hit, and the decoy hits
 *     too when Comet reported them together ({@code decoy_search} 0 or 1)
 * @param decoyPepXml Comet's separate decoy pepXML, {@code <base>.decoy.pep.xml}, when the search
 *     wrote one ({@code decoy_search = 2}): the decoy-table rows of this file are then joined to it
 */
public record PdvMzTabSource(
        String psmIdBase,
        Path spectrumFile,
        SpectrumFileFormat format,
        Path pepXml,
        Optional<Path> decoyPepXml) {

    /**
     * Validates the source.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the base is empty
     */
    public PdvMzTabSource {
        Objects.requireNonNull(psmIdBase, "psmIdBase");
        Objects.requireNonNull(spectrumFile, "spectrumFile");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(pepXml, "pepXml");
        Objects.requireNonNull(decoyPepXml, "decoyPepXml");
        if (psmIdBase.isEmpty()) {
            throw new IllegalArgumentException("a source's PSMId base must not be empty");
        }
    }
}
