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

package org.cometgui.workflow.state;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * SHA-256 over bytes already in memory -- a fingerprint's canonical encoding -- as lower-case hex.
 *
 * <p>This is not a second file hasher. Files are hashed by the one {@code HashService}, and their
 * digests reach this package as values; what is hashed here is a short encoding this package builds
 * itself. Lower-case hex comes from {@link HexFormat#of()}, which does not consult the default
 * locale.
 */
final class Sha256 {

    /** Hexadecimal characters in a SHA-256 digest. */
    static final int HEX_LENGTH = 64;

    private static final Pattern HEX_DIGEST = Pattern.compile("[0-9a-fA-F]{64}");

    private Sha256() {}

    /**
     * The digest of some bytes.
     *
     * @param bytes the bytes
     * @return 64 lower-case hexadecimal characters
     */
    static String hex(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException absent) {
            // Every Java platform is required to provide SHA-256 (MessageDigest's documentation).
            throw new IllegalStateException("this Java runtime has no SHA-256", absent);
        }
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    /**
     * The digest of a string's UTF-8 encoding.
     *
     * @param text the text
     * @return 64 lower-case hexadecimal characters
     */
    static String hex(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Frames text as {@code <n>:<text>}, {@code n} its length in UTF-8 bytes written in ASCII
     * decimal, so that whatever the text contains the framing is unambiguous.
     *
     * @param text the text
     * @return the framed text
     */
    static String framed(String text) {
        return Integer.toString(text.getBytes(StandardCharsets.UTF_8).length) + ':' + text;
    }

    /**
     * Checks a value is a SHA-256 hex digest and returns it in lower case.
     *
     * @param value the candidate
     * @param what what the value is, for the message
     * @return the digest in lower case
     * @throws NullPointerException naming {@code what} if {@code value} is {@code null}
     * @throws IllegalArgumentException naming {@code what} and the value if it is not 64
     *     hexadecimal characters
     */
    static String requireDigest(String value, String what) {
        Objects.requireNonNull(value, what);
        if (!HEX_DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    what
                            + " must be "
                            + HEX_LENGTH
                            + " hexadecimal characters (a SHA-256), but was: \""
                            + value
                            + "\"");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
