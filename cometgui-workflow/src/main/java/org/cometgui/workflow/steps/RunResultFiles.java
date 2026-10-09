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
import java.util.Objects;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.tools.percolator.PercolatorArtefact;

/**
 * Where a run's raw Percolator results are, for a reader outside this package: the Results
 * section's port (Phase 10 unit 8), which lists and opens the results of a project's runs.
 *
 * <p>The names are not repeated here. The directory is {@link
 * PercolatorDeclarations#outputDirectory}'s and each file's name is {@link
 * PercolatorArtefact#fileName()}, the one place each is written down, so a reader finds exactly the
 * files {@code run-percolator} asked Percolator to write and {@code finalise-results} indexed.
 * Pure: nothing here touches the disk, and whether a file exists is the caller's question.
 */
public final class RunResultFiles {

    private RunResultFiles() {}

    /**
     * The directory of a run's raw Percolator outputs.
     *
     * @param run the run
     * @return {@code outputs/percolator} under the run's directory
     * @throws NullPointerException if {@code run} is {@code null}
     */
    public static Path percolatorOutputDirectory(RunLayout run) {
        return PercolatorDeclarations.outputDirectory(Objects.requireNonNull(run, "run"));
    }

    /**
     * Where one of a run's four result tables is, whether or not the run has it.
     *
     * @param run the run
     * @param kind the table
     * @return for example {@code outputs/percolator/decoy-psms.tsv} under the run's directory
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Path table(RunLayout run, TableKind kind) {
        Objects.requireNonNull(kind, "kind");
        return percolatorOutputDirectory(run).resolve(ResultSteps.artefactOf(kind).fileName());
    }

    /**
     * Where a run's learned feature weights are, whether or not the run has them.
     *
     * @param run the run
     * @return {@code outputs/percolator/weights.txt} under the run's directory
     * @throws NullPointerException if {@code run} is {@code null}
     */
    public static Path weights(RunLayout run) {
        return percolatorOutputDirectory(run).resolve(PercolatorArtefact.WEIGHTS.fileName());
    }
}
