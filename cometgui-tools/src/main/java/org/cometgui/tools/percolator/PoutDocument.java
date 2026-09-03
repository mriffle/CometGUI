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

package org.cometgui.tools.percolator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * What a Percolator {@code pout} document actually says, read back from the file the binary wrote.
 *
 * <h2>The document is the evidence, not the exit code and not the file's existence</h2>
 *
 * <p>{@code R-PERC-02} requires the {@code XML_OUTPUT} probe to check the file, the namespace and
 * the {@code psm} count, and the reason is a trap the specification does not spell out but this
 * project executed: when Percolator aborts on <em>median decoy score &lt;= score at 1% FDR</em> the
 * output file <strong>exists and is zero bytes</strong>. "The file exists" is therefore not a probe
 * condition, and {@link #read} refuses an empty file with its own message rather than letting it
 * surface as a parse error, so that the one case this project has actually seen is the one a reader
 * is told about.
 *
 * <h2>The namespace, hand-typed and checked against the bytes</h2>
 *
 * <p>{@link #NAMESPACE} is {@code http://per-colator.com/percolator_out/15} in full. The
 * specification calls it "the {@code percolator_out/15} namespace" and this phase's work log quotes
 * it as {@code http://per-colator.com/percolator_out/}, one path segment short; the string here was
 * read out of the documents the real 3.06.5 and 3.07.1 binaries wrote on 2026-09-03, both of which
 * carry the full form. A prefix match would have hidden the difference, which is why this is an
 * equality test.
 *
 * <h2>Hardened, because the writer is not trusted</h2>
 *
 * <p>The document is produced by a binary the user may have pointed at themselves ({@code
 * R-TOOL-08}), so the reader disables DTD support and external entities outright: a local
 * "Percolator" that answered {@code -X} with a document containing an external entity would
 * otherwise be reading the probing machine's files. Both properties are forced on the factory
 * rather than assumed of it, for the reason phase 05 unit 4 recorded when five XML hardening calls
 * turned out to be deletable with the suite still green.
 */
public final class PoutDocument {

    /** The namespace a {@code pout} document carries, in full, hand-typed from the real files. */
    public static final String NAMESPACE = "http://per-colator.com/percolator_out/15";

    /** The document element a {@code pout} document carries. */
    public static final String ROOT_ELEMENT = "percolator_output";

    private final String rootElement;
    private final String namespace;
    private final int psmCount;
    private final int peptideCount;
    private final Set<String> psmDecoyValues;

    private PoutDocument(
            String rootElement,
            String namespace,
            int psmCount,
            int peptideCount,
            Set<String> psmDecoyValues) {
        this.rootElement = rootElement;
        this.namespace = namespace;
        this.psmCount = psmCount;
        this.peptideCount = peptideCount;
        this.psmDecoyValues = psmDecoyValues;
    }

    /**
     * Reads a document Percolator wrote.
     *
     * @param file the file the binary was told to write
     * @return what it says
     * @throws IOException if the file is absent, is empty, or is not well-formed XML -- each with
     *     its own message, because "it wrote nothing", "it wrote an empty file" and "it wrote
     *     something that is not a document" are three different things a probe verdict must not
     *     blur together
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static PoutDocument read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new IOException(
                    "Percolator wrote no file at " + file + ", so it produced no XML to inspect");
        }
        long size = Files.size(file);
        if (size == 0L) {
            throw new IOException(
                    "Percolator left a ZERO-BYTE file at "
                            + file
                            + ": the file exists and says nothing, which is what an aborted run"
                            + " leaves behind, so its existence is not evidence of XML output");
        }
        try (InputStream bytes = Files.newInputStream(file)) {
            return parse(bytes, file);
        }
    }

    private static PoutDocument parse(InputStream bytes, Path file) throws IOException {
        XMLStreamReader reader = null;
        try {
            reader = hardenedFactory().createXMLStreamReader(bytes);
            return walk(reader);
        } catch (XMLStreamException notADocument) {
            throw new IOException(
                    "the file Percolator wrote at "
                            + file
                            + " is not well-formed XML: "
                            + notADocument.getMessage(),
                    notADocument);
        } finally {
            closeQuietly(reader);
        }
    }

    /*
     * Forced, never assumed.  A factory whose defaults happen to be safe today is a protection that
     * has never been seen to work, and this is the parser that reads a document written by a binary
     * the user chose.
     */
    private static XMLInputFactory hardenedFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        return factory;
    }

    private static PoutDocument walk(XMLStreamReader reader) throws XMLStreamException {
        String rootElement = "";
        String namespace = "";
        int psmCount = 0;
        int peptideCount = 0;
        Set<String> decoyValues = new LinkedHashSet<>();
        while (reader.hasNext()) {
            if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            String local = reader.getLocalName();
            if (rootElement.isEmpty()) {
                rootElement = local;
                namespace = Objects.toString(reader.getNamespaceURI(), "");
            }
            if ("psm".equals(local)) {
                psmCount++;
                String decoy = reader.getAttributeValue(NAMESPACE, "decoy");
                if (decoy != null) {
                    decoyValues.add(decoy);
                }
            } else if ("peptide".equals(local)) {
                peptideCount++;
            }
        }
        return new PoutDocument(
                rootElement,
                namespace,
                psmCount,
                peptideCount,
                Collections.unmodifiableSet(decoyValues));
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException alreadyBroken) {
            /*
             * The document has already been read or has already failed; a close that fails after
             * that cannot change either verdict, and letting it replace the real diagnostic would
             * send a reader to the wrong place.
             */
        }
    }

    /**
     * The document element's local name.
     *
     * @return the name, or the empty string for a document with no elements at all
     */
    public String rootElement() {
        return rootElement;
    }

    /**
     * The document element's namespace.
     *
     * @return the namespace URI, or the empty string when the element carries none
     */
    public String namespace() {
        return namespace;
    }

    /**
     * How many {@code psm} elements the document holds.
     *
     * @return the count
     */
    public int psmCount() {
        return psmCount;
    }

    /**
     * How many {@code peptide} elements the document holds.
     *
     * @return the count
     */
    public int peptideCount() {
        return peptideCount;
    }

    /**
     * The distinct values of the {@code decoy} attribute seen on {@code psm} elements.
     *
     * <p>Empty for a target-only run: the real 3.07.1 binary writes no {@code decoy} attribute at
     * all without {@code -Z}, rather than writing {@code false} on every row. So "both values are
     * present" is a genuinely different observation from "the run produced psms", which is what
     * makes {@code XML_DECOY_OUTPUT} separable from {@code XML_OUTPUT}.
     *
     * @return the values, in the order first seen, immutable
     */
    public Set<String> psmDecoyValues() {
        return psmDecoyValues;
    }

    /**
     * Whether this is a Percolator {@code pout} document: the right element in the right namespace.
     *
     * @return {@code true} when both match exactly
     */
    public boolean isPercolatorOutput() {
        return ROOT_ELEMENT.equals(rootElement) && NAMESPACE.equals(namespace);
    }

    /**
     * Whether both decoy values are present, which is what {@code -Z} adds.
     *
     * @return {@code true} when {@code psm} elements carry both {@code true} and {@code false}
     */
    public boolean hasBothDecoyValues() {
        return psmDecoyValues.contains("true") && psmDecoyValues.contains("false");
    }
}
