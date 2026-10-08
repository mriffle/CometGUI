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

import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.RealPercolators;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.RealSearch.LaunchRecorder;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cancel through the interface ({@code AC-WF-05} in the interface): the real Comet 2026.03.0
 * searching one K562 file against the WHOLE proteome with one thread -- about half a minute, the
 * search unit 6's gate-7 test cancels -- is caught searching (its own {@code Load spectra:} line,
 * never a delay), and Cancel is pressed. Comet and every descendant are dead by pid, the stepper
 * shows Comet cancelled, the outcome says so, and the rerun preview offers a retry from the
 * cancelled step.
 *
 * <p>Files read outside this module: see {@link RealSearch} and {@link RealPercolators} (a run
 * includes Percolator since Phase 09, so the Tool Manager offers the staged, probed 3.07.1; this
 * run is cancelled before Percolator is reached).
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealCancelUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-07T00:00:00Z"));

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static LaunchRecorder launches;

    private static Path comet;

    private static Path project;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        comet = RealSearch.stageComet(root.resolve("bin/comet"));
        ToolOffer percolator = RealPercolators.installed3071(root.resolve("bin/percolator"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        Path spectrum = RealSearch.spectra(inputs).get(0);
        Path proteome = RealSearch.proteome(inputs.resolve("proteome.fasta"));
        project = root.resolve("project");
        launches = new LaunchRecorder(new ProcessService(Clock.systemUTC()));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        launches,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(
                                () -> InstalledComet.at(RealSearch.RELEASE, comet).with(percolator),
                                project));
        driver = new TestFxUiDriver(app.application());

        app.chooser().spectra(spectrum);
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        driver.clickOn("ess-spectra-add");
        app.chooser().database(proteome);
        driver.clickOn("ess-database_name-choose");
        ParameterEditorApp.choose(
                driver,
                "ess-decoy_search",
                "Concatenated: targets and decoys compete, one result per spectrum");
        enter(driver, "ess-num_threads", "1");
        // A new configuration searches without a spectral library (D-012): nothing to clear.
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-ms1_realtime-toggle");
        assertEquals("", driver.textOf("adv-spectral_library_name"));
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName(
            "Cancel during a real search: Comet dead by pid, the stepper and the outcome say"
                    + " cancelled, and the preview offers the retry")
    void cancelARealSearch() throws IOException {
        driver.clickOn("nav-run");
        assertEquals(
                "The workflow engine can run this search.", RunSection.awaitEngineAnswer(driver));
        assertTrue(RunSection.isDisabled(driver, "run-cancel"), "nothing to cancel before Run");

        CompletableFuture<String> searching = launches.lineContaining("Load spectra:");
        driver.clickOn("run-start");
        String loaded = within(searching);
        assertTrue(loaded.contains("Load spectra: 728"), loaded);
        RunSection.awaitText(
                driver, "run-outcome", text -> text.startsWith("Running: "), RunSection.RUN_BOUND);
        assertEquals("Running", driver.textOf("stage-comet-state"));
        assertFalse(RunSection.isDisabled(driver, "run-cancel"), "Cancel is offered while it runs");

        List<LaunchRecorder.Launch> comets = cometLaunches();
        assertEquals(1, comets.size());
        ProcessHandle searcher = ProcessHandle.of(comets.get(0).process().pid()).orElseThrow();
        assertTrue(searcher.isAlive(), "Comet is searching when Cancel is pressed");
        List<ProcessHandle> family = new ArrayList<>();
        family.add(searcher);
        family.addAll(searcher.descendants().toList());

        driver.clickOn("run-cancel");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);

        for (ProcessHandle member : family) {
            assertFalse(
                    ProcessHandle.of(member.pid()).map(ProcessHandle::isAlive).orElse(false),
                    () -> "pid " + member.pid() + " outlived the cancellation");
        }
        Path run = onlyRun();
        String id = idOf(run);
        assertAll(
                "cancelled, in words and on the stepper",
                () ->
                        assertEquals(
                                "The run was cancelled: run "
                                        + id
                                        + " in "
                                        + run
                                        + ". Its logs and provenance are kept, and the outputs it"
                                        + " left are recorded as partial.",
                                outcome),
                () -> assertEquals("Cancelled", driver.textOf("stage-comet-state")),
                () -> assertEquals("Succeeded", driver.textOf("stage-validate-state")),
                () -> assertTrue(RunSection.isDisabled(driver, "run-cancel")),
                () ->
                        assertTrue(
                                Files.isRegularFile(run.resolve("logs/comet-01.log")),
                                "the stage log is kept"));

        assertEquals(
                "The workflow engine can run this search.", RunSection.awaitEngineAnswer(driver));
        assertEquals(
                "Rerun preview against run "
                        + id
                        + ":\nnothing the steps read has changed, so Run retries run "
                        + id
                        + " and runs exactly the steps marked below.\n"
                        + "- validate-configuration: runs again, as a prerequisite (needed by"
                        + " resolve-comet; needed by resolve-percolator; needed by hash-inputs)\n"
                        + "- resolve-comet: runs again, as a prerequisite (needed by run-comet)\n"
                        + "- resolve-percolator: runs again, as a prerequisite (needed by"
                        + " run-percolator)\n"
                        + "- serialise-comet-params: reused from run "
                        + id
                        + "\n- hash-inputs: runs again, as a prerequisite (needed by run-comet)\n"
                        + "- run-comet: re-executes (no successful earlier execution is"
                        + " recorded)\n"
                        + "- validate-comet-outputs: re-executes (no successful earlier execution"
                        + " is recorded; run-comet re-executes)\n"
                        + "- merge-pin: re-executes (no successful earlier execution is recorded;"
                        + " validate-comet-outputs re-executes)\n"
                        + "- run-percolator: re-executes (no successful earlier execution is"
                        + " recorded; merge-pin re-executes)\n"
                        + "- parse-percolator: re-executes (no successful earlier execution is"
                        + " recorded; run-percolator re-executes)\n"
                        + "- finalise-provenance: re-executes (no successful earlier execution is"
                        + " recorded; merge-pin re-executes)",
                driver.textOf("run-preview"));
        assertFalse(RunSection.isDisabled(driver, "run-start"), "the retry is offered");
        assertEquals(1, cometLaunches().size(), "the preview launched nothing");
    }

    private static String within(CompletableFuture<String> line) {
        try {
            return line.get(RunSection.RUN_BOUND.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException timedOut) {
            return fail("Comet did not start searching within " + RunSection.RUN_BOUND);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fail("interrupted", interrupted);
        } catch (ExecutionException impossible) {
            return fail("the wait failed", impossible);
        }
    }

    private static Path onlyRun() {
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            List<Path> all = runs.toList();
            assertEquals(1, all.size(), () -> "the runs: " + all);
            return all.get(0);
        } catch (IOException unreadable) {
            throw new AssertionError("the project's runs cannot be listed", unreadable);
        }
    }

    private static String idOf(Path run) {
        String name = String.valueOf(run.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }

    private static List<LaunchRecorder.Launch> cometLaunches() {
        return launches.launches().stream()
                .filter(launch -> launch.argv().get(0).equals(comet.toString()))
                .toList();
    }
}
