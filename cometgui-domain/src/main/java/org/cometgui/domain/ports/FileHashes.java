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

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The pair of checksums recorded for one file: MD5 and SHA-256.
 *
 * <p>Both, always. The Definition of Done requires the provenance record to carry "MD5 plus SHA-256
 * for every input and output", so this type cannot represent a file that was hashed only one way --
 * an {@code Optional} field here would let a caller record half a checksum and still satisfy the
 * compiler.
 *
 * <p>Values are held in lowercase hexadecimal, which is the form {@code md5sum} and {@code
 * sha256sum} print and therefore the form a scientist re-verifying a run will compare against. A
 * checksum given in uppercase is accepted and converted; anything that is not a checksum of the
 * right length is rejected, because a malformed digest that reaches a manifest is discovered at
 * verification time, months later, when the file it describes may be gone.
 *
 * @param md5 the MD5 digest, 32 lowercase hexadecimal characters
 * @param sha256 the SHA-256 digest, 64 lowercase hexadecimal characters
 */
public record FileHashes(String md5, String sha256) {

    /** Number of hexadecimal characters in an MD5 digest. */
    public static final int MD5_LENGTH = 32;

    /** Number of hexadecimal characters in a SHA-256 digest. */
    public static final int SHA256_LENGTH = 64;

    private static final Pattern HEXADECIMAL = Pattern.compile("[0-9a-fA-F]+");

    /**
     * Validates and canonicalises both digests.
     *
     * @throws NullPointerException if either digest is {@code null}
     * @throws IllegalArgumentException if either digest is not hexadecimal of the right length,
     *     with a message naming the field and the rejected value
     */
    public FileHashes {
        md5 = canonical(md5, "md5", MD5_LENGTH);
        sha256 = canonical(sha256, "sha256", SHA256_LENGTH);
    }

    private static String canonical(String digest, String field, int length) {
        Objects.requireNonNull(digest, field);
        if (digest.length() != length || !HEXADECIMAL.matcher(digest).matches()) {
            throw new IllegalArgumentException(
                    field
                            + " must be "
                            + length
                            + " hexadecimal characters, but was: \""
                            + digest
                            + "\"");
        }
        return digest.toLowerCase(Locale.ROOT);
    }
}
