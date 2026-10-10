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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;

/**
 * A CometGUI-authored run that exercises what the K562 run lacks, written by the test into a
 * directory: Comet's {@code decoy_search = 2} (a separate decoy pepXML, decoy rows sharing a {@code
 * PSMId} with target rows), a variable N-terminal modification, a residue carrying a static and a
 * variable delta, a variable mass Comet prints as {@code 15.994900}, several alternative proteins,
 * tied hits (position is not {@code hit_rank}), a query without {@code retention_time_sec}, a
 * spectrum file with a gap in its scan numbers, 32-bit and 64-bit, compressed and uncompressed
 * peaks, a second source with PSMs after a source without any, and Percolator's numbers in the
 * forms it prints them ({@code 9.27998e-07}).
 *
 * <p>Every form is modelled on what Comet 2026.02.2 wrote for a K562 search with these settings
 * (observed 2026-10-10: {@code n[42.0106]} in the PIN, {@code mod_nterm_mass="43.018390"}, {@code
 * static="57.021464" variable="15.994915"} on one residue, shared {@code SpecId}s with {@code
 * decoy_search = 2}); no K562 value is copied ({@code D-006}). Sequences, proteins and peaks are
 * invented.
 */
final class ConstructedRun {

    static final String BASE_A = "made_a";
    static final String BASE_EMPTY = "made_empty";
    static final String BASE_B = "made_b";

    /** A native id carrying a scan. */
    static String nativeId(int scan) {
        return "controllerType=0 controllerNumber=1 scan=" + scan;
    }

    /** One spectrum to write: scan, MS level, precursor text, peaks, encoding. */
    record Spec(
            int scan,
            int msLevel,
            String precursor,
            double[] mz,
            double[] intensity,
            boolean wide,
            boolean zlib) {}

    private final Path directory;

    ConstructedRun(Path directory) {
        this.directory = directory;
    }

    Path file(String name) {
        return directory.resolve(name);
    }

