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

import java.util.Objects;
import org.cometgui.install.registry.ArchiveMember;

/**
 * One member the manifest asks for, and where the manifest says it goes.
 *
 * <p>Two strings that are emphatically not the same thing. {@link #memberPath()} is upstream's name
 * for the member and is used <strong>only to find it</strong>; {@link #installedPath()} is
 * CometGUI's own and is the only one that ever places a file. That separation is what lets {@code
 * rel-3-06-05/percolator-noxml-osx-portable.zip} -- whose single member is really named {@code
 * ../my_build/percolator-noxml/src/percolator} -- install correctly while the traversal guard stays
 * exactly as strict as it was.
 *
 * @param memberPath the member's name inside the artefact, as upstream spells it
 * @param installedPath where it is written, relative to the destination directory
 */
public record RequestedMember(String memberPath, String installedPath) {

    /**
     * Validates that both strings say something.
     *
     * @throws NullPointerException if either is {@code null}
     * @throws IllegalArgumentException if either is blank
     */
    public RequestedMember {
        Objects.requireNonNull(memberPath, "memberPath");
        Objects.requireNonNull(installedPath, "installedPath");
        if (memberPath.isBlank()) {
            throw new IllegalArgumentException("memberPath must not be blank");
        }
        if (installedPath.isBlank()) {
            throw new IllegalArgumentException("installedPath must not be blank");
        }
    }

    /**
     * The request a manifest record's member describes.
     *
     * @param member the manifest's member
     * @return the request
     * @throws NullPointerException if {@code member} is {@code null}
     */
    public static RequestedMember of(ArchiveMember member) {
        Objects.requireNonNull(member, "member");
        return new RequestedMember(member.path(), member.installedPath());
    }
}
