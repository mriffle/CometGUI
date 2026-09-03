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
 *
 * <h2>The failing build is excluded by its download, not by its version</h2>
 *
 * <p>"Do not send the user back to the build that just failed" is a rule about <strong>a
 * row</strong>, and this manifest has a case where a row and a version are not the same thing.
 * Comet 2026.02.2 publishes <em>two</em> macOS builds -- {@code comet.aarch64.macos.exe} and {@code
 * comet.macos.exe} -- and on Apple silicon both are offered, native first, because {@code D-004}
 * says the x86-64 one runs there under Rosetta 2. Keyed on the version, a native build that failed
 * to load would take its own sibling out of the alternatives with it and the diagnostic would read
 * "none known", which is false: there is a managed build in the manifest that runs on that machine.
 *
 * <p>So the key is the <strong>download URL</strong>, which is the key unit 2 established for the
 * one-row-per-download rule and for the same reason -- Comet's two macOS builds are two different
 * files with two different digests, while PDV's zip is one file carried on five platforms, and
 * {@link ArtefactManifest#select} has already collapsed the second case before this class sees it.
 * The platform would be the wrong key in the other direction: one platform legitimately carries
 * several versions -- Percolator 3.07.1 and 3.06.5 are both {@code linux-x86-64} -- and excluding
 * by platform would delete every alternative there is.
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
                .filter(candidate -> !isTheSameDownloadAs(candidate, record))
                .filter(this::couldRunHere)
                .map(ArtefactRecord::describe)
                .toList();
    }

    /*
     * The URL, not the version and not the platform.  See this class's documentation: one version
     * can be two rows (Comet's two macOS builds, D-004), and one platform can be several versions
     * (Percolator 3.07.1 and 3.06.5 on linux-x86-64), so both of those keys answer a different
     * question from the one being asked.
     */
    private static boolean isTheSameDownloadAs(ArtefactRecord candidate, ArtefactRecord record) {
        return candidate.url().equals(record.url());
    }

    private boolean couldRunHere(ArtefactRecord candidate) {
        return !HostRequirementCheck.check(candidate.minimumHostRequirements(), versions)
                .isRefusal();
    }
}
