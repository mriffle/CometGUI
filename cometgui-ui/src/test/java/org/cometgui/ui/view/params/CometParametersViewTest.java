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

package org.cometgui.ui.view.params;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.viewmodel.params.EditorMode;
import org.cometgui.ui.viewmodel.params.FieldViewModel;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.cometgui.ui.viewmodel.params.VariableModsViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The editor view, built headless over a real editor: every parameter of the release has its
 * control on Advanced, the levels follow the release, a summary entry moves the focus to its field,
 * and the destructive resets ask before they act. Identifiers are typed out (P7-5); the GUI gate
 * tests in {@code cometgui-app} drive the same view through the launched application.
 */
class CometParametersViewTest {

    private ParameterSession session;

    private ParameterEditorViewModel editor;

    private Stage stage;

    private Scene scene;

    @BeforeAll
    static void toolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @BeforeEach
    void build() throws InterruptedException {
        session = Editors.session();
        Editors.ScriptedChooser chooser = new Editors.ScriptedChooser();
        SpectrumInputsViewModel inputs = Editors.inputs(session, chooser, new Editors.KnownFiles());
        editor = Editors.editor(session, inputs, chooser);
        VariableModsViewModel mods = new VariableModsViewModel(session);
        FxToolkit.onFxThread(
                () -> {
                    scene =
                            new Scene(
                                    new CometParametersView(session, editor, inputs, mods),
                                    1280,
                                    800);
                    stage = new Stage();
                    stage.setScene(scene);
                    stage.show();
                    settle();
                });
    }

    @AfterEach
    void close() throws InterruptedException {
        FxToolkit.onFxThread(stage::close);
    }

    private void settle() {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }

    private Node node(String id) {
        return scene.lookup("#" + id);
    }

