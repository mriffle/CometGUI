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

package org.cometgui.domain.run;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One spectrum file's place in a run: its 1-based position and the {@code -N} base name {@link
 * OutputBaseNames} gave it.
 *
 * @param position the file's 1-based position among the run's inputs
 * @param input the spectrum file
 * @param base the output base name
 */
public record OutputBase(int position, Path input, String base) {

    /**
     * Validates the entry.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the position is not positive or the base is unsafe
     */
    public OutputBase {
        if (position < 1) {
            throw new IllegalArgumentException("a position is 1-based, but was " + position);
        }
        Objects.requireNonNull(input, "input");
        OutputBaseNames.requireSafe(base);
    }

    /**
     * The process-service stage identifier of this file's Comet invocation, which also names its
     * log ({@link RunLayout#cometStageId(int)}).
     *
     * @return {@code comet-<nn>}
     */
    public String stageId() {
        return RunLayout.cometStageId(position);
    }
}
