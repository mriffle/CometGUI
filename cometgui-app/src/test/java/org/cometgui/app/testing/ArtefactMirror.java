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

package org.cometgui.app.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * The shipped manifest and the gitignored mirror of the bytes upstream publishes.
 *
 * <p>The mirror's absence is a <strong>failure</strong> and never a skip: a Tool Manager suite that
 * stops installing the real artefacts stops proving anything about the product a scientist runs,
 * and a skipped end-to-end test is the shape this project calls a check that cannot go red.
 */
public final class ArtefactMirror {

    /** Where the artefact mirror lives, relative to the repository root. */
    public static final String MIRROR = "scratch/phase05/artefacts";

    private ArtefactMirror() {
        throw new AssertionError("ArtefactMirror is a fixture holder and is never instantiated");
    }

    /**
     * One row of the shipped manifest, by tool, version and platform.
     *
     * @param tool the tool
     * @param version the release, as upstream spells it
     * @param platform the build
     * @return the record the product would really install
     * @throws IOException if the manifest cannot be read
     */
    public static ArtefactRecord record(ToolName tool, String version, HostPlatform platform)
            throws IOException {
        ToolVersion wanted = ToolVersion.parse(version);
        List<ArtefactRecord> matching =
                ArtefactManifestReader.readFromClasspath().artefacts().stream()
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
     * One real artefact from the mirror.
     *
     * @param fileName the mirror's name for it
     * @return the file
     */
    public static Path artefact(String fileName) {
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
                            + " fails rather than skips, because an install suite that stops"
                            + " reading the real artefacts stops proving anything.");
        }
        return file;
    }

    /**
     * The repository root, found by walking up to the directory holding {@code manifests}.
     *
     * @return the root
     */
    public static Path repositoryRoot() {
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
