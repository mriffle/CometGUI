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

package org.cometgui.app.e2e.tiera;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.cometgui.app.e2e.scenario.ChooserScript;
import org.cometgui.app.testing.ScriptedChooser;

/**
 * Tier A's answer to the file dialogs: the scenario's {@link ChooserScript} over the {@link
 * ScriptedChooser} that the real {@code CometGuiApplication}'s file-chooser factory returns -- the
 * one seam of the composition root that stands in for the native dialog (P14-4). The product's own
 * use of the answer -- the spectrum list, the database field, the validation -- runs unchanged.
 */
final class ScriptedChooserScript implements ChooserScript {

    private final ScriptedChooser chooser;

    /**
     * Over the chooser the application was built with.
     *
     * @param chooser the chooser
     */
    ScriptedChooserScript(ScriptedChooser chooser) {
        this.chooser = Objects.requireNonNull(chooser, "chooser");
    }

    @Override
    public void nextSpectra(List<Path> files) {
        chooser.spectra(files.toArray(Path[]::new));
    }

    @Override
    public void nextDatabase(Path file) {
        chooser.database(file);
    }

    @Override
    public void nextFile(Path file) {
        chooser.file(file);
    }

    @Override
    public void nextExportTarget(Path file) {
        chooser.exportTo(file);
    }

    @Override
    public List<String> asked() {
        return chooser.asked();
    }
}
