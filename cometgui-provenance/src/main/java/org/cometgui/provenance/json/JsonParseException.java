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

package org.cometgui.provenance.json;

import java.io.Serial;

/**
 * Thrown by {@link JsonReader} when a document is not the JSON this project accepts: what rule was
 * broken, and where, and nothing else.
 *
 * <p><strong>The message never contains a character of the document, and that is a security
 * property rather than a stylistic one.</strong> A provenance document can arrive from anywhere --
 * an old run directory, a copied archive, a colleague's export -- and a parse failure is exactly
 * the moment at which a document has not been through {@link
 * org.cometgui.domain.secrets.SecretRedactor}. A parser that reported {@code unexpected token
 * "ghp_S3cr3t..."} would print a credential into the application log, into a bug report and into
 * whatever a user pastes into an issue, and phase 04's exit gate item 6 is that no secret reaches a
 * log, a provenance record or an export. So the message names the <em>rule</em> -- which is text
 * from this repository -- and the {@link #line()}, {@link #column()} and {@link #offset()} of the
 * character that broke it, and the reader opens the file at that position to see the rest.
 *
 * <p>Unchecked, because a caller cannot repair a malformed document: the only useful responses are
 * to tell the user which file is unreadable and where, or to record the run's provenance as
 * unavailable. Both are decisions for the layer that knows what the file was for, which is why the
 * reader does not force every intermediate frame to declare it.
 */
public final class JsonParseException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    /** The 1-based line the offending character is on. */
    private final int line;

    /** The 1-based column of the offending character within its line. */
    private final int column;

    /** The 0-based index of the offending character in the document. */
    private final int offset;

    /**
     * Creates the exception, appending the location to the rule that was broken.
     *
     * <p>Package-private: only {@link JsonReader} is in a position to know a location, and an
     * exception of this type that a caller invented would claim a precision it does not have.
     *
     * @param rule what the document did that no accepted document does, quoting nothing from it
     * @param line the 1-based line of the offending character
     * @param column the 1-based column of the offending character
     * @param offset the 0-based index of the offending character in the document
     */
    JsonParseException(String rule, int line, int column, int offset) {
        super(rule + " (line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    /**
     * The line the failure is on, counting from one.
     *
     * <p>Lines are separated by {@code \n}, which is the only line terminator this project writes;
     * a carriage return is an ordinary character for the purposes of this count.
     *
     * @return the 1-based line number
     */
    public int line() {
        return line;
    }

    /**
     * The column the failure is at, counting from one, in characters rather than in bytes.
     *
     * @return the 1-based column number
     */
    public int column() {
        return column;
    }

    /**
     * The position of the failure in the document, counting characters from zero.
     *
     * <p>Characters, not bytes: the document is a {@link String} by the time it reaches the parser,
     * and a byte offset would depend on how many of the characters before it were multi-byte in
     * UTF-8. A caller that needs a byte offset can compute one from the text it supplied.
     *
     * @return the 0-based character offset
     */
    public int offset() {
        return offset;
    }
}
