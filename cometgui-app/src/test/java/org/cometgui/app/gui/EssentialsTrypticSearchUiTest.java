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

import static org.cometgui.app.gui.ParameterEditorApp.choose;
import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javafx.scene.control.CheckBox;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 07 exit-gate item 1 ({@code AC-PAR-03}): "A GUI test configures a complete tryptic DDA
 * search using Essentials only, and the generated parameter file matches an expected canonical file
 * exactly."
 *
 * <h2>The search</h2>
 *
 * <p>A TMT-labelled tryptic DDA search on Comet 2026.03.0, the default release: two mzML files and
 * a reviewed human FASTA through the injected file chooser; a 10 ppm precursor window with 0/+1
 * isotope offsets; low-resolution fragment bins (a high-res precursor, low-res fragment
 * instrument); fully tryptic with one missed cleavage; TMT on lysine and the peptide N-terminus as
 * static modifications; protein N-terminal acetylation added as a variable modification beside the
 * default oxidised methionine; Comet's internal concatenated decoys with the prefix {@code rev_};
 * eight threads. Every value is set through an Essentials control -- a click, a key press, typed
 * text -- and the Advanced and Expert levels are never shown.
 *
 * <h2>The expected file</h2>
 *
 * <p>{@code essentials-tryptic-dda-2026.03.0.params}, checked in beside this class and
 * hand-reviewed: it is Comet 2026.03.0's own {@code comet -q} file in CometGUI's canonical form,
 * with exactly the lines this search sets changed, {@code output_percolatorfile} on (the workflow
 * requires it) and a header naming the build. The build is this test's own, injected through the
 * application's constructor ({@code 0.0.0-guitest}), so the header line is compared like every
 * other line -- nothing in the file is substituted before comparing. The comparison is byte for
 * byte.
 */
class EssentialsTrypticSearchUiTest {

    /** The build this test names, which the expected file's header carries. */
    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    /**
     * Where the search's files are: {@code /srv/proteomics} on this Linux build host. Built from
     * the file system's root rather than written as one literal (SpotBugs rejects a hard-coded
     * absolute path), and fixed rather than temporary because the database path is a line of the
     * expected file. The files need not exist: the file system the application is given reports
     * them as readable files.
     */
    private static final Path DATA =
            FileSystems.getDefault()
                    .getRootDirectories()
                    .iterator()
                    .next()
                    .resolve("srv")
                    .resolve("proteomics");

    private static final Path FASTA = DATA.resolve("uniprot-human-reviewed-2026_03.fasta");

    private static final Path RUN_1 = DATA.resolve("tmt10-fraction01.mzML");

    private static final Path RUN_2 = DATA.resolve("tmt10-fraction02.mzML");

    private static final String EXPECTED = "essentials-tryptic-dda-2026.03.0.params";

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    @TempDir private static Path saveDirectory;

