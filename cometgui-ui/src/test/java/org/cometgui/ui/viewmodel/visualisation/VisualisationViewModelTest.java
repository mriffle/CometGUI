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
import org.cometgui.ui.viewmodel.results.ResultRowView;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;
import org.cometgui.workflow.pdv.PdvStatus;
import org.cometgui.workflow.storage.ViewStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Visualisation section's state: the Results section's one selection, the refusals in words,
 * drawing at once when PDV is installed and after installing it when it is not, and the install's
 * progress, cancellation and failure -- over a scripted port, with the Results section real.
 */
class VisualisationViewModelTest {

    private static final String RUN = "run-1";

    private static final Instant CREATED = Instant.parse("2026-10-10T08:00:00Z");

    private static final Path JAR = Path.of("cache/pdv/PDV-2.7.0.jar").toAbsolutePath();

    @TempDir private Path work;

    private final Queue background = new Queue();

    private final Queue ui = new Queue();

    private final List<String> ended = new ArrayList<>();

    private ScriptedVisualisation port;

    private OneRun runs;

    private ResultTableViewModel table;

    private ResultsViewModel results;

    private VisualisationViewModel section;

    private String base;

    @BeforeEach
    void build() throws IOException {
        port = new ScriptedVisualisation(work);
        base = work.resolve("runs/" + RUN + "/outputs/comet/k562_3").toString();
        Path psms =
                Files.writeString(
                        work.resolve("psms.tsv"),
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n"
                                + base
                                + "_101_2_1\t1.5\t0.001\t0.0001\tK.PEPTIDEA.R\tsp|P1|A\n"
                                + base
                                + "_102_3_1\t1.2\t0.002\t0.0002\tK.PEPTIDEB.R\tsp|P2|B\n",
                        StandardCharsets.UTF_8);
        runs = new OneRun(work, psms);
        DisplayFiltersViewModel filters = new DisplayFiltersViewModel();
        table = new ResultTableViewModel(filters, Runnable::run, Runnable::run);
        results =
                new ResultsViewModel(
                        runs, filters, table, new WeightsViewModel(), Runnable::run, Runnable::run);
        section =
                new VisualisationViewModel(
                        port, results, table, background, ui, () -> ended.add("ended"));
    }

    @AfterEach
    void close() {
        results.close();
    }

