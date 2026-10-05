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
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.RunReadinessViewModel;

/**
 * The Run section's Run control (decision P7-6): enabled only when neither the parameters nor the
 * workflow engine has a reason against it, and with every reason shown as text beside it -- the
 * parameters' first, then the engine's, which is present until the workflow engine exists, so the
 * control never pretends it could start a run.
 */
public final class RunControl extends VBox {

    private final RunReadinessViewModel readiness;

    private final Button run;

    private final Label parameters;

    private final Label engine;

    /**
     * The Run control.
     *
     * @param readiness whether a run may start, and why not
     */
    public RunControl(RunReadinessViewModel readiness) {
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        setSpacing(4);
        run = Texts.button(UiIds.RUN_START, "Run", "Run the search");
        parameters = Texts.label(UiIds.RUN_PARAMETERS, "", "the parameters' readiness");
        engine = Texts.label(UiIds.RUN_ENGINE, "", "the workflow engine's readiness");
        getChildren().addAll(run, parameters, engine);
        readiness.runEnabledProperty().addListener((observable, before, after) -> refresh());
        readiness.blockingReasonsProperty().addListener((observable, before, after) -> refresh());
        refresh();
    }

    private void refresh() {
        run.setDisable(!readiness.runEnabled());
        parameters.setText(
                readiness.parametersBlockRun()
                        ? "The parameters block a run:\n"
                                + String.join("\n", readiness.blockingReasons())
                        : "The parameters do not block a run.");
        engine.setText(readiness.engineReason().orElse("The workflow engine can run."));
        run.setAccessibleHelp(readiness.reasonsText());
    }
}
