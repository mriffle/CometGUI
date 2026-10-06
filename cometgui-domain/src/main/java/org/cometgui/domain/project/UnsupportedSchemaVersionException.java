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

package org.cometgui.domain.project;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when a {@code project.json} or {@code run.json} declares a schema version this build does
 * not read ({@code R-RUN-04}).
 *
 * <p>It names the document, the version the document declares and the version this build reads, and
 * the message says which of the two refusals applies. Nothing else from the document reaches it: a
 * schema version is a number this repository defines, so quoting it discloses nothing.
 */
public final class UnsupportedSchemaVersionException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    /** The document, as the caller named it -- usually its path. */
    private final String document;

    /** The version the document declares. */
    private final long found;

    /** The version this build reads and writes. */
    private final int current;

    /** Whether the document is older or newer than this build. */
    private final SchemaVerdict verdict;

    /**
     * Creates the refusal.
     *
     * @param document the document, as the caller named it
     * @param found the version it declares
     * @param current the version this build reads
     * @param verdict {@link SchemaVerdict#OLDER} or {@link SchemaVerdict#NEWER}
     * @param message the full explanation
     */
    UnsupportedSchemaVersionException(
            String document, long found, int current, SchemaVerdict verdict, String message) {
        super(message);
        this.document = Objects.requireNonNull(document, "document");
        this.found = found;
        this.current = current;
        this.verdict = Objects.requireNonNull(verdict, "verdict");
    }

    /**
     * The document that was refused.
     *
     * @return the document's name or path, as the caller gave it
     */
    public String document() {
        return document;
    }

    /**
     * The version the document declares.
     *
     * @return the declared schema version
     */
    public long found() {
        return found;
    }

    /**
     * The version this build reads.
     *
     * @return the current schema version
     */
    public int current() {
        return current;
    }

    /**
     * Which refusal this is.
     *
     * @return {@link SchemaVerdict#OLDER} or {@link SchemaVerdict#NEWER}, never {@link
     *     SchemaVerdict#CURRENT}
     */
    public SchemaVerdict verdict() {
        return verdict;
    }
}
