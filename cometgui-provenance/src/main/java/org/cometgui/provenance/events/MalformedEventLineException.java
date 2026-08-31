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

package org.cometgui.provenance.events;

import java.io.Serial;

/**
 * Thrown by {@link EventLineFormat#parse} when a line is not one this application wrote.
 *
 * <p>Checked, and package-private, because it never reaches a caller of this package: {@link
 * ProvenanceEventLogReader} catches every one of these and turns it into an {@link EventLogDefect}
 * on the recovery result. That is the whole design of the reader -- damage is data, not control
 * flow -- and a checked exception is what makes the compiler insist that the reader deals with it
 * at the one place it is thrown rather than letting it escape a recovery that promised not to
 * throw.
 *
 * <p><strong>The message never quotes the line.</strong> See {@link EventLineFormat} for why: the
 * bytes of a damaged log are not necessarily bytes this application wrote and redacted.
 */
final class MalformedEventLineException extends Exception {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what was expected and at which character offset, quoting no file content
     */
    MalformedEventLineException(String message) {
        super(message);
    }
}
