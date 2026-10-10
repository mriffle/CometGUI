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

package org.cometgui.ui.controls;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.cometgui.ui.viewmodel.NoManagedBuildViewModel;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.cometgui.ui.viewmodel.ToolRowViewModel;

/**
 * The Tool Manager section's content: one row per tool build the port offered.
 *
 * <h2>What a row says, and where every word comes from</h2>
 *
 * <p>The tool and the version, the state, what the build can do with the evidence behind each
 * claim, its advisories, how many bytes installing it would move, the {@code R-PLAT-03} loader
 * diagnostic where there is one, and where it is installed where it is. Every one of those strings
 * is produced by {@link ToolRowViewModel} and asserted there; this class places them and gives each
 * one a stable identifier. There is no logic here beyond showing and hiding.
 *
 * <h2>A build that cannot run here is shown, not hidden</h2>
 *
 * <p>{@code R-PERC-01} is a rule about not <em>promising</em> a build that cannot run, not a reason
 * to pretend it does not exist -- so a release upstream does not publish for this platform, and one
 * this machine does not meet the requirements of, both get a row, with their state and their
 * diagnostic. What they do not get is an enabled Install action.
 *
 * <h2>Rows are kept, not rebuilt</h2>
 *
 * <p>A view for a row is built once and reused for as long as the view-model keeps that row, so a
 * running install's progress label and its Cancel action survive the Tool Manager being read again.
 * A row that stops being offered has its view dropped and its listeners removed with it; leaving
 * them attached would leak one listener per read of a list that is read on every progress report of
 * a finished install.
 */
public final class ToolManagerPane extends VBox {

    /** The Install action's label. */
    public static final String INSTALL_ACTION = "Install";

    /** The Cancel action's label. */
    public static final String CANCEL_ACTION = "Cancel";

    private final ToolManagerViewModel viewModel;

    /**
     * The view-model's row list, held in a field on purpose.
     *
     * <p>{@link ToolManagerViewModel#rows()} builds a fresh unmodifiable view on each call, and
     * that view observes the list behind it <em>weakly</em>. A listener registered on a wrapper
     * nobody kept would stop firing at the next garbage collection and the section would quietly
     * stop updating, intermittently and only under memory pressure. {@code StageStepper} carries
     * the same field for the same reason.
     */
    private final ObservableList<ToolRowViewModel> rows;

    /** Held for the same reason as {@link #rows}: the wrapper observes the list weakly. */
    private final ObservableList<NoManagedBuildViewModel> gaps;

    /** One explanation per tool with no managed build here, above the rows ({@code D-011}). */
    private final VBox gapBox = new VBox(8);

    private final List<NoManagedBuildView> gapViews = new ArrayList<>();

    private final Label summary = new Label();

    private final VBox rowBox = new VBox(12);

    /** One view per row the view-model is showing, keyed by the row's stable key. */
    private final Map<String, ToolRowView> rowViews = new LinkedHashMap<>();

    /**
     * A Tool Manager pane over the given view-model.
     *
     * <p>Shows whatever the view-model holds now, which for a freshly built one is nothing: {@link
     * ToolManagerViewModel#refresh()} is what reads the port, and the composition root calls it. A
     * view that read the port in its own constructor would verify every installed tool's checksums
     * while a window was being laid out.
     *
     * @param viewModel the tool builds, their states and the two actions
     * @throws NullPointerException if {@code viewModel} is {@code null}
     */
    public ToolManagerPane(ToolManagerViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.rows = viewModel.rows();
        this.gaps = viewModel.noManagedBuild();
        setId(UiIds.TOOL_MANAGER_PANE);
        setSpacing(8);
        setPadding(new Insets(8));

        summary.setId(UiIds.TOOL_MANAGER_SUMMARY);
        summary.setWrapText(true);
        named(summary, "what the tool list holds");
        summary.textProperty().bind(viewModel.summaryProperty());

        rowBox.setId(UiIds.TOOL_MANAGER_ROWS);
        rowBox.setPadding(new Insets(4, 0, 4, 0));

        ScrollPane scroller = new ScrollPane(rowBox);
        scroller.setFitToWidth(true);
        named(scroller, "the tool builds this machine can have");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        gapBox.setId(UiIds.TOOL_MANAGER_NO_MANAGED_BUILD);

        getChildren().addAll(summary, gapBox, scroller);

        rows.addListener((ListChangeListener<ToolRowViewModel>) change -> showRows());
        showRows();
        gaps.addListener((ListChangeListener<NoManagedBuildViewModel>) change -> showGaps());
        showGaps();
    }

