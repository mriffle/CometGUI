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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.cometgui.app.e2e.scenario.NoXmlScenario;
import org.cometgui.app.e2e.support.EndToEnd;
import org.cometgui.app.e2e.tiera.ProcessServiceOracle;
import org.cometgui.app.testing.RealPercolators;
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
 * The specification's second end-to-end scenario, "Percolator without XML", in Tier B (Phase 14
 * unit 7; gate item 3): the packaged artefact, installed, launched as a separate process and driven
 * through the test-only bridge -- the <em>same</em> tier-neutral steps as {@code
 * TierANoXmlScenarioTest}, {@link NoXmlScenario}'s, with the composition {@link TierBApplication}'s
 * (see {@code TierBCanonicalScenarioTest} for what that composition holds every launch to).
 *
 * <p>Everything the Tier A class asserts is asserted here, against the packaged product: Percolator
 * 3.09 registered as a local binary through the Percolator section and run without XML; standard
 * viewing; the Limelight section's explanation before any conversion; the compatible rerun
 * installing 3.07.1 -- downloaded by the packaged application into the fresh home -- only after the
 * click; "Comet was not rerun" proved from disk (bytes, inode, mtime) before the product's records;
 * the rerun's XML; the conversion; both Percolator executions in provenance. On top of them, the
 * Tier B invariants: the conversion ran on the installed runtime's {@code bin/java} (the derived
 * run's {@code limelight.converted} record), and at the end the install tree is exactly as
 * installed and no launch directory holds anything, before and after the window is closed (exit 0,
 * reaped).
 *
 * <p>Where the scenario diverges from the specification's wording -- 3.09 has no managed Linux
 * build, E5's top-1 setting, no upload, PDV or relaunch -- is said in {@code
 * TierANoXmlScenarioTest} and holds here unchanged.
 *
 * <h2>Files read outside this module</h2>
 *
 * <p>Those of {@link TierBApplication}, and the four files of the Percolator 3.09 fixture under
 * {@code scratch/percolator/3.09} (the wrapper the packaged application runs, the {@code .rpm}'s
 * binary and two Boost 1.66 libraries). Network: none from this JVM but loopback; the packaged
 * application reaches the pinned GitHub release URLs.
 *
 * <h2>How to run it</h2>
 *
 * <pre>{@code
 * bash scripts/package/build-app-image.sh
 * LC_ALL=C.UTF-8 mvn -B -o -pl cometgui-app -am test -Dtest=TierBNoXmlScenarioTest \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dcometgui.e2e=true
 * }</pre>
 */
