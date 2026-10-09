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

package org.cometgui.ui.viewmodel.results;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.results.parser.FeatureWeights;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * The learned feature weights view ({@code R-PERC-08}, {@code R-PERC-09}, design decision P10-8):
 * one row per feature of a run's {@link WeightsSummary} -- the summary computes every value; this
 * class only writes them as text and orders the rows.
 *
 * <p>The table is the source of truth; a chart, if the view draws one, is secondary. It is sortable
 * by any column, on the summary's numbers rather than on their text, with ties in the file's
 * feature order in both directions, as the result tables' ties are. The number of splits shown is
 * the summary's, read from the weights file, never assumed to be three.
 *
 * <p>Everything here runs on the interface thread: the summary is already in memory.
 */
public final class WeightsViewModel {

    /** The view's name, as the specification gives it. */
    public static final String TITLE = "Learned feature weights (Percolator SVM)";

    /** What the values are, and are not, as the specification requires it said. */
    public static final String DESCRIPTION =
            "These are the coefficients Percolator's linear SVM learned for each feature, after"
                    + " Percolator's feature normalisation, in each of its cross-validation"
                    + " splits. They are not causal importances: a large weight says the feature"
                    + " helped separate target from decoy matches in this run's training, not that"
                    + " it makes a match correct. The rank is by mean absolute weight, 1 for the"
                    + " largest; the bias term m0 is listed and not ranked. This table is the"
                    + " source of truth, and its export keeps every value at full precision.";

    /** The status when the run has no weights. */
    public static final String NO_WEIGHTS =
            "No learned feature weights: no run is open, or the open run has no weights artefact.";

    /** Decimal places of each split's weight as shown: as many as Percolator writes. */
    public static final int SPLIT_DECIMALS = 4;

    /** Decimal places of each statistic as shown. */
    public static final int STATISTIC_DECIMALS = 6;

    /** What the bias term's row says it is. */
    public static final String BIAS_NOTE = "bias term, not ranked";

    private List<FeatureWeights> features = List.of();

    private final NonNullProperty<List<WeightsRowView>> rows;

    private final NonNullProperty<List<String>> splitHeadings;

    private final NonNullProperty<String> status;

    private final NonNullProperty<WeightsSort> sort;

    /** A view with no weights shown. */
    public WeightsViewModel() {
        rows = new NonNullProperty<>(this, "rows", List.of());
        splitHeadings = new NonNullProperty<>(this, "splitHeadings", List.of());
        status = new NonNullProperty<>(this, "status", NO_WEIGHTS);
        sort = new NonNullProperty<>(this, "sort", WeightsSort.FILE_ORDER);
    }

    /**
     * Shows a run's weights, in the file's feature order, or none.
     *
     * @param summary the summary; empty for none
     */
    public void show(Optional<WeightsSummary> summary) {
        Objects.requireNonNull(summary, "summary");
        sort.set(WeightsSort.FILE_ORDER);
        if (summary.isEmpty()) {
            features = List.of();
            splitHeadings.set(List.of());
            status.set(NO_WEIGHTS);
            rows.set(List.of());
            return;
        }
        WeightsSummary weights = summary.get();
        features = weights.features();
        int splits = weights.splitCount();
        List<String> headings = new ArrayList<>(splits);
        for (int split = 1; split <= splits; split++) {
            headings.add("Split " + split);
        }
        splitHeadings.set(List.copyOf(headings));
        status.set(
                splits
                        + (splits == 1
                                ? " cross-validation split, as read from "
                                : " cross-validation splits, as read from ")
                        + weights.file()
                        + "; "
                        + features.size()
                        + (features.size() == 1 ? " feature." : " features."));
        publishRows();
    }

    /**
     * Sorts by a column, as a heading's click does: ascending, then descending, then file order.
     *
     * @param column the column; not {@link WeightsColumn#SPLIT}, which {@link #sortBySplit} sorts
     * @throws IllegalArgumentException for {@link WeightsColumn#SPLIT}
     */
    public void sortBy(WeightsColumn column) {
        Objects.requireNonNull(column, "column");
        if (column == WeightsColumn.SPLIT) {
            throw new IllegalArgumentException("a split is sorted by sortBySplit(split)");
        }
        toggle(new WeightsSort(column, 0, false));
    }

    /**
     * Sorts by one split's normalised weight, toggling as {@link #sortBy} does.
     *
     * @param split the split, from 0
     * @throws IllegalArgumentException if the weights shown have no such split
     */
    public void sortBySplit(int split) {
        if (split < 0 || split >= splitHeadings.get().size()) {
            throw new IllegalArgumentException(
                    "the weights shown have "
                            + splitHeadings.get().size()
                            + " splits, so there is no split "
                            + (split + 1));
        }
        toggle(new WeightsSort(WeightsColumn.SPLIT, split, false));
    }

