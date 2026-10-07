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

import java.math.BigDecimal;
import java.util.Objects;
import org.cometgui.results.parser.QValue;
import org.cometgui.results.parser.ResultRow;

/**
 * A display q-value filter: a view predicate over rows Percolator already wrote, never a rerun and
 * never a change to a file.
 *
 * <p>The specification's predicate (<em>Q-value result filters</em>) is {@code visible := q_value
 * <= filter}: the boundary is inclusive, so a row at exactly the cutoff is visible. {@code
 * R-RES-01}: the cutoff is within {@code [0, 1]}, 0 and 1 included; anything else is refused with a
 * message. {@code R-RES-02}: a row whose q-value is not known is neither passing nor failing but
 * {@link Visibility#UNKNOWN_Q_VALUE}, counted in its own category.
 *
 * <p>There are exactly two kinds, {@link PsmQValueFilter} and {@link PeptideQValueFilter}, set
 * independently. Neither is, contains or converts to Percolator's {@code --testFDR} or {@code
 * --trainFDR} (those are {@code org.cometgui.params.percolator.validation.TestFdr} and {@code
 * TrainFdr}): a display filter changes what is shown and exported, a learning threshold changes
 * what Percolator computes ({@code AC-RES-05}).
 *
 * <p>The comparison is made between {@code double}s: the row's q-value as {@link
 * Double#parseDouble} reads it and the cutoff's nearest {@code double}. Both are correctly rounded
 * from their decimal text and rounding is monotone, so equal decimals compare equal -- {@code 0.01}
 * against {@code 0.01} is visible -- and the two can disagree with exact decimal arithmetic only
 * for decimals closer together than a {@code double} can tell apart, beyond the six significant
 * digits Percolator writes.
 */
public abstract sealed class QValueFilter permits PsmQValueFilter, PeptideQValueFilter {

    /** The default cutoff for either filter, 0.01 ({@code R-RES-01}). */
    public static final BigDecimal DEFAULT_CUTOFF = new BigDecimal("0.01");

    private final BigDecimal cutoff;
    private final double comparable;

    /*
     * The cutoff arrives already checked: an abstract class whose constructor throws leaves a
     * partially built object behind (SpotBugs CT_CONSTRUCTOR_THROW), so each final subclass checks
     * it first, through checked(), and this constructor cannot fail.
     */
    QValueFilter(BigDecimal checkedCutoff) {
        this.cutoff = checkedCutoff;
        this.comparable = checkedCutoff.doubleValue();
    }

    /**
     * Checks a cutoff's range: {@code [0, 1]}, both ends included ({@code R-RES-01}).
     *
     * @param what the filter's name, for the message
     * @param cutoff the cutoff
     * @return the same cutoff
     * @throws IllegalArgumentException if it is below 0 or above 1
     * @throws NullPointerException if it is {@code null}
     */
    static BigDecimal checked(String what, BigDecimal cutoff) {
        Objects.requireNonNull(cutoff, what + " cutoff");
        if (cutoff.signum() < 0 || cutoff.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "The "
                            + what
                            + " must be between 0 and 1 inclusive, but was "
                            + cutoff.toPlainString());
        }
        return cutoff;
    }

    /**
     * Reads a cutoff typed as text, in {@code Locale.ROOT} form.
     *
     * @param what the filter's name, for the message
     * @param text the text
     * @return the cutoff, not yet range-checked
     * @throws IllegalArgumentException if the text is not a decimal number -- {@code NaN}, {@code
     *     Infinity}, a comma decimal separator and blank text included
     */
    static BigDecimal parseCutoff(String what, String text) {
        Objects.requireNonNull(text, what + " text");
        try {
            return new BigDecimal(text.strip());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "The "
                            + what
                            + " must be a number between 0 and 1 inclusive, written with a '.'"
                            + " decimal point, but was '"
                            + text
                            + "'",
                    notANumber);
        }
    }

    /**
     * Converts a cutoff given as a {@code double}.
     *
     * @param what the filter's name, for the message
     * @param value the value
     * @return the cutoff, not yet range-checked
     * @throws IllegalArgumentException if the value is {@code NaN} or infinite
     */
    static BigDecimal cutoffOf(String what, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "The "
                            + what
                            + " must be a number between 0 and 1 inclusive, but was "
                            + value);
        }
        return BigDecimal.valueOf(value);
    }

    /**
     * The cutoff.
     *
     * @return the value, within {@code [0, 1]}
     */
    public BigDecimal cutoff() {
        return cutoff;
    }

    /**
     * The cutoff as text, for display, provenance and export metadata.
     *
     * @return for example {@code 0.01}
     */
    public String text() {
        return cutoff.toPlainString();
    }

    /**
     * Where a q-value falls.
     *
     * @param qValue the q-value
     * @return {@link Visibility#PASSES} when known and at or below the cutoff, {@link
     *     Visibility#FAILS} when known and above it, otherwise {@link Visibility#UNKNOWN_Q_VALUE}
     */
    public Visibility classify(QValue qValue) {
        if (!qValue.isKnown()) {
            return Visibility.UNKNOWN_Q_VALUE;
        }
        return qValue.value() <= comparable ? Visibility.PASSES : Visibility.FAILS;
    }

    /**
     * Where a row falls.
     *
     * @param row the row
     * @return as {@link #classify(QValue)} for its q-value
     */
    public Visibility classify(ResultRow row) {
        return classify(row.qValue());
    }

    /**
     * Whether a row passes: the specification's {@code q_value <= filter}.
     *
     * @param row the row
     * @return {@code true} only for {@link Visibility#PASSES}; a row with an unknown q-value is not
     *     passing, and a caller shows it through {@link Visibility#UNKNOWN_Q_VALUE} rather than
     *     hiding it
     */
    public boolean passes(ResultRow row) {
        return classify(row) == Visibility.PASSES;
    }

    /**
     * Counts rows by where they fall.
     *
     * @param rows the rows
     * @return the counts
     */
    public FilterCounts count(Iterable<ResultRow> rows) {
        FilterTally tally = tally();
        rows.forEach(tally);
        return tally.counts();
    }

    /**
     * A tally to feed rows to one at a time, for example from {@code
     * ResultTableReader.forEach(file, filter.tally())}.
     *
     * @return a new, empty tally under this filter
     */
    public FilterTally tally() {
        return new FilterTally(this);
    }

    @Override
    public boolean equals(Object other) {
        return other != null
                && other.getClass() == getClass()
                && cutoff.compareTo(((QValueFilter) other).cutoff) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass().getName(), cutoff.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(q <= " + text() + ")";
    }
}
