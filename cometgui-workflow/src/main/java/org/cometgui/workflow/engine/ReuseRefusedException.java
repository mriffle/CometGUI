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

import java.util.Objects;

/**
 * A run was asked to reuse a recorded result that failed revalidation ({@code R-RUN-02}). Nothing
 * was started. {@link #check()} names every file that changed and offers the steps to force.
 */
public class ReuseRefusedException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The check is a value the caller acts on; it is not part of a serialised form. */
    private final transient ReuseCheck check;

    /**
     * Creates the exception.
     *
     * @param check the refused check
     */
    public ReuseRefusedException(ReuseCheck check) {
        super(Objects.requireNonNull(check, "check").message());
        this.check = check;
    }

    /**
     * The refused check.
     *
     * @return the check, with its mismatches and the offered plan
     */
    public ReuseCheck check() {
        return check;
    }
}