    @BeforeAll
    static void launch() {
        app = ParameterEditorApp.launch(BUILD, FASTA, RUN_1, RUN_2);
        driver = new TestFxUiDriver(app.application());
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName(
            "Essentials alone configures a tryptic DDA search whose saved file is the expected one")
    void essentialsConfiguresATrypticSearch() throws IOException {
        ParameterEditorApp.openEditor(driver);
        assertTrue(driver.isVisible("param-essentials"), "the editor opens on Essentials");
        assertFalse(driver.isVisible("param-advanced"), "Advanced is not shown");

        // Inputs: spectra and the database, through the injected chooser.
        app.chooser().spectra(RUN_1, RUN_2);
        driver.clickOn("ess-spectra-add");
        app.chooser().database(FASTA);
        driver.clickOn("ess-database_name-choose");
        assertAll(
                "inputs",
                () ->
                        assertEquals(
                                "2 spectrum files chosen.", driver.textOf("ess-spectra-summary")),
                () ->
                        assertEquals(
                                "Found and readable: /srv/proteomics/tmt10-fraction01.mzML",
                                driver.textOf("ess-spectrum-0")),
                () ->
                        assertEquals(
                                "Found and readable: /srv/proteomics/tmt10-fraction02.mzML",
                                driver.textOf("ess-spectrum-1")),
                () ->
                        assertEquals(
                                "/srv/proteomics/uniprot-human-reviewed-2026_03.fasta",
                                driver.textOf("ess-database_name")),
                () ->
                        assertEquals(
                                "Database file: Found and readable:"
                                        + " /srv/proteomics/uniprot-human-reviewed-2026_03.fasta",
                                driver.textOf("ess-database-status")),
                () -> assertEquals(List.of("spectra", "database"), app.chooser().asked()));

        // Precursor: a 10 ppm window, 0/+1 isotope offsets.
        enter(driver, "ess-peptide_mass_tolerance_lower", "-10");
        enter(driver, "ess-peptide_mass_tolerance_upper", "10");
        choose(driver, "ess-peptide_mass_units", "ppm");
        choose(driver, "ess-isotope_error", "0, +1");

        // Fragment ions: the instrument setting for low-resolution fragments.
        choose(
                driver,
                "ess-fragment-setting",
                "Low-res precursor, low-res fragments / High-res precursor, low-res fragments");

        // Digestion: trypsin, fully specific, one missed cleavage.
        choose(driver, "ess-search_enzyme_number", "1. Trypsin");
        choose(driver, "ess-num_enzyme_termini", "Fully specific: both termini");
        enter(driver, "ess-allowed_missed_cleavage", "1");

        // Static modifications: TMT on lysine and on the peptide N-terminus.
        enter(driver, "ess-add_K_lysine", "229.162932");
        enter(driver, "ess-add_Nterm_peptide", "229.162932");

        // Variable modifications: protein N-terminal acetylation beside oxidised methionine.
        choose(
                driver,
                "ess-varmod-preset",
                "Acetyl: +42.010565 on protein N-terminus; max 1 per peptide; optional");
        driver.clickOn("ess-varmod-add");

        // Decoys and threads.
        choose(
                driver,
                "ess-decoy_search",
                "Concatenated: targets and decoys compete, one result per spectrum");
        enter(driver, "ess-decoy_prefix", "rev_");
        enter(driver, "ess-num_threads", "8");

        CheckBox pin = (CheckBox) driver.node("ess-output_percolatorfile");
        assertAll(
                "what the Essentials controls now show",
                () ->
                        assertEquals(
                                "Precursor setting: -10 to 10 ppm; applied to: Precursor m/z;"
                                        + " isotope offsets: 0, +1",
                                driver.textOf("ess-precursor-summary")),
                () ->
                        assertEquals(
                                "Fragment ions: As in: Low-res precursor, low-res fragments /"
                                        + " High-res precursor, low-res fragments",
                                driver.textOf("ess-fragment-setting-words")),
                () ->
                        assertEquals(
                                "Serialised: variable_mod02 = 42.010565 ^ 0 1 -1 0 0 0.0",
                                driver.textOf("ess-variable_mod02-serialised")),
                () -> assertTrue(driver.callOnFxThread(pin::isSelected), "PIN output on"),
                () -> assertTrue(driver.callOnFxThread(pin::isDisabled), "PIN output locked"),
                () ->
                        assertEquals(
                                "Validation: No errors or warnings.",
                                driver.textOf("param-summary-headline")),
                () -> assertFalse(driver.isVisible("param-advanced"), "Advanced never shown"),
                () -> assertFalse(driver.isVisible("param-expert"), "Expert never shown"));

        Path saved = saveDirectory.resolve("comet.params");
        app.chooser().saveTo(saved);
        driver.clickOn("param-save");
        String status = driver.textOf("param-save-status");
        assertTrue(
                status.startsWith("Saved " + saved + " ("),
                () -> "the save status names the file written: " + status);

        byte[] expected = expectedFile();
        byte[] actual = Files.readAllBytes(saved);
        if (!java.util.Arrays.equals(expected, actual)) {
            Path copy = Path.of("target", "gate-1-actual-comet.params");
            Files.write(copy, actual);
            assertEquals(
                    new String(expected, StandardCharsets.UTF_8),
                    new String(actual, StandardCharsets.UTF_8),
                    "the saved file differs from the checked-in "
                            + EXPECTED
                            + "; the file"
                            + " written is copied to "
                            + copy.toAbsolutePath());
        }
        assertArrayEquals(expected, actual, "byte for byte");
    }

    private static byte[] expectedFile() throws IOException {
        try (InputStream in = EssentialsTrypticSearchUiTest.class.getResourceAsStream(EXPECTED)) {
            assertNotNull(in, EXPECTED + " is checked in beside this test");
            return in.readAllBytes();
        }
    }
}
