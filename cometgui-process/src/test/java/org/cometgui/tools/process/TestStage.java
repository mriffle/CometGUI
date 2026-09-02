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

package org.cometgui.tools.process;

import org.cometgui.domain.run.StageTag;

/**
 * A stage, for tests that need one.
 *
 * <p>The workflow module's stage enumeration does not exist yet and this module must not depend on
 * it when it does: {@code StageTag} is the domain's seam precisely so that a stage can be named
 * without knowing what a workflow is.
 *
 * @param id the identifier, which becomes the log file's name
 * @param displayName the name a user would see
 */
record TestStage(String id, String displayName) implements StageTag {

    /**
     * A stage whose display name is its identifier, for the many tests that do not care.
     *
     * @param id the identifier
     * @return the stage
     */
    static TestStage named(String id) {
        return new TestStage(id, id);
    }
}
