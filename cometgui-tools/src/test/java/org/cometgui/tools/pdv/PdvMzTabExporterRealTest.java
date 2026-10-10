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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.tools.testing.K562Run;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The exporter on the real K562 run: Percolator 3.07.1's target and decoy PSM tables over the two
 * files {@code _3} and {@code _4}, Comet 2026.02.2's pepXML for each ({@code decoy_search = 1}, so
 * decoys are in the same file) and the two scan-range-subset mzML files Comet searched.
 *
 * <p>Every expected value below was typed by hand from the source files -- the Percolator tables,
 * the pepXML {@code spectrum_query} and {@code search_hit} attributes, the mzML {@code selected ion
 * m/z} -- never computed by the exporter's classes. The export is K562-derived ({@code D-006}): it
 * is written under this module's build directory and deleted afterwards. This is not the fidelity
 * suite ({@code R-PDV-03} is graded by an independent one); it pins counts and spot values.
 */
class PdvMzTabExporterRealTest {

    private static final String BASE_3 = "20100614_Velos1_TaGe_SA_K562_3";
    private static final String BASE_4 = "20100614_Velos1_TaGe_SA_K562_4";

    private static Path work;
    private static PdvMzTabExport export;
    private static List<String> lines;
    private static Map<String, String[]> byPsmId;
    private static List<String> header;

    @BeforeAll
    static void exportTheRun() throws IOException {
        Path build =
                Path.of(System.getProperty("cometgui.buildDirectory", "target")).toAbsolutePath();
        Files.createDirectories(build);
        work = Files.createTempDirectory(build, "mztab-real-");
        Files.createDirectories(work.resolve("pdv"));
        export =
                PdvMzTabExporter.export(
                        new PdvMzTabRequest(
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
                                work.resolve("pdv").resolve("mztab")));
        lines = Files.readAllLines(export.mzTab(), StandardCharsets.UTF_8);
        byPsmId = new HashMap<>();
        for (String line : lines) {
            String[] fields = line.split("\t", -1);
            if (fields[0].equals("PSH")) {
                header = List.of(fields);
            } else if (fields[0].equals("PSM")) {
                byPsmId.merge(
                        fields[23] + "/" + fields[22],
                        fields,
                        (a, b) -> {
                            throw new AssertionError("one PSMId twice in one table");
                        });
            }
        }
    }

