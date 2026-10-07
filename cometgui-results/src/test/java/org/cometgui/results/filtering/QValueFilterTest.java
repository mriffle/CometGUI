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

package org.cometgui.results.filtering;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.results.parser.QValue;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.testing.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The display q-value filters (R-RES-01, R-RES-02, AC-RES-01..03 and AC-RES-05): defaults,
 * inclusive boundary, range, independence, and the unknown-q-value category. Expected values are
 * typed by hand.
 */
class QValueFilterTest {

    private static ResultRow row(String q) {
        return new ResultRow(2, "p", "1", 1.0, QValue.of(q), "0.1", 0.1, "K.A.R", List.of("sp|A"));
    }

    @Nested
    @DisplayName("defaults: 0.01 and 0.01")
    class Defaults {

        @Test
        @DisplayName("the PSM default is 0.01 (AC-RES-01), the peptide default 0.01 (AC-RES-02)")
        void defaults() {
            assertEquals(new BigDecimal("0.01"), PsmQValueFilter.DEFAULT.cutoff());
            assertEquals(new BigDecimal("0.01"), PeptideQValueFilter.DEFAULT.cutoff());
            assertEquals("0.01", PsmQValueFilter.DEFAULT.text());
            assertEquals("0.01", PeptideQValueFilter.DEFAULT.text());
            assertEquals(new BigDecimal("0.01"), QValueFilter.DEFAULT_CUTOFF);
            assertSame(PsmQValueFilter.DEFAULT, DisplayFilters.DEFAULTS.psm());
            assertSame(PeptideQValueFilter.DEFAULT, DisplayFilters.DEFAULTS.peptide());
        }
    }

    @Nested
    @DisplayName("the boundary is inclusive")
    class Boundary {

        @ParameterizedTest
        @CsvSource({
            "0.01, PASSES",
            "0.010, PASSES",
            "1e-2, PASSES",
            "0.0099999, PASSES",
            "0, PASSES",
            "0.0100001, FAILS",
            "0.011, FAILS",
            "1, FAILS"
        })
        @DisplayName("at the 0.01 default: exactly 0.01 is visible, anything above is not")
        void atDefault(String q, Visibility expected) {
            assertEquals(expected, PsmQValueFilter.DEFAULT.classify(QValue.of(q)));
            assertEquals(expected, PeptideQValueFilter.DEFAULT.classify(row(q)));
            assertEquals(expected == Visibility.PASSES, PsmQValueFilter.DEFAULT.passes(row(q)));
        }

        @Test
        @DisplayName("cutoff 0 is accepted: only q = 0 passes")
        void zero() {
            PsmQValueFilter filter = PsmQValueFilter.parse("0");
            assertEquals(Visibility.PASSES, filter.classify(QValue.of("0")));
            assertEquals(Visibility.PASSES, filter.classify(QValue.of("-0")));
            assertEquals(Visibility.FAILS, filter.classify(QValue.of("1e-9")));
            assertEquals(BigDecimal.ZERO, filter.cutoff());
        }

        @Test
        @DisplayName("cutoff 1 is accepted: every known q-value passes")
        void one() {
            PeptideQValueFilter filter = PeptideQValueFilter.parse("1");
            assertEquals(Visibility.PASSES, filter.classify(QValue.of("1")));
            assertEquals(Visibility.PASSES, filter.classify(QValue.of("0.999999")));
            assertEquals(Visibility.UNKNOWN_Q_VALUE, filter.classify(QValue.of("1.5")));
        }

        @Test
        @DisplayName("a value given as a double is compared the same way")
        void fromDouble() {
            PsmQValueFilter filter = PsmQValueFilter.of(0.05);
            assertEquals(new BigDecimal("0.05"), filter.cutoff());
            assertEquals(Visibility.PASSES, filter.classify(QValue.of("0.05")));
            assertEquals(Visibility.FAILS, filter.classify(QValue.of("0.0500001")));
            assertEquals(new BigDecimal("0.0"), PeptideQValueFilter.of(0.0).cutoff());
            assertEquals(new BigDecimal("1.0"), PeptideQValueFilter.of(1.0).cutoff());
        }
    }

    @Nested
    @DisplayName("unknown q-values are their own category (R-RES-02)")
    class Unknown {

        @ParameterizedTest
        @ValueSource(strings = {"", "NaN", "nan", "inf", "Infinity", "0,005", "1.5", "-0.01"})
        @DisplayName("neither passing nor failing, under any cutoff")
        void unknown(String q) {
            for (QValueFilter filter :
                    List.of(
                            PsmQValueFilter.parse("0"),
                            PsmQValueFilter.DEFAULT,
                            PeptideQValueFilter.parse("1"))) {
                assertEquals(Visibility.UNKNOWN_Q_VALUE, filter.classify(QValue.of(q)));
                assertFalse(filter.passes(row(q)));
            }
        }
    }

