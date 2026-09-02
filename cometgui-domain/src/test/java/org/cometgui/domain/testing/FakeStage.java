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

package org.cometgui.domain.testing;

import org.cometgui.domain.run.StageTag;

/**
 * A {@link StageTag} a test can name, standing in for the workflow module's stage enumeration.
 *
 * <p>The domain deliberately knows nothing about the workflow's stages beyond this interface, so
 * every test of a stage-filtered console can and should use a tag it made up. That is not a
 * shortcut around a real type: it is the property {@link StageTag} exists to have.
 *
 * @param id the stable identifier, as {@link StageTag#id()} defines it
 * @param displayName the name a user would see
 */
public record FakeStage(String id, String displayName) implements StageTag {

    /**
     * A stage whose display name is derived from its identifier.
     *
     * @param id the stable identifier
     * @return the stage
     */
    public static FakeStage named(String id) {
        return new FakeStage(id, "The " + id + " stage");
    }
}
