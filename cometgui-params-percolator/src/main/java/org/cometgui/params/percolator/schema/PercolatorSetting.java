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

package org.cometgui.params.percolator.schema;

import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;

/**
 * The Percolator settings CometGUI exposes under Advanced, each with the one {@link ToolCapability}
 * a build must have been <em>observed</em> to have before the setting may reach it.
 *
 * <p>{@code R-PERC-06}: no option is passed to a build that does not have it. The mapping lives
 * here, beside the settings, so that the interface (which shows only supported settings), the
 * command builder (which emits only supported options) and provenance (which records what was not
 * passed and why) read one table. The option <em>spellings</em> are the command builder's, not this
 * module's.
 *
 * <p>The descriptions are the words the interface shows. The two FDR descriptions say outright that
 * they are not the q-value result filters ({@code R-PERC-04}).
 */
public enum PercolatorSetting {

    /** {@code testFDR}: needs {@link ToolCapability#TEST_FDR_OPTION}. */
    TEST_FDR(
            "test-fdr",
            "testFDR",
            ToolCapability.TEST_FDR_OPTION,
            "A learning threshold inside Percolator: the false discovery rate at which Percolator"
                    + " selects the best cross-validation result and reports its final results."
                    + " Changing it changes what Percolator computes, so it takes effect only"
                    + " when Percolator runs. It is not the PSM q-value result filter, which only"
                    + " changes which results are displayed and exported and never reruns"
                    + " Percolator."),

    /** {@code trainFDR}: needs {@link ToolCapability#TRAIN_FDR_OPTION}. */
    TRAIN_FDR(
            "train-fdr",
            "trainFDR",
            ToolCapability.TRAIN_FDR_OPTION,
            "A learning threshold inside Percolator: the false discovery rate that defines the"
                    + " positive examples Percolator trains on. Changing it changes what"
                    + " Percolator computes, so it takes effect only when Percolator runs. It is"
                    + " not the PSM or peptide q-value result filter, which only change which"
                    + " results are displayed and exported and never rerun Percolator."),

    /** The random seed: needs {@link ToolCapability#SEED_OPTION}. */
    RANDOM_SEED(
            "random-seed",
            "Random seed",
            ToolCapability.SEED_OPTION,
            "The seed of Percolator's random number generator, which decides how results are"
                    + " split for cross-validation. It is always recorded, so a rerun of this run"
                    + " can reproduce it."),

    /** Maximum training iterations: needs {@link ToolCapability#MAX_ITERATIONS_OPTION}. */
    MAXIMUM_ITERATIONS(
            "maximum-iterations",
            "Maximum iterations",
            ToolCapability.MAX_ITERATIONS_OPTION,
            "The most training iterations Percolator runs before it stops."),

    /** Thread count: needs {@link ToolCapability#THREAD_OPTION}. */
    THREAD_COUNT(
            "thread-count",
            "Thread count",
            ToolCapability.THREAD_OPTION,
            "How many threads Percolator uses to train during cross-validation. It changes how"
                    + " long the run takes, not its results.");

    private final String id;
    private final String label;
    private final ToolCapability requiredCapability;
    private final String description;

    PercolatorSetting(
            String id, String label, ToolCapability requiredCapability, String description) {
        this.id = id;
        this.label = label;
        this.requiredCapability = requiredCapability;
        this.description = description;
    }

    /**
     * The stable identifier, lower case and hyphenated, for settings files and provenance keys.
     *
     * @return for example {@code test-fdr}
     */
    public String id() {
        return id;
    }

    /**
     * The name the interface shows.
     *
     * @return for example {@code testFDR}
     */
    public String label() {
        return label;
    }

    /**
     * The capability a build must have been observed to have before this setting may reach it.
     *
     * @return the capability, never {@code null}
     */
    public ToolCapability requiredCapability() {
        return requiredCapability;
    }

    /**
     * The sentence the interface shows under the setting.
     *
     * @return the description, never blank
     */
    public String description() {
        return description;
    }

    /**
     * Whether a build with these observed capabilities accepts this setting.
     *
     * @param observed the build's observed capabilities -- never its unobserved claims
     * @return {@code true} if {@link #requiredCapability()} is among them
     * @throws NullPointerException if {@code observed} is {@code null}
     */
    public boolean isSupportedBy(Set<ToolCapability> observed) {
        return Objects.requireNonNull(observed, "observed").contains(requiredCapability);
    }
}
