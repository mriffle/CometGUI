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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javafx.scene.control.CheckBox;
import javafx.scene.input.KeyCode;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.LimelightFiles;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.RealPercolators;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.RealSearch.LaunchRecorder;
import org.cometgui.app.testing.StagedAndManaged;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
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
 * Phase 12 gate items 1, 5 and 8 through the interface, with real binaries end to end: a real
 * search -- the real Comet and Percolator 3.07.1, Limelight conversion switched on in the
 * Percolator section -- run through the Run section, then converted through the Limelight section
 * from an <strong>empty</strong> tool cache.
 *
 * <ul>
 *   <li><strong>Gate 8</strong>, before anything else: on this real CometGUI run, whose Percolator
 *       wrote no decoy PSMs, decoy import is disabled with the workflow's explanation, a click on
 *       it changes nothing, and the keyboard never reaches it -- a Tab walk through the whole
 *       section reaches the cutoff, the output, the prefix, open-mod and Convert, and never decoy
 *       import.
 *   <li><strong>Gate 1</strong>: Convert installs the real pinned converter through the product's
 *       own Tool Manager over the empty cache, from a loopback artefact server, and converts. The
 *       section says valid; this test then validates the file itself against the schema it reads
 *       from the installed JAR, counts its {@code psm} elements against the run's own target PSM
 *       rows, hashes it against the SHA-256 the section shows, and reads the run's {@code
 *       limelight.converted} event.
 *   <li><strong>Gate 5</strong>: the cutoff shows 0.01; set to 0.05 in the Limelight section, the
 *       Results section's PSM and peptide filters are still 0.01 with unchanged counts; a PSM
 *       filter changed there leaves the Limelight cutoff at 0.05; and the next conversion's file
 *       carries {@code default_filter_value} 0.05, read here, while the first still carries 0.01.
 * </ul>
 *
 * <p>Every identifier is a hand-typed literal (P7-5). Files read outside this module: those of
 * {@link RealSearch}, {@link RealPercolators} and {@link ArtefactMirror} -- {@code
 * scratch/fixture/}, {@code scratch/phase05/artefacts/} (Comet, Percolator 3.07.1 and the pinned
 * converter JAR) -- and {@code manifests/tools.json} on the class path. Anything missing fails.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LimelightConversionUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-12T00:00:00Z"));

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String CONVERTER_JAR = "v2.8.1__cometPercolator2LimelightXML.jar";

    /** The pinned JAR's SHA-256, typed out. */
    private static final String CONVERTER_SHA256 =
            "843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1";

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    /** How long a first-use install and one conversion may take. A bound, not a delay. */
    private static final Duration CONVERT_BOUND = Duration.ofMinutes(5);

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static LaunchRecorder launches;

    private static LoopbackArtefactServer served;

    private static ArtefactRecord converter;

    private static ToolManager managed;

    private static Path cacheRoot;

    private static Path project;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        Path comet = RealSearch.stageComet(root.resolve("bin/comet"));
        ToolOffer percolator = RealPercolators.installed3071(root.resolve("bin/percolator"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealSearch.spectra(inputs);
        Path subset = RealSearch.subset(inputs.resolve("subset.fasta"));
        project = root.resolve("project");
        cacheRoot = root.resolve("cometgui-data");
        converter = ArtefactMirror.record(ToolName.LIMELIGHT_CONVERTER, "2.8.1", LINUX);
        served =
                new LoopbackArtefactServer()
                        .serve(converter.url(), ArtefactMirror.artefact(CONVERTER_JAR));
        launches = new LaunchRecorder(new ProcessService(Clock.systemUTC()));
        managed =
                ToolManagerWiring.toolManager(
                        LINUX,
                        DEBIAN_12,
                        cacheRoot,
                        launches,
                        Clock.systemUTC(),
                        ToolManagerWiring.installThreads(),
                        served);
        ToolManager tools =
                new StagedAndManaged(
                        InstalledComet.at(RealSearch.RELEASE, comet).with(percolator),
                        managed,
                        Set.of(ToolName.LIMELIGHT_CONVERTER));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        launches,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(() -> tools, project));
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
    static void stop() throws IOException {
        try {
            if (app != null) {
                app.stop();
            }
        } finally {
            if (served != null) {
                served.close();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "a real run with Percolator 3.07.1 and Limelight on; gate 8: decoy import disabled with"
                    + " the explanation, not selectable by mouse or keyboard")
    void aRealRunAndGate8() throws IOException {
        driver.clickOn("nav-percolator");
        RunSection.awaitText(
                driver,
                "percolator-offers",
                text -> text.endsWith("can run."),
                RunSection.CHECK_BOUND);
        driver.clickOn("percolator-limelight");
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
        assertTrue(
                Files.isRegularFile(run.resolve("outputs/percolator/pout.xml")),
                "Limelight on: Percolator 3.07.1 wrote XML");

        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, idOf(run), ResultsSection.OPEN_BOUND);
        driver.clickOn("nav-limelight");
        String compatibility =
                RunSection.awaitText(
                        driver,
                        "limelight-compatibility",
                        text -> !text.startsWith("Checking"),
                        RunSection.CHECK_BOUND);
        String decoys = driver.textOf("limelight-decoy-status");
        Path pout = run.resolve("outputs/percolator/pout.xml");
        assertAll(
                "the section before anything is converted",
                () ->
                        assertEquals(
                                "Run "
                                        + idOf(run)
                                        + "'s Percolator 3.07.1 wrote the Percolator XML the"
                                        + " Limelight converter reads, so the run can be"
                                        + " converted.",
                                compatibility),
                () -> assertTrue(driver.textOf("limelight-run").startsWith("Run " + idOf(run))),
                () -> assertEquals("0.01", driver.textOf("limelight-q-cutoff")),
                () -> assertFalse(RunSection.isDisabled(driver, "limelight-q-cutoff")),
                () -> assertFalse(RunSection.isDisabled(driver, "limelight-convert")),
                () -> assertFalse(driver.isVisible("limelight-action"), "no action is offered"),
                () ->
                        assertEquals(
                                "Limelight decoy import is not available for this run: its"
                                        + " Percolator output "
                                        + pout
                                        + " holds no decoy PSMs, because Percolator was not run"
                                        + " with -Z (decoy XML output). Asking the converter to"
                                        + " import decoys from it would silently import none."
                                        + " Comet's internal decoys cannot be imported either:"
                                        + " the converter looks every protein up in the FASTA,"
                                        + " and Comet's generated decoys are not FASTA entries.",
                                decoys),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-import-decoys")),
                () -> assertFalse(decoySelected()),
                () ->
                        assertEquals(
                                "The FASTA the run recorded, which the converter reads: "
                                        + scratch.toRealPath().resolve("inputs/subset.fasta"),
                                driver.textOf("limelight-fasta")),
                () ->
                        assertEquals(
                                "The pepXML directory of the run that searched, which the"
                                        + " converter reads: "
                                        + run.resolve("outputs/comet"),
                                driver.textOf("limelight-pepxml-directory")),
                () ->
                        assertEquals(
                                "The next conversion writes "
                                        + run.resolve("outputs/limelight/limelight-1.xml")
                                        + ".",
                                driver.textOf("limelight-output-status")),
                () ->
                        assertTrue(
                                driver.textOf("limelight-converter-status")
                                        .startsWith(
                                                "The Limelight converter 2.8.1 is not installed."),
                                driver.textOf("limelight-converter-status")),
                () -> assertEquals(List.of(), served.requested(), "nothing fetched yet"),
                () -> assertFalse(Files.exists(cacheRoot.resolve("tools")), "the cache is empty"));

        // gate 8: the mouse
        driver.clickOn("limelight-import-decoys");
        assertFalse(decoySelected(), "a click on the disabled control changes nothing");

        // gate 8: the keyboard -- a Tab walk through the whole section
        driver.clickOn("nav-limelight");
        List<String> reached = new ArrayList<>();
        for (int press = 0; press < 40; press++) {
            driver.tab();
            String focused = driver.focusedNodeId();
            if ("nav-limelight".equals(focused)) {
                break;
            }
            reached.add(focused);
        }
        assertAll(
                "the keyboard reaches every enabled control and never decoy import",
                () ->
                        assertTrue(
                                reached.containsAll(
                                        List.of(
                                                "limelight-refresh",
                                                "limelight-q-cutoff",
                                                "limelight-output",
                                                "limelight-decoy-prefix",
                                                "limelight-open-mod",
                                                "limelight-convert")),
                                () -> "reached by Tab: " + reached),
                () -> assertFalse(reached.contains("limelight-import-decoys"), reached::toString),
                () -> assertFalse(decoySelected()));
        driver.press(KeyCode.SPACE);
        assertAll(
                () -> assertFalse(decoySelected()),
                () ->
                        assertFalse(
                                Files.exists(run.resolve("results/limelight-settings.json")),
                                "no setting was saved: decoy import was never asked for"));
    }

    @Test
    @Order(2)
    @DisplayName(
            "gate 1: Convert installs the converter from an empty cache and converts; the file is"
                    + " valid against the JAR's own schema, holds the run's PSMs, and hashes to the"
                    + " SHA-256 shown")
    void gate1() throws IOException {
        Path run = onlyRun(0);
        driver.clickOn("nav-limelight");
        int launchedBefore = launches.launches().size();
        driver.clickOn("limelight-convert");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "limelight-outcome",
                        text ->
                                text.startsWith("Run " + idOf(run) + " was converted")
                                        || text.startsWith("Nothing was converted")
                                        || text.startsWith("The converter's output was not kept")
                                        || text.startsWith("The Limelight converter could not"),
                        CONVERT_BOUND);
        Path firstFile = run.resolve("outputs/limelight/limelight-1.xml");
        Path jar = installedConverter();
        int rows = LimelightFiles.targetPsmRows(run);
        LimelightFiles.Read read = LimelightFiles.read(firstFile);
        String shownSha = driver.textOf("limelight-result-sha256");
        List<Map<String, Object>> converted = LimelightFiles.events(run, "limelight.converted");
        assertAll(
                "the conversion",
                () ->
                        assertTrue(
                                outcome.startsWith(
                                        "Run "
                                                + idOf(run)
                                                + " was converted to valid Limelight XML: "
                                                + firstFile
                                                + ", holding "
                                                + rows
                                                + " PSMs and "),
                                outcome),
                () -> assertEquals(firstFile.toString(), driver.textOf("limelight-result-file")),
                () -> assertEquals(Integer.toString(rows), driver.textOf("limelight-result-psms")),
                () -> assertTrue(rows > 100, "a real run's PSMs: " + rows),
                () -> assertEquals(rows, read.psms(), "the file's psm elements, counted here"),
                () -> assertEquals(Optional.of("0.01"), read.qFilter()),
                () -> LimelightFiles.assertValid(firstFile, jar),
                () -> LimelightFiles.assertSha256(firstFile, shownSha),
                () -> assertEquals(CONVERTER_SHA256, RealSearch.sha256(jar)),
                () -> assertEquals(List.of(converter.url()), served.requested(), "fetched once"),
                () -> assertEquals(1, converted.size()),
                () -> assertEquals(shownSha, converted.get(0).get("output.sha256")),
                () -> assertEquals(Integer.toString(rows), converted.get(0).get("output.psms")),
                () -> assertEquals("0.01", converted.get(0).get("limelight.q-cutoff")),
                () -> assertEquals(CONVERTER_SHA256, converted.get(0).get("converter.jar.sha256")),
                () ->
                        assertFalse(
                                driver.textOf("limelight-log").isBlank(),
                                "the converter's log lines were shown"),
                () -> {
                    List<LaunchRecorder.Launch> conversions =
                            launches.launches().stream()
                                    .skip(launchedBefore)
                                    .filter(launch -> launch.argv().contains("-o"))
                                    .toList();
                    assertEquals(1, conversions.size(), "one conversion process");
                    assertFalse(
                            conversions.get(0).onFxThread(),
                            "the conversion started on the JavaFX thread");
                    assertTrue(
                            conversions.get(0).argv().contains(jar.toString()),
                            () -> "the installed JAR ran: " + conversions.get(0).argv());
                });
    }

    @Test
    @Order(3)
    @DisplayName(
            "gate 5: the Limelight cutoff and the display filters are independent; the next"
                    + " file carries 0.05")
    void gate5() throws IOException {
        Path run = onlyRun(0);
        Path firstFile = run.resolve("outputs/limelight/limelight-1.xml");
        driver.clickOn("nav-results");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        List<String> counts = ResultsSection.counts(driver);
        assertAll(
                () -> assertEquals("0.01", driver.textOf("results-psm-filter")),
                () -> assertEquals("0.01", driver.textOf("results-peptide-filter")));

        driver.clickOn("nav-limelight");
        enter(driver, "limelight-q-cutoff", "0.05");
        assertEquals(
                "Saved: the next conversion of run "
                        + idOf(run)
                        + " uses the Limelight q-value cutoff 0.05, imports no decoys.",
                RunSection.awaitText(
                        driver,
                        "limelight-settings-status",
                        text -> text.startsWith("Saved"),
                        RunSection.CHECK_BOUND));

        driver.clickOn("nav-results");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertAll(
                "the display filters did not move",
                () -> assertEquals("0.01", driver.textOf("results-psm-filter")),
                () -> assertEquals("0.01", driver.textOf("results-peptide-filter")),
                () -> assertEquals(counts, ResultsSection.counts(driver)));
        enter(driver, "results-psm-filter", "0.02");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        driver.clickOn("nav-limelight");
        assertEquals("0.05", driver.textOf("limelight-q-cutoff"), "the PSM filter moved, not this");

        driver.clickOn("limelight-convert");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "limelight-outcome",
                        text ->
                                text.startsWith("Run " + idOf(run) + " was converted")
                                                && text.contains("limelight-2.xml")
                                        || text.startsWith("Nothing was converted")
                                        || text.startsWith("The converter's output was not kept"),
                        CONVERT_BOUND);
        Path second = run.resolve("outputs/limelight/limelight-2.xml");
        assertAll(
                () -> assertTrue(outcome.contains("with the PSM q-value filter 0.05"), outcome),
                () -> assertEquals(Optional.of("0.05"), LimelightFiles.read(second).qFilter()),
                () -> assertEquals(Optional.of("0.01"), LimelightFiles.read(firstFile).qFilter()),
                () -> LimelightFiles.assertValid(second, installedConverter()),
                () -> LimelightFiles.assertSha256(second, driver.textOf("limelight-result-sha256")),
                () ->
                        assertEquals(
                                List.of(converter.url()), served.requested(), "not fetched again"));
        driver.clickOn("nav-results");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        assertEquals("0.02", driver.textOf("results-psm-filter"), "the conversion moved no filter");
    }

    private static boolean decoySelected() {
        CheckBox box = (CheckBox) driver.node("limelight-import-decoys");
        return driver.callOnFxThread(box::isSelected);
    }

    /** The converter the Tool Manager installed, as it offers it now. */
    private static Path installedConverter() {
        return managed.offers().stream()
                .filter(offer -> offer.tool() == ToolName.LIMELIGHT_CONVERTER)
                .filter(offer -> offer.installedPath().isPresent())
                .map(offer -> offer.installedPath().orElseThrow())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no converter is installed"));
    }

    private static Path onlyRun(int position) {
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            List<Path> sorted = runs.sorted().toList();
            assertEquals(position + 1, sorted.size(), () -> "the runs: " + sorted);
            return sorted.get(position);
        } catch (IOException unreadable) {
            throw new AssertionError("the project's runs cannot be listed", unreadable);
        }
    }

    private static String idOf(Path directory) {
        String name = String.valueOf(directory.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }
}
