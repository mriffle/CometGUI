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

package org.cometgui.app.e2e.tiera;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javafx.application.Application;
import javafx.stage.Stage;
import org.cometgui.app.bootstrap.CometGuiApplication;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.BuildIdentityResource;
import org.cometgui.app.config.PdvDisplay;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.e2e.scenario.CanonicalScenario;
import org.cometgui.app.e2e.scenario.ScenarioLayout;
import org.cometgui.app.e2e.support.EndToEnd;
import org.cometgui.app.e2e.support.FreshHome;
import org.cometgui.app.testing.ProjectXvfb;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.ScriptedChooser;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.cometgui.tools.limelight.fake.LoopbackGuard;
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
import org.testfx.api.FxToolkit;

/**
 * The canonical end-to-end scenario in Tier A, steps 1 to 30 (Phase 14 units 2 and 3): the
 * assembled application, in-process, from a brand-new home and an empty tool cache, driven as a
 * scientist drives it -- through the search, PDV, the Limelight conversion and upload, provenance,
 * and a close and relaunch -- with what it claims proved by the scenario's own oracles. The steps
 * are {@link CanonicalScenario}'s, tier-neutral; this class is Tier A's composition and nothing
 * else.
 *
 * <h2>The composition (design decision P14-4)</h2>
 *
 * <p>The real {@link CometGuiApplication}, over a production {@link ApplicationServices} -- {@link
 * FreshHome#services(Map)}, in which only the environment the application reads is the test's: the
 * fresh home, and {@code DISPLAY} naming the project-local Xvfb ({@link ProjectXvfb}) -- and the
 * production build identity. The file-chooser factory is a {@link ScriptedChooser}. The run setup
 * is {@link RunWiring.Setup#forThisApplication}'s own Tool Manager and project, with one
 * difference, stated here because it is a third test-supplied seam where P14-4 names two: the
 * source of PDV's display environment. It is the product's own {@link PdvDisplay#fromHost} over the
 * application's environment -- so the product finds {@code DISPLAY} through the {@code
 * EnvironmentReader} port, which step 25 asserts -- plus the project-local X11 and font closures'
 * library and fontconfig locations. A real Linux desktop has {@code libX11.so.6} in its system
 * library directories; this build host has no X11 library at all, and the JVM PDV runs on cannot
 * load {@code libawt_xawt.so} without them (measured: {@code UnsatisfiedLinkError ... libX11.so.6:
 * cannot open shared object file}). {@link PdvDisplay#fromHost} passes only {@code DISPLAY} and
 * {@code XAUTHORITY}, so without this seam PDV cannot draw on this host. The Tool Manager, the
 * installer, the process service and the workflow are the product's own; the installer fetches
 * Comet and Percolator through the Tool Manager, and PDV and the Limelight converter on first use,
 * from the URLs the shipped manifest pins, on GitHub. No tool is staged or mirrored, no loopback
 * artefact server is used, and no product class is replaced.
 *
 * <h2>The network</h2>
 *
 * <p>The module's {@link LoopbackGuard} sends every non-loopback connection of this JVM to a
 * sinkhole and fails a test that reached one. This class lets through, while it runs, exactly the
 * hosts the tools' pinned release URLs are served from -- {@code github.com} and the {@code
 * githubusercontent.com} storage hosts its release downloads redirect to -- exactly as {@code
 * UpstreamInstallUiTest} does, and puts the guard back afterwards. Any other host, a Limelight
 * server among them, still lands in the sinkhole and fails the test. Step 27 uploads to the {@code
 * D-007} fake, {@link FakeLimelightServer}, on loopback, with a test key made for the run; the fake
 * reads the Limelight schema from the converter JAR step 26 installed in the fresh home.
 *
 * <h2>Files read outside this module</h2>
 *
 * <p>{@code manifests/tools.json} (the scenario's own reader), and through {@link RealSearch} the
 * {@code D-006} local fixture under {@code scratch/fixture}: both K562 mzML files (checksum
 * verified, then LF-repaired and verified again) and the UniProt human proteome (checksum
 * verified). This is the local fixture, not the CI fixture, whose half of {@code D-006} is open; a
 * missing or changed file fails the test, nothing skips. For the display, {@link ProjectXvfb}'s:
 * {@code tools/x11-bookworm-20260829/}, {@code tools/fontstack-bookworm-20260829/} and {@code
 * scripts/feasibility/gui-spike/xkbcomp-path-shim.c}, compiled with the host's {@code gcc}.
 * Network: the pinned GitHub release URLs, and loopback.
 *
 * <h2>How to run it</h2>
 *
 * <pre>{@code
 * LC_ALL=C.UTF-8 mvn -B -o -pl cometgui-app -am test -Dtest=TierACanonicalScenarioTest \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dcometgui.e2e=true
 * }</pre>
 *
 * <p>The steps are ordered and each builds on the last; once one fails, the rest are skipped with
 * that step named, rather than failing on the state it left. The installs (P14-5) come straight
 * after the application is ready, and the same application then configures and runs the search,
 * draws, converts, uploads and shows the provenance: the only relaunch is step 29's, through the
 * application's own {@code stop()} and a new composition root from the same home (P14-4).
 */
