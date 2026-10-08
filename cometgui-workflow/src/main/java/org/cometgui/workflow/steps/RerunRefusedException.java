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

package org.cometgui.workflow.steps;

import java.io.Serial;

/**
 * A compatible-version Percolator rerun cannot be made from the run it names, and nothing was
 * created: no run directory exists for it, and nothing was launched ({@link PercolatorRerun}).
 *
 * <p>The message is for the scientist. It names the run, and when a file is the reason it names the
 * file, its role and both SHA-256 values ({@code R-RUN-02}).
 */
public final class RerunRefusedException extends Exception {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * Creates the refusal.
     *
     * @param message why, naming the run and any file
     */
    public RerunRefusedException(String message) {
        super(message);
    }
}
