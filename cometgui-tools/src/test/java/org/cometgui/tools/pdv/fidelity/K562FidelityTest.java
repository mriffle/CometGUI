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

package org.cometgui.tools.pdv.fidelity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.tools.pdv.PdvMzTabExporter;
import org.cometgui.tools.pdv.PdvMzTabRequest;
import org.cometgui.tools.pdv.PdvMzTabSource;
import org.cometgui.tools.pdv.SpectrumFileFormat;
import org.cometgui.tools.testing.K562Run;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase 11 exit gate items 6 and 7 on the real K562 {@code _3} + {@code _4} run: Comet 2026.02.2's
 * pepXML, Percolator 3.07.1's target and decoy tables, and the two subset mzML files, exported once
 * through {@link PdvMzTabExporter#export} and compared, value by value, against those sources by
 * the suite's own readers ({@link FidelityCheck}; design decision P11-9).
 *
 * <p>{@code D-006}: the export is written under the build directory and deleted afterwards.
 */
class K562FidelityTest {

    static final String BASE_3 = "20100614_Velos1_TaGe_SA_K562_3";
    static final String BASE_4 = "20100614_Velos1_TaGe_SA_K562_4";

    private static Path work;
    private static FidelityCheck check;

    @BeforeAll
    static void exportOnce() throws IOException {
        work = FidelityFiles.workDirectory("fidelity-k562-");
        List<FidelityCheck.Source> sources = sources();
        PdvMzTabExporter.export(request(work.resolve("mztab")));
        check =
                new FidelityCheck(
                        K562Run.percolatorTargets(),
                        K562Run.percolatorDecoys(),
                        sources,
                        work.resolve("mztab").resolve("psms.mztab"));
    }

    static List<FidelityCheck.Source> sources() throws IOException {
        return List.of(
                new FidelityCheck.Source(BASE_3, K562Run.mzml(), K562Run.pepXml(), null),
                new FidelityCheck.Source(BASE_4, K562Run.mzml4(), K562Run.pepXml4(), null));
    }

    static PdvMzTabRequest request(Path directory) throws IOException {
        return new PdvMzTabRequest(
                K562Run.percolatorTargets(),
                Optional.of(K562Run.percolatorDecoys()),
                List.of(
                        new PdvMzTabSource(
                                BASE_3,
                                K562Run.mzml(),
                                SpectrumFileFormat.MZML,
                                K562Run.pepXml(),
                                Optional.empty()),
                        new PdvMzTabSource(
                                BASE_4,
                                K562Run.mzml4(),
                                SpectrumFileFormat.MZML,
                                K562Run.pepXml4(),
                                Optional.empty())),
                directory);
    }

    @AfterAll
    static void deleteTheExport() throws IOException {
        FidelityFiles.delete(work);
    }

    private static void passes(FidelityCheck.Finding finding, long expected) {
        assertAll(
                () -> assertEquals(List.of(), finding.failures(), finding.report()),
                () -> assertEquals(expected, finding.compared(), finding.report()));
    }

    private static long rows() {
        return check.sourceRows().size();
    }

    @Test
    @DisplayName(
            "the suite's own join: all 6670 K562 Percolator rows have one hit and one spectrum")
    void sourcesJoin() {
        assertEquals(3897, check.sourceRows().stream().filter(row -> !row.decoy()).count());
        assertEquals(2773, check.sourceRows().stream().filter(row -> row.decoy()).count());
        passes(check.sourcesJoin(), 6670);
        assertEquals(6670, check.joined().size());
    }

    @Test
    @DisplayName("the mzTab has the shape PDV reads: MTD, one PSH, PSM rows, no empty field")
    void shape() {
        passes(check.shape(), 6670);
    }

    @Test
    @DisplayName("gate 6: every PSM exactly once and none invented, counted both sides")
    void everyPsmExactlyOnce() {
        assertEquals(rows(), check.mzTab().rows().size());
        passes(check.completeness(), 2 * 6670);
    }

    @Test
    @DisplayName("gate 6: score, q-value and PEP are Percolator's text in every row")
    void scores() {
        passes(check.scores(), rows());
    }

    @Test
    @DisplayName("gate 6: charge is the pepXML assumed_charge in every row")
    void charge() {
        passes(check.charge(), rows());
    }

    @Test
    @DisplayName("gate 6: observed m/z is the producing spectrum's selected-ion m/z in every row")
    void observedMz() {
        passes(check.observedMz(), rows());
    }

    @Test
    @DisplayName("gate 6: retention time is the pepXML retention_time_sec, seconds, in every row")
    void retentionTime() {
        passes(check.retentionTime(), rows());
    }

    @Test
    @DisplayName("gate 6: accessions are Percolator's proteinIds, every protein in order")
    void accessions() {
        passes(check.accessions(), rows());
        long several = check.sourceRows().stream().filter(row -> row.proteins().size() > 1).count();
        assertTrue(several > 100, several + " K562 PSMs name more than one protein");
    }

    @Test
    @DisplayName("gate 6: sequence and flanks are the pepXML hit's and Percolator's")
    void sequence() {
        passes(check.sequence(), rows());
    }

    @Test
    @DisplayName("gate 6: modifications match the pepXML hit as (position, delta) values")
    void modificationsAgainstPepXml() {
        passes(check.modificationsAgainstPepXml(), rows());
    }

    @Test
    @DisplayName("gate 6: modifications match Percolator's peptide, e.g. M[15.9949]")
    void modificationsAgainstPercolator() {
        passes(check.modificationsAgainstPercolator(), rows());
        long oxidised =
                check.sourceRows().stream()
                        .filter(row -> row.peptide().contains("M[15.9949]"))
                        .count();
        assertTrue(oxidised > 100, oxidised + " K562 PSMs carry M[15.9949]");
    }

    @Test
    @DisplayName("gate 6: Comet's static +57.021464 is written on every cysteine")
    void fixedModifications() {
        passes(check.fixedModifications(), rows());
    }

    @Test
    @DisplayName("gate 6: the columns no source supplies are null in every row")
    void nulls() {
        passes(check.nulls(), rows() * FidelityCheck.NULL_COLUMNS.size());
    }

    @Test
    @DisplayName(
            "gate 7, first: in both mzML files 1-based position and scan number differ for every"
                    + " spectrum a PSM came from")
    void positionAndScanDiffer() {
        Map<String, MzMlFile.Spectrum> used = new LinkedHashMap<>();
        for (FidelityCheck.Joined joined : check.joined().values()) {
            used.put(joined.source().base() + "/" + joined.spectrum().id(), joined.spectrum());
        }
        List<String> coincide = new ArrayList<>();
        for (Map.Entry<String, MzMlFile.Spectrum> spectrum : used.entrySet()) {
            long scan = MzMlFile.scanOf(spectrum.getValue().id());
            assertTrue(scan > 0, spectrum.getKey() + " carries a scan number");
            assertEquals(
                    Integer.toString(spectrum.getValue().position() - 1),
                    spectrum.getValue().index(),
                    "the suite's position count agrees with the file's index attribute");
            if (scan == spectrum.getValue().position()) {
                coincide.add(spectrum.getKey());
            }
        }
        assertAll(
                () -> assertEquals(List.of(), coincide),
                () -> assertTrue(used.size() > 1000, used.size() + " spectra produced a PSM"),
                () ->
                        assertEquals(
                                "controllerType=0 controllerNumber=1 scan=11187",
                                check.spectra(K562Run.mzml()).spectra().get(0).id(),
                                "_3's first spectrum, at position 1, is scan 11187"),
                () ->
                        assertEquals(
                                "controllerType=0 controllerNumber=1 scan=9938",
                                check.spectra(K562Run.mzml4()).spectra().get(0).id(),
                                "_4's first spectrum, at position 1, is scan 9938"));
    }

    @Test
    @DisplayName(
            "gate 7: every row's spectra_ref, resolved as PDV resolves it, is the producing"
                    + " spectrum, peak for peak")
    void spectraRefIsTheProducingSpectrum() throws IOException {
        FidelityCheck.Finding finding = check.spectraRef();
        long peaks = 0;
        for (FidelityCheck.Joined joined : check.joined().values()) {
            peaks += 2L * joined.spectrum().mz().length;
        }
        passes(finding, peaks);
        assertTrue(peaks > 500_000, peaks + " peak values compared");
    }

    /** The mzTab with each row's {@code spectra_ref} replaced. */
    private static MzTabFile withRefs(Map<String, String> refByRow) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, String> row : check.mzTab().rows()) {
            Map<String, String> copy = new LinkedHashMap<>(row);
            String key = row.get(FidelityCheck.DECOY) + "/" + row.get(FidelityCheck.PSM_ID);
            copy.put("spectra_ref", refByRow.getOrDefault(key, row.get("spectra_ref")));
            rows.add(copy);
        }
        return new MzTabFile(check.mzTab().metadata(), check.mzTab().columns(), rows, List.of());
    }

    private static String run(Map<String, String> row) {
        String ref = row.get("spectra_ref");
        return ref.substring(0, ref.indexOf(':'));
    }

    private static FidelityCheck.Joined joinedOf(Map<String, String> row) {
        return check.joined()
                .get(
                        PercolatorTable.Row.key(
                                "1".equals(row.get(FidelityCheck.DECOY)),
                                row.get(FidelityCheck.PSM_ID)));
    }

    @Test
    @DisplayName(
            "gate 7 is falsifiable: references by file position, or by scan number, are caught")
    void positionOrScanReferencesAreCaught() throws IOException {
        Map<String, String> byPosition = new HashMap<>();
        Map<String, String> byScan = new HashMap<>();
        for (Map<String, String> row : check.mzTab().rows()) {
            String key = row.get(FidelityCheck.DECOY) + "/" + row.get(FidelityCheck.PSM_ID);
            MzMlFile.Spectrum spectrum = joinedOf(row).spectrum();
            byPosition.put(key, run(row) + ":index=" + (spectrum.position() - 1));
            byScan.put(key, run(row) + ":index=" + MzMlFile.scanOf(spectrum.id()));
        }
        FidelityCheck.Finding position = check.spectraRef(withRefs(byPosition));
        FidelityCheck.Finding scan = check.spectraRef(withRefs(byScan));
        String named = "target PSM " + BASE_3 + "_11367_2_1: ";
        assertAll(
                () -> assertTrue(position.failures().size() > rows() * 9 / 10, position.report()),
                () ->
                        assertTrue(
                                position.failures().stream().anyMatch(f -> f.startsWith(named)),
                                position.report()),
                () -> assertTrue(scan.failures().size() >= rows(), scan.report()),
                () ->
                        assertTrue(
                                scan.failures().stream()
                                        .anyMatch(
                                                f ->
                                                        f.startsWith(named)
                                                                && f.contains("is beyond")),
                                scan.report()));
    }

    @Test
    @DisplayName(
            "gate 7 is falsifiable: pointing every PSM of a shared spectrum at its first row's"
                    + " entry -- the same peaks -- is caught")
    void firstRowOfASharedSpectrumIsCaught() throws IOException {
        Map<String, String> firstRef = new HashMap<>();
        Map<String, String> shared = new HashMap<>();
        int sharing = 0;
        for (Map<String, String> row : check.mzTab().rows()) {
            String key = row.get(FidelityCheck.DECOY) + "/" + row.get(FidelityCheck.PSM_ID);
            FidelityCheck.Joined joined = joinedOf(row);
            String spectrum = joined.source().base() + "/" + joined.spectrum().id();
            String first = firstRef.putIfAbsent(spectrum, row.get("spectra_ref"));
            if (first != null) {
                shared.put(key, first);
                sharing++;
            }
        }
        FidelityCheck.Finding finding = check.spectraRef(withRefs(shared));
        List<String> notReachable =
                finding.failures().stream()
                        .filter(f -> f.contains("PDV selects only the first row"))
                        .toList();
        List<String> otherwise =
                finding.failures().stream()
                        .filter(f -> !f.contains("PDV selects only the first row"))
                        .filter(f -> !f.contains("entries for"))
                        .toList();
        String fiveOfOne = "target PSM " + BASE_3 + "_11288_4_2: ";
        int expectedSharing = sharing;
        assertAll(
                () -> assertTrue(expectedSharing > 3000, expectedSharing + " rows share"),
                () -> assertEquals(expectedSharing, notReachable.size(), finding.report()),
                () ->
                        assertEquals(
                                List.of(),
                                otherwise,
                                "the peaks are the same spectrum's, so only identity catches it"),
                () ->
                        assertTrue(
                                notReachable.stream().anyMatch(f -> f.startsWith(fiveOfOne)),
                                finding.report()),
                () -> assertFalse(finding.failures().isEmpty()),
                () -> assertNotEquals(0, finding.compared()));
    }
}
