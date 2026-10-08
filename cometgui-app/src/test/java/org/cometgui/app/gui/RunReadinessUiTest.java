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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.input.KeyCode;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.RealPercolators;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * Run readiness in the launched application with the workflow engine's half real (decision P8-16,
 * {@code AC-WF-03} in the interface): a Comet 2026.03.0 registered with the Tool Manager, two
 * spectrum files and a FASTA chosen through the interface, and the pre-run check -- which reads the
 * FASTA off the JavaFX thread -- deciding.
 *
 * <ol>
 *   <li>A FASTA with no decoys and {@code decoy_search = 0}: the decoy block's own words on screen,
 *       Run disabled, and the reason reached by the keyboard alone -- in the accessible help of the
 *       Run navigation entry, the tab stop a keyboard reaches; choosing Comet's own decoys lifts it
 *       and Run is enabled.
 *   <li>With the engine ready, a parameter error alone disables Run: the parameters' half is not
 *       hidden behind an engine that always says no (Phase 07's gate-6 note).
 * </ol>
 *
 * <p>No Comet is launched here, so the "Comet" is a file that is executable and never run, and the
 * spectrum files are named, not read. A run includes Percolator since Phase 09, so the Tool Manager
 * also offers the pinned Percolator 3.07.1, staged, held to its SHA-256 and probed ({@link
 * RealPercolators}); the engine's half is ready only with a Percolator that can run. Every text is
 * typed out.
 *
 * <p>Files read outside this module: those {@link RealPercolators} names for 3.07.1.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunReadinessUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-07T00:00:00Z"));

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static Path fasta;

    private static Path project;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        Path comet = Files.createDirectories(root.resolve("bin")).resolve("comet");
        Files.writeString(comet, "#!/bin/sh\nexit 0\n", StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(comet, PosixFilePermissions.fromString("rwx------"));
        ToolOffer percolator = RealPercolators.installed3071(root.resolve("bin/percolator"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        Path first =
                Files.writeString(inputs.resolve("a.mzML"), "spectra", StandardCharsets.US_ASCII);
        Path second =
                Files.writeString(inputs.resolve("b.mzML"), "spectra", StandardCharsets.US_ASCII);
        fasta =
                Files.writeString(
                        inputs.resolve("targets.fasta"),
                        ">sp|P00001|ONE first\nPEPTIDEK\n>sp|P00002|TWO second\nSAMPLERK\n",
                        StandardCharsets.US_ASCII);
        project = root.resolve("project");
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        new ProcessService(Clock.systemUTC()),
                        new BoundedMessageLog(),
                        new RunWiring.Setup(
                                () -> InstalledComet.at("2026.03.0", comet).with(percolator),
                                project));
        driver = new TestFxUiDriver(app.application());
        app.chooser().spectra(first, second);
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("ess-spectra-add");
        app.chooser().database(fasta);
        driver.clickOn("ess-database_name-choose");
        // A new configuration searches without a spectral library (D-012): nothing to clear.
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-ms1_realtime-toggle");
        assertEquals("", driver.textOf("adv-spectral_library_name"));
        driver.clickOn("param-mode-essentials");
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
            "a FASTA with no decoys and decoy_search = 0: the decoy block in words, Run"
                    + " disabled, the reason reached by keyboard; choosing Comet's decoys"
                    + " enables Run")
    void theDecoyBlockOnScreen() {
        driver.clickOn("nav-run");
        String block =
                "decoy_search = 0 (no internal decoys) and "
                        + fasta
                        + " holds no entry whose accession begins with DECOY_ (0 of 2 records):"
                        + " Percolator would have no negative examples; set decoy_search to 1 or"
                        + " 2 so that Comet makes decoys, or choose a FASTA whose decoys begin"
                        + " with DECOY_";
        assertEquals(
                "The workflow engine cannot start this search:\n" + block,
                RunSection.awaitEngineAnswer(driver));
        assertAll(
                "blocked by the engine alone",
                () -> assertTrue(RunSection.isDisabled(driver, "run-start"), "Run is disabled"),
                () -> assertTrue(RunSection.isDisabled(driver, "run-cancel"), "nothing to cancel"),
                () ->
                        assertEquals(
                                "The parameters do not block a run.",
                                driver.textOf("run-parameters")),
                () ->
                        assertEquals(
                                block,
                                driver.callOnFxThread(
                                        () -> driver.node("run-start").getAccessibleHelp()),
                                "a screen reader on Run hears the same reason"),
                () ->
                        assertEquals(
                                "No rerun preview: the run cannot start until the reasons above"
                                        + " are resolved.",
                                driver.textOf("run-preview")),
                () ->
                        assertTrue(
                                Files.isRegularFile(project.resolve("project.json")),
                                "the check opened the session's project"),
                () ->
                        assertTrue(
                                Files.isRegularFile(project.resolve("project.lock")),
                                "and holds its lock"),
                () -> assertEquals(List.of(), runsIn(project), "but created no run"));

        // The keyboard alone: from another section, the arrow key selects Run and puts the focus
        // on its navigation entry -- the window's tab stop, since a disabled Run takes no focus --
        // and what a screen reader announces there ends with the reason.
        driver.clickOn("nav-comet-parameters");
        driver.press(KeyCode.UP);
        assertEquals("nav-run", driver.focusedNodeId());
        assertEquals(
                "Inputs, workflow summary, selected tool versions, high-level parameter summary,"
                        + " validation, and Run and Cancel controls. "
                        + block,
                driver.callOnFxThread(() -> driver.node("nav-run").getAccessibleHelp()),
                "the Run entry's accessible help: the section, then why Run is disabled");

        // Fixed: Comet makes the decoys.
        ParameterEditorApp.openEditor(driver);
        ParameterEditorApp.choose(
                driver,
                "ess-decoy_search",
                "Concatenated: targets and decoys compete, one result per spectrum");
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        assertAll(
                "ready",
                () -> assertFalse(RunSection.isDisabled(driver, "run-start"), "Run is enabled"),
                () -> assertTrue(RunSection.isDisabled(driver, "run-cancel")),
                () ->
                        assertEquals(
                                "No earlier run in this session: Run starts a new run, and every"
                                        + " step executes.",
                                driver.textOf("run-preview")),
                () ->
                        assertEquals(
                                "No run has started in this session.",
                                driver.textOf("run-outcome")));
    }

    @Test
    @Order(2)
    @DisplayName("with the engine ready, a parameter error alone disables Run, in words")
    void theParametersAloneDisableRun() {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        enter(driver, "ess-peptide_mass_tolerance_lower", "10");
        enter(driver, "ess-peptide_mass_tolerance_upper", "-10");
        driver.clickOn("nav-run");
        assertEquals(
                ENGINE_CAN_RUN,
                RunSection.awaitEngineAnswer(driver),
                "the window's error is the parameters' reason, not the engine's too");
        assertAll(
                "blocked by the parameters alone",
                () ->
                        assertTrue(
                                RunSection.isDisabled(driver, "run-start"),
                                "Run is disabled by the parameters alone"),
                () ->
                        assertEquals(
                                "The parameters block a run:\nError -- Precursor tolerance, lower"
                                        + " bound (peptide_mass_tolerance_lower), Precursor mass"
                                        + " and isotope handling: peptide_mass_tolerance_lower ="
                                        + " 10 and peptide_mass_tolerance_upper = -10: the lower"
                                        + " bound is above the upper bound, so no precursor can"
                                        + " match and Comet refuses to search; the lower bound is"
                                        + " normally negative, for example -20.0 and 20.0",
                                driver.textOf("run-parameters")));

        ParameterEditorApp.openEditor(driver);
        enter(driver, "ess-peptide_mass_tolerance_upper", "10");
        enter(driver, "ess-peptide_mass_tolerance_lower", "-10");
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        assertEquals("The parameters do not block a run.", driver.textOf("run-parameters"));
        assertFalse(RunSection.isDisabled(driver, "run-start"), "Run is enabled again");
    }

    private static List<String> runsIn(Path directory) {
        try (Stream<Path> runs = Files.list(directory.resolve("runs"))) {
            return runs.map(path -> String.valueOf(path.getFileName())).sorted().toList();
        } catch (IOException unreadable) {
            throw new AssertionError("the project's runs directory cannot be listed", unreadable);
        }
    }
}
