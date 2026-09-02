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

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;

/**
 * The verdict on one downloaded file, with everything a scientist would need to act on it.
 *
 * <p>The message names the expected digest, the actual digest and the URL the bytes came from,
 * because those three together are what distinguishes "the download went wrong, try again" from
 * "upstream published different bytes under the same URL and somebody needs to look at that".
 *
 * <p>The record's own constructor enforces that the components agree with the outcome -- a {@link
 * VerificationOutcome#MATCHED} carrying a digest that differs from the expected one cannot be
 * built. That is deliberate: it means a defect in the verifier that returned the wrong outcome
 * fails loudly here rather than silently letting an unverified artefact through.
 *
 * @param outcome what was found
 * @param source the URL the bytes were fetched from
 * @param file the file that was checked
 * @param expected the digests the manifest pins
 * @param expectedSizeBytes the length the manifest pins
 * @param actual the digests computed from the file, or empty when they were not computed
 * @param actualSizeBytes the length of the file, or a negative number when there is no file
 */
public record VerificationResult(
        VerificationOutcome outcome,
        URI source,
        Path file,
        FileHashes expected,
        long expectedSizeBytes,
        Optional<FileHashes> actual,
        long actualSizeBytes) {

    /** What {@link #actualSizeBytes()} holds when there is no file to measure. */
    public static final long NO_FILE = -1L;

    /**
     * Validates that the components describe the outcome they claim.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the components contradict the outcome -- for example a
     *     {@code MATCHED} whose actual SHA-256 differs from the expected one, or a {@code
     *     FILE_ABSENT} that carries a size
     */
    public VerificationResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        switch (outcome) {
            case MATCHED -> {
                requireDigests(actual, outcome);
                requireSameSha256(actual.orElseThrow(), expected, true);
                requireSize(actualSizeBytes, expectedSizeBytes, true);
            }
            case SHA256_MISMATCH -> {
                requireDigests(actual, outcome);
                requireSameSha256(actual.orElseThrow(), expected, false);
            }
            case SIZE_MISMATCH -> {
                requireNoDigests(actual, outcome);
                requireSize(actualSizeBytes, expectedSizeBytes, false);
            }
            case FILE_ABSENT -> {
                requireNoDigests(actual, outcome);
                if (actualSizeBytes != NO_FILE) {
                    throw new IllegalArgumentException(
                            "FILE_ABSENT carries no size, but actualSizeBytes was "
                                    + actualSizeBytes);
                }
            }
        }
    }

    private static void requireDigests(Optional<FileHashes> actual, VerificationOutcome outcome) {
        if (actual.isEmpty()) {
            throw new IllegalArgumentException(
                    outcome + " requires the digests that were computed");
        }
    }

    private static void requireNoDigests(Optional<FileHashes> actual, VerificationOutcome outcome) {
        if (actual.isPresent()) {
            throw new IllegalArgumentException(
                    outcome + " is reached without computing digests, but digests were supplied");
        }
    }

    private static void requireSameSha256(FileHashes actual, FileHashes expected, boolean same) {
        if (actual.sha256().equals(expected.sha256()) != same) {
            throw new IllegalArgumentException(
                    same
                            ? "MATCHED requires the actual SHA-256 to equal the expected one, but "
                                    + actual.sha256()
                                    + " is not "
                                    + expected.sha256()
                            : "SHA256_MISMATCH requires the two digests to differ, but both are "
                                    + expected.sha256());
        }
    }

    private static void requireSize(long actualSize, long expectedSize, boolean same) {
        if ((actualSize == expectedSize) != same) {
            throw new IllegalArgumentException(
                    same
                            ? "MATCHED requires the actual size to equal the expected one, but "
                                    + actualSize
                                    + " is not "
                                    + expectedSize
                            : "SIZE_MISMATCH requires the two sizes to differ, but both are "
                                    + expectedSize);
        }
    }

    /**
     * Whether the artefact may be installed and executed.
     *
     * @return {@code true} only when the SHA-256 and the size both matched
     */
    public boolean accepted() {
        return outcome.accepted();
    }

    /**
     * Whether the recorded MD5 also agreed.
     *
     * <p>Provenance only. Nothing in this class or in {@link ArtefactVerifier} consults it, and
     * {@link #accepted()} is independent of it in both directions: a file whose MD5 agrees and
     * whose SHA-256 does not is rejected, and a file whose SHA-256 agrees is accepted whatever this
     * says. {@code R-SEC-02} is that sentence, and a caller that used this to decide anything would
     * be undoing it.
     *
     * @return {@code true} when digests were computed and the MD5 matched the manifest's
     */
    public boolean md5Matches() {
        return actual.map(computed -> computed.md5().equals(expected.md5())).orElse(false);
    }

    /**
     * What happened, in one sentence a scientist can act on.
     *
     * @return the message, naming the file, the URL and -- where there are any -- the expected and
     *     actual digests
     */
    public String message() {
        return switch (outcome) {
            case MATCHED ->
                    "verified "
                            + file
                            + ": sha-256 "
                            + expected.sha256()
                            + " over "
                            + actualSizeBytes
                            + " bytes, from "
                            + source;
            case SHA256_MISMATCH ->
                    "SHA-256 mismatch, so the artefact is rejected and is never executed"
                            + " (R-SEC-02): expected "
                            + expected.sha256()
                            + " but "
                            + file
                            + " hashes to "
                            + actual.map(FileHashes::sha256).orElse("(not computed)")
                            + ", downloaded from "
                            + source
                            + ". MD5 is recorded for provenance and decides nothing: expected "
                            + expected.md5()
                            + ", actual "
                            + actual.map(FileHashes::md5).orElse("(not computed)")
                            + ".";
            case SIZE_MISMATCH ->
                    "size mismatch, so the artefact is rejected without hashing it: the manifest"
                            + " pins "
                            + expectedSizeBytes
                            + " bytes and "
                            + file
                            + " holds "
                            + actualSizeBytes
                            + ", downloaded from "
                            + source
                            + " (expected sha-256 "
                            + expected.sha256()
                            + ")";
            case FILE_ABSENT ->
                    "there is no file to verify at "
                            + file
                            + ", so the artefact from "
                            + source
                            + " is rejected (expected sha-256 "
                            + expected.sha256()
                            + ")";
        };
    }
}
