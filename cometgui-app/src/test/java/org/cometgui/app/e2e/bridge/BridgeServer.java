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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Request;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Response;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Status;
import org.cometgui.app.uidriver.RemotableUiDriver;

/**
 * The bridge's server: {@code 127.0.0.1} only, an ephemeral port, a per-launch token, and nothing
 * on offer but the {@link RemotableUiDriver} methods and five {@code bridge.} operations.
 *
 * <h2>Listening</h2>
 *
 * <p>It binds the IPv4 loopback address from its four bytes ({@link BridgeArguments#loopback()}),
 * checks after binding that the socket really is on a loopback address, and drops any accepted
 * connection whose peer is not. Port 0 lets the kernel choose a free port, so two launches never
 * collide. The port, a token of 32 random bytes from {@link SecureRandom}, and this JVM's pid are
 * written to the port file -- created owner-read-write only, beside it under a temporary name and
 * then moved into place atomically, so a reader never sees half a file. The token never appears in
 * the environment, on the command line or in any output.
 *
 * <h2>Serving</h2>
 *
 * <p>Each connection gets its own daemon thread, so a test holding one connection open does not
 * block another (the refusal checks open their own). Every request must carry the token, compared
 * in constant time; a request without it, or with another, is answered {@link Status#REFUSED} and
 * the connection is closed. A request is then one of:
 *
 * <ul>
 *   <li>a {@link RemotableUiDriver} method, by name, with arguments of its exact parameter types --
 *       executed by the one driver {@link AttachedApplication#driver()} returns, one call at a
 *       time. An {@code AssertionError} it throws comes back as {@link Status#ASSERTION_FAILED}
 *       with its message, so a remote failure reads exactly as the in-process one would;
 *   <li>{@code bridge.awaitApplication(int seconds)} -- wait for the window, attach the driver,
 *       answer the window's title;
 *   <li>{@code bridge.environment()} -- this JVM's encodings, {@code java.home} and the environment
 *       variables a test asserts on, as {@code name=value} lines (P14-9);
 *   <li>{@code bridge.chooser.answer(String kind, String titleRegex, List<String> paths)}, {@code
 *       bridge.chooser.asked()} and {@code bridge.chooser.transformed()} -- the file dialogs
 *       ({@link BridgeDialogs}, {@link DialogsTransformer}).
 * </ul>
 *
 * <p>Nothing else: no view-model, no port, no workflow, no product class. The bridge adds no UI
 * semantics of its own; what a click or a selection means is {@code RobotFxUiDriver}'s, the same
 * code Tier A runs.
 */
public final class BridgeServer implements AutoCloseable {

    /** The system properties {@code bridge.environment} reports, in order. */
    static final List<String> PROPERTIES =
            List.of(
                    "sun.jnu.encoding",
                    "file.encoding",
                    "native.encoding",
                    "java.home",
                    "java.runtime.version",
                    "sun.java.command",
                    "jpackage.app-path",
                    "java.class.path",
                    "user.home",
                    "user.dir");

    /** The environment variables it reports, in order. */
    static final List<String> VARIABLES =
            List.of(
                    "LC_ALL",
                    "LC_CTYPE",
                    "LANG",
                    "HOME",
                    "XDG_DATA_HOME",
                    "DISPLAY",
                    "PATH",
                    "JAVA_HOME",
                    "JAVA_TOOL_OPTIONS",
                    "JDK_JAVA_OPTIONS");

    private static final int TOKEN_BYTES = 32;

    /** Where every token comes from. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServerSocket listening;

    private final byte[] token;

    private final AttachedApplication application;

    /** One driver call at a time, whichever connection it came from. */
    private final Object driving = new Object();

    private BridgeServer(ServerSocket listening, byte[] token, AttachedApplication application) {
        this.listening = listening;
        this.token = token.clone();
        this.application = application;
    }

    /**
     * Everything the agent does once it has decided this JVM is the packaged launcher: parse its
     * arguments, install the dialog transformer, and start the server.
     *
     * @param arguments the agent's arguments, as {@link BridgeArguments#parse} reads them
     * @param instrumentation the JVM's instrumentation
     */
    static void startInThisJvm(String arguments, Instrumentation instrumentation) {
        BridgeArguments parsed;
        try {
            parsed = BridgeArguments.parse(arguments);
        } catch (IllegalArgumentException refused) {
            System.err.println(BridgeAgent.SAYS + "not started: " + refused.getMessage());
            return;
        }
        instrumentation.addTransformer(new DialogsTransformer(), false);
        start(parsed, new FxApplication());
    }

