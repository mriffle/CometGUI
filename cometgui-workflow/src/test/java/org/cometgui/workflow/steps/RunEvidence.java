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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.events.RecoveredEventLog;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;

/**
 * Reads what a run left on disk, with the project's own readers -- never from the objects the run
 * returned -- so a test proves what was recorded.
 */
final class RunEvidence {

    /** One stage-log line: a fixed-width UTC timestamp, a stream tag, the text. */
    static final Pattern STAGE_LOG_LINE =
            Pattern.compile(
                    "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
                            + " \\[(cometgui|stdout|stderr)\\] .*");

    private RunEvidence() {}

    /** The run's event log, recovered by the project's reader; it must be intact. */
    static List<ProvenanceEvent> events(RunLayout layout) throws IOException {
        RecoveredEventLog log = ProvenanceEventLogReader.recover(layout.eventLogFile());
        assertTrue(log.intact(), () -> "the event log has defects: " + log.defects());
        return log.events();
    }

    /** The payload of the last {@code stage.finished} event of a step. */
    static Map<String, String> finished(RunLayout layout, EngineStep step) throws IOException {
        Map<String, String> last = null;
        for (ProvenanceEvent event : events(layout)) {
            if (event.type() == ProvenanceEventType.STAGE_FINISHED
                    && step.id().equals(event.payload().get(ProvenanceEvent.STAGE_KEY))) {
                last = event.payload();
            }
        }
        assertTrue(last != null, "no stage.finished event for " + step.id());
        return last;
    }

    /**
     * The sequence number of a step's last event of one type -- where it stands in the order the
     * log recorded.
     */
    static long sequenceOf(RunLayout layout, ProvenanceEventType type, EngineStep step)
            throws IOException {
        long found = -1;
        for (ProvenanceEvent event : events(layout)) {
            if (event.type() == type
                    && step.id().equals(event.payload().get(ProvenanceEvent.STAGE_KEY))) {
                found = event.sequence();
            }
        }
        assertTrue(found > 0, "no " + type + " event for " + step.id());
        return found;
    }

    /** The run's {@code provenance.json}, read back by the project's reader. */
    static ProvenanceManifest manifest(RunLayout layout) throws IOException {
        return ManifestReader.readFrom(layout.provenanceJsonFile());
    }

    /** The tool records of one tool, in recorded order. */
    static List<ToolRecord> tools(ProvenanceManifest manifest, String name) {
        List<ToolRecord> tools = new ArrayList<>();
        for (ToolRecord tool : manifest.tools()) {
            if (tool.name().equals(name)) {
                tools.add(tool);
            }
        }
        return tools;
    }

    /** Every line of a stage log, each required to be a well-formed stage-log line. */
    static List<String> stageLog(Path log) throws IOException {
        List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        assertTrue(!lines.isEmpty(), log + " is empty");
        for (String line : lines) {
            assertTrue(STAGE_LOG_LINE.matcher(line).matches(), () -> log + ": " + line);
        }
        return lines;
    }

    /** The files of a directory, by name. */
    static List<String> listing(Path directory) throws IOException {
        try (Stream<Path> listed = Files.list(directory)) {
            return listed.map(path -> String.valueOf(path.getFileName())).sorted().toList();
        }
    }

    /** The data rows of a PIN: every line after the header. */
    static long pinRows(Path pin) throws IOException {
        List<String> lines = Files.readAllLines(pin, StandardCharsets.ISO_8859_1);
        assertTrue(lines.get(0).startsWith("SpecId\t"), pin + " does not begin with its header");
        return lines.size() - 1L;
    }

    /** How many lines of a PIN are a header. */
    static long headerLines(Path pin) throws IOException {
        long headers = 0;
        for (String line : Files.readAllLines(pin, StandardCharsets.ISO_8859_1)) {
            if (line.startsWith("SpecId\t")) {
                headers++;
            }
        }
        return headers;
    }

    /** Requires one event payload member. */
    static void assertDetail(Map<String, String> payload, String key, String expected) {
        assertEquals(expected, payload.get(key), () -> key + " in " + payload);
    }

    /**
     * A tree hash: every path under a directory, the directory itself included, with its kind,
     * size, SHA-256, last-modified time and POSIX permissions. Two equal snapshots mean nothing
     * under the directory was added, removed, rewritten -- even with the same bytes -- or
     * re-permissioned.
     */
    static Map<String, String> tree(Path root) throws IOException {
        Map<String, String> tree = new TreeMap<>();
        List<Path> paths;
        try (Stream<Path> walked = Files.walk(root)) {
            paths = walked.toList();
        }
        for (Path path : paths) {
            String relative = root.relativize(path).toString();
            String permissions =
                    PosixFilePermissions.toString(
                            Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS));
            String modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toString();
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                tree.put(relative + "/", "directory " + modified + " " + permissions);
            } else {
                tree.put(
                        relative,
                        Files.size(path)
                                + " "
                                + RealComet.sha256(path)
                                + " "
                                + modified
                                + " "
                                + permissions);
            }
        }
        return tree;
    }
}
