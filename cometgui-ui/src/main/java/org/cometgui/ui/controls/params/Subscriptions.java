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

import java.util.ArrayList;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * The listeners one build of the editor's controls added to longer-lived view-models, so that they
 * can be removed when that build is replaced.
 *
 * <p>Changing release builds new fields and so new controls; the session and the structured
 * view-models outlive both. A control that listened to them directly and was then thrown away would
 * keep receiving every change and keep its whole subtree reachable. Each build therefore registers
 * its listeners here, and the view that replaces the build calls {@link #dispose()}.
 */
public final class Subscriptions {

    private final List<Runnable> removals = new ArrayList<>();

    private boolean disposed;

    /** An empty set of subscriptions. */
    public Subscriptions() {
        // Listeners are added by the controls that need them.
    }

    /**
     * Calls an action whenever a value changes, until {@link #dispose()}.
     *
     * <p>A listener removed while its value is notifying is still called in that round -- JavaFX
     * notifies a snapshot of its listeners -- so the action is also skipped once disposed: a
     * control of a release that has just been replaced must not read the new release's
     * configuration.
     *
     * @param <T> the value's type
     * @param value the observed value
     * @param action what to run on a change
     */
    public <T> void onChange(ObservableValue<T> value, Runnable action) {
        ChangeListener<T> listener =
                (observable, before, after) -> {
                    if (!disposed) {
                        action.run();
                    }
                };
        value.addListener(listener);
        removals.add(() -> value.removeListener(listener));
    }

    /** Removes every listener added through this object. */
    public void dispose() {
        disposed = true;
        for (Runnable removal : removals) {
            removal.run();
        }
        removals.clear();
    }
}
