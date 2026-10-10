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

package org.cometgui.workflow.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.pdv.PdvControlAnswer;
import org.cometgui.tools.pdv.PdvWindowShutdown;
import org.cometgui.tools.pdv.derived.PdvController;
import org.cometgui.workflow.steps.RunResultFiles;
import org.cometgui.workflow.testing.FakePdvWindows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opening a finished run in PDV's window through the workflow: the mzTab prepared, the launch
 * recorded before it starts, the (decoy, PSMId) index turning a Results selection into PDV's {@code
 * spectra_ref}, and the window closed -- against a fake {@code denovo-gui} that answers as PDV's
 * control server does, on real loopback ports.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class PdvWindowsTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    private static final Duration BOUND = Duration.ofSeconds(20);

    private static final String SCAN_101 = PdvFixtures.SCAN_101;

    @TempDir private Path project;

    private RunLayout run;
    private RunIdentity identity;
    private String psmBase;
    private PdvInstallation pdv;
    private final Set<RunId> executing = new TreeSet<>((a, b) -> a.value().compareTo(b.value()));
    private final FakePdvWindows fake = new FakePdvWindows();
    private final List<String> recordedAtStart = new ArrayList<>();
    private final LinkedBlockingQueue<PdvWindowStatus> statuses = new LinkedBlockingQueue<>();
    private PdvWindows windows;

    @BeforeEach
    void finishedRun() throws IOException {
        run = PdvFixtures.run(project, PdvFixtures.RUN_ID);
        Path spectra = PdvFixtures.mgf(project.resolve("inputs").resolve("sample.mgf"), SCAN_101);
        identity = PdvFixtures.identity(PdvFixtures.RUN_ID, spectra);
        String base = identity.spectra().get(0).base();
        psmBase = run.cometOutputBase(base).toString();
        pepXml(run.pepXmlFile(base), hit("PEPTIDEK", "R", "A"), hit("KEDITPEP", "K", "-"));
        table(
                RunResultFiles.table(run, TableKind.TARGET_PSMS),
                row(1, "2.5", "0.001", "R.PEPTIDEK.A"),
                row(2, "1.5", "0.01", "K.KEDITPEP.-"));
        // decoy_search = 2: the decoy hits are in their own pepXML, and share the target PSMIds
        pepXml(Path.of(psmBase + PdvMzTabs.DECOY_PEP_XML_SUFFIX), hit("KEDITPEP", "K", "-"));
        table(
                RunResultFiles.table(run, TableKind.DECOY_PSMS),
                row(1, "-1.5", "0.9", "K.KEDITPEP.-"));
        Path jar =
                PdvFixtures.jar(project.resolve("tools/PDV-2.7.0/PDV-2.7.0.jar"), "PDV", "2.7.0");
        pdv = new PdvInstallation(ToolVersion.parse("2.7.0"), jar);
        windows = windows(fake);
    }

    @AfterEach
    void closeEverything() {
        windows.close();
        fake.windows().forEach(FakePdvWindows.Window::requestCancellation);
    }

    /** The windows over a runner that notes, at each start, the events already in the log. */
    private PdvWindows windows(ProcessRunner processes) {
        return windows(processes, new StreamingHashService());
    }

    private PdvWindows windows(ProcessRunner processes, HashService hashes) {
        ProcessRunner noting =
                (command, listener) -> {
                    recordedAtStart.add(lastLaunchArgv());
                    return processes.start(command, listener);
                };
        return new PdvWindows(
                new PdvMzTabs(
                        hashes, SecretRedactor.patternsOnly(), CLOCK, () -> Set.copyOf(executing)),
                new PdvLaunches(noting, hashes, SecretRedactor.patternsOnly(), CLOCK, LINUX),
                new PdvController(
                        new PdvController.Timing(
                                Duration.ofMillis(20),
                                BOUND,
                                Duration.ofMillis(50),
                                Duration.ofSeconds(5),
                                Duration.ofSeconds(10),
                                3)),
                () -> Set.copyOf(executing));
    }

    private String lastLaunchArgv() {
        try {
            List<ProvenanceEvent> launched = launches();
            if (launched.isEmpty()) {
                return "no event";
            }
            Map<String, String> payload = launched.get(launched.size() - 1).payload();
            List<String> argv = new ArrayList<>();
            int count = Integer.parseInt(payload.get(PdvLaunches.ARGV_COUNT));
            for (int index = 0; index < count; index++) {
                argv.add(payload.get(PdvLaunches.ARGV_PREFIX + index));
            }
            return String.join(" ", argv);
        } catch (IOException unreadable) {
            return unreadable.toString();
        }
    }

    private List<ProvenanceEvent> launches() throws IOException {
        if (!Files.exists(run.eventLogFile())) {
            return List.of();
        }
        return ProvenanceEventLogReader.recover(run.eventLogFile()).events().stream()
                .filter(event -> event.type() == ProvenanceEventType.PDV_LAUNCHED)
                .toList();
    }

    private PdvWindowRequest request() {
        return new PdvWindowRequest(
                run,
                identity,
                run,
                pdv,
                new JavaRuntime(project.resolve("jre/bin/java")),
                Map.of("DISPLAY", ":7"));
    }

    private String row(int rank, String score, String q, String peptide) {
        return psmBase
                + "_101_2_"
                + rank
                + "\t"
                + score
                + "\t"
                + q
                + "\t0.01\t"
                + peptide
                + "\tsp|P1|ONE";
    }

    private static void table(Path file, String... rows) throws IOException {
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "a directory"));
        StringBuilder text =
                new StringBuilder(
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
        for (String row : rows) {
            text.append(row).append('\n');
        }
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    private static String hit(String peptide, String previous, String next) {
        return "   <search_hit hit_rank=\"1\" peptide=\""
                + peptide
                + "\" peptide_prev_aa=\""
                + previous
                + "\" peptide_next_aa=\""
                + next
                + "\" protein=\"sp|P1|ONE\" num_tot_proteins=\"1\""
                + " calc_neutral_pep_mass=\"900.400000\" massdiff=\"0.001000\">\n"
                + "    <search_score name=\"xcorr\" value=\"2.0\"/>\n"
                + "   </search_hit>\n";
    }

    private static void pepXml(Path file, String... hits) throws IOException {
        StringBuilder text =
                new StringBuilder(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<msms_pipeline_analysis"
                                + " xmlns=\"http://regis-web.systemsbiology.net/pepXML\">\n"
                                + " <msms_run_summary base_name=\"/data/sample\""
                                + " raw_data_type=\"raw\" raw_data=\".mgf\">\n"
                                + "  <search_summary base_name=\"/run/sample\""
                                + " search_engine=\"Comet\""
                                + " search_engine_version=\"2026.03 rev. 0 (fa08489)\""
                                + " precursor_mass_type=\"monoisotopic\""
                                + " fragment_mass_type=\"monoisotopic\" search_id=\"1\">\n"
                                + "  </search_summary>\n"
                                + "  <spectrum_query spectrum=\"sample.00101.00101.2\""
                                + " spectrumNativeID=\""
                                + SCAN_101
                                + "\" start_scan=\"101\" end_scan=\"101\""
                                + " precursor_neutral_mass=\"900.400000\" assumed_charge=\"2\""
                                + " index=\"1\" retention_time_sec=\"61.5\">\n"
                                + "   <search_result>\n");
        int rank = 0;
        for (String hit : hits) {
            rank++;
            text.append(hit.replace("hit_rank=\"1\"", "hit_rank=\"" + rank + "\""));
        }
        text.append("   </search_result>\n  </spectrum_query>\n </msms_run_summary>\n")
                .append("</msms_pipeline_analysis>\n");
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "a directory"));
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    private PdvWindowStatus next() throws InterruptedException {
        PdvWindowStatus status = statuses.poll(BOUND.toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(status != null, "no status within " + BOUND);
        return status;
    }

    private PdvWindowStatus openReady() throws IOException, InterruptedException {
        PdvWindowStatus starting = windows.open(request(), statuses::add);
        assertEquals(PdvWindowStatus.Phase.STARTING, starting.phase(), starting.message());
        assertEquals(starting, next());
        PdvWindowStatus ready = next();
        assertEquals(PdvWindowStatus.Phase.READY, ready.phase(), ready.message());
        return ready;
    }

    private static PdvSelection await(CompletableFuture<PdvSelection> selection)
            throws InterruptedException, ExecutionException, TimeoutException {
        return selection.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
    }

    private RunId runId() {
        return new RunId(PdvFixtures.RUN_ID);
    }

    @Test
    @DisplayName(
            "open prepares the run's mzTab, records the launch before PDV starts, and the window"
                    + " becomes ready on its port")
    void openRecordsThenLaunches() throws IOException, InterruptedException {
        PdvWindowStatus ready = openReady();

        FakePdvWindows.Window window = fake.windows().get(0);
        List<String> argv = window.command().argv();
        Path mzTab = run.root().resolve("pdv/mztab/psms.mztab");
        List<ProvenanceEvent> launched = launches();
        Map<String, String> payload = launched.get(0).payload();
        assertAll(
                () -> assertEquals(1, launched.size(), "one launch, one event"),
                () ->
                        assertEquals(
                                List.of(String.join(" ", argv)),
                                recordedAtStart,
                                "the event held exactly this command when PDV was started"),
                () -> assertEquals("denovo-gui", payload.get(PdvLaunches.KIND)),
                () -> assertEquals(PdvFixtures.RUN_ID, payload.get(PdvLaunches.RUN_ID)),
                () -> assertEquals(mzTab.toString(), payload.get("launch.mztab")),
                () ->
                        assertEquals(
                                PdvFixtures.digest(mzTab, "SHA-256"),
                                payload.get("launch.mztab-sha256")),
                () -> assertEquals("3", payload.get("launch.psms")),
                () ->
                        assertEquals(
                                List.of(
                                        project.resolve("jre/bin/java").toString(),
                                        "-Duser.home=" + run.root().resolve("pdv/window/home"),
                                        "-jar",
                                        pdv.jar().toString(),
                                        "denovo-gui",
                                        "--mztab",
                                        mzTab.toString(),
                                        "--spectrum",
                                        run.root().resolve("pdv/mztab/ms_run-1.mgf").toString(),
                                        "--port",
                                        Integer.toString(window.port())),
                                argv),
                () ->
                        assertEquals(
                                run.root().resolve("pdv/window"),
                                window.command().workingDirectory()),
                () -> assertEquals(":7", window.command().environment().get("DISPLAY")),
                () ->
                        assertEquals(
                                new PdvWindowStatus(
                                        runId(),
                                        PdvWindowStatus.Phase.READY,
                                        OptionalInt.of(window.port()),
                                        "PDV is ready on loopback port "
                                                + window.port()
                                                + ", showing "
                                                + mzTab
                                                + "."),
                                ready),
                () -> assertEquals(Optional.of(ready), windows.status(runId())),
                () -> assertTrue(Files.isDirectory(run.root().resolve("pdv/window/home"))));
    }

    @Test
    @DisplayName(
            "a Results selection becomes its own row's spectra_ref: a target and a decoy sharing a"
                    + " PSMId are told apart")
    void selectionsUseTheIndex()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        fake.knowing("ms_run[1]:index=0", "ms_run[1]:index=1", "ms_run[1]:index=2");
        openReady();
        String shared = psmBase + "_101_2_1";

        PdvSelection target = await(windows.select(runId(), false, shared));
        PdvSelection second = await(windows.select(runId(), false, psmBase + "_101_2_2"));
        PdvSelection decoy = await(windows.select(runId(), true, shared));

        assertAll(
                () ->
                        assertEquals(
                                new PdvSelection(
                                        runId(),
                                        Optional.of("ms_run[1]:index=0"),
                                        PdvSelection.Kind.SELECTED,
                                        OptionalInt.of(200),
                                        "PDV answered /select with 200 selected."),
                                target),
                () -> assertEquals(Optional.of("ms_run[1]:index=1"), second.spectraRef()),
                () -> assertEquals(Optional.of("ms_run[1]:index=2"), decoy.spectraRef()),
                () -> assertEquals(PdvSelection.Kind.SELECTED, decoy.kind()),
                () ->
                        assertEquals(
                                List.of(
                                        "ms_run[1]:index=0",
                                        "ms_run[1]:index=1",
                                        "ms_run[1]:index=2"),
                                fake.selected()));
    }

    @Test
    @DisplayName("PDV's other answers are reported as what they are")
    void pdvsAnswersAreReported()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        fake.answeringSelect(
                ref ->
                        ref.endsWith("=0")
                                ? new PdvControlAnswer(404, "unknown ref")
                                : new PdvControlAnswer(503, "not ready"));
        openReady();

        PdvSelection unknown = await(windows.select(runId(), false, psmBase + "_101_2_1"));
        PdvSelection notReady = await(windows.selectReference(runId(), "ms_run[1]:index=9"));

        assertAll(
                () -> assertEquals(PdvSelection.Kind.UNKNOWN_TO_PDV, unknown.kind()),
                () -> assertEquals(OptionalInt.of(404), unknown.status()),
                () -> assertEquals("PDV answered /select with 404 unknown ref.", unknown.message()),
                () -> assertEquals(PdvSelection.Kind.REFUSED_BY_PDV, notReady.kind()),
                () -> assertEquals(OptionalInt.of(503), notReady.status()));
    }

    @Test
    @DisplayName("a PSM the mzTab does not hold is reported, and nothing is sent to PDV")
    void aPsmNotInTheMzTab()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        openReady();

        PdvSelection missing = await(windows.select(runId(), true, psmBase + "_101_2_2"));

        assertAll(
                () ->
                        assertEquals(
                                new PdvSelection(
                                        runId(),
                                        Optional.empty(),
                                        PdvSelection.Kind.NOT_IN_MZTAB,
                                        OptionalInt.empty(),
                                        "The decoy PSM "
                                                + psmBase
                                                + "_101_2_2 is not a row of the mzTab PDV was"
                                                + " given, so it cannot be shown there."),
                                missing),
                () ->
                        assertEquals(
                                "The target PSM other is not a row of the mzTab PDV was given, so"
                                        + " it cannot be shown there.",
                                await(windows.select(runId(), false, "other")).message()),
                () -> assertEquals(List.of(), fake.selected()));
    }

    @Test
    @DisplayName("a burst of selections reaches PDV as the last one; the rest are superseded")
    void aBurstIsDebounced()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        fake.knowing("ms_run[1]:index=1");
        openReady();

        CompletableFuture<PdvSelection> first =
                windows.select(runId(), false, psmBase + "_101_2_1");
        CompletableFuture<PdvSelection> last = windows.select(runId(), false, psmBase + "_101_2_2");

        assertAll(
                () -> assertEquals(PdvSelection.Kind.SUPERSEDED, await(first).kind()),
                () -> assertEquals(OptionalInt.empty(), await(first).status()),
                () -> assertEquals(PdvSelection.Kind.SELECTED, await(last).kind()),
                () -> assertEquals(List.of("ms_run[1]:index=1"), fake.selected()));
    }

    @Test
    @DisplayName("a run that is not open in PDV is told so, and nothing is sent")
    void notOpen() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        PdvSelection selection = await(windows.select(runId(), false, psmBase + "_101_2_1"));
        PdvSelection byRef = await(windows.selectReference(runId(), "ms_run[1]:index=0"));
        assertAll(
                () ->
                        assertEquals(
                                new PdvSelection(
                                        runId(),
                                        Optional.empty(),
                                        PdvSelection.Kind.NOT_OPEN,
                                        OptionalInt.empty(),
                                        "Run pdv-test-run is not open in PDV."),
                                selection),
                () -> assertEquals(Optional.of("ms_run[1]:index=0"), byRef.spectraRef()),
                () -> assertEquals(PdvSelection.Kind.NOT_OPEN, byRef.kind()),
                () -> assertEquals(Optional.empty(), windows.status(runId())),
                () -> assertEquals(Optional.empty(), windows.close(runId())),
                () -> assertEquals(List.of(), fake.requests()));
    }

    @Test
    @DisplayName("an executing run is refused: no mzTab, no event, no PDV")
    void anExecutingRunIsRefused() throws IOException {
        executing.add(runId());

        IOException refused =
                assertThrows(IOException.class, () -> windows.open(request(), statuses::add));

        assertAll(
                () ->
                        assertEquals(
                                "run pdv-test-run is executing: its results are not final and the"
                                        + " workflow engine holds its event log, where every PDV"
                                        + " launch is recorded. Open it in PDV when the run has"
                                        + " ended.",
                                refused.getMessage()),
                () -> assertEquals(List.of(), fake.windows()),
                () -> assertEquals(List.of(), launches()),
                () -> assertFalse(Files.exists(run.root().resolve("pdv"))));
    }

    @Test
    @DisplayName(
            "close sends /shutdown, ends the process, and the window is closed; a selection after"
                    + " it is not sent")
    void closeEndsTheWindow()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        openReady();
        FakePdvWindows.Window window = fake.windows().get(0);

        PdvWindowShutdown shutdown = windows.close(runId()).orElseThrow();
        PdvWindowStatus closed = next();

        assertAll(
                () -> assertEquals("answered 200 bye", shutdown.controlServer()),
                () -> assertEquals(OptionalInt.of(FakePdvWindows.TERMINATED), shutdown.exitCode()),
                () -> assertFalse(window.isAlive()),
                () -> assertTrue(window.cancelled()),
                () -> assertEquals(PdvWindowStatus.Phase.CLOSED, closed.phase()),
                () -> assertEquals(Optional.empty(), windows.status(runId())),
                () ->
                        assertEquals(
                                PdvSelection.Kind.NOT_OPEN,
                                await(windows.select(runId(), false, psmBase + "_101_2_1"))
                                        .kind()));
    }

    @Test
    @DisplayName(
            "opening a run again closes its window before the mzTab is prepared, and launches a"
                    + " new one")
    void reopeningClosesFirst() throws IOException, InterruptedException {
        openReady();
        FakePdvWindows.Window first = fake.windows().get(0);

        windows.open(request(), statuses::add);

        assertAll(
                () -> assertTrue(first.cancelled()),
                () -> assertFalse(first.isAlive()),
                () -> assertEquals(PdvWindowStatus.Phase.CLOSED, next().phase()),
                () -> assertEquals(PdvWindowStatus.Phase.STARTING, next().phase()),
                () -> assertEquals(2, fake.windows().size()),
                () -> assertEquals(2, launches().size(), "two launches, two events"),
                () -> assertEquals(PdvWindowStatus.Phase.READY, next().phase()));
    }

    @Test
    @DisplayName(
            "a run whose sources changed is closed in PDV before its mzTab is written again, which"
                    + " deletes the directory PDV reads")
    void theWindowIsClosedBeforeTheMzTabIsMadeAgain() throws IOException, InterruptedException {
        List<Boolean> aliveWhileHashing = new CopyOnWriteArrayList<>();
        StreamingHashService real = new StreamingHashService();
        windows.close();
        windows =
                windows(
                        fake,
                        file -> {
                            if (!fake.windows().isEmpty()) {
                                aliveWhileHashing.add(fake.windows().get(0).isAlive());
                            }
                            return real.hash(file);
                        });
        openReady();
        aliveWhileHashing.clear();
        table(
                RunResultFiles.table(run, TableKind.TARGET_PSMS),
                row(1, "2.6", "0.001", "R.PEPTIDEK.A"),
                row(2, "1.5", "0.01", "K.KEDITPEP.-"));

        windows.open(request(), statuses::add);

        long written =
                ProvenanceEventLogReader.recover(run.eventLogFile()).events().stream()
                        .filter(event -> event.type() == ProvenanceEventType.MZTAB_WRITTEN)
                        .count();
        assertAll(
                () ->
                        assertEquals(
                                2, written, "the changed source made the mzTab be written again"),
                () -> assertFalse(aliveWhileHashing.isEmpty(), "the sources were hashed"),
                () ->
                        assertFalse(
                                aliveWhileHashing.contains(Boolean.TRUE),
                                "the first PDV was running while the mzTab was prepared again"));
    }

    @Test
    @DisplayName("closing every window ends every PDV and refuses later opens")
    void closeAll() throws IOException, InterruptedException {
        openReady();

        windows.close();

        assertAll(
                () -> assertFalse(fake.windows().get(0).isAlive()),
                () -> assertEquals(Optional.empty(), windows.status(runId())),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () -> windows.open(request(), statuses::add)));
    }

    @Test
    @DisplayName("a launch the recording path refuses leaves nothing open")
    void aRefusedLaunchOpensNothing() throws IOException {
        Path other = PdvFixtures.jar(project.resolve("tools/other/PDV-2.7.0.jar"), "PDV", "2.6.0");
        pdv = new PdvInstallation(ToolVersion.parse("2.7.0"), other);

        IOException refused =
                assertThrows(IOException.class, () -> windows.open(request(), statuses::add));

        assertAll(
                () ->
                        assertTrue(
                                refused.getMessage().endsWith("; nothing was launched"),
                                refused.getMessage()),
                () -> assertEquals(List.of(), fake.windows()),
                () -> assertEquals(Optional.empty(), windows.status(runId())),
                () -> assertEquals(List.of(), launches()));
    }

    @Test
    @DisplayName("a window whose PDV fails to start is reported failed, with PDV's own words")
    void aFailedWindowIsReported() throws IOException, InterruptedException {
        fake.loadingFor(Integer.MAX_VALUE);
        windows.open(request(), statuses::add);
        next();

        fake.windows().get(0).die(1, "java.lang.ArrayIndexOutOfBoundsException");
        PdvWindowStatus failed = next();

        assertAll(
                () -> assertEquals(PdvWindowStatus.Phase.FAILED, failed.phase()),
                () ->
                        assertTrue(
                                failed.message()
                                                .startsWith(
                                                        "PDV exited with status 1 before it"
                                                                + " was ready")
                                        && failed.message()
                                                .endsWith(
                                                        "java.lang.ArrayIndexOutOfBoundsException"),
                                failed.message()),
                () -> assertEquals(Optional.of(failed), windows.status(runId())));
    }

    @Test
    @DisplayName("a status and a selection require what they say")
    void values() {
        assertAll(
                () -> assertTrue(PdvWindowStatus.Phase.CLOSED.isFinal()),
                () -> assertTrue(PdvWindowStatus.Phase.FAILED.isFinal()),
                () -> assertFalse(PdvWindowStatus.Phase.READY.isFinal()),
                () -> assertFalse(PdvWindowStatus.Phase.STARTING.isFinal()),
                () ->
                        assertEquals(
                                "a selection has PDV's status exactly when PDV answered: NOT_OPEN",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvSelection(
                                                                runId(),
                                                                Optional.empty(),
                                                                PdvSelection.Kind.NOT_OPEN,
                                                                OptionalInt.of(200),
                                                                "m"))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PdvSelection(
                                                runId(),
                                                Optional.empty(),
                                                PdvSelection.Kind.UNKNOWN_TO_PDV,
                                                OptionalInt.empty(),
                                                "m")),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PdvSelection(
                                                runId(),
                                                Optional.empty(),
                                                PdvSelection.Kind.REFUSED_BY_PDV,
                                                OptionalInt.empty(),
                                                "m")),
                () -> assertEquals(run.root().resolve("pdv/window"), request().windowDirectory()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> request().displayEnvironment().put("X", "y")));
    }
}
