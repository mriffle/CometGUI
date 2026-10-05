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

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.EditOutcome;
import org.cometgui.ui.viewmodel.params.MigrationReviewViewModel;
import org.cometgui.ui.viewmodel.params.MigrationRow;
import org.cometgui.ui.viewmodel.params.ParameterSession;

/**
 * The review of a migrated configuration ({@code R-PARAM-13}): shown after a release switch or an
 * imported file was migrated, for as long as the migration is under review -- so it stays reachable
 * while any entry is unresolved.
 *
 * <p>One row per change the migration made: what it did in words, the parameter, the value the
 * source release had, the value now, and the model's explanation. A change that needs the
 * scientist's decision is marked in text as blocking the run, with two actions: accept the value
 * the configuration now holds, or go to the field to set another. Once resolved, the row says how.
 * The rows, their states and what accepting does are the {@link MigrationReviewViewModel}'s.
 */
public final class MigrationReviewPane extends VBox {

    private final MigrationReviewViewModel review;

    private final Consumer<String> goToField;

    private final Label headline;

    private final Label status;

    private final VBox rows = new VBox(6);

    /**
     * The review.
     *
     * @param review the migration review's view-model
     * @param session the session, whose configuration and review the pane follows
     * @param goToField what "go to the field" does with a parameter's name
     */
    public MigrationReviewPane(
            MigrationReviewViewModel review, ParameterSession session, Consumer<String> goToField) {
        this.review = Objects.requireNonNull(review, "review");
        this.goToField = Objects.requireNonNull(goToField, "goToField");
        Objects.requireNonNull(session, "session");
        setId(UiIds.PARAM_MIGRATION);
        setSpacing(4);
        setPadding(new Insets(4, 0, 4, 0));
        headline = Texts.label(UiIds.PARAM_MIGRATION_HEADLINE, "", "migration review");
        headline.setStyle("-fx-font-weight: bold;");
        status = Texts.label(UiIds.PARAM_MIGRATION_STATUS, "", "migration review status");
        getChildren().addAll(headline, rows, status);
        session.reviewProperty().addListener((observable, before, after) -> refresh());
        session.modelProperty().addListener((observable, before, after) -> refresh());
        refresh();
    }

    private void refresh() {
        boolean shown = review.underReview();
        setVisible(shown);
        setManaged(shown);
        rows.getChildren().clear();
        if (!shown) {
            status.setText("");
            return;
        }
        headline.setText("Migration review: " + review.headline() + ".");
        List<MigrationRow> changes = review.rows();
        for (int index = 0; index < changes.size(); index++) {
            rows.getChildren().add(row(index, changes.get(index)));
        }
    }

    private VBox row(int index, MigrationRow row) {
        String parameter =
                row.displayName()
                        .map(n -> n + " (" + row.parameter() + ")")
                        .orElse(row.parameter());
        String text =
                row.outcomeWords()
                        + " -- "
                        + parameter
                        + ": was "
                        + row.sourceValue()
                        + ", now "
                        + row.valueNow()
                        + ". "
                        + row.entry().explanation();
        Label change = new Label(text);
        change.setId(UiIds.migrationRow(index));
        change.setWrapText(true);
        named(change, text);
        String stateWords =
                row.stateText().isEmpty() ? "No decision needed." : row.stateText() + ".";
        Label state = new Label(stateWords);
        state.setId(UiIds.migrationRowState(index));
        state.setWrapText(true);
        named(state, parameter + ": " + stateWords);
        if (row.blocking()) {
            state.setStyle("-fx-font-weight: bold;");
        }
        VBox box = new VBox(2, change, state);
        box.setPadding(new Insets(0, 0, 0, 16));
        if (row.blocking()) {
            Button accept =
                    Texts.button(
                            UiIds.migrationRowAccept(index),
                            "Accept this value",
                            "Accept the value " + row.valueNow() + " for " + parameter);
            accept.setOnAction(event -> accepted(parameter, review.accept(row.parameter())));
            HBox actions = new HBox(8, accept);
            row.focusTarget()
                    .ifPresent(
                            name -> {
                                Button go =
                                        Texts.button(
                                                UiIds.migrationRowGoTo(index),
                                                "Go to the field",
                                                "Go to the field of " + parameter);
                                go.setOnAction(event -> goToField.accept(name));
                                actions.getChildren().add(go);
                            });
            actions.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(actions);
        }
        return box;
    }

    private void accepted(String parameter, EditOutcome outcome) {
        refresh();
        status.setText(
                outcome.refusal()
                        .orElse(
                                "Accepted the value of "
                                        + parameter
                                        + "; it no longer blocks"
                                        + " the run."));
    }
}
