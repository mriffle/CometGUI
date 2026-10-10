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

package org.cometgui.ui.viewmodel.visualisation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.ui.testing.ScriptedVisualisation;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;
import org.cometgui.workflow.pdv.PdvSelection;
import org.cometgui.workflow.pdv.PdvStatus;
import org.cometgui.workflow.pdv.PdvWindowStatus;
import org.cometgui.workflow.storage.ViewStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Visualisation section's live PDV window: opening the Results section's run, its states in
 * words, every PSM selected there -- target or decoy -- sent to it, and the window closed when the
 * result set closes, when asked, and when the application exits. Over a scripted port, with the
 * Results section real and two runs to choose between.
 */
class VisualisationWindowTest {

    private static final RunId FIRST = new RunId("run-1");

    private static final RunId SECOND = new RunId("run-2");

    private static final Instant CREATED = Instant.parse("2026-10-10T08:00:00Z");

    private static final Path JAR = Path.of("cache/pdv/PDV-2.7.0.jar").toAbsolutePath();

    @TempDir private Path work;

    private final Queue background = new Queue();

    private final Queue ui = new Queue();

    private final List<String> ended = new ArrayList<>();

    private ScriptedVisualisation port;

    private TwoRuns runs;

    private ResultTableViewModel table;

    private ResultsViewModel results;

    private VisualisationViewModel section;

    private String base;

    @BeforeEach
    void build() throws IOException {
        port = new ScriptedVisualisation(work);
        base = work.resolve("outputs/comet/k562_3").toString();
        Path psms =
                Files.writeString(
                        work.resolve("psms.tsv"),
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n"
                                + base
                                + "_101_2_1\t1.5\t0.001\t0.0001\tK.PEPTIDEA.R\tsp|P1|A\n"
                                + base
                                + "_102_3_1\t1.2\t0.002\t0.0002\tK.PEPTIDEB.R\tsp|P2|B\n",
                        StandardCharsets.UTF_8);
        runs = new TwoRuns(work, psms);
        DisplayFiltersViewModel filters = new DisplayFiltersViewModel();
        table = new ResultTableViewModel(filters, Runnable::run, Runnable::run);
        results =
                new ResultsViewModel(
                        runs, filters, table, new WeightsViewModel(), Runnable::run, Runnable::run);
        section =
                new VisualisationViewModel(
                        port, results, table, background, ui, () -> ended.add("ended"));
        results.refresh();
        results.choose(listed(FIRST));
    }

    @AfterEach
    void close() {
        results.close();
    }

