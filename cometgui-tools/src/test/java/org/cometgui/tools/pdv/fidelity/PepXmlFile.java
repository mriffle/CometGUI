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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * A Comet pepXML read by the fidelity suite's own StAX reader, sharing nothing with the product's
 * pepXML reader (design decision P11-9). Every attribute is kept as the file's text.
 */
final class PepXmlFile {

    /** A modification declaration of the search summary. */
    record Declaration(
            String element, String site, String massdiff, String mass, String variable) {}

    /** One residue's modification: its 1-based position and the attributes Comet wrote. */
    record ResidueMod(int position, String mass, String staticDelta, String variableDelta) {}

    /** One search hit, at its 1-based position in its query. */
    record Hit(
            int position,
            String hitRank,
            String peptide,
            String previous,
            String next,
            List<String> proteins,
            String ntermMass,
            String ctermMass,
            List<ResidueMod> residueMods) {}

    /** One spectrum query. */
    record Query(
            String spectrum,
            String nativeId,
            String startScan,
            String assumedCharge,
            String retentionTimeSec,
            List<Hit> hits) {}

    private final Path file;
    private final List<Declaration> declarations;
    private final List<Query> queries;
    private final Map<String, List<Query>> byScanAndCharge = new HashMap<>();

    private PepXmlFile(Path file, List<Declaration> declarations, List<Query> queries) {
        this.file = file;
        this.declarations = List.copyOf(declarations);
        this.queries = List.copyOf(queries);
        for (Query query : queries) {
            byScanAndCharge
                    .computeIfAbsent(
                            query.startScan() + "/" + query.assumedCharge(),
                            ignored -> new ArrayList<>())
                    .add(query);
        }
    }

    Path file() {
        return file;
    }

    List<Declaration> declarations() {
        return declarations;
    }

    List<Query> queries() {
        return queries;
    }

    /**
     * The queries with a start scan and an assumed charge.
     *
     * @param scan the scan, as text
     * @param charge the charge, as text
     * @return every such query, in file order
     */
    List<Query> queries(String scan, String charge) {
        return byScanAndCharge.getOrDefault(scan + "/" + charge, List.of());
    }

    /**
     * Reads a pepXML file.
     *
     * @param file the file
     * @return what it holds
     * @throws IOException if it cannot be read or parsed
     */
    static PepXmlFile read(Path file) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        List<Declaration> declarations = new ArrayList<>();
        List<Query> queries = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file)) {
            XMLStreamReader xml = factory.createXMLStreamReader(in);
            try {
                String[] query = null;
                List<Hit> hits = null;
                String[] hit = null;
                List<String> proteins = null;
                List<ResidueMod> mods = null;
                String nterm = null;
                String cterm = null;
                while (xml.hasNext()) {
                    int event = xml.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        switch (xml.getLocalName()) {
                            case "aminoacid_modification" ->
                                    declarations.add(
                                            new Declaration(
                                                    "aminoacid",
                                                    attribute(xml, "aminoacid"),
                                                    attribute(xml, "massdiff"),
                                                    attribute(xml, "mass"),
                                                    attribute(xml, "variable")));
                            case "terminal_modification" ->
                                    declarations.add(
                                            new Declaration(
                                                    "terminal",
                                                    attribute(xml, "terminus"),
                                                    attribute(xml, "massdiff"),
                                                    attribute(xml, "mass"),
                                                    attribute(xml, "variable")));
                            case "spectrum_query" -> {
                                query =
                                        new String[] {
                                            attribute(xml, "spectrum"),
                                            attribute(xml, "spectrumNativeID"),
                                            attribute(xml, "start_scan"),
                                            attribute(xml, "assumed_charge"),
                                            attribute(xml, "retention_time_sec")
                                        };
                                hits = new ArrayList<>();
                            }
                            case "search_hit" -> {
                                hit =
                                        new String[] {
                                            attribute(xml, "hit_rank"),
                                            attribute(xml, "peptide"),
                                            attribute(xml, "peptide_prev_aa"),
                                            attribute(xml, "peptide_next_aa")
                                        };
                                proteins = new ArrayList<>();
                                proteins.add(attribute(xml, "protein"));
                                mods = new ArrayList<>();
                                nterm = null;
                                cterm = null;
                            }
                            case "alternative_protein" -> {
                                if (proteins == null) {
                                    throw new IOException(file + ": a protein outside a hit");
                                }
                                proteins.add(attribute(xml, "protein"));
                            }
                            case "modification_info" -> {
                                nterm = attribute(xml, "mod_nterm_mass");
                                cterm = attribute(xml, "mod_cterm_mass");
                            }
                            case "mod_aminoacid_mass" -> {
                                if (mods == null) {
                                    throw new IOException(file + ": a modification outside a hit");
                                }
                                mods.add(
                                        new ResidueMod(
                                                Integer.parseInt(attribute(xml, "position")),
                                                attribute(xml, "mass"),
                                                attribute(xml, "static"),
                                                attribute(xml, "variable")));
                            }
                            default -> {
                                // every other element carries nothing the mzTab states
                            }
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if ("search_hit".equals(xml.getLocalName())) {
                            if (hits == null || hit == null) {
                                throw new IOException(file + ": a hit outside a query");
                            }
                            hits.add(
                                    new Hit(
                                            hits.size() + 1,
                                            hit[0],
                                            hit[1],
                                            hit[2],
                                            hit[3],
                                            List.copyOf(proteins),
                                            nterm,
                                            cterm,
                                            List.copyOf(mods)));
                            hit = null;
                        } else if ("spectrum_query".equals(xml.getLocalName())) {
                            if (query == null || hits == null) {
                                throw new IOException(file + ": an unbalanced query");
                            }
                            queries.add(
                                    new Query(
                                            query[0],
                                            query[1],
                                            query[2],
                                            query[3],
                                            query[4],
                                            List.copyOf(hits)));
                            query = null;
                            hits = null;
                        }
                    }
                }
            } finally {
                xml.close();
            }
        } catch (XMLStreamException | NumberFormatException broken) {
            throw new IOException(file + " is not readable pepXML: " + broken.getMessage(), broken);
        }
        return new PepXmlFile(file, declarations, queries);
    }

    private static String attribute(XMLStreamReader xml, String name) {
        return xml.getAttributeValue(null, name);
    }
}
