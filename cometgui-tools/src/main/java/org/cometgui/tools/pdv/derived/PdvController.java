/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * DERIVED FILE. This file is derived from Noble-Lab/CasanovoGUI and has been
 * modified for CometGUI. Upstream project:
 * <https://github.com/Noble-Lab/CasanovoGUI>, licensed GPL-3.0.
 * Copyright (C) the CasanovoGUI authors.
 *
 * The attribution above is collective because upstream carries no per-file
 * copyright notice: every CasanovoGUI source file begins with its package
 * statement, and `grep -rl Copyright --include=*.java src` in a clone of that
 * repository matches nothing. No notice was dropped in copying.
 *
 * WHICH upstream file, and at WHICH commit, is recorded per file in the
 * documentation comment below, because this header block is fixed and
 * identical in every derived file. config/checkstyle/checkstyle-derived.xml
 * requires that record and fails the build when it is missing.
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

package org.cometgui.tools.pdv.derived;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.pdv.PdvControlAnswer;
import org.cometgui.tools.pdv.PdvSelectOutcome;
import org.cometgui.tools.pdv.PdvWindowShutdown;
import org.cometgui.tools.pdv.PdvWindowState;

/**
 * Drives PDV {@code denovo-gui} windows over their loopback HTTP control servers: one window per
 * mzTab, started on an ephemeral port, {@code /ready} polled until PDV has imported the mzTab,
 * {@code /select?ref=<spectra_ref>} sent -- debounced -- when the scientist selects a PSM, and
 * {@code /shutdown} plus the end of the process when the window is closed ({@code R-PDV-04}).
 *
 * <p>Derived from Noble-Lab/CasanovoGUI src/main/java/org/casanovo/gui/core/PdvController.java at
 * commit 480b3013e7f8fb51a2b8c58681043821e3e7f865, GPL-3.0, modified.
 *
 * <h2>What was kept from upstream</h2>
 *
 * <ul>
 *   <li><b>One instance per mzTab.</b> Upstream's map of the mzTab's absolute path to the PDV
 *       showing it, and its rule that registering a window for an mzTab replaces the earlier one.
 *   <li><b>An ephemeral loopback port</b> reserved by binding a {@link ServerSocket} to port 0 and
 *       handing its number to {@code --port} ({@link #LOOPBACK}).
 *   <li><b>Readiness by polling {@code /ready}</b> until it answers 200, the process dies, or a
 *       five-minute cap passes -- upstream's three conditions and its cap.
 *   <li><b>The debounce.</b> A single-thread scheduler on which a burst of selections collapses to
 *       the last one, after upstream's 60 ms window; the selection is a {@code POST
 *       /select?ref=<spectra_ref>} with the reference URL-encoded as UTF-8, and nothing is sent to
 *       a window that is not live and ready.
 *   <li>The loopback address, the HTTP client and its timeouts: 3 s to connect and 5 s for a
 *       selection in upstream; one {@link Timing#requestTimeout()} here.
 * </ul>
 *
 * <h2>What was changed for CometGUI</h2>
 *
 * <ul>
 *   <li><b>The process is started through the {@link ProcessRunner} the caller gives</b>, never
 *       with {@code ProcessBuilder}. In the product that runner is {@code
 *       org.cometgui.workflow.pdv.PdvLaunches.forRun(..., DENOVO_GUI, ...)}, which records the
 *       {@code pdv.launched} event before each start and then hands the command to the one process
 *       service (design decisions P11-1, P11-3, P11-4). Upstream's {@code launchAndRegister}, which
 *       also downloaded PDV, is not here: nothing of upstream's download, unpacking or version code
 *       is.
 *   <li><b>No failure is swallowed.</b> Upstream logged and dropped every failure "so it can never
 *       disrupt the UI", which also left a UI waiting for a PDV that had died. Here a window that
 *       cannot become ready is reported as {@link PdvWindowState.Phase#FAILED} with an actionable
 *       message -- the exit status and the last lines PDV wrote, or the deadline and PDV's last
 *       answer -- and a window that misses the deadline is stopped (R-PDV-04). Every selection
 *       returns a future of what became of it: PDV's status and body, superseded, not open, or the
 *       request's failure.
 *   <li><b>Polling is scheduled</b> ({@link ScheduledExecutorService#scheduleWithFixedDelay}) with
 *       a deadline, instead of a {@code Thread.sleep} loop on a thread per window.
 *   <li><b>The bind race is handled.</b> Upstream accepted the gap between closing the reserving
 *       socket and PDV binding the port as "tiny". PDV 2.7.0 reports a failed bind on its standard
 *       error ({@value #BIND_FAILURE}...) and keeps running without a control server; the window is
 *       then stopped and launched again on a fresh port, at most {@link Timing#launchAttempts()}
 *       times in all, and fails naming the cause after that. Every attempt goes through the runner,
 *       so every attempt is recorded.
 *   <li><b>A selection made before PDV is ready is kept</b>, the latest one only, and sent when it
 *       is; upstream dropped it.
 *   <li><b>Shutdown ends the process.</b> PDV's {@code /shutdown} stops only its control server and
 *       leaves the window and the JVM running (design decision P11-16), so {@link #shutdown} sends
 *       it and then asks the process service to end the process and its descendants ({@link
 *       RunningProcess#requestCancellation()}), and waits for the exit up to {@link
 *       Timing#shutdownDeadline()}. Upstream had no shutdown at all.
 * </ul>
 *
 * <h2>Threads</h2>
 *
 * <p>Nothing here runs on the caller's thread except {@link #open}, which starts the process and
 * returns, and {@link #shutdown} and {@link #close}, which wait for the exit within their bound.
 * Readiness polls and exit reports run on a small scheduled pool; selections are debounced and sent
 * on one thread of their own, in order. A {@link Listener} is called with each new state while the
 * window's own lock is held, so it sees the states in the order they happened; it must return
 * promptly and must not call back into this controller.
 */
