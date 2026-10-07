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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.params.percolator.schema.SettingsApplicability;
import org.cometgui.params.percolator.testing.Nulls;
import org.cometgui.params.percolator.validation.TestFdr;
import org.cometgui.params.percolator.validation.TrainFdr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The Percolator settings: defaults, the ranges measured on 3.06.5, 3.07.1 and 3.09, the two FDRs
 * kept apart from the display filters, and what reaches a build with a given capability set.
 */
class PercolatorSettingsTest {

    private static final Set<ToolCapability> ALL_OPTIONS =
            EnumSet.of(
                    ToolCapability.SEED_OPTION,
                    ToolCapability.THREAD_OPTION,
                    ToolCapability.TEST_FDR_OPTION,
                    ToolCapability.TRAIN_FDR_OPTION,
                    ToolCapability.MAX_ITERATIONS_OPTION);

    /*
     * The settings are immutable, so a wither whose result is dropped is reported by SpotBugs as an
     * ignored return value. Routing the attempt through a Supplier keeps the result used, and the
     * assertion -- that the attempt is refused, with its message -- is unchanged.
     */
    private static Executable built(Supplier<?> attempt) {
        return attempt::get;
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("0.01, 0.01, seed 1, 10 iterations, 3 threads")
        void defaults() {
            PercolatorSettings settings = PercolatorSettings.defaults();
            assertEquals(new TestFdr(new BigDecimal("0.01")), settings.testFdr());
            assertEquals(new TrainFdr(new BigDecimal("0.01")), settings.trainFdr());
            assertEquals(1, settings.randomSeed());
            assertEquals(10, settings.maximumIterations());
            assertEquals(3, settings.threadCount());
            assertEquals("0.01", settings.valueText(PercolatorSetting.TEST_FDR));
            assertEquals("0.01", settings.valueText(PercolatorSetting.TRAIN_FDR));
            assertEquals("1", settings.valueText(PercolatorSetting.RANDOM_SEED));
            assertEquals("10", settings.valueText(PercolatorSetting.MAXIMUM_ITERATIONS));
            assertEquals("3", settings.valueText(PercolatorSetting.THREAD_COUNT));
            assertThrows(
                    NullPointerException.class,
                    () -> settings.valueText(Nulls.of(PercolatorSetting.class)));
        }

        @Test
        @DisplayName("the published bounds, as measured")
        void bounds() {
            assertEquals(1, PercolatorSettings.DEFAULT_RANDOM_SEED);
            assertEquals(1, PercolatorSettings.MINIMUM_RANDOM_SEED);
            assertEquals(20000, PercolatorSettings.MAXIMUM_RANDOM_SEED);
            assertEquals(10, PercolatorSettings.DEFAULT_MAXIMUM_ITERATIONS);
            assertEquals(1, PercolatorSettings.MINIMUM_MAXIMUM_ITERATIONS);
            assertEquals(1000, PercolatorSettings.MAXIMUM_MAXIMUM_ITERATIONS);
            assertEquals(3, PercolatorSettings.DEFAULT_THREAD_COUNT);
            assertEquals(1, PercolatorSettings.MINIMUM_THREAD_COUNT);
            assertEquals(128, PercolatorSettings.MAXIMUM_THREAD_COUNT);
        }
    }

    @Nested
    @DisplayName("ranges")
    class Ranges {

        private final PercolatorSettings base = PercolatorSettings.defaults();

