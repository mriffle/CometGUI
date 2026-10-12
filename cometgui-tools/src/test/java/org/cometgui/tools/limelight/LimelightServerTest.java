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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link LimelightServer}, {@link LimelightUploadRequest} and {@link LimelightUploadStep}. */
class LimelightServerTest {

    @Test
    @DisplayName("scheme and host lower-cased, blanks and trailing slashes gone, path case kept")
    void normalised() {
        LimelightServer server =
                LimelightServer.parse("  HTTPS://Limelight.Example.ORG/LimeLight//  ");

        assertEquals("https://limelight.example.org/LimeLight", server.toString());
        assertEquals(URI.create("https://limelight.example.org/LimeLight"), server.webApp());
        assertEquals(
                URI.create("https://limelight.example.org/LimeLight/d/pg/project/42"),
                server.projectPage(42));
        assertEquals(
                URI.create("https://limelight.example.org/LimeLight/x"), server.endpoint("/x"));
        assertEquals(
                "https://h.example:8443",
                LimelightServer.parse("https://h.example:8443/").toString());
        assertEquals("https://h.example", LimelightServer.parse("https://h.example").toString());
    }

    @Test
    @DisplayName("two spellings of one server are one server")
    void equality() {
        LimelightServer one = LimelightServer.parse("https://ll.example/limelight/");
        LimelightServer two = LimelightServer.parse("HTTPS://LL.example/limelight");

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
        assertNotEquals(one, LimelightServer.parse("https://ll.example/other"));
        assertNotEquals(
                one.hashCode(), LimelightServer.parse("https://ll.example/other").hashCode());
        assertFalse(one.equals("https://ll.example/limelight"));
    }

    @Test
    @DisplayName("http only to this computer")
    void plainHttpOnlyToLoopback() {
        assertEquals(
                "http://127.0.0.1:9/limelight",
                LimelightServer.parse("http://127.0.0.1:9/limelight").toString());
        assertEquals(
                "http://localhost:9/l", LimelightServer.parse("http://LOCALHOST:9/l").toString());
        assertEquals("http://[::1]:9/l", LimelightServer.parse("http://[::1]:9/l").toString());
        assertEquals(
                "http://127.200.3.4/l", LimelightServer.parse("http://127.200.3.4/l").toString());
        assertEquals(
                "This address cannot be used as a Limelight server: plain http would send the"
                        + " submit key across the network unencrypted; use https.",
                refusal("http://limelight.example.org/limelight"));
        assertTrue(refusal("http://128.0.0.1/l").contains("plain http"));
        assertTrue(refusal("http://127.0.0.1.example/l").contains("plain http"));
    }

    @Test
    @DisplayName("each malformed address is refused for its own reason, never echoed")
    void refusals() {
        assertEquals(
                "This address cannot be used as a Limelight server: it is empty.", refusal("   "));
        assertEquals(
                "This address cannot be used as a Limelight server: it is not an absolute URL; it"
                        + " must start with https://.",
                refusal("limelight.example.org/limelight"));
        assertEquals(
                "This address cannot be used as a Limelight server: only https (or http to this"
                        + " computer) can be used.",
                refusal("ftp://limelight.example.org/"));
        String withPassword = refusal("https://scientist:hunter2-pass@limelight.example.org/l");
        assertEquals(
                "This address cannot be used as a Limelight server: it carries a user name or"
                        + " password; Limelight takes its submit key separately, never in the"
                        + " address.",
                withPassword);
        assertFalse(withPassword.contains("hunter2"));
        assertEquals(
                "This address cannot be used as a Limelight server: it names no host.",
                refusal("https:///limelight"));
        assertEquals(
                "This address cannot be used as a Limelight server: it has a query (?...); give the"
                        + " web application's address only.",
                refusal("https://h.example/l?x=1"));
        assertEquals(
                "This address cannot be used as a Limelight server: it has a fragment (#...); give"
                        + " the web application's address only.",
                refusal("https://h.example/l#top"));
        assertTrue(refusal("https://h.example/a b").contains("it is not a URL: "));
        assertTrue(refusal("mailto:someone@example.org").contains("only https"));
    }

    private static String refusal(String text) {
        return assertThrows(IllegalArgumentException.class, () -> LimelightServer.parse(text))
                .getMessage();
    }

    @Test
    @DisplayName("project ids: positive whole numbers that fit an int")
    void projectIds() {
        assertEquals(7, LimelightServer.parseProjectId(" 7 "));
        assertEquals(2147483647, LimelightServer.parseProjectId("2147483647"));
        assertEquals(
                "a Limelight project id is at most 2147483647, not 2147483648",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> LimelightServer.parseProjectId("2147483648"))
                        .getMessage());
        assertEquals(
                "a Limelight project id is a positive whole number, not 0",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> LimelightServer.parseProjectId("0"))
                        .getMessage());
        assertEquals(
                "a Limelight project id is a positive whole number, not \"-3\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> LimelightServer.parseProjectId("-3"))
                        .getMessage());
        assertThrows(IllegalArgumentException.class, () -> LimelightServer.parseProjectId("7a"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LimelightServer.parseProjectId("12345678901"));
        assertThrows(IllegalArgumentException.class, () -> LimelightServer.checkedProjectId(-1));
        assertEquals(1, LimelightServer.checkedProjectId(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> LimelightServer.parse("https://h.example").projectPage(0));
    }

    @Test
    @DisplayName("a request: positive project, absolute file, a real SHA-256 shape")
    void requests() {
        LimelightServer server = LimelightServer.parse("https://h.example/l");
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "run", "limelight-1.xml");
        String sha = "a".repeat(64);
        Path root = java.util.Objects.requireNonNull(file.getRoot(), "root");

        assertEquals(
                "limelight-1.xml", new LimelightUploadRequest(server, 3, file, sha).fileName());
        assertThrows(
                IllegalArgumentException.class,
                () -> new LimelightUploadRequest(server, 0, file, sha));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LimelightUploadRequest(server, 3, Path.of("relative.xml"), sha));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LimelightUploadRequest(server, 3, root, sha));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LimelightUploadRequest(server, 3, file, "A".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LimelightUploadRequest(server, 3, file, "a".repeat(63)));
    }

    @Test
    @DisplayName("the four steps: names and paths as limelight-core v8.3.1 defines them")
    void steps() {
        assertEquals("auth-test", LimelightUploadStep.AUTH_TEST.id());
        assertEquals(
                "/d/rws/for-page/project-upload-data-upload-submit-pgm-auth-test",
                LimelightUploadStep.AUTH_TEST.path());
        assertEquals("initialize", LimelightUploadStep.INITIALIZE.id());
        assertEquals(
                "/d/rws/for-page/project-upload-data-upload-initialize-from-submit-pgm",
                LimelightUploadStep.INITIALIZE.path());
        assertEquals("upload-file", LimelightUploadStep.UPLOAD_FILE.id());
        assertEquals(
                "/d/rws/for-page/project-upload-data-upload-file-from-submit-pgm",
                LimelightUploadStep.UPLOAD_FILE.path());
        assertEquals("final-submit", LimelightUploadStep.FINAL_SUBMIT.id());
        assertEquals(
                "/d/rws/for-page/project-upload-data-upload-submit-from-submit-pgm",
                LimelightUploadStep.FINAL_SUBMIT.path());
        assertEquals(4, LimelightUploadStep.values().length);
    }
}
