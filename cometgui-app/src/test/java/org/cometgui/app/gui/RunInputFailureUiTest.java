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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.RealSearch.LaunchRecorder;
import org.cometgui.app.testing.TestPercolators;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.controls.UiIds;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two failure paths of the specification's list in the Run section of the launched application
 * (Phase 14, gate item 4), with stand-in tools:
 *
 * <ol>
 *   <li><strong>"a source input deleted after selection"</strong>: both spectrum files chosen
 *       through the interface, the pre-run check satisfied, then one file deleted and Run pressed.
 *       The run does not start, the file is named by position and path, no process is launched and
 *       no run is created.
 *   <li><strong>"Comet exits zero but the PIN is missing"</strong>: a stand-in Comet that writes
 *       each file's pepXML and no PIN, and exits 0. The run fails at {@code run-comet}, the outcome
 *       names the missing PIN, the stepper shows Comet failed, and Percolator was never launched.
 * </ol>
 *
 * <p><strong>What is deliberately not asserted.</strong> After that failed run ends, the stepper's
 * Percolator stage reads "Running" and stays so (measured: unchanged 30 seconds after the outcome
 * was shown). {@code StageProjection.combine} reports a stage whose steps are partly terminal as
 * running, and the stage's {@code resolve-percolator} succeeded before {@code run-comet} failed, so
 * an ended run is drawn as if Percolator were under way. Phase 14 unit 8a reports this as a product
 * defect; asserting either the wrong word or the right one here would pin a defect or fail on it,
 * so the Percolator stage is judged by its launches alone.
 *
 * <p>No file outside this module is read: the Comet is a shell script, the Percolator an executable
 * offered as an installed managed build ({@link TestPercolators}) and never run here, the spectrum
 * files are named and hashed, never parsed. Every expected text is typed out; a run's identifier
 * and directory, which a clock names, are read from the project.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunInputFailureUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-23T00:00:00Z"));

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    /** Writes {@code <-N base>.pep.xml}, never a PIN, and exits 0. */
    private static final String COMET_WITHOUT_PIN =
            "#!/bin/sh\n"
                    + "base=''\n"
                    + "for argument in \"$@\"; do\n"
                    + "  case \"$argument\" in\n"
                    + "    -N*) base=\"${argument#-N}\" ;;\n"
                    + "  esac\n"
                    + "done\n"
                    + "[ -n \"$base\" ] || exit 9\n"
                    + "printf '<?xml version=\"1.0\"?>\\n<msms_pipeline_analysis/>\\n'"
                    + " > \"$base.pep.xml\"\n"
                    + "exit 0\n";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static LaunchRecorder launches;

    private static Path comet;

    private static Path percolator;

    private static Path first;

    private static Path second;

    private static Path project;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        Path bin = Files.createDirectories(root.resolve("bin"));
        comet =
                Files.writeString(
                        bin.resolve("comet"), COMET_WITHOUT_PIN, StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(comet, PosixFilePermissions.fromString("rwx------"));
        percolator =
                Files.writeString(
                        bin.resolve("percolator"),
                        "#!/bin/sh\nexit 0\n",
                        StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(percolator, PosixFilePermissions.fromString("rwx------"));
        ToolOffer offer =
                TestPercolators.installed(
                        "3.07.1", ToolOrigin.MANAGED, percolator, TestPercolators.ALL, List.of());
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        first = Files.writeString(inputs.resolve("a.mzML"), "spectra a", StandardCharsets.US_ASCII);
        second =
                Files.writeString(inputs.resolve("b.mzML"), "spectra b", StandardCharsets.US_ASCII);
        Path fasta =
                Files.writeString(
                        inputs.resolve("targets.fasta"),
                        ">sp|P00001|ONE first\nPEPTIDEK\n>sp|P00002|TWO second\nSAMPLERK\n",
                        StandardCharsets.US_ASCII);
        project = root.resolve("project");
        launches = new LaunchRecorder(new ProcessService(Clock.systemUTC()));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        launches,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(
                                () -> InstalledComet.at("2026.03.0", comet).with(offer), project));
        driver = new TestFxUiDriver(app.application());
        app.chooser().spectra(first, second);
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        driver.clickOn("ess-spectra-add");
        app.chooser().database(fasta);
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
    @Order(1)
    @DisplayName(
            "a spectrum file deleted after it was chosen, then Run: the run does not start, the"
                    + " file is named, nothing is launched and no run is created")
    void aSpectrumFileDeletedAfterSelection() throws IOException {
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver), "both files present");
        Files.delete(second);
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.CHECK_BOUND);
        assertAll(
                () ->
                        assertEquals(
                                "The run did not start: the run cannot start:\n- spectrum file 2 ("
                                        + second
                                        + ") does not exist or cannot be read",
                                outcome),
                () -> assertEquals(List.of(), launches.launches(), "nothing was launched"),
                () -> assertEquals(List.of(), runs(), "and no run was created"),
                () -> assertEquals("Not started", driver.textOf("stage-comet-state")));
    }

    @Test
    @Order(2)
    @DisplayName(
            "Comet exits 0 but writes no PIN: the run fails at run-comet naming the PIN, Comet is"
                    + " shown failed, Percolator is never launched")
    void cometExitsZeroWithoutAPin() throws IOException {
        Files.writeString(second, "spectra b", StandardCharsets.US_ASCII);
        // The check reads the files when the spectrum list changes, so the restored file is chosen
        // again.
        ParameterEditorApp.openEditor(driver);
        driver.clickOn(UiIds.spectrumRemove(1));
        app.chooser().spectra(second);
        driver.clickOn("ess-spectra-add");
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run ") && !text.contains("did not start"),
                        RunSection.RUN_BOUND);
        List<Path> made = runs();
        assertEquals(1, made.size(), () -> "the runs: " + made);
        Path run = made.get(0);
        String name = String.valueOf(run.getFileName());
        String id = name.substring(name.indexOf('-') + 1);
        assertAll(
                () ->
                        assertEquals(
                                "The run failed at step run-comet (Run Comet once per spectrum"
                                        + " file): run-comet reported success, but its declared"
                                        + " output file, role pin "
                                        + run.resolve("outputs/comet/a.pin")
                                        + " does not exist. Run "
                                        + id
                                        + " in "
                                        + run
                                        + ".",
                                outcome),
                () -> assertEquals("Failed", driver.textOf("stage-comet-state")),
                () ->
                        assertEquals(
                                2,
                                launchesOf(comet).size(),
                                "Comet really ran, once per spectrum file"),
                () ->
                        assertEquals(
                                List.of(), launchesOf(percolator), "Percolator was never launched"),
                () ->
                        assertTrue(
                                Files.isRegularFile(run.resolve("outputs/comet/a.pep.xml")),
                                "the stand-in's pepXML is there"),
                () ->
                        assertEquals(
                                List.of(),
                                pinsUnder(run),
                                "and no PIN anywhere in the run, merged or not"));
    }

    private static List<List<String>> launchesOf(Path executable) {
        return launches.launches().stream()
                .map(LaunchRecorder.Launch::argv)
                .filter(argv -> argv.get(0).equals(executable.toString()))
                .toList();
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

    private static List<String> pinsUnder(Path root) throws IOException {
        try (Stream<Path> walked = Files.walk(root)) {
            return walked.filter(path -> String.valueOf(path.getFileName()).endsWith(".pin"))
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
    }
}
