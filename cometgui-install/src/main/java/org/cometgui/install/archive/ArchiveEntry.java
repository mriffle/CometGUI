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

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * One entry of an archive, as the container declares it and before anything has been checked.
 *
 * <p>Deliberately a description and not an instruction. Nothing here has been validated: {@link
 * #name()} is whatever bytes upstream put in the header, which for a real artefact this project
 * installs is {@code ../my_build/percolator-noxml/src/percolator}. Validation happens in {@link
 * ExtractionGuard}, once, for every format.
 *
 * @param name the entry's name exactly as the container spells it, {@code ./} prefixes and all
 * @param type what the container says it is
 * @param declaredSizeBytes the length the container declares, which the guard checks against the
 *     bytes actually delivered; zero for a directory, and the link target's own length for a
 *     symbolic link, so that a million-link archive is still accounted for as expansion
 * @param linkTarget the target of a symbolic link, or the empty string for anything else
 */
record ArchiveEntry(String name, ArchiveEntryType type, long declaredSizeBytes, String linkTarget) {

    /**
     * Validates the shape of the description, not its safety.
     *
     * @throws NullPointerException if the name, type or link target is {@code null}
     * @throws IllegalArgumentException if the declared size is negative
     */
    ArchiveEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(linkTarget, "linkTarget");
        if (declaredSizeBytes < 0) {
            throw new IllegalArgumentException(
                    "declaredSizeBytes must not be negative, but was: " + declaredSizeBytes);
        }
    }

    /**
     * A regular file entry.
     *
     * @param name the entry's name as the container spells it
     * @param sizeBytes the declared length
     * @return the entry
     */
    static ArchiveEntry file(String name, long sizeBytes) {
        return new ArchiveEntry(name, ArchiveEntryType.FILE, sizeBytes, "");
    }

    /**
     * A directory entry.
     *
     * @param name the entry's name as the container spells it
     * @return the entry
     */
    static ArchiveEntry directory(String name) {
        return new ArchiveEntry(name, ArchiveEntryType.DIRECTORY, 0L, "");
    }

    /**
     * A symbolic-link entry.
     *
     * @param name the entry's name as the container spells it
     * @param target the link target as the container spells it
     * @return the entry
     */
    static ArchiveEntry symlink(String name, String target) {
        return new ArchiveEntry(
                name,
                ArchiveEntryType.SYMLINK,
                target.getBytes(StandardCharsets.UTF_8).length,
                target);
    }

    /**
     * An entry of a kind this extractor never creates.
     *
     * @param name the entry's name as the container spells it
     * @param sizeBytes the declared length, so that the reader can still step over its bytes
     * @return the entry
     */
    static ArchiveEntry other(String name, long sizeBytes) {
        return new ArchiveEntry(name, ArchiveEntryType.OTHER, sizeBytes, "");
    }
}
