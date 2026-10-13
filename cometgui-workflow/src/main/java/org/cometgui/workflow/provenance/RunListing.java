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

package org.cometgui.workflow.provenance;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One directory under a project's {@code runs/}, as the Provenance section's run list shows it.
 *
 * <p>Every run directory is listed, whatever became of its run: completed, failed, cancelled,
 * running, interrupted, never started, and one whose {@code run.json} cannot be read at all -- the
 * last is listed by its directory name, so a damaged run is visible rather than silently absent.
 *
 * @param directoryName the directory's name, {@code <created>-<run id>} for a run CometGUI made
 * @param directory the directory, to load the run from
 * @param runId the run's identifier, when {@code run.json} could be read
 * @param created when the run was created, canonical UTC, when {@code run.json} could be read
 * @param condition what {@code run.json} says about the latest attempt
 * @param sealedRecord whether {@code provenance/provenance.json} exists (it is not parsed here;
 *     {@link ProvenanceReader#load} says whether it can be read)
 * @param problem why {@code run.json} could not be read, when it could not, redacted
 */
public record RunListing(
        String directoryName,
        Path directory,
        Optional<String> runId,
        Optional<String> created,
        RunCondition condition,
        boolean sealedRecord,
        Optional<String> problem) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public RunListing {
        Objects.requireNonNull(directoryName, "directoryName");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(problem, "problem");
    }
}
