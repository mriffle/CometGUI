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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.RealSearch.LaunchRecorder;
import org.cometgui.app.testing.SwitchableNetwork;
import org.cometgui.app.uidriver.Await;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.cache.InstallationState;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.provenance.hashing.StreamingHashService;
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
 * The specification's failure paths "network unavailable on first install" and "network unavailable
 * after tools are cached", driven through the launched application (Phase 14, gate item 4).
 *
 * <ol>
 *   <li><strong>No network on the first install.</strong> Comet's Install is pressed in the Tool
 *       Manager with the network down. The row ends saying the attempt did not succeed and why --
 *       the install step, the URL the transfer went to and the refused connection -- the console's
 *       log says the same, Install is offered again; nothing was executed, nothing is in the cache,
 *       and Run still says Comet is not installed.
 *   <li><strong>The network back.</strong> The same Install control, pressed again, installs Comet;
 *       Percolator installs beside it.
 *   <li><strong>No network once both are cached.</strong> The network is taken away again, and a
 *       real search -- the {@code D-006} K562 files and the proteome's first 1000 records, the
 *       Comet and Percolator the Tool Manager just installed -- runs to success from the Run
 *       section, with <strong>not one fetch</strong> after the cut.
 * </ol>
 *
 * <p><strong>The failure in words.</strong> The specification's scenario wants the failure in
 * words. Phase 14 unit 8a found that the row said "the last install attempt did not succeed" and no
 * more, because {@code ManagedToolManager} discarded the installer's exception; since Phase 15 unit
 * 1 the row's failure label carries the step and the exception's own words, and the same line
 * reaches the console's message log. Both are asserted here, word for word.
 *
 * <p>What is real: the application and its sections, {@code ManagedToolManager} as {@link
 * ToolManagerWiring} composes it over an empty cache, the shipped manifest, the real downloader,
 * checksum, extraction and probe (which executes Comet and Percolator), the real workflow. The one
 * substitution is the network: {@link SwitchableNetwork}, whose "up" is {@link
 * LoopbackArtefactServer} serving the mirrored upstream bytes and whose "down" is a refused
 * loopback connection through the real {@code HttpDownloader}.
 *
 * <p>Files read outside this module: {@code scratch/phase05/artefacts} (as {@code
 * ToolManagerInstallUiTest}), {@code scratch/fixture} and {@code manifests/tools.json} (as {@link
 * RealSearch}). Their absence is a failure, never a skip.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the artefacts are read from the project's Linux artefact mirror, and the probe and"
                        + " the search run Linux binaries")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NetworkUnavailableUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-23T00:00:00Z"));

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /** This project's own build host, hand-typed rather than read from the machine. */
    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String COMET_ROW = "tool-row-comet-2026_03_0-1";

    private static final String PERCOLATOR_ROW = "tool-row-percolator-3_07_1-1";

    private static final String FAILED =
            "CometGUI-managed: the last install attempt did not succeed";

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    /** The Run section's reason while the default Comet is not installed. */
    private static final String COMET_MISSING =
            "Comet 2026.03.0 is not installed, and the parameters are for that release: install it"
                    + " in the Tool Manager section, or register a Comet 2026.03.0 already on this"
                    + " computer there.";

    /** The Run section's reason while the default Percolator is not installed. */
    private static final String PERCOLATOR_MISSING =
            "Percolator 3.07.1, the default Percolator for the enabled downstream stages, is not"
                    + " installed: install it in the Tool Manager section, register a local"
                    + " Percolator binary in the Percolator section, or choose an installed build"
                    + " there.";

    /** Where the Tool Manager installs Comet 2026.03.0, relative to the cache root, typed out. */
    private static final String INSTALLED_COMET = "tools/comet/2026.3/linux-x86-64/bin/comet";

    /** How long one install, or one failed attempt, may take. A bound. */
    private static final Duration INSTALL_BOUND = Duration.ofMinutes(2);

    @TempDir private static Path scratch;

    private static LoopbackArtefactServer served;

    private static SwitchableNetwork network;

    private static LaunchRecorder launches;

    private static Path cacheRoot;

    private static Path project;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static BoundedMessageLog messageLog;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ArtefactRecord percolator = ArtefactMirror.record(ToolName.PERCOLATOR, "3.07.1", LINUX);
        served =
                new LoopbackArtefactServer()
                        .serve(
                                comet().url(),
                                ArtefactMirror.artefact("v2026.03.0__comet.linux.exe"))
                        .serve(
                                percolator.url(),
                                ArtefactMirror.artefact(
                                        "rel-3-07-01__percolator-noxml-ubuntu-portable.zip"))
                        .serve(
                                percolator.companions().get(0).url(),
                                ArtefactMirror.artefact(
                                        "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb"));
        network = SwitchableNetwork.downUntilRestored(served);
        launches = new LaunchRecorder(new ProcessService(Clock.systemUTC()));
        cacheRoot = root.resolve("cometgui-data");
        project = root.resolve("project");
        ToolManager manager =
                ToolManagerWiring.toolManager(
                        LINUX,
                        DEBIAN_12,
                        cacheRoot,
                        launches,
                        Clock.systemUTC(),
                        ToolManagerWiring.installThreads(),
                        network);
        messageLog = new BoundedMessageLog();
        app =
                ParameterEditorApp.launch(
                        BUILD, launches, messageLog, new RunWiring.Setup(() -> manager, project));
        driver = new TestFxUiDriver(app.application());
    }

    @AfterAll
    static void stop() throws IOException {
        if (app != null) {
            app.stop();
        }
        if (network != null) {
            network.close();
        }
        if (served != null) {
            served.close();
        }
    }

    private static ArtefactRecord comet() throws IOException {
        return ArtefactMirror.record(ToolName.COMET, "2026.03.0", LINUX);
    }

    @Test
    @Order(1)
    @DisplayName(
            "network unavailable on the first install: the row says the attempt did not succeed"
                    + " and why, the log says the same, Install is offered again, nothing ran, the"
                    + " cache is empty")
    void firstInstallWithoutANetwork() throws IOException, InterruptedException {
        driver.clickOn("nav-tool-manager");
        driver.clickOn(COMET_ROW + "-install");
        String shown =
                Await.until(
                        COMET_ROW + " to say the attempt failed",
                        () -> driver.textOf(COMET_ROW + "-state"),
                        FAILED::equals,
                        INSTALL_BOUND);
        ToolCache cache = new ToolCache(cacheRoot, new StreamingHashService());
        String why =
                "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE, failed: the request to"
                        + " http://127.0.0.1:"
                        + network.deadPort()
                        + comet().url().getPath()
                        + " failed before any body arrived: java.net.ConnectException";
        List<String> logged =
                Await.until(
                        "the console's log to record the failed install",
                        () ->
                                messageLog.snapshot().stream()
                                        .filter(
                                                message ->
                                                        message.severity() == MessageSeverity.ERROR)
                                        .map(LogMessage::text)
                                        .filter(text -> text.startsWith("The install of "))
                                        .toList(),
                        lines -> !lines.isEmpty(),
                        INSTALL_BOUND);
        assertAll(
                () -> assertEquals(FAILED, shown),
                () ->
                        assertEquals(
                                why,
                                driver.textOf(COMET_ROW + "-failure"),
                                "the row says why: the step, the URL the transfer went to, and"
                                        + " the refused connection"),
                () ->
                        assertEquals(
                                List.of("The install of comet 2026.03.0 did not succeed. " + why),
                                logged,
                                "and the console's log says the same, once"),
                () ->
                        assertFalse(
                                RunSection.isDisabled(driver, COMET_ROW + "-install"),
                                "Install is offered again"),
                () ->
                        assertEquals(
                                List.of(comet().url()),
                                network.askedForWhileDown(),
                                "one transfer was tried, at the URL the manifest pins"),
                () ->
                        assertEquals(
                                List.of(),
                                launches.launches(),
                                "nothing was executed: no probe of bytes that never arrived"),
                () ->
                        assertEquals(
                                InstallationState.NOT_PRESENT,
                                cache.verify(comet()).state(),
                                "R-TOOL-04: nothing that could look installed"),
                () ->
                        assertEquals(
                                List.of(),
                                filesUnder(cacheRoot.resolve("tools")),
                                "no file under the tool directories"),
                () -> assertEquals(List.of(), served.requested(), "nothing reached the server"));
        driver.clickOn("nav-run");
        assertEquals(
                "The workflow engine cannot start this search:\n"
                        + COMET_MISSING
                        + "\n"
                        + PERCOLATOR_MISSING,
                RunSection.awaitEngineAnswer(driver),
                "Run still says Comet is not installed");
    }

    @Test
    @Order(2)
    @DisplayName("the network back: the same Install control installs Comet, and Percolator")
    void theNetworkBack() {
        network.restore();
        driver.clickOn("nav-tool-manager");
        install(COMET_ROW);
        install(PERCOLATOR_ROW);
    }

    @Test
    @Order(3)
    @DisplayName(
            "network unavailable after the tools are cached: a real search runs to success from"
                    + " the Run section with not one fetch")
    void cachedToolsRunWithoutANetwork() throws IOException {
        int servedBefore = served.requested().size();
        int downBefore = network.askedForWhileDown().size();
        network.cutOff();

        Path inputs = Files.createDirectories(scratch.toRealPath().resolve("inputs"));
        List<Path> spectra = RealSearch.spectra(inputs);
        Path subset = RealSearch.subset(inputs.resolve("subset.fasta"));
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
        ParameterEditorApp.enter(driver, "ess-num_threads", "4");

        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        Path run = onlyRun();
        String name = String.valueOf(run.getFileName());
        String id = name.substring(name.indexOf('-') + 1);
        assertAll(
                () -> assertEquals("The run succeeded: run " + id + " in " + run + ".", outcome),
                () -> assertEquals("Succeeded", driver.textOf("stage-comet-state")),
                () -> assertEquals("Succeeded", driver.textOf("stage-percolator-state")),
                () ->
                        assertTrue(
                                Files.size(run.resolve("outputs/percolator/psms.tsv")) > 0,
                                "Percolator wrote its PSMs"),
                () ->
                        assertEquals(
                                2,
                                searchesBy(INSTALLED_COMET),
                                "the search ran the Comet the Tool Manager installed, once per"
                                        + " file (its -N searches, not its probes)"),
                () ->
                        assertEquals(
                                List.<URI>of(),
                                network.askedForWhileDown()
                                        .subList(downBefore, network.askedForWhileDown().size()),
                                "not one fetch after the network was cut"),
                () ->
                        assertEquals(
                                servedBefore,
                                served.requested().size(),
                                "and nothing reached the server"));
    }

    /**
     * Presses a row's Install control and waits until the row, read again after the install, says
     * it is installed and where -- as {@code InstallReachesTheSectionsUiTest} does.
     */
    private static void install(String row) {
        driver.clickOn(row + "-install");
        Await.until(
                row + " to be read again as installed, with its installed path",
                () -> driver.textOf(row + "-state") + " | " + driver.textOf(row + "-path"),
                shown -> shown.startsWith("CometGUI-managed: installed | Installed at: "),
                INSTALL_BOUND);
    }

    /** The Comet searches -- launches with a {@code -N} base -- of one executable. */
    private static long searchesBy(String relative) {
        String executable = cacheRoot.resolve(relative).toString();
        return launches.launches().stream()
                .map(LaunchRecorder.Launch::argv)
                .filter(argv -> argv.get(0).equals(executable))
                .filter(argv -> argv.stream().anyMatch(argument -> argument.startsWith("-N")))
                .count();
    }

    private static Path onlyRun() {
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            List<Path> sorted = runs.sorted().toList();
            assertEquals(1, sorted.size(), () -> "the runs: " + sorted);
            return sorted.get(0);
        } catch (IOException unreadable) {
            throw new AssertionError("the project's runs cannot be listed", unreadable);
        }
    }

    private static List<String> filesUnder(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> walked = Files.walk(directory)) {
            return walked.filter(Files::isRegularFile).map(Path::toString).sorted().toList();
        }
    }
}
