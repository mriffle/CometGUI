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

package org.cometgui.ui.controls.visualisation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javax.imageio.ImageIO;
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
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.testing.ScriptedVisualisation;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;
import org.cometgui.ui.viewmodel.visualisation.VisualisationViewModel;
import org.cometgui.workflow.pdv.PdvStatus;
import org.cometgui.workflow.storage.ViewStateStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Visualisation pane shows its view-model and acts only through it: the labels follow, the draw
 * action is disabled with the reason as its help, Cancel and the progress bar exist only while an
 * install runs, and a figure PDV wrote is shown -- or why it cannot be.
 */
class VisualisationPaneTest {

    @TempDir private Path work;

    @BeforeAll
    static void toolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @Test
    @DisplayName("the pane follows the section: draw, install progress, Cancel and the figure")
    void thePaneFollowsTheSection() throws IOException, InterruptedException {
        ScriptedVisualisation port = new ScriptedVisualisation(work);
        Path psms =
                Files.writeString(
                        work.resolve("psms.tsv"),
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n"
                                + work.resolve("outputs/comet/k562_3")
                                + "_101_2_1\t1.5\t0.001\t0.0001\tK.PEPTIDEA.R\tsp|P1|A\n",
                        StandardCharsets.UTF_8);
        DisplayFiltersViewModel filters = new DisplayFiltersViewModel();
        ResultTableViewModel table =
                new ResultTableViewModel(filters, Runnable::run, Runnable::run);
        ResultsViewModel results =
                new ResultsViewModel(
                        new OneTable(work, psms),
                        filters,
                        table,
                        new WeightsViewModel(),
                        Runnable::run,
                        Runnable::run);
        VisualisationViewModel section =
                new VisualisationViewModel(
                        port, results, table, Runnable::run, Runnable::run, () -> {});
        Scene scene =
                FxToolkit.callOnFxThread(
                        () -> {
                            Scene built = new Scene(new VisualisationPane(section), 900, 700);
                            built.getRoot().applyCss();
                            built.getRoot().layout();
                            return built;
                        });
        Button draw = (Button) scene.lookup("#" + UiIds.VISUALISATION_DRAW);
        Button cancel = (Button) scene.lookup("#" + UiIds.VISUALISATION_INSTALL_CANCEL);
        ProgressBar progress =
                (ProgressBar) scene.lookup("#" + UiIds.VISUALISATION_INSTALL_PROGRESS);
        Label pdv = (Label) scene.lookup("#" + UiIds.VISUALISATION_PDV_STATUS);
        Label refusal = (Label) scene.lookup("#" + UiIds.VISUALISATION_DRAW_REFUSAL);
        Label outcome = (Label) scene.lookup("#" + UiIds.VISUALISATION_OUTCOME);
        Label psm = (Label) scene.lookup("#" + UiIds.VISUALISATION_PSM);
        Label run = (Label) scene.lookup("#" + UiIds.VISUALISATION_RUN);
        Label figureStatus = (Label) scene.lookup("#" + UiIds.VISUALISATION_FIGURE_STATUS);
        ImageView figure = (ImageView) scene.lookup("#" + UiIds.VISUALISATION_FIGURE);
        assertAll(
                () -> assertTrue(draw.isDisabled()),
                () -> assertTrue(cancel.isDisabled()),
                () -> assertFalse(progress.isVisible()),
                () -> assertEquals(VisualisationViewModel.NOT_READ, pdv.getText()),
                () -> assertEquals(VisualisationViewModel.NO_RUN, run.getText()),
                () -> assertEquals(refusal.getText(), draw.getAccessibleHelp()),
                () -> assertNull(figure.getImage()));

        port.status(
                PdvStatus.notInstalled(ToolVersion.parse("2.7.0"), OptionalLong.of(100), false));
        FxToolkit.onFxThread(
                () -> {
                    section.refresh();
                    results.refresh();
                    table.select(table.page().rows().get(0).key());
                });
        assertAll(
                () -> assertFalse(draw.isDisabled()),
                () -> assertEquals("", draw.getAccessibleHelp()),
                () -> assertTrue(psm.getText().startsWith("PSM " + work), psm.getText()),
                () -> assertTrue(run.getText().startsWith("Run run-1, created "), run.getText()));

        FxToolkit.onFxThread(draw::fire);
        assertAll(
                () -> assertEquals(List.of(ToolVersion.parse("2.7.0")), port.installs()),
                () -> assertTrue(progress.isVisible()),
                () -> assertFalse(cancel.isDisabled()),
                () -> assertTrue(draw.isDisabled()));
        FxToolkit.onFxThread(() -> port.report(InstallPhase.DOWNLOADING, 50, 100));
        assertEquals(0.5, progress.getProgress());
        assertEquals("Installing PDV 2.7.0: Downloading: 50 of 100 bytes.", pdv.getText());
        FxToolkit.onFxThread(cancel::fire);
        assertEquals(1, port.cancels());
        FxToolkit.onFxThread(() -> port.report(InstallPhase.CANCELLED, 50, 100));
        assertAll(
                () -> assertEquals(VisualisationViewModel.INSTALL_CANCELLED, outcome.getText()),
                () -> assertFalse(progress.isVisible()),
                () -> assertTrue(cancel.isDisabled()));

        port.status(PdvStatus.installed(ToolVersion.parse("2.7.0"), work.resolve("PDV.jar")));
        FxToolkit.onFxThread(section::refresh);
        FxToolkit.onFxThread(draw::fire);
        assertEquals(
                "The figure "
                        + port.figure()
                        + " could not be shown: java.nio.file.NoSuchFileException: "
                        + port.figure(),
                figureStatus.getText(),
                "the scripted figure does not exist yet");
        assertNull(figure.getImage());

        Files.createDirectories(Objects.requireNonNull(port.figure().getParent(), "its directory"));
        BufferedImage drawn = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        drawn.setRGB(3, 4, 0xFF6600);
        assertTrue(ImageIO.write(drawn, "png", port.figure().toFile()));
        FxToolkit.onFxThread(
                () -> {
                    table.clearSelection();
                    table.select(table.page().rows().get(0).key());
                });
        FxToolkit.onFxThread(draw::fire);
        assertAll(
                () -> assertNotNull(figure.getImage()),
                () -> assertEquals(80, figure.getImage().getWidth()),
                () -> assertEquals("", figureStatus.getText()),
                () ->
                        assertEquals(
                                "The annotated spectrum PDV drew: " + port.figure(),
                                figure.getAccessibleText()),
                () -> assertTrue(outcome.getText().startsWith("PDV drew the annotated spectrum")));
        FxToolkit.onFxThread(results::close);
    }

    /** A Results port with one run and its target PSM table. */
    private static final class OneTable implements ResultsPort {

        private final Path project;

        private final Path psms;

        OneTable(Path project, Path psms) {
            this.project = project;
            this.psms = psms;
        }

        @Override
        public List<ResultsRun> runs() {
            return List.of(
                    new ResultsRun(
                            new RunId("run-1"),
                            Instant.parse("2026-10-10T08:00:00Z"),
                            AttemptOutcome.SUCCEEDED,
                            EnumSet.of(TableKind.TARGET_PSMS),
                            false));
        }

        @Override
        public OpenedResults open(RunId run) throws IOException {
            RunLayout layout = new RunLayout(project.resolve("runs/run-1"));
            return new OpenedResults(
                    run,
                    Map.of(
                            TableKind.TARGET_PSMS,
                            ResultStores.open(
                                    psms,
                                    TableKind.TARGET_PSMS,
                                    layout.resultIndexDirectory(),
                                    new StreamingHashService())),
                    Optional.empty(),
                    Map.of(),
                    ViewStateStore.read(layout));
        }

        @Override
        public void close(OpenedResults results) {
            for (var store : results.stores().values()) {
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
