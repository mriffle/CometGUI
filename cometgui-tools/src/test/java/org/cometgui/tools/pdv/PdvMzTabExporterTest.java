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

import static org.cometgui.tools.pdv.MzTabFixtures.DECLARATIONS;
import static org.cometgui.tools.pdv.MzTabFixtures.hit;
import static org.cometgui.tools.pdv.MzTabFixtures.modifications;
import static org.cometgui.tools.pdv.MzTabFixtures.nativeId;
import static org.cometgui.tools.pdv.MzTabFixtures.query;
import static org.cometgui.tools.pdv.MzTabFixtures.row;
import static org.cometgui.tools.pdv.MzTabFixtures.variable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.tools.pdv.PdvMzTabException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The exporter on constructed runs: what it writes for each shape a Comet hit can take, and the
 * refusal -- naming the PSM, the files and the reason, with nothing left at the requested path --
 * for every PSM it cannot represent faithfully (gate item 8, design decision P11-8).
 *
 * <p>Every expected value is typed here from the constructed sources, never computed by the
 * exporter's classes.
 */
class PdvMzTabExporterTest {

    private static final String BASE = "made";
    private static final String OTHER = "other";

    @TempDir private Path root;

    private Path pdv;
    private Path target;

    @BeforeEach
    void layout() throws IOException {
        pdv = Files.createDirectories(root.resolve("run").resolve("pdv"));
        target = pdv.resolve("mztab");
    }

    /** The ordinary query: scan 101, charge 2, an unmodified and an oxidised hit. */
    private static String ordinaryQuery() {
        return query(
                BASE,
                101,
                2,
                nativeId(101),
                "600.5",
                hit("PEPTIDEK", "R", "A", null),
                hit("MMMMK", "K", "G", modifications("", variable(1, "147.035400", "15.994915"))));
    }

    private Path pepXml(String declarations, String... queries) throws IOException {
        return MzTabFixtures.pepXml(
                root.resolve("in").resolve(BASE + ".pep.xml"), BASE, declarations, queries);
    }

    private Path table(String name, String... rows) throws IOException {
        return MzTabFixtures.table(root.resolve("in").resolve(name), rows);
    }

    private Path spectra(String... titles) throws IOException {
        return MzTabFixtures.mgf(root.resolve("in").resolve(BASE + ".mgf"), titles);
    }

    private PdvMzTabSource source(Path pepXml, Path spectra) {
        return new PdvMzTabSource(BASE, spectra, SpectrumFileFormat.MGF, pepXml, Optional.empty());
    }

    private PdvMzTabRequest request(Path targets, PdvMzTabSource... sources) {
        return new PdvMzTabRequest(targets, Optional.empty(), List.of(sources), target);
    }

    private PdvMzTabExport ordinary(String... rows) throws IOException {
        return PdvMzTabExporter.export(
                request(
                        table("targets.tsv", rows),
                        source(pepXml(DECLARATIONS, ordinaryQuery()), spectra(nativeId(101)))));
    }

    private static List<String[]> psmLines(Path mzTab) throws IOException {
        List<String[]> lines = new ArrayList<>();
        for (String line : Files.readAllLines(mzTab, StandardCharsets.UTF_8)) {
            if (line.startsWith("PSM\t")) {
                lines.add(line.split("\t", -1));
            }
        }
        return lines;
    }

    private static String field(String[] line, String column) {
        return line[PdvMzTabExporter.COLUMNS.indexOf(column) + 1];
    }

    /** Runs a refused export and checks what every refusal must be. */
    private PdvMzTabException refused(PdvMzTabRequest request, Reason reason, String psmId)
            throws IOException {
        PdvMzTabException refusal =
                assertThrows(PdvMzTabException.class, () -> PdvMzTabExporter.export(request));
        assertAll(
                () -> assertEquals(reason, refusal.reason(), refusal.getMessage()),
                () -> assertEquals(Optional.ofNullable(psmId), refusal.psmId()),
                () ->
                        assertTrue(
                                refusal.getMessage()
                                        .startsWith(
                                                "The mzTab for PDV was not written: "
                                                        + (psmId == null
                                                                ? ""
                                                                : "PSM " + psmId + " ")),
                                refusal.getMessage()),
                () -> assertTrue(refusal.getMessage().endsWith("[" + reason + "]")),
                () -> assertFalse(Files.exists(target), "nothing at the requested path"),
                () -> assertEquals(List.of(), entries(pdv), "no staging directory left behind"));
        return refusal;
    }

