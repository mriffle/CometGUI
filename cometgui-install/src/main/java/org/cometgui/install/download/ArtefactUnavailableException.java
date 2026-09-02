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
import java.util.Optional;

/**
 * The pinned artefact is not at its URL any more.
 *
 * <p>This is {@code D-008}'s failure and it is deliberately not a download failure. Managed tool
 * binaries are downloaded from upstream by pinned URL and SHA-256 and are <strong>never
 * redistributed</strong>, so the project holds no copy: when upstream deletes or re-tags a release
 * asset there is nothing to fall back on, and the only useful thing the application can do is name
 * the URL and the checksum it was expecting so that a human can find out what upstream did.
 *
 * <p>Reporting it as a corrupt download would send that human looking for a disk or network fault
 * that does not exist; reporting it as a probe failure would suggest the tool is broken rather than
 * absent. A 404 is an availability fact and nothing else.
 */
public final class ArtefactUnavailableException extends DownloadException {

    private static final long serialVersionUID = 1L;

    /** The HTTP status that said so: 404 or 410. */
    private final int statusCode;

    /** The pinned SHA-256, or {@code null} when the caller supplied none. */
    private final String expectedSha256;

    /**
     * Creates the availability failure.
     *
     * @param source the pinned URL that answered
     * @param statusCode the HTTP status, 404 or 410
     * @param expectedSha256 the SHA-256 the manifest pins, or {@code null} if the caller had none
     */
    ArtefactUnavailableException(URI source, int statusCode, String expectedSha256) {
        super(message(source, statusCode, expectedSha256), source);
        this.statusCode = statusCode;
        this.expectedSha256 = expectedSha256;
    }

    private static String message(URI source, int statusCode, String expectedSha256) {
        return "the pinned artefact is no longer available upstream: HTTP "
                + statusCode
                + " for "
                + source
                + "; expected sha-256 "
                + (expectedSha256 == null ? "(not supplied by the caller)" : expectedSha256)
                + ". CometGUI redistributes no tool binary and holds no copy (D-008), so this is an"
                + " upstream availability failure, not a corrupt download and not a probe failure:"
                + " the release asset has been removed or re-tagged.";
    }

    /**
     * The HTTP status that reported the absence.
     *
     * @return 404 or 410
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * The SHA-256 the manifest pins for the artefact that has gone.
     *
     * @return the expected digest, or empty when the caller supplied none
     */
    public Optional<String> expectedSha256() {
        return Optional.ofNullable(expectedSha256);
    }
}
