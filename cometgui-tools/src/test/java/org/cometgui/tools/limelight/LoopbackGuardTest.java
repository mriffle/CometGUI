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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.tools.limelight.LimelightUploadOutcome.Failed;
import org.cometgui.tools.limelight.fake.LoopbackGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The guard (phase 12, P12-11 and P12-16), proved: under it a request to an address that is not
 * this computer lands in the loopback sinkhole and nowhere else. The addresses are {@code
 * 192.0.2.1}, RFC 5737's TEST-NET-1, reserved for documentation and routed nowhere, and a name
 * under {@code .invalid} (RFC 6761), which can never resolve. Neither is a Limelight host.
 */
class LoopbackGuardTest {

    private static final String TEST_NET = "192.0.2.1";

    @Test
    @DisplayName("the guard is installed for this JVM before any test, by registration alone")
    void installedForTheWholeJvm() {
        assertTrue(LoopbackGuard.installed(), "the proxy properties do not point at the sinkhole");
        assertEquals("localhost|127.*|[::1]", System.getProperty("http.nonProxyHosts"));
    }

    @Test
    @DisplayName("plain HTTP to TEST-NET: the sinkhole has the request, and it was refused")
    void plainHttpLandsInTheSinkhole() throws IOException, InterruptedException {
        try (HttpClient http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpResponse<String> answer =
                    http.send(
                            HttpRequest.newBuilder(
                                            URI.create("http://" + TEST_NET + "/limelight/x"))
                                    .POST(HttpRequest.BodyPublishers.ofString("<x/>"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());

            assertEquals(403, answer.statusCode());
        }
        assertEquals(
                List.of("POST http://" + TEST_NET + "/limelight/x HTTP/1.1"),
                LoopbackGuard.takeCaught());
    }

    @Test
    @DisplayName("HTTPS to TEST-NET: the sinkhole has the CONNECT, and the tunnel failed")
    void httpsLandsInTheSinkhole() {
        try (HttpClient http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertThrows(
                    IOException.class,
                    () ->
                            http.send(
                                    HttpRequest.newBuilder(
                                                    URI.create(
                                                            "https://" + TEST_NET + "/limelight/x"))
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString()));
        }
        assertEquals(List.of("CONNECT " + TEST_NET + ":443 HTTP/1.1"), LoopbackGuard.takeCaught());
    }

    @Test
    @DisplayName("the real upload client, pointed at TEST-NET: caught, failed, nothing left")
    void theRealClientIsCaught(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("x.xml"), "<limelight_input/>");

        Failed failed = upload("https://" + TEST_NET + "/limelight", file);

        assertEquals(LimelightUploadStep.AUTH_TEST, failed.step());
        assertTrue(failed.reason().startsWith("the connection failed: "), failed.reason());
        assertEquals(List.of("CONNECT " + TEST_NET + ":443 HTTP/1.1"), LoopbackGuard.takeCaught());
    }

    @Test
    @DisplayName("the real upload client, pointed at a name: caught before any lookup")
    void aNameIsCaughtUnresolved(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("x.xml"), "<limelight_input/>");

        upload("https://upload.cometgui.invalid/limelight", file);

        assertEquals(
                List.of("CONNECT upload.cometgui.invalid:443 HTTP/1.1"),
                LoopbackGuard.takeCaught());
    }

    @Test
    @DisplayName("this machine's own non-loopback address is caught too, and never left it")
    void theMachinesOwnAddressIsCaught(@TempDir Path tmp) throws IOException {
        String own = ownNonLoopbackAddress();
        Path file = Files.writeString(tmp.resolve("x.xml"), "<limelight_input/>");

        Failed failed = upload("https://" + own + ":9/limelight", file);

        assertEquals(LimelightUploadStep.AUTH_TEST, failed.step());
        assertEquals(List.of("CONNECT " + own + ":9 HTTP/1.1"), LoopbackGuard.takeCaught());
    }

    /**
     * An IPv4 address of one of this machine's own interfaces that is not loopback: outside {@code
     * http.nonProxyHosts}, so the guard must catch it, yet a connection to it -- if the guard were
     * ever bypassed -- stays inside this machine. That makes this the test to run against an
     * injected bypass without sending a packet anywhere.
     */
    private static String ownNonLoopbackAddress() throws IOException {
        for (NetworkInterface face : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!face.isUp() || face.isLoopback()) {
                continue;
            }
            for (InetAddress address : Collections.list(face.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return address.getHostAddress();
                }
            }
        }
        throw new AssertionError("this machine has no non-loopback IPv4 address to test with");
    }

    @Test
    @DisplayName("after each test, anything the sinkhole caught and nobody claimed fails it")
    void anUnclaimedCatchFailsTheTest() throws IOException, InterruptedException {
        try (HttpClient http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            http.send(
                    HttpRequest.newBuilder(URI.create("http://" + TEST_NET + "/unclaimed")).build(),
                    HttpResponse.BodyHandlers.discarding());
        }

        AssertionError failed =
                assertThrows(
                        AssertionError.class, () -> LoopbackGuard.requireNothingCaught("a test"));

        assertEquals(
                "a test tried to reach a host that is not this computer; the loopback sinkhole"
                        + " stopped it: [GET http://"
                        + TEST_NET
                        + "/unclaimed HTTP/1.1]",
                failed.getMessage());
        LoopbackGuard.requireNothingCaught("the same test, once claimed");
    }

    private static Failed upload(String server, Path file) {
        LimelightUploadClient client =
                new LimelightUploadClient(
                        Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10));
        return assertInstanceOf(
                Failed.class,
                client.upload(
                        new LimelightUploadRequest(
                                LimelightServer.parse(server), 7, file, "f".repeat(64)),
                        new SessionSecrets().admit("LLguard-seeded-key-000000"),
                        LimelightUploadListener.NONE));
    }
}
