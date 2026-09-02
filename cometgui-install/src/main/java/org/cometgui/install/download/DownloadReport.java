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

package org.cometgui.install.download;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * What one completed transfer actually did.
 *
 * <p>Every field exists because some claim about a downloader is otherwise unfalsifiable. "It
 * resumed" and "it re-downloaded the whole file" produce identical destination files, so only
 * {@link #bytesTransferred()} tells them apart -- a resume that silently fetched everything again
 * would pass any test that checked the bytes on disk. {@link #rangeRequested()} says what was asked
 * for and {@link #statusCode()} says what the server answered, which is how a clean restart against
 * a server that refuses ranges is distinguished from a resume that worked.
 *
 * @param source the URL requested -- always the original one, never a stored redirect target
 * @param destination the file the transfer produced
 * @param statusCode the HTTP status of the response the body came from: 200 for a whole body, 206
 *     for a range
 * @param rangeRequested whether a {@code Range} header was sent
 * @param resumedFromBytes how many bytes already on disk were kept, 0 for a transfer from zero
 * @param bytesTransferred how many bytes this attempt received over the network
 * @param fileSizeBytes the size of the finished file
 * @param declaredTotalBytes the total size the server declared, or a negative number when it
 *     declared none -- which is why a caller must not divide by it without checking
 */
public record DownloadReport(
        URI source,
        Path destination,
        int statusCode,
        boolean rangeRequested,
        long resumedFromBytes,
        long bytesTransferred,
        long fileSizeBytes,
        long declaredTotalBytes) {

    /** What {@link #declaredTotalBytes()} holds when the server declared no length. */
    public static final long NO_DECLARED_TOTAL = -1L;

    /**
     * Validates the report.
     *
     * @throws NullPointerException if the source or the destination is {@code null}
     * @throws IllegalArgumentException if any byte count is negative, or if the bytes kept plus the
     *     bytes received do not add up to the size of the finished file
     */
    public DownloadReport {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        requireNotNegative(resumedFromBytes, "resumedFromBytes");
        requireNotNegative(bytesTransferred, "bytesTransferred");
        requireNotNegative(fileSizeBytes, "fileSizeBytes");
        if (resumedFromBytes + bytesTransferred != fileSizeBytes) {
            throw new IllegalArgumentException(
                    "a report must account for every byte of the finished file: "
                            + resumedFromBytes
                            + " kept plus "
                            + bytesTransferred
                            + " received is "
                            + (resumedFromBytes + bytesTransferred)
                            + ", but the file is "
                            + fileSizeBytes
                            + " bytes");
        }
    }

    private static void requireNotNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative, but was: " + value);
        }
    }

    /**
     * Whether this transfer continued a partial file rather than starting from zero.
     *
     * @return {@code true} if any byte already on disk was kept
     */
    public boolean resumed() {
        return resumedFromBytes > 0;
    }

    /**
     * Whether the server declared how long the artefact is.
     *
     * @return {@code true} when {@link #declaredTotalBytes()} is a real length
     */
    public boolean totalWasDeclared() {
        return declaredTotalBytes >= 0;
    }
}
