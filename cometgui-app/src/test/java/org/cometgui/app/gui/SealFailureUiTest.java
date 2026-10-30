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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
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
 * Defect E12, repaired, through the launched application: a run whose provenance record cannot be
 * written is shown <strong>failed</strong>, never succeeded.
 *
 * <p>A search is run from the Run section with stand-in tools ({@link StandInRun}). While its
 * Percolator runs -- its files written, waiting on a child -- the run's {@code provenance/} is made
 * unwritable (proved by a refused write, so a test run as root fails rather than passing), and the
 * child is then killed, so Percolator ends well and every step does its work. Nothing writes into
 * {@code provenance/} between then and the seal except the already open event log.
 *
 * <ol>
 *   <li><strong>The Run section</strong>: the outcome says the run failed, that its provenance
 *       record could not be written, naming the file and the error, and that the tools' work is
 *       kept; the stepper shows Comet and Percolator succeeded, the Results stage failed and the
 *       run state failed, with the reason; neither says "succeeded" for the run. {@code run.json},
 *       read by this test's own JSON reader, records the attempt failed with that reason.
 *   <li><strong>The Results section</strong> lists the run as failed -- what any failed run is
 *       offered -- never as succeeded.
 *   <li><strong>The Provenance section</strong> lists it as failed with no sealed record and opens
 *       it as such.
 * </ol>
 *
 * <p>No file outside this module is read. Waits are on the stand-in's lines, on the interface's
 * labels and, for lists, on {@link Await}.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in tools are POSIX shell scripts and modes are POSIX")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SealFailureUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-29T00:00:00Z"));

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    /** How long a section may take to list its runs or open one. A bound, never a delay. */
    private static final Duration SECTION_BOUND = Duration.ofMinutes(2);

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static LaunchRecorder launches;

    private static Path project;

    private static Path run;

    private static String id;

    /** What the Run section showed once the run ended, read as it ended. */
    private static String outcome;

    private static String runState;

    private static Map<String, String> stages;

    private static boolean cancelDisabled;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        Path comet = StandInRun.comet(root.resolve("bin/comet"));
        Path percolator = StandInRun.releasablePercolator(root.resolve("bin/percolator"));
        ToolOffer offer =
                TestPercolators.installed(
                        "3.07.1", ToolOrigin.MANAGED, percolator, TestPercolators.ALL, List.of());
        List<Path> inputs = StandInRun.inputs(root.resolve("inputs"));
        project = root.resolve("project");
        launches = new LaunchRecorder(new ProcessService(Clock.systemUTC()));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        launches,
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
        runWithItsRecordUnwritable();
    }

    /**
     * Runs the search from the Run section and, while Percolator waits, makes {@code provenance/}
     * unwritable, then lets Percolator end well; reads what the section shows once the run ends.
     */
    private static void runWithItsRecordUnwritable() throws IOException {
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        CompletableFuture<String> childLine = launches.lineContaining("child ");
        driver.clickOn("run-start");
        long child = pidAfter(within(childLine), "child ");
        try {
            RunSection.awaitText(
                    driver, "stage-percolator-state", "Running"::equals, RunSection.RUN_BOUND);
            run = onlyRun();
            id = idOf(run);
            readOnly(run.resolve("provenance"));
            StandInRun.kill(child);
            outcome =
                    RunSection.awaitText(
                            driver,
                            "run-outcome",
                            text -> text.startsWith("The run "),
                            RunSection.RUN_BOUND);
        } finally {
            StandInRun.kill(child);
            if (run != null) {
                writable(run.resolve("provenance"));
            }
        }
        runState = driver.textOf(UiIds.STAGE_STEPPER_RUN_STATE);
        stages =
                Map.of(
                        "comet", driver.textOf("stage-comet-state"),
                        "percolator", driver.textOf("stage-percolator-state"),
                        "results", driver.textOf("stage-results-state"));
        cancelDisabled = RunSection.isDisabled(driver, "run-cancel");
    }

    @AfterAll
    static void stop() throws IOException {
        try {
            if (run != null) {
                writable(run.resolve("provenance"));
            }
        } finally {
            if (app != null) {
                app.stop();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "provenance/ unwritable before the seal: the outcome and the stepper say the run"
                    + " failed, with the reason, and run.json records it; nothing says succeeded")
    @SuppressWarnings("unchecked")
    void theRunSectionSaysFailed() throws IOException {
        Path record = run.resolve("provenance/provenance.json");
        String reason =
                "its provenance record "
                        + record
                        + " could not be written: permission was denied ("
                        + record
                        + ".tmp-";
        Map<String, Object> runJson =
                TestJson.object(Files.readString(run.resolve("run.json"), StandardCharsets.UTF_8));
        Map<String, Object> attempt =
                (Map<String, Object>) ((List<Object>) runJson.get("attempts")).get(0);
        String said = outcome;
        assertAll(
                "the run failed for its record, in words, on the stepper and in run.json",
                () -> assertTrue(said.startsWith("The run failed: " + reason), said),
                () ->
                        assertTrue(
                                said.endsWith(
                                        "). Run "
                                                + id
                                                + " in "
                                                + run
                                                + ". What its tools wrote is kept in the run's"
                                                + " directory."),
                                said),
                () -> assertFalse(said.toLowerCase(Locale.ROOT).contains("succeeded"), said),
                () -> assertEquals("Succeeded", stages.get("comet")),
                () -> assertEquals("Succeeded", stages.get("percolator")),
                () -> assertEquals("Failed", stages.get("results")),
                () ->
                        assertTrue(
                                runState.startsWith(
                                        "Run state: Failed. Its provenance record "
                                                + record
                                                + " could not be written: permission was denied"),
                                runState),
                () -> assertTrue(cancelDisabled, "Cancel is offered again only for a new run"),
                () -> assertEquals("failed", attempt.get("outcome")),
                () ->
                        assertTrue(
                                String.valueOf(attempt.get("reason")).startsWith(reason),
                                () -> "run.json's reason: " + attempt.get("reason")),
                () -> assertEquals(3L, ((Number) runJson.get("schemaVersion")).longValue()),
                () -> assertFalse(Files.exists(record), "no record"),
                () ->
                        assertTrue(
                                Files.isRegularFile(run.resolve("outputs/percolator/psms.tsv")),
                                "Percolator's tables are kept"));
    }

    @Test
    @Order(2)
    @DisplayName("the Results section lists the run as failed, never as succeeded")
    void theResultsSectionSaysFailed() {
        driver.clickOn(UiIds.navigationEntry(SectionId.RESULTS));
        List<String> offered =
                Await.until(
                        "the Results section to list the run",
                        () -> ParameterEditorApp.comboItems(driver, UiIds.RESULTS_RUN),
                        items -> items.size() == 1,
                        SECTION_BOUND);
        String label = offered.get(0);
        assertAll(
                "the run in Results",
                () -> assertTrue(label.startsWith(id + " -- created "), label),
                () -> assertTrue(label.endsWith(", failed"), label),
                () -> assertFalse(label.contains("succeeded"), label));
    }

    @Test
    @Order(3)
    @DisplayName(
            "the Provenance section lists the run as failed with no sealed record, and opens it so")
    void theProvenanceSectionSaysFailed() {
        driver.clickOn(UiIds.navigationEntry(SectionId.PROVENANCE));
        List<String> listed =
                Await.until(
                        "the Provenance section to list the run",
                        () -> ParameterEditorApp.comboItems(driver, UiIds.PROVENANCE_RUN),
                        items -> items.size() == 1,
                        SECTION_BOUND);
        String label = listed.get(0);
        assertTrue(label.startsWith(id + " -- created "), label);
        assertTrue(label.endsWith(", failed, no sealed record"), label);
        ParameterEditorApp.choose(driver, UiIds.PROVENANCE_RUN, label);
        String opened =
                RunSection.awaitText(
                        driver,
                        UiIds.PROVENANCE_RECORD,
                        text -> text.startsWith("No sealed record"),
                        SECTION_BOUND);
        String summary = driver.textOf(UiIds.PROVENANCE_SUMMARY);
        assertAll(
                "opened",
                () -> assertTrue(opened.startsWith("No sealed record: "), opened),
                () -> assertTrue(summary.contains("Status: failed"), summary),
                () -> assertFalse(summary.contains("completed"), summary));
    }

    private static void readOnly(Path directory) throws IOException {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"));
        assertThrows(
                AccessDeniedException.class,
                () -> Files.createFile(directory.resolve("write-probe")),
                "the directory accepted a write after its write permission was removed --"
                        + " running as root, or a file system ignoring modes");
    }

    private static void writable(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        }
    }

    private static long pidAfter(String line, String prefix) {
        int at = line.indexOf(prefix);
        return Long.parseLong(line.substring(at + prefix.length()).trim());
    }

    private static String within(CompletableFuture<String> line) {
        try {
            return line.get(RunSection.RUN_BOUND.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException timedOut) {
            return fail(
                    "the stand-in Percolator did not report itself within "
                            + RunSection.RUN_BOUND
                            + "; the outcome reads: "
                            + driver.textOf("run-outcome"));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fail("interrupted", interrupted);
        } catch (ExecutionException impossible) {
            return fail("the wait failed", impossible);
        }
    }

    private static Path onlyRun() throws IOException {
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            List<Path> all = runs.toList();
            assertEquals(1, all.size(), () -> "the runs: " + all);
            return all.get(0);
        }
    }

    private static String idOf(Path runDirectory) {
        String name = String.valueOf(runDirectory.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }
}
