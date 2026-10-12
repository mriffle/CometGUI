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

package org.cometgui.ui.controls.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.ui.controls.AccessibleControls;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.testing.Percolators;
import org.cometgui.ui.testing.ScriptedLimelight;
import org.cometgui.ui.testing.ToolOffers;
import org.cometgui.ui.viewmodel.limelight.LimelightOptions;
import org.cometgui.ui.viewmodel.limelight.LimelightResult;
import org.cometgui.ui.viewmodel.limelight.LimelightRunState;
import org.cometgui.ui.viewmodel.limelight.LimelightViewModel;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;
import org.cometgui.workflow.limelight.LimelightCompatibility;
import org.cometgui.workflow.limelight.LimelightConverterStatus;
import org.cometgui.workflow.limelight.LimelightReadiness;
import org.cometgui.workflow.storage.LimelightSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Limelight pane shows its view-model and acts only through it: every conversion control is
 * disabled while the run cannot be converted and the action carries the workflow's label; a
 * convertible run's fields commit through the view-model; decoy import stays disabled and
 * unselected on a run without decoys; Convert's log and result follow; and every identified control
 * has a name of its own.
 */
class LimelightPaneTest {

    private static final RunId RUN = new RunId("run-1");

    private static final String NO_DECOYS =
            "Limelight decoy import is not available for this run: its Percolator XML holds no"
                    + " decoy PSMs.";

    private static final String NO_XML = "run run-1's Percolator 3.09 wrote no Percolator XML.";

    private ScriptedLimelight port;

    private Scene scene;

    @BeforeAll
    static void toolkit() throws InterruptedException {
        FxToolkit.start();
    }

    private static LimelightReadiness readiness(
            LimelightCompatibility compatibility, Optional<String> decoys) {
        Path root = Path.of("project/runs/run-1").toAbsolutePath();
        return new LimelightReadiness(
                RUN,
                List.of(),
                LimelightSettings.DEFAULTS,
                root.resolve("parameters/comet.params"),
                root.resolve("outputs/percolator/pout.xml"),
                root.resolve("outputs/comet"),
                Path.of("inputs/subset.fasta").toAbsolutePath(),
                Optional.of(root.resolve("outputs/limelight/limelight-1.xml")),
                decoys,
                Optional.empty(),
                compatibility);
    }

    private LimelightViewModel open(LimelightRunState state) throws InterruptedException {
        port = new ScriptedLimelight("none");
        port.converter(
                LimelightConverterStatus.installed(
                        ToolVersion.parse("2.8.1"), Path.of("c.jar").toAbsolutePath()));
        port.check(run -> state);
        DisplayFiltersViewModel filters = new DisplayFiltersViewModel();
        ResultsViewModel results =
                new ResultsViewModel(
                        new OneRun(),
                        filters,
                        new ResultTableViewModel(filters, Runnable::run, Runnable::run),
                        new WeightsViewModel(),
                        Runnable::run,
                        Runnable::run);
        LimelightViewModel section =
                new LimelightViewModel(
                        port,
                        results,
                        new SimpleObjectProperty<>(Percolators.ready()),
                        () -> true,
                        Runnable::run,
                        Runnable::run,
                        () -> {});
        scene =
                FxToolkit.callOnFxThread(
                        () -> {
                            Scene built = new Scene(new LimelightPane(section), 1000, 900);
                            section.refresh();
                            results.refresh();
                            built.getRoot().applyCss();
                            built.getRoot().layout();
                            return built;
                        });
        return section;
    }

    private <T extends Node> T node(String id) {
        @SuppressWarnings("unchecked")
        T found = (T) scene.lookup("#" + id);
        assertNotNull(found, id);
        return found;
    }

