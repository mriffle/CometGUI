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

package org.cometgui.workflow.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.pdv.PdvFigureException;
import org.cometgui.tools.pdv.PdvSpectrumSupport;
import org.cometgui.tools.pdv.UnsupportedSpectrumFileException;
import org.cometgui.workflow.pdv.PdvFixtures.Behaviour;
import org.cometgui.workflow.pdv.PdvFixtures.FakePdv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drawing a PSM's spectrum: the mapping from the PSM to one spectrum, the refusals made before
 * anything is launched, and the launch through the one recording path -- with PDV behind the
 * process seam ({@link FakePdv}), which writes what the real one writes. The real PDV draws in the
 * application's GUI test.
 */
class PsmFiguresTest {

    private static final String PEPTIDE = "FNLTYVSHDGDDK";

    @TempDir private Path directory;

    private Path jar;

    private RunLayout run;

    private Path spectra;

    private RunIdentity identity;

    @BeforeEach
    void build() throws IOException {
        jar = PdvFixtures.jar(directory.resolve("tools/PDV-2.7.0.jar"), "PDV", "2.7.0");
        run = PdvFixtures.run(directory.resolve("project"), PdvFixtures.RUN_ID);
        spectra =
                PdvFixtures.mgf(
                        directory.resolve("inputs/k562_3.mgf"),
                        PdvFixtures.SCAN_101,
                        PdvFixtures.SCAN_102);
        identity = PdvFixtures.identity(PdvFixtures.RUN_ID, spectra);
        Files.writeString(run.pepXmlFile("k562_3"), "<msms_pipeline_analysis/>\n");
    }