    private ResultsRun listed(RunId id) {
        return results.runsProperty().get().stream()
                .filter(run -> run.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private void readStatus(PdvStatus status) {
        port.status(status);
        section.refresh();
        settle();
    }

    private void settle() {
        while (background.pending() + ui.pending() > 0) {
            background.drain();
            ui.drain();
        }
    }

    private String select(int row) {
        String psmId = table.page().rows().get(row).psmId();
        table.select(table.page().rows().get(row).key());
        return psmId;
    }

    private static PdvStatus installed() {
        return PdvStatus.installed(ToolVersion.parse("2.7.0"), JAR);
    }

    private static PdvStatus notInstalled() {
        return PdvStatus.notInstalled(
                ToolVersion.parse("2.7.0"), OptionalLong.of(103_407_417L), false);
    }

    /** Opens run 1 in the scripted PDV and makes its window ready. */
    private void openReady() {
        readStatus(installed());
        assertTrue(section.openInPdv());
        settle();
        port.windowState(FIRST, PdvWindowStatus.Phase.READY, "ready");
        settle();
    }

    private String text() {
        return section.windowTextProperty().get();
    }

    @Test
    @DisplayName("nothing is opened before PDV's state is read; then a finished run can be opened")
    void refusals() {
        assertAll(
                () -> assertEquals(VisualisationViewModel.NO_WINDOW, text()),
                () ->
                        assertEquals(
                                VisualisationViewModel.NOT_READ,
                                section.openRefusalProperty().get()),
                () -> assertFalse(section.openEnabledProperty().get()),
                () -> assertFalse(section.closeEnabledProperty().get()),
                () ->
                        assertEquals(
                                VisualisationViewModel.NOTHING_SHOWN,
                                section.selectionTextProperty().get()));
        assertFalse(section.openInPdv());
        assertEquals(VisualisationViewModel.NOT_READ, text());
        readStatus(PdvStatus.unavailable("No PDV here."));
        assertEquals("No PDV here.", section.openRefusalProperty().get());
        readStatus(installed());
        assertAll(
                () -> assertEquals("", section.openRefusalProperty().get()),
                () -> assertTrue(section.openEnabledProperty().get()),
                () -> assertEquals(List.of(), port.opens()));
    }

    @Test
    @DisplayName("an executing run, or no run at all, is not opened, and says why")
    void executingOrNone() {
        readStatus(installed());
        runs.executing(true);
        results.refresh();
        String executing =
                "Run run-1 is executing: its results are not final, and the workflow engine holds"
                        + " its provenance event log, where every PDV launch is recorded. Open it"
                        + " in PDV when the run has ended.";
        assertEquals(executing, section.openRefusalProperty().get());
        assertFalse(section.openInPdv());
        assertEquals(executing, text());
        runs.none();
        results.refresh();
        assertEquals(
                VisualisationViewModel.NO_RUN + " Open a run there to show it in PDV.",
                section.openRefusalProperty().get());
        assertEquals(List.of(), port.opens());
    }

    @Test
    @DisplayName(
            "opening runs on the background, says it is opening, then follows the window's states")
    void opening() {
        readStatus(installed());

        assertTrue(section.openInPdv());

        assertAll(
                () ->
                        assertEquals(
                                "Opening run run-1 in PDV: writing the mzTab PDV reads, then"
                                        + " starting PDV.",
                                text()),
                () ->
                        assertEquals(
                                "A run is being opened in PDV; it can be opened again when that"
                                        + " has finished.",
                                section.openRefusalProperty().get()),
                () -> assertEquals(List.of(), port.opens(), "not on the interface thread"));
        settle();
        assertAll(
                () -> assertEquals(List.of("run-1"), port.opens()),
                () ->
                        assertEquals(
                                "PDV is starting for run run-1 on loopback port 41547, importing"
                                        + " the run's mzTab.",
                                text()),
                () -> assertTrue(section.closeEnabledProperty().get()),
                () -> assertTrue(section.openEnabledProperty().get(), "it can be opened again"));
        port.windowState(FIRST, PdvWindowStatus.Phase.READY, "ready");
        settle();
        assertEquals(
                "PDV is ready on loopback port 41547, showing run run-1. The PSM selected in the"
                        + " Results section is shown there.",
                text());
        port.windowState(FIRST, PdvWindowStatus.Phase.FAILED, "it died");
        settle();
        assertAll(
                () -> assertEquals("PDV could not show run run-1: it died", text()),
                () -> assertFalse(section.closeEnabledProperty().get()));
    }

    @Test
    @DisplayName("a refused opening opens nothing and says why in the port's words")
    void aRefusedOpening() {
        readStatus(installed());
        port.openFails("an unsupported spectrum file");

        section.openInPdv();
        settle();

        assertAll(
                () ->
                        assertEquals(
                                "PDV was not opened for run run-1: an unsupported spectrum file",
                                text()),
                () -> assertFalse(section.closeEnabledProperty().get()),
                () -> assertTrue(section.openEnabledProperty().get()));
    }

    @Test
    @DisplayName(
            "the PSM selected when PDV becomes ready, and each one after, is sent -- target or"
                    + " decoy as the table shown")
    void selectionsAreSent() {
        readStatus(installed());
        String first = select(0);
        section.openInPdv();
        settle();
        assertEquals(List.of(), port.shows(), "nothing is sent before PDV is ready");
        port.windowState(FIRST, PdvWindowStatus.Phase.READY, "ready");
        settle();
        assertEquals(List.of("run-1 target " + first), port.shows());
        assertEquals(
                "Target PSM " + first + " is shown in PDV: PDV answered /select with 200 selected.",
                section.selectionTextProperty().get());

        String second = select(1);
        settle();
        results.chooseTable(TableKind.DECOY_PSMS);
        settle();
        String decoy = select(0);
        settle();
        results.chooseTable(TableKind.TARGET_PEPTIDES);
        select(1);
        settle();

        assertEquals(
                List.of("run-1 target " + first, "run-1 target " + second, "run-1 decoy " + decoy),
                port.shows(),
                "a table change sends nothing; a decoy row is sent as a decoy; no peptide row is");
        assertEquals(
                "Decoy PSM " + decoy + " is shown in PDV: PDV answered /select with 200 selected.",
                section.selectionTextProperty().get());
    }

    @Test
    @DisplayName("PDV's refusals are shown in its words; a superseded selection changes nothing")
    void answersInWords() {
        openReady();
        port.answeringShow(
                key ->
                        new PdvSelection(
                                FIRST,
                                Optional.of("ms_run[1]:index=9"),
                                key.contains("_101_")
                                        ? PdvSelection.Kind.UNKNOWN_TO_PDV
                                        : PdvSelection.Kind.SUPERSEDED,
                                key.contains("_101_") ? OptionalInt.of(404) : OptionalInt.empty(),
                                "PDV answered /select with 404 unknown ref."));
        String first = select(0);
        settle();
        String said = section.selectionTextProperty().get();
        select(1);
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "Target PSM "
                                        + first
                                        + " is not shown in PDV: PDV answered /select with 404"
                                        + " unknown ref.",
                                said),
                () -> assertEquals(said, section.selectionTextProperty().get()));
    }

