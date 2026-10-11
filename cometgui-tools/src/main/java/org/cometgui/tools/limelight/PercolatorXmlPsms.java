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

package org.cometgui.tools.limelight;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.cometgui.tools.percolator.PoutDocument;

/**
 * What the Limelight converter will read from a run's Percolator XML: whether it is Percolator XML
 * at all, every PSM id, and how many PSMs are decoys.
 *
 * <p>Read by streaming ({@code StAX}): a real {@code pout} file is several megabytes (2 874 350
 * bytes for the two-file K562 fixture) and grows with the run, so the document is never held. Only
 * the {@code psm} elements' {@code p:psm_id} and {@code p:decoy} attributes are kept.
 *
 * <h2>Decoys, as the real files carry them</h2>
 *
 * <p>Percolator 3.07.1 run with {@code -X} alone writes target PSMs only and <strong>no {@code
 * p:decoy} attribute at all</strong> ({@code percolator-3.07.1/percolator.pout.xml}: 3897 PSMs,
 * none carrying it). With {@code -X -Z} every PSM carries {@code p:decoy="true"} or {@code "false"}
 * ({@code decoy-chain/withZ.pout.xml}: 3897 false, 2773 true). A missing attribute is therefore a
 * target, and {@link #decoyPsmCount()} counts {@code "true"} only; any other value is refused,
 * because a file that says something else about decoys is not one this reader understands. This
 * count is what design decision P12-7 reads at configuration time to decide whether Limelight decoy
 * import can be offered.
 *
 * <h2>Hardened</h2>
 *
 * <p>DTD support and external entities are forced off on the factory ({@link #harden}), for the
 * reason {@link PoutDocument} gives: the file is written by a binary the user may have registered
 * themselves. The namespace and document element are {@link PoutDocument}'s, so "is this Percolator
 * XML" has one definition in this module.
 *
 * <p><strong>Two readers of one format, recorded rather than hidden.</strong> {@link PoutDocument}
 * is the capability probe's reader of the synthetic document a probe run writes; it keeps counts
 * and the set of decoy values, which is what the {@code XML_OUTPUT} and {@code XML_DECOY_OUTPUT}
 * verdicts need. This one keeps the ids, which the pepXML-set check ({@link PepXmlSet}) needs and
 * the probe does not.
 */
public final class PercolatorXmlPsms {

    private final Path file;
    private final boolean percolatorXml;
    private final List<String> psmIds;
    private final int decoyPsmCount;

    private PercolatorXmlPsms(
            Path file, boolean percolatorXml, List<String> psmIds, int decoyPsmCount) {
        this.file = file;
        this.percolatorXml = percolatorXml;
        this.psmIds = psmIds;
        this.decoyPsmCount = decoyPsmCount;
    }

    /**
     * Reads a Percolator XML file.
     *
     * <p>A well-formed document whose element is not {@code percolator_output} in Percolator's
     * namespace is read no further and reported by {@link #isPercolatorXml()} as {@code false},
     * with no PSMs.
     *
     * @param file the file
     * @return what it holds
     * @throws IOException if the file is absent, is empty, is not well-formed XML, carries a
     *     DOCTYPE, or has a {@code psm} without an id or with a {@code p:decoy} value other than
     *     {@code true} or {@code false} -- each naming the file
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static PercolatorXmlPsms read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new IOException("there is no Percolator XML file at " + file);
        }
        if (Files.size(file) == 0L) {
            throw new IOException(
                    "the Percolator XML file "
                            + file
                            + " is empty (zero bytes), which is what an aborted Percolator run"
                            + " leaves behind");
        }
        try (InputStream bytes = Files.newInputStream(file)) {
            return walk(
                    file, harden(XMLInputFactory.newDefaultFactory()).createXMLStreamReader(bytes));
        } catch (XMLStreamException notADocument) {
            throw new IOException(
                    "the Percolator XML file "
                            + file
                            + " is not well-formed XML: "
                            + notADocument.getMessage(),
                    notADocument);
        }
    }

    /**
     * Forces a factory into the safe state, whatever state it arrived in.
     *
     * @param factory the factory
     * @return the same factory, with DTD support and external entities off
     * @throws NullPointerException if {@code factory} is {@code null}
     */
    static XMLInputFactory harden(XMLInputFactory factory) {
        Objects.requireNonNull(factory, "factory");
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        return factory;
    }

    private static PercolatorXmlPsms walk(Path file, XMLStreamReader reader)
            throws XMLStreamException, IOException {
        boolean root = true;
        List<String> ids = new ArrayList<>();
        int decoys = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.DTD) {
                throw new IOException(
                        "the Percolator XML file "
                                + file
                                + " carries a DOCTYPE, which Percolator never writes, so it is not"
                                + " read");
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            if (root) {
                root = false;
                if (!PoutDocument.ROOT_ELEMENT.equals(reader.getLocalName())
                        || !PoutDocument.NAMESPACE.equals(reader.getNamespaceURI())) {
                    return new PercolatorXmlPsms(file, false, List.of(), 0);
                }
            } else if ("psm".equals(reader.getLocalName())
                    && PoutDocument.NAMESPACE.equals(reader.getNamespaceURI())) {
                ids.add(psmId(file, reader));
                if (isDecoy(file, reader)) {
                    decoys++;
                }
            }
        }
        return new PercolatorXmlPsms(file, true, List.copyOf(ids), decoys);
    }

    private static String psmId(Path file, XMLStreamReader reader) throws IOException {
        String id = reader.getAttributeValue(PoutDocument.NAMESPACE, "psm_id");
        if (id == null || id.isEmpty()) {
            throw new IOException(
                    "the Percolator XML file "
                            + file
                            + " has a psm element without a p:psm_id at line "
                            + reader.getLocation().getLineNumber());
        }
        return id;
    }

    private static boolean isDecoy(Path file, XMLStreamReader reader) throws IOException {
        String decoy = reader.getAttributeValue(PoutDocument.NAMESPACE, "decoy");
        if (decoy == null || "false".equals(decoy)) {
            return false;
        }
        if ("true".equals(decoy)) {
            return true;
        }
        throw new IOException(
                "the Percolator XML file "
                        + file
                        + " says p:decoy=\""
                        + decoy
                        + "\" at line "
                        + reader.getLocation().getLineNumber()
                        + "; Percolator writes only \"true\" or \"false\"");
    }

    /**
     * The file this was read from.
     *
     * @return the file
     */
    public Path file() {
        return file;
    }

    /**
     * Whether the document element is {@code percolator_output} in Percolator's namespace.
     *
     * @return {@code true} for Percolator XML
     */
    public boolean isPercolatorXml() {
        return percolatorXml;
    }

    /**
     * Every {@code psm} element's {@code p:psm_id}, in document order.
     *
     * @return the ids, immutable; empty when the file is not Percolator XML
     */
    public List<String> psmIds() {
        return List.copyOf(psmIds);
    }

    /**
     * How many {@code psm} elements say {@code p:decoy="true"}.
     *
     * @return the count; zero for any run made without Percolator's {@code -Z}
     */
    public int decoyPsmCount() {
        return decoyPsmCount;
    }
}
