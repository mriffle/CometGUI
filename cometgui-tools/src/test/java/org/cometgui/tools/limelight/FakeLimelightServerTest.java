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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.cometgui.tools.limelight.fake.LoopbackGuard;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fake is credible: the <strong>real</strong> importer, {@code limelightSubmitImport.jar}
 * v8.3.1 pinned by SHA-256, uploads the real Limelight XML to it and is accepted, and is refused on
 * a corrupt file -- as unit 1's spike showed against its own throwaway fake. The importer runs in a
 * child JVM through the process service, with the loopback guard's proxy properties on its own
 * command line, so it cannot reach anything but the fake either. Its key is a seeded test value.
 * The JAR is gitignored scratch: missing or changed FAILS this test, it never skips.
 */
class FakeLimelightServerTest {

    /** The importer, as unit 1 fetched it. */
    static final String IMPORTER =
            "scratch/limelight-upload-spike/artefacts/v8.3.1__limelightSubmitImport.jar";

    /** Its SHA-256, equal to the digest GitHub publishes for the release asset. */
    static final String IMPORTER_SHA256 =
            "281d882ab6a2b0e12a0138dc19bfbae680069d569f7e070cf37e7dee7b03217b";

    private static final String KEY = "LLfake-credibility-key-0123456789";

    @Test
    @DisplayName("the fake's wire schema is the spike's committed file, byte for byte")
    void theWireSchemaIsTheSpikes() throws IOException {
        Path committed =
                UpstreamArtefacts.repositoryRoot()
                        .resolve(
                                "scripts/spikes/limelight_upload_spike/submit-import-wire-v16.xsd");

        assertArrayEquals(Files.readAllBytes(committed), FakeLimelightServer.wireSchemaBytes());
        assertEquals(15_841, FakeLimelightServer.wireSchemaBytes().length);
    }

    @Test
    @DisplayName("the real importer uploads the real file to the fake, and is accepted")
    void theRealImporterIsAccepted(@TempDir Path tmp) throws IOException {
        Path file =
                LimelightFixtures.real(
                        LimelightFixtures.LIMELIGHT_XML, LimelightFixtures.LIMELIGHT_XML_SHA256);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, 7)) {
            ToolRunOutcome run = importer(tmp, fake, file);

            assertEquals(0, run.exitCode().orElse(-1), run.joinedOutput());
            assertTrue(run.standardOutput().contains("Submission Successful"), run.joinedOutput());
            FakeLimelightServer.Upload upload = fake.uploads().get(0);
            assertEquals("4100", upload.trackingId());
            assertEquals(LimelightFixtures.LIMELIGHT_XML_SHA256, upload.receivedSha256());
            assertEquals("VALID", upload.verdict());
            assertTrue(upload.submitted());
            assertEquals("import_dir_for_id_0004100", upload.importerSubDir());
            assertEquals(4, fake.calls().size());
            assertEquals(List.of(), fake.wireViolations());
            assertTrue(
                    fake.requests()
                            .get(2)
                            .headers()
                            .containsKey("limelight_upload_file_params_xml_base_64"));
        }
    }

    @Test
    @DisplayName("the real importer is refused on a corrupt file, and never submits")
    void theRealImporterIsRefused(@TempDir Path tmp) throws IOException {
        Path corrupt =
                LimelightFixtures.real(
                        LimelightFixtures.CORRUPT_BOGUS, LimelightFixtures.CORRUPT_BOGUS_SHA256);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, 7)) {
            ToolRunOutcome run = importer(tmp, fake, corrupt);

            assertEquals(6, run.exitCode().orElse(-1), run.joinedOutput());
            assertEquals(3, fake.calls().size());
            assertFalse(fake.uploads().get(0).submitted());
            assertTrue(fake.uploads().get(0).verdict().startsWith("INVALID: ERROR line 3 col 25"));
            assertEquals(List.of(), fake.wireViolations());
        }
    }

    @Test
    @DisplayName(
            "anything but a submit-import POST is answered 404, and nothing is recorded as one")
    void otherRequestsAre404() throws IOException, InterruptedException {
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, 7);
                HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<Void> get =
                    http.send(
                            HttpRequest.newBuilder(URI.create(fake.baseUrl() + "/d/x")).build(),
                            HttpResponse.BodyHandlers.discarding());
            HttpResponse<Void> unknown =
                    http.send(
                            HttpRequest.newBuilder(URI.create(fake.baseUrl() + "/d/rws/unknown"))
                                    .POST(HttpRequest.BodyPublishers.ofString("<x/>"))
                                    .build(),
                            HttpResponse.BodyHandlers.discarding());

            assertEquals(404, get.statusCode());
            assertEquals(404, unknown.statusCode());
            assertEquals(List.of(), fake.uploads());
            assertTrue(fake.baseUrl().startsWith("http://127.0.0.1:"));
        }
    }

    private static ToolRunOutcome importer(Path tmp, FakeLimelightServer fake, Path file)
            throws IOException {
        Path jar = UpstreamArtefacts.repositoryRoot().resolve(IMPORTER);
        if (!Files.isRegularFile(jar)) {
            throw new AssertionError(
                    jar
                            + " is missing: refill it with python3"
                            + " scripts/spikes/limelight_upload_spike.py --only artefacts");
        }
        String actual = UpstreamArtefacts.sha256(jar);
        if (!IMPORTER_SHA256.equals(actual)) {
            throw new AssertionError(
                    jar + " has SHA-256 " + actual + ", pinned " + IMPORTER_SHA256);
        }
        String sinkhole = Integer.toString(LoopbackGuard.install());
        List<String> argv = new ArrayList<>();
        argv.add(
                ProcessHandle.current()
                        .info()
                        .command()
                        .orElse(
                                Path.of(System.getProperty("java.home"), "bin", "java")
                                        .toString()));
        argv.addAll(
                List.of(
                        "-Dhttp.proxyHost=127.0.0.1",
                        "-Dhttp.proxyPort=" + sinkhole,
                        "-Dhttps.proxyHost=127.0.0.1",
                        "-Dhttps.proxyPort=" + sinkhole,
                        "-Dhttp.nonProxyHosts=" + LoopbackGuard.LOOPBACK_ONLY,
                        "-jar",
                        jar.toString(),
                        "--retry-count-limit=0",
                        "--limelight-web-app-url=" + fake.baseUrl(),
                        "--project-id=7",
                        "--limelight-xml-file=" + file,
                        "--no-scan-files",
                        "--no-search-description",
                        "--user-submit-import-key=" + KEY));
        return new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(3))
                .run(new ToolCommand(argv, tmp, Map.of()));
    }
}
