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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;

/**
 * What the parser, model and writer tests share: the real {@code comet -q} and {@code -p} output of
 * Comet 2026.02.2 (unit 1's fixtures, read through {@link CometFixtures}), the bundled metadata,
 * and a fixed build identity for the writer's header.
 */
public final class ParamsFiles {

    /** The Comet version every fixture is for. */
    public static final ToolVersion COMET = ToolVersion.parse(CometFixtures.COMET_2026_02_2);

    /** The CometGUI version the test writer names in its header. */
    public static final String BUILD_VERSION = "0.1.0-SNAPSHOT";

    private ParamsFiles() {}

    /**
     * The real {@code comet -q} output for 2026.02.2, linux/x86-64.
     *
     * @return its text
     */
    public static String complete() {
        return text(CometFixtures.Mode.COMPLETE);
    }

    /**
     * The real {@code comet -q} output of one release, linux/x86-64.
     *
     * @param version the release, which must have fixtures
     * @return its text
     */
    public static String complete(ToolVersion version) {
        return text(version.text(), CometFixtures.Mode.COMPLETE);
    }

    /**
     * The real {@code comet -p} output for 2026.02.2, linux/x86-64.
     *
     * @return its text
     */
    public static String defaults() {
        return text(CometFixtures.Mode.DEFAULTS);
    }

    private static String text(CometFixtures.Mode mode) {
        return text(CometFixtures.COMET_2026_02_2, mode);
    }

    private static String text(String version, CometFixtures.Mode mode) {
        try {
            return new String(
                    CometFixtures.bytes(version, CometFixtures.LINUX_X86_64, mode),
                    StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /**
     * The bundled metadata.
     *
     * @return the metadata
     */
    public static CuratedMetadata metadata() {
        return MetadataLoader.loadBundled();
    }

    /**
     * A build identity for the writer.
     *
     * @return version {@value #BUILD_VERSION}, commit unknown
     */
    public static BuildIdentity build() {
        return BuildIdentity.of(
                BUILD_VERSION, BuildIdentity.UNKNOWN_COMMIT, Instant.parse("2026-10-02T00:00:00Z"));
    }

    /**
     * The real {@code -q} text with a CONSTRUCTED block inserted before the line that starts with a
     * prefix. The result is test input, not Comet's output.
     *
     * @param beforeLineStarting the start of the line to insert before; must occur exactly once
     * @param block the lines to insert, each ending in {@code \n}
     * @return the edited text
     */
    public static String completeWith(String beforeLineStarting, String block) {
        return completeWith(COMET, beforeLineStarting, block);
    }

    /**
     * One release's real {@code -q} text with a CONSTRUCTED block inserted before the line that
     * starts with a prefix. The result is test input, not Comet's output.
     *
     * @param version the release, which must have fixtures
     * @param beforeLineStarting the start of the line to insert before; must occur exactly once
     * @param block the lines to insert, each ending in {@code \n}
     * @return the edited text
     */
    public static String completeWith(
            ToolVersion version, String beforeLineStarting, String block) {
        String text = complete(version);
        int at = text.indexOf("\n" + beforeLineStarting);
        if (at < 0 || text.indexOf("\n" + beforeLineStarting, at + 1) >= 0) {
            throw new AssertionError(
                    "\""
                            + beforeLineStarting
                            + "\" does not start exactly one line of the fixture");
        }
        return text.substring(0, at + 1) + block + text.substring(at + 1);
    }

    /**
     * The real {@code -q} text with one CONSTRUCTED replacement of a whole line.
     *
     * @param lineStarting the start of the line to replace; must occur exactly once
     * @param replacement the new line, without its {@code \n}
     * @return the edited text
     */
    public static String completeReplacing(String lineStarting, String replacement) {
        String text = complete();
        int at = text.indexOf("\n" + lineStarting);
        if (at < 0 || text.indexOf("\n" + lineStarting, at + 1) >= 0) {
            throw new AssertionError(
                    "\"" + lineStarting + "\" does not start exactly one line of the fixture");
        }
        int end = text.indexOf('\n', at + 1);
        return text.substring(0, at + 1) + replacement + text.substring(end);
    }
}
