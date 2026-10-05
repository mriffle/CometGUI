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

package org.cometgui.ui.viewmodel.params;

import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * One row of a preset's preview -- <em>Parameter, Current, Preset</em> -- with the check box that
 * says whether applying the selection applies it.
 *
 * <p>A row starts selected. A row whose parameter is locked by the workflow (decision P7-7) starts
 * unselected and cannot be selected: applying it would be undone at once by the enforced outputs,
 * so the row says why instead.
 */
public final class PresetRowViewModel {

    private final DiffRowView view;

    private final Optional<String> lockReason;

    private final ReadOnlyBooleanWrapper selected;

    PresetRowViewModel(DiffRowView view, Optional<String> lockReason) {
        this.view = Objects.requireNonNull(view, "view");
        this.lockReason = Objects.requireNonNull(lockReason, "lockReason");
        this.selected = new ReadOnlyBooleanWrapper(this, "selected", lockReason.isEmpty());
    }

    /**
     * The row: parameter, current text, preset text.
     *
     * @return the row
     */
    public DiffRowView view() {
        return view;
    }

    /**
     * The parameter's name.
     *
     * @return the name
     */
    public String parameter() {
        return view.row().key();
    }

    /**
     * Why the row cannot be applied.
     *
     * @return the lock's reason, or empty when it can be
     */
    public Optional<String> lockReason() {
        return lockReason;
    }

    /**
     * Whether applying the selection applies this row.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty selectedProperty() {
        return selected.getReadOnlyProperty();
    }

    /**
     * Whether applying the selection applies this row.
     *
     * @return the check box's state
     */
    public boolean isSelected() {
        return selected.get();
    }

    /**
     * Ticks or clears the row's check box.
     *
     * @param select whether to apply this row
     * @return accepted, or refused with the lock's reason when a locked row is selected
     */
    public EditOutcome setSelected(boolean select) {
        if (select && lockReason.isPresent()) {
            return EditOutcome.refused(
                    view.label() + " cannot be set by a preset. " + lockReason.get() + ".");
        }
        selected.set(select);
        return EditOutcome.applied();
    }
}
