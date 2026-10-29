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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.storage.RunStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The specification's failure path <strong>"insufficient disk space where it can be simulated
 * safely"</strong>, through the real workflow and engine (Phase 14, gate item 4).
 *
 * <h2>What is simulated, and what is not</h2>
 *
 * <p><strong>Simulated</strong>: the kernel refusing a write for want of room, in the middle of a
 * run. A second JVM ({@link RunChild} {@code full}) prepares a run of both spectrum files, then
 * lowers its own {@code RLIMIT_FSIZE} to {@value #LIMIT} bytes with {@code /usr/bin/prlimit} and
 * runs it. From then on every write that would make a file larger than that is refused by the
 * kernel -- in the product's own JVM, whose file channels throw {@code IOException("File too
 * large")} ({@code EFBIG}). The limit is chosen from measured sizes so that the first write refused
 * is the product's own: each stand-in PIN is 10 318 bytes, under it; the merged PIN of the two is
 * about 20 600, over it; {@code run.json}, the event log and {@code provenance.json} stay under it
 * in a run that stops at the merge (about 4 200, 11 700 and 11 400 bytes). Nothing outside a
 * temporary directory is touched, and no real file system fills.
 *
 * <p><strong>Not simulated</strong>: {@code ENOSPC} itself ("No space left on device") -- the error
 * differs, the code path in the product (an {@code IOException} from a write) is the same; a device
 * that fills for every process at once; a tool, rather than CometGUI, hitting the limit (a POSIX
 * tool is killed by {@code SIGXFSZ} instead, which is "the tool exits non-zero"); and any
 * free-space check before a run, which the product does not have.
 *
 * <h2>What is proved</h2>
 *
 * <p>The run fails at {@code merge-pin} and says why ({@code File too large}); nothing after it
 * starts; no merged PIN is left, whole or partial, and no temporary file anywhere in the run;
 * {@code run.json} and {@code provenance.json} parse whole and agree that the run failed; the event
 * log ends with the run failed; nothing could not be finalised. The limit is proved in force by the
 * child's own {@code /proc/self/limits}, so the failure cannot be a coincidence.
 *
 * <p>And the disk filling <strong>at the seal</strong>, after every step's work (defect E12,
 * repaired): {@link #theRecordRefusedForWantOfRoom}.
 *
 * <p>No file outside this module is read.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason = "RLIMIT_FSIZE is set with util-linux prlimit and read from /proc")
class FileSizeLimitRunTest {

    /** The file-size limit the run executes under, in bytes. */
    static final long LIMIT = 15_000;

    /**
     * The file-size limit lowered at the seal, in bytes: above {@code run.json} with its reason
     * (about 5 100 bytes in this run), below {@code provenance.json} (about 16 200). The event log
     * is already larger (about 20 100 bytes at the seal), so its last events are refused too --
     * which a disk with no room left would do as well.
     */
    static final long SEAL_LIMIT = 12_000;

