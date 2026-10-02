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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The migration fixture: the real {@code comet -q} and {@code comet -p} output of an <em>older</em>
 * Comet release, the second real version a schema migration needs.
 *
 * <p>Comet 2024.01.0 is not in the release matrix ({@code manifests/tools.json} is the product's
 * install list, and an older Comet is not offered to users), so its binary is pinned <strong>here,
 * once</strong>: {@link #ROW} names its upstream URL and the SHA-256 recorded when it was
 * downloaded. The binary lives in the gitignored mirror {@link #MIRROR}; {@link
 * MigrationFixtureRealBinaryTest} stages it, checks the SHA-256 before running it, runs it through
 * {@code ProcessService} and requires the fixtures to equal its output byte for byte. The fixtures
 * sit under {@link #RESOURCE_ROOT}, beside -- not inside -- {@link CometFixtures#RESOURCE_ROOT}, so
 * the release-matrix check ({@link FixtureMatrix}) neither counts nor requires them.
 */
public final class MigrationFixtures {

    /** The class-path directory holding the migration fixtures. */
    public static final String RESOURCE_ROOT = "/fixtures/comet-migration";

    /** The gitignored mirror holding the older binary, relative to the repository root. */
    public static final String MIRROR = "scratch/phase06/artefacts";

    /** The older release, spelled as the metadata and the manifest spell versions. */
    public static final String VERSION = "2024.01.0";

    /**
     * The pinned binary: upstream's Linux x86-64 asset of release {@code v2024.01.0}, SHA-256 as
     * recorded on download (2026-10-02; two independent downloads agreed, 6 889 456 bytes, the size
     * the GitHub release API lists). Upstream publishes no digest for this asset.
     */
    public static final CometManifest.Row ROW =
            new CometManifest.Row(
                    VERSION,
                    "v2024.01.0",
                    "linux",
                    "x86-64",
                    "https://github.com/UWPR/Comet/releases/download/v2024.01.0/comet.linux.exe",
                    "2834f928594ae57a1fdc0f4a5591bfc0e2b5c91e3e9c33a5ab2e9f508a942379");

    /** What pins {@link #ROW}'s SHA-256, for a failure message. */
    public static final String PINNED_BY =
            "MigrationFixtures.ROW (the migration fixture's capture record in"
                    + " docs/developer/comet_parameter_schema.rst)";

    /** How to refill {@link #MIRROR}, for a failure message. */
    public static final String REFILL =
            " The migration mirror is gitignored. Refill it by downloading "
                    + ROW.url()
                    + " to "
                    + MIRROR
                    + "/"
                    + ROW.releaseTag()
                    + "__comet.linux.exe and checking it with sha256sum against "
                    + ROW.sha256()
                    + " before use. This test fails rather than skips, because a fixture that is"
                    + " no longer compared with the real binary is no longer proved real.";

    private MigrationFixtures() {}

    /**
     * The migration fixture root on the test class path.
     *
     * @return the directory holding one subdirectory per older Comet version
     */
    public static Path root() {
        URL url = MigrationFixtures.class.getResource(RESOURCE_ROOT);
        if (url == null) {
            throw new AssertionError("the class path holds no " + RESOURCE_ROOT);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException impossible) {
            throw new AssertionError(url + " is not a URI", impossible);
        }
    }

    /**
     * The fixture directory of {@link #VERSION} on Linux x86-64.
     *
     * @return the directory
     */
    public static Path directory() {
        return CometFixtures.directory(root(), VERSION, CometFixtures.LINUX_X86_64);
    }

    /**
     * One migration fixture, which must exist.
     *
     * @param mode which dump
     * @return the file
     * @throws AssertionError if it does not exist
     */
    public static Path file(CometFixtures.Mode mode) {
        Path file = directory().resolve(mode.fileName());
        if (!Files.isRegularFile(file)) {
            throw new AssertionError("there is no migration fixture " + file);
        }
        return file;
    }

    /**
     * A migration fixture's exact bytes.
     *
     * @param mode which dump
     * @return the bytes
     * @throws IOException if it cannot be read
     */
    public static byte[] bytes(CometFixtures.Mode mode) throws IOException {
        return Files.readAllBytes(file(mode));
    }

    /**
     * A migration fixture's text.
     *
     * @param mode which dump
     * @return the text, decoded as UTF-8 (Comet writes ASCII)
     * @throws IOException if it cannot be read
     */
    public static String text(CometFixtures.Mode mode) throws IOException {
        return Files.readString(file(mode), StandardCharsets.UTF_8);
    }

    /**
     * A migration fixture's lines.
     *
     * @param mode which dump
     * @return the lines, without terminators
     * @throws IOException if it cannot be read
     */
    public static List<String> lines(CometFixtures.Mode mode) throws IOException {
        return Files.readAllLines(file(mode), StandardCharsets.UTF_8);
    }
}