    @Test
    @DisplayName(
            "choosing another run in the Results section closes the window: its result set has"
                    + " closed")
    void anotherRunClosesTheWindow() {
        openReady();

        results.choose(listed(SECOND));
        settle();
        select(0);
        settle();

        assertAll(
                () -> assertEquals(List.of("run-1"), port.closes()),
                () ->
                        assertEquals(
                                "PDV's window for run run-1 was closed: the Results section no"
                                        + " longer shows that run.",
                                text()),
                () -> assertEquals(List.of(), port.shows(), "nothing is sent to a closed window"),
                () -> assertFalse(section.closeEnabledProperty().get()),
                () ->
                        assertEquals(
                                VisualisationViewModel.NOTHING_SHOWN,
                                section.selectionTextProperty().get()));
        port.windowState(FIRST, PdvWindowStatus.Phase.CLOSED, "late");
        settle();
        assertTrue(text().contains("was closed: the Results section"), "a stale state is dropped");
    }

    @Test
    @DisplayName("a window still opening when the run changes is closed once it has started")
    void aStaleOpeningIsClosed() {
        readStatus(installed());
        section.openInPdv();

        results.choose(listed(SECOND));
        settle();

        assertAll(
                () -> assertEquals(List.of("run-1"), port.opens()),
                () -> assertEquals(List.of("run-1", "run-1"), port.closes()),
                () -> assertTrue(text().startsWith("PDV's window for run run-1 was closed")),
                () -> assertTrue(section.openEnabledProperty().get()));
    }

    @Test
    @DisplayName("Close PDV closes the window on the background; its closed state follows")
    void closeIsAsked() {
        openReady();

        section.closePdv();
        assertEquals(List.of(), port.closes(), "not on the interface thread");
        settle();
        port.windowState(FIRST, PdvWindowStatus.Phase.CLOSED, "PDV was closed.");
        settle();

        assertAll(
                () -> assertEquals(List.of("run-1"), port.closes()),
                () -> assertEquals("PDV's window for run run-1 is closed: PDV was closed.", text()),
                () -> assertFalse(section.closeEnabledProperty().get()));
        section.closePdv();
        settle();
        assertEquals(List.of("run-1"), port.closes(), "a closed window is not closed again");
    }

    @Test
    @DisplayName("the application's exit closes every window, on the calling thread")
    void exitClosesEverything() {
        openReady();
        section.closeAllWindows();
        assertEquals(List.of("all"), port.closes());
        port.windowState(FIRST, PdvWindowStatus.Phase.CLOSED, "late");
        settle();
        assertTrue(text().startsWith("PDV is ready"), "nothing after exit is applied");
    }

