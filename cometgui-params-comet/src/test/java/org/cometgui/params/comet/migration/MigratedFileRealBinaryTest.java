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

package org.cometgui.params.comet.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.CometManifest;
import org.cometgui.params.comet.fixtures.UpstreamMirror;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;
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
 * The REAL pinned Comet 2026.03.0 binary accepts the migrated canonical files: Comet 2026.02.2's
 * real {@code -q} and {@code -p} files and Comet 2024.01.0's, each migrated to 2026.03.0 and
 * written by the canonical writer.
 *
 * <p>This test runs <strong>no production class of this module</strong>. It reads the migrated
 * files' bytes from {@code fixtures/comet-migrated/2026.03.0/}, held to their {@code SHA256SUMS};
 * {@link MigrationTo202603Test.Written} proves that migration and the writer produce exactly those
 * bytes. A test that migrated and wrote here would be mapped by PIT's coverage to every migration,
 * parser and writer mutant and would re-run its searches for each (the params gate's control 9
 * scores a timeout as not killed).
 *
 * <p>Two runs per file, each against Comet 2026.03.0's own {@code -q} file as the control:
 *
 * <ul>
 *   <li><strong>Parameter load</strong>: {@code comet -P<file> missing.mzML}. Comet reads the whole
 *       file before it looks at the input, so a transcript ending at {@code Error - input file
 *       "missing.mzML" not found.} with no other Warning or Error line shows the reader accepted
 *       it. The file is run exactly as written.
 *   <li><strong>A real short search</strong>: {@code comet -P<file> -N<out> <spectra>}, with five
 *       explicit text edits: four of the validation corpus's base edits -- {@code database_name}
 *       the first 1000 records of the UniProt human proteome, {@code spectral_library_name} empty,
 *       {@code scan_range = 11188 11192} and {@code num_threads = 4} -- and {@code output_txtfile =
 *       1}, so that the results can be compared with the control's. Every migrated file holds
 *       {@code /some/path/db.fasta} and {@code /some/path/speclib.file}, the placeholders Comet's
 *       own {@code -q} writes and migration carries (2024.01.0 has no library parameter; migration
 *       adds 2026.03.0's default, the same placeholder), and a search with either placeholder stops
 *       before it starts. Choosing the database and the library is the workflow's job, not
 *       migration's. The search must exit 0 with <strong>no Warning or Error line</strong> -- in
 *       particular none about {@code index_search_type} -- and give the control's results.
 * </ul>
 *
 * <p>Then the same search with the one migrated line put back as it was in 2026.02.2, {@code
 * index_search_type = 1}, must add exactly Comet 2026.03.0's "is ignored" warning: what migration
 * spared every search.
 *
 * <p>The spectra and the proteome are local inputs that are never committed ({@code D-006}), read
 * from {@code scratch/fixture/} and held to the SHA-256s the validation corpus records; refill them
 * with {@code python3 scripts/feasibility/fetch_ephemeral_input.py}. Missing or changed inputs FAIL
 * this test; nothing is skipped.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class MigratedFileRealBinaryTest {

    private static final String RELEASE = CometFixtures.COMET_2026_03_0;

    private static final String WRITTEN = "/fixtures/comet-migrated/2026.03.0/";

    private static final List<String> FILES =
            List.of(
                    "from-2026.02.2-q.params",
                    "from-2026.02.2-p.params",
                    "from-2024.01.0-q.params",
                    "from-2024.01.0-p.params");

    private static final String MISSING_INPUT = "missing.mzML";

    private static final String REACHED_INPUTS =
            "Error - input file \"" + MISSING_INPUT + "\" not found.";

    private static Path scratch;

    private static Path binary;

    private static Path spectra;

    private static Path database;

    private static Run controlLoad;

    private static Run controlSearch;

    /**
     * What one run did.
     *
     * @param exit the exit code
     * @param lines its Warning and Error lines, standard output first, stripped
     * @param results the body of its {@code .txt} output (every line but the first, which names the
     *     run), empty when it wrote none
     */
    record Run(int exit, List<String> lines, String results) {}

    static Stream<String> files() {
        return FILES.stream();
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static JsonValue.JsonObject object(JsonValue.JsonObject parent, String name) {
        return (JsonValue.JsonObject) parent.member(name).orElseThrow();
    }

    private static String text(JsonValue.JsonObject parent, String name) {
        return ((JsonValue.JsonString) parent.member(name).orElseThrow()).value();
    }

    private static byte[] pinned(Path root, String file, String sha256)
            throws IOException, NoSuchAlgorithmException {
        Path path = root.resolve(file);
        assertTrue(
                Files.isRegularFile(path),
                file
                        + " does not exist. It is a local, gitignored input (D-006); refill it with"
                        + " python3 scripts/feasibility/fetch_ephemeral_input.py, which fetches by"
                        + " checksum.");
        byte[] bytes = Files.readAllBytes(path);
        assertEquals(sha256, sha256(bytes), file + " is not the pinned file");
        return bytes;
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = MigratedFileRealBinaryTest.class.getResourceAsStream(name)) {
            assertTrue(in != null, "the class path holds no " + name);
            return in.readAllBytes();
        }
    }

    /** A migrated file's bytes, held to the {@code SHA256SUMS} beside it. */
    private static byte[] migrated(String file) throws IOException, NoSuchAlgorithmException {
        byte[] bytes = resource(WRITTEN + file);
        String sums = new String(resource(WRITTEN + "SHA256SUMS"), StandardCharsets.UTF_8);
        assertTrue(sums.contains(sha256(bytes) + "  " + file + "\n"), file + " against " + sums);
        return bytes;
    }

    @BeforeAll
    static void stage(@TempDir Path directory)
            throws IOException, NoSuchAlgorithmException, InterruptedException {
        scratch = directory;
        Path root = UpstreamMirror.repositoryRoot();
        JsonValue.JsonObject corpus =
                (JsonValue.JsonObject)
                        JsonReader.parse(
                                new String(
                                        resource("/fixtures/comet-validation/corpus.json"),
                                        StandardCharsets.UTF_8));
        JsonValue.JsonObject inputs = object(corpus, "spectra");
        byte[] raw = pinned(root, text(inputs, "file"), text(inputs, "sha256"));
        String lf = new String(raw, StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
        byte[] repaired = lf.getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(text(inputs, "lineFeedSha256"), sha256(repaired), "the CRLF-repaired mzML");
        spectra = scratch.resolve("20100614_Velos1_TaGe_SA_K562_3.mzML");
        Files.write(spectra, repaired);
        JsonValue.JsonObject db = object(corpus, "database");
        byte[] proteome = pinned(root, text(db, "file"), text(db, "sha256"));
        String all = new String(proteome, StandardCharsets.ISO_8859_1);
        int records = (int) ((JsonValue.JsonNumber) db.member("records").orElseThrow()).value();
        int end = -1;
        for (int record = 0; record < records; record++) {
            end = all.indexOf("\n>", end + 1);
            assertTrue(end > 0, "the proteome has fewer than " + records + " records");
        }
        byte[] subset = (all.substring(0, end) + "\n").getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(text(db, "subsetSha256"), sha256(subset), "the proteome's first records");
        database = scratch.resolve("subset.fasta");
        Files.write(database, subset);

        CometManifest.Row row =
                CometManifest.cometRows(CometManifest.repositoryManifest()).stream()
                        .filter(r -> r.version().equals(RELEASE))
                        .filter(CometManifest.Row::isLinuxX8664)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no linux/x86-64 " + RELEASE));
        binary =
                UpstreamMirror.stage(root, row, scratch.resolve("bin").resolve("comet"))
                        .toAbsolutePath();

        byte[] own =
                CometFixtures.bytes(
                        RELEASE, CometFixtures.LINUX_X86_64, CometFixtures.Mode.COMPLETE);
        controlLoad = load("control", own);
        controlSearch = search("control", own, Map.of());
    }

    /** {@code comet -P<file> missing.mzML}, the file exactly as given. */
    private static Run load(String label, byte[] params) throws IOException, InterruptedException {
        Path directory =
                Files.createDirectories(scratch.resolve("load-" + label.replace(".params", "")));
        Path file = directory.resolve(label + ".params");
        Files.write(file, params);
        return run(directory, List.of("-P" + file.toAbsolutePath(), MISSING_INPUT));
    }

    /**
     * A five-scan search of the file with the five search edits, and any others given, each
     * replacing the one line that declares the parameter.
     */
    private static Run search(String label, byte[] params, Map<String, String> more)
            throws IOException, InterruptedException {
        Path directory =
                Files.createDirectories(scratch.resolve("search-" + label.replace(".params", "")));
        String text = new String(params, StandardCharsets.UTF_8);
        text = replace(text, "database_name", database.toAbsolutePath().toString());
        text = replace(text, "spectral_library_name", "");
        text = replace(text, "scan_range", "11188 11192");
        text = replace(text, "num_threads", "4");
        text = replace(text, "output_txtfile", "1");
        for (Map.Entry<String, String> edit : more.entrySet()) {
            text = replace(text, edit.getKey(), edit.getValue());
        }
        Path file = directory.resolve(label + ".params");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return run(
                directory,
                List.of(
                        "-P" + file.toAbsolutePath(),
                        "-N" + directory.resolve("out").toAbsolutePath(),
                        spectra.toAbsolutePath().toString()));
    }

    /** The text with the one line declaring {@code name} replaced; its inline comment dropped. */
    private static String replace(String text, String name, String value) {
        Pattern line =
                Pattern.compile("^" + Pattern.quote(name) + " = [^\\n]*$", Pattern.MULTILINE);
        Matcher matcher = line.matcher(text);
        assertTrue(matcher.find(), name + " is not declared");
        int start = matcher.start();
        int stop = matcher.end();
        assertFalse(matcher.find(), name + " is declared twice");
        return text.substring(0, start) + name + " = " + value + text.substring(stop);
    }

    private static Run run(Path directory, List<String> arguments)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(binary.toString());
        command.addAll(arguments);
        ToolCommand tool = new ToolCommand(command, directory.toAbsolutePath(), Map.of());
        Collector collector = new Collector();
        RunningProcess process = new ProcessService(Clock.systemUTC()).start(tool, collector);
        if (!collector.exited.await(120, TimeUnit.SECONDS)) {
            process.requestCancellation();
            throw new AssertionError(tool.displayString() + " did not finish in 120 s");
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
        String results = "";
        try (Stream<Path> written = Files.list(directory)) {
            List<Path> text = written.filter(p -> p.toString().endsWith(".txt")).toList();
            if (!text.isEmpty()) {
                List<String> body = Files.readAllLines(text.get(0), StandardCharsets.UTF_8);
                results = String.join("\n", body.subList(1, body.size()));
            }
        }
        Run done = new Run(collector.exitCode.get(), lines, results);
        System.out.println(
                "[comet "
                        + RELEASE
                        + "] "
                        + String.join(" ", arguments)
                        + " -> exit "
                        + done.exit()
                        + ", lines "
                        + done.lines()
                        + ", "
                        + (results.isEmpty()
                                ? "no results"
                                : results.lines().count() + " result lines"));
        return done;
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
    @DisplayName(
            "Comet 2026.03.0's own -q file: the reader reaches the input; the search is silent")
    void controls() {
        assertEquals(1, controlLoad.exit());
        assertEquals(List.of(REACHED_INPUTS), controlLoad.lines());
        assertEquals(0, controlSearch.exit());
        assertEquals(List.of(), controlSearch.lines());
        assertFalse(controlSearch.results().isEmpty(), "the control search wrote no results");
    }

    @ParameterizedTest
    @MethodSource("files")
    @DisplayName("each migrated file loads without a word and searches without a warning")
    void accepted(String file) throws IOException, NoSuchAlgorithmException, InterruptedException {
        byte[] bytes = migrated(file);
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("# comet_version 2026.03 rev. 0 (fa08489)\n"), file);
        assertTrue(text.contains("\nindex_search_type = -1 "), file);

        Run load = load(file, bytes);
        assertEquals(1, load.exit(), file);
        assertEquals(List.of(REACHED_INPUTS), load.lines(), file);

        Run search = search(file, bytes, Map.of());
        assertEquals(0, search.exit(), file + ": " + search.lines());
        assertEquals(List.of(), search.lines(), file);
        assertEquals(controlSearch.results(), search.results(), file + ": the control's results");
    }

    @Test
    @DisplayName("the one migrated line put back to 2026.02.2's 1 draws 2026.03.0's warning")
    void unmigratedWarns() throws IOException, NoSuchAlgorithmException, InterruptedException {
        byte[] bytes = migrated(FILES.get(0));
        Run search = search("unmigrated", bytes, Map.of("index_search_type", "1"));
        assertEquals(0, search.exit());
        assertEquals(
                List.of(
                        "Warning - index_search_type = 1 is ignored: \""
                                + database.toAbsolutePath()
                                + "\" is not an .idx file (plain FASTA search). It only selects"
                                + " the index type to auto-build when database_name names an .idx"
                                + " file that does not exist yet."),
                search.lines());
        assertEquals(controlSearch.results(), search.results());
    }
}
