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

package org.cometgui.workflow.pdv;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * A run's mzTab for PDV, ready to open: what {@code denovo-gui} is given.
 *
 * @param mzTab the mzTab, for {@code --mztab}
 * @param mgfs the MGFs it references, in {@code ms_run} order, for {@code --spectrum}
 * @param mzTabHashes the mzTab's MD5 and SHA-256, as recorded in the run's event log
 * @param psms how many PSM lines the mzTab holds
 * @param reused {@code true} when an earlier export was found unchanged and nothing was written
 */
public record PdvMzTab(
        Path mzTab, List<Path> mgfs, FileHashes mzTabHashes, long psms, boolean reused) {

    /**
     * Validates and copies the record.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PdvMzTab {
        Objects.requireNonNull(mzTab, "mzTab");
        mgfs = List.copyOf(mgfs);
        Objects.requireNonNull(mzTabHashes, "mzTabHashes");
    }

    @Override
    public List<Path> mgfs() {
        return List.copyOf(mgfs);
    }
}
