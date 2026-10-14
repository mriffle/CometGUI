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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.RealSearch.LaunchRecorder;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.presets.PresetApplication;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.viewmodel.limelight.LimelightPort;
import org.cometgui.ui.viewmodel.limelight.LimelightResult;
import org.cometgui.ui.viewmodel.limelight.LimelightUploadPort;
import org.cometgui.ui.viewmodel.limelight.LimelightUploaded;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.limelight.LimelightUploadRefusedException;
import org.cometgui.workflow.limelight.LimelightUploadWatcher;
import org.cometgui.workflow.provenance.LoadedRun;
import org.cometgui.workflow.provenance.ProvenanceReader;
import org.cometgui.workflow.state.Plan;

/**
 * The shared world of the Phase 13 exit gate on real runs (design decisions P13-7 and P13-10):
 * Comet 2026.03.0, Percolator 3.07.1 and the Limelight converter 2.8.1 installed by the product's
 * own installer -- {@link ToolManagerWiring#toolManager}, over an empty cache, fetching the real
 * artefacts from the mirror through a loopback server, with the real three-stage probe and the real
 * completion markers -- and runs started through the product's own run port ({@link
 * RunWiring#port}: {@code WorkflowRunPort}, {@code CometWorkflow}, the engine).
 *
 * <p>Built once by the gate test class and shared by every item: the completed run of the two K562
 * files against the 1000-record subset, with Percolator writing XML for Limelight; its conversion;
 * a refused and a submitted upload of the conversion to the loopback fake; and its two exports,
 * rendered through the read model from the one loaded record.
 *
 * <p>Files read outside this module: those of {@link RealSearch} and {@link ArtefactMirror} --
 * {@code scratch/fixture/} and {@code scratch/phase05/artefacts/} -- and the converter JAR again
 * for the fake's schema; {@code manifests/tools.json} on the class path.
 */
final class GateWorld {

    static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-gatetest", "unknown", Instant.parse("2026-10-13T00:00:00Z"));

    static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    static final String COMET_FILE = "v2026.03.0__comet.linux.exe";

    static final String PERCOLATOR_ZIP = "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";

    static final String PERCOLATOR_DEB = "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";

    static final String CONVERTER_JAR = "v2.8.1__cometPercolator2LimelightXML.jar";

    /** How long one install, one run, one conversion or one upload may take. A bound. */
    static final long BOUND_MINUTES = 10;

    /** The Limelight project the uploads go to. */
    static final String PROJECT = "7";

    /** The seeded Limelight submit key. */
    static final String KEY = seed("CGSEED-KEY-");

    /** The password in a Limelight server address typed with user information. */
    static final String URL_PASSWORD = seed("CGSEED-URLPW-");

    /** The password in a URL inside an applied preset's name, which reaches the settings. */
    static final String PRESET_URL_PASSWORD = seed("CGSEED-PRESETPW-");

    /** The value of a secret-named assignment inside the same preset name. */
    static final String PRESET_ASSIGNED = seed("CGSEED-PRESETKV-");

    /**
     * The password in a URL inside a second applied preset's LONG name: a carrier of more than 256
     * characters, so that a redaction rule skipped for long values (Phase 04's shape #6, a
     * size-conditioned leak) is seen by this real run too.
     */
    static final String LONG_PRESET_PASSWORD = seed("CGSEED-LONGPW-");

    /** The second preset's name: well over 256 characters, the seeded URL in its middle. */
    static final String LONG_PRESET_NAME =
            "Shared lab thread count, as agreed at the group meeting and recorded here so that"
                    + " every run made with it can be traced back to the decision and to the"
                    + " page where the reasoning is written down, which lives at"
                    + " https://scientist:"
                    + LONG_PRESET_PASSWORD
                    + "@presets.example.org/lab/threads -- please do not change it without"
                    + " asking first, because the comparison between this month's runs and last"
                    + " month's depends on it staying the same";

    /** Every seeded secret, by where it entered. */
    static final Map<String, String> SEEDS = seeds();

    private final Path root;

    private final Path cacheRoot;

    private final Path project;

    private final Path exports;

    private final List<Path> spectra;

    private final Path subset;

    private final LaunchRecorder launches;

    private final ToolManager tools;

    private final ApplicationServices services;

    private final ProjectSession session;

    private final CachingHashService hashes;

    private final SessionEngine engine;

    private final SessionSecrets secrets;

    private final BoundedMessageLog console;

    private final Path completed;

    /** Every regular file under the completed run's outputs/, listed from disk when it ended. */
    private final List<Path> outputsWhenSealed;

    private final LimelightResult conversion;

    private final String refusedServerMessage;

