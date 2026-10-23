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

package org.cometgui.app.e2e.tierb;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.cometgui.app.e2e.scenario.CanonicalScenario;
import org.cometgui.app.e2e.support.EndToEnd;
import org.cometgui.app.e2e.tiera.FakeLimelightEndpoint;
import org.cometgui.app.e2e.tiera.ProcessServiceOracle;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
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
 * The canonical end-to-end scenario in Tier B (Phase 14 unit 7; gate item 2; {@code AC-TST-07}):
 * the packaged artefact, installed, launched as a separate process and driven through the test-only
 * loopback bridge -- the <em>same</em> tier-neutral steps as {@code TierACanonicalScenarioTest},
 * {@link CanonicalScenario}'s, with the composition {@link TierBApplication}'s. Its job is the
 * specification's: prove that packaging did not break the product -- the bundled runtime starts,
 * tool download works from the installed layout, a complete run succeeds, provenance is written --
 * and it runs all thirty steps, with these Tier B invariants on top of the scenario's own:
 *
 * <ul>
 *   <li><strong>steps 3-4</strong>: the launched process reports a UTF-8 {@code sun.jnu.encoding},
 *       the installed runtime as {@code java.home}, the installed launcher, the fresh home and no
 *       {@code JAVA_HOME} ({@link TierBApplication#start()});
 *   <li><strong>steps 14-15</strong>: Comet and Percolator downloaded and installed by the packaged
 *       application into the fresh home's tool cache (the scenario's own check of each row's path),
 *       and nothing written into the install tree or the launch directory;
 *   <li><strong>step 25</strong>: PDV installed on first use from the installed layout, and
 *       launched with the installed runtime's {@code bin/java} -- see "What is not proved" below;
 *   <li><strong>step 26</strong>: the converter run with the installed runtime's {@code bin/java},
 *       by the run's own {@code limelight.converted} record ({@code converter.java} and the
 *       argument array's first element);
 *   <li><strong>steps 29-30</strong>: a real process restart -- the window closed by the window
 *       manager's message, exit 0, reaped; a new process with a new pid over the same home;
 *   <li><strong>last</strong>: the install tree, snapshotted before the first launch, unchanged by
 *       bytes, mtime and inode after everything; every launch directory empty.
 * </ul>
 *
 * <h2>What is not proved here: that PDV draws (Phase 14 E6)</h2>
 *
 * <p>The product gives PDV its own environment -- the application's {@code DISPLAY} (and {@code
 * XAUTHORITY} where the desktop has one), a run-owned {@code HOME}, {@code LANG} -- and nothing
 * else ({@code PdvDisplay.fromHost}, P11-17). This build host has no X11 client library ({@code
 * libX11.so.6}) in any system library directory -- the packaged application itself reaches the
 * project Xvfb only through the launcher's {@code LD_LIBRARY_PATH}, which stands in for a desktop's
 * system libraries -- so the JVM PDV runs on cannot load {@code libawt_xawt.so} and draws nothing.
 * Tier A gives PDV the project closure's library path through its {@code RunWiring.Setup} seam; a
 * packaged application has no such seam, and giving PDV the path by any test-only route into the
 * product would test a different product. So on this host step 25 asserts what is true -- PDV
 * absent before, installed on first use into the fresh home's cache by the installed application,
 * its {@code pdv.launched} event naming the pinned JAR, the installed runtime's {@code bin/java} as
 * the launcher, exactly the product's own environment for it -- and then that PDV failed <em>for
 * that reason and no other</em> ({@link PdvOnThisHost}): the outcome says it exited 1 having drawn
 * nothing, in AWT's native-library loading, and the host's {@code ldd} on the installed runtime's
 * {@code libawt_xawt.so}, in PDV's environment, finds no {@code libX11.so.6}. On a host whose
 * system library directories hold {@code libX11.so.6} the same step instead requires the figure,
 * exactly as Tier A does. That the packaged product draws the figure on a real desktop is therefore
 * <strong>not shown on this host</strong>; steps 28 and 30 still hold PDV's JAR and its {@code
 * pdv.launched} event to the disk.
 *
 * <h2>The network</h2>
 *
 * <p>The packaged application downloads Comet, Percolator, PDV and the converter itself, from the
 * URLs the shipped manifest pins, on GitHub. This test JVM reaches only loopback: the bridge and
 * the {@code D-007} fake ({@link FakeLimelightServer}), which the packaged application uploads to.
 *
 * <h2>Files read outside this module</h2>
 *
 * <p>Those of {@link TierBApplication}; nothing else -- the fake reads the Limelight schema from
 * the converter JAR the packaged application installed in the fresh home.
 *
 * <h2>How to run it</h2>
 *
 * <pre>{@code
 * bash scripts/package/build-app-image.sh
 * LC_ALL=C.UTF-8 mvn -B -o -pl cometgui-app -am test -Dtest=TierBCanonicalScenarioTest \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dcometgui.e2e=true
 * }</pre>
 *
 * <p>The steps are ordered and each builds on the last; once one fails, the rest are skipped with
 * that step named.
 */
