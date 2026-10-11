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

/**
 * The four calls of an upload, in the order they are made (phase 12, design decision P12-14; the
 * paths are {@code limelight-core} v8.3.1's, {@code CallSubmitImportWebservice.java:101-105}).
 *
 * <p>The protocol's fifth call, the FASTA check, is never made: CometGUI sends only the Limelight
 * XML.
 */
public enum LimelightUploadStep {

    /** Checks the key and the project; the server answers with its program version. */
    AUTH_TEST(
            "auth-test",
            "/d/rws/for-page/project-upload-data-upload-submit-pgm-auth-test",
            "submitImport_AuthTest_Response_PgmXML"),

    /** Creates the import; the server answers with its tracking id, {@code uploadKey}. */
    INITIALIZE(
            "initialize",
            "/d/rws/for-page/project-upload-data-upload-initialize-from-submit-pgm",
            "submitImport_Init_Response_PgmXML"),

    /** Sends the Limelight XML as one request body, its parameters in a header. */
    UPLOAD_FILE(
            "upload-file",
            "/d/rws/for-page/project-upload-data-upload-file-from-submit-pgm",
            "submitImport_UploadFile_Response_PgmXML"),

    /** Queues the import; the server answers with {@code importerSubDir}. */
    FINAL_SUBMIT(
            "final-submit",
            "/d/rws/for-page/project-upload-data-upload-submit-from-submit-pgm",
            "submitImport_FinalSubmit_Response_PgmXML");

    private final String id;
    private final String path;
    private final String responseRoot;

    LimelightUploadStep(String id, String path, String responseRoot) {
        this.id = id;
        this.path = path;
        this.responseRoot = responseRoot;
    }

    /**
     * The step's name in logs and events.
     *
     * @return a lower-case hyphenated name
     */
    public String id() {
        return id;
    }

    /**
     * The call's path under the web application.
     *
     * @return the path, starting with {@code /}
     */
    public String path() {
        return path;
    }

    /**
     * The root element the server's answer must have.
     *
     * @return the element name as JAXB writes it
     */
    String responseRoot() {
        return responseRoot;
    }
}
