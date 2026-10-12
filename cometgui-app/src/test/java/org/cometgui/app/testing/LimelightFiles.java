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

package org.cometgui.app.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;

/**
 * A GUI test's own reading of a Limelight XML file and of what the run recorded about it -- sharing
 * no code with the product's judge: the converter's schema read from the installed JAR with {@link
 * ZipFile}, validation with the JDK's own {@link SchemaFactory}, the {@code psm} elements and the
 * Percolator PSM q-value filter counted with a plain StAX walk, the run's target PSM rows counted
 * from its Percolator table, and the {@code limelight.converted} events read from the run's {@code
 * events.log} with the tests' own JSON reader.
 */
public final class LimelightFiles {

    /** The schema's entry at the root of the converter JAR. */
    public static final String SCHEMA_ENTRY = "limelight-xml.xsd";

    private LimelightFiles() {}

    /**
     * Validates a Limelight XML file against the schema inside the converter JAR.
     *
     * @param file the Limelight XML
     * @param jar the installed converter JAR
     * @throws IOException if either cannot be read
     * @throws AssertionError naming the first schema violation
     */
    public static void assertValid(Path file, Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(SCHEMA_ENTRY);
            assertTrue(entry != null, jar + " holds no " + SCHEMA_ENTRY);
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Schema schema;
            try (InputStream in = zip.getInputStream(entry)) {
                schema = factory.newSchema(new StreamSource(in));
            }
            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(file.toFile()));
        } catch (SAXException invalid) {
            throw new AssertionError(
                    file + " is not valid against the converter's own schema: " + invalid, invalid);
        }
    }

    /**
     * How many {@code psm} elements a Limelight XML file holds, and the default filter value of its
     * Percolator PSM-level {@code q-value} annotation.
     *
     * @param file the Limelight XML
     * @return the count and the value
     * @throws IOException if it cannot be read
     */
    public static Read read(Path file) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        int psms = 0;
        String program = "";
        Optional<String> filter = Optional.empty();
        try (InputStream in = Files.newInputStream(file)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String name = reader.getLocalName();
                if ("psm".equals(name)) {
                    psms++;
                } else if ("search_program".equals(name)) {
                    program = String.valueOf(reader.getAttributeValue(null, "name"));
                } else if ("filterable_psm_annotation_type".equals(name)
                        && "Percolator".equals(program)
                        && "q-value".equals(reader.getAttributeValue(null, "name"))) {
                    filter =
                            Optional.ofNullable(
                                    reader.getAttributeValue(null, "default_filter_value"));
                }
            }
            reader.close();
        } catch (XMLStreamException unreadable) {
            throw new IOException(file + " cannot be read as XML", unreadable);
        }
        return new Read(psms, filter);
    }

    /**
     * How many target PSM rows a run's Percolator table holds: its lines after the header.
     *
     * @param run the run's directory
     * @return the rows
     * @throws IOException if the table cannot be read
     */
    public static int targetPsmRows(Path run) throws IOException {
        List<String> lines =
                Files.readAllLines(
                        run.resolve("outputs/percolator/psms.tsv"), StandardCharsets.UTF_8);
        assertTrue(lines.get(0).startsWith("PSMId\t"), "the table's header: " + lines.get(0));
        return (int) lines.stream().skip(1).filter(line -> !line.isBlank()).count();
    }

    /**
     * The payloads of a run's events of one type, oldest first.
     *
     * @param run the run's directory
     * @param type the event type, for example {@code limelight.converted}
     * @return the payloads
     * @throws IOException if the log cannot be read
     */
    public static List<Map<String, Object>> events(Path run, String type) throws IOException {
        Path log = run.resolve("provenance/events.log");
        List<Map<String, Object>> found = new ArrayList<>();
        if (!Files.exists(log)) {
            return found;
        }
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            Map<String, Object> event = TestJson.object(line);
            if (type.equals(event.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) event.get("payload");
                found.add(payload);
            }
        }
        return found;
    }

    /**
     * Checks a file's SHA-256, computed here, against what the interface showed.
     *
     * @param file the file
     * @param shown the SHA-256 the interface showed
     * @throws IOException if the file cannot be read
     */
    public static void assertSha256(Path file, String shown) throws IOException {
        assertEquals(RealSearch.sha256(file), shown, "the SHA-256 shown for " + file);
    }

    /**
     * What a test read from a Limelight XML file.
     *
     * @param psms how many {@code psm} elements it holds
     * @param qFilter the Percolator PSM q-value annotation's default filter value
     */
    public record Read(int psms, Optional<String> qFilter) {}
}
