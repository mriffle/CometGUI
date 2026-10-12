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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

/**
 * {@link SubmitImportWire}: every document it writes is valid against the wire schema JAXB
 * generated from the reference importer's classes, and every answer is read hardened. The expected
 * documents are typed out by hand.
 */
class SubmitImportWireTest {

    private static final String KEY = "LLwire-seeded-key-0123456789";

    private static final String PROLOG =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private static Schema wire() throws IOException, SAXException {
        SchemaFactory factory = SchemaFactory.newDefaultInstance();
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newSchema(
                new StreamSource(new ByteArrayInputStream(FakeLimelightServer.wireSchemaBytes())));
    }

    private static void valid(String document) throws IOException, SAXException {
        wire().newValidator()
                .validate(
                        new StreamSource(
                                new ByteArrayInputStream(
                                        document.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("the four requests, exactly, and each valid against the wire schema")
    void theRequests() throws IOException, SAXException {
        String auth = SubmitImportWire.authTest(7, KEY);
        String init = SubmitImportWire.initialize(7, KEY);
        String base64 =
                SubmitImportWire.uploadParameters(7, "4100", "a&b.xml", "f".repeat(64), KEY, true);
        String raw =
                SubmitImportWire.uploadParameters(7, "4100", "a.xml", "f".repeat(64), KEY, false);
        String submit = SubmitImportWire.finalSubmit(7, "4100", "a\"b.xml", KEY);

        assertEquals(
                PROLOG
                        + "<submitImport_AuthTest_Request_PgmXML submitProgramVersionNumber=\"16\""
                        + " projectIdentifier=\"7\" userSubmitImportProgramKey=\""
                        + KEY
                        + "\"/>",
                auth);
        assertEquals(
                PROLOG
                        + "<submitImport_Init_Request_PgmXML submitProgramVersionNumber=\"16\""
                        + " projectIdentifier=\"7\" userSubmitImportProgramKey=\""
                        + KEY
                        + "\" submitterSameMachine=\"false\"/>",
                init);
        assertEquals(
                PROLOG
                        + "<submitImport_UploadFile_Request_Common projectIdentifier=\"7\""
                        + " uploadKey=\"4100\" fileIndex=\"1\" fileType=\"1\""
                        + " filename=\"a&amp;b.xml\""
                        + " fileSHA256Hash=\""
                        + "f".repeat(64)
                        + "\" userSubmitImportProgramKey=\""
                        + KEY
                        + "\" submitProgramVersion=\"16\" newHeaderParamsFormatVersion=\"1\"/>",
                base64);
        assertFalse(raw.contains("newHeaderParamsFormatVersion"));
        assertFalse(raw.contains("\n"), "the raw form travels in a header on one line");
        assertEquals(
                PROLOG
                        + "<submitImport_FinalSubmit_Request_PgmXML"
                        + " submitProgramVersionNumber=\"16\""
                        + " projectIdentifier=\"7\" userSubmitImportProgramKey=\""
                        + KEY
                        + "\" uploadKey=\"4100\" submitterSameMachine=\"false\"><fileItems"
                        + " uploadedFilename=\"a&quot;b.xml\" fileType=\"1\" fileIndex=\"1\""
                        + " isLimelightXMLFile=\"true\"/>"
                        + "</submitImport_FinalSubmit_Request_PgmXML>",
                submit);
        for (String document : new String[] {auth, init, base64, raw, submit}) {
            valid(document);
        }
    }

    @Test
    @DisplayName("escaping: the five specials and tab, and control characters refused")
    void escaping() {
        assertEquals("&amp;&lt;&gt;&quot;&#9;'x", SubmitImportWire.escape("&<>\"\t'x"));
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class, () -> SubmitImportWire.escape("ab\ncd"));
        assertEquals(
                "a value sent to Limelight holds a control character at position 2",
                refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SubmitImportWire.escape("\u0000"));
    }

    @Test
    @DisplayName("an answer: root, success, flags (true ones only) and the message")
    void readingAnAnswer() throws IOException {
        SubmitImportWire.Answer answer =
                SubmitImportWire.read(
                        bytes(
                                "<submitImport_UploadFile_Response_PgmXML statusSuccess=\"false\""
                                        + " projectLocked=\"1\" fileSizeLimitExceeded=\"false\""
                                        + " maxSize=\"0\" statusFailErrorMessage=\"no &amp; no\">"
                                        + "<child a=\"true\"/>"
                                        + "</submitImport_UploadFile_Response_PgmXML>"),
                        "submitImport_UploadFile_Response_PgmXML");

        assertFalse(answer.success());
        assertEquals(Map.of("projectLocked", "true"), answer.flags());
        assertEquals(Optional.of("no & no"), answer.message());
        assertEquals(Optional.of("0"), answer.attribute("maxSize"));
        assertEquals(Optional.empty(), answer.attribute("child"));
        assertTrue(SubmitImportWire.read(bytes("<r statusSuccess=\"true\"/>"), "r").success());
        assertTrue(SubmitImportWire.read(bytes("<r statusSuccess=\"1\"/>"), "r").success());
        assertFalse(SubmitImportWire.read(bytes("<r statusSuccess=\"TRUE\"/>"), "r").success());
        assertFalse(SubmitImportWire.read(bytes("<r/>"), "r").success());
        assertEquals(
                Optional.empty(),
                SubmitImportWire.read(bytes("<r statusFailErrorMessage=\"  \"/>"), "r").message());
        assertEquals(
                Map.of(), SubmitImportWire.read(bytes("<r statusSuccess=\"true\"/>"), "r").flags());
    }

    @Test
    @DisplayName("refused: a DOCTYPE, an entity, the wrong root, malformed, or too large")
    void refusals() {
        assertEquals(
                "the server's answer carries a DOCTYPE, so it is not read",
                refusal("<!DOCTYPE r><r statusSuccess=\"true\"/>"));
        assertEquals(
                "the server's answer carries a DOCTYPE, so it is not read",
                refusal(
                        "<!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/hostname\">]>"
                                + "<r statusFailErrorMessage=\"&x;\"/>"));
        assertEquals("the server answered with <q>, not <r>", refusal("<q/>"));
        assertTrue(refusal("<r>").startsWith("the server's answer is not well-formed XML: "));
        byte[] exactly = new byte[1 << 20];
        java.util.Arrays.fill(exactly, (byte) ' ');
        byte[] element = bytes("<r statusSuccess=\"true\"/>");
        System.arraycopy(element, 0, exactly, 0, element.length);
        assertTrue(assertDoesNotThrowRead(exactly).success(), "an answer of exactly 1 MiB is read");
        IOException large =
                assertThrows(
                        IOException.class,
                        () -> SubmitImportWire.read(new byte[(1 << 20) + 1], "r"));
        assertEquals("the server's answer is larger than 1048576 bytes", large.getMessage());
    }

    private static String refusal(String document) {
        return assertThrows(IOException.class, () -> SubmitImportWire.read(bytes(document), "r"))
                .getMessage();
    }

    private static SubmitImportWire.Answer assertDoesNotThrowRead(byte[] body) {
        try {
            return SubmitImportWire.read(body, "r");
        } catch (IOException refused) {
            throw new AssertionError("refused: " + refused.getMessage(), refused);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
