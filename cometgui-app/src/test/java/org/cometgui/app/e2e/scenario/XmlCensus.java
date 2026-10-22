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

package org.cometgui.app.e2e.scenario;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An XML document parsed by the JDK's own SAX parser, namespace-aware, with no DTD and no external
 * entity -- what "the pepXML and the Percolator XML parse" (canonical steps 18 and 20) means,
 * proved by a parser that shares nothing with the product -- and a census of what it holds: the
 * root element, how many elements of each local name, and how many carry each value of one
 * attribute.
 *
 * @param root the root element's local name
 * @param rootNamespace the root element's namespace URI, empty when it has none
 * @param elements how many elements of each local name the document holds
 * @param attributeValues for the attribute named when parsing, how many elements carry each value
 *     of it, keyed {@code <element local name>=<value>}
 */
public record XmlCensus(
        String root,
        String rootNamespace,
        Map<String, Long> elements,
        Map<String, Long> attributeValues) {

    /** The maps, copied. */
    public XmlCensus {
        elements = Map.copyOf(elements);
        attributeValues = Map.copyOf(attributeValues);
    }

    /**
     * Parses a document and takes its census.
     *
     * @param file the document
     * @param attribute the local name of an attribute whose values are counted, for example {@code
     *     decoy}
     * @return the census
     * @throws IOException if the file cannot be read
     * @throws AssertionError if it is not well-formed XML, naming the file and the parser's reason
     */
    public static XmlCensus of(Path file, String attribute) throws IOException {
        Map<String, Long> elements = new TreeMap<>();
        Map<String, Long> values = new TreeMap<>();
        String[] root = new String[2];
        DefaultHandler handler =
                new DefaultHandler() {
                    @Override
                    public void startElement(
                            String uri, String localName, String qualifiedName, Attributes found) {
                        if (root[0] == null) {
                            root[0] = localName;
                            root[1] = uri;
                        }
                        elements.merge(localName, 1L, Long::sum);
                        for (int index = 0; index < found.getLength(); index++) {
                            if (found.getLocalName(index).equals(attribute)) {
                                values.merge(
                                        localName + "=" + found.getValue(index), 1L, Long::sum);
                            }
                        }
                    }
                };
        try (InputStream in = Files.newInputStream(file)) {
            parser().parse(in, handler);
        } catch (SAXException malformed) {
            throw new AssertionError(file + " is not well-formed XML: " + malformed, malformed);
        }
        if (root[0] == null) {
            throw new AssertionError(file + " has no root element");
        }
        return new XmlCensus(root[0], root[1], elements, values);
    }

    /**
     * How many elements of a local name the document holds.
     *
     * @param localName the name
     * @return the count, 0 when there are none
     */
    public long count(String localName) {
        return elements.getOrDefault(localName, 0L);
    }

    private static SAXParser parser() {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newSAXParser();
        } catch (ParserConfigurationException | SAXException unavailable) {
            throw new AssertionError("the JDK's SAX parser cannot be configured", unavailable);
        }
    }
}