    /**
     * Writes every file.
     *
     * @throws IOException if one cannot be written
     */
    void write() throws IOException {
        Files.createDirectories(directory);
        mzMl(
                file(BASE_A + ".mzML"),
                List.of(
                        new Spec(
                                700,
                                1,
                                null,
                                new double[] {400.1, 500.2},
                                new double[] {10.0, 20.0},
                                true,
                                true),
                        new Spec(
                                701,
                                2,
                                "615.788391113281",
                                new double[] {
                                    110.07096099853516, 120.08057403564453, 600.3000000000001
                                },
                                new double[] {3373.84814453125, 30834.462890625, 1e-3},
                                true,
                                true),
                        new Spec(
                                702,
                                2,
                                "500.250000000000",
                                new double[] {101.0001f, 202.5f, 333.33334f},
                                new double[] {1.5f, 1e7f, 0.0001f},
                                false,
                                false),
                        new Spec(
                                703,
                                2,
                                "733.4",
                                new double[] {150.125f, 160.0625f},
                                new double[] {99.9f, 100.1f},
                                false,
                                true),
                        new Spec(
                                705,
                                2,
                                "812.123456789012",
                                new double[] {175.11895751953125, 276.1666259765625},
                                new double[] {1234.5, 5678.25},
                                true,
                                false)));
        mzMl(
                file(BASE_EMPTY + ".mzML"),
                List.of(
                        new Spec(
                                40,
                                2,
                                "450.5",
                                new double[] {200.0},
                                new double[] {1.0},
                                true,
                                true)));
        mzMl(
                file(BASE_B + ".mzML"),
                List.of(
                        new Spec(
                                9001,
                                1,
                                null,
                                new double[] {300.0},
                                new double[] {1.0},
                                true,
                                true),
                        new Spec(
                                9002,
                                2,
                                "421.758911132813",
                                new double[] {147.11280822753906, 248.16049194335938},
                                new double[] {2.0e4, 3.0e4},
                                true,
                                true)));
        Files.writeString(
                file(BASE_A + ".pep.xml"),
                pepXml(
                        BASE_A,
                        query(
                                        BASE_A,
                                        701,
                                        2,
                                        "600.5",
                                        hit(
                                                "1",
                                                "PEPTIDEK",
                                                "K",
                                                "A",
                                                "sp|P00001|ONE_INVENTED",
                                                List.of(
                                                        "sp|P00002|TWO_INVENTED",
                                                        "tr|Q00003|THREE_INVENTED"),
                                                "<modification_info"
                                                        + " modified_peptide=\"n[43]PEPTIDEK\""
                                                        + " mod_nterm_mass=\"43.018390\">\n"
                                                        + "    </modification_info>\n"),
                                        hit(
                                                "2",
                                                "ACMDEFK",
                                                "R",
                                                "L",
                                                "sp|P00004|FOUR_INVENTED",
                                                List.of(),
                                                "<modification_info"
                                                        + " modified_peptide="
                                                        + "\"AC[176]M[147]DEFK\">\n"
                                                        + "     <mod_aminoacid_mass position=\"2\""
                                                        + " mass=\"176.025564\""
                                                        + " static=\"57.021464\""
                                                        + " variable=\"15.994915\""
                                                        + " source=\"param\"/>\n"
                                                        + "     <mod_aminoacid_mass position=\"3\""
                                                        + " mass=\"147.035385\""
                                                        + " variable=\"15.994900\""
                                                        + " source=\"param\"/>\n"
                                                        + "    </modification_info>\n"),
                                        hit(
                                                "2",
                                                "LLGLLK",
                                                "K",
                                                "G",
                                                "sp|P00005|FIVE_INVENTED",
                                                List.of(),
                                                ""))
                                + query(
                                        BASE_A,
                                        701,
                                        3,
                                        "600.5",
                                        hit(
                                                "1",
                                                "MCPEPTIDER",
                                                "K",
                                                "S",
                                                "sp|P00006|SIX_INVENTED",
                                                List.of("sp|P00007|SEVEN_INVENTED"),
                                                "<modification_info"
                                                        + " modified_peptide=\"M[147]CPEPTIDER\">\n"
                                                        + "     <mod_aminoacid_mass position=\"1\""
                                                        + " mass=\"147.035385\""
                                                        + " variable=\"15.994900\""
                                                        + " source=\"param\"/>\n"
                                                        + "     <mod_aminoacid_mass position=\"2\""
                                                        + " mass=\"160.030649\""
                                                        + " static=\"57.021464\"/>\n"
                                                        + "    </modification_info>\n"))
                                + query(
                                        BASE_A,
                                        702,
                                        2,
                                        null,
                                        hit(
                                                "1",
                                                "SAMPLER",
                                                "R",
                                                "-",
                                                "sp|P00008|EIGHT_INVENTED",
                                                List.of(),
                                                ""))
                                + query(
                                        BASE_A,
                                        705,
                                        2,
                                        "4797.0319",
                                        hit(
                                                "1",
                                                "CCK",
                                                "-",
                                                "V",
                                                "sp|P00010|TEN_INVENTED",
                                                List.of(),
                                                "<modification_info"
                                                        + " modified_peptide=\"CCK\">\n"
                                                        + "     <mod_aminoacid_mass position=\"1\""
                                                        + " mass=\"160.030649\""
                                                        + " static=\"57.021464\"/>\n"
                                                        + "     <mod_aminoacid_mass position=\"2\""
                                                        + " mass=\"160.030649\""
                                                        + " static=\"57.021464\"/>\n"
                                                        + "    </modification_info>\n"))),
                StandardCharsets.UTF_8);
        Files.writeString(
                file(BASE_A + ".decoy.pep.xml"),
                pepXml(
                        BASE_A,
                        query(
                                        BASE_A,
                                        701,
                                        2,
                                        "600.5",
                                        hit(
                                                "1",
                                                "KEDITPEP",
                                                "K",
                                                "-",
                                                "DECOY_sp|P00001|ONE_INVENTED",
                                                List.of("sp|P00009|NINE_INVENTED"),
                                                ""))
                                + query(
                                        BASE_A,
                                        703,
                                        2,
                                        "601.25",
                                        hit(
                                                "1",
                                                "MKEDITPEP",
                                                "-",
                                                "R",
                                                "DECOY_sp|P00011|ELEVEN_INVENTED",
                                                List.of(),
                                                "<modification_info"
                                                        + " modified_peptide="
                                                        + "\"n[43]M[147]KEDITPEP\""
                                                        + " mod_nterm_mass=\"43.018390\">\n"
                                                        + "     <mod_aminoacid_mass position=\"1\""
                                                        + " mass=\"147.035385\""
                                                        + " variable=\"15.994900\""
                                                        + " source=\"param\"/>\n"
                                                        + "    </modification_info>\n"))),
                StandardCharsets.UTF_8);
        Files.writeString(
                file(BASE_EMPTY + ".pep.xml"),
                pepXml(
                        BASE_EMPTY,
                        query(
                                BASE_EMPTY,
                                40,
                                2,
                                "12.5",
                                hit(
                                        "1",
                                        "NEVERSEENK",
                                        "K",
                                        "A",
                                        "sp|P00012|TWELVE_INVENTED",
                                        List.of(),
                                        ""))),
                StandardCharsets.UTF_8);
        Files.writeString(
                file(BASE_EMPTY + ".decoy.pep.xml"),
                pepXml(BASE_EMPTY, ""),
                StandardCharsets.UTF_8);
        Files.writeString(
                file(BASE_B + ".pep.xml"),
                pepXml(
                        BASE_B,
                        query(
                                BASE_B,
                                9002,
                                2,
                                "7001.9",
                                hit(
                                        "1",
                                        "WIDNPTVDDR",
                                        "R",
                                        "G",
                                        "sp|P00013|THIRTEEN_INVENTED",
                                        List.of(),
                                        ""))),
                StandardCharsets.UTF_8);
        Files.writeString(
                file(BASE_B + ".decoy.pep.xml"), pepXml(BASE_B, ""), StandardCharsets.UTF_8);
        table(
                file("psms.target.txt"),
                row(
                        BASE_A + "_701_2_1",
                        "2.37114",
                        "0.00112905",
                        "9.27998e-07",
                        "K.n[42.0106]PEPTIDEK.A",
                        "sp|P00001|ONE_INVENTED",
                        "sp|P00002|TWO_INVENTED",
                        "tr|Q00003|THREE_INVENTED"),
                row(
                        BASE_B + "_9002_2_1",
                        "1.96553",
                        "0.00112905",
                        "7.21716e-06",
                        "R.WIDNPTVDDR.G",
                        "sp|P00013|THIRTEEN_INVENTED"),
                row(
                        BASE_A + "_701_3_1",
                        "0.463662",
                        "0.00198145",
                        "0.0142648",
                        "K.M[15.9949]CPEPTIDER.S",
                        "sp|P00006|SIX_INVENTED",
                        "sp|P00007|SEVEN_INVENTED"),
                row(
                        BASE_A + "_701_2_2",
                        "0.217176",
                        "0.0028532",
                        "0.0486522",
                        "R.AC[15.9949]M[15.9949]DEFK.L",
                        "sp|P00004|FOUR_INVENTED"),
                row(
                        BASE_A + "_702_2_1",
                        "-0.153285",
                        "0.0102041",
                        "0.33",
                        "R.SAMPLER.-",
                        "sp|P00008|EIGHT_INVENTED"),
                row(
                        BASE_A + "_701_2_3",
                        "-1.04",
                        "0.5",
                        "1",
                        "K.LLGLLK.G",
                        "sp|P00005|FIVE_INVENTED"),
                row(
                        BASE_A + "_705_2_1",
                        "-2.5e-05",
                        "1",
                        "1",
                        "-.CCK.V",
                        "sp|P00010|TEN_INVENTED"));
        table(
                file("psms.decoy.txt"),
                row(
                        BASE_A + "_701_2_1",
                        "-0.0921",
                        "0.666667",
                        "0.999012",
                        "K.KEDITPEP.-",
                        "DECOY_sp|P00001|ONE_INVENTED",
                        "DECOY_sp|P00009|NINE_INVENTED"),
                row(
                        BASE_A + "_703_2_1",
                        "-3.1",
                        "1",
                        "1",
                        "-.n[42.0106]M[15.9949]KEDITPEP.R",
                        "DECOY_sp|P00011|ELEVEN_INVENTED"));
    }

