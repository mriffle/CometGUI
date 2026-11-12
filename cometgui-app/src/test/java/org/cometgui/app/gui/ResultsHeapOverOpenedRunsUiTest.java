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
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.control.ComboBox;
import javax.management.JMException;
import javax.management.ObjectName;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The heap the Results section retains does not grow with the runs opened in one session ({@code
 * R-RES-03}, {@code AC-RES-10}; Phase 15 unit 8's finding, traced and fixed in Phase 16 unit 3).
 *
 * <h2>What leaked</h2>
 *
 * <p>The results table rebuilds its column headers whenever its visible columns change, which every
 * opened run and every table chosen does. Each column's heading is a button this section keeps for
 * its lifetime, given to the table as the column's graphic; the header label a rebuild discards had
 * added a listener to that button's layout bounds and never removed it, so the button kept every
 * discarded header reachable. A heap dump taken after 150 opens of three runs with the real K562
 * tables held 1 280 {@code TableColumnHeader}s, 1 260 of them on one path: the section's heading
 * buttons, a button's {@code layoutBounds} listeners, a discarded {@code LabelSkin}, its {@code
 * Label}, that label's parent. There the live heap grew by about 168 kB an open, 20 MB over 120
 * opens, and never came back.
 *
 * <h2>What is measured, and why it does not depend on timing</h2>
 *
 * <p>The application is the real one over three constructed runs, opened in turn through the run
 * chooser {@value #WARM_UP} times to warm up and {@value #OPENS} times more. Before and after the
 * second stretch the JVM's own class histogram is taken ({@code GC.class_histogram}, which collects
 * the whole heap first and lists every live object): the bytes of all live objects, and the number
 * of live {@code TableColumnHeader}s. Both are counts of what the heap holds once everything
 * unreachable is gone, not of how long anything took, and each reading is the least of {@value
 * #READINGS} taken after consecutive opens, so that the cells a table happens to be showing at one
 * moment do not count as growth.
 *
 * <ul>
 *   <li>the live headers may not grow by more than {@value #HEADER_SLACK} -- two generations of the
 *       table's nine columns, for a rebuild caught half way. Measured on these runs: 20 then 20
 *       with the fix; 227 then 875 without it.
 *   <li>the live heap may not grow by more than {@value #HEAP_SLACK_MIB} MiB over the {@value
 *       #OPENS} opens. Measured on these runs: 0.4 MB with the fix, about 3 kB an open -- nearly
 *       half of it the toolkit's own short-circuit binding stubs on the columns' {@code resizable}
 *       and {@code visible} properties (about 30 an open, which the toolkit drops when those
 *       properties next change), a little of it the events this test's robot records, the rest
 *       inside the difference between two readings; 12.9 MB without the fix. In the K562 trace two
 *       readings of an unchanged section differed by up to 2.1 MB with the cells on screen, which
 *       is what the bound leaves room for.
 * </ul>
 */
class ResultsHeapOverOpenedRunsUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-11-12T00:00:00Z"));

    /** Opens before the first reading: every lazily made cache and skin exists by then. */
    static final int WARM_UP = 30;

    /** Opens between the two readings. */
    static final int OPENS = 120;

    /** Readings each figure is the least of, one after each of that many consecutive opens. */
    static final int READINGS = 3;

    /** How many more live column headers the second reading may show than the first. */
    static final int HEADER_SLACK = 18;

    /** How much more live heap the second reading may show than the first, in MiB. */
    static final int HEAP_SLACK_MIB = 6;

    private static final String HEADER = "javafx.scene.control.skin.TableColumnHeader";

    private static final List<String> RUNS = List.of("run-a", "run-b", "run-c");

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static List<Throwable> listenerFailures;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        Path target = ResultRuns.fixture(ResultRuns.REAL_3071_PSMS, root.resolve("in/psms.tsv"));
        Path decoy =
                ResultRuns.fixture(ResultRuns.REAL_3071_DECOY_PSMS, root.resolve("in/decoy.tsv"));
        Path weights = ResultRuns.fixture(ResultRuns.WEIGHTS_3071, root.resolve("in/weights.txt"));
        for (int index = 0; index < RUNS.size(); index++) {
            ResultRuns.run(
                    project,
                    RUNS.get(index),
                    Instant.parse("2026-11-12T08:00:00Z").minusSeconds(3600L * index),
                    List.of(root.resolve("data/sample_A.mzML"), root.resolve("data/sample_B.mzML")),
                    Map.of(
                            TableKind.TARGET_PSMS, target,
                            TableKind.TARGET_PEPTIDES, target,
                            TableKind.DECOY_PSMS, decoy),
                    Optional.of(weights));
        }
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        listenerFailures = ResultsSection.recordListenerFailures(driver);
        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, RUNS.get(0), ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    /** What one class histogram says: the live bytes, and the live column headers. */
    private record Reading(long liveBytes, long headers) {

        Reading least(Reading other) {
            return new Reading(
                    Math.min(liveBytes, other.liveBytes), Math.min(headers, other.headers));
        }
    }

    /**
     * Opens a run through the chooser, as a click on its item does, and waits until its first table
     * is shown.
     */
    private static void open(int number) {
        String id = RUNS.get(number % RUNS.size());
        List<String> offered = ParameterEditorApp.comboItems(driver, "results-run");
        int index = -1;
        for (int item = 0; item < offered.size(); item++) {
            if (offered.get(item).startsWith(id + " -- ")) {
                index = item;
            }
        }
        if (index < 0) {
            fail("the run chooser offers no " + id + ": " + offered);
        }
        int chosen = index;
        ComboBox<?> chooser = (ComboBox<?>) driver.node("results-run");
        driver.onFxThread(() -> chooser.getSelectionModel().select(chosen));
        ResultsSection.awaitOpened(driver, id, ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
    }

    /** Opens {@code count} runs, starting with run {@code from}, and reads the heap at the end. */
    private static Reading openAndRead(int from, int count) {
        for (int number = from; number < from + count - READINGS; number++) {
            open(number);
        }
        Reading least = null;
        for (int number = from + count - READINGS; number < from + count; number++) {
            open(number);
            Reading now = read();
            least = least == null ? now : least.least(now);
        }
        return least;
    }

    /**
     * The live objects, from the JVM's class histogram: see {@code
     * ConsoleFloodUiTest.liveHeapBytes} for why the used heap after a collection is not this.
     */
    private static Reading read() {
        String histogram;
        try {
            histogram =
                    (String)
                            ManagementFactory.getPlatformMBeanServer()
                                    .invoke(
                                            new ObjectName(
                                                    "com.sun.management:type=DiagnosticCommand"),
                                            "gcClassHistogram",
                                            new Object[] {new String[0]},
                                            new String[] {String[].class.getName()});
        } catch (JMException unavailable) {
            throw new IllegalStateException(
                    "the JVM's class histogram could not be taken, so the retained heap cannot"
                            + " be measured",
                    unavailable);
        }
        long bytes = 0;
        long headers = -1;
        int rows = 0;
        for (String line : histogram.split("\n")) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length >= 4 && fields[0].endsWith(":")) {
                rows++;
                if (!fields[3].contains("jdk.internal.vm.Filler")) {
                    bytes += Long.parseLong(fields[2]);
                }
                if (fields[3].equals(HEADER)) {
                    headers = Long.parseLong(fields[1]);
                }
            }
        }
        assertTrue(
                rows > 100 && bytes > 0 && headers > 0,
                "the class histogram listed "
                        + rows
                        + " classes, "
                        + bytes
                        + " bytes and "
                        + headers
                        + " "
                        + HEADER
                        + ", which is not a heap that holds this application showing a table");
        return new Reading(bytes, headers);
    }

    @Test
    @DisplayName(
            "opening 120 more runs retains no more column headers and no more heap than before")
    void theRetainedHeapDoesNotGrowWithTheRunsOpened() {
        Reading before = openAndRead(1, WARM_UP);
        Reading after = openAndRead(1 + WARM_UP, OPENS);

        long headerGrowth = after.headers() - before.headers();
        long heapGrowth = after.liveBytes() - before.liveBytes();
        String measured =
                "live column headers "
                        + before.headers()
                        + " then "
                        + after.headers()
                        + "; live heap "
                        + before.liveBytes()
                        + " then "
                        + after.liveBytes()
                        + " bytes, over "
                        + OPENS
                        + " runs opened";
        assertAll(
                measured,
                () ->
                        assertTrue(
                                headerGrowth <= HEADER_SLACK,
                                "the table's discarded column headers stay reachable: "
                                        + headerGrowth
                                        + " more are live after "
                                        + OPENS
                                        + " more runs were opened, and at most "
                                        + HEADER_SLACK
                                        + " may be"),
                () ->
                        assertTrue(
                                heapGrowth <= HEAP_SLACK_MIB * 1024L * 1024L,
                                "the retained heap grows with the runs opened: "
                                        + heapGrowth
                                        + " bytes more are live after "
                                        + OPENS
                                        + " more runs were opened ("
                                        + heapGrowth / OPENS
                                        + " bytes a run), and at most "
                                        + HEAP_SLACK_MIB
                                        + " MiB may be"),
                () -> assertEquals(List.of(), listenerFailures, "listener failures"));
    }
}
