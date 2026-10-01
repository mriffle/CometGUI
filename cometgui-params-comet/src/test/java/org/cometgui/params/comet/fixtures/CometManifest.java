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

package org.cometgui.params.comet.fixtures;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;

/**
 * The Comet rows of the artefact manifest, read through the project's one JSON reader.
 *
 * <p>The release matrix is <em>read</em>, never assumed: every test that needs to know which Comet
 * versions and platforms exist asks this class, and this class asks {@code manifests/tools.json}.
 * Only the six fields the fixture tests need are taken from each row; the installer's own reader,
 * {@code org.cometgui.install.registry.ArtefactManifestReader}, is the authority on the rest and on
 * validation, and this module does not depend on it.
 */
public final class CometManifest {

    /** The manifest's {@code tool} value for Comet. */
    public static final String COMET = "comet";

    private CometManifest() {}

    /**
     * One Comet artefact row.
     *
     * @param version the manifest's version string, e.g. {@code 2026.02.2}
     * @param releaseTag the upstream release tag, e.g. {@code v2026.02.2}
     * @param os the manifest's operating system, e.g. {@code linux}
     * @param arch the manifest's architecture, e.g. {@code x86-64}
     * @param url where upstream publishes the binary
     * @param sha256 the pinned SHA-256, lower-case hexadecimal
     */
    public record Row(
            String version, String releaseTag, String os, String arch, String url, String sha256) {

        /**
         * The platform as {@code os/arch}, for messages.
         *
         * @return the platform
         */
        public String platform() {
            return os + "/" + arch;
        }

        /**
         * The fixture directory name for this platform, {@code <os>-<arch>}.
         *
         * @return the directory name
         */
        public String directoryName() {
            return os + "-" + arch;
        }

        /**
         * Whether this is the one platform this project's host can execute.
         *
         * @return {@code true} for {@code linux}/{@code x86-64}
         */
        public boolean isLinuxX8664() {
            return "linux".equals(os) && "x86-64".equals(arch);
        }
    }

    /**
     * Every Comet row in a manifest file, in manifest order.
     *
     * @param manifest the manifest file
     * @return the Comet rows
     * @throws IOException if the file cannot be read
     * @throws AssertionError if the document is not the shape this reads
     */
    public static List<Row> cometRows(Path manifest) throws IOException {
        JsonValue root = JsonReader.parse(Files.readString(manifest, StandardCharsets.UTF_8));
        if (!(root instanceof JsonValue.JsonObject object)) {
            throw new AssertionError(manifest + " is not a JSON object");
        }
        JsonValue artefacts =
                object.member("artefacts")
                        .orElseThrow(() -> new AssertionError(manifest + " has no \"artefacts\""));
        if (!(artefacts instanceof JsonValue.JsonArray array)) {
            throw new AssertionError(manifest + "'s \"artefacts\" is not an array");
        }
        List<Row> rows = new ArrayList<>();
        for (JsonValue element : array.elements()) {
            if (!(element instanceof JsonValue.JsonObject row)) {
                throw new AssertionError(manifest + " has an artefact that is not an object");
            }
            if (COMET.equals(string(manifest, row, "tool"))) {
                rows.add(
                        new Row(
                                string(manifest, row, "version"),
                                string(manifest, row, "releaseTag"),
                                string(manifest, row, "os"),
                                string(manifest, row, "arch"),
                                string(manifest, row, "url"),
                                string(manifest, row, "sha256")));
            }
        }
        return List.copyOf(rows);
    }

    /**
     * The distinct Comet versions in a manifest, in first-appearance order.
     *
     * @param manifest the manifest file
     * @return the versions
     * @throws IOException if the file cannot be read
     */
    public static Set<String> cometVersions(Path manifest) throws IOException {
        Set<String> versions = new LinkedHashSet<>();
        for (Row row : cometRows(manifest)) {
            versions.add(row.version());
        }
        return versions;
    }

    /**
     * The repository's own manifest.
     *
     * @return {@code manifests/tools.json} under the repository root
     */
    public static Path repositoryManifest() {
        return UpstreamMirror.repositoryRoot().resolve(UpstreamMirror.MANIFEST);
    }

    private static String string(Path manifest, JsonValue.JsonObject row, String name) {
        JsonValue value =
                row.member(name)
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                manifest
                                                        + " has an artefact with no \""
                                                        + name
                                                        + "\""));
        if (!(value instanceof JsonValue.JsonString text)) {
            throw new AssertionError(
                    manifest + " has an artefact whose \"" + name + "\" is not a string");
        }
        return text.value();
    }
}