    /**
     * Binds, writes the port file and starts accepting, on a daemon thread; or, if any of that
     * fails, says why on standard error and returns {@code null} -- a bridge that cannot start must
     * not stop the application from starting.
     *
     * @param arguments where to write the port file
     * @param application what to drive
     * @return the running server, or {@code null} if it could not start
     */
    static BridgeServer start(BridgeArguments arguments, AttachedApplication application) {
        try {
            return open(arguments, application);
        } catch (IOException | RuntimeException failed) {
            System.err.println(BridgeAgent.SAYS + "not started: " + failed);
            return null;
        }
    }

    /**
     * Binds, writes the port file and starts accepting.
     *
     * @param arguments where to write the port file
     * @param application what to drive
     * @return the running server
     * @throws IOException if the socket cannot be bound or the port file written
     */
    static BridgeServer open(BridgeArguments arguments, AttachedApplication application)
            throws IOException {
        Objects.requireNonNull(application, "application");
        ServerSocket socket = new ServerSocket(0, 16, arguments.bind());
        try {
            if (!socket.getInetAddress().isLoopbackAddress()) {
                throw new IOException(
                        "the bridge's socket is bound to "
                                + socket.getInetAddress()
                                + ", which is not loopback");
            }
            byte[] token = new byte[TOKEN_BYTES];
            RANDOM.nextBytes(token);
            BridgeServer server = new BridgeServer(socket, token, application);
            writePortFile(arguments.portFile(), socket.getLocalPort(), server.tokenText());
            Thread accepting = new Thread(server::accept, "cometgui-e2e-bridge-accept");
            accepting.setDaemon(true);
            accepting.start();
            return server;
        } catch (IOException | RuntimeException failed) {
            socket.close();
            throw failed;
        }
    }

    /**
     * The port the server listens on.
     *
     * @return the port
     */
    int port() {
        return listening.getLocalPort();
    }

    /** Stops accepting connections; for the bridge's own tests. */
    @Override
    public void close() throws IOException {
        listening.close();
    }

    private String tokenText() {
        return HexFormat.of().formatHex(token);
    }

