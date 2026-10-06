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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * What the REAL pinned Comet binaries do with an existing index -- the binary half of the index
 * rules, as {@link ValidationCorpusRealBinaryTest} is of the corpus:
 *
 * <ul>
 *   <li><strong>Formats.</strong> Each release builds a fragment-ion index of the {@code D-006}
 *       subset and each release searches both indexes. A release searches an index exactly when the
 *       metadata's {@code indexFormats} for it lists the index's format, and refuses it otherwise
 *       with its own message -- the same test, both releases, so data that made the check
 *       version-blind would fail here.
 *   <li><strong>Contradictions.</strong> Each release builds a peptide index from its own {@code
 *       -q} defaults and searches it with one option changed. For every option the index rules call
 *       a contradiction, the search exits 0 and writes exactly the PIN rows of the unchanged search
 *       -- the index's value was used and the parameter file's silently ignored -- and, where the
 *       same change alters a plain FASTA search, that is shown too, so the option is one that
 *       matters. Comet 2026.03.0 warns about {@code index_search_type} alone; Comet 2026.02.2 warns
 *       about nothing. A narrower mass or length range, by contrast, changes the rows: Comet
 *       applies it, and the rules do not call it a contradiction.
 *   <li><strong>Decoy prefix.</strong> Comet 2026.03.0 labels decoys by the prefix its format-5
 *       index records, whatever the search says; Comet 2026.02.2's format 4 records none, and the
 *       search's prefix is applied.
 * </ul>
 *
 * <p>Like the corpus's binary half, this test runs <strong>no production class of this
 * module</strong>: parameter files are plain text edits of the release's real {@code -q} fixture
 * ({@link ValidationCorpus#file}), and the metadata is read with the provenance module's JSON
 * reader, so PIT never maps a validator mutant to these searches. Inputs, never committed ({@code
 * D-006}), each held to its SHA-256, FAIL the test when missing: the K562 run (an LF copy), the
 * proteome's first 1000 records, and a target-decoy FASTA built here from them. Each index is built
 * as the workflow builds one, with {@code -D} naming a symbolic link inside the index's own
 * directory; Comet's threads are bounded ({@code num_threads = 4}).
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class IndexCompatibilityRealBinaryTest {

    private static final ToolVersion NEWER = ToolVersion.parse("2026.03.0");

    private static final ToolVersion OLDER = ToolVersion.parse("2026.02.2");

    private static final List<ToolVersion> RELEASES = List.of(NEWER, OLDER);

    /** The subset with a reversed DECOY_ record after each record, 60 residues a line. */
    static final String TARGET_DECOY_SHA256 =
            "ff098f19bba637aa33a736a01ad59cf35911292c9fb7598324ed02a7d5b07a99";

    /** The scans searched: enough for 495 PSMs from a peptide index. */
    private static final String SCANS = "11000 11300";

    /**
     * One search option changed, as the index rules see it.
     *
     * @param id the run's name
     * @param name the parameter
     * @param value its value text
     * @param mattersForFasta whether the same change alters a plain FASTA search of these scans
     */
    record Variant(String id, String name, String value, boolean mattersForFasta) {

        @Override
        public String toString() {
            return name + " = " + value;
        }
    }

    /** Every option the index rules call a contradiction, each with a value that contradicts. */
    static final List<Variant> CONTRADICTIONS =
            List.of(
                    new Variant("ds1", "decoy_search", "1", true),
                    new Variant("enzyme", "search_enzyme_number", "3", true),
                    new Variant("enzyme2", "search_enzyme2_number", "3", false),
                    new Variant("termini", "num_enzyme_termini", "1", true),
                    new Variant("missed", "allowed_missed_cleavage", "0", true),
                    new Variant("clip", "clip_nterm_methionine", "1", true),
                    new Variant("parent", "mass_type_parent", "0", true),
                    new Variant("fragment", "mass_type_fragment", "0", true),
                    new Variant("cysteine", "add_C_cysteine", "0.0", true),
                    new Variant("vm1off", "variable_mod01", "0.0 M 0 3 -1 0 0 0.0", true),
                    new Variant("vm2", "variable_mod02", "79.966331 STY 0 3 -1 0 0 0.0", true),
                    new Variant("vm6", "variable_mod06", "79.966331 STY 0 3 -1 0 0 0.0", true),
                    new Variant("require", "require_variable_mod", "1", true),
                    new Variant("maxmods", "max_variable_mods_in_peptide", "1", true),
                    new Variant("masswide", "digest_mass_range", "400.0 6000.0", false),
                    new Variant("lengthwide", "peptide_length_range", "3 50", true),
                    new Variant("type", "index_search_type", "1", false));

    /** What one search did. */
    record Run(int exit, List<String> lines, String pin) {}

    private static final Map<String, Run> RUNS = new ConcurrentHashMap<>();

    private static final Map<ToolVersion, Path> FRAGMENT_INDEX = new ConcurrentHashMap<>();

    private static final Map<ToolVersion, Path> PEPTIDE_INDEX = new ConcurrentHashMap<>();

    private static final Map<ToolVersion, Path> DECOY_INDEX = new ConcurrentHashMap<>();

    static Stream<Variant> contradictions() {
        return CONTRADICTIONS.stream();
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
                        + " does not exist. It is a local, gitignored input (D-006); refill it with"
                        + " python3 scripts/feasibility/fetch_ephemeral_input.py, which fetches by"
                        + " checksum.");
        byte[] bytes = Files.readAllBytes(path);
        assertEquals(sha256, sha256(bytes), file + " is not the pinned file");
        return bytes;
    }

    /** Each record followed by a decoy: DECOY_ before its header, its sequence reversed. */
    static byte[] withReversedDecoys(byte[] fasta) {
        String text = new String(fasta, StandardCharsets.ISO_8859_1);
        StringBuilder out = new StringBuilder();
        for (String chunk : text.split(">", -1)) {
            if (chunk.isEmpty()) {
                continue;
            }
            int newline = chunk.indexOf('\n');
            String header = chunk.substring(0, newline);
            String sequence = chunk.substring(newline + 1).replace("\n", "");
            appendRecord(out, header, sequence);
            appendRecord(out, "DECOY_" + header, new StringBuilder(sequence).reverse().toString());
        }
        return out.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void appendRecord(StringBuilder out, String header, String sequence) {
        out.append('>').append(header).append('\n');
        for (int at = 0; at < sequence.length(); at += 60) {
            out.append(sequence, at, Math.min(sequence.length(), at + 60)).append('\n');
        }
    }

    @BeforeAll
    static void runEverything(@TempDir Path scratch)
            throws IOException,
                    NoSuchAlgorithmException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException {
        Path root = UpstreamMirror.repositoryRoot();
        ValidationCorpus.Inputs inputs = ValidationCorpus.load().inputs();
        byte[] raw = pinned(root, inputs.spectra(), inputs.spectraSha256());
        byte[] repaired =
                new String(raw, StandardCharsets.ISO_8859_1)
                        .replace("\r\n", "\n")
                        .getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(inputs.lineFeedSha256(), sha256(repaired), "the CRLF-repaired mzML");
        Path spectra = scratch.resolve("20100614_Velos1_TaGe_SA_K562_3.mzML");
        Files.write(spectra, repaired);
        String proteome =
                new String(
                        pinned(root, inputs.database(), inputs.databaseSha256()),
                        StandardCharsets.ISO_8859_1);
        int end = -1;
        for (int record = 0; record < inputs.records(); record++) {
            end = proteome.indexOf("\n>", end + 1);
        }
        byte[] subsetBytes =
                (proteome.substring(0, end) + "\n").getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(inputs.subsetSha256(), sha256(subsetBytes), "the proteome's first records");
        Path subset = scratch.resolve("subset.fasta");
        Files.write(subset, subsetBytes);
        byte[] decoyBytes = withReversedDecoys(subsetBytes);
        assertEquals(TARGET_DECOY_SHA256, sha256(decoyBytes), "the target-decoy FASTA");
        Path targetDecoy = scratch.resolve("target-decoy.fasta");
        Files.write(targetDecoy, decoyBytes);

        Map<ToolVersion, Path> binaries = new LinkedHashMap<>();
        for (ToolVersion release : RELEASES) {
            CometManifest.Row row =
                    CometManifest.cometRows(CometManifest.repositoryManifest()).stream()
                            .filter(r -> r.version().equals(release.text()))
                            .filter(CometManifest.Row::isLinuxX8664)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("no linux/x86-64 " + release));
            Path binary =
                    UpstreamMirror.stage(
                                    root,
                                    row,
                                    scratch.resolve("bin-" + release.text()).resolve("comet"))
                            .toAbsolutePath();
            binaries.put(release, binary);
            FRAGMENT_INDEX.put(release, build(binary, release, scratch, "fragment", subset, "-i"));
            PEPTIDE_INDEX.put(release, build(binary, release, scratch, "peptide", subset, "-j"));
            DECOY_INDEX.put(release, build(binary, release, scratch, "decoy", targetDecoy, "-i"));
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> pending = new ArrayList<>();
            for (ToolVersion release : RELEASES) {
                Path binary = binaries.get(release);
                for (ToolVersion builder : RELEASES) {
                    pending.add(
                            pool.submit(
                                    () ->
                                            search(
                                                    "format-" + builder.text(),
                                                    binary,
                                                    release,
                                                    scratch,
                                                    FRAGMENT_INDEX.get(builder),
                                                    spectra,
                                                    List.of())));
                }
                List<List<ValidationCorpus.Edit>> variants = new ArrayList<>();
                variants.add(List.of());
                for (Variant variant : CONTRADICTIONS) {
                    variants.add(
                            List.of(new ValidationCorpus.Edit(variant.name(), variant.value())));
                }
                variants.add(
                        List.of(new ValidationCorpus.Edit("digest_mass_range", "600.0 1500.0")));
                variants.add(List.of(new ValidationCorpus.Edit("peptide_length_range", "5 10")));
                for (List<ValidationCorpus.Edit> edits : variants) {
                    String id =
                            edits.isEmpty()
                                    ? "base"
                                    : edits.get(0).name() + "=" + edits.get(0).value();
                    pending.add(
                            pool.submit(
                                    () ->
                                            search(
                                                    "index-" + id,
                                                    binary,
                                                    release,
                                                    scratch,
                                                    PEPTIDE_INDEX.get(release),
                                                    spectra,
                                                    edits)));
                    pending.add(
                            pool.submit(
                                    () ->
                                            search(
                                                    "fasta-" + id,
                                                    binary,
                                                    release,
                                                    scratch,
                                                    subset,
                                                    spectra,
                                                    edits)));
                }
                for (String prefix : List.of("DECOY_", "REV_")) {
                    pending.add(
                            pool.submit(
                                    () ->
                                            search(
                                                    "prefix-" + prefix,
                                                    binary,
                                                    release,
                                                    scratch,
                                                    DECOY_INDEX.get(release),
                                                    spectra,
                                                    List.of(
                                                            new ValidationCorpus.Edit(
                                                                    "decoy_prefix", prefix)))));
                }
            }
            for (Future<?> run : pending) {
                run.get(10, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<ValidationCorpus.Edit> base(String database) {
        return List.of(
                new ValidationCorpus.Edit("database_name", database),
                new ValidationCorpus.Edit("spectral_library_name", ""),
                new ValidationCorpus.Edit("num_threads", "4"),
                new ValidationCorpus.Edit("output_percolatorfile", "1"),
                new ValidationCorpus.Edit("scan_range", SCANS));
    }

    /** Builds an index in a directory of its own, -D naming a link to the FASTA there. */
    private static Path build(
            Path binary, ToolVersion release, Path scratch, String kind, Path fasta, String flag)
            throws IOException {
        Path directory =
                Files.createDirectories(scratch.resolve("index-" + release.text() + "-" + kind));
        Path link = Files.createSymbolicLink(directory.resolve("db.fasta"), fasta);
        Path params = directory.resolve("comet.params");
        Files.writeString(
                params,
                ValidationCorpus.file(release, base(link.toString())),
                StandardCharsets.UTF_8);
        Outcome outcome =
                execute(
                        new ToolCommand(
                                List.of(binary.toString(), "-P" + params, flag, "-D" + link),
                                directory,
                                Map.of()));
        assertEquals(0, outcome.exit(), release + " " + kind + ": " + outcome.lines());
        Path index = directory.resolve("db.fasta.idx");
        assertTrue(Files.isRegularFile(index), "no index in " + directory);
        return index;
    }

    private static Void search(
            String id,
            Path binary,
            ToolVersion release,
            Path scratch,
            Path database,
            Path spectra,
            List<ValidationCorpus.Edit> edits)
            throws IOException {
        Path directory =
                Files.createDirectories(
                        scratch.resolve("search-" + release.text())
                                .resolve(id.replaceAll("[^A-Za-z0-9._-]", "_")));
        List<ValidationCorpus.Edit> all = new ArrayList<>(base(database.toString()));
        all.addAll(edits);
        Path params = directory.resolve("case.params");
        Files.writeString(params, ValidationCorpus.file(release, all), StandardCharsets.UTF_8);
        Outcome outcome =
                execute(
                        new ToolCommand(
                                List.of(
                                        binary.toString(),
                                        "-P" + params,
                                        "-N" + directory.resolve("out"),
                                        spectra.toString()),
                                directory,
                                Map.of()));
        String pin = "";
        try (Stream<Path> listed = Files.list(directory)) {
            for (Path file : listed.toList()) {
                if (file.getFileName().toString().endsWith(".pin")) {
                    pin =
                            Files.readString(file, StandardCharsets.ISO_8859_1)
                                    .replace(directory.toString(), "<run>");
                }
            }
        }
        List<String> lines = new ArrayList<>();
        for (String line : outcome.lines()) {
            String stripped = line.strip();
            if (stripped.contains("Warning") || stripped.contains("Error")) {
                lines.add(stripped.replace(database.toString(), "<database>"));
            }
        }
        RUNS.put(key(release, id), new Run(outcome.exit(), lines, pin));
        return null;
    }

    private static String key(ToolVersion release, String id) {
        return release.text() + "/" + id;
    }

    private static Run run(ToolVersion release, String id) {
        Run run = RUNS.get(key(release, id));
        assertTrue(run != null, "no run " + key(release, id));
        return run;
    }

    /** What a process did: its exit code and every line of both streams. */
    record Outcome(int exit, List<String> lines) {}

    private static Outcome execute(ToolCommand command) throws IOException {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger exit = new AtomicInteger(Integer.MIN_VALUE);
        CountDownLatch exited = new CountDownLatch(1);
        ProcessListener listener =
                new ProcessListener() {
                    @Override
                    public void onStandardOutput(String line) {
                        lines.add(line);
                    }

                    @Override
                    public void onStandardError(String line) {
                        lines.add(line);
                    }

                    @Override
                    public void onExit(int code) {
                        exit.set(code);
                        exited.countDown();
                    }
                };
        RunningProcess process = new ProcessService(Clock.systemUTC()).start(command, listener);
        try {
            if (!exited.await(300, TimeUnit.SECONDS)) {
                process.requestCancellation();
                throw new AssertionError(command.displayString() + " did not finish in 300 s");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", interrupted);
        }
        synchronized (lines) {
            return new Outcome(exit.get(), List.copyOf(lines));
        }
    }

    /** Each release's indexFormats.readable, read from the bundled metadata as plain JSON. */
    private static Map<String, List<Long>> readableFormats() throws IOException {
        String json;
        try (InputStream in =
                IndexCompatibilityRealBinaryTest.class.getResourceAsStream(
                        "/org/cometgui/params/comet/schema/comet-parameters.json")) {
            assertTrue(in != null, "the class path holds no comet-parameters.json");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, List<Long>> readable = new LinkedHashMap<>();
        JsonValue.JsonObject document = (JsonValue.JsonObject) JsonReader.parse(json);
        for (JsonValue element :
                ((JsonValue.JsonArray) document.member("versions").orElseThrow()).elements()) {
            JsonValue.JsonObject record = (JsonValue.JsonObject) element;
            String version =
                    ((JsonValue.JsonString) record.member("version").orElseThrow()).value();
            JsonValue.JsonObject formats =
                    (JsonValue.JsonObject) record.member("indexFormats").orElseThrow();
            readable.put(
                    version,
                    ((JsonValue.JsonArray) formats.member("readable").orElseThrow())
                            .elements().stream()
                                    .map(number -> ((JsonValue.JsonNumber) number).value())
                                    .toList());
        }
        return readable;
    }

    /** The format number of an index, from its first line. */
    private static long formatOf(Path index) throws IOException {
        String first;
        try (InputStream in = Files.newInputStream(index)) {
            first =
                    new String(in.readNBytes(80), StandardCharsets.ISO_8859_1)
                            .lines()
                            .findFirst()
                            .orElseThrow();
        }
        assertTrue(first.startsWith("Comet index database v"), first);
        return Long.parseLong(
                first.substring("Comet index database v".length(), first.indexOf('.')));
    }

    @Test
    @DisplayName("each release searches exactly the index formats its metadata says it reads")
    void formatsAreTheData() throws IOException {
        Map<String, List<Long>> readable = readableFormats();
        assertEquals(List.of(5L), readable.get("2026.03.0"));
        assertEquals(List.of(4L), readable.get("2026.02.2"));
        assertEquals(5L, formatOf(FRAGMENT_INDEX.get(NEWER)));
        assertEquals(4L, formatOf(FRAGMENT_INDEX.get(OLDER)));
        Map<ToolVersion, String> refusal =
                Map.of(
                        NEWER,
                        "Error - \"<database>\" is not a v5 unified index file (v4 and older are"
                                + " intentionally not read: the protein-list layout changed for"
                                + " protein-terminal variable mods). Rebuild it from the FASTA with"
                                + " -i (FI_DB) or -j (PI_DB).",
                        OLDER,
                        "Error - \"<database>\" is not a v4 unified index file; rebuild it with -i"
                                + " or -j.");
        int searched = 0;
        int refused = 0;
        for (ToolVersion release : RELEASES) {
            for (ToolVersion builder : RELEASES) {
                Run run = run(release, "format-" + builder.text());
                long format = formatOf(FRAGMENT_INDEX.get(builder));
                String where = "Comet " + release.text() + " on a v" + format + " index";
                if (readable.get(release.text()).contains(format)) {
                    assertEquals(0, run.exit(), where + ": " + run.lines());
                    assertFalse(run.pin().isEmpty(), where + " wrote no PIN");
                    searched++;
                } else {
                    assertEquals(1, run.exit(), where + ": " + run.lines());
                    assertTrue(
                            run.lines().contains(refusal.get(release)), where + ": " + run.lines());
                    assertTrue(run.pin().isEmpty(), where + " wrote a PIN");
                    refused++;
                }
            }
        }
        assertEquals(2, searched);
        assertEquals(2, refused);
    }

    /** The lines a release prints for its own -q file whatever the case. */
    private static List<String> control(ToolVersion release) {
        return run(release, "index-base").lines();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contradictions")
    @DisplayName("both releases search the index as built and silently ignore the contradiction")
    void contradictionIgnored(Variant variant) {
        String id = variant.name() + "=" + variant.value();
        for (ToolVersion release : RELEASES) {
            String where = "Comet " + release.text() + ", " + variant;
            Run base = run(release, "index-base");
            Run changed = run(release, "index-" + id);
            assertEquals(0, base.exit(), where + " base: " + base.lines());
            assertEquals(0, changed.exit(), where + ": " + changed.lines());
            assertTrue(base.pin().lines().count() > 400, where + ": the base search found little");
            assertEquals(base.pin(), changed.pin(), where + ": the index's value was not used");
            List<String> beyond = new ArrayList<>(changed.lines());
            beyond.removeAll(control(release));
            List<String> expected =
                    release.equals(NEWER) && variant.name().equals("index_search_type")
                            ? List.of(
                                    "Warning - index_search_type = 1 is ignored: \"<database>\" is"
                                            + " a peptide index and its own IndexSearchType: header"
                                            + " line decides. Delete the file or rebuild it with -i"
                                            + " to change the type.")
                            : List.of();
            assertEquals(expected, beyond, where + ": Warning and Error lines");
            if (variant.mattersForFasta()) {
                Run fastaBase = run(release, "fasta-base");
                Run fastaChanged = run(release, "fasta-" + id);
                assertEquals(0, fastaChanged.exit(), where + " (FASTA): " + fastaChanged.lines());
                assertNotEquals(
                        fastaBase.pin(),
                        fastaChanged.pin(),
                        where + ": the change alters a FASTA search, so it matters");
            }
        }
    }

    @Test
    @DisplayName("a narrower mass or length range is applied to an index search: no contradiction")
    void narrowerRangesApply() {
        for (ToolVersion release : RELEASES) {
            Run base = run(release, "index-base");
            for (String id :
                    List.of("digest_mass_range=600.0 1500.0", "peptide_length_range=5 10")) {
                Run narrower = run(release, "index-" + id);
                assertEquals(0, narrower.exit(), release + " " + id);
                assertTrue(
                        narrower.pin().lines().count() < base.pin().lines().count(),
                        release + " " + id + ": the narrower range was not applied");
            }
        }
    }

    private static long decoyRows(Run run) {
        return run.pin().lines().skip(1).filter(line -> line.split("\t")[1].equals("-1")).count();
    }

    @Test
    @DisplayName("2026.03.0 labels decoys by the index's prefix; 2026.02.2's index records none")
    void decoyPrefix() {
        Run newerOwn = run(NEWER, "prefix-DECOY_");
        Run newerOther = run(NEWER, "prefix-REV_");
        assertEquals(0, newerOther.exit(), newerOther.lines().toString());
        assertTrue(decoyRows(newerOwn) > 0, "the target-decoy index's decoys were found");
        assertEquals(newerOwn.pin(), newerOther.pin(), "decoy_prefix = REV_ was silently ignored");
        Run olderOwn = run(OLDER, "prefix-DECOY_");
        Run olderOther = run(OLDER, "prefix-REV_");
        assertTrue(decoyRows(olderOwn) > 0, "the target-decoy index's decoys were found");
        assertEquals(0, decoyRows(olderOther), "Comet 2026.02.2 applies the search's prefix");
    }
}