@EndToEnd
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the reference platform: only the linux/x86-64 Comet and Percolator have been"
                        + " executed in this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TierACanonicalScenarioTest {

    /** The hosts the tools' pinned release URLs are served from, and nothing else. */
    private static final String UPSTREAM_HOSTS = "github.com|*.githubusercontent.com";

    /** The project the controlled Limelight endpoint holds. */
    private static final int LIMELIGHT_PROJECT = 7;

    /** The test key the endpoint accepts: a recognisable prefix and 48 characters made per run. */
    private static final String KEY = seed();

    @TempDir private static Path parent;

    private static ProjectXvfb display;

    private static Map<String, String> desktop;

    /** What the product's own {@link PdvDisplay#fromHost} answered, each time PDV asked. */
    private static final List<Map<String, String>> PRODUCT_DISPLAY =
            Collections.synchronizedList(new ArrayList<>());

    private static FakeLimelightServer fake;

    private static String guardedNonProxyHosts;

    private static FreshHome home;

    private static ScriptedChooser chooser;

    private static CanonicalScenario scenario;

    private static Application application;

    private static String failedStep;

    @BeforeAll
    static void prepare() throws IOException {
        assertTrue(
                LoopbackGuard.installed(), "the loopback guard is in place before it is widened");
        guardedNonProxyHosts = System.getProperty("http.nonProxyHosts");
        System.setProperty("http.nonProxyHosts", guardedNonProxyHosts + "|" + UPSTREAM_HOSTS);

        Path root = parent.toRealPath();
        display = ProjectXvfb.start(root.resolve("xvfb"));
        desktop = Map.of(PdvDisplay.DISPLAY, display.display());
        home = FreshHome.create(root, FreshHome.Naming.SPACE_AND_NON_ASCII);
        Path inputs = Files.createDirectories(home.root().resolve("inputs"));
        List<Path> spectra = RealSearch.spectra(inputs);
        Path database = RealSearch.proteome(inputs.resolve("UP000005640_9606.fasta"));
        chooser = new ScriptedChooser();
        scenario =
                new CanonicalScenario(
                        ScenarioLayout.defaultsUnder(
                                home,
                                inputs,
                                spectra,
                                database,
                                RealSearch.repositoryRoot().resolve("manifests/tools.json"),
                                Files.createDirectories(root.resolve("oracle-work"))),
                        new ScriptedChooserScript(chooser),
                        new ProcessServiceOracle());
    }

    private static String seed() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder seed = new StringBuilder("CGE2E-");
        for (int index = 0; index < 48; index++) {
            seed.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return seed.toString();
    }

    @AfterAll
    static void stop() throws TimeoutException, IOException {
        try {
            if (application != null) {
                FxToolkit.cleanupApplication(application);
            }
        } finally {
            if (guardedNonProxyHosts != null) {
                System.setProperty("http.nonProxyHosts", guardedNonProxyHosts);
            }
            if (fake != null) {
                fake.close();
            }
            if (display != null) {
                display.close();
            }
            if (scenario != null) {
                for (String observed : scenario.observations()) {
                    System.out.println("[canonical tier A] " + observed);
                }
            }
        }
    }

    /** One step of the scenario, which every later step builds on. */
    @FunctionalInterface
    private interface Step {
        void run() throws Exception;
    }

    private static void step(String name, Step step) throws Exception {
        assumeTrue(
                failedStep == null, () -> "not run: an earlier step failed (" + failedStep + ")");
        try {
            step.run();
        } catch (Exception | Error failed) {
            failedStep = name;
            throw failed;
        }
    }

    @Test
    @Order(1)
    @DisplayName("steps 1-2: a brand-new home and application data directory, no tool present")
    void freshHome() throws Exception {
        step("1-2", scenario::freshHomeHoldsNoTool);
    }

    @Test
    @Order(2)
    @DisplayName("steps 3-4: the assembled application starts and reaches its ready state")
    void launch() throws Exception {
        step(
                "3-4",
                () -> {
                    start();
                    scenario.applicationIsReady();
                });
    }

    /**
     * Starts the real application over a new production composition root for the fresh home and
     * attaches the scenario to it. Step 29's relaunch calls it again (P14-4: a new {@code
     * CometGuiApplication} over a new composition root, built from the same home, in the same JVM).
     */
    private static void start() throws TimeoutException {
        ApplicationServices services = home.services(desktop);
        RunWiring.Setup production = RunWiring.Setup.forThisApplication(services);
        RunWiring.Setup setup =
                new RunWiring.Setup(
                        production.tools(),
                        production.projectDirectory(),
                        TierACanonicalScenarioTest::pdvEnvironment);
        Stage primary = FxToolkit.registerPrimaryStage();
        application =
                FxToolkit.setupApplication(
                        () ->
                                new CometGuiApplication(
                                        services,
                                        new BoundedMessageLog(),
                                        BuildIdentityResource::load,
                                        owner -> chooser,
                                        setup));
        scenario.attach(new RobotFxUiDriver(RunningApplication.showing(primary)));
    }

    /**
     * What PDV is given to reach a display: the product's own {@link PdvDisplay#fromHost} over the
     * application's environment -- which answers {@code DISPLAY} with the project's Xvfb because
     * the fresh home's environment reports it -- and, beside it, the project-local X11 and font
     * closures' library and fontconfig locations ({@link ProjectXvfb#clientEnvironment()}), which a
     * real Linux desktop has in its system library directories and this build host does not have at
     * all: without them the JVM PDV runs on cannot load {@code libawt_xawt.so} ({@code libX11.so.6}
     * not found). Nothing here replaces a variable the product chose.
     */
    private static Map<String, String> pdvEnvironment(EnvironmentReader host) {
        Map<String, String> chosen = PdvDisplay.fromHost(host);
        PRODUCT_DISPLAY.add(chosen);
        Map<String, String> environment = new LinkedHashMap<>(display.clientEnvironment());
        environment.remove(PdvDisplay.DISPLAY);
        environment.putAll(chosen);
        return Map.copyOf(environment);
    }

    @Test
    @Order(3)
    @DisplayName(
            "steps 14-15: Comet and Percolator installed from the empty cache through the Tool"
                    + " Manager (P14-5): downloaded, checksum-verified, probed; the Run section"
                    + " learns of them and the default project appears, with no run")
    void install() throws Exception {
        step("14-15", scenario::installCometAndPercolatorFromTheToolManager);
    }

    @Test
    @Order(4)
    @DisplayName(
            "steps 5-7: the default project, no run in it yet; two spectrum files and the FASTA"
                    + " through the controls")
    void inputs() throws Exception {
        step("5-7", scenario::chooseSpectraAndDatabase);
    }

    @Test
    @Order(5)
    @DisplayName("step 8: the default Comet, 2026.03.0")
    void defaultComet() throws Exception {
        step("8", scenario::defaultCometIsSelected);
    }

    @Test
    @Order(6)
    @DisplayName("step 9: the default XML-capable Percolator, resolved from the shipped manifest")
    void defaultPercolator() throws Exception {
        step("9", scenario::defaultXmlCapablePercolatorIsSelected);
    }

    @Test
    @Order(7)
    @DisplayName(
            "steps 10-12: a precursor and a variable modification changed; the summary says so")
    void parameters() throws Exception {
        step("10-12", scenario::changeParameters);
    }

    @Test
    @Order(8)
    @DisplayName("step 13: both q filters show 0.01")
    void defaultFilters() throws Exception {
        step("13", scenario::bothFiltersAreOnePercent);
    }

    @Test
    @Order(9)
    @DisplayName("steps 14, 16: Run, waiting on observable workflow state; the run succeeds")
    void run() throws Exception {
        step("14,16", scenario::run);
    }

    @Test
    @Order(10)
    @DisplayName("step 17: comet.params holds exactly the GUI-selected values, outputs forced on")
    void cometParams() throws Exception {
        step("17", scenario::cometParamsHoldExactlyTheChosenValues);
    }

    @Test
    @Order(11)
    @DisplayName(
            "step 18: one Comet per file, distinct -N, nothing beside the inputs, outputs parse")
    void cometOutputs() throws Exception {
        step("18", scenario::cometRanOncePerFileAndWroteNothingBesideTheInputs);
    }

    @Test
    @Order(12)
    @DisplayName("step 19: the merged PIN has one header and the summed rows of its sources")
    void mergedPin() throws Exception {
        step("19", scenario::mergedPinHasOneHeaderAndEveryRow);
    }

    @Test
    @Order(13)
    @DisplayName("step 20: Percolator exited 0; PSM, peptide, weights and XML artefacts parse")
    void percolatorArtefacts() throws Exception {
        step("20", scenario::percolatorRanAndItsArtefactsParse);
    }

    @Test
    @Order(14)
    @DisplayName("step 21: PSM and peptide counts at q <= 0.01 equal the raw files' own counts")
    void countsAtOnePercent() throws Exception {
        step("21", scenario::countsAtOnePercentMatchTheRawFiles);
    }

    @Test
    @Order(15)
    @DisplayName("step 22: the PSM filter at 0.005 changes only the PSM count, to the raw count")
    void psmFilter() throws Exception {
        step("22", scenario::psmFilterAtHalfAPercentChangesOnlyThePsms);
    }

    @Test
    @Order(16)
    @DisplayName("step 23: the peptide filter at 0.02 gives the raw file's peptide count")
    void peptideFilter() throws Exception {
        step("23", scenario::peptideFilterAtTwoPercent);
    }

    @Test
    @Order(17)
    @DisplayName("steps 21-23: a cutoff equal to a q-value in the file passes (<=, never <)")
    void inclusiveCutoff() throws Exception {
        step("21-23 boundary", scenario::aCutoffEqualToAQValuePasses);
    }

    @Test
    @Order(18)
    @DisplayName("step 24: the learned feature weights and their ranking equal the artefact's")
    void learnedWeights() throws Exception {
        step("24", scenario::learnedWeightsMatchTheArtefact);
    }

    @Test
    @Order(19)
    @DisplayName(
            "step 25: PDV, absent before, installs on first use and draws the figure of a PSM"
                    + " the test chose from the raw table")
    void pdv() throws Exception {
        step(
                "25",
                () -> {
                    scenario.pdvDrawsAKnownPsm();
                    assertTrue(
                            !PRODUCT_DISPLAY.isEmpty()
                                    && PRODUCT_DISPLAY.stream()
                                            .allMatch(
                                                    chosen ->
                                                            chosen.equals(
                                                                    Map.of(
                                                                            PdvDisplay.DISPLAY,
                                                                            display.display()))),
                            () ->
                                    "the product read DISPLAY from the fresh home's environment: "
                                            + PRODUCT_DISPLAY);
                });
    }

    @Test
    @Order(20)
    @DisplayName(
            "step 26: the real converter, absent before, installs on first use and converts; the"
                    + " XML is valid against the JAR's schema and holds the raw table's PSMs")
    void limelightConversion() throws Exception {
        step("26", scenario::limelightConverts);
    }

    @Test
    @Order(21)
    @DisplayName(
            "step 27: the upload to the loopback fake (D-007) with a test key; the fake received"
                    + " exactly the converted file, valid, and the screen shows its tracking id")
    void upload() throws Exception {
        step(
                "27",
                () -> {
                    fake =
                            FakeLimelightServer.start(
                                    KEY, LIMELIGHT_PROJECT, scenario.installedConverter());
                    scenario.uploadsToTheEndpoint(
                            new FakeLimelightEndpoint(fake, LIMELIGHT_PROJECT, KEY));
                });
    }

    @Test
    @Order(22)
    @DisplayName(
            "step 28: provenance recomputed from disk -- every declared file's MD5 and SHA-256,"
                    + " every tool binary and JAR, versions, argument arrays -- shown as recorded,"
                    + " and the key in no file")
    void provenance() throws Exception {
        step("28", scenario::provenanceHoldsAgainstTheDisk);
    }

    @Test
    @Order(23)
    @DisplayName(
            "steps 29-30: closed through its own stop(), relaunched over a new composition root"
                    + " from the same home; the project, run, results, filters, tools and"
                    + " provenance are coherent")
    void relaunch() throws Exception {
        step(
                "29-30",
                () -> {
                    scenario.recordWhatIsShownBeforeTheClose();
                    FxToolkit.cleanupApplication(application);
                    application = null;
                    start();
                    scenario.theRelaunchedApplicationIsCoherent();
                });
    }
}
