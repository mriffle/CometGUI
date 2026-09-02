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
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;

/**
 * Valid model objects for the tests that break exactly one thing about them.
 *
 * <p>Hand-typed, and deliberately not produced by {@link ArtefactManifestReader}: a fixture the
 * reader built would agree with the reader about any mistake the reader makes.
 */
final class ManifestFixtures {

    static final String SHA256 = "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";
    static final String MD5 = "9c86de1c45d2d93dae1ab43216b5864c";
    static final String MEMBER_SHA256 =
            "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c";
    static final String MEMBER_MD5 = "0b77b68fd859639d7421f1c5e006ade5";
    static final URI URL =
            URI.create("https://github.com/example/example/releases/download/t/artefact");

    /**
     * A download URL of its own for one tool, version and platform.
     *
     * <p>Distinct per record on purpose. Selection offers at most one row per download, so fixtures
     * that shared a URL would collapse into one row and a test about ordering would silently be
     * testing nothing. The tests that need two records to name <em>one</em> download say so by
     * passing the URL explicitly.
     *
     * @param tool the tool
     * @param version the version text
     * @param platform the artefact's platform
     * @return a URL unique to the three of them
     */
    static URI urlFor(ToolName tool, String version, HostPlatform platform) {
        return URI.create(
                "https://github.com/example/example/releases/download/t/"
                        + tool.id()
                        + "-"
                        + version
                        + "-"
                        + platform.id());
    }

    private ManifestFixtures() {}

    static FileHashes hashes() {
        return new FileHashes(MD5, SHA256);
    }

    static FileHashes memberHashes() {
        return new FileHashes(MEMBER_MD5, MEMBER_SHA256);
    }

    static ArtefactLicence licence() {
        return new ArtefactLicence(
                "Apache-2.0",
                URI.create("https://raw.githubusercontent.com/example/example/t/LICENSE"),
                "upstream LICENSE at tag t is the Apache License 2.0");
    }

    static ArchiveMember member() {
        return new ArchiveMember("percolator", 2538632, memberHashes(), "bin/percolator");
    }

    static HostPlatform platform(HostOperatingSystem os, HostArchitecture arch) {
        return new HostPlatform(os, arch);
    }

    /**
     * A valid named-member record.
     *
     * @param tool the tool
     * @param version the version text
     * @param platform the artefact's platform
     * @return the record
     */
    static ArtefactRecord namedMember(ToolName tool, String version, HostPlatform platform) {
        return new ArtefactRecord(
                tool,
                ToolVersion.parse(version),
                "rel-t",
                platform,
                ArtefactKind.ZIP,
                urlFor(tool, version, platform),
                946303,
                hashes(),
                Optional.of(member()),
                Optional.empty(),
                true,
                licence(),
                List.of(),
                List.of(),
                List.of(),
                MinimumHostRequirements.none(),
                ToolVersion.parse("0.1.0"));
    }

    /**
     * A valid whole-artefact record.
     *
     * @param tool the tool
     * @param version the version text
     * @param platform the artefact's platform
     * @return the record
     */
    static ArtefactRecord wholeArtefact(ToolName tool, String version, HostPlatform platform) {
        return wholeArtefact(tool, version, platform, urlFor(tool, version, platform));
    }

    /**
     * A valid whole-artefact record naming a download the caller chooses.
     *
     * <p>For the tests about one download carried on more than one platform, which is what PDV and
     * the Limelight converter really are.
     *
     * @param tool the tool
     * @param version the version text
     * @param platform the artefact's platform
     * @param url the download
     * @return the record
     */
    static ArtefactRecord wholeArtefact(
            ToolName tool, String version, HostPlatform platform, URI url) {
        return new ArtefactRecord(
                tool,
                ToolVersion.parse(version),
                "rel-t",
                platform,
                ArtefactKind.BARE_EXECUTABLE,
                url,
                946303,
                hashes(),
                Optional.empty(),
                Optional.of("bin/tool"),
                true,
                licence(),
                List.of(),
                List.of(),
                List.of(),
                MinimumHostRequirements.none(),
                ToolVersion.parse("0.1.0"));
    }

    /**
     * A companion that takes named files out of a package payload.
     *
     * @param id the companion identifier
     * @param installedPath where its one member is installed
     * @return the companion
     */
    static ArtefactCompanion payloadCompanion(String id, String installedPath) {
        return new ArtefactCompanion(
                id,
                ArtefactKind.DEB_PAYLOAD,
                URI.create("https://github.com/example/example/releases/download/t/payload"),
                1852660,
                hashes(),
                false,
                Optional.empty(),
                "the two schemas no portable archive ships",
                List.of(
                        new ArchiveMember(
                                "usr/share/xml/percolator/percolator_out.xsd",
                                10388,
                                memberHashes(),
                                installedPath)));
    }
}
