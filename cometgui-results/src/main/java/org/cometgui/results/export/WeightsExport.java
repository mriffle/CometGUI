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

package org.cometgui.results.export;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * A learned-feature-weights export that was written, its sidecar written and its provenance event
 * recorded -- {@link ResultExporter#exportWeights} returns one only when all three happened.
 *
 * @param file the new file under the run's {@code exports/}
 * @param sidecar its metadata, {@code <file>.json} beside it
 * @param source the weights artefact the summary was computed from, only ever read
 * @param splitCount how many cross-validation splits the artefact holds, read from it
 * @param featureCount how many features it names, the bias term included
 * @param sourceHashes the artefact's checksums
 * @param exportHashes the export file's checksums
 * @param created when the export was made
 * @param eventSequence the sequence number of its event in the run's provenance event log
 */
public record WeightsExport(
        Path file,
        Path sidecar,
        Path source,
        int splitCount,
        int featureCount,
        FileHashes sourceHashes,
        FileHashes exportHashes,
        Instant created,
        long eventSequence) {

    /**
     * Every component present.
     *
     * @throws NullPointerException if a reference is {@code null}
     */
    public WeightsExport {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(sidecar, "sidecar");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceHashes, "sourceHashes");
        Objects.requireNonNull(exportHashes, "exportHashes");
        Objects.requireNonNull(created, "created");
    }
}
