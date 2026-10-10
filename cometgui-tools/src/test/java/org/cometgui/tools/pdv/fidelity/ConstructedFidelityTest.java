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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.tools.pdv.PdvMzTabExporter;
import org.cometgui.tools.pdv.PdvMzTabRequest;
import org.cometgui.tools.pdv.PdvMzTabSource;
import org.cometgui.tools.pdv.SpectrumFileFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase 11 exit gate items 6 and 7 on a constructed run ({@link ConstructedRun}) that holds what
 * the K562 run lacks: {@code decoy_search = 2} with shared {@code PSMId}s, an N-terminal
 * modification, a residue with a static and a variable delta, {@code 15.994900}, several proteins,
 * tied hits, a query without a retention time, and 32-bit peaks.
 */
class ConstructedFidelityTest {

    private static Path work;
    private static ConstructedRun run;
    private static FidelityCheck check;

    @BeforeAll
    static void exportOnce() throws IOException {
        work = FidelityFiles.workDirectory("fidelity-made-");
        run = new ConstructedRun(work.resolve("sources"));
        run.write();
        PdvMzTabExporter.export(request(run, work.resolve("mztab")));
        check =
                new FidelityCheck(
                        run.file("psms.target.txt"),
                        run.file("psms.decoy.txt"),
                        run.sources(),
                        work.resolve("mztab").resolve("psms.mztab"));
    }

