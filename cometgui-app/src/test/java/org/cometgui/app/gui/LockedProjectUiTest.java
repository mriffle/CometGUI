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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The specification's failure path <strong>"a second CometGUI instance opening a locked
 * project"</strong> at the level of the interface ({@code R-RUN-05}; Phase 14, gate item 4).
 *
 * <p>Another CometGUI -- a second JVM opening the project through the product's own {@code
 * ProjectSession} ({@link OtherCometGui} {@code hold}) -- holds the project's lock when the
 * application under test is launched on the same project directory.
 *
 * <ol>
 *   <li><strong>Refused, on screen, naming the other instance</strong>: the Run section's engine
 *       answer says the project cannot be used because it is open in another CometGUI, naming that
 *       JVM's pid, host and start time as its lock file records them; Run is disabled, no process
 *       is launched and no run is created.
 *   <li><strong>Recovered once the holder exits</strong>: the other CometGUI closes its session and
 *       exits; the next pre-run check -- started, as every one is, by a change to the parameters --
 *       takes the lock, the lock file now names this JVM, and a run started from the interface
 *       succeeds.
 * </ol>
 *
 * <p>No file outside this module is read: the tools are the shell scripts of {@link StandInRun}.
 * Waits are on the child's lines, on its exit and on the interface's labels, never on a delay.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in tools are POSIX shell scripts")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LockedProjectUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-23T00:00:00Z"));

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    /** How {@code LockOwner.describe()} writes a start time, written out here. */
    private static final DateTimeFormatter SECONDS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static LaunchRecorder launches;

    private static Path project;

    private static FileChannel gate;

    private static FileLock gateLock;

    private static OtherCometGuiJvm holder;

    private static String held;

    @BeforeAll
    static void launch() throws IOException, InterruptedException {
        Path root = scratch.toRealPath();
        Path comet = StandInRun.comet(root.resolve("bin/comet"));
        Path percolator = StandInRun.percolator(root.resolve("bin/percolator"), false);
        ToolOffer offer =
                TestPercolators.installed(
                        "3.07.1", ToolOrigin.MANAGED, percolator, TestPercolators.ALL, List.of());
        List<Path> inputs = StandInRun.inputs(root.resolve("inputs"));
        project = root.resolve("project");
        Path gateFile = Files.createFile(root.resolve("gate"));
        gate = FileChannel.open(gateFile, StandardOpenOption.WRITE);
        gateLock = gate.lock();
        holder = OtherCometGuiJvm.start(root, "hold", project.toString(), gateFile.toString());
        held = holder.awaitLine(line -> line.startsWith("HELD "));

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
    static void stop() throws IOException, InterruptedException {
        try {
            if (app != null) {
                app.stop();
            }
        } finally {
            if (gate != null && gate.isOpen()) {
                gate.close();
            }
            if (holder != null) {
                holder.stop();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "the project locked by another CometGUI: refused on screen naming its pid, host and"
                    + " start; Run disabled, nothing launched, no run created")
    void refusedWhileTheOtherHoldsIt() throws IOException {
        Map<String, Object> record = lockRecord();
        String pid = String.valueOf(record.get("pid"));
        String host = String.valueOf(record.get("host"));
        String since = SECONDS.format(Instant.parse(String.valueOf(record.get("started"))));
        driver.clickOn("nav-run");
        String answer = RunSection.awaitEngineAnswer(driver);
        assertAll(
                "refused while held",
                () -> assertEquals("HELD " + holder.pid() + " " + host, held),
                () -> assertEquals(Long.toString(holder.pid()), pid, "the lock names the child"),
                () ->
                        assertEquals(
                                "The workflow engine cannot start this search:\nThe project at "
                                        + project
                                        + " cannot be used, so no run can start: the project "
                                        + project
                                        + " is open in another CometGUI: process "
                                        + pid
                                        + " on host "
                                        + host
                                        + ", since "
                                        + since
                                        + ". Close it there first.",
                                answer),
                () -> assertTrue(RunSection.isDisabled(driver, "run-start"), "Run is disabled"),
                () -> assertEquals(List.of(), launches.launches(), "nothing was launched"),
                () -> assertEquals(List.of(), runs(), "and no run was created"));
    }

    @Test
    @Order(2)
    @DisplayName(
            "the other CometGUI exits: the next check takes the lock, the lock names this JVM,"
                    + " and a run from the interface succeeds")
    void recoveredOnceTheOtherExits() throws IOException, InterruptedException {
        gateLock.release();
        gate.close();
        assertEquals(0, holder.awaitExit(), () -> String.valueOf(holder.output()));
        List<String> said = holder.output();
        assertEquals("RELEASED true", said.get(said.size() - 1));
        assertEquals(0, Files.size(project.resolve("project.lock")), "the lock was released");

        ParameterEditorApp.openEditor(driver);
        enter(driver, "ess-num_threads", "2");
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        assertFalse(RunSection.isDisabled(driver, "run-start"), "Run is offered again");
        assertEquals(
                Long.toString(ProcessHandle.current().pid()),
                String.valueOf(lockRecord().get("pid")),
                "the lock now names this JVM");
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        List<Path> made = runs();
        assertEquals(1, made.size(), () -> "the runs: " + made);
        Path run = made.get(0);
        String name = String.valueOf(run.getFileName());
        assertEquals(
                "The run succeeded: run "
                        + name.substring(name.indexOf('-') + 1)
                        + " in "
                        + run
                        + ".",
                outcome);
    }

    private static Map<String, Object> lockRecord() throws IOException {
        return TestJson.object(
                Files.readString(project.resolve("project.lock"), StandardCharsets.UTF_8));
    }

    private static List<Path> runs() throws IOException {
        Path directory = project.resolve("runs");
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> listed = Files.list(directory)) {
            return listed.sorted().toList();
        }
    }
}
