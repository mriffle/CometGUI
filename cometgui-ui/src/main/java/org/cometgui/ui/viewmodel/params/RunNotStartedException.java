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

package org.cometgui.ui.viewmodel.params;

import java.util.Objects;

/**
 * A run did not start: the pre-run check blocked it, a recorded result could not be reused, or the
 * run could not be created. Nothing was launched. The message says why, in words the Run section
 * shows as the outcome.
 */
public final class RunNotStartedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message why the run did not start; not blank
     * @param cause what refused it, or {@code null}
     * @throws IllegalArgumentException if the message is blank
     */
    public RunNotStartedException(String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        if (message.isBlank()) {
            throw new IllegalArgumentException("a run that did not start has to say why");
        }
    }
}
