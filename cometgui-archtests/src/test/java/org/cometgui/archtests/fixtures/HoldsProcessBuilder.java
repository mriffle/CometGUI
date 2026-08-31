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
 * Merely holds a {@code ProcessBuilder}: a field, a constructor parameter and a return type, and
 * not one call to it.
 *
 * <p>A class that can hold one is one refactor away from constructing one, and the argv, the
 * working directory and the environment R-PROC-04 requires would be assembled here rather than in
 * the process service. Nothing in this class creates a process, so it is the fixture that says
 * whether the rule is about the type or only about the constructor call.
 */
public final class HoldsProcessBuilder {

    private final ProcessBuilder held;

    HoldsProcessBuilder(ProcessBuilder held) {
        this.held = held;
    }

    ProcessBuilder held() {
        return held;
    }
}
