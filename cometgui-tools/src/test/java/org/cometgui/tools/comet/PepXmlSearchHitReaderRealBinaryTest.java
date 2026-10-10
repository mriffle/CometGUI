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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.comet.PepXmlShapes.Recorder;
import org.cometgui.tools.testing.RealCometSearch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link PepXmlSearchHitReader} and {@link PepXmlHitIndex} on pepXML the pinned Comet 2026.02.2 and
 * 2026.03.0 binaries write <strong>now</strong>, under the settings that produce the forms the
 * Phase 00 files lack: {@code decoy_search = 2} (a separate {@code <base>.decoy.pep.xml}), variable
 * N- and C-terminal modifications, a static peptide C-terminal and a static protein N-terminal
 * modification, and a variable modification stacked on a static one.
 *
 * <p>Both K562 files are searched against the proteome's first 1000 records ({@link
 * RealCometSearch}). Every value the reader delivers equals {@link PepXmlLines}' independent
 * reading; the declarations are typed from a probe run of 2026.03.0 on 2026-10-10; and every PIN
 * row -- target and decoy -- joins through the index of the pepXML its label names, with the PIN's
 * own peptide, terminal and residue modifications and proteins agreeing with the hit.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "comet.linux.exe is a Linux ELF binary; the Windows and macOS Comet builds have"
                        + " never been executed anywhere in this project")
class PepXmlSearchHitReaderRealBinaryTest {

    /** The settings, each a value the binary's own {@code -q} file declares. */
    static final Map<String, String> SETTINGS =
            Map.of(
                    "decoy_search", "2",
                    "variable_mod02", "42.010565 n 0 1 -1 0 0 0.0",
                    "variable_mod03", "15.994915 C 0 1 -1 0 0 0.0",
                    "variable_mod04", "-0.984016 c 0 1 -1 0 0 0.0",
                    "add_Cterm_peptide", "14.015650",
                    "add_Nterm_protein", "28.0313");

    /**
     * The residue declarations. The methionine one is the binary's own {@code -q} default, {@code
     * variable_mod01 = 15.9949 M ...}, which Comet writes as {@code 15.994900}: the text the file
     * states, not the mass a reader might expect.
     */
    static final List<PepXmlAminoAcidModification> RESIDUE_DECLARATIONS =
            List.of(
                    new PepXmlAminoAcidModification(
                            "M", d("15.994900"), d("147.035385"), true, false),
                    new PepXmlAminoAcidModification(
                            "C", d("15.994915"), d("176.025564"), true, false),
                    new PepXmlAminoAcidModification(
                            "C", d("57.021464"), d("160.030649"), false, false));

    static final List<PepXmlTerminalModification> TERMINAL_DECLARATIONS =
            List.of(
                    new PepXmlTerminalModification(
                            PepXmlTerminus.N, d("42.010565"), d("43.018390"), true, false),
                    new PepXmlTerminalModification(
                            PepXmlTerminus.C, d("-0.984016"), d("30.034374"), true, false),
                    new PepXmlTerminalModification(
                            PepXmlTerminus.C, d("14.015650"), d("31.018390"), false, false),
                    new PepXmlTerminalModification(
                            PepXmlTerminus.N, d("28.031300"), d("29.039125"), false, true));

    /** A variable N-terminal acetyl, alone or on top of the static protein N-terminal mass. */
    static final Set<String> VARIABLE_NTERM = Set.of("43.018390", "71.049690");

    static final Set<String> NTERM = Set.of("43.018390", "71.049690", "29.039125");

    static final String VARIABLE_CTERM = "30.034374";

    static final Set<String> CTERM = Set.of("31.018390", VARIABLE_CTERM);

    private static final Pattern SPEC_ID = Pattern.compile("(.+)_(\\d+)_(\\d+)_(\\d+)");

    private static final Pattern PIN_PEPTIDE =
            Pattern.compile("(.)\\.(n\\[[^]]*\\])?(.+?)(c\\[[^]]*\\])?\\.(.)");

    private static final Pattern RESIDUE = Pattern.compile("([A-Z])(?:\\[([^]]*)\\])?");

    static Stream<RealCometSearch.Release> releases() {
        return Stream.of(RealCometSearch.NEWER, RealCometSearch.OLDER);
    }

    @ParameterizedTest(name = "Comet {0}")
    @MethodSource("releases")
    @DisplayName(
            "decoy_search = 2 and terminal modifications: read, cross-read, and joined to the PIN")
    void realSearch(RealCometSearch.Release release, @TempDir Path root) throws IOException {
        RealCometSearch.Search search = RealCometSearch.fastaSearch(root, release, SETTINGS);
        RunLayout run = search.run();
        for (String base : List.of("k562_3", "k562_4")) {
            Path target = run.pepXmlFile(base);
            Path decoy = Path.of(run.cometOutputBase(base) + ".decoy.pep.xml");
            assertTrue(Files.isRegularFile(decoy), decoy + ": decoy_search = 2 writes it");
            List<PepXmlSearchHit> targetHits = readAndCrossRead(target, run, base);
            List<PepXmlSearchHit> decoyHits = readAndCrossRead(decoy, run, base);
            assertTrue(targetHits.stream().noneMatch(h -> h.protein().startsWith("DECOY_")));
            assertTrue(decoyHits.stream().allMatch(h -> h.protein().startsWith("DECOY_")));
            assertTrue(
                    decoyHits.stream()
                            .flatMap(h -> h.alternativeProteins().stream())
                            .noneMatch(p -> p.startsWith("DECOY_")),
                    "Comet prefixes only a decoy hit's first protein");
            List<PepXmlSearchHit> all = new ArrayList<>(targetHits);
            all.addAll(decoyHits);
            assertTerminalForms(all, base);
            joinThePin(run, base, target, decoy);
        }
    }

