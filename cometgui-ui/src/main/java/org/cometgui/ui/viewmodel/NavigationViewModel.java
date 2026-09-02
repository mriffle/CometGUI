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

package org.cometgui.ui.viewmodel;

import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The selection model behind the shell's left navigation.
 *
 * <p>It holds the sections in the order they are shown and which one is selected, and nothing else.
 * There is no {@code Node} here, no {@code Scene} and no {@code Platform.runLater}: this half of
 * the MVVM boundary is ordinary Java that a unit test drives without a display or a toolkit, which
 * is what lets navigation be tested for real rather than only through a robot.
 *
 * <h2>Keyboard reachability</h2>
 *
 * <p>{@link #selectNext()} and {@link #selectPrevious()} exist because the phase's first exit-gate
 * item requires every section to be reachable by keyboard alone. The view binds them to key
 * handlers; the movement rule itself lives here so that it is tested without a robot and so that
 * two different key handlers cannot disagree about it.
 *
 * <h2>Decision: the ends do not wrap</h2>
 *
 * <p><strong>{@link #selectNext()} at the last section and {@link #selectPrevious()} at the first
 * do nothing and report {@code false}.</strong> They do not wrap round.
 *
 * <p>Two reasons, and neither is taste. The first is that this is what every list control the user
 * has ever used does -- a {@code ListView}, a menu, a browser's tab strip -- and a navigation list
 * that silently jumped from Settings back to Run would make "press Down until you reach it" an
 * unreliable way to find a section, which is exactly the interaction a keyboard-only user relies
 * on. The second is that a screen reader announces the boundary: reaching the end of the list is
 * information, and wrapping destroys it. Reachability does not need wrapping, because both
 * directions exist and the list is short.
 *
 * <p>The returned {@code boolean} is what makes the boundary testable and is what a view uses to
 * decide whether a Previous or Next button is enabled: {@code true} if the selection moved.
 */
public final class NavigationViewModel {

    /**
     * Every section, in display order. Never modified after construction; {@link #sections()}
     * publishes an unmodifiable view of it.
     */
    private final ObservableList<SectionId> sections =
            FXCollections.observableArrayList(SectionId.displayOrder());

    /** The selected section; never {@code null}, never bindable. See {@link NonNullProperty}. */
    private final NonNullProperty<SectionId> selectedSection =
            new NonNullProperty<>(this, "selectedSection", SectionId.RUN);

    /**
     * A navigation model with {@link SectionId#RUN} selected.
     *
     * <p>Run is the default because it is the first section in the specification's information
     * architecture and the one a user who has just started the application wants: the workflow's
     * inputs and its Run control.
     */
    public NavigationViewModel() {
        // Every field is initialised at its declaration; this constructor exists to carry that
        // explanation and the default-selection decision.
    }

    /**
     * The sections in display order: the eight primary sections, then the two secondary ones.
     *
     * <p>Observable, because a view binds a list control to it, and unmodifiable, because the set
     * of sections is fixed by {@link SectionId} -- a caller that could add one would create a
     * navigation entry with no pane behind it. The wrapper is built here rather than stored, so
     * that no caller ever holds a reference to the backing list; it observes the backing list
     * weakly, so holding one is cheap and dropping one leaks nothing.
     *
     * @return an unmodifiable observable list of all ten sections; attempting to change it throws
     *     {@link UnsupportedOperationException}
     */
    public ObservableList<SectionId> sections() {
        return FXCollections.unmodifiableObservableList(sections);
    }

    /**
     * The selected section, as an observable property.
     *
     * <p>This is how a view observes selection changes: add a {@code ChangeListener} to it, or bind
     * a control's property to it. Its value is never {@code null}, and it starts at {@link
     * SectionId#RUN}.
     *
     * <p><strong>Read-only, deliberately.</strong> {@link #select(SectionId)}, {@link
     * #selectNext()} and {@link #selectPrevious()} are the only ways the selection changes, so a
     * view wires a control to it in two explicit steps rather than with {@code bindBidirectional}:
     * a listener here that moves the control, and a listener on the control that calls {@link
     * #select(SectionId)}. That is not a formality -- a {@code ListView}'s selection model reports
     * {@code null} whenever its selection is cleared, and a bidirectional binding would push that
     * {@code null} in as a state this application does not have. Filtering it out at the one call
     * site is the view's job and is a line of code; recovering from a {@code null} selected section
     * everywhere it is read is not.
     *
     * @return the read-only property, whose value is never {@code null}
     */
    public ReadOnlyObjectProperty<SectionId> selectedSectionProperty() {
        return selectedSection.getReadOnlyProperty();
    }

    /**
     * The section selected right now.
     *
     * @return the selected section, never {@code null}
     */
    public SectionId selectedSection() {
        return selectedSection.get();
    }

    /**
     * Selects a section.
     *
     * <p>Selecting the section that is already selected is a no-op and fires no change event, which
     * is JavaFX property semantics and is what keeps a two-way control binding from looping.
     *
     * @param section the section to select
     * @throws NullPointerException if {@code section} is {@code null}
     */
    public void select(SectionId section) {
        selectedSection.set(Objects.requireNonNull(section, "section"));
    }

    /**
     * Moves the selection one place later in the display order, without wrapping.
     *
     * @return {@code true} if the selection moved; {@code false} if the last section was already
     *     selected, in which case nothing changed
     */
    public boolean selectNext() {
        return moveBy(1);
    }

    /**
     * Moves the selection one place earlier in the display order, without wrapping.
     *
     * @return {@code true} if the selection moved; {@code false} if the first section was already
     *     selected, in which case nothing changed
     */
    public boolean selectPrevious() {
        return moveBy(-1);
    }

    /**
     * Moves the selection by an offset, clamping at both ends rather than wrapping.
     *
     * @param offset how far to move: {@code 1} for the next section, {@code -1} for the previous
     * @return whether the selection moved
     */
    private boolean moveBy(int offset) {
        int target = sections.indexOf(selectedSection.get()) + offset;
        if (target < 0 || target >= sections.size()) {
            return false;
        }
        selectedSection.set(sections.get(target));
        return true;
    }
}