    @Nested
    @DisplayName("the range is [0, 1]; everything else is refused with a message")
    class Range {

        @ParameterizedTest
        @CsvSource({"-0.0001, -0.0001", "1.0001, 1.0001", "-1, -1", "2, 2", "1E+1, 10"})
        @DisplayName("below 0 or above 1, typed")
        void outOfRange(String text, String shown) {
            IllegalArgumentException psm =
                    assertThrows(IllegalArgumentException.class, () -> PsmQValueFilter.parse(text));
            assertEquals(
                    "The PSM q-value filter must be between 0 and 1 inclusive, but was " + shown,
                    psm.getMessage());
            IllegalArgumentException peptide =
                    assertThrows(
                            IllegalArgumentException.class, () -> PeptideQValueFilter.parse(text));
            assertEquals(
                    "The peptide q-value filter must be between 0 and 1 inclusive, but was "
                            + shown,
                    peptide.getMessage());
        }

        @Test
        @DisplayName("below 0 or above 1, as doubles and as BigDecimals")
        void outOfRangeOtherwise() {
            assertThrows(IllegalArgumentException.class, () -> PsmQValueFilter.of(-0.0001));
            assertThrows(IllegalArgumentException.class, () -> PeptideQValueFilter.of(1.0001));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PsmQValueFilter(new BigDecimal("-1E-10")));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PeptideQValueFilter(new BigDecimal("1.0000000001")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "", "  ", "0,01", "abc", "1/100"})
        @DisplayName("not a number, typed")
        void notANumber(String text) {
            IllegalArgumentException refused =
                    assertThrows(IllegalArgumentException.class, () -> PsmQValueFilter.parse(text));
            assertEquals(
                    "The PSM q-value filter must be a number between 0 and 1 inclusive, written"
                            + " with a '.' decimal point, but was '"
                            + text
                            + "'",
                    refused.getMessage());
            assertThrows(IllegalArgumentException.class, () -> PeptideQValueFilter.parse(text));
        }

