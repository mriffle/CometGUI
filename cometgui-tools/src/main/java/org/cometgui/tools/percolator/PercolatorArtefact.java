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

package org.cometgui.tools.percolator;

import java.util.Objects;

/**
 * Every file a Percolator run of this product can be asked to write, with its one fixed file name
 * in the run's Percolator output directory and the option that asks for it.
 *
 * <p><strong>The file names live here and nowhere else.</strong> {@link PercolatorCommands} names
 * each requested file by {@link #fileName()}, and {@link PercolatorCommand#artefacts()} lists the
 * ones a command will produce, so that the step that runs it can verify and record exactly those
 * and expect no other -- a run without {@link #POUT_XML} among its artefacts expects no XML.
 *
 * <p>The standard output and standard error of the run are not here: they are the process service's
 * log, not a file Percolator is asked to write.
 */
public enum PercolatorArtefact {

    /** The target PSM table, {@code psms.tsv}. */
    TARGET_PSMS("psms.tsv", PercolatorOption.RESULTS_PSMS),

    /** The target peptide table, {@code peptides.tsv}. */
    TARGET_PEPTIDES("peptides.tsv", PercolatorOption.RESULTS_PEPTIDES),

    /** The decoy PSM table, {@code decoy-psms.tsv}. */
    DECOY_PSMS("decoy-psms.tsv", PercolatorOption.DECOY_RESULTS_PSMS),

    /** The decoy peptide table, {@code decoy-peptides.tsv}. */
    DECOY_PEPTIDES("decoy-peptides.tsv", PercolatorOption.DECOY_RESULTS_PEPTIDES),

    /**
     * The learned feature weights, {@code weights.txt}: comment lines, then per cross-validation
     * split a header row and two weight rows -- not one table, hence not {@code .tsv}.
     */
    WEIGHTS("weights.txt", PercolatorOption.WEIGHTS),

    /** The pout XML document, {@code pout.xml}, targets only. */
    POUT_XML("pout.xml", PercolatorOption.XML_OUTPUT);

    private final String fileName;
    private final PercolatorOption option;

    PercolatorArtefact(String fileName, PercolatorOption option) {
        this.fileName = fileName;
        this.option = Objects.requireNonNull(option, "option");
    }

    /**
     * The file's name inside the run's Percolator output directory.
     *
     * @return for example {@code psms.tsv}
     */
    public String fileName() {
        return fileName;
    }

    /**
     * The option whose value names this file.
     *
     * @return the option
     */
    public PercolatorOption option() {
        return option;
    }
}
