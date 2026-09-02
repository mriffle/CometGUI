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

package org.cometgui.install.registry;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The field rules the artefact registry's records share, in one place.
 *
 * <p>Four records carry a relative install path, three carry a download URL and every one of them
 * carries a byte count. Stating each rule once is not tidiness: a path rule that differs between
 * two records is a path rule that can be satisfied on one of them and not the other, and this
 * manifest's paths are the strings that decide where a downloaded file lands on a scientist's
 * machine.
 *
 * <p>Package-private on purpose. These are the manifest's rules, not the product's; the domain owns
 * anything a second module would need.
 */
final class ArtefactValues {

    /** Lower-case hexadecimal, the form both digests are recorded in. */
    private static final Pattern HEXADECIMAL = Pattern.compile("[0-9a-fA-F]+");

    /** The scheme a download URL must use; see {@link #downloadUrl}. */
    static final String REQUIRED_SCHEME = "https";

    private ArtefactValues() {
        throw new AssertionError("ArtefactValues is a utility class and is never instantiated");
    }

    /**
     * Requires text that says something.
     *
     * @param value the value read from the manifest
     * @param field the field name, for the message
     * @return the value, stripped of surrounding whitespace
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is blank, naming the field
     */
    static String requiredText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    /**
     * Requires a positive byte count.
     *
     * <p>Zero is refused as well as negative. Every artefact this manifest names is a real file
     * upstream published, and a zero-byte artefact is a failed download recorded as a fact.
     *
     * @param value the count read from the manifest
     * @param field the field name, for the message
     * @return the value
     * @throws IllegalArgumentException if it is not positive, naming the field and the value
     */
    static long positiveSize(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    field + " must be a positive number of bytes, but was: " + value);
        }
        return value;
    }

    /**
     * Requires a download URL this product is allowed to fetch from.
     *
     * <p>HTTPS and nothing else, because {@code R-SEC-02} makes the SHA-256 the trust mechanism and
     * a plain-HTTP download hands an intermediary the chance to serve different bytes and see them
     * accepted only if it also rewrote the manifest -- which is a race not worth being able to
     * lose. Credentials in the URL are refused too: {@code D-008}'s artefacts are public releases,
     * so a user-info component is either a mistake or a secret about to be written into a
     * provenance record.
     *
     * @param value the URL read from the manifest
     * @param field the field name, for the message
     * @return the URL
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is not an absolute {@code https} URL with a host and
     *     no user-info component, naming the field and the value
     */
    static URI downloadUrl(URI value, String field) {
        Objects.requireNonNull(value, field);
        /*
         * There is no `getHost().isEmpty()` clause, and its absence is deliberate: java.net.URI
         * returns a null host for every authority it cannot parse as a server -- "https:///p",
         * "https://:8080/p", "https://user@/p" all give null -- so an empty non-null host is a
         * value no input can produce, and a clause that cannot be false is a check that cannot
         * fail.  An empty user-info component, on the other hand, is real: "https://@host/p"
         * yields userInfo "" and is refused here like any other credential-bearing URL.
         */
        if (!value.isAbsolute()
                || !REQUIRED_SCHEME.equals(value.getScheme().toLowerCase(Locale.ROOT))
                || value.getHost() == null
                || value.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    field
                            + " must be an absolute https URL with a host and no credentials, but"
                            + " was: \""
                            + value
                            + "\"");
        }
        return value;
    }

    /**
     * Requires a path that can only ever place a file <em>inside</em> a tool's install directory.
     *
     * <p>This is the rule that makes the registry's design safe rather than merely tidy. A real
     * upstream artefact -- {@code rel-3-06-05/percolator-noxml-osx-portable.zip} -- has a single
     * member named {@code ../my_build/percolator-noxml/src/percolator}, so an archive's own entry
     * name is not a path anyone may write to. In this manifest the archive's name is carried
     * separately and never places anything; the destination is one of these, and it is checked
     * here.
     *
     * <p>Refused: an empty or blank path, an absolute one, a Windows drive letter, a backslash, an
     * empty segment, a {@code .} or {@code ..} segment, and a NUL character. A backslash is refused
     * rather than translated because this string is the manifest's own text and the manifest writes
     * {@code /}; accepting both would make two spellings of one path.
     *
     * @param value the path read from the manifest
     * @param field the field name, for the message
     * @return the path, stripped of surrounding whitespace
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is not a relative path that stays inside the install
     *     directory, naming the field and the value
     */
    static String installRelativePath(String value, String field) {
        String path = requiredText(value, field);
        boolean rejected =
                path.startsWith("/")
                        || path.endsWith("/")
                        || path.indexOf('\\') >= 0
                        || path.indexOf(':') >= 0
                        || path.indexOf('\0') >= 0;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                rejected = true;
            }
        }
        if (rejected) {
            throw new IllegalArgumentException(
                    field
                            + " must be a relative path inside the install directory, with no"
                            + " empty, \".\" or \"..\" segment and no backslash, but was: \""
                            + path
                            + "\"");
        }
        return path;
    }

    /**
     * Requires a digest of the right length in hexadecimal.
     *
     * <p>{@link org.cometgui.domain.ports.FileHashes} remains the authority and canonicalises both
     * digests; this exists only so that a reader can say <em>which</em> of the two members of a
     * pair was malformed, which a single rejection out of the pair cannot. The lengths come from
     * that type's own constants rather than being written again here.
     *
     * @param value the digest read from the manifest
     * @param length the number of hexadecimal characters the digest must have
     * @param field the field name, for the message
     * @return the digest
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is not hexadecimal of that length, naming the field
     */
    static String digest(String value, int length, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() != length || !HEXADECIMAL.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be " + length + " hexadecimal characters");
        }
        return value;
    }
}
