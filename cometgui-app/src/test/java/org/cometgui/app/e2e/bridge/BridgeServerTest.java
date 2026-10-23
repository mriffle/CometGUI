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

package org.cometgui.app.e2e.bridge;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Request;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Response;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Status;
import org.cometgui.app.uidriver.RemotableUiDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The server over a recording stand-in for the application: the port file, the token, the dispatch
 * to {@link RemotableUiDriver} methods and nothing else, and how a remote failure is reported. The
 * real application, the real driver and the real dialogs are {@code BridgeEndToEndTest}'s.
 */
class BridgeServerTest {

    @TempDir private Path scratch;

    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

    private boolean attached;

    private BridgeServer server;

    private Map<String, String> portFile;

    @BeforeEach
    void start() throws IOException {
        BridgeDialogs.reset();
        RemotableUiDriver recording =
                (RemotableUiDriver)
                        Proxy.newProxyInstance(
                                RemotableUiDriver.class.getClassLoader(),
                                new Class<?>[] {RemotableUiDriver.class},
                                (proxy, method, arguments) -> {
                                    List<Object> given =
                                            arguments == null
                                                    ? List.of()
                                                    : Arrays.asList(arguments);
                                    calls.add(method.getName() + given);
                                    return switch (method.getName()) {
                                        case "textOf" -> "the text of #" + arguments[0];
                                        case "isDisabled" -> Boolean.TRUE;
                                        case "rowsOf" -> List.of("row 1", "row 2");
                                        case "select" ->
                                                throw new AssertionError(
                                                        "#" + arguments[0] + " offers no item");
                                        case "clickOn" -> {
                                            if ("boom".equals(arguments[0])) {
                                                throw new IllegalStateException("boom");
                                            }
                                            yield null;
                                        }
                                        default -> null;
                                    };
                                });
        AttachedApplication application =
                new AttachedApplication() {
                    @Override
                    public String await(Duration bound) {
                        attached = true;
                        return "CometGUI";
                    }

                    @Override
                    public RemotableUiDriver driver() {
                        if (!attached) {
                            throw new IllegalStateException("not attached yet");
                        }
                        return recording;
                    }
                };
        Path file = scratch.resolve("bridge.port");
        server = BridgeServer.open(BridgeArguments.parse("port-file=" + file), application);
        portFile = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            portFile.put(
                    line.substring(0, line.indexOf('=')), line.substring(line.indexOf('=') + 1));
        }
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
        BridgeDialogs.reset();
    }

    @Test
    @DisplayName(
            "the port file holds the port, a 64-hex-digit token and this pid, readable by its"
                    + " owner only, and no temporary file is left beside it")
    void portFile() throws IOException {
        Path file = scratch.resolve("bridge.port");
        try (var listed = Files.list(scratch)) {
            assertEquals(List.of(file), listed.toList());
        }
        assertAll(
                () -> assertEquals(List.of("port", "token", "pid"), List.copyOf(portFile.keySet())),
                () -> assertEquals(Integer.toString(server.port()), portFile.get("port")),
                () -> assertTrue(portFile.get("token").matches("[0-9a-f]{64}")),
                () ->
                        assertEquals(
                                Long.toString(ProcessHandle.current().pid()), portFile.get("pid")),
                () ->
                        assertEquals(
                                "rw-------",
                                PosixFilePermissions.toString(
                                        Files.getPosixFilePermissions(file))));
    }

    @Test
    @DisplayName(
            "a driver method runs on the driver, with its value; before attaching it is refused")
    void dispatch() throws IOException {
        try (Connection connection = connect()) {
            assertEquals(
                    Status.REFUSED, connection.call("textOf", "x").status(), "not attached yet");
            assertEquals("CometGUI", connection.call("bridge.awaitApplication", 5).value());
            assertEquals("the text of #summary", connection.call("textOf", "summary").value());
            assertEquals(true, connection.call("isDisabled", "run-start").value());
            assertEquals(List.of("row 1", "row 2"), connection.call("rowsOf", "t").value());
            Response clicked = connection.call("clickOn", "nav-run");
            assertEquals(Status.OK, clicked.status());
            assertNull(clicked.value());
            assertEquals(Status.OK, connection.call("typeInto", "f", null).status());
        }
        assertEquals(
                List.of(
                        "textOf[summary]",
                        "isDisabled[run-start]",
                        "rowsOf[t]",
                        "clickOn[nav-run]",
                        "typeInto[f, null]"),
                calls);
    }

    @Test
    @DisplayName(
            "a driver's AssertionError comes back as ASSERTION_FAILED with its message, anything"
                    + " else as ERROR naming the exception")
    void failures() throws IOException {
        try (Connection connection = connect()) {
            connection.call("bridge.awaitApplication", 5);
            Response failed = connection.call("select", "ess-decoy_search", "Nope");
            Response threw = connection.call("clickOn", "boom");
            assertAll(
                    () -> assertEquals(Status.ASSERTION_FAILED, failed.status()),
                    () -> assertEquals("#ess-decoy_search offers no item", failed.message()),
                    () -> assertEquals(Status.ERROR, threw.status()),
                    () -> assertEquals("java.lang.IllegalStateException: boom", threw.message()));
        }
    }

    @Test
    @DisplayName(
            "nothing but RemotableUiDriver's methods, with their exact argument types, and the"
                    + " bridge's own operations")
    void nothingElse() throws IOException {
        try (Connection connection = connect()) {
            connection.call("bridge.awaitApplication", 5);
            for (Response refused :
                    List.of(
                            connection.call("getClass"),
                            connection.call("node", "x"),
                            connection.call("onFxThread", "x"),
                            connection.call("textOf", 3),
                            connection.call("textOf"),
                            connection.call("isDisabled", "a", "b"),
                            connection.call("bridge.environment", "extra"),
                            connection.call("bridge.awaitApplication", "soon"))) {
                assertEquals(Status.REFUSED, refused.status(), refused.message());
                assertTrue(refused.message().startsWith("refused: the bridge offers no operation"));
            }
        }
        assertEquals(List.of(), calls, "the driver was never called");
    }

    @Test
    @DisplayName(
            "no token, a wrong token: REFUSED, and the connection is closed; a stranger is dropped")
    void tokens() throws IOException {
        try (Connection connection = connect()) {
            Response refused = connection.exchange(new Request("", "tab", List.of()));
            assertEquals(Status.REFUSED, refused.status());
            assertEquals("refused: the request carries no token", refused.message());
            assertEquals(-1, connection.in.read(), "closed");
        }
        try (Connection connection = connect()) {
            String wrong = "f" + portFile.get("token").substring(1);
            if (wrong.equals(portFile.get("token"))) {
                wrong = "0" + portFile.get("token").substring(1);
            }
            Response refused = connection.exchange(new Request(wrong, "tab", List.of()));
            assertEquals(Status.REFUSED, refused.status());
            assertEquals("refused: the request's token is not this bridge's", refused.message());
            assertEquals(-1, connection.in.read(), "closed");
        }
        try (Socket stranger = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            stranger.setSoTimeout(30_000);
            stranger.getOutputStream()
                    .write("GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            assertEquals(-1, stranger.getInputStream().read(), "dropped without an answer");
        }
        assertEquals(List.of(), calls, "the driver was never called");
    }

    @Test
    @DisplayName(
            "the environment report has the encodings, java.home and pid; the chooser operations"
                    + " queue and report")
    void bridgeOperations() throws IOException {
        try (Connection connection = connect()) {
            @SuppressWarnings("unchecked")
            List<String> environment = (List<String>) connection.call("bridge.environment").value();
            assertTrue(
                    environment.contains(
                            "sun.jnu.encoding=" + System.getProperty("sun.jnu.encoding")));
            assertTrue(environment.contains("java.home=" + System.getProperty("java.home")));
            assertTrue(environment.contains("pid=" + ProcessHandle.current().pid()));
            assertEquals(
                    Status.OK,
                    connection
                            .call("bridge.chooser.answer", "open", "T", List.of("/tmp/a.fasta"))
                            .status());
            Response wrongKind =
                    connection.call("bridge.chooser.answer", "pick", "T", List.of("/tmp/a"));
            assertEquals(Status.ERROR, wrongKind.status());
            assertTrue(wrongKind.message().contains("not \"pick\""), wrongKind.message());
            assertEquals(List.of(), connection.call("bridge.chooser.asked").value());
            assertEquals(1, BridgeDialogs.unused());
        }
    }

    private Connection connect() throws IOException {
        return new Connection(new Socket(InetAddress.getLoopbackAddress(), server.port()));
    }

    /** A raw protocol client, independent of the test side's {@code BridgeClient}. */
    private final class Connection implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            socket.setSoTimeout(30_000);
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(socket.getOutputStream());
            BridgeProtocol.writeMagic(out);
        }

        Response call(String operation, Object... arguments) throws IOException {
            List<Object> given = new ArrayList<>();
            Collections.addAll(given, arguments);
            return exchange(new Request(portFile.get("token"), operation, given));
        }

        Response exchange(Request request) throws IOException {
            BridgeProtocol.writeRequest(out, request);
            return BridgeProtocol.readResponse(in);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
