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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Submitted;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.cometgui.tools.limelight.fake.LoopbackGuard;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code D-007} nightly Limelight-sandbox slot's upload (Phase 14 unit 9): one upload, through
 * the product's own {@link LimelightUploadClient}, of a Limelight XML file to a sandbox the owner
 * names -- run by {@code scripts/ci/nightly-limelight-sandbox.sh} only when one is configured.
 *
 * <h2>Nothing is configured, and nothing has a default</h2>
 *
 * <p>{@code D-007}: every automated upload goes to the local fake; a sandbox run is provisioned for
 * but not configured, because no sandbox has been named and naming one is the owner's. So {@link
 * #theConfiguredSandboxAcceptsTheUpload()} is disabled, with that reason, unless the system
 * property {@value #URL} is set -- and nothing in this repository sets it: there is no default URL,
 * no fallback host, and the property is read from the JVM, never from a file in the tree. Without
 * it the ordinary build reports the test as skipped with the reason, which is the slot "wired and
 * skipping with a stated reason".
 *
 * <h2>What a configured run needs, all of it the owner's</h2>
 *
 * <ul>
 *   <li>{@value #URL}: the sandbox's web application, {@code https://} only. A plain-http URL is
 *       refused here even for loopback, so the slot can never be pointed at the fake by accident
 *       and called a sandbox run.
 *   <li>{@value #PROJECT}: the sandbox project's id.
 *   <li>{@value #KEY_FILE}: a file holding the submit key, read here and admitted to the session's
 *       redactor at once; it is never printed, and a message about it names the property, never the
 *       value.
 *   <li>{@value #XML}: the Limelight XML file to upload. On a CI runner that is a conversion of a
 *       search of {@code D-006}'s CI fixture, whose half of {@code D-006} is open; the local
 *       fixture must not be presented as it.
 * </ul>
 *
 * <h2>The network</h2>
 *
 * <p>This module's {@link LoopbackGuard} sends every non-loopback connection to a sinkhole and
 * fails the test that made it. The configured test lets through exactly the sandbox's host, for the
 * one upload, and puts the guard back afterwards -- as the Tier A scenario does for the tools'
 * GitHub hosts. Any other host still fails the test.
 *
 * <h2>Shown working where it can be</h2>
 *
 * <p>{@link #theSamePathAgainstTheFake()} makes the very same {@link #uploadOnce} call against the
 * {@code D-007} fake, in every ordinary build, so the code the configured run executes is not code
 * that has never run. What only a real sandbox can show -- that a Limelight server accepts the file
 * -- has not been shown, because there is none.
 */
class LimelightSandboxUploadTest {

    /** The sandbox's web application, {@code https://} only. Not set anywhere in this tree. */
    static final String URL = "cometgui.limelight.sandbox.url";

    /** The sandbox project's id. */
    static final String PROJECT = "cometgui.limelight.sandbox.project";

    /** A file holding the submit key. */
    static final String KEY_FILE = "cometgui.limelight.sandbox.keyFile";

    /** The Limelight XML file to upload. */
    static final String XML = "cometgui.limelight.sandbox.xml";

    private static final String FAKE_KEY = "LLcometgui-seeded-key-0123456789ab";

    private static final int FAKE_PROJECT = 7;

    private final SessionSecrets secrets = new SessionSecrets();

    @AfterEach
    void nothingLeftTheMachineUnclaimed() {
        assertEquals(List.of(), LoopbackGuard.takeCaught(), "the sinkhole caught a request");
    }

    /**
     * The sandbox, as configured, or empty when nothing is.
     *
     * @param server the web application
     * @param project the project id
     * @param key the submit key, never printed
     * @param xml the file to upload
     */
    record Sandbox(LimelightServer server, int project, String key, Path xml) {

        @Override
        public String toString() {
            return "Sandbox[" + server + ", project " + project + ", " + xml + "]";
        }

        /**
         * Reads the configuration.
         *
         * @param property a property source: {@code System::getProperty} in the configured run
         * @return empty when {@value #URL} is not set
         * @throws AssertionError naming the property that is missing or wrong, never a key
         * @throws IOException if the key file cannot be read
         */
        static Optional<Sandbox> configured(UnaryOperator<String> property) throws IOException {
            String url = property.apply(URL);
            if (url == null || url.isBlank()) {
                return Optional.empty();
            }
            if (!url.strip().startsWith("https://")) {
                throw new AssertionError(
                        URL
                                + " must be an https:// address of a Limelight SANDBOX, not "
                                + url
                                + ": the submit key travels in every request, and the slot is"
                                + " never the local fake (D-007)");
            }
            LimelightServer server = LimelightServer.parse(url);
            String project = required(property, PROJECT);
            Path keyFile = Path.of(required(property, KEY_FILE));
            Path xml = Path.of(required(property, XML)).toAbsolutePath();
            if (!Files.isRegularFile(keyFile)) {
                throw new AssertionError(KEY_FILE + " names no file: " + keyFile);
            }
            if (!Files.isRegularFile(xml)) {
                throw new AssertionError(XML + " names no file: " + xml);
            }
            String key = Files.readString(keyFile, StandardCharsets.UTF_8).strip();
            if (key.isEmpty()) {
                throw new AssertionError(KEY_FILE + " names an empty file: " + keyFile);
            }
            return Optional.of(
                    new Sandbox(server, LimelightServer.parseProjectId(project), key, xml));
        }

        private static String required(UnaryOperator<String> property, String name) {
            String value = property.apply(name);
            if (value == null || value.isBlank()) {
                throw new AssertionError(
                        URL + " is set, so " + name + " must be too: a sandbox run needs it");
            }
            return value.strip();
        }
    }

    /**
     * The one upload both tests make: the product's client, the file's own digest, the key admitted
     * to the session first.
     *
     * @param server the server
     * @param project the project
     * @param key the submit key
     * @param xml the file
     * @return the outcome, required to be a submission
     * @throws IOException if the file cannot be hashed
     */
    private Submitted uploadOnce(LimelightServer server, int project, String key, Path xml)
            throws IOException {
        LimelightUploadOutcome outcome =
                new LimelightUploadClient(
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(120),
                                Duration.ofSeconds(600))
                        .upload(
                                new LimelightUploadRequest(
                                        server, project, xml, UpstreamArtefacts.sha256(xml)),
                                secrets.admit(key),
                                new LimelightUploadListener() {});
        return assertInstanceOf(Submitted.class, outcome, () -> "the upload: " + outcome);
    }

    @Test
    @EnabledIfSystemProperty(
            named = URL,
            matches = ".+",
            disabledReason =
                    "D-007: no Limelight sandbox has been named, and naming one is the project"
                            + " owner's decision. The nightly slot"
                            + " (scripts/ci/nightly-limelight-sandbox.sh) runs this test when"
                            + " COMETGUI_LIMELIGHT_SANDBOX_URL is configured; every other upload"
                            + " test uses the local fake.")
    @DisplayName("D-007's nightly slot: the configured sandbox accepts one upload")
    void theConfiguredSandboxAcceptsTheUpload() throws IOException {
        Sandbox sandbox =
                Sandbox.configured(System::getProperty)
                        .orElseThrow(() -> new AssertionError(URL + " is not set"));
        String host = sandbox.server().webApp().getHost();
        String guarded = System.getProperty("http.nonProxyHosts");
        assertEquals(LoopbackGuard.LOOPBACK_ONLY, guarded, "the guard is in place before");
        System.setProperty("http.nonProxyHosts", guarded + "|" + host);
        try {
            Submitted submitted =
                    uploadOnce(sandbox.server(), sandbox.project(), sandbox.key(), sandbox.xml());
            assertFalse(submitted.uploadKey().isEmpty(), "the sandbox's tracking id");
            assertEquals(LimelightUploadStep.FINAL_SUBMIT, submitted.step());
            assertEquals(Files.size(sandbox.xml()), submitted.bytesSent(), "every byte sent");
        } finally {
            System.setProperty("http.nonProxyHosts", guarded);
        }
    }

    @Test
    @DisplayName("the slot's own upload call, against the D-007 fake, in every build")
    void theSamePathAgainstTheFake() throws IOException {
        Path file =
                LimelightFixtures.real(
                        LimelightFixtures.LIMELIGHT_XML, LimelightFixtures.LIMELIGHT_XML_SHA256);
        try (FakeLimelightServer fake = FakeLimelightServer.start(FAKE_KEY, FAKE_PROJECT)) {
            Submitted submitted =
                    uploadOnce(LimelightServer.parse(fake.baseUrl()), FAKE_PROJECT, FAKE_KEY, file);
            FakeLimelightServer.Upload received = fake.uploads().get(0);
            assertEquals(received.trackingId(), submitted.uploadKey());
            assertEquals(LimelightFixtures.LIMELIGHT_XML_SHA256, received.receivedSha256());
            assertEquals("VALID", received.verdict());
            assertTrue(received.submitted(), "the final submit reached the fake");
            assertEquals(List.of(), fake.wireViolations());
        }
    }

    @Test
    @DisplayName("nothing is configured by default: no URL, no sandbox")
    void nothingIsConfiguredByDefault() throws IOException {
        assertEquals(Optional.empty(), Sandbox.configured(name -> null));
        assertEquals(Optional.empty(), Sandbox.configured(name -> "  "));
    }

    @Test
    @DisplayName("a sandbox is https: plain http is refused, the local fake's address included")
    void aSandboxIsHttps() {
        for (String url :
                List.of(
                        "http://127.0.0.1:8080/limelight",
                        "http://sandbox.example.org/limelight")) {
            AssertionError refused =
                    assertThrows(
                            AssertionError.class,
                            () -> Sandbox.configured(Map.of(URL, url)::get),
                            url);
            assertTrue(refused.getMessage().startsWith(URL + " must be an https://"), url);
        }
    }

    @Test
    @DisplayName("a configured URL without its project, key file or XML is refused by name")
    void anIncompleteSandboxIsNamed(@TempDir Path directory) throws IOException {
        String url = "https://sandbox.example.org/limelight";
        Path key = Files.writeString(directory.resolve("key"), "SECRET-0123456789\n");
        Path xml = Files.writeString(directory.resolve("limelight.xml"), "<x/>");
        AssertionError noProject =
                assertThrows(AssertionError.class, () -> Sandbox.configured(Map.of(URL, url)::get));
        assertEquals(
                URL + " is set, so " + PROJECT + " must be too: a sandbox run needs it",
                noProject.getMessage());
        AssertionError noKey =
                assertThrows(
                        AssertionError.class,
                        () -> Sandbox.configured(Map.of(URL, url, PROJECT, "3")::get));
        assertTrue(noKey.getMessage().contains(KEY_FILE), noKey.getMessage());
        Sandbox sandbox =
                Sandbox.configured(
                                Map.of(
                                                URL,
                                                url,
                                                PROJECT,
                                                "3",
                                                KEY_FILE,
                                                key.toString(),
                                                XML,
                                                xml.toString())
                                        ::get)
                        .orElseThrow();
        assertEquals("SECRET-0123456789", sandbox.key());
        assertFalse(sandbox.toString().contains("SECRET"), sandbox.toString());
        assertEquals(3, sandbox.project());
    }
}
