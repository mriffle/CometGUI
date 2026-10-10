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

package org.cometgui.tools.pdv;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A finished export: an mzTab and the MGFs it references, all in one directory.
 *
 * @param directory the directory, as the request named it
 * @param mzTab the mzTab, {@value PdvMzTabExporter#MZTAB_FILE_NAME} in the directory
 * @param mgfs one per {@code ms_run}, in {@code ms_run} order -- the order PDV's {@code --spectrum}
 *     must list them in
 * @param targetPsms how many rows the target table holds
 * @param decoyPsms how many rows the decoy table holds; 0 when the run wrote none
 * @param psmsWritten how many PSM lines the mzTab holds, counted by reading it back
 */
public record PdvMzTabExport(
        Path directory,
        Path mzTab,
        List<PdvMzTabMgf> mgfs,
        long targetPsms,
        long decoyPsms,
        long psmsWritten) {

    /**
     * Validates and copies the export.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PdvMzTabExport {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(mzTab, "mzTab");
        mgfs = List.copyOf(mgfs);
    }

    @Override
    public List<PdvMzTabMgf> mgfs() {
        return List.copyOf(mgfs);
    }
}
