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

import java.io.IOException;
import java.net.URI;

/**
 * What went wrong with a transfer, in a form the Tool Manager can tell a scientist apart.
 *
 * <p>The four subclasses are not decoration. {@code D-008} says this project redistributes no tool
 * binary and holds no copy to fall back on, so "the artefact is not there any more" is a different
 * event from "the bytes arrived damaged" and from "the network failed", and only the first means
 * somebody has to go and look at what upstream published. Reporting all three as one {@code
 * IOException} would put the diagnosis back on the reader of a stack trace.
 *
 * <ul>
 *   <li>{@link ArtefactUnavailableException} -- the pinned URL answered 404 or 410. The artefact
 *       has been removed or re-tagged upstream.
 *   <li>{@link TruncatedDownloadException} -- the server said how long the body would be and then
 *       sent less of it. The URL is fine; the transfer is not.
 *   <li>{@link DownloadFailedException} -- the network or the server failed: no connection, a
 *       stalled read, an unexpected status.
 *   <li>{@link DownloadCancelledException} -- the caller asked for it to stop. Not a failure at
 *       all, and the reason it is a sibling rather than a flag on one of the others.
 * </ul>
 */
public abstract class DownloadException extends IOException {

    private static final long serialVersionUID = 1L;

    /** The URL the transfer was for. {@link URI} is serializable, so this class stays so. */
    private final URI source;

    /**
     * Creates a failure for one URL.
     *
     * @param message what happened, in words a scientist can act on
     * @param source the URL the transfer was for
     */
    protected DownloadException(String message, URI source) {
        super(message);
        this.source = source;
    }

    /**
     * Creates a failure for one URL, caused by another.
     *
     * @param message what happened, in words a scientist can act on
     * @param source the URL the transfer was for
     * @param cause the underlying failure
     */
    protected DownloadException(String message, URI source, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    /**
     * The URL the transfer was for.
     *
     * @return the source URL, never {@code null}
     */
    public final URI source() {
        return source;
    }
}