    /**
     * The view of one row, for a test that has the row rather than its identifier.
     *
     * @param row the row
     * @return its view, or empty when this pane is not showing that row
     * @throws NullPointerException if {@code row} is {@code null}
     */
    public Optional<Node> viewOf(ToolRowViewModel row) {
        Objects.requireNonNull(row, "row");
        return Optional.ofNullable(rowViews.get(row.key()));
    }

    /** Puts the rows on screen in step with the view-model, keeping the views it already built. */
    private void showRows() {
        List<Node> ordered = new ArrayList<>();
        Map<String, ToolRowView> kept = new LinkedHashMap<>();
        for (ToolRowViewModel row : rows) {
            ToolRowView view = rowViews.remove(row.key());
            if (view == null) {
                view = new ToolRowView(viewModel, row);
            }
            kept.put(row.key(), view);
            ordered.add(view);
        }
        for (ToolRowView dropped : rowViews.values()) {
            dropped.detach();
        }
        rowViews.clear();
        rowViews.putAll(kept);
        rowBox.getChildren().setAll(ordered);
    }

    /** One tool build: everything the row says, and the two actions. */
    private void showGaps() {
        for (NoManagedBuildView dropped : gapViews) {
            dropped.detach();
        }
        gapViews.clear();
        for (NoManagedBuildViewModel gap : gaps) {
            gapViews.add(new NoManagedBuildView(viewModel, gap));
        }
        gapBox.getChildren().setAll(gapViews);
        gapBox.setVisible(!gapViews.isEmpty());
        gapBox.setManaged(!gapViews.isEmpty());
    }

    /**
     * One tool with no managed build here: the plain explanation, the register action where the
     * product can register that tool, and what the last registration did.
     */
    private static final class NoManagedBuildView extends VBox {

        private final NoManagedBuildViewModel gap;

        private final Button register = new Button();

        private final Label status = new Label();

        private final ChangeListener<Object> onChange;

        NoManagedBuildView(ToolManagerViewModel viewModel, NoManagedBuildViewModel gap) {
            this.gap = gap;
            String tool = gap.tool().id();
            setId(UiIds.noManagedBuild(tool));
            setSpacing(4);
            setPadding(new Insets(4));

            Label explanation = new Label(gap.explanationText());
            explanation.setId(UiIds.noManagedBuildText(tool));
            explanation.setWrapText(true);
            named(explanation, "why CometGUI cannot install " + tool + " here");

            register.setText(gap.registerActionText());
            register.setId(UiIds.noManagedBuildRegister(tool));
            named(register, gap.registerActionText());
            register.setOnAction(event -> viewModel.register(gap));
            register.setVisible(gap.fact().localRegistration());
            register.setManaged(gap.fact().localRegistration());

            status.setId(UiIds.noManagedBuildStatus(tool));
            status.setWrapText(true);
            named(status, "what registering your own " + tool + " did");

            getChildren().addAll(explanation, register, status);

            onChange = (property, was, now) -> show();
            gap.registeringProperty().addListener(onChange);
            gap.statusProperty().addListener(onChange);
            show();
        }

        void detach() {
            gap.registeringProperty().removeListener(onChange);
            gap.statusProperty().removeListener(onChange);
        }

        private void show() {
            register.setDisable(!gap.canRegister());
            status.setText(gap.status());
            status.setVisible(!gap.status().isEmpty());
            status.setManaged(!gap.status().isEmpty());
        }
    }

    private static final class ToolRowView extends VBox {

