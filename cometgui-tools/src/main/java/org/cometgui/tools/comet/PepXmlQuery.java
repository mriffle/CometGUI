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

import java.util.Objects;
import java.util.Optional;

/**
 * One {@code spectrum_query}'s own attributes, as Comet states them; its hits are delivered beside
 * it ({@link PepXmlVisitor#spectrumQuery}).
 *
 * <p>The numbers that are integers in the file are carried as numbers: the reader accepts only
 * their canonical form (no sign, no leading zero), so {@code Long.toString} of each is the file's
 * own text. The decimals are carried as their text ({@link PepXmlDecimal}). Comet omits {@code
 * spectrumNativeID} when the spectrum has no native id, and {@code retention_time_sec} when the
 * retention time is not positive.
 *
 * @param spectrum {@code spectrum}, verbatim: Comet's {@code <file>.<scan>.<scan>.<charge>} with
 *     the scan zero-padded to five digits
 * @param spectrumNativeId {@code spectrumNativeID}, when present
 * @param startScan {@code start_scan}: the scan number Percolator's {@code PSMId} carries
 * @param endScan {@code end_scan}
 * @param precursorNeutralMass {@code precursor_neutral_mass}
 * @param assumedCharge {@code assumed_charge}
 * @param index {@code index}, Comet's 1-based query number
 * @param retentionTimeSec {@code retention_time_sec}, when present
 */
public record PepXmlQuery(
        String spectrum,
        Optional<String> spectrumNativeId,
        long startScan,
        long endScan,
        PepXmlDecimal precursorNeutralMass,
        int assumedCharge,
        long index,
        Optional<PepXmlDecimal> retentionTimeSec) {

    /**
     * Validates the query.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PepXmlQuery {
        Objects.requireNonNull(spectrum, "spectrum");
        Objects.requireNonNull(spectrumNativeId, "spectrumNativeId");
        Objects.requireNonNull(precursorNeutralMass, "precursorNeutralMass");
        Objects.requireNonNull(retentionTimeSec, "retentionTimeSec");
    }
}
