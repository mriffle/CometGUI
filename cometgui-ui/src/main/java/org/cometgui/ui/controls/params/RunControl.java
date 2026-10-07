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

package org.cometgui.ui.controls.params;

import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.RunReadinessViewModel;
import org.cometgui.ui.viewmodel.params.RunViewModel;

/**
 * The Run section's Run and Cancel controls (decisions P7-6, P8-16): Run is enabled only when
 * neither the parameters nor the workflow engine has a reason against it, with every reason shown
 * as text beside it -- the parameters' first, then the engine's; Cancel is enabled only while a
 * started run can be cancelled. Below them, the outcome of the last run in words and the rerun
 * preview of the next one ({@code R-RUN-01}).
 *
 * <p>Both actions only call the view-model, which does the engine's work off the JavaFX thread.
 */
public final class RunControl extends VBox {

    private final RunReadinessViewModel readiness;

    private final RunViewModel engine;

    private final Button run;

    private final Button cancel;

    private final Label parameters;

    private final Label engineReasons;

    /**
     * The Run control.
     *
     * @param readiness whether a run may start, and why not
     * @param engine the engine half: Run, Cancel, the outcome and the rerun preview
     */
    public RunControl(RunReadinessViewModel readiness, RunViewModel engine) {
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.engine = Objects.requireNonNull(engine, "engine");
        setSpacing(4);
        run = Texts.button(UiIds.RUN_START, "Run", "Run the search");
        cancel = Texts.button(UiIds.RUN_CANCEL, "Cancel", "Cancel the running search");
        run.setOnAction(event -> engine.start());
        cancel.setOnAction(event -> engine.cancel());
        parameters = Texts.label(UiIds.RUN_PARAMETERS, "", "the parameters' readiness");
        engineReasons = Texts.label(UiIds.RUN_ENGINE, "", "the workflow engine's readiness");
        Label outcome = Texts.label(UiIds.RUN_OUTCOME, engine.outcome(), "the run's outcome");
        Label preview = Texts.label(UiIds.RUN_PREVIEW, engine.preview(), "the rerun preview");
        getChildren().addAll(new HBox(8, run, cancel), parameters, engineReasons, outcome, preview);
        readiness.runEnabledProperty().addListener((observable, before, after) -> refresh());
        readiness.blockingReasonsProperty().addListener((observable, before, after) -> refresh());
        readiness.engineReasonsProperty().addListener((observable, before, after) -> refresh());
        readiness.reasonsTextProperty().addListener((observable, before, after) -> refresh());
        engine.cancelEnabledProperty().addListener((observable, before, after) -> refresh());
        engine.outcomeProperty().addListener((observable, before, after) -> outcome.setText(after));
        engine.previewProperty().addListener((observable, before, after) -> preview.setText(after));
        refresh();
    }

    private void refresh() {
        run.setDisable(!readiness.runEnabled());
        cancel.setDisable(!engine.cancelEnabled());
        parameters.setText(
                readiness.parametersBlockRun()
                        ? "The parameters block a run:\n"
                                + String.join("\n", readiness.blockingReasons())
                        : "The parameters do not block a run.");
        engineReasons.setText(
                readiness.engineReasons().isEmpty()
                        ? "The workflow engine can run this search."
                        : "The workflow engine cannot start this search:\n"
                                + String.join("\n", readiness.engineReasons()));
        run.setAccessibleHelp(readiness.reasonsText());
    }
}
