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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.install.download.DownloadReport;

/**
 * A file on disk that has been proved to be the artefact the manifest pinned.
 *
 * <p>Only {@link VerifiedDownloader} creates one, and it creates one only after a SHA-256 match, so
 * holding an instance <em>is</em> the evidence {@code R-SEC-02} requires before an executable is
 * launched. A later stage that takes this type rather than a {@link Path} cannot be handed an
 * unverified download by accident.
 *
 * <p>{@link #attempts()} carries what each transfer did, which is how "it resumed" and "it quietly
 * downloaded the whole thing again" are told apart after the fact -- and how a provenance record
 * can say that a restart was needed.
 *
 * @param file the verified file
 * @param hashes its MD5 and SHA-256, computed from the bytes on disk rather than copied from the
 *     manifest
 * @param attempts what each transfer did, oldest first; never empty
 */
public record VerifiedArtefact(Path file, FileHashes hashes, List<DownloadReport> attempts) {

    /**
     * Validates and copies the attempt list.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if there were no attempts
     */
    public VerifiedArtefact {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(hashes, "hashes");
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
        if (attempts.isEmpty()) {
            throw new IllegalArgumentException(
                    "a verified artefact was produced by at least one transfer");
        }
    }

    /**
     * What each transfer did, oldest first.
     *
     * @return the attempts, immutable and never empty
     */
    @Override
    public List<DownloadReport> attempts() {
        return List.copyOf(attempts);
    }

    /**
     * How many transfers it took.
     *
     * @return 1 normally, 2 when a resumed download failed its checksum and was fetched again from
     *     zero
     */
    public int attemptCount() {
        return attempts.size();
    }

    /**
     * The transfer that produced the verified file.
     *
     * @return the last attempt
     */
    public DownloadReport lastAttempt() {
        return attempts.get(attempts.size() - 1);
    }
}
