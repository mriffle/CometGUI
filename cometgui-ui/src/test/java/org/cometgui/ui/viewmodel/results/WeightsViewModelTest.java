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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.results.parser.WeightsSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The learned feature weights view over real and constructed weights files (phase 10 gate item 7 at
 * the view-model). The expected numbers are cometgui-results' independently computed ones ({@code
 * WEIGHTS-SUMMARY.txt}: Python {@code Fraction}/{@code decimal}, not Java), written here by hand at
 * the view's documented precision -- four decimal places for a split's weight, six for a statistic.
 */
class WeightsViewModelTest {

    private final WeightsViewModel weights = new WeightsViewModel();

    private static WeightsSummary summary(Path file) throws IOException {
        return WeightsSummary.of(WeightsReader.read(file));
    }

    private void showTies() throws IOException {
        weights.show(Optional.of(summary(ResultsFixtures.copy("weights-ties.txt"))));
    }

    private List<String> order() {
        return weights.rowsProperty().get().stream().map(WeightsRowView::feature).toList();
    }

    @Test
    @DisplayName("the specified name and description; nothing shown before a run's weights")
    void words() {
        assertEquals("Learned feature weights (Percolator SVM)", WeightsViewModel.TITLE);
        assertTrue(
                WeightsViewModel.DESCRIPTION.contains(
                        "coefficients Percolator's linear SVM learned for each feature, after"
                                + " Percolator's feature normalisation, in each of its"
                                + " cross-validation splits. They are not causal importances"),
                WeightsViewModel.DESCRIPTION);
        assertEquals(List.of(), weights.rowsProperty().get());
        assertEquals(
                "No learned feature weights: no run is open, or the open run has no weights"
                        + " artefact.",
                weights.statusProperty().get());
    }

    @Test
    @DisplayName("every row, in file order, at the documented precision")
    void rows() throws IOException {
        Path file = ResultsFixtures.copy("weights-ties.txt");
        showTies();
        String allPositive = "all positive (2 positive, 0 negative, 0 zero)";
        String allZero = "all zero (0 positive, 0 negative, 2 zero)";
        String allNegative = "all negative (0 positive, 2 negative, 0 zero)";
        assertEquals(
                List.of(
                        new WeightsRowView(
                                "c",
                                false,
                                "",
                                List.of("0.7500", "-0.2500"),
                                "0.250000",
                                "0.500000",
                                "0.500000",
                                "mixed (1 positive, 1 negative, 0 zero)",
                                "2"),
                        new WeightsRowView(
                                "z",
                                false,
                                "",
                                List.of("0.0000", "-0.0000"),
                                "0.000000",
                                "0.000000",
                                "0.000000",
                                allZero,
                                "7"),
                        new WeightsRowView(
                                "a",
                                false,
                                "",
                                List.of("0.5000", "0.5000"),
                                "0.500000",
                                "0.500000",
                                "0.000000",
                                allPositive,
                                "2"),
                        new WeightsRowView(
                                "f",
                                false,
                                "",
                                List.of("2.0000", "1.0000"),
                                "1.500000",
                                "1.500000",
                                "0.500000",
                                allPositive,
                                "1"),
                        new WeightsRowView(
                                "d",
                                false,
                                "",
                                List.of("0.0000", "0.5000"),
                                "0.250000",
                                "0.250000",
                                "0.250000",
                                "mixed (1 positive, 0 negative, 1 zero)",
                                "5"),
                        new WeightsRowView(
                                "b",
                                false,
                                "",
                                List.of("-0.2500", "-0.7500"),
                                "-0.500000",
                                "0.500000",
                                "0.250000",
                                allNegative,
                                "2"),
                        new WeightsRowView(
                                "e",
                                false,
                                "",
                                List.of("0.1250", "0.1250"),
                                "0.125000",
                                "0.125000",
                                "0.000000",
                                allPositive,
                                "6"),
                        new WeightsRowView(
                                "y",
                                false,
                                "",
                                List.of("0.0000", "0.0000"),
                                "0.000000",
                                "0.000000",
                                "0.000000",
                                allZero,
                                "7"),
                        new WeightsRowView(
                                "m0",
                                true,
                                "bias term, not ranked",
                                List.of("-3.0000", "-1.0000"),
                                "-2.000000",
                                "2.000000",
                                "1.000000",
                                allNegative,
                                "")),
                weights.rowsProperty().get());
        assertEquals(List.of("Split 1", "Split 2"), weights.splitHeadingsProperty().get());
        assertEquals(
                "2 cross-validation splits, as read from " + file + "; 9 features.",
                weights.statusProperty().get());
    }

