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
import java.util.Objects;
import java.util.Optional;

/**
 * A downloaded artefact did not match its pinned checksum, and no further attempt will be made.
 *
 * <p>Thrown rather than returned on purpose. {@code R-SEC-02} makes SHA-256 verification mandatory
 * <em>before an executable is launched</em>, and a caller that ignored a returned verdict would
 * still have a file on disk to launch. There is no file: {@link VerifiedDownloader} deletes it
 * before raising this.
 *
 * <p>{@link #attempts()} is how a reader tells the two shapes apart. One attempt means a download
 * that never resumed and still failed, so the bytes upstream are serving do not match the manifest.
 * Two means a resumed download failed, was discarded, was fetched again from zero, and failed again
 * -- which rules out the client's own splice and points at upstream.
 */
public final class ArtefactVerificationException extends IOException {

    private static final long serialVersionUID = 1L;

    /** What the verification found. Enums are serializable, so this class stays so. */
    private final VerificationOutcome outcome;

    /** Where the bytes came from. */
    private final URI source;

    /** The SHA-256 the manifest pins. */
    private final String expectedSha256;

    /** The SHA-256 the file actually hashed to, or {@code null} when it was never computed. */
    private final String actualSha256;

    /** How many transfers were made before giving up: 1 or 2. */
    private final int attempts;

    /**
     * Creates the rejection from a verdict.
     *
     * @param result the verdict, which must not be an accepted one
     * @param attempts how many transfers were made
     */
    ArtefactVerificationException(VerificationResult result, int attempts) {
        super(
                Objects.requireNonNull(result, "result").message()
                        + " (after "
                        + attempts
                        + " transfer attempt(s))");
        this.outcome = result.outcome();
        this.source = result.source();
        this.expectedSha256 = result.expected().sha256();
        this.actualSha256 =
                result.actual().map(org.cometgui.domain.ports.FileHashes::sha256).orElse(null);
        this.attempts = attempts;
    }

    /**
     * What the verification found.
     *
     * @return the outcome, never {@link VerificationOutcome#MATCHED}
     */
    public VerificationOutcome outcome() {
        return outcome;
    }

    /**
     * Where the bytes came from.
     *
     * @return the source URL
     */
    public URI source() {
        return source;
    }

    /**
     * The digest the manifest pins.
     *
     * @return the expected SHA-256
     */
    public String expectedSha256() {
        return expectedSha256;
    }

    /**
     * The digest the file actually hashed to.
     *
     * @return the actual SHA-256, or empty when the file was absent or the wrong size and was
     *     therefore never hashed
     */
    public Optional<String> actualSha256() {
        return Optional.ofNullable(actualSha256);
    }

    /**
     * How many transfers were made before giving up.
     *
     * @return 1 when the failing download had not resumed, 2 when a resumed download was discarded
     *     and re-fetched from zero
     */
    public int attempts() {
        return attempts;
    }
}
