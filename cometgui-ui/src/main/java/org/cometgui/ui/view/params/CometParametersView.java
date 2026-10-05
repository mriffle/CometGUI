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

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.params.ExpertPane;
import org.cometgui.ui.controls.params.ImportControl;
import org.cometgui.ui.controls.params.MigrationReviewPane;
import org.cometgui.ui.controls.params.ParameterSearchPane;
import org.cometgui.ui.controls.params.Subscriptions;
import org.cometgui.ui.controls.params.ValidationSummaryPane;
import org.cometgui.ui.viewmodel.params.EditorMode;
import org.cometgui.ui.viewmodel.params.ExpertViewModel;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSearchViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.SearchHit;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.cometgui.ui.viewmodel.params.VariableModsViewModel;

/**
 * The Comet parameter editor, as the Comet Parameters section shows it.
 *
 * <ul>
 *   <li>A <strong>release selector</strong> over the offered releases, the default first; a switch
 *       migrates the configuration, and a refused switch says why in text beside it.
 *   <li>A <strong>level switch</strong> -- Essentials, Advanced, Expert -- with the save action and
 *       the reset of the whole configuration (which asks to confirm); below it, the import of a
 *       parameter file and the choice a file for another release waits for.
 *   <li>The <strong>validation summary</strong>, at the top: the counts in words and one focusable
 *       entry per finding; activating an entry shows the level and category holding its field and
 *       moves the keyboard focus there.
 *   <li>The <strong>migration review</strong>, while a migrated configuration is under review: one
 *       row per change, the ones needing a decision marked as blocking the run.
 *   <li>The <strong>parameter search</strong> with its five filters; activating a result shows the
 *       parameter's field on the Advanced level and moves the focus there.
 *   <li>The <strong>body</strong>: the Essentials level, the Advanced level and the Expert level,
 *       exactly one shown.
 * </ul>
 *
 * <p>Changing release gives the session new fields, so the two levels are rebuilt for the new
 * release -- when the session publishes the new configuration, so that every new control reads the
 * release it was built for. The previous build's listeners are removed first.
 */
public final class CometParametersView extends VBox {

    private final ParameterEditorViewModel editor;

    private final ParameterSession session;

    private final SpectrumInputsViewModel inputs;

    private final VariableModsViewModel mods;

    private final VBox essentialsHolder = new VBox();

    private final VBox advancedHolder = new VBox();

    private final VBox expert = new VBox(6);

    private final ExpertPane expertPane;

    private final ScrollPane body = new ScrollPane();

    private final ComboBox<ToolVersion> release = new ComboBox<>();

    private final Map<EditorMode, ToggleButton> modes = new EnumMap<>(EditorMode.class);

    private final VBox levels = new VBox();

    private Subscriptions build = new Subscriptions();

    private EssentialsView essentials;

    private AdvancedView advanced;

    private ToolVersion builtFor;

    private boolean updating;

    /**
     * The editor.
     *
     * @param session the configuration being edited
     * @param editor the editor's own state, summary and readiness over the session
     * @param inputs the spectrum inputs over the session
     * @param mods the variable-modification view-model over the session
     * @param search the global parameter search over the session
     * @param expertMode the Expert level's view-model over the session, comparing with the editor's
     *     saves
     */
    public CometParametersView(
            ParameterSession session,
            ParameterEditorViewModel editor,
            SpectrumInputsViewModel inputs,
            VariableModsViewModel mods,
            ParameterSearchViewModel search,
            ExpertViewModel expertMode) {
        this.session = Objects.requireNonNull(session, "session");
        this.editor = Objects.requireNonNull(editor, "editor");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.mods = Objects.requireNonNull(mods, "mods");
        setId(UiIds.PARAM_EDITOR);
        setSpacing(8);
        setPadding(new Insets(4, 0, 0, 0));

        session.modelProperty().addListener((observable, before, after) -> follow());

        getChildren().addAll(releaseRow(), modeRow(), new ImportControl(editor));
        getChildren().add(new ValidationSummaryPane(editor.summary(), this::focusParameter));
        getChildren()
                .add(
                        new MigrationReviewPane(
                                editor.migrationReview(), session, this::focusParameter));
        getChildren().add(new ParameterSearchPane(search, this::openSearchHit));

        expert.setId(UiIds.PARAM_EXPERT);
        expertPane =
                new ExpertPane(expertMode, editor.files(), editor.presets().presets(), session);
        expert.getChildren().add(expertPane);

        levels.getChildren().addAll(essentialsHolder, advancedHolder, expert);
        body.setId(UiIds.PARAM_BODY);
        named(body, "Comet parameters");
        body.setContent(levels);
        body.setFitToWidth(true);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().add(body);

        rebuild();
        editor.modeProperty().addListener((observable, before, after) -> showMode());
        showMode();
    }