        @Test
        @DisplayName("NaN and infinities as doubles")
        void notFinite() {
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class, () -> PsmQValueFilter.of(Double.NaN));
            assertEquals(
                    "The PSM q-value filter must be a number between 0 and 1 inclusive, but was"
                            + " NaN",
                    refused.getMessage());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PeptideQValueFilter.of(Double.POSITIVE_INFINITY));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PsmQValueFilter.of(Double.NEGATIVE_INFINITY));
        }

        @Test
        @DisplayName("null is refused")
        void nulls() {
            assertThrows(NullPointerException.class, () -> new PsmQValueFilter(null));
            assertThrows(NullPointerException.class, () -> PeptideQValueFilter.parse(null));
            assertThrows(
                    NullPointerException.class,
                    () -> new DisplayFilters(null, PeptideQValueFilter.DEFAULT));
            assertThrows(
                    NullPointerException.class,
                    () -> new DisplayFilters(PsmQValueFilter.DEFAULT, null));
        }

        @Test
        @DisplayName("surrounding white space in typed text is ignored")
        void stripped() {
            assertEquals(new BigDecimal("0.05"), PsmQValueFilter.parse(" 0.05 ").cutoff());
        }
    }

    @Nested
    @DisplayName("PSM and peptide filters are independent (AC-RES-03)")
    class Independence {

        @Test
        @DisplayName("changing one leaves the other as it was")
        void independent() {
            DisplayFilters changed = DisplayFilters.DEFAULTS.withPsm(PsmQValueFilter.parse("0.05"));
            assertEquals(new BigDecimal("0.05"), changed.psm().cutoff());
            assertSame(PeptideQValueFilter.DEFAULT, changed.peptide());
            DisplayFilters both = changed.withPeptide(PeptideQValueFilter.parse("0.2"));
            assertEquals(new BigDecimal("0.05"), both.psm().cutoff());
            assertEquals(new BigDecimal("0.2"), both.peptide().cutoff());
            assertEquals(new BigDecimal("0.01"), DisplayFilters.DEFAULTS.psm().cutoff());
            assertEquals(new BigDecimal("0.01"), DisplayFilters.DEFAULTS.peptide().cutoff());
        }

        @Test
        @DisplayName("a PSM filter never equals a peptide filter, even at the same cutoff")
        void distinctTypes() {
            assertNotEquals(PsmQValueFilter.DEFAULT, PeptideQValueFilter.DEFAULT);
            assertNotEquals(
                    PsmQValueFilter.DEFAULT.hashCode(), PeptideQValueFilter.DEFAULT.hashCode());
            assertEquals(PsmQValueFilter.parse("0.010"), PsmQValueFilter.DEFAULT);
            assertEquals(
                    PsmQValueFilter.parse("0.010").hashCode(), PsmQValueFilter.DEFAULT.hashCode());
            assertNotEquals(PsmQValueFilter.parse("0.02"), PsmQValueFilter.DEFAULT);
            assertFalse(PsmQValueFilter.DEFAULT.equals(null));
            assertFalse(PsmQValueFilter.DEFAULT.equals(new BigDecimal("0.01")));
            assertEquals("PsmQValueFilter(q <= 0.01)", PsmQValueFilter.DEFAULT.toString());
            assertEquals(
                    "PeptideQValueFilter(q <= 0.2)", PeptideQValueFilter.parse("0.2").toString());
        }

        @Test
        @DisplayName("the same row, two filters, two verdicts")
        void twoVerdicts() {
            DisplayFilters filters =
                    new DisplayFilters(
                            PsmQValueFilter.parse("0.05"), PeptideQValueFilter.parse("0.001"));
            ResultRow row = row("0.01");
            assertTrue(filters.psm().passes(row));
            assertFalse(filters.peptide().passes(row));
        }
    }

    @Nested
    @DisplayName("not Percolator's testFDR or trainFDR (AC-RES-05)")
    class NotLearningThresholds {

        @Test
        @DisplayName("their own types, holding nothing from the Percolator settings package")
        void ownTypes() {
            for (Class<?> type : List.of(PsmQValueFilter.class, PeptideQValueFilter.class)) {
                assertEquals(QValueFilter.class, type.getSuperclass());
                assertEquals(Object.class, QValueFilter.class.getSuperclass());
                assertEquals(0, type.getInterfaces().length);
                List<Class<?>> mentioned = new ArrayList<>();
                for (Class<?> declaring : List.of(type, QValueFilter.class)) {
                    for (Field field : declaring.getDeclaredFields()) {
                        mentioned.add(field.getType());
                    }
                    for (Method method : declaring.getDeclaredMethods()) {
                        mentioned.add(method.getReturnType());
                        mentioned.addAll(List.of(method.getParameterTypes()));
                    }
                }
                for (Class<?> each : mentioned) {
                    assertFalse(
                            each.getName().startsWith("org.cometgui.params"),
                            type + " mentions " + each);
                }
            }
        }
    }

    @Nested
    @DisplayName("counting")
    class Counting {

        private static final String CONSTRUCTED_SHA256 =
                "ea58b3b63f9031bf53b5675d117b1e7203137a0ebd2399c9def824334267b6c7";

        private List<ResultRow> constructed() throws IOException {
            return ResultTableReader.readAll(
                            Fixtures.verified("constructed/psms-unknown-q.tsv", CONSTRUCTED_SHA256))
                    .rows();
        }

        @ParameterizedTest
        @CsvSource({
            "0, 1, 6",
            "0.001, 3, 4",
            "0.01, 5, 2",
            "0.0100001, 6, 1",
            "0.5, 6, 1",
            "1, 7, 0"
        })
        @DisplayName("the constructed table, hand-counted: 15 rows, 8 unknown at every cutoff")
        void constructedCounts(String cutoff, long passing, long failing) throws IOException {
            FilterCounts expected = new FilterCounts(15, passing, failing, 8);
            assertEquals(expected, PsmQValueFilter.parse(cutoff).count(constructed()));
            assertEquals(expected, PeptideQValueFilter.parse(cutoff).count(constructed()));
        }

        @Test
        @DisplayName("a tally fed row by row agrees, and an empty one counts nothing")
        void tally() throws IOException {
            FilterTally tally = PsmQValueFilter.DEFAULT.tally();
            assertEquals(new FilterCounts(0, 0, 0, 0), tally.counts());
            tally.accept(row("0.01"));
            assertEquals(new FilterCounts(1, 1, 0, 0), tally.counts());
            tally.accept(row("0.02"));
            assertEquals(new FilterCounts(2, 1, 1, 0), tally.counts());
            tally.accept(row("NaN"));
            assertEquals(new FilterCounts(3, 1, 1, 1), tally.counts());
            constructed().forEach(tally);
            assertEquals(new FilterCounts(18, 6, 3, 9), tally.counts());
        }

        @Test
        @DisplayName("counts must add up")
        void countsAddUp() {
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class, () -> new FilterCounts(4, 1, 1, 1));
            assertEquals(
                    "every row falls in exactly one category: total 4, passing 1, failing 1,"
                            + " unknown q-value 1",
                    refused.getMessage());
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new FilterCounts(1, -1, 1, 1)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new FilterCounts(1, 1, -1, 1)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new FilterCounts(1, 1, 1, -1)),
                    () -> assertEquals(3, new FilterCounts(3, 1, 1, 1).total()),
                    () -> assertEquals(0, new FilterCounts(0, 0, 0, 0).total()));
        }
    }
}
