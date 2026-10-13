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

package org.cometgui.workflow.provenance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The run list: every run directory, whatever became of its run. */
class ProvenanceListTest {

    @TempDir private Path tmp;

    private RunDirectories dirs;

    @BeforeEach
    void create() throws IOException {
        dirs = RunDirectories.create(tmp);
    }

    @AfterEach
    void close() throws IOException {
        dirs.close();
    }

    private RunLayout run(String id, int minute) throws IOException {
        return dirs.recordedRun(
                id,
                Instant.parse("2026-10-13T09:0" + minute + ":00Z"),
                RunDirectories.cometParams());
    }

    @Test
    @DisplayName("lists every outcome, in creation order, with id, time and whether it is sealed")
    void everyOutcome() throws IOException {
        RunLayout completed = run("run-a", 1);
        dirs.attempt(
                completed,
                Instant.parse("2026-10-13T09:01:01Z"),
                AttemptOutcome.SUCCEEDED,
                Instant.parse("2026-10-13T09:01:09Z"));
        Files.writeString(completed.provenanceJsonFile(), "{}", StandardCharsets.UTF_8);
        RunLayout failed = run("run-b", 2);
        dirs.attempt(
                failed,
                Instant.parse("2026-10-13T09:02:01Z"),
                AttemptOutcome.FAILED,
                Instant.parse("2026-10-13T09:02:09Z"));
        RunLayout cancelled = run("run-c", 3);
        dirs.attempt(
                cancelled,
                Instant.parse("2026-10-13T09:03:01Z"),
                AttemptOutcome.CANCELLED,
                Instant.parse("2026-10-13T09:03:09Z"));
        RunLayout running = run("run-d", 4);
        dirs.attempt(running, Instant.parse("2026-10-13T09:04:01Z"), AttemptOutcome.RUNNING, null);
        RunLayout interrupted = run("run-e", 5);
        dirs.attempt(
                interrupted, Instant.parse("2026-10-13T09:05:01Z"), AttemptOutcome.RUNNING, null);
        RunLayout prepared = run("run-f", 6);
        RunLayout damaged = run("run-g", 7);
        Files.writeString(damaged.runFile(), "{\"schemaVersion\": 1,", StandardCharsets.UTF_8);
        Path stray = Files.createDirectory(dirs.project().runsDirectory().resolve("zz-not-a-run"));
        Files.writeString(
                dirs.project().runsDirectory().resolve("notes.txt"),
                "a file, not a run\n",
                StandardCharsets.UTF_8);

        List<RunListing> listed =
                LoadedRunViewTest.reader().list(dirs.project(), Set.of(new RunId("run-d")));

        assertEquals(
                List.of(
                        "20261013T090100Z-run-a",
                        "20261013T090200Z-run-b",
                        "20261013T090300Z-run-c",
                        "20261013T090400Z-run-d",
                        "20261013T090500Z-run-e",
                        "20261013T090600Z-run-f",
                        "20261013T090700Z-run-g",
                        "zz-not-a-run"),
                listed.stream().map(RunListing::directoryName).toList());
        assertEquals(
                List.of(
                        RunCondition.COMPLETED,
                        RunCondition.FAILED,
                        RunCondition.CANCELLED,
                        RunCondition.RUNNING,
                        RunCondition.INTERRUPTED,
                        RunCondition.NOT_STARTED,
                        RunCondition.UNREADABLE,
                        RunCondition.UNREADABLE),
                listed.stream().map(RunListing::condition).toList());
        RunListing first = listed.get(0);
        assertAll(
                () -> assertEquals(Optional.of("run-a"), first.runId()),
                () -> assertEquals(Optional.of("2026-10-13T09:01:00.000Z"), first.created()),
                () -> assertEquals(completed.root(), first.directory()),
                () -> assertTrue(first.sealedRecord()),
                () -> assertEquals(Optional.empty(), first.problem()),
                () -> assertFalse(listed.get(1).sealedRecord()));
        RunListing broken = listed.get(6);
        assertAll(
                () -> assertEquals(Optional.empty(), broken.runId()),
                () -> assertEquals(Optional.empty(), broken.created()),
                () ->
                        assertTrue(
                                broken.problem().orElseThrow().contains("run.json"),
                                broken.problem().orElseThrow()));
        assertEquals(prepared.root(), listed.get(5).directory());
        assertEquals(stray, listed.get(7).directory());
        assertTrue(
                listed.get(7).problem().orElseThrow().contains("run.json"),
                listed.get(7).problem().orElseThrow());
    }

    @Test
    @DisplayName("a project with no runs/ directory lists nothing, rather than failing")
    void noRunsDirectory() throws IOException {
        ProjectLayout elsewhere = new ProjectLayout(tmp.resolve("nowhere"));
        assertEquals(List.of(), LoadedRunViewTest.reader().list(elsewhere, Set.of()));
    }

    @Test
    @DisplayName("the condition words are the ones the list shows")
    void words() {
        assertEquals(
                List.of(
                        "not started",
                        "running",
                        "interrupted",
                        "completed",
                        "failed",
                        "cancelled",
                        "run.json unreadable"),
                List.of(RunCondition.values()).stream().map(RunCondition::word).toList());
    }
}
