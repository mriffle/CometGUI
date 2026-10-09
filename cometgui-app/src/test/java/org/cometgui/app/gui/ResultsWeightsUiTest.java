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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.TableView;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.K562Outputs;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.app.testing.TestJson;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.workflow.steps.RunResultFiles;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 10 exit-gate item 7 on screen ({@code R-PERC-08}, {@code R-PERC-09}, {@code AC-RES-08},
 * {@code AC-RES-09}): the <em>Learned feature weights (Percolator SVM)</em> table shows, for every
 * feature, each split's normalised weight, the mean signed, mean absolute and standard deviation,
 * the sign consistency and the rank, equal to values computed independently from the weights
 * artefact; the number of split columns is the number of splits in the file; sorting by mean
 * absolute weight, descending, gives the rank order; and the Export button writes the values at
 * full precision with the split count in the sidecar.
 *
 * <p><strong>The independent values</strong> are computed here, from the raw text, by {@link
 * Independent}: the file's non-comment lines in threes (feature names, normalised weights, raw
 * weights), every value an exact {@link BigDecimal}, the means and population variance (divide by
 * <i>n</i>) in {@link MathContext#DECIMAL128}, the square root at 40 digits, signs by {@link
 * BigDecimal#signum()} (so {@code -0.0000} is zero), the rank competition ranking among the
 * features other than the bias {@code m0} by exact comparison of mean absolute weight -- nothing
 * from {@code org.cometgui.results}. That computation is itself checked against a second,
 * independent one: the numbers unit 4 printed with Python {@code fractions} and recorded in {@code
 * cometgui-results/src/test/resources/org/cometgui/results/parser/WEIGHTS-SUMMARY.txt}, typed out
 * here in {@link #K562_PINS}, {@link #TWO_PINS} and {@link #FOUR_PINS} (summary statistics only:
 * D-006).
 *
 * <p><strong>Display precision</strong>, as {@code WeightsViewModel} documents it: a split's weight
 * with 4 decimals, a statistic with 6. Every shown number must equal the independent value rounded
 * half-up to that many decimals, compared as numbers (so {@code -0.0000} equals {@code 0}). The
 * exported file holds every number at full precision ({@code Double.toString}); each must be within
 * {@value #TOLERANCE} of the independent value -- the product sums at most four {@code double}s
 * below 400 in magnitude and divides once, an error under 1e-13, while any wrong computation (a
 * split dropped or repeated, {@code n - 1}, a signed mean for an absolute one) moves a value here
 * by at least 1e-5.
 *
 * <p><strong>The runs</strong> (constructed, {@link ResultRuns}): {@code run-k562-weights} with the
 * real K562 Percolator 3.07.1 weights (3 splits, 22 features; {@link K562Outputs}, from {@code
 * scratch/}, failing, never skipping, without it), {@code run-two-splits} and {@code
 * run-four-splits} with the hand-made two- and four-split files. Each has {@code
 * psms-unknown-q.tsv} as its target PSM table, since a run is listed only with a table.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResultsWeightsUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-09T00:00:00Z"));

    /** How far an exported number may be from the independent value. */
    private static final double TOLERANCE = 1e-12;

    private static final String K562 = "run-k562-weights";

    private static final String TWO = "run-two-splits";

    private static final String FOUR = "run-four-splits";

    /**
     * Unit 4's Python numbers for the K562 weights, from {@code WEIGHTS-SUMMARY.txt}: feature, rank
     * ({@code -} for the bias), verdict, positive/negative/zero splits, mean signed, mean absolute,
     * standard deviation.
     */
    private static final String K562_PINS =
            """
    lnrSp 1 ALL_NEGATIVE 0/3/0 -0.33633333333333332 0.33633333333333332 0.079188944233957864
    deltLCn 18 ALL_ZERO 0/0/3 0 0 0
    deltCn 7 ALL_POSITIVE 3/0/0 0.088066666666666668 0.088066666666666668 0.041435277508690853
    lnExpect 2 ALL_NEGATIVE 0/3/0 -0.23543333333333333 0.23543333333333333 0.10420787984707405
    Xcorr 3 ALL_POSITIVE 3/0/0 0.1139 0.1139 0.01969839248940549
    Sp 9 MIXED 1/2/0 -0.040066666666666667 0.042466666666666666 0.031526954957446951
    IonFrac 4 ALL_POSITIVE 3/0/0 0.094200000000000006 0.094200000000000006 0.032167996518278848
    Mass 5 MIXED 2/1/0 0.08953333333333334 0.090399999999999994 0.074673705025411874
    PepLen 15 MIXED 2/1/0 -0.0117 0.014500000000000001 0.019536802877304839
    Charge1 18 ALL_ZERO 0/0/3 0 0 0
    Charge2 14 MIXED 2/1/0 0.0076333333333333331 0.015166666666666667 0.014524997609485365
    Charge3 13 MIXED 1/2/0 -0.0018 0.018133333333333335 0.01859910392106745
    Charge4 12 MIXED 1/2/0 -0.017566666666666668 0.019166666666666665 0.0142712609424987
    Charge5 17 ALL_POSITIVE 3/0/0 0.011066666666666667 0.011066666666666667 0.0093596058796416321
    Charge6 18 ALL_ZERO 0/0/3 0 0 0
    enzN 16 ALL_POSITIVE 3/0/0 0.014066666666666667 0.014066666666666667 0.0085888817018799878
    enzC 18 ALL_ZERO 0/0/3 0 0 0
    enzInt 11 MIXED 1/2/0 -0.019699999999999999 0.027166666666666665 0.036005647705140184
    lnNumSP 10 MIXED 2/1/0 0.024533333333333334 0.033133333333333334 0.030216588527201781
    dM 8 MIXED 2/1/0 0.053800000000000001 0.054333333333333331 0.067759919323053114
    absdM 6 ALL_NEGATIVE 0/3/0 -0.088800000000000004 0.088800000000000004 0.075335560439056051
    m0 - ALL_NEGATIVE 0/3/0 -0.74250000000000005 0.74250000000000005 0.064065643418814322
    """;

    /** Unit 4's Python numbers for {@code weights-two-splits.txt}. */
    private static final String TWO_PINS =
            """
    lnrSp 3 ALL_POSITIVE 2/0/0 0.1275 0.1275 0.0025000000000000001
    deltLCn 4 ALL_NEGATIVE 0/2/0 -0.0275 0.0275 0.0025000000000000001
    Xcorr 1 ALL_POSITIVE 2/0/0 1.4750000000000001 1.4750000000000001 0.025000000000000001
    Charge2 2 MIXED 1/1/0 0.050000000000000003 0.14999999999999999 0.14999999999999999
    m0 - ALL_NEGATIVE 0/2/0 -2.4500000000000002 2.4500000000000002 0.050000000000000003
    """;

    /** Unit 4's Python numbers for {@code weights-four-splits.txt}. */
    private static final String FOUR_PINS =
            """
    feat_a 2 MIXED 2/0/2 0.3125 0.3125 0.3247595264191645
    feat_b 1 MIXED 1/3/0 -0.875 0.9375 0.91429617739548708
    m0 - MIXED 3/1/0 0.64087499999999997 1.1408750000000001 1.7037476476506137
    """;

    @TempDir private static Path scratch;

    private static RunLayout k562;

    private static RunLayout two;

    private static RunLayout four;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static List<Throwable> listenerFailures;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        k562 =
                run(
                        project,
                        root,
                        K562,
                        "2026-10-09T10:00:00Z",
                        K562Outputs.weights(root.resolve("k562")));
        two =
                run(
                        project,
                        root,
                        TWO,
                        "2026-10-09T09:00:00Z",
                        ResultRuns.fixture(
                                ResultRuns.WEIGHTS_TWO_SPLITS, root.resolve("two/weights.txt")));
        four =
                run(
                        project,
                        root,
                        FOUR,
                        "2026-10-09T08:00:00Z",
                        ResultRuns.fixture(
                                ResultRuns.WEIGHTS_FOUR_SPLITS, root.resolve("four/weights.txt")));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        listenerFailures = ResultsSection.recordListenerFailures(driver);
        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, K562, ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "gate 7: the independent computation agrees with unit 4's Python numbers for all"
                    + " three files")
    void theTwoIndependentComputationsAgree() throws IOException {
        assertAgrees(K562_PINS, Independent.of(RunResultFiles.weights(k562)));
        assertAgrees(TWO_PINS, Independent.of(RunResultFiles.weights(two)));
        assertAgrees(FOUR_PINS, Independent.of(RunResultFiles.weights(four)));
    }

    @Test
    @Order(2)
    @DisplayName(
            "gate 7: the real K562 weights on screen -- 3 split columns read from the file, every"
                    + " value, verdict and rank of 22 features -- equal the independent values")
    void theK562WeightsOnScreen() throws IOException {
        Independent expected = showAndCompare(K562, k562);
        assertEquals(3, expected.splits(), "the file has three splits");
        assertEquals(22, expected.features().size());
    }

    @Test
    @Order(3)
    @DisplayName(
            "gate 7: sorting by mean absolute weight, descending, gives the rank order; a third"
                    + " press restores the file's order")
    void sortingByMeanAbsoluteGivesTheRankOrder() throws IOException {
        Independent expected = showAndCompare(K562, k562);
        driver.clickOn("weights-sort-mean-absolute");
        driver.clickOn("weights-sort-mean-absolute");
        assertEquals("Mean absolute, descending", driver.textOf("weights-sort-mean-absolute"));
        List<Feature> byMeanAbsolute = new ArrayList<>(expected.features());
        // descending by the exact value; equal values keep the file's order (a stable sort)
        byMeanAbsolute.sort(Comparator.comparing(Feature::meanAbsolute).reversed());
        List<List<String>> shown = cells();
        assertEquals(
                byMeanAbsolute.stream().map(ResultsWeightsUiTest::label).toList(),
                shown.stream().map(row -> row.get(0)).toList(),
                "the features by mean absolute weight, descending");
        List<String> ranks =
                shown.stream()
                        .map(row -> row.get(row.size() - 1))
                        .filter(rank -> !rank.isEmpty())
                        .toList();
        List<String> ordered =
                ranks.stream().sorted(Comparator.comparing(Integer::valueOf)).toList();
        assertEquals(ordered, ranks, "the ranks shown run in rank order");
        assertEquals("1", ranks.get(0));

        driver.clickOn("weights-sort-mean-absolute");
        assertEquals("Mean absolute", driver.textOf("weights-sort-mean-absolute"));
        assertEquals(
                expected.features().stream().map(ResultsWeightsUiTest::label).toList(),
                cells().stream().map(row -> row.get(0)).toList(),
                "the file's order again");
    }

    @Test
    @Order(4)
    @DisplayName("gate 7: a two-split file shows two split columns, a four-split file four")
    void twoAndFourSplits() throws IOException {
        Independent twoSplits = showAndCompare(TWO, two);
        assertEquals(2, twoSplits.splits());
        Independent fourSplits = showAndCompare(FOUR, four);
        assertEquals(4, fourSplits.splits());
    }

    @Test
    @Order(5)
    @DisplayName(
            "gate 7: the weights exported with the button hold the independent values at full"
                    + " precision, and the sidecar the split count read from the file")
    void theExportHoldsTheIndependentValues() throws IOException {
        for (Map.Entry<String, RunLayout> run :
                Map.of(K562, k562, TWO, two, FOUR, four).entrySet()) {
            Independent expected = showAndCompare(run.getKey(), run.getValue());
            String status = ResultsSection.export(driver, "results-export-weights");
            assertTrue(
                    status.startsWith(
                            "Exported the learned feature weights of "
                                    + expected.features().size()
                                    + " features over "
                                    + expected.splits()
                                    + " splits to "),
                    () -> "the status: " + status);
            Path file;
            try (Stream<Path> exports = Files.list(run.getValue().exportsDirectory())) {
                List<Path> tables =
                        exports.filter(path -> path.toString().endsWith(".tsv")).toList();
                assertEquals(1, tables.size(), () -> "one export: " + tables);
                file = tables.get(0);
            }
            assertTrue(status.contains(file.toString()), () -> status + " names " + file);
            Map<String, Object> sidecar =
                    TestJson.object(Files.readString(Path.of(file + ".json")));
            assertAll(
                    "the sidecar of " + run.getKey(),
                    () -> assertEquals(run.getKey(), sidecar.get("runId")),
                    () ->
                            assertEquals(
                                    new BigDecimal(expected.splits()), sidecar.get("splitCount")),
                    () ->
                            assertEquals(
                                    new BigDecimal(expected.features().size()),
                                    sidecar.get("featureCount")));
            assertExported(expected, Files.readAllLines(file, StandardCharsets.UTF_8));
        }
        ResultsSection.assertNoListenerFailed(listenerFailures);
    }

    // ===================================================================== on screen ====

    /** Shows a run's weights and compares every cell with the independent values. */
    private static Independent showAndCompare(String runId, RunLayout run) throws IOException {
        driver.clickOn("nav-results");
        ResultsSection.showRun(driver, runId);
        Path file = RunResultFiles.weights(run);
        Independent expected = Independent.of(file);
        int splits = expected.splits();
        assertEquals(
                splits
                        + " cross-validation splits, as read from "
                        + file
                        + "; "
                        + expected.features().size()
                        + " features.",
                driver.textOf("weights-status"));
        List<String> headings = new ArrayList<>();
        for (int split = 1; split <= splits; split++) {
            headings.add(driver.textOf("weights-sort-split-" + split));
        }
        assertEquals(
                Stream.iterate(1, split -> split + 1)
                        .limit(splits)
                        .map(split -> "Split " + split)
                        .toList(),
                headings);
        assertEquals(
                1 + splits + 5,
                driver.callOnFxThread(() -> table().getColumns().size()),
                "feature, one column per split, and five statistics");
        assertTrue(
                !ParameterEditorApp.exists(driver, "weights-sort-split-" + (splits + 1)),
                "no split column beyond the file's");
        List<List<String>> shown = cells();
        assertEquals(expected.features().size(), shown.size(), "one row per feature");
        List<String> wrong = new ArrayList<>();
        for (int index = 0; index < shown.size(); index++) {
            List<String> row = shown.get(index);
            Feature feature = expected.features().get(index);
            String where = runId + " " + feature.name() + ": ";
            if (!row.get(0).equals(label(feature))) {
                wrong.add(where + "feature shown as " + row.get(0));
            }
            for (int split = 0; split < splits; split++) {
                shownAs(
                        row.get(1 + split),
                        feature.normalised().get(split),
                        4,
                        where + "split " + (split + 1),
                        wrong);
            }
            int at = 1 + splits;
            shownAs(row.get(at), feature.meanSigned(), 6, where + "mean signed", wrong);
            shownAs(row.get(at + 1), feature.meanAbsolute(), 6, where + "mean absolute", wrong);
            shownAs(row.get(at + 2), feature.deviation(), 6, where + "standard deviation", wrong);
            if (!row.get(at + 3).equals(feature.signText())) {
                wrong.add(where + "sign consistency shown as " + row.get(at + 3));
            }
            if (!row.get(at + 4).equals(feature.rank())) {
                wrong.add(where + "rank shown as '" + row.get(at + 4) + "', not " + feature.rank());
            }
        }
        assertEquals(List.of(), wrong, "cells that differ from the independent values");
        return expected;
    }

    /** A shown number must be the independent value, rounded half-up to its displayed places. */
    private static void shownAs(
            String shown, BigDecimal exact, int places, String where, List<String> wrong) {
        BigDecimal rounded = exact.setScale(places, RoundingMode.HALF_UP);
        boolean form = shown.matches("-?[0-9]+\\.[0-9]{" + places + "}");
        if (!form || new BigDecimal(shown).compareTo(rounded) != 0) {
            wrong.add(where + " shown as " + shown + ", expected " + rounded.toPlainString());
        }
    }

    private static String label(Feature feature) {
        return feature.name().equals("m0") ? "m0 (bias term, not ranked)" : feature.name();
    }

    /** The weights table's cells, row by row, as the {@code TableView} holds them. */
    private static List<List<String>> cells() {
        TableView<?> table = table();
        return driver.callOnFxThread(
                () -> {
                    List<List<String>> rows = new ArrayList<>();
                    for (int row = 0; row < table.getItems().size(); row++) {
                        List<String> cells = new ArrayList<>();
                        for (var column : table.getColumns()) {
                            Object cell = column.getCellData(row);
                            cells.add(cell == null ? null : cell.toString());
                        }
                        rows.add(cells);
                    }
                    return rows;
                });
    }

    private static TableView<?> table() {
        Node node = driver.node("weights-table");
        if (!(node instanceof TableView<?> table)) {
            return fail("#weights-table is not a table");
        }
        return table;
    }

    // ===================================================================== exported ====

    private static void assertExported(Independent expected, List<String> lines) {
        int splits = expected.splits();
        List<String> header = List.of(lines.get(0).split("\t", -1));
        List<String> wanted = new ArrayList<>(List.of("feature", "bias"));
        for (int split = 1; split <= splits; split++) {
            wanted.add("split_" + split + "_normalised");
        }
        wanted.addAll(
                List.of(
                        "mean_signed",
                        "mean_absolute",
                        "standard_deviation",
                        "sign_consistency",
                        "rank"));
        for (int split = 1; split <= splits; split++) {
            wanted.add("split_" + split + "_raw");
        }
        assertEquals(wanted, header, "the export's columns");
        assertEquals(expected.features().size() + 1, lines.size(), "a row per feature");
        List<String> wrong = new ArrayList<>();
        for (int index = 0; index < expected.features().size(); index++) {
            Feature feature = expected.features().get(index);
            String[] fields = lines.get(index + 1).split("\t", -1);
            String where = feature.name() + ": ";
            if (!fields[0].equals(feature.name())
                    || !fields[1].equals(Boolean.toString(feature.name().equals("m0")))) {
                wrong.add(where + fields[0] + " " + fields[1]);
            }
            for (int split = 0; split < splits; split++) {
                near(
                        fields[2 + split],
                        feature.normalised().get(split),
                        where + "normalised",
                        wrong);
                near(
                        fields[2 + splits + 5 + split],
                        feature.raw().get(split),
                        where + "raw",
                        wrong);
            }
            int at = 2 + splits;
            near(fields[at], feature.meanSigned(), where + "mean_signed", wrong);
            near(fields[at + 1], feature.meanAbsolute(), where + "mean_absolute", wrong);
            near(fields[at + 2], feature.deviation(), where + "standard_deviation", wrong);
            if (!fields[at + 3].equals(feature.verdictWords())) {
                wrong.add(where + "sign_consistency " + fields[at + 3]);
            }
            if (!fields[at + 4].equals(feature.rank())) {
                wrong.add(where + "rank " + fields[at + 4]);
            }
        }
        assertEquals(List.of(), wrong, "exported values that differ from the independent values");
    }

    private static void near(String written, BigDecimal exact, String where, List<String> wrong) {
        double difference = Math.abs(Double.parseDouble(written) - exact.doubleValue());
        if (!(difference <= TOLERANCE)) {
            wrong.add(where + " written " + written + ", expected " + exact.toPlainString());
        }
    }

    // ===================================================================== independent ====

    /**
     * One feature's values, computed here.
     *
     * @param name its name
     * @param normalised each split's normalised weight, exact
     * @param raw each split's raw weight, exact
     * @param meanSigned the mean
     * @param meanAbsolute the mean of the absolute values
     * @param deviation the population standard deviation
     * @param positive splits above 0
     * @param negative splits below 0
     * @param zero splits at 0
     * @param rank the competition rank by mean absolute weight, empty for {@code m0}
     */
    private record Feature(
            String name,
            List<BigDecimal> normalised,
            List<BigDecimal> raw,
            BigDecimal meanSigned,
            BigDecimal meanAbsolute,
            BigDecimal deviation,
            int positive,
            int negative,
            int zero,
            String rank) {

        String verdict() {
            int n = normalised.size();
            return positive == n
                    ? "ALL_POSITIVE"
                    : negative == n ? "ALL_NEGATIVE" : zero == n ? "ALL_ZERO" : "MIXED";
        }

        String verdictWords() {
            return verdict().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        }

        String signText() {
            return verdictWords()
                    + " ("
                    + positive
                    + " positive, "
                    + negative
                    + " negative, "
                    + zero
                    + " zero)";
        }
    }

    /**
     * A weights file's values, computed independently of {@code org.cometgui.results}.
     *
     * @param splits the number of splits in the file
     * @param features its features, in the file's order
     */
    private record Independent(int splits, List<Feature> features) {

        static Independent of(Path file) throws IOException {
            List<String> lines =
                    Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                            .filter(line -> !line.startsWith("#"))
                            .toList();
            if (lines.isEmpty() || lines.size() % 3 != 0) {
                return fail(file + " is not whole splits of three lines");
            }
            int splits = lines.size() / 3;
            List<String> names = Arrays.asList(lines.get(0).split("\t", -1));
            List<List<BigDecimal>> normalised = new ArrayList<>();
            List<List<BigDecimal>> raw = new ArrayList<>();
            for (int split = 0; split < splits; split++) {
                assertEquals(names, Arrays.asList(lines.get(3 * split).split("\t", -1)));
                normalised.add(numbers(lines.get(3 * split + 1)));
                raw.add(numbers(lines.get(3 * split + 2)));
            }
            MathContext context = MathContext.DECIMAL128;
            BigDecimal n = new BigDecimal(splits);
            List<Feature> unranked = new ArrayList<>();
            for (int index = 0; index < names.size(); index++) {
                List<BigDecimal> weights = new ArrayList<>();
                List<BigDecimal> raws = new ArrayList<>();
                BigDecimal sum = BigDecimal.ZERO;
                BigDecimal absolute = BigDecimal.ZERO;
                int positive = 0;
                int negative = 0;
                for (int split = 0; split < splits; split++) {
                    BigDecimal weight = normalised.get(split).get(index);
                    weights.add(weight);
                    raws.add(raw.get(split).get(index));
                    sum = sum.add(weight);
                    absolute = absolute.add(weight.abs());
                    positive += weight.signum() > 0 ? 1 : 0;
                    negative += weight.signum() < 0 ? 1 : 0;
                }
                BigDecimal mean = sum.divide(n, context);
                BigDecimal squares = BigDecimal.ZERO;
                for (BigDecimal weight : weights) {
                    BigDecimal off = weight.subtract(mean);
                    squares = squares.add(off.multiply(off));
                }
                BigDecimal deviation = squares.divide(n, context).sqrt(new MathContext(40));
                unranked.add(
                        new Feature(
                                names.get(index),
                                weights,
                                raws,
                                mean,
                                absolute.divide(n, context),
                                deviation,
                                positive,
                                negative,
                                splits - positive - negative,
                                ""));
            }
            List<Feature> features = new ArrayList<>();
            for (Feature feature : unranked) {
                String rank = "";
                if (!feature.name().equals("m0")) {
                    int above = 0;
                    for (Feature other : unranked) {
                        if (!other.name().equals("m0")
                                && other.meanAbsolute().compareTo(feature.meanAbsolute()) > 0) {
                            above++;
                        }
                    }
                    rank = Integer.toString(above + 1);
                }
                features.add(
                        new Feature(
                                feature.name(),
                                feature.normalised(),
                                feature.raw(),
                                feature.meanSigned(),
                                feature.meanAbsolute(),
                                feature.deviation(),
                                feature.positive(),
                                feature.negative(),
                                feature.zero(),
                                rank));
            }
            return new Independent(splits, List.copyOf(features));
        }

        private static List<BigDecimal> numbers(String line) {
            return Arrays.stream(line.split("\t", -1)).map(BigDecimal::new).toList();
        }
    }

    /** The computation here agrees with unit 4's Python numbers, within the tolerance. */
    private static void assertAgrees(String pins, Independent computed) {
        List<String> lines = pins.lines().filter(line -> !line.isBlank()).toList();
        assertEquals(lines.size(), computed.features().size(), "the features pinned");
        List<String> wrong = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String[] pin = lines.get(index).trim().split(" ");
            Feature feature = computed.features().get(index);
            String counts = feature.positive() + "/" + feature.negative() + "/" + feature.zero();
            String rank = feature.rank().isEmpty() ? "-" : feature.rank();
            if (!pin[0].equals(feature.name())
                    || !pin[1].equals(rank)
                    || !pin[2].equals(feature.verdict())
                    || !pin[3].equals(counts)) {
                wrong.add(String.join(" ", pin) + " but computed " + feature);
            }
            near(pin[4], feature.meanSigned(), pin[0] + " mean", wrong);
            near(pin[5], feature.meanAbsolute(), pin[0] + " mean absolute", wrong);
            near(pin[6], feature.deviation(), pin[0] + " deviation", wrong);
        }
        assertEquals(List.of(), wrong, "the two independent computations disagree");
    }

    private static RunLayout run(
            ProjectLayout project, Path root, String runId, String created, Path weights)
            throws IOException {
        return ResultRuns.run(
                project,
                runId,
                Instant.parse(created),
                List.of(root.resolve("data/sample_A.mzML")),
                Map.of(
                        TableKind.TARGET_PSMS,
                        ResultRuns.fixture(
                                ResultRuns.PSMS_UNKNOWN_Q, root.resolve(runId + "/psms.tsv"))),
                Optional.of(weights));
    }
}
