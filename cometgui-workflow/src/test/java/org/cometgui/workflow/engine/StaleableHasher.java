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

package org.cometgui.workflow.engine;

import java.io.IOException;
import java.nio.file.Path;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.provenance.hashing.StreamingHashService;

/**
 * A hasher that can be told to return a stale digest for one file -- the only way to put a stale
 * entry into the real {@code CachingHashService}, which otherwise refuses to remember anything it
 * cannot vouch for. Used to prove that the engine reads bytes where it must, not the cache.
 */
final class StaleableHasher implements HashService {

    private final StreamingHashService real = new StreamingHashService();

    private volatile Path liePath;

    private volatile FileHashes lie;

    void lieAbout(Path path, FileHashes hashes) {
        liePath = path;
        lie = hashes;
    }

    void stopLying() {
        lie = null;
    }

    @Override
    public FileHashes hash(Path path) throws IOException {
        FileHashes told = lie;
        if (told != null && path.equals(liePath)) {
            return told;
        }
        return real.hash(path);
    }
}
