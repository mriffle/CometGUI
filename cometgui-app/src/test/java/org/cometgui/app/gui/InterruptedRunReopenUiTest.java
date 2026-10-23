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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.RealSearch.LaunchRecorder;
import org.cometgui.app.testing.TestJson;
import org.cometgui.app.testing.TestPercolators;
import org.cometgui.app.uidriver.Await;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.SectionId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The specification's failure path <strong>"a stale or partial run reopened"</strong> through the
 * launched application (Phase 14, gate item 4).
 *
 * <p>Before the application is launched, another CometGUI -- a second JVM using the product's own
 * {@code ProjectSession}, workflow and engine ({@link OtherCometGui} {@code halt}) -- runs a search
 * in the project and halts itself with {@code Runtime.halt} while its Percolator runs: the Comet
 * half recorded succeeded, Percolator's tables written, {@code run.json} recording the attempt
 * running, no {@code provenance.json}, and the project lock left naming a dead process. The
 * application under test is then launched on that project directory, as the next CometGUI would be.
 *
 * <ol>
 *   <li><strong>What the crash left</strong>, proved first so that nothing below is vacuous.
 *   <li><strong>The Provenance section</strong> lists the run as interrupted with no sealed record,
 *       says on opening it that it has no sealed record, and warns of it.
 *   <li><strong>The Results section</strong> does not offer the run as complete: its label does not
 *       say it succeeded, nor that it is executing.
 *   <li><strong>The next run is correct</strong>: the Run section's check takes over the stale lock
 *       (the lock file now names this JVM), its preview says Run starts a new run rather than
 *       retrying the interrupted one, the new run succeeds and is listed completed and sealed
 *       beside the interrupted one, still interrupted, whose directory is unchanged by bytes, mtime
 *       and entries.
 * </ol>
 *
 * <p>What the application does <em>not</em> do is reported by Phase 14 unit 8b rather than pinned
 * here either way: it takes over the stale lock without saying so anywhere on screen (the storage
 * layer's recovery notice, which names the dead owner, is never shown), and the Results section
 * labels the interrupted run with the word {@code running}.
 *
 * <p>No file outside this module is read: the tools are the shell scripts of {@link StandInRun}.
 * Waits are on the child's lines and exit, on the interface's labels, and -- for the lists, which
 * announce nothing -- on {@link Await}.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in tools are POSIX shell scripts")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InterruptedRunReopenUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-23T00:00:00Z"));

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    /** How long a section may take to list its runs or open one. A bound, never a delay. */
    private static final Duration SECTION_BOUND = Duration.ofMinutes(2);

    private static final String INTERRUPTED = ", interrupted, no sealed record";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static OtherCometGuiJvm crashed;

    private static String halting;

    private static int crashExit;

    private static long strandedTool = -1;

    private static long strandedChild = -1;

    private static Path project;

    private static Path interruptedRun;

    private static Map<String, String> interruptedTree;

    /** The lock file as the crash left it, read before the application is launched. */
    private static String lockAtCrash;

    @BeforeAll
    static void launch() throws IOException, InterruptedException {
        Path root = scratch.toRealPath();
        Path comet = StandInRun.comet(root.resolve("bin/comet"));
        Path waiting = StandInRun.percolator(root.resolve("bin/percolator-crashed"), true);
        Path percolator = StandInRun.percolator(root.resolve("bin/percolator"), false);
        List<Path> inputs = StandInRun.inputs(root.resolve("inputs"));
        project = root.resolve("project");
        crashed =
                OtherCometGuiJvm.start(
                        root,
                        "halt",
                        project.toString(),
                        comet.toString(),
                        waiting.toString(),
                        inputs.get(0).toString(),
                        inputs.get(1).toString());
        halting = crashed.awaitLine(line -> line.startsWith("HALTING "));
        crashExit = crashed.awaitExit();
        String[] parts = halting.split(" ", 4);
        strandedTool = Long.parseLong(parts[1]);
        strandedChild = Long.parseLong(parts[2]);
        interruptedRun = Path.of(parts[3]);
        interruptedTree = tree(interruptedRun);
        // Read now: the application's first pre-run check, when its window opens, takes the lock.
        lockAtCrash = Files.readString(project.resolve("project.lock"), StandardCharsets.UTF_8);

        ToolOffer offer =
                TestPercolators.installed(
                        "3.07.1", ToolOrigin.MANAGED, percolator, TestPercolators.ALL, List.of());
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        new LaunchRecorder(new ProcessService(Clock.systemUTC())),
                        new BoundedMessageLog(),
                        new RunWiring.Setup(
                                () ->
                                        InstalledComet.at(StandInRun.COMET_RELEASE, comet)
                                                .with(offer),
                                project));
        driver = new TestFxUiDriver(app.application());
        app.chooser().spectra(inputs.get(0));
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        driver.clickOn("ess-spectra-add");
        app.chooser().database(inputs.get(1));
        driver.clickOn("ess-database_name-choose");
        ParameterEditorApp.choose(
                driver,
                "ess-decoy_search",
                "Concatenated: targets and decoys compete, one result per spectrum");
    }

    @AfterAll
    static void stop() throws InterruptedException {
        try {
            if (app != null) {
                app.stop();
            }
        } finally {
            StandInRun.kill(strandedChild);
            StandInRun.kill(strandedTool);
            if (crashed != null) {
                crashed.stop();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "what the crash left: the JVM gone, the lock naming it, run.json running, no sealed"
                    + " record, Percolator's tables written and its process stranded")
    @SuppressWarnings("unchecked")
    void whatTheCrashLeft() throws IOException {
        Map<String, Object> runJson = json(interruptedRun.resolve("run.json"));
        List<Object> attempts = (List<Object>) runJson.get("attempts");
        assertAll(
                "the crash",
                () -> assertEquals(0, crashExit, "Runtime.halt(0)"),
                () -> assertFalse(StandInRun.running(crashed.pid()), "the JVM is gone"),
                () ->
                        assertTrue(
                                lockAtCrash.contains("\"pid\": " + crashed.pid() + ","),
                                () -> "the lock file named the dead JVM: " + lockAtCrash),
                () -> assertEquals(OtherCometGui.CRASHED_RUN, runJson.get("runId")),
                () -> assertEquals(1, attempts.size()),
                () ->
                        assertEquals(
                                "running",
                                ((Map<String, Object>) attempts.get(0)).get("outcome"),
                                "run.json records the attempt running"),
                () ->
                        assertFalse(
                                Files.exists(interruptedRun.resolve("provenance/provenance.json"))),
                () ->
                        assertTrue(
                                Files.isRegularFile(
                                        interruptedRun.resolve("outputs/percolator/psms.tsv")),
                                "Percolator's tables were written before the crash"),
                () ->
                        assertTrue(
                                StandInRun.running(strandedTool),
                                "the crashed CometGUI's Percolator is still running"));
    }

    @Test
    @Order(2)
    @DisplayName(
            "the Provenance section lists the run as interrupted with no sealed record, and says"
                    + " so when it is opened")
    void theProvenanceSectionSaysInterrupted() {
        driver.clickOn(UiIds.navigationEntry(SectionId.PROVENANCE));
        List<String> listed = provenanceRuns(1);
        String label = listed.get(0);
        assertAll(
                () ->
                        assertTrue(
                                label.startsWith(OtherCometGui.CRASHED_RUN + " -- created "),
                                label),
                () -> assertTrue(label.endsWith(INTERRUPTED), label));
        ParameterEditorApp.choose(driver, UiIds.PROVENANCE_RUN, label);
        String record =
                RunSection.awaitText(
                        driver,
                        UiIds.PROVENANCE_RECORD,
                        text -> text.startsWith("No sealed record"),
                        SECTION_BOUND);
        List<String> warnings = rows(UiIds.PROVENANCE_WARNINGS);
        assertAll(
                "opened",
                () ->
                        assertEquals(
                                "No sealed record: this run has no provenance.json -- it is still"
                                        + " running, or the application or the computer stopped"
                                        + " before the record was written -- so it is shown from"
                                        + " its run.json and events.log alone.",
                                record),
                () ->
                        assertTrue(
                                driver.textOf(UiIds.PROVENANCE_SUMMARY)
                                        .startsWith(
                                                "Run "
                                                        + OtherCometGui.CRASHED_RUN
                                                        + " of project cometgui-default\n"),
                                driver.textOf(UiIds.PROVENANCE_SUMMARY)),
                () ->
                        assertTrue(
                                warnings.stream()
                                        .anyMatch(
                                                row ->
                                                        row.contains(
                                                                "this run has no sealed provenance"
                                                                        + " record: it crashed"
                                                                        + " before finalising")),
                                () -> "the missing record is warned about: " + warnings));
    }

    @Test
    @Order(3)
    @DisplayName("the Results section does not offer the interrupted run as complete")
    void theResultsSectionDoesNotOfferItAsComplete() {
        driver.clickOn(UiIds.navigationEntry(SectionId.RESULTS));
        List<String> offered =
                Await.until(
                        "the Results section to list the interrupted run",
                        () -> ParameterEditorApp.comboItems(driver, UiIds.RESULTS_RUN),
                        items -> items.size() == 1,
                        SECTION_BOUND);
        String label = offered.get(0);
        assertAll(
                "the interrupted run in Results",
                () ->
                        assertTrue(
                                label.startsWith(OtherCometGui.CRASHED_RUN + " -- created "),
                                label),
                () ->
                        assertFalse(
                                label.endsWith(", succeeded"), "not offered as complete: " + label),
                () -> assertFalse(label.contains("executing now"), "nor as executing: " + label));
    }

    @Test
    @Order(4)
    @DisplayName(
            "the next run: the stale lock taken over, a new run rather than a retry, succeeded and"
                    + " sealed, the interrupted run listed and untouched")
    void theNextRunIsANewRunAndSucceeds() throws IOException {
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        assertAll(
                "before Run",
                () ->
                        assertEquals(
                                Long.toString(ProcessHandle.current().pid()),
                                String.valueOf(lockRecord().get("pid")),
                                "the stale lock was taken over: it now names this JVM"),
                () ->
                        assertEquals(
                                "No earlier run in this session: Run starts a new run, and every"
                                        + " step executes.",
                                driver.textOf("run-preview"),
                                "the interrupted run is not silently retried"));
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        List<Path> made = runs();
        assertEquals(2, made.size(), () -> "the runs: " + made);
        Path next =
                made.stream().filter(run -> !run.equals(interruptedRun)).findFirst().orElseThrow();
        String name = String.valueOf(next.getFileName());
        String id = name.substring(name.indexOf('-') + 1);
        driver.clickOn(UiIds.navigationEntry(SectionId.PROVENANCE));
        List<String> listed = provenanceRuns(2);
        assertAll(
                "the next run",
                () -> assertNotEquals(interruptedRun, next),
                () -> assertEquals("The run succeeded: run " + id + " in " + next + ".", outcome),
                () ->
                        assertEquals(
                                "completed",
                                TestJson.at(
                                        json(next.resolve("provenance/provenance.json")),
                                        "run",
                                        "status")),
                () ->
                        assertTrue(
                                listed.stream()
                                        .anyMatch(
                                                label ->
                                                        label.startsWith(id + " -- created ")
                                                                && label.endsWith(
                                                                        ", completed, sealed"
                                                                                + " record")),
                                () -> "the new run, completed and sealed: " + listed),
                () ->
                        assertTrue(
                                listed.stream()
                                        .anyMatch(
                                                label ->
                                                        label.startsWith(
                                                                        OtherCometGui.CRASHED_RUN
                                                                                + " -- ")
                                                                && label.endsWith(INTERRUPTED)),
                                () -> "the interrupted run, still interrupted: " + listed),
                () ->
                        assertEquals(
                                interruptedTree,
                                tree(interruptedRun),
                                "the interrupted run's directory is untouched"));
    }

    private static List<String> provenanceRuns(int count) {
        return Await.until(
                "the Provenance section to list " + count + " run(s)",
                () -> ParameterEditorApp.comboItems(driver, UiIds.PROVENANCE_RUN),
                items -> items.size() == count,
                SECTION_BOUND);
    }

    private static List<String> rows(String id) {
        Node node = driver.node(id);
        if (!(node instanceof ListView<?> list)) {
            return fail("#" + id + " is not a list");
        }
        return driver.callOnFxThread(
                () -> {
                    List<String> texts = new ArrayList<>();
                    for (Object item : list.getItems()) {
                        texts.add(String.valueOf(item));
                    }
                    return texts;
                });
    }

    private static Map<String, Object> lockRecord() throws IOException {
        return json(project.resolve("project.lock"));
    }

    private static Map<String, Object> json(Path file) throws IOException {
        return TestJson.object(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static List<Path> runs() throws IOException {
        try (Stream<Path> listed = Files.list(project.resolve("runs"))) {
            return listed.sorted().toList();
        }
    }

    /**
     * Every path under a run, with its size, modification time and this test's own SHA-256 for a
     * file, its modification time for a directory.
     */
    private static Map<String, String> tree(Path root) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        try (Stream<Path> walked = Files.walk(root)) {
            for (Path path : walked.toList()) {
                String name = root.relativize(path).toString();
                String modified = Long.toString(Files.getLastModifiedTime(path).toMillis());
                entries.put(
                        name,
                        Files.isDirectory(path)
                                ? "dir " + modified
                                : "file " + Files.size(path) + " " + modified + " " + sha256(path));
            }
        }
        return entries;
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JVM has SHA-256", impossible);
        }
    }
}
