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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.viewmodel.params.ChoiceOption;
import org.cometgui.ui.viewmodel.params.EditorMode;
import org.cometgui.ui.viewmodel.params.ExpertViewModel;
import org.cometgui.ui.viewmodel.params.FieldViewModel;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSearchViewModel;
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

    private ExpertViewModel expert;

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
        expert = Editors.expert(session, editor);
        ParameterSearchViewModel search = new ParameterSearchViewModel(session);
        FxToolkit.onFxThread(
                () -> {
                    scene =
                            new Scene(
                                    new CometParametersView(
                                            session, editor, inputs, mods, search, expert),
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
                    assertNull(node("param-expert-placeholder"), "the Expert pane, not a label");
                    assertNull(node("ess-preset-placeholder"), "the preset choice, not a label");
                    assertNotNull(node("param-expert-draft"));
                    assertFalse(node("param-expert").isVisible());
                    assertNotNull(node("ess-preset-choice"));
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

    /** The canonical text with one line (1-based) replaced. */
    private static String replaceLine(String text, int number, String line) {
        String[] lines = text.split("\n", -1);
        lines[number - 1] = line;
        return String.join("\n", lines);
    }

    @Test
    @DisplayName("the custom-enzyme editor adds a row the selectors offer, and keeps a used one")
    void customEnzyme() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    editor.setMode(EditorMode.ADVANCED);
                    settle();
                    assertEquals("12", ((TextField) node("adv-enzyme-new-number")).getText());
                    ((TextField) node("adv-enzyme-new-name")).setText("Custom_AspN");
                    ((TextField) node("adv-enzyme-new-cut")).setText("D");
                    ((TextField) node("adv-enzyme-new-nocut")).setText("-");
                    @SuppressWarnings("unchecked")
                    ComboBox<ChoiceOption> sense =
                            (ComboBox<ChoiceOption>) node("adv-enzyme-new-sense");
                    sense.setValue(sense.getItems().get(1));
                    ((Button) node("adv-enzyme-add")).fire();
                    settle();
                    assertEquals(
                            "Added enzyme 12. Custom_AspN.",
                            ((Label) node("adv-enzyme-status")).getText());
                    assertTrue(
                            ((Label) node("adv-enzyme-row-12"))
                                    .getText()
                                    .startsWith("12. Custom_AspN -- "),
                            ((Label) node("adv-enzyme-row-12")).getText());
                    assertEquals("13", ((TextField) node("adv-enzyme-new-number")).getText());
                    assertTrue(
                            ((ComboBox<?>) node("adv-search_enzyme_number"))
                                    .getItems().stream()
                                            .anyMatch(o -> o.toString().contains("Custom_AspN")));
                    ((Button) node("adv-enzyme-row-1-remove")).fire();
                    assertTrue(
                            ((Label) node("adv-enzyme-status"))
                                    .getText()
                                    .startsWith("Not removed: 1. Trypsin is selected as "),
                            ((Label) node("adv-enzyme-status")).getText());
                    ((Button) node("adv-enzyme-row-12-remove")).fire();
                    settle();
                    assertNull(node("adv-enzyme-row-12"));
                });
    }

    @Test
    @DisplayName("Expert: a failed apply changes nothing and names the line; a good one confirms")
    void expertApply() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    editor.setMode(EditorMode.EXPERT);
                    settle();
                    assertTrue(node("param-expert").isVisible());
                    TextArea draft = (TextArea) node("param-expert-draft");
                    assertEquals(expert.canonicalText(), draft.getText());
                    var before = session.model();
                    draft.setText(replaceLine(draft.getText(), 8, "num_threads 0"));
                    settle();
                    String malformed =
                            "Error, line 8: line 8 is not a comment, a declaration or an enzyme"
                                    + " row: not a comment, a blank line or a declaration: there"
                                    + " is no '=' before any '#': \"num_threads 0\"";
                    assertEquals(malformed, ((Button) node("param-expert-diagnostic-0")).getText());
                    assertEquals(
                            "Line 8, not readable -- " + malformed,
                            ((Label) node("param-expert-line-8")).getText());
                    ((Button) node("param-expert-diagnostic-0")).fire();
                    assertSame(draft, scene.getFocusOwner());
                    assertEquals(expert.lineStart(8), draft.getCaretPosition());

                    ((Button) node("param-expert-apply")).fire();
                    settle();
                    assertSame(before, session.model());
                    assertEquals(
                            "The draft was not applied; the configuration is unchanged.\n"
                                    + malformed,
                            ((Label) node("param-expert-apply-status")).getText());
                    assertEquals(
                            "Offending lines:\nLine 8: num_threads 0",
                            ((Label) node("param-expert-offending")).getText());
                    assertFalse(node("param-expert-confirmation").isVisible());

                    draft.setText(replaceLine(draft.getText(), 8, "num_threads = 6"));
                    ((Button) node("param-expert-apply")).fire();
                    settle();
                    assertSame(before, session.model(), "nothing changes before confirming");
                    assertTrue(node("param-expert-confirmation").isVisible());
                    assertEquals(
                            "Applying changes 1 value:\nSearch threads (num_threads): 0 -> 6",
                            ((Label) node("param-expert-changes")).getText());
                    ((Button) node("param-expert-confirm")).fire();
                    settle();
                    assertEquals("6", session.model().text("num_threads"));
                    assertEquals(
                            "Applied: the configuration now holds the draft.",
                            ((Label) node("param-expert-apply-status")).getText());
                    assertEquals(expert.canonicalText(), draft.getText());
                    assertEquals(
                            "Compared with Comet 2026.03.0's defaults: 2 differences (this"
                                    + " configuration, then the other).\nSearch threads"
                                    + " (num_threads): 6 / 0\nWrite Percolator input (PIN)"
                                    + " (output_percolatorfile): 1 / 0",
                            ((Label) node("param-expert-compare-rows")).getText());
                    assertEquals(
                            "Compared with the last saved configuration: Nothing has been saved"
                                    + " yet.",
                            ((Label) node("param-expert-saved-rows")).getText());
                });
    }

    @Test
    @DisplayName("Expert lists an imported unknown parameter, and removes it on request")
    void unknownParameters() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    editor.files()
                            .importText(
                                    expert.canonicalText()
                                            .replace(
                                                    "\npeff_format = ",
                                                    "\nmystery_setting = 7\npeff_format = "),
                                    "mystery.params");
                    editor.setMode(EditorMode.EXPERT);
                    settle();
                    assertEquals(
                            "1 unknown parameter is kept as imported and written back unless"
                                    + " removed.",
                            ((Label) node("param-expert-unknown-headline")).getText());
                    assertTrue(
                            ((Label) node("param-expert-unknown-0"))
                                    .getText()
                                    .startsWith("mystery_setting = 7 -- WARNING: "),
                            ((Label) node("param-expert-unknown-0")).getText());
                    ((Button) node("param-expert-unknown-0-remove")).fire();
                    settle();
                    assertEquals(List.of(), session.model().unknownParameters());
                    assertNull(node("param-expert-unknown-0"));
                    assertEquals(
                            "Removed mystery_setting.",
                            ((Label) node("param-expert-unknown-headline")).getText());
                });
    }

    @Test
    @DisplayName("a preset preview changes nothing; a subset applies exactly that subset")
    void presetPreview() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    var before = session.model();
                    @SuppressWarnings("unchecked")
                    ComboBox<org.cometgui.params.comet.presets.Preset> choice =
                            (ComboBox<org.cometgui.params.comet.presets.Preset>)
                                    node("ess-preset-choice");
                    choice.setValue(choice.getItems().get(0));
                    ((Button) node("ess-preset-preview")).fire();
                    settle();
                    assertSame(before, session.model());
                    assertTrue(node("ess-preset-review").isVisible());
                    assertEquals(
                            "Precursor tolerance, upper bound (peptide_mass_tolerance_upper)",
                            ((CheckBox) node("ess-preset-row-0")).getText());
                    assertEquals("20.0", ((Label) node("ess-preset-row-0-current")).getText());
                    assertEquals("3.0", ((Label) node("ess-preset-row-0-preset")).getText());
                    assertNull(node("ess-preset-row-8"));
                    ((Button) node("ess-preset-cancel")).fire();
                    settle();
                    assertSame(before, session.model());
                    assertFalse(node("ess-preset-review").isVisible());
                    assertEquals(
                            "Cancelled: nothing was changed.",
                            ((Label) node("ess-preset-status")).getText());

                    ((Button) node("ess-preset-preview")).fire();
                    settle();
                    for (int row = 1; row < 8; row++) {
                        if (row != 5) {
                            ((CheckBox) node("ess-preset-row-" + row)).fire();
                        }
                    }
                    ((Button) node("ess-preset-apply-selected")).fire();
                    settle();
                    assertEquals("3.0", session.model().text("peptide_mass_tolerance_upper"));
                    assertEquals("1.0005", session.model().text("fragment_bin_tol"));
                    assertEquals("-20.0", session.model().text("peptide_mass_tolerance_lower"));
                    assertEquals("0.0", session.model().text("fragment_bin_offset"));
                    assertEquals(
                            "Applied 2 changes of Low-res precursor, low-res fragments: Precursor"
                                    + " tolerance, upper bound (peptide_mass_tolerance_upper) 20.0"
                                    + " -> 3.0; Fragment bin width (fragment_bin_tol) 0.02 ->"
                                    + " 1.0005.",
                            ((Label) node("ess-preset-status")).getText());
                });
    }

    @Test
    @DisplayName("a search result says why it matched, and opens the field on Advanced")
    void search() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    assertNull(node("param-search-result-0"));
                    assertEquals(
                            "Type to find a parameter, or tick a filter.",
                            ((Label) node("param-search-headline")).getText());
                    ((TextField) node("param-search")).setText("semi-tryptic");
                    settle();
                    assertEquals(
                            "1 parameter found.",
                            ((Label) node("param-search-headline")).getText());
                    Button hit = (Button) node("param-search-result-0");
                    assertTrue(
                            hit.getText().endsWith(" -- Matched by alias \"semi-tryptic\""),
                            hit.getText());
                    hit.fire();
                    settle();
                    assertEquals(EditorMode.ADVANCED, editor.mode());
                    assertEquals("adv-num_enzyme_termini", scene.getFocusOwner().getId());
                    assertTrue(
                            ((ToggleButton) node("adv-category-digestion_enzymes-toggle"))
                                    .isSelected());
                    ((CheckBox) node("param-search-filter-modified")).fire();
                    ((TextField) node("param-search")).setText("");
                    settle();
                    assertEquals(
                            "Write Percolator input (PIN) (output_percolatorfile) -- Listed by the"
                                    + " filters",
                            ((Button) node("param-search-result-0")).getText());
                    assertNull(node("param-search-result-1"));
                });
    }

    @Test
    @DisplayName("an imported file of the older release waits, then migrates under review")
    void importAndReview() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    assertFalse(node("param-import-offer").isVisible());
                    assertFalse(node("param-migration").isVisible());
                    String older =
                            new String(
                                    org.cometgui.params.comet.parser.ReleaseDefaults.bundledFile(
                                            ToolVersion.parse("2026.02.2")),
                                    java.nio.charset.StandardCharsets.UTF_8);
                    older =
                            older.replace(
                                    "variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0",
                                    "variable_mod01 = 15.9949 M 0 3 2 4 0 0.0");
                    editor.files().importText(older, "older.params");
                    settle();
                    assertTrue(node("param-import-offer").isVisible());
                    assertTrue(
                            ((Label) node("param-import-question"))
                                    .getText()
                                    .startsWith(
                                            "older.params was written for Comet 2026.02.2; the"
                                                    + " editor is set to Comet 2026.03.0."));
                    ((Button) node("param-import-migrate")).fire();
                    settle();
                    assertFalse(node("param-import-offer").isVisible());
                    assertTrue(node("param-migration").isVisible());
                    assertEquals(
                            "Migration review: Comet 2026.02.2 -> 2026.03.0: 118 parameters, 2"
                                    + " changed, 116 unchanged; 1 needs your decision.",
                            ((Label) node("param-migration-headline")).getText());
                    assertEquals(
                            "Blocks the run until you decide.",
                            ((Label) node("param-migration-row-1-state")).getText());
                    assertTrue(editor.readiness().parametersBlockRun());
                    ((Button) node("param-migration-row-1-accept")).fire();
                    settle();
                    assertEquals(
                            "Resolved: you accepted the value it holds.",
                            ((Label) node("param-migration-row-1-state")).getText());
                    assertNull(node("param-migration-row-1-accept"));
                    assertFalse(editor.readiness().parametersBlockRun());
                });
    }
}