    @Test
    @DisplayName(
            "gate 3: a run without Percolator XML -- every conversion control disabled, the"
                    + " explanation shown, the action labelled as the workflow labels it")
    void incompatible() throws InterruptedException {
        CompatibilityAction action =
                CompatibilityAction.forStage(
                        List.of(ToolOffers.percolatorAvailable()),
                        DownstreamStage.LIMELIGHT_CONVERSION);
        open(
                new LimelightRunState(
                        readiness(
                                new LimelightCompatibility(
                                        RUN,
                                        Optional.of("3.09"),
                                        Optional.of(NO_XML),
                                        Optional.of(action)),
                                Optional.of(NO_DECOYS)),
                        new LimelightOptions("0.01", false, "", false)));
        Button button = node(UiIds.LIMELIGHT_ACTION);
        assertAll(
                () ->
                        assertEquals(
                                NO_XML, this.<Label>node(UiIds.LIMELIGHT_COMPATIBILITY).getText()),
                () -> assertEquals(action.label(), button.getText()),
                () -> assertEquals(action.label(), button.getAccessibleText()),
                () -> assertEquals(action.explanation(), button.getAccessibleHelp()),
                () -> assertTrue(button.isVisible()),
                () -> assertFalse(button.isDisabled()),
                () -> assertTrue(this.<TextField>node(UiIds.LIMELIGHT_Q_CUTOFF).isDisabled()),
                () -> assertTrue(this.<TextField>node(UiIds.LIMELIGHT_OUTPUT).isDisabled()),
                () -> assertTrue(this.<TextField>node(UiIds.LIMELIGHT_DECOY_PREFIX).isDisabled()),
                () -> assertTrue(this.<CheckBox>node(UiIds.LIMELIGHT_OPEN_MOD).isDisabled()),
                () -> assertTrue(this.<CheckBox>node(UiIds.LIMELIGHT_IMPORT_DECOYS).isDisabled()),
                () -> assertTrue(this.<Button>node(UiIds.LIMELIGHT_CONVERT).isDisabled()),
                () ->
                        assertEquals(
                                NO_XML,
                                this.<Label>node(UiIds.LIMELIGHT_CONVERT_REFUSAL).getText()),
                () -> assertTrue(this.<Button>node(UiIds.LIMELIGHT_RERUN_CANCEL).isDisabled()),
                () -> assertTrue(this.<Button>node(UiIds.LIMELIGHT_INSTALL_CANCEL).isDisabled()),
                () ->
                        assertFalse(
                                this.<ProgressBar>node(UiIds.LIMELIGHT_INSTALL_PROGRESS)
                                        .isVisible()));
        FxToolkit.onFxThread(button::fire);
        assertEquals(1, port.installs().size(), "the action installs the build, then reruns");
        assertEquals("percolator", port.installs().get(0).tool());
    }

    @Test
    @DisplayName(
            "a convertible run: the cutoff commits through the view-model, decoy import cannot be"
                    + " selected, Convert shows the log and the result")
    void convertible() throws InterruptedException {
        LimelightRunState state =
                new LimelightRunState(
                        readiness(
                                new LimelightCompatibility(
                                        RUN,
                                        Optional.of("3.07.1"),
                                        Optional.empty(),
                                        Optional.empty()),
                                Optional.of(NO_DECOYS)),
                        new LimelightOptions("0.01", false, "", false));
        LimelightViewModel section = open(state);
        TextField cutoff = node(UiIds.LIMELIGHT_Q_CUTOFF);
        CheckBox decoys = node(UiIds.LIMELIGHT_IMPORT_DECOYS);
        Button action = node(UiIds.LIMELIGHT_ACTION);
        assertAll(
                () -> assertEquals("0.01", cutoff.getText()),
                () -> assertFalse(cutoff.isDisabled()),
                () -> assertTrue(decoys.isDisabled()),
                () -> assertFalse(decoys.isSelected()),
                () -> assertEquals(NO_DECOYS, decoys.getAccessibleHelp()),
                () ->
                        assertEquals(
                                NO_DECOYS,
                                this.<Label>node(UiIds.LIMELIGHT_DECOY_STATUS).getText()),
                () -> assertFalse(action.isVisible()),
                () -> assertFalse(action.isManaged()),
                () -> assertEquals("no action is offered for this run", action.getAccessibleText()),
                () -> assertFalse(this.<Button>node(UiIds.LIMELIGHT_CONVERT).isDisabled()),
                () ->
                        assertTrue(
                                this.<Label>node(UiIds.LIMELIGHT_FASTA)
                                        .getText()
                                        .endsWith(
                                                Path.of("inputs/subset.fasta")
                                                        .toAbsolutePath()
                                                        .toString())));

        port.save(
                run ->
                        new LimelightRunState(
                                state.readiness(), new LimelightOptions("0.05", false, "", false)));
        FxToolkit.onFxThread(
                () -> {
                    cutoff.setText("0.05");
                    cutoff.fireEvent(new javafx.event.ActionEvent());
                });
        assertEquals(
                List.of(new LimelightOptions("0.05", false, "", false)),
                port.saves().stream().map(ScriptedLimelight.Save::options).toList());
        FxToolkit.onFxThread(decoys::fire);
        assertAll(
                () -> assertFalse(decoys.isSelected(), "a disabled control does not fire"),
                () -> assertEquals(1, port.saves().size()),
                () -> assertFalse(section.importDecoysProperty().get()));

        port.convert(
                run ->
                        new LimelightResult(
                                Path.of("out.xml").toAbsolutePath(),
                                12,
                                10,
                                "m",
                                "s".repeat(64),
                                "0.05",
                                Optional.empty()),
                "a line");
        FxToolkit.onFxThread(() -> this.<Button>node(UiIds.LIMELIGHT_CONVERT).fire());
        assertAll(
                () -> assertEquals("a line", this.<TextArea>node(UiIds.LIMELIGHT_LOG).getText()),
                () -> assertEquals("12", this.<Label>node(UiIds.LIMELIGHT_RESULT_PSMS).getText()),
                () ->
                        assertEquals(
                                "s".repeat(64),
                                this.<Label>node(UiIds.LIMELIGHT_RESULT_SHA256).getText()),
                () ->
                        assertEquals(
                                Path.of("out.xml").toAbsolutePath().toString(),
                                this.<Label>node(UiIds.LIMELIGHT_RESULT_FILE).getText()),
                () ->
                        assertTrue(
                                this.<Label>node(UiIds.LIMELIGHT_OUTCOME)
                                        .getText()
                                        .startsWith("Run run-1 was converted to valid Limelight")));
    }

