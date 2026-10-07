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

import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.params.percolator.schema.SettingsApplicability;
import org.cometgui.params.percolator.validation.TestFdr;
import org.cometgui.params.percolator.validation.TrainFdr;

/**
 * The Percolator settings of one run: what the Advanced settings show and what the command builder
 * passes, for the options a build was observed to accept.
 *
 * <p>Immutable; a change is a new value. Every value is validated here, so an instance that exists
 * is one Percolator will accept.
 *
 * <p><strong>Where the ranges come from.</strong> Percolator 3.06.5, 3.07.1 and 3.09 were each run
 * with values on both sides of every bound on 2026-10-07 and refused the same ones with the same
 * messages: {@code --seed} "requires an integer between 1 and 20000"; {@code --maxiter} "between 0
 * and 1000"; {@code --num-threads} "between 1 and 128"; the two FDRs "a float between 0 and 1".
 * These ranges match, with two deliberate narrowings: a maximum of 0 iterations, which trains
 * nothing, is refused, and so is an FDR of 0 (see {@link TrainFdr}).
 *
 * <p><strong>The defaults are Percolator's own</strong> -- seed 1, 10 iterations, 3 threads (one
 * per cross-validation fold), both FDRs 0.01 -- so a run that passes them and a run on a build that
 * cannot accept them compute the same thing. The seed is nevertheless always passed when the build
 * accepts it and always recorded ({@code R-PERC-05}): an implicit seed is one a later Percolator
 * release could change.
 *
 * <p><strong>Not the display filters.</strong> {@link TestFdr} and {@link TrainFdr} are learning
 * thresholds of their own types; the q-value result filters live with the results and never reach
 * Percolator ({@code AC-RES-05}).
 *
 * @param testFdr the {@code testFDR} learning threshold
 * @param trainFdr the {@code trainFDR} learning threshold
 * @param randomSeed the random seed, {@value #MINIMUM_RANDOM_SEED} to {@value #MAXIMUM_RANDOM_SEED}
 * @param maximumIterations the most training iterations, {@value #MINIMUM_MAXIMUM_ITERATIONS} to
 *     {@value #MAXIMUM_MAXIMUM_ITERATIONS}
 * @param threadCount the training thread count, {@value #MINIMUM_THREAD_COUNT} to {@value
 *     #MAXIMUM_THREAD_COUNT}
 */
