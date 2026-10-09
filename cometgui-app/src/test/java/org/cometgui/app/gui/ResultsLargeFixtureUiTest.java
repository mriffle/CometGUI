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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javafx.collections.ListChangeListener;
import javafx.scene.control.TableView;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.InstalledComet;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 10 exit-gate item 6, the interface's half ({@code R-RES-03}, {@code AC-RES-10}, design
 * decision P10-11): the 1 000 000-row large fixture, as a run's target PSM table, opens in the
 * Results section through the store factory -- which picks the disk store -- and its filters, sorts
 * and page moves each complete within a documented time, while the results table's items
 * <strong>never exceed one page</strong> and the heap grows by less than a documented budget.
 *
 * <p><strong>The fixture.</strong> {@code scratch/phase10/large/psms.tsv} (D-006: constructed by
 * {@code python3 scripts/fixtures/large-results-fixture.py}, never committed), held to its pinned
 * SHA-256 and copied into a constructed run's {@code outputs/percolator/}. Absent or different, the
 * test fails naming the command that makes it; it never skips. The expected counts are the
 * generator's manifest's ({@code manifest.json}, computed with Python's {@code decimal}), typed out
 * below for the four cutoffs the test sets: 0, 0.005, 0.01 and 1.
 *
 * <p><strong>Where the heap proof is.</strong> The hard proof that the store does not need the heap
 * is {@code DiskStoreBudgetTest}'s child JVM at {@code -Xmx64m} (unit 3), with a negative control
 * that dies of {@code OutOfMemoryError}. This test is the interface's corroboration: the test JVM's
 * heap is large, so it measures the used heap after a forced collection before the run is opened
 * and after every action, and holds the growth to {@link #HEAP_GROWTH_BUDGET_MB}. The table held in
 * memory would cost about 670 MB (unit 2's measurement); a list of every passing row about 150 MB.
 *
 * <p><strong>Budgets</strong>, each at least the model's own budget for the same work in {@code
 * DiskStoreBudgetTest} (cold open 8 s, counts 1 s, numeric sort 3 s, text sort 10 s), because each
 * action here is that work plus a page built and applied on the JavaFX thread. Measured on the
 * phase 10 host (2026-10-09, 64 cores, Linux, JDK 25, headless Monocle, warm page cache), from the
 * robot's first event to the answer on screen: open 3.4 s (the cold open: hash, index, map); a
 * filter change 0.7-1.0 s (most of it typing the cutoff); the score sort 1.3 s on first use and 0.3
 * s reversed; the peptide sort 3.4 s; a page move 0.4-0.6 s. Each budget is three to five times its
 * measurement and at least the model's: open {@link #OPEN_BUDGET}, filter {@link #FILTER_BUDGET},
 * numeric sort {@link #NUMERIC_SORT_BUDGET}, text sort {@link #TEXT_SORT_BUDGET}, page {@link
 * #PAGE_BUDGET}. The used heap grew by 3 MB across the whole sequence. Every run prints its
 * measurements. The class takes about 25 s, most of it copying and hashing the 147 MB table.
 */
class ResultsLargeFixtureUiTest {

    /** The large fixture, relative to the repository root. */
    static final String LARGE_PSMS = "scratch/phase10/large/psms.tsv";

    /** Its SHA-256, as unit 1's manifest and every earlier test of it pins it. */
    static final String LARGE_PSMS_SHA256 =
            "4f7aaecd0164bc22be51de1c60dfed3e70ddf65a4283f38a1912c76ece8cfcf4";

    /** The command that makes it. */
    static final String REMAKE = "python3 scripts/fixtures/large-results-fixture.py";

    /** Opening the run: listing, hashing the table, building and mapping its index, one page. */
    static final Duration OPEN_BUDGET = Duration.ofSeconds(15);

    /** One filter change: the counts and the first page. */
    static final Duration FILTER_BUDGET = Duration.ofSeconds(4);

    /** A numeric sort, its sort file built on first use, and the first page. */
    static final Duration NUMERIC_SORT_BUDGET = Duration.ofSeconds(6);

    /** A text sort, its external merge sort on first use, and the first page. */
    static final Duration TEXT_SORT_BUDGET = Duration.ofSeconds(15);

    /** One page move. */
    static final Duration PAGE_BUDGET = Duration.ofMillis(2500);

    /** The most the used heap may grow, after a forced collection, from before the open. */
    static final long HEAP_GROWTH_BUDGET_MB = 64;

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-09T00:00:00Z"));

    private static final String RUN = "run-large-fixture";

    /** The manifest's counts at each cutoff the test sets: total, passing, failing, unknown. */
    private static final Map<String, List<String>> MANIFEST =
            Map.of(
                    "0", List.of("1000000", "5003", "993519", "1478"),
                    "0.005", List.of("1000000", "175162", "823360", "1478"),
                    "0.01", List.of("1000000", "219777", "778745", "1478"),
                    "1", List.of("1000000", "998522", "0", "1478"));

    @TempDir private static Path scratch;

    private static Path fixture;

    private static ProjectLayout project;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static List<Throwable> listenerFailures;

    /** The most items the results table ever held, recorded on every change of its items. */
    private static final AtomicInteger MOST_ITEMS = new AtomicInteger();

    private final Map<String, Long> measured = new LinkedHashMap<>();

    @BeforeAll
    static void launch() throws IOException {
        fixture = ArtefactMirror.repositoryRoot().resolve(LARGE_PSMS);
        if (!Files.isRegularFile(fixture)) {
            fail(
                    "the large fixture "
                            + fixture
                            + " is absent. Make it with: "
                            + REMAKE
                            + " (about 20 s). This test fails rather than skips: a gate that stops"
                            + " reading the fixture stops proving anything.");
        }
        assertEquals(
                LARGE_PSMS_SHA256,
                ResultRuns.sha256(fixture),
                "the large fixture's SHA-256; remake it with: " + REMAKE);
        Path root = scratch.toRealPath();
        project = ResultRuns.project(root.resolve("project"));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        listenerFailures = ResultsSection.recordListenerFailures(driver);
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
                ResultsSection.OPEN_BOUND);
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName(
            "gate 6: the 1 000 000-row run opens on disk; filters at 0, 0.005, 0.01 and 1 show the"
                    + " manifest's counts; sorts and page moves; never more than one page of items;"
                    + " every action within its budget; heap growth within budget")
    void theLargeFixture() throws IOException {
        long heapBefore = usedHeapAfterCollection();
        RunLayout run =
                ResultRuns.run(
                        project,
                        RUN,
                        Instant.parse("2026-10-09T09:00:00Z"),
                        List.of(
                                project.root().resolve("data/sample_A.mzML"),
                                project.root().resolve("data/sample_B.mzML")),
                        Map.of(TableKind.TARGET_PSMS, fixture),
                        Optional.empty());

        long start = System.nanoTime();
        driver.clickOn("results-refresh");
        ResultsSection.awaitOpened(driver, RUN, OPEN_BUDGET.multipliedBy(2));
        ResultsSection.settle(driver, OPEN_BUDGET.multipliedBy(2));
        record("open", start, OPEN_BUDGET);
        assertOnePage("after the open", 200);
        assertEquals(MANIFEST.get("0.01"), ResultsSection.counts(driver), "the counts at 0.01");
        try (Stream<Path> index = Files.list(run.resultIndexDirectory())) {
            assertTrue(
                    index.findAny().isPresent(),
                    "the store factory chose the disk store: its index is in results/index/");
        }
        assertEquals("Rows 1 to 200 of 219777 (page 1 of 1099)", driver.textOf("results-page"));

        for (String cutoff : List.of("0", "0.005", "1", "0.01")) {
            start = System.nanoTime();
            enter(driver, "results-psm-filter", cutoff);
            ResultsSection.settle(driver, FILTER_BUDGET.multipliedBy(4));
            record("filter " + cutoff, start, FILTER_BUDGET);
            assertEquals(MANIFEST.get(cutoff), ResultsSection.counts(driver), "at " + cutoff);
            assertOnePage("at " + cutoff, 200);
        }

        sort("results-sort-score", "sort by score, ascending", NUMERIC_SORT_BUDGET);
        sort("results-sort-score", "sort by score, descending", NUMERIC_SORT_BUDGET);
        sort("results-sort-peptide", "sort by peptide, ascending", TEXT_SORT_BUDGET);

        page("results-next-page", "Rows 201 to 400 of 219777 (page 2 of 1099)", 200);
        page("results-last-page", "Rows 219601 to 219777 of 219777 (page 1099 of 1099)", 177);
        page("results-previous-page", "Rows 219401 to 219600 of 219777 (page 1098 of 1099)", 200);
        page("results-first-page", "Rows 1 to 200 of 219777 (page 1 of 1099)", 200);

        long heapAfter = usedHeapAfterCollection();
        long growthMb = (heapAfter - heapBefore) / (1024 * 1024);
        System.out.println(
                "ResultsLargeFixtureUiTest measured (ms): "
                        + measured
                        + "; used heap before "
                        + heapBefore / (1024 * 1024)
                        + " MB, after "
                        + heapAfter / (1024 * 1024)
                        + " MB, growth "
                        + growthMb
                        + " MB; most items ever held "
                        + MOST_ITEMS.get());
        assertAll(
                () ->
                        assertTrue(
                                growthMb <= HEAP_GROWTH_BUDGET_MB,
                                "the used heap grew by "
                                        + growthMb
                                        + " MB, over its budget of "
                                        + HEAP_GROWTH_BUDGET_MB
                                        + " MB"),
                () ->
                        assertTrue(
                                MOST_ITEMS.get() <= ResultsSection.PAGE_SIZE,
                                "the results table once held "
                                        + MOST_ITEMS.get()
                                        + " items, more than one page of "
                                        + ResultsSection.PAGE_SIZE),
                () ->
                        assertEquals(
                                LARGE_PSMS_SHA256,
                                ResultRuns.sha256(RunResultFiles.table(run, TableKind.TARGET_PSMS)),
                                "the run's raw table is unchanged"),
                () -> ResultsSection.assertNoListenerFailed(listenerFailures));
    }

    private void sort(String heading, String what, Duration budget) {
        long start = System.nanoTime();
        driver.clickOn(heading);
        ResultsSection.settle(driver, budget.multipliedBy(3));
        record(what, start, budget);
        assertEquals("Rows 1 to 200 of 219777 (page 1 of 1099)", driver.textOf("results-page"));
        assertOnePage(what, 200);
    }

    private void page(String action, String position, int rows) {
        long start = System.nanoTime();
        driver.clickOn(action);
        ResultsSection.settle(driver, PAGE_BUDGET.multipliedBy(4));
        RunSection.awaitText(driver, "results-page", position::equals, PAGE_BUDGET.multipliedBy(4));
        record(action, start, PAGE_BUDGET);
        assertOnePage(action, rows);
    }

    private void record(String what, long startNanos, Duration budget) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        measured.put(what, millis);
        assertTrue(
                millis <= budget.toMillis(),
                what + " took " + millis + " ms, over its budget of " + budget.toMillis() + " ms");
    }

    private static void assertOnePage(String when, int expected) {
        int items = ResultsSection.items(driver);
        assertTrue(
                items <= ResultsSection.PAGE_SIZE,
                when
                        + ": the results table holds "
                        + items
                        + " items, more than one page of "
                        + ResultsSection.PAGE_SIZE);
        assertEquals(expected, items, when + ": the page's rows");
    }

    private static long usedHeapAfterCollection() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        // the management bean's collection, not System.gc(), which SpotBugs reports as DM_GC
        for (int round = 0; round < 3; round++) {
            memory.gc();
        }
        return memory.getHeapMemoryUsage().getUsed();
    }
}
