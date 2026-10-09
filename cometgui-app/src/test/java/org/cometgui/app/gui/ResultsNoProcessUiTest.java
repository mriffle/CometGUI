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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.RecordingProcessRunner;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 10 exit-gate item 2 ({@code R-RES-01}, design decision P10-10): changing a filter launches
 * no process. The witness is the application's one {@code ProcessRunner}: the application is built
 * through its real composition root with a {@link RecordingProcessRunner} in front of the real
 * process service as that runner -- it records every argument array and then really launches it, so
 * "nothing was recorded" is a statement about the product rather than about a recorder that could
 * not launch anything.
 *
 * <p>Both filters are changed several times and the counts on screen are seen to change each time
 * (the counts are worked out by hand in {@link ResultsFiltersUiTest}'s documentation; at 0 the PSMs
 * passing are c08 alone, the peptides passing the two rows at 0); across all of it the runner's
 * record does not grow, and every file under the run's {@code outputs/} -- the raw Percolator
 * tables and weights -- has the SHA-256 it had before, computed here with the JDK alone.
 */
class ResultsNoProcessUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-09T00:00:00Z"));

    private static final String RUN = "run-gate-two";

    @TempDir private static Path scratch;

    private static RecordingProcessRunner processes;

    private static RunLayout run;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static List<Throwable> listenerFailures;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        run =
                ResultRuns.run(
                        project,
                        RUN,
                        Instant.parse("2026-10-09T08:00:00Z"),
                        List.of(root.resolve("data/sample_A.mzML")),
                        Map.of(
                                TableKind.TARGET_PSMS,
                                ResultRuns.fixture(
                                        ResultRuns.PSMS_UNKNOWN_Q, root.resolve("in/psms.tsv")),
                                TableKind.TARGET_PEPTIDES,
                                ResultRuns.fixture(
                                        ResultRuns.PSMS_SHUFFLED, root.resolve("in/peptides.tsv"))),
                        Optional.of(
                                ResultRuns.fixture(
                                        ResultRuns.WEIGHTS_3071, root.resolve("in/weights.txt"))));
        processes = RecordingProcessRunner.inFrontOf(Clock.systemUTC());
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        processes,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(InstalledComet::nothing, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        listenerFailures = ResultsSection.recordListenerFailures(driver);
        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, RUN, ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName(
            "gate 2: both filters changed several times, the counts change each time, the one"
                    + " process runner is called zero times, and the raw outputs are unchanged")
    void changingTheFiltersLaunchesNoProcess() throws IOException {
        Map<String, String> outputsBefore = outputs();
        assertEquals(3, outputsBefore.size(), "the run's raw outputs: " + outputsBefore.keySet());
        List<List<String>> launchedBefore = processes.launched();

        assertEquals(List.of("15", "5", "2", "8"), ResultsSection.counts(driver), "PSMs at 0.01");
        assertEquals(List.of("15", "6", "1", "8"), psm("0.5"));
        assertEquals(List.of("15", "1", "6", "8"), psm("0"));
        assertEquals(List.of("15", "3", "4", "8"), psm("0.0099999"));
        assertEquals(List.of("15", "5", "2", "8"), psm("0.01"));

        ParameterEditorApp.choose(driver, "results-table-choice", "Target peptides");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertEquals(List.of("23", "9", "7", "7"), ResultsSection.counts(driver), "at 0.01");
        assertEquals(List.of("23", "6", "10", "7"), peptide("0.005"));
        assertEquals(List.of("23", "16", "0", "7"), peptide("1"));
        assertEquals(List.of("23", "2", "14", "7"), peptide("0"));
        assertEquals(List.of("23", "9", "7", "7"), peptide("0.01"));

        List<List<String>> launchedAfter = processes.launched();
        assertAll(
                () ->
                        assertEquals(
                                launchedBefore,
                                launchedAfter,
                                "the one process runner was called while the filters changed"),
                () ->
                        assertEquals(
                                List.of(),
                                launchedAfter,
                                "nothing was launched at all, start-up included"),
                () ->
                        assertEquals(
                                outputsBefore,
                                outputs(),
                                "the SHA-256 of every file under the run's outputs/"),
                () ->
                        assertTrue(
                                Files.isRegularFile(run.viewStateFile()),
                                "the one write a filter change makes: the run's view state"),
                () -> ResultsSection.assertNoListenerFailed(listenerFailures));
    }

    private static List<String> psm(String cutoff) {
        enter(driver, "results-psm-filter", cutoff);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        return ResultsSection.counts(driver);
    }

    private static List<String> peptide(String cutoff) {
        enter(driver, "results-peptide-filter", cutoff);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        return ResultsSection.counts(driver);
    }

    /** Every file under the run's outputs/, by relative path, to its SHA-256. */
    private static Map<String, String> outputs() throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        try (Stream<Path> files = Files.walk(run.outputsDirectory())) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                hashes.put(
                        run.outputsDirectory().relativize(file).toString(),
                        ResultRuns.sha256(file));
            }
        }
        return hashes;
    }
}
