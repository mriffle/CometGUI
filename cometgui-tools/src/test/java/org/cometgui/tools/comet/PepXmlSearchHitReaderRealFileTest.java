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

package org.cometgui.tools.comet;

import static org.cometgui.tools.comet.PepXmlShapes.d;
import static org.cometgui.tools.comet.PepXmlShapes.od;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.tools.comet.PepXmlShapes.Recorder;
import org.cometgui.tools.testing.K562Run;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PepXmlSearchHitReader} and {@link PepXmlHitIndex} on the real K562 pepXML files Comet
 * 2026.02.2 wrote on Phase 00's scientific path, and Percolator 3.07.1's PSMs from them ({@code
 * D-006}: local, gitignored, held to their SHA-256 by {@link K562Run}; a missing one fails, nothing
 * skips).
 *
 * <p>Every value the reader delivers is compared with an independent reading of the same file,
 * {@link PepXmlLines}, which uses no XML parser; a hand-checked sample is typed from the file; and
 * every Percolator PSM of each file is joined through the index and its peptide and modifications
 * checked against Percolator's own text -- which is also where the spike's two source facts are
 * re-established from product code: hit_rank differs from the position for 81 and 99 target PSMs,
 * and the pepXML drops DECOY_ from an alternative protein for 31 and 12.
 */
class PepXmlSearchHitReaderRealFileTest {

    private static final Pattern PSM_ID = Pattern.compile("(.+)_(\\d+)_(\\d+)_(\\d+)");

    private static final Pattern PERCOLATOR_PEPTIDE = Pattern.compile("(.)\\.(.+)\\.(.)");

    private static final Pattern RESIDUE = Pattern.compile("([A-Z])(?:\\[([^]]*)\\])?");

    /** One Percolator row, read here with a tab split. */
    private record Row(
            String psmId,
            String base,
            PepXmlHitKey key,
            String prev,
            String residues,
            String next,
            List<int[]> variablePositions,
            List<String> variableMasses,
            List<String> proteins) {}