        private final ToolRowViewModel row;

        private final Label state = new Label();

        private final Label capabilities = new Label();

        private final Label advisories = new Label();

        private final Label download = new Label();

        private final Label diagnostic = new Label();

        private final Label installedPath = new Label();

        private final Label progress = new Label();

        private final Button install = new Button(INSTALL_ACTION);

        private final Button cancel = new Button(CANCEL_ACTION);

        private final ChangeListener<Object> onChange;

        ToolRowView(ToolManagerViewModel viewModel, ToolRowViewModel row) {
            this.row = row;
            String key = row.key();
            String name = row.nameText();
            setId(UiIds.toolRow(key));
            setSpacing(2);
            setPadding(new Insets(4));

            Label nameLabel = new Label(name);
            nameLabel.setId(UiIds.toolRowName(key));
            named(nameLabel, name);

            prepare(state, UiIds.toolRowState(key), "state of " + name);
            prepare(capabilities, UiIds.toolRowCapabilities(key), "capabilities of " + name);
            prepare(advisories, UiIds.toolRowAdvisories(key), "advisories for " + name);
            prepare(download, UiIds.toolRowDownload(key), "download size of " + name);
            prepare(diagnostic, UiIds.toolRowDiagnostic(key), "loader diagnostic for " + name);
            prepare(installedPath, UiIds.toolRowPath(key), "installed location of " + name);
            prepare(progress, UiIds.toolRowProgress(key), "install progress for " + name);

            install.setId(UiIds.toolRowInstall(key));
            named(install, INSTALL_ACTION + " " + name);
            install.setOnAction(event -> viewModel.install(row));

            cancel.setId(UiIds.toolRowCancel(key));
            named(cancel, CANCEL_ACTION + " the install of " + name);
            cancel.setOnAction(event -> viewModel.cancel(row));

            HBox actions = new HBox(8, install, cancel);
            getChildren()
                    .addAll(
                            nameLabel,
                            state,
                            capabilities,
                            advisories,
                            download,
                            diagnostic,
                            installedPath,
                            progress,
                            actions);

            onChange = (property, was, now) -> show();
            row.offerProperty().addListener(onChange);
            row.progressProperty().addListener(onChange);
            show();
        }

        /** Stops listening to a row this pane no longer shows. */
        void detach() {
            row.offerProperty().removeListener(onChange);
            row.progressProperty().removeListener(onChange);
        }

        /** Puts every label and both actions in step with the row. */
        private void show() {
            state.setText(row.stateText());
            capabilities.setText(row.capabilitiesText());
            advisories.setText(row.advisoriesText());
            download.setText(row.downloadText());
            showOptional(diagnostic, row.diagnosticText());
            showOptional(installedPath, row.installedPathText());
            showOptional(progress, row.progressText());
            install.setDisable(!row.canInstall());
            cancel.setDisable(!row.canCancel());
        }

        /**
         * Gives one of a row's labels its identifier and its accessible name.
         *
         * <p>The accessible name says what the control is and which build it belongs to, and does
         * not change when the value does -- so a screen reader user who has moved to "capabilities
         * of percolator 3.07.1" is still there after the row is read again, and a label that is
         * empty because there is nothing to say still has a name.
         *
         * @param label the label to prepare
         * @param id its stable identifier
         * @param accessibleName what a screen reader should call it
         */
        private static void prepare(Label label, String id, String accessibleName) {
            label.setId(id);
            label.setWrapText(true);
            named(label, accessibleName);
        }

        /*
         * A LABEL WITH NOTHING TO SAY STAYS IN THE SCENE, hidden and unmanaged, which is the shape
         * the host-baseline banner already uses: a slot that always exists is one a test can look
         * up and assert is empty, where a slot that is added and removed can only be asserted
         * absent -- and absent is also what a broken view looks like.
         */
        private static void showOptional(Label label, Optional<String> text) {
            label.setText(text.orElse(""));
            label.setVisible(text.isPresent());
            label.setManaged(text.isPresent());
        }
    }
}