    private PsmFigures figures(FakePdv pdv) {
        return new PsmFigures(
                new PdvLaunches(
                        pdv,
                        new StreamingHashService(),
                        SecretRedactor.patternsOnly(),
                        Clock.systemUTC(),
                        new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64)),
                Duration.ofSeconds(30));
    }

    private PsmFigureRequest request(String psmId) {
        return request(psmId, identity, run);
    }

    private PsmFigureRequest request(String psmId, RunIdentity of, RunLayout searched) {
        return new PsmFigureRequest(
                run,
                of,
                searched,
                psmId,
                new PdvInstallation(ToolVersion.parse("2.7.0"), jar),
                new JavaRuntime(Path.of(System.getProperty("java.home"), "bin", "java")),
                Map.of("DISPLAY", ":7"));
    }

    private String psm(long scan) {
        return run.cometOutputBase("k562_3") + "_" + scan + "_2_1";
    }

    @Test
    @DisplayName(
            "a PSM is drawn from the spectrum whose native id carries its scan, recorded first,"
                    + " in a new directory under the run")
    void aPsmIsDrawn() throws IOException {
        FakePdv pdv = new FakePdv(run, Behaviour.DRAWS, PEPTIDE);

        DrawnSpectrum drawn = figures(pdv).draw(request(psm(102)));

        Path work = run.root().resolve("pdv/figures/0001");
        assertAll(
                () -> assertEquals(PdvFixtures.SCAN_102, drawn.nativeId()),
                () -> assertEquals(PEPTIDE, drawn.peptide()),
                () -> assertEquals(psm(102), drawn.psmId()),
                () -> assertEquals(spectra, drawn.spectrumFile()),
                () -> assertEquals(run.pepXmlFile("k562_3"), drawn.pepXml()),
                () ->
                        assertEquals(
                                work.resolve("figures/" + PEPTIDE + PdvFixtures.SCAN_102 + ".png"),
                                drawn.figure()),
                () -> assertEquals(800, drawn.width()),
                () -> assertEquals(400, drawn.height()),
                () ->
                        assertEquals(
                                List.of(PdvFixtures.SCAN_102),
                                Files.readAllLines(
                                        work.resolve("spectrum-ids.txt"), StandardCharsets.UTF_8)),
                () -> assertEquals(List.of(1), pdv.eventsAtStart()),
                () -> assertEquals(List.of(true), pdv.lastEventWasThisCommand()));
        Map<String, String> payload = PdvFixtures.launches(run).get(0).payload();
        assertAll(
                () -> assertEquals(psm(102), payload.get("launch.psm-id")),
                () -> assertEquals(PdvFixtures.SCAN_102, payload.get("launch.native-id")),
                () -> assertEquals(spectra.toString(), payload.get("launch.spectrum-file")),
                () ->
                        assertEquals(
                                run.pepXmlFile("k562_3").toString(), payload.get("launch.pepxml")),
                () -> assertEquals("cli-figure", payload.get("pdv.launch")),
                () ->
                        assertEquals(
                                PdvFixtures.digest(jar, "SHA-256"), payload.get("pdv.jar.sha256")),
                () -> assertEquals(work.toString(), payload.get("process.working-directory")),
                () ->
                        assertEquals(
                                ":7",
                                pdv.started().get(0).environment().get("DISPLAY"),
                                "PDV is given the request's display"));

        DrawnSpectrum again = figures(pdv).draw(request(psm(101)));

        assertEquals(PdvFixtures.SCAN_101, again.nativeId());
        assertTrue(
                again.figure().startsWith(run.root().resolve("pdv/figures/0002")),
                () -> "a second drawing has its own directory: " + again.figure());
        assertEquals(2, PdvFixtures.launches(run).size());
    }

    @Test
    @DisplayName("a launch whose PDV fails still left its record, and PDV's failure is stated")
    void aFailedLaunchLeavesItsRecord() throws IOException {
        FakePdv pdv = new FakePdv(run, Behaviour.EXITS_NON_ZERO, PEPTIDE);

        PdvFigureException failed =
                assertThrows(PdvFigureException.class, () -> figures(pdv).draw(request(psm(101))));

        assertEquals(PdvFigureException.Failure.EXITED_NON_ZERO, failed.failure());
        assertTrue(failed.getMessage().startsWith("PDV exited with status 1"), failed.getMessage());
        List<ProvenanceEvent> launched = PdvFixtures.launches(run);
        assertEquals(1, launched.size(), "recorded although PDV failed");
        assertEquals(pdv.started().get(0).argv(), PdvFixtures.argv(launched.get(0)));
    }

    @Test
    @DisplayName(
            "an unsupported spectrum file gives PdvSpectrumSupport's own error before anything is"
                    + " launched or recorded")
    void anUnsupportedFileLaunchesNothing() throws IOException {
        Path mzxml = Files.writeString(directory.resolve("inputs/k562_9.mzXML"), "<mzXML/>\n");
        RunIdentity other = PdvFixtures.identity(PdvFixtures.RUN_ID, mzxml);
        Files.writeString(run.pepXmlFile("k562_9"), "<msms_pipeline_analysis/>\n");
        FakePdv pdv = new FakePdv(run, Behaviour.DRAWS, PEPTIDE);
        String expected =
                assertThrows(
                                UnsupportedSpectrumFileException.class,
                                () ->
                                        PdvSpectrumSupport.forVersion(ToolVersion.parse("2.7.0"))
                                                .check(mzxml))
                        .getMessage();

        UnsupportedSpectrumFileException refused =
                assertThrows(
                        UnsupportedSpectrumFileException.class,
                        () ->
                                figures(pdv)
                                        .draw(
                                                request(
                                                        run.cometOutputBase("k562_9") + "_5_2_1",
                                                        other,
                                                        run)));

        assertEquals(expected, refused.getMessage());
        assertTrue(refused.getMessage().contains("cannot be visualised by PDV 2.7.0"));
        assertNothingHappened(pdv);
    }

    @Test
    @DisplayName("a scan no spectrum carries has no documented mapping: refused, naming it")
    void anAbsentScanIsRefused() {
        FakePdv pdv = new FakePdv(run, Behaviour.DRAWS, PEPTIDE);

        SpectrumNotDrawnException refused =
                assertThrows(
                        SpectrumNotDrawnException.class, () -> figures(pdv).draw(request(psm(7))));

        assertEquals(SpectrumNotDrawnException.Reason.NO_DOCUMENTED_MAPPING, refused.reason());
        assertEquals(
                "PSM "
                        + psm(7)
                        + " names scan 7 of "
                        + spectra
                        + ", and no spectrum there carries that scan number in its native id"
                        + " (scan=7), so there is no documented mapping from the PSM to one"
                        + " spectrum and nothing was drawn.",
                refused.getMessage());
        assertNothingHappened(pdv);
    }

    @Test
    @DisplayName("a scan two spectra carry is ambiguous: refused, naming how many")
    void anAmbiguousScanIsRefused() throws IOException {
        Path twice =
                PdvFixtures.mgf(
                        directory.resolve("inputs2/k562_3.mgf"),
                        PdvFixtures.SCAN_101,
                        "file=x scan=101");
        RunIdentity other = PdvFixtures.identity(PdvFixtures.RUN_ID, twice);
        FakePdv pdv = new FakePdv(run, Behaviour.DRAWS, PEPTIDE);

        SpectrumNotDrawnException refused =
                assertThrows(
                        SpectrumNotDrawnException.class,
                        () -> figures(pdv).draw(request(psm(101), other, run)));

        assertEquals(SpectrumNotDrawnException.Reason.NO_DOCUMENTED_MAPPING, refused.reason());
        assertTrue(
                refused.getMessage().contains(", and 2 spectra there carry that scan number"),
                refused.getMessage());
        assertNothingHappened(pdv);
    }

    @Test
    @DisplayName("a PSM identifier without a spectrum reference is refused")
    void noReference() {
        FakePdv pdv = new FakePdv(run, Behaviour.DRAWS, PEPTIDE);

        SpectrumNotDrawnException refused =
                assertThrows(
                        SpectrumNotDrawnException.class, () -> figures(pdv).draw(request("psm84")));

        assertEquals(SpectrumNotDrawnException.Reason.NO_SPECTRUM_REFERENCE, refused.reason());
        assertEquals(
                "The PSM \"psm84\" names no spectrum: its identifier has no file base, scan and"
                        + " charge, so no spectrum can be drawn for it.",
                refused.getMessage());
        assertNothingHappened(pdv);
    }

    @Test
    @DisplayName("a PSM searched as a base none of the run's files has is refused")
    void anotherBase() {
        FakePdv pdv = new FakePdv(run, Behaviour.DRAWS, PEPTIDE);
        String foreign = directory.resolve("elsewhere/outputs/comet/k562_3") + "_101_2_1";

        SpectrumNotDrawnException refused =
                assertThrows(
                        SpectrumNotDrawnException.class, () -> figures(pdv).draw(request(foreign)));

        assertEquals(SpectrumNotDrawnException.Reason.NO_SPECTRUM_FILE, refused.reason());
        assertEquals(
                "The PSM \""
                        + foreign
                        + "\" was searched as "
                        + directory.resolve("elsewhere/outputs/comet/k562_3")
                        + ", which is none of run "
                        + PdvFixtures.RUN_ID
                        + "'s spectrum files, so there is no spectrum file to draw it from.",
                refused.getMessage());
        assertNothingHappened(pdv);
    }

    @Test
    @DisplayName("a missing pepXML is refused, naming it")
    void noPepXml() throws IOException {
        Files.delete(run.pepXmlFile("k562_3"));
        FakePdv pdv = new FakePdv(run, Behaviour.DRAWS, PEPTIDE);

        SpectrumNotDrawnException refused =
                assertThrows(
                        SpectrumNotDrawnException.class,
                        () -> figures(pdv).draw(request(psm(101))));

        assertEquals(SpectrumNotDrawnException.Reason.NO_PEPXML, refused.reason());
        assertEquals(
                "The Comet pepXML "
                        + run.pepXmlFile("k562_3")
                        + " that PDV draws PSM "
                        + psm(101)
                        + " from is missing, so its spectrum cannot be drawn.",
                refused.getMessage());
        assertNothingHappened(pdv);
    }

    @Test
    @DisplayName(
            "a derived run draws from the pepXML of the run it was derived from, and records the"
                    + " launch in its own event log")
    void aDerivedRunDrawsFromItsSource() throws IOException {
        RunLayout source = PdvFixtures.run(directory.resolve("project"), "source-run");
        Files.writeString(source.pepXmlFile("k562_3"), "<msms_pipeline_analysis/>\n");
        Files.delete(run.pepXmlFile("k562_3"));
        FakePdv pdv = new FakePdv(run, Behaviour.DRAWS, PEPTIDE);
        String psmId = source.cometOutputBase("k562_3") + "_101_2_1";

        DrawnSpectrum drawn = figures(pdv).draw(request(psmId, identity, source));

        assertEquals(source.pepXmlFile("k562_3"), drawn.pepXml());
        assertEquals(1, PdvFixtures.launches(run).size());
        assertEquals(List.of(), PdvFixtures.launches(source));
    }

    private void assertNothingHappened(FakePdv pdv) {
        assertAll(
                () -> assertEquals(List.of(), pdv.started(), "nothing was launched"),
                () -> assertFalse(Files.exists(run.eventLogFile()), "nothing was recorded"),
                () ->
                        assertFalse(
                                Files.exists(run.root().resolve("pdv")),
                                "no PDV directory was made"));
    }
}
