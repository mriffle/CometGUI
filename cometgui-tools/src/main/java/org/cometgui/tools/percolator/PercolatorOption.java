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
import org.cometgui.domain.tools.ToolCapability;

/**
 * Every Percolator command-line option this product may pass, spelled exactly as the capability
 * probe passed it, and the one capability whose probe run proved it is accepted.
 *
 * <p><strong>One table, read by the probe and by whatever builds a real command.</strong> Phase
 * 09's design decision P9-7 is that every option the command builder can emit maps to exactly one
 * {@link ToolCapability} that the probe establishes, and {@code R-PERC-06} forbids passing an
 * option the probed build does not advertise. Both halves are only as good as the agreement between
 * the spelling the probe ran and the spelling the builder emits: a probe that proved {@code -m}
 * while the builder passed {@code --results-psms} would have proved nothing about the option
 * actually passed. So the spelling lives here once, and {@link PercolatorCapabilityProbe} builds
 * its argument arrays from these constants.
 *
 * <p>The spellings were run against the real 3.06.5 and 3.07.1 portable binaries and the 3.09
 * {@code .rpm} binary on 2026-10-07 ({@code --no-analytics} on 2026-10-08); every one was accepted
 * by all three, except {@code -X} and {@code -Z}, which 3.09 rejects. That is a record of what was
 * observed, not a rule: what a given build accepts is whatever the probe watched it accept.
 *
 * <p>{@link ToolCapability#DECOY_OUTPUT} is the one capability two options map to: the decoy PSM
 * and decoy peptide tables are one capability in the specification's list, and the probe proves
 * them in one run that must write both.
 */
public enum PercolatorOption {

    /** {@code -X <file>}: write pout XML. */
    XML_OUTPUT("-X", ToolCapability.XML_OUTPUT),

    /** {@code -Z}: include decoys in that XML. */
    XML_DECOY_OUTPUT("-Z", ToolCapability.XML_DECOY_OUTPUT),

    /** {@code --results-psms <file>}: the target PSM table. */
    RESULTS_PSMS("--results-psms", ToolCapability.PSM_TSV_OUTPUT),

    /** {@code --results-peptides <file>}: the target peptide table. */
    RESULTS_PEPTIDES("--results-peptides", ToolCapability.PEPTIDE_TSV_OUTPUT),

    /** {@code --decoy-results-psms <file>}: the decoy PSM table. */
    DECOY_RESULTS_PSMS("--decoy-results-psms", ToolCapability.DECOY_OUTPUT),

    /** {@code --decoy-results-peptides <file>}: the decoy peptide table. */
    DECOY_RESULTS_PEPTIDES("--decoy-results-peptides", ToolCapability.DECOY_OUTPUT),

    /** {@code --weights <file>}: the learned feature weights. */
    WEIGHTS("--weights", ToolCapability.WEIGHTS_OUTPUT),

    /** {@code --seed <value>}: the random seed. */
    SEED("--seed", ToolCapability.SEED_OPTION),

    /** {@code --num-threads <value>}: the thread count. */
    NUM_THREADS("--num-threads", ToolCapability.THREAD_OPTION),

    /** {@code --testFDR <value>}: the FDR for selecting and reporting results. */
    TEST_FDR("--testFDR", ToolCapability.TEST_FDR_OPTION),

    /** {@code --trainFDR <value>}: the FDR for defining positive training examples. */
    TRAIN_FDR("--trainFDR", ToolCapability.TRAIN_FDR_OPTION),

    /** {@code --maxiter <value>}: the maximum number of training iterations. */
    MAX_ITERATIONS("--maxiter", ToolCapability.MAX_ITERATIONS_OPTION),

    /**
     * {@code --no-analytics}: post no usage analytics. Takes no value, is not a setting, and is
     * passed on every run of a build that accepts it ({@code D-013}).
     */
    NO_ANALYTICS("--no-analytics", ToolCapability.NO_ANALYTICS_OPTION);

    private final String spelling;
    private final ToolCapability capability;

    PercolatorOption(String spelling, ToolCapability capability) {
        this.spelling = spelling;
        this.capability = Objects.requireNonNull(capability, "capability");
    }

    /**
     * The option exactly as it goes into an argument array.
     *
     * @return for example {@code --results-psms}
     */
    public String spelling() {
        return spelling;
    }

    /**
     * The capability whose probe run proved this option is accepted, and without which it must not
     * be passed ({@code R-PERC-06}).
     *
     * @return the capability, always a Percolator one
     */
    public ToolCapability capability() {
        return capability;
    }
}
