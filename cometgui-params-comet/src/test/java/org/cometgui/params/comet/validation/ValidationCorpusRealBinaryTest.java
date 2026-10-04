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

package org.cometgui.params.comet.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometManifest;
import org.cometgui.params.comet.fixtures.UpstreamMirror;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The validation corpus replayed against the REAL pinned Comet binaries, 2026.03.0 and 2026.02.2:
 * for every case and both releases, {@code comet -P<case>.params -N<out> <spectra>} is run through
 * {@link ProcessService}, and its exit code and its Warning and Error lines (beyond the release's
 * control run) must be the recorded ones; the validator, given the same parameters for that
 * release, must give the recorded findings; and the two must meet the agreement criterion.
 *
 * <p>The search reads local inputs that are never committed ({@code D-006}): the Crux K562 run
 * {@code scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML} and the UniProt human proteome {@code
 * scratch/fixture/UP000005640_9606.fasta}, each held to its SHA-256 here. Refill them with {@code
 * python3 scripts/feasibility/fetch_ephemeral_input.py}, which fetches by checksum. The mzML is
 * served with CRLF line endings, which break its index; the test strips them into a private copy
 * and holds the copy to its own SHA-256 too. The database is the proteome's first 1000 records,
 * written to a private copy and held to its SHA-256, so each search takes about 0.2 s; the corpus
 * scans five spectra (one case, 1500). Missing or changed inputs FAIL this test; nothing is
 * skipped.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class ValidationCorpusRealBinaryTest {

    private static final ValidationCorpus CORPUS = ValidationCorpus.load();

    private static final List<ToolVersion> RELEASES = List.of(Models.COMET_2026_03_0, Models.COMET);

    private static final Map<String, Run> RUNS = new ConcurrentHashMap<>();

    private static final Map<ToolVersion, Run> CONTROLS = new ConcurrentHashMap<>();

    private static Path spectra;

    private static Path database;

    /**
     * What one search did.
     *
     * @param exit the exit code
     * @param lines its Warning and Error lines, standard output first, stripped
     * @param database the database path it was given
     * @param model the validator's model of the same parameters
     * @param refused the edits the release's codec refused
     */
    record Run(
            int exit,
            List<String> lines,
            String database,
            CometParameters model,
            List<String> refused) {}

    static Stream<ValidationCorpus.Case> cases() {
        return CORPUS.cases().stream();
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] pinned(Path root, String file, String sha256)
            throws IOException, NoSuchAlgorithmException {
        Path path = root.resolve(file);
        assertTrue(
                Files.isRegularFile(path),
                file
                        + " does not exist. It is a local, gitignored input (D-006) the validation"
                        + " corpus searches; refill it with python3"
                        + " scripts/feasibility/fetch_ephemeral_input.py, which fetches by"
                        + " checksum.");
        byte[] bytes = Files.readAllBytes(path);
        assertEquals(sha256, sha256(bytes), file + " is not the pinned file");
        return bytes;
    }

    @BeforeAll
    static void runEverything(@TempDir Path scratch)
            throws IOException,
                    NoSuchAlgorithmException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Path root = UpstreamMirror.repositoryRoot();
        ValidationCorpus.Inputs inputs = CORPUS.inputs();
        byte[] raw = pinned(root, inputs.spectra(), inputs.spectraSha256());
        String lf = new String(raw, StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
        byte[] repaired = lf.getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(inputs.lineFeedSha256(), sha256(repaired), "the CRLF-repaired mzML");
        spectra = scratch.resolve("20100614_Velos1_TaGe_SA_K562_3.mzML");
        Files.write(spectra, repaired);
        byte[] proteome = pinned(root, inputs.database(), inputs.databaseSha256());
        String text = new String(proteome, StandardCharsets.ISO_8859_1);
        int end = -1;
        for (int record = 0; record < inputs.records(); record++) {
            end = text.indexOf("\n>", end + 1);
            assertTrue(end > 0, "the proteome has fewer than " + inputs.records() + " records");
        }
        byte[] subset = (text.substring(0, end) + "\n").getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(inputs.subsetSha256(), sha256(subset), "the proteome's first records");
        database = scratch.resolve("subset.fasta");
        Files.write(database, subset);

        Map<ToolVersion, Path> binaries = new LinkedHashMap<>();
        for (ToolVersion release : RELEASES) {
            CometManifest.Row row =
                    CometManifest.cometRows(CometManifest.repositoryManifest()).stream()
                            .filter(r -> r.version().equals(release.text()))
                            .filter(CometManifest.Row::isLinuxX8664)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("no linux/x86-64 " + release));
            binaries.put(
                    release,
                    UpstreamMirror.stage(
                                    root,
                                    row,
                                    scratch.resolve("bin-" + release.text()).resolve("comet"))
                            .toAbsolutePath());
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> pending = new ArrayList<>();
            for (ToolVersion release : RELEASES) {
                pending.add(
                        pool.submit(
                                () -> {
                                    CONTROLS.put(
                                            release,
                                            run(
                                                    binaries.get(release),
                                                    release,
                                                    scratch.resolve("control-" + release.text()),
                                                    List.of()));
                                    return null;
                                }));
                for (ValidationCorpus.Case kase : CORPUS.cases()) {
                    pending.add(
                            pool.submit(
                                    () -> {
                                        RUNS.put(
                                                key(kase, release),
                                                run(
                                                        binaries.get(release),
                                                        release,
                                                        scratch.resolve(kase.id())
                                                                .resolve(release.text()),
                                                        kase.edits()));
                                        return null;
                                    }));
                }
            }
            for (Future<?> run : pending) {
                run.get(10, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static String key(ValidationCorpus.Case kase, ToolVersion release) {
        return kase.id() + "@" + release.text();
    }

    /**
     * Runs one search in its own directory. A case that names an {@code .idx} database gets its own
     * copy of the database, because Comet builds the index beside it.
     */
    private static Run run(
            Path binary, ToolVersion release, Path directory, List<ValidationCorpus.Edit> edits)
            throws IOException, InterruptedException {
        Files.createDirectories(directory);
        Path db = database;
        if (edits.stream().anyMatch(edit -> edit.value().contains(".idx"))) {
            db = Files.copy(database, directory.resolve("subset.fasta"));
        }
        String path = db.toAbsolutePath().toString();
        List<ValidationCorpus.Edit> base = CORPUS.base().stream().map(e -> e.at(path)).toList();
        List<ValidationCorpus.Edit> own = edits.stream().map(e -> e.at(path)).toList();
        List<ValidationCorpus.Edit> all = new ArrayList<>(base);
        all.addAll(own);
        Path params = directory.resolve("case.params");
        Files.writeString(params, ValidationCorpus.file(release, all), StandardCharsets.UTF_8);
        ToolCommand command =
                new ToolCommand(
                        List.of(
                                binary.toString(),
                                "-P" + params.toAbsolutePath(),
                                "-N" + directory.resolve("out").toAbsolutePath(),
                                spectra.toAbsolutePath().toString()),
                        directory.toAbsolutePath(),
                        Map.of());
        Collector collector = new Collector();
        RunningProcess process = new ProcessService(Clock.systemUTC()).start(command, collector);
        if (!collector.exited.await(120, TimeUnit.SECONDS)) {
            process.requestCancellation();
            throw new AssertionError(command.displayString() + " did not finish in 120 s");
        }
        List<String> lines = new ArrayList<>();
        for (List<String> stream : List.of(collector.standardOutput, collector.standardError)) {
            synchronized (stream) {
                for (String line : stream) {
                    if (line.contains("Warning") || line.contains("Error")) {
                        lines.add(line.strip());
                    }
                }
            }
        }
        List<String> refused = new ArrayList<>();
        CometParameters model = ValidationCorpus.model(release, base, own, refused);
        return new Run(collector.exitCode.get(), lines, path, model, refused);
    }

    private static final class Collector implements ProcessListener {

        private final List<String> standardOutput = Collections.synchronizedList(new ArrayList<>());
        private final List<String> standardError = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);
        private final CountDownLatch exited = new CountDownLatch(1);

        @Override
        public void onStandardOutput(String line) {
            standardOutput.add(line);
        }

        @Override
        public void onStandardError(String line) {
            standardError.add(line);
        }

        @Override
        public void onExit(int code) {
            exitCode.set(code);
            exited.countDown();
        }
    }

    @Test
    @DisplayName("each release's control run exits 0 with exactly its recorded lines")
    void controls() {
        for (ValidationCorpus.Control control : CORPUS.controls()) {
            Run run = CONTROLS.get(control.version());
            assertEquals(0, run.exit(), control.version().text());
            assertEquals(control.lines(), run.lines(), control.version().text());
        }
    }

    @ParameterizedTest
    @MethodSource("cases")
    @DisplayName("both real binaries give the recorded verdict, and the validator agrees")
    void replay(ValidationCorpus.Case kase) {
        for (ToolVersion release : RELEASES) {
            ValidationCorpus.Verdict verdict = kase.verdict(release);
            Run run = RUNS.get(key(kase, release));
            String where = kase.id() + ", Comet " + release.text();
            List<String> control =
                    CORPUS.controls().stream()
                            .filter(c -> c.version().equals(release))
                            .findFirst()
                            .orElseThrow()
                            .lines();
            List<String> beyond = new ArrayList<>(run.lines());
            if (run.exit() == 0) {
                for (String line : control) {
                    assertTrue(beyond.remove(line), where + ": the control line is missing");
                }
            } else {
                beyond.removeAll(control);
            }
            List<String> expected =
                    verdict.lines().stream()
                            .map(line -> line.replace(ValidationCorpus.DATABASE, run.database()))
                            .toList();
            assertEquals(verdict.exit(), run.exit(), where + ": exit code; lines " + run.lines());
            assertEquals(expected, beyond, where + ": Warning and Error lines");
            ValidationReport report = CometValidator.standard().validate(run.model());
            assertEquals(
                    verdict.findings(),
                    ValidationCorpusTest.findings(report),
                    where + ": " + report);
            assertEquals(verdict.builtInCode(), !run.refused().isEmpty(), where);
            ValidationCorpus.assertAgreement(kase, verdict);
            assertEquals(
                    ValidationCorpus.of(run.exit(), beyond),
                    ValidationCorpus.of(verdict.exit(), verdict.lines()),
                    where);
        }
    }
}
