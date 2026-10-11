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

package org.cometgui.tools.limelight.fake;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * The test guard against a real Limelight server (phase 12, design decisions P12-11 and P12-16): in
 * a test JVM that can upload, no HTTP or HTTPS connection can leave this computer.
 *
 * <p><strong>How.</strong> It starts a <em>sinkhole</em> -- a server on {@code 127.0.0.1} that
 * records the first line of whatever reaches it, answers {@code 403} and hangs up -- and makes it
 * the proxy for the whole JVM: {@code http.proxyHost}/{@code https.proxyHost} point at it, and
 * {@code http.nonProxyHosts} is loopback only. The JDK's default {@link java.net.ProxySelector}
 * reads those properties on every connection, and the upload client is built with exactly that
 * selector. So a request to the loopback fake goes straight to it, and a request to any other host
 * -- by address or by name; with a proxy the name is never even looked up -- lands in the sinkhole
 * instead of leaving the machine: {@code POST http://host/...} for plain HTTP, {@code CONNECT
 * host:443} for HTTPS. The other half of the guard is an architecture rule: no product class may
 * set a proxy property or choose its own proxy, so the product cannot route around this.
 *
 * <p><strong>Installed for the whole test JVM, before any test.</strong> This class is a JUnit
 * Jupiter extension registered in the module's test resources ({@code
 * META-INF/services/org.junit.jupiter.api.extension.Extension}, with automatic registration
 * switched on in {@code junit-platform.properties}); Jupiter constructs it before the first test
 * class runs, and the constructor installs the sinkhole. No POM changes. After every test it fails
 * the test if the sinkhole caught something the test did not claim with {@link #takeCaught()} -- so
 * an attempt to reach a real host is a red test, not a silent 403.
 *
 * <p><strong>Self-contained, for copying.</strong> It depends on nothing but the JDK and JUnit
 * Jupiter's API. A module that uploads in its tests carries a byte-identical copy at the same
 * package path, with the two resource files, and a test that fails if the copy drifts.
 */
public final class LoopbackGuard implements BeforeAllCallback, AfterEachCallback {

    /** {@code http.nonProxyHosts} under the guard: loopback only. */
    public static final String LOOPBACK_ONLY = "localhost|127.*|[::1]";

    /** Installs the guard, once per JVM; Jupiter calls this when it registers the extension. */
    public LoopbackGuard() {
        install();
    }

    /**
     * Installs the sinkhole and points the JVM's proxy properties at it; calling it again changes
     * nothing.
     *
     * @return the sinkhole's port
     */
    public static int install() {
        String port = Integer.toString(Holder.SINKHOLE.port());
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", port);
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", port);
        System.setProperty("http.nonProxyHosts", LOOPBACK_ONLY);
        return Holder.SINKHOLE.port();
    }

    /**
     * Whether the guard is installed in this JVM, as the properties show it.
     *
     * @return {@code true} when every proxy property points at the sinkhole
     */
    public static boolean installed() {
        String port = Integer.toString(Holder.SINKHOLE.port());
        return "127.0.0.1".equals(System.getProperty("http.proxyHost"))
                && port.equals(System.getProperty("http.proxyPort"))
                && "127.0.0.1".equals(System.getProperty("https.proxyHost"))
                && port.equals(System.getProperty("https.proxyPort"))
                && LOOPBACK_ONLY.equals(System.getProperty("http.nonProxyHosts"));
    }

    /**
     * What the sinkhole caught since the last call, and forgets it: for a test that reaches for a
     * non-loopback address on purpose, to prove the guard.
     *
     * @return the first line of each request that reached it, in order
     */
    public static List<String> takeCaught() {
        return Holder.SINKHOLE.take();
    }

    /** The one sinkhole, started the first time anything asks for it. */
    private static final class Holder {

        private static final Sinkhole SINKHOLE = start();

        private static Sinkhole start() {
            try {
                return new Sinkhole();
            } catch (IOException cannotBind) {
                throw new UncheckedIOException("the loopback sinkhole could not start", cannotBind);
            }
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        install();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        requireNothingCaught(context.getDisplayName());
    }

    /**
     * Fails if the sinkhole caught anything since the last time it was asked, and forgets it: what
     * the guard does after every test.
     *
     * @param test the test's name, for the message
     * @throws AssertionError naming every request the sinkhole stopped
     */
    public static void requireNothingCaught(String test) {
        List<String> caught = takeCaught();
        if (!caught.isEmpty()) {
            throw new AssertionError(
                    test
                            + " tried to reach a host that is not this computer; the loopback"
                            + " sinkhole stopped it: "
                            + caught);
        }
    }

    /** The sinkhole: records each request's first line, answers 403, hangs up. */
    private static final class Sinkhole implements Runnable {

        private final ServerSocket socket;
        private final List<String> caught = new ArrayList<>();

        Sinkhole() throws IOException {
            socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            Thread thread = new Thread(this, "loopback-guard-sinkhole");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        synchronized List<String> take() {
            List<String> taken = List.copyOf(caught);
            caught.clear();
            return taken;
        }

        private synchronized void record(String line) {
            caught.add(line);
        }

        @Override
        public void run() {
            while (!socket.isClosed()) {
                try (Socket connection = socket.accept()) {
                    connection.setSoTimeout(10_000);
                    record(firstLine(connection.getInputStream()));
                    OutputStream out = connection.getOutputStream();
                    out.write(
                            ("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close"
                                            + "\r\n\r\n")
                                    .getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } catch (SocketException closed) {
                    if (socket.isClosed()) {
                        return;
                    }
                } catch (IOException ignored) {
                    record("(a connection the sinkhole could not read: " + ignored + ")");
                }
            }
        }

        private static String firstLine(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int next = in.read();
            while (next >= 0 && next != '\n' && line.size() < 8192) {
                if (next != '\r') {
                    line.write(next);
                }
                next = in.read();
            }
            return line.toString(StandardCharsets.ISO_8859_1);
        }
    }
}
