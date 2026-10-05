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

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.PresetsViewModel;

/**
 * The search/acquisition preset choice of Essentials, applied only through a reviewable diff
 * ({@code AC-PAR-08}, exit gate item 3).
 *
 * <p>Choosing a preset and asking for its preview changes nothing: the preview ({@link
 * PresetReviewPane}) shows one row per parameter the preset would change -- the parameter, its
 * current value and the preset's -- each with a check box, which release the preset was made for,
 * and its compatibility problems in words. <em>Apply all</em>, <em>Apply selected</em> and
 * <em>Cancel</em> are the only ways out; each says what it did in the status line. Every decision
 * is the {@link PresetsViewModel}'s: the rows, what a locked row may do, and what applying changes.
 */
public final class PresetControl extends VBox {

    private final PresetsViewModel presets;

    private final ComboBox<Preset> choice = new ComboBox<>();

    private final PresetReviewPane pane;

    /**
     * The preset choice and its preview.
     *
     * @param presets the presets' view-model
     * @param session the session, whose configuration the preview follows
     * @param subscriptions where this build's listeners are registered
     */
    public PresetControl(
            PresetsViewModel presets, ParameterSession session, Subscriptions subscriptions) {
        this.presets = Objects.requireNonNull(presets, "presets");
        Objects.requireNonNull(session, "session");
        setSpacing(6);

        Label label = new Label("Search/acquisition preset");
        label.setMinWidth(240);
        named(label, "Search/acquisition preset");
        choice.setId(UiIds.PRESET_CHOICE);
        named(choice, "Search/acquisition preset");
        label.setLabelFor(choice);
        choice.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(Preset preset) {
                        return preset == null ? "" : preset.displayName();
                    }

                    @Override
                    public Preset fromString(String text) {
                        return null;
                    }
                });
        choice.getItems().setAll(presets.presets());
        if (!choice.getItems().isEmpty()) {
            choice.setValue(choice.getItems().get(0));
        }
        choice.setAccessibleHelp(
                "A preset is a set of changes. Preview shows what it would change, and nothing"
                        + " changes until you apply all or some of it.");
        Button preview =
                Texts.button(
                        UiIds.PRESET_PREVIEW,
                        "Preview changes...",
                        "Preview what the chosen preset would change; nothing changes yet");
        preview.setOnAction(event -> previewChosen());
        HBox line = new HBox(8, label, choice, preview);
        line.setAlignment(Pos.CENTER_LEFT);

        pane =
                new PresetReviewPane(
                        presets,
                        PresetReviewPane.Ids.PRESET,
                        "No preset applied. Choose one and preview it to see what it changes.",
                        choice::requestFocus,
                        subscriptions);

        getChildren().addAll(line, pane);
    }

    private void previewChosen() {
        Preset chosen = choice.getValue();
        if (chosen == null) {
            pane.say("Choose a preset to preview.");
            return;
        }
        pane.previewing(presets.preview(chosen));
    }
}
