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
 * Constructs a {@code ProcessBuilder} while living outside {@code org.cometgui.tools.process}.
 *
 * <p>This is the shape scripts/verify-test-gates.sh control 2 injects into a sandbox copy of the
 * tree: the "just run the binary here" shortcut. It exists as a committed fixture so the proof that
 * R-PROC-02 still rejects it runs in the ordinary test suite, on every build, against the same rule
 * object the product is graded with.
 */
public final class ConstructsProcessBuilder {

    private ConstructsProcessBuilder() {}

    /**
     * @return a process builder this class has no business creating
     */
    static ProcessBuilder shortcut() {
        return new ProcessBuilder("comet", "-P/tmp/comet.params");
    }
}
