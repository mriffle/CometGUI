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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A second, independent reading of a Comet pepXML, for the real-file tests: <strong>no XML
 * parser</strong>, only Comet's own layout -- one element per line -- and a regular expression over
 * each tag's attributes. It shares no code with {@link PepXmlSearchHitReader}, so a value both
 * agree on was not produced by the same mistake twice (P11-9's principle, applied to the reader's
 * own tests).
 */
final class PepXmlLines {

    private static final Pattern TAG = Pattern.compile("^\\s*<([a-z_]+)\\b(.*?)/?>\\s*$");

    private static final Pattern ATTRIBUTE = Pattern.compile("([A-Za-z_:]+)=\"([^\"]*)\"");

    /** One element's attributes, unescaped, in document order. */
    record Element(String name, Map<String, String> attributes) {

        String get(String attribute) {
            return attributes.get(attribute);
        }

        Optional<String> optional(String attribute) {
            return Optional.ofNullable(attributes.get(attribute));
        }
    }

    /** One search hit and its children. */
    record Hit(
            Element hit,
            List<String> alternatives,
            List<Element> scores,
            Element modificationInfo,
            List<Element> residues) {}

    /** One spectrum query and its hits. */
    record Query(Element query, List<Hit> hits) {}

    /** A whole file. */
    record Document(List<Element> declarations, List<Query> queries) {}

    private PepXmlLines() {}

