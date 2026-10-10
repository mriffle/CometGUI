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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Constructed, CometGUI-authored sources for the mzTab exporter's tests: a pepXML in the exact
 * shape Comet 2026.03.0 writes (the attributes of {@code fixtures/pepxml/comet-shapes.pep.xml}), a
 * Percolator PSM table and an MGF spectrum source. Nothing here is K562-derived.
 */
final class MzTabFixtures {

    /** The Percolator PSM table header. */
    static final String HEADER = "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds";

    /** The declarations of the constructed search: static C, variable M, nothing terminal. */
    static final String DECLARATIONS =
            "  <aminoacid_modification aminoacid=\"M\" massdiff=\"15.994915\" mass=\"147.035400\""
                    + " variable=\"Y\"/>\n"
                    + "  <aminoacid_modification aminoacid=\"C\" massdiff=\"57.021464\""
                    + " mass=\"160.030649\" variable=\"N\"/>\n";

    /** One variable N-terminal acetylation, declared as Comet declares it. */
    static final String N_TERMINAL_ACETYL =
            "  <terminal_modification terminus=\"N\" massdiff=\"42.010565\" mass=\"43.018390\""
                    + " variable=\"Y\" protein_terminus=\"N\"/>\n";

    private MzTabFixtures() {}

    /** A Thermo-style native id for a scan. */
    static String nativeId(long scan) {
        return "controllerType=0 controllerNumber=1 scan=" + scan;
    }

