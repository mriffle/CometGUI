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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.cometgui.domain.ports.HashService;

/**
 * What an index records of the raw table it describes: its size, its modification time and its
 * SHA-256. An index whose record differs from the raw table in any of the three is not used.
 *
 * @param size the size in bytes
 * @param modifiedNanos the modification time, in nanoseconds since the epoch
 * @param sha256 the SHA-256, lower-case hexadecimal, from the injected {@link HashService}
 */
record RawIdentity(long size, long modifiedNanos, String sha256) {

    /** The raw identity, every field present. */
    RawIdentity {
        Objects.requireNonNull(sha256, "sha256");
    }

    /**
     * Reads a raw table's identity, hashing it through the one hasher ({@code R-PROC-01}, design
     * decision P10-1).
     *
     * @param raw the raw table
     * @param hasher the hasher
     * @return the identity
     * @throws ResultIndexException if the file's size or time changed while it was being hashed
     * @throws IOException if the file cannot be read
     */
    static RawIdentity of(Path raw, HashService hasher) throws IOException {
        long size = Files.size(raw);
        long modified = modifiedNanos(raw);
        String sha256 = hasher.hash(raw).sha256();
        RawIdentity identity = new RawIdentity(size, modified, sha256);
        identity.requireUnchanged(raw, IndexProblem.RAW_CHANGED_WHILE_INDEXING);
        return identity;
    }

    /**
     * Checks, cheaply, that a raw table still has this size and modification time.
     *
     * @param raw the raw table
     * @param problem the reason to give if either differs
     * @throws ResultIndexException if either differs
     * @throws IOException if the file cannot be read
     */
    void requireUnchanged(Path raw, IndexProblem problem) throws IOException {
        long nowSize = Files.size(raw);
        long nowModified = modifiedNanos(raw);
        if (nowSize != size || nowModified != modifiedNanos) {
            throw new ResultIndexException(
                    problem,
                    raw,
                    "size "
                            + size
                            + " -> "
                            + nowSize
                            + ", modified "
                            + modifiedNanos
                            + " -> "
                            + nowModified
                            + " ns");
        }
    }

    /**
     * The SHA-256 as 32 bytes.
     *
     * @return the digest
     */
    byte[] sha256Bytes() {
        return HexFormat.of().parseHex(sha256);
    }

    private static long modifiedNanos(Path raw) throws IOException {
        return Files.getLastModifiedTime(raw).to(TimeUnit.NANOSECONDS);
    }
}