    static Document read(Path file) throws IOException {
        List<Element> declarations = new ArrayList<>();
        List<Query> queries = new ArrayList<>();
        Element query = null;
        List<Hit> hits = null;
        Element hit = null;
        List<String> alternatives = null;
        List<Element> scores = null;
        Element modificationInfo = null;
        List<Element> residues = null;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if ("</search_hit>".equals(trimmed)) {
                hits.add(new Hit(hit, alternatives, scores, modificationInfo, residues));
                hit = null;
                continue;
            }
            if ("</spectrum_query>".equals(trimmed)) {
                queries.add(new Query(query, hits));
                query = null;
                continue;
            }
            Matcher tag = TAG.matcher(line);
            if (!tag.matches()) {
                continue;
            }
            Element element = new Element(tag.group(1), attributes(tag.group(2)));
            switch (element.name()) {
                case "aminoacid_modification", "terminal_modification" -> declarations.add(element);
                case "spectrum_query" -> {
                    query = element;
                    hits = new ArrayList<>();
                }
                case "search_hit" -> {
                    hit = element;
                    alternatives = new ArrayList<>();
                    scores = new ArrayList<>();
                    modificationInfo = null;
                    residues = new ArrayList<>();
                }
                case "alternative_protein" -> alternatives.add(element.get("protein"));
                case "search_score" -> scores.add(element);
                case "modification_info" -> modificationInfo = element;
                case "mod_aminoacid_mass" -> residues.add(element);
                default -> {
                    // Not compared.
                }
            }
        }
        return new Document(declarations, queries);
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher attribute = ATTRIBUTE.matcher(text);
        while (attribute.find()) {
            out.put(attribute.group(1), unescape(attribute.group(2)));
        }
        return out;
    }

    private static String unescape(String text) {
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /**
     * Requires the reader's queries to equal this reading, value by value, as text.
     *
     * @param expected this reading
     * @param actual what the reader delivered
     */
    static void assertSame(Document expected, List<PepXmlShapes.Visited> actual) {
        assertEquals(expected.queries().size(), actual.size(), "spectrum queries");
        for (int index = 0; index < actual.size(); index++) {
            Query want = expected.queries().get(index);
            PepXmlQuery query = actual.get(index).query();
            String where = "query " + want.query().get("spectrum");
            Element q = want.query();
            assertEquals(q.get("spectrum"), query.spectrum(), where);
            assertEquals(q.optional("spectrumNativeID"), query.spectrumNativeId(), where);
            assertEquals(q.get("start_scan"), Long.toString(query.startScan()), where);
            assertEquals(q.get("end_scan"), Long.toString(query.endScan()), where);
            assertEquals(
                    q.get("precursor_neutral_mass"), query.precursorNeutralMass().text(), where);
            assertEquals(q.get("assumed_charge"), Integer.toString(query.assumedCharge()), where);
            assertEquals(q.get("index"), Long.toString(query.index()), where);
            assertEquals(
                    q.optional("retention_time_sec"),
                    query.retentionTimeSec().map(PepXmlDecimal::text),
                    where);
            List<PepXmlSearchHit> hits = actual.get(index).hits();
            assertEquals(want.hits().size(), hits.size(), where + ": hits");
            for (int h = 0; h < hits.size(); h++) {
                assertHit(want.hits().get(h), hits.get(h), h + 1, where + ", hit " + (h + 1));
            }
        }
    }

    private static void assertHit(Hit want, PepXmlSearchHit hit, int position, String where) {
        Element h = want.hit();
        assertEquals(position, hit.position(), where);
        assertEquals(h.get("hit_rank"), Integer.toString(hit.hitRank()), where);
        assertEquals(h.get("peptide"), hit.peptide(), where);
        assertEquals(h.get("peptide_prev_aa"), hit.peptidePrevAa(), where);
        assertEquals(h.get("peptide_next_aa"), hit.peptideNextAa(), where);
        assertEquals(h.get("protein"), hit.protein(), where);
        assertEquals(want.alternatives(), hit.alternativeProteins(), where);
        assertEquals(h.get("num_tot_proteins"), Long.toString(hit.numTotProteins()), where);
        assertEquals(h.get("calc_neutral_pep_mass"), hit.calcNeutralPepMass().text(), where);
        assertEquals(h.get("massdiff"), hit.massDiff().text(), where);
        assertEquals(
                want.scores().stream()
                        .map(s -> new PepXmlNameValue(s.get("name"), s.get("value")))
                        .toList(),
                hit.searchScores(),
                where);
        if (want.modificationInfo() == null) {
            assertEquals(Optional.empty(), hit.modificationInfo(), where);
            return;
        }
        PepXmlModificationInfo info = hit.modificationInfo().orElseThrow();
        Element m = want.modificationInfo();
        assertEquals(m.get("modified_peptide"), info.modifiedPeptide(), where);
        assertEquals(
                m.optional("mod_nterm_mass"), info.ntermMass().map(PepXmlDecimal::text), where);
        assertEquals(
                m.optional("mod_cterm_mass"), info.ctermMass().map(PepXmlDecimal::text), where);
        assertEquals(want.residues().size(), info.residues().size(), where);
        for (int r = 0; r < info.residues().size(); r++) {
            Element e = want.residues().get(r);
            PepXmlResidueModification mod = info.residues().get(r);
            String at = where + ", residue " + e.get("position");
            assertEquals(e.get("position"), Integer.toString(mod.position()), at);
            assertEquals(
                    h.get("peptide").charAt(Integer.parseInt(e.get("position")) - 1),
                    mod.residue(),
                    at);
            assertEquals(e.get("mass"), mod.mass().text(), at);
            assertEquals(e.optional("static"), mod.staticDelta().map(PepXmlDecimal::text), at);
            assertEquals(e.optional("variable"), mod.variableDelta().map(PepXmlDecimal::text), at);
            assertEquals(e.optional("source"), mod.source(), at);
            assertEquals(e.optional("id"), mod.peffId(), at);
        }
        assertEquals(List.of(), info.sequenceVariants(), where);
    }

    /**
     * Requires the reader's declarations to equal this reading's.
     *
     * @param expected this reading
     * @param summary what the reader delivered
     */
    static void assertDeclarations(Document expected, PepXmlSearchSummary summary) {
        List<String> want = new ArrayList<>();
        for (Element e : expected.declarations()) {
            want.add(e.name() + " " + e.attributes());
        }
        List<String> got = new ArrayList<>();
        for (PepXmlAminoAcidModification a : summary.aminoAcidModifications()) {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("aminoacid", a.aminoAcid());
            attributes.put("massdiff", a.massDiff().text());
            attributes.put("mass", a.mass().text());
            attributes.put("variable", a.variable() ? "Y" : "N");
            if (a.binary()) {
                attributes.put("binary", "Y");
            }
            got.add("aminoacid_modification " + attributes);
        }
        for (PepXmlTerminalModification t : summary.terminalModifications()) {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("terminus", t.terminus().name());
            attributes.put("massdiff", t.massDiff().text());
            attributes.put("mass", t.mass().text());
            attributes.put("variable", t.variable() ? "Y" : "N");
            attributes.put("protein_terminus", t.proteinTerminus() ? "Y" : "N");
            got.add("terminal_modification " + attributes);
        }
        assertEquals(want, got, "declarations, aminoacid_modification first as Comet writes them");
    }
}