    /** Opens the run and selects its second PSM. */
    private String selectSecond() {
        results.refresh();
        ResultRowView second = table.page().rows().get(1);
        table.select(second.key());
        return second.psmId();
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

    private static PdvStatus installed() {
        return PdvStatus.installed(ToolVersion.parse("2.7.0"), JAR);
    }

    private static PdvStatus notInstalled() {
        return PdvStatus.notInstalled(
                ToolVersion.parse("2.7.0"), OptionalLong.of(103_407_417L), false);
    }

    @Test
    @DisplayName("with nothing read and nothing open, every refusal is in words")
    void nothingYet() {
        assertAll(
                () ->
                        assertEquals(
                                VisualisationViewModel.NOT_READ, section.pdvTextProperty().get()),
                () -> assertEquals(VisualisationViewModel.NO_RUN, section.runTextProperty().get()),
                () -> assertEquals(VisualisationViewModel.NO_PSM, section.psmTextProperty().get()),
                () ->
                        assertEquals(
                                "No run's results are open in the Results section. Open a run"
                                        + " there and select a PSM.",
                                section.drawRefusalProperty().get()),
                () -> assertFalse(section.drawEnabledProperty().get()),
                () ->
                        assertEquals(
                                VisualisationViewModel.NOTHING_DRAWN,
                                section.outcomeProperty().get()),
                () -> assertEquals(Optional.empty(), section.figureProperty().get()));
        assertFalse(section.draw());
        assertEquals(
                "No run's results are open in the Results section. Open a run there and select a"
                        + " PSM.",
                section.outcomeProperty().get());
        assertEquals(List.of(), port.draws());
    }

    @Test
    @DisplayName("the run and PSM are the Results section's selection, followed as it changes")
    void theOneSelection() {
        results.refresh();
        readStatus(installed());
        assertEquals("Run " + RUN + ", created " + CREATED + ".", section.runTextProperty().get());
        assertEquals(
                "No PSM is selected in the Results section. Select one there to draw its"
                        + " spectrum.",
                section.drawRefusalProperty().get());
        String psmId = selectSecond();
        assertAll(
                () ->
                        assertEquals(
                                "PSM "
                                        + psmId
                                        + ": peptide K.PEPTIDEB.R, charge 3, scan 102 of "
                                        + base
                                        + ".",
                                section.psmTextProperty().get()),
                () -> assertEquals("", section.drawRefusalProperty().get()),
                () -> assertTrue(section.drawEnabledProperty().get()));
        table.clearSelection();
        assertEquals(VisualisationViewModel.NO_PSM, section.psmTextProperty().get());
        assertFalse(section.drawEnabledProperty().get());
        results.chooseTable(TableKind.TARGET_PEPTIDES);
        assertAll(
                () ->
                        assertEquals(
                                VisualisationViewModel.PEPTIDE_TABLE,
                                section.psmTextProperty().get()),
                () ->
                        assertEquals(
                                VisualisationViewModel.PEPTIDE_TABLE,
                                section.drawRefusalProperty().get()));
        table.select(table.page().rows().get(0).key());
        assertEquals(VisualisationViewModel.PEPTIDE_TABLE, section.drawRefusalProperty().get());
    }

    @Test
    @DisplayName("an executing run is refused: the engine holds its event log")
    void anExecutingRunIsRefused() {
        runs.executing(true);
        readStatus(installed());
        selectSecond();
        assertEquals(
                "Run "
                        + RUN
                        + " is executing: the workflow engine holds its provenance event log,"
                        + " where every PDV launch is recorded. Draw when the run has ended.",
                section.drawRefusalProperty().get());
        assertEquals(
                "Run " + RUN + ", created " + CREATED + ", executing now.",
                section.runTextProperty().get());
        assertFalse(section.draw());
        assertEquals(List.of(), port.draws());
    }

    @Test
    @DisplayName("a run that starts executing while shown is refused at once")
    void aRunThatStartsExecuting() {
        readStatus(installed());
        selectSecond();
        assertTrue(section.drawEnabledProperty().get());
        runs.executing(true);
        results.refresh();
        assertFalse(section.drawEnabledProperty().get());
        assertTrue(
                section.drawRefusalProperty().get().startsWith("Run " + RUN + " is executing"),
                section.drawRefusalProperty().get());
    }

    @Test
    @DisplayName("PDV's state unread, unavailable, or being installed elsewhere refuses, in words")
    void pdvRefusals() {
        String psmId = selectSecond();
        assertEquals(VisualisationViewModel.NOT_READ, section.drawRefusalProperty().get());
        readStatus(PdvStatus.unavailable("No PDV build can be installed here."));
        assertEquals("No PDV build can be installed here.", section.pdvTextProperty().get());
        assertEquals("No PDV build can be installed here.", section.drawRefusalProperty().get());
        readStatus(PdvStatus.installing(ToolVersion.parse("2.7.0")));
        assertEquals(
                PdvStatus.installing(ToolVersion.parse("2.7.0")).explanation(),
                section.drawRefusalProperty().get());
        port.statusFails("the cache is unreadable");
        section.refresh();
        settle();
        assertEquals(
                "PDV's state could not be read: the cache is unreadable",
                section.drawRefusalProperty().get());
        assertFalse(section.draw());
        assertEquals(List.of(), port.draws());
        assertEquals(List.of(), port.installs());
        assertTrue(psmId.endsWith("_102_3_1"));
    }

    @Test
    @DisplayName("an older read of PDV's state is dropped when a newer one was asked for")
    void staleReadsAreDropped() {
        port.status(installed());
        section.refresh();
        port.status(PdvStatus.unavailable("newer"));
        section.refresh();
        background.runLast();
        background.runFirst();
        ui.runFirst();
        ui.runFirst();
        assertEquals("newer", section.pdvTextProperty().get());
    }

    @Test
    @DisplayName("PDV installed: draw at once, on the background, and show the figure")
    void drawAtOnce() {
        readStatus(installed());
        String psmId = selectSecond();

        assertTrue(section.draw());
        assertAll(
                () ->
                        assertEquals(
                                "Drawing the annotated spectrum of PSM " + psmId + " with PDV.",
                                section.outcomeProperty().get()),
                () ->
                        assertEquals(
                                "A spectrum is being drawn; the next can be drawn when it has"
                                        + " finished.",
                                section.drawRefusalProperty().get()),
                () -> assertEquals(List.of(), port.draws(), "not on the interface thread"));
        settle();
        assertEquals(Optional.of(port.figure()), section.figureProperty().get());
        section.draw();
        assertEquals(
                Optional.empty(),
                section.figureProperty().get(),
                "a new drawing replaces the figure shown; none is shown while PDV draws");
        settle();
        assertAll(
                () -> assertEquals(List.of(RUN + " " + psmId, RUN + " " + psmId), port.draws()),
                () -> assertEquals(Optional.of(port.figure()), section.figureProperty().get()),
                () ->
                        assertEquals(
                                "PDV drew the annotated spectrum of PSM "
                                        + psmId
                                        + " (FNLTYVSHDGDDK) from spectrum \"controllerType=0"
                                        + " controllerNumber=1 scan=11367\" of "
                                        + work.resolve("k562_3.mzML")
                                        + ": "
                                        + port.figure()
                                        + ", 800 x 400 pixels.",
                                section.outcomeProperty().get()),
                () -> assertTrue(section.drawEnabledProperty().get()),
                () -> assertEquals(List.of(), port.installs()));
    }

    @Test
    @DisplayName("a drawing that fails says why, in the refusal's own words")
    void aFailedDrawing() {
        readStatus(installed());
        selectSecond();
        port.drawFails("The spectrum file /x.mzXML cannot be visualised by PDV 2.7.0: because.");

        section.draw();
        settle();

        assertEquals(
                "Nothing was drawn: The spectrum file /x.mzXML cannot be visualised by PDV 2.7.0:"
                        + " because.",
                section.outcomeProperty().get());
        assertEquals(Optional.empty(), section.figureProperty().get());
        assertTrue(section.drawEnabledProperty().get());
    }

    @Test
    @DisplayName(
            "PDV not installed: the first draw installs it through the port, with progress, and"
                    + " then draws")
    void installThenDraw() {
        readStatus(notInstalled());
        String psmId = selectSecond();

        assertTrue(section.draw());
        assertAll(
                () -> assertEquals(List.of(ToolVersion.parse("2.7.0")), port.installs()),
                () -> assertTrue(section.installingProperty().get()),
                () -> assertEquals(-1, section.installProgressProperty().get()),
                () ->
                        assertEquals(
                                "Installing PDV 2.7.0: starting.", section.pdvTextProperty().get()),
                () ->
                        assertEquals(
                                "PDV is being installed; the spectrum is drawn when it has"
                                        + " finished.",
                                section.drawRefusalProperty().get()),
                () ->
                        assertEquals(
                                "PDV 2.7.0 is being installed; the spectrum of PSM "
                                        + psmId
                                        + " is drawn when it has finished.",
                                section.outcomeProperty().get()));
        port.report(InstallPhase.DOWNLOADING, 25, 100);
        settle();
        assertEquals(0.25, section.installProgressProperty().get());
        assertEquals(
                "Installing PDV 2.7.0: Downloading: 25 of 100 bytes.",
                section.pdvTextProperty().get());
        section.refresh();
        port.status(installed());
        settle();
        assertEquals(
                "Installing PDV 2.7.0: Downloading: 25 of 100 bytes.",
                section.pdvTextProperty().get(),
                "a read of PDV's state does not hide the install's progress");
        port.report(InstallPhase.VERIFYING, 100, -1);
        settle();
        assertEquals(-1, section.installProgressProperty().get());
        port.report(InstallPhase.EXTRACTING, 100, 0);
        settle();
        assertEquals(-1, section.installProgressProperty().get(), "a zero total is not divided by");
        port.report(InstallPhase.DONE, 100, 100);
        ui.drain();
        assertEquals(
                "PDV is installed; drawing the spectrum of PSM " + psmId + ".",
                section.outcomeProperty().get());
        assertEquals(
                "A spectrum is being drawn; the next can be drawn when it has finished.",
                section.drawRefusalProperty().get());
        settle();
        assertAll(
                () -> assertFalse(section.installingProperty().get()),
                () -> assertEquals(0, section.installProgressProperty().get()),
                () -> assertEquals(List.of("ended"), ended),
                () -> assertEquals(List.of(RUN + " " + psmId), port.draws()),
                () -> assertEquals(installed().explanation(), section.pdvTextProperty().get()),
                () -> assertEquals(Optional.of(port.figure()), section.figureProperty().get()));
        port.report(InstallPhase.DONE, 100, 100);
        settle();
        assertEquals(List.of("ended"), ended, "a report after the end is ignored");
    }

    @Test
    @DisplayName("an install cancelled through the section draws nothing and says so")
    void cancelled() {
        readStatus(notInstalled());
        selectSecond();
        section.cancelInstall();
        assertEquals(0, port.cancels(), "nothing to cancel yet");
        section.draw();
        section.cancelInstall();
        assertEquals(1, port.cancels());
        int reads = port.statusReads();
        port.report(InstallPhase.CANCELLED, 20, 100);
        ui.drain();
        assertTrue(section.drawEnabledProperty().get(), "Draw is offered again at once");
        settle();
        assertAll(
                () ->
                        assertEquals(
                                VisualisationViewModel.INSTALL_CANCELLED,
                                section.outcomeProperty().get()),
                () -> assertFalse(section.installingProperty().get()),
                () -> assertEquals(List.of("ended"), ended),
                () -> assertEquals(List.of(), port.draws()),
                () -> assertEquals(reads + 1, port.statusReads(), "PDV's state is read again"),
                () -> assertEquals(notInstalled().explanation(), section.pdvTextProperty().get()),
                () -> assertTrue(section.drawEnabledProperty().get()));
        section.cancelInstall();
        assertEquals(1, port.cancels(), "an ended install is not cancelled again");
    }

    @Test
    @DisplayName("a failed install draws nothing and points at the Tool Manager")
    void failed() {
        readStatus(notInstalled());
        selectSecond();
        section.draw();
        port.report(InstallPhase.FAILED, 20, 100);
        settle();
        assertEquals(VisualisationViewModel.INSTALL_FAILED, section.outcomeProperty().get());
        assertEquals(List.of(), port.draws());
    }

    @Test
    @DisplayName("an install the port refuses to start draws nothing and says why")
    void refusedInstall() {
        readStatus(notInstalled());
        selectSecond();
        port.installFails("no Tool Manager here");

        assertFalse(section.draw());

        assertAll(
                () ->
                        assertEquals(
                                "PDV could not be installed, so nothing was drawn: no Tool"
                                        + " Manager here",
                                section.outcomeProperty().get()),
                () -> assertFalse(section.installingProperty().get()),
                () -> assertEquals(0, section.installProgressProperty().get()),
                () -> assertEquals(notInstalled().explanation(), section.pdvTextProperty().get()),
                () -> assertTrue(section.drawEnabledProperty().get()));
    }

    @Test
    @DisplayName("an install that ends without PDV installed draws nothing and says what is")
    void doneButNotInstalled() {
        readStatus(notInstalled());
        selectSecond();
        section.draw();
        port.status(PdvStatus.unavailable("PDV vanished."));
        port.report(InstallPhase.DONE, 100, 100);
        settle();
        assertEquals("Nothing was drawn: PDV vanished.", section.outcomeProperty().get());
        assertEquals(List.of(), port.draws());
        port.statusFails("unreadable");
        section.draw();
        assertEquals("PDV vanished.", section.outcomeProperty().get());
    }

    @Test
    @DisplayName("a read of PDV's state that fails after an install draws nothing")
    void doneButUnreadable() {
        readStatus(notInstalled());
        selectSecond();
        section.draw();
        port.statusFails("unreadable");
        port.report(InstallPhase.DONE, 100, 100);
        settle();
        assertEquals(
                "Nothing was drawn: PDV's state could not be read: unreadable",
                section.outcomeProperty().get());
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

        void runFirst() {
            tasks.pollFirst().run();
        }

        void runLast() {
            tasks.pollLast().run();
        }

        void drain() {
            while (!tasks.isEmpty()) {
                runFirst();
            }
        }
    }

    /** A Results port with one run and its target PSM and peptide tables. */
    private static final class OneRun implements ResultsPort {

        private final Path project;

        private final Path psms;

        private final List<ResultStore> opened = new ArrayList<>();

        private boolean executing;

        OneRun(Path project, Path psms) {
            this.project = project;
            this.psms = psms;
        }

        void executing(boolean now) {
            executing = now;
        }

        @Override
        public List<ResultsRun> runs() {
            return List.of(
                    new ResultsRun(
                            new RunId(RUN),
                            CREATED,
                            executing ? AttemptOutcome.RUNNING : AttemptOutcome.SUCCEEDED,
                            EnumSet.of(TableKind.TARGET_PSMS, TableKind.TARGET_PEPTIDES),
                            executing));
        }

        @Override
        public OpenedResults open(RunId run) throws IOException {
            RunLayout layout = new RunLayout(project.resolve("runs").resolve(RUN));
            Map<TableKind, ResultStore> stores = new EnumMap<>(TableKind.class);
            for (TableKind kind : List.of(TableKind.TARGET_PSMS, TableKind.TARGET_PEPTIDES)) {
                ResultStore store =
                        ResultStores.open(
                                psms,
                                kind,
                                layout.resultIndexDirectory(),
                                new StreamingHashService());
                opened.add(store);
                stores.put(kind, store);
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
