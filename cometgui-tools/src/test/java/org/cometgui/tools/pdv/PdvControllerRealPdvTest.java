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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import java.util.stream.Stream;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.pdv.derived.DenovoGuiCommand;
import org.cometgui.tools.pdv.derived.PdvController;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.StartedProcess;
import org.cometgui.tools.testing.K562Run;
import org.cometgui.tools.testing.ProjectXvfb;
import org.cometgui.tools.testing.RealPdv;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Gate item 5's display half (design decision P11-16): PDV 2.7.0 opened on the product's K562 mzTab
 * through the <strong>derived controller</strong> -- ephemeral port, {@code /ready} polled, {@code
 * /select} sent over HTTP -- and what PDV then drew read by {@link PdvDisplayObserver}, which runs
 * PDV's own, unmodified main class in PDV's JVM and reads its state by reflection. No screen
 * coordinates.
 *
 * <p>The command is the product's {@link DenovoGuiCommand}; the runner rewrites only {@code -jar
 * <PDV jar>} to {@code -cp <PDV jar>:<test classes> PdvDisplayObserver}, keeping every argument,
 * and puts the JVM under the host's {@code timeout} so that a test JVM PIT halts cannot orphan it
 * (unit 5's rule). Selecting PSM after PSM -- two of them on one spectrum -- must each time draw
 * exactly that PSM's peptide, charge and modifications on that PSM's own row, over the producing
 * spectrum's peaks bit for bit, as read from the mzML with this test's own reader.
 */
@EnabledOnOs(OS.LINUX)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 6, unit = TimeUnit.MINUTES)
class PdvControllerRealPdvTest {

    private static final String BASE_3 = "20100614_Velos1_TaGe_SA_K562_3";
    private static final String BASE_4 = "20100614_Velos1_TaGe_SA_K562_4";
    private static final Duration PDV_LIFETIME = Duration.ofMinutes(5);
    private static final Duration READY_BOUND = Duration.ofMinutes(3);
    private static final Duration DRAW_BOUND = Duration.ofSeconds(60);

    /** Scan 11288 charge 4 carries five target PSMs: rank 1 and 2 share that one spectrum. */
    private static final String SHARED_1 = BASE_3 + "_11288_4_1";

    private static final String SHARED_2 = BASE_3 + "_11288_4_2";

    /** Rank 3 of the same spectrum, with an oxidised methionine at residue 2. */
    private static final String SHARED_OXIDISED = BASE_3 + "_11288_4_3";

    /** Another spectrum: scan 11367, charge 2. */
    private static final String OTHER = BASE_3 + "_11367_2_1";

    private static Path work;
    private static ProjectXvfb display;
    private static PdvController controller;
    private static ObserverRunner runner;
    private static Path mzTab;
    private static PdvMzTabExport export;
    private static final Map<String, Row> TARGET_ROWS = new HashMap<>();
    private static final BlockingQueue<PdvWindowState> STATES = new LinkedBlockingQueue<>();
    private static PdvWindowState ready;

    /** One target row of the mzTab: its 0-based position among the PSM rows and its reference. */
    private record Row(long position, String ref) {}