    @Test
    @DisplayName("PDV not installed: install first, then open")
    void installThenOpen() {
        readStatus(notInstalled());

        assertTrue(section.openInPdv());
        assertEquals(
                "PDV 2.7.0 is being installed; run run-1 is opened in PDV when it has finished.",
                text());
        assertEquals(
                "PDV is being installed; it can be opened when it has finished.",
                section.openRefusalProperty().get());
        port.status(installed());
        port.report(InstallPhase.DONE, 100, 100);
        ui.drain();
        assertEquals("PDV is installed; opening run run-1 in PDV.", text());
        settle();
        assertAll(
                () -> assertEquals(List.of(ToolVersion.parse("2.7.0")), port.installs()),
                () -> assertEquals(List.of("ended"), ended),
                () -> assertEquals(List.of("run-1"), port.opens()),
                () -> assertTrue(text().startsWith("PDV is starting for run run-1"), text()));
    }

    @Test
    @DisplayName(
            "an install that is cancelled, fails, is refused, or ends uninstalled opens nothing")
    void installsThatDoNotOpen() {
        readStatus(notInstalled());
        section.openInPdv();
        port.report(InstallPhase.CANCELLED, 1, 100);
        settle();
        assertEquals(VisualisationViewModel.WINDOW_INSTALL_CANCELLED, text());
        section.openInPdv();
        port.report(InstallPhase.FAILED, 1, 100);
        settle();
        assertEquals(VisualisationViewModel.WINDOW_INSTALL_FAILED, text());
        section.openInPdv();
        port.status(PdvStatus.unavailable("PDV vanished."));
        port.report(InstallPhase.DONE, 100, 100);
        settle();
        assertEquals("PDV was not opened: PDV vanished.", text());
        readStatus(notInstalled());
        port.installFails("no Tool Manager");
        assertFalse(section.openInPdv());
        assertEquals("PDV could not be installed, so it was not opened: no Tool Manager", text());
        assertEquals(List.of(), port.opens());
    }

    /** An executor that holds its tasks until the test runs them. */
    private static final class Queue implements Executor {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        int pending() {
            return tasks.size();
        }

        void drain() {
            while (!tasks.isEmpty()) {
                tasks.pollFirst().run();
            }
        }
    }

    /** A Results port with two finished runs, each with target and decoy PSM tables. */
    private static final class TwoRuns implements ResultsPort {

        private static final List<TableKind> TABLES =
                List.of(TableKind.TARGET_PSMS, TableKind.DECOY_PSMS, TableKind.TARGET_PEPTIDES);

        private final Path project;

        private final Path psms;

        private boolean executing;

        private boolean none;

        TwoRuns(Path project, Path psms) {
            this.project = project;
            this.psms = psms;
        }

        void executing(boolean now) {
            executing = now;
        }

        void none() {
            none = true;
        }

        @Override
        public List<ResultsRun> runs() {
            if (none) {
                return List.of();
            }
            List<ResultsRun> listed = new ArrayList<>();
            for (RunId id : List.of(FIRST, SECOND)) {
                boolean busy = executing && id.equals(FIRST);
                listed.add(
                        new ResultsRun(
                                id,
                                CREATED,
                                busy ? AttemptOutcome.RUNNING : AttemptOutcome.SUCCEEDED,
                                EnumSet.copyOf(TABLES),
                                busy));
            }
            return listed;
        }

        @Override
        public OpenedResults open(RunId run) throws IOException {
            RunLayout layout = new RunLayout(project.resolve("runs").resolve(run.value()));
            Map<TableKind, ResultStore> stores = new EnumMap<>(TableKind.class);
            for (TableKind kind : TABLES) {
                stores.put(
                        kind,
                        ResultStores.open(
                                psms,
                                kind,
                                layout.resultIndexDirectory(),
                                new StreamingHashService()));
            }
            return new OpenedResults(
                    run, stores, Optional.empty(), Map.of(), ViewStateStore.read(layout));
        }

        @Override
        public void close(OpenedResults results) {
            for (ResultStore store : results.stores().values()) {
                try {
                    store.close();
                } catch (IOException ignored) {
                    // closed already
                }
            }
        }

        @Override
        public void saveViewState(RunId run, DisplayFilters filters) {
            // nothing is saved here
        }

        @Override
        public TableExport exportTable(
                RunId run, TableKind kind, QValueFilter filter, Category category)
                throws IOException {
            throw new IOException("no exports here");
        }

        @Override
        public WeightsExport exportWeights(RunId run, WeightsSummary weights) throws IOException {
            throw new IOException("no exports here");
        }
    }
}
