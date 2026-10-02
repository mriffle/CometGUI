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

package org.cometgui.params.comet.model;

import java.math.BigDecimal;
import java.util.Objects;
import org.cometgui.params.comet.value.DecimalList;
import org.cometgui.params.comet.value.DecimalRange;
import org.cometgui.params.comet.value.IntegerRange;
import org.cometgui.params.comet.value.VariableModification;

/**
 * The typed value of one parameter. Which variant a parameter holds is fixed by its {@link
 * org.cometgui.params.comet.schema.ValueKind}; {@link ParameterValueCodec#fits} says which.
 *
 * <p>The two parameters of the signed tolerance pair hold a {@link Decimal} each, and the
 * ion-series family a {@link Flag} each: the model has one entry per parameter, and {@link
 * CometParameters#tolerancePair()} and {@link CometParameters#ionSeries()} give the structured
 * views over them.
 */
public sealed interface ParameterValue {

    /**
     * A whole number: the {@code INTEGER}, {@code INTEGER_ENUM} and {@code ENZYME_REFERENCE} kinds.
     *
     * @param value the number
     */
    record Whole(int value) implements ParameterValue {}

    /**
     * One decimal, with the scale written: the {@code DECIMAL} and {@code TOLERANCE_PAIR_MEMBER}
     * kinds.
     *
     * @param value the number
     */
    record Decimal(BigDecimal value) implements ParameterValue {

        /** Validates presence. */
        public Decimal {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Off or on, written {@code 0} or {@code 1}: the {@code BOOLEAN_FLAG} and {@code
     * ION_SERIES_FLAG} kinds.
     *
     * @param on whether it is on
     */
    record Flag(boolean on) implements ParameterValue {}

    /**
     * Text, possibly empty: the {@code STRING}, {@code STRING_ENUM} and {@code FILE_PATH} kinds.
     *
     * <p>It must read back as written, so it may hold no {@code #} (Comet ends a value there), no
     * line break, and no white space at either end (the reader trims).
     *
     * @param text the text
     */
    record Text(String text) implements ParameterValue {

        /**
         * Validates the text.
         *
         * @throws IllegalArgumentException if the text could not be read back as written
         */
        public Text {
            Objects.requireNonNull(text, "text");
            ParameterValueCodec.unwritableText(text)
                    .ifPresent(
                            problem -> {
                                throw new IllegalArgumentException(
                                        "the text \""
                                                + text
                                                + "\" cannot be a parameter value: "
                                                + problem);
                            });
        }
    }

    /**
     * Two whole numbers on one line: the {@code INTEGER_RANGE} kind.
     *
     * @param range the range
     */
    record WholeRange(IntegerRange range) implements ParameterValue {

        /** Validates presence. */
        public WholeRange {
            Objects.requireNonNull(range, "range");
        }
    }

    /**
     * Two decimals on one line: the {@code DECIMAL_RANGE} kind.
     *
     * @param range the range
     */
    record DecimalPair(DecimalRange range) implements ParameterValue {

        /** Validates presence. */
        public DecimalPair {
            Objects.requireNonNull(range, "range");
        }
    }

    /**
     * Zero or more decimals: the {@code DECIMAL_LIST} kind.
     *
     * @param list the list
     */
    record Decimals(DecimalList list) implements ParameterValue {

        /** Validates presence. */
        public Decimals {
            Objects.requireNonNull(list, "list");
        }
    }

    /**
     * A variable-modification tuple: the {@code VARIABLE_MOD_TUPLE} kind.
     *
     * @param modification the tuple
     */
    record Tuple(VariableModification modification) implements ParameterValue {

        /** Validates presence. */
        public Tuple {
            Objects.requireNonNull(modification, "modification");
        }
    }
}