    /** The run's sources, as the suite names them. */
    List<FidelityCheck.Source> sources() {
        return List.of(
                new FidelityCheck.Source(
                        BASE_A,
                        file(BASE_A + ".mzML"),
                        file(BASE_A + ".pep.xml"),
                        file(BASE_A + ".decoy.pep.xml")),
                new FidelityCheck.Source(
                        BASE_EMPTY,
                        file(BASE_EMPTY + ".mzML"),
                        file(BASE_EMPTY + ".pep.xml"),
                        file(BASE_EMPTY + ".decoy.pep.xml")),
                new FidelityCheck.Source(
                        BASE_B,
                        file(BASE_B + ".mzML"),
                        file(BASE_B + ".pep.xml"),
                        file(BASE_B + ".decoy.pep.xml")));
    }

    static String row(String psmId, String score, String q, String pep, String... rest) {
        return psmId + "\t" + score + "\t" + q + "\t" + pep + "\t" + String.join("\t", rest);
    }

    static void table(Path file, String... rows) throws IOException {
        StringBuilder text =
                new StringBuilder(
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
        for (String row : rows) {
            text.append(row).append('\n');
        }
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    static String hit(
            String hitRank,
            String peptide,
            String previous,
            String next,
            String protein,
            List<String> alternatives,
            String modifications) {
        StringBuilder text =
                new StringBuilder("   <search_hit hit_rank=\"")
                        .append(hitRank)
                        .append("\" peptide=\"")
                        .append(peptide)
                        .append("\" peptide_prev_aa=\"")
                        .append(previous)
                        .append("\" peptide_next_aa=\"")
                        .append(next)
                        .append("\" protein=\"")
                        .append(protein)
                        .append("\" num_tot_proteins=\"")
                        .append(alternatives.size() + 1)
                        .append("\" num_matched_ions=\"4\" tot_num_ions=\"18\"")
                        .append(" calc_neutral_pep_mass=\"1229.557118\" massdiff=\"0.005111\"")
                        .append(" num_tol_term=\"2\" num_missed_cleavages=\"0\"")
                        .append(" num_matched_peptides=\"1332\">\n");
        for (String alternative : alternatives) {
            text.append("    <alternative_protein protein=\"").append(alternative).append("\"/>\n");
        }
        if (!modifications.isEmpty()) {
            text.append("    ").append(modifications);
        }
        text.append("    <search_score name=\"xcorr\" value=\"0.7450\"/>\n")
                .append("    <search_score name=\"expect\" value=\"4.06E+00\"/>\n")
                .append("   </search_hit>\n");
        return text.toString();
    }

    static String query(String base, int scan, int charge, String rt, String... hits) {
        StringBuilder text =
                new StringBuilder(" <spectrum_query spectrum=\"")
                        .append(base)
                        .append('.')
                        .append(scan)
                        .append('.')
                        .append(scan)
                        .append('.')
                        .append(charge)
                        .append("\" spectrumNativeID=\"")
                        .append(nativeId(scan))
                        .append("\" start_scan=\"")
                        .append(scan)
                        .append("\" end_scan=\"")
                        .append(scan)
                        .append("\" precursor_neutral_mass=\"1229.562229\" assumed_charge=\"")
                        .append(charge)
                        .append("\" index=\"")
                        .append(scan)
                        .append('"');
        if (rt != null) {
            text.append(" retention_time_sec=\"").append(rt).append('"');
        }
        text.append(">\n  <search_result>\n");
        for (String hit : hits) {
            text.append(hit);
        }
        return text.append("  </search_result>\n </spectrum_query>\n").toString();
    }

    static String pepXml(String base, String queries) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + " <msms_pipeline_analysis date=\"2026-10-10T12:00:00\""
                + " xmlns=\"http://regis-web.systemsbiology.net/pepXML\">\n"
                + " <msms_run_summary base_name=\"/data/"
                + base
                + "\" msManufacturer=\"UNKNOWN\" msModel=\"UNKNOWN\" raw_data_type=\"raw\""
                + " raw_data=\".mzML\">\n"
                + " <sample_enzyme name=\"Trypsin\">\n"
                + "  <specificity cut=\"KR\" no_cut=\"P\" sense=\"C\"/>\n"
                + " </sample_enzyme>\n"
                + " <search_summary base_name=\"/run/outputs/comet/"
                + base
                + "\" search_engine=\"Comet\" search_engine_version=\"2026.02 rev. 2 (6edec91)\""
                + " precursor_mass_type=\"monoisotopic\" fragment_mass_type=\"monoisotopic\""
                + " search_id=\"1\">\n"
                + "  <search_database local_path=\"/data/invented.fasta\" type=\"AA\"/>\n"
                + "  <enzymatic_search_constraint enzyme=\"Trypsin\""
                + " max_num_internal_cleavages=\"2\" min_number_termini=\"2\"/>\n"
                + "  <aminoacid_modification aminoacid=\"C\" massdiff=\"15.994915\""
                + " mass=\"176.025564\" variable=\"Y\"/>\n"
                + "  <aminoacid_modification aminoacid=\"M\" massdiff=\"15.994900\""
                + " mass=\"147.035385\" variable=\"Y\"/>\n"
                + "  <aminoacid_modification aminoacid=\"C\" massdiff=\"57.021464\""
                + " mass=\"160.030649\" variable=\"N\"/>\n"
                + "  <terminal_modification terminus=\"N\" massdiff=\"42.010565\""
                + " mass=\"43.018390\" variable=\"Y\" protein_terminus=\"Y\"/>\n"
                + "  <parameter name=\"decoy_search\" value=\"2\"/>\n"
                + " </search_summary>\n"
                + queries
                + " </msms_run_summary>\n"
                + "</msms_pipeline_analysis>\n";
    }

    /**
     * Writes a plain (unindexed) mzML: each spectrum's index, its native id, MS level, the selected
     * ion as text, and its peaks in the stated encoding.
     */
    static void mzMl(Path file, List<Spec> specs) throws IOException {
        StringBuilder text =
                new StringBuilder(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<mzML xmlns=\"http://psi.hupo.org/ms/mzml\" version=\"1.1.0\">\n"
                                + "  <cvList count=\"2\">\n"
                                + "    <cv id=\"MS\" fullName=\"PSI-MS\" version=\"4.1.56\"/>\n"
                                + "    <cv id=\"UO\" fullName=\"Unit Ontology\" version=\"1\"/>\n"
                                + "  </cvList>\n"
                                + "  <run id=\"constructed\">\n"
                                + "    <spectrumList count=\""
                                + specs.size()
                                + "\" defaultDataProcessingRef=\"pwiz\">\n");
        int index = 0;
        for (Spec spec : specs) {
            text.append("      <spectrum index=\"")
                    .append(index++)
                    .append("\" id=\"")
                    .append(nativeId(spec.scan()))
                    .append("\" defaultArrayLength=\"")
                    .append(spec.mz().length)
                    .append("\">\n")
                    .append(
                            spec.msLevel() == 1
                                    ? "        <cvParam cvRef=\"MS\" accession=\"MS:1000579\""
                                            + " name=\"MS1 spectrum\" value=\"\"/>\n"
                                    : "        <cvParam cvRef=\"MS\" accession=\"MS:1000580\""
                                            + " name=\"MSn spectrum\" value=\"\"/>\n")
                    .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000511\"")
                    .append(" name=\"ms level\" value=\"")
                    .append(spec.msLevel())
                    .append("\"/>\n")
                    .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000127\"")
                    .append(" name=\"centroid spectrum\" value=\"\"/>\n")
                    .append("        <scanList count=\"1\">\n")
                    .append("          <scan>\n")
                    .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000016\"")
                    .append(" name=\"scan start time\" value=\"")
                    .append(10 + spec.scan() / 100.0)
                    .append("\" unitCvRef=\"UO\" unitAccession=\"UO:0000031\"")
                    .append(" unitName=\"minute\"/>\n")
                    .append("          </scan>\n")
                    .append("        </scanList>\n");
            if (spec.precursor() != null) {
                text.append("        <precursorList count=\"1\">\n")
                        .append("          <precursor>\n")
                        .append("            <isolationWindow>\n")
                        .append("              <cvParam cvRef=\"MS\" accession=\"MS:1000827\"")
                        .append(" name=\"isolation window target m/z\" value=\"999.0\"/>\n")
                        .append("            </isolationWindow>\n")
                        .append("            <selectedIonList count=\"1\">\n")
                        .append("              <selectedIon>\n")
                        .append("                <cvParam cvRef=\"MS\" accession=\"MS:1000744\"")
                        .append(" name=\"selected ion m/z\" value=\"")
                        .append(spec.precursor())
                        .append("\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\"")
                        .append(" unitName=\"m/z\"/>\n")
                        .append("              </selectedIon>\n")
                        .append("            </selectedIonList>\n")
                        .append("          </precursor>\n")
                        .append("        </precursorList>\n");
            }
            text.append("        <binaryDataArrayList count=\"2\">\n")
                    .append(array(spec.mz(), spec.wide(), spec.zlib(), "MS:1000514", "m/z array"))
                    .append(
                            array(
                                    spec.intensity(),
                                    spec.wide(),
                                    spec.zlib(),
                                    "MS:1000515",
                                    "intensity array"))
                    .append("        </binaryDataArrayList>\n")
                    .append("      </spectrum>\n");
        }
        text.append("    </spectrumList>\n  </run>\n</mzML>\n");
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    private static String array(
            double[] values, boolean wide, boolean zlib, String accession, String name) {
        ByteBuffer buffer =
                ByteBuffer.allocate(values.length * (wide ? Double.BYTES : Float.BYTES))
                        .order(ByteOrder.LITTLE_ENDIAN);
        for (double value : values) {
            if (wide) {
                buffer.putDouble(value);
            } else {
                buffer.putFloat((float) value);
            }
        }
        byte[] bytes = buffer.array();
        if (zlib) {
            Deflater deflater = new Deflater();
            try {
                deflater.setInput(bytes);
                deflater.finish();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                while (!deflater.finished()) {
                    out.write(chunk, 0, deflater.deflate(chunk));
                }
                bytes = out.toByteArray();
            } finally {
                deflater.end();
            }
        }
        String encoded = Base64.getEncoder().encodeToString(bytes);
        List<String> lines = new ArrayList<>();
        lines.add("          <binaryDataArray encodedLength=\"" + encoded.length() + "\">");
        lines.add(
                wide
                        ? "            <cvParam cvRef=\"MS\" accession=\"MS:1000523\""
                                + " name=\"64-bit float\" value=\"\"/>"
                        : "            <cvParam cvRef=\"MS\" accession=\"MS:1000521\""
                                + " name=\"32-bit float\" value=\"\"/>");
        lines.add(
                zlib
                        ? "            <cvParam cvRef=\"MS\" accession=\"MS:1000574\""
                                + " name=\"zlib compression\" value=\"\"/>"
                        : "            <cvParam cvRef=\"MS\" accession=\"MS:1000576\""
                                + " name=\"no compression\" value=\"\"/>");
        lines.add(
                "            <cvParam cvRef=\"MS\" accession=\""
                        + accession
                        + "\" name=\""
                        + name
                        + "\" value=\"\"/>");
        lines.add("            <binary>" + encoded + "</binary>");
        lines.add("          </binaryDataArray>");
        return String.join("\n", lines) + "\n";
    }
}