    @Test
    @DisplayName("Advanced has a control for every parameter of the release, Essentials its groups")
    void everyParameterHasAControl() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    List<String> missing = new ArrayList<>();
                    for (FieldViewModel field : session.fields()) {
                        if (node("adv-" + field.name()) == null) {
                            missing.add(field.name());
                        }
                    }
                    assertEquals(List.of(), missing);
                    assertEquals(118, session.fields().size());
                    for (String group :
                            List.of(
                                    "ess-group-inputs",
                                    "ess-group-search-preset",
                                    "ess-group-precursor",
                                    "ess-group-fragment",
                                    "ess-group-digestion",
                                    "ess-group-static-modifications",
                                    "ess-group-variable-modifications",
                                    "ess-group-decoys",
                                    "ess-group-execution",
                                    "ess-group-outputs")) {
                        assertNotNull(node(group), group);
                    }
                    assertNull(node("ess-activation_method"), "not an Essentials parameter");
                    assertTrue(node("param-essentials").isVisible());
                    assertFalse(node("param-advanced").getParent().isVisible());
                    assertEquals(
                            "Expert level: not built yet. It will show the canonical comet.params"
                                    + " text with line diagnostics, diffs and a validating apply;"
                                    + " until then, use Essentials and Advanced.",
                            ((Label) node("param-expert-placeholder")).getText());
                });
    }

    @Test
    @DisplayName("a release switch rebuilds both levels for the new release")
    void releaseSwitchRebuilds() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    Node before = node("adv-index_search_type");
                    assertNotNull(node("ess-variable_mod01-terminus-protein-n"));
                    editor.selectRelease(ToolVersion.parse("2026.02.2"));
                    settle();
                    Node after = node("adv-index_search_type");
                    assertNotNull(after);
                    assertNotSame(before, after, "a new control for the new release's field");
                    assertNull(node("ess-variable_mod01-terminus-protein-n"), "no ^ on 2026.02.2");
                    assertEquals(
                            "Comet 2026.02.2 is selected. Migrated: Comet 2026.03.0 -> 2026.02.2:"
                                    + " 118 parameters, 1 changed, 117 unchanged; 0 need your"
                                    + " decision.",
                            ((Label) node("param-release-status")).getText());
                });
    }

    @Test
    @DisplayName(
            "a summary entry moves the focus to its field, on Advanced with its category shown")
    void summaryEntryMovesTheFocus() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    session.edit("peptide_mass_tolerance_lower", "10");
                    session.edit("peptide_mass_tolerance_upper", "-10");
                    settle();
                    ((Button) node("param-summary-entry-0")).fire();
                    assertEquals("ess-peptide_mass_tolerance_lower", scene.getFocusOwner().getId());

                    session.edit("peptide_mass_tolerance_upper", "20.0");
                    session.edit("peptide_mass_tolerance_lower", "-20.0");
                    session.edit("activation_method", "HCD");
                    session.edit("ms_level", "nine");
                    settle();
                    assertTrue(
                            ((Button) node("param-summary-entry-0"))
                                    .getText()
                                    .startsWith("Not applied -- MS level searched (ms_level)"));
                    ((Button) node("param-summary-entry-0")).fire();
                    settle();
                    ToggleButton spectrumFilters =
                            (ToggleButton) node("adv-category-spectrum_filters-toggle");
                    assertAll(
                            () -> assertEquals(EditorMode.ADVANCED, editor.mode()),
                            () -> assertEquals("adv-ms_level", scene.getFocusOwner().getId()),
                            () -> assertTrue(spectrumFilters.isSelected()),
                            () -> assertTrue(node("adv-ms_level").isVisible()));
                });
    }

    @Test
    @DisplayName(
            "a category reset asks first: cancel keeps every value, confirm resets the category")
    void categoryResetAsks() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    session.edit("num_output_lines", "9");
                    settle();
                    Button reset = (Button) node("adv-category-output-reset");
                    Button confirm = (Button) node("adv-category-output-reset-confirm");
                    Button cancel = (Button) node("adv-category-output-reset-cancel");
                    assertFalse(confirm.isVisible());
                    reset.fire();
                    assertTrue(confirm.isVisible());
                    assertFalse(reset.isVisible());
                    assertEquals(
                            "Reset 11 parameters to the Comet 2026.03.0 defaults",
                            confirm.getText());
                    cancel.fire();
                    assertEquals("9", session.model().text("num_output_lines"));
                    assertTrue(reset.isVisible());
                    reset.fire();
                    confirm.fire();
                    assertEquals("5", session.model().text("num_output_lines"));
                    assertEquals(
                            "1",
                            session.model().text("output_percolatorfile"),
                            "the locked output keeps its enforced value");
                    assertTrue(((CheckBox) node("adv-output_percolatorfile")).isSelected());
                });
    }

    @Test
    @DisplayName("starting again asks first: cancel keeps the configuration, confirm resets it")
    void resetAllAsks() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    session.edit("decoy_prefix", "rev_");
                    settle();
                    Button resetAll = (Button) node("param-reset-all");
                    resetAll.fire();
                    ((Button) node("param-reset-all-cancel")).fire();
                    assertEquals("rev_", session.model().text("decoy_prefix"));
                    resetAll.fire();
                    ((Button) node("param-reset-all-confirm")).fire();
                    assertEquals("DECOY_", session.model().text("decoy_prefix"));
                    assertEquals("DECOY_", ((TextField) node("ess-decoy_prefix")).getText());
                });
    }

    @Test
    @DisplayName("a typed value is committed on Enter and a refused one is stated at the field")
    void typedValues() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    TextField missed = (TextField) node("ess-allowed_missed_cleavage");
                    missed.setText("1");
                    missed.fireEvent(new javafx.event.ActionEvent());
                    assertEquals("1", session.model().text("allowed_missed_cleavage"));
                    missed.setText("many");
                    missed.fireEvent(new javafx.event.ActionEvent());
                    assertEquals("1", session.model().text("allowed_missed_cleavage"));
                    String state = ((Label) node("ess-allowed_missed_cleavage-state")).getText();
                    assertTrue(
                            state.startsWith(
                                    "Error: not applied, the configuration still holds \"1\"."),
                            state);
                    assertEquals(
                            state, ((Label) node("adv-allowed_missed_cleavage-state")).getText());
                    assertTrue(missed.getAccessibleHelp().contains("Validation: " + state));
                });
    }
}
