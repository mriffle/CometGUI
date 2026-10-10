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
import java.util.Objects;

/**
 * One MGF of an export: {@code ms_run[n]}'s spectra, one entry per PSM row of that run.
 *
 * @param msRun {@code n}, 1-based
 * @param file the MGF
 * @param entries how many entries it holds, counted by reading it back
 * @param source the source it re-encodes
 */
public record PdvMzTabMgf(int msRun, Path file, long entries, PdvMzTabSource source) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if a reference is {@code null}
     */
    public PdvMzTabMgf {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(source, "source");
    }
}
