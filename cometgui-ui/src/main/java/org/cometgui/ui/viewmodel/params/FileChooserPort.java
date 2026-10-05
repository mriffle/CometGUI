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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The file chooser, as the editor's view-models see it (decision P7-9): the view implements it with
 * a JavaFX file chooser on the interface thread, and a test replaces it with a scripted answer --
 * the specification's "file chooser abstraction and its test injection".
 *
 * <p>Every call blocks until the scientist has chosen or cancelled, as a modal chooser does.
 */
public interface FileChooserPort {

    /**
     * Asks for spectrum files.
     *
     * @return the files chosen, in the order the chooser gave them; empty if cancelled
     */
    List<Path> chooseSpectrumFiles();

    /**
     * Asks for the sequence database (a FASTA file, or a Comet {@code .idx} index).
     *
     * @return the file chosen, or empty if cancelled
     */
    Optional<Path> chooseDatabase();

    /**
     * Asks for the file a file-path parameter names (an OBO file, a spectral library, a compound
     * modification list): any one existing file.
     *
     * @param what the parameter's display name, which the chooser's title shows
     * @return the file chosen, or empty if cancelled
     */
    Optional<Path> chooseFile(String what);

    /**
     * Asks where to save a new parameter file.
     *
     * @return the path to write, or empty if cancelled; the file is never overwritten if it exists
     */
    Optional<Path> chooseSaveTarget();
}
