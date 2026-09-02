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

package org.cometgui.ui.view;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.SectionId;

/**
 * One navigation section's pane: a heading, the section's own description, and a note saying which
 * phase fills it.
 *
 * <p>Ten of these exist, one per {@link SectionId}, and eight of them hold nothing else. That is
 * the phase's scope rather than an omission -- phase 02 is "the frame that later phases fill" --
 * and each pane says so in text, so an empty section can be told apart from a broken one without
 * reading the source.
 *
 * <p>Two panes are given content by the shell: {@link SectionId#RUN} hosts the stage stepper and
 * {@link SectionId#CONSOLE} hosts the console. Both arrive through {@link #addContent(Node)} rather
 * than through a subclass, because a section pane differs from another only in what it holds, and
 * ten near-identical classes would be ten places for the heading, the description and the note to
 * drift apart.
 *
 * <p>The heading and the description are the section's own {@link SectionId#title()} and {@link
 * SectionId#description()}: the specification's information architecture, read from the model
 * rather than retyped into a view.
 */
public final class SectionPane extends VBox {

    private final SectionId section;

    /**
     * The pane for one section.
     *
     * @param section the section this pane shows
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public SectionPane(SectionId section) {
        this.section = Objects.requireNonNull(section, "section");
        setId(UiIds.sectionPane(section));
        setSpacing(8);
        setPadding(new Insets(12));

        Label heading = new Label(section.title());
        heading.setId(UiIds.sectionHeading(section));
        named(heading, section.title() + " section");

        Label description = new Label(section.description());
        description.setId(UiIds.sectionDescription(section));
        description.setWrapText(true);
        named(description, section.description());

        String note = SectionArrivals.noteFor(section);
        Label arrival = new Label(note);
        arrival.setId(UiIds.sectionNote(section));
        arrival.setWrapText(true);
        named(arrival, note);

        getChildren().addAll(heading, description, arrival);
    }

    /**
     * The section this pane shows.
     *
     * @return the section, never {@code null}
     */
    public SectionId section() {
        return section;
    }

    /**
     * Appends content below the note.
     *
     * @param content the node to add
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public void addContent(Node content) {
        getChildren().add(Objects.requireNonNull(content, "content"));
    }
}
