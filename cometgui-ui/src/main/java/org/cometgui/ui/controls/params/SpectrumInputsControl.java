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

import java.util.List;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.InputFile;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;

/**
 * The spectrum files to search (decision P7-9): an action that adds files through the file chooser,
 * and one row per chosen file with its status and its full path in words and a remove action.
 * Spectra are run inputs, not parameters; nothing here touches the configuration.
 */
public final class SpectrumInputsControl extends VBox {

    private final SpectrumInputsViewModel inputs;

    private final Label summary;

    private final VBox rows = new VBox(2);

    /**
     * The spectrum inputs.
     *
     * @param inputs the view-model
     * @param subscriptions where this build's listeners are registered
     */
    public SpectrumInputsControl(SpectrumInputsViewModel inputs, Subscriptions subscriptions) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        setSpacing(4);
        Button add =
                Texts.button(
                        UiIds.SPECTRA_ADD,
                        "Add spectrum files...",
                        "Add spectrum files to search, with a file chooser");
        add.setOnAction(event -> inputs.chooseSpectra());
        summary = Texts.label(UiIds.SPECTRA_SUMMARY, summaryText(), "spectrum files chosen");
        HBox top = new HBox(8, add, summary);
        top.setAlignment(Pos.CENTER_LEFT);
        rows.setId(UiIds.SPECTRA_LIST);
        getChildren().addAll(top, rows);
        subscriptions.onChange(inputs.spectraProperty(), this::refresh);
        refresh();
    }

    private void refresh() {
        summary.setText(summaryText());
        rows.getChildren().clear();
        List<InputFile> files = inputs.spectra();
        for (int index = 0; index < files.size(); index++) {
            InputFile file = files.get(index);
            Label text =
                    Texts.label(UiIds.spectrum(index), file.text(), "spectrum file " + (index + 1));
            Button remove =
                    Texts.button(
                            UiIds.spectrumRemove(index),
                            "Remove",
                            "Remove the spectrum file " + file.path());
            remove.setOnAction(event -> inputs.removeSpectrum(file.path()));
            HBox row = new HBox(8, text, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }
    }

    private String summaryText() {
        int count = inputs.spectra().size();
        return switch (count) {
            case 0 -> "No spectrum files chosen.";
            case 1 -> "1 spectrum file chosen.";
            default -> count + " spectrum files chosen.";
        };
    }
}
