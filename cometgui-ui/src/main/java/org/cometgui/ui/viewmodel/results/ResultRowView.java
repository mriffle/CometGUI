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

package org.cometgui.ui.viewmodel.results;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.results.filtering.store.RowKey;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.SpectrumReference;

/**
 * One row of a results table as the interface shows it: its stable key and its cells as text.
 *
 * <p>The score, q-value and PEP are <strong>exactly as Percolator wrote them</strong> -- never read
 * as a number and written again -- so a q-value of {@code 1e-3} or {@code NaN} is shown as {@code
 * 1e-3} or {@code NaN}. The source file, scan and charge come from the row's {@link
 * SpectrumReference}, read by the parser from the {@code PSMId}; they are empty when the {@code
 * PSMId} is not in Comet's {@code SpecId} shape. The proteins are joined by {@value
 * #PROTEIN_SEPARATOR}.
 *
 * @param key the row's stable key: its line in the raw file
 * @param psmId the {@code PSMId}
 * @param sourceFile the spectrum file's display name; the base itself when the run's inputs do not
 *     name it; empty when the {@code PSMId} is not in {@code SpecId} shape
 * @param scan the scan number, or empty
 * @param charge the precursor charge, or empty
 * @param peptide the peptide
 * @param proteins every protein, joined
 * @param score the score as written
 * @param qValue the q-value as written
 * @param pep the posterior error probability as written
 */
public record ResultRowView(
        RowKey key,
        String psmId,
        String sourceFile,
        String scan,
        String charge,
        String peptide,
        String proteins,
        String score,
        String qValue,
        String pep) {

    /** What the proteins of a row are joined by. */
    public static final String PROTEIN_SEPARATOR = ", ";

    /**
     * A row view.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public ResultRowView {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(psmId, "psmId");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(scan, "scan");
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(peptide, "peptide");
        Objects.requireNonNull(proteins, "proteins");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(qValue, "qValue");
        Objects.requireNonNull(pep, "pep");
    }

    /**
     * The view of a row the store returned.
     *
     * @param row the row
     * @param sourceFiles each Comet {@code -N} base to its spectrum file's display name
     * @return the view
     */
    public static ResultRowView of(ResultRow row, Map<String, String> sourceFiles) {
        Objects.requireNonNull(sourceFiles, "sourceFiles");
        String source = "";
        String scan = "";
        String charge = "";
        Optional<SpectrumReference> reference = row.spectrumReference();
        if (reference.isPresent()) {
            SpectrumReference spectrum = reference.get();
            source = sourceFiles.getOrDefault(spectrum.base(), spectrum.base());
            scan = Long.toString(spectrum.scan());
            charge = Integer.toString(spectrum.charge());
        }
        return new ResultRowView(
                RowKey.of(row),
                row.psmId(),
                source,
                scan,
                charge,
                row.peptide(),
                String.join(PROTEIN_SEPARATOR, row.proteinIds()),
                row.scoreText(),
                row.qValue().text(),
                row.posteriorErrorProbabilityText());
    }

    /**
     * One cell's text.
     *
     * @param column the column
     * @return the cell
     */
    public String cell(ResultsColumn column) {
        return switch (column) {
            case PSM_ID -> psmId;
            case SOURCE_FILE -> sourceFile;
            case SCAN -> scan;
            case CHARGE -> charge;
            case PEPTIDE -> peptide;
            case PROTEINS -> proteins;
            case SCORE -> score;
            case Q_VALUE -> qValue;
            case PEP -> pep;
        };
    }
}
