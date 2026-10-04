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
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Where the real {@code comet -q} and {@code comet -p} fixtures are, by version, platform and mode.
 *
 * <p>The layout is {@code fixtures/comet/<version>/<os>-<arch>/comet-{q,p}.params} plus a {@code
 * SHA256SUMS} beside them, on the test class path, with {@code <version>}, {@code <os>} and {@code
 * <arch>} spelled exactly as {@code manifests/tools.json} spells them. Every file is the unmodified
 * {@code comet.params.new} the pinned binary wrote; {@link CometFixtureRealBinaryTest} re-proves
 * that on every Linux build. <strong>Later tests read fixtures through this class</strong> rather
 * than by path, so that the layout is stated once.
 */
public final class CometFixtures {

    /** The class-path directory holding every Comet fixture. */
    public static final String RESOURCE_ROOT = "/fixtures/comet";

    /** The checksum file in each fixture directory, in {@code sha256sum} format. */
    public static final String SHA256SUMS = "SHA256SUMS";

    /** Comet 2026.02.2, the release Phase 06 was built on, as the manifest spells it. */
    public static final String COMET_2026_02_2 = "2026.02.2";

    /** Comet 2026.03.0, the default from specification revision 12 ({@code D-010}). */
    public static final String COMET_2026_03_0 = "2026.03.0";

    /** The one platform this project's host can execute, as a fixture directory name. */
    public static final String LINUX_X86_64 = "linux-x86-64";

    /** The file Comet writes into its working directory under either option. */
    public static final String WRITTEN_FILE = "comet.params.new";

    private CometFixtures() {}

    /** Which of Comet's two parameter dumps a fixture holds. */
    public enum Mode {
        /**
         * {@code comet -q}: the complete parameter file, the schema source ({@code R-PARAM-01}).
         */
        COMPLETE("-q", "comet-q.params"),
        /** {@code comet -p}: the default parameter file, the fallback ({@code R-PARAM-02}). */
        DEFAULTS("-p", "comet-p.params");

        private final String argument;
        private final String fileName;

        Mode(String argument, String fileName) {
            this.argument = argument;
            this.fileName = fileName;
        }

        /**
         * The command-line option that makes Comet write this dump.
         *
         * @return {@code -q} or {@code -p}
         */
        public String argument() {
            return argument;
        }

        /**
         * The fixture's file name.
         *
         * @return {@code comet-q.params} or {@code comet-p.params}
         */
        public String fileName() {
            return fileName;
        }
    }

    /**
     * The fixture root on the test class path, as a directory.
     *
     * @return the directory holding one subdirectory per Comet version
     */
    public static Path root() {
        URL url = CometFixtures.class.getResource(RESOURCE_ROOT);
        if (url == null) {
            throw new AssertionError(
                    "the class path holds no "
                            + RESOURCE_ROOT
                            + "; the test resources are missing");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException impossible) {
            throw new AssertionError(url + " is not a URI", impossible);
        }
    }

    /**
     * Every Comet version that has a fixture directory under a root, whether or not the manifest
     * names it: the drift test runs for each, so a captured release cannot sit unchecked.
     *
     * @param root the fixture root, {@link #root()} or a test's copy of it
     * @return the version directory names, sorted
     * @throws IOException if the root cannot be listed
     */
    public static List<String> versions(Path root) throws IOException {
        TreeSet<String> versions = new TreeSet<>();
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory)
                    .forEach(child -> versions.add(root.relativize(child).toString()));
        }
        return List.copyOf(versions);
    }

    /**
     * One fixture directory under a given root, without checking that it exists.
     *
     * @param root the fixture root
     * @param version the Comet version, as the manifest spells it
     * @param platform the {@code <os>-<arch>} directory name
     * @return the directory
     */
    public static Path directory(Path root, String version, String platform) {
        return root.resolve(version).resolve(platform);
    }

    /**
     * One fixture file, which must exist.
     *
     * @param version the Comet version, as the manifest spells it
     * @param platform the {@code <os>-<arch>} directory name
     * @param mode which dump
     * @return the file
     * @throws AssertionError if it does not exist
     */
    public static Path file(String version, String platform, Mode mode) {
        Path file = directory(root(), version, platform).resolve(mode.fileName());
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "there is no "
                            + mode.argument()
                            + " fixture for Comet "
                            + version
                            + " on "
                            + platform
                            + " at "
                            + file
                            + "; capture it from the real binary as"
                            + " docs/developer/comet_parameter_schema.rst describes");
        }
        return file;
    }

    /**
     * The 2026.02.2 Linux x86-64 fixture for one mode -- the one most tests want.
     *
     * @param mode which dump
     * @return the file
     */
    public static Path linux202602(Mode mode) {
        return file(COMET_2026_02_2, LINUX_X86_64, mode);
    }

    /**
     * A fixture's exact bytes.
     *
     * @param version the Comet version
     * @param platform the {@code <os>-<arch>} directory name
     * @param mode which dump
     * @return the bytes
     * @throws IOException if it cannot be read
     */
    public static byte[] bytes(String version, String platform, Mode mode) throws IOException {
        return Files.readAllBytes(file(version, platform, mode));
    }

    /**
     * A fixture's lines. Comet writes ASCII with LF line ends; the bytes are decoded as UTF-8,
     * which is the same thing for ASCII and fails loudly for anything else.
     *
     * @param version the Comet version
     * @param platform the {@code <os>-<arch>} directory name
     * @param mode which dump
     * @return the lines, without terminators
     * @throws IOException if it cannot be read or is not UTF-8
     */
    public static List<String> lines(String version, String platform, Mode mode)
            throws IOException {
        return Files.readAllLines(file(version, platform, mode), StandardCharsets.UTF_8);
    }
}