    /**
     * E12, repaired, for the disk filling at the seal. The child JVM runs a one-file search with
     * the stand-in tools under no limit and lowers its {@code RLIMIT_FSIZE} to {@value #SEAL_LIMIT}
     * bytes only after the last step has recorded its success ({@link SealTrigger}): every step's
     * work is done, and the first write refused is {@code provenance.json}'s. The run must end
     * FAILED, saying that its provenance record could not be written, which file, and {@code File
     * too large}; {@code run.json} -- small enough to be written -- records the attempt failed with
     * that reason; the event log, too large to take another line, cannot record the end, and the
     * result says so; no record, report or temporary file is left; and every step succeeded.
     */
    @Test
    @DisplayName(
            "the disk full at the seal: FAILED saying provenance.json could not be written (File"
                    + " too large), run.json failed with that reason, the unrecorded events"
                    + " stated, every step's work kept")
    void theRecordRefusedForWantOfRoom(@TempDir Path directory)
            throws IOException, InterruptedException {
        Path root = directory.toRealPath();
        Path search = Files.createDirectories(root.resolve("search"));
        RunChildJvm child =
                RunChildJvm.start(root, "seal", search.toString(), Long.toString(SEAL_LIMIT));
        try {
            child.awaitLine(line -> line.equals("DONE"));
            assertEquals(0, child.awaitExit(), () -> String.valueOf(child.output()));
        } finally {
            child.stop();
        }
        List<String> said = child.output();
        RunLayout run = new RunLayout(Path.of(valueOf(said, "RUN ")));
        ProjectLayout project = new ProjectLayout(search.resolve("project"));
        List<RunAttempt> attempts =
                new RunStore(
                                project,
                                java.time.Clock.systemUTC(),
                                () -> {
                                    throw new AssertionError("no run is created here");
                                })
                        .read(run)
                        .attempts();
        String reason =
                "its provenance record "
                        + run.provenanceJsonFile()
                        + " could not be written: File"
                        + " too large";
        List<ProvenanceEvent> events = RunEvidence.events(run);
        ProvenanceEvent last = events.get(events.size() - 1);
        assertAll(
                "the record refused for want of room",
                () ->
                        assertEquals(
                                "Max file size " + SEAL_LIMIT + " " + SEAL_LIMIT + " bytes",
                                valueOf(said, "LIMITED "),
                                "the limit was in force at the seal"),
                () -> assertEquals("true", valueOf(said, "FIRED ")),
                () -> assertEquals(List.of(), linesStarting(said, "TRIGGER ")),
                () ->
                        assertTrue(
                                Long.parseLong(valueOf(said, "AT-SEAL run.json "))
                                        < SEAL_LIMIT - 500,
                                "run.json has room for its reason"),
                () ->
                        assertTrue(
                                Long.parseLong(valueOf(said, "AT-SEAL events.log ")) > SEAL_LIMIT,
                                "the event log has none"),
                () -> assertEquals("FAILED", valueOf(said, "OUTCOME ")),
                () -> assertEquals(reason, valueOf(said, "REASON ")),
                () -> assertEquals(List.of(), linesStarting(said, "FAILURE "), "no step failed"),
                () ->
                        assertEquals(
                                StandInTools.statesUpTo(
                                                EngineStep.FINALISE_PROVENANCE, StepState.SUCCEEDED)
                                        .entrySet()
                                        .stream()
                                        .map(
                                                state ->
                                                        "STATE "
                                                                + state.getKey().id()
                                                                + " "
                                                                + state.getValue())
                                        .sorted()
                                        .toList(),
                                linesStarting(said, "STATE ").stream().sorted().toList(),
                                "every step succeeded"),
                () ->
                        assertEquals(
                                List.of(
                                        "FINALISATION could not append stage.finished:"
                                                + " java.io.IOException: File too large",
                                        "FINALISATION could not append run.finished:"
                                                + " java.io.IOException: File too large"),
                                linesStarting(said, "FINALISATION "),
                                "the events the log could not take are stated"),
                () -> assertEquals(1, attempts.size()),
                () -> assertEquals(AttemptOutcome.FAILED, attempts.get(0).outcome()),
                () -> assertEquals(java.util.Optional.of(reason), attempts.get(0).reason()),
                () -> assertFalse(Files.exists(run.provenanceJsonFile()), "no record"),
                () -> assertFalse(Files.exists(run.provenanceRstFile()), "no report"),
                () ->
                        assertEquals(
                                List.of(),
                                temporaryFiles(run.root()),
                                "no temporary file anywhere in the run"),
                () ->
                        assertEquals(
                                List.of("stage.started", "finalise-provenance", "running"),
                                List.of(
                                        last.type().wireName(),
                                        last.payload().get(ProvenanceEvent.STAGE_KEY),
                                        last.payload().get("state")),
                                "the log ends where the room ran out: the last step running"),
                () ->
                        assertTrue(
                                Files.isRegularFile(run.mergedPinFile())
                                        && Files.size(run.mergedPinFile()) > 0,
                                "the merged PIN is kept"));
    }

