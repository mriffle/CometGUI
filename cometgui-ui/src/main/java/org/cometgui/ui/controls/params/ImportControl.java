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
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.ImportOffer;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;

/**
 * Importing a parameter file: the import action, the outcome in words, and -- for a file written
 * for another Comet release -- the choice it waits for, with the question naming both releases
 * ({@code R-PARAM-06}): migrate it to the selected release with a reviewable report, read it as its
 * own release (offered only when the editor offers that release), read it as the selected release
 * with the mismatch warning, or import nothing.
 *
 * <p>Every step is the editor's ({@link ParameterEditorViewModel#importFile()} and the offer's
 * actions); this control shows the outcome the editor states.
 */
public final class ImportControl extends VBox {

    private final ParameterEditorViewModel editor;

    private final Button importFile;

    private final VBox offer = new VBox(4);

    private final Label question;

    private final Button own;

    /**
     * The import action and its offer.
     *
     * @param editor the editor
     */
    public ImportControl(ParameterEditorViewModel editor) {
        this.editor = Objects.requireNonNull(editor, "editor");
        setSpacing(4);
        importFile =
                Texts.button(
                        UiIds.PARAM_IMPORT,
                        "Import parameter file...",
                        "Import a Comet parameter file as the configuration");
        importFile.setOnAction(event -> editor.importFile());

        Label status = Texts.label(UiIds.PARAM_IMPORT_STATUS, "", "import status");
        status.textProperty().bind(editor.importStatusProperty());

        offer.setId(UiIds.PARAM_IMPORT_OFFER);
        offer.setPadding(new Insets(0, 0, 0, 16));
        question = Texts.label(UiIds.PARAM_IMPORT_QUESTION, "", "the waiting import's question");
        Button migrate =
                Texts.button(
                        UiIds.PARAM_IMPORT_MIGRATE,
                        "Migrate it to the selected release",
                        "Migrate the file to the selected Comet release, with a reviewable report");
        migrate.setOnAction(event -> editor.migrateOffered());
        own =
                Texts.button(
                        UiIds.PARAM_IMPORT_OWN,
                        "Switch to its release",
                        "Read the file as the Comet release it was written for, switching the"
                                + " editor to that release");
        own.setOnAction(event -> editor.readOfferedAsItsRelease());
        Button selected =
                Texts.button(
                        UiIds.PARAM_IMPORT_SELECTED,
                        "Read it as the selected release",
                        "Read the file as the selected Comet release, with a version mismatch"
                                + " warning");
        selected.setOnAction(event -> editor.readOfferedAsSelected());
        Button dismiss =
                Texts.button(UiIds.PARAM_IMPORT_DISMISS, "Import nothing", "Import nothing");
        dismiss.setOnAction(
                event -> {
                    editor.dismissOffer();
                    importFile.requestFocus();
                });
        offer.getChildren().addAll(question, new HBox(8, migrate, own, selected, dismiss));

        getChildren().addAll(importFile, status, offer);
        editor.files().offerProperty().addListener((observable, before, after) -> refresh());
        refresh();
    }

    private void refresh() {
        Optional<ImportOffer> waiting = editor.files().offer();
        offer.setVisible(waiting.isPresent());
        offer.setManaged(waiting.isPresent());
        waiting.ifPresent(
                shown -> {
                    question.setText(shown.question());
                    own.setDisable(!shown.declaredOffered());
                    own.setText(
                            shown.declaredOffered()
                                    ? "Switch to Comet " + shown.declared().text()
                                    : "Comet "
                                            + shown.declared().text()
                                            + " is not offered by this editor");
                });
    }
}
