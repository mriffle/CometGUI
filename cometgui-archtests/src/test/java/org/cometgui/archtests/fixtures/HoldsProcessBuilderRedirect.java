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

package org.cometgui.archtests.fixtures;

/**
 * Holds a nested type of {@code ProcessBuilder} -- a {@code ProcessBuilder.Redirect} -- and nothing
 * else.
 *
 * <p>{@code Redirect} is not assignable to {@code ProcessBuilder}, so a rule expressed only as
 * "assignable to ProcessBuilder" does not see it, and a class deciding where a tool's stdout goes
 * is exactly the logic that belongs in the process service. This fixture is why the shared rule
 * carries a name-based clause covering {@code java.lang.ProcessBuilder} and its nested types
 * alongside the assignability clause.
 */
public final class HoldsProcessBuilderRedirect {

    private final ProcessBuilder.Redirect redirect;

    HoldsProcessBuilderRedirect(ProcessBuilder.Redirect redirect) {
        this.redirect = redirect;
    }

    ProcessBuilder.Redirect redirect() {
        return redirect;
    }
}
