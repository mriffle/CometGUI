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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The specification's failure path <strong>"the user cancels Percolator"</strong> through the Run
 * section of the launched application (Phase 14, gate item 4): a search is run from the interface,
 * and while its Percolator is running -- a stand-in that has written its files and keeps running
 * with a child of its own ({@link StandInRun}) -- Cancel is pressed.
 *
 * <p>Proved: Percolator and its child are dead by pid; the outcome says the run was cancelled, in
 * the words the interface uses; the stepper shows Comet succeeded, Percolator cancelled and Results
 * never started, and Cancel is offered again only for a new run; the run's {@code provenance.json}
 * -- read by this test's own JSON reader -- is sealed as cancelled, with the Percolator invocation
 * cancelled and each file Percolator wrote marked partial while Comet's are complete.
 *
 * <p>No file outside this module is read: the tools are shell scripts written here, the spectrum
 * file is named and hashed, never parsed. Waits are on the lines the stand-in prints and on the
 * interface's own labels, never on a delay.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in tools are POSIX shell scripts")
class PercolatorCancelUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-23T00:00:00Z"));

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static LaunchRecorder launches;

    private static Path percolator;

    private static Path project;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        Path comet = StandInRun.comet(root.resolve("bin/comet"));
        percolator = StandInRun.percolator(root.resolve("bin/percolator"), true);
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
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName(
            "Cancel while Percolator runs: Percolator and its child dead by pid, the outcome and"
                    + " the stepper say cancelled, the record sealed as cancelled with"
                    + " Percolator's files partial")
    void cancelWhilePercolatorRuns() throws IOException {
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        CompletableFuture<String> childLine = launches.lineContaining("child ");
        CompletableFuture<String> toolLine = launches.lineContaining("pid ");
        driver.clickOn("run-start");
        long child = pidAfter(within(childLine), "child ");
        long tool = pidAfter(within(toolLine), "pid ");
        try {
            RunSection.awaitText(
                    driver, "stage-percolator-state", "Running"::equals, RunSection.RUN_BOUND);
            List<LaunchRecorder.Launch> percolators = launchesOf(percolator);
            assertAll(
                    "Percolator is running when Cancel is pressed",
                    () -> assertEquals("Succeeded", driver.textOf("stage-comet-state")),
                    () -> assertFalse(RunSection.isDisabled(driver, "run-cancel")),
                    () -> assertEquals(1, percolators.size()),
                    () -> assertEquals(percolators.get(0).process().pid(), tool),
                    () -> assertTrue(StandInRun.running(tool), "Percolator runs"),
                    () -> assertTrue(StandInRun.running(child), "and so does its child"));

            driver.clickOn("run-cancel");
            String outcome =
                    RunSection.awaitText(
                            driver,
                            "run-outcome",
                            text -> text.startsWith("The run "),
                            RunSection.RUN_BOUND);
            Path run = onlyRun();
            String id = idOf(run);
            Map<String, Object> record =
                    TestJson.object(
                            Files.readString(
                                    run.resolve("provenance/provenance.json"),
                                    StandardCharsets.UTF_8));
            assertAll(
                    "cancelled, by pid, in words, on the stepper and in the record",
                    () ->
                            assertFalse(
                                    StandInRun.running(tool),
                                    "pid " + tool + " (Percolator) outlived the cancel"),
                    () ->
                            assertFalse(
                                    StandInRun.running(child),
                                    "pid " + child + " (its child) outlived the cancel"),
                    () ->
                            assertEquals(
                                    "The run was cancelled: run "
                                            + id
                                            + " in "
                                            + run
                                            + ". Its logs and provenance are kept, and the"
                                            + " outputs it left are recorded as partial.",
                                    outcome),
                    () -> assertEquals("Succeeded", driver.textOf("stage-comet-state")),
                    () -> assertEquals("Cancelled", driver.textOf("stage-percolator-state")),
                    () -> assertEquals("Not started", driver.textOf("stage-results-state")),
                    () -> assertTrue(RunSection.isDisabled(driver, "run-cancel")),
                    () -> assertEquals("cancelled", TestJson.at(record, "run", "status")),
                    () ->
                            assertEquals(
                                    List.of("comet 0 completed", "percolator 143 cancelled"),
                                    executions(record)),
                    () ->
                            assertEquals(
                                    expectedOutputs(),
                                    outputs(record),
                                    "Percolator's files partial, Comet's complete"));
        } finally {
            StandInRun.kill(child);
            StandInRun.kill(tool);
        }
    }

    /** The outputs a cancelled Percolator leaves recorded, by file name, typed out. */
    private static Map<String, String> expectedOutputs() {
        Map<String, String> outputs = new TreeMap<>();
        outputs.put("comet.params", "comet-params completed");
        outputs.put("a.pep.xml", "pepxml completed");
        outputs.put("a.pin", "pin completed");
        outputs.put("merged.pin", "merged-pin completed");
        outputs.put("psms.tsv", "percolator-psms partial");
        outputs.put("peptides.tsv", "percolator-peptides partial");
        outputs.put("decoy-psms.tsv", "percolator-decoy-psms partial");
        outputs.put("decoy-peptides.tsv", "percolator-decoy-peptides partial");
        outputs.put("weights.txt", "percolator-weights partial");
        return outputs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> outputs(Map<String, Object> record) {
        Map<String, String> outputs = new TreeMap<>();
        for (Object each : (List<Object>) record.get("files")) {
            Map<String, Object> file = (Map<String, Object>) each;
            if ("output".equals(file.get("direction"))) {
                outputs.put(
                        String.valueOf(Path.of(String.valueOf(file.get("path"))).getFileName()),
                        file.get("role") + " " + file.get("status"));
            }
        }
        return outputs;
    }

    @SuppressWarnings("unchecked")
    private static List<String> executions(Map<String, Object> record) {
        return ((List<Object>) record.get("tools"))
                .stream()
                        .map(each -> (Map<String, Object>) each)
                        .map(
                                tool ->
                                        tool.get("name")
                                                + " "
                                                + TestJson.at(tool, "execution", "exitCode")
                                                + " "
                                                + TestJson.at(tool, "execution", "status"))
                        .sorted()
                        .toList();
    }

    private static List<LaunchRecorder.Launch> launchesOf(Path executable) {
        return launches.launches().stream()
                .filter(launch -> launch.argv().get(0).equals(executable.toString()))
                .toList();
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

    private static String idOf(Path run) {
        String name = String.valueOf(run.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }
}
