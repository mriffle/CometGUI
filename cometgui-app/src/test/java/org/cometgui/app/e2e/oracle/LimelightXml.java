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

package org.cometgui.app.e2e.oracle;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * What a Limelight XML file holds, read by the test's own StAX walk on the JDK alone (canonical
 * scenario step 26; P14-7): its root element, how many {@code psm} and {@code reported_peptide}
 * elements it has, and how many of those PSMs carry a Percolator {@code q-value} annotation of at
 * most a cutoff -- compared exactly, as {@link BigDecimal}s -- so that the file can be checked
 * against the Percolator table it was converted from; and the default filter value the file gives
 * the Percolator PSM q-value, which is the conversion's Limelight q-value cutoff.
 *
 * <p>Reading it also proves it well-formed: a document the parser cannot read to its end fails with
 * the parser's message. Whether it is <em>valid</em> is a separate question, answered against the
 * converter's own schema.
 *
 * @param root the root element's local name
 * @param psms how many {@code psm} elements it has
 * @param reportedPeptides how many {@code reported_peptide} elements it has
 * @param psmsAtCutoff how many {@code psm} elements have a Percolator q-value of at most the cutoff
 * @param psmsWithoutQ how many {@code psm} elements have no usable Percolator q-value
 * @param qFilter the Percolator PSM q-value annotation type's {@code default_filter_value}
 */
public record LimelightXml(
        String root,
        int psms,
        int reportedPeptides,
        int psmsAtCutoff,
        int psmsWithoutQ,
        Optional<String> qFilter) {

    private static final Pattern NUMBER =
            Pattern.compile("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?");

    /**
     * Reads a file.
     *
     * @param file the Limelight XML
     * @param cutoff the q-value cutoff the PSMs are counted at, exactly as typed
     * @return what it holds
     * @throws IOException if it cannot be read, or is not well-formed XML
     */
    public static LimelightXml read(Path file, String cutoff) throws IOException {
        BigDecimal limit = new BigDecimal(cutoff);
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        String root = null;
        int psms = 0;
        int peptides = 0;
        int atCutoff = 0;
        int withoutQ = 0;
        Optional<String> filter = Optional.empty();
        String program = "";
        Deque<String> open = new ArrayDeque<>();
        String psmQ = null;
        try (InputStream in = Files.newInputStream(file)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.END_ELEMENT) {
                    String closed = open.pop();
                    if ("psm".equals(closed)) {
                        if (psmQ == null || !NUMBER.matcher(psmQ).matches()) {
                            withoutQ++;
                        } else if (new BigDecimal(psmQ).compareTo(limit) <= 0) {
                            atCutoff++;
                        }
                        psmQ = null;
                    }
                    continue;
                }
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String name = reader.getLocalName();
                if (root == null) {
                    root = name;
                }
                boolean inPsm = open.contains("psm");
                open.push(name);
                switch (name) {
                    case "psm" -> {
                        psms++;
                        psmQ = null;
                    }
                    case "reported_peptide" -> peptides++;
                    case "search_program" ->
                            program = String.valueOf(reader.getAttributeValue(null, "name"));
                    case "filterable_psm_annotation_type" -> {
                        if ("Percolator".equals(program)
                                && "q-value".equals(reader.getAttributeValue(null, "name"))) {
                            filter =
                                    Optional.ofNullable(
                                            reader.getAttributeValue(null, "default_filter_value"));
                        }
                    }
                    case "filterable_psm_annotation" -> {
                        if (inPsm
                                && "Percolator"
                                        .equals(reader.getAttributeValue(null, "search_program"))
                                && "q-value"
                                        .equals(
                                                reader.getAttributeValue(
                                                        null, "annotation_name"))) {
                            psmQ = reader.getAttributeValue(null, "value");
                        }
                    }
                    default -> {
                        // nothing else is counted
                    }
                }
            }
            reader.close();
        } catch (XMLStreamException malformed) {
            throw new IOException(
                    file + " is not well-formed XML: " + malformed.getMessage(), malformed);
        }
        if (root == null) {
            throw new IOException(file + " holds no element");
        }
        return new LimelightXml(root, psms, peptides, atCutoff, withoutQ, filter);
    }
}
