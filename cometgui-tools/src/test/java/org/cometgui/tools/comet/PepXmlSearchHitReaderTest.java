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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.tools.comet.PepXmlShapes.Recorder;
import org.cometgui.tools.comet.PepXmlShapes.Visited;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link PepXmlSearchHitReader} on the CometGUI-authored fixture ({@link PepXmlShapes}): every
 * value is proved against what the fixture states, and every refusal names the file, the line, the
 * query and the hit. The real Comet files are read in {@link PepXmlSearchHitReaderRealFileTest}.
 */
class PepXmlSearchHitReaderTest {

    @TempDir private Path directory;

    private Recorder readFixture() throws CometOutputException {
        Recorder recorder = new Recorder();
        PepXmlSearchSummary returned =
                PepXmlSearchHitReader.read(
                        PepXmlShapes.write(directory, PepXmlShapes.text()), recorder);
        assertEquals(List.of(returned), recorder.summaries(), "one summary, the one returned");
        return recorder;
    }

    @Test
    @DisplayName("the search summary: the file, its input, its -N base, every declaration in order")
    void summary() throws CometOutputException {
        Recorder recorder = readFixture();
        assertEquals(
                new PepXmlSearchSummary(
                        directory.resolve("shapes.pep.xml"),
                        "/data/shapes.mzML",
                        "/run/outputs/comet/shapes",
                        "2026.03 rev. 0 (fa08489)",
                        List.of(
                                new PepXmlAminoAcidModification(
                                        "M", d("15.994915"), d("147.035400"), true, false),
                                new PepXmlAminoAcidModification(
                                        "C", d("15.994915"), d("176.025564"), true, false),
                                new PepXmlAminoAcidModification(
                                        "S", d("79.966331"), d("166.998359"), true, true),
                                new PepXmlAminoAcidModification(
                                        "C", d("57.021464"), d("160.030649"), false, false)),
                        List.of(
                                new PepXmlTerminalModification(
                                        PepXmlTerminus.N,
                                        d("42.010565"),
                                        d("43.018390"),
                                        true,
                                        false),
                                new PepXmlTerminalModification(
                                        PepXmlTerminus.C,
                                        d("14.015650"),
                                        d("31.018390"),
                                        false,
                                        false),
                                new PepXmlTerminalModification(
                                        PepXmlTerminus.N,
                                        d("28.031300"),
                                        d("29.039125"),
                                        false,
                                        true))),
                recorder.summaries().get(0));
    }

    @Test
    @DisplayName("the queries, in file order, with the file's own text")
    void queries() throws CometOutputException {
        List<Visited> visited = readFixture().queries();
        assertEquals(
                List.of(
                        new PepXmlQuery(
                                "shapes.00101.00101.2",
                                Optional.of("controllerType=0 controllerNumber=1 scan=101"),
                                101,
                                101,
                                d("1000.500000"),
                                2,
                                1,
                                od("600.5")),
                        new PepXmlQuery(
                                "shapes.00102.00102.3",
                                Optional.of("controllerType=0 controllerNumber=1 scan=102"),
                                102,
                                102,
                                d("1500.750000"),
                                3,
                                2,
                                od("601.0")),
                        new PepXmlQuery(
                                "shapes.00103.00103.2",
                                Optional.empty(),
                                103,
                                103,
                                d("800.400000"),
                                2,
                                3,
                                Optional.empty()),
                        new PepXmlQuery(
                                "shapes.00102.00102.2",
                                Optional.of("controllerType=0 controllerNumber=1 scan=102"),
                                102,
                                102,
                                d("1000.495000"),
                                2,
                                4,
                                od("601.0"))),
                visited.stream().map(Visited::query).toList());
        assertEquals(List.of(2, 4, 0, 1), visited.stream().map(v -> v.hits().size()).toList());
        assertEquals("601.0", visited.get(1).query().retentionTimeSec().orElseThrow().text());
    }

    @Test
    @DisplayName("an unmodified hit: proteins (escaped name unescaped), scores in order, no mods")
    void unmodifiedHit() throws CometOutputException {
        PepXmlSearchHit hit = readFixture().queries().get(0).hits().get(0);
        assertEquals(
                new PepXmlSearchHit(
                        1,
                        1,
                        "PEPTIDEK",
                        "R",
                        "A",
                        "sp|P00001|ONE_INVENTED",
                        List.of("sp|P00002|TWO_INVENTED", "tr|Q00003|A&B_INVENTED"),
                        3,
                        d("1000.498765"),
                        d("0.001235"),
                        List.of(
                                new PepXmlNameValue("xcorr", "2.5000"),
                                new PepXmlNameValue("deltacn", "0.4000"),
                                new PepXmlNameValue("spscore", "500.0"),
                                new PepXmlNameValue("sprank", "1"),
                                new PepXmlNameValue("expect", "1.00E-05")),
                        Optional.empty()),
                hit);
        assertEquals(
                List.of(
                        "sp|P00001|ONE_INVENTED",
                        "sp|P00002|TWO_INVENTED",
                        "tr|Q00003|A&B_INVENTED"),
                hit.proteins());
        assertEquals(Optional.of("1.00E-05"), hit.searchScore("expect"));
        assertEquals(Optional.of("2.5000"), hit.searchScore("xcorr"));
        assertEquals(Optional.empty(), hit.searchScore("lnExpect"));
    }

