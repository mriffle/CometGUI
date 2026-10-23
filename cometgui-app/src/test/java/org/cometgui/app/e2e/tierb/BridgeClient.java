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

package org.cometgui.app.e2e.tierb;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.cometgui.app.e2e.bridge.BridgeProtocol;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Request;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Response;

/**
 * One connection to a launched application's bridge, on {@code 127.0.0.1}.
 *
 * <p>{@link #call} sends one request with the endpoint's token and turns the answer into what an
 * in-process call would have done: the value; an {@code AssertionError} with the driver's own
 * message when the driver failed an assertion; an {@link IllegalStateException} naming the
 * exception when it threw anything else or the bridge refused. {@link #exchange} is the raw form,
 * for the tests of the bridge's refusals.
 *
 * <p>Calls are serialised: the bridge answers each request before it reads the next.
 */
public final class BridgeClient implements AutoCloseable {

    /** How long one answer may take: a deadlock detector; a click waits for its events. */
    private static final int READ_TIMEOUT_MILLIS = 15 * 60 * 1000;

    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;

    private final BridgeEndpoint endpoint;

    private final Socket socket;

    private final DataInputStream in;

    private final DataOutputStream out;

    /** One request at a time; a lock of this object's own, so that no caller can hold it. */
    private final Object exchanging = new Object();

    private BridgeClient(BridgeEndpoint endpoint, Socket socket) throws IOException {
        this.endpoint = endpoint;
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    /**
     * Connects to a bridge and says the protocol's opening bytes.
     *
     * @param endpoint where it listens
     * @return the connection
     * @throws UncheckedIOException if it cannot connect
     */
    public static BridgeClient connect(BridgeEndpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        Socket socket = new Socket();
        try {
            socket.connect(
                    new InetSocketAddress(
                            InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), endpoint.port()),
                    CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            BridgeClient client = new BridgeClient(endpoint, socket);
            BridgeProtocol.writeMagic(client.out);
            return client;
        } catch (IOException failed) {
            closeQuietly(socket);
            throw new UncheckedIOException("cannot connect to the bridge at " + endpoint, failed);
        }
    }

    /**
     * The endpoint this connection is to.
     *
     * @return the endpoint
     */
    public BridgeEndpoint endpoint() {
        return endpoint;
    }

    /**
     * Runs one operation in the packaged application.
     *
     * @param operation a {@code RemotableUiDriver} method's name, or a {@code bridge.} operation
     * @param arguments its arguments
     * @return what it returned ({@code null} for {@code void})
     * @throws AssertionError if the driver failed an assertion, with its message
     * @throws IllegalStateException if the operation threw anything else, or was refused
     * @throws UncheckedIOException if the connection broke
     */
    public Object call(String operation, Object... arguments) {
        Response response =
                exchange(new Request(endpoint.token(), operation, Arrays.asList(arguments)));
        return switch (response.status()) {
            case OK -> response.value();
            case ASSERTION_FAILED -> throw new AssertionError(response.message());
            case ERROR ->
                    throw new IllegalStateException(
                            "the packaged application threw, running "
                                    + operation
                                    + ": "
                                    + response.message());
            case REFUSED ->
                    throw new IllegalStateException(
                            "the bridge refused " + operation + ": " + response.message());
        };
    }

    /**
     * Runs an operation that answers a string, or {@code null}.
     *
     * @param operation the operation
     * @param arguments its arguments
     * @return the string
     */
    public String text(String operation, Object... arguments) {
        return (String) call(operation, arguments);
    }

    /**
     * Runs an operation that answers a boolean.
     *
     * @param operation the operation
     * @param arguments its arguments
     * @return the boolean
     */
    public boolean flag(String operation, Object... arguments) {
        return (Boolean) call(operation, arguments);
    }

    /**
     * Runs an operation that answers a list of strings.
     *
     * @param operation the operation
     * @param arguments its arguments
     * @return the list
     */
    @SuppressWarnings("unchecked")
    public List<String> lines(String operation, Object... arguments) {
        return (List<String>) call(operation, arguments);
    }

    /**
     * Sends one request as it is -- with whatever token it carries -- and reads the answer.
     *
     * @param request the request
     * @return the answer
     * @throws UncheckedIOException if the connection broke, or the bridge closed it without one
     */
    public Response exchange(Request request) {
        synchronized (exchanging) {
            try {
                BridgeProtocol.writeRequest(out, request);
                return BridgeProtocol.readResponse(in);
            } catch (IOException broken) {
                throw new UncheckedIOException(
                        "the connection to the bridge at " + endpoint + " broke", broken);
            }
        }
    }

    /**
     * Whether the bridge has closed this connection: the next read meets the end of the stream.
     *
     * @return {@code true} if it has
     */
    public boolean closedByTheBridge() {
        synchronized (exchanging) {
            try {
                return in.read() < 0;
            } catch (IOException broken) {
                return true;
            }
        }
    }

    @Override
    public void close() {
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing more can be done with it
        }
    }
}
