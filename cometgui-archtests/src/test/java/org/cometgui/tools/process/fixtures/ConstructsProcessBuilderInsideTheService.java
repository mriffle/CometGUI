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

package org.cometgui.tools.process.fixtures;

/**
 * Constructs a {@code ProcessBuilder} from inside the package R-PROC-02 confines process creation
 * to, and must therefore be ACCEPTED by the same rule that rejects every fixture in {@code
 * org.cometgui.archtests.fixtures}.
 *
 * <p>Without this control the negative controls prove very little: a rule that rejected every use
 * of {@code ProcessBuilder} everywhere would pass all of them and would fail the product's own
 * {@code ProcessService} on the next build.
 */
public final class ConstructsProcessBuilderInsideTheService {

    private ConstructsProcessBuilderInsideTheService() {}

    /**
     * @return a process builder created in the one package allowed to create one
     */
    static ProcessBuilder legally() {
        return new ProcessBuilder("comet", "-P/tmp/comet.params");
    }
}
