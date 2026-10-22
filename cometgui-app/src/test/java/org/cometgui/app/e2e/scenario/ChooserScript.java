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

package org.cometgui.app.e2e.scenario;

import java.nio.file.Path;
import java.util.List;

/**
 * The answers the next native file dialogs give, queued by a scenario before the click that opens
 * them -- the tier-neutral half of the specification's "file chooser abstraction and its test
 * injection" (design decision P14-3).
 *
 * <p>A scenario never touches the dialog, the window it is modal over, or the product's chooser
 * port: it says which files a user would pick, then clicks the control a user clicks ({@code Add
 * spectrum files...}, {@code Choose...}), and the product's own chooser logic runs on that answer.
 * Tier A implements this over the test file-chooser factory the real {@code CometGuiApplication} is
 * built with; Tier B (Phase 14 unit 7) implements it over the loopback bridge, which replaces only
 * the native dialog inside the packaged application.
 *
 * <p>Each queued answer is used by exactly one dialog, in order; a dialog nobody queued an answer
 * for is cancelled, as a user closing it would cancel it.
 */
public interface ChooserScript {

    /**
     * Queues the files the next spectrum-file dialog returns, in this order.
     *
     * @param files the spectrum files, absolute
     */
    void nextSpectra(List<Path> files);

    /**
     * Queues the file the next sequence-database dialog returns.
     *
     * @param file the FASTA file, absolute
     */
    void nextDatabase(Path file);

    /**
     * Queues the file the next file-parameter dialog returns (an OBO file, a spectral library).
     *
     * @param file the file, absolute
     */
    void nextFile(Path file);

    /**
     * Queues the path the next export dialog returns.
     *
     * @param file where the export is to be written, absolute
     */
    void nextExportTarget(Path file);

    /**
     * Every dialog the application has opened so far, in order, as the implementation names them:
     * {@code spectra}, {@code database}, {@code file:<what>}, {@code parameter file}, {@code save}
     * or {@code export:<title>:<suggested name>}.
     *
     * @return the dialogs asked for
     */
    List<String> asked();
}
