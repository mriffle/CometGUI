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

import java.util.Objects;

/**
 * One spectrum file of a run: its 1-based position, its record, and the {@code -N} base name its
 * Comet outputs are named after.
 *
 * <p>The position is what the Comet invocation's stage identifier and log are named after ({@link
 * #stageId()}), so this record is the map from {@code logs/comet-<nn>.log} back to a file that
 * design decision P8-3 promises.
 *
 * @param position the file's 1-based position among the run's inputs
 * @param file the file's record
 * @param base its output base name
 */
public record SpectrumInput(int position, RecordedInput file, String base) {

    /**
     * Validates the entry.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the position is not positive or the base unsafe
     */
    public SpectrumInput {
        if (position < 1) {
            throw new IllegalArgumentException("a position is 1-based, but was " + position);
        }
        Objects.requireNonNull(file, "file");
        OutputBaseNames.requireSafe(base);
    }

    /**
     * The stage identifier of this file's Comet invocation.
     *
     * @return {@code comet-<nn>}
     */
    public String stageId() {
        return RunLayout.cometStageId(position);
    }
}
