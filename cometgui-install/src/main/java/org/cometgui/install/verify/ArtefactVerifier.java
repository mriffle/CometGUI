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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Decides whether a downloaded file is the artefact the manifest pinned.
 *
 * <p>{@code R-SEC-02} in one class: <em>SHA-256 verification is mandatory before an executable is
 * launched. MD5 shall also be computed and recorded for provenance but shall never be the security
 * trust mechanism.</em> Both halves are here and both are load-bearing. The MD5 is computed --
 * {@link FileHashes} cannot represent a file hashed only one way, so it could not be skipped even
 * carelessly -- and it is recorded on the result, where {@link VerificationResult#md5Matches()}
 * reports it and nothing consults it.
 *
 * <p>The digests come from the project's one {@link HashService}, which reads the file once and
 * computes both in a single pass. Writing a second digest implementation here would be the
 * duplication this project has already paid for twice, and the file being hashed can be 99 MB.
 *
 * <h2>The order of the checks</h2>
 *
 * <p>Absent, then size, then SHA-256. The size check is a cheap pre-filter, not a substitute: a
 * file that passes it is still hashed, and a file that fails it cannot be the artefact whatever it
 * hashes to. Putting it first means a 99 MB download that arrived at the wrong length is rejected
 * without reading it back off the disk. It never lets anything through -- an artefact is accepted
 * only when the SHA-256 matches.
 */
public final class ArtefactVerifier {

    /** The project's one hasher: one open, one pass, both digests. */
    private final HashService hashes;

    /**
     * Creates a verifier over a hash service.
     *
     * @param hashes the hasher; in production {@code
     *     org.cometgui.provenance.hashing.StreamingHashService}
     * @throws NullPointerException if {@code hashes} is {@code null}
     */
    public ArtefactVerifier(HashService hashes) {
        this.hashes = Objects.requireNonNull(hashes, "hashes");
    }

    /**
     * Verifies a downloaded file against the record that says what it should be.
     *
     * @param record the manifest record the file was downloaded for
     * @param file the downloaded file
     * @return the verdict
     * @throws IOException if the file exists but cannot be read
     * @throws NullPointerException if either argument is {@code null}
     */
    public VerificationResult verify(ArtefactRecord record, Path file) throws IOException {
        Objects.requireNonNull(record, "record");
        return verify(file, record.hashes(), record.sizeBytes(), record.url());
    }

    /**
     * Verifies a downloaded file against a pinned size and digest pair.
     *
     * @param file the downloaded file
     * @param expected the digests the manifest pins
     * @param expectedSizeBytes the length the manifest pins
     * @param source the URL the bytes were fetched from, for the message
     * @return the verdict
     * @throws IOException if the file exists but cannot be read
     * @throws NullPointerException if any argument is {@code null}
     */
    public VerificationResult verify(
            Path file, FileHashes expected, long expectedSizeBytes, URI source) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(source, "source");
        if (!Files.isRegularFile(file)) {
            return new VerificationResult(
                    VerificationOutcome.FILE_ABSENT,
                    source,
                    file,
                    expected,
                    expectedSizeBytes,
                    Optional.empty(),
                    VerificationResult.NO_FILE);
        }
        long actualSize = Files.size(file);
        if (actualSize != expectedSizeBytes) {
            return new VerificationResult(
                    VerificationOutcome.SIZE_MISMATCH,
                    source,
                    file,
                    expected,
                    expectedSizeBytes,
                    Optional.empty(),
                    actualSize);
        }
        FileHashes actual = hashes.hash(file);
        VerificationOutcome outcome =
                actual.sha256().equals(expected.sha256())
                        ? VerificationOutcome.MATCHED
                        : VerificationOutcome.SHA256_MISMATCH;
        return new VerificationResult(
                outcome,
                source,
                file,
                expected,
                expectedSizeBytes,
                Optional.of(actual),
                actualSize);
    }
}
