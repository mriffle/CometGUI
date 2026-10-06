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

package org.cometgui.workflow.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceStatus;

/** Builds the provenance record of a declared file from its digests and its attributes. */
final class FileFacts {

    private FileFacts() {}

    /**
     * The record of one file.
     *
     * @param file the declared file
     * @param hashes its digests, computed by the caller
     * @param status its status
     * @return the record, with the size and modification time read now, in one call
     * @throws IOException if the attributes cannot be read
     */
    static FileRecord record(DeclaredFile file, FileHashes hashes, ProvenanceStatus status)
            throws IOException {
        BasicFileAttributes attributes =
                Files.readAttributes(file.path(), BasicFileAttributes.class);
        return new FileRecord(
                file.direction(),
                file.role(),
                file.path(),
                attributes.size(),
                attributes.lastModifiedTime().toInstant(),
                hashes,
                status);
    }

    /**
     * The direction and role of a file, as a mismatch message names them.
     *
     * @param file the declared file
     * @return for example {@code input file, role spectrum}
     */
    static String roleOf(DeclaredFile file) {
        return file.direction().wireName() + " file, role " + file.role();
    }
}
