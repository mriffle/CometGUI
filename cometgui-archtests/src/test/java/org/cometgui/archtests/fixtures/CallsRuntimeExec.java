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

import java.io.IOException;

/**
 * Starts a process through {@code Runtime.exec} rather than {@code ProcessBuilder}.
 *
 * <p>The exit gate item names {@code ProcessBuilder} only. Phase 01's rule covers {@code
 * Runtime.exec} as well, because a rule that confined one constructor and left the other route open
 * would be a rule in name only, and this fixture is what keeps that extra clause from being deleted
 * as untested decoration.
 */
public final class CallsRuntimeExec {

    private CallsRuntimeExec() {}

    /**
     * @return a process this class has no business starting
     * @throws IOException if the process cannot be started
     */
    static Process shortcut() throws IOException {
        return Runtime.getRuntime().exec(new String[] {"comet", "-P/tmp/comet.params"});
    }
}