    @Test
    @DisplayName(
            "a decoy hit: the file's protein, its alternative without the prefix, a static mod")
    void decoyHitWithStaticModification() throws CometOutputException {
        PepXmlSearchHit hit = readFixture().queries().get(0).hits().get(1);
        assertEquals(
                new PepXmlSearchHit(
                        2,
                        2,
                        "ACDEFGHIK",
                        "K",
                        "-",
                        "DECOY_sp|P00004|FOUR_INVENTED",
                        List.of("sp|P00005|FIVE_INVENTED"),
                        2,
                        d("1000.490000"),
                        d("0.010000"),
                        List.of(
                                new PepXmlNameValue("xcorr", "0.9000"),
                                new PepXmlNameValue("deltacn", "0.6400"),
                                new PepXmlNameValue("spscore", "40.0"),
                                new PepXmlNameValue("sprank", "7"),
                                new PepXmlNameValue("expect", "3.20E+00")),
                        Optional.of(
                                new PepXmlModificationInfo(
                                        "ACDEFGHIK",
                                        Optional.empty(),
                                        Optional.empty(),
                                        List.of(
                                                new PepXmlResidueModification(
                                                        2,
                                                        'C',
                                                        d("160.030649"),
                                                        od("57.021464"),
                                                        Optional.empty(),
                                                        Optional.empty(),
                                                        Optional.empty())),
                                        List.of()))),
                hit);
    }

    @Test
    @DisplayName("tied hit_rank: the position is 1..n in file order, the rank is the file's")
    void tiedRanks() throws CometOutputException {
        List<PepXmlSearchHit> hits = readFixture().queries().get(1).hits();
        assertEquals(List.of(1, 2, 3, 4), hits.stream().map(PepXmlSearchHit::position).toList());
        assertEquals(List.of(1, 2, 2, 3), hits.stream().map(PepXmlSearchHit::hitRank).toList());
        assertEquals(
                List.of("LLLLK", "MMMMK", "MMMMK", "VVVVR"),
                hits.stream().map(PepXmlSearchHit::peptide).toList());
        assertEquals(
                List.of(List.of(1, 2), List.of(2, 3)),
                hits.subList(1, 3).stream()
                        .map(
                                h ->
                                        h.modificationInfo().orElseThrow().residues().stream()
                                                .map(PepXmlResidueModification::position)
                                                .toList())
                        .toList());
        PepXmlResidueModification oxidation =
                hits.get(2).modificationInfo().orElseThrow().residues().get(1);
        assertEquals(
                new PepXmlResidueModification(
                        3,
                        'M',
                        d("147.035400"),
                        Optional.empty(),
                        od("15.994915"),
                        Optional.of("param"),
                        Optional.empty()),
                oxidation);
        assertEquals(
                "MM[147]M[147]MK", hits.get(2).modificationInfo().orElseThrow().modifiedPeptide());
    }

    @Test
    @DisplayName("terminal masses, a stacked static+variable residue, a PEFF mod, PEFF variants")
    void everyModificationForm() throws CometOutputException {
        PepXmlSearchHit hit = readFixture().queries().get(3).hits().get(0);
        assertEquals("-", hit.peptidePrevAa());
        assertEquals("-", hit.peptideNextAa());
        assertEquals(
                new PepXmlModificationInfo(
                        "n[71]S[167]C[176]M[147]EK",
                        od("71.049690"),
                        od("31.018390"),
                        List.of(
                                new PepXmlResidueModification(
                                        1,
                                        'S',
                                        d("166.998359"),
                                        Optional.empty(),
                                        od("79.966331"),
                                        Optional.of("param"),
                                        Optional.empty()),
                                new PepXmlResidueModification(
                                        2,
                                        'C',
                                        d("176.025564"),
                                        od("57.021464"),
                                        od("15.994915"),
                                        Optional.of("param"),
                                        Optional.empty()),
                                new PepXmlResidueModification(
                                        3,
                                        'M',
                                        d("147.035400"),
                                        Optional.empty(),
                                        od("15.994915"),
                                        Optional.of("peff"),
                                        Optional.of("MOD:00719"))),
                        List.of(
                                new PepXmlSequenceVariant(
                                        "aminoacid_substitution",
                                        List.of(
                                                new PepXmlNameValue("position", "5"),
                                                new PepXmlNameValue("orig_aa", "R"))),
                                new PepXmlSequenceVariant(
                                        "sequence_substitution",
                                        List.of(
                                                new PepXmlNameValue("position", "4"),
                                                new PepXmlNameValue("num_aas", "1"),
                                                new PepXmlNameValue("orig_aas", "Q"))))),
                hit.modificationInfo().orElseThrow());
        assertEquals(
                List.of(new PepXmlNameValue("position", "5"), new PepXmlNameValue("orig_aa", "R")),
                hit.modificationInfo().orElseThrow().sequenceVariants().get(0).attributes());
        assertEquals(Optional.of("1:13.00"), hit.searchScore("ascorepro_sitescore"));
        assertEquals(7, hit.searchScores().size());
    }

    @Test
    @DisplayName("a query with an empty search_result is delivered with no hits")
    void emptyQuery() throws CometOutputException {
        assertEquals(List.of(), readFixture().queries().get(2).hits());
    }

    @Test
    @DisplayName("a query with no search_result at all is delivered with no hits")
    void noSearchResult() throws CometOutputException {
        Recorder recorder = new Recorder();
        PepXmlSearchHitReader.read(
                PepXmlShapes.write(
                        directory,
                        PepXmlShapes.damaged(
                                "index=\"3\">\n  <search_result>\n  </search_result>\n",
                                "index=\"3\">\n")),
                recorder);
        assertEquals(List.of(), recorder.queries().get(2).hits());
        assertEquals(4, recorder.queries().size());
    }

