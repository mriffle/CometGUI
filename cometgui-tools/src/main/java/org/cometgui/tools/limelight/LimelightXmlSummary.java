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

package org.cometgui.tools.limelight;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What a Limelight XML file that passed every check holds.
 *
 * @param file the file
 * @param size its size in bytes, positive
 * @param psmCount how many {@code psm} elements it holds
 * @param reportedPeptideCount how many {@code reported_peptide} elements it holds
 * @param psmQValueDefault the {@code default_filter_value} of the Percolator PSM-level {@code
 *     q-value} annotation type, as written -- where the converter puts its {@code -q} -- or empty
 *     when the file carries none
 */
public record LimelightXmlSummary(
        Path file,
        long size,
        int psmCount,
        int reportedPeptideCount,
        Optional<String> psmQValueDefault) {

    /**
     * Validates the summary.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the size is not positive or a count is negative
     */
    public LimelightXmlSummary {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(psmQValueDefault, "psmQValueDefault");
        if (size <= 0L || psmCount < 0 || reportedPeptideCount < 0) {
            throw new IllegalArgumentException(
                    "a valid Limelight XML file has a positive size and no negative count, but"
                            + " was size="
                            + size
                            + " psms="
                            + psmCount
                            + " reportedPeptides="
                            + reportedPeptideCount);
        }
    }

    /**
     * The same summary for a file that has been moved.
     *
     * @param moved where the file is now
     * @return the summary naming that path
     */
    public LimelightXmlSummary at(Path moved) {
        return new LimelightXmlSummary(
                moved, size, psmCount, reportedPeptideCount, psmQValueDefault);
    }
}
