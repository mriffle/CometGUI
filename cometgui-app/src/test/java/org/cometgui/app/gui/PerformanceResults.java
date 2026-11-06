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

import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.performance.PerfRecorder;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Performance (c) and (g): rendering and filtering large PSM and peptide result sets in the Results
 * section, and the peak heap the interface uses for them ({@code AC-RES-10}: "Result tables remain
 * responsive on the large performance fixture within the documented heap budget").
 *
 * <p><strong>The fixture</strong> is Phase 10's large performance fixture -- 1 000 000 target PSMs
 * and 400 000 target peptides, synthetic, made by {@code scripts/fixtures/large-results-fixture.py}
 * and never committed -- in the directory the runner names ({@code results.fixture}), each table
 * held to its pinned SHA-256. Each repeat is a <em>fresh</em> constructed run holding copies of
 * both tables, so every open is cold (hash, index, map), as a scientist's first look at a run is.
 *
 * <p><strong>What is timed</strong>, through the real composition root and the robot, from the
 * first event to the answer on screen, exactly as {@link ResultsLargeFixtureUiTest} times its
 * budgets: choosing the run ({@code results-open}); four PSM filter changes ({@code
 * results-psm-filter}); a numeric and a text sort, each first use ({@code
 * results-psm-sort-numeric}, {@code results-psm-sort-text}); four page moves ({@code
 * results-psm-page}); showing the peptide table ({@code results-peptide-show}); four peptide filter
 * changes ({@code results-peptide-filter}). Every count shown is checked against the generator's
 * manifest, every page position against the arithmetic, and the table never holds more than one
 * page.
 *
 * <p><strong>The heap</strong> ({@code gui-heap-peak}): the used heap after a forced collection is
 * read before the first run is opened and after every action; a repeat's sample is the most it
 * stood above that baseline during the repeat, in MiB -- the retained heap at its peak, which the
 * documented budget ({@link ResultsLargeFixtureUiTest#HEAP_GROWTH_BUDGET_MB}) bounds.
 *
 * <p>Opt-in (P15-5). The runner opens a warm-up run first, which the application shows at start and
 * which is not timed: it is the one open that also loads the classes.
 */
@EnabledIfSystemProperty(
        named = PerfRecorder.ENABLE,
        matches = "true",
        disabledReason = PerfRecorder.DISABLED)
class PerformanceResults {

    static final String OPEN = "results-open";

    static final String PSM_FILTER = "results-psm-filter";

    static final String SORT_NUMERIC = "results-psm-sort-numeric";

    static final String SORT_TEXT = "results-psm-sort-text";

    static final String PAGE = "results-psm-page";

    static final String PEPTIDE_SHOW = "results-peptide-show";

    static final String PEPTIDE_FILTER = "results-peptide-filter";

    static final String HEAP = "gui-heap-peak";

    /** Every measurement this class makes; the ones the plan names are written. */
    static final List<String> IDS =
            List.of(
                    OPEN,
                    PSM_FILTER,
                    SORT_NUMERIC,
                    SORT_TEXT,
                    PAGE,
                    PEPTIDE_SHOW,
                    PEPTIDE_FILTER,
                    HEAP);

    /** How long one action may take before the run fails it. A bound, never a delay. */
    private static final Duration BOUND = Duration.ofMinutes(5);

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-performance", "unknown", Instant.parse("2026-11-06T00:00:00Z"));

    private static final List<String> CUTOFFS = List.of("0", "0.005", "1", "0.01");

    private static final double MIB = 1024.0 * 1024.0;

    private static final AtomicInteger MOST_ITEMS = new AtomicInteger();

    private Path work;

    private ParameterEditorApp app;

    private final Map<String, PerfRecorder.Measurement> measurements = new LinkedHashMap<>();

    private final List<String> wrong = new ArrayList<>();

    private final List<String> openParts = new ArrayList<>();

    private FxUiDriver driver;

    @AfterEach
    void cleanUp() throws IOException {
        if (app != null) {
            app.stop();
        }
        PerfRecorder.delete(work);
    }

    @Test
    @DisplayName("the large fixture's PSM and peptide tables opened, filtered, sorted and paged")
    void largeResultTables() throws IOException {
        OptionalInt repeats = OptionalInt.empty();
        for (String id : IDS) {
            OptionalInt planned = PerfRecorder.repeats(id);
            if (planned.isPresent()) {
                if (repeats.isPresent() && repeats.getAsInt() != planned.getAsInt()) {
                    fail("every results measurement repeats as often: " + IDS);
                }
                repeats = planned;
            }
        }
        if (repeats.isEmpty()) {
            return;
        }
        Path fixture = Path.of(PerfRecorder.parameter("results.fixture"));
        Path psms = fixture.resolve("psms.tsv");
        Path peptides = fixture.resolve("peptides.tsv");
        assertEquals(
                ResultsLargeFixtureUiTest.LARGE_PSMS_SHA256,
                ResultRuns.sha256(psms),
                "the large fixture's PSM table; remake it with "
                        + ResultsLargeFixtureUiTest.REMAKE);
        assertEquals(
                PerfRecorder.parameter("results.peptides.sha256"),
                ResultRuns.sha256(peptides),
                "the large fixture's peptide table, against its manifest");
        for (String id : IDS) {
            measurements.put(id, PerfRecorder.start(id, HEAP.equals(id) ? "MiB" : "ms"));
        }

        work = PerfRecorder.work("results");
        Path root = work.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        List<Throwable> listenerFailures = ResultsSection.recordListenerFailures(driver);
        TableView<?> table = (TableView<?>) driver.node("results-table");
        driver.onFxThread(
                () ->
                        table.getItems()
                                .addListener(
                                        (ListChangeListener<Object>)
                                                change ->
                                                        MOST_ITEMS.accumulateAndGet(
                                                                change.getList().size(),
                                                                Math::max)));
        driver.clickOn("nav-results");
        RunSection.awaitText(
                driver,
                "results-readiness",
                text -> text.startsWith("No run with results yet."),
                BOUND);

        int count = repeats.getAsInt();
        Instant first = Instant.parse("2026-11-06T09:00:00Z");
        for (int run = 0; run <= count; run++) {
            // run 0 is the warm-up, the most recent, so the application opens it on refresh
            ResultRuns.run(
                    project,
                    runId(run),
                    run == 0 ? first.plusSeconds(3600) : first.plusSeconds(run * 60L),
                    List.of(
                            project.root().resolve("data/sample_A.mzML"),
                            project.root().resolve("data/sample_B.mzML")),
                    Map.of(TableKind.TARGET_PSMS, psms, TableKind.TARGET_PEPTIDES, peptides),
                    Optional.empty());
        }
        // The copies are written back to disk before anything is timed: an open must not be
        // measured against the kernel flushing 1.2 GB of this set-up (measured: the first open
        // 15.6 s while the others took 0.9 s, until this was added).
        long synced = PerfRecorder.syncTree(project.root());
        measurements.get(OPEN).evidence("bytesSyncedBeforeTiming", synced);
        long baseline = PerfRecorder.usedHeapAfterCollection();
        driver.clickOn("results-refresh");
        ResultsSection.awaitOpened(driver, runId(0), BOUND);
        ResultsSection.settle(driver, BOUND);

        for (int run = 1; run <= count; run++) {
            double peak = repeat(run, baseline);
            measurements.get(HEAP).sample(peak);
        }

        measurements.get(HEAP).evidence("baselineMiB", Math.round(baseline / MIB));
        measurements
                .get(HEAP)
                .evidence("documentedBudgetMiB", ResultsLargeFixtureUiTest.HEAP_GROWTH_BUDGET_MB);
        measurements.get(OPEN).evidence("psmRows", 1_000_000).evidence("peptideRows", 400_000);
        measurements.get(OPEN).evidence("eachOpen", openParts);
        for (PerfRecorder.Measurement measurement : measurements.values()) {
            measurement.check(
                    "every count, page position and table shown was the expected one",
                    wrong.isEmpty(),
                    wrong.isEmpty() ? "all as expected" : String.join("; ", wrong));
            measurement.check(
                    "the results table never held more than one page",
                    MOST_ITEMS.get() <= ResultsSection.PAGE_SIZE,
                    "most items held "
                            + MOST_ITEMS.get()
                            + ", a page is "
                            + ResultsSection.PAGE_SIZE);
        }
        ResultsSection.assertNoListenerFailed(listenerFailures);
        for (Map.Entry<String, PerfRecorder.Measurement> measurement : measurements.entrySet()) {
            if (PerfRecorder.repeats(measurement.getKey()).isPresent()) {
                measurement.getValue().finish();
            }
        }
    }

    /** One repeat over a fresh run; returns the peak retained heap above the baseline, in MiB. */
    private double repeat(int run, long baseline) throws IOException {
        long peak = 0;
        String id = runId(run);

        long start = System.nanoTime();
        open(id);
        record(OPEN, start);
        expect("open " + id, "Rows 1 to 200 of 219777 (page 1 of 1099)", page());
        expectCounts("PSMs at 0.01 after the open", "results.psms.counts.0.01");
        peak = Math.max(peak, PerfRecorder.usedHeapAfterCollection() - baseline);

        for (String cutoff : CUTOFFS) {
            start = System.nanoTime();
            enter(driver, "results-psm-filter", cutoff);
            ResultsSection.settle(driver, BOUND);
            record(PSM_FILTER, start);
            expectCounts("PSMs at " + cutoff, "results.psms.counts." + cutoff);
            peak = Math.max(peak, PerfRecorder.usedHeapAfterCollection() - baseline);
        }

        start = System.nanoTime();
        driver.clickOn("results-sort-score");
        ResultsSection.settle(driver, BOUND);
        record(SORT_NUMERIC, start);
        expect("sort by score", "Rows 1 to 200 of 219777 (page 1 of 1099)", page());
        peak = Math.max(peak, PerfRecorder.usedHeapAfterCollection() - baseline);

        start = System.nanoTime();
        driver.clickOn("results-sort-peptide");
        ResultsSection.settle(driver, BOUND);
        record(SORT_TEXT, start);
        expect("sort by peptide", "Rows 1 to 200 of 219777 (page 1 of 1099)", page());
        peak = Math.max(peak, PerfRecorder.usedHeapAfterCollection() - baseline);

        for (String[] move :
                List.of(
                        new String[] {
                            "results-next-page", "Rows 201 to 400 of 219777 (page 2 of 1099)"
                        },
                        new String[] {
                            "results-last-page",
                            "Rows 219601 to 219777 of 219777 (page 1099 of 1099)"
                        },
                        new String[] {
                            "results-previous-page",
                            "Rows 219401 to 219600 of 219777 (page 1098 of 1099)"
                        },
                        new String[] {
                            "results-first-page", "Rows 1 to 200 of 219777 (page 1 of 1099)"
                        })) {
            start = System.nanoTime();
            driver.clickOn(move[0]);
            ResultsSection.settle(driver, BOUND);
            RunSection.awaitText(driver, "results-page", move[1]::equals, BOUND);
            record(PAGE, start);
            peak = Math.max(peak, PerfRecorder.usedHeapAfterCollection() - baseline);
        }

        start = System.nanoTime();
        ParameterEditorApp.choose(driver, "results-table-choice", "Target peptides");
        ResultsSection.settle(driver, BOUND);
        record(PEPTIDE_SHOW, start);
        expect("the peptide table", "Rows 1 to 200 of 88111 (page 1 of 441)", page());
        expectCounts("peptides at 0.01", "results.peptides.counts.0.01");
        peak = Math.max(peak, PerfRecorder.usedHeapAfterCollection() - baseline);

        for (String cutoff : CUTOFFS) {
            start = System.nanoTime();
            enter(driver, "results-peptide-filter", cutoff);
            ResultsSection.settle(driver, BOUND);
            record(PEPTIDE_FILTER, start);
            expectCounts("peptides at " + cutoff, "results.peptides.counts." + cutoff);
            peak = Math.max(peak, PerfRecorder.usedHeapAfterCollection() - baseline);
        }
        return peak / MIB;
    }

    private void open(String id) {
        List<String> offered = ParameterEditorApp.comboItems(driver, "results-run");
        String item =
                offered.stream()
                        .filter(label -> label.startsWith(id + " -- "))
                        .findFirst()
                        .orElseGet(() -> fail("the run selector offers no " + id + ": " + offered));
        // Selected directly, as a click on the item selects it: the keyboard would walk through
        // every run between, opening each -- so that the run timed here was already indexed
        // (measured: 0.85 s "opens" that were warm, and one 15 s open that was five).
        ComboBox<?> selector = (ComboBox<?>) driver.node("results-run");
        int index = offered.indexOf(item);
        long chosen = System.nanoTime();
        driver.onFxThread(() -> selector.getSelectionModel().select(index));
        double choosing = PerfRecorder.millisSince(chosen);
        ResultsSection.awaitOpened(driver, id, BOUND);
        double opened = PerfRecorder.millisSince(chosen);
        ResultsSection.settle(driver, BOUND);
        openParts.add(
                String.format(
                        java.util.Locale.ROOT,
                        "%s: chosen %.0f ms, shown %.0f ms, settled %.0f ms",
                        id,
                        choosing,
                        opened,
                        PerfRecorder.millisSince(chosen)));
    }

    private void record(String id, long startNanos) {
        measurements.get(id).sample(PerfRecorder.millisSince(startNanos));
    }

    private String page() {
        return driver.textOf("results-page");
    }

    private void expect(String what, String expected, String observed) {
        if (!expected.equals(observed)) {
            wrong.add(what + ": expected <" + expected + "> but was <" + observed + ">");
        }
    }

    private void expectCounts(String what, String parameter) throws IOException {
        List<String> expected = List.of(PerfRecorder.parameter(parameter).split(",", -1));
        expect(what, String.valueOf(expected), String.valueOf(ResultsSection.counts(driver)));
    }

    private static String runId(int run) {
        return String.format(java.util.Locale.ROOT, "perf-results-%02d", run);
    }
}
