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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.pdv.PdvMzTabExporter;
import org.cometgui.tools.pdv.PdvMzTabRequest;
import org.cometgui.tools.pdv.PdvMzTabSource;
import org.cometgui.tools.pdv.SpectrumFileFormat;
import org.cometgui.tools.testing.RealCometSearch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Gate items 6 and 7 on what the REAL Comet 2026.02.2 writes under settings the K562 run did not
 * use, searched here from the two K562 files against the proteome's first 1000 records:
 *
 * <ul>
 *   <li><strong>separate decoys</strong>: {@code decoy_search = 2} (a {@code .decoy.pep.xml} per
 *       file, decoy rows sharing their {@code PSMId}s with target rows), a variable peptide
 *       N-terminal acetyl, and a variable oxidation on cysteine beside Comet's static
 *       carbamidomethyl, so one residue carries a static and a variable delta;
 *   <li><strong>static terminal labels</strong>: a fixed N-terminal and lysine mass ({@code
 *       229.162932}), which the pepXML states on every hit and Percolator's peptide never shows.
 * </ul>
 *
 * <p>Percolator is not run: its PSM tables are written here from the merged PIN Comet wrote, each
 * row's {@code PSMId}, peptide and proteins copied verbatim from the PIN's {@code SpecId}, {@code
 * Peptide} and {@code Proteins} -- exactly what Percolator copies -- and its score, q-value and PEP
 * set to text in the forms Percolator prints. The tables are synthetic in their numbers only, which
 * this suite compares as text.
 */
@EnabledOnOs(OS.LINUX)
class RealCometFidelityTest {

    private static final String[] SCORES = {"2.37114", "-0.153285", "0.463662", "-1.04"};
    private static final String[] Q_VALUES = {"0.00112905", "0.0028532", "1", "0.5"};
    private static final String[] PEPS = {"9.27998e-07", "0.0142648", "1", "1.27808e-06"};

    private static Path work;
    private static FidelityCheck separate;
    private static FidelityCheck labelled;

    @BeforeAll
    static void searchAndExport() throws IOException {
        work = FidelityFiles.workDirectory("fidelity-comet-");
        separate =
                searchAndExport(
                        work.resolve("separate"),
                        Map.of(
                                "decoy_search", "2",
                                "variable_mod02", "42.010565 n 0 1 -1 0 0 0.0",
                                "variable_mod03", "15.994915 C 0 3 -1 0 0 0.0"),
                        true);
        labelled =
                searchAndExport(
                        work.resolve("labelled"),
                        Map.of(
                                "add_Nterm_peptide", "229.162932",
                                "add_K_lysine", "229.162932"),
                        false);
    }

    @AfterAll
    static void deleteEverything() throws IOException {
        FidelityFiles.delete(work);
    }

    private static FidelityCheck searchAndExport(
            Path root, Map<String, String> changed, boolean separateDecoys) throws IOException {
        RealCometSearch.Search search =
                RealCometSearch.fastaSearch(
                        Files.createDirectories(root), RealCometSearch.OLDER, changed);
        RunLayout run = search.run();
        Path targets = root.resolve("psms.target.txt");
        Path decoys = root.resolve("psms.decoy.txt");
        tablesFromPin(run.mergedPinFile(), targets, decoys);
        List<FidelityCheck.Source> sources = new ArrayList<>();
        List<PdvMzTabSource> exported = new ArrayList<>();
        for (String base : List.of("k562_3", "k562_4")) {
            Path decoyPepXml = run.cometOutputDirectory().resolve(base + ".decoy.pep.xml");
            assertEquals(
                    separateDecoys,
                    Files.isRegularFile(decoyPepXml),
                    "Comet wrote a separate decoy pepXML exactly under decoy_search = 2");
            Path spectra = root.resolve("inputs").resolve(base + ".mzML");
            String psmIdBase = run.cometOutputBase(base).toString();
            sources.add(
                    new FidelityCheck.Source(
                            psmIdBase,
                            spectra,
                            run.pepXmlFile(base),
                            separateDecoys ? decoyPepXml : null));
            exported.add(
                    new PdvMzTabSource(
                            psmIdBase,
                            spectra,
                            SpectrumFileFormat.MZML,
                            run.pepXmlFile(base),
                            separateDecoys ? Optional.of(decoyPepXml) : Optional.empty()));
        }
        Path directory = root.resolve("mztab");
        PdvMzTabExporter.export(
                new PdvMzTabRequest(targets, Optional.of(decoys), exported, directory));
        return new FidelityCheck(targets, decoys, sources, directory.resolve("psms.mztab"));
    }

