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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.K562Run;
import org.cometgui.tools.testing.ProjectXvfb;
import org.cometgui.tools.testing.RealPdv;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The product's mzTab and MGFs, given unmodified to the real PDV 2.7.0 {@code denovo-gui} under the
 * project-local Xvfb: PDV imports every row ({@code /ready} answers 200 only once its import loop
 * has finished, and an exception in any row stops the loop for ever), every sampled row is
 * selectable by its own {@code spectra_ref} -- rows that share one spectrum included -- and a
 * reference no row has is unknown.
 *
 * <p>The export is the real K562 run, both files, target and decoy, plus a constructed third
 * spectrum file whose two PSMs carry the forms K562 lacks: an N-terminal modification ({@code
 * [+42.010565]-PEPTIDEK}) and a residue with a static and a variable modification ({@code
 * C[+57.021464][+15.994915]}). One PDV launch, polled -- never slept on -- under deadlines.
 * Everything written is K562-derived ({@code D-006}), under this module's build directory, deleted
 * afterwards. The control API proves import and selection; it cannot report what PDV drew (P11-16).
 */
@EnabledOnOs(OS.LINUX)
class PdvMzTabRealPdvTest {

    private static final String BASE_3 = "20100614_Velos1_TaGe_SA_K562_3";
    private static final String BASE_4 = "20100614_Velos1_TaGe_SA_K562_4";
    private static final String MADE = "constructed";

    private static final Duration READY_DEADLINE = Duration.ofMinutes(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private static ProjectXvfb display;
    private static Path work;
    private static PdvMzTabExport export;
    private static RunningProcess pdv;
    private static CountDownLatch exited;
    private static volatile int port;
    private static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private static Map<String, String> refByPsmId;

    @BeforeAll
    static void launchPdvOnTheExport() throws IOException, InterruptedException {
        Path build =
                Path.of(System.getProperty("cometgui.buildDirectory", "target")).toAbsolutePath();
        Files.createDirectories(build);
        work = Files.createTempDirectory(build, "mztab-pdv-");
        display = ProjectXvfb.start(work.resolve("xvfb"));
        Path made = work.resolve("made");
        String nterm = MADE + "_101_2_1";
        String both = MADE + "_101_2_2";
        Path targets =
                appended(
                        K562Run.percolatorTargets(),
                        made.resolve("targets.tsv"),
                        MzTabFixtures.row(
                                nterm,
                                "3.1",
                                "0.0001",
                                "1e-05",
                                "-.n[42.0106]PEPTIDEK.A",
                                "sp|P00001|ONE_INVENTED"),
                        MzTabFixtures.row(
                                both,
                                "2.9",
                                "0.0001",
                                "2e-05",
                                "K.AC[15.9949]DEFGHIK.-",
                                "sp|P00001|ONE_INVENTED"));
        MzTabFixtures.pepXml(
                made.resolve(MADE + ".pep.xml"),
                MADE,
                MzTabFixtures.DECLARATIONS
                        + "  <aminoacid_modification aminoacid=\"C\" massdiff=\"15.994915\""
                        + " mass=\"176.025564\" variable=\"Y\"/>\n"
                        + MzTabFixtures.N_TERMINAL_ACETYL,
                MzTabFixtures.query(
                        MADE,
                        101,
                        2,
                        MzTabFixtures.nativeId(101),
                        "600.5",
                        MzTabFixtures.hit(
                                "PEPTIDEK",
                                "-",
                                "A",
                                MzTabFixtures.modifications("mod_nterm_mass=\"43.018390\"")),
                        MzTabFixtures.hit(
                                "ACDEFGHIK",
                                "K",
                                "-",
                                MzTabFixtures.modifications(
                                        "",
                                        "<mod_aminoacid_mass position=\"2\" mass=\"176.025564\""
                                                + " static=\"57.021464\" variable=\"15.994915\""
                                                + " source=\"param\"/>"))));
        MzTabFixtures.mgf(made.resolve(MADE + ".mgf"), MzTabFixtures.nativeId(101));
        Files.createDirectories(work.resolve("pdv"));
        export =
                PdvMzTabExporter.export(
                        new PdvMzTabRequest(
                                targets,
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
                                                Optional.empty()),
                                        new PdvMzTabSource(
                                                MADE,
                                                made.resolve(MADE + ".mgf"),
                                                SpectrumFileFormat.MGF,
                                                made.resolve(MADE + ".pep.xml"),
                                                Optional.empty())),
                                work.resolve("pdv").resolve("mztab")));
        refByPsmId = new LinkedHashMap<>();
        for (String line : Files.readAllLines(export.mzTab(), StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t", -1);
            if (fields[0].equals("PSM")) {
                refByPsmId.put(fields[22] + "/" + fields[23], fields[16]);
            }
        }
        port = freePort();
        Path home = Files.createDirectories(work.resolve("home"));
        List<String> spectra = new ArrayList<>();
        for (PdvMzTabMgf mgf : export.mgfs()) {
            spectra.add(mgf.file().toString());
        }
        Map<String, String> environment = new LinkedHashMap<>(display.clientEnvironment());
        environment.put("LANG", "C.UTF-8");
        environment.put("HOME", home.toString());
        ToolCommand command =
                new ToolCommand(
                        List.of(
                                JavaRuntime.ofThisApplication().launcher().toString(),
                                "-Duser.home=" + home,
                                "-jar",
                                RealPdv.jar().toString(),
                                "denovo-gui",
                                "--mztab",
                                export.mzTab().toString(),
                                "--spectrum",
                                String.join(",", spectra),
                                "--port",
                                Integer.toString(port)),
                        work,
                        environment);
        exited = new CountDownLatch(1);
        pdv =
                new ProcessService(Clock.systemUTC())
                        .start(
                                command,
                                new ProcessListener() {
                                    @Override
                                    public void onStandardOutput(String line) {
                                        LOG.add(line);
                                    }

                                    @Override
                                    public void onStandardError(String line) {
                                        LOG.add(line);
                                    }

                                    @Override
                                    public void onExit(int exitCode) {
                                        LOG.add("PDV exited with status " + exitCode);
                                        exited.countDown();
                                    }
                                });
        awaitReady();
    }

