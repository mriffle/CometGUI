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

package org.cometgui.workflow.testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * The smallest HTTP/1.1 server a fake control server needs: one request per connection, answered
 * with a status and a short text body and {@code Connection: close}. Bound to the loopback address
 * only. Written on {@link ServerSocket} because the JDK's own server lives in {@code
 * com.sun.net.httpserver}, which the project's Checkstyle rules forbid importing.
 *
 * <p>A copy of {@code cometgui-tools}' test class of the same name: test classes are not shared
 * between modules.
 */
public final class LoopbackHttp implements AutoCloseable {

    /** One request: its method, its path and its raw (still encoded) query, or null. */
    public record Request(String method, String path, String rawQuery) {}

    /** One response. */
    public record Response(int status, String body) {}

    /** Answers a request. */
    @FunctionalInterface
    public interface Handler {

        /**
         * The response.
         *
         * @param request the request
         * @return the response
         */
        Response handle(Request request);
    }

    private final ServerSocket socket;

    private final Handler handler;

    private final Thread acceptor;

    private volatile boolean stopped;

    private LoopbackHttp(ServerSocket socket, Handler handler) {
        this.socket = socket;
        this.handler = handler;
        this.acceptor = new Thread(this::accept, "loopback-http-" + socket.getLocalPort());
        acceptor.setDaemon(true);
    }

    /**
     * Binds a server on a loopback port and starts serving.
     *
     * @param port the port; 0 for any
     * @param handler answers each request, on a thread of its own
     * @return the running server
     * @throws IOException if the port cannot be bound
     */
    public static LoopbackHttp serve(int port, Handler handler) throws IOException {
        ServerSocket bound = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        LoopbackHttp server = new LoopbackHttp(bound, Objects.requireNonNull(handler, "handler"));
        server.acceptor.start();
        return server;
    }

    /**
     * The port it listens on.
     *
     * @return the port
     */
    public int port() {
        return socket.getLocalPort();
    }

    private void accept() {
        while (!stopped) {
            Socket connection;
            try {
                connection = socket.accept();
            } catch (IOException closed) {
                return;
            }
            Thread worker = new Thread(() -> answer(connection), "loopback-http-request");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void answer(Socket connection) {
        try (Socket open = connection;
                InputStream in = open.getInputStream();
                OutputStream out = open.getOutputStream()) {
            String requestLine = line(in);
            long length = 0;
            for (String header = line(in); !header.isEmpty(); header = line(in)) {
                int colon = header.indexOf(':');
                if (colon > 0
                        && header.substring(0, colon)
                                .trim()
                                .toLowerCase(Locale.ROOT)
                                .equals("content-length")) {
                    length = Long.parseLong(header.substring(colon + 1).trim());
                }
            }
            in.skipNBytes(length);
            String[] parts = requestLine.split(" ");
            String target = parts.length > 1 ? parts[1] : "/";
            int question = target.indexOf('?');
            Request request =
                    new Request(
                            parts[0],
                            question < 0 ? target : target.substring(0, question),
                            question < 0 ? null : target.substring(question + 1));
            Response response = handler.handle(request);
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            String head =
                    "HTTP/1.1 "
                            + response.status()
                            + " X\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "
                            + body.length
                            + "\r\nConnection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        } catch (SocketException reset) {
            // the client went away
        } catch (IOException unreadable) {
            // a request that could not be read is not answered
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int next = in.read(); next >= 0 && next != '\n'; next = in.read()) {
            if (next != '\r') {
                bytes.write(next);
            }
        }
        return bytes.toString(StandardCharsets.US_ASCII);
    }

    /** Stops accepting connections and frees the port. */
    @Override
    public void close() {
        stopped = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
