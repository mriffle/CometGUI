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

package org.cometgui.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.events.RecoveredEventLog;
import org.cometgui.provenance.hashing.StreamingHashService;

/** Shared checks for the engine's integration tests. */
final class EngineAssertions {

    /** How long any one run may take before a test gives up on it: a failure bound, not a wait. */
    static final Duration RUN_BOUND = Duration.ofSeconds(120);

    private EngineAssertions() {}

    static RunResult awaitResult(RunHandle handle) throws InterruptedException {
        return handle.await(RUN_BOUND)
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "the run did not finish within "
                                                + RUN_BOUND
                                                + "; states "
                                                + handle.states()));
    }

    /** Reads the event log and requires it intact. */
    static List<ProvenanceEvent> intactEvents(Path log) throws IOException {
        RecoveredEventLog recovered = ProvenanceEventLogReader.recover(log);
        assertTrue(recovered.intact(), () -> "the event log has defects: " + recovered.defects());
        return recovered.events();
    }

    /** The "type stage state" of every stage event of one step, in log order. */
    static List<String> stageEvents(List<ProvenanceEvent> events, String stepId) {
        List<String> found = new ArrayList<>();
        for (ProvenanceEvent event : events) {
            if ((event.type() == ProvenanceEventType.STAGE_STARTED
                            || event.type() == ProvenanceEventType.STAGE_FINISHED)
                    && stepId.equals(event.payload().get("stage"))) {
                found.add(event.type().wireName() + " " + event.payload().get("state"));
            }
        }
        return found;
    }

    /** The one stage.finished event of a step. */
    static ProvenanceEvent finishedEvent(List<ProvenanceEvent> events, String stepId) {
        ProvenanceEvent found = null;
        for (ProvenanceEvent event : events) {
            if (event.type() == ProvenanceEventType.STAGE_FINISHED
                    && stepId.equals(event.payload().get("stage"))) {
                if (found != null) {
                    throw new AssertionError("two stage.finished events for " + stepId);
                }
                found = event;
            }
        }
        if (found == null) {
            throw new AssertionError("no stage.finished event for " + stepId);
        }
        return found;
    }

    private static final Pattern LOG_LINE =
            Pattern.compile(
                    "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
                            + " \\[(cometgui|stdout|stderr)\\] .*");

    /**
     * Reads a stage log, requiring every line to be a well-formed log line and the last to be the
     * process service's own record of how the stage ended.
     *
     * @return the log's lines
     */
    static List<String> parsedStageLog(Path log, String stageId) throws IOException {
        List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        assertTrue(lines.size() >= 3, () -> log + " is too short: " + lines);
        for (String line : lines) {
            assertTrue(
                    LOG_LINE.matcher(line).matches(), () -> log + " has a malformed line: " + line);
        }
        String last = lines.get(lines.size() - 1);
        assertTrue(
                last.contains(" [cometgui] stage " + stageId + " ended: exit code "),
                () -> log + " does not end with the stage's end: " + last);
        return lines;
    }

    /**
     * Waits until the wall clock is past the second in which the file last changed, so that the
     * hash cache will remember it ({@code CachingHashService}'s "settled" rule). This waits on an
     * observable state -- the clock passing a computed instant -- not for a fixed time.
     */
    static void awaitSettled(Path file) throws IOException {
        FileTime modified = Files.getLastModifiedTime(file);
        FileTime changed = (FileTime) Files.getAttribute(file, "unix:ctime");
        Instant latest =
                modified.toInstant().isAfter(changed.toInstant())
                        ? modified.toInstant()
                        : changed.toInstant();
        Instant settled = latest.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1).plusMillis(20);
        while (Instant.now().isBefore(settled)) {
            LockSupport.parkUntil(settled.toEpochMilli());
        }
    }

    static String sha256(Path file) throws IOException {
        return new StreamingHashService().hash(file).sha256();
    }
}
