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

package org.cometgui.tools.pdv.fidelity;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.pdv.PdvMzTabExporter;
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
 * The real PDV 2.7.0, unmodified, under the project-local Xvfb, accepts the K562 export: it reaches
 * {@code /ready} (which it answers only once every row is imported), selects by its own {@code
 * spectra_ref} every row of the spectrum that carries the most PSMs, decoy rows and rows of the
 * second {@code ms_run}, and does not know a reference no row has. The references are chosen by the
 * suite's own reading of the mzTab and sources, never from the exporter's report.
 */
@EnabledOnOs(OS.LINUX)
class RealPdvFidelityTest {

    private static final Duration READY_DEADLINE = Duration.ofMinutes(3);
    private static final Duration PDV_LIFETIME = Duration.ofMinutes(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static Path work;
    private static FidelityCheck check;
    private static ProjectXvfb display;
    private static RunningProcess pdv;
    private static CountDownLatch exited;
    private static volatile int port;

    @BeforeAll
    static void exportAndLaunch() throws IOException, InterruptedException {
        work = FidelityFiles.workDirectory("fidelity-pdv-");
        Path directory = Files.createDirectories(work.resolve("pdv")).resolve("mztab");
        PdvMzTabExporter.export(K562FidelityTest.request(directory));
        check =
                new FidelityCheck(
                        K562Run.percolatorTargets(),
                        K562Run.percolatorDecoys(),
                        K562FidelityTest.sources(),
                        directory.resolve("psms.mztab"));
        List<String> spectra = new ArrayList<>();
        for (int run = 1; check.mzTab().metadata().containsKey(location(run)); run++) {
            spectra.add(
                    Path.of(URI.create(check.mzTab().metadata().get(location(run)))).toString());
        }
        assertEquals(2, spectra.size(), "two ms_runs, one per K562 file");
        display = ProjectXvfb.start(work.resolve("xvfb"));
        port = freePort();
        Path home = Files.createDirectories(work.resolve("home"));
        Map<String, String> environment = new LinkedHashMap<>(display.clientEnvironment());
        environment.put("LANG", "C.UTF-8");
        environment.put("HOME", home.toString());
        ToolCommand command =
                new ToolCommand(
                        List.of(
                                timeout().toString(),
                                "--kill-after=10s",
                                Long.toString(PDV_LIFETIME.toSeconds()),
                                JavaRuntime.ofThisApplication().launcher().toString(),
                                "-Duser.home=" + home,
                                "-jar",
                                RealPdv.jar().toString(),
                                "denovo-gui",
                                "--mztab",
                                directory.resolve("psms.mztab").toString(),
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

    private static String location(int run) {
        return "ms_run[" + run + "]-location";
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
            FidelityFiles.delete(work);
        }
    }

    /** The host's {@code timeout}, so PDV cannot outlive a halted test JVM. */
    private static Path timeout() {
        String path = Objects.requireNonNullElse(System.getenv("PATH"), "");
        for (String directory : path.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(directory, "timeout");
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("no timeout on PATH: PDV is not launched unbounded");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Polls {@code /ready} on a schedule until 200, PDV's exit, or the deadline. */
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
                            // not listening yet
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
                throw new AssertionError("PDV did not import within " + READY_DEADLINE + LOG, late);
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

    private static String select(String ref) throws IOException, InterruptedException {
        HttpResponse<String> response =
                get("/select?ref=" + URLEncoder.encode(ref, StandardCharsets.UTF_8));
        return response.statusCode() + " " + response.body().strip();
    }

    @Test
    @DisplayName(
            "PDV selects every row of the most-shared spectrum, decoys and the second ms_run, each"
                    + " by its own reference")
    void sampledRowsAreSelected() throws IOException, InterruptedException {
        Map<String, List<Map<String, String>>> bySpectrum = new TreeMap<>();
        List<Map<String, String>> decoys = new ArrayList<>();
        List<Map<String, String>> secondRun = new ArrayList<>();
        for (Map<String, String> row : check.mzTab().rows()) {
            FidelityCheck.Joined joined =
                    check.joined()
                            .get(
                                    PercolatorTable.Row.key(
                                            "1".equals(row.get(FidelityCheck.DECOY)),
                                            row.get(FidelityCheck.PSM_ID)));
            bySpectrum
                    .computeIfAbsent(
                            joined.source().base() + "/" + joined.spectrum().id(),
                            ignored -> new ArrayList<>())
                    .add(row);
            if (joined.row().decoy()) {
                decoys.add(row);
            }
            if (joined.source().base().equals(K562FidelityTest.BASE_4)) {
                secondRun.add(row);
            }
        }
        List<Map<String, String>> mostShared = List.of();
        for (List<Map<String, String>> rows : bySpectrum.values()) {
            if (rows.size() > mostShared.size()) {
                mostShared = rows;
            }
        }
        List<Map<String, String>> sample = new ArrayList<>(mostShared);
        sample.addAll(decoys.subList(0, 5));
        sample.add(decoys.get(decoys.size() - 1));
        sample.addAll(secondRun.subList(0, 3));
        sample.add(secondRun.get(secondRun.size() - 1));
        sample.add(check.mzTab().rows().get(0));
        sample.add(check.mzTab().rows().get(check.mzTab().rows().size() - 1));
        List<String> expected = new ArrayList<>();
        List<String> answered = new ArrayList<>();
        for (Map<String, String> row : sample) {
            String ref = row.get("spectra_ref");
            expected.add(ref + " -> 200 selected");
            answered.add(ref + " -> " + select(ref));
        }
        int shared = mostShared.size();
        assertAll(
                () -> assertTrue(shared >= 5, shared + " PSMs share the most-shared spectrum"),
                () -> assertEquals(expected, answered));
    }

    @Test
    @DisplayName("a reference no row has is unknown to PDV: past an ms_run's end, or a third run")
    void unknownReferences() throws IOException, InterruptedException {
        long firstRunRows =
                check.mzTab().rows().stream()
                        .filter(row -> row.get("spectra_ref").startsWith("ms_run[1]:"))
                        .count();
        String past = "ms_run[1]:index=" + firstRunRows;
        String third = "ms_run[3]:index=0";
        assertAll(
                () -> assertEquals("404 unknown ref", select(past)),
                () -> assertEquals("404 unknown ref", select(third)),
                () ->
                        assertEquals(
                                "200 selected", select("ms_run[1]:index=" + (firstRunRows - 1))));
    }
}