    private static List<String> entries(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> String.valueOf(file.getFileName())).sorted().toList();
        }
    }

    @Nested
    @DisplayName("what it writes")
    class Writes {

        @Test
        @DisplayName("each row's values come from its own sources, one MGF entry per row")
        void ordinaryRows() throws IOException {
            PdvMzTabExport export =
                    ordinary(
                            row(
                                    BASE + "_101_2_2",
                                    "1.5",
                                    "0.01",
                                    "0.02",
                                    "K.M[15.9949]MMMK.G",
                                    "sp|P1|A",
                                    "sp|P2|B"),
                            row(
                                    BASE + "_101_2_1",
                                    "2.5",
                                    "0.001",
                                    "0.002",
                                    "R.PEPTIDEK.A",
                                    "sp|P3|C"));
            List<String[]> lines = psmLines(export.mzTab());
            assertAll(
                    () -> assertEquals(2, lines.size()),
                    () -> assertEquals(2, export.psmsWritten()),
                    () -> assertEquals(2, export.targetPsms()),
                    () -> assertEquals(0, export.decoyPsms()),
                    () -> assertEquals(target.toAbsolutePath(), export.directory()),
                    () ->
                            assertEquals(
                                    target.toAbsolutePath().resolve("psms.mztab"), export.mzTab()),
                    () -> assertEquals(1, export.mgfs().size()),
                    () -> assertEquals(2, export.mgfs().get(0).entries()),
                    () ->
                            assertEquals(
                                    target.toAbsolutePath().resolve("ms_run-1.mgf"),
                                    export.mgfs().get(0).file()),
                    () -> assertEquals(List.of("ms_run-1.mgf", "psms.mztab"), entries(target)),
                    () -> assertEquals(List.of("mztab"), entries(pdv)),
                    () ->
                            assertArrayEquals(
                                    new String[] {
                                        "PSM",
                                        "MMMMK",
                                        "1",
                                        "sp|P1|A;sp|P2|B",
                                        "null",
                                        "null",
                                        "null",
                                        "[MS, MS:1002251, Comet, ]|[MS, MS:1001490, Percolator, ]",
                                        "1.5",
                                        "0.01",
                                        "0.02",
                                        "1-CHEMMOD:+15.994915",
                                        "600.5",
                                        "2",
                                        "500.251",
                                        "null",
                                        "ms_run[1]:index=0",
                                        "K",
                                        "G",
                                        "null",
                                        "null",
                                        "M[+15.994915]MMMK",
                                        "0",
                                        BASE + "_101_2_2",
                                        "1.5",
                                        "0.01",
                                        "0.02"
                                    },
                                    lines.get(0)),
                    () ->
                            assertArrayEquals(
                                    new String[] {
                                        "PSM",
                                        "PEPTIDEK",
                                        "2",
                                        "sp|P3|C",
                                        "null",
                                        "null",
                                        "null",
                                        "[MS, MS:1002251, Comet, ]|[MS, MS:1001490, Percolator, ]",
                                        "2.5",
                                        "0.001",
                                        "0.002",
                                        "null",
                                        "600.5",
                                        "2",
                                        "500.251",
                                        "null",
                                        "ms_run[1]:index=1",
                                        "R",
                                        "A",
                                        "null",
                                        "null",
                                        "PEPTIDEK",
                                        "0",
                                        BASE + "_101_2_1",
                                        "2.5",
                                        "0.001",
                                        "0.002"
                                    },
                                    lines.get(1)));
        }

        @Test
        @DisplayName(
                "rows sharing a spectrum get byte-identical entries, as PdvMgfWriter writes them")
        void sharedSpectrum() throws IOException {
            Path spectra = spectra(nativeId(100), nativeId(101));
            PdvMzTabExport export =
                    PdvMzTabExporter.export(
                            request(
                                    table(
                                            "targets.tsv",
                                            row(
                                                    BASE + "_101_2_1",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "R.PEPTIDEK.A",
                                                    "p"),
                                            row(
                                                    BASE + "_101_2_2",
                                                    "1",
                                                    "0.02",
                                                    "0.2",
                                                    "K.M[15.9949]MMMK.G",
                                                    "p")),
                                    source(pepXml(DECLARATIONS, ordinaryQuery()), spectra)));
            Path expected = Files.createDirectories(root.resolve("expected"));
            PdvMgfWriter.writeOnePerRow(
                    spectra,
                    SpectrumFileFormat.MGF,
                    List.of(nativeId(101), nativeId(101)),
                    expected,
                    "one-per-row.mgf");
            assertAll(
                    () ->
                            assertArrayEquals(
                                    Files.readAllBytes(expected.resolve("one-per-row.mgf")),
                                    Files.readAllBytes(export.mgfs().get(0).file())),
                    () ->
                            assertEquals(
                                    List.of("ms_run[1]:index=0", "ms_run[1]:index=1"),
                                    psmLines(export.mzTab()).stream()
                                            .map(line -> field(line, "spectra_ref"))
                                            .toList()));
        }

        @Test
        @DisplayName("decoy rows follow the targets, flagged, with their own PSM_ID and entries")
        void decoysFollowTargets() throws IOException {
            PdvMzTabExport export =
                    PdvMzTabExporter.export(
                            new PdvMzTabRequest(
                                    table(
                                            "targets.tsv",
                                            row(
                                                    BASE + "_101_2_1",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "R.PEPTIDEK.A",
                                                    "p")),
                                    Optional.of(
                                            table(
                                                    "decoys.tsv",
                                                    row(
                                                            BASE + "_101_2_2",
                                                            "1",
                                                            "0.5",
                                                            "0.9",
                                                            "K.M[15.9949]MMMK.G",
                                                            "DECOY_p"))),
                                    List.of(
                                            source(
                                                    pepXml(DECLARATIONS, ordinaryQuery()),
                                                    spectra(nativeId(101)))),
                                    target));
            List<String[]> lines = psmLines(export.mzTab());
            assertAll(
                    () -> assertEquals(1, export.targetPsms()),
                    () -> assertEquals(1, export.decoyPsms()),
                    () -> assertEquals("0", field(lines.get(0), PdvMzTabExporter.DECOY_COLUMN)),
                    () -> assertEquals("1", field(lines.get(1), PdvMzTabExporter.DECOY_COLUMN)),
                    () -> assertEquals("2", field(lines.get(1), "PSM_ID")),
                    () -> assertEquals("MMMMK", field(lines.get(1), "sequence")),
                    () -> assertEquals("DECOY_p", field(lines.get(1), "accession")),
                    () -> assertEquals("ms_run[1]:index=1", field(lines.get(1), "spectra_ref")));
        }

        @Test
        @DisplayName("with a separate decoy pepXML, a decoy row's shared PSMId names the decoy hit")
        void separateDecoyFile() throws IOException {
            Path decoyPepXml =
                    MzTabFixtures.pepXml(
                            root.resolve("in").resolve(BASE + ".decoy.pep.xml"),
                            BASE,
                            DECLARATIONS,
                            query(
                                    BASE,
                                    101,
                                    2,
                                    nativeId(101),
                                    "600.5",
                                    hit("KEDITPEP", "K", "-", null)));
            PdvMzTabExport export =
                    PdvMzTabExporter.export(
                            new PdvMzTabRequest(
                                    table(
                                            "targets.tsv",
                                            row(
                                                    BASE + "_101_2_1",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "R.PEPTIDEK.A",
                                                    "p")),
                                    Optional.of(
                                            table(
                                                    "decoys.tsv",
                                                    row(
                                                            BASE + "_101_2_1",
                                                            "1",
                                                            "0.5",
                                                            "0.9",
                                                            "K.KEDITPEP.-",
                                                            "DECOY_p"))),
                                    List.of(
                                            new PdvMzTabSource(
                                                    BASE,
                                                    spectra(nativeId(101)),
                                                    SpectrumFileFormat.MGF,
                                                    pepXml(DECLARATIONS, ordinaryQuery()),
                                                    Optional.of(decoyPepXml))),
                                    target));
            List<String[]> lines = psmLines(export.mzTab());
            assertAll(
                    () -> assertEquals("PEPTIDEK", field(lines.get(0), "sequence")),
                    () -> assertEquals("KEDITPEP", field(lines.get(1), "sequence")),
                    () -> assertEquals("-", field(lines.get(1), "post")),
                    () ->
                            assertEquals(
                                    BASE + "_101_2_1",
                                    field(lines.get(1), "opt_global_percolator_PSMId")));
        }

        @Test
        @DisplayName("static, variable and both-on-one-residue modifications keep the pepXML texts")
        void residueModifications() throws IOException {
            String both =
                    "<mod_aminoacid_mass position=\"2\" mass=\"176.025564\""
                            + " static=\"57.021464\" variable=\"15.994915\" source=\"param\"/>";
            String hit =
                    hit(
                            "ACDEMK",
                            "K",
                            "-",
                            modifications("", both, variable(5, "147.035400", "+15.994915")));
            String row =
                    row(BASE + "_101_2_1", "2", "0.01", "0.1", "K.AC[15.9949]DEM[15.995]K.-", "p");
            PdvMzTabExport export =
                    PdvMzTabExporter.export(
                            request(
                                    table("targets.tsv", row),
                                    source(
                                            pepXml(
                                                    DECLARATIONS,
                                                    query(
                                                            BASE,
                                                            101,
                                                            2,
                                                            nativeId(101),
                                                            "600.5",
                                                            hit)),
                                            spectra(nativeId(101)))));
            String[] line = psmLines(export.mzTab()).get(0);
            assertAll(
                    () ->
                            assertEquals(
                                    "AC[+57.021464][+15.994915]DEM[+15.994915]K",
                                    field(line, PdvMzTabExporter.PROFORMA_COLUMN)),
                    () ->
                            assertEquals(
                                    "2-CHEMMOD:+57.021464,2-CHEMMOD:+15.994915,"
                                            + "5-CHEMMOD:+15.994915",
                                    field(line, "modifications")));
        }

        @Test
        @DisplayName("a negative delta keeps its sign, and a PEFF modification its delta")
        void negativeAndPeffModifications() throws IOException {
            String peff =
                    "<mod_aminoacid_mass position=\"3\" mass=\"147.035400\""
                            + " variable=\"15.994915\" source=\"peff\" id=\"MOD:00719\"/>";
            String hit =
                    hit(
                            "QEMK",
                            "K",
                            "-",
                            modifications("", variable(1, "111.032028", "-17.026549"), peff));
            String row =
                    row(BASE + "_101_2_1", "2", "0.01", "0.1", "K.Q[-17.0265]EM[15.9949]K.-", "p");
            PdvMzTabExport export =
                    PdvMzTabExporter.export(
                            request(
                                    table("targets.tsv", row),
                                    source(
                                            pepXml(
                                                    DECLARATIONS,
                                                    query(BASE, 101, 2, nativeId(101), null, hit)),
                                            spectra(nativeId(101)))));
            String[] line = psmLines(export.mzTab()).get(0);
            assertAll(
                    () ->
                            assertEquals(
                                    "Q[-17.026549]EM[+15.994915]K",
                                    field(line, PdvMzTabExporter.PROFORMA_COLUMN)),
                    () ->
                            assertEquals(
                                    "1-CHEMMOD:-17.026549,3-CHEMMOD:+15.994915",
                                    field(line, "modifications")),
                    () -> assertEquals("null", field(line, "retention_time")));
        }

        @Test
        @DisplayName("the one declared N-terminal modification is transcribed, variable or static")
        void nTerminalModifications() throws IOException {
            String variableQuery =
                    query(
                            BASE,
                            101,
                            2,
                            nativeId(101),
                            "600.5",
                            hit(
                                    "PEPTIDEK",
                                    "-",
                                    "A",
                                    modifications("mod_nterm_mass=\"43.018390\"")));
            PdvMzTabExport variableExport =
                    PdvMzTabExporter.export(
                            request(
                                    table(
                                            "targets.tsv",
                                            row(
                                                    BASE + "_101_2_1",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "-.n[42.0106]PEPTIDEK.A",
                                                    "p")),
                                    source(
                                            pepXml(
                                                    DECLARATIONS + MzTabFixtures.N_TERMINAL_ACETYL,
                                                    variableQuery),
                                            spectra(nativeId(101)))));
            String[] variableLine = psmLines(variableExport.mzTab()).get(0);
            String tmt =
                    "  <terminal_modification terminus=\"N\" massdiff=\"229.162932\""
                            + " mass=\"230.170757\" variable=\"N\" protein_terminus=\"N\"/>\n";
            String staticQuery =
                    query(
                            BASE,
                            101,
                            2,
                            nativeId(101),
                            "600.5",
                            hit(
                                    "PEPTIDEK",
                                    "-",
                                    "A",
                                    modifications("mod_nterm_mass=\"230.170757\"")));
            PdvMzTabExport staticExport =
                    PdvMzTabExporter.export(
                            new PdvMzTabRequest(
                                    table(
                                            "static.tsv",
                                            row(
                                                    BASE + "_101_2_1",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "-.PEPTIDEK.A",
                                                    "p")),
                                    Optional.empty(),
                                    List.of(
                                            source(
                                                    pepXml(DECLARATIONS + tmt, staticQuery),
                                                    spectra(nativeId(101)))),
                                    pdv.resolve("second")));
            String[] staticLine = psmLines(staticExport.mzTab()).get(0);
            List<String> metadata =
                    Files.readAllLines(variableExport.mzTab(), StandardCharsets.UTF_8);
            List<String> staticMetadata =
                    Files.readAllLines(staticExport.mzTab(), StandardCharsets.UTF_8);
            assertAll(
                    () ->
                            assertEquals(
                                    "[+42.010565]-PEPTIDEK",
                                    field(variableLine, PdvMzTabExporter.PROFORMA_COLUMN)),
                    () ->
                            assertEquals(
                                    "0-CHEMMOD:+42.010565", field(variableLine, "modifications")),
                    () ->
                            assertEquals(
                                    "[+229.162932]-PEPTIDEK",
                                    field(staticLine, PdvMzTabExporter.PROFORMA_COLUMN)),
                    () ->
                            assertTrue(
                                    metadata.contains(
                                            "MTD\tvariable_mod[2]\t[, CHEMMOD:+42.010565, , ]")),
                    () -> assertTrue(metadata.contains("MTD\tvariable_mod[2]-site\tN-term")),
                    () ->
                            assertTrue(
                                    metadata.contains("MTD\tvariable_mod[2]-position\tAny N-term")),
                    () ->
                            assertTrue(
                                    staticMetadata.contains(
                                            "MTD\tfixed_mod[2]-position\tAny N-term")));
        }

        @Test
        @DisplayName("an empty proteinIds is null; a q-value outside [0, 1] is still transcribed")
        void nullAccessionAndOutOfRangeQValue() throws IOException {
            String[] line =
                    psmLines(
                                    ordinary(
                                                    row(
                                                            BASE + "_101_2_1",
                                                            "-0.5",
                                                            "1.5",
                                                            "1e-400",
                                                            "R.PEPTIDEK.A",
                                                            ""))
                                            .mzTab())
                            .get(0);
            assertAll(
                    () -> assertEquals("null", field(line, "accession")),
                    () -> assertEquals("1.5", field(line, "search_engine_score[2]")),
                    () -> assertEquals("1e-400", field(line, "search_engine_score[3]")),
                    () -> assertEquals("-0.5", field(line, "search_engine_score[1]")));
        }

        @Test
        @DisplayName("a source no row names is no ms_run; the next is ms_run[1]")
        void unusedSourceIsNoRun() throws IOException {
            Path otherPepXml =
                    MzTabFixtures.pepXml(
                            root.resolve("in").resolve(OTHER + ".pep.xml"),
                            OTHER,
                            DECLARATIONS,
                            query(OTHER, 7, 2, nativeId(7), "1.0", hit("AAAK", "K", "-", null)));
            PdvMzTabExport export =
                    PdvMzTabExporter.export(
                            new PdvMzTabRequest(
                                    table(
                                            "targets.tsv",
                                            row(
                                                    BASE + "_101_2_1",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "R.PEPTIDEK.A",
                                                    "p")),
                                    Optional.empty(),
                                    List.of(
                                            new PdvMzTabSource(
                                                    OTHER,
                                                    MzTabFixtures.mgf(
                                                            root.resolve("in").resolve("o.mgf"),
                                                            nativeId(7)),
                                                    SpectrumFileFormat.MGF,
                                                    otherPepXml,
                                                    Optional.empty()),
                                            source(
                                                    pepXml(DECLARATIONS, ordinaryQuery()),
                                                    spectra(nativeId(101)))),
                                    target));
            assertAll(
                    () -> assertEquals(1, export.mgfs().size()),
                    () -> assertEquals(BASE, export.mgfs().get(0).source().psmIdBase()),
                    () -> assertEquals(1, export.mgfs().get(0).msRun()),
                    () ->
                            assertEquals(
                                    "ms_run[1]:index=0",
                                    field(psmLines(export.mzTab()).get(0), "spectra_ref")),
                    () -> assertEquals(List.of("ms_run-1.mgf", "psms.mztab"), entries(target)));
        }

        @Test
        @DisplayName("an existing directory is refused before anything is read or written")
        void existingDirectory() throws IOException {
            Files.createDirectories(target);
            Files.writeString(target.resolve("keep"), "x");
            assertThrows(
                    FileAlreadyExistsException.class,
                    () ->
                            ordinary(
                                    row(
                                            BASE + "_101_2_1",
                                            "2",
                                            "0.01",
                                            "0.1",
                                            "R.PEPTIDEK.A",
                                            "p")));
            assertAll(
                    () -> assertEquals(List.of("keep"), entries(target)),
                    () -> assertEquals(List.of("mztab"), entries(pdv)));
        }
    }

    @Nested
    @DisplayName("what it refuses, by name")
    class Refuses {

        private PdvMzTabRequest ordinaryRequest(String... rows) throws IOException {
            return request(
                    table("targets.tsv", rows),
                    source(pepXml(DECLARATIONS, ordinaryQuery()), spectra(nativeId(101))));
        }

        @Test
        @DisplayName("no PSM at all")
        void noPsms() throws IOException {
            refused(ordinaryRequest(), Reason.NO_PSMS, null);
        }

        @Test
        @DisplayName("a PSMId that names no hit")
        void noSpectrumReference() throws IOException {
            refused(
                    ordinaryRequest(row("made-101-2-1", "2", "0.01", "0.1", "R.PEPTIDEK.A", "p")),
                    Reason.NO_SPECTRUM_REFERENCE,
                    "made-101-2-1");
        }

        @Test
        @DisplayName("a base that is none of the run's")
        void unknownBase() throws IOException {
            PdvMzTabException refusal =
                    refused(
                            ordinaryRequest(
                                    row(
                                            OTHER + "_101_2_1",
                                            "2",
                                            "0.01",
                                            "0.1",
                                            "R.PEPTIDEK.A",
                                            "p")),
                            Reason.UNKNOWN_BASE,
                            OTHER + "_101_2_1");
            assertEquals(List.of(root.resolve("in").resolve("targets.tsv")), refusal.files());
        }

        @Test
        @DisplayName("two rows naming one hit")
        void duplicate() throws IOException {
            PdvMzTabException refusal =
                    refused(
                            ordinaryRequest(
                                    row(BASE + "_101_2_1", "2", "0.01", "0.1", "R.PEPTIDEK.A", "p"),
                                    row(
                                            BASE + "_101_2_1",
                                            "1",
                                            "0.01",
                                            "0.1",
                                            "R.PEPTIDEK.A",
                                            "p")),
                            Reason.DUPLICATE_PSM,
                            BASE + "_101_2_1");
            assertTrue(
                    refusal.getMessage().contains("as the row at line 2 of "),
                    refusal.getMessage());
        }

        @Test
        @DisplayName("a rank beyond the query's hits, a scan or charge no query has, rank 0")
        void noHit() throws IOException {
            refused(
                    ordinaryRequest(
                            row(BASE + "_101_2_1", "2", "0.01", "0.1", "R.PEPTIDEK.A", "p"),
                            row(BASE + "_101_2_3", "2", "0.01", "0.1", "R.PEPTIDEK.A", "p")),
                    Reason.NO_PEPXML_HIT,
                    BASE + "_101_2_3");
            refused(
                    ordinaryRequest(
                            row(BASE + "_101_3_1", "2", "0.01", "0.1", "R.PEPTIDEK.A", "p")),
                    Reason.NO_PEPXML_HIT,
                    BASE + "_101_3_1");
            refused(
                    ordinaryRequest(
                            row(BASE + "_101_2_0", "2", "0.01", "0.1", "R.PEPTIDEK.A", "p")),
                    Reason.NO_PEPXML_HIT,
                    BASE + "_101_2_0");
            refused(
                    ordinaryRequest(
                            row(
                                    BASE + "_1099511627776_2_1",
                                    "2",
                                    "0.01",
                                    "0.1",
                                    "R.PEPTIDEK.A",
                                    "p")),
                    Reason.NO_PEPXML_HIT,
                    BASE + "_1099511627776_2_1");
        }

        @Test
        @DisplayName("two queries with the PSM's scan and charge")
        void ambiguousQuery() throws IOException {
            refused(
                    request(
                            table(
                                    "targets.tsv",
                                    row(
                                            BASE + "_101_2_1",
                                            "2",
                                            "0.01",
                                            "0.1",
                                            "R.PEPTIDEK.A",
                                            "p")),
                            source(
                                    pepXml(DECLARATIONS, ordinaryQuery(), ordinaryQuery()),
                                    spectra(nativeId(101)))),
                    Reason.AMBIGUOUS_QUERY,
                    BASE + "_101_2_1");
        }

        @Test
        @DisplayName("a Percolator peptide that is not its hit's")
        void peptideMismatch() throws IOException {
            String[][] cases = {
                {"R.PEPTIDEQ.A", "1"},
                {"K.PEPTIDEK.A", "1"},
                {"R.PEPTIDEK.G", "1"},
                {"R.PEPT[79.9663]IDEK.A", "1"},
                {"K.MMMMK.G", "2"},
                {"K.MM[15.9949]MMK.G", "2"},
                {"K.M[15.9948]MMMK.G", "2"},
                {"K.n[42.0106]M[15.9949]MMMK.G", "2"},
                {"K.M[15.9949]MMMKc[1.0].G", "2"},
                {"PEPTIDEK", "1"},
            };
            for (String[] mismatch : cases) {
                String psmId = BASE + "_101_2_" + mismatch[1];
                PdvMzTabException refusal =
                        refused(
                                ordinaryRequest(row(psmId, "2", "0.01", "0.1", mismatch[0], "p")),
                                Reason.PEPTIDE_MISMATCH,
                                psmId);
                assertTrue(
                        refusal.getMessage().contains("\"" + mismatch[0] + "\""),
                        refusal.getMessage());
                assertEquals(2, refusal.files().size(), "the table and the pepXML");
            }
        }

        @Test
        @DisplayName("Percolator's mass, rounded to fewer places than the pepXML's, agrees")
        void roundingAgreement() throws IOException {
            PdvMzTabExport export =
                    ordinary(row(BASE + "_101_2_2", "2", "0.01", "0.1", "K.M[15.99]MMMK.G", "p"));
            assertEquals(1, export.psmsWritten());
        }

        @Test
        @DisplayName("a C-terminal modification, or an N-terminal mass no one declaration states")
        void unrepresentableModification() throws IOException {
            String acetyl = DECLARATIONS + MzTabFixtures.N_TERMINAL_ACETYL;
            String protein =
                    "  <terminal_modification terminus=\"N\" massdiff=\"28.031300\""
                            + " mass=\"29.039125\" variable=\"N\" protein_terminus=\"Y\"/>\n";
            String[][] cases = {
                {acetyl, "mod_cterm_mass=\"31.018390\"", "C-terminal"},
                {acetyl, "mod_nterm_mass=\"71.049690\"", "is not the mass \"43.018390\""},
                {acetyl + protein, "mod_nterm_mass=\"43.018390\"", "declares 2 N-terminal"},
                {DECLARATIONS, "mod_nterm_mass=\"43.018390\"", "declares 0 N-terminal"},
            };
            for (String[] modification : cases) {
                String terminal =
                        query(
                                BASE,
                                101,
                                2,
                                nativeId(101),
                                "1.0",
                                hit("PEPTIDEK", "-", "A", modifications(modification[1])));
                PdvMzTabRequest request =
                        request(
                                table(
                                        "targets.tsv",
                                        row(
                                                BASE + "_101_2_1",
                                                "2",
                                                "0.01",
                                                "0.1",
                                                "-.PEPTIDEK.A",
                                                "p")),
                                source(pepXml(modification[0], terminal), spectra(nativeId(101))));
                PdvMzTabException refusal =
                        refused(request, Reason.UNREPRESENTABLE_MODIFICATION, BASE + "_101_2_1");
                assertTrue(refusal.getMessage().contains(modification[2]), refusal.getMessage());
            }
        }

        @Test
        @DisplayName("a PEFF sequence variant")
        void sequenceVariant() throws IOException {
            String variant =
                    query(
                            BASE,
                            101,
                            2,
                            nativeId(101),
                            "1.0",
                            hit(
                                    "PEPTIDEK",
                                    "R",
                                    "A",
                                    modifications(
                                            "",
                                            "<aminoacid_substitution position=\"5\""
                                                    + " orig_aa=\"R\"/>")));
            refused(
                    request(
                            table(
                                    "targets.tsv",
                                    row(
                                            BASE + "_101_2_1",
                                            "2",
                                            "0.01",
                                            "0.1",
                                            "R.PEPTIDEK.A",
                                            "p")),
                            source(pepXml(DECLARATIONS, variant), spectra(nativeId(101)))),
                    Reason.SEQUENCE_VARIANT,
                    BASE + "_101_2_1");
        }

        @Test
        @DisplayName("a query without spectrumNativeID")
        void noNativeId() throws IOException {
            refused(
                    request(
                            table(
                                    "targets.tsv",
                                    row(
                                            BASE + "_101_2_1",
                                            "2",
                                            "0.01",
                                            "0.1",
                                            "R.PEPTIDEK.A",
                                            "p")),
                            source(
                                    pepXml(
                                            DECLARATIONS,
                                            query(
                                                    BASE,
                                                    101,
                                                    2,
                                                    null,
                                                    "1.0",
                                                    hit("PEPTIDEK", "R", "A", null))),
                                    spectra(nativeId(101)))),
                    Reason.NO_NATIVE_ID,
                    BASE + "_101_2_1");
        }

        @Test
        @DisplayName("a native id that is no spectrum of the file, or two of them")
        void spectrumNotOne() throws IOException {
            refused(
                    request(
                            table(
                                    "targets.tsv",
                                    row(
                                            BASE + "_101_2_1",
                                            "2",
                                            "0.01",
                                            "0.1",
                                            "R.PEPTIDEK.A",
                                            "p")),
                            source(pepXml(DECLARATIONS, ordinaryQuery()), spectra(nativeId(100)))),
                    Reason.NO_SPECTRUM,
                    BASE + "_101_2_1");
            refused(
                    request(
                            table(
                                    "targets.tsv",
                                    row(
                                            BASE + "_101_2_2",
                                            "2",
                                            "0.01",
                                            "0.1",
                                            "K.M[15.9949]MMMK.G",
                                            "p")),
                            source(
                                    pepXml(DECLARATIONS, ordinaryQuery()),
                                    spectra(nativeId(101), nativeId(101)))),
                    Reason.AMBIGUOUS_SPECTRUM,
                    BASE + "_101_2_2");
        }

        @Test
        @DisplayName("a spectrum whose native id carries another scan")
        void spectrumMismatch() throws IOException {
            refused(
                    request(
                            table(
                                    "targets.tsv",
                                    row(
                                            BASE + "_101_2_1",
                                            "2",
                                            "0.01",
                                            "0.1",
                                            "R.PEPTIDEK.A",
                                            "p")),
                            source(
                                    pepXml(
                                            DECLARATIONS,
                                            query(
                                                    BASE,
                                                    101,
                                                    2,
                                                    nativeId(102),
                                                    "1.0",
                                                    hit("PEPTIDEK", "R", "A", null))),
                                    spectra(nativeId(102)))),
                    Reason.SPECTRUM_MISMATCH,
                    BASE + "_101_2_1");
        }

        @Test
        @DisplayName("a spectrum the MGF cannot carry: no precursor")
        void unrepresentableSpectrum() throws IOException {
            Path spectra =
                    Files.writeString(
                            Files.createDirectories(root.resolve("in")).resolve("bare.mgf"),
                            "BEGIN IONS\nTITLE=" + nativeId(101) + "\n100.5 1.5\nEND IONS\n");
            PdvMzTabException refusal =
                    refused(
                            request(
                                    table(
                                            "targets.tsv",
                                            row(
                                                    BASE + "_101_2_1",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "R.PEPTIDEK.A",
                                                    "p")),
                                    source(pepXml(DECLARATIONS, ordinaryQuery()), spectra)),
                            Reason.UNREPRESENTABLE_SPECTRUM,
                            BASE + "_101_2_1");
            assertTrue(refusal.getMessage().contains("PEPMASS"), refusal.getMessage());
        }

        @Test
        @DisplayName("a score, q-value or PEP that is not a decimal as written")
        void notADecimal() throws IOException {
            String[][] cases = {
                {"abc", "0.01", "0.1", "score"},
                {"2", "nan", "0.1", "q-value"},
                {"2", "", "0.1", "q-value"},
                {"2", "0.01", "inf", "posterior_error_prob"},
                {"2", "0.01", "0,1", "posterior_error_prob"},
            };
            for (String[] values : cases) {
                PdvMzTabException refusal =
                        refused(
                                ordinaryRequest(
                                        row(
                                                BASE + "_101_2_1",
                                                values[0],
                                                values[1],
                                                values[2],
                                                "R.PEPTIDEK.A",
                                                "p")),
                                Reason.NOT_A_DECIMAL,
                                BASE + "_101_2_1");
                assertTrue(
                        refusal.getMessage().contains("has the " + values[3] + " \""),
                        refusal.getMessage());
            }
        }

        @Test
        @DisplayName("a protein the accession column cannot carry")
        void unrepresentableProtein() throws IOException {
            String[][] cases = {{"a;b"}, {"null"}, {"p", ""}};
            for (String[] proteins : cases) {
                refused(
                        ordinaryRequest(
                                row(
                                        BASE + "_101_2_1",
                                        "2",
                                        "0.01",
                                        "0.1",
                                        "R.PEPTIDEK.A",
                                        proteins)),
                        Reason.UNREPRESENTABLE_TEXT,
                        BASE + "_101_2_1");
            }
        }

        @Test
        @DisplayName("a source that is missing or that its reader refuses")
        void unreadableSources() throws IOException {
            Path table =
                    table(
                            "targets.tsv",
                            row(BASE + "_101_2_1", "2", "0.01", "0.1", "R.PEPTIDEK.A", "p"));
            Path pepXml = pepXml(DECLARATIONS, ordinaryQuery());
            Path spectra = spectra(nativeId(101));
            Path missing = root.resolve("in").resolve("missing");
            PdvMzTabException noTable =
                    refused(
                            request(missing, source(pepXml, spectra)),
                            Reason.UNREADABLE_SOURCE,
                            null);
            PdvMzTabException noPepXml =
                    refused(
                            request(table, source(missing, spectra)),
                            Reason.UNREADABLE_SOURCE,
                            null);
            PdvMzTabException noSpectra =
                    refused(
                            request(table, source(pepXml, missing)),
                            Reason.UNREADABLE_SOURCE,
                            null);
            Path broken = Files.writeString(root.resolve("in").resolve("broken.pep.xml"), "<x/>");
            PdvMzTabException brokenPepXml =
                    refused(
                            request(table, source(broken, spectra)),
                            Reason.UNREADABLE_SOURCE,
                            null);
            assertAll(
                    () -> assertEquals(List.of(missing), noTable.files()),
                    () -> assertEquals(List.of(missing), noPepXml.files()),
                    () -> assertEquals(List.of(missing), noSpectra.files()),
                    () -> assertEquals(List.of(broken), brokenPepXml.files()),
                    () -> assertTrue(noTable.getCause() instanceof IOException));
        }

        private Path decoyPepXml(String... queries) throws IOException {
            return MzTabFixtures.pepXml(
                    root.resolve("in").resolve(BASE + ".decoy.pep.xml"),
                    BASE,
                    DECLARATIONS,
                    queries);
        }

        private PdvMzTabRequest withDecoys(Path decoyPepXml, String... decoyRows)
                throws IOException {
            return new PdvMzTabRequest(
                    table(
                            "targets.tsv",
                            row(BASE + "_101_2_1", "2", "0.01", "0.1", "R.PEPTIDEK.A", "p")),
                    Optional.of(table("decoys.tsv", decoyRows)),
                    List.of(
                            new PdvMzTabSource(
                                    BASE,
                                    spectra(nativeId(101)),
                                    SpectrumFileFormat.MGF,
                                    pepXml(DECLARATIONS, ordinaryQuery()),
                                    Optional.of(decoyPepXml))),
                    target);
        }

        @Test
        @DisplayName("a refused decoy row is named from the decoy table, with the decoy pepXML")
        void decoyRowsAreNamed() throws IOException {
            String decoyQuery =
                    query(BASE, 101, 2, nativeId(101), "1.0", hit("KEDITPEP", "K", "-", null));
            Path decoys = root.resolve("in").resolve("decoys.tsv");
            Path decoyPepXml = decoyPepXml(decoyQuery);
            PdvMzTabException missing =
                    refused(
                            withDecoys(
                                    decoyPepXml,
                                    row(BASE + "_101_2_4", "1", "0.5", "0.9", "K.KEDITPEP.-", "d")),
                            Reason.NO_PEPXML_HIT,
                            BASE + "_101_2_4");
            PdvMzTabException duplicate =
                    refused(
                            withDecoys(
                                    decoyPepXml,
                                    row(BASE + "_101_2_1", "1", "0.5", "0.9", "K.KEDITPEP.-", "d"),
                                    row(BASE + "_101_2_1", "1", "0.5", "0.9", "K.KEDITPEP.-", "d")),
                            Reason.DUPLICATE_PSM,
                            BASE + "_101_2_1");
            PdvMzTabException ambiguous =
                    refused(
                            withDecoys(
                                    decoyPepXml(decoyQuery, decoyQuery),
                                    row(BASE + "_101_2_1", "1", "0.5", "0.9", "K.KEDITPEP.-", "d")),
                            Reason.AMBIGUOUS_QUERY,
                            BASE + "_101_2_1");
            PdvMzTabException mismatch =
                    refused(
                            withDecoys(
                                    decoyPepXml(decoyQuery),
                                    row(BASE + "_101_2_1", "1", "0.5", "0.9", "R.PEPTIDEK.A", "d")),
                            Reason.PEPTIDE_MISMATCH,
                            BASE + "_101_2_1");
            Path decoyFile = root.resolve("in").resolve(BASE + ".decoy.pep.xml");
            assertAll(
                    () -> assertEquals(List.of(decoys, decoyFile), missing.files()),
                    () ->
                            assertTrue(
                                    duplicate.getMessage().contains("line 2 of " + decoys + ","),
                                    duplicate.getMessage()),
                    () -> assertEquals(List.of(decoys), duplicate.files()),
                    () -> assertEquals(List.of(decoys, decoyFile), ambiguous.files()),
                    () -> assertEquals(List.of(decoys, decoyFile), mismatch.files()));
        }

        @Test
        @DisplayName("a refusal in a second source names that source's PSM, file and spectrum")
        void secondSourceIsNamed() throws IOException {
            Path otherPepXml =
                    MzTabFixtures.pepXml(
                            root.resolve("in").resolve(OTHER + ".pep.xml"),
                            OTHER,
                            DECLARATIONS,
                            query(OTHER, 6, 2, nativeId(6), "1.0", hit("AAAK", "K", "-", null)),
                            query(OTHER, 7, 2, nativeId(7), "1.0", hit("CCCK", "K", "-", null)));
            Path otherSpectra = MzTabFixtures.mgf(root.resolve("in").resolve("o.mgf"), nativeId(6));
            PdvMzTabSource other =
                    new PdvMzTabSource(
                            OTHER,
                            otherSpectra,
                            SpectrumFileFormat.MGF,
                            otherPepXml,
                            Optional.empty());
            PdvMzTabSource made =
                    source(pepXml(DECLARATIONS, ordinaryQuery()), spectra(nativeId(101)));
            String good = row(BASE + "_101_2_1", "2", "0.01", "0.1", "R.PEPTIDEK.A", "p");
            PdvMzTabException noSpectrum =
                    refused(
                            request(
                                    table(
                                            "targets.tsv",
                                            good,
                                            row(
                                                    OTHER + "_6_2_1",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "K.AAAK.-",
                                                    "p"),
                                            row(
                                                    OTHER + "_7_2_1",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "K.CCCK.-",
                                                    "p")),
                                    made,
                                    other),
                            Reason.NO_SPECTRUM,
                            OTHER + "_7_2_1");
            PdvMzTabException noHit =
                    refused(
                            request(
                                    table(
                                            "targets.tsv",
                                            good,
                                            row(
                                                    OTHER + "_7_2_2",
                                                    "2",
                                                    "0.01",
                                                    "0.1",
                                                    "K.CCCK.-",
                                                    "p")),
                                    made,
                                    other),
                            Reason.NO_PEPXML_HIT,
                            OTHER + "_7_2_2");
            assertAll(
                    () ->
                            assertTrue(
                                    noSpectrum
                                            .getMessage()
                                            .contains("names the spectrum \"" + nativeId(7) + "\""),
                                    noSpectrum.getMessage()),
                    () -> assertEquals(List.of(otherSpectra), noSpectrum.files()),
                    () ->
                            assertEquals(
                                    List.of(root.resolve("in").resolve("targets.tsv"), otherPepXml),
                                    noHit.files()));
        }

        @Test
        @DisplayName("a Comet version an mzTab parameter cannot carry")
        void unrepresentableVersion() throws IOException {
            Path pepXml = pepXml(DECLARATIONS, ordinaryQuery());
            Files.writeString(
                    pepXml,
                    Files.readString(pepXml).replace("2026.03 rev. 0 (fa08489)", "2026, rev. 0"));
            refused(
                    request(
                            table(
                                    "targets.tsv",
                                    row(
                                            BASE + "_101_2_1",
                                            "2",
                                            "0.01",
                                            "0.1",
                                            "R.PEPTIDEK.A",
                                            "p")),
                            source(pepXml, spectra(nativeId(101)))),
                    Reason.UNREPRESENTABLE_TEXT,
                    null);
        }
    }
}
