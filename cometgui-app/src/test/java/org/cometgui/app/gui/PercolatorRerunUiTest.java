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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
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
 * Phase 09 gate item 6 through the interface, with real binaries end to end: Percolator 3.09 is
 * registered as a local binary through the Percolator section (the product's own registration,
 * which runs and probes it), a real search runs with it -- Limelight conversion off, so 3.09 is the
 * default and no XML is asked for -- and then, with Limelight conversion switched on, the section's
 * compatible-version rerun shows its preview and produces a second run with Percolator 3.07.1 whose
 * provenance, read back with {@code ManifestReader}, records {@code -X}; Comet is not launched
 * again, and the first run's raw Percolator outputs are unchanged.
 *
 * <p>The registered 3.09 is named {@code 3.09.0}: the version is read from the binary's own banner
 * ("Percolator version 3.09.0"), never from a file name.
 *
 * <p>Files read outside this module: see {@link RealSearch} and {@link RealPercolators}. Every
 * expected text is typed out; a run's identifier and directory, which a clock names, are read from
 * the project, and the merged PIN's SHA-256 is computed here, independently of the product.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PercolatorRerunUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-08T00:00:00Z"));

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static LaunchRecorder launches;

    private static Path comet;

    private static Path percolator3071;

    private static Path wrapper309;

    private static Path project;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        comet = RealSearch.stageComet(root.resolve("bin/comet"));
        ToolOffer managed = RealPercolators.installed3071(root.resolve("bin/percolator"));
        percolator3071 = managed.installedPath().orElseThrow();
        wrapper309 = RealPercolators.wrapper309();
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealSearch.spectra(inputs);
        Path subset = RealSearch.subset(inputs.resolve("subset.fasta"));
        project = root.resolve("project");
        launches = new LaunchRecorder(new ProcessService(Clock.systemUTC()));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        launches,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(
                                () ->
                                        InstalledComet.at(RealSearch.RELEASE, comet)
                                                .with(managed)
                                                .registering(RealPercolators.registrar()),
                                project));
        driver = new TestFxUiDriver(app.application());

        app.chooser().spectra(spectra.get(0), spectra.get(1));
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        driver.clickOn("ess-spectra-add");
        app.chooser().database(subset);
        driver.clickOn("ess-database_name-choose");
        ParameterEditorApp.choose(
                driver,
                "ess-decoy_search",
                "Concatenated: targets and decoys compete, one result per spectrum");
        enter(driver, "ess-num_threads", "4");
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
            "Percolator 3.09 registered through the section becomes the default; no rerun is"
                    + " offered before a run")
    void register309() {
        driver.clickOn("nav-percolator");
        assertEquals(
                "1 Percolator build is known on this computer, and 1 of them is installed and can"
                        + " run.",
                RunSection.awaitText(
                        driver,
                        "percolator-offers",
                        text -> text.endsWith("can run."),
                        RunSection.CHECK_BOUND));
        assertEquals(
                "No run has been made in this session, so there is no merged PIN to rerun"
                        + " Percolator from. Run a search first.",
                RunSection.awaitText(
                        driver,
                        "percolator-rerun-preview",
                        text -> !text.startsWith("Checking"),
                        RunSection.CHECK_BOUND));
        assertTrue(RunSection.isDisabled(driver, "percolator-rerun"));

        app.chooser().file(wrapper309);
        driver.clickOn("percolator-register");
        assertEquals(
                "Registered Percolator 3.09.0 (registered local binary) from "
                        + wrapper309
                        + ". Observed capabilities: PSM_TSV_OUTPUT, PEPTIDE_TSV_OUTPUT,"
                        + " DECOY_OUTPUT, WEIGHTS_OUTPUT, THREAD_OPTION, SEED_OPTION,"
                        + " TEST_FDR_OPTION, TRAIN_FDR_OPTION, MAX_ITERATIONS_OPTION,"
                        + " NO_ANALYTICS_OPTION.",
                RunSection.awaitText(
                        driver,
                        "percolator-register-status",
                        text -> !text.startsWith("Registering"),
                        RunSection.CHECK_BOUND));
        assertEquals(
                "The default Percolator changed from 3.07.1 to 3.09.0 (registered local binary)"
                        + " because the Percolator builds available on this computer changed.",
                RunSection.awaitText(
                        driver,
                        "percolator-notice",
                        text -> text.startsWith("The default Percolator changed"),
                        RunSection.CHECK_BOUND));
        assertEquals(
                "Percolator 3.09.0 (registered local binary) runs in the next search: the resolved"
                        + " default.",
                driver.textOf("percolator-selection"));
    }

    @Test
    @Order(2)
    @DisplayName("a real run with 3.09, Limelight off: no XML option, recorded in provenance")
    void aRunWith309() throws IOException {
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        Path run = onlyRun(0);
        assertEquals("The run succeeded: run " + idOf(run) + " in " + run + ".", outcome);
        assertEquals(2, launchesOf(comet).size(), "one Comet per spectrum file");
        List<LaunchRecorder.Launch> percolators = launchesOf(wrapper309);
        assertEquals(1, percolators.size());
        assertFalse(percolators.get(0).argv().contains("-X"), "3.09 is never asked for XML");
        ToolRecord tool = percolatorTool(ManifestReader.readFrom(provenanceOf(run)));
        assertAll(
                () -> assertEquals("3.09.0", tool.version()),
                () -> assertEquals(RealPercolators.SHA256_WRAPPER_309, tool.hashes().sha256()),
                () -> assertFalse(tool.execution().command().argv().contains("-X")),
                () -> assertFalse(Files.exists(run.resolve("outputs/percolator/pout.xml"))));
    }

    @Test
    @Order(3)
    @DisplayName(
            "gate 6: Limelight on, the rerun's preview, and a second run with 3.07.1 whose"
                    + " provenance has -X; Comet not launched again; the first run unchanged")
    void gate6TheRerun() throws IOException {
        Path first = onlyRun(0);
        String firstId = idOf(first);
        Path merged = first.resolve("inputs/pin/merged.pin");
        String firstPsms = RealSearch.sha256(first.resolve("outputs/percolator/psms.tsv"));
        String firstProvenance = RealSearch.sha256(provenanceOf(first));

        driver.clickOn("nav-percolator");
        driver.clickOn("percolator-limelight");
        assertEquals(
                "The default Percolator changed from 3.09.0 (registered local binary) to 3.07.1"
                        + " because Limelight conversion was switched on and needs XML_OUTPUT.",
                driver.textOf("percolator-notice"));
        String preview =
                RunSection.awaitText(
                        driver,
                        "percolator-rerun-preview",
                        text -> text.startsWith("Rerun "),
                        RunSection.CHECK_BOUND);
        assertEquals(
                "Rerun Percolator 3.07.1 from run "
                        + firstId
                        + ", in a new run:\n"
                        + "Percolator reruns in a new run on the merged PIN of run "
                        + firstId
                        + " ("
                        + merged
                        + ", SHA-256 "
                        + RealSearch.sha256(merged)
                        + ", re-hashed and unchanged); Comet is not run.\n"
                        + "validate-configuration: prepares -- needed by resolve-percolator\n"
                        + "resolve-percolator: prepares -- needed by run-percolator\n"
                        + "serialise-comet-params: not executed -- its result is reused from run "
                        + firstId
                        + "\nrun-comet: not executed -- its result is reused from run "
                        + firstId
                        + "\nvalidate-comet-outputs: not executed -- its result is reused from run "
                        + firstId
                        + "\nmerge-pin: not executed -- its result is reused from run "
                        + firstId
                        + "\nrun-percolator: executes -- no successful earlier execution is"
                        + " recorded\n"
                        + "parse-percolator: executes -- no successful earlier execution is"
                        + " recorded; run-percolator re-executes\n"
                        + "finalise-provenance: executes -- no successful earlier execution is"
                        + " recorded",
                preview);
        assertEquals("Rerun Percolator 3.07.1", driver.textOf("percolator-rerun"));
        assertFalse(RunSection.isDisabled(driver, "percolator-rerun"));
        int cometBefore = launchesOf(comet).size();

        driver.clickOn("percolator-rerun");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "percolator-rerun-outcome",
                        text -> text.startsWith("The Percolator rerun "),
                        RunSection.RUN_BOUND);
        Path second = onlyRun(1);
        assertEquals(
                "The Percolator rerun succeeded: run "
                        + idOf(second)
                        + " in "
                        + second
                        + ", rescoring the merged PIN of run "
                        + firstId
                        + ".",
                outcome);

        assertEquals(cometBefore, launchesOf(comet).size(), "Comet was launched by the rerun");
        List<LaunchRecorder.Launch> reruns = launchesOf(percolator3071);
        assertEquals(1, reruns.size(), "one Percolator 3.07.1 invocation");
        assertFalse(reruns.get(0).onFxThread(), "launched on the JavaFX thread");
        ProvenanceManifest after = ManifestReader.readFrom(provenanceOf(second));
        ToolRecord tool = percolatorTool(after);
        List<String> argv = tool.execution().command().argv();
        int x = argv.indexOf("-X");
        assertAll(
                "the second run's record",
                () -> assertEquals("3.07.1", tool.version()),
                () -> assertEquals(RealPercolators.SHA256_3071, tool.hashes().sha256()),
                () -> assertEquals(percolator3071.toString(), argv.get(0)),
                () -> assertTrue(x > 0, () -> "-X in the recorded argv: " + argv),
                () ->
                        assertEquals(
                                second.resolve("outputs/percolator/pout.xml").toString(),
                                argv.get(x + 1)),
                () ->
                        assertTrue(
                                Files.isRegularFile(second.resolve("outputs/percolator/pout.xml"))),
                () ->
                        assertEquals(
                                List.of(),
                                after.tools().stream()
                                        .filter(record -> record.name().equals("comet"))
                                        .toList(),
                                "no Comet executed in the rerun"),
                () -> assertEquals(firstId, after.settings().get("rerun.source-run-id")),
                () ->
                        assertEquals(
                                RealSearch.sha256(merged),
                                after.settings().get("rerun.merged-pin-sha256")),
                () ->
                        assertEquals(
                                "resolved-default", after.settings().get("percolator.selection")));
        assertAll(
                "the first run is untouched",
                () ->
                        assertEquals(
                                firstPsms,
                                RealSearch.sha256(first.resolve("outputs/percolator/psms.tsv"))),
                () -> assertEquals(firstProvenance, RealSearch.sha256(provenanceOf(first))),
                () ->
                        assertEquals(
                                "3.09.0",
                                percolatorTool(ManifestReader.readFrom(provenanceOf(first)))
                                        .version()));
    }

    private static ToolRecord percolatorTool(ProvenanceManifest manifest) {
        List<ToolRecord> tools =
                manifest.tools().stream().filter(tool -> tool.name().equals("percolator")).toList();
        assertEquals(1, tools.size(), () -> "one Percolator record: " + tools);
        return tools.get(0);
    }

    private static Path provenanceOf(Path run) {
        return run.resolve("provenance/provenance.json");
    }

    private static List<LaunchRecorder.Launch> launchesOf(Path executable) {
        return launches.launches().stream()
                .filter(launch -> launch.argv().get(0).equals(executable.toString()))
                .toList();
    }

    /** The project's runs, oldest first; the one at a position. */
    private static Path onlyRun(int position) {
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            List<Path> sorted = runs.sorted().toList();
            assertEquals(position + 1, sorted.size(), () -> "the runs: " + sorted);
            return sorted.get(position);
        } catch (IOException unreadable) {
            throw new AssertionError("the project's runs cannot be listed", unreadable);
        }
    }

    /** A run's identifier: its directory's name after the creation time and the hyphen. */
    private static String idOf(Path run) {
        String name = String.valueOf(run.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }
}
