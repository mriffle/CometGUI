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

import java.io.IOException;
import java.net.BindException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.pdv.PdvControlAnswer;

/**
 * A stand-in for PDV's {@code denovo-gui}, for the controller's tests: a {@link ProcessRunner}
 * whose every start binds a real loopback HTTP server on the command's {@code --port}, answering
 * {@code /ready}, {@code /select} and {@code /shutdown} the way PDV 2.7.0's {@code
 * PdvControlServer} does ({@code docs/developer/pdv_mztab_spike.rst}, <em>The control API</em>) --
 * and whose "process" lives until it is cancelled or told to die, like PDV, which {@code /shutdown}
 * does not end.
 *
 * <p>Behaviour is set before a start and applies to the windows started after. Every request every
 * window received is recorded, in order.
 *
 * <p>A copy of {@code cometgui-tools}' test class of the same name: test classes are not shared
 * between modules.
 */
public final class FakePdvWindows implements ProcessRunner {

    /** What a fake PDV writes to its standard error when its port is taken, as PDV 2.7.0 does. */
    public static final String BIND_FAILURE = "Could not start PDV control server on port ";

    /** The exit status of a fake PDV ended through {@link RunningProcess#requestCancellation}. */
    public static final int TERMINATED = 143;

    private final List<Window> windows = new CopyOnWriteArrayList<>();

    private final List<String> requests = new CopyOnWriteArrayList<>();

    private volatile int loadingPolls;

    private volatile Set<String> known = Set.of();

    private volatile Function<String, PdvControlAnswer> selectAnswer;

    private volatile String refuseStart;

    private volatile int refuseAt = -1;

    private final AtomicInteger starts = new AtomicInteger();

    /**
     * How many {@code /ready} polls each window answers {@code 503 loading} before {@code 200
     * ready}; {@link Integer#MAX_VALUE} for never.
     *
     * @param polls the count
     * @return this
     */
    public FakePdvWindows loadingFor(int polls) {
        this.loadingPolls = polls;
        return this;
    }

    /**
     * The references a window knows: {@code 200 selected} for these, {@code 404 unknown ref} for
     * any other.
     *
     * @param refs the references
     * @return this
     */
    public FakePdvWindows knowing(String... refs) {
        this.known = Set.of(refs);
        return this;
    }

    /**
     * Answers every {@code /select} with this instead.
     *
     * @param answer the answer for a decoded reference
     * @return this
     */
    public FakePdvWindows answeringSelect(Function<String, PdvControlAnswer> answer) {
        this.selectAnswer = answer;
        return this;
    }

    /**
     * Refuses the next start, as a runner that cannot launch PDV does.
     *
     * @param refusal the message of the {@link IOException} thrown
     * @return this
     */
    public FakePdvWindows refusingNextStart(String refusal) {
        return refusingStart(starts.get(), refusal);
    }

    /**
     * Refuses one start, counted from 0 over every start this runner was asked for.
     *
     * @param index which start
     * @param refusal the message of the {@link IOException} thrown
     * @return this
     */
    public FakePdvWindows refusingStart(int index, String refusal) {
        this.refuseStart = refusal;
        this.refuseAt = index;
        return this;
    }