    @Test
    @DisplayName("a hit whose modification_info is empty keeps it, with no residues")
    void emptyModificationInfo() throws CometOutputException {
        Recorder recorder = new Recorder();
        PepXmlSearchHitReader.read(
                PepXmlShapes.write(
                        directory,
                        PepXmlShapes.damaged(
                                "    <modification_info modified_peptide=\"ACDEFGHIK\">\n"
                                        + "     <mod_aminoacid_mass position=\"2\""
                                        + " mass=\"160.030649\" static=\"57.021464\"/>\n",
                                "    <modification_info modified_peptide=\"ACDEFGHIK\">\n")),
                recorder);
        assertEquals(
                Optional.of(
                        new PepXmlModificationInfo(
                                "ACDEFGHIK",
                                Optional.empty(),
                                Optional.empty(),
                                List.of(),
                                List.of())),
                recorder.queries().get(0).hits().get(1).modificationInfo());
    }

    @Test
    @DisplayName("binary=\"N\" is read as not binary")
    void binaryNo() throws CometOutputException {
        Recorder recorder = new Recorder();
        PepXmlSearchHitReader.read(
                PepXmlShapes.write(directory, PepXmlShapes.damaged("binary=\"Y\"", "binary=\"N\"")),
                recorder);
        assertFalse(recorder.summaries().get(0).aminoAcidModifications().get(2).binary());
    }

    @Test
    @DisplayName("a pepXML with no spectrum query delivers its summary and nothing else")
    void noQueries() throws CometOutputException {
        String text = PepXmlShapes.text();
        String header = text.substring(0, text.indexOf(" <spectrum_query"));
        Recorder recorder = new Recorder();
        PepXmlSearchHitReader.read(
                PepXmlShapes.write(
                        directory, header + " </msms_run_summary>\n</msms_pipeline_analysis>\n"),
                recorder);
        assertEquals(1, recorder.summaries().size());
        assertEquals(List.of(), recorder.queries());
    }

