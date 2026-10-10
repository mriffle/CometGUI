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

package org.cometgui.app.gui;

import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ProjectSession;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.ProjectXvfb;
import org.cometgui.app.testing.RealPercolators;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.TestJson;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.pdv.PdvWindowShutdown;
import org.cometgui.tools.pdv.derived.PdvController;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.StartedProcess;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.workflow.pdv.PdvInstallation;
import org.cometgui.workflow.pdv.PdvLaunches;
import org.cometgui.workflow.pdv.PdvMzTabs;
import org.cometgui.workflow.pdv.PdvSelection;
import org.cometgui.workflow.pdv.PdvWindowRequest;
import org.cometgui.workflow.pdv.PdvWindowStatus;
import org.cometgui.workflow.pdv.PdvWindows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 11 gate item 5, AC-VIS-04, through the product: a real search's run opened in PDV's live
 * window from the Visualisation section -- PDV installed first, through the Tool Manager, because
 * the cache is empty -- on an ephemeral port, {@code /ready} reached by polling, and the PSM
 * selected in the Results section's table shown there, PDV's {@code /select} answer {@code 200
 * selected} surfaced in the interface; the launch in the run's {@code events.log} before PDV
 * started; the window ended by Close PDV, by the workflow API, and by the application's exit --
 * each time the process gone and the port closed.
 *
 * <p>The live window is the product's, launched through {@code PdvLaunches} by {@code PdvWindows}
 * and the derived controller. The test's process runner only watches each launch and puts PDV's JVM
 * under the host's {@code timeout}, so that a test JVM that dies cannot orphan it. What PDV then
 * <em>drew</em> is proved by {@code PdvControllerRealPdvTest} in {@code cometgui-tools} (design
 * decision P11-16): this test proves the product path and the control API's answers.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project, and the"
                        + " project-local X11 stack PDV draws under is Linux-only")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class PdvLiveWindowUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-10T00:00:00Z"));

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String PDV_ZIP = "v2.7.0__PDV-2.7.0.zip";

    /** The SHA-256 of {@code PDV-2.7.0.jar}, as unit 2 measured and pinned it. */
    private static final String PDV_JAR_SHA256 =
            "fc876310b6f9dd95bd9538f212e28d45ac0226ea1615a9c09f2e904d70d07908";

    /** How long an install, a mzTab and PDV's import may take together. A bound, not a delay. */
    private static final Duration PDV_BOUND = Duration.ofMinutes(6);

    /** How long one selection may take to come back. */
    private static final Duration SELECT_BOUND = Duration.ofSeconds(60);

    /** The longest PDV's JVM may live, whatever happens to the test. */
    private static final Duration PDV_LIFETIME = Duration.ofMinutes(10);

    private static final Pattern READY_PORT =
            Pattern.compile("^PDV is ready on loopback port ([0-9]+), showing run ");

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static final AtomicBoolean STOPPED = new AtomicBoolean();

    private static FxUiDriver driver;

    private static ProjectXvfb display;

    private static LoopbackArtefactServer served;

    private static CappedPdv processes;

    private static Path project;

    private static Path cacheRoot;

    private static Map<String, String> pdvDisplay;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        display = ProjectXvfb.start(root.resolve("xvfb"));
        Path comet = RealSearch.stageComet(root.resolve("bin/comet"));
        ToolOffer percolator = RealPercolators.installed3071(root.resolve("bin/percolator"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealSearch.spectra(inputs);
        Path subset = RealSearch.subset(inputs.resolve("subset.fasta"));
        project = root.resolve("project");
        cacheRoot = root.resolve("cometgui-data");
        ArtefactRecord pdv = ArtefactMirror.record(ToolName.PDV, "2.7.0", LINUX);
        served = new LoopbackArtefactServer().serve(pdv.url(), ArtefactMirror.artefact(PDV_ZIP));
        processes = new CappedPdv(new ProcessService(Clock.systemUTC()));
        ToolManager managed =
                ToolManagerWiring.toolManager(
                        LINUX,
                        DEBIAN_12,
                        cacheRoot,
                        processes,
                        Clock.systemUTC(),
                        ToolManagerWiring.installThreads(),
                        served);
        ToolManager tools =
                new StagedSearchManagedPdv(
                        InstalledComet.at(RealSearch.RELEASE, comet).with(percolator), managed);
        pdvDisplay = display.clientEnvironment();
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        processes,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(() -> tools, project, host -> pdvDisplay));
        driver = new TestFxUiDriver(app.application());

        app.chooser().spectra(spectra.get(0), spectra.get(1));
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        driver.clickOn("ess-spectra-add");
        app.chooser().database(subset);
        driver.clickOn("ess-database_name-choose");
        ParameterEditorApp.choose(
                driver,
                "ess-decoy_search",
                "Concatenated: targets and decoys compete, one result per spectrum");
        enter(driver, "ess-num_threads", "4");
    }

    @AfterAll
    static void stop() throws IOException {
        try {
            if (app != null && !STOPPED.get()) {
                app.stop();
            }
        } finally {
            try {
                if (processes != null) {
                    processes.endEverything();
                }
                if (served != null) {
                    served.close();
                }
            } finally {
                if (display != null) {
                    display.close();
                }
            }
        }
    }

    @Test
    @DisplayName(
            "Open run in PDV installs PDV, starts it on an ephemeral port and polls it ready; each"
                    + " PSM selected in Results is answered 200 selected; the launch is recorded"
                    + " first; Close PDV, the workflow API and the application's exit each end PDV")
    void theLiveWindow()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        // ---- a finished run, two PSMs selected in turn in the Results section ---------------
        Path run = search();
        String runId = idOf(run);
        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, runId, ResultsSection.OPEN_BOUND);
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        ParameterEditorApp.choose(driver, "results-category", "All rows");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        List<String> psmIds = ResultsSection.column(driver, 0);
        assertTrue(psmIds.size() >= 2, () -> "two PSMs on the first page: " + psmIds);
        String first = psmIds.get(0);
        String second = psmIds.get(1);
        ResultsSection.selectRow(driver, 0);

        // ---- Open run in PDV: installed first (empty cache), then opened and polled ready ----
        driver.clickOn("nav-visualisation");
        RunSection.awaitText(
                driver, UiIds.VISUALISATION_OPEN_REFUSAL, String::isEmpty, RunSection.CHECK_BOUND);
        assertEquals(List.of(), processes.windows(), "PDV is not running before it is opened");
        driver.clickOn(UiIds.VISUALISATION_OPEN);
        String ready = awaitReady(runId);
        int port = portOf(ready);
        String shownFirst = awaitSelection(first);
        assertEquals(
                "Target PSM " + first + " is shown in PDV: PDV answered /select with 200 selected.",
                shownFirst);
        Path jar = cacheRoot.resolve("tools/pdv/2.7/linux-x86-64/PDV-2.7.0/PDV-2.7.0.jar");
        assertAll(
                "installed through the Tool Manager on first use",
                () -> assertEquals(PDV_JAR_SHA256, RealSearch.sha256(jar)),
                () -> assertEquals(1, served.requested().size(), "fetched once"));

        // ---- the next selection follows -------------------------------------------------------
        driver.clickOn("nav-results");
        ResultsSection.selectRow(driver, 1);
        driver.clickOn("nav-visualisation");
        assertEquals(
                "Target PSM "
                        + second
                        + " is shown in PDV: PDV answered /select with 200 selected.",
                awaitSelection(second));

        // ---- the same window's control API: a reference no row has is 404 ---------------------
        HttpResponse<String> unknown = select(port, "ms_run[1]:index=99999999");
        assertAll(
                () -> assertEquals(404, unknown.statusCode()),
                () -> assertEquals("unknown ref", unknown.body().strip()));

        // ---- the launch was recorded before PDV started ---------------------------------------
        CappedPdv.Launch window = processes.windows().get(0);
        List<Map<String, Object>> launched = denovoLaunches(run);
        Map<String, Object> payload = payload(launched.get(0));
        Path mzTab = run.resolve("pdv/mztab/psms.mztab");
        assertAll(
                "the pdv.launched event of the live window",
                () -> assertEquals(1, launched.size(), "one window, one event"),
                () -> assertTrue(window.eventAlreadyWritten(), "recorded before PDV started"),
                () -> assertEquals(window.argv(), argv(payload)),
                () -> assertEquals("denovo-gui", payload.get("pdv.launch")),
                () -> assertEquals(runId, payload.get("run.id")),
                () -> assertEquals("2.7.0", payload.get("tool.version")),
                () -> assertEquals(PDV_JAR_SHA256, payload.get("pdv.jar.sha256")),
                () -> assertEquals(mzTab.toString(), payload.get("launch.mztab")),
                () -> assertEquals(Integer.toString(port), argvAfter(window.argv(), "--port")),
                () -> assertEquals(mzTab.toString(), argvAfter(window.argv(), "--mztab")));

        // ---- Close PDV ends the process and frees the port -------------------------------------
        driver.clickOn(UiIds.VISUALISATION_CLOSE);
        String closed =
                RunSection.awaitText(
                        driver,
                        UiIds.VISUALISATION_WINDOW,
                        text -> text.startsWith("PDV's window for run " + runId + " is closed"),
                        SELECT_BOUND);
        assertTrue(
                closed.startsWith(
                        "PDV's window for run "
                                + runId
                                + " is closed: PDV was closed: /shutdown answered 200 bye, and"
                                + " the process ended with status "),
                closed);
        assertGone(window, port, mzTab);

        // ---- the workflow API, not through the interface: known 200, unknown 404, close ------
        workflowApi(run, jar, mzTab, first);

        // ---- the application's exit ends a window that is open --------------------------------
        driver.clickOn(UiIds.VISUALISATION_OPEN);
        int again = portOf(awaitReady(runId));
        CappedPdv.Launch reopened = processes.windows().get(processes.windows().size() - 1);
        assertTrue(reopened.process().isAlive());
        app.stopApplication();
        STOPPED.set(true);
        assertGone(reopened, again, mzTab);
        assertEquals(3, denovoLaunches(run).size(), "three windows, three events");
    }

    /** Opens the run through {@link PdvWindows} directly, as the product's port does. */
    private static void workflowApi(Path run, Path jar, Path mzTab, String psmId)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        StreamingHashService hashes = new StreamingHashService();
        Clock clock = Clock.systemUTC();
        RunLayout layout = new RunLayout(run);
        PdvWindows windows =
                new PdvWindows(
                        new PdvMzTabs(hashes, SecretRedactor.patternsOnly(), clock, Set::of),
                        new PdvLaunches(
                                processes, hashes, SecretRedactor.patternsOnly(), clock, LINUX),
                        new PdvController(PdvController.Timing.DEFAULT),
                        Set::of);
        try {
            BlockingQueue<PdvWindowStatus> states = new LinkedBlockingQueue<>();
            ApplicationServices host = ApplicationServices.forThisHost();
            PdvWindowStatus starting =
                    windows.open(
                            new PdvWindowRequest(
                                    layout,
                                    new ProjectSession(project, clock, host.runIds())
                                            .readRun(layout)
                                            .identity(),
                                    layout,
                                    new PdvInstallation(ToolVersion.parse("2.7.0"), jar),
                                    JavaRuntime.ofThisApplication(),
                                    pdvDisplay),
                            states::add);
            assertEquals(PdvWindowStatus.Phase.STARTING, starting.phase(), starting.message());
            PdvWindowStatus ready = states.poll(PDV_BOUND.toSeconds(), TimeUnit.SECONDS);
            while (ready != null && ready.phase() == PdvWindowStatus.Phase.STARTING) {
                ready = states.poll(PDV_BOUND.toSeconds(), TimeUnit.SECONDS);
            }
            PdvWindowStatus answer = ready;
            assertTrue(
                    answer != null && answer.phase() == PdvWindowStatus.Phase.READY,
                    () -> "the window did not become ready: " + answer);
            int port = answer.port().orElseThrow();
            RunId id = starting.run();
            PdvSelection known =
                    windows.select(id, false, psmId)
                            .get(SELECT_BOUND.toSeconds(), TimeUnit.SECONDS);
            PdvSelection unknown =
                    windows.selectReference(id, "ms_run[1]:index=99999999")
                            .get(SELECT_BOUND.toSeconds(), TimeUnit.SECONDS);
            assertAll(
                    "the workflow API's answers, PDV's own",
                    () -> assertEquals(PdvSelection.Kind.SELECTED, known.kind(), known.message()),
                    () -> assertEquals(OptionalInt.of(200), known.status()),
                    () -> assertEquals("PDV answered /select with 200 selected.", known.message()),
                    () -> assertEquals(PdvSelection.Kind.UNKNOWN_TO_PDV, unknown.kind()),
                    () -> assertEquals(OptionalInt.of(404), unknown.status()),
                    () ->
                            assertEquals(
                                    "PDV answered /select with 404 unknown ref.",
                                    unknown.message()));
            CappedPdv.Launch launch = processes.windows().get(processes.windows().size() - 1);
            PdvWindowShutdown shutdown = windows.close(id).orElseThrow();
            assertAll(
                    () -> assertEquals("answered 200 bye", shutdown.controlServer()),
                    () -> assertTrue(shutdown.exited()));
            assertGone(launch, port, mzTab);
        } finally {
            windows.close();
        }
    }

    private static String awaitReady(String runId) {
        return RunSection.awaitText(
                driver,
                UiIds.VISUALISATION_WINDOW,
                text ->
                        READY_PORT.matcher(text).find()
                                || text.startsWith("PDV could not show run " + runId)
                                || text.startsWith("PDV was not opened")
                                || text.startsWith("PDV could not be installed")
                                || text.startsWith("The PDV install was cancelled"),
                PDV_BOUND);
    }

    private static int portOf(String ready) {
        Matcher port = READY_PORT.matcher(ready);
        assertTrue(port.find(), ready);
        return Integer.parseInt(port.group(1));
    }

    private static String awaitSelection(String psmId) {
        return RunSection.awaitText(
                driver,
                UiIds.VISUALISATION_SELECTION,
                text -> text.startsWith("Target PSM " + psmId + " "),
                SELECT_BOUND);
    }

    private static HttpResponse<String> select(int port, String ref)
            throws IOException, InterruptedException {
        return HTTP.send(
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/select?ref="
                                                + URLEncoder.encode(ref, StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(20))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** The process has ended, no process of this JVM has the mzTab open, and the port refuses. */
    private static void assertGone(CappedPdv.Launch launch, int port, Path mzTab) {
        assertAll(
                "PDV ended",
                () -> assertFalse(launch.process().isAlive(), "the launched process ended"),
                () ->
                        assertEquals(
                                List.of(),
                                ProcessHandle.current()
                                        .descendants()
                                        .filter(ProcessHandle::isAlive)
                                        .filter(
                                                child ->
                                                        child.info()
                                                                .commandLine()
                                                                .orElse("")
                                                                .contains(mzTab.toString()))
                                        .map(ProcessHandle::pid)
                                        .toList(),
                                "no PDV process is left"),
                () ->
                        assertThrows(
                                ConnectException.class,
                                () -> new Socket(InetAddress.getLoopbackAddress(), port).close(),
                                "the control port is closed"));
    }

    private static String argvAfter(List<String> argv, String option) {
        return argv.get(argv.indexOf(option) + 1);
    }

    /** Runs one search through the Run section and returns its run directory. */
    private static Path search() {
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            List<Path> sorted = runs.sorted().toList();
            assertEquals(1, sorted.size(), () -> "the runs: " + sorted);
            Path run = sorted.get(0);
            assertEquals("The run succeeded: run " + idOf(run) + " in " + run + ".", outcome);
            return run;
        } catch (IOException unreadable) {
            return fail("the project's runs cannot be listed", unreadable);
        }
    }

    private static String idOf(Path run) {
        String name = String.valueOf(run.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }

    /** The {@code pdv.launched} events of kind {@code denovo-gui}, read with the test's JSON. */
    private static List<Map<String, Object>> denovoLaunches(Path run) throws IOException {
        Path log = run.resolve("provenance/events.log");
        List<Map<String, Object>> events = new ArrayList<>();
        if (!Files.exists(log)) {
            return events;
        }
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            Map<String, Object> event = TestJson.object(line);
            if ("pdv.launched".equals(event.get("type"))
                    && "denovo-gui".equals(payload(event).get("pdv.launch"))) {
                events.add(event);
            }
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }

    private static List<String> argv(Map<String, Object> payload) {
        int count = Integer.parseInt(String.valueOf(payload.get("argv.count")));
        List<String> argv = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            argv.add(String.valueOf(payload.get("argv." + index)));
        }
        return argv;
    }

    /**
     * The application's one process service, wrapped: a {@code denovo-gui} launch is noted -- its
     * argument array, and whether its run's event log already held a {@code pdv.launched} event
     * with exactly that array -- and started under the host's {@code timeout}; everything else
     * passes through unchanged.
     */
    private static final class CappedPdv implements ProcessRunner {

        /** One window launch. */
        record Launch(List<String> argv, boolean eventAlreadyWritten, StartedProcess process) {}

        private final ProcessService delegate;

        private final List<Launch> windows = Collections.synchronizedList(new ArrayList<>());

        CappedPdv(ProcessService delegate) {
            this.delegate = delegate;
        }

        @Override
        public StartedProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            if (!command.argv().contains("denovo-gui")) {
                return delegate.start(command, listener);
            }
            // a window runs in <run>/pdv/window
            Path run = up(up(command.workingDirectory()));
            List<Map<String, Object>> recorded = denovoLaunches(run);
            boolean written =
                    !recorded.isEmpty()
                            && argv(payload(recorded.get(recorded.size() - 1)))
                                    .equals(command.argv());
            List<String> capped = new ArrayList<>();
            capped.add(onPath("timeout").toString());
            capped.add("--kill-after=10s");
            capped.add(Long.toString(PDV_LIFETIME.toSeconds()));
            capped.addAll(command.argv());
            StartedProcess started =
                    delegate.start(
                            new ToolCommand(
                                    capped, command.workingDirectory(), command.environment()),
                            listener);
            windows.add(new Launch(command.argv(), written, started));
            return started;
        }

        List<Launch> windows() {
            synchronized (windows) {
                return List.copyOf(windows);
            }
        }

        void endEverything() {
            for (Launch launch : windows()) {
                launch.process().requestCancellation();
            }
        }

        private static Path up(Path path) {
            return Objects.requireNonNull(path.getParent(), () -> path + " has no parent");
        }

        private static Path onPath(String program) {
            for (String directory :
                    Objects.requireNonNullElse(System.getenv("PATH"), "")
                            .split(File.pathSeparator)) {
                Path candidate = Path.of(directory, program);
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
            return fail(
                    "no "
                            + program
                            + " on PATH: PDV must not outlive a test JVM that dies, so this test"
                            + " fails rather than launch it unbounded");
        }
    }

    /**
     * The test's Tool Manager: PDV is the product's managed Tool Manager's, over the empty cache;
     * everything else is the staged Comet and Percolator a search runs with.
     */
    private static final class StagedSearchManagedPdv implements ToolManager {

        private final ToolManager staged;

        private final ToolManager managedPdv;

        StagedSearchManagedPdv(ToolManager staged, ToolManager managedPdv) {
            this.staged = staged;
            this.managedPdv = managedPdv;
        }

        @Override
        public List<ToolOffer> offers() {
            List<ToolOffer> offers = new ArrayList<>(staged.offers());
            for (ToolOffer offer : managedPdv.offers()) {
                if (offer.tool() == ToolName.PDV) {
                    offers.add(offer);
                }
            }
            return List.copyOf(offers);
        }

        @Override
        public List<NoManagedBuild> noManagedBuild() {
            return List.of();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            if (tool != ToolName.PDV) {
                throw new IllegalArgumentException("only PDV is installed in this test");
            }
            return managedPdv.install(tool, version, listener);
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable)
                throws ToolRegistrationException {
            return staged.registerLocalBinary(tool, executable);
        }
    }
}