    @Override
    public RunningProcess start(ToolCommand command, ProcessListener listener) throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(listener, "listener");
        int index = starts.getAndIncrement();
        String refusal = refuseStart;
        if (refusal != null && index == refuseAt) {
            refuseStart = null;
            throw new IOException(refusal);
        }
        int at = command.argv().indexOf("--port");
        if (at < 0) {
            throw new IOException("a denovo-gui command without --port: " + command);
        }
        int port = Integer.parseInt(command.argv().get(at + 1));
        Window window = new Window(command, listener, port, loadingPolls, known, selectAnswer);
        windows.add(window);
        window.bind();
        return window;
    }

    /**
     * Every window started, in order.
     *
     * @return the windows
     */
    public List<Window> windows() {
        return List.copyOf(windows);
    }

    /**
     * Every request every window received, in order: {@code <port> <method> <path>[?<decoded
     * ref>]}.
     *
     * @return the requests
     */
    public List<String> requests() {
        return List.copyOf(requests);
    }

    /**
     * The decoded references of every {@code /select} received, in order.
     *
     * @return the references
     */
    public List<String> selected() {
        List<String> refs = new ArrayList<>();
        for (String request : requests) {
            int at = request.indexOf("/select?");
            if (at >= 0) {
                refs.add(request.substring(at + "/select?".length()));
            }
        }
        return refs;
    }

    /** One fake PDV: its control server and its process. */
    public final class Window implements RunningProcess {

        private final ToolCommand command;
        private final ProcessListener listener;
        private final int port;
        private final AtomicInteger polls = new AtomicInteger();
        private final int loading;
        private final Set<String> refs;
        private final Function<String, PdvControlAnswer> answer;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean ended = new AtomicBoolean();
        private final CountDownLatch exited = new CountDownLatch(1);
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());
        private volatile LoopbackHttp server;
        private volatile int exitCode;

        Window(
                ToolCommand command,
                ProcessListener listener,
                int port,
                int loading,
                Set<String> refs,
                Function<String, PdvControlAnswer> answer) {
            this.command = command;
            this.listener = listener;
            this.port = port;
            this.loading = loading;
            this.refs = refs;
            this.answer = answer;
        }

        void bind() throws IOException {
            try {
                server = LoopbackHttp.serve(port, this::handle);
            } catch (BindException taken) {
                listener.onStandardOutput("PDV pretending to start");
                listener.onStandardError(BIND_FAILURE + port + ": " + taken.getMessage());
                events.add("bind-failed");
                return;
            }
            listener.onStandardOutput("PDV control server listening on 127.0.0.1:" + port);
        }

        private LoopbackHttp.Response handle(LoopbackHttp.Request request) {
            switch (request.path()) {
                case "/ready":
                    record(request, "");
                    boolean isReady = polls.incrementAndGet() > loading;
                    return new LoopbackHttp.Response(
                            isReady ? 200 : 503, isReady ? "ready" : "loading");
                case "/select":
                    return select(request);
                case "/shutdown":
                    record(request, "");
                    events.add("control-server-stopped");
                    LoopbackHttp running = server;
                    new Thread(running::close, "fake-pdv-shutdown").start();
                    return new LoopbackHttp.Response(200, "bye");
                default:
                    record(request, "");
                    return new LoopbackHttp.Response(404, "no such context");
            }
        }

        private LoopbackHttp.Response select(LoopbackHttp.Request request) {
            String query = request.rawQuery();
            String ref = null;
            if (query != null && query.startsWith("ref=")) {
                ref = URLDecoder.decode(query.substring(4), StandardCharsets.UTF_8);
            }
            record(request, ref == null ? "" : "?" + ref);
            if (ref == null || ref.isEmpty()) {
                return new LoopbackHttp.Response(400, "missing ref");
            }
            if (answer != null) {
                PdvControlAnswer given = answer.apply(ref);
                return new LoopbackHttp.Response(given.status(), given.body());
            }
            return refs.contains(ref)
                    ? new LoopbackHttp.Response(200, "selected")
                    : new LoopbackHttp.Response(404, "unknown ref");
        }

        private void record(LoopbackHttp.Request request, String suffix) {
            requests.add(port + " " + request.method() + " " + request.path() + suffix);
            events.add(request.path());
        }

        /**
         * The command this window was started with.
         *
         * @return the command
         */
        public ToolCommand command() {
            return command;
        }

        /**
         * The port its control server was told to listen on.
         *
         * @return the port
         */
        public int port() {
            return port;
        }

        /**
         * What happened to this window, in order: request paths, {@code bind-failed}, {@code
         * control-server-stopped}, {@code cancelled}, {@code died}.
         *
         * @return the events
         */
        public List<String> events() {
            synchronized (events) {
                return List.copyOf(events);
            }
        }

        /**
         * Whether {@link #requestCancellation} was called.
         *
         * @return whether it was
         */
        public boolean cancelled() {
            return cancelled.get();
        }

        /**
         * Ends the "process" by itself, as PDV dying or its window being closed does: the lines are
         * written to standard error, then the exit is reported.
         *
         * @param code the exit status
         * @param standardError the lines
         */
        public void die(int code, String... standardError) {
            for (String line : standardError) {
                listener.onStandardError(line);
            }
            events.add("died");
            end(code);
        }

        /**
         * Waits for the "process" to end.
         *
         * @param bound how long
         * @return whether it ended
         * @throws InterruptedException if interrupted
         */
        public boolean awaitExit(Duration bound) throws InterruptedException {
            return exited.await(bound.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void end(int code) {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            LoopbackHttp running = server;
            if (running != null) {
                running.close();
            }
            exitCode = code;
            exited.countDown();
            new Thread(() -> listener.onExit(code), "fake-pdv-exit").start();
        }

        @Override
        public boolean isAlive() {
            return exited.getCount() > 0;
        }

        @Override
        public int waitForExit() throws InterruptedException {
            exited.await();
            return exitCode;
        }

        @Override
        public void requestCancellation() {
            if (cancelled.compareAndSet(false, true)) {
                events.add("cancelled");
            }
            end(TERMINATED);
        }
    }
}
