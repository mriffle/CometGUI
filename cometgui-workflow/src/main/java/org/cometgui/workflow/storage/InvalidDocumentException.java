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

package org.cometgui.workflow.storage;

import java.io.Serial;
import java.util.Objects;
import org.cometgui.provenance.json.JsonParseException;

/**
 * Thrown when a {@code project.json}, {@code run.json} or {@code project.lock} is not a document
 * this build can read: not UTF-8, not JSON, too large, or JSON whose content the model refuses.
 *
 * <p><strong>Located.</strong> It names the document (its path) and the member that broke a rule --
 * {@code "spectra[1].sha256"} -- and, for a document that is not JSON at all, carries the {@link
 * JsonParseException} with its line and column as its cause.
 *
 * <p><strong>No value from the document reaches the message.</strong> The rule {@code
 * ManifestReader} follows, for the same reason: a document read from disk may hold anything, and a
 * message quoting it is how a credential reaches a log. Member names are part of the schema and are
 * literals in this repository; values are never printed, and when a model constructor refuses a
 * value its exception is not attached, because its message quotes the value.
 *
 * <p>A schema version this build does not read is not this exception: it is {@link
 * org.cometgui.domain.project.UnsupportedSchemaVersionException}, which says which version and
 * which policy applies.
 */
public final class InvalidDocumentException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    /** The document, as a path or a label. */
    private final String document;

    /** The member that broke a rule; empty for the document as a whole. */
    private final String member;

    /**
     * Creates the refusal.
     *
     * @param document the document
     * @param member the member path, empty for the document as a whole
     * @param message the full explanation
     */
    InvalidDocumentException(String document, String member, String message) {
        super(message);
        this.document = Objects.requireNonNull(document, "document");
        this.member = Objects.requireNonNull(member, "member");
    }

    /**
     * Creates the refusal of a document that is not JSON.
     *
     * @param document the document
     * @param message the full explanation
     * @param cause the parse failure, whose message is a rule and a position and quotes nothing
     */
    InvalidDocumentException(String document, String message, JsonParseException cause) {
        super(message, cause);
        this.document = Objects.requireNonNull(document, "document");
        this.member = "";
    }

    /**
     * The document that was refused.
     *
     * @return its path or label
     */
    public String document() {
        return document;
    }

    /**
     * The member that broke a rule.
     *
     * @return for example {@code "spectra[1].sha256"}; empty when the document as a whole is wrong
     */
    public String member() {
        return member;
    }
}