    @Test
    @DisplayName("every identified control has a name of its own, and the upload area is there")
    void everyControlIsNamed() throws InterruptedException {
        open(
                new LimelightRunState(
                        readiness(
                                new LimelightCompatibility(
                                        RUN, Optional.empty(), Optional.empty(), Optional.empty()),
                                Optional.of(NO_DECOYS)),
                        new LimelightOptions("0.01", false, "", false)));
        LimelightPane pane = node(UiIds.LIMELIGHT_PANE);
        List<String> unnamed =
                FxToolkit.callOnFxThread(
                        () -> {
                            List<String> found = new ArrayList<>();
                            collect(pane, found);
                            return found;
                        });
        assertAll(
                () -> assertEquals(List.of(), unnamed),
                () -> assertNotNull(node(UiIds.LIMELIGHT_UPLOAD)),
                () ->
                        assertEquals(
                                "Uploading Limelight XML to a Limelight server is not available"
                                        + " yet.",
                                this.<Label>node(UiIds.LIMELIGHT_UPLOAD_PENDING).getText()));
        Label upload = new Label("the upload half");
        upload.setId("upload-half");
        FxToolkit.onFxThread(() -> pane.showUpload(upload));
        VBox area = node(UiIds.LIMELIGHT_UPLOAD);
        assertAll(
                () -> assertEquals(2, area.getChildren().size()),
                () -> assertSame(upload, area.getChildren().get(1)),
                () -> assertTrue(scene.lookup("#" + UiIds.LIMELIGHT_UPLOAD_PENDING) == null));
    }

    private static void collect(Node node, List<String> unnamed) {
        if (node instanceof Control control && control.getId() != null) {
            String name = control.getAccessibleText();
            if (name == null || name.isBlank() || AccessibleControls.hasGeneratedName(control)) {
                unnamed.add(control.getId());
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, unnamed);
            }
        }
    }

    /** A Results port listing one run, whose stores this test never needs. */
    private static final class OneRun implements ResultsPort {

        @Override
        public List<ResultsRun> runs() {
            return List.of(
                    new ResultsRun(
                            RUN,
                            Instant.parse("2026-10-12T08:00:00Z"),
                            AttemptOutcome.SUCCEEDED,
                            EnumSet.of(TableKind.TARGET_PSMS),
                            false));
        }

        @Override
        public OpenedResults open(RunId run) throws IOException {
            throw new IOException("no stores in this test");
        }

        @Override
        public void close(OpenedResults opened) {
            // nothing is opened
        }

        @Override
        public void saveViewState(RunId run, DisplayFilters filters) {
            // nothing is saved
        }

        @Override
        public TableExport exportTable(
                RunId run, TableKind kind, QValueFilter filter, Category category)
                throws IOException {
            throw new IOException("no exports");
        }

        @Override
        public WeightsExport exportWeights(RunId run, WeightsSummary weights) throws IOException {
            throw new IOException("no exports");
        }
    }
}