public final class PdvController implements AutoCloseable {

    /** What PDV 2.7.0 writes to its standard error when its control server cannot bind. */
    public static final String BIND_FAILURE = "Could not start PDV control server on port ";

    /** How many of the lines PDV last wrote a failure message quotes. */
    public static final int REMEMBERED_LINES = 12;

    /** Reserves a loopback port for PDV's control server to listen on. */
    @FunctionalInterface
    public interface PortReserver {

        /**
         * A port that was free a moment ago.
         *
         * @return the port, 1 to 65535
         * @throws IOException if none could be reserved
         */
        int reserve() throws IOException;
    }

    /** Told each state a window passes through. */
    @FunctionalInterface
    public interface Listener {

        /**
         * A window's new state.
         *
         * @param state the state
         */
        void changed(PdvWindowState state);
    }

    /**
     * The controller's bounds and intervals.
     *
     * @param pollInterval the pause between two {@code /ready} polls
     * @param readyDeadline how long a window has to answer {@code /ready} with 200 before it is
     *     stopped and reported failed
     * @param debounce how long a selection waits for a later one to replace it
     * @param requestTimeout how long one request to PDV may take, connecting included
     * @param shutdownDeadline how long {@link #shutdown} waits for the process to end
     * @param launchAttempts how many times a window is launched when PDV cannot bind its port
     */
    public record Timing(
            Duration pollInterval,
            Duration readyDeadline,
            Duration debounce,
            Duration requestTimeout,
            Duration shutdownDeadline,
            int launchAttempts) {

        /**
         * The product's: upstream's five-minute readiness cap and 60 ms debounce, four polls a
         * second, five seconds a request, fifteen for an exit (three times the process service's
         * termination grace), and three launches.
         */
        public static final Timing DEFAULT =
                new Timing(
                        Duration.ofMillis(250),
                        Duration.ofMinutes(5),
                        Duration.ofMillis(60),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(15),
                        3);

        /**
         * Requires positive durations and at least one launch.
         *
         * @throws IllegalArgumentException if a duration is not positive or no launch is allowed
         * @throws NullPointerException if a duration is {@code null}
         */
        public Timing {
            positive(pollInterval, "pollInterval");
            positive(readyDeadline, "readyDeadline");
            positive(debounce, "debounce");
            positive(requestTimeout, "requestTimeout");
            positive(shutdownDeadline, "shutdownDeadline");
            if (launchAttempts < 1) {
                throw new IllegalArgumentException(
                        "a window is launched at least once, not " + launchAttempts + " times");
            }
        }

        private static void positive(Duration duration, String name) {
            Objects.requireNonNull(duration, name);
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException(name + " must be positive: " + duration);
            }
        }
    }

    /** Reserves a free port by binding a loopback socket to port 0 and closing it. */
    public static final PortReserver LOOPBACK =
            () -> {
                try (ServerSocket socket =
                        new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                    return socket.getLocalPort();
                }
            };

    private final Timing timing;

    private final PortReserver ports;

    private final HttpClient http;

    /** Readiness polls and exit reports. */
    private final ScheduledExecutorService pollers;

    /** The debounce and every selection, one at a time and in order: upstream's "pdv-select". */
    private final ScheduledExecutorService selects;

    /** mzTab absolute path -> the window showing it. */
    private final Map<Path, Window> windows = new ConcurrentHashMap<>();

    /**
     * Serialises opening and shutting windows, so that two opens of one mzTab cannot interleave.
     */
    private final Object lifecycle = new Object();

    private volatile boolean closed;

    /**
     * A controller reserving its ports on the loopback interface.
     *
     * @param timing the bounds and intervals
     */
    public PdvController(Timing timing) {
        this(timing, LOOPBACK);
    }

    /**
     * A controller reserving its ports through {@code ports}.
     *
     * @param timing the bounds and intervals
     * @param ports reserves each window's port
     */
    public PdvController(Timing timing, PortReserver ports) {
        this.timing = Objects.requireNonNull(timing, "timing");
        this.ports = Objects.requireNonNull(ports, "ports");
        this.http = HttpClient.newBuilder().connectTimeout(timing.requestTimeout()).build();
        this.pollers = Executors.newScheduledThreadPool(2, daemons("cometgui-pdv-ready"));
        this.selects = Executors.newSingleThreadScheduledExecutor(daemons("cometgui-pdv-select"));
    }

    private static ThreadFactory daemons(String name) {
        AtomicInteger next = new AtomicInteger();
        return work -> {
            Thread thread = new Thread(work, name + "-" + next.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Opens a PDV window for an mzTab: shuts down the window already open for it, if any; reserves
     * a port; starts the command for that port through the runner; and begins polling {@code
     * /ready}. Returns once the process has been started.
     *
     * @param mzTab the mzTab the window shows: the window's key
     * @param runner starts the process; in the product, the run's recording PDV runner
     * @param command the command for a port
     * @param listener told each state the window passes through, the first being the {@link
     *     PdvWindowState.Phase#STARTING} returned here
     * @return the window's state when this returns
     * @throws IOException if no port can be reserved or the runner refuses or fails to start PDV;
     *     nothing is then open for the mzTab
     * @throws IllegalStateException if the controller has been closed
     */
    public PdvWindowState open(
            Path mzTab, ProcessRunner runner, IntFunction<ToolCommand> command, Listener listener)
            throws IOException {
        Path key = keyOf(mzTab);
        Objects.requireNonNull(runner, "runner");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(listener, "listener");
        synchronized (lifecycle) {
            if (closed) {
                throw new IllegalStateException("the PDV controller has been closed");
            }
            Window earlier = windows.remove(key);
            if (earlier != null) {
                earlier.shutdown();
            }
            Window window = new Window(key, runner, command, listener, ports.reserve());
            synchronized (window) {
                window.deadline = System.nanoTime() + timing.readyDeadline().toNanos();
                window.launch(window.attempt);
                windows.put(key, window);
                return window.state;
            }
        }
    }

    /**
     * Where the window for an mzTab stands.
     *
     * @param mzTab the mzTab
     * @return its window's state; empty if no window was opened for it, or it was shut down
     */
    public Optional<PdvWindowState> state(Path mzTab) {
        Window window = windows.get(keyOf(mzTab));
        if (window == null) {
            return Optional.empty();
        }
        synchronized (window) {
            return Optional.of(window.state);
        }
    }

    /**
     * Asks the window for an mzTab to select the PSM with this {@code spectra_ref}, debounced: a
     * selection replaced within {@link Timing#debounce()} by a later one for the same window is
     * never sent. A selection made while the window is starting is kept, the latest only, and sent
     * when it is ready.
     *
     * @param mzTab the mzTab whose window is driven
     * @param spectraRef the PSM's {@code spectra_ref}, verbatim
     * @return what becomes of the selection; never completed exceptionally
     */
    public CompletableFuture<PdvSelectOutcome> select(Path mzTab, String spectraRef) {
        Path key = keyOf(mzTab);
        Objects.requireNonNull(spectraRef, "spectraRef");
        Window window = windows.get(key);
        if (window == null) {
            return CompletableFuture.completedFuture(
                    notOpen(spectraRef, "No PDV window is open for " + key + "."));
        }
        return window.select(spectraRef);
    }

    /**
     * Shuts down the window for an mzTab: sends {@code /shutdown}, which stops only PDV's control
     * server, then has the process service end the process and its descendants, and waits for it to
     * exit, at most {@link Timing#shutdownDeadline()}.
     *
     * @param mzTab the mzTab
     * @return how it went; empty if no window was open for it
     */
    public Optional<PdvWindowShutdown> shutdown(Path mzTab) {
        Path key = keyOf(mzTab);
        synchronized (lifecycle) {
            Window window = windows.remove(key);
            return window == null ? Optional.empty() : Optional.of(window.shutdown());
        }
    }

    /**
     * Shuts down every window, as {@link #shutdown} does, then stops the controller's threads.
     * Every later {@link #open} is refused.
     */
    @Override
    public void close() {
        synchronized (lifecycle) {
            closed = true;
            for (Path key : List.copyOf(windows.keySet())) {
                Window window = windows.remove(key);
                if (window != null) {
                    window.shutdown();
                }
            }
        }
        pollers.shutdownNow();
        selects.shutdownNow();
        http.shutdownNow();
    }

    private static Path keyOf(Path mzTab) {
        return Objects.requireNonNull(mzTab, "mzTab").toAbsolutePath().normalize();
    }

    private static PdvSelectOutcome notOpen(String ref, String why) {
        return new PdvSelectOutcome(PdvSelectOutcome.Kind.NOT_OPEN, ref, Optional.empty(), why);
    }

    private PdvControlAnswer request(int port, String path, boolean post)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(timing.requestTimeout());
        HttpRequest request =
                post ? builder.POST(HttpRequest.BodyPublishers.noBody()).build() : builder.build();
        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new PdvControlAnswer(response.statusCode(), response.body());
    }

    private static String seconds(Duration duration) {
        return duration.toMillis() / 1000.0 + " s";
    }

    private static String describe(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    /** A selection not yet sent. */
    private record Pending(String ref, CompletableFuture<PdvSelectOutcome> outcome) {}

    /** One launch of a window's process: its port, its output and its exit. */
    private final class Attempt implements ProcessListener {

        private final Window window;
        private final int port;
        private final Deque<String> lines = new ArrayDeque<>();
        private final CountDownLatch exited = new CountDownLatch(1);
        private volatile boolean bindFailed;
        private volatile int exitCode;
        private RunningProcess process;

        Attempt(Window window, int port) {
            this.window = window;
            this.port = port;
        }

        @Override
        public void onStandardOutput(String line) {
            remember(line);
        }

        @Override
        public void onStandardError(String line) {
            remember(line);
            if (line.startsWith(BIND_FAILURE)) {
                bindFailed = true;
            }
        }

        @Override
        public void onExit(int code) {
            exitCode = code;
            exited.countDown();
            try {
                pollers.execute(() -> window.exited(this));
            } catch (RejectedExecutionException controllerClosed) {
                // the controller is closing and waits for this exit itself
            }
        }

        private void remember(String line) {
            synchronized (lines) {
                lines.addLast(line);
                if (lines.size() > REMEMBERED_LINES) {
                    lines.removeFirst();
                }
            }
        }

        String lastLines() {
            List<String> copy;
            synchronized (lines) {
                copy = new ArrayList<>(lines);
            }
            return copy.isEmpty()
                    ? " PDV wrote nothing."
                    : " The last lines PDV wrote: " + String.join(" | ", copy);
        }
    }

    /** One mzTab's window. Every mutable field is guarded by the window's own lock. */
    private final class Window {

        private final Path mzTab;
        private final ProcessRunner runner;
        private final IntFunction<ToolCommand> command;
        private final Listener listener;

        private PdvWindowState state;
        private Attempt attempt;
        private int launches;
        private long deadline;
        private String lastReadyAnswer = "nothing: its control server was not listening yet";
        private Future<?> polling = CompletableFuture.completedFuture(null);
        private Pending pending;
        private Future<?> debouncing = CompletableFuture.completedFuture(null);
        private boolean closing;

        Window(
                Path mzTab,
                ProcessRunner runner,
                IntFunction<ToolCommand> command,
                Listener listener,
                int firstPort) {
            this.mzTab = mzTab;
            this.runner = runner;
            this.command = command;
            this.listener = listener;
            this.attempt = new Attempt(this, firstPort);
        }

        /** Starts the attempt's process; the caller holds the lock. */
        void launch(Attempt next) throws IOException {
            launches++;
            int port = next.port;
            // an exit reported before this assignment waits for this lock, so it is not missed
            next.process = runner.start(command.apply(port), next);
            attempt = next;
            enter(
                    new PdvWindowState(
                            PdvWindowState.Phase.STARTING,
                            OptionalInt.of(port),
                            "PDV is starting on loopback port "
                                    + port
                                    + " and importing "
                                    + mzTab
                                    + "."));
            polling =
                    pollers.scheduleWithFixedDelay(
                            () -> poll(next),
                            0,
                            timing.pollInterval().toMillis(),
                            TimeUnit.MILLISECONDS);
        }

        private void enter(PdvWindowState next) {
            state = next;
            listener.changed(next);
        }

        /** One readiness poll of one attempt. */
        void poll(Attempt polled) {
            try {
                synchronized (this) {
                    if (!starting(polled) || stillStarting(polled)) {
                        return;
                    }
                }
                PdvControlAnswer answer;
                try {
                    answer = request(polled.port, "/ready", false);
                } catch (IOException notListening) {
                    synchronized (this) {
                        lastReadyAnswer = "no answer (" + describe(notListening) + ")";
                    }
                    return;
                }
                synchronized (this) {
                    if (!starting(polled) || stillStarting(polled)) {
                        return;
                    }
                    if (answer.status() == 200) {
                        ready(polled);
                    } else {
                        lastReadyAnswer = answer.describe();
                    }
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException unexpected) {
                synchronized (this) {
                    if (starting(polled)) {
                        polled.process.requestCancellation();
                        fail(
                                "PDV's readiness could not be checked ("
                                        + describe(unexpected)
                                        + "), so it was stopped."
                                        + polled.lastLines());
                    }
                }
            }
        }

        private boolean starting(Attempt polled) {
            return attempt == polled && !closing && state.phase() == PdvWindowState.Phase.STARTING;
        }

        /**
         * Handles a failed bind and a passed deadline; the caller holds the lock.
         *
         * @return {@code true} if the attempt was ended here and is not to be polled
         */
        private boolean stillStarting(Attempt polled) {
            if (polled.bindFailed) {
                polling.cancel(false);
                polled.process.requestCancellation();
                if (launches < timing.launchAttempts()) {
                    try {
                        launch(new Attempt(this, ports.reserve()));
                    } catch (IOException | RuntimeException refused) {
                        fail(
                                "PDV could not listen on loopback port "
                                        + polled.port
                                        + " and could not be started again on another: "
                                        + describe(refused)
                                        + ".");
                    }
                } else {
                    fail(
                            "PDV could not listen on a loopback port: "
                                    + launches
                                    + " launches, each on a fresh port, failed to bind, the last"
                                    + " on port "
                                    + polled.port
                                    + ". Another program may be taking ports as fast as they are"
                                    + " freed; open the run in PDV again."
                                    + polled.lastLines());
                }
                return true;
            }
            if (System.nanoTime() - deadline > 0) {
                polled.process.requestCancellation();
                fail(
                        "PDV did not become ready within "
                                + seconds(timing.readyDeadline())
                                + ": its control server on port "
                                + polled.port
                                + " last answered /ready with "
                                + lastReadyAnswer
                                + ". It was stopped; open the run in PDV again, and if this"
                                + " recurs the mzTab may be too large to import in that time."
                                + polled.lastLines());
                return true;
            }
            return false;
        }

        private void ready(Attempt polled) {
            polling.cancel(false);
            enter(
                    new PdvWindowState(
                            PdvWindowState.Phase.READY,
                            OptionalInt.of(polled.port),
                            "PDV is ready on loopback port "
                                    + polled.port
                                    + ", showing "
                                    + mzTab
                                    + "."));
            if (pending != null) {
                schedule(Duration.ZERO);
            }
        }

        /** Reports a window that cannot be used; the caller holds the lock. */
        private void fail(String why) {
            cancelTimers();
            enter(new PdvWindowState(PdvWindowState.Phase.FAILED, state.port(), why));
            abandon(why);
        }

        private void cancelTimers() {
            polling.cancel(false);
            debouncing.cancel(false);
        }

        private void abandon(String why) {
            if (pending != null) {
                pending.outcome().complete(notOpen(pending.ref(), why));
                pending = null;
            }
        }

        /** The attempt's process ended. */
        synchronized void exited(Attempt ended) {
            if (attempt != ended || closing || state.phase().isFinal()) {
                return;
            }
            if (state.phase() == PdvWindowState.Phase.STARTING) {
                fail(
                        "PDV exited with status "
                                + ended.exitCode
                                + " before it was ready, so the run cannot be shown."
                                + ended.lastLines());
                return;
            }
            cancelTimers();
            String why =
                    "PDV's window was closed: the process ended with status "
                            + ended.exitCode
                            + ". Open the run in PDV again to show it.";
            enter(new PdvWindowState(PdvWindowState.Phase.CLOSED, state.port(), why));
            abandon(why);
        }

        synchronized CompletableFuture<PdvSelectOutcome> select(String ref) {
            CompletableFuture<PdvSelectOutcome> outcome = new CompletableFuture<>();
            if (closing || state.phase().isFinal()) {
                outcome.complete(notOpen(ref, closing ? "PDV is being closed." : state.message()));
                return outcome;
            }
            if (pending != null) {
                pending.outcome()
                        .complete(
                                new PdvSelectOutcome(
                                        PdvSelectOutcome.Kind.SUPERSEDED,
                                        pending.ref(),
                                        Optional.empty(),
                                        "Not sent: a later selection replaced it within "
                                                + seconds(timing.debounce())
                                                + "."));
            }
            pending = new Pending(ref, outcome);
            if (state.phase() == PdvWindowState.Phase.READY) {
                schedule(timing.debounce());
            }
            return outcome;
        }

        /** (Re)starts the debounce; the caller holds the lock. */
        private void schedule(Duration delay) {
            debouncing.cancel(false);
            try {
                debouncing = selects.schedule(this::send, delay.toMillis(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException controllerClosed) {
                abandon("The PDV controller has been closed.");
            }
        }

        /** Sends the selection still pending, on the selection thread. */
        void send() {
            Pending sending;
            int port;
            synchronized (this) {
                sending = pending;
                pending = null;
                if (sending == null) {
                    return;
                }
                if (closing || state.phase() != PdvWindowState.Phase.READY) {
                    sending.outcome()
                            .complete(
                                    notOpen(
                                            sending.ref(),
                                            closing ? "PDV is being closed." : state.message()));
                    return;
                }
                port = attempt.port;
            }
            PdvSelectOutcome outcome;
            try {
                PdvControlAnswer answer =
                        request(
                                port,
                                "/select?ref="
                                        + URLEncoder.encode(sending.ref(), StandardCharsets.UTF_8),
                                true);
                outcome =
                        new PdvSelectOutcome(
                                PdvSelectOutcome.Kind.ANSWERED,
                                sending.ref(),
                                Optional.of(answer),
                                "PDV answered /select with " + answer.describe() + ".");
            } catch (IOException failed) {
                outcome = failedSelection(sending.ref(), port, describe(failed));
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                outcome = failedSelection(sending.ref(), port, "the request was interrupted");
            }
            sending.outcome().complete(outcome);
        }

        private PdvSelectOutcome failedSelection(String ref, int port, String why) {
            return new PdvSelectOutcome(
                    PdvSelectOutcome.Kind.FAILED,
                    ref,
                    Optional.empty(),
                    "The selection could not be sent to PDV on port " + port + ": " + why + ".");
        }

        /** Ends the window: {@code /shutdown}, then the process, then the wait. */
        PdvWindowShutdown shutdown() {
            Attempt last;
            boolean live;
            synchronized (this) {
                closing = true;
                cancelTimers();
                abandon("PDV is being closed.");
                last = attempt;
                live = !state.phase().isFinal();
            }
            String control;
            if (last.exited.getCount() == 0) {
                control = "not sent: PDV had already exited";
            } else if (!live) {
                control = "not sent: the window had already failed";
            } else {
                control = shutdownControlServer(last.port);
            }
            last.process.requestCancellation();
            boolean ended;
            try {
                ended =
                        last.exited.await(
                                timing.shutdownDeadline().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                ended = false;
            }
            PdvWindowShutdown result =
                    new PdvWindowShutdown(
                            control,
                            ended,
                            ended ? OptionalInt.of(last.exitCode) : OptionalInt.empty());
            synchronized (this) {
                enter(
                        ended
                                ? new PdvWindowState(
                                        PdvWindowState.Phase.CLOSED,
                                        state.port(),
                                        "PDV was closed: /shutdown "
                                                + control
                                                + ", and the process ended with status "
                                                + last.exitCode
                                                + ".")
                                : new PdvWindowState(
                                        PdvWindowState.Phase.FAILED,
                                        state.port(),
                                        "PDV was asked to close (/shutdown "
                                                + control
                                                + ") and its process had not ended "
                                                + seconds(timing.shutdownDeadline())
                                                + " after it was told to stop."));
            }
            return result;
        }

        private String shutdownControlServer(int port) {
            try {
                return "answered " + request(port, "/shutdown", false).describe();
            } catch (IOException failed) {
                return "was not answered (" + describe(failed) + ")";
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return "was interrupted";
            }
        }
    }
}