        @Test
        @DisplayName("seed: 1 and 20000 accepted, 0 and 20001 refused")
        void seed() {
            assertEquals(1, base.withRandomSeed(1).randomSeed());
            assertEquals(20000, base.withRandomSeed(20000).randomSeed());
            assertEquals(
                    "random seed must be a whole number from 1 to 20000, but was 0",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    built(() -> base.withRandomSeed(0)))
                            .getMessage());
            assertEquals(
                    "random seed must be a whole number from 1 to 20000, but was 20001",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    built(() -> base.withRandomSeed(20001)))
                            .getMessage());
        }

        @Test
        @DisplayName("iterations: 1 and 1000 accepted, 0 and 1001 refused")
        void iterations() {
            assertEquals(1, base.withMaximumIterations(1).maximumIterations());
            assertEquals(1000, base.withMaximumIterations(1000).maximumIterations());
            assertEquals(
                    "maximum iterations must be a whole number from 1 to 1000, but was 0",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    built(() -> base.withMaximumIterations(0)))
                            .getMessage());
            assertEquals(
                    "maximum iterations must be a whole number from 1 to 1000, but was 1001",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    built(() -> base.withMaximumIterations(1001)))
                            .getMessage());
        }

        @Test
        @DisplayName("threads: 1 and 128 accepted, 0 and 129 refused")
        void threads() {
            assertEquals(1, base.withThreadCount(1).threadCount());
            assertEquals(128, base.withThreadCount(128).threadCount());
            assertEquals(
                    "thread count must be a whole number from 1 to 128, but was 0",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    built(() -> base.withThreadCount(0)))
                            .getMessage());
            assertEquals(
                    "thread count must be a whole number from 1 to 128, but was 129",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    built(() -> base.withThreadCount(129)))
                            .getMessage());
        }

        @Test
        @DisplayName("each wither changes its own setting and nothing else")
        void withers() {
            TestFdr test = TestFdr.parse("0.05");
            TrainFdr train = TrainFdr.parse("0.02");
            PercolatorSettings changed =
                    base.withTestFdr(test)
                            .withTrainFdr(train)
                            .withRandomSeed(42)
                            .withMaximumIterations(25)
                            .withThreadCount(8);
            assertEquals(new PercolatorSettings(test, train, 42, 25, 8), changed);
            assertEquals("0.05", changed.valueText(PercolatorSetting.TEST_FDR));
            assertEquals("0.02", changed.valueText(PercolatorSetting.TRAIN_FDR));
            assertEquals("42", changed.valueText(PercolatorSetting.RANDOM_SEED));
            assertEquals("25", changed.valueText(PercolatorSetting.MAXIMUM_ITERATIONS));
            assertEquals("8", changed.valueText(PercolatorSetting.THREAD_COUNT));
            assertEquals(base, PercolatorSettings.defaults());
            assertThrows(
                    NullPointerException.class,
                    built(() -> base.withTestFdr(Nulls.of(TestFdr.class))));
            assertThrows(
                    NullPointerException.class,
                    built(() -> base.withTrainFdr(Nulls.of(TrainFdr.class))));
        }

        @Test
        @DisplayName("values render the same under a comma-decimal locale")
        void localeIndependent() {
            Locale saved = Locale.getDefault();
            try {
                Locale.setDefault(Locale.GERMANY);
                PercolatorSettings settings =
                        base.withTestFdr(TestFdr.parse("0.005")).withRandomSeed(12345);
                assertEquals("0.005", settings.valueText(PercolatorSetting.TEST_FDR));
                assertEquals("12345", settings.valueText(PercolatorSetting.RANDOM_SEED));
            } finally {
                Locale.setDefault(saved);
            }
        }
    }

    @Nested
    @DisplayName("the FDR thresholds")
    class Fdr {

        @Test
        @DisplayName("1 is accepted; 0, below 0 and above 1 are refused, naming the option")
        void range() {
            assertEquals("1", new TestFdr(BigDecimal.ONE).text());
            assertEquals("1", TrainFdr.parse("1.000").text());
            assertEquals(
                    "testFDR must be greater than 0 and at most 1, but was 0",
                    assertThrows(IllegalArgumentException.class, () -> TestFdr.parse("0"))
                            .getMessage());
            assertEquals(
                    "trainFDR must be greater than 0 and at most 1, but was 0.000",
                    assertThrows(IllegalArgumentException.class, () -> TrainFdr.parse("0.000"))
                            .getMessage());
            assertEquals(
                    "testFDR must be greater than 0 and at most 1, but was -0.01",
                    assertThrows(IllegalArgumentException.class, () -> TestFdr.parse("-0.01"))
                            .getMessage());
            assertEquals(
                    "trainFDR must be greater than 0 and at most 1, but was 1.0001",
                    assertThrows(IllegalArgumentException.class, () -> TrainFdr.parse("1.0001"))
                            .getMessage());
            assertEquals("0.0000001", TestFdr.parse("0.0000001").text());
        }

        @Test
        @DisplayName("parsed as typed: whitespace ignored, exponent accepted, comma refused")
        void parsing() {
            assertEquals("0.05", TestFdr.parse(" 0.05 ").text());
            assertEquals("0.01", TrainFdr.parse("1e-2").text());
            assertEquals("0.01", TestFdr.parse("0.010").text());
            assertEquals(TestFdr.parse("0.010"), TestFdr.DEFAULT);
            assertEquals(
                    "testFDR must be a number greater than 0 and at most 1, written with a full"
                            + " stop as the decimal separator (for example 0.01), but was \"0,01\"",
                    assertThrows(IllegalArgumentException.class, () -> TestFdr.parse("0,01"))
                            .getMessage());
            assertEquals(
                    "trainFDR must be a number greater than 0 and at most 1, written with a full"
                            + " stop as the decimal separator (for example 0.01), but was \"\"",
                    assertThrows(IllegalArgumentException.class, () -> TrainFdr.parse("  "))
                            .getMessage());
            assertThrows(NullPointerException.class, () -> TestFdr.parse(null));
            assertThrows(NullPointerException.class, () -> new TrainFdr(null));
        }

        @Test
        @DisplayName("AC-RES-05: testFDR and trainFDR are their own types and fields")
        void separateTypes() {
            RecordComponent[] components = PercolatorSettings.class.getRecordComponents();
            assertEquals("testFdr", components[0].getName());
            assertEquals(TestFdr.class, components[0].getType());
            assertEquals("trainFdr", components[1].getName());
            assertEquals(TrainFdr.class, components[1].getType());
            assertNotEquals(TestFdr.class, TrainFdr.class);
            for (RecordComponent component : components) {
                assertFalse(
                        component.getType().getName().startsWith("org.cometgui.results"),
                        component.getName());
            }
            assertNotEquals((Object) TestFdr.parse("0.01"), (Object) TrainFdr.parse("0.01"));
        }
    }

    @Nested
    @DisplayName("R-PERC-04: the settings and their descriptions")
    class Descriptions {

        @Test
        @DisplayName("each setting's id, label and required capability")
        void table() {
            assertEquals(
                    List.of(
                            "test-fdr testFDR TEST_FDR_OPTION",
                            "train-fdr trainFDR TRAIN_FDR_OPTION",
                            "random-seed Random seed SEED_OPTION",
                            "maximum-iterations Maximum iterations MAX_ITERATIONS_OPTION",
                            "thread-count Thread count THREAD_OPTION"),
                    java.util.Arrays.stream(PercolatorSetting.values())
                            .map(s -> s.id() + " " + s.label() + " " + s.requiredCapability().id())
                            .toList());
        }

        @Test
        @DisplayName("testFDR and trainFDR say they are not the display filters")
        void notTheFilters() {
            assertEquals(
                    "A learning threshold inside Percolator: the false discovery rate at which"
                            + " Percolator selects the best cross-validation result and reports"
                            + " its final results. Changing it changes what Percolator computes,"
                            + " so it takes effect only when Percolator runs. It is not the PSM"
                            + " q-value result filter, which only changes which results are"
                            + " displayed and exported and never reruns Percolator.",
                    PercolatorSetting.TEST_FDR.description());
            assertEquals(
                    "A learning threshold inside Percolator: the false discovery rate that defines"
                            + " the positive examples Percolator trains on. Changing it changes"
                            + " what Percolator computes, so it takes effect only when Percolator"
                            + " runs. It is not the PSM or peptide q-value result filter, which"
                            + " only change which results are displayed and exported and never"
                            + " rerun Percolator.",
                    PercolatorSetting.TRAIN_FDR.description());
            assertEquals(
                    "The seed of Percolator's random number generator, which decides how results"
                            + " are split for cross-validation. It is always recorded, so a rerun"
                            + " of this run can reproduce it.",
                    PercolatorSetting.RANDOM_SEED.description());
            assertEquals(
                    "The most training iterations Percolator runs before it stops.",
                    PercolatorSetting.MAXIMUM_ITERATIONS.description());
            assertEquals(
                    "How many threads Percolator uses to train during cross-validation. It changes"
                            + " how long the run takes, not its results.",
                    PercolatorSetting.THREAD_COUNT.description());
        }
    }

    @Nested
    @DisplayName("what reaches a build")
    class Applicability {

        @Test
        @DisplayName("every option observed: every setting supported")
        void all() {
            SettingsApplicability split = PercolatorSettings.defaults().applicability(ALL_OPTIONS);
            assertEquals(List.of(PercolatorSetting.values()), split.supported());
            assertEquals(List.of(), split.unsupported());
            assertTrue(split.isSupported(PercolatorSetting.RANDOM_SEED));
        }

        @Test
        @DisplayName("seed and threads not observed: exactly those two unsupported")
        void partial() {
            SettingsApplicability split =
                    SettingsApplicability.forCapabilities(
                            EnumSet.of(
                                    ToolCapability.TEST_FDR_OPTION,
                                    ToolCapability.TRAIN_FDR_OPTION,
                                    ToolCapability.MAX_ITERATIONS_OPTION,
                                    ToolCapability.XML_OUTPUT));
            assertEquals(
                    List.of(
                            PercolatorSetting.TEST_FDR,
                            PercolatorSetting.TRAIN_FDR,
                            PercolatorSetting.MAXIMUM_ITERATIONS),
                    split.supported());
            assertEquals(
                    List.of(PercolatorSetting.RANDOM_SEED, PercolatorSetting.THREAD_COUNT),
                    split.unsupported());
            assertFalse(split.isSupported(PercolatorSetting.THREAD_COUNT));
            assertTrue(split.isSupported(PercolatorSetting.TRAIN_FDR));
            assertThrows(
                    NullPointerException.class,
                    () -> split.isSupported(Nulls.of(PercolatorSetting.class)));
        }

        @Test
        @DisplayName("nothing observed: nothing supported")
        void none() {
            SettingsApplicability split = SettingsApplicability.forCapabilities(Set.of());
            assertEquals(List.of(), split.supported());
            assertEquals(List.of(PercolatorSetting.values()), split.unsupported());
            assertThrows(
                    NullPointerException.class, () -> SettingsApplicability.forCapabilities(null));
            assertThrows(
                    NullPointerException.class,
                    () -> PercolatorSetting.TEST_FDR.isSupportedBy(Nulls.of(Set.class)));
        }

        @Test
        @DisplayName("the effective seed: passed with SEED_OPTION, recorded as not passed without")
        void effectiveSeed() {
            PercolatorSettings settings = PercolatorSettings.defaults().withRandomSeed(7);
            EffectiveSeed passed = settings.effectiveSeed(ALL_OPTIONS);
            assertEquals(new EffectiveSeed(7, true), passed);
            assertEquals("7", passed.recordedValue());
            assertEquals(
                    "The random seed 7 was passed to Percolator, so a rerun with the same seed"
                            + " reproduces this run's cross-validation splits.",
                    passed.explanation());

            EffectiveSeed notPassed =
                    settings.effectiveSeed(EnumSet.of(ToolCapability.THREAD_OPTION));
            assertEquals(new EffectiveSeed(7, false), notPassed);
            assertEquals("not-passed", notPassed.recordedValue());
            assertEquals(
                    "No random seed was passed: this Percolator build has no observed"
                            + " SEED_OPTION, so it ran with its own default seed, and the"
                            + " configured seed 7 was not used.",
                    notPassed.explanation());
            assertThrows(
                    NullPointerException.class, () -> settings.effectiveSeed(Nulls.of(Set.class)));
            assertEquals(
                    "random seed must be a whole number from 1 to 20000, but was 0",
                    assertThrows(IllegalArgumentException.class, () -> new EffectiveSeed(0, true))
                            .getMessage());
        }
    }
}
