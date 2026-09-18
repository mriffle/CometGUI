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

package org.cometgui.install.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * The shipped manifest, the real artefact bytes and the hosts these tests answer for.
 *
 * <p>Everything here is the product's own data. The manifest is {@code manifests/tools.json} read
 * the way the application reads it, and the artefacts are the files the project fetched from
 * upstream by pinned URL and verified by SHA-256 into the gitignored mirror -- which is why the
 * mirror's absence is a failure here and not a skip: a Tool Manager suite that stops reading the
 * real manifest and the real bytes stops proving anything about the product a scientist installs.
 */
final class ToolManagerFixtures {

    /** Where the gitignored artefact mirror lives, relative to the repository root. */
    static final String MIRROR = "scratch/phase05/artefacts";

    /** The host every test here answers for, unless it says otherwise. */
    static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /** This project's own build host, hand-typed rather than read from the machine. */
    static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    /**
     * A host too old for Percolator 3.07.1's declared {@code GLIBC_2.34} floor and new enough for
     * 3.06.5's {@code GLIBC_2.14}, so that the advance check has something to refuse and something
     * to offer instead. Its C++ runtime is this host's, so the refusal names the C library.
     */
    static final HostRuntimeVersions CENTOS_7 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.17")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private ToolManagerFixtures() {
        throw new AssertionError(
                "ToolManagerFixtures is a fixture holder and is never instantiated");
    }

    /**
     * @return the manifest the application ships, read from the classpath as the product reads it
     * @throws IOException if it cannot be read
     */
    static ArtefactManifest shippedManifest() throws IOException {
        return ArtefactManifestReader.readFromClasspath();
    }

    /**
     * One row of the shipped manifest, by tool, version and platform.
     *
     * @param tool the tool
     * @param version the release, as upstream spells it
     * @param platform the build
     * @return the record
     * @throws IOException if the manifest cannot be read
     */
    static ArtefactRecord record(ToolName tool, String version, HostPlatform platform)
            throws IOException {
        ToolVersion wanted = ToolVersion.parse(version);
        List<ArtefactRecord> matching =
                shippedManifest().artefacts().stream()
                        .filter(record -> record.tool() == tool)
                        .filter(record -> record.version().equals(wanted))
                        .filter(record -> record.platform().equals(platform))
                        .toList();
        if (matching.size() != 1) {
            throw new AssertionError(
                    "manifests/tools.json holds "
                            + matching.size()
                            + " row(s) for "
                            + tool.id()
                            + " "
                            + version
                            + " "
                            + platform.id()
                            + ", and these tests install the artefact the product really ships");
        }
        return matching.get(0);
    }

    /**
     * One real artefact from the gitignored mirror.
     *
     * @param fileName the mirror's name for it
     * @return the file
     */
    static Path artefact(String fileName) {
        Path file = repositoryRoot().resolve(MIRROR).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "the real artefact \""
                            + fileName
                            + "\" is not in the mirror at "
                            + file
                            + ". The mirror is gitignored and holds the bytes upstream publishes;"
                            + " refill it by fetching each artefact from the URL in"
                            + " manifests/tools.json and checking its SHA-256 before use. This test"
                            + " fails rather than skips, because a Tool Manager suite that stops"
                            + " reading the real artefacts stops proving anything.");
        }
        return file;
    }

    /**
     * The repository root, found by walking up to the directory holding {@code manifests}.
     *
     * @return the root
     */
    static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError(
                    "no repository root above "
                            + Path.of("").toAbsolutePath()
                            + " holds a manifests directory");
        }
        return cursor;
    }
}
