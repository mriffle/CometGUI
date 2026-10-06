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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.RunLayout;

/**
 * Validates one pepXML file Comet wrote ({@code R-CMT-03}'s per-file output check).
 *
 * <p>The file is <strong>streamed</strong> with a hardened StAX parser -- never loaded whole, since
 * a pepXML is megabytes per thousand spectra -- and is refused, with a {@link CometOutputException}
 * naming it, unless:
 *
 * <ul>
 *   <li>it exists, is a regular file and is not empty;
 *   <li>it is well-formed XML to its end -- a file truncated part-way is not;
 *   <li>it declares no {@code DOCTYPE}: Comet writes none, and a DTD is how an external entity
 *       reaches the file system or the network ({@link #harden(XMLInputFactory)});
 *   <li>its document element is {@code msms_pipeline_analysis} in the pepXML namespace {@value
 *       #NAMESPACE};
 *   <li>it holds exactly one {@code msms_run_summary}, whose {@code base_name} followed by its
 *       {@code raw_data} is the spectrum file the search was given -- this is where Comet names its
 *       input, measured: {@code base_name="<dir>/k562_3"}, {@code raw_data=".mzML"}, with the
 *       extension's case kept ({@code .MZML}) and the path XML-escaped ({@code &amp;amp;});
 *   <li>it holds exactly one {@code search_summary}, whose {@code base_name} is the {@code -N} base
 *       the search was given, so the file is this invocation's and not a stale one.
 * </ul>
 *
 * <p>It counts the {@code spectrum_query} elements. Zero is not refused here: a search that matched
 * no spectrum writes a valid pepXML, and the PIN check is what refuses a search with no rows.
 *
 * <p>With {@code decoy_search = 2} Comet also writes {@code <base>.decoy.pep.xml}, measured to name
 * the same input and the same {@code -N} base; it can be validated by {@link #validate(Path, Path,
 * Path)}.
 */
public final class CometPepXmlValidator {

    /** The pepXML namespace Comet writes. */
    public static final String NAMESPACE = "http://regis-web.systemsbiology.net/pepXML";

    /** The document element of a pepXML file. */
    static final String ROOT = "msms_pipeline_analysis";

    static final String RUN_SUMMARY = "msms_run_summary";

    static final String SEARCH_SUMMARY = "search_summary";

    static final String SPECTRUM_QUERY = "spectrum_query";

    static final String BASE_NAME = "base_name";

    static final String RAW_DATA = "raw_data";

    private CometPepXmlValidator() {}

