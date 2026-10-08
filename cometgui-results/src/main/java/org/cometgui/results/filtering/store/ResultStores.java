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

package org.cometgui.results.filtering.store;

import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.results.parser.PercolatorOutputException;

/**
 * Opens {@link ResultStore}s.
 *
 * <p>Phase 10 unit 2 provides the in-memory store only. Unit 3 adds the disk-backed store and the
 * documented row threshold at which this factory switches to it ({@code R-RES-03}, design decision
 * P10-4); until then {@link #open} is the in-memory store at any size.
 */
public final class ResultStores {

    private ResultStores() {}

    /**
     * Opens a table in the store that suits its size.
     *
     * @param file the raw Percolator table, only ever read
     * @param kind which of the four tables it is
     * @return the open store; the caller closes it
     * @throws PercolatorOutputException if the reader refuses the file
     * @throws NullPointerException if either is {@code null}
     */
    public static ResultStore open(Path file, TableKind kind) throws PercolatorOutputException {
        return inMemory(file, kind);
    }

    /**
     * Opens a table in memory, whatever its size.
     *
     * @param file the raw Percolator table, only ever read
     * @param kind which of the four tables it is
     * @return the open store; the caller closes it
     * @throws PercolatorOutputException if the reader refuses the file
     * @throws NullPointerException if either is {@code null}
     */
    public static ResultStore inMemory(Path file, TableKind kind) throws PercolatorOutputException {
        Objects.requireNonNull(file, "file");
        return InMemoryResultStore.open(file, kind);
    }
}
