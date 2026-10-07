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

package org.cometgui.results.parser;

import java.util.List;
import java.util.Objects;

/**
 * One row of a Percolator PSM or peptide table.
 *
 * <p>Every field is kept as written. The score and the posterior error probability are also read as
 * numbers, {@link Double#NaN} where the text is not a decimal number; only the q-value has a policy
 * for that case ({@code R-RES-02}), carried by {@link QValue}.
 *
 * @param line the row's line number in the file, counting the header as line 1
 * @param psmId the {@code PSMId} field
 * @param scoreText the {@code score} field as written
 * @param score the score, or {@link Double#NaN} if {@code scoreText} is not a decimal number
 * @param qValue the {@code q-value} field
 * @param posteriorErrorProbabilityText the {@code posterior_error_prob} field as written
 * @param posteriorErrorProbability the posterior error probability, or {@link Double#NaN} if its
 *     text is not a decimal number
 * @param peptide the {@code peptide} field
 * @param proteinIds every protein the row names, in order, as written: the {@code proteinIds} field
 *     and, when that is the last column, every field after it
 */
public record ResultRow(
        long line,
        String psmId,
        String scoreText,
        double score,
        QValue qValue,
        String posteriorErrorProbabilityText,
        double posteriorErrorProbability,
        String peptide,
        List<String> proteinIds) {

    /**
     * A row.
     *
     * @throws NullPointerException if any text field is {@code null}
     */
    public ResultRow {
        Objects.requireNonNull(psmId, "psmId");
        Objects.requireNonNull(scoreText, "scoreText");
        Objects.requireNonNull(qValue, "qValue");
        Objects.requireNonNull(posteriorErrorProbabilityText, "posteriorErrorProbabilityText");
        Objects.requireNonNull(peptide, "peptide");
        proteinIds = List.copyOf(proteinIds);
    }
}
