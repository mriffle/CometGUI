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

package org.cometgui.install.verify;

/**
 * What checking a downloaded file against its pinned checksums found.
 *
 * <p>Four values, and exactly one of them lets the artefact be used. That asymmetry is the whole
 * point of the type: {@code R-SEC-02} makes SHA-256 verification mandatory before an executable is
 * launched, so every state that is not {@link #MATCHED} has to be as easy to notice as the one that
 * is, and a boolean would let a caller write {@code if (!result.failed())} and lose the reason.
 *
 * <p><strong>There is no MD5 value here.</strong> MD5 is computed and recorded for provenance and
 * is never the trust mechanism ({@code R-SEC-02}), so a file whose MD5 agrees and whose SHA-256
 * does not is {@link #SHA256_MISMATCH} -- a rejection -- and a file whose SHA-256 agrees is
 * accepted whatever its MD5 says. Giving MD5 an outcome would be the first step towards letting it
 * decide something.
 */
public enum VerificationOutcome {

    /** The file is the size the manifest pins and its SHA-256 is the digest the manifest pins. */
    MATCHED,

    /**
     * The file is the right length and the wrong bytes.
     *
     * <p>The one outcome that means something actively went wrong rather than incomplete: a spliced
     * resume, a corrupted transfer, or an artefact that upstream re-tagged.
     */
    SHA256_MISMATCH,

    /**
     * The file is not the length the manifest pins, so its digest is not computed.
     *
     * <p>Hashing a 99 MB file that is already known to be the wrong file buys nothing; the
     * rejection is the answer either way.
     */
    SIZE_MISMATCH,

    /** There is no regular file at the path at all. */
    FILE_ABSENT;

    /**
     * Whether this outcome permits the artefact to be installed and executed.
     *
     * @return {@code true} only for {@link #MATCHED}
     */
    public boolean accepted() {
        return this == MATCHED;
    }
}
