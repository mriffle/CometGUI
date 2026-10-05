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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.UiIds.Surface;
import org.cometgui.ui.viewmodel.params.DecoyOption;
import org.cometgui.ui.viewmodel.params.FieldViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;

/**
 * The target/decoy strategy as one Essentials control ({@code R-DEC-01}): where decoys come from,
 * chosen by meaning -- decoys already in the FASTA, or Comet's internal decoys concatenated or
 * reported separately -- and never as a bare {@code decoy_search} number. The choice is set through
 * the session's {@code setDecoySource}; the field's origin and validation state are shown in text.
 */
public final class DecoySourceControl extends VBox {

    private final ParameterSession session;

    private final FieldViewModel field;

    private final ComboBox<DecoyOption> sources = new ComboBox<>();

    private final Label origin;

    private final Label state;

    private boolean updating;

    /**
     * The decoy-source control.
     *
     * @param session the session
     * @param surface where the control is shown, which its identifiers name
     * @param subscriptions where this build's listeners are registered
     */
    public DecoySourceControl(
            ParameterSession session, Surface surface, Subscriptions subscriptions) {
        this.session = Objects.requireNonNull(session, "session");
        this.field = session.field(DecoySource.PARAMETER);
        setSpacing(2);
        String name = field.name();
        Label label = new Label("Decoy source");
        label.setId(UiIds.parameterLabel(surface, name));
        label.setMinWidth(240);
        label.setPrefWidth(240);
        named(label, "Decoy source");
        sources.setId(UiIds.parameterControl(surface, name));
        named(sources, "Decoy source: " + field.displayName());
        label.setLabelFor(sources);
        sources.getItems().setAll(session.decoyOptions());
        sources.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(DecoyOption option) {
                        return option == null ? "" : option.label();
                    }

                    @Override
                    public DecoyOption fromString(String text) {
                        return null;
                    }
                });
        sources.valueProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating && after != null) {
                                session.setDecoySource(after.source());
                                refresh();
                            }
                        });
        HBox line = new HBox(8, label, sources);
        line.setAlignment(Pos.CENTER_LEFT);
        origin =
                Texts.label(UiIds.parameterOrigin(surface, name), "", "origin of the decoy source");
        state =
                Texts.label(
                        UiIds.parameterState(surface, name),
                        "",
                        "validation state of the decoy source");
        getChildren().addAll(line, origin, state);
        subscriptions.onChange(session.modelProperty(), this::refresh);
        subscriptions.onChange(field.stateTextProperty(), this::refresh);
        refresh();
    }

    /**
     * Moves the keyboard focus to the choice, when a summary entry asks for {@code decoy_search}.
     */
    public void focus() {
        sources.requestFocus();
    }

    private void refresh() {
        updating = true;
        try {
            sources.setValue(
                    session.decoySource()
                            .flatMap(
                                    source ->
                                            sources.getItems().stream()
                                                    .filter(option -> option.source() == source)
                                                    .findFirst())
                            .orElse(null));
            origin.setText("Value from: " + field.originText());
            state.setText(field.stateText());
            String help =
                    "Where the decoys Percolator learns from come from ("
                            + field.name()
                            + "). Choices, with the value written to comet.params in brackets: "
                            + String.join(
                                    "; ",
                                    sources.getItems().stream()
                                            .map(o -> o.label() + " [" + o.token() + "]")
                                            .toList())
                            + ".\nValidation: "
                            + field.stateText();
            sources.setAccessibleHelp(help);
            sources.setTooltip(new Tooltip(help));
        } finally {
            updating = false;
        }
    }
}