    private static Path appended(Path table, Path copy, String... rows) throws IOException {
        Files.createDirectories(Objects.requireNonNull(copy.getParent(), "a directory"));
        StringBuilder text = new StringBuilder(Files.readString(table, StandardCharsets.UTF_8));
        for (String row : rows) {
            text.append(row).append('\n');
        }
        return Files.writeString(copy, text.toString(), StandardCharsets.UTF_8);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Polls {@code /ready} on a schedule until it answers 200, PDV exits or the deadline. */
    private static void awaitReady() throws InterruptedException {
        CompletableFuture<String> ready = new CompletableFuture<>();
        ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
        try {
            poller.scheduleWithFixedDelay(
                    () -> {
                        if (exited.getCount() == 0) {
                            ready.completeExceptionally(
                                    new AssertionError("PDV exited before it was ready: " + LOG));
                            return;
                        }
                        try {
                            HttpResponse<String> response = get("/ready");
                            if (response.statusCode() == 200) {
                                ready.complete(response.body());
                            }
                        } catch (IOException notYet) {
                            // the server is not listening yet
                        } catch (InterruptedException stopped) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    0,
                    250,
                    TimeUnit.MILLISECONDS);
            try {
                assertEquals(
                        "ready",
                        ready.get(READY_DEADLINE.toMillis(), TimeUnit.MILLISECONDS).strip());
            } catch (TimeoutException late) {
                throw new AssertionError(
                        "PDV did not import the mzTab within " + READY_DEADLINE + ": " + LOG, late);
            } catch (ExecutionException failed) {
                throw new AssertionError(failed.getCause().getMessage(), failed.getCause());
            }
        } finally {
            poller.shutdownNow();
        }
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> select(String ref)
            throws IOException, InterruptedException {
        return get("/select?ref=" + URLEncoder.encode(ref, StandardCharsets.UTF_8));
    }

    @AfterAll
    static void stopPdv() throws IOException, InterruptedException {
        try {
            if (pdv != null) {
                pdv.requestCancellation();
                if (!exited.await(1, TimeUnit.MINUTES)) {
                    throw new AssertionError("PDV did not exit when asked");
                }
            }
        } finally {
            if (display != null) {
                display.close();
            }
            if (work != null && Files.exists(work)) {
                try (Stream<Path> walk = Files.walk(work)) {
                    for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }

    private static String ref(String table, String psmId) {
        String ref = refByPsmId.get(table + "/" + psmId);
        if (ref == null) {
            throw new AssertionError("the export has no row " + psmId + " in table " + table);
        }
        return ref;
    }

    @Test
    @DisplayName(
            "PDV imports every row, and each sampled row -- shared spectra, decoys, both files,"
                    + " the N-terminal and doubly modified forms -- is selected by its own ref")
    void everySampledRowIsSelectable() throws IOException, InterruptedException {
        List<String> sample = new ArrayList<>();
        // Scan 11288's five charge-4 target PSMs share one spectrum (the spike's R12 case).
        for (Map.Entry<String, String> row : refByPsmId.entrySet()) {
            if (row.getKey().startsWith("0/" + BASE_3 + "_11288_4_")) {
                sample.add(row.getValue());
            }
        }
        assertEquals(5, sample.size(), "scan 11288 charge 4 carries five target PSMs");
        sample.add(ref("0", BASE_3 + "_11367_2_1"));
        sample.add(ref("0", BASE_4 + "_10175_5_1"));
        sample.add(ref("1", BASE_4 + "_10599_3_2"));
        sample.add(ref("1", BASE_3 + "_11738_2_5"));
        sample.add(ref("0", MADE + "_101_2_1"));
        sample.add(ref("0", MADE + "_101_2_2"));
        assertEquals(sample.size(), Set.copyOf(sample).size(), "every sampled ref is distinct");
        List<String> answers = new ArrayList<>();
        for (String ref : sample) {
            HttpResponse<String> response = select(ref);
            answers.add(ref + " -> " + response.statusCode() + " " + response.body().strip());
        }
        List<String> expected = new ArrayList<>();
        for (String ref : sample) {
            expected.add(ref + " -> 200 selected");
        }
        assertEquals(expected, answers);
    }

    @Test
    @DisplayName(
            "a reference no row has is unknown to PDV: one past ms_run[1]'s last entry, or a"
                    + " fourth ms_run")
    void anUnknownReferenceIsNotFound() throws IOException, InterruptedException {
        String pastTheEnd = "ms_run[1]:index=" + export.mgfs().get(0).entries();
        HttpResponse<String> past = select(pastTheEnd);
        HttpResponse<String> fourth = select("ms_run[4]:index=0");
        assertAll(
                () -> assertEquals(404, past.statusCode(), past.body()),
                () -> assertEquals("unknown ref", past.body().strip()),
                () -> assertEquals(404, fourth.statusCode(), fourth.body()),
                () -> assertEquals(3, export.mgfs().size()),
                () -> assertEquals(6672, export.psmsWritten()),
                () ->
                        assertTrue(
                                ref("0", MADE + "_101_2_1").equals("ms_run[3]:index=0")
                                        && ref("0", MADE + "_101_2_2").equals("ms_run[3]:index=1"),
                                "the constructed rows are ms_run[3]'s two entries"));
    }
}
