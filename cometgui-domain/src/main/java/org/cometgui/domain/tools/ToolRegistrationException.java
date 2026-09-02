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

package org.cometgui.domain.tools;

/**
 * Thrown when a binary the user pointed at cannot be registered.
 *
 * <p>Checked, deliberately. Registering a local binary is something a user does with a file
 * chooser, and being told "that is Percolator 3.04, and 3.05 is the minimum" is part of the normal
 * flow rather than a programming error. A caller that forgets to handle it does not compile, which
 * is the point: {@code R-TOOL-08}'s whole subject is a file whose contents nobody controls.
 *
 * <p>The message is written for the user, names what was found and what was required, and is what
 * the Tool Manager shows.
 */
public class ToolRegistrationException extends Exception {

    /** Fixed, because this type adds no state to {@link Exception} and will not. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the failure with the sentence the user will read.
     *
     * @param message why the binary was not registered, naming what was found and what was required
     */
    public ToolRegistrationException(String message) {
        super(message);
    }

    /**
     * Creates the failure with a sentence and the lower-level failure behind it.
     *
     * @param message why the binary was not registered
     * @param cause the failure that caused it -- an unreadable file, a process that would not start
     */
    public ToolRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
