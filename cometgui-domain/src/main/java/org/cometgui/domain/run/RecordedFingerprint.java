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

package org.cometgui.domain.run;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A step's input fingerprint as a run records it: the fingerprint, and the digest of each of the
 * step's inputs, keyed by the input kind's identifier.
 *
 * <p>This is {@code org.cometgui.workflow.state.StepFingerprint} with its enum keys written as
 * their stable identifiers: the domain cannot see the workflow module, and {@code run.json} must
 * outlive any renaming of a Java constant. The workflow converts in both directions; a later rerun
 * preview is computed from these values ({@code R-RUN-01}).
 *
 * <p>Values are 64 <em>lower-case</em> hexadecimal characters, exactly as the fingerprint code
 * produces them. Upper case is refused rather than folded: this record is read back from a file,
 * and a recorded fingerprint that differs from what the code computes only in case is a file that
 * something other than CometGUI wrote.
 *
 * @param value the step's fingerprint
 * @param inputDigests the digest of each of the step's inputs, keyed by input-kind identifier
 */
public record RecordedFingerprint(String value, Map<String, String> inputDigests) {

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    /**
     * Validates and copies the record; the digests are held sorted by identifier.
     *
     * @throws NullPointerException naming a component, key or value that is {@code null}
     * @throws IllegalArgumentException naming a value that is not a lower-case SHA-256 or a key
     *     that is not a lower-case hyphenated identifier
     */
    public RecordedFingerprint {
        requireDigest(value, "fingerprint");
        Objects.requireNonNull(inputDigests, "inputDigests");
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : inputDigests.entrySet()) {
            String kind = requireIdentifier(entry.getKey(), "input kind");
            sorted.put(kind, requireDigest(entry.getValue(), "digest of " + kind));
        }
        inputDigests = Collections.unmodifiableMap(sorted);
    }

    /**
     * The input digests, sorted by input-kind identifier and unmodifiable.
     *
     * @return the digests
     */
    @Override
    public Map<String, String> inputDigests() {
        return Collections.unmodifiableMap(new TreeMap<>(inputDigests));
    }

    /**
     * Requires a lower-case SHA-256 in hexadecimal.
     *
     * @param digest the candidate
     * @param what what it is, for the message
     * @return the digest
     */
    static String requireDigest(String digest, String what) {
        Objects.requireNonNull(digest, what);
        if (!DIGEST.matcher(digest).matches()) {
            throw new IllegalArgumentException(
                    what
                            + " must be 64 lower-case hexadecimal characters, but was: \""
                            + digest
                            + "\"");
        }
        return digest;
    }

    /**
     * Whether a text is a stable identifier: lower-case letters and digits in hyphen-separated
     * words, as {@code EngineStep.id()} and {@code InputKind.id()} are.
     *
     * <p>Public so that a reader can check a key from a file <em>before</em> naming it in a
     * message: a key that passes is a safe literal, one that does not is never quoted.
     *
     * @param text the candidate
     * @return {@code true} if it is an identifier
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static boolean isIdentifier(String text) {
        return IDENTIFIER.matcher(Objects.requireNonNull(text, "text")).matches();
    }

    /**
     * Requires a stable identifier: lower-case letters and digits in hyphen-separated words, as
     * {@code EngineStep.id()} and {@code InputKind.id()} are.
     *
     * @param identifier the candidate
     * @param what what it is, for the message
     * @return the identifier
     */
    static String requireIdentifier(String identifier, String what) {
        Objects.requireNonNull(identifier, what);
        if (!isIdentifier(identifier)) {
            throw new IllegalArgumentException(
                    what
                            + " must be lower-case letters and digits in hyphen-separated"
                            + " words, but was: \""
                            + identifier
                            + "\"");
        }
        return identifier;
    }
}
