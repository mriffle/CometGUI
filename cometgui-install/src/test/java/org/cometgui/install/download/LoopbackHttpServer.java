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

package org.cometgui.install.download;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A one-request-at-a-time HTTP/1.1 server on a loopback socket, for testing the downloader against
 * responses a well-behaved server library will not produce.
 *
 * <p><strong>Why raw sockets rather than {@code com.sun.net.httpserver}.</strong> Checkstyle's
 * {@code IllegalImport} rule forbids importing {@code com.sun}, {@code sun} and {@code
 * jdk.internal} anywhere in this repository, test sources included ({@code
 * includeTestSourceDirectory} is true). Sockets are not a workaround for that rule; they are also
 * the only way to serve some of what the downloader has to survive -- a body that stops short of
 * its declared {@code Content-Length}, a {@code 206} whose {@code Content-Range} total disagrees
 * with what it sends, a server that answers a range request with the whole file.
 *
 * <p>Every response carries {@code Connection: close}, so each request arrives on a fresh
 * connection and the accept loop can handle them one at a time. Requests are recorded in order,
 * with their headers, because several of this unit's claims -- "it sent a {@code Range} header",
 * "the retry did not resume", "it re-requested the original URL" -- are claims about the requests
 * made rather than about the file produced.
 */
public final class LoopbackHttpServer implements AutoCloseable {

    /** How a test answers one request. */
    @FunctionalInterface
    public interface Responder {

        /**
         * Writes one complete HTTP response.
         *
         * @param request what the client asked for
         * @param out the connection, closed by the server afterwards
         * @throws IOException if the connection fails, which is normal when a client cancels
         */
        void respond(Request request, OutputStream out) throws IOException;
    }

    /**
     * One request as it arrived.
     *
     * @param method the HTTP method
     * @param path the request target
     * @param headers the headers, keyed by lower-case name
     */
    public record Request(String method, String path, Map<String, String> headers) {

        /** Takes an immutable copy of the headers. */
        public Request {
            headers = Map.copyOf(headers);
        }

        /**
         * The headers, keyed by lower-case name.
         *
         * @return the headers, immutable
         */
        @Override
        public Map<String, String> headers() {
            return Map.copyOf(headers);
        }

        /**
         * One header, by lower-case name.
         *
         * @param name the header name, lower case
         * @return its value if present
         */
        public Optional<String> header(String name) {
            return Optional.ofNullable(headers.get(name));
        }

        /**
         * The {@code Range} header, which is what proves a resume was attempted.
         *
         * @return the range if one was sent
         */
        public Optional<String> range() {
            return header("range");
        }
    }

    private final ServerSocket socket;
    private final Thread thread;
    private final Responder responder;
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<IOException> errors = Collections.synchronizedList(new ArrayList<>());

    /**
     * Starts a server on a free loopback port.
     *
     * @param responder how each request is answered
     * @throws IOException if the socket cannot be bound
     */
    public LoopbackHttpServer(Responder responder) throws IOException {
        this.responder = responder;
        this.socket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        this.thread = new Thread(this::acceptLoop, "loopback-http-server");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /**
     * A URL on this server, as a loopback address literal.
     *
     * @param path the path, starting with a slash
     * @return the URL
     */
    public URI uri(String path) {
        return URI.create("http://127.0.0.1:" + socket.getLocalPort() + path);
    }

    /**
     * Every request this server has answered, oldest first.
     *
     * @return the requests
     */
    public List<Request> requests() {
        return List.copyOf(requests);
    }

    /**
     * Failures the server hit while writing. A client that cancels mid-transfer produces one, so
     * this is only asserted on in tests where the client is expected to read to the end.
     *
     * @return the failures
     */
    public List<IOException> errors() {
        return List.copyOf(errors);
    }

    @Override
    public void close() throws IOException {
        socket.close();
        try {
            thread.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try (Socket connection = socket.accept()) {
                connection.setTcpNoDelay(true);
                Request request = readRequest(connection.getInputStream());
                if (request == null) {
                    continue;
                }
                requests.add(request);
                responder.respond(request, connection.getOutputStream());
                connection.getOutputStream().flush();
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    errors.add(e);
                }
            }
        }
    }