    @AfterAll
    static void deleteDerivedData() throws IOException {
        if (work != null && Files.exists(work)) {
            try (Stream<Path> walk = Files.walk(work)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static String[] psm(String table, String psmId) {
        String[] fields = byPsmId.get(psmId + "/" + table);
        if (fields == null) {
            throw new AssertionError("no PSM line for " + psmId + " in the " + table + " rows");
        }
        return fields;
    }

    private static String column(String[] fields, String name) {
        return fields[header.indexOf(name)];
    }

    @Test
    @DisplayName(
            "every row of both tables is one PSM line: 3897 target and 2773 decoy, 6670 in all")
    void countsInAndOut() throws IOException {
        long psmLines = lines.stream().filter(line -> line.startsWith("PSM\t")).count();
        long decoyLines =
                lines.stream()
                        .filter(line -> line.startsWith("PSM\t"))
                        .filter(line -> line.split("\t", -1)[22].equals("1"))
                        .count();
        assertAll(
                () -> assertEquals(3897, export.targetPsms()),
                () -> assertEquals(2773, export.decoyPsms()),
                () -> assertEquals(6670, export.psmsWritten()),
                () -> assertEquals(6670, psmLines),
                () -> assertEquals(2773, decoyLines),
                () -> assertEquals(6670, byPsmId.size(), "no PSMId repeats within a table"),
                () ->
                        assertEquals(
                                countRows(K562Run.percolatorTargets(), BASE_3)
                                        + countRows(K562Run.percolatorDecoys(), BASE_3),
                                export.mgfs().get(0).entries(),
                                "ms_run[1] holds one entry per _3 row"),
                () ->
                        assertEquals(
                                countRows(K562Run.percolatorTargets(), BASE_4)
                                        + countRows(K562Run.percolatorDecoys(), BASE_4),
                                export.mgfs().get(1).entries(),
                                "ms_run[2] holds one entry per _4 row"),
                () ->
                        assertEquals(
                                List.of("ms_run-1.mgf", "ms_run-2.mgf", "psms.mztab"),
                                fileNames(export.directory())),
                () -> assertEquals(1, export.mgfs().get(0).msRun()),
                () -> assertEquals(BASE_4, export.mgfs().get(1).source().psmIdBase()));
    }

    private static long countRows(Path table, String base) throws IOException {
        try (Stream<String> rows = Files.lines(table, StandardCharsets.UTF_8)) {
            return rows.skip(1).filter(row -> row.startsWith(base + "_")).count();
        }
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> String.valueOf(file.getFileName())).sorted().toList();
        }
    }

    @Test
    @DisplayName("the metadata is exactly the shape PDV accepts, with ms_run locations last")
    void metadata() {
        List<String> metadata = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("MTD\t")) {
                metadata.add(line);
            }
        }
        Path directory = export.directory();
        assertEquals(
                List.of(
                        "MTD\tmzTab-version\t1.0.0",
                        "MTD\tmzTab-mode\tSummary",
                        "MTD\tmzTab-type\tIdentification",
                        "MTD\tdescription\tThe PSMs of one CometGUI run, generated only for PDV's"
                                + " denovo-gui display: not a deliverable and not an interchange"
                                + " file. Every value is transcribed from Percolator's PSM tables,"
                                + " Comet's pepXML and the spectrum files; a value no source gives"
                                + " is null. Rows: the target table, then the decoy table, each in"
                                + " file order. accession: Percolator's proteinIds joined by ';'."
                                + " spectra_ref: the entry of the ms_run's MGF, one per row.",
                        "MTD\tsoftware[1]\t[MS, MS:1002251, Comet, 2026.02 rev. 2 (6edec91)]",
                        "MTD\tsoftware[2]\t[MS, MS:1001490, Percolator, ]",
                        "MTD\tpsm_search_engine_score[1]\t[MS, MS:1001492, percolator:score, ]",
                        "MTD\tpsm_search_engine_score[2]\t[MS, MS:1001491, percolator:Q value, ]",
                        "MTD\tpsm_search_engine_score[3]\t[MS, MS:1001493, percolator:PEP, ]",
                        "MTD\tfixed_mod[1]\t[, CHEMMOD:+57.021464, , ]",
                        "MTD\tfixed_mod[1]-site\tC",
                        "MTD\tfixed_mod[1]-position\tAnywhere",
                        "MTD\tvariable_mod[1]\t[, CHEMMOD:+15.994915, , ]",
                        "MTD\tvariable_mod[1]-site\tM",
                        "MTD\tvariable_mod[1]-position\tAnywhere",
                        "MTD\tcolunit-psm\tretention_time=[UO, UO:0000010, second, ]",
                        "MTD\tms_run[1]-format\t[MS, MS:1001062, Mascot MGF format, ]",
                        "MTD\tms_run[1]-id_format\t[MS, MS:1000774, multiple peak list nativeID"
                                + " format, ]",
                        "MTD\tms_run[1]-location\t" + directory.resolve("ms_run-1.mgf").toUri(),
                        "MTD\tms_run[2]-format\t[MS, MS:1001062, Mascot MGF format, ]",
                        "MTD\tms_run[2]-id_format\t[MS, MS:1000774, multiple peak list nativeID"
                                + " format, ]",
                        "MTD\tms_run[2]-location\t" + directory.resolve("ms_run-2.mgf").toUri()),
                metadata);
        assertEquals(
                List.of(
                        "PSH",
                        "sequence",
                        "PSM_ID",
                        "accession",
                        "unique",
                        "database",
                        "database_version",
                        "search_engine",
                        "search_engine_score[1]",
                        "search_engine_score[2]",
                        "search_engine_score[3]",
                        "modifications",
                        "retention_time",
                        "charge",
                        "exp_mass_to_charge",
                        "uri",
                        "spectra_ref",
                        "pre",
                        "post",
                        "start",
                        "end",
                        "opt_global_cv_MS:1003169_proforma_peptidoform_sequence",
                        "opt_global_cv_MS:1002217_decoy_peptide",
                        "opt_global_percolator_PSMId",
                        "opt_global_percolator_score",
                        "opt_global_percolator_q_value",
                        "opt_global_percolator_posterior_error_prob"),
                header);
    }

    @Test
    @DisplayName("Percolator's first target PSM is the first line, every field as the sources say")
    void theFirstTargetPsm() {
        assertEquals(
                String.join(
                        "\t",
                        "PSM",
                        "FNLTYVSHDGDDK",
                        "1",
                        "sp|P26639|SYTC_HUMAN",
                        "null",
                        "null",
                        "null",
                        "[MS, MS:1002251, Comet, ]|[MS, MS:1001490, Percolator, ]",
                        "2.37114",
                        "0.00112905",
                        "9.27998e-07",
                        "null",
                        "4797.0",
                        "2",
                        "755.841003417969",
                        "null",
                        "ms_run[1]:index=0",
                        "R",
                        "K",
                        "null",
                        "null",
                        "FNLTYVSHDGDDK",
                        "0",
                        BASE_3 + "_11367_2_1",
                        "2.37114",
                        "0.00112905",
                        "9.27998e-07"),
                lines.stream().filter(line -> line.startsWith("PSM\t")).findFirst().orElseThrow());
    }

    @Test
    @DisplayName("static and variable modifications are the pepXML's texts, at their positions")
    void modifications() {
        String[] cysteine = psm("0", BASE_3 + "_11566_3_1");
        String[] both = psm("0", BASE_4 + "_10175_5_1");
        assertAll(
                () -> assertEquals("EEECHFYAGGQVYPGEASR", column(cysteine, "sequence")),
                () ->
                        assertEquals(
                                "EEEC[+57.021464]HFYAGGQVYPGEASR",
                                column(cysteine, PdvMzTabExporter.PROFORMA_COLUMN)),
                () -> assertEquals("4-CHEMMOD:+57.021464", column(cysteine, "modifications")),
                () -> assertEquals("4859.0", column(cysteine, "retention_time")),
                () -> assertEquals("3", column(cysteine, "charge")),
                () ->
                        assertEquals(
                                "VC[+57.021464]YYYDGDVGNYYYGQGHPM[+15.994915]KPHR",
                                column(both, PdvMzTabExporter.PROFORMA_COLUMN)),
                () ->
                        assertEquals(
                                "2-CHEMMOD:+57.021464,20-CHEMMOD:+15.994915",
                                column(both, "modifications")),
                () -> assertEquals("4830.1", column(both, "retention_time")),
                () -> assertEquals("5", column(both, "charge")),
                () -> assertEquals("591.855773925781", column(both, "exp_mass_to_charge")),
                () -> assertEquals("sp|Q13547|HDAC1_HUMAN", column(both, "accession")),
                () -> assertTrue(column(both, "spectra_ref").startsWith("ms_run[2]:index=")));
    }

    @Test
    @DisplayName("a multi-protein PSM carries every Percolator protein, joined by ';'")
    void accessions() {
        assertEquals(
                "sp|O60739|EIF1B_HUMAN;sp|P41567|EIF1_HUMAN",
                column(psm("0", BASE_3 + "_11317_3_1"), "accession"));
    }

    @Test
    @DisplayName("decoy rows are flagged and transcribed like targets, from both files")
    void decoys() {
        String[] last = psm("1", BASE_3 + "_11738_2_5");
        String[] first = psm("1", BASE_4 + "_10599_3_2");
        assertAll(
                () -> assertEquals("1", column(last, PdvMzTabExporter.DECOY_COLUMN)),
                () -> assertEquals("EMASEIVQLSQR", column(last, "sequence")),
                () -> assertEquals("DECOY_sp|Q16740|CLPP_HUMAN", column(last, "accession")),
                () -> assertEquals("-2.18392", column(last, "search_engine_score[1]")),
                () -> assertEquals("0.680788", column(last, "search_engine_score[2]")),
                () -> assertEquals("1", column(last, "search_engine_score[3]")),
                () -> assertEquals("0.680788", column(last, PdvMzTabExporter.Q_VALUE_COLUMN)),
                () -> assertEquals("1", column(last, PdvMzTabExporter.PEP_COLUMN)),
                () -> assertEquals("K", column(last, "pre")),
                () -> assertEquals("D", column(last, "post")),
                () -> assertEquals("4909.7", column(last, "retention_time")),
                () -> assertEquals("696.850158691406", column(last, "exp_mass_to_charge")),
                () -> assertEquals("6670", column(last, "PSM_ID")),
                () -> assertEquals("1", column(first, PdvMzTabExporter.DECOY_COLUMN)),
                () -> assertEquals("EIQQELEEIQHSK", column(first, "sequence")),
                () -> assertEquals("0.0142648", column(first, "search_engine_score[3]")),
                () -> assertEquals("537.931518554688", column(first, "exp_mass_to_charge")),
                () -> assertEquals("3898", column(first, "PSM_ID")));
    }

    @Test
    @DisplayName("a row's spectra_ref names an MGF entry titled with its spectrum's native id")
    void spectraReferences() throws IOException {
        String[] row = psm("0", BASE_4 + "_10175_5_1");
        String ref = column(row, "spectra_ref");
        int index = Integer.parseInt(ref.substring("ms_run[2]:index=".length()));
        List<String> titles = new ArrayList<>();
        List<String> precursors = new ArrayList<>();
        for (String line :
                Files.readAllLines(export.mgfs().get(1).file(), StandardCharsets.UTF_8)) {
            if (line.startsWith("TITLE=")) {
                titles.add(line.substring(6));
            } else if (line.startsWith("PEPMASS=")) {
                precursors.add(line.substring(8));
            }
        }
        assertAll(
                () ->
                        assertEquals(
                                "controllerType=0 controllerNumber=1 scan=10175",
                                titles.get(index)),
                () -> assertEquals("591.855773925781", precursors.get(index)));
    }
}