    private final LimelightUploaded refusedUpload;

    private final LimelightUploaded submittedUpload;

    private final LoadedRun loaded;

    private final Path exportedJson;

    private final Path exportedRst;

    private GateWorld(Path scratch)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunNotStartedException {
        root = scratch.toRealPath();
        cacheRoot = root.resolve("cometgui-data");
        project = root.resolve("project");
        exports = Files.createDirectories(root.resolve("exports"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        spectra = RealSearch.spectra(inputs);
        subset = RealSearch.subset(inputs.resolve("subset.fasta"));
        Clock clock = Clock.systemUTC();
        launches = new LaunchRecorder(new ProcessService(clock));
        ArtefactRecord percolator = ArtefactMirror.record(ToolName.PERCOLATOR, "3.07.1", LINUX);
        LoopbackArtefactServer served =
                new LoopbackArtefactServer()
                        .serve(
                                ArtefactMirror.record(ToolName.COMET, "2026.03.0", LINUX).url(),
                                ArtefactMirror.artefact(COMET_FILE))
                        .serve(percolator.url(), ArtefactMirror.artefact(PERCOLATOR_ZIP))
                        .serve(
                                percolator.companions().get(0).url(),
                                ArtefactMirror.artefact(PERCOLATOR_DEB))
                        .serve(
                                ArtefactMirror.record(ToolName.LIMELIGHT_CONVERTER, "2.8.1", LINUX)
                                        .url(),
                                ArtefactMirror.artefact(CONVERTER_JAR));
        try (served) {
            tools =
                    ToolManagerWiring.toolManager(
                            LINUX,
                            DEBIAN_12,
                            cacheRoot,
                            launches,
                            clock,
                            ToolManagerWiring.installThreads(),
                            served);
            install(tools, ToolName.COMET, "2026.03.0");
            install(tools, ToolName.PERCOLATOR, "3.07.1");
            install(tools, ToolName.LIMELIGHT_CONVERTER, "2.8.1");
        }
        EnvironmentReader environment = withoutSessionBus(new SystemEnvironmentReader());
        services =
                new ApplicationServices(
                        clock,
                        environment,
                        new PlatformFileSystemAccess(environment),
                        new ClockRunIdSource(clock),
                        new FfmGlibcVersionSource(),
                        launches,
                        null,
                        null);
        session = new ProjectSession(project, clock, services.runIds());
        hashes = RunWiring.hasher();
        console = new BoundedMessageLog();
        engine =
                RunWiring.port(
                        services,
                        console,
                        Optional.of(tools),
                        "every tool is installed in this test",
                        session,
                        BUILD,
                        hashes);
        secrets = new SessionSecrets();

        Ended ended =
                run(
                        searchModel(subset, "4", ValueOrigin.PRESET),
                        List.of(seededPreset(), longPreset()),
                        spectra,
                        Optional.empty());
        completed = ended.directory();
        assertEquals(
                AttemptOutcome.SUCCEEDED,
                ended.result().outcome(),
                () -> "the completed run: " + ended.result().failures());
        outputsWhenSealed = regularFiles(completed.resolve("outputs"));

        ResultsPort results = RunWiring.results(services, session, hashes, BUILD, engine);
        ResultsRun listed =
                results.runs().stream()
                        .filter(
                                run ->
                                        run.id()
                                                .value()
                                                .equals(
                                                        ended.result()
                                                                .manifest()
                                                                .run()
                                                                .runId()
                                                                .value()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "the Results section does not list "
                                                        + completed
                                                        + ": "
                                                        + listedRuns(results)));
        LimelightPort limelight =
                RunWiring.limelight(
                        services,
                        console,
                        session,
                        hashes,
                        engine,
                        Optional.of(tools),
                        "every tool is installed in this test");
        conversion = limelight.convert(listed, limelight.check(listed, "").saved(), "", line -> {});

        LimelightUploadPort upload =
                RunWiring.limelightUpload(services, session, hashes, engine, secrets);
        try (FakeLimelightServer fake = FakeLimelightServer.start(KEY, Integer.parseInt(PROJECT))) {
            String withUser =
                    fake.baseUrl().replace("http://", "http://scientist:" + URL_PASSWORD + "@");
            refusedServerMessage = refusedServer(upload, withUser);
            fake.refuseKey(true);
            refusedUpload =
                    upload.send(
                            upload.prepare(listed, conversion.file(), fake.baseUrl(), PROJECT),
                            Optional.of(upload.admit(KEY)),
                            false,
                            Watcher.QUIET);
            fake.refuseKey(false);
            submittedUpload =
                    upload.send(
                            upload.prepare(listed, conversion.file(), fake.baseUrl(), PROJECT),
                            Optional.of(upload.admit(KEY)),
                            false,
                            Watcher.QUIET);
        }

        loaded =
                new ProvenanceReader(
                                secrets::redactor,
                                new CachingHashService(new StreamingHashService()))
                        .load(completed);
        exportedJson = loaded.exportJson(exports.resolve("exported-provenance.json"), false);
        exportedRst = loaded.exportRst(exports.resolve("exported-provenance.rst"), false);
    }

    // accessors, one per component of the world
    Path root() {
        return root;
    }

    Path cacheRoot() {
        return cacheRoot;
    }

    Path project() {
        return project;
    }

    Path exports() {
        return exports;
    }

    List<Path> spectra() {
        return spectra;
    }

    Path subset() {
        return subset;
    }

    LaunchRecorder launches() {
        return launches;
    }

    ToolManager tools() {
        return tools;
    }

    ApplicationServices services() {
        return services;
    }

    ProjectSession session() {
        return session;
    }

    CachingHashService hashes() {
        return hashes;
    }

    SessionEngine engine() {
        return engine;
    }

    SessionSecrets secrets() {
        return secrets;
    }

    BoundedMessageLog console() {
        return console;
    }

    Path completed() {
        return completed;
    }

    List<Path> outputsWhenSealed() {
        return outputsWhenSealed;
    }

    LimelightResult conversion() {
        return conversion;
    }

    String refusedServerMessage() {
        return refusedServerMessage;
    }

    LimelightUploaded refusedUpload() {
        return refusedUpload;
    }

    LimelightUploaded submittedUpload() {
        return submittedUpload;
    }

    LoadedRun loaded() {
        return loaded;
    }

    Path exportedJson() {
        return exportedJson;
    }

    Path exportedRst() {
        return exportedRst;
    }

    /**
     * Builds the world: installs, runs, converts, uploads and exports.
     *
     * @param scratch the directory it is built in, which must outlive every test using it
     * @return the world
     * @throws IOException if a step cannot read or write what it needs
     * @throws InterruptedException if interrupted while waiting
     * @throws ExecutionException if a run's outcome could not be delivered
     * @throws TimeoutException if a run outlasts its bound
     * @throws RunNotStartedException if the port refuses to start a run
     */
    static GateWorld build(Path scratch)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunNotStartedException {
        return new GateWorld(scratch);
    }

    private static String seed(String prefix) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder seed = new StringBuilder(prefix);
        for (int index = 0; index < 40; index++) {
            seed.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return seed.toString();
    }

    private static Map<String, String> seeds() {
        Map<String, String> seeds = new LinkedHashMap<>();
        seeds.put("the Limelight submit key", KEY);
        seeds.put("the password of a Limelight server address with user information", URL_PASSWORD);
        seeds.put("the password of a URL in an applied preset's name", PRESET_URL_PASSWORD);
        seeds.put("the value of api_key= in an applied preset's name", PRESET_ASSIGNED);
        seeds.put(
                "the password of a URL in a long (> 256 characters) applied preset's name",
                LONG_PRESET_PASSWORD);
        return Collections.unmodifiableMap(seeds);
    }

    /** The second applied preset, whose long name carries one seeded secret. */
    static PresetApplication longPreset() {
        return new PresetApplication(
                "lab-threads-agreed",
                LONG_PRESET_NAME,
                Preset.Origin.USER,
                ToolVersion.parse("2026.03.0"),
                1,
                Map.of("num_threads", "4"));
    }

    /** The applied preset whose name carries two seeded secrets into the run's settings. */
    static PresetApplication seededPreset() {
        return new PresetApplication(
                "lab-threads",
                "Lab threads, shared from https://scientist:"
                        + PRESET_URL_PASSWORD
                        + "@presets.example.org/lab with api_key="
                        + PRESET_ASSIGNED,
                Preset.Origin.USER,
                ToolVersion.parse("2026.03.0"),
                1,
                Map.of("num_threads", "4"));
    }

    /** The configuration a scientist would set: the database, decoys, and the threads. */
    static CometParameters searchModel(Path database, String threads, ValueOrigin threadsOrigin) {
        return ParameterEditorWiring.newSession()
                .model()
                .withText("database_name", database.toString(), ValueOrigin.USER)
                .withText("decoy_search", "1", ValueOrigin.USER)
                .withText("num_threads", threads, threadsOrigin);
    }

    /** The installed managed build of a tool, as the Tool Manager offers it. */
    ToolOffer installed(ToolName tool, String version) {
        return tools.offers().stream()
                .filter(offer -> offer.tool() == tool)
                .filter(offer -> offer.version().equals(ToolVersion.parse(version)))
                .filter(offer -> offer.state() == ToolInstallState.INSTALLED)
                .filter(offer -> offer.origin() == ToolOrigin.MANAGED)
                .findFirst()
                .orElseThrow(() -> new AssertionError(tool.id() + " " + version + " installed"));
    }

    /** Percolator 3.07.1 with Limelight conversion enabled, so it writes XML. */
    PercolatorRequest percolatorForLimelight() {
        ToolOffer percolator = installed(ToolName.PERCOLATOR, "3.07.1");
        return new PercolatorRequest(
                Optional.of(percolator),
                true,
                Optional.of(
                        PercolatorResolver.resolve(
                                List.of(percolator),
                                EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION))),
                Optional.of(PercolatorSettings.defaults()),
                List.of(),
                false);
    }

    /**
     * Starts a run through the port and waits for it to end.
     *
     * @param model the configuration
     * @param presets the applied presets
     * @param searched the spectrum files
     * @param whenRunning given the attempt once it has started, before it is awaited
     * @return the run directory and how the attempt ended
     */
    Ended run(
            CometParameters model,
            List<PresetApplication> presets,
            List<Path> searched,
            Optional<java.util.function.Consumer<ActiveRun>> whenRunning)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunNotStartedException {
        CompletableFuture<RunResult> finished = new CompletableFuture<>();
        List<String> before = runDirectories();
        ActiveRun active =
                engine.start(
                        model,
                        presets,
                        searched,
                        percolatorForLimelight(),
                        new RunObserver() {
                            @Override
                            public void planned(Plan plan, String description) {}

                            @Override
                            public void onTransition(StepTransition transition) {}

                            @Override
                            public void onRunFinished(RunResult result) {
                                finished.complete(result);
                            }
                        });
        whenRunning.ifPresent(action -> action.accept(active));
        RunResult result = finished.get(BOUND_MINUTES, TimeUnit.MINUTES);
        List<String> after = new ArrayList<>(runDirectories());
        after.removeAll(before);
        assertEquals(1, after.size(), "one new run directory: " + after);
        return new Ended(project.resolve("runs").resolve(after.get(0)), result);
    }

    /**
     * A run that has ended.
     *
     * @param directory its directory
     * @param result how its attempt ended
     */
    record Ended(Path directory, RunResult result) {}

    private List<String> runDirectories() throws IOException {
        Path runs = project.resolve("runs");
        if (!Files.isDirectory(runs)) {
            return List.of();
        }
        try (Stream<Path> listed = Files.list(runs)) {
            return listed.map(path -> String.valueOf(path.getFileName())).sorted().toList();
        }
    }

    private static String listedRuns(ResultsPort results) {
        try {
            return String.valueOf(results.runs());
        } catch (IOException unreadable) {
            return unreadable.toString();
        }
    }

    static String idOf(Path run) {
        return String.valueOf(run.getFileName());
    }

    /** Every regular file under a directory, found by walking it from disk, sorted. */
    static List<Path> regularFiles(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static void install(ToolManager manager, ToolName tool, String version)
            throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        List<InstallPhase> phases = Collections.synchronizedList(new ArrayList<>());
        manager.install(
                tool,
                ToolVersion.parse(version),
                progress -> {
                    phases.add(progress.phase());
                    if (progress.phase().isTerminal()) {
                        done.countDown();
                    }
                });
        assertTrue(done.await(BOUND_MINUTES, TimeUnit.MINUTES), tool.id() + " " + phases);
        assertEquals(
                InstallPhase.DONE,
                phases.get(phases.size() - 1),
                tool.id() + " " + version + ": " + phases);
    }

    private static String refusedServer(LimelightUploadPort upload, String server) {
        try {
            upload.server(server);
        } catch (LimelightUploadRefusedException refused) {
            return refused.getMessage();
        }
        throw new AssertionError("a server address carrying a password was accepted");
    }

    /** The real environment, without a session bus: no desktop keyring can be reached. */
    private static EnvironmentReader withoutSessionBus(EnvironmentReader real) {
        return new EnvironmentReader() {
            @Override
            public Optional<String> environmentVariable(String name) {
                return "DBUS_SESSION_BUS_ADDRESS".equals(name)
                        ? Optional.empty()
                        : real.environmentVariable(name);
            }

            @Override
            public Optional<String> systemProperty(String name) {
                return real.systemProperty(name);
            }
        };
    }

    /** Hears nothing. */
    private enum Watcher implements LimelightUploadWatcher {
        QUIET;

        @Override
        public void line(String line) {}

        @Override
        public void bytesSent(long bytes, long total) {}
    }

    /** The text of a file, for a dump. */
    static String text(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
