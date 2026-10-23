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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import org.cometgui.app.e2e.scenario.CanonicalScenario;
import org.cometgui.app.e2e.scenario.NoXmlScenario;
import org.cometgui.app.e2e.support.EndToEnd;
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
 * The specification's second end-to-end scenario, "Percolator without XML", in Tier A (Phase 14
 * unit 4; gate item 3): the assembled application, in-process, from a brand-new home and an empty
 * tool cache, driven as a scientist drives it. The steps are {@link NoXmlScenario}'s, tier-neutral;
 * this class is Tier A's composition -- {@link TierAApplication}, the same one {@link
 * TierACanonicalScenarioTest} runs on -- and nothing else.
 *
 * <h2>Where the scenario diverges from the specification's wording</h2>
 *
 * <ul>
 *   <li><strong>"Select Percolator 3.09"</strong>: the shipped manifest has no Linux x86-64 managed
 *       Percolator 3.09 ({@code R-PERC-12}), so 3.09 is registered as a local binary through the
 *       Percolator section's own control -- the specification's own fallback, {@code R-LL-02} --
 *       with the dialog answered with {@code scratch/percolator/3.09/run-percolator-3.09.sh}, the
 *       wrapper around upstream's {@code .rpm} binary that Phase 09 measured ({@link
 *       RealPercolators#wrapper309()} holds every file behind it to its SHA-256 first). Comet is
 *       installed through the Tool Manager first (P14-5, escalated as E1).
 *   <li><strong>"where no such managed build exists for the platform the UI offers local-binary
 *       registration instead"</strong> cannot occur here: on Linux the real manifest has a managed
 *       XML-capable Percolator (3.07.1), so the rerun is what the UI offers. The other branch is
 *       covered by {@code LimelightRerunUiTest.noManagedBuild} (Phase 12), which offers every
 *       managed Percolator as unpublished for the platform and sees the registration action, "no
 *       managed build exists for this platform", no rerun anywhere in the section, and the
 *       Percolator section's registration started by the press.
 *   <li><strong>E5</strong>: as in the canonical scenario, step 10 sets Comet's results per
 *       spectrum to 1 in the Advanced level, because with Comet's default top 5 the pinned
 *       converter refuses this fixture's run ({@link CanonicalScenario#RESULTS_PER_SPECTRUM}).
 *   <li><strong>No upload, no PDV, no relaunch</strong>: the specification's second scenario does
 *       not ask for them; the canonical scenario proves them.
 * </ul>
 *
 * <h2>Files read outside this module</h2>
 *
 * <p>Those of {@link TierAApplication} ({@code manifests/tools.json}, the {@code D-006} local
 * fixture under {@code scratch/fixture}, the project-local X11 and font closures under {@code
 * tools/}, {@code scripts/feasibility/gui-spike/xkbcomp-path-shim.c}), and the four files of the
 * Percolator 3.09 fixture under {@code scratch/percolator/3.09} -- the wrapper, the {@code .rpm}'s
 * binary and the two Boost 1.66 libraries. Network: the pinned GitHub release URLs (Comet, then
 * Percolator 3.07.1 when the rerun is pressed, then the converter on first use), and loopback.
 *
 * <h2>How to run it</h2>
 *
 * <pre>{@code
 * LC_ALL=C.UTF-8 mvn -B -o -pl cometgui-app -am test -Dtest=TierANoXmlScenarioTest \
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
                "the reference platform: only the linux/x86-64 Comet and Percolator have been"
                        + " executed in this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TierANoXmlScenarioTest {

    @TempDir private static Path parent;

    private static TierAApplication tierA;

    private static NoXmlScenario scenario;

    private static String failedStep;

    @BeforeAll
    static void prepare() throws IOException {
        Path wrapper = RealPercolators.wrapper309().toRealPath();
        tierA = TierAApplication.prepare(parent);
        scenario =
                new NoXmlScenario(
                        tierA.layout(), tierA.chooserScript(), new ProcessServiceOracle(), wrapper);
    }

    @AfterAll
    static void stop() throws TimeoutException {
        try {
            if (tierA != null) {
                tierA.close();
            }
        } finally {
            if (scenario != null) {
                for (String observed : scenario.observations()) {
                    System.out.println("[no-XML tier A] " + observed);
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
    @DisplayName("the assembled application starts and reaches its ready state")
    void launch() throws IOException, InterruptedException, TimeoutException {
        step(
                "launch",
                () -> {
                    scenario.attach(tierA.start());
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
            "step 7: the Limelight conversion of the derived run succeeds; the XML is valid"
                    + " against the installed JAR's schema and holds its PSMs")
    void conversion() throws IOException, InterruptedException, TimeoutException {
        step("7", scenario::limelightConvertsTheRerun);
    }

    @Test
    @Order(15)
    @DisplayName(
            "step 8: provenance holds both Percolator executions -- distinct versions, checksums"
                    + " equal to the binaries on disk, and commands")
    void provenance() throws IOException, InterruptedException, TimeoutException {
        step("8", scenario::provenanceHoldsBothPercolatorExecutions);
    }
}