    static PdvMzTabRequest request(ConstructedRun constructed, Path directory) {
        List<PdvMzTabSource> sources = new ArrayList<>();
        for (FidelityCheck.Source source : constructed.sources()) {
            sources.add(
                    new PdvMzTabSource(
                            source.base(),
                            source.spectra(),
                            SpectrumFileFormat.MZML,
                            source.pepXml(),
                            Optional.of(source.decoyPepXml())));
        }
        return new PdvMzTabRequest(
                constructed.file("psms.target.txt"),
                Optional.of(constructed.file("psms.decoy.txt")),
                sources,
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

    private static Map<String, String> row(String table, String psmId) {
        for (Map<String, String> row : check.mzTab().rows()) {
            if (row.get(FidelityCheck.DECOY).equals(table)
                    && row.get(FidelityCheck.PSM_ID).equals(psmId)) {
                return row;
            }
        }
        throw new AssertionError("no row " + table + "/" + psmId);
    }

    private static Map<Integer, List<BigDecimal>> proForma(Map<String, String> row) {
        return Peptidoforms.proForma(row.get(FidelityCheck.PROFORMA)).sortedDeltas();
    }

    private static List<BigDecimal> masses(String... masses) {
        List<BigDecimal> list = new ArrayList<>();
        for (String mass : masses) {
            list.add(new BigDecimal(mass).stripTrailingZeros());
        }
        list.sort(BigDecimal::compareTo);
        return list;
    }

    @Test
    @DisplayName("every gate-6 and gate-7 check passes on the constructed run, 9 PSMs")
    void everyCheckPasses() throws IOException {
        assertEquals(9, check.sourceRows().size());
        passes(check.sourcesJoin(), 9);
        passes(check.shape(), 9);
        passes(check.completeness(), 18);
        passes(check.scores(), 9);
        passes(check.charge(), 9);
        passes(check.observedMz(), 9);
        passes(check.retentionTime(), 9);
        passes(check.accessions(), 9);
        passes(check.sequence(), 9);
        passes(check.modificationsAgainstPepXml(), 9);
        passes(check.modificationsAgainstPercolator(), 9);
        passes(check.fixedModifications(), 9);
        passes(check.nulls(), 9L * FidelityCheck.NULL_COLUMNS.size());
        long peaks = 0;
        for (FidelityCheck.Joined joined : check.joined().values()) {
            peaks += 2L * joined.spectrum().mz().length;
        }
        passes(check.spectraRef(), peaks);
    }

    @Test
    @DisplayName(
            "decoy_search = 2: a target and a decoy row share a PSMId, and each is joined to its"
                    + " own pepXML")
    void sharedPsmIds() {
        Map<String, String> target = row("0", ConstructedRun.BASE_A + "_701_2_1");
        Map<String, String> decoy = row("1", ConstructedRun.BASE_A + "_701_2_1");
        assertAll(
                () -> assertEquals("PEPTIDEK", target.get("sequence")),
                () -> assertEquals("KEDITPEP", decoy.get("sequence")),
                () ->
                        assertEquals(
                                "DECOY_sp|P00001|ONE_INVENTED;DECOY_sp|P00009|NINE_INVENTED",
                                decoy.get("accession"),
                                "Percolator's proteins, not the pepXML's undecorated"
                                        + " alternative"));
    }

    @Test
    @DisplayName("the parsed modifications the suite expects are the ones the run states")
    void parsedModifications() {
        assertAll(
                () ->
                        assertEquals(
                                Map.of(0, masses("42.010565")),
                                proForma(row("0", ConstructedRun.BASE_A + "_701_2_1")),
                                "a variable N-terminal acetyl from mod_nterm_mass 43.018390"),
                () ->
                        assertEquals(
                                Map.of(
                                        2, masses("57.021464", "15.994915"),
                                        3, masses("15.994900")),
                                proForma(row("0", ConstructedRun.BASE_A + "_701_2_2")),
                                "static and variable on one cysteine; 15.994900 as Comet prints"),
                () ->
                        assertEquals(
                                Map.of(1, masses("57.021464"), 2, masses("57.021464")),
                                proForma(row("0", ConstructedRun.BASE_A + "_705_2_1")),
                                "fixed modifications explicit on every cysteine"),
                () ->
                        assertEquals(
                                Map.of(0, masses("42.010565"), 1, masses("15.994900")),
                                proForma(row("1", ConstructedRun.BASE_A + "_703_2_1")),
                                "a decoy's N-terminal and residue modifications"),
                () ->
                        assertEquals(
                                Map.of(), proForma(row("0", ConstructedRun.BASE_B + "_9002_2_1"))),
                () ->
                        assertEquals(
                                "null",
                                row("0", ConstructedRun.BASE_B + "_9002_2_1")
                                        .get("modifications")));
    }

    @Test
    @DisplayName("a query without retention_time_sec gives null, not a value from the spectrum")
    void missingRetentionTimeIsNull() {
        assertAll(
                () ->
                        assertEquals(
                                "null",
                                row("0", ConstructedRun.BASE_A + "_702_2_1").get("retention_time")),
                () ->
                        assertEquals(
                                "4797.0319",
                                row("0", ConstructedRun.BASE_A + "_705_2_1")
                                        .get("retention_time")));
    }

    @Test
    @DisplayName(
            "tied hits: PSMId rank 3 is the third search_hit, whose hit_rank is 2, not the"
                    + " second")
    void positionNotHitRank() {
        assertEquals("LLGLLK", row("0", ConstructedRun.BASE_A + "_701_2_3").get("sequence"));
    }

    @Test
    @DisplayName(
            "ms_run numbers the sources with PSMs in source order, skipping one without any, and"
                    + " position and scan differ in each")
    void msRunsAndPositions() {
        assertAll(
                () ->
                        assertTrue(
                                row("0", ConstructedRun.BASE_B + "_9002_2_1")
                                        .get("spectra_ref")
                                        .startsWith("ms_run[2]:")),
                () ->
                        assertTrue(
                                row("1", ConstructedRun.BASE_A + "_703_2_1")
                                        .get("spectra_ref")
                                        .startsWith("ms_run[1]:")),
                () -> assertTrue(!check.mzTab().metadata().containsKey("ms_run[3]-location")));
        for (FidelityCheck.Joined joined : check.joined().values()) {
            assertTrue(
                    MzMlFile.scanOf(joined.spectrum().id()) != joined.spectrum().position(),
                    joined.spectrum().id() + " is at position " + joined.spectrum().position());
        }
    }
}
