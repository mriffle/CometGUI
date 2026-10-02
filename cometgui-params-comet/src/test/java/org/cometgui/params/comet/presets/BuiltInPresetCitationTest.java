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

package org.cometgui.params.comet.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.params.comet.fixtures.UpstreamMirror;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every built-in preset value is what Comet's own documentation says, checked rather than trusted.
 *
 * <p>Comet publishes example parameter files for the three instrument-resolution patterns with its
 * 2026.02 parameter pages. They are checked in unmodified under {@code
 * fixtures/comet-presets/parameters_202602/} (downloaded 2026-10-02 and pinned by {@code
 * SHA256SUMS}; they are Comet's documentation, Apache-2.0, not CometGUI's input). This test
 * requires each built-in preset to cite its file by name, line and SHA-256, the cited line to
 * declare exactly the preset's value, and the presets to set exactly the parameters on which
 * Comet's three examples differ.
 */
class BuiltInPresetCitationTest {

    private static final List<Preset> BUILT_IN =
            PresetLoader.loadBundled(MetadataLoader.loadBundled());

    private static final Pattern CITED =
            Pattern.compile(
                    "^https://uwpr\\.github\\.io/Comet/parameters/parameters_202602/"
                            + "(comet\\.params\\.[a-z-]+) line ([0-9]+): ([a-z_]+) = (\\S+)"
                            + " \\(fetched 2026-10-02, SHA-256 ([0-9a-f]{64})\\);.*");

    private static final Pattern DECLARATION =
            Pattern.compile("^([A-Za-z0-9_]+) *= *([^#]*?) *(#.*)?$");

    private static Path directory() {
        try {
            return Path.of(
                    BuiltInPresetCitationTest.class
                            .getResource("/fixtures/comet-presets/parameters_202602")
                            .toURI());
        } catch (URISyntaxException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static List<String> lines(String file) throws IOException {
        return Files.readAllLines(directory().resolve(file), StandardCharsets.UTF_8);
    }

    private static Map<String, String> declarations(String file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines(file)) {
            if (line.startsWith("[COMET_ENZYME_INFO]")) {
                break;
            }
            Matcher matcher = DECLARATION.matcher(line);
            if (matcher.matches()) {
                values.put(matcher.group(1), matcher.group(2));
            }
        }
        return values;
    }

    @Test
    @DisplayName("Comet's three example files are the bytes their SHA256SUMS pins")
    void theExamplesAreUnchanged() throws IOException {
        Path sums = directory().resolve("SHA256SUMS");
        List<String> listed = Files.readAllLines(sums, StandardCharsets.UTF_8);
        assertEquals(3, listed.size());
        for (String line : listed) {
            String[] parts = line.split("  ", 2);
            assertEquals(parts[0], UpstreamMirror.sha256(directory().resolve(parts[1])), line);
        }
    }

    @Test
    @DisplayName("each value is declared on the cited line of the cited, pinned Comet example")
    void everyValueIsOnItsCitedLine() throws IOException {
        int checked = 0;
        for (Preset preset : BUILT_IN) {
            String file = "comet.params." + preset.id();
            assertTrue(preset.source().orElseThrow().endsWith("/" + file));
            String sha = UpstreamMirror.sha256(directory().resolve(file));
            for (PresetDelta delta : preset.deltas()) {
                String citation = delta.citation().orElseThrow();
                Matcher matcher = CITED.matcher(citation);
                assertTrue(matcher.matches(), citation);
                assertEquals(file, matcher.group(1), citation);
                assertEquals(delta.parameter(), matcher.group(3), citation);
                assertEquals(delta.value(), matcher.group(4), citation);
                assertEquals(sha, matcher.group(5), citation);
                String line = lines(file).get(Integer.parseInt(matcher.group(2)) - 1);
                Matcher declared = DECLARATION.matcher(line);
                assertTrue(declared.matches(), line);
                assertEquals(delta.parameter(), declared.group(1), line);
                assertEquals(delta.value(), declared.group(2), line);
                assertTrue(
                        citation.contains(
                                " parameter page https://uwpr.github.io/Comet/parameters/"
                                        + "parameters_202602/"
                                        + delta.parameter()
                                        + ".html"),
                        citation);
                checked++;
            }
        }
        assertEquals(24, checked);
    }

    @Test
    @DisplayName("the presets set exactly the parameters Comet's three examples disagree on")
    void thePresetsSetWhatTheExamplesVary() throws IOException {
        Map<String, String> lowLow = declarations("comet.params.low-low");
        Map<String, String> highLow = declarations("comet.params.high-low");
        Map<String, String> highHigh = declarations("comet.params.high-high");
        assertEquals(lowLow.keySet(), highHigh.keySet());
        assertEquals(lowLow.keySet(), highLow.keySet());
        Set<String> varying = new TreeSet<>();
        for (Map.Entry<String, String> entry : lowLow.entrySet()) {
            String name = entry.getKey();
            if (!entry.getValue().equals(highHigh.get(name))
                    || !entry.getValue().equals(highLow.get(name))) {
                varying.add(name);
            }
        }
        for (Preset preset : BUILT_IN) {
            Set<String> set = new TreeSet<>();
            preset.deltas().forEach(d -> set.add(d.parameter()));
            assertEquals(varying, set, preset.id());
            Map<String, String> example = declarations("comet.params." + preset.id());
            for (PresetDelta delta : preset.deltas()) {
                assertEquals(example.get(delta.parameter()), delta.value(), delta.parameter());
            }
        }
        assertEquals(8, varying.size(), varying.toString());
    }
}
