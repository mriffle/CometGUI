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

package org.cometgui.tools.pdv;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What to export: a run's Percolator PSM tables, the Comet output and spectrum file each PSM came
 * from, and the directory to write into.
 *
 * @param targetPsms Percolator's target PSM table
 * @param decoyPsms Percolator's decoy PSM table, when the run wrote one
 * @param sources the run's spectrum files, in the run's input order; {@code ms_run[n]} numbers
 *     those that hold at least one PSM, in this order
 * @param directory the directory to create: it must not exist, and its parent must
 */
public record PdvMzTabRequest(
        Path targetPsms, Optional<Path> decoyPsms, List<PdvMzTabSource> sources, Path directory) {

    /**
     * Validates and copies the request.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if there is no source, two sources share a {@code PSMId}
     *     base, or the directory has no parent
     */
    public PdvMzTabRequest {
        Objects.requireNonNull(targetPsms, "targetPsms");
        Objects.requireNonNull(decoyPsms, "decoyPsms");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        Objects.requireNonNull(directory, "directory");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("an mzTab export needs at least one source");
        }
        Set<String> bases = new HashSet<>();
        for (PdvMzTabSource source : sources) {
            if (!bases.add(source.psmIdBase())) {
                throw new IllegalArgumentException(
                        "two sources share the PSMId base \"" + source.psmIdBase() + "\"");
            }
        }
        if (directory.toAbsolutePath().getParent() == null) {
            throw new IllegalArgumentException(
                    "the export directory " + directory + " has no parent to create it in");
        }
    }

    @Override
    public List<PdvMzTabSource> sources() {
        return List.copyOf(sources);
    }
}