    private static List<Row> percolatorRows(Path file, String base) throws IOException {
        List<Row> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds", lines.get(0));
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\t", -1);
            Matcher id = PSM_ID.matcher(fields[0]);
            assertTrue(id.matches(), fields[0]);
            if (!id.group(1).equals(base)) {
                continue;
            }
            Matcher peptide = PERCOLATOR_PEPTIDE.matcher(fields[4]);
            assertTrue(peptide.matches(), fields[4]);
            StringBuilder residues = new StringBuilder();
            List<int[]> positions = new ArrayList<>();
            List<String> masses = new ArrayList<>();
            Matcher residue = RESIDUE.matcher(peptide.group(2));
            int consumed = 0;
            while (residue.find()) {
                assertEquals(consumed, residue.start(), fields[4]);
                consumed = residue.end();
                residues.append(residue.group(1));
                if (residue.group(2) != null) {
                    positions.add(new int[] {residues.length()});
                    masses.add(residue.group(2));
                }
            }
            assertEquals(peptide.group(2).length(), consumed, fields[4]);
            rows.add(
                    new Row(
                            fields[0],
                            id.group(1),
                            new PepXmlHitKey(
                                    Long.parseLong(id.group(2)),
                                    Integer.parseInt(id.group(3)),
                                    Integer.parseInt(id.group(4))),
                            peptide.group(1),
                            residues.toString(),
                            peptide.group(3),
                            positions,
                            masses,
                            List.of(fields).subList(5, fields.length)));
        }
        return rows;
    }

    private static Recorder read(Path pepXml) throws CometOutputException {
        Recorder recorder = new Recorder();
        PepXmlSearchHitReader.read(pepXml, recorder);
        return recorder;
    }

    @Test
    @DisplayName(
            "K562_3 and _4: every value equals an independent reading; the counts are the file's")
    void everyValue() throws IOException {
        long[][] counts = {{728, 3638, 1956, 489, 1454}, {607, 3032, 1766, 326, 1268}};
        Path[] files = {K562Run.pepXml(), K562Run.pepXml4()};
        for (int f = 0; f < files.length; f++) {
            Recorder recorder = read(files[f]);
            PepXmlLines.Document lines = PepXmlLines.read(files[f]);
            PepXmlLines.assertSame(lines, recorder.queries());
            PepXmlLines.assertDeclarations(lines, recorder.summaries().get(0));
            List<PepXmlSearchHit> hits =
                    recorder.queries().stream().flatMap(v -> v.hits().stream()).toList();
            assertEquals(counts[f][0], recorder.queries().size(), files[f] + ": queries");
            assertEquals(counts[f][1], hits.size(), files[f] + ": hits");
            assertEquals(
                    counts[f][2],
                    hits.stream()
                            .flatMap(h -> h.modificationInfo().stream())
                            .mapToLong(m -> m.residues().size())
                            .sum(),
                    files[f] + ": mod_aminoacid_mass");
            assertEquals(
                    counts[f][3],
                    hits.stream().mapToLong(h -> h.alternativeProteins().size()).sum(),
                    files[f] + ": alternative_protein");
            assertEquals(
                    counts[f][4],
                    hits.stream().filter(h -> h.modificationInfo().isPresent()).count(),
                    files[f] + ": modification_info");
            assertTrue(
                    hits.stream()
                            .flatMap(h -> h.modificationInfo().stream())
                            .allMatch(m -> m.ntermMass().isEmpty() && m.ctermMass().isEmpty()),
                    "this search declared no terminal modification");
        }
    }

    @Test
    @DisplayName("K562_3 by hand: the header and the first query, typed from the file")
    void handChecked() throws IOException {
        Recorder recorder = read(K562Run.pepXml());
        PepXmlSearchSummary summary = recorder.summaries().get(0);
        assertEquals(
                "/workspace/scratch/scientific-path/mzml-lf/20100614_Velos1_TaGe_SA_K562_3.mzML",
                summary.input());
        assertEquals(
                "/workspace/scratch/scientific-path/comet/20100614_Velos1_TaGe_SA_K562_3",
                summary.outputBase());
        assertEquals("2026.02 rev. 2 (6edec91)", summary.searchEngineVersion());
        assertEquals(
                List.of(
                        new PepXmlAminoAcidModification(
                                "M", d("15.994915"), d("147.035400"), true, false),
                        new PepXmlAminoAcidModification(
                                "C", d("57.021464"), d("160.030649"), false, false)),
                summary.aminoAcidModifications());
        assertEquals(List.of(), summary.terminalModifications());

        PepXmlShapes.Visited first = recorder.queries().get(0);
        assertEquals(
                new PepXmlQuery(
                        "20100614_Velos1_TaGe_SA_K562_3.11188.11188.2",
                        Optional.of("controllerType=0 controllerNumber=1 scan=11188"),
                        11188,
                        11188,
                        d("1229.562229"),
                        2,
                        1,
                        od("4741.0")),
                first.query());
        PepXmlSearchHit top = first.hits().get(0);
        assertEquals("WIDNPTVDDR", top.peptide());
        assertEquals("sp|P18206|VINC_HUMAN", top.protein());
        assertEquals("1229.567666", top.calcNeutralPepMass().text());
        assertEquals("-0.005436", top.massDiff().text());
        assertEquals(Optional.of("2.91E-09"), top.searchScore("expect"));
        assertEquals(
                new PepXmlResidueModification(
                        9,
                        'M',
                        d("147.035400"),
                        Optional.empty(),
                        od("15.994915"),
                        Optional.of("param"),
                        Optional.empty()),
                first.hits().get(1).modificationInfo().orElseThrow().residues().get(0));
        assertEquals(
                new PepXmlResidueModification(
                        6,
                        'C',
                        d("160.030649"),
                        od("57.021464"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                first.hits().get(2).modificationInfo().orElseThrow().residues().get(0));
    }

    @Test
    @DisplayName("K562_3 scan 11262 z3: two hits tied at hit_rank 3 are positions 3 and 4")
    void tiedHits() throws IOException {
        PepXmlHitIndex index =
                PepXmlHitIndex.of(
                        K562Run.pepXml(),
                        Set.of(
                                new PepXmlHitKey(11262, 3, 3),
                                new PepXmlHitKey(11262, 3, 4),
                                new PepXmlHitKey(11262, 3, 5)));
        PepXmlSearchHit third = index.find(new PepXmlHitKey(11262, 3, 3)).orElseThrow().hit();
        PepXmlSearchHit fourth = index.find(new PepXmlHitKey(11262, 3, 4)).orElseThrow().hit();
        PepXmlSearchHit fifth = index.find(new PepXmlHitKey(11262, 3, 5)).orElseThrow().hit();
        assertEquals(List.of(3, 3, 4), List.of(third.hitRank(), fourth.hitRank(), fifth.hitRank()));
        assertEquals("DDPESLPSLLEKEEMPSFMR", third.peptide());
        assertEquals("DDPESLPSLLEKEEMPSFMR", fourth.peptide());
        // Percolator: _11262_3_3 is ...EEMPSFM[15.9949]R, _11262_3_4 is ...EEM[15.9949]PSFMR.
        assertEquals(19, third.modificationInfo().orElseThrow().residues().get(0).position());
        assertEquals(15, fourth.modificationInfo().orElseThrow().residues().get(0).position());
        assertEquals("THFGGGKTTGFGMIYDSLDYAK", fifth.peptide());
        assertEquals(
                "4764.4",
                index.find(new PepXmlHitKey(11262, 3, 5))
                        .orElseThrow()
                        .query()
                        .retentionTimeSec()
                        .orElseThrow()
                        .text());
    }

    @Test
    @DisplayName("K562_3 scan 11911 z2: the alternative protein as the pepXML states it, no DECOY_")
    void decoyPrefixDropped() throws IOException {
        PepXmlSearchHit hit =
                PepXmlHitIndex.of(K562Run.pepXml(), Set.of(new PepXmlHitKey(11911, 2, 1)))
                        .find(new PepXmlHitKey(11911, 2, 1))
                        .orElseThrow()
                        .hit();
        assertEquals(List.of("sp|P24752|THIL_HUMAN", "sp|Q9NVE4|CCD87_HUMAN"), hit.proteins());
        assertEquals(2, hit.numTotProteins());
    }

    @Test
    @DisplayName("every Percolator PSM of each file joins to its hit by scan, charge and position")
    void percolatorJoin() throws IOException {
        Path[] files = {K562Run.pepXml(), K562Run.pepXml4()};
        int[] targets = {2130, 1767};
        int[] decoys = {1508, 1265};
        int[] tied = {81, 99};
        int[] prefixDropped = {31, 12};
        for (int f = 0; f < files.length; f++) {
            String outputBase = read(files[f]).summaries().get(0).outputBase();
            String base = outputBase.substring(outputBase.lastIndexOf('/') + 1);
            List<Row> targetRows = percolatorRows(K562Run.percolatorTargets(), base);
            List<Row> decoyRows = percolatorRows(K562Run.percolatorDecoys(), base);
            assertEquals(targets[f], targetRows.size(), base + ": target PSMs");
            assertEquals(decoys[f], decoyRows.size(), base + ": decoy PSMs");
            List<Row> all = new ArrayList<>(targetRows);
            all.addAll(decoyRows);
            Set<PepXmlHitKey> keys = new HashSet<>();
            all.forEach(r -> keys.add(r.key()));
            assertEquals(all.size(), keys.size(), "a PSMId names one hit");
            PepXmlHitIndex index = PepXmlHitIndex.of(files[f], keys);
            assertEquals(Set.of(), index.missing(), base);
            assertEquals(all.size(), index.size(), base);
            for (Row row : all) {
                PepXmlPsm psm = index.find(row.key()).orElseThrow();
                assertJoined(row, psm);
            }
            assertEquals(
                    tied[f],
                    targetRows.stream()
                            .filter(
                                    r ->
                                            index.find(r.key()).orElseThrow().hit().hitRank()
                                                    != r.key().position())
                            .count(),
                    base + ": target PSMs whose hit_rank is not their position");
            long dropped = 0;
            for (Row row : targetRows) {
                List<String> pepXml = index.find(row.key()).orElseThrow().hit().proteins();
                if (!new HashSet<>(row.proteins()).equals(new HashSet<>(pepXml))) {
                    dropped++;
                    List<String> stripped = new ArrayList<>();
                    stripped.add(pepXml.get(0));
                    for (String p : row.proteins()) {
                        if (!p.equals(pepXml.get(0))) {
                            stripped.add(p.startsWith("DECOY_") ? p.substring(6) : p);
                        }
                    }
                    assertEquals(
                            new HashSet<>(stripped),
                            new HashSet<>(pepXml),
                            row.psmId() + ": the only difference is a dropped DECOY_");
                }
            }
            assertEquals(prefixDropped[f], dropped, base + ": DECOY_ dropped on an alternative");
        }
    }

    /** The joined hit is the PSM Percolator names: flanks, residues, variable modifications. */
    private static void assertJoined(Row row, PepXmlPsm psm) {
        PepXmlSearchHit hit = psm.hit();
        assertEquals(row.key(), psm.key(), row.psmId());
        assertEquals(row.prev(), hit.peptidePrevAa(), row.psmId());
        assertEquals(row.residues(), hit.peptide(), row.psmId());
        assertEquals(row.next(), hit.peptideNextAa(), row.psmId());
        List<PepXmlResidueModification> variable =
                hit.modificationInfo().stream()
                        .flatMap(m -> m.residues().stream())
                        .filter(m -> m.variableDelta().isPresent())
                        .toList();
        assertEquals(row.variablePositions().size(), variable.size(), row.psmId());
        for (int i = 0; i < variable.size(); i++) {
            assertEquals(
                    row.variablePositions().get(i)[0], variable.get(i).position(), row.psmId());
            BigDecimal pepXml = variable.get(i).variableDelta().orElseThrow().value();
            BigDecimal percolator = new BigDecimal(row.variableMasses().get(i));
            assertTrue(
                    pepXml.subtract(percolator).abs().compareTo(new BigDecimal("0.00005")) < 0,
                    row.psmId() + ": " + pepXml + " vs Percolator's rounded " + percolator);
        }
    }
}
