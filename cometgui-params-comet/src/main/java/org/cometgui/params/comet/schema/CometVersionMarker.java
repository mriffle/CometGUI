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

package org.cometgui.params.comet.schema;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The version Comet writes on the first line of a parameter file, and its mapping to the version
 * string the manifest and {@link ToolVersion} use.
 *
 * <p>Comet 2026.02.2 writes {@code # comet_version 2026.02 rev. 2 (6edec91)}: the {@code
 * comet_version} macro {@code "2026.02 rev. 2"} from {@code CometSearch/Common.h}, then the first
 * seven characters of the commit it was built from in parentheses when the build recorded one
 * ({@code BuildCometVersionString}). The manifest, the release tag and {@link ToolVersion} call the
 * same release {@code 2026.02.2}. The mapping is: release {@code YYYY.NN} and revision {@code R}
 * give {@code YYYY.NN.R}; the build hash is kept but is not part of the version.
 *
 * <p>Comet's own check of a marker is lenient (any text containing {@code 2026.0}, {@code 2025.0}
 * or {@code 2024.0} passes). This parser is strict, so that a marker it cannot map is reported
 * rather than guessed at.
 *
 * @param release the {@code YYYY.NN} part, as written
 * @param revision the number after {@code rev.}
 * @param build the build hash in parentheses, if present
 */
public record CometVersionMarker(String release, int revision, Optional<String> build) {

    /** The text before the version on a marker line. */
    public static final String LINE_PREFIX = "# comet_version ";

    private static final Pattern MARKER =
            Pattern.compile("(\\d{4}\\.\\d{2}) rev\\. (\\d{1,4})(?: \\(([0-9a-f]{7,40})\\))?");

    private static final Pattern RELEASE_VERSION =
            Pattern.compile("(\\d{4}\\.\\d{2})\\.(\\d{1,4})");

    /** Validates the components. */
    public CometVersionMarker {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(build, "build");
    }

    /**
     * Parses the version text of a marker, without the {@code # comet_version } prefix.
     *
     * @param text for example {@code 2026.02 rev. 2 (6edec91)}
     * @return the marker
     * @throws IllegalArgumentException if the text is not {@code YYYY.NN rev. R} with an optional
     *     {@code (hash)}, quoting what was rejected
     */
    public static CometVersionMarker parse(String text) {
        Objects.requireNonNull(text, "text");
        Matcher matcher = MARKER.matcher(text.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "\""
                            + text.strip()
                            + "\" is not a Comet version marker; expected \"YYYY.NN rev. R\""
                            + " optionally followed by \" (build hash)\", as in \"2026.02 rev. 2"
                            + " (6edec91)\"");
        }
        return new CometVersionMarker(
                matcher.group(1),
                Integer.parseInt(matcher.group(2)),
                Optional.ofNullable(matcher.group(3)));
    }

    /**
     * Parses a whole marker line, as the first line of a parameter file holds it.
     *
     * @param line for example {@code # comet_version 2026.02 rev. 2 (6edec91)}
     * @return the marker
     * @throws IllegalArgumentException if the line does not start with {@value #LINE_PREFIX} or
     *     what follows is not a marker
     */
    public static CometVersionMarker parseLine(String line) {
        Objects.requireNonNull(line, "line");
        if (!line.startsWith(LINE_PREFIX)) {
            throw new IllegalArgumentException(
                    "a Comet version marker line starts with \""
                            + LINE_PREFIX
                            + "\", and this one does not: \""
                            + line.strip()
                            + "\"");
        }
        return parse(line.substring(LINE_PREFIX.length()));
    }

    /**
     * The release-and-revision text Comet writes for a version, without a build hash.
     *
     * @param version a Comet version as the manifest spells it, such as {@code 2026.02.2}
     * @return for example {@code 2026.02 rev. 2}
     * @throws IllegalArgumentException if the version's text is not {@code YYYY.NN.R}
     */
    public static String releaseTextFor(ToolVersion version) {
        Objects.requireNonNull(version, "version");
        Matcher matcher = RELEASE_VERSION.matcher(version.text());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Comet version \""
                            + version.text()
                            + "\" is not YYYY.NN.R, so it has no comet_version marker spelling");
        }
        return matcher.group(1) + " rev. " + Integer.parseInt(matcher.group(2));
    }

    /**
     * The version the manifest and {@link ToolVersion} use for this marker.
     *
     * @return {@code YYYY.NN.R}
     */
    public ToolVersion toolVersion() {
        return ToolVersion.parse(release + "." + revision);
    }

    /**
     * The marker's text as Comet writes it after {@value #LINE_PREFIX}.
     *
     * @return for example {@code 2026.02 rev. 2 (6edec91)}
     */
    public String text() {
        return release + " rev. " + revision + build.map(hash -> " (" + hash + ")").orElse("");
    }
}
