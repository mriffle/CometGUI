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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The submit-import protocol's XML, written and read exactly as {@code limelight-core} v8.3.1's
 * JAXB classes write and read it (phase 12, design decision P12-14). The names are JAXB's, not the
 * Java field names -- {@code statusFailErrorMessage}, not {@code statusFail_ErrorMessage} -- and
 * the committed wire schema {@code
 * scripts/spikes/limelight_upload_spike/submit-import-wire-v16.xsd}, which JAXB generated from
 * those classes, is what the test fake validates every request against.
 *
 * <p><strong>What is sent, and what is not.</strong> Every request carries the program version, the
 * project id and the key, because the server requires all three on every call. The file's
 * parameters carry its name, its SHA-256, the file index and type, and the import's {@code
 * uploadKey}. They do <em>not</em> carry the two optional attributes the reference importer adds,
 * {@code absoluteFilenameWPathOnSubmitMachine} and {@code canonicalFilenameWPathOnSubmitMachine}:
 * the server only stores them as tracking metadata (nullable columns; the v1 upload-file controller
 * of {@code limelight-core} v8.3.1, lines 713-714), and they would tell a third-party server the
 * scientist's user name and directory layout. The least the protocol requires is the default.
 * Likewise the initialisation says {@code submitterSameMachine="false"}: that flag asks the server
 * to prepare a same-machine hand-over this client never uses.
 *
 * <p><strong>Reading an answer is hardened</strong> exactly as the Limelight XML reader is ({@link
 * LimelightXmlJudge#harden}): no DTD, no external entities, a document carrying a DOCTYPE is
 * refused, and at most {@value #MAX_ANSWER_BYTES} bytes are read.
 */
final class SubmitImportWire {

    /** The submit-program version this client speaks: v8.3.1's. */
    static final int PROGRAM_VERSION = 16;

    /** From this server version on the file's parameters travel Base64-encoded. */
    static final int BASE64_PARAMETERS_MINIMUM = 16;

    /** The header the parameters travel in, Base64-encoded, for a server at version 16 or later. */
    static final String PARAMETERS_HEADER_BASE64 = "limelight_upload_file_params_xml_base_64";

    /** The header the parameters travel in, as raw single-line XML, for an older server. */
    static final String PARAMETERS_HEADER_RAW = "limelight_upload_file_params_xml";

    /** The attribute the key travels in. */
    static final String KEY_ATTRIBUTE = "userSubmitImportProgramKey";

    /** The attribute every answer carries. */
    static final String SUCCESS_ATTRIBUTE = "statusSuccess";

    /** The attribute the server's own explanation of a refusal travels in. */
    static final String MESSAGE_ATTRIBUTE = "statusFailErrorMessage";

    /** The attribute of the authentication answer naming the server's program version. */
    static final String SERVER_VERSION_ATTRIBUTE = "submitProgramVersionNumberCurrentPerWebapp";

    /** The attribute of the initialisation answer carrying the tracking id. */
    static final String UPLOAD_KEY_ATTRIBUTE = "uploadKey";

    /** The attribute of the final-submit answer naming the server's import directory. */
    static final String IMPORTER_SUB_DIR_ATTRIBUTE = "importerSubDir";

    /** {@code LimelightSubmit_FileImportFileType}: a Limelight XML file. */
    static final int LIMELIGHT_XML_FILE_TYPE = 1;

    /** The one file's index. */
    static final int FILE_INDEX = 1;

    /** The most an answer may be: every real answer is well under a kilobyte. */
    static final int MAX_ANSWER_BYTES = 1 << 20;

    private static final String PROLOG =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private SubmitImportWire() {}

    /**
     * The authentication test.
     *
     * @param projectId the project
     * @param key the key
     * @return the request document
     */
    static String authTest(int projectId, String key) {
        return PROLOG + element("submitImport_AuthTest_Request_PgmXML", common(projectId, key), "");
    }

    /**
     * The initialisation.
     *
     * @param projectId the project
     * @param key the key
     * @return the request document
     */
    static String initialize(int projectId, String key) {
        Map<String, String> attributes = common(projectId, key);
        attributes.put("submitterSameMachine", "false");
        return PROLOG + element("submitImport_Init_Request_PgmXML", attributes, "");
    }

    /**
     * The file's parameters, sent in a header with the file as the body.
     *
     * @param projectId the project
     * @param uploadKey the import's tracking id
     * @param fileName the file's name
     * @param sha256 the file's SHA-256
     * @param key the key
     * @param base64Form whether the server is at version 16 or later (the parameters then carry
     *     {@code newHeaderParamsFormatVersion="1"})
     * @return the parameters document, on one line
     */
    static String uploadParameters(
            int projectId,
            String uploadKey,
            String fileName,
            String sha256,
            String key,
            boolean base64Form) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("projectIdentifier", Integer.toString(projectId));
        attributes.put(UPLOAD_KEY_ATTRIBUTE, uploadKey);
        attributes.put("fileIndex", Integer.toString(FILE_INDEX));
        attributes.put("fileType", Integer.toString(LIMELIGHT_XML_FILE_TYPE));
        attributes.put("filename", fileName);
        attributes.put("fileSHA256Hash", sha256);
        attributes.put(KEY_ATTRIBUTE, key);
        attributes.put("submitProgramVersion", Integer.toString(PROGRAM_VERSION));
        if (base64Form) {
            attributes.put("newHeaderParamsFormatVersion", "1");
        }
        return PROLOG + element("submitImport_UploadFile_Request_Common", attributes, "");
    }

    /**
     * The final submit, naming the one file sent.
     *
     * @param projectId the project
     * @param uploadKey the import's tracking id
     * @param fileName the file's name
     * @param key the key
     * @return the request document
     */
    static String finalSubmit(int projectId, String uploadKey, String fileName, String key) {
        Map<String, String> attributes = common(projectId, key);
        attributes.put(UPLOAD_KEY_ATTRIBUTE, uploadKey);
        attributes.put("submitterSameMachine", "false");
        Map<String, String> item = new LinkedHashMap<>();
        item.put("uploadedFilename", fileName);
        item.put("fileType", Integer.toString(LIMELIGHT_XML_FILE_TYPE));
        item.put("fileIndex", Integer.toString(FILE_INDEX));
        item.put("isLimelightXMLFile", "true");
        return PROLOG
                + element(
                        "submitImport_FinalSubmit_Request_PgmXML",
                        attributes,
                        element("fileItems", item, ""));
    }

    private static Map<String, String> common(int projectId, String key) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("submitProgramVersionNumber", Integer.toString(PROGRAM_VERSION));
        attributes.put("projectIdentifier", Integer.toString(projectId));
        attributes.put(KEY_ATTRIBUTE, key);
        return attributes;
    }

    /**
     * One element with its attributes, escaped, and its content.
     *
     * @param name the element
     * @param attributes its attributes, in order
     * @param content its content, already XML, or empty
     * @return the element
     */
    static String element(String name, Map<String, String> attributes, String content) {
        StringBuilder xml = new StringBuilder(128).append('<').append(name);
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            xml.append(' ')
                    .append(attribute.getKey())
                    .append("=\"")
                    .append(escape(attribute.getValue()))
                    .append('"');
        }
        if (content.isEmpty()) {
            return xml.append("/>").toString();
        }
        return xml.append('>').append(content).append("</").append(name).append('>').toString();
    }

    /**
     * An attribute value, escaped so that it reads back as exactly these characters.
     *
     * @param value the value
     * @return the escaped value
     * @throws IllegalArgumentException if it holds a control character other than tab, which no
     *     value this client sends can legitimately hold
     */
    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\t' -> escaped.append("&#9;");
                default -> {
                    if (Character.isISOControl(character)) {
                        throw new IllegalArgumentException(
                                "a value sent to Limelight holds a control character at position "
                                        + index);
                    }
                    escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }

    /**
     * One answer: its root element and the root's attributes.
     *
     * @param root the root element's name
     * @param attributes its attributes, by wire name
     */
    record Answer(String root, Map<String, String> attributes) {

        Answer {
            Objects.requireNonNull(root, "root");
            attributes = Map.copyOf(attributes);
        }

        /**
         * Whether the server said {@code statusSuccess="true"}.
         *
         * @return the verdict
         */
        boolean success() {
            return isTrue(attributes.get(SUCCESS_ATTRIBUTE));
        }

        /**
         * Every attribute the server set to true, except {@code statusSuccess}: its reasons.
         *
         * @return the flags by wire name, sorted, each "true"
         */
        Map<String, String> flags() {
            Map<String, String> flags = new TreeMap<>();
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                if (!SUCCESS_ATTRIBUTE.equals(attribute.getKey()) && isTrue(attribute.getValue())) {
                    flags.put(attribute.getKey(), "true");
                }
            }
            return flags;
        }

        /**
         * The server's own explanation, when it gave one.
         *
         * @return its {@code statusFailErrorMessage}, or empty when absent or blank
         */
        Optional<String> message() {
            return Optional.ofNullable(attributes.get(MESSAGE_ATTRIBUTE))
                    .filter(text -> !text.isBlank());
        }

        /**
         * One attribute.
         *
         * @param name its wire name
         * @return its value, or empty
         */
        Optional<String> attribute(String name) {
            return Optional.ofNullable(attributes.get(name));
        }

        private static boolean isTrue(String value) {
            return "true".equals(value) || "1".equals(value);
        }
    }

    /**
     * Reads an answer, refusing anything that is not one well-formed document with the expected
     * root and no DOCTYPE.
     *
     * @param body the answer's bytes
     * @param expectedRoot the root element the step's answer has
     * @return the answer
     * @throws IOException naming what is wrong, never quoting the body
     */
    static Answer read(byte[] body, String expectedRoot) throws IOException {
        if (body.length > MAX_ANSWER_BYTES) {
            throw new IOException(
                    "the server's answer is larger than " + MAX_ANSWER_BYTES + " bytes");
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        String root = null;
        try {
            XMLStreamReader reader =
                    LimelightXmlJudge.harden(XMLInputFactory.newDefaultFactory())
                            .createXMLStreamReader(new ByteArrayInputStream(body));
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) {
                    throw new IOException(
                            "the server's answer carries a DOCTYPE, so it is not read");
                }
                if (event == XMLStreamConstants.START_ELEMENT && root == null) {
                    root = reader.getLocalName();
                    for (int index = 0; index < reader.getAttributeCount(); index++) {
                        attributes.put(
                                reader.getAttributeLocalName(index),
                                reader.getAttributeValue(index));
                    }
                }
            }
        } catch (XMLStreamException malformed) {
            throw new IOException(
                    "the server's answer is not well-formed XML: " + malformed.getMessage(),
                    malformed);
        }
        if (!expectedRoot.equals(root)) {
            throw new IOException(
                    "the server answered with <" + root + ">, not <" + expectedRoot + ">");
        }
        return new Answer(root, attributes);
    }
}
