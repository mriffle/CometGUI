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
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import javafx.scene.control.CheckBox;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.RealPercolators;
import org.cometgui.app.testing.TestPercolators;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Percolator section through the launched application (Phase 09 gate items 3 and 4, interface
 * half; {@code R-PERC-04}, {@code R-PERC-10}, {@code R-PERC-11}, {@code R-RES-01}), over a Tool
 * Manager offering two installed builds a test writes out -- the test is about what the interface
 * makes of the builds, not about a binary, so nothing is launched:
 *
 * <ul>
 *   <li>Percolator 3.09, a registered local binary observed to have every capability but the two
 *       XML ones (what Phase 09 unit 1 probed on the real 3.09);
 *   <li>Percolator 3.07.1, a managed build observed to have all eleven, with the manifest's two
 *       advisories.
 * </ul>
 *
 * <p>Limelight off: the default is 3.09. Switched on: the default is 3.07.1, the notice names both
 * versions and {@code XML_OUTPUT}, and the reason on screen is the sentence provenance records.
 * Switched off: 3.09 again, noticed. Every text is typed out here.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PercolatorSectionUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-08T00:00:00Z"));

    private static final String NAME_309 = "Percolator 3.09 (registered local binary)";

    /** The skip reason: on screen and in provenance, the same words. */
    private static final String WHY_NOT_309 =
            "Using Percolator 3.07.1 rather than 3.09 (registered local binary) because 3.09"
                    + " (registered local binary) lacks XML_OUTPUT, which Limelight conversion"
                    + " needs (the Limelight converter reads the Percolator XML that XML_OUTPUT"
                    + " writes).";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static ToolOffer local309;

    private static ToolOffer managed3071;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        local309 =
                TestPercolators.installed(
                        "3.09",
                        ToolOrigin.LOCAL,
                        root.resolve("bin/percolator-3.09"),
                        TestPercolators.allBut(
                                ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                        List.of());
        managed3071 =
                TestPercolators.installed(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        root.resolve("bin/percolator-3.07.1"),
                        TestPercolators.ALL,
                        List.of(
                                new ToolAdvisory(
                                        "percolator.3-07-1-predates-i-spline-pep-regressor",
                                        RealPercolators.I_SPLINE_ADVISORY),
                                new ToolAdvisory(
                                        "percolator.3-07-1-predates-pep-above-one-fix",
                                        RealPercolators.PEP_ABOVE_ONE_ADVISORY)));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(
                                () -> InstalledComet.nothing().with(managed3071, local309),
                                root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        driver.clickOn("nav-percolator");
        assertEquals(
                "2 Percolator builds are known on this computer, and 2 of them are installed and"
                        + " can run.",
                RunSection.awaitText(
                        driver,
                        "percolator-offers",
                        text -> text.endsWith("can run."),
                        RunSection.CHECK_BOUND));
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
            "gate 3: Limelight off -> 3.09; on -> 3.07.1, the notice naming both and"
                    + " XML_OUTPUT; off -> 3.09 again, noticed")
    void gate3TheSwitchMovesTheDefault() {
        driver.clickOn("nav-percolator");
        assertAll(
                "Limelight off",
                () ->
                        assertEquals(
                                List.of("Percolator 3.07.1", NAME_309 + " -- the resolved default"),
                                ParameterEditorApp.comboItems(driver, "percolator-version")),
                () ->
                        assertEquals(
                                NAME_309 + " -- the resolved default",
                                ParameterEditorApp.comboText(driver, "percolator-version")),
                () ->
                        assertEquals(
                                NAME_309 + " runs in the next search: the resolved default.",
                                driver.textOf("percolator-selection")),
                () ->
                        assertEquals(
                                NAME_309
                                        + " is the newest Percolator that can be used on this"
                                        + " computer.",
                                driver.textOf("percolator-reason")),
                () -> assertFalse(isSelected("percolator-limelight")));

        driver.clickOn("percolator-limelight");
        assertAll(
                "Limelight on",
                () -> assertTrue(isSelected("percolator-limelight")),
                () ->
                        assertEquals(
                                "The default Percolator changed from 3.09 (registered local"
                                        + " binary) to 3.07.1 because Limelight conversion was"
                                        + " switched on and needs XML_OUTPUT.",
                                driver.textOf("percolator-notice")),
                () ->
                        assertEquals(
                                "Percolator 3.07.1 -- the resolved default",
                                ParameterEditorApp.comboText(driver, "percolator-version")),
                () ->
                        assertEquals(
                                "Percolator 3.07.1 runs in the next search: the resolved default.",
                                driver.textOf("percolator-selection")));

        driver.clickOn("percolator-limelight");
        assertAll(
                "Limelight off again",
                () ->
                        assertEquals(
                                "The default Percolator changed from 3.07.1 to 3.09 (registered"
                                        + " local binary) because Limelight conversion was switched"
                                        + " off, so XML_OUTPUT is no longer needed.",
                                driver.textOf("percolator-notice")),
                () ->
                        assertEquals(
                                NAME_309 + " -- the resolved default",
                                ParameterEditorApp.comboText(driver, "percolator-version")));
    }

    @Test
    @Order(2)
    @DisplayName(
            "gate 4: the skip reason on screen names 3.09 and XML_OUTPUT, in the words"
                    + " provenance records; and 3.07.1's advisories are shown at selection")
    void gate4TheSkipReasonAndTheAdvisories() {
        driver.clickOn("nav-percolator");
        driver.clickOn("percolator-limelight");
        assertTrue(isSelected("percolator-limelight"));
        String recorded =
                PercolatorResolver.resolve(
                                List.of(managed3071, local309),
                                EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION))
                        .skipped()
                        .get(0)
                        .reason();
        assertAll(
                () -> assertEquals(WHY_NOT_309, driver.textOf("percolator-reason")),
                () ->
                        assertEquals(
                                "Newer Percolator builds passed over:\n- " + WHY_NOT_309,
                                driver.textOf("percolator-skipped")),
                () ->
                        assertEquals(
                                WHY_NOT_309,
                                recorded,
                                "the sentence percolator.skipped.01.reason records"),
                () ->
                        assertEquals(
                                "Advisories for Percolator 3.07.1, shown when it is selected:\n- "
                                        + RealPercolators.I_SPLINE_ADVISORY
                                        + "\n- "
                                        + RealPercolators.PEP_ABOVE_ONE_ADVISORY,
                                driver.textOf("percolator-advisories")),
                () ->
                        assertEquals(
                                "Limelight conversion can run: a Percolator on this computer was"
                                        + " observed to write the Percolator XML it reads"
                                        + " (XML_OUTPUT).",
                                driver.textOf("percolator-limelight-status")));
        driver.clickOn("percolator-limelight");
        assertFalse(isSelected("percolator-limelight"));
    }

    @Test
    @Order(3)
    @DisplayName("another installed build can be chosen, and the default followed again")
    void chooseAnotherBuild() {
        driver.clickOn("nav-percolator");
        ParameterEditorApp.choose(driver, "percolator-version", "Percolator 3.07.1");
        assertEquals(
                "Percolator 3.07.1 runs in the next search: your choice. The resolved default is"
                        + " Percolator 3.09 (registered local binary); choose it, or Use the"
                        + " default, to follow the default again.",
                driver.textOf("percolator-selection"));
        driver.clickOn("percolator-use-default");
        assertEquals(
                NAME_309 + " runs in the next search: the resolved default.",
                driver.textOf("percolator-selection"));
    }

    @Test
    @Order(4)
    @DisplayName(
            "Advanced: every option 3.09 supports is editable, testFDR said not to be the"
                    + " display filter; the filters are 0.01 and 0.01 and set independently")
    void advancedAndFilters() {
        driver.clickOn("nav-percolator");
        driver.clickOn("percolator-advanced-toggle");
        assertAll(
                () -> assertFalse(RunSection.isDisabled(driver, "percolator-test-fdr")),
                () -> assertFalse(RunSection.isDisabled(driver, "percolator-random-seed")),
                () -> assertFalse(RunSection.isDisabled(driver, "percolator-thread-count")),
                () -> assertEquals("1", driver.textOf("percolator-random-seed")),
                () -> assertEquals("0.01", driver.textOf("percolator-test-fdr")),
                () ->
                        assertEquals(
                                "A learning threshold inside Percolator: the false discovery rate"
                                        + " at which Percolator selects the best cross-validation"
                                        + " result and reports its final results. Changing it"
                                        + " changes what Percolator computes, so it takes effect"
                                        + " only when Percolator runs. It is not the PSM q-value"
                                        + " result filter, which only changes which results are"
                                        + " displayed and exported and never reruns Percolator.",
                                driver.textOf("percolator-test-fdr-state")));
        enter(driver, "percolator-test-fdr", "abc");
        assertTrue(
                driver.textOf("percolator-test-fdr-state")
                        .endsWith(
                                "\nNot valid: testFDR must be a number greater than 0 and at most"
                                        + " 1, written with a full stop as the decimal separator"
                                        + " (for example 0.01), but was \"abc\""),
                () -> driver.textOf("percolator-test-fdr-state"));
        enter(driver, "percolator-test-fdr", "0.05");
        assertEquals("0.05", driver.textOf("percolator-test-fdr"));
        driver.clickOn("percolator-advanced-toggle");

        assertAll(
                () -> assertEquals("0.01", driver.textOf("percolator-psm-filter")),
                () -> assertEquals("0.01", driver.textOf("percolator-peptide-filter")),
                () ->
                        assertEquals(
                                "The PSM and the peptide q-value filters (each 0.01 by default,"
                                        + " from 0 to 1, a q-value equal to the cutoff passing)"
                                        + " change only which PSMs and peptides are displayed and"
                                        + " exported. Changing them never reruns Percolator or any"
                                        + " other tool, and they are not Percolator's testFDR or"
                                        + " trainFDR, the learning thresholds under Advanced"
                                        + " settings.",
                                driver.textOf("percolator-filters-status")));
        enter(driver, "percolator-psm-filter", "0.05");
        assertEquals("0.05", driver.textOf("percolator-psm-filter"));
        assertEquals("0.01", driver.textOf("percolator-peptide-filter"), "independent");
    }

    @Test
    @Order(5)
    @DisplayName("before any run, the rerun action is not offered, and says why")
    void noRerunBeforeARun() {
        driver.clickOn("nav-percolator");
        assertEquals(
                "No run can start: this application was composed without a process service, so"
                        + " no tool can be launched.",
                RunSection.awaitText(
                        driver,
                        "percolator-rerun-preview",
                        text -> !text.startsWith("Checking"),
                        RunSection.CHECK_BOUND));
        assertTrue(RunSection.isDisabled(driver, "percolator-rerun"));
    }

    private static boolean isSelected(String id) {
        CheckBox box = (CheckBox) driver.node(id);
        return driver.callOnFxThread(box::isSelected);
    }
}
