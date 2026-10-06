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

package org.cometgui.workflow.steps;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.CometParameters;

/**
 * A Comet search as the user configured it, before it is a run: what Run readiness checks and what
 * {@link CometWorkflow#prepare} turns into a recorded run.
 *
 * <p>The protein database is the model's own {@code database_name}: the file Comet reads is the
 * file the parameter file names, so there is one place it is chosen. It is either a FASTA file or,
 * with {@link IndexMode#NONE}, an existing Comet {@code .idx} index.
 *
 * @param model the Comet parameters, with the workflow-enforced outputs applied ({@link
 *     CometParameters#withWorkflowEnforcedOutputs()}); the pre-run check refuses one without them
 * @param spectra the spectrum files, in the order the user gave them; that order fixes each file's
 *     position, its {@code -N} base name and its place in the merged PIN
 * @param comet the selected Comet
 * @param indexMode whether the search builds (or reuses) a Comet index first, and which kind
 */
public record SearchRequest(
        CometParameters model, List<Path> spectra, CometSelection comet, IndexMode indexMode) {

    /**
     * Requires every component and copies the list.
     *
     * @throws NullPointerException naming a component or a spectrum file that is {@code null}
     */
    public SearchRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(spectra, "spectra");
        for (Path spectrum : spectra) {
            Objects.requireNonNull(spectrum, "spectra contains null");
        }
        spectra = List.copyOf(spectra);
        Objects.requireNonNull(comet, "comet");
        Objects.requireNonNull(indexMode, "indexMode");
    }

    @Override
    public List<Path> spectra() {
        return List.copyOf(spectra);
    }
}
