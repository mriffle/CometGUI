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

package org.cometgui.tools.comet;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What a valid PIN file holds: its feature columns and how many target and decoy rows.
 *
 * @param file the PIN file
 * @param featureColumns its feature columns, in order ({@link PinHeader#featureColumns()})
 * @param targets the data rows labelled {@code 1}
 * @param decoys the data rows labelled {@code -1}
 */
public record PinSummary(Path file, List<String> featureColumns, long targets, long decoys) {

    /**
     * Validates the summary.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a count is negative
     */
    public PinSummary {
        Objects.requireNonNull(file, "file");
        featureColumns = List.copyOf(featureColumns);
        if (targets < 0 || decoys < 0) {
            throw new IllegalArgumentException(
                    "row counts cannot be negative: "
                            + targets
                            + " targets, "
                            + decoys
                            + " decoys");
        }
    }

    /**
     * The feature columns, in order.
     *
     * @return the names, immutable
     */
    @Override
    public List<String> featureColumns() {
        return List.copyOf(featureColumns);
    }

    /**
     * Every data row.
     *
     * @return {@code targets + decoys}
     */
    public long rows() {
        return targets + decoys;
    }
}