@EndToEnd
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the reference platform: the Linux app-image is the only packaged artefact built"
                        + " and executed in this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TierBNoXmlScenarioTest {

    @TempDir private static Path parent;

    private static TierBApplication tierB;

    private static NoXmlScenario scenario;

    private static String failedStep;

    @BeforeAll
    static void prepare() throws IOException {
        Path wrapper = RealPercolators.wrapper309().toRealPath();
        tierB = TierBApplication.prepare(parent);
        scenario =
                new NoXmlScenario(
                        tierB.layout(), tierB.chooserScript(), new ProcessServiceOracle(), wrapper);
    }

    @AfterAll
    static void stop() throws IOException {
        try {
            if (tierB != null) {
                for (Map<String, String> jvm : tierB.reported()) {
                    System.out.println("[no-XML tier B] launched JVM: " + jvm);
                }
                tierB.close();
            }
        } finally {
            if (scenario != null) {
                for (String observed : scenario.observations()) {
                    System.out.println("[no-XML tier B] " + observed);
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
    @DisplayName("a brand-new home and application data directory, no tool present")
    void freshHome() throws IOException, InterruptedException, TimeoutException {
        step("fresh home", scenario::freshHomeHoldsNoTool);
    }

    @Test
    @Order(2)
    @DisplayName(
            "the installed launcher starts the bundled runtime, which reports a UTF-8 locale; the"
                    + " application reaches its ready state")
    void launch() throws IOException, InterruptedException, TimeoutException {
        step(
                "launch",
                () -> {
                    scenario.attach(tierB.start());
                    scenario.applicationIsReady();
                });
    }

    @Test
    @Order(3)
    @DisplayName(
            "step 1: Comet installed through the Tool Manager; Percolator 3.09, which has no"
                    + " managed Linux build, registered as a local binary through the Percolator"
                    + " section and selected (R-LL-02)")
    void select309() throws IOException, InterruptedException, TimeoutException {
        step("1", scenario::installCometAndRegisterPercolator);
    }

    @Test
    @Order(4)
    @DisplayName("step 2: two spectrum files and the FASTA through the controls; the default Comet")
    void inputs() throws IOException, InterruptedException, TimeoutException {
        step(
                "2 inputs",
                () -> {
                    scenario.chooseSpectraAndDatabase();
                    scenario.defaultCometIsSelected();
                });
    }

    @Test
    @Order(5)
    @DisplayName("step 2: the parameters changed (E5's top 1 among them); both q filters 0.01")
    void parameters() throws IOException, InterruptedException, TimeoutException {
        step(
                "2 parameters",
                () -> {
                    scenario.changeParameters();
                    scenario.bothFiltersAreOnePercent();
                });
    }

    @Test
    @Order(6)
    @DisplayName("step 2: Run; Comet and Percolator 3.09 succeed")
    void run() throws IOException, InterruptedException, TimeoutException {
        step("2 run", scenario::run);
    }

    @Test
    @Order(7)
    @DisplayName(
            "step 2: comet.params as chosen; one Comet per file, nothing beside the inputs; the"
                    + " merged PIN")
    void cometOutputs() throws IOException, InterruptedException, TimeoutException {
        step(
                "2 Comet",
                () -> {
                    scenario.cometParamsHoldExactlyTheChosenValues();
                    scenario.cometRanOncePerFileAndWroteNothingBesideTheInputs();
                    scenario.mergedPinHasOneHeaderAndEveryRow();
                });
    }

    @Test
    @Order(8)
    @DisplayName("step 2: Percolator 3.09 ran once, given no XML option, and wrote no XML")
    void percolatorWithoutXml() throws IOException, InterruptedException, TimeoutException {
        step("2 Percolator", scenario::percolatorRanWithoutXml);
    }

    @Test
    @Order(9)
    @DisplayName(
            "step 2: standard viewing -- PSM and peptide counts at 0.01 and the learned weights"
                    + " equal the raw files'")
    void viewing() throws IOException, InterruptedException, TimeoutException {
        step(
                "2 viewing",
                () -> {
                    scenario.countsAtOnePercentMatchTheRawFiles();
                    scenario.learnedWeightsMatchTheArtefact();
                });
    }

    @Test
    @Order(10)
    @DisplayName(
            "step 3: the Limelight section explains the XML incompatibility before any"
                    + " conversion is attempted; nothing was launched or installed")
    void explanation() throws IOException, InterruptedException, TimeoutException {
        step("3", scenario::limelightExplainsBeforeAnyConversion);
    }

    @Test
    @Order(11)
    @DisplayName(
            "step 4: no 3.07.1 in the cache before the click; the one-click rerun installs it and"
                    + " uses it")
    void compatibleRerun() throws IOException, InterruptedException, TimeoutException {
        step("4", scenario::theCompatibleRerunInstallsOnlyAfterTheClick);
    }

    @Test
    @Order(12)
    @DisplayName(
            "step 5: Comet was not rerun -- proved from disk (bytes, inode, mtime; no new pepXML"
                    + " or PIN), then the product's records agree")
    void cometNotRerun() throws IOException, InterruptedException, TimeoutException {
        step("5", scenario::cometWasNotRerun);
    }

    @Test
    @Order(13)
    @DisplayName("step 6: the new Percolator execution wrote XML holding its tables' rows")
    void xml() throws IOException, InterruptedException, TimeoutException {
        step("6", scenario::theRerunWroteXml);
    }

    @Test
    @Order(14)
    @DisplayName(
            "step 7: the Limelight conversion of the derived run succeeds, on the installed"
                    + " runtime's java; the XML is valid against the installed JAR's schema")
    void conversion() throws IOException, InterruptedException, TimeoutException {
        step(
                "7",
                () -> {
                    scenario.limelightConvertsTheRerun();
                    tierB.assertConvertedOnTheInstalledRuntime(
                            scenario.events("limelight.converted"));
                });
    }

    @Test
    @Order(15)
    @DisplayName(
            "step 8: provenance holds both Percolator executions -- distinct versions, checksums"
                    + " equal to the binaries on disk, and commands")
    void provenance() throws IOException, InterruptedException, TimeoutException {
        step("8", scenario::provenanceHoldsBothPercolatorExecutions);
    }

    @Test
    @Order(16)
    @DisplayName(
            "the window closed: exit 0, reaped; the install tree exactly as installed, and no"
                    + " launch directory holds anything")
    void close() throws IOException, InterruptedException, TimeoutException {
        step(
                "close",
                () -> {
                    tierB.assertInstallTreeUntouched();
                    tierB.closeApplication();
                    tierB.assertInstallTreeUntouched();
                });
    }
}
