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
 * An MGF file {@link PdvMgfWriter} wrote.
 *
 * @param file the file, complete: it was moved into place only after its last entry was written
 * @param titles every entry's {@code TITLE}, in file order, so that entry {@code n} (0-based) --
 *     what an mzTab {@code ms_run[k]:index=n} names -- has the title {@code titles().get(n)}
 */
public record MgfFile(Path file, List<String> titles) {

    /**
     * Validates the record and copies the titles.
     *
     * @throws NullPointerException if a component or a title is {@code null}
     */
    public MgfFile {
        Objects.requireNonNull(file, "file");
        titles = List.copyOf(titles);
    }

    @Override
    public List<String> titles() {
        return List.copyOf(titles);
    }

    /**
     * How many entries the file holds.
     *
     * @return the count
     */
    public int entries() {
        return titles.size();
    }
}
