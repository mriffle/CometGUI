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

package org.cometgui.workflow.engine;

/**
 * A step could not do its work. The message is shown to the user and written into the step's {@code
 * stage.finished} event, so it should say what failed and, where it can, what to do.
 */
public class StepFailedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what failed
     */
    public StepFailedException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message what failed
     * @param cause the underlying failure
     */
    public StepFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
