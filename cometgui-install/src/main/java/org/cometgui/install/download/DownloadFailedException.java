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

/**
 * The transfer did not happen, for a reason that is neither "the artefact has gone" nor "the body
 * stopped short": no connection, a stalled read, a status this client will not accept.
 *
 * <p>Its message always names the URL and what the failure actually was, because the underlying
 * exceptions frequently do not. {@code java.net.ConnectException} for a refused loopback connection
 * arrives with a {@code null} message, so a report built from {@code getMessage()} alone would tell
 * a scientist the word "null".
 */
public final class DownloadFailedException extends DownloadException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a transfer failure.
     *
     * @param message what went wrong
     * @param source the URL the transfer was for
     */
    DownloadFailedException(String message, URI source) {
        super(message, source);
    }

    /**
     * Creates a transfer failure caused by another.
     *
     * @param message what went wrong
     * @param source the URL the transfer was for
     * @param cause the underlying failure
     */
    DownloadFailedException(String message, URI source, Throwable cause) {
        super(message, source, cause);
    }

    /**
     * Describes a cause whose own message may be absent, which is common in {@code java.net}.
     *
     * @param cause the underlying failure
     * @return the cause's class and message, or just its class when it carries no message
     */
    static String describe(Throwable cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getName()
                : cause.getClass().getName() + ": " + message;
    }
}
