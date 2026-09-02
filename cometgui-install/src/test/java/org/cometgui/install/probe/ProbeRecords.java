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

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArchiveMember;
import org.cometgui.install.registry.ArtefactLicence;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;

/** The artefact records the probe suite runs against, real ones first. */
final class ProbeRecords {

    /** This project's own host, and the platform every record here is built for. */
    static final HostPlatform LINUX_X86_64 =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private ProbeRecords() {}

    /**
     * The shipped manifest, read the way the product reads it.
     *
     * @return the manifest
     * @throws IOException if the resource cannot be read
     */
    static ArtefactManifest shipped() throws IOException {
        return ArtefactManifestReader.readFromClasspath();
    }

    /**
     * A real Linux Percolator record out of the shipped manifest.
     *
     * @param version the version, as the manifest spells it
     * @return the record
     * @throws IOException if the manifest cannot be read
     */
    static ArtefactRecord shippedPercolator(String version) throws IOException {
        return shipped()
                .select(LINUX_X86_64, ToolName.PERCOLATOR, ToolVersion.parse(version))
                .stream()
                .map(selection -> selection.artefact())
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "the shipped manifest has no linux-x86-64 Percolator "
                                                + version
                                                + " row, which every probe test here is built on"));
    }

    /**
     * The Percolator 3.09 Debian payload, which the shipped manifest deliberately does
     * <strong>not</strong> carry: 3.09 publishes no Linux portable archive, its {@code .deb} needs
     * {@code GLIBC_2.38} and {@code GLIBCXX_3.4.32} and does not ship the Boost library it imports,
     * so the honest manifest entry is its absence. It exists here as a fixture because it is the
     * one artefact in this project that reproduces both layers of a real {@code R-PLAT-03} failure.
     *
     * @return the record
     */
    static ArtefactRecord payload309() {
        return new ArtefactRecord(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.09"),
                "rel-3-09",
                LINUX_X86_64,
                ArtefactKind.DEB_PAYLOAD,
                URI.create(
                        "https://github.com/percolator/percolator/releases/download/rel-3-09/"
                                + "percolator-v3-09-linux-amd64.deb"),
                3278718,
                new FileHashes(
                        "6e63c909135c9e46cbe7676515e93fa7",
                        "3488743548d607d468f5b1bdbc06e7d99d03af4f0bf00264a0a086e32d662cf1"),
                Optional.of(
                        new ArchiveMember(
                                "./usr/bin/percolator",
                                StagedBinaries.PAYLOAD_309_SIZE,
                                new FileHashes(
                                        "61b789ca29838a3eb2cd19af765fb9a0",
                                        StagedBinaries.PAYLOAD_309_SHA256),
                                "bin/percolator")),
                Optional.empty(),
                true,
                new ArtefactLicence(
                        "Apache-2.0",
                        URI.create(
                                "https://raw.githubusercontent.com/percolator/percolator/"
                                        + "rel-3-09/license.txt"),
                        "upstream license.txt at tag rel-3-09 is the Apache License 2.0"),
                List.of(),
                List.of(),
                List.of(),
                new MinimumHostRequirements(
                        Optional.of(GlibcVersion.parse("2.38")),
                        Optional.of(GlibcVersion.parse("3.4.32")),
                        Optional.empty(),
                        List.of()),
                ToolVersion.parse("0.1.0"));
    }

    /**
     * The same payload with no host floors declared at all, so that the advance check has nothing
     * to say and the <em>loader</em> is what refuses it.
     *
     * @return the record
     */
    static ArtefactRecord payload309WithNoDeclaredFloors() {
        ArtefactRecord declared = payload309();
        return new ArtefactRecord(
                declared.tool(),
                declared.version(),
                declared.releaseTag(),
                declared.platform(),
                declared.kind(),
                declared.url(),
                declared.sizeBytes(),
                declared.hashes(),
                declared.member(),
                declared.expectedExecutablePath(),
                declared.executable(),
                declared.licence(),
                declared.companions(),
                declared.capabilities(),
                declared.advisories(),
                MinimumHostRequirements.none(),
                declared.minimumCometGuiVersion());
    }
}
