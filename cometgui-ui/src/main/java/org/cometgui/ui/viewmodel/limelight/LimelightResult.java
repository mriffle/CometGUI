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

package org.cometgui.ui.viewmodel.limelight;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A conversion that produced valid Limelight XML, as the workflow recorded it: the file, what the
 * output judge read from it, and its checksums from the conversion's {@code limelight.converted}
 * event -- never recomputed by the interface.
 *
 * @param file the Limelight XML, read-only at its final path
 * @param psms how many {@code psm} elements it holds
 * @param reportedPeptides how many {@code reported_peptide} elements it holds
 * @param md5 its MD5, as recorded
 * @param sha256 its SHA-256, as recorded
 * @param qCutoff the PSM q-value filter the file carries, as written in it
 * @param log the converter's log for this conversion, when one was written
 */
public record LimelightResult(
        Path file,
        int psms,
        int reportedPeptides,
        String md5,
        String sha256,
        String qCutoff,
        Optional<Path> log) {

    /**
     * Requires every component.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a count is negative
     */
    public LimelightResult {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(md5, "md5");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(qCutoff, "qCutoff");
        Objects.requireNonNull(log, "log");
        if (psms < 0 || reportedPeptides < 0) {
            throw new IllegalArgumentException("a count of elements is never negative");
        }
    }
}