    /**
     * A pepXML file.
     *
     * @param file where to write it
     * @param base the search's {@code -N} base
     * @param declarations the {@code aminoacid_modification} and {@code terminal_modification}
     *     lines
     * @param queries the {@code spectrum_query} elements
     * @return the file
     */
    static Path pepXml(Path file, String base, String declarations, String... queries)
            throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append(
                        " <msms_pipeline_analysis date=\"2026-10-10T12:00:00\""
                                + " xmlns=\"http://regis-web.systemsbiology.net/pepXML\""
                                + " summary_xml=\"")
                .append(file)
                .append("\">\n")
                .append(" <msms_run_summary base_name=\"/data/")
                .append(base)
                .append(
                        "\" msManufacturer=\"UNKNOWN\" msModel=\"UNKNOWN\" raw_data_type=\"raw\""
                                + " raw_data=\".mgf\">\n")
                .append(" <sample_enzyme name=\"Trypsin\">\n")
                .append("  <specificity cut=\"KR\" no_cut=\"P\" sense=\"C\"/>\n")
                .append(" </sample_enzyme>\n")
                .append(" <search_summary base_name=\"/run/outputs/comet/")
                .append(base)
                .append(
                        "\" search_engine=\"Comet\" search_engine_version=\"2026.03 rev. 0"
                                + " (fa08489)\" precursor_mass_type=\"monoisotopic\""
                                + " fragment_mass_type=\"monoisotopic\" search_id=\"1\">\n")
                .append("  <search_database local_path=\"/data/invented.fasta\" type=\"AA\"/>\n")
                .append(declarations)
                .append("  <parameter name=\"decoy_search\" value=\"1\"/>\n")
                .append(" </search_summary>\n");
        for (String query : queries) {
            text.append(query);
        }
        text.append(" </msms_run_summary>\n</msms_pipeline_analysis>\n");
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "a directory"));
        return Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    /**
     * A {@code spectrum_query}.
     *
     * @param base the file's base
     * @param scan its scan
     * @param charge its assumed charge
     * @param nativeId its {@code spectrumNativeID}, or {@code null} to leave it out
     * @param retentionTime its {@code retention_time_sec}, or {@code null} to leave it out
     * @param hits its {@code search_hit} elements
     * @return the element
     */
    static String query(
            String base,
            long scan,
            int charge,
            String nativeId,
            String retentionTime,
            String... hits) {
        String padded = String.format(java.util.Locale.ROOT, "%05d", scan);
        StringBuilder text = new StringBuilder();
        text.append(" <spectrum_query spectrum=\"")
                .append(base)
                .append('.')
                .append(padded)
                .append('.')
                .append(padded)
                .append('.')
                .append(charge)
                .append('"');
        if (nativeId != null) {
            text.append(" spectrumNativeID=\"").append(nativeId).append('"');
        }
        text.append(" start_scan=\"")
                .append(scan)
                .append("\" end_scan=\"")
                .append(scan)
                .append("\" precursor_neutral_mass=\"1000.500000\" assumed_charge=\"")
                .append(charge)
                .append("\" index=\"")
                .append(scan)
                .append('"');
        if (retentionTime != null) {
            text.append(" retention_time_sec=\"").append(retentionTime).append('"');
        }
        text.append(">\n  <search_result>\n");
        int rank = 0;
        for (String hit : hits) {
            rank++;
            text.append(hit.replace("HIT_RANK", Integer.toString(rank)));
        }
        return text.append("  </search_result>\n </spectrum_query>\n").toString();
    }

    /**
     * A {@code search_hit}; its rank is its position.
     *
     * @param peptide the residues
     * @param previous {@code peptide_prev_aa}
     * @param next {@code peptide_next_aa}
     * @param modificationInfo the {@code modification_info} element, or {@code null}
     * @return the element
     */
    static String hit(String peptide, String previous, String next, String modificationInfo) {
        return "   <search_hit hit_rank=\"HIT_RANK\" peptide=\""
                + peptide
                + "\" peptide_prev_aa=\""
                + previous
                + "\" peptide_next_aa=\""
                + next
                + "\" protein=\"sp|P00001|ONE_INVENTED\" num_tot_proteins=\"1\""
                + " num_matched_ions=\"9\" tot_num_ions=\"14\""
                + " calc_neutral_pep_mass=\"1000.498765\""
                + " massdiff=\"0.001235\" num_tol_term=\"2\" num_missed_cleavages=\"0\""
                + " num_matched_peptides=\"1200\">\n"
                + (modificationInfo == null ? "" : modificationInfo)
                + "    <search_score name=\"xcorr\" value=\"2.5000\"/>\n"
                + "    <search_score name=\"expect\" value=\"1.00E-05\"/>\n"
                + "   </search_hit>\n";
    }

    /**
     * A {@code modification_info}.
     *
     * @param attributes its attributes after {@code modified_peptide}, such as {@code
     *     mod_nterm_mass="43.018390"}; empty for none
     * @param children its {@code mod_aminoacid_mass} and variant elements
     * @return the element
     */
    static String modifications(String attributes, String... children) {
        StringBuilder text =
                new StringBuilder("    <modification_info modified_peptide=\"X\"")
                        .append(attributes.isEmpty() ? "" : " " + attributes)
                        .append(">\n");
        for (String child : children) {
            text.append("     ").append(child).append('\n');
        }
        return text.append("    </modification_info>\n").toString();
    }

    /** A static {@code mod_aminoacid_mass}. */
    static String fixed(int position, String mass, String delta) {
        return "<mod_aminoacid_mass position=\""
                + position
                + "\" mass=\""
                + mass
                + "\" static=\""
                + delta
                + "\"/>";
    }

    /** A variable {@code mod_aminoacid_mass} from the parameter file. */
    static String variable(int position, String mass, String delta) {
        return "<mod_aminoacid_mass position=\""
                + position
                + "\" mass=\""
                + mass
                + "\" variable=\""
                + delta
                + "\" source=\"param\"/>";
    }

    /**
     * A Percolator PSM table.
     *
     * @param file where to write it
     * @param rows its rows, tab-separated, without the header
     * @return the file
     */
    static Path table(Path file, String... rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.addAll(List.of(rows));
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "a directory"));
        return Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    /** One Percolator row. */
    static String row(
            String psmId, String score, String q, String pep, String peptide, String... proteins) {
        return psmId
                + "\t"
                + score
                + "\t"
                + q
                + "\t"
                + pep
                + "\t"
                + peptide
                + "\t"
                + String.join("\t", proteins);
    }

    /**
     * An MGF spectrum source: entry {@code n} titled {@code titles[n]}, with precursor {@code
     * 500.25<n+1>}, charge 2+, retention time and two peaks.
     *
     * @param file where to write it
     * @param titles each entry's title
     * @return the file
     */
    static Path mgf(Path file, String... titles) throws IOException {
        StringBuilder text = new StringBuilder();
        int number = 0;
        for (String title : titles) {
            number++;
            text.append("BEGIN IONS\nTITLE=")
                    .append(title)
                    .append("\nPEPMASS=500.25")
                    .append(number)
                    .append("\nCHARGE=2+\nRTINSECONDS=6")
                    .append(number)
                    .append(".5\n100.125 10.5\n200.25 20.")
                    .append(number)
                    .append("\nEND IONS\n");
        }
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "a directory"));
        return Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }
}
