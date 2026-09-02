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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;

/**
 * How to make one tool say what version it is, and how to read the answer.
 *
 * <p>The version a tool prints is not the version a manifest writes. Comet says {@code Comet
 * version 2026.02 rev. 2 (6edec91)} and the manifest says {@code 2026.02.2}; Percolator says {@code
 * Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18} and the manifest says {@code 3.07.1}.
 * So the pattern's capture groups are <strong>joined with dots</strong> to form the version text:
 * one group for Percolator, three for Comet. That keeps the translation in data rather than in a
 * per-tool branch, and it is what makes {@code R-TOOL-06}'s identity stage a parse rather than a
 * string comparison.
 *
 * <p><strong>Both banners here were produced by execution on this project's own host on
 * 2026-09-02</strong>, and {@link #evidence()} records which run. A banner for a tool nobody has
 * run would be a rule that has never seen its subject, so this class ships two and no more: PDV and
 * the Limelight converter are JARs whose identity needs a JVM launch, which is phase 05 unit 7's
 * work, and {@link StagedToolProbe} refuses by name rather than guessing when it is asked for a
 * banner it does not have.
 *
 * @param arguments what to pass the executable to make it print its banner
 * @param pattern the pattern to look for, whose capture groups joined with dots are the version
 * @param evidence the run this banner was taken from, in one sentence
 */
public record VersionBanner(List<String> arguments, Pattern pattern, String evidence) {

    /**
     * Validates the banner and takes an immutable copy of the arguments.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the pattern has no capture group, or the evidence is
     *     blank
     */
    public VersionBanner {
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(pattern, "pattern");
        if (pattern.matcher("").groupCount() == 0) {
            throw new IllegalArgumentException(
                    "a version banner pattern must capture the version's components, but \""
                            + pattern.pattern()
                            + "\" captures nothing");
        }
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.isBlank()) {
            throw new IllegalArgumentException(
                    "evidence must say which run this banner was taken from; a banner nobody has"
                            + " seen printed is a rule that has never seen its subject");
        }
    }

    /**
     * The arguments to pass, immutable and in order.
     *
     * @return the arguments, possibly empty
     */
    @Override
    public List<String> arguments() {
        return List.copyOf(arguments);
    }

    /**
     * Reads the version out of lines a tool printed.
     *
     * @param lines the lines to search, in the order they should be searched
     * @return the version, or empty when no line carries the banner or the components it carries
     *     are not a version this product accepts
     * @throws NullPointerException if {@code lines} is {@code null}
     */
    public Optional<ToolVersion> readFrom(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return parse(joinedComponents(matcher));
            }
        }
        return Optional.empty();
    }

    private static String joinedComponents(Matcher matcher) {
        List<String> components = new ArrayList<>(matcher.groupCount());
        for (int group = 1; group <= matcher.groupCount(); group++) {
            components.add(matcher.group(group));
        }
        return String.join(".", components);
    }

    /*
     * A banner that matched but does not spell a version this product accepts is UNPARSEABLE, not a
     * guess: R-TOOL-06's identity stage exists to parse a version, and a partly-read one would be
     * recorded in provenance as a fact.
     */
    private static Optional<ToolVersion> parse(String text) {
        try {
            return Optional.of(ToolVersion.parse(text));
        } catch (IllegalArgumentException notAVersion) {
            return Optional.empty();
        }
    }

    /**
     * Percolator's banner.
     *
     * @return the banner, observed on 3.06.5 and 3.07.1
     */
    public static VersionBanner percolator() {
        return new VersionBanner(
                List.of("--help"),
                Pattern.compile("Percolator version (\\d{1,4}\\.\\d{1,4}(?:\\.\\d{1,4})?),"),
                "executed on this project's Debian 12 host on 2026-09-02: the real portable"
                        + " binaries of Percolator 3.06.5 and 3.07.1 answer --help with exit 0 and"
                        + " print \"Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18\" on"
                        + " STANDARD ERROR, leaving standard output empty");
    }

    /**
     * Comet's banner.
     *
     * @return the banner, observed on 2026.02.2
     */
    public static VersionBanner comet() {
        return new VersionBanner(
                List.of("-h"),
                Pattern.compile("Comet version \"?(\\d{1,4})\\.(\\d{1,2}) rev\\. (\\d{1,4})"),
                "executed on this project's Debian 12 host on 2026-09-02: the real"
                        + " comet.linux.exe answers -h with exit "
                        + "1 -- a non-zero exit from a working"
                        + " binary -- and prints \" Comet version 2026.02 rev. 2 (6edec91)\" on"
                        + " STANDARD ERROR, whose three numbers "
                        + "are the manifest's 2026.02.2. Run with"
                        + " no arguments at all it prints the same "
                        + "numbers QUOTED, on standard output,"
                        + " which is why the opening quote is "
                        + "optional here and why both streams are"
                        + " searched");
    }

    /**
     * The banners this project has actually watched a tool print.
     *
     * @return the banners by tool, immutable
     */
    public static Map<ToolName, VersionBanner> observedOnThisProject() {
        Map<ToolName, VersionBanner> banners = new LinkedHashMap<>();
        banners.put(ToolName.COMET, comet());
        banners.put(ToolName.PERCOLATOR, percolator());
        return Map.copyOf(banners);
    }
}