    private static void writePortFile(Path file, int port, String token) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        if (directory == null) {
            throw new IOException("the port file " + file + " has no directory to be written in");
        }
        Path partial =
                Files.createTempFile(
                        directory,
                        ".bridge-port-",
                        ".partial",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(
                    partial,
                    "port="
                            + port
                            + "\ntoken="
                            + token
                            + "\npid="
                            + ProcessHandle.current().pid()
                            + "\n",
                    StandardCharsets.UTF_8);
            Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private void accept() {
        while (!listening.isClosed()) {
            Socket client;
            try {
                client = listening.accept();
            } catch (IOException closedOrBroken) {
                return;
            }
            if (!client.getInetAddress().isLoopbackAddress()) {
                closeQuietly(client);
                continue;
            }
            Thread serving = new Thread(() -> serve(client), "cometgui-e2e-bridge-connection");
            serving.setDaemon(true);
            serving.start();
        }
    }

    private void serve(Socket client) {
        try (client;
                DataInputStream in =
                        new DataInputStream(new BufferedInputStream(client.getInputStream()));
                DataOutputStream out =
                        new DataOutputStream(new BufferedOutputStream(client.getOutputStream()))) {
            BridgeProtocol.readMagic(in);
            while (true) {
                Request request;
                try {
                    request = BridgeProtocol.readRequest(in);
                } catch (EOFException closed) {
                    return;
                }
                if (!holdsTheToken(request.token())) {
                    BridgeProtocol.writeResponse(
                            out,
                            Response.failed(
                                    Status.REFUSED,
                                    request.token().isEmpty()
                                            ? "refused: the request carries no token"
                                            : "refused: the request's token is not this"
                                                    + " bridge's"));
                    return;
                }
                BridgeProtocol.writeResponse(out, handle(request));
            }
        } catch (IOException brokenOrNotABridgeClient) {
            // the peer went away, or did not speak the protocol: nothing to answer
        }
    }

    private boolean holdsTheToken(String offered) {
        return MessageDigest.isEqual(
                offered.getBytes(StandardCharsets.US_ASCII),
                tokenText().getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Runs one authenticated request.
     *
     * @param request the request
     * @return its response
     */
    Response handle(Request request) {
        List<Object> arguments = request.arguments();
        try {
            return switch (request.operation()) {
                case "bridge.awaitApplication" -> {
                    int seconds = (Integer) only(request, Integer.class);
                    yield Response.ok(application.await(Duration.ofSeconds(seconds)));
                }
                case "bridge.environment" -> {
                    none(request);
                    yield Response.ok(environment());
                }
                case "bridge.chooser.answer" -> {
                    if (arguments.size() != 3
                            || !(arguments.get(0) instanceof String kind)
                            || !(arguments.get(1) instanceof String title)
                            || !(arguments.get(2) instanceof List<?> paths)) {
                        yield refused(request);
                    }
                    List<String> files = new ArrayList<>();
                    for (Object path : paths) {
                        files.add((String) path);
                    }
                    BridgeDialogs.queue(kind, title, files);
                    yield Response.ok(null);
                }
                case "bridge.chooser.asked" -> {
                    none(request);
                    yield Response.ok(BridgeDialogs.asked());
                }
                case "bridge.chooser.transformed" -> {
                    none(request);
                    yield Response.ok(DialogsTransformer.report());
                }
                default -> drive(request);
            };
        } catch (WrongArguments wrong) {
            return refused(request);
        } catch (AssertionError failed) {
            return Response.failed(Status.ASSERTION_FAILED, failed.getMessage());
        } catch (RuntimeException failed) {
            return Response.failed(Status.ERROR, failed.toString());
        }
    }

    private Response drive(Request request) {
        Method method = remotable(request);
        if (method == null) {
            return refused(request);
        }
        synchronized (driving) {
            RemotableUiDriver driver;
            try {
                driver = application.driver();
            } catch (IllegalStateException notAttached) {
                return Response.failed(Status.REFUSED, notAttached.getMessage());
            }
            try {
                return Response.ok(method.invoke(driver, request.arguments().toArray()));
            } catch (InvocationTargetException thrown) {
                Throwable cause = thrown.getCause();
                if (cause instanceof AssertionError failed) {
                    return Response.failed(Status.ASSERTION_FAILED, failed.getMessage());
                }
                return Response.failed(Status.ERROR, String.valueOf(cause));
            } catch (IllegalAccessException impossible) {
                return Response.failed(Status.ERROR, impossible.toString());
            }
        }
    }

    /**
     * The {@link RemotableUiDriver} method a request names, if its arguments fit that method's
     * parameters exactly.
     *
     * @param request the request
     * @return the method, or {@code null} if there is none
     */
    static Method remotable(Request request) {
        for (Method method : RemotableUiDriver.class.getMethods()) {
            if (!method.getName().equals(request.operation())
                    || method.getParameterCount() != request.arguments().size()) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            boolean fits = true;
            for (int index = 0; index < types.length; index++) {
                fits &= fits(types[index], request.arguments().get(index));
            }
            if (fits) {
                return method;
            }
        }
        return null;
    }

    private static boolean fits(Class<?> type, Object argument) {
        if (type == String.class) {
            return argument == null || argument instanceof String;
        }
        if (type == boolean.class) {
            return argument instanceof Boolean;
        }
        if (type == int.class) {
            return argument instanceof Integer;
        }
        return false;
    }

    /**
     * This JVM's encodings, {@code java.home} and environment, as {@code name=value} lines: the
     * properties first, then {@code pid}, then the variables; a property or variable that is not
     * set is left out.
     *
     * @return the lines
     */
    static List<String> environment() {
        List<String> lines = new ArrayList<>();
        for (String property : PROPERTIES) {
            String value = System.getProperty(property);
            if (value != null) {
                lines.add(property + "=" + value);
            }
        }
        lines.add("pid=" + ProcessHandle.current().pid());
        for (String variable : VARIABLES) {
            String value = System.getenv(variable);
            if (value != null) {
                lines.add(variable + "=" + value);
            }
        }
        return List.copyOf(lines);
    }

    private static Response refused(Request request) {
        return Response.failed(
                Status.REFUSED,
                "refused: the bridge offers no operation "
                        + request.operation()
                        + " taking "
                        + describe(request.arguments()));
    }

    private static String describe(List<Object> arguments) {
        List<String> kinds = new ArrayList<>();
        for (Object argument : arguments) {
            kinds.add(argument == null ? "null" : argument.getClass().getSimpleName());
        }
        return "(" + String.join(", ", kinds) + ")";
    }

    private static Object only(Request request, Class<?> type) {
        if (request.arguments().size() != 1 || !type.isInstance(request.arguments().get(0))) {
            throw new WrongArguments();
        }
        return request.arguments().get(0);
    }

    private static void none(Request request) {
        if (!request.arguments().isEmpty()) {
            throw new WrongArguments();
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // it is being dropped either way
        }
    }

    /** A request's arguments do not fit its operation. */
    private static final class WrongArguments extends RuntimeException {
        private static final long serialVersionUID = 1L;

        WrongArguments() {
            super(null, null, false, false);
        }
    }
}
