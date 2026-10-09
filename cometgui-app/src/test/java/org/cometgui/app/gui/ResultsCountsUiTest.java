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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.IndependentCounts;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.K562Outputs;
import org.cometgui.app.testing.ResultRuns;
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
 * Phase 10 exit-gate item 3 on screen ({@code R-RES-01}, {@code AC-RES-01}..{@code 03}): the counts
 * the Results section shows -- total, passing, failing and unknown q-value, for the target PSMs,
 * target peptides, decoy PSMs and decoy peptides -- equal counts computed independently from the
 * raw Percolator files, at the cutoffs 0, 0.005, 0.01, 0.05 and 1 and more, set by typing into the
 * section's own PSM and peptide filter fields.
 *
 * <p><strong>Two runs, both of real Percolator 3.07.1 output</strong>, constructed through the run
 * store ({@link ResultRuns}) with the tables put where {@code run-percolator} writes them:
 *
 * <ul>
 *   <li>{@code run-k562}: Phase 00's search of the two {@code D-006} K562 files ({@link
 *       K562Outputs}: 3897 target PSMs, 2773 decoy PSMs, 2985 target peptides, 2359 decoy
 *       peptides), copied from {@code scratch/} and held to their SHA-256s -- the test fails, never
 *       skips, without them. The expected counts are, for each table and cutoff, <em>both</em> the
 *       {@code awk}-pinned counts of {@code real-k562/PROVENANCE.txt} (typed out in {@link
 *       K562Outputs#AWK}) <em>and</em> {@link IndependentCounts} run on the run's own copy of the
 *       bytes. Two more cutoffs are checked against the counter alone: {@code 0.00112905}, the
 *       smallest target PSM q-value, so that rows lie exactly on the cutoff, and {@code 0.680615},
 *       the largest.
 *   <li>{@code run-synthetic}: Percolator 3.07.1 over CometGUI's own synthetic 64 + 64 PIN (the
 *       checked-in {@code real-3.07.1-psms.tsv} as the target PSM and target peptide tables, which
 *       Percolator wrote byte-identical, and {@code real-3.07.1-decoy-psms.tsv} as both decoy
 *       tables, likewise). 17 target rows have the q-value {@code 0.0588235} exactly, so that
 *       cutoff is inclusive on real data. Expected counts typed out from this {@code awk} (mawk
 *       1.3.4, run by the unit 9 agent on 2026-10-09 over {@code cometgui-results/src/test/
 *       resources/org/cometgui/results/parser/real/percolator-3.07.1/}), and the counter:
 * </ul>
 *
 * <pre>
 *   LC_ALL=C awk -F'\t' -v c="$c" 'NR&gt;1 { t++; q=$3;
 *     if (q !~ /^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?$/ ||
 *         q+0 &lt; 0 || q+0 &gt; 1) u++;
 *     else if (q+0 &lt;= c+0) p++; else f++ }
 *     END { printf "%d %d %d %d\n", t, p, f, u }' psms.tsv
 *
 *   c          psms.tsv       decoy-psms.tsv
 *   0          64 0 64 0      64 0 64 0
 *   0.005      64 0 64 0      64 0 64 0
 *   0.01       64 0 64 0      64 0 64 0
 *   0.05       64 0 64 0      64 0 64 0
 *   0.0588235  64 17 47 0     64 0 64 0
 *   1          64 64 0 0      64 64 0 0
 * </pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResultsCountsUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-09T00:00:00Z"));

    private static final String K562 = "run-k562";

    private static final String SYNTHETIC = "run-synthetic";

    /** The selector's label of each table, typed out. */
    private static final Map<TableKind, String> LABELS =
            Map.of(
                    TableKind.TARGET_PSMS, "Target PSMs",
                    TableKind.TARGET_PEPTIDES, "Target peptides",
                    TableKind.DECOY_PSMS, "Decoy PSMs",
                    TableKind.DECOY_PEPTIDES, "Decoy peptides");

    /** What the counts sentence calls each table's rows, typed out. */
    private static final Map<TableKind, String> ROWS =
            Map.of(
                    TableKind.TARGET_PSMS, "target PSMs",
                    TableKind.TARGET_PEPTIDES, "target peptides",
                    TableKind.DECOY_PSMS, "decoy PSMs",
                    TableKind.DECOY_PEPTIDES, "decoy peptides");

    private static final List<TableKind> ORDER =
            List.of(
                    TableKind.TARGET_PSMS,
                    TableKind.TARGET_PEPTIDES,
                    TableKind.DECOY_PSMS,
                    TableKind.DECOY_PEPTIDES);

    /** {@code run-synthetic}'s awk counts, typed out from the table above: target, decoy. */
    private static final Map<String, List<String>> SYNTHETIC_AWK = new LinkedHashMap<>();

    static {
        SYNTHETIC_AWK.put("0", List.of("64 0 64 0", "64 0 64 0"));
        SYNTHETIC_AWK.put("0.005", List.of("64 0 64 0", "64 0 64 0"));
        SYNTHETIC_AWK.put("0.01", List.of("64 0 64 0", "64 0 64 0"));
        SYNTHETIC_AWK.put("0.05", List.of("64 0 64 0", "64 0 64 0"));
        SYNTHETIC_AWK.put("0.0588235", List.of("64 17 47 0", "64 0 64 0"));
        SYNTHETIC_AWK.put("1", List.of("64 64 0 0", "64 64 0 0"));
    }

    @TempDir private static Path scratch;

    private static RunLayout k562;

    private static RunLayout synthetic;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static List<Throwable> listenerFailures;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        k562 =
                ResultRuns.run(
                        project,
                        K562,
                        Instant.parse("2026-10-09T08:00:00Z"),
                        List.of(root.resolve("data/k562_3.mzML"), root.resolve("data/k562_4.mzML")),
                        K562Outputs.tables(root.resolve("k562")),
                        Optional.of(K562Outputs.weights(root.resolve("k562-weights"))));
        Path target =
                ResultRuns.fixture(ResultRuns.REAL_3071_PSMS, root.resolve("synthetic/psms.tsv"));
        Path decoy =
                ResultRuns.fixture(
                        ResultRuns.REAL_3071_DECOY_PSMS, root.resolve("synthetic/decoy.tsv"));
        synthetic =
                ResultRuns.run(
                        project,
                        SYNTHETIC,
                        Instant.parse("2026-10-09T07:00:00Z"),
                        List.of(root.resolve("data/synthetic.mzML")),
                        Map.of(
                                TableKind.TARGET_PSMS, target,
                                TableKind.TARGET_PEPTIDES, target,
                                TableKind.DECOY_PSMS, decoy,
                                TableKind.DECOY_PEPTIDES, decoy),
                        Optional.empty());
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        listenerFailures = ResultsSection.recordListenerFailures(driver);
        driver.clickOn("nav-results");
        // the most recent run is opened first
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
            "gate 3: the real K562 run's four tables at 0, 0.005, 0.01, 0.05 and 1 show the"
                    + " awk-pinned counts, and the independent counter's on the same bytes")
    void theK562RunAtThePinnedCutoffs() throws IOException {
        driver.clickOn("nav-results");
        ResultsSection.showRun(driver, K562);
        List<String> wrong = new ArrayList<>();
        for (String cutoff : K562Outputs.CUTOFFS) {
            ResultsSection.filters(driver, cutoff, cutoff);
            for (TableKind kind : ORDER) {
                List<String> pinned = K562Outputs.awk(kind, cutoff);
                List<String> counted =
                        IndependentCounts.counts(RunResultFiles.table(k562, kind), cutoff);
                assertEquals(pinned, counted, "the awk pin and the counter agree");
                compare(kind, cutoff, pinned, wrong);
            }
        }
        assertEquals(List.of(), wrong, "counts shown that differ from the independent counts");
    }

    @Test
    @Order(2)
    @DisplayName(
            "gate 3: the K562 run at its smallest and largest real q-values -- rows exactly on the"
                    + " cutoff pass -- shows the independent counter's counts")
    void theK562RunOnItsOwnQValues() throws IOException {
        driver.clickOn("nav-results");
        ResultsSection.showRun(driver, K562);
        List<String> wrong = new ArrayList<>();
        for (String cutoff : List.of("0.00112905", "0.680615")) {
            ResultsSection.filters(driver, cutoff, cutoff);
            for (TableKind kind : ORDER) {
                compare(
                        kind,
                        cutoff,
                        IndependentCounts.counts(RunResultFiles.table(k562, kind), cutoff),
                        wrong);
            }
        }
        assertEquals(List.of(), wrong, "counts shown that differ from the independent counts");
        List<String> atSmallest =
                IndependentCounts.counts(
                        RunResultFiles.table(k562, TableKind.TARGET_PSMS), "0.00112905");
        assertEquals(
                List.of("3897", "0", "3897", "0"),
                IndependentCounts.counts(
                        RunResultFiles.table(k562, TableKind.TARGET_PSMS), "0.00112904"),
                "just below the smallest q-value nothing passes");
        assertEquals("3897", atSmallest.get(0));
        assertNotEquals("0", atSmallest.get(1), "the rows at exactly the smallest q-value pass");
    }

    @Test
    @Order(3)
    @DisplayName(
            "gate 3: the real run over CometGUI's synthetic PIN, inclusive at 0.0588235, shows the"
                    + " awk counts and the counter's")
    void theSyntheticRun() throws IOException {
        driver.clickOn("nav-results");
        ResultsSection.showRun(driver, SYNTHETIC);
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, List<String>> pinned : SYNTHETIC_AWK.entrySet()) {
            String cutoff = pinned.getKey();
            ResultsSection.filters(driver, cutoff, cutoff);
            for (TableKind kind : ORDER) {
                List<String> awk =
                        List.of(pinned.getValue().get(kind.isDecoy() ? 1 : 0).split(" "));
                assertEquals(
                        awk,
                        IndependentCounts.counts(RunResultFiles.table(synthetic, kind), cutoff),
                        "the awk pin and the counter agree");
                compare(kind, cutoff, awk, wrong);
            }
        }
        assertEquals(List.of(), wrong, "counts shown that differ from the independent counts");
    }

    @Test
    @Order(4)
    @DisplayName("no JavaFX listener threw while the counts were read")
    void noListenerThrew() {
        ResultsSection.assertNoListenerFailed(listenerFailures);
    }

    /**
     * Shows a table and compares the four counts and the sentence with the expected ones; a
     * difference is added to {@code wrong}, so that one run of the test names every one.
     */
    private static void compare(
            TableKind kind, String cutoff, List<String> expected, List<String> wrong) {
        ResultsSection.showTable(driver, LABELS.get(kind));
        List<String> shown = ResultsSection.counts(driver);
        String sentence =
                ResultsSection.summary(
                        ROWS.get(kind),
                        cutoff,
                        Long.parseLong(expected.get(0)),
                        Long.parseLong(expected.get(1)),
                        Long.parseLong(expected.get(2)),
                        Long.parseLong(expected.get(3)));
        if (!shown.equals(expected)) {
            wrong.add(
                    LABELS.get(kind)
                            + " at "
                            + cutoff
                            + ": shown "
                            + shown
                            + ", expected "
                            + expected);
        }
        String said = driver.textOf("results-counts");
        if (!said.equals(sentence)) {
            wrong.add(LABELS.get(kind) + " at " + cutoff + ": said \"" + said + "\"");
        }
    }
}