    /**
     * One row per feature, in the current order.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<WeightsRowView>> rowsProperty() {
        return rows.getReadOnlyProperty();
    }

    /**
     * The headings of the split columns, one per split the file holds.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<String>> splitHeadingsProperty() {
        return splitHeadings.getReadOnlyProperty();
    }

    /**
     * How many splits and features are shown and from which file, or why none are.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> statusProperty() {
        return status.getReadOnlyProperty();
    }

    /**
     * The order.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<WeightsSort> sortProperty() {
        return sort.getReadOnlyProperty();
    }

    private void toggle(WeightsSort ascending) {
        WeightsSort now = sort.get();
        boolean same = now.column() == ascending.column() && now.split() == ascending.split();
        if (!same) {
            sort.set(ascending);
        } else if (!now.descending()) {
            sort.set(new WeightsSort(now.column(), now.split(), true));
        } else {
            sort.set(WeightsSort.FILE_ORDER);
        }
        publishRows();
    }

    private void publishRows() {
        List<Integer> order = new ArrayList<>(features.size());
        for (int index = 0; index < features.size(); index++) {
            order.add(index);
        }
        WeightsSort by = sort.get();
        if (by.column() != WeightsColumn.FILE_ORDER) {
            order.sort(comparator(by));
        }
        List<WeightsRowView> views = new ArrayList<>(order.size());
        for (int index : order) {
            views.add(view(features.get(index)));
        }
        rows.set(List.copyOf(views));
    }

    /**
     * The order of two features' indexes: the column's values, the direction applied to them only,
     * then file order ascending.
     */
    private Comparator<Integer> comparator(WeightsSort by) {
        Comparator<Integer> values =
                (left, right) -> compare(by, features.get(left), features.get(right));
        if (by.descending()) {
            values = values.reversed();
        }
        Comparator<Integer> withMissingLast =
                by.column() == WeightsColumn.RANK
                        ? Comparator.<Integer, Boolean>comparing(
                                        index -> features.get(index).rank().isEmpty())
                                .thenComparing(values)
                        : values;
        return withMissingLast.thenComparing(Comparator.naturalOrder());
    }

    private static int compare(WeightsSort by, FeatureWeights left, FeatureWeights right) {
        return switch (by.column()) {
            case FILE_ORDER -> 0;
            case FEATURE -> left.name().compareTo(right.name());
            case SPLIT ->
                    numbers(left.normalised().get(by.split()), right.normalised().get(by.split()));
            case MEAN_SIGNED -> numbers(left.meanSigned(), right.meanSigned());
            case MEAN_ABSOLUTE -> numbers(left.meanAbsolute(), right.meanAbsolute());
            case STANDARD_DEVIATION -> numbers(left.standardDeviation(), right.standardDeviation());
            case SIGN_CONSISTENCY -> left.signConsistency().compareTo(right.signConsistency());
            case RANK -> Integer.compare(left.rank().orElse(0), right.rank().orElse(0));
        };
    }

    /** Numeric order in which -0.0 equals 0.0, as it does as a weight. */
    private static int numbers(double left, double right) {
        return Double.compare(left + 0.0, right + 0.0);
    }

    private static WeightsRowView view(FeatureWeights feature) {
        List<String> splits = new ArrayList<>(feature.normalised().size());
        for (double weight : feature.normalised()) {
            splits.add(decimal(weight, SPLIT_DECIMALS));
        }
        return new WeightsRowView(
                feature.name(),
                feature.isBias(),
                feature.isBias() ? BIAS_NOTE : "",
                splits,
                decimal(feature.meanSigned(), STATISTIC_DECIMALS),
                decimal(feature.meanAbsolute(), STATISTIC_DECIMALS),
                decimal(feature.standardDeviation(), STATISTIC_DECIMALS),
                signWords(feature),
                feature.rank().isPresent() ? Integer.toString(feature.rank().getAsInt()) : "");
    }

    private static String decimal(double value, int places) {
        return String.format(Locale.ROOT, "%." + places + "f", value);
    }

    private static String signWords(FeatureWeights feature) {
        String verdict =
                switch (feature.signConsistency()) {
                    case ALL_POSITIVE -> "all positive";
                    case ALL_NEGATIVE -> "all negative";
                    case MIXED -> "mixed";
                    case ALL_ZERO -> "all zero";
                };
        return verdict
                + " ("
                + feature.positiveSplits()
                + " positive, "
                + feature.negativeSplits()
                + " negative, "
                + feature.zeroSplits()
                + " zero)";
    }
}