    @Test
    @DisplayName(
            "a write refused for want of room mid-run: failed at merge-pin saying why, no merged"
                    + " PIN or temporary file left, run.json and provenance.json whole and"
                    + " agreeing")
    void aWriteRefusedForWantOfRoom(@TempDir Path directory)
            throws IOException, InterruptedException {
        Path root = directory.toRealPath();
        Path search = Files.createDirectories(root.resolve("search"));
        RunChildJvm child =
                RunChildJvm.start(root, "full", search.toString(), Long.toString(LIMIT));
        try {
            child.awaitLine(line -> line.equals("DONE"));
            assertEquals(0, child.awaitExit(), () -> String.valueOf(child.output()));
        } finally {
            child.stop();
        }
        List<String> said = child.output();
        RunLayout run = new RunLayout(Path.of(valueOf(said, "RUN ")));
        ProjectLayout project = new ProjectLayout(search.resolve("project"));
        List<RunAttempt> attempts =
                new RunStore(
                                project,
                                java.time.Clock.systemUTC(),
                                () -> {
                                    throw new AssertionError("no run is created here");
                                })
                        .read(run)
                        .attempts();
        ProvenanceManifest manifest = RunEvidence.manifest(run);
        List<ProvenanceEvent> events = RunEvidence.events(run);
        ProvenanceEvent last = events.get(events.size() - 1);
        assertAll(
                "a write refused for want of room",
                () ->
                        assertEquals(
                                "Max file size " + LIMIT + " " + LIMIT + " bytes",
                                valueOf(said, "LIMITED "),
                                "the limit was in force for the run"),
                () -> assertEquals("FAILED", valueOf(said, "OUTCOME ")),
                () ->
                        assertEquals(
                                List.of("FAILURE merge-pin File too large"),
                                linesStarting(said, "FAILURE "),
                                "merge-pin fails, saying why, and nothing else does"),
                () ->
                        assertEquals(
                                List.of(),
                                linesStarting(said, "FINALISATION "),
                                "and everything was finalised"),
                () ->
                        assertEquals(
                                List.of(),
                                RunEvidence.listing(run.pinInputsDirectory()),
                                "no merged PIN, whole or partial"),
                () ->
                        assertEquals(
                                List.of(),
                                temporaryFiles(run.root()),
                                "no temporary file anywhere in the run"),
                () -> assertEquals(1, attempts.size()),
                () -> assertEquals(AttemptOutcome.FAILED, attempts.get(0).outcome()),
                () ->
                        assertEquals(
                                List.of(
                                        "hash-inputs",
                                        "resolve-comet",
                                        "resolve-percolator",
                                        "run-comet",
                                        "serialise-comet-params",
                                        "validate-comet-outputs",
                                        "validate-configuration"),
                                attempts.get(0).succeededSteps().keySet().stream()
                                        .sorted()
                                        .toList(),
                                "run.json: everything before the merge succeeded"),
                () -> assertEquals(ProvenanceStatus.FAILED, manifest.run().status()),
                () -> assertEquals(ProvenanceEventType.RUN_FINISHED, last.type()),
                () -> assertEquals("failed", last.payload().get(ProvenanceEvent.STATUS_KEY)),
                () ->
                        assertEquals(
                                "File too large",
                                RunEvidence.finished(run, EngineStep.MERGE_PIN)
                                        .get(ProvenanceEvent.MESSAGE_KEY),
                                "the event log says why merge-pin failed"),
                () ->
                        assertEquals(
                                expectedStates(),
                                linesStarting(said, "STATE ").stream().sorted().toList(),
                                "the Comet half succeeded, merge-pin failed, nothing after"));
    }

    private static List<String> expectedStates() {
        return StandInTools.statesUpTo(EngineStep.MERGE_PIN, StepState.FAILED).entrySet().stream()
                .map(state -> "STATE " + state.getKey().id() + " " + state.getValue())
                .sorted()
                .toList();
    }

    private static String valueOf(List<String> lines, String prefix) {
        List<String> found = linesStarting(lines, prefix);
        assertEquals(1, found.size(), () -> "one " + prefix + "line in " + lines);
        return found.get(0).substring(prefix.length());
    }

    private static List<String> linesStarting(List<String> lines, String prefix) {
        return lines.stream().filter(line -> line.startsWith(prefix)).toList();
    }

    private static List<String> temporaryFiles(Path root) throws IOException {
        try (Stream<Path> walked = Files.walk(root)) {
            return walked.map(path -> String.valueOf(path.getFileName()))
                    .filter(name -> name.contains(".tmp-") || name.endsWith(".part"))
                    .sorted()
                    .toList();
        }
    }
}