    @BeforeAll
    static void openPdvThroughTheController()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path build =
                Path.of(System.getProperty("cometgui.buildDirectory", "target")).toAbsolutePath();
        Files.createDirectories(build);
        work = Files.createTempDirectory(build, "pdv-controller-");
        Files.createDirectories(work.resolve("pdv"));
        export =
                PdvMzTabExporter.export(
                        new PdvMzTabRequest(
                                K562Run.percolatorTargets(),
                                Optional.of(K562Run.percolatorDecoys()),
                                List.of(
                                        new PdvMzTabSource(
                                                BASE_3,
                                                K562Run.mzml(),
                                                SpectrumFileFormat.MZML,
                                                K562Run.pepXml(),
                                                Optional.empty()),
                                        new PdvMzTabSource(
                                                BASE_4,
                                                K562Run.mzml4(),
                                                SpectrumFileFormat.MZML,
                                                K562Run.pepXml4(),
                                                Optional.empty())),
                                work.resolve("pdv").resolve("mztab")));
        mzTab = export.mzTab();
        long position = 0;
        List<String> header = null;
        for (String line : Files.readAllLines(mzTab, StandardCharsets.UTF_8)) {
            List<String> fields = List.of(line.split("\t", -1));
            if (fields.get(0).equals("PSH")) {
                header = fields;
            } else if (fields.get(0).equals("PSM")) {
                Objects.requireNonNull(header, "the PSH line comes first");
                if (fields.get(header.indexOf(PdvMzTabExporter.DECOY_COLUMN)).equals("0")) {
                    TARGET_ROWS.put(
                            fields.get(header.indexOf(PdvMzTabExporter.PSM_ID_COLUMN)),
                            new Row(position, fields.get(header.indexOf("spectra_ref"))));
                }
                position++;
            }
        }
        display = ProjectXvfb.start(work.resolve("xvfb"));
        List<Path> spectra = new ArrayList<>();
        for (PdvMzTabMgf mgf : export.mgfs()) {
            spectra.add(mgf.file());
        }
        Path home = Files.createDirectories(work.resolve("window").resolve("home"));
        DenovoGuiCommand command =
                new DenovoGuiCommand(
                        JavaRuntime.ofThisApplication(),
                        RealPdv.jar(),
                        mzTab,
                        spectra,
                        Optional.empty(),
                        false,
                        home,
                        work.resolve("window"),
                        display.clientEnvironment());
        controller =
                new PdvController(
                        new PdvController.Timing(
                                Duration.ofMillis(250),
                                READY_BOUND,
                                Duration.ofMillis(60),
                                Duration.ofSeconds(20),
                                Duration.ofSeconds(30),
                                3));
        runner = new ObserverRunner();
        controller.open(mzTab, runner, command::forPort, STATES::add);
        for (PdvWindowState state = next(READY_BOUND);
                state.phase() != PdvWindowState.Phase.READY;
                state = next(READY_BOUND)) {
            if (state.phase().isFinal()) {
                throw new AssertionError("PDV did not become ready: " + state.message());
            }
        }
        ready = controller.state(mzTab).orElseThrow();
        String attached = runner.observed(line -> line.equals(PdvDisplayObserver.ATTACHED));
        assertEquals(PdvDisplayObserver.ATTACHED, attached);
    }

    private static PdvWindowState next(Duration bound) throws InterruptedException {
        PdvWindowState state = STATES.poll(bound.toMillis(), TimeUnit.MILLISECONDS);
        if (state == null) {
            throw new AssertionError("no state change within " + bound + ": " + runner.log());
        }
        return state;
    }

    @AfterAll
    static void stopEverything() throws IOException {
        try {
            if (controller != null) {
                controller.close();
            }
        } finally {
            try {
                if (display != null) {
                    display.close();
                }
            } finally {
                if (work != null && Files.exists(work)) {
                    try (Stream<Path> walk = Files.walk(work)) {
                        for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(path);
                        }
                    }
                }
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "selecting PSM after PSM through the controller draws exactly each PSM's peptide on"
                    + " its own row over its producing spectrum's peaks -- two PSMs of one spectrum"
                    + " included")
    void eachSelectionDrawsThatPsm()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Map<Long, MzMlPeaks.Spectrum> spectra =
                MzMlPeaks.byScan(K562Run.mzml(), Set.of(11_288L, 11_367L));
        Map<String, String> peptides = percolatorPeptides(K562Run.percolatorTargets());
        List<Map<String, String>> drawn = new ArrayList<>();
        for (String psmId : List.of(OTHER, SHARED_1, SHARED_2, SHARED_OXIDISED, SHARED_1)) {
            Row row = TARGET_ROWS.get(psmId);
            runner.forgetDisplayed();
            PdvSelectOutcome outcome =
                    controller
                            .select(mzTab, row.ref())
                            .get(DRAW_BOUND.toSeconds(), TimeUnit.SECONDS);
            assertEquals(
                    Optional.of(new PdvControlAnswer(200, "selected")),
                    outcome.answer(),
                    () -> psmId + ": " + outcome.message());
            Map<String, String> shown = runner.displayedRow(row.position(), DRAW_BOUND);
            drawn.add(shown);
            String[] parts = psmId.substring(BASE_3.length() + 1).split("_");
            MzMlPeaks.Spectrum source = spectra.get(Long.parseLong(parts[0]));
            String percolator = peptides.get(psmId);
            List<double[]> peaks = new ArrayList<>(source.peaks());
            assertAll(
                    psmId + " as PDV drew it: " + shown,
                    () -> assertEquals(Long.toString(row.position()), shown.get("row")),
                    () -> assertEquals(residues(percolator), shown.get("sequence")),
                    () -> assertEquals(parts[1], shown.get("charge")),
                    () -> assertEquals(Integer.toString(peaks.size()), shown.get("peaks")),
                    () -> assertEquals(PdvDisplayObserver.digest(peaks), shown.get("peaksSha256")),
                    () ->
                            assertEquals(
                                    Double.parseDouble(source.selectedIonMz()),
                                    Double.parseDouble(shown.get("precursor"))),
                    () ->
                            assertEquals(
                                    psmId.equals(SHARED_OXIDISED) ? "2:15.994915" : "",
                                    shown.get("modifications")),
                    () -> assertFalse(shown.containsKey("error"), shown::toString));
        }
        assertAll(
                "the drawn spectrum changed with each selection",
                () ->
                        assertEquals(
                                drawn.get(1).get("peaksSha256"), drawn.get(2).get("peaksSha256")),
                () -> assertNotEquals(drawn.get(1).get("sequence"), drawn.get(2).get("sequence")),
                () ->
                        assertNotEquals(
                                drawn.get(0).get("peaksSha256"), drawn.get(1).get("peaksSha256")),
                () -> assertNotEquals(drawn.get(1).get("row"), drawn.get(2).get("row")),
                () ->
                        assertEquals(
                                drawn.get(1), drawn.get(4), "back to the first: the same drawing"));
    }

    @Test
    @Order(2)
    @DisplayName("a reference no row has is answered 404 unknown ref through the controller")
    void anUnknownReferenceIsNotFound()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        String past = "ms_run[1]:index=" + export.mgfs().get(0).entries();
        PdvSelectOutcome outcome =
                controller.select(mzTab, past).get(DRAW_BOUND.toSeconds(), TimeUnit.SECONDS);
        assertEquals(Optional.of(new PdvControlAnswer(404, "unknown ref")), outcome.answer());
    }

    @Test
    @Order(3)
    @DisplayName(
            "shutdown ends PDV's JVM and its control port, though /shutdown alone leaves PDV"
                    + " running")
    void shutdownEndsPdv()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        int port = ready.port().orElseThrow();
        StartedProcess started = runner.only();

        PdvWindowShutdown shutdown = controller.shutdown(mzTab).orElseThrow();

        assertAll(
                () -> assertEquals("answered 200 bye", shutdown.controlServer()),
                () -> assertTrue(shutdown.exited()),
                () -> assertEquals(OptionalInt.of(143), shutdown.exitCode(), runner::log),
                () -> assertFalse(started.isAlive()),
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

    /** Percolator's peptide per target PSMId, read here from the table itself. */
    private static Map<String, String> percolatorPeptides(Path table) throws IOException {
        Map<String, String> peptides = new HashMap<>();
        List<String> lines = Files.readAllLines(table, StandardCharsets.UTF_8);
        List<String> header = List.of(lines.get(0).split("\t", -1));
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\t", -1);
            peptides.put(fields[header.indexOf("PSMId")], fields[header.indexOf("peptide")]);
        }
        return peptides;
    }

    /** {@code K.QM[15.9949]SEK.L} -> {@code QMSEK}. */
    private static String residues(String percolator) {
        String inner =
                percolator.substring(percolator.indexOf('.') + 1, percolator.lastIndexOf('.'));
        return inner.replaceAll("\\[[^]]*]", "");
    }

    /**
     * The test's runner: the controller's command with {@code -jar <PDV jar>} replaced by the
     * observer's class path and main class, under the host's {@code timeout}, through the process
     * service; the observer's reports are kept, every line is passed on.
     */
    private static final class ObserverRunner implements ProcessRunner {

        private final ProcessService service = new ProcessService(Clock.systemUTC());
        private final List<StartedProcess> started =
                Collections.synchronizedList(new ArrayList<>());
        private final BlockingQueue<String> observed = new LinkedBlockingQueue<>();
        private final List<String> log = Collections.synchronizedList(new ArrayList<>());

        @Override
        public StartedProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            List<String> argv = command.argv();
            assertEquals("-jar", argv.get(2), "the product command launches -jar");
            assertEquals("denovo-gui", argv.get(4), "the product command is denovo-gui");
            List<String> rewritten = new ArrayList<>();
            rewritten.add(onPath("timeout").toString());
            rewritten.add("--kill-after=10s");
            rewritten.add(Long.toString(PDV_LIFETIME.toSeconds()));
            rewritten.add(argv.get(0));
            rewritten.add(argv.get(1));
            rewritten.add("-cp");
            rewritten.add(argv.get(3) + File.pathSeparator + testClasses());
            rewritten.add(PdvDisplayObserver.class.getName());
            rewritten.addAll(argv.subList(4, argv.size()));
            StartedProcess process =
                    service.start(
                            new ToolCommand(
                                    rewritten, command.workingDirectory(), command.environment()),
                            new ProcessListener() {
                                @Override
                                public void onStandardOutput(String line) {
                                    log.add(line);
                                    if (line.startsWith("PDVOBS")) {
                                        observed.add(line);
                                    }
                                    listener.onStandardOutput(line);
                                }

                                @Override
                                public void onStandardError(String line) {
                                    log.add(line);
                                    listener.onStandardError(line);
                                }

                                @Override
                                public void onExit(int exitCode) {
                                    log.add("exited with status " + exitCode);
                                    listener.onExit(exitCode);
                                }
                            });
            started.add(process);
            return process;
        }

        StartedProcess only() {
            assertEquals(1, started.size(), "PDV was launched once");
            return started.get(0);
        }

        String log() {
            synchronized (log) {
                return String.join(" | ", log.subList(Math.max(0, log.size() - 30), log.size()));
            }
        }

        void forgetDisplayed() {
            observed.clear();
        }

        String observed(java.util.function.Predicate<String> wanted) throws InterruptedException {
            long deadline = System.nanoTime() + READY_BOUND.toNanos();
            while (System.nanoTime() < deadline) {
                String line = observed.poll(1, TimeUnit.SECONDS);
                if (line != null && wanted.test(line)) {
                    return line;
                }
                if (line != null && line.startsWith("PDVOBS error")) {
                    throw new AssertionError("the observer failed: " + line);
                }
            }
            throw new AssertionError("the observer did not report in time: " + log());
        }

        /** The next report of a drawing on that row. */
        Map<String, String> displayedRow(long row, Duration bound) throws InterruptedException {
            long deadline = System.nanoTime() + bound.toNanos();
            List<String> seen = new ArrayList<>();
            while (System.nanoTime() < deadline) {
                String line = observed.poll(1, TimeUnit.SECONDS);
                if (line == null) {
                    continue;
                }
                seen.add(line);
                if (line.startsWith(PdvDisplayObserver.PREFIX)) {
                    Map<String, String> fields = new LinkedHashMap<>();
                    for (String field :
                            line.substring(PdvDisplayObserver.PREFIX.length()).split("\t")) {
                        int equals = field.indexOf('=');
                        if (equals > 0) {
                            fields.put(field.substring(0, equals), field.substring(equals + 1));
                        }
                    }
                    if (Long.toString(row).equals(fields.get("row"))
                            && fields.containsKey("peaks")) {
                        return fields;
                    }
                }
            }
            throw new AssertionError(
                    "PDV drew nothing on row " + row + " within " + bound + "; seen: " + seen);
        }

        private static Path testClasses() {
            try {
                return Path.of(
                        PdvDisplayObserver.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
            } catch (URISyntaxException impossible) {
                throw new AssertionError(impossible);
            }
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
            throw new AssertionError(
                    "no "
                            + program
                            + " on PATH: PDV must not outlive a halted test JVM, so this test"
                            + " fails rather than launch it unbounded");
        }
    }
}
