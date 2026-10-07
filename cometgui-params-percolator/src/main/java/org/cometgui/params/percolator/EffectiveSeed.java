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

package org.cometgui.params.percolator;

/**
 * The random seed a Percolator run actually used, as far as CometGUI can know it ({@code
 * R-PERC-05}, gate item 7).
 *
 * <p>When the build was observed to accept a seed, the configured seed is passed and is the
 * effective one. When it was not, nothing is passed and the build runs with whatever seed it uses
 * by itself -- which CometGUI did not choose and does not claim to know. Either way the record says
 * which, so that provenance never shows a seed that was not used.
 *
 * @param configuredSeed the seed in the run's settings
 * @param passed whether it reaches Percolator
 */
public record EffectiveSeed(int configuredSeed, boolean passed) {

    /**
     * Validates the seed.
     *
     * @throws IllegalArgumentException if the seed is outside {@link
     *     PercolatorSettings#MINIMUM_RANDOM_SEED} to {@link PercolatorSettings#MAXIMUM_RANDOM_SEED}
     */
    public EffectiveSeed {
        PercolatorSettings.checkRandomSeed(configuredSeed);
    }

    /**
     * The value recorded in provenance under the seed key.
     *
     * @return the seed in decimal when it was passed, otherwise {@code not-passed}
     */
    public String recordedValue() {
        return passed ? Integer.toString(configuredSeed) : "not-passed";
    }

    /**
     * The sentence recorded beside {@link #recordedValue()} and shown in the interface.
     *
     * @return why this is the effective seed
     */
    public String explanation() {
        if (passed) {
            return "The random seed "
                    + configuredSeed
                    + " was passed to Percolator, so a rerun with the same seed reproduces this"
                    + " run's cross-validation splits.";
        }
        return "No random seed was passed: this Percolator build has no observed SEED_OPTION, so"
                + " it ran with its own default seed, and the configured seed "
                + configuredSeed
                + " was not used.";
    }
}