    /**
     * Validates the pepXML file of one input of a run: {@link RunLayout#pepXmlFile(String)}, which
     * must name the input's path and {@link RunLayout#cometOutputBase(String)}.
     *
     * @param run the run
     * @param input the spectrum file and its base name
     * @return what the file says
     * @throws CometOutputException if the file is missing, unreadable or not a valid pepXML of that
     *     input, naming it
     */
    public static PepXmlSummary validate(RunLayout run, OutputBase input)
            throws CometOutputException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(input, "input");
        return validate(
                run.pepXmlFile(input.base()), input.input(), run.cometOutputBase(input.base()));
    }

    /**
     * Validates a pepXML file against the input and the {@code -N} base it must name.
     *
     * @param pepXml the file
     * @param input the spectrum file the search was given, as it was given
     * @param outputBase the {@code -N} base the search was given
     * @return what the file says
     * @throws CometOutputException if the file is missing, unreadable or not a valid pepXML of that
     *     input, naming it
     */
    public static PepXmlSummary validate(Path pepXml, Path input, Path outputBase)
            throws CometOutputException {
        Objects.requireNonNull(pepXml, "pepXml");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(outputBase, "outputBase");
        if (Files.isDirectory(pepXml)) {
            throw refused(pepXml, "is a directory, not a file", null);
        }
        long size;
        try {
            size = Files.size(pepXml);
        } catch (NoSuchFileException missing) {
            throw refused(pepXml, "does not exist: Comet did not write it", missing);
        } catch (IOException unreadable) {
            throw refused(pepXml, "cannot be read: " + unreadable.getMessage(), unreadable);
        }
        if (size == 0) {
            throw refused(pepXml, "is empty: Comet left a zero-byte file", null);
        }
        Walk walk = new Walk();
        try (InputStream bytes = Files.newInputStream(pepXml)) {
            walk.through(harden(XMLInputFactory.newDefaultFactory()).createXMLStreamReader(bytes));
        } catch (XMLStreamException malformed) {
            throw refused(pepXml, "is not well-formed XML: " + malformed.getMessage(), malformed);
        } catch (IOException unreadable) {
            throw refused(pepXml, "cannot be read: " + unreadable.getMessage(), unreadable);
        }
        if (walk.doctype) {
            throw refused(
                    pepXml,
                    "declares a DOCTYPE, which Comet never writes and which is refused unread",
                    null);
        }
        if (!ROOT.equals(walk.root) || !NAMESPACE.equals(walk.rootNamespace)) {
            throw refused(
                    pepXml,
                    "is not a pepXML file: its document element is {"
                            + walk.rootNamespace
                            + "}"
                            + walk.root
                            + ", not {"
                            + NAMESPACE
                            + "}"
                            + ROOT,
                    null);
        }
        if (walk.runSummaries != 1) {
            throw refused(
                    pepXml,
                    "holds " + walk.runSummaries + " msms_run_summary elements, not exactly one",
                    null);
        }
        if (walk.searchSummaries != 1) {
            throw refused(
                    pepXml,
                    "holds " + walk.searchSummaries + " search_summary elements, not exactly one",
                    null);
        }
        String named = walk.baseName + walk.rawData;
        if (!named.equals(input.toString())) {
            throw refused(
                    pepXml,
                    "names its input \""
                            + named
                            + "\" (msms_run_summary base_name and raw_data), not the spectrum file"
                            + " the search was given, \""
                            + input
                            + "\"",
                    null);
        }
        if (!walk.searchBase.equals(outputBase.toString())) {
            throw refused(
                    pepXml,
                    "names the output base \""
                            + walk.searchBase
                            + "\" (search_summary base_name), not the -N base the search was"
                            + " given, \""
                            + outputBase
                            + "\"",
                    null);
        }
        return new PepXmlSummary(pepXml, named, walk.searchBase, walk.spectrumQueries);
    }

    /**
     * Forces a factory into the safe state, whatever state it arrived in: no DTD support and no
     * external entities.
     *
     * <p>Forced rather than assumed, as {@code PoutDocument.harden} and the installer's {@code
     * PkgPayloadReader.harden} are: a factory whose defaults happen to be safe makes a hardening
     * call deletable with every test green, so a test hands this method a factory set to the unsafe
     * values and requires it back safe. A {@code DOCTYPE} is refused outright as well.
     *
     * @param factory the factory
     * @return the same factory, hardened
     */
    static XMLInputFactory harden(XMLInputFactory factory) {
        Objects.requireNonNull(factory, "factory");
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        return factory;
    }

    private static CometOutputException refused(Path file, String problem, Throwable cause) {
        return new CometOutputException(file, "the pepXML file " + file + " " + problem, cause);
    }

    /** What one pass over the document saw. */
    private static final class Walk {

        private boolean doctype;

        private String root = "";

        private String rootNamespace = "";

        private int runSummaries;

        private int searchSummaries;

        private String baseName = "";

        private String rawData = "";

        private String searchBase = "";

        private long spectrumQueries;

        /*
         * The reader is not closed here: XMLStreamReader.close frees only the reader's own state
         * and does not close the input stream, which the caller's try-with-resources closes -- the
         * same reasoning PoutDocument records.  It stops at a DOCTYPE without reading further.
         */
        void through(XMLStreamReader reader) throws XMLStreamException {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) {
                    doctype = true;
                    return;
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    element(reader);
                }
            }
        }

        private void element(XMLStreamReader reader) {
            String local = reader.getLocalName();
            String namespace = Objects.toString(reader.getNamespaceURI(), "");
            if (root.isEmpty()) {
                root = local;
                rootNamespace = namespace;
            }
            if (!NAMESPACE.equals(namespace)) {
                return;
            }
            if (RUN_SUMMARY.equals(local)) {
                runSummaries++;
                baseName = Objects.toString(reader.getAttributeValue(null, BASE_NAME), "");
                rawData = Objects.toString(reader.getAttributeValue(null, RAW_DATA), "");
            } else if (SEARCH_SUMMARY.equals(local)) {
                searchSummaries++;
                searchBase = Objects.toString(reader.getAttributeValue(null, BASE_NAME), "");
            } else if (SPECTRUM_QUERY.equals(local)) {
                spectrumQueries++;
            }
        }
    }
}
