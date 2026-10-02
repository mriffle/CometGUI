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

package org.cometgui.params.comet.schema;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The Comet versions a curated definition claims, both ends inclusive.
 *
 * <p>An open upper end means "this version and every later one", which is what makes the drift test
 * useful for a new release: the metadata goes on claiming every parameter it describes, and the
 * release's {@code -q} output either confirms each claim or fails the build.
 *
 * @param from the first version claimed
 * @param through the last version claimed, or empty for no upper end
 */
public record VersionRange(ToolVersion from, Optional<ToolVersion> through) {

    /**
     * Validates the range.
     *
     * @throws IllegalArgumentException if {@code through} is earlier than {@code from}
     */
    public VersionRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(through, "through");
        if (through.isPresent() && through.get().compareTo(from) < 0) {
            throw new IllegalArgumentException(
                    "a version range must not end ("
                            + through.get().text()
                            + ") before it starts ("
                            + from.text()
                            + ")");
        }
    }

    /**
     * Whether a version lies in the range.
     *
     * @param version the version
     * @return {@code true} if {@code from <= version <= through}
     */
    public boolean contains(ToolVersion version) {
        Objects.requireNonNull(version, "version");
        return version.isAtLeast(from)
                && through.map(last -> version.compareTo(last) <= 0).orElse(true);
    }
}