    @Test
    @DisplayName("sortable by every column on the values, ties in file order both ways")
    void sorting() throws IOException {
        showTies();
        weights.sortBy(WeightsColumn.MEAN_ABSOLUTE);
        assertEquals(List.of("z", "y", "e", "d", "c", "a", "b", "f", "m0"), order());
        weights.sortBy(WeightsColumn.MEAN_ABSOLUTE);
        assertEquals(
                new WeightsSort(WeightsColumn.MEAN_ABSOLUTE, 0, true),
                weights.sortProperty().get());
        assertEquals(List.of("m0", "f", "c", "a", "b", "d", "e", "z", "y"), order());
        weights.sortBy(WeightsColumn.MEAN_ABSOLUTE);
        assertEquals(WeightsSort.FILE_ORDER, weights.sortProperty().get());
        assertEquals(List.of("c", "z", "a", "f", "d", "b", "e", "y", "m0"), order());
        weights.sortBy(WeightsColumn.RANK);
        assertEquals(List.of("f", "c", "a", "b", "d", "e", "z", "y", "m0"), order());
        weights.sortBy(WeightsColumn.RANK);
        assertEquals(
                List.of("z", "y", "e", "d", "c", "a", "b", "f", "m0"),
                order(),
                "the unranked bias last in both directions");
        weights.sortBy(WeightsColumn.FEATURE);
        assertEquals(List.of("a", "b", "c", "d", "e", "f", "m0", "y", "z"), order());
        weights.sortBySplit(0);
        assertEquals(List.of("m0", "b", "z", "d", "y", "e", "a", "c", "f"), order());
        weights.sortBySplit(1);
        assertEquals(List.of("m0", "b", "c", "z", "y", "e", "a", "d", "f"), order());
        weights.sortBySplit(1);
        assertEquals(
                List.of("f", "a", "d", "e", "z", "y", "c", "b", "m0"),
                order(),
                "-0.0000 and 0 are equal weights: z and y in file order");
        weights.sortBy(WeightsColumn.SIGN_CONSISTENCY);
        assertEquals(List.of("a", "f", "e", "b", "m0", "c", "d", "z", "y"), order());
        weights.sortBy(WeightsColumn.MEAN_SIGNED);
        assertEquals(List.of("m0", "b", "z", "y", "e", "c", "d", "a", "f"), order());
        weights.sortBy(WeightsColumn.STANDARD_DEVIATION);
        assertEquals(List.of("z", "a", "e", "y", "d", "b", "c", "f", "m0"), order());
        weights.sortBy(WeightsColumn.FILE_ORDER);
        assertEquals(List.of("c", "z", "a", "f", "d", "b", "e", "y", "m0"), order());
        assertThrows(IllegalArgumentException.class, () -> weights.sortBy(WeightsColumn.SPLIT));
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> weights.sortBySplit(2));
        assertEquals(
                "the weights shown have 2 splits, so there is no split 3", refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> weights.sortBySplit(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WeightsSort(WeightsColumn.MEAN_SIGNED, 1, false));
    }

    @Test
    @DisplayName("the split count is the file's: two, four, three")
    void splitCounts() throws IOException {
        weights.show(Optional.of(summary(ResultsFixtures.copy("weights-four-splits.txt"))));
        assertEquals(
                List.of("Split 1", "Split 2", "Split 3", "Split 4"),
                weights.splitHeadingsProperty().get());
        WeightsRowView featB = weights.rowsProperty().get().get(1);
        assertEquals(
                new WeightsRowView(
                        "feat_b",
                        false,
                        "",
                        List.of("-2.2500", "-0.2500", "0.1250", "-1.1250"),
                        "-0.875000",
                        "0.937500",
                        "0.914296",
                        "mixed (1 positive, 3 negative, 0 zero)",
                        "1"),
                featB);
        assertEquals("0.640875", weights.rowsProperty().get().get(2).meanSigned());
        weights.sortBy(WeightsColumn.RANK);
        weights.show(Optional.of(summary(ResultsFixtures.copy("weights-two-splits.txt"))));
        assertEquals(
                WeightsSort.FILE_ORDER, weights.sortProperty().get(), "a new file, file order");
        assertEquals(List.of("lnrSp", "deltLCn", "Xcorr", "Charge2", "m0"), order());
        assertEquals(List.of("0.1250", "0.1300"), weights.rowsProperty().get().get(0).splits());
        weights.show(Optional.empty());
        assertEquals(List.of(), weights.rowsProperty().get());
        assertEquals(List.of(), weights.splitHeadingsProperty().get());
    }

    @Test
    @DisplayName("the real K562 weights: ranks, means and deviations as computed independently")
    void realK562() throws IOException {
        Path file = ResultsFixtures.k562Weights();
        weights.show(Optional.of(summary(file)));
        List<String> header =
                Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.startsWith("#"))
                        .findFirst()
                        .map(line -> List.of(line.split("\t")))
                        .orElseThrow();
        assertEquals(
                List.of("Split 1", "Split 2", "Split 3"), weights.splitHeadingsProperty().get());
        assertEquals(header, order());
        weights.sortBy(WeightsColumn.RANK);
        List<WeightsRowView> ranked = weights.rowsProperty().get();
        assertAll(
                () ->
                        assertEquals(
                                List.of("lnrSp", "1", "-0.336333", "0.336333", "0.079189"),
                                cells(ranked.get(0))),
                () ->
                        assertEquals(
                                List.of("lnExpect", "2", "-0.235433", "0.235433", "0.104208"),
                                cells(ranked.get(1))),
                () ->
                        assertEquals(
                                List.of("Xcorr", "3", "0.113900", "0.113900", "0.019698"),
                                cells(ranked.get(2))),
                () -> assertEquals("m0", ranked.get(ranked.size() - 1).feature()),
                () ->
                        assertEquals(
                                "all negative (0 positive, 3 negative, 0 zero)",
                                ranked.get(0).signConsistency()));
    }

    private static List<String> cells(WeightsRowView row) {
        return List.of(
                row.feature(),
                row.rank(),
                row.meanSigned(),
                row.meanAbsolute(),
                row.standardDeviation());
    }
}
