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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.pdv.PdvWindowState.Phase;
import org.cometgui.tools.pdv.derived.DenovoGuiCommand;
import org.cometgui.tools.pdv.derived.PdvController;
import org.cometgui.tools.testing.FakePdvWindows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The derived PDV controller against a fake {@code denovo-gui}: real loopback HTTP servers on the
 * ports the controller reserves, answering as PDV 2.7.0's control server does, and "processes" that
 * live until cancelled -- {@code /shutdown} included -- or told to die. Every wait is a future or a
 * latch with a bound; nothing sleeps.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class PdvControllerTest {

    private static final Duration BOUND = Duration.ofSeconds(20);

    private static final String KNOWN = "ms_run[1]:index=7";

    private static final String OTHER = "ms_run[2]:index=0";

    @TempDir private Path work;

    private final FakePdvWindows pdv = new FakePdvWindows();

    private final List<PdvController> controllers = new ArrayList<>();

    private final LinkedBlockingQueue<PdvWindowState> states = new LinkedBlockingQueue<>();

    @AfterEach
    void closeEverything() {
        controllers.forEach(PdvController::close);
        for (FakePdvWindows.Window window : pdv.windows()) {
            window.requestCancellation();
        }
    }

    private static PdvController.Timing timing(Duration deadline, Duration debounce, int attempts) {
        return new PdvController.Timing(
                Duration.ofMillis(20),
                deadline,
                debounce,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                attempts);
    }

    private PdvController controller(PdvController.Timing timing) {
        PdvController controller = new PdvController(timing);
        controllers.add(controller);
        return controller;
    }

    private PdvController controller() {
        return controller(timing(BOUND, Duration.ofMillis(150), 3));
    }

    private IntFunction<ToolCommand> command() {
        Path jar = work.resolve("PDV-2.7.0.jar");
        return new DenovoGuiCommand(
                        new JavaRuntime(work.resolve("bin/java")),
                        jar,
                        mzTab(),
                        List.of(work.resolve("ms_run-1.mgf")),
                        Optional.empty(),
                        false,
                        work.resolve("home"),
                        work,
                        Map.of())
                ::forPort;
    }

    private Path mzTab() {
        return work.resolve("pdv.mztab");
    }

    private PdvWindowState next() throws InterruptedException {
        PdvWindowState state = states.poll(BOUND.toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(state != null, "no state change within " + BOUND);
        return state;
    }

    private PdvWindowState nextIn(Phase phase) throws InterruptedException {
        PdvWindowState state = next();
        assertEquals(phase, state.phase(), state::message);
        return state;
    }

    private PdvWindowState open(PdvController controller) throws IOException {
        return controller.open(mzTab(), pdv, command(), states::add);
    }

    private PdvWindowState openReady(PdvController controller)
            throws IOException, InterruptedException {
        PdvWindowState starting = open(controller);
        assertEquals(Phase.STARTING, starting.phase());
        assertEquals(starting, nextIn(Phase.STARTING));
        return nextIn(Phase.READY);
    }

    private static PdvSelectOutcome await(CompletableFuture<PdvSelectOutcome> outcome)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        return outcome.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static int portOf(FakePdvWindows.Window window) {
        return window.port();
    }

    @Test
    @DisplayName(
            "a window starts on a reserved loopback port, keeps polling /ready through 503 loading,"
                    + " and is ready when /ready answers 200")
    void readinessIsPolled()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.loadingFor(3);
        PdvController controller = controller();

        PdvWindowState starting = open(controller);
        PdvWindowState ready = nextIn(Phase.STARTING);
        PdvWindowState readyNow = nextIn(Phase.READY);

        FakePdvWindows.Window window = pdv.windows().get(0);
        int port = portOf(window);
        assertAll(
                () -> assertEquals(starting, ready),
                () -> assertEquals(OptionalInt.of(port), starting.port()),
                () ->
                        assertEquals(
                                "PDV is starting on loopback port "
                                        + port
                                        + " and importing "
                                        + mzTab()
                                        + ".",
                                starting.message()),
                () ->
                        assertEquals(
                                new PdvWindowState(
                                        Phase.READY,
                                        OptionalInt.of(port),
                                        "PDV is ready on loopback port "
                                                + port
                                                + ", showing "
                                                + mzTab()
                                                + "."),
                                readyNow),
                () ->
                        assertEquals(
                                List.of("/ready", "/ready", "/ready", "/ready"),
                                window.events(),
                                "three 503 loading answers were polled through, then one 200"),
                () -> assertEquals(Optional.of(readyNow), controller.state(mzTab())),
                () -> assertTrue(port > 0 && port <= 65_535),
                () -> assertEquals("--port", window.command().argv().get(9)));
    }

    @Test
    @DisplayName("a known PSM is answered 200 selected, an unknown one 404 unknown ref, verbatim")
    void selectionsAreAnswered()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.knowing(KNOWN);
        PdvController controller = controller();
        openReady(controller);

        PdvSelectOutcome known = await(controller.select(mzTab(), KNOWN));
        PdvSelectOutcome unknown = await(controller.select(mzTab(), OTHER));

        int port = portOf(pdv.windows().get(0));
        assertAll(
                () ->
                        assertEquals(
                                new PdvSelectOutcome(
                                        PdvSelectOutcome.Kind.ANSWERED,
                                        KNOWN,
                                        Optional.of(new PdvControlAnswer(200, "selected")),
                                        "PDV answered /select with 200 selected."),
                                known),
                () ->
                        assertEquals(
                                new PdvSelectOutcome(
                                        PdvSelectOutcome.Kind.ANSWERED,
                                        OTHER,
                                        Optional.of(new PdvControlAnswer(404, "unknown ref")),
                                        "PDV answered /select with 404 unknown ref."),
                                unknown),
                () ->
                        assertEquals(
                                List.of(
                                        port + " POST /select?" + KNOWN,
                                        port + " POST /select?" + OTHER),
                                pdv.requests().subList(1, 3)));
    }

    @Test
    @DisplayName(
            "a burst of selections collapses to the last: one /select is sent, the rest are"
                    + " superseded")
    void aBurstIsDebounced()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.knowing(KNOWN);
        PdvController controller = controller();
        openReady(controller);
        List<CompletableFuture<PdvSelectOutcome>> burst = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            burst.add(controller.select(mzTab(), "ms_run[1]:index=" + index));
        }
        burst.add(controller.select(mzTab(), KNOWN));

        List<PdvSelectOutcome.Kind> kinds = new ArrayList<>();
        for (CompletableFuture<PdvSelectOutcome> outcome : burst) {
            kinds.add(await(outcome).kind());
        }
        PdvSelectOutcome first = await(burst.get(0));
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        PdvSelectOutcome.Kind.SUPERSEDED,
                                        PdvSelectOutcome.Kind.SUPERSEDED,
                                        PdvSelectOutcome.Kind.SUPERSEDED,
                                        PdvSelectOutcome.Kind.SUPERSEDED,
                                        PdvSelectOutcome.Kind.ANSWERED),
                                kinds),
                () -> assertEquals(List.of(KNOWN), pdv.selected()),
                () -> assertEquals("ms_run[1]:index=0", first.spectraRef()),
                () -> assertEquals(Optional.empty(), first.answer()),
                () ->
                        assertEquals(
                                "Not sent: a later selection replaced it within 0.15 s.",
                                first.message()));
    }

    @Test
    @DisplayName(
            "a selection made while PDV is still loading is kept, the latest only, and sent once it"
                    + " is ready")
    void aSelectionWaitsForReadiness()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.loadingFor(5).knowing(KNOWN);
        PdvController controller = controller();
        open(controller);
        CompletableFuture<PdvSelectOutcome> early = controller.select(mzTab(), OTHER);
        CompletableFuture<PdvSelectOutcome> later = controller.select(mzTab(), KNOWN);

        PdvSelectOutcome sent = await(later);
        assertAll(
                () -> assertEquals(PdvSelectOutcome.Kind.SUPERSEDED, await(early).kind()),
                () ->
                        assertEquals(
                                Optional.of(new PdvControlAnswer(200, "selected")), sent.answer()),
                () -> assertEquals(List.of(KNOWN), pdv.selected()),
                () -> assertEquals(Phase.READY, controller.state(mzTab()).orElseThrow().phase()));
    }

    @Test
    @DisplayName("PDV answering /select 503 not ready is reported as its answer, not swallowed")
    void notReadyIsSurfaced()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.answeringSelect(ref -> new PdvControlAnswer(503, "not ready"));
        PdvController controller = controller();
        openReady(controller);

        PdvSelectOutcome outcome = await(controller.select(mzTab(), KNOWN));

        assertEquals(
                new PdvSelectOutcome(
                        PdvSelectOutcome.Kind.ANSWERED,
                        KNOWN,
                        Optional.of(new PdvControlAnswer(503, "not ready")),
                        "PDV answered /select with 503 not ready."),
                outcome);
    }

    @Test
    @DisplayName(
            "PDV dying before it is ready fails the window with its exit status and its last"
                    + " lines, and a waiting selection is told")
    void deathBeforeReadyIsActionable()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.loadingFor(Integer.MAX_VALUE);
        PdvController controller = controller();
        open(controller);
        nextIn(Phase.STARTING);
        CompletableFuture<PdvSelectOutcome> waiting = controller.select(mzTab(), KNOWN);

        pdv.windows()
                .get(0)
                .die(1, "Exception in thread \"main\" java.lang.OutOfMemoryError", "at PDV.main");
        PdvWindowState failed = nextIn(Phase.FAILED);

        int port = portOf(pdv.windows().get(0));
        String expected =
                "PDV exited with status 1 before it was ready, so the run cannot be shown. The"
                        + " last lines PDV wrote: PDV control server listening on 127.0.0.1:"
                        + port
                        + " | Exception in thread \"main\" java.lang.OutOfMemoryError | at"
                        + " PDV.main";
        PdvSelectOutcome told = await(waiting);
        assertAll(
                () -> assertEquals(expected, failed.message()),
                () -> assertEquals(OptionalInt.of(port), failed.port()),
                () -> assertEquals(PdvSelectOutcome.Kind.NOT_OPEN, told.kind()),
                () -> assertEquals(expected, told.message()),
                () -> assertEquals(Optional.of(failed), controller.state(mzTab())),
                () ->
                        assertEquals(
                                new PdvSelectOutcome(
                                        PdvSelectOutcome.Kind.NOT_OPEN,
                                        OTHER,
                                        Optional.empty(),
                                        expected),
                                await(controller.select(mzTab(), OTHER))),
                () -> assertEquals(List.of(), pdv.selected()));
    }

    @Test
    @DisplayName(
            "a PDV that never answers /ready 200 is stopped at the deadline and the window fails"
                    + " naming the deadline and PDV's last answer")
    void theDeadlineIsActionable()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.loadingFor(Integer.MAX_VALUE);
        PdvController controller =
                controller(timing(Duration.ofSeconds(1), Duration.ofMillis(50), 3));
        open(controller);
        nextIn(Phase.STARTING);

        PdvWindowState failed = nextIn(Phase.FAILED);

        FakePdvWindows.Window window = pdv.windows().get(0);
        assertAll(
                () ->
                        assertTrue(
                                failed.message()
                                        .startsWith(
                                                "PDV did not become ready within 1.0 s: its"
                                                        + " control server on port "
                                                        + window.port()
                                                        + " last answered /ready with 503 loading."
                                                        + " It was stopped; open the run in PDV"
                                                        + " again"),
                                failed.message()),
                () -> assertTrue(window.cancelled(), "the process was stopped"),
                () -> assertTrue(window.awaitExit(BOUND)),
                () -> assertFalse(window.isAlive()));
    }

    @Test
    @DisplayName(
            "shutdown sends /shutdown, which leaves PDV running, and then ends the process and"
                    + " waits for it")
    void shutdownEndsTheProcess()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvController controller = controller();
        openReady(controller);
        FakePdvWindows.Window window = pdv.windows().get(0);

        PdvWindowShutdown shutdown = controller.shutdown(mzTab()).orElseThrow();
        PdvWindowState closed = nextIn(Phase.CLOSED);

        assertAll(
                () ->
                        assertEquals(
                                new PdvWindowShutdown(
                                        "answered 200 bye",
                                        true,
                                        OptionalInt.of(FakePdvWindows.TERMINATED)),
                                shutdown),
                () ->
                        assertEquals(
                                List.of(
                                        "/ready",
                                        "/shutdown",
                                        "control-server-stopped",
                                        "cancelled"),
                                window.events(),
                                "/shutdown first, then the process, which /shutdown left alive"),
                () -> assertFalse(window.isAlive()),
                () ->
                        assertEquals(
                                "PDV was closed: /shutdown answered 200 bye, and the process"
                                        + " ended with status 143.",
                                closed.message()),
                () -> assertEquals(Optional.empty(), controller.state(mzTab())),
                () -> assertEquals(Optional.empty(), controller.shutdown(mzTab())),
                () ->
                        assertEquals(
                                PdvSelectOutcome.Kind.NOT_OPEN,
                                await(controller.select(mzTab(), KNOWN)).kind()));
        assertThrows(
                ConnectException.class,
                () -> new Socket(InetAddress.getLoopbackAddress(), window.port()).close(),
                "the control port is closed");
    }

    @Test
    @DisplayName("shutting down a window that is still starting ends its process too")
    void shutdownWhileStarting()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.loadingFor(Integer.MAX_VALUE);
        PdvController controller = controller();
        open(controller);
        nextIn(Phase.STARTING);
        CompletableFuture<PdvSelectOutcome> waiting = controller.select(mzTab(), KNOWN);

        PdvWindowShutdown shutdown = controller.shutdown(mzTab()).orElseThrow();

        assertAll(
                () -> assertTrue(shutdown.exited()),
                () -> assertEquals("answered 200 bye", shutdown.controlServer()),
                () -> assertTrue(pdv.windows().get(0).cancelled()),
                () ->
                        assertEquals(
                                new PdvSelectOutcome(
                                        PdvSelectOutcome.Kind.NOT_OPEN,
                                        KNOWN,
                                        Optional.empty(),
                                        "PDV is being closed."),
                                await(waiting)));
    }

    @Test
    @DisplayName("a window whose PDV died is shut down without /shutdown, and says so")
    void shutdownAfterDeath()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvController controller = controller();
        openReady(controller);
        pdv.windows().get(0).die(0);
        PdvWindowState closed = nextIn(Phase.CLOSED);

        PdvWindowShutdown shutdown = controller.shutdown(mzTab()).orElseThrow();

        assertAll(
                () ->
                        assertEquals(
                                "PDV's window was closed: the process ended with status 0. Open"
                                        + " the run in PDV again to show it.",
                                closed.message()),
                () ->
                        assertEquals(
                                new PdvWindowShutdown(
                                        "not sent: PDV had already exited",
                                        true,
                                        OptionalInt.of(0)),
                                shutdown),
                () -> assertFalse(pdv.windows().get(0).events().contains("/shutdown")));
    }

    @Test
    @DisplayName(
            "when PDV cannot bind the reserved port it is stopped and launched again on a fresh"
                    + " one, every launch through the runner")
    void theBindRaceIsRetried()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            AtomicInteger reservations = new AtomicInteger();
            PdvController controller =
                    new PdvController(
                            timing(BOUND, Duration.ofMillis(50), 3),
                            () ->
                                    reservations.getAndIncrement() == 0
                                            ? taken.getLocalPort()
                                            : PdvController.LOOPBACK.reserve());
            controllers.add(controller);

            open(controller);
            PdvWindowState first = nextIn(Phase.STARTING);
            PdvWindowState second = nextIn(Phase.STARTING);
            PdvWindowState ready = nextIn(Phase.READY);

            List<FakePdvWindows.Window> windows = pdv.windows();
            assertAll(
                    () -> assertEquals(2, windows.size(), "launched twice"),
                    () -> assertEquals(OptionalInt.of(taken.getLocalPort()), first.port()),
                    () ->
                            assertEquals(
                                    List.of("bind-failed", "cancelled"), windows.get(0).events()),
                    () -> assertEquals(OptionalInt.of(windows.get(1).port()), second.port()),
                    () -> assertEquals(second.port(), ready.port()),
                    () -> assertTrue(windows.get(1).port() != taken.getLocalPort()));
        }
    }

    @Test
    @DisplayName("a port that cannot be bound at any of the allowed launches fails the window")
    void theBindRaceGivesUp()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            PdvController controller =
                    new PdvController(timing(BOUND, Duration.ofMillis(50), 2), taken::getLocalPort);
            controllers.add(controller);

            open(controller);
            nextIn(Phase.STARTING);
            nextIn(Phase.STARTING);
            PdvWindowState failed = nextIn(Phase.FAILED);

            assertAll(
                    () ->
                            assertTrue(
                                    failed.message()
                                            .startsWith(
                                                    "PDV could not listen on a loopback port: 2"
                                                            + " launches, each on a fresh port,"
                                                            + " failed to bind, the last on port "
                                                            + taken.getLocalPort()
                                                            + "."),
                                    failed.message()),
                    () ->
                            assertTrue(
                                    failed.message()
                                            .contains(
                                                    FakePdvWindows.BIND_FAILURE
                                                            + taken.getLocalPort()),
                                    failed.message()),
                    () -> assertEquals(2, pdv.windows().size()),
                    () -> assertTrue(pdv.windows().get(1).cancelled()));
        }
    }

    @Test
    @DisplayName("a relaunch the runner refuses fails the window, naming the refusal")
    void aRefusedRelaunchFails()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            PdvController controller =
                    new PdvController(timing(BOUND, Duration.ofMillis(50), 3), taken::getLocalPort);
            controllers.add(controller);
            pdv.refusingStart(1, "the run's event log is locked");
            open(controller);

            nextIn(Phase.STARTING);
            PdvWindowState failed = nextIn(Phase.FAILED);

            assertEquals(
                    "PDV could not listen on loopback port "
                            + taken.getLocalPort()
                            + " and could not be started again on another: IOException: the run's"
                            + " event log is locked.",
                    failed.message());
        }
    }

    @Test
    @DisplayName("a runner that refuses the launch leaves nothing open and says why")
    void aRefusedLaunchOpensNothing()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.refusingNextStart("this runner launches PDV and nothing else");
        PdvController controller = controller();

        IOException refused = assertThrows(IOException.class, () -> open(controller));

        assertAll(
                () ->
                        assertEquals(
                                "this runner launches PDV and nothing else", refused.getMessage()),
                () -> assertEquals(Optional.empty(), controller.state(mzTab())),
                () -> assertTrue(states.isEmpty()),
                () ->
                        assertEquals(
                                new PdvSelectOutcome(
                                        PdvSelectOutcome.Kind.NOT_OPEN,
                                        KNOWN,
                                        Optional.empty(),
                                        "No PDV window is open for " + mzTab() + "."),
                                await(controller.select(mzTab(), KNOWN))));
    }

    @Test
    @DisplayName("opening an mzTab again shuts its earlier window down first: one window per mzTab")
    void oneWindowPerMzTab()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvController controller = controller();
        openReady(controller);
        FakePdvWindows.Window earlier = pdv.windows().get(0);

        PdvWindowState again =
                controller.open(
                        work.resolve(".").resolve("pdv.mztab"), pdv, command(), states::add);

        assertAll(
                () -> assertEquals(Phase.CLOSED, next().phase(), "the earlier window closed"),
                () -> assertEquals(again, next()),
                () -> assertTrue(earlier.cancelled()),
                () -> assertFalse(earlier.isAlive()),
                () -> assertEquals(2, pdv.windows().size()),
                () -> assertEquals(Phase.READY, nextIn(Phase.READY).phase()));
    }

    @Test
    @DisplayName("windows of different mzTabs are independent")
    void windowsAreKeyedOnTheirMzTab()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.knowing(KNOWN);
        PdvController controller = controller();
        openReady(controller);
        Path second = work.resolve("other.mztab");
        List<PdvWindowState> others = Collections.synchronizedList(new ArrayList<>());
        controller.open(second, pdv, command(), others::add);

        controller.shutdown(second).orElseThrow();

        assertAll(
                () -> assertEquals(Phase.READY, controller.state(mzTab()).orElseThrow().phase()),
                () -> assertEquals(Optional.empty(), controller.state(second)),
                () -> assertTrue(pdv.windows().get(0).isAlive()),
                () -> assertEquals(Phase.CLOSED, others.get(others.size() - 1).phase()),
                () ->
                        assertEquals(
                                PdvSelectOutcome.Kind.ANSWERED,
                                await(controller.select(mzTab(), KNOWN)).kind()));
    }

    @Test
    @DisplayName("closing the controller shuts every window down and refuses any later open")
    void closeShutsEverything()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvController controller = controller();
        openReady(controller);
        controller.open(work.resolve("other.mztab"), pdv, command(), state -> {});

        controller.close();

        assertAll(
                () -> assertFalse(pdv.windows().get(0).isAlive()),
                () -> assertFalse(pdv.windows().get(1).isAlive()),
                () -> assertEquals(Optional.empty(), controller.state(mzTab())),
                () ->
                        assertEquals(
                                "the PDV controller has been closed",
                                assertThrows(IllegalStateException.class, () -> open(controller))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a selection with no window open is not sent")
    void noWindowNoSelection()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvController controller = controller();

        PdvSelectOutcome outcome = await(controller.select(mzTab(), KNOWN));

        assertAll(
                () -> assertEquals(PdvSelectOutcome.Kind.NOT_OPEN, outcome.kind()),
                () -> assertEquals(Optional.empty(), controller.shutdown(mzTab())),
                () -> assertEquals(List.of(), pdv.requests()));
    }

    @Test
    @DisplayName("a selection that cannot reach PDV fails, naming the port and the failure")
    void anUnreachableSelectionFails()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvController controller = controller();
        openReady(controller);
        FakePdvWindows.Window window = pdv.windows().get(0);
        java.net.http.HttpClient.newHttpClient()
                .send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:" + window.port() + "/shutdown"))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding());

        PdvSelectOutcome outcome = await(controller.select(mzTab(), KNOWN));

        assertAll(
                () -> assertEquals(PdvSelectOutcome.Kind.FAILED, outcome.kind()),
                () -> assertEquals(Optional.empty(), outcome.answer()),
                () ->
                        assertTrue(
                                outcome.message()
                                        .startsWith(
                                                "The selection could not be sent to PDV on port "
                                                        + window.port()
                                                        + ": "),
                                outcome.message()));
    }

    @Test
    @DisplayName(
            "a selection waiting in the debounce when PDV's window is closed is told, not left"
                    + " waiting")
    void aWaitingSelectionIsToldOfTheClose()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvController controller = controller(timing(BOUND, Duration.ofSeconds(30), 3));
        openReady(controller);
        CompletableFuture<PdvSelectOutcome> waiting = controller.select(mzTab(), KNOWN);

        pdv.windows().get(0).die(0);
        PdvWindowState closed = nextIn(Phase.CLOSED);

        assertEquals(
                new PdvSelectOutcome(
                        PdvSelectOutcome.Kind.NOT_OPEN, KNOWN, Optional.empty(), closed.message()),
                await(waiting));
    }

    @Test
    @DisplayName("a /shutdown nobody answers is reported, and the process is ended all the same")
    void anUnansweredShutdown()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvController controller = controller();
        openReady(controller);
        FakePdvWindows.Window window = pdv.windows().get(0);
        java.net.http.HttpClient.newHttpClient()
                .send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://127.0.0.1:" + window.port() + "/shutdown"))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding());

        PdvWindowShutdown shutdown = controller.shutdown(mzTab()).orElseThrow();

        assertAll(
                () ->
                        assertTrue(
                                shutdown.controlServer().startsWith("was not answered ("),
                                shutdown.controlServer()),
                () -> assertTrue(shutdown.exited()),
                () -> assertTrue(window.cancelled()));
    }

    @Test
    @DisplayName("a failure quotes the last twelve lines PDV wrote, and no more")
    void theLastTwelveLines()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        pdv.loadingFor(Integer.MAX_VALUE);
        PdvController controller = controller();
        open(controller);
        nextIn(Phase.STARTING);
        String[] lines = new String[15];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = "line " + index;
        }

        pdv.windows().get(0).die(1, lines);
        PdvWindowState failed = nextIn(Phase.FAILED);

        assertTrue(
                failed.message()
                        .endsWith(
                                " The last lines PDV wrote: line 3 | line 4 | line 5 | line 6 |"
                                        + " line 7 | line 8 | line 9 | line 10 | line 11 | line"
                                        + " 12 | line 13 | line 14"),
                failed.message());
        assertEquals(12, PdvController.REMEMBERED_LINES);
    }

    @Test
    @DisplayName("the product's timing is upstream's cap and debounce, and nothing is non-positive")
    void timing() {
        PdvController.Timing product = PdvController.Timing.DEFAULT;
        assertAll(
                () -> assertEquals(Duration.ofMinutes(5), product.readyDeadline()),
                () -> assertEquals(Duration.ofMillis(60), product.debounce()),
                () -> assertEquals(Duration.ofMillis(250), product.pollInterval()),
                () -> assertEquals(Duration.ofSeconds(5), product.requestTimeout()),
                () -> assertEquals(Duration.ofSeconds(15), product.shutdownDeadline()),
                () -> assertEquals(3, product.launchAttempts()),
                () -> assertEquals(1, timing(BOUND, Duration.ofMillis(1), 1).launchAttempts()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> timing(Duration.ZERO, Duration.ofMillis(1), 1)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> timing(BOUND, Duration.ofMillis(-1), 1)),
                () ->
                        assertEquals(
                                "a window is launched at least once, not 0 times",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> timing(BOUND, Duration.ofMillis(1), 0))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PdvController.Timing(
                                                Duration.ofMillis(1),
                                                BOUND,
                                                Duration.ofMillis(1),
                                                Duration.ZERO,
                                                BOUND,
                                                1)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PdvController.Timing(
                                                Duration.ofMillis(1),
                                                BOUND,
                                                Duration.ofMillis(1),
                                                BOUND,
                                                Duration.ofMillis(-5),
                                                1)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PdvController.Timing(
                                                Duration.ZERO,
                                                BOUND,
                                                Duration.ofMillis(1),
                                                BOUND,
                                                BOUND,
                                                1)));
    }

    @Test
    @DisplayName("the loopback reserver hands out a port that is free to bind on 127.0.0.1")
    void theLoopbackReserver() throws IOException {
        int port = PdvController.LOOPBACK.reserve();
        try (ServerSocket bound = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            assertEquals(port, bound.getLocalPort());
        }
        assertTrue(port > 0);
    }

    @Test
    @DisplayName("a listener sees the window's states in order, the returned one first")
    void theListenerIsTold()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvController controller = controller();
        PdvWindowState returned = open(controller);
        assertSame(Phase.STARTING, returned.phase());
        assertEquals(returned, next());
        assertEquals(Phase.READY, next().phase());
    }
}
