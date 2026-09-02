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

package org.cometgui.install.probe;

import java.util.List;
import java.util.Objects;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.registry.ArtefactSelection;

/**
 * What else this host could run, for the "available alternatives" half of {@code R-PLAT-03}.
 *
 * <p>{@code R-PLAT-03} asks a loader diagnostic to name "the host's version, the required version,
 * and the available alternatives", and the third is the only one that is not in front of the probe
 * at the moment of failure: it is a question about the manifest. So it is answered from the
 * manifest -- the other builds of the same tool this host can run, whose declared floors this host
 * is not already known to fail.
 *
 * <p>Each is named with {@code ArtefactRecord.describe()} -- {@code percolator 3.06.5 linux-x86-64}
 * -- rather than a sentence composed here. One rendering of an artefact's identity, used
 * everywhere, cannot drift from another.
 *
 * <p>A build whose own floors this host provably fails is not offered as an alternative, because
 * "try this instead" about a build that will fail the same way is worse than saying nothing. A
 * build whose floors are merely <em>undetermined</em> <strong>is</strong> offered: not knowing is
 * not a reason to withhold it, and {@code R-PLAT-02} makes the probe the authority.
 */
public final class ManifestAlternatives {

    private final ArtefactManifest manifest;
    private final HostPlatform host;
    private final HostRuntimeVersions versions;

    /**
     * Creates the source.
     *
     * @param manifest the shipped artefact manifest
     * @param host the machine in front of the user
     * @param versions what that machine's runtimes were established to be
     * @throws NullPointerException if any argument is {@code null}
     */
    public ManifestAlternatives(
            ArtefactManifest manifest, HostPlatform host, HostRuntimeVersions versions) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.host = Objects.requireNonNull(host, "host");
        this.versions = Objects.requireNonNull(versions, "versions");
    }

    /**
     * What to offer instead of one artefact.
     *
     * @param record the artefact that failed
     * @return the other builds of the same tool this host could run, in the manifest's own offer
     *     order; empty when there are none
     * @throws NullPointerException if {@code record} is {@code null}
     */
    public List<String> forArtefact(ArtefactRecord record) {
        Objects.requireNonNull(record, "record");
        return manifest.select(host, record.tool()).stream()
                .map(ArtefactSelection::artefact)
                .filter(candidate -> !candidate.version().equals(record.version()))
                .filter(this::couldRunHere)
                .map(ArtefactRecord::describe)
                .toList();
    }

    private boolean couldRunHere(ArtefactRecord candidate) {
        return !HostRequirementCheck.check(candidate.minimumHostRequirements(), versions)
                .isRefusal();
    }
}
