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

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;

/**
 * A second download that is installed with a tool, and what is taken out of it ({@code R-TOOL-02}).
 *
 * <p>Two shapes of companion exist today and both are here for a stated reason.
 *
 * <ul>
 *   <li><strong>The Percolator XSD pair.</strong> No portable archive upstream publishes ships an
 *       XSD -- every one holds exactly one member, the bare executable -- so the two schemas come
 *       out of the matching {@code noxml} {@code .deb} or {@code .pkg} payload. They are
 *       <em>not</em> a runtime prerequisite: phase 00 ran the binary with no schema present and it
 *       wrote pout XML. {@link #runtimePrerequisite()} records that distinction, because {@code
 *       R-TOOL-02} requires it recorded in the registry rather than left implicit.
 *   <li><strong>Comet's three Thermo libraries.</strong> Each is its own bare download, and Comet
 *       reads Thermo RAW on Windows only when all three sit beside the executable. {@link
 *       #gatesCapability()} names {@code THERMO_RAW_WINDOWS}, so the rule "an install missing them
 *       shall not advertise it" is a fact in the manifest that the prober reads, rather than a
 *       conditional written into code that no manifest change can reach.
 * </ul>
 *
 * <p><strong>Every companion names its members, whatever its kind.</strong> For a payload kind the
 * members are the paths to pull out of it; for {@link ArtefactKind#BARE_EXECUTABLE} the download
 * <em>is</em> the file, so it has exactly one member whose length and digests must equal the
 * download's own -- checked here, so that a mistyped digest on a single-file companion is a
 * rejection rather than a value nobody compares.
 *
 * @param id a stable identifier, so a test and a provenance record can name this companion without
 *     depending on its URL
 * @param kind how the download is unpacked, chosen from the field and never from the URL suffix
 * @param url where it is fetched from
 * @param sizeBytes its length
 * @param hashes its MD5 and SHA-256
 * @param runtimePrerequisite whether the tool needs these files to run at all, as opposed to
 *     needing them for provenance and validation
 * @param gatesCapability the capability an install missing this companion may not advertise, or
 *     empty when it gates none
 * @param note why this companion exists and where it comes from, in a sentence a reader a year
 *     later can act on
 * @param members the files taken out of it, at least one, none named twice and none installed twice
 */
public record ArtefactCompanion(
        String id,
        ArtefactKind kind,
        URI url,
        long sizeBytes,
        FileHashes hashes,
        boolean runtimePrerequisite,
        Optional<ToolCapability> gatesCapability,
        String note,
        List<ArchiveMember> members) {

    /**
     * Validates the companion and takes a defensive, immutable copy of its member list.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the identifier or note is blank, the size is not
     *     positive, the URL is not an absolute https URL, the member list is empty, a member name
     *     or install path is used twice, or a {@code BARE_EXECUTABLE} companion does not describe
     *     exactly the file it downloads -- naming the field
     */
    public ArtefactCompanion {
        id = ArtefactValues.requiredText(id, "companion id");
        Objects.requireNonNull(kind, "companion kind");
        url = ArtefactValues.downloadUrl(url, "companion url");
        sizeBytes = ArtefactValues.positiveSize(sizeBytes, "companion sizeBytes");
        Objects.requireNonNull(hashes, "companion hashes");
        Objects.requireNonNull(gatesCapability, "companion gatesCapability");
        note = ArtefactValues.requiredText(note, "companion note");
        members = checkedMembers(members);
        checkBareExecutableDescribesItsOwnFile(kind, sizeBytes, hashes, members);
    }

    private static List<ArchiveMember> checkedMembers(List<ArchiveMember> members) {
        List<ArchiveMember> copy =
                List.copyOf(Objects.requireNonNull(members, "companion members"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(
                    "companion members must name at least one file: a companion that installs"
                            + " nothing is a download with no reason to happen");
        }
        Set<String> paths = new LinkedHashSet<>();
        Set<String> destinations = new LinkedHashSet<>();
        for (ArchiveMember member : copy) {
            if (!paths.add(member.path())) {
                throw new IllegalArgumentException(
                        "companion members names the member \""
                                + member.path()
                                + "\" more than"
                                + " once");
            }
            if (!destinations.add(member.installedPath())) {
                throw new IllegalArgumentException(
                        "companion members installs two files at \""
                                + member.installedPath()
                                + "\"");
            }
        }
        return copy;
    }

    /*
     * A BARE_EXECUTABLE companion has no container, so its one member is the download itself.
     * Requiring the two descriptions to agree turns what would otherwise be an unchecked
     * restatement -- a size and a digest written twice, with nothing comparing them -- into a
     * rejection.
     */
    private static void checkBareExecutableDescribesItsOwnFile(
            ArtefactKind kind, long sizeBytes, FileHashes hashes, List<ArchiveMember> members) {
        if (kind != ArtefactKind.BARE_EXECUTABLE) {
            return;
        }
        if (members.size() != 1) {
            throw new IllegalArgumentException(
                    "a BARE_EXECUTABLE companion is a single downloaded file, so companion members"
                            + " must name exactly one, but named "
                            + members.size());
        }
        ArchiveMember only = members.get(0);
        if (only.sizeBytes() != sizeBytes || !only.hashes().equals(hashes)) {
            throw new IllegalArgumentException(
                    "a BARE_EXECUTABLE companion's one member is the downloaded file itself, so"
                            + " its sizeBytes and digests must equal the companion's own");
        }
    }

    /**
     * Checks that this companion's gated capability, if it has one, belongs to the tool it is
     * attached to.
     *
     * <p>A companion of a Percolator artefact that gated {@code THERMO_RAW_WINDOWS} would be
     * describing something Percolator cannot do, and the Tool Manager would render it. The check
     * lives here rather than in the reader so that a companion built in code is held to it too.
     *
     * @param tool the tool whose record carries this companion
     * @return this companion, so the check can sit in a stream or an assignment
     * @throws NullPointerException if {@code tool} is {@code null}
     * @throws IllegalArgumentException if the gated capability belongs to a different tool, with a
     *     message naming the capability, its own tool and the tool it was offered to
     */
    public ArtefactCompanion requireGatesCapabilityOf(ToolName tool) {
        Objects.requireNonNull(tool, "tool");
        gatesCapability.ifPresent(capability -> capability.requireBelongsTo(tool));
        return this;
    }

    /**
     * The files this companion installs, immutable and in manifest order.
     *
     * <p>Copied on the way out, as the domain's record accessors are, so that the guarantee is
     * visible at the call site and to SpotBugs.
     *
     * @return the members, immutable and never empty
     */
    @Override
    public List<ArchiveMember> members() {
        return List.copyOf(members);
    }

    /**
     * The install paths this companion writes to, in manifest order.
     *
     * <p>The installer and the completion marker both need exactly this list, and deriving it in
     * two places is how two answers to one question start.
     *
     * @return the destination paths, relative to the tool's install directory
     */
    public List<String> installedPaths() {
        List<String> paths = new ArrayList<>(members.size());
        for (ArchiveMember member : members) {
            paths.add(member.installedPath());
        }
        return List.copyOf(paths);
    }
}