    private static Request readRequest(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int newlines = 0;
        int b;
        while ((b = in.read()) != -1) {
            head.write(b);
            if (b == '\n') {
                newlines++;
                if (newlines == 2) {
                    break;
                }
            } else if (b != '\r') {
                newlines = 0;
            }
        }
        String text = head.toString(StandardCharsets.ISO_8859_1);
        if (text.isBlank()) {
            return null;
        }
        String[] lines = text.split("\r\n");
        String[] start = lines[0].split(" ");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(
                        lines[i].substring(0, colon).toLowerCase(Locale.ROOT),
                        lines[i].substring(colon + 1).trim());
            }
        }
        return new Request(start[0], start.length > 1 ? start[1] : "", headers);
    }

    // ------------------------------------------------------------------ writing responses --

    /**
     * Writes text as ISO-8859-1, which is what an HTTP status line and headers are.
     *
     * @param out the connection
     * @param text the text
     * @throws IOException if the connection fails
     */
    public static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * Writes a status line, the given headers, {@code Connection: close} and a blank line.
     *
     * @param out the connection
     * @param status the status code
     * @param reason the reason phrase
     * @param headers header lines, without the trailing CRLF
     * @throws IOException if the connection fails
     */
    public static void head(OutputStream out, int status, String reason, String... headers)
            throws IOException {
        StringBuilder text =
                new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason);
        for (String header : headers) {
            text.append("\r\n").append(header);
        }
        write(out, text.append("\r\nConnection: close\r\n\r\n").toString());
    }

    /**
     * A responder that serves the current bytes, answering {@code Range} with {@code 206}.
     *
     * <p>The body is a supplier so that a test can change what upstream publishes between two
     * attempts -- the case {@code If-Range} cannot catch on the real host.
     *
     * @param body the bytes to serve, read afresh on every request
     * @param etag the {@code ETag} to send, the same on every request unless the test changes it
     * @return the responder
     */
    public static Responder honouringRange(Supplier<byte[]> body, Supplier<String> etag) {
        return (request, out) -> {
            byte[] bytes = body.get();
            Optional<Integer> start = rangeStart(request);
            if (start.isPresent()) {
                int from = start.get();
                head(
                        out,
                        206,
                        "Partial Content",
                        "Content-Length: " + (bytes.length - from),
                        "Content-Range: bytes "
                                + from
                                + "-"
                                + (bytes.length - 1)
                                + "/"
                                + bytes.length,
                        "Accept-Ranges: bytes",
                        "ETag: " + etag.get());
                out.write(bytes, from, bytes.length - from);
            } else {
                head(
                        out,
                        200,
                        "OK",
                        "Content-Length: " + bytes.length,
                        "Accept-Ranges: bytes",
                        "ETag: " + etag.get());
                out.write(bytes);
            }
        };
    }

    /**
     * A responder that ignores {@code Range} and always sends the whole body with {@code 200},
     * which is what a server without range support does.
     *
     * @param body the bytes to serve
     * @return the responder
     */
    public static Responder ignoringRange(Supplier<byte[]> body) {
        return (request, out) -> {
            byte[] bytes = body.get();
            head(out, 200, "OK", "Content-Length: " + bytes.length);
            out.write(bytes);
        };
    }

    /**
     * The first byte of a {@code Range: bytes=N-} header, if one was sent.
     *
     * @param request the request
     * @return the offset asked for
     */
    public static Optional<Integer> rangeStart(Request request) {
        return request.range()
                .map(
                        value ->
                                Integer.parseInt(
                                        value.substring(
                                                value.indexOf('=') + 1, value.indexOf('-'))));
    }
}
