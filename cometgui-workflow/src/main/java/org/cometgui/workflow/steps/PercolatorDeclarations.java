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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.percolator.PercolatorArtefact;
import org.cometgui.workflow.engine.DeclaredFile;
import org.cometgui.workflow.engine.StepDeclaration;

/**
 * Where a run's Percolator files are, and every file each Percolator step reads and writes (design
 * decisions P9-9 and P9-10): the declarations the engine hashes into provenance and re-hashes
 * before it reuses a step.
 *
 * <p>Pure: nothing here touches the disk. The artefacts a run declares are exactly the ones its
 * built command asks Percolator to write ({@code PercolatorCommand.artefacts()}), so a run whose
 * command requests no pout XML declares none and expects none.
 *
 * <h2>Roles</h2>
 *
 * <p>The {@code role} of each provenance file record: {@value #SETTINGS} (the archived settings),
 * {@code merged-pin} (Percolator's input, as {@code merge-pin} wrote it), and one role per artefact
 * -- {@value #PSMS}, {@value #PEPTIDES}, {@value #DECOY_PSMS}, {@value #DECOY_PEPTIDES}, {@value
 * #WEIGHTS} and {@value #POUT_XML}.
 */
final class PercolatorDeclarations {

    /** The archived Percolator settings. */
    static final String SETTINGS = "percolator-settings";

    /** The target PSM table. */
    static final String PSMS = "percolator-psms";

    /** The target peptide table. */
    static final String PEPTIDES = "percolator-peptides";

    /** The decoy PSM table. */
    static final String DECOY_PSMS = "percolator-decoy-psms";

    /** The decoy peptide table. */
    static final String DECOY_PEPTIDES = "percolator-decoy-peptides";

    /** The learned feature weights. */
    static final String WEIGHTS = "percolator-weights";

    /** The pout XML document. */
    static final String POUT_XML = "percolator-pout-xml";

    /**
     * The stage identifier of the one Percolator invocation; its log is {@code
     * logs/percolator.log}.
     */
    static final String INVOCATION = "percolator";

    /** The archived settings' file name in the run's {@code parameters/} directory. */
    static final String SETTINGS_FILE_NAME = "percolator-settings.json";

    /** The raw outputs' directory name under the run's {@code outputs/}. */
    static final String OUTPUT_DIRECTORY_NAME = "percolator";

    private PercolatorDeclarations() {}

    /**
     * The archived settings file.
     *
     * @param layout the run
     * @return {@code parameters/percolator-settings.json}
     */
    static Path settingsFile(RunLayout layout) {
        return layout.parametersDirectory().resolve(SETTINGS_FILE_NAME);
    }

    /**
     * The settings file's path relative to the run directory, as documentation names it.
     *
     * @return {@code parameters/percolator-settings.json}
     */
    static String settingsRelativePath() {
        return "parameters/" + SETTINGS_FILE_NAME;
    }

    /**
     * The directory of the raw Percolator outputs.
     *
     * @param layout the run
     * @return {@code outputs/percolator}
     */
    static Path outputDirectory(RunLayout layout) {
        return layout.outputsDirectory().resolve(OUTPUT_DIRECTORY_NAME);
    }

    /**
     * The provenance role of one artefact.
     *
     * @param artefact the artefact
     * @return its role
     */
    static String roleOf(PercolatorArtefact artefact) {
        return switch (artefact) {
            case TARGET_PSMS -> PSMS;
            case TARGET_PEPTIDES -> PEPTIDES;
            case DECOY_PSMS -> DECOY_PSMS;
            case DECOY_PEPTIDES -> DECOY_PEPTIDES;
            case WEIGHTS -> WEIGHTS;
            case POUT_XML -> POUT_XML;
        };
    }

    /**
     * {@code run-percolator}: reads the merged PIN and the archived settings, writes every artefact
     * its command lists, one invocation.
     */
    static StepDeclaration runPercolator(PercolatorRun run) {
        List<DeclaredFile> files = new ArrayList<>();
        files.add(DeclaredFile.input(RunDeclarations.MERGED_PIN, run.layout().mergedPinFile()));
        files.add(DeclaredFile.input(SETTINGS, settingsFile(run.layout())));
        for (Map.Entry<PercolatorArtefact, Path> artefact : run.command().artefacts().entrySet()) {
            files.add(DeclaredFile.output(roleOf(artefact.getKey()), artefact.getValue()));
        }
        return new StepDeclaration(files, List.of(INVOCATION));
    }

    /** {@code parse-percolator}: reads every artefact {@code run-percolator} wrote. */
    static StepDeclaration parsePercolator(PercolatorRun run) {
        List<DeclaredFile> files = new ArrayList<>();
        for (Map.Entry<PercolatorArtefact, Path> artefact : run.command().artefacts().entrySet()) {
            files.add(DeclaredFile.input(roleOf(artefact.getKey()), artefact.getValue()));
        }
        return new StepDeclaration(files, List.of());
    }
}
