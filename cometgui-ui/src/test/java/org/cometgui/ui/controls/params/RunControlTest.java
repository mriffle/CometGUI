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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.cometgui.domain.params.PreRunFacts;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.Editors.KnownFiles;
import org.cometgui.ui.testing.Editors.ScriptedChooser;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.testing.ScriptedEngine;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.ui.viewmodel.params.EngineCheck;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.RunViewModel;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.cometgui.workflow.steps.CometWorkflow;
import org.cometgui.workflow.steps.PreRunReport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Run control over its view-models: what it shows for each state, that Run and Cancel only call
 * the view-model (the engine is reached on the background executor, never from the control), and
 * that it adds no tab stop of its own (the Run navigation entry carries the reasons for the
 * keyboard).
 */
class RunControlTest {

    private final ScriptedEngine engine =
            new ScriptedEngine(model -> EngineCheck.unavailable("Comet 2026.03.0 is missing."));

    private Scene scene;

    private ParameterSession session;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @BeforeEach
    void build() throws InterruptedException {
        session = Editors.session();
        ScriptedChooser chooser = new ScriptedChooser();
        SpectrumInputsViewModel inputs = Editors.inputs(session, chooser, new KnownFiles());
        ParameterEditorViewModel editor = Editors.editor(session, inputs, chooser);
        RunViewModel run =
                Editors.run(session, inputs, editor, new StageStepperViewModel(), engine);
        engine.announce(CometWorkflow.planFor(IndexMode.NONE), "run run-1 in /p/runs/run-1");
        FxToolkit.onFxThread(
                () -> {
                    scene = new Scene(new RunControl(editor.readiness(), run), 900, 400);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    run.recheck();
                });
    }

    private void settle() throws InterruptedException {
        FxToolkit.onFxThread(engine::settle);
    }

    private String text(String id) throws InterruptedException {
        return FxToolkit.callOnFxThread(() -> ((Label) scene.lookup("#" + id)).getText());
    }

    private boolean disabled(String id) throws InterruptedException {
        return FxToolkit.callOnFxThread(() -> scene.lookup("#" + id).isDisabled());
    }

    @Test
    @DisplayName(
            "checking, then the engine's reason: Run disabled, every reason in text, and no"
                    + " tab stop of its own")
    void reasonsShown() throws InterruptedException {
        assertAll(
                "while checking",
                () -> assertTrue(disabled("run-start")),
                () -> assertTrue(disabled("run-cancel")),
                () ->
                        assertEquals(
                                "The workflow engine cannot start this search:\n"
                                        + RunViewModel.CHECKING,
                                text("run-engine")),
                () -> assertEquals("No run has started in this session.", text("run-outcome")),
                () ->
                        assertEquals(
                                "The rerun preview is shown when the pre-run check has"
                                        + " finished.",
                                text("run-preview")));
        settle();
        assertAll(
                "answered",
                () -> assertTrue(disabled("run-start")),
                () ->
                        assertEquals(
                                "The workflow engine cannot start this search:\n"
                                        + "Comet 2026.03.0 is missing.",
                                text("run-engine")),
                () -> assertEquals("The parameters do not block a run.", text("run-parameters")),
                () ->
                        assertEquals(
                                "Comet 2026.03.0 is missing.",
                                FxToolkit.callOnFxThread(
                                        () -> scene.lookup("#run-start").getAccessibleHelp())),
                () ->
                        assertEquals(
                                List.of(false, false, false),
                                FxToolkit.callOnFxThread(
                                        () ->
                                                List.of(
                                                        scene.lookup("#run-engine")
                                                                .isFocusTraversable(),
                                                        scene.lookup("#run-outcome")
                                                                .isFocusTraversable(),
                                                        scene.lookup("#run-preview")
                                                                .isFocusTraversable())),
                                "no tab stop of its own: the navigation entry carries the reasons"
                                        + " for the keyboard (ShellView)"));
    }

    @Test
    @DisplayName("Run and Cancel only ask the view-model; the engine is reached on its executor")
    void actionsGoThroughTheViewModel() throws InterruptedException {
        engine.answer(
                model ->
                        EngineCheck.checked(
                                new PreRunReport(
                                        List.of(),
                                        new ValidationReport(List.of()),
                                        PreRunFacts.none()),
                                Optional.empty()));
        FxToolkit.onFxThread(() -> session.edit("fragment_bin_tol", "1.0005"));
        settle();
        assertFalse(disabled("run-start"));
        assertEquals("The workflow engine can run this search.", text("run-engine"));

        FxToolkit.onFxThread(() -> ((Button) scene.lookup("#run-start")).fire());
        assertEquals(List.of(), engine.started(), "nothing on the interface thread");
        assertTrue(disabled("run-start"));
        settle();
        assertEquals(1, engine.started().size());
        assertFalse(disabled("run-cancel"));
        assertEquals("Running: run run-1 in /p/runs/run-1.", text("run-outcome"));

        FxToolkit.onFxThread(() -> ((Button) scene.lookup("#run-cancel")).fire());
        assertEquals(0, engine.cancels(), "nothing on the interface thread");
        assertTrue(disabled("run-cancel"));
        settle();
        assertEquals(1, engine.cancels());
        assertEquals("Cancelling the run: run run-1 in /p/runs/run-1.", text("run-outcome"));
    }
}