public record PercolatorSettings(
        TestFdr testFdr,
        TrainFdr trainFdr,
        int randomSeed,
        int maximumIterations,
        int threadCount) {

    /** The default random seed: Percolator's own, made explicit. */
    public static final int DEFAULT_RANDOM_SEED = 1;

    /** The smallest seed Percolator accepts. */
    public static final int MINIMUM_RANDOM_SEED = 1;

    /** The largest seed Percolator accepts. */
    public static final int MAXIMUM_RANDOM_SEED = 20000;

    /** The default maximum number of training iterations: Percolator's own. */
    public static final int DEFAULT_MAXIMUM_ITERATIONS = 10;

    /** The fewest iterations allowed: at least one, so that Percolator trains at all. */
    public static final int MINIMUM_MAXIMUM_ITERATIONS = 1;

    /** The most iterations Percolator accepts. */
    public static final int MAXIMUM_MAXIMUM_ITERATIONS = 1000;

    /** The default thread count: Percolator's own, one thread per cross-validation fold. */
    public static final int DEFAULT_THREAD_COUNT = 3;

    /** The fewest threads Percolator accepts. */
    public static final int MINIMUM_THREAD_COUNT = 1;

    /** The most threads Percolator accepts. */
    public static final int MAXIMUM_THREAD_COUNT = 128;

    /**
     * Validates every setting.
     *
     * @throws NullPointerException if either FDR is {@code null}
     * @throws IllegalArgumentException if a number is out of range, naming the setting, the value
     *     and the range
     */
    public PercolatorSettings {
        Objects.requireNonNull(testFdr, "testFdr");
        Objects.requireNonNull(trainFdr, "trainFdr");
        checkRandomSeed(randomSeed);
        checkRange(
                "maximum iterations",
                maximumIterations,
                MINIMUM_MAXIMUM_ITERATIONS,
                MAXIMUM_MAXIMUM_ITERATIONS);
        checkRange("thread count", threadCount, MINIMUM_THREAD_COUNT, MAXIMUM_THREAD_COUNT);
    }

    /**
     * The settings a new run starts from.
     *
     * @return both FDRs 0.01, seed 1, 10 iterations, 3 threads
     */
    public static PercolatorSettings defaults() {
        return new PercolatorSettings(
                TestFdr.DEFAULT,
                TrainFdr.DEFAULT,
                DEFAULT_RANDOM_SEED,
                DEFAULT_MAXIMUM_ITERATIONS,
                DEFAULT_THREAD_COUNT);
    }

    static void checkRandomSeed(int seed) {
        checkRange("random seed", seed, MINIMUM_RANDOM_SEED, MAXIMUM_RANDOM_SEED);
    }

    private static void checkRange(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name
                            + " must be a whole number from "
                            + minimum
                            + " to "
                            + maximum
                            + ", but was "
                            + value);
        }
    }

    /**
     * These settings with another {@code testFDR}.
     *
     * @param value the new threshold
     * @return a new value
     */
    public PercolatorSettings withTestFdr(TestFdr value) {
        return new PercolatorSettings(value, trainFdr, randomSeed, maximumIterations, threadCount);
    }

    /**
     * These settings with another {@code trainFDR}.
     *
     * @param value the new threshold
     * @return a new value
     */
    public PercolatorSettings withTrainFdr(TrainFdr value) {
        return new PercolatorSettings(testFdr, value, randomSeed, maximumIterations, threadCount);
    }

    /**
     * These settings with another random seed.
     *
     * @param value the new seed
     * @return a new value
     * @throws IllegalArgumentException if it is out of range
     */
    public PercolatorSettings withRandomSeed(int value) {
        return new PercolatorSettings(testFdr, trainFdr, value, maximumIterations, threadCount);
    }

    /**
     * These settings with another maximum number of iterations.
     *
     * @param value the new maximum
     * @return a new value
     * @throws IllegalArgumentException if it is out of range
     */
    public PercolatorSettings withMaximumIterations(int value) {
        return new PercolatorSettings(testFdr, trainFdr, randomSeed, value, threadCount);
    }

    /**
     * These settings with another thread count.
     *
     * @param value the new count
     * @return a new value
     * @throws IllegalArgumentException if it is out of range
     */
    public PercolatorSettings withThreadCount(int value) {
        return new PercolatorSettings(testFdr, trainFdr, randomSeed, maximumIterations, value);
    }

    /**
     * One setting's value as it is written on a command line and in provenance: plain decimal, the
     * same in every locale.
     *
     * @param setting the setting
     * @return for example {@code 0.01} or {@code 10}
     * @throws NullPointerException if {@code setting} is {@code null}
     */
    public String valueText(PercolatorSetting setting) {
        return switch (Objects.requireNonNull(setting, "setting")) {
            case TEST_FDR -> testFdr.text();
            case TRAIN_FDR -> trainFdr.text();
            case RANDOM_SEED -> Integer.toString(randomSeed);
            case MAXIMUM_ITERATIONS -> Integer.toString(maximumIterations);
            case THREAD_COUNT -> Integer.toString(threadCount);
        };
    }

    /**
     * Which of these settings a build with these observed capabilities accepts.
     *
     * @param observed the build's observed capabilities
     * @return the split
     * @throws NullPointerException if {@code observed} is {@code null}
     */
    public SettingsApplicability applicability(Set<ToolCapability> observed) {
        return SettingsApplicability.forCapabilities(observed);
    }

    /**
     * The seed a build with these observed capabilities will actually use.
     *
     * @param observed the build's observed capabilities
     * @return the configured seed, passed or not
     * @throws NullPointerException if {@code observed} is {@code null}
     */
    public EffectiveSeed effectiveSeed(Set<ToolCapability> observed) {
        return new EffectiveSeed(randomSeed, PercolatorSetting.RANDOM_SEED.isSupportedBy(observed));
    }
}
