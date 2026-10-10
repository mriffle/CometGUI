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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javax.imageio.ImageIO;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.ProjectXvfb;
import org.cometgui.app.testing.RealPercolators;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.TestJson;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.StartedProcess;
import org.cometgui.ui.controls.UiIds;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * PDV installs on first use from an empty tool cache, and the search workflow works while it
 * downloads (phase 11 gate item 1); every PDV launch is in the run's provenance with the JAR's
 * SHA-256 and reported version (gate item 4) -- all through the real interface, with the real PDV
 * 2.7.0 artefact, the real Comet and Percolator, and PDV drawing under the project-local Xvfb.
 *
 * <h2>The Tool Manager</h2>
 *
 * <p>PDV's rows and its install are the product's own {@code ManagedToolManager} over an
 * <strong>empty</strong> tool cache ({@link ToolManagerWiring#toolManager}), fetching the 103 407
 * 417-byte release asset over real HTTP from {@link LoopbackArtefactServer} -- the downloader, its
 * SHA-256, the extraction and the completion marker are all the product's. Comet and Percolator are
 * {@link RealRunUiTest}'s staged builds ({@link InstalledComet}), so that a search can run without
 * downloading them; the composition is {@link StagedSearchManagedPdv}, which routes PDV to the
 * managed half and everything else to the staged half and adds nothing of its own.
 *
 * <h2>"While it downloads" is proved by state, not by timing</h2>
 *
 * <p>The loopback server <strong>holds the PDV transfer still</strong> once {@value #HOLD_AT} bytes
 * have arrived ({@link LoopbackArtefactServer#holdingAfter}): the transfer's own thread waits
 * inside its progress report, so the download is in progress and cannot complete until the test
 * releases it. While it is held, a second real search is configured and run through the Run section
 * to success; then the test checks the transfer is <em>still</em> held, the Visualisation section
 * still says it is downloading, the Tool Manager still offers PDV as being installed and no PDV JAR
 * exists -- and only then releases it. No sleep decides anything.
 *
 * <h2>What is asserted at the end</h2>
 *
 * <ul>
 *   <li>The install completed through the Tool Manager and was verified: the section and the Tool
 *       Manager's row both say installed, at the cache path; the JAR's SHA-256, computed here, is
 *       PDV 2.7.0's.
 *   <li>The figure was drawn for the PSM selected in the Results section: a valid 800 x 400 PNG
 *       with PDV's two ion-series colours, shown in the section, annotated with the selected PSM's
 *       own peptide.
 *   <li>The run's {@code events.log} holds one {@code pdv.launched} event whose SHA-256 equals an
 *       independent hash of the installed JAR and whose version equals the JAR manifest's {@code
 *       Implementation-Version} read here with {@link JarFile}; its argument array is the one the
 *       process service was given; and the process service, wrapped here, saw that event already
 *       written when PDV was started -- and started PDV exactly once, off the JavaFX thread.
 * </ul>
 *
 * <p>Files read outside this module: those of {@link RealSearch}, {@link RealPercolators} and
 * {@link ArtefactMirror} ({@code scratch/fixture/}, {@code scratch/phase05/artefacts/}), and {@link
 * ProjectXvfb}'s ({@code tools/x11-bookworm-20260829/}, {@code tools/fontstack-bookworm-20260829/},
 * {@code scripts/feasibility/gui-spike/xkbcomp-path-shim.c}, compiled with the host's {@code gcc}).
 * Anything missing fails the test, naming it.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project, and the"
                        + " project-local X11 stack PDV draws under is Linux-only")
class PdvFirstUseUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-10T00:00:00Z"));

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String PDV_ZIP = "v2.7.0__PDV-2.7.0.zip";

    /** The release asset's size, typed from the manifest. */
    private static final long PDV_BYTES = 103_407_417L;

    /** The SHA-256 of {@code PDV-2.7.0.jar} inside that asset, as unit 2 measured and pinned it. */
    private static final String PDV_JAR_SHA256 =
            "fc876310b6f9dd95bd9538f212e28d45ac0226ea1615a9c09f2e904d70d07908";

    /** Where the PDV transfer is held still: well into it, far short of its end. */
    private static final long HOLD_AT = 30_000_000L;

    /** The Tool Manager's row for PDV 2.7.0. */
    private static final String PDV_ROW = "pdv-2_7_0-1";

    /** How long the hold, an install after it, and one PDV figure may take. Bounds, not delays. */
    private static final Duration PDV_BOUND = Duration.ofMinutes(6);

    /** How long one click or one round trip to the interface may take while PDV installs. */
    private static final Duration RESPONSIVE_BOUND = Duration.ofSeconds(30);

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    /** PDV's two ion-series colours ({@code PDVCLIMainClass.displayResults}). */
    private static final int ORANGE = 0xFF6600;

    private static final int GREEN = 0x009900;

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static ProjectXvfb display;

    private static LoopbackArtefactServer served;

    private static LoopbackArtefactServer.Hold hold;

    private static PdvLaunchWatch processes;

    private static ToolManager managed;

    private static Path comet;

    private static Path project;

    private static Path cacheRoot;

    private static ArtefactRecord pdv;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        display = ProjectXvfb.start(root.resolve("xvfb"));
        comet = RealSearch.stageComet(root.resolve("bin/comet"));
        ToolOffer percolator = RealPercolators.installed3071(root.resolve("bin/percolator"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealSearch.spectra(inputs);
        Path subset = RealSearch.subset(inputs.resolve("subset.fasta"));
        project = root.resolve("project");
        cacheRoot = root.resolve("cometgui-data");
        pdv = ArtefactMirror.record(ToolName.PDV, "2.7.0", LINUX);
        served = new LoopbackArtefactServer().serve(pdv.url(), ArtefactMirror.artefact(PDV_ZIP));
        hold = served.holdingAfter(pdv.url(), HOLD_AT);
        processes = new PdvLaunchWatch(new ProcessService(Clock.systemUTC()));
        managed =
                ToolManagerWiring.toolManager(
                        LINUX,
                        DEBIAN_12,
                        cacheRoot,
                        processes,
                        Clock.systemUTC(),
                        ToolManagerWiring.installThreads(),
                        served);
        ToolManager tools =
                new StagedSearchManagedPdv(
                        InstalledComet.at(RealSearch.RELEASE, comet).with(percolator), managed);
        Map<String, String> pdvDisplay = display.clientEnvironment();
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        processes,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(() -> tools, project, host -> pdvDisplay));
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
            if (hold != null) {
                hold.release();
            }
            if (app != null) {
                app.stop();
            }
        } finally {
            try {
                if (served != null) {
                    served.close();
                }
            } finally {
                if (display != null) {
                    display.close();
                }
            }
        }
    }

    @Test
    @DisplayName(
            "first use installs PDV from an empty cache; a real search runs to success while the"
                    + " transfer is held mid-way; then PDV is verified, draws the selected PSM, and"
                    + " the launch is in the run's provenance with the JAR's SHA-256 and version")
    void pdvInstallsOnFirstUseWhileASearchRuns() throws IOException, InterruptedException {
        // ---- a run with results, searched before PDV is anywhere -------------------------------
        Path first = search(0);
        String firstId = idOf(first);
        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, firstId, ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        ParameterEditorApp.choose(driver, "results-category", "All rows");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        List<String> psmIds = ResultsSection.column(driver, 0);
        List<String> peptides = ResultsSection.column(driver, 4);
        int chosen = -1;
        for (int row = 0; row < psmIds.size() && chosen < 0; row++) {
            if (psmIds.get(row).endsWith("_1") && !peptides.get(row).contains("[")) {
                chosen = row;
            }
        }
        assertTrue(chosen >= 0, () -> "no unmodified rank-1 PSM on the first page: " + psmIds);
        String psmId = psmIds.get(chosen);
        String peptide = peptides.get(chosen);
        ResultsSection.selectRow(driver, chosen);

        driver.clickOn("nav-visualisation");
        assertAll(
                "the section before first use",
                () ->
                        assertEquals(
                                "PDV 2.7.0 is not installed. It is installed the first time a"
                                        + " spectrum is drawn: a download of 103,407,417 bytes,"
                                        + " which no search waits for.",
                                RunSection.awaitText(
                                        driver,
                                        UiIds.VISUALISATION_PDV_STATUS,
                                        text -> text.startsWith("PDV 2.7.0 is"),
                                        RunSection.CHECK_BOUND)),
                () ->
                        assertTrue(
                                driver.textOf(UiIds.VISUALISATION_RUN)
                                        .startsWith("Run " + firstId + ", created "),
                                driver.textOf(UiIds.VISUALISATION_RUN)),
                () ->
                        assertTrue(
                                driver.textOf(UiIds.VISUALISATION_PSM)
                                        .startsWith("PSM " + psmId + ": peptide " + peptide),
                                driver.textOf(UiIds.VISUALISATION_PSM)),
                () -> assertEquals("", driver.textOf(UiIds.VISUALISATION_DRAW_REFUSAL)),
                () ->
                        assertFalse(
                                Files.exists(cacheRoot.resolve("tools")),
                                "the tool cache is empty: nothing of PDV is installed"),
                () -> assertEquals(List.of(), served.requested(), "nothing fetched yet"));

        // ---- first use: the install starts, and is held mid-transfer ------------------------
        withinResponsiveBound("pressing Draw", () -> driver.clickOn(UiIds.VISUALISATION_DRAW));
        assertTrue(
                hold.awaitHeld(PDV_BOUND.toSeconds()),
                "the PDV transfer did not reach " + HOLD_AT + " bytes");
        RunSection.awaitText(
                driver,
                UiIds.VISUALISATION_PDV_STATUS,
                text -> text.startsWith("Installing PDV 2.7.0: Downloading: "),
                RunSection.CHECK_BOUND);
        withinResponsiveBound(
                "the interface answering while the transfer is held",
                () -> driver.callOnFxThread(() -> Boolean.TRUE));
        assertDownloading("as the install starts");

        // ---- while it is held: a second real search, through the Run section ------------------
        ParameterEditorApp.openEditor(driver);
        enter(driver, "ess-fragment_bin_tol", "1.0005");
        Path second = search(1);
        assertTrue(
                Files.isRegularFile(second.resolve("outputs/percolator/psms.tsv")),
                "the search during the download produced its results");
        assertEquals(4, cometLaunches(), "two Comet invocations per search, two searches");
        driver.clickOn("nav-visualisation");
        assertDownloading("after the search finished");

        // ---- released: verified, installed, drawn -------------------------------------------
        hold.release();
        String outcome =
                RunSection.awaitText(
                        driver,
                        UiIds.VISUALISATION_OUTCOME,
                        text ->
                                text.startsWith("PDV drew ")
                                        || text.startsWith("Nothing was drawn")
                                        || text.startsWith("PDV could not be installed")
                                        || text.startsWith("The PDV install was cancelled"),
                        PDV_BOUND);
        assertTrue(outcome.startsWith("PDV drew the annotated spectrum of PSM " + psmId), outcome);
        assertFalse(hold.abandoned(), "the transfer carried on because the test released it");
        Path jar = cacheRoot.resolve("tools/pdv/2.7/linux-x86-64/PDV-2.7.0/PDV-2.7.0.jar");
        assertAll(
                "installed and verified through the Tool Manager",
                () -> assertTrue(Files.isRegularFile(jar)),
                () -> assertEquals(PDV_JAR_SHA256, RealSearch.sha256(jar)),
                () ->
                        assertEquals(
                                "PDV 2.7.0 is installed: " + jar + ".",
                                driver.textOf(UiIds.VISUALISATION_PDV_STATUS)),
                () ->
                        assertEquals(
                                "CometGUI-managed: installed",
                                RunSection.awaitText(
                                        driver,
                                        UiIds.toolRowState(PDV_ROW),
                                        text -> text.endsWith(": installed"),
                                        RunSection.CHECK_BOUND)),
                () -> assertEquals(List.of(pdv.url()), served.requested(), "fetched once"));

        String stripped = peptide.substring(peptide.indexOf('.') + 1, peptide.lastIndexOf('.'));
        Path figure = onlyFigure(first);
        assertAll(
                "the figure of the selected PSM",
                () ->
                        assertTrue(
                                outcome.contains(" (" + stripped + ") from spectrum "),
                                () -> "PDV annotated " + stripped + ": " + outcome),
                () -> assertTrue(outcome.contains(figure.toString()), outcome),
                () -> assertFigure(figure),
                () -> assertTrue(figureShown(), "the section shows the figure"));

        // ---- gate item 4: the launch in the run's provenance ----------------------------------
        List<Map<String, Object>> launched = launchEvents(first);
        assertEquals(1, launched.size(), "one PDV launch, one event");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) launched.get(0).get("payload");
        List<PdvLaunchWatch.Launch> started = processes.pdvLaunches(jar);
        assertEquals(1, started.size(), "PDV was started once");
        String manifestVersion;
        try (JarFile file = new JarFile(jar.toFile())) {
            manifestVersion =
                    file.getManifest()
                            .getMainAttributes()
                            .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        }
        String version = manifestVersion;
        assertAll(
                "the pdv.launched event",
                () -> assertEquals(RealSearch.sha256(jar), payload.get("pdv.jar.sha256")),
                () -> assertEquals(version, payload.get("tool.version")),
                () -> assertEquals("2.7.0", version),
                () -> assertEquals(jar.toString(), payload.get("pdv.jar.path")),
                () -> assertEquals("cli-figure", payload.get("pdv.launch")),
                () -> assertEquals(firstId, payload.get("run.id")),
                () -> assertEquals(psmId, payload.get("launch.psm-id")),
                () -> assertEquals(started.get(0).argv(), argv(payload)),
                () ->
                        assertTrue(
                                started.get(0).eventAlreadyWritten(),
                                "the event was in the run's log when PDV was started"),
                () -> assertFalse(started.get(0).onFxThread(), "PDV started off the FX thread"),
                () -> assertEquals(List.of(), launchEvents(second), "nothing in the other run"));
    }

    private static boolean figureShown() {
        ImageView shown = (ImageView) driver.node(UiIds.VISUALISATION_FIGURE);
        return driver.callOnFxThread(() -> shown.getImage() != null);
    }

    /**
     * Runs one interaction with the interface and fails if it does not return within {@link
     * #RESPONSIVE_BOUND}: an install that held the JavaFX thread would freeze the click itself, and
     * a test that simply waited inside the click would pass once the freeze ended -- shown at sign
     * off by an injection that blocked the interface for fifteen minutes and was not caught.
     */
    private static void withinResponsiveBound(String what, Runnable interaction) {
        CompletableFuture<Void> done = CompletableFuture.runAsync(interaction);
        try {
            done.get(RESPONSIVE_BOUND.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException frozen) {
            fail(what + " did not return within " + RESPONSIVE_BOUND + ": the interface is frozen");
        } catch (ExecutionException failed) {
            fail(what + " failed", failed.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted during " + what);
        }
    }

    /** The transfer is held, in progress, and nothing of it is installed or launched. */
    private static void assertDownloading(String when) {
        String status = driver.textOf(UiIds.VISUALISATION_PDV_STATUS);
        List<ToolOffer> offered =
                managed.offers().stream().filter(offer -> offer.tool() == ToolName.PDV).toList();
        assertAll(
                "the PDV transfer " + when,
                () -> assertTrue(hold.holding(), "the transfer is standing still at the hold"),
                () ->
                        assertTrue(
                                hold.heldAt() >= HOLD_AT && hold.heldAt() < PDV_BYTES,
                                "held part way: " + hold.heldAt()),
                () ->
                        assertTrue(
                                status.startsWith("Installing PDV 2.7.0: Downloading: ")
                                        && status.endsWith(" of 103,407,417 bytes."),
                                status),
                () -> assertTrue(driver.isVisible(UiIds.VISUALISATION_INSTALL_PROGRESS)),
                () ->
                        assertFalse(
                                RunSection.isDisabled(driver, UiIds.VISUALISATION_INSTALL_CANCEL)),
                () ->
                        assertEquals(
                                List.of(ToolInstallState.INSTALLING),
                                offered.stream().map(ToolOffer::state).toList(),
                                "the Tool Manager offers PDV as being installed"),
                () ->
                        assertFalse(
                                Files.exists(cacheRoot.resolve("tools/pdv")),
                                "no PDV is in the cache yet"),
                () -> assertEquals(List.of(), processes.pdvLaunches(), "PDV was not launched"));
    }

    /** Runs one search through the Run section and returns its run directory. */
    private static Path search(int position) {
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        Path run = onlyRun(position);
        assertEquals("The run succeeded: run " + idOf(run) + " in " + run + ".", outcome);
        return run;
    }

    private static long cometLaunches() {
        return processes.launches().stream()
                .filter(launch -> launch.argv().get(0).equals(comet.toString()))
                .count();
    }

    private static Path onlyRun(int position) {
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            List<Path> sorted = runs.sorted().toList();
            assertEquals(position + 1, sorted.size(), () -> "the runs: " + sorted);
            return sorted.get(position);
        } catch (IOException unreadable) {
            return fail("the project's runs cannot be listed", unreadable);
        }
    }

    private static String idOf(Path run) {
        String name = String.valueOf(run.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }

    private static Path onlyFigure(Path run) throws IOException {
        Path figures = run.resolve("pdv/figures/0001/figures");
        try (Stream<Path> files = Files.list(figures)) {
            List<Path> pngs =
                    files.filter(file -> String.valueOf(file.getFileName()).endsWith(".png"))
                            .toList();
            assertEquals(1, pngs.size(), () -> "one figure in " + figures + ": " + pngs);
            return pngs.get(0);
        }
    }

    /** The figure read with ImageIO here: PDV's size, ink, and both ion-series colours. */
    private static void assertFigure(Path figure) throws IOException {
        BufferedImage image = ImageIO.read(figure.toFile());
        assertTrue(image != null, figure + " is not an image ImageIO reads");
        long nonWhite = 0;
        long orange = 0;
        long green = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                nonWhite += rgb == 0xFFFFFF ? 0 : 1;
                orange += rgb == ORANGE ? 1 : 0;
                green += rgb == GREEN ? 1 : 0;
            }
        }
        long ink = nonWhite;
        long annotatedOrange = orange;
        long annotatedGreen = green;
        assertAll(
                () -> assertEquals(800, image.getWidth()),
                () -> assertEquals(400, image.getHeight()),
                () -> assertTrue(ink >= 3200, "drawn, not blank: " + ink + " non-white pixels"),
                () -> assertTrue(annotatedOrange >= 100, "orange ions: " + annotatedOrange),
                () -> assertTrue(annotatedGreen >= 100, "green ions: " + annotatedGreen));
    }

    /** The {@code pdv.launched} events in a run's event log, read with the test's own JSON. */
    private static List<Map<String, Object>> launchEvents(Path run) throws IOException {
        Path log = run.resolve("provenance/events.log");
        List<Map<String, Object>> events = new ArrayList<>();
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            Map<String, Object> event = TestJson.object(line);
            if ("pdv.launched".equals(event.get("type"))) {
                events.add(event);
            }
        }
        return events;
    }

    private static List<String> argv(Map<String, Object> payload) {
        int count = Integer.parseInt(String.valueOf(payload.get("argv.count")));
        List<String> argv = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            argv.add(String.valueOf(payload.get("argv." + index)));
        }
        return argv;
    }

    /**
     * The application's one process service, wrapped: every launch recorded, and for a PDV launch
     * whether its run's event log already held a {@code pdv.launched} event with exactly this
     * argument array when the process service was asked to start it.
     */
    private static final class PdvLaunchWatch implements ProcessRunner {

        /** One launch. */
        record Launch(List<String> argv, boolean onFxThread, boolean eventAlreadyWritten) {}

        private final ProcessService delegate;

        private final List<Launch> launches = Collections.synchronizedList(new ArrayList<>());

        PdvLaunchWatch(ProcessService delegate) {
            this.delegate = delegate;
        }

        @Override
        public StartedProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            boolean written = false;
            if (command.argv().stream().anyMatch(argument -> argument.endsWith("PDV-2.7.0.jar"))) {
                // a figure runs in <run>/pdv/figures/NNNN
                Path run = up(up(up(command.workingDirectory())));
                List<Map<String, Object>> recorded = launchEvents(run);
                if (!recorded.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> last =
                            (Map<String, Object>) recorded.get(recorded.size() - 1).get("payload");
                    written = argv(last).equals(command.argv());
                }
            }
            launches.add(new Launch(command.argv(), Platform.isFxApplicationThread(), written));
            return delegate.start(command, listener);
        }

        private static Path up(Path path) {
            return Objects.requireNonNull(path.getParent(), () -> path + " has no parent");
        }

        List<Launch> launches() {
            synchronized (launches) {
                return List.copyOf(launches);
            }
        }

        List<Launch> pdvLaunches() {
            return launches().stream()
                    .filter(
                            launch ->
                                    launch.argv().stream()
                                            .anyMatch(argument -> argument.endsWith(".jar")))
                    .toList();
        }

        List<Launch> pdvLaunches(Path jar) {
            return launches().stream()
                    .filter(launch -> launch.argv().contains(jar.toString()))
                    .toList();
        }
    }

    /**
     * The test's Tool Manager: PDV is the product's managed Tool Manager's, over the empty cache;
     * everything else is the staged Comet and Percolator a search runs with.
     */
    private static final class StagedSearchManagedPdv implements ToolManager {

        private final ToolManager staged;

        private final ToolManager managedPdv;

        StagedSearchManagedPdv(ToolManager staged, ToolManager managedPdv) {
            this.staged = staged;
            this.managedPdv = managedPdv;
        }

        @Override
        public List<ToolOffer> offers() {
            List<ToolOffer> offers = new ArrayList<>(staged.offers());
            for (ToolOffer offer : managedPdv.offers()) {
                if (offer.tool() == ToolName.PDV) {
                    offers.add(offer);
                }
            }
            return List.copyOf(offers);
        }

        @Override
        public List<NoManagedBuild> noManagedBuild() {
            return List.of();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            if (tool != ToolName.PDV) {
                throw new IllegalArgumentException("only PDV is installed in this test");
            }
            return managedPdv.install(tool, version, listener);
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable)
                throws ToolRegistrationException {
            return staged.registerLocalBinary(tool, executable);
        }
    }
}
