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

package org.cometgui.domain.ports;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Computes the checksums the provenance record is required to carry for every input and output.
 *
 * <p>The signature is the specification's, from <em>Software Architecture / Key interfaces</em>.
 * There is no implementation in this module and there is none in this phase: hashing is phase 04's
 * work, and it is the phase that owns the streaming read, the buffer size and the proof that the
 * digests match an independent tool.
 *
 * @see FileHashes
 */
@FunctionalInterface
public interface HashService {

    /**
     * Hashes a file with both algorithms the Definition of Done requires.
     *
     * @param path the file to hash
     * @return its MD5 and SHA-256 checksums
     * @throws IOException if the file cannot be read
     * @throws NullPointerException if {@code path} is {@code null}
     */
    FileHashes hash(Path path) throws IOException;
}
