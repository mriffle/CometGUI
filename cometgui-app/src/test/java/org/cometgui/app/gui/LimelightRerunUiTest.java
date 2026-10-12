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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
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
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.params.percolator.resolution.DownstreamStage;
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
 * Phase 12 gate items 3 and 4 through the interface -- the specification's second end-to-end
 * scenario, "Percolator without XML" -- with real binaries end to end: Percolator 3.09 registered
 * as a local binary through the Percolator section, a real search with it, then the Limelight
 * section.
 *
 * <ul>
 *   <li><strong>Gate 3</strong>: before anything is attempted the section explains that the run's
 *       Percolator wrote no XML; every conversion control is disabled; the action's text is the
 *       label {@link CompatibilityAction} gives over the Tool Manager's offers, computed here --
 *       "Rerun Percolator with 3.07.1 for Limelight"; and the recording process runner shows no
 *       converter was launched and nothing was fetched.
 *   <li><strong>{@code R-LL-02}</strong>: with an offer set in which no XML-capable build can be
 *       obtained (every managed Percolator offered as unpublished for this platform), the action is
 *       local-binary registration with "no managed build exists for this platform", and no rerun is
 *       offered anywhere in the section; pressing it starts the Percolator section's own
 *       registration (its chooser is asked).
 *   <li><strong>Gate 4</strong>: back to the real offer set, the rerun action installs Percolator
 *       3.07.1 through the product's Tool Manager over an empty cache (a loopback artefact server)
 *       only now, reruns it on the merged PIN in a new run -- Comet not launched again -- makes
 *       that run the Results section's, and Convert (installing the converter on first use)
 *       produces valid Limelight XML, checked here against the JAR's schema, the derived run's own
 *       PSM rows and the SHA-256 shown. Both runs' {@code provenance.json}, read here, record their
 *       own Percolator execution: 3.09 without {@code -X}, 3.07.1 with it, different SHA-256s and
 *       argument arrays.
 * </ul>
 *
 * <p>Every identifier is a hand-typed literal (P7-5). Files read outside this module: those of
 * {@link RealSearch}, {@link RealPercolators} ({@code scratch/percolator/3.09/}) and {@link
 * ArtefactMirror} ({@code scratch/fixture/}, {@code scratch/phase05/artefacts/}: Comet, the
 * Percolator 3.07.1 zip and {@code .deb}, the converter JAR), and {@code manifests/tools.json} on
 * the class path.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LimelightRerunUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-12T00:00:00Z"));

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String RERUN_LABEL = "Rerun Percolator with 3.07.1 for Limelight";

    private static final String REGISTER_LABEL = "Register a local Percolator binary for Limelight";

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    /** How long an install, a rerun or a conversion may take. A bound, not a delay. */
    private static final Duration BOUND = Duration.ofMinutes(5);

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static LaunchRecorder launches;

    private static LoopbackArtefactServer served;

    private static ArtefactRecord percolator;

    private static ArtefactRecord converter;

    private static StagedAndManaged tools;

    private static ToolManager managed;

    private static Path comet;

    private static Path wrapper309;

    private static Path cacheRoot;

    private static Path project;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        comet = RealSearch.stageComet(root.resolve("bin/comet"));
        wrapper309 = RealPercolators.wrapper309();
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealSearch.spectra(inputs);
        Path subset = RealSearch.subset(inputs.resolve("subset.fasta"));
        project = root.resolve("project");
        cacheRoot = root.resolve("cometgui-data");
        percolator = ArtefactMirror.record(ToolName.PERCOLATOR, "3.07.1", LINUX);
        converter = ArtefactMirror.record(ToolName.LIMELIGHT_CONVERTER, "2.8.1", LINUX);
        served =
                new LoopbackArtefactServer()
                        .serve(
                                percolator.url(),
                                ArtefactMirror.artefact(
                                        "rel-3-07-01__percolator-noxml-ubuntu-portable.zip"))
                        .serve(
                                percolator.companions().get(0).url(),
                                ArtefactMirror.artefact(
                                        "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb"))
                        .serve(
                                converter.url(),
                                ArtefactMirror.artefact(
                                        "v2.8.1__cometPercolator2LimelightXML.jar"));
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
        tools =
                new StagedAndManaged(
                        InstalledComet.at(RealSearch.RELEASE, comet),
                        managed,
                        Set.of(ToolName.PERCOLATOR, ToolName.LIMELIGHT_CONVERTER));
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
    @DisplayName("a real run with Percolator 3.09, registered as a local binary, Limelight off")
    void aRunWith309() {
        driver.clickOn("nav-percolator");
        RunSection.awaitText(
                driver,
                "percolator-offers",
                text -> text.contains("Percolator build"),
                RunSection.CHECK_BOUND);
        app.chooser().file(wrapper309);
        driver.clickOn("percolator-register");
        String registered =
                RunSection.awaitText(
                        driver,
                        "percolator-register-status",
                        text -> !text.startsWith("Registering"),
                        RunSection.CHECK_BOUND);
        assertTrue(registered.startsWith("Registered Percolator 3.09.0"), registered);
        assertEquals(
                "Percolator 3.09.0 (registered local binary) runs in the next search: the resolved"
                        + " default.",
                RunSection.awaitText(
                        driver,
                        "percolator-selection",
                        text -> text.contains("3.09.0"),
                        RunSection.CHECK_BOUND));
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        Path first = onlyRun(0);
        assertAll(
                () ->
                        assertEquals(
                                "The run succeeded: run " + idOf(first) + " in " + first + ".",
                                outcome),
                () -> assertEquals(2, cometLaunches(), "one Comet per spectrum file"),
                () -> assertFalse(Files.exists(first.resolve("outputs/percolator/pout.xml"))));
    }

    @Test
    @Order(2)
    @DisplayName(
            "gate 3: before any attempt, the explanation is shown, every conversion control is"
                    + " disabled, and the action is the resolution's label; nothing is launched")
    void gate3() {
        Path first = onlyRun(0);
        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, idOf(first), ResultsSection.OPEN_BOUND);
        driver.clickOn("nav-limelight");
        String explanation =
                RunSection.awaitText(
                        driver,
                        "limelight-compatibility",
                        text -> !text.startsWith("Checking"),
                        RunSection.CHECK_BOUND);
        String action =
                RunSection.awaitText(
                        driver,
                        "limelight-action",
                        text -> !text.isEmpty(),
                        RunSection.CHECK_BOUND);
        CompatibilityAction resolved =
                CompatibilityAction.forStage(
                        percolatorOffers(), DownstreamStage.LIMELIGHT_CONVERSION);
        assertAll(
                "the section before anything is attempted",
                () ->
                        assertTrue(
                                explanation.contains("3.09.0 (registered local binary)"),
                                explanation),
                () -> assertTrue(explanation.contains("XML"), explanation),
                () -> assertEquals(explanation, driver.textOf("limelight-convert-refusal")),
                () ->
                        assertTrue(
                                driver.textOf("limelight-refusals")
                                        .startsWith("PERCOLATOR_XML: " + explanation),
                                driver.textOf("limelight-refusals")),
                () -> assertEquals(resolved.label(), action),
                () -> assertEquals(RERUN_LABEL, action),
                () -> assertTrue(resolved.installFirst(), "3.07.1 is not installed yet"),
                () ->
                        assertEquals(
                                resolved.explanation(),
                                driver.textOf("limelight-action-explanation")),
                () -> assertTrue(driver.isVisible("limelight-action")),
                () -> assertFalse(RunSection.isDisabled(driver, "limelight-action")),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-convert")),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-q-cutoff")),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-output")),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-import-decoys")),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-decoy-prefix")),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-open-mod")));
        driver.clickOn("limelight-convert");
        assertAll(
                "nothing was attempted",
                () -> assertEquals(List.of(), converterLaunches()),
                () -> assertEquals(List.of(), served.requested(), "nothing fetched"),
                () -> assertFalse(Files.exists(cacheRoot.resolve("tools/percolator"))),
                () -> assertFalse(Files.exists(cacheRoot.resolve("tools/limelight-converter"))),
                () -> assertFalse(Files.exists(first.resolve("outputs/limelight"))),
                () -> assertEquals(1, onlyRuns().size()));
    }

    @Test
    @Order(3)
    @DisplayName(
            "R-LL-02: where no XML-capable build can be obtained, registration is offered -- no"
                    + " rerun -- and it starts the Percolator section's registration")
    void noManagedBuild() {
        tools.hideManagedPercolator(true);
        driver.clickOn("nav-limelight");
        driver.clickOn("limelight-refresh");
        String action =
                RunSection.awaitText(
                        driver,
                        "limelight-action",
                        text -> text.equals(REGISTER_LABEL),
                        RunSection.CHECK_BOUND);
        CompatibilityAction resolved =
                CompatibilityAction.forStage(
                        percolatorOffers(), DownstreamStage.LIMELIGHT_CONVERSION);
        String explanation = driver.textOf("limelight-action-explanation");
        assertAll(
                () -> assertEquals(resolved.label(), action),
                () -> assertFalse(resolved.offersRerun()),
                () ->
                        assertTrue(
                                explanation.startsWith(
                                        "No managed Percolator build that can write XML_OUTPUT"
                                                + " exists for this platform"),
                                explanation),
                () -> assertEquals(resolved.explanation(), explanation),
                () ->
                        assertFalse(
                                textsInSection().stream()
                                        .anyMatch(text -> text.startsWith("Rerun Percolator")),
                                () -> "a rerun is offered somewhere: " + textsInSection()),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-convert")));
        driver.clickOn("limelight-action");
        assertAll(
                () ->
                        assertTrue(
                                app.chooser()
                                        .asked()
                                        .contains("file:a Percolator executable to register"),
                                () -> "the chooser was asked: " + app.chooser().asked()),
                () ->
                        assertEquals(
                                "No Percolator binary was registered. The Percolator section's"
                                        + " registration status says why.",
                                driver.textOf("limelight-action-outcome")),
                () ->
                        assertEquals(
                                "No file was chosen, so nothing was registered.",
                                RunSection.awaitText(
                                        driver,
                                        "percolator-register-status",
                                        text -> text.startsWith("No file"),
                                        RunSection.CHECK_BOUND)),
                () -> assertEquals(List.of(), served.requested(), "nothing fetched"));
        tools.hideManagedPercolator(false);
        driver.clickOn("limelight-refresh");
        RunSection.awaitText(
                driver,
                "limelight-action",
                text -> text.equals(RERUN_LABEL),
                RunSection.CHECK_BOUND);
    }

    @Test
    @Order(4)
    @DisplayName(
            "gate 4: the rerun installs 3.07.1 only now, reruns Percolator alone in a new run, and"
                    + " that run converts to valid Limelight XML; provenance shows both"
                    + " Percolator executions")
    void gate4() throws IOException {
        Path first = onlyRun(0);
        String firstProvenance = RealSearch.sha256(first.resolve("provenance/provenance.json"));
        long cometBefore = cometLaunches();
        driver.clickOn("nav-limelight");
        assertFalse(
                RunSection.isDisabled(driver, "limelight-action"),
                () ->
                        "the action is disabled: "
                                + driver.textOf("limelight-action-outcome")
                                + " | "
                                + driver.textOf("limelight-convert-refusal"));
        driver.clickOn("limelight-action");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "limelight-action-outcome",
                        text ->
                                text.startsWith("The Percolator rerun ")
                                        || text.startsWith("Percolator could not")
                                        || text.startsWith("Percolator was installed, but")
                                        || text.startsWith("The Percolator install"),
                        BOUND);
        Path second = onlyRun(1);
        assertTrue(
                outcome.startsWith(
                        "The Percolator rerun succeeded: run "
                                + idOf(second)
                                + " in "
                                + second
                                + ", rescoring the merged PIN of run "
                                + idOf(first)
                                + "."),
                outcome);
        RunSection.awaitText(
                driver,
                "limelight-run",
                text -> text.startsWith("Run " + idOf(second) + ","),
                RunSection.CHECK_BOUND);
        String compatibility =
                RunSection.awaitText(
                        driver,
                        "limelight-compatibility",
                        text -> text.startsWith("Run " + idOf(second) + "'s"),
                        RunSection.CHECK_BOUND);
        assertAll(
                "the derived run is the Results section's run, and convertible",
                () ->
                        assertEquals(
                                "Run "
                                        + idOf(second)
                                        + "'s Percolator 3.07.1 wrote the Percolator XML the"
                                        + " Limelight converter reads, so the run can be"
                                        + " converted.",
                                compatibility),
                () -> assertTrue(driver.textOf("results-readiness").contains(idOf(second))),
                () -> assertFalse(driver.isVisible("limelight-action")),
                () -> assertEquals(cometBefore, cometLaunches(), "Comet was not run again"),
                () ->
                        assertTrue(
                                served.requested().contains(percolator.url()),
                                "3.07.1 was installed for the rerun: " + served.requested()));

        driver.clickOn("limelight-convert");
        String converted =
                RunSection.awaitText(
                        driver,
                        "limelight-outcome",
                        text ->
                                text.startsWith("Run " + idOf(second) + " was converted")
                                        || text.startsWith("Nothing was converted")
                                        || text.startsWith("The converter's output was not kept")
                                        || text.startsWith("The Limelight converter could not"),
                        BOUND);
        Path file = second.resolve("outputs/limelight/limelight-1.xml");
        int rows = LimelightFiles.targetPsmRows(second);
        Path jar = installed(ToolName.LIMELIGHT_CONVERTER);
        Path percolator3071 = installed(ToolName.PERCOLATOR);
        assertAll(
                "the derived run converted",
                () ->
                        assertTrue(
                                converted.startsWith(
                                        "Run "
                                                + idOf(second)
                                                + " was converted to valid Limelight XML: "
                                                + file),
                                converted),
                () -> LimelightFiles.assertValid(file, jar),
                () -> assertEquals(rows, LimelightFiles.read(file).psms()),
                () -> assertEquals(Integer.toString(rows), driver.textOf("limelight-result-psms")),
                () -> LimelightFiles.assertSha256(file, driver.textOf("limelight-result-sha256")),
                () ->
                        assertEquals(
                                driver.textOf("limelight-result-sha256"),
                                LimelightFiles.events(second, "limelight.converted")
                                        .get(0)
                                        .get("output.sha256")));

        ProvenanceManifest before =
                ManifestReader.readFrom(first.resolve("provenance/provenance.json"));
        ProvenanceManifest after =
                ManifestReader.readFrom(second.resolve("provenance/provenance.json"));
        ToolRecord original = percolatorTool(before);
        ToolRecord rerun = percolatorTool(after);
        List<String> rerunArgv = rerun.execution().command().argv();
        List<LaunchRecorder.Launch> reruns =
                launches.launches().stream()
                        .filter(launch -> launch.argv().get(0).equals(percolator3071.toString()))
                        .filter(launch -> launch.argv().contains("-X"))
                        .toList();
        assertAll(
                "both Percolator executions are recorded",
                () -> assertEquals("3.09.0", original.version()),
                () -> assertEquals(RealPercolators.SHA256_WRAPPER_309, original.hashes().sha256()),
                () -> assertFalse(original.execution().command().argv().contains("-X")),
                () -> assertEquals("3.07.1", rerun.version()),
                () -> assertEquals(RealPercolators.SHA256_3071, rerun.hashes().sha256()),
                () -> assertTrue(rerunArgv.contains("-X"), rerunArgv::toString),
                () -> assertEquals(percolator3071.toString(), rerunArgv.get(0)),
                () -> assertNotEquals(original.execution().command().argv(), rerunArgv),
                () -> assertEquals(1, reruns.size(), "Percolator 3.07.1 ran once, with -X"),
                () -> assertEquals(rerunArgv, reruns.get(0).argv()),
                () -> assertFalse(reruns.get(0).onFxThread()),
                () ->
                        assertEquals(
                                List.of(),
                                after.tools().stream()
                                        .filter(record -> record.name().equals("comet"))
                                        .toList(),
                                "no Comet executed in the rerun"),
                () -> assertEquals(idOf(first), after.settings().get("rerun.source-run-id")),
                () ->
                        assertEquals(
                                firstProvenance,
                                RealSearch.sha256(first.resolve("provenance/provenance.json")),
                                "the original run is untouched"));
    }

    private static List<ToolOffer> percolatorOffers() {
        return tools.offers().stream()
                .filter(offer -> offer.tool() == ToolName.PERCOLATOR)
                .toList();
    }

    private static Path installed(ToolName tool) {
        return managed.offers().stream()
                .filter(offer -> offer.tool() == tool)
                .filter(offer -> offer.installedPath().isPresent())
                .filter(offer -> offer.installedPath().get().startsWith(cacheRoot))
                .map(offer -> offer.installedPath().orElseThrow())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + tool.id() + " is installed"));
    }

    private static List<List<String>> converterLaunches() {
        return launches.launches().stream()
                .map(LaunchRecorder.Launch::argv)
                .filter(argv -> argv.stream().anyMatch(arg -> arg.contains("LimelightXML")))
                .toList();
    }

    private static long cometLaunches() {
        return launches.launches().stream()
                .filter(launch -> launch.argv().get(0).equals(comet.toString()))
                .count();
    }

    /** Every text a labelled control in the Limelight section shows. */
    private static List<String> textsInSection() {
        Parent pane = (Parent) driver.node("limelight-pane");
        return driver.callOnFxThread(
                () -> {
                    List<String> texts = new java.util.ArrayList<>();
                    collect(pane, texts);
                    return texts;
                });
    }

    private static void collect(Node node, List<String> texts) {
        if (node instanceof Labeled labeled && labeled.isVisible() && labeled.getText() != null) {
            texts.add(labeled.getText());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, texts);
            }
        }
    }

    private static ToolRecord percolatorTool(ProvenanceManifest manifest) {
        List<ToolRecord> found =
                manifest.tools().stream().filter(tool -> tool.name().equals("percolator")).toList();
        assertEquals(1, found.size(), () -> "one Percolator record: " + found);
        return found.get(0);
    }

    private static List<Path> onlyRuns() {
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            return runs.sorted().toList();
        } catch (IOException unreadable) {
            throw new AssertionError("the project's runs cannot be listed", unreadable);
        }
    }

    private static Path onlyRun(int position) {
        List<Path> sorted = onlyRuns();
        assertTrue(sorted.size() > position, () -> "the runs: " + sorted);
        return sorted.get(position);
    }

    private static String idOf(Path directory) {
        String name = String.valueOf(directory.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }
}