    /** Percolator-shaped tables from a PIN: target rows (label 1) and decoy rows (label -1). */
    private static void tablesFromPin(Path pin, Path targets, Path decoys) throws IOException {
        List<String> lines = Files.readAllLines(pin, StandardCharsets.UTF_8);
        List<String> header = List.of(lines.get(0).split("\t", -1));
        int specId = header.indexOf("SpecId");
        int label = header.indexOf("Label");
        int peptide = header.indexOf("Peptide");
        int proteins = header.indexOf("Proteins");
        assertTrue(specId >= 0 && label >= 0 && peptide >= 0 && proteins == header.size() - 1);
        String columns = "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n";
        StringBuilder target = new StringBuilder(columns);
        StringBuilder decoy = new StringBuilder(columns);
        for (int index = 1; index < lines.size(); index++) {
            String[] fields = lines.get(index).split("\t", -1);
            StringBuilder row =
                    new StringBuilder(fields[specId])
                            .append('\t')
                            .append(SCORES[index % SCORES.length])
                            .append('\t')
                            .append(Q_VALUES[index % Q_VALUES.length])
                            .append('\t')
                            .append(PEPS[index % PEPS.length])
                            .append('\t')
                            .append(fields[peptide]);
            for (int field = proteins; field < fields.length; field++) {
                row.append('\t').append(fields[field]);
            }
            row.append('\n');
            switch (fields[label]) {
                case "1" -> target.append(row);
                case "-1" -> decoy.append(row);
                default -> throw new AssertionError("PIN line " + (index + 1) + " has no label");
            }
        }
        Files.writeString(targets, target.toString(), StandardCharsets.UTF_8);
        Files.writeString(decoys, decoy.toString(), StandardCharsets.UTF_8);
    }

    private static void allPass(FidelityCheck check) throws IOException {
        long rows = check.sourceRows().size();
        List<FidelityCheck.Finding> findings = check.all();
        List<String> reports = new ArrayList<>();
        for (FidelityCheck.Finding finding : findings) {
            if (!finding.failures().isEmpty() || finding.compared() == 0) {
                reports.add(finding.report());
            }
        }
        assertAll(
                () -> assertEquals(List.of(), reports),
                () -> assertTrue(rows > 3000, rows + " PSMs"),
                () -> assertEquals(rows, check.joined().size()),
                () -> assertEquals(rows, check.mzTab().rows().size()));
    }

    private static long count(FidelityCheck check, String peptidePart) {
        return check.sourceRows().stream()
                .filter(row -> row.peptide().contains(peptidePart))
                .count();
    }

    @Test
    @DisplayName(
            "decoy_search = 2, a variable N-terminal acetyl and a static+variable cysteine: every"
                    + " check passes")
    void separateDecoys() throws IOException {
        allPass(separate);
        long shared =
                separate.sourceRows().stream()
                        .filter(row -> row.decoy())
                        .filter(
                                row ->
                                        separate.joined()
                                                .containsKey(
                                                        PercolatorTable.Row.key(
                                                                false, row.psmId())))
                        .count();
        long both = 0;
        for (FidelityCheck.Joined joined : separate.joined().values()) {
            for (PepXmlFile.ResidueMod mod : joined.hit().residueMods()) {
                if (mod.staticDelta() != null && mod.variableDelta() != null) {
                    both++;
                }
            }
        }
        long nTerminal = count(separate, ".n[42.0106]");
        long allShared = shared;
        long allBoth = both;
        assertAll(
                () -> assertTrue(allShared > 1000, allShared + " decoy rows share a target PSMId"),
                () -> assertTrue(allBoth > 10, allBoth + " residues carry static and variable"),
                () -> assertTrue(nTerminal > 50, nTerminal + " PSMs carry n[42.0106]"));
    }

    @Test
    @DisplayName(
            "static N-terminal and lysine labels, which Percolator's peptide never shows: every"
                    + " check passes")
    void staticLabels() throws IOException {
        allPass(labelled);
        long labelledNterm = 0;
        for (FidelityCheck.Joined joined : labelled.joined().values()) {
            if ("230.170757".equals(joined.hit().ntermMass())) {
                labelledNterm++;
            }
        }
        long expected = labelled.joined().size();
        long found = labelledNterm;
        assertAll(
                () -> assertEquals(expected, found, "every hit carries the static N-terminal"),
                () -> assertEquals(0, count(labelled, "n[")));
    }
}
