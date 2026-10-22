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
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.application.Application;
import javafx.stage.Stage;
import org.cometgui.app.bootstrap.CometGuiApplication;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.BuildIdentityResource;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.e2e.scenario.CanonicalScenario;
import org.cometgui.app.e2e.scenario.ScenarioLayout;
import org.cometgui.app.e2e.support.EndToEnd;
import org.cometgui.app.e2e.support.FreshHome;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.ScriptedChooser;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.domain.log.BoundedMessageLog;
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
 * The canonical end-to-end scenario in Tier A, steps 1 to 24 (Phase 14 unit 2): the assembled
 * application, in-process, from a brand-new home and an empty tool cache, driven as a scientist
 * drives it, with what it claims proved by the scenario's own oracles. The steps are {@link
 * CanonicalScenario}'s, tier-neutral; this class is Tier A's composition and nothing else.
 *
 * <h2>The composition (design decision P14-4)</h2>
 *
 * <p>The real {@link CometGuiApplication}, over {@link RunWiring.Setup#forThisApplication} and a
 * production {@link ApplicationServices} -- {@link FreshHome#services()}, in which only the
 * environment the application reads is the fresh home's -- and the production build identity.
 * Exactly one other seam is filled: the file-chooser factory, with a {@link ScriptedChooser}. The
 * Tool Manager, the installer, the process service and the workflow are the product's own; the
 * installer fetches Comet and Percolator from the URLs the shipped manifest pins, on GitHub. No
 * tool is staged or mirrored, no loopback artefact server is used, and no product class is
 * replaced.
 *
 * <h2>The network</h2>
 *
 * <p>The module's {@link LoopbackGuard} sends every non-loopback connection of this JVM to a
 * sinkhole and fails a test that reached one. This class lets through, while it runs, exactly the
 * hosts the tools' pinned release URLs are served from -- {@code github.com} and the {@code
 * githubusercontent.com} storage hosts its release downloads redirect to -- exactly as {@code
 * UpstreamInstallUiTest} does, and puts the guard back afterwards. Any other host, a Limelight
 * server among them, still lands in the sinkhole and fails the test.
 *
 * <h2>Files read outside this module</h2>
 *
 * <p>{@code manifests/tools.json} (the scenario's own reader), and through {@link RealSearch} the
 * {@code D-006} local fixture under {@code scratch/fixture}: both K562 mzML files (checksum
 * verified, then LF-repaired and verified again) and the UniProt human proteome (checksum
 * verified). This is the local fixture, not the CI fixture, whose half of {@code D-006} is open; a
 * missing or changed file fails the test, nothing skips. Network: the pinned GitHub release URLs.
 *
 * <h2>How to run it</h2>
 *
 * <pre>{@code
 * LC_ALL=C.UTF-8 mvn -B -o -pl cometgui-app test -Dtest=TierACanonicalScenarioTest \
 *     -Dcometgui.e2e=true
 * }</pre>
 *
 * <p>The steps are ordered and each builds on the last; once one fails, the rest are skipped with
 * that step named, rather than failing on the state it left. The installs (P14-5) come straight
 * after the application is ready and are followed by a relaunch over the same home: the sections do
 * not learn of an install the running application made (a product defect, described in {@link
 * CanonicalScenario}), and the relaunch is how the scenario proceeds past it until it is fixed.
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

    @TempDir private static Path parent;

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

    @AfterAll
    static void stop() throws TimeoutException {
        try {
            if (application != null) {
                FxToolkit.cleanupApplication(application);
            }
        } finally {
            if (guardedNonProxyHosts != null) {
                System.setProperty("http.nonProxyHosts", guardedNonProxyHosts);
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
     * Starts the real application over a new production composition root for the fresh home -- the
     * first time, and again for the relaunch (P14-4: a new {@code CometGuiApplication} over a new
     * composition root, built from the same home, in the same JVM) -- and attaches the scenario to
     * it.
     */
    private static void start() throws TimeoutException {
        ApplicationServices services = home.services();
        Stage primary = FxToolkit.registerPrimaryStage();
        application =
                FxToolkit.setupApplication(
                        () ->
                                new CometGuiApplication(
                                        services,
                                        new BoundedMessageLog(),
                                        BuildIdentityResource::load,
                                        owner -> chooser,
                                        RunWiring.Setup.forThisApplication(services)));
        scenario.attach(new RobotFxUiDriver(RunningApplication.showing(primary)));
    }

    @Test
    @Order(3)
    @DisplayName(
            "steps 14-15: Comet and Percolator installed from the empty cache through the Tool"
                    + " Manager (P14-5): downloaded, checksum-verified, probed")
    void install() throws Exception {
        step("14-15", scenario::installCometAndPercolatorFromTheToolManager);
    }

    @Test
    @Order(4)
    @DisplayName(
            "after the installs, the application closed through its own stop() and relaunched over"
                    + " the same home (the workaround for the sections not learning of an install)")
    void relaunch() throws Exception {
        step(
                "relaunch",
                () -> {
                    FxToolkit.cleanupApplication(application);
                    application = null;
                    start();
                    scenario.relaunchedApplicationSeesTheInstalledTools();
                });
    }

    @Test
    @Order(5)
    @DisplayName("steps 5-7: no project yet; two spectrum files and the FASTA through the controls")
    void inputs() throws Exception {
        step("5-7", scenario::chooseSpectraAndDatabase);
    }

    @Test
    @Order(6)
    @DisplayName("step 8: the default Comet, 2026.03.0")
    void defaultComet() throws Exception {
        step("8", scenario::defaultCometIsSelected);
    }

    @Test
    @Order(7)
    @DisplayName("step 9: the default XML-capable Percolator, resolved from the shipped manifest")
    void defaultPercolator() throws Exception {
        step("9", scenario::defaultXmlCapablePercolatorIsSelected);
    }

    @Test
    @Order(8)
    @DisplayName(
            "steps 10-12: a precursor and a variable modification changed; the summary says so")
    void parameters() throws Exception {
        step("10-12", scenario::changeParameters);
    }

    @Test
    @Order(9)
    @DisplayName("step 13: both q filters show 0.01")
    void defaultFilters() throws Exception {
        step("13", scenario::bothFiltersAreOnePercent);
    }

    @Test
    @Order(10)
    @DisplayName("steps 14, 16: Run, waiting on observable workflow state; the run succeeds")
    void run() throws Exception {
        step("14,16", scenario::run);
    }

    @Test
    @Order(11)
    @DisplayName("step 17: comet.params holds exactly the GUI-selected values, outputs forced on")
    void cometParams() throws Exception {
        step("17", scenario::cometParamsHoldExactlyTheChosenValues);
    }

    @Test
    @Order(12)
    @DisplayName(
            "step 18: one Comet per file, distinct -N, nothing beside the inputs, outputs parse")
    void cometOutputs() throws Exception {
        step("18", scenario::cometRanOncePerFileAndWroteNothingBesideTheInputs);
    }

    @Test
    @Order(13)
    @DisplayName("step 19: the merged PIN has one header and the summed rows of its sources")
    void mergedPin() throws Exception {
        step("19", scenario::mergedPinHasOneHeaderAndEveryRow);
    }

    @Test
    @Order(14)
    @DisplayName("step 20: Percolator exited 0; PSM, peptide, weights and XML artefacts parse")
    void percolatorArtefacts() throws Exception {
        step("20", scenario::percolatorRanAndItsArtefactsParse);
    }

    @Test
    @Order(15)
    @DisplayName("step 21: PSM and peptide counts at q <= 0.01 equal the raw files' own counts")
    void countsAtOnePercent() throws Exception {
        step("21", scenario::countsAtOnePercentMatchTheRawFiles);
    }

    @Test
    @Order(16)
    @DisplayName("step 22: the PSM filter at 0.005 changes only the PSM count, to the raw count")
    void psmFilter() throws Exception {
        step("22", scenario::psmFilterAtHalfAPercentChangesOnlyThePsms);
    }

    @Test
    @Order(17)
    @DisplayName("step 23: the peptide filter at 0.02 gives the raw file's peptide count")
    void peptideFilter() throws Exception {
        step("23", scenario::peptideFilterAtTwoPercent);
    }

    @Test
    @Order(18)
    @DisplayName("steps 21-23: a cutoff equal to a q-value in the file passes (<=, never <)")
    void inclusiveCutoff() throws Exception {
        step("21-23 boundary", scenario::aCutoffEqualToAQValuePasses);
    }

    @Test
    @Order(19)
    @DisplayName("step 24: the learned feature weights and their ranking equal the artefact's")
    void learnedWeights() throws Exception {
        step("24", scenario::learnedWeightsMatchTheArtefact);
    }
}
