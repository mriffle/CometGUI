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

package org.cometgui.install.registry;

import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * One named file taken out of a downloaded artefact, and where it is installed.
 *
 * <p>Two things travel together here and they are not the same string. {@link #path()} is the name
 * the archive or package payload uses, which upstream chooses and which this project does not
 * control: {@code rel-3-06-05/percolator-noxml-osx-portable.zip} names its single member {@code
 * ../my_build/percolator-noxml/src/percolator}. {@link #installedPath()} is where CometGUI puts it,
 * relative to the tool's install directory, and it is the manifest's own string.
 *
 * <p><strong>Only the second one ever places a file.</strong> That is what makes this design
 * stronger than sanitising an archive entry name rather than weaker: for a named member no
 * attacker-controlled string reaches the file system at all. {@link #path()} is therefore validated
 * as text and not as a path -- a member whose name escapes its archive is a member this manifest
 * can still name -- while {@link #installedPath()} is held to {@link
 * ArtefactValues#installRelativePath}.
 *
 * <p>The size and both digests are recorded so that the extractor can check what it took out before
 * anything is installed, rather than checking only the archive it came from.
 *
 * @param path the member's name inside the artefact, exactly as the archive or payload spells it
 * @param sizeBytes the member's uncompressed length
 * @param hashes the member's MD5 and SHA-256
 * @param installedPath where CometGUI writes it, relative to the tool's install directory
 */
public record ArchiveMember(String path, long sizeBytes, FileHashes hashes, String installedPath) {

    /**
     * Validates the member.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if {@code path} is blank, if {@code sizeBytes} is not
     *     positive, or if {@code installedPath} is not a relative path inside the install directory
     *     -- naming the field
     */
    public ArchiveMember {
        path = ArtefactValues.requiredText(path, "member path");
        sizeBytes = ArtefactValues.positiveSize(sizeBytes, "member sizeBytes");
        Objects.requireNonNull(hashes, "member hashes");
        installedPath = ArtefactValues.installRelativePath(installedPath, "member installedPath");
    }
}