    @Test
    @DisplayName("comments and processing instructions are ignored")
    void commentsIgnored() throws CometOutputException {
        Recorder recorder = new Recorder();
        PepXmlSearchHitReader.read(
                PepXmlShapes.write(
                        directory,
                        PepXmlShapes.damaged(
                                "  <search_result>\n   <search_hit hit_rank=\"1\""
                                        + " peptide=\"LLLLK\"",
                                "  <search_result><!-- c --><?pi x?>\n   <search_hit hit_rank=\"1\""
                                        + " peptide=\"LLLLK\"")),
                recorder);
        assertEquals(4, recorder.queries().get(1).hits().size());
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                // query-level values
                Arguments.of(
                        "retention_time_sec=\"600.5\"",
                        "retention_time_sec=\"6.005E2\"",
                        "line 20, spectrum_query \"shapes.00101.00101.2\" (index \"1\") has"
                                + " retention_time_sec=\"6.005E2\", which is not a plain decimal"
                                + " number"),
                Arguments.of(
                        "precursor_neutral_mass=\"1000.500000\" ",
                        "",
                        "spectrum_query \"shapes.00101.00101.2\" (index \"1\") has a"
                                + " spectrum_query with no precursor_neutral_mass attribute"),
                Arguments.of(
                        "start_scan=\"101\"",
                        "start_scan=\"0101\"",
                        "has start_scan=\"0101\", which is not a canonical integer from 0 to"
                                + " 9223372036854775807"),
                Arguments.of(
                        "end_scan=\"101\"",
                        "end_scan=\"-101\"",
                        "has end_scan=\"-101\", which is not a canonical integer"),
                Arguments.of(
                        "assumed_charge=\"2\" index=\"1\"",
                        "assumed_charge=\"2147483648\" index=\"1\"",
                        "has assumed_charge=\"2147483648\", which is not a canonical integer from"
                                + " 0 to 2147483647"),
                Arguments.of(
                        "index=\"1\"",
                        "index=\"one\"",
                        "spectrum_query \"shapes.00101.00101.2\" (index \"one\") has"
                                + " index=\"one\","),
                Arguments.of(
                        " <spectrum_query spectrum=\"shapes.00103.00103.2\"",
                        " <spectrum_query",
                        "spectrum_query \"\" (index \"3\") has a spectrum_query with no spectrum"
                                + " attribute"),
                // hit-level values
                Arguments.of(
                        "hit_rank=\"1\" peptide=\"PEPTIDEK\"",
                        "hit_rank=\"0\" peptide=\"PEPTIDEK\"",
                        "line 22, spectrum_query \"shapes.00101.00101.2\" (index \"1\"),"
                                + " search_hit 1 (hit_rank \"0\", peptide \"PEPTIDEK\") has"
                                + " hit_rank 0; Comet ranks from 1"),
                Arguments.of(
                        "peptide=\"PEPTIDEK\"",
                        "peptide=\"PEPtIDEK\"",
                        "search_hit 1 (hit_rank \"1\", peptide \"PEPtIDEK\") has peptide"
                                + " \"PEPtIDEK\", which is not a run of upper-case residue"
                                + " letters"),
                Arguments.of(
                        "peptide=\"PEPTIDEK\"",
                        "peptide=\"\"",
                        "has peptide \"\", which is not a run of upper-case residue letters"),
                Arguments.of(
                        "peptide=\"PEPTIDEK\" peptide_prev_aa=\"R\"",
                        "peptide=\"PEPTIDEK\" peptide_prev_aa=\"RR\"",
                        "has peptide_prev_aa=\"RR\", which is not one character"),
                Arguments.of(
                        "peptide=\"PEPTIDEK\" peptide_prev_aa=\"R\" peptide_next_aa=\"A\"",
                        "peptide=\"PEPTIDEK\" peptide_prev_aa=\"R\" peptide_next_aa=\"\"",
                        "has peptide_next_aa=\"\", which is not one character"),
                Arguments.of(
                        "protein=\"sp|P00001|ONE_INVENTED\" ",
                        "",
                        "has a search_hit with no protein attribute"),
                Arguments.of(
                        "num_tot_proteins=\"3\"",
                        "num_tot_proteins=\"three\"",
                        "has num_tot_proteins=\"three\", which is not a canonical integer"),
                Arguments.of(
                        "calc_neutral_pep_mass=\"1000.498765\"",
                        "calc_neutral_pep_mass=\"NaN\"",
                        "search_hit 1 (hit_rank \"1\", peptide \"PEPTIDEK\") has"
                                + " calc_neutral_pep_mass=\"NaN\", which is not a plain decimal"
                                + " number"),
                Arguments.of(
                        "massdiff=\"0.001235\"",
                        "massdiff=\" 0.001235\"",
                        "has massdiff=\" 0.001235\", which is not a plain decimal number"),
                Arguments.of(
                        "<alternative_protein protein=\"sp|P00002|TWO_INVENTED\"/>",
                        "<alternative_protein/>",
                        "line 23, spectrum_query \"shapes.00101.00101.2\" (index \"1\"),"
                                + " search_hit 1 (hit_rank \"1\", peptide \"PEPTIDEK\") has an"
                                + " alternative_protein with no protein attribute"),
                Arguments.of(
                        "<search_score name=\"spscore\" value=\"500.0\"/>",
                        "<search_score name=\"xcorr\" value=\"500.0\"/>",
                        "has a second search_score named \"xcorr\""),
                Arguments.of(
                        "<search_score name=\"spscore\" value=\"500.0\"/>",
                        "<search_score name=\"spscore\"/>",
                        "has a search_score with no value attribute"),
                Arguments.of(
                        "<search_score name=\"spscore\" value=\"500.0\"/>",
                        "<search_score value=\"500.0\"/>",
                        "has a search_score with no name attribute"),
                // modification_info and its residues
                Arguments.of(
                        "<mod_aminoacid_mass position=\"2\" mass=\"160.030649\"",
                        "<mod_aminoacid_mass position=\"10\" mass=\"160.030649\"",
                        "line 34, spectrum_query \"shapes.00101.00101.2\" (index \"1\"),"
                                + " search_hit 2 (hit_rank \"2\", peptide \"ACDEFGHIK\") has a"
                                + " mod_aminoacid_mass at position \"10\", outside the peptide"
                                + " ACDEFGHIK (residues 1 to 9)"),
                Arguments.of(
                        "<mod_aminoacid_mass position=\"2\" mass=\"160.030649\"",
                        "<mod_aminoacid_mass position=\"0\" mass=\"160.030649\"",
                        "has a mod_aminoacid_mass at position \"0\", outside the peptide ACDEFGHIK"
                                + " (residues 1 to 9)"),
                Arguments.of(
                        "<mod_aminoacid_mass position=\"2\" mass=\"160.030649\"",
                        "<mod_aminoacid_mass position=\"02\" mass=\"160.030649\"",
                        "has a mod_aminoacid_mass at position \"02\", outside the peptide"),
                Arguments.of(
                        "<mod_aminoacid_mass position=\"2\" mass=\"160.030649\"",
                        "<mod_aminoacid_mass mass=\"160.030649\"",
                        "has a mod_aminoacid_mass with no position attribute"),
                Arguments.of(
                        "position=\"1\" mass=\"147.035400\" variable=\"15.994915\""
                                + " source=\"param\"/>\n"
                                + "     <mod_aminoacid_mass position=\"2\"",
                        "position=\"1\" mass=\"147.035400\" variable=\"15.994915\""
                                + " source=\"param\"/>\n"
                                + "     <mod_aminoacid_mass position=\"1\"",
                        "search_hit 2 (hit_rank \"2\", peptide \"MMMMK\") has a second"
                                + " mod_aminoacid_mass at position 1"),
                Arguments.of(
                        "position=\"2\" mass=\"160.030649\" static=\"57.021464\"/>",
                        "position=\"2\" mass=\"160.030649\"/>",
                        "has a mod_aminoacid_mass at position 2 with neither a static nor a"
                                + " variable delta"),
                Arguments.of(
                        "position=\"2\" mass=\"160.030649\" static=\"57.021464\"/>",
                        "position=\"2\" static=\"57.021464\"/>",
                        "has a mod_aminoacid_mass with no mass attribute"),
                Arguments.of(
                        "position=\"2\" mass=\"160.030649\" static=\"57.021464\"/>",
                        "position=\"2\" mass=\"160.030649\" static=\"57.021464 \"/>",
                        "has static=\"57.021464 \", which is not a plain decimal number"),
                Arguments.of(
                        "variable=\"79.966331\" source=\"param\"",
                        "variable=\"+-79.966331\" source=\"param\"",
                        "has variable=\"+-79.966331\", which is not a plain decimal number"),
                Arguments.of(
                        "position=\"2\" mass=\"160.030649\" static",
                        "position=\"2\" mass=\"1.6e2\" static",
                        "has mass=\"1.6e2\", which is not a plain decimal number"),
                Arguments.of(
                        " source=\"peff\" id=\"MOD:00719\"",
                        " source=\"peff\"",
                        "line 82, spectrum_query \"shapes.00102.00102.2\" (index \"4\"),"
                                + " search_hit 1 (hit_rank \"1\", peptide \"SCMEK\") has a"
                                + " mod_aminoacid_mass at position 3 from a PEFF source with no"
                                + " id"),
                Arguments.of(
                        "variable=\"79.966331\" source=\"param\"",
                        "variable=\"79.966331\" source=\"param\" id=\"MOD:00696\"",
                        "has a mod_aminoacid_mass at position 1 with an id but no PEFF source"),
                Arguments.of(
                        "variable=\"79.966331\" source=\"param\"",
                        "variable=\"79.966331\" id=\"MOD:00696\"",
                        "has a mod_aminoacid_mass at position 1 with an id but no PEFF source"),
                Arguments.of(
                        "variable=\"79.966331\" source=\"param\"",
                        "variable=\"79.966331\" source=\"unimod\"",
                        "has a mod_aminoacid_mass at position 1 with source=\"unimod\"; Comet"
                                + " writes only param and peff"),
                Arguments.of(
                        "variable=\"79.966331\" source=\"param\"",
                        "variable=\"79.966331\" source=\"param\" neutral_loss=\"97.976896\"",
                        "has a mod_aminoacid_mass with an attribute {}neutral_loss that Comet"
                                + " does not write; it could change what the mass means"),
                Arguments.of(
                        "variable=\"79.966331\" source=\"param\"",
                        "variable=\"79.966331\" source=\"param\" xsi:type=\"x\"",
                        "has a mod_aminoacid_mass with an attribute"
                                + " {http://www.w3.org/2001/XMLSchema-instance}type that Comet"
                                + " does not write"),
                Arguments.of(
                        "mod_nterm_mass=\"71.049690\"",
                        "mod_nterm_mass=\"71,049690\"",
                        "line 79, spectrum_query \"shapes.00102.00102.2\" (index \"4\"),"
                                + " search_hit 1 (hit_rank \"1\", peptide \"SCMEK\") has"
                                + " mod_nterm_mass=\"71,049690\", which is not a plain decimal"
                                + " number"),
                Arguments.of(
                        "mod_cterm_mass=\"31.018390\"",
                        "mod_cterm_mass=\"inf\"",
                        "has mod_cterm_mass=\"inf\", which is not a plain decimal number"),
                Arguments.of(
                        "<modification_info modified_peptide=\"ACDEFGHIK\">",
                        "<modification_info>",
                        "has a modification_info with no modified_peptide attribute"),
                Arguments.of(
                        "<modification_info modified_peptide=\"ACDEFGHIK\">",
                        "<modification_info modified_peptide=\"ACDEFGHIK\""
                                + " mod_nterm_delta=\"1.0\">",
                        "has a modification_info with an attribute {}mod_nterm_delta that Comet"
                                + " does not write"),
                Arguments.of(
                        "    </modification_info>\n    <search_score name=\"xcorr\""
                                + " value=\"0.9000\"/>",
                        "    </modification_info>\n    <modification_info"
                                + " modified_peptide=\"ACDEFGHIK\"/>\n    <search_score"
                                + " name=\"xcorr\" value=\"0.9000\"/>",
                        "search_hit 2 (hit_rank \"2\", peptide \"ACDEFGHIK\") has more than one"
                                + " modification_info"),
                // the closed element vocabulary
                Arguments.of(
                        "<search_score name=\"spscore\" value=\"500.0\"/>",
                        "<analysis_result analysis=\"peptideprophet\"/>",
                        "line 27, spectrum_query \"shapes.00101.00101.2\" (index \"1\"),"
                                + " search_hit 1 (hit_rank \"1\", peptide \"PEPTIDEK\") holds an"
                                + " element {http://regis-web.systemsbiology.net/pepXML}"
                                + "analysis_result inside search_hit, which Comet does not write"
                                + " there; this reader cannot know what it would change"),
                Arguments.of(
                        "<search_score name=\"spscore\" value=\"500.0\"/>",
                        "<search_score xmlns=\"urn:other\" name=\"spscore\" value=\"500.0\"/>",
                        "holds an element {urn:other}search_score inside search_hit"),
                Arguments.of(
                        "     <mod_aminoacid_mass position=\"2\" mass=\"160.030649\"",
                        "     <alternative_protein protein=\"x\"/>"
                                + "<mod_aminoacid_mass position=\"2\""
                                + " mass=\"160.030649\"",
                        "holds an element {http://regis-web.systemsbiology.net/pepXML}"
                                + "alternative_protein inside modification_info, which Comet does"
                                + " not write there"),
                Arguments.of(
                        "  </search_result>\n </spectrum_query>\n <spectrum_query"
                                + " spectrum=\"shapes.00102.00102.3\"",
                        "  </search_result>\n  <search_result/>\n </spectrum_query>\n"
                                + " <spectrum_query spectrum=\"shapes.00102.00102.3\"",
                        "spectrum_query \"shapes.00101.00101.2\" (index \"1\") holds more than one"
                                + " search_result, so a hit's position would be ambiguous"),
                // declarations
                Arguments.of(
                        "aminoacid=\"M\"",
                        "aminoacid=\"MM\"",
                        "line 10 declares an aminoacid_modification on \"MM\", which is not one"
                                + " residue"),
                Arguments.of(
                        "aminoacid=\"M\" massdiff=\"15.994915\"",
                        "massdiff=\"15.994915\"",
                        "has an aminoacid_modification with no aminoacid attribute"),
                Arguments.of(
                        "aminoacid=\"M\" massdiff=\"15.994915\" mass=\"147.035400\" variable=\"Y\"",
                        "aminoacid=\"M\" massdiff=\"15.994915\" mass=\"147.035400\" variable=\"y\"",
                        "line 10 has variable=\"y\", which is neither Y nor N"),
                Arguments.of(
                        "binary=\"Y\"",
                        "binary=\"1\"",
                        "has binary=\"1\", which is neither Y nor N"),
                Arguments.of(
                        "aminoacid=\"M\" massdiff=\"15.994915\"",
                        "aminoacid=\"M\" massdiff=\"15.994915e0\"",
                        "has massdiff=\"15.994915e0\", which is not a plain decimal number"),
                Arguments.of(
                        "aminoacid=\"M\" massdiff=\"15.994915\" mass=\"147.035400\"",
                        "aminoacid=\"M\" massdiff=\"15.994915\" mass=\"147.035400\" symbol=\"*\"",
                        "has an aminoacid_modification with an attribute {}symbol that Comet does"
                                + " not write"),
                Arguments.of(
                        "terminus=\"N\" massdiff=\"42.010565\"",
                        "terminus=\"n\" massdiff=\"42.010565\"",
                        "line 14 declares a terminal_modification on terminus=\"n\", which is"
                                + " neither N nor C"),
                Arguments.of(
                        "terminus=\"C\" massdiff=\"14.015650\"",
                        "massdiff=\"14.015650\"",
                        "has a terminal_modification with no terminus attribute"),
                Arguments.of(
                        "variable=\"N\" protein_terminus=\"Y\"",
                        "variable=\"N\"",
                        "has a terminal_modification with no protein_terminus attribute"),
                Arguments.of(
                        "variable=\"N\" protein_terminus=\"Y\"",
                        "variable=\"N\" protein_terminus=\"Y\" description=\"x\"",
                        "has a terminal_modification with an attribute {}description that Comet"
                                + " does not write"),
                Arguments.of(
                        "massdiff=\"28.031300\"",
                        "massdiff=\"28.0313OO\"",
                        "has massdiff=\"28.0313OO\", which is not a plain decimal number"),
                // the header
                Arguments.of(
                        "search_engine=\"Comet\"",
                        "search_engine=\"X! Tandem\"",
                        "line 7 was written by search_engine=\"X! Tandem\", not Comet; this reader"
                                + " knows only what Comet writes"),
                Arguments.of(
                        "search_engine=\"Comet\" ",
                        "",
                        "has a search_summary with no search_engine attribute"),
                Arguments.of(
                        "search_engine_version=\"2026.03 rev. 0 (fa08489)\" ",
                        "",
                        "has a search_summary with no search_engine_version attribute"),
                Arguments.of(
                        "<search_summary base_name=\"/run/outputs/comet/shapes\" ",
                        "<search_summary ",
                        "has a search_summary with no base_name attribute"),
                Arguments.of(
                        " </search_summary>\n",
                        " </search_summary>\n <search_summary base_name=\"b\""
                                + " search_engine=\"Comet\" search_engine_version=\"v\"/>\n",
                        "line 20 holds more than one search_summary"),
                Arguments.of(
                        " </msms_run_summary>\n",
                        " </msms_run_summary>\n <msms_run_summary base_name=\"/data/other\""
                                + " raw_data=\".mzML\"/>\n",
                        "shapes.pep.xml line 97 holds more than one msms_run_summary; a Comet"
                                + " pepXML names one spectrum file"),
                Arguments.of(
                        " </sample_enzyme>\n",
                        " </sample_enzyme>\n <spectrum_query spectrum=\"early\"/>\n",
                        "line 7 holds a spectrum_query before its search_summary, which Comet"
                                + " never writes"),
                Arguments.of(
                        "  <parameter name=\"decoy_search\" value=\"1\"/>",
                        "  <parameter name=\"decoy_search\" value=\"1\"><x/></parameter>",
                        "holds an element {http://regis-web.systemsbiology.net/pepXML}x inside"
                                + " parameter"),
                Arguments.of(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE x [<!ENTITY e"
                                + " SYSTEM \"file:///etc/passwd\">]>\n",
                        "declares a DOCTYPE, which Comet never writes and which is refused"
                                + " unread"),
                Arguments.of(
                        "xmlns=\"http://regis-web.systemsbiology.net/pepXML\"",
                        "xmlns=\"http://regis-web.systemsbiology.net/pepXML/2\"",
                        "is not a pepXML file: its document element is"
                                + " {http://regis-web.systemsbiology.net/pepXML/2}"
                                + "msms_pipeline_analysis, not"
                                + " {http://regis-web.systemsbiology.net/pepXML}"
                                + "msms_pipeline_analysis"),
                Arguments.of(
                        " </msms_run_summary>\n</msms_pipeline_analysis>\n",
                        " </msms_run_summary>\n",
                        "is not well-formed XML"));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("refusals")
    @DisplayName("a value this reader cannot represent exactly is refused, naming where it is")
    void refused(String find, String replacement, String expected) {
        Path file = PepXmlShapes.write(directory, PepXmlShapes.damaged(find, replacement));
        String message = PepXmlShapes.refusal(file);
        assertTrue(
                message.startsWith("the pepXML file " + file + " "),
                "names the file first: " + message);
        assertTrue(message.contains(expected), "expected \"" + expected + "\" in: " + message);
    }

    @Test
    @DisplayName(
            "a refusal after a query names no query; one in a later query names no earlier hit")
    void contextIsReset() {
        String afterQueries =
                PepXmlShapes.refusal(
                        PepXmlShapes.write(
                                directory,
                                PepXmlShapes.damaged(
                                        " </msms_run_summary>\n",
                                        " </msms_run_summary>\n <msms_run_summary/>\n")));
        assertFalse(afterQueries.contains("spectrum_query \""), afterQueries);
        assertFalse(afterQueries.contains("search_hit"), afterQueries);
        String laterQuery =
                PepXmlShapes.refusal(
                        PepXmlShapes.write(
                                directory,
                                PepXmlShapes.damaged(
                                        "precursor_neutral_mass=\"1500.750000\"",
                                        "precursor_neutral_mass=\"x\"")));
        assertTrue(laterQuery.contains("(index \"2\") has precursor_neutral_mass"), laterQuery);
        assertFalse(laterQuery.contains("search_hit"), laterQuery);
    }

    @Test
    @DisplayName("a refusal names the exact file, line, query and hit")
    void wholeMessage() {
        Path file =
                PepXmlShapes.write(
                        directory,
                        PepXmlShapes.damaged(
                                "<mod_aminoacid_mass position=\"2\" mass=\"160.030649\"",
                                "<mod_aminoacid_mass position=\"11\" mass=\"160.030649\""));
        assertEquals(
                "the pepXML file "
                        + file
                        + " line 34, spectrum_query \"shapes.00101.00101.2\" (index \"1\"),"
                        + " search_hit 2 (hit_rank \"2\", peptide \"ACDEFGHIK\") has a"
                        + " mod_aminoacid_mass at position \"11\", outside the peptide ACDEFGHIK"
                        + " (residues 1 to 9)",
                PepXmlShapes.refusal(file));
    }

    @Test
    @DisplayName("a document with no msms_run_summary, or none with a search_summary, is refused")
    void missingSummaries() {
        String root =
                "<?xml version=\"1.0\"?>\n<msms_pipeline_analysis"
                        + " xmlns=\"http://regis-web.systemsbiology.net/pepXML\">";
        Path bare = PepXmlShapes.write(directory, root + "</msms_pipeline_analysis>");
        assertEquals(
                "the pepXML file " + bare + " holds no msms_run_summary",
                PepXmlShapes.refusal(bare));
        Path noSearch =
                PepXmlShapes.write(
                        directory, root + "<msms_run_summary/></msms_pipeline_analysis>");
        assertEquals(
                "the pepXML file " + noSearch + " holds no search_summary",
                PepXmlShapes.refusal(noSearch));
    }

    @Test
    @DisplayName("an element under the wrong root, or a second root element, is refused")
    void misplacedRoot() {
        String message =
                PepXmlShapes.refusal(
                        PepXmlShapes.write(
                                directory,
                                "<?xml version=\"1.0\"?>\n<msms_run_summary"
                                        + " xmlns=\"http://regis-web.systemsbiology.net/pepXML\"/>"));
        assertTrue(
                message.contains(
                        "is not a pepXML file: its document element is"
                                + " {http://regis-web.systemsbiology.net/pepXML}msms_run_summary"),
                message);
        String nested =
                PepXmlShapes.refusal(
                        PepXmlShapes.write(
                                directory,
                                PepXmlShapes.damaged(
                                        " <msms_run_summary base_name",
                                        " <msms_pipeline_analysis/><msms_run_summary base_name")));
        assertTrue(nested.contains("msms_pipeline_analysis inside msms_pipeline_analysis"), nested);
    }

    @Test
    @DisplayName("an empty file, or one in an encoding the parser cannot open, is not XML")
    void notXmlAtAll() {
        Path empty = PepXmlShapes.write(directory, "");
        String message = PepXmlShapes.refusal(empty);
        assertTrue(
                message.startsWith("the pepXML file " + empty + " is not well-formed XML: "),
                message);
        Path encoding =
                PepXmlShapes.write(
                        directory,
                        PepXmlShapes.damaged(
                                "encoding=\"UTF-8\"", "encoding=\"X-NO-SUCH-CHARSET\""));
        String unknown = PepXmlShapes.refusal(encoding);
        assertTrue(
                unknown.startsWith("the pepXML file " + encoding + " is not well-formed XML: "),
                unknown);
    }

    @Test
    @DisplayName("a missing file, a directory and an unreadable file are refused by name")
    void unreadable() throws IOException {
        Path missing = directory.resolve("absent.pep.xml");
        assertEquals(
                "the pepXML file " + missing + " does not exist", PepXmlShapes.refusal(missing));
        assertEquals(
                "the pepXML file " + directory + " is a directory, not a file",
                PepXmlShapes.refusal(directory));
        Path locked = PepXmlShapes.write(directory, PepXmlShapes.text());
        assertTrue(locked.toFile().setReadable(false));
        try {
            String message = PepXmlShapes.refusal(locked);
            assertTrue(
                    message.startsWith("the pepXML file " + locked + " cannot be read: "), message);
        } finally {
            assertTrue(locked.toFile().setReadable(true));
        }
    }

    /** A visitor that throws what it is given once it sees the n-th query. */
    private static final class Thrower<X extends Exception> implements PepXmlVisitor<X> {

        private final X thrown;

        private final int atQuery;

        private int seen;

        Thrower(X thrown, int atQuery) {
            this.thrown = thrown;
            this.atQuery = atQuery;
        }

        @Override
        public void searchSummary(PepXmlSearchSummary summary) throws X {
            if (atQuery == 0) {
                throw thrown;
            }
        }

        @Override
        public void spectrumQuery(PepXmlQuery query, List<PepXmlSearchHit> hits) throws X {
            if (++seen == atQuery) {
                throw thrown;
            }
        }
    }

    @Test
    @DisplayName("a visitor's own exception passes through unchanged, never as a file problem")
    void visitorExceptions() {
        Path file = PepXmlShapes.write(directory, PepXmlShapes.text());
        AccessDeniedException io = new AccessDeniedException("visitor's own");
        assertSame(
                io,
                assertThrows(
                        AccessDeniedException.class,
                        () -> PepXmlSearchHitReader.read(file, new Thrower<>(io, 2))));
        CometOutputException comet = new CometOutputException(file, "visitor's refusal", null);
        assertSame(
                comet,
                assertThrows(
                        CometOutputException.class,
                        () -> PepXmlSearchHitReader.read(file, new Thrower<>(comet, 0))));
        IllegalStateException unchecked = new IllegalStateException("visitor's bug");
        assertSame(
                unchecked,
                assertThrows(
                        IllegalStateException.class,
                        () -> PepXmlSearchHitReader.read(file, new Thrower<>(unchecked, 3))));
        Exception checked = new Exception("visitor's checked");
        assertSame(
                checked,
                assertThrows(
                        Exception.class,
                        () -> PepXmlSearchHitReader.read(file, new Thrower<>(checked, 1))));
    }

    @Test
    @EnabledOnOs(value = OS.LINUX, disabledReason = "reads the descriptor table in /proc/self/fd")
    @DisplayName(
            "the file is closed on every way out: read, refused, visitor threw, not XML at all")
    void closesTheFile() throws IOException {
        Path good = PepXmlShapes.write(directory.resolve("good"), PepXmlShapes.text());
        PepXmlSearchHitReader.read(good, new Recorder());
        Path late =
                PepXmlShapes.write(
                        directory.resolve("late"),
                        PepXmlShapes.damaged("index=\"4\"", "index=\"four\""));
        PepXmlShapes.refusal(late);
        Path thrown = PepXmlShapes.write(directory.resolve("thrown"), PepXmlShapes.text());
        IllegalStateException bug = new IllegalStateException("visitor's bug");
        assertSame(
                bug,
                assertThrows(
                        IllegalStateException.class,
                        () -> PepXmlSearchHitReader.read(thrown, new Thrower<>(bug, 1))));
        Path empty = PepXmlShapes.write(directory.resolve("empty"), "");
        PepXmlShapes.refusal(empty);
        // Refused while the parser is created, before the walk: the stream is closed there.
        Path encoding =
                PepXmlShapes.write(
                        directory.resolve("encoding"),
                        PepXmlShapes.damaged("encoding=\"UTF-8\"", "encoding=\"X-NO-SUCH\""));
        PepXmlShapes.refusal(encoding);
        assertEquals(List.of(), OpenFiles.to(good, late, thrown, empty, encoding));
    }

    @Test
    @DisplayName("streaming: queries before a late defect are delivered, then the file is refused")
    void lateDefect() {
        Path file =
                PepXmlShapes.write(
                        directory,
                        PepXmlShapes.damaged(
                                "index=\"4\" retention_time_sec=\"601.0\"",
                                "index=\"4\" retention_time_sec=\"late\""));
        Recorder recorder = new Recorder();
        assertThrows(CometOutputException.class, () -> PepXmlSearchHitReader.read(file, recorder));
        assertEquals(3, recorder.queries().size());
    }

    @Test
    @DisplayName("canonical integers: no sign, no leading zero, at most 18 digits, at most max")
    void canonicalIntegers() {
        assertAll(
                () -> assertEquals(0, PepXmlSearchHitReader.canonical("0", 5)),
                () -> assertEquals(5, PepXmlSearchHitReader.canonical("5", 5)),
                () -> assertEquals(-1, PepXmlSearchHitReader.canonical("6", 5)),
                () -> assertEquals(-1, PepXmlSearchHitReader.canonical(null, 5)),
                () -> assertEquals(-1, PepXmlSearchHitReader.canonical("", 5)),
                () -> assertEquals(-1, PepXmlSearchHitReader.canonical("05", 5)),
                () -> assertEquals(-1, PepXmlSearchHitReader.canonical("+5", 5)),
                () ->
                        assertEquals(
                                999_999_999_999_999_999L,
                                PepXmlSearchHitReader.canonical(
                                        "999999999999999999", Long.MAX_VALUE)),
                () ->
                        assertEquals(
                                -1,
                                PepXmlSearchHitReader.canonical(
                                        "1000000000000000000", Long.MAX_VALUE)));
    }

    @Test
    @DisplayName("the closed vocabulary is exactly the nineteen elements Comet writes")
    void vocabulary() {
        assertEquals(18, PepXmlSearchHitReader.PARENT.size(), "and the root, which has no parent");
    }
}
