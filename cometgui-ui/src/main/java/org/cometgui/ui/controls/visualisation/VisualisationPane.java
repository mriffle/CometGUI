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

package org.cometgui.ui.controls.visualisation;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.value.ObservableValue;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.params.Texts;
import org.cometgui.ui.viewmodel.visualisation.VisualisationViewModel;

/**
 * The Visualisation section: PDV's state with an install's progress and its Cancel control, the run
 * and PSM selected in the Results section; PDV's live window -- "Open run in PDV", "Close PDV", why
 * it cannot be opened when it cannot, the window's state and what PDV answered for the last PSM
 * sent to it; and the action that draws the selected PSM's annotated spectrum as a figure, why it
 * cannot when it cannot, what the last drawing did, and the figure.
 *
 * <p>Every control here only calls the view-model; nothing is parsed, hashed or sent from here.
 */
public final class VisualisationPane extends ScrollPane {

    private final ImageView figure = new ImageView();

    private final Label figureStatus;

    /**
     * The pane over its view-model.
     *
     * @param visualisation the section's state and actions
     */
    public VisualisationPane(VisualisationViewModel visualisation) {
        Objects.requireNonNull(visualisation, "visualisation");
        setId(UiIds.VISUALISATION_PANE);
        named(this, "the Visualisation section's content");
        setFitToWidth(true);

        Label pdv =
                Texts.label(
                        UiIds.VISUALISATION_PDV_STATUS,
                        visualisation.pdvTextProperty().get(),
                        "PDV's state");
        follow(pdv, visualisation.pdvTextProperty());

        ProgressBar progress = new ProgressBar();
        progress.setId(UiIds.VISUALISATION_INSTALL_PROGRESS);
        named(progress, "Progress of the PDV install");
        progress.progressProperty().bind(visualisation.installProgressProperty());
        progress.visibleProperty().bind(visualisation.installingProperty());
        progress.managedProperty().bind(visualisation.installingProperty());

        Button cancel =
                Texts.button(
                        UiIds.VISUALISATION_INSTALL_CANCEL,
                        "Cancel the PDV install",
                        "Cancel the PDV install this section started");
        cancel.setOnAction(event -> visualisation.cancelInstall());
        cancel.disableProperty().bind(visualisation.installingProperty().not());

        Label run =
                Texts.label(
                        UiIds.VISUALISATION_RUN,
                        visualisation.runTextProperty().get(),
                        "the run open in the Results section");
        follow(run, visualisation.runTextProperty());
        Label psm =
                Texts.label(
                        UiIds.VISUALISATION_PSM,
                        visualisation.psmTextProperty().get(),
                        "the PSM selected in the Results section");
        follow(psm, visualisation.psmTextProperty());

        Button draw =
                Texts.button(
                        UiIds.VISUALISATION_DRAW,
                        "Draw the selected PSM's spectrum",
                        "Draw the selected PSM's annotated spectrum with PDV, installing PDV first"
                                + " if it is not installed");
        draw.setOnAction(event -> visualisation.draw());
        draw.disableProperty().bind(visualisation.drawEnabledProperty().not());
        draw.accessibleHelpProperty().bind(visualisation.drawRefusalProperty());
        Label refusal =
                Texts.label(
                        UiIds.VISUALISATION_DRAW_REFUSAL,
                        visualisation.drawRefusalProperty().get(),
                        "the spectrum can be drawn");
        follow(refusal, visualisation.drawRefusalProperty());

        Label outcome =
                Texts.label(
                        UiIds.VISUALISATION_OUTCOME,
                        visualisation.outcomeProperty().get(),
                        "nothing has been drawn");
        follow(outcome, visualisation.outcomeProperty());

        Button open =
                Texts.button(
                        UiIds.VISUALISATION_OPEN,
                        "Open run in PDV",
                        "Open the Results section's run in PDV's window, installing PDV first if it"
                                + " is not installed; the PSM selected there is then shown in it");
        open.setOnAction(event -> visualisation.openInPdv());
        open.disableProperty().bind(visualisation.openEnabledProperty().not());
        open.accessibleHelpProperty().bind(visualisation.openRefusalProperty());
        Button close =
                Texts.button(
                        UiIds.VISUALISATION_CLOSE, "Close PDV", "Close PDV's window for this run");
        close.setOnAction(event -> visualisation.closePdv());
        close.disableProperty().bind(visualisation.closeEnabledProperty().not());
        Label openRefusal =
                Texts.label(
                        UiIds.VISUALISATION_OPEN_REFUSAL,
                        visualisation.openRefusalProperty().get(),
                        "the run can be opened in PDV");
        follow(openRefusal, visualisation.openRefusalProperty());
        Label window =
                Texts.label(
                        UiIds.VISUALISATION_WINDOW,
                        visualisation.windowTextProperty().get(),
                        "PDV's window");
        follow(window, visualisation.windowTextProperty());
        Label selection =
                Texts.label(
                        UiIds.VISUALISATION_SELECTION,
                        visualisation.selectionTextProperty().get(),
                        "the PSM shown in PDV's window");
        follow(selection, visualisation.selectionTextProperty());

        figure.setId(UiIds.VISUALISATION_FIGURE);
        figure.setAccessibleRole(AccessibleRole.IMAGE_VIEW);
        figure.setAccessibleText("No annotated spectrum has been drawn yet.");
        figure.setPreserveRatio(true);
        figureStatus = Texts.label(UiIds.VISUALISATION_FIGURE_STATUS, "", "the figure is shown");
        visualisation
                .figureProperty()
                .addListener((observable, before, after) -> showFigure(after));

        VBox content =
                new VBox(
                        6,
                        caption("PDV"),
                        pdv,
                        new HBox(8, progress, cancel),
                        caption("Selected in the Results section"),
                        run,
                        psm,
                        caption("PDV's window"),
                        new HBox(8, open, close),
                        openRefusal,
                        window,
                        selection,
                        caption("A figure of the selected PSM"),
                        draw,
                        refusal,
                        outcome,
                        figureStatus,
                        figure);
        setContent(content);
    }

    /**
     * Shows a figure PDV wrote: the PNG's bytes, decoded by JavaFX for display and nothing else.
     *
     * @param shown the figure, or empty to show none
     */
    private void showFigure(Optional<Path> shown) {
        if (shown.isEmpty()) {
            figure.setImage(null);
            figureStatus.setText("");
            return;
        }
        Path file = shown.get();
        try (InputStream in = Files.newInputStream(file)) {
            figure.setImage(new Image(in));
            figure.setAccessibleText("The annotated spectrum PDV drew: " + file);
            figureStatus.setText("");
        } catch (IOException unreadable) {
            figure.setImage(null);
            figure.setAccessibleText("No figure is shown.");
            figureStatus.setText("The figure " + file + " could not be shown: " + unreadable);
        }
    }

    private static Label caption(String shown) {
        Label caption = new Label(shown);
        named(caption, shown);
        return caption;
    }

    private static void follow(Label label, ObservableValue<String> shown) {
        shown.addListener((observable, before, after) -> label.setText(after));
    }
}