@EndToEnd
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the reference platform: the Linux app-image is the only packaged artefact built"
                        + " and executed in this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TierBCanonicalScenarioTest {

    /** The project the controlled Limelight endpoint holds. */
    private static final int LIMELIGHT_PROJECT = 7;

    /** The test key the endpoint accepts: a recognisable prefix and 48 characters made per run. */
    private static final String KEY = seed();

    @TempDir private static Path parent;

    private static TierBApplication tierB;

    private static FakeLimelightServer fake;

    private static CanonicalScenario scenario;

    private static String failedStep;

    @BeforeAll
    static void prepare() throws IOException {
        tierB = TierBApplication.prepare(parent);
        scenario =
                new CanonicalScenario(
                        tierB.layout(), tierB.chooserScript(), new ProcessServiceOracle());
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
            if (fake != null) {
                fake.close();
            }
        } finally {
            try {
                if (tierB != null) {
                    for (Map<String, String> jvm : tierB.reported()) {
                        System.out.println("[canonical tier B] launched JVM: " + jvm);
                    }
                    tierB.close();
                }
            } finally {
                if (scenario != null) {
                    for (String observed : scenario.observations()) {
                        System.out.println("[canonical tier B] " + observed);
                    }
                }
            }
        }
    }

    /** One step of the scenario, which every later step builds on. */
    @FunctionalInterface
    private interface Step {
        void run() throws IOException, InterruptedException, TimeoutException;
    }

    private static void step(String name, Step step)
            throws IOException, InterruptedException, TimeoutException {
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
    void freshHome() throws IOException, InterruptedException, TimeoutException {
        step("1-2", scenario::freshHomeHoldsNoTool);
    }

    @Test
    @Order(2)
    @DisplayName(
            "steps 3-4: the installed launcher starts the bundled runtime, which reports a UTF-8"
                    + " locale, the installed runtime and the fresh home; the application is ready")
    void launch() throws IOException, InterruptedException, TimeoutException {
        step(
                "3-4",
                () -> {
                    scenario.attach(tierB.start());
                    scenario.applicationIsReady();
                });
    }

    @Test
    @Order(3)
    @DisplayName(
            "steps 14-15: Comet and Percolator downloaded and installed by the packaged"
                    + " application into the fresh home; nothing written into the install tree")
    void install() throws IOException, InterruptedException, TimeoutException {
        step(
                "14-15",
                () -> {
                    scenario.installCometAndPercolatorFromTheToolManager();
                    tierB.assertInstallTreeUntouched();
                    tierB.assertLaunchDirectoriesUntouched();
                });
    }

    @Test
    @Order(4)
    @DisplayName(
            "steps 5-7: the default project, no run in it yet; two spectrum files and the FASTA"
                    + " through the controls")
    void inputs() throws IOException, InterruptedException, TimeoutException {
        step("5-7", scenario::chooseSpectraAndDatabase);
    }

    @Test
    @Order(5)
    @DisplayName("step 8: the default Comet, 2026.03.0")
    void defaultComet() throws IOException, InterruptedException, TimeoutException {
        step("8", scenario::defaultCometIsSelected);
    }

    @Test
    @Order(6)
    @DisplayName("step 9: the default XML-capable Percolator, resolved from the shipped manifest")
    void defaultPercolator() throws IOException, InterruptedException, TimeoutException {
        step("9", scenario::defaultXmlCapablePercolatorIsSelected);
    }

    @Test
    @Order(7)
    @DisplayName(
            "steps 10-12: a precursor and a variable modification changed; the summary says so")
    void parameters() throws IOException, InterruptedException, TimeoutException {
        step("10-12", scenario::changeParameters);
    }

    @Test
    @Order(8)
    @DisplayName("step 13: both q filters show 0.01")
    void defaultFilters() throws IOException, InterruptedException, TimeoutException {
        step("13", scenario::bothFiltersAreOnePercent);
    }

    @Test
    @Order(9)
    @DisplayName("steps 14, 16: Run, waiting on observable workflow state; the run succeeds")
    void run() throws IOException, InterruptedException, TimeoutException {
        step("14,16", scenario::run);
    }

    @Test
    @Order(10)
    @DisplayName("step 17: comet.params holds exactly the GUI-selected values, outputs forced on")
    void cometParams() throws IOException, InterruptedException, TimeoutException {
        step("17", scenario::cometParamsHoldExactlyTheChosenValues);
    }

    @Test
    @Order(11)
    @DisplayName(
            "step 18: one Comet per file, distinct -N, nothing beside the inputs, outputs parse")
    void cometOutputs() throws IOException, InterruptedException, TimeoutException {
        step("18", scenario::cometRanOncePerFileAndWroteNothingBesideTheInputs);
    }

    @Test
    @Order(12)
    @DisplayName("step 19: the merged PIN has one header and the summed rows of its sources")
    void mergedPin() throws IOException, InterruptedException, TimeoutException {
        step("19", scenario::mergedPinHasOneHeaderAndEveryRow);
    }

    @Test
    @Order(13)
    @DisplayName("step 20: Percolator exited 0; PSM, peptide, weights and XML artefacts parse")
    void percolatorArtefacts() throws IOException, InterruptedException, TimeoutException {
        step("20", scenario::percolatorRanAndItsArtefactsParse);
    }

    @Test
    @Order(14)
    @DisplayName("step 21: PSM and peptide counts at q <= 0.01 equal the raw files' own counts")
    void countsAtOnePercent() throws IOException, InterruptedException, TimeoutException {
        step("21", scenario::countsAtOnePercentMatchTheRawFiles);
    }

    @Test
    @Order(15)
    @DisplayName("step 22: the PSM filter at 0.005 changes only the PSM count, to the raw count")
    void psmFilter() throws IOException, InterruptedException, TimeoutException {
        step("22", scenario::psmFilterAtHalfAPercentChangesOnlyThePsms);
    }

    @Test
    @Order(16)
    @DisplayName("step 23: the peptide filter at 0.02 gives the raw file's peptide count")
    void peptideFilter() throws IOException, InterruptedException, TimeoutException {
        step("23", scenario::peptideFilterAtTwoPercent);
    }

    @Test
    @Order(17)
    @DisplayName("steps 21-23: a cutoff equal to a q-value in the file passes (<=, never <)")
    void inclusiveCutoff() throws IOException, InterruptedException, TimeoutException {
        step("21-23 boundary", scenario::aCutoffEqualToAQValuePasses);
    }

    @Test
    @Order(18)
    @DisplayName("step 24: the learned feature weights and their ranking equal the artefact's")
    void learnedWeights() throws IOException, InterruptedException, TimeoutException {
        step("24", scenario::learnedWeightsMatchTheArtefact);
    }

    @Test
    @Order(19)
    @DisplayName(
            "step 25: PDV, absent before, installed on first use from the installed application"
                    + " and launched with the installed runtime's java; on this host (no system"
                    + " libX11, E6) it cannot draw, and fails for that reason alone")
    void pdv() throws IOException, InterruptedException, TimeoutException {
        step(
                "25",
                () -> {
                    CanonicalScenario.PdvLaunch launch = scenario.pdvIsLaunchedForAKnownPsm();
                    PdvOnThisHost.assertLaunchedFromTheInstalledRuntime(
                            launch, tierB.installed(), tierB.current().environment());
                    if (PdvOnThisHost.systemLibX11().isPresent()) {
                        scenario.pdvDrewTheKnownPsm(launch);
                    } else {
                        List<String> naming =
                                PdvOnThisHost.assertFailedForWantOfLibX11Only(
                                        launch, tierB.installed());
                        System.out.println(
                                "[canonical tier B] step 25 on a host with no system "
                                        + PdvOnThisHost.LIB_X11
                                        + ": "
                                        + launch
                                        + "; ldd in PDV's environment: "
                                        + naming);
                    }
                });
    }

    @Test
    @Order(20)
    @DisplayName(
            "step 26: the real converter, absent before, installed on first use and run on the"
                    + " installed runtime's java; the XML is valid and holds the raw table's PSMs")
    void limelightConversion() throws IOException, InterruptedException, TimeoutException {
        step(
                "26",
                () -> {
                    scenario.limelightConverts();
                    tierB.assertConvertedOnTheInstalledRuntime(
                            scenario.events("limelight.converted"));
                });
    }

    @Test
    @Order(21)
    @DisplayName(
            "step 27: the packaged application uploads to the loopback fake (D-007) with a test"
                    + " key; the fake received exactly the converted file, valid")
    void upload() throws IOException, InterruptedException, TimeoutException {
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
            "step 28: provenance written by the packaged application, recomputed from disk --"
                    + " every declared file, tool binary and JAR, versions, argument arrays -- and"
                    + " the key in no file")
    void provenance() throws IOException, InterruptedException, TimeoutException {
        step("28", scenario::provenanceHoldsAgainstTheDisk);
    }

    @Test
    @Order(23)
    @DisplayName(
            "steps 29-30: a real restart -- the window closed, exit 0, reaped; a new process over"
                    + " the same home; the project, run, results, filters, tools and provenance"
                    + " are coherent")
    void restart() throws IOException, InterruptedException, TimeoutException {
        step(
                "29-30",
                () -> {
                    scenario.recordWhatIsShownBeforeTheClose();
                    tierB.closeApplication();
                    scenario.attach(tierB.start());
                    scenario.theRelaunchedApplicationIsCoherent();
                });
    }

    @Test
    @Order(24)
    @DisplayName(
            "after everything, before the last close: the install tree is exactly as installed,"
                    + " and no launch directory holds anything")
    void installTree() throws IOException, InterruptedException, TimeoutException {
        step(
                "install tree",
                () -> {
                    tierB.assertInstallTreeUntouched();
                    tierB.closeApplication();
                    tierB.assertInstallTreeUntouched();
                    assertTrue(tierB.installTreeEntries() > 100, "the snapshot covers the image");
                });
    }
}
