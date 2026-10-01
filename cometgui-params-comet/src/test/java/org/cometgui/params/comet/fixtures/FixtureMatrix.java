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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the fixture tree against the release matrix in {@code manifests/tools.json}.
 *
 * <p>For every distinct Comet {@code version} in the manifest there must be a {@code linux-x86-64}
 * fixture directory holding both dumps and a {@code SHA256SUMS} that lists both and verifies. Every
 * other platform the manifest names for that version either has a fixture directory that verifies
 * in the same way, or is reported as <strong>never captured, because never executed here</strong>
 * -- which is a statement of what was not done, never a pass.
 */
public final class FixtureMatrix {

    private static final Pattern SUM_LINE = Pattern.compile("^([0-9a-f]{64}) [ *]([^/\\\\]+)$");

    private FixtureMatrix() {}

    /**
     * What the check found.
     *
     * @param problems every reason the fixture tree does not cover the manifest; empty when it does
     * @param neverCaptured one line per manifest Comet row whose platform has no fixtures, saying
     *     so
     * @param verified one line per fixture directory that verified
     */
    public record Report(List<String> problems, List<String> neverCaptured, List<String> verified) {

        /** Takes immutable copies. */
        public Report {
            problems = List.copyOf(problems);
            neverCaptured = List.copyOf(neverCaptured);
            verified = List.copyOf(verified);
        }
    }

    /**
     * Checks a fixture root against a manifest.
     *
     * @param manifest the artefact manifest
     * @param fixtureRoot the directory holding one subdirectory per Comet version
     * @return what was found
     * @throws IOException if a file cannot be read
     */
    public static Report check(Path manifest, Path fixtureRoot) throws IOException {
        List<String> problems = new ArrayList<>();
        List<String> neverCaptured = new ArrayList<>();
        List<String> verified = new ArrayList<>();
        List<CometManifest.Row> rows = CometManifest.cometRows(manifest);
        if (rows.isEmpty()) {
            problems.add(manifest + " names no Comet artefact at all, so there is no matrix");
        }
        for (String version : CometManifest.cometVersions(manifest)) {
            boolean linuxRowSeen = false;
            for (CometManifest.Row row : rows) {
                if (!row.version().equals(version)) {
                    continue;
                }
                Path directory = CometFixtures.directory(fixtureRoot, version, row.directoryName());
                if (row.isLinuxX8664()) {
                    linuxRowSeen = true;
                }
                if (!Files.isDirectory(directory)) {
                    if (row.isLinuxX8664()) {
                        problems.add(
                                "manifests/tools.json names Comet "
                                        + version
                                        + " for linux/x86-64 and there are no fixtures for it at "
                                        + directory
                                        + "; capture comet -q and comet -p from that binary as"
                                        + " docs/developer/comet_parameter_schema.rst describes");
                    } else {
                        neverCaptured.add(
                                "Comet "
                                        + version
                                        + " "
                                        + row.platform()
                                        + ": -q/-p output never captured, because never executed"
                                        + " here");
                    }
                    continue;
                }
                List<String> found = verifyDirectory(directory);
                if (found.isEmpty()) {
                    verified.add("Comet " + version + " " + row.platform() + ": " + directory);
                } else {
                    problems.addAll(found);
                }
            }
            if (!linuxRowSeen) {
                problems.add(
                        "manifests/tools.json names Comet "
                                + version
                                + " but no linux/x86-64 row for it, the one platform whose"
                                + " output this project can capture");
            }
        }
        return new Report(problems, neverCaptured, verified);
    }

    /**
     * Fails, naming every problem, unless the report has none.
     *
     * @param report what {@link #check} found
     * @throws AssertionError listing the problems, if there are any
     */
    public static void assertCovered(Report report) {
        if (!report.problems().isEmpty()) {
            throw new AssertionError(
                    "the Comet fixtures do not cover the release matrix in"
                            + " manifests/tools.json:\n  "
                            + String.join("\n  ", report.problems()));
        }
    }

    /**
     * Verifies one fixture directory: both dumps present, a {@code SHA256SUMS} listing both, and
     * every listed digest equal to its file's.
     *
     * @param directory the fixture directory
     * @return every problem found; empty when it verifies
     * @throws IOException if a file cannot be read
     */
    public static List<String> verifyDirectory(Path directory) throws IOException {
        List<String> problems = new ArrayList<>();
        for (CometFixtures.Mode mode : CometFixtures.Mode.values()) {
            if (!Files.isRegularFile(directory.resolve(mode.fileName()))) {
                problems.add(directory + " has no " + mode.fileName());
            }
        }
        Path sums = directory.resolve(CometFixtures.SHA256SUMS);
        if (!Files.isRegularFile(sums)) {
            problems.add(directory + " has no " + CometFixtures.SHA256SUMS);
            return problems;
        }
        Map<String, String> listed = new LinkedHashMap<>();
        int number = 0;
        for (String line : Files.readAllLines(sums, StandardCharsets.UTF_8)) {
            number++;
            Matcher matcher = SUM_LINE.matcher(line);
            if (!matcher.matches()) {
                problems.add(
                        sums + " line " + number + " is not a sha256sum line: \"" + line + "\"");
                continue;
            }
            if (listed.put(matcher.group(2), matcher.group(1)) != null) {
                problems.add(sums + " lists " + matcher.group(2) + " twice");
            }
        }
        for (CometFixtures.Mode mode : CometFixtures.Mode.values()) {
            if (!listed.containsKey(mode.fileName())) {
                problems.add(sums + " does not list " + mode.fileName());
            }
        }
        for (Map.Entry<String, String> entry : listed.entrySet()) {
            Path file = directory.resolve(entry.getKey());
            if (!Files.isRegularFile(file)) {
                problems.add(sums + " lists " + entry.getKey() + ", which does not exist");
                continue;
            }
            String actual = UpstreamMirror.sha256(file);
            if (!actual.equals(entry.getValue())) {
                problems.add(
                        file
                                + " has SHA-256 "
                                + actual
                                + " but "
                                + CometFixtures.SHA256SUMS
                                + " records "
                                + entry.getValue()
                                + "; the fixture has changed since it was captured");
            }
        }
        return problems;
    }
}
