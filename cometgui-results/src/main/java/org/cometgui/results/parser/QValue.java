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

package org.cometgui.results.parser;

import java.util.Objects;

/**
 * One row's q-value exactly as Percolator wrote it, and what it means.
 *
 * <p>The specification keeps the original q-values (<em>Q-value result filters</em>), so the text
 * is held verbatim. {@code R-RES-02}: a q-value that is missing or unparsable is not a number to
 * compare against a filter; it is its own category, {@link #isKnown()} {@code false}, so that a
 * caller can count it and show it rather than silently dropping or including the row. A value
 * outside {@code [0, 1]} is not a q-value either and joins that category.
 */
public final class QValue {

    /** Why a q-value is, or is not, known. */
    public enum Status {
        /** A finite decimal number within {@code [0, 1]}. */
        KNOWN,
        /** The field is empty. */
        MISSING,
        /**
         * The field is not a decimal number in {@code Locale.ROOT} form: {@code NaN}, {@code nan},
         * {@code inf}, {@code Infinity}, a comma decimal separator, or any other text.
         */
        UNPARSABLE,
        /** A finite number outside {@code [0, 1]}, which no q-value can be. */
        OUT_OF_RANGE
    }

    private final String text;
    private final Status status;
    private final double value;

    private QValue(String text, Status status, double value) {
        this.text = text;
        this.status = status;
        this.value = value;
    }

    /**
     * Reads a q-value field.
     *
     * @param text the field exactly as written; never {@code null}
     * @return the q-value, known or not
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static QValue of(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            return new QValue(text, Status.MISSING, Double.NaN);
        }
        double parsed = DecimalText.parse(text);
        if (Double.isNaN(parsed)) {
            return new QValue(text, Status.UNPARSABLE, Double.NaN);
        }
        if (parsed < 0.0 || parsed > 1.0) {
            return new QValue(text, Status.OUT_OF_RANGE, Double.NaN);
        }
        return new QValue(text, Status.KNOWN, parsed);
    }

    /**
     * The field exactly as Percolator wrote it.
     *
     * @return the original text, possibly empty
     */
    public String text() {
        return text;
    }

    /**
     * Whether this is known, or why it is not.
     *
     * @return the status
     */
    public Status status() {
        return status;
    }

    /**
     * Whether this is a q-value a filter can compare.
     *
     * @return {@code true} only for {@link Status#KNOWN}
     */
    public boolean isKnown() {
        return status == Status.KNOWN;
    }

    /**
     * The number, for a known q-value.
     *
     * @return the value, within {@code [0, 1]}
     * @throws IllegalStateException if the q-value is not known: there is no number to compare, and
     *     returning one would silently include or exclude the row
     */
    public double value() {
        if (status != Status.KNOWN) {
            throw new IllegalStateException(
                    "the q-value '" + text + "' is " + status + ", so it has no value to compare");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QValue that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return status == Status.KNOWN ? text : status + "('" + text + "')";
    }
}
