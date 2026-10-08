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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.TestPercolators;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 09 gate item 3's tail and {@code R-PERC-03}, through the launched application: a Tool
 * Manager with no <em>observed</em> XML-capable Percolator -- a macOS-like 3.07.1 row whose {@code
 * XML_OUTPUT} is only inferred from artefact bytes, not installed, and Percolator 3.09 registered
 * locally without XML -- gives the non-XML default and Limelight conversion unavailable, with the
 * specific explanation and both remedies on screen, and registration offered. Registering a local
 * binary through the interface (a build a test writes out: observed {@code XML_OUTPUT}, no {@code
 * SEED_OPTION}) re-reads the builds, re-resolves, makes Limelight available, and Advanced then
 * shows the seed as not supported by that build. The Tool Manager's answers are written out here:
 * the test is about the interface's logic, not a binary. Every text is typed out.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PercolatorUnavailableUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-08T00:00:00Z"));

    private static final String REMEDIES =
            "\nWhat you can do:\n"
                    + "- Register a local Percolator binary that can write Percolator XML, from the"
                    + " Tool Manager. CometGUI probes it, and offers Limelight conversion if the"
                    + " probe observes XML_OUTPUT.\n"
                    + "- Run the Limelight conversion on a computer whose platform has an"
                    + " XML-capable Percolator, rerunning Percolator there from this run's merged"
                    + " PIN.";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static Path registered;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ToolOffer macosLike =
                TestPercolators.notInstalled(
                        "3.07.1",
                        Set.of(ToolCapability.XML_OUTPUT),
                        CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES);
        ToolOffer local309 =
                TestPercolators.installed(
                        "3.09",
                        ToolOrigin.LOCAL,
                        root.resolve("bin/percolator-3.09"),
                        TestPercolators.allBut(
                                ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                        List.of());
        registered = root.resolve("bin/percolator-3.10");
        ToolOffer seedless =
                TestPercolators.installed(
                        "3.10",
                        ToolOrigin.LOCAL,
                        registered,
                        TestPercolators.allBut(ToolCapability.SEED_OPTION),
                        List.of());
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        null,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(
                                () ->
                                        InstalledComet.nothing()
                                                .with(macosLike, local309)
                                                .registering((tool, path) -> seedless),
                                root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
        driver.clickOn("nav-percolator");
        RunSection.awaitText(
                driver,
                "percolator-offers",
                text -> text.endsWith("can run."),
                RunSection.CHECK_BOUND);
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
            "no observed XML-capable build: the non-XML default, Limelight unavailable with the"
                    + " explanation and both remedies, the inferred claim named, registration"
                    + " offered")
    void limelightUnavailable() {
        driver.clickOn("nav-percolator");
        assertAll(
                () ->
                        assertEquals(
                                "2 Percolator builds are known on this computer, and 1 of them is"
                                        + " installed and can run.",
                                driver.textOf("percolator-offers")),
                () ->
                        assertEquals(
                                "Percolator 3.09 (registered local binary) runs in the next"
                                        + " search: the resolved default.",
                                driver.textOf("percolator-selection")),
                () ->
                        assertEquals(
                                "Limelight conversion is unavailable. Limelight conversion is"
                                        + " unavailable: no Percolator that can be used on this"
                                        + " computer has been observed to have XML_OUTPUT, and the"
                                        + " Limelight converter reads the Percolator XML that"
                                        + " XML_OUTPUT writes. Percolator 3.07.1 claims XML_OUTPUT"
                                        + " from inferred-from-artefact-bytes evidence, which has"
                                        + " not been observed by running it; installing 3.07.1"
                                        + " will probe it."
                                        + REMEDIES,
                                driver.textOf("percolator-limelight-status")),
                () ->
                        assertEquals(
                                "Not installed, so not offered for running -- install from the"
                                        + " Tool Manager section: Percolator 3.07.1.",
                                driver.textOf("percolator-installable")),
                () ->
                        assertEquals(
                                List.of(
                                        "Percolator 3.09 (registered local binary) -- the resolved"
                                                + " default"),
                                ParameterEditorApp.comboItems(driver, "percolator-version")),
                () -> assertFalse(RunSection.isDisabled(driver, "percolator-register")));

        driver.clickOn("percolator-limelight");
        assertAll(
                "switched on: nothing can satisfy it, and the interface says so",
                () ->
                        assertEquals(
                                "The default Percolator is still 3.09 (registered local binary)"
                                        + " (Limelight conversion was switched on and needs"
                                        + " XML_OUTPUT, but no Percolator here has been observed"
                                        + " to have it, so Limelight conversion is unavailable).",
                                driver.textOf("percolator-notice")),
                () ->
                        assertEquals(
                                "Percolator 3.09 (registered local binary) is the newest Percolator"
                                        + " that can be used on this computer. Limelight"
                                        + " conversion is switched on but unavailable, because no"
                                        + " Percolator here has been observed to have"
                                        + " XML_OUTPUT.",
                                driver.textOf("percolator-reason")));
    }

    @Test
    @Order(2)
    @DisplayName(
            "registering a local binary through the interface re-reads the builds and"
                    + " re-resolves: the new build is the default and Limelight can run")
    void registrationRefreshes() {
        driver.clickOn("nav-percolator");
        app.chooser().file(registered);
        driver.clickOn("percolator-register");
        assertEquals(
                "Registered Percolator 3.10 (registered local binary) from "
                        + registered
                        + ". Observed capabilities: XML_OUTPUT, XML_DECOY_OUTPUT, PSM_TSV_OUTPUT,"
                        + " PEPTIDE_TSV_OUTPUT, DECOY_OUTPUT, WEIGHTS_OUTPUT, THREAD_OPTION,"
                        + " TEST_FDR_OPTION, TRAIN_FDR_OPTION, MAX_ITERATIONS_OPTION.",
                RunSection.awaitText(
                        driver,
                        "percolator-register-status",
                        text -> text.startsWith("Registered"),
                        RunSection.CHECK_BOUND));
        assertEquals(
                "The default Percolator changed from 3.09 (registered local binary) to 3.10"
                        + " (registered local binary) because the Percolator builds available on"
                        + " this computer changed.",
                RunSection.awaitText(
                        driver,
                        "percolator-notice",
                        text -> text.startsWith("The default Percolator changed"),
                        RunSection.CHECK_BOUND));
        assertAll(
                () ->
                        assertEquals(
                                "Percolator 3.10 (registered local binary) runs in the next"
                                        + " search: the resolved default.",
                                driver.textOf("percolator-selection")),
                () ->
                        assertEquals(
                                "Limelight conversion can run: a Percolator on this computer was"
                                        + " observed to write the Percolator XML it reads"
                                        + " (XML_OUTPUT).",
                                driver.textOf("percolator-limelight-status")),
                () ->
                        assertTrue(
                                app.chooser()
                                        .asked()
                                        .contains("file:a Percolator executable to register"),
                                () -> "the chooser was asked: " + app.chooser().asked()));
    }

    @Test
    @Order(3)
    @DisplayName(
            "Advanced shows only what the selected build supports: the seed of a build without"
                    + " SEED_OPTION is not editable, and says why")
    void seedNotSupported() {
        driver.clickOn("nav-percolator");
        driver.clickOn("percolator-advanced-toggle");
        assertAll(
                () -> assertTrue(RunSection.isDisabled(driver, "percolator-random-seed")),
                () -> assertFalse(RunSection.isDisabled(driver, "percolator-test-fdr")),
                () -> assertFalse(RunSection.isDisabled(driver, "percolator-train-fdr")),
                () -> assertFalse(RunSection.isDisabled(driver, "percolator-maximum-iterations")),
                () -> assertFalse(RunSection.isDisabled(driver, "percolator-thread-count")),
                () ->
                        assertEquals(
                                "The seed of Percolator's random number generator, which decides"
                                        + " how results are split for cross-validation. It is"
                                        + " always recorded, so a rerun of this run can reproduce"
                                        + " it.\n"
                                        + "Not supported by this build: Percolator 3.10"
                                        + " (registered local binary) was not observed to accept"
                                        + " SEED_OPTION, so this setting is not passed to it.",
                                driver.textOf("percolator-random-seed-state")));
    }
}
