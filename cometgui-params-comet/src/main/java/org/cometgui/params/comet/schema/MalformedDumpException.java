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

package org.cometgui.params.comet.schema;

import java.io.Serial;

/**
 * Thrown when text offered as a Comet parameter dump is not one: a malformed line, a name declared
 * twice, no version marker on the first line, or no enzyme table. The message names the line.
 */
public final class MalformedDumpException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what is wrong and on which line
     */
    public MalformedDumpException(String message) {
        super(message);
    }
}