    private HBox releaseRow() {
        Label label = new Label("Comet release");
        named(label, "Comet release");
        release.setId(UiIds.PARAM_RELEASE);
        named(release, "Comet release");
        release.setAccessibleHelp(
                "The Comet release the configuration is for. Choosing another migrates the"
                        + " configuration to it.");
        label.setLabelFor(release);
        release.getItems().setAll(session.offeredReleases());
        release.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(ToolVersion version) {
                        if (version == null) {
                            return "";
                        }
                        boolean first = version.equals(session.offeredReleases().get(0));
                        return "Comet " + version.text() + (first ? " (default)" : "");
                    }

                    @Override
                    public ToolVersion fromString(String text) {
                        return null;
                    }
                });
        release.setValue(session.release());
        release.valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating && after != null) {
                                editor.selectRelease(after);
                                showRelease();
                            }
                        });
        Label status = new Label(editor.releaseStatus());
        status.setId(UiIds.PARAM_RELEASE_STATUS);
        status.setWrapText(true);
        named(status, editor.releaseStatus());
        status.textProperty().bind(editor.releaseStatusProperty());
        status.accessibleTextProperty().bind(editor.releaseStatusProperty());
        HBox row = new HBox(8, label, release, status);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox modeRow() {
        ToggleGroup group = new ToggleGroup();
        HBox switches = new HBox(4);
        switches.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label("Level");
        named(label, "Editor level");
        switches.getChildren().add(label);
        for (EditorMode mode : EditorMode.values()) {
            ToggleButton button = new ToggleButton(mode.words());
            button.setId(
                    switch (mode) {
                        case ESSENTIALS -> UiIds.PARAM_MODE_ESSENTIALS;
                        case ADVANCED -> UiIds.PARAM_MODE_ADVANCED;
                        case EXPERT -> UiIds.PARAM_MODE_EXPERT;
                    });
            named(button, AccessibleRole.RADIO_BUTTON, mode.words() + " level");
            button.setToggleGroup(group);
            button.setOnAction(
                    event -> {
                        editor.setMode(mode);
                        showMode();
                    });
            modes.put(mode, button);
            switches.getChildren().add(button);
        }

        Button save = new Button("Save parameter file...");
        save.setId(UiIds.PARAM_SAVE);
        named(save, "Save the configuration as a new parameter file");
        save.setOnAction(event -> editor.save());
        Label saveStatus = new Label(editor.saveStatus());
        saveStatus.setId(UiIds.PARAM_SAVE_STATUS);
        saveStatus.setWrapText(true);
        named(saveStatus, editor.saveStatus());
        saveStatus.textProperty().bind(editor.saveStatusProperty());
        saveStatus.accessibleTextProperty().bind(editor.saveStatusProperty());

        Button resetAll = new Button("Start again from defaults...");
        resetAll.setId(UiIds.PARAM_RESET_ALL);
        named(resetAll, "Start again from the selected release's defaults (asks to confirm)");
        Button confirm = new Button("Discard every change and start from the defaults");
        confirm.setId(UiIds.PARAM_RESET_ALL_CONFIRM);
        named(confirm, confirm.getText());
        Button cancel = new Button("Keep the configuration");
        cancel.setId(UiIds.PARAM_RESET_ALL_CANCEL);
        named(cancel, cancel.getText());
        Runnable ask = () -> confirming(true, resetAll, confirm, cancel);
        resetAll.setOnAction(event -> ask.run());
        cancel.setOnAction(
                event -> {
                    confirming(false, resetAll, confirm, cancel);
                    resetAll.requestFocus();
                });
        confirm.setOnAction(
                event -> {
                    session.resetAll();
                    confirming(false, resetAll, confirm, cancel);
                    resetAll.requestFocus();
                });
        confirming(false, resetAll, confirm, cancel);
        HBox actions = new HBox(8, save, resetAll, confirm, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);
        switches.getChildren().add(actions);
        HBox.setMargin(actions, new Insets(0, 0, 0, 24));
        return new VBox(4, switches, saveStatus);
    }

    private static void confirming(boolean asking, Button ask, Button confirm, Button cancel) {
        ask.setVisible(!asking);
        ask.setManaged(!asking);
        for (Button answer : List.of(confirm, cancel)) {
            answer.setVisible(asking);
            answer.setManaged(asking);
        }
        if (asking) {
            cancel.requestFocus();
        }
    }

    /** Rebuilds the two levels when the configuration is of another release than they were. */
    private void follow() {
        if (!session.release().equals(builtFor)) {
            rebuild();
        }
        showRelease();
    }

    private void rebuild() {
        build.dispose();
        build = new Subscriptions();
        builtFor = session.release();
        essentials = new EssentialsView(session, editor, inputs, mods, build);
        advanced = new AdvancedView(session, editor, mods, build);
        essentialsHolder.getChildren().setAll(essentials);
        advancedHolder.getChildren().setAll(advanced);
    }

    private void showRelease() {
        updating = true;
        try {
            release.setValue(session.release());
        } finally {
            updating = false;
        }
    }

    private void showMode() {
        EditorMode shown = editor.mode();
        for (Map.Entry<EditorMode, ToggleButton> entry : modes.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == shown);
        }
        show(essentialsHolder, shown == EditorMode.ESSENTIALS);
        show(advancedHolder, shown == EditorMode.ADVANCED);
        show(expert, shown == EditorMode.EXPERT);
        expertPane.setShown(shown == EditorMode.EXPERT);
    }

    private static void show(Node level, boolean visible) {
        level.setVisible(visible);
        level.setManaged(visible);
    }

    /**
     * Moves the keyboard focus to a parameter's control: on Essentials while Essentials is shown
     * and has it, otherwise on Advanced with its category shown. Scrolls it into view.
     *
     * @param name the parameter a summary entry names
     */
    void focusParameter(String name) {
        EditorMode shown = editor.showParameter(name);
        showMode();
        boolean focused =
                shown == EditorMode.ESSENTIALS ? essentials.focus(name) : advanced.focus(name);
        if (focused && getScene() != null && getScene().getFocusOwner() != null) {
            scrollTo(getScene().getFocusOwner());
        }
    }

    /**
     * Moves to a search result: a modelled parameter's field on the Advanced level, which has every
     * parameter of the release, with its category shown and the focus on it; an unknown parameter's
     * entry on the Expert level, where unknown parameters are listed.
     *
     * @param hit the result activated
     */
    void openSearchHit(SearchHit hit) {
        if (hit.unknown()) {
            editor.setMode(EditorMode.EXPERT);
            showMode();
            return;
        }
        editor.showInAdvanced(hit.name());
        showMode();
        if (advanced.focus(hit.name())
                && getScene() != null
                && getScene().getFocusOwner() != null) {
            scrollTo(getScene().getFocusOwner());
        }
    }

    private void scrollTo(Node node) {
        body.applyCss();
        body.layout();
        Bounds inLevels = levels.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
        double contentHeight = levels.getBoundsInLocal().getHeight();
        double viewport = body.getViewportBounds().getHeight();
        if (inLevels == null || contentHeight <= viewport) {
            return;
        }
        double top = Math.max(0, inLevels.getMinY() - viewport / 3);
        body.setVvalue(Math.min(1, top / (contentHeight - viewport)));
    }
}
