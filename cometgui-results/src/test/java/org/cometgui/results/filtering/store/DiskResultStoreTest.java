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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.cometgui.results.testing.TestHasher;

/**
 * The whole store contract ({@link ResultStoreContract}), unchanged, run on the disk-backed store,
 * forced whatever the table's size: every test the in-memory store passes, this store passes too
 * (design decision P10-4). Its own checks -- index invalidation, sort files, the threshold and the
 * heap budget -- are in {@link DiskIndexInvalidationTest}, {@link ResultStoresTest}, {@link
 * DiskLargeFixtureTest} and {@link DiskStoreBudgetTest}.
 */
class DiskResultStoreTest extends ResultStoreContract {

    @Override
    protected ResultStore open(Path table, TableKind kind, Path workDirectory) throws IOException {
        ResultStore store = ResultStores.onDisk(table, kind, workDirectory, new TestHasher());
        assertTrue(store instanceof DiskResultStore, "the disk-backed store, forced");
        return store;
    }
}
