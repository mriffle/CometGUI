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

/**
 * A run the engine has started, as the Run section holds it: something to cancel. Its progress and
 * its outcome arrive through the {@link RunObserver} it was started with.
 */
@FunctionalInterface
public interface ActiveRun {

    /**
     * Asks the run to stop: the running tools and their descendant processes are terminated through
     * the process service, and steps not yet started never start. Returns without waiting. May do
     * I/O, so it is never called on the JavaFX application thread.
     */
    void cancel();
}
