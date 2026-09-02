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

package org.cometgui.install.archive;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What one extraction actually did, so that a caller can check it rather than trust it.
 *
 * <p>{@code Exit code 0 proves nothing} is a rule of this project, and this record is what makes
 * the rule satisfiable here: the installer, the completion marker and the tests all read the same
 * account of what was written, how many entries were read and how far the artefact expanded.
 *
 * @param placed every file, directory and symbolic link created, in the order it was created
 * @param entriesRead how many entries the container yielded, including ones that were skipped
 * @param expandedBytes every uncompressed byte that left the container, whether it was written or
 *     discarded -- this is the figure the decompression-bomb ceilings are applied to
 * @param artefactBytes the artefact's size on disk, the denominator of the expansion ratio
 */
public record ExtractionReport(
        List<PlacedFile> placed, int entriesRead, long expandedBytes, long artefactBytes) {

    /**
     * Validates the report and takes a defensive, immutable copy of the list.
     *
     * @throws NullPointerException if {@code placed} is {@code null}
     * @throws IllegalArgumentException if any count is negative
     */
    public ExtractionReport {
        placed = List.copyOf(Objects.requireNonNull(placed, "placed"));
        if (entriesRead < 0 || expandedBytes < 0 || artefactBytes < 0) {
            throw new IllegalArgumentException(
                    "an extraction report counts entries and bytes, and none of them can be"
                            + " negative: entriesRead="
                            + entriesRead
                            + " expandedBytes="
                            + expandedBytes
                            + " artefactBytes="
                            + artefactBytes);
        }
    }

    /**
     * Everything the extraction created, immutable and in creation order.
     *
     * @return the placed files, possibly empty
     */
    @Override
    public List<PlacedFile> placed() {
        return List.copyOf(placed);
    }

    /**
     * The paths created, relative to the destination directory, in creation order.
     *
     * @return the paths
     */
    public List<String> paths() {
        return placed.stream().map(PlacedFile::path).toList();
    }

    /**
     * Looks up what was written at one destination path.
     *
     * @param path the path relative to the destination directory
     * @return the entry, or empty if nothing was written there
     */
    public Optional<PlacedFile> at(String path) {
        return placed.stream().filter(file -> file.path().equals(path)).findFirst();
    }
}