    private static List<PepXmlSearchHit> readAndCrossRead(Path file, RunLayout run, String base)
            throws IOException {
        Recorder recorder = new Recorder();
        PepXmlSearchSummary summary = PepXmlSearchHitReader.read(file, recorder);
        PepXmlLines.Document lines = PepXmlLines.read(file);
        PepXmlLines.assertSame(lines, recorder.queries());
        PepXmlLines.assertDeclarations(lines, summary);
        assertEquals(RESIDUE_DECLARATIONS, summary.aminoAcidModifications(), file.toString());
        assertEquals(TERMINAL_DECLARATIONS, summary.terminalModifications(), file.toString());
        assertEquals(run.cometOutputBase(base).toString(), summary.outputBase());
        assertTrue(recorder.queries().size() > 100, file + ": a real search");
        return recorder.queries().stream().flatMap(v -> v.hits().stream()).toList();
    }

    private static void assertTerminalForms(List<PepXmlSearchHit> hits, String base) {
        List<PepXmlModificationInfo> infos =
                hits.stream().map(h -> h.modificationInfo().orElseThrow()).toList();
        Set<String> cterm = new HashSet<>();
        Set<String> nterm = new HashSet<>();
        for (PepXmlModificationInfo info : infos) {
            cterm.add(info.ctermMass().orElseThrow().text());
            info.ntermMass().ifPresent(n -> nterm.add(n.text()));
        }
        assertEquals(CTERM, cterm, base + ": every hit carries the static C-terminal mass");
        assertTrue(NTERM.containsAll(nterm), base + ": " + nterm);
        assertTrue(nterm.contains("43.018390"), base);
        assertTrue(
                infos.stream()
                        .flatMap(i -> i.residues().stream())
                        .anyMatch(
                                r -> r.staticDelta().isPresent() && r.variableDelta().isPresent()),
                base + ": a residue with both a static and a variable delta");
    }

    private static void joinThePin(RunLayout run, String base, Path target, Path decoy)
            throws IOException {
        List<String> lines = Files.readAllLines(run.pinFile(base), StandardCharsets.UTF_8);
        List<String> header = List.of(lines.get(0).split("\t", -1));
        int peptideColumn = header.indexOf("Peptide");
        List<String[]> rows = new ArrayList<>();
        Set<PepXmlHitKey> targets = new HashSet<>();
        Set<PepXmlHitKey> decoys = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            rows.add(fields);
            ("1".equals(fields[1]) ? targets : decoys).add(key(fields[0], run, base));
        }
        assertTrue(!targets.isEmpty() && !decoys.isEmpty(), base + ": both labels");
        PepXmlHitIndex targetIndex = PepXmlHitIndex.of(target, targets);
        PepXmlHitIndex decoyIndex = PepXmlHitIndex.of(decoy, decoys);
        assertEquals(Set.of(), targetIndex.missing());
        assertEquals(Set.of(), decoyIndex.missing());
        assertEquals(rows.size(), targetIndex.size() + decoyIndex.size());
        for (String[] row : rows) {
            boolean isDecoy = "-1".equals(row[1]);
            PepXmlSearchHit hit =
                    (isDecoy ? decoyIndex : targetIndex)
                            .find(key(row[0], run, base))
                            .orElseThrow()
                            .hit();
            assertPinRow(row, peptideColumn, hit, isDecoy);
        }
    }

    private static PepXmlHitKey key(String specId, RunLayout run, String base) {
        Matcher id = SPEC_ID.matcher(specId);
        assertTrue(id.matches(), specId);
        assertEquals(run.cometOutputBase(base).toString(), id.group(1), "the -N base");
        return new PepXmlHitKey(
                Long.parseLong(id.group(2)),
                Integer.parseInt(id.group(3)),
                Integer.parseInt(id.group(4)));
    }

    private static void assertPinRow(String[] row, int column, PepXmlSearchHit hit, boolean decoy) {
        Matcher peptide = PIN_PEPTIDE.matcher(row[column]);
        assertTrue(peptide.matches(), row[column]);
        String where = row[0] + " " + row[column];
        assertEquals(peptide.group(1), hit.peptidePrevAa(), where);
        assertEquals(peptide.group(5), hit.peptideNextAa(), where);
        PepXmlModificationInfo info = hit.modificationInfo().orElseThrow();
        assertEquals(
                peptide.group(2) != null,
                info.ntermMass().map(m -> VARIABLE_NTERM.contains(m.text())).orElse(false),
                where + ": a variable N-terminal modification in both or neither");
        assertEquals(
                peptide.group(4) != null,
                VARIABLE_CTERM.equals(info.ctermMass().orElseThrow().text()),
                where + ": a variable C-terminal modification in both or neither");
        StringBuilder residues = new StringBuilder();
        List<Integer> variable = new ArrayList<>();
        Matcher residue = RESIDUE.matcher(peptide.group(3));
        while (residue.find()) {
            residues.append(residue.group(1));
            if (residue.group(2) != null) {
                variable.add(residues.length());
            }
        }
        assertEquals(residues.toString(), hit.peptide(), where);
        assertEquals(
                variable,
                info.residues().stream()
                        .filter(r -> r.variableDelta().isPresent())
                        .map(PepXmlResidueModification::position)
                        .toList(),
                where);
        Set<String> expected = new HashSet<>();
        expected.add(hit.protein());
        for (String alternative : hit.alternativeProteins()) {
            expected.add(decoy ? "DECOY_" + alternative : alternative);
        }
        assertEquals(
                expected,
                new HashSet<>(List.of(row).subList(column + 1, row.length)),
                where
                        + ": the PIN's proteins, the decoy file's alternatives with the prefix put"
                        + " back");
    }
}
