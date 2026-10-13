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

import static org.cometgui.app.config.IndependentJson.array;
import static org.cometgui.app.config.IndependentJson.bool;
import static org.cometgui.app.config.IndependentJson.number;
import static org.cometgui.app.config.IndependentJson.object;
import static org.cometgui.app.config.IndependentJson.string;
import static org.cometgui.app.config.IndependentJson.stringOrNull;
import static org.cometgui.app.config.IndependentJson.strings;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.SecretSweep;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.ui.viewmodel.limelight.LimelightUploaded;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.workflow.provenance.LoadedRun;
import org.cometgui.workflow.provenance.ProvenanceReader;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RecordState;
import org.cometgui.workflow.provenance.WarningSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 13's exit gate on real runs, proved against the files on disk and never against the record
 * itself (design decisions P13-7 and P13-10). The world -- three tools installed by the product's
 * installer into an empty cache, a completed Comet and Percolator run of the two K562 files through
 * the product's run port, its Limelight conversion, a refused and a submitted upload to the
 * loopback fake, and both exports through the read model -- is {@link GateWorld}, built once here.
 *
 * <p><strong>Every expected value comes from the disk or is typed out here.</strong> Digests are
 * this test's own ({@link OwnDigests}); the record is read with this test's own JSON reader ({@link
 * IndependentJson}) and the report with its own reStructuredText reader ({@link IndependentRst});
 * tools are found in the tool cache by walking it and reading each completion marker with the same
 * JSON reader; a displayed command is split by this test's own tokenizer and compared with the
 * recorded argument array element by element.
 *
 * <p><strong>What is not proved here, and why.</strong> Gate item 4's third case -- a run killed
 * before it sealed its record, opened from {@code events.log} alone -- needs an engine that dies
 * mid-run, and neither this module's test support nor the product can make one die honestly; the
 * read model's handling of an unsealed run is unit 3's {@code ProvenanceStatesTest} over a
 * constructed run directory. Gate item 6's environment path: the only variable the product adds to
 * a tool's environment is {@code LANG=C.UTF-8}, which the record shows; no path a user or a test
 * can reach adds another, so no secret can be seeded there (asserted below). Gate item 6's Comet
 * parameter path: a Comet parameter value is written verbatim into the archived {@code
 * comet.params} that {@code AC-PRV-04} requires to be the exact file executed, so a secret typed as
 * one cannot be absent from the run directory by design; the seeded secrets here enter the settings
 * through an applied preset's name instead.
 *
 * <p>Files read outside this module: those {@link GateWorld} reads.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
class RealProvenanceGateTest {

    private static final String COMET_ARTEFACT =
            "https://github.com/UWPR/Comet/releases/download/v2026.03.0/comet.linux.exe"
                    + " sha256:ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed";

    private static final String PERCOLATOR_ARTEFACT =
            "https://github.com/percolator/percolator/releases/download/rel-3-07-01/"
                    + "percolator-noxml-ubuntu-portable.zip"
                    + " sha256:4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";

    private static final List<String> COMET_CAPABILITIES =
            List.of("COMPLETE_PARAMS_QUERY", "PEPXML_OUTPUT", "PIN_OUTPUT");

    private static final List<String> PERCOLATOR_CAPABILITIES =
            List.of(
                    "DECOY_OUTPUT",
                    "MAX_ITERATIONS_OPTION",
                    "NO_ANALYTICS_OPTION",
                    "PEPTIDE_TSV_OUTPUT",
                    "PSM_TSV_OUTPUT",
                    "SEED_OPTION",
                    "TEST_FDR_OPTION",
                    "THREAD_OPTION",
                    "TRAIN_FDR_OPTION",
                    "WEIGHTS_OUTPUT",
                    "XML_DECOY_OUTPUT",
                    "XML_OUTPUT");

    /**
     * Every file the completed run's record lists, as direction, role and file name, in record
     * order: each step's declared inputs and outputs -- the archived parameters, the two spectra
     * and the database; each Comet invocation's pepXML and PIN; those four again as the merge's
     * inputs; the merged PIN out and in; the Percolator settings; Percolator's six outputs, and the
     * same six again as the parse step's inputs.
     */
    private static final List<String> RECORDED_FILES =
            List.of(
                    "output comet-params comet.params",
                    "input spectrum k562_3.mzML",
                    "input spectrum k562_4.mzML",
                    "input fasta subset.fasta",
                    "input comet-params comet.params",
                    "output pepxml k562_3.pep.xml",
                    "output pin k562_3.pin",
                    "output pepxml k562_4.pep.xml",
                    "output pin k562_4.pin",
                    "input pepxml k562_3.pep.xml",
                    "input pin k562_3.pin",
                    "input pepxml k562_4.pep.xml",
                    "input pin k562_4.pin",
                    "output merged-pin merged.pin",
                    "input merged-pin merged.pin",
                    "input percolator-settings percolator-settings.json",
                    "output percolator-psms psms.tsv",
                    "output percolator-peptides peptides.tsv",
                    "output percolator-decoy-psms decoy-psms.tsv",
                    "output percolator-decoy-peptides decoy-peptides.tsv",
                    "output percolator-weights weights.txt",
                    "output percolator-pout-xml pout.xml",
                    "input percolator-psms psms.tsv",
                    "input percolator-peptides peptides.tsv",
                    "input percolator-decoy-psms decoy-psms.tsv",
                    "input percolator-decoy-peptides decoy-peptides.tsv",
                    "input percolator-weights weights.txt",
                    "input percolator-pout-xml pout.xml");

    /** What the completed run wrote under outputs/ before anything ran after it. */
    private static final List<String> OUTPUTS_AT_SEAL =
            List.of(
                    "outputs/comet/k562_3.pep.xml",
                    "outputs/comet/k562_3.pin",
                    "outputs/comet/k562_4.pep.xml",
                    "outputs/comet/k562_4.pin",
                    "outputs/percolator/decoy-peptides.tsv",
                    "outputs/percolator/decoy-psms.tsv",
                    "outputs/percolator/peptides.tsv",
                    "outputs/percolator/pout.xml",
                    "outputs/percolator/psms.tsv",
                    "outputs/percolator/weights.txt");

    private static final Pattern MD5 = Pattern.compile("[0-9a-f]{32}");

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    @TempDir private static Path scratch;

    private static GateWorld world;

    private static long builtInMillis;

    @BeforeAll
    static void build()
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunNotStartedException {
        long started = System.nanoTime();
        world = GateWorld.build(scratch);
        builtInMillis = (System.nanoTime() - started) / 1_000_000;
        System.out.println("[gate] the shared world was built in " + builtInMillis + " ms");
    }

    @AfterAll
    static void close() throws IOException {
        if (world != null) {
            world.session().close();
        }
    }

    // ------------------------------------------------------------------------------- helpers --

    private static Map<String, Object> recordOf(Path run) throws IOException {
        return object(
                IndependentJson.parse(
                        Files.readString(
                                run.resolve("provenance").resolve("provenance.json"),
                                StandardCharsets.UTF_8)));
    }

    private static List<Map<String, Object>> objects(Object array) {
        List<Map<String, Object>> objects = new ArrayList<>();
        for (Object element : array(array)) {
            objects.add(object(element));
        }
        return objects;
    }

    private static List<Map<String, Object>> toolsNamed(Map<String, Object> record, String name) {
        return objects(record.get("tools")).stream()
                .filter(tool -> string(tool, "name").equals(name))
                .toList();
    }

    private static String fileName(String path) {
        return String.valueOf(Path.of(path).getFileName());
    }

    /** Every completion marker in the tool cache, found by walking it, with its directory. */
    private static Map<Path, Map<String, Object>> markers() throws IOException {
        Map<Path, Map<String, Object>> markers = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(world.cacheRoot())) {
            for (Path file :
                    walk.filter(path -> path.endsWith(".cometgui-install.json"))
                            .sorted()
                            .toList()) {
                markers.put(
                        file.getParent(),
                        object(
                                IndependentJson.parse(
                                        Files.readString(file, StandardCharsets.UTF_8))));
            }
        }
        return markers;
    }

    /** The marker whose install holds an executable, or a failure naming the path. */
    private static Map.Entry<Path, Map<String, Object>> markerHolding(Path executable)
            throws IOException {
        for (Map.Entry<Path, Map<String, Object>> marker : markers().entrySet()) {
            if (marker.getKey()
                    .resolve(string(marker.getValue(), "executablePath"))
                    .equals(executable)) {
                return marker;
            }
        }
        throw new AssertionError(
                executable
                        + " is the executable of no install in the tool cache "
                        + world.cacheRoot());
    }

    /** The marker's record of one installed file. */
    private static Map<String, Object> markerFile(Map<String, Object> marker, String path) {
        for (Map<String, Object> file : objects(marker.get("files"))) {
            if (string(file, "path").equals(path)) {
                return file;
            }
        }
        throw new AssertionError("the completion marker records no file " + path);
    }

    /**
     * Splits a displayed command -- a bracketed, comma-separated list of double-quoted arguments,
     * with backslash escapes -- into its arguments, by this test's own reading of that form.
     */
    static List<String> tokens(String displayed) {
        assertTrue(
                displayed.startsWith("[\"") && displayed.endsWith("\"]"),
                "a displayed command is a bracketed list of quoted arguments: " + displayed);
        List<String> arguments = new ArrayList<>();
        int at = 1;
        while (true) {
            assertEquals('"', displayed.charAt(at), "an argument opens with a quote: " + displayed);
            at++;
            StringBuilder argument = new StringBuilder();
            while (displayed.charAt(at) != '"') {
                char c = displayed.charAt(at++);
                if (c == '\\') {
                    char escape = displayed.charAt(at++);
                    switch (escape) {
                        case 'n' -> argument.append('\n');
                        case 'r' -> argument.append('\r');
                        case 't' -> argument.append('\t');
                        case 'u' -> {
                            argument.append(
                                    (char) Integer.parseInt(displayed.substring(at, at + 4), 16));
                            at += 4;
                        }
                        default -> argument.append(escape);
                    }
                } else {
                    argument.append(c);
                }
            }
            arguments.add(argument.toString());
            at++;
            if (displayed.charAt(at) == ']') {
                assertEquals(displayed.length() - 1, at, "nothing after the list: " + displayed);
                return arguments;
            }
            assertEquals(", ", displayed.substring(at, at + 2), "a separator: " + displayed);
            at += 2;
        }
    }

    private ProvenanceReader reader() {
        return new ProvenanceReader(
                world.secrets()::redactor, new CachingHashService(new StreamingHashService()));
    }

    // ------------------------------------------------------------------------------- item 1 --

    @Nested
    @DisplayName("gate 1: every listed file recomputed from disk, and every output listed")
    class ListedFiles {

        @Test
        @DisplayName(
                "every input and output the record lists has both digests, and this test's own"
                        + " MD5, SHA-256 and size of the file on disk equal them")
        void everyListedFileMatchesTheDisk() throws IOException {
            List<Map<String, Object>> files = objects(recordOf(world.completed()).get("files"));
            List<String> census = new ArrayList<>();
            List<Runnable> checks = new ArrayList<>();
            for (Map<String, Object> file : files) {
                String path = string(file, "path");
                census.add(
                        string(file, "direction")
                                + " "
                                + string(file, "role")
                                + " "
                                + fileName(path));
                Path onDisk = Path.of(path);
                assertTrue(Files.isRegularFile(onDisk), path + " is listed and not on disk");
                OwnDigests own = OwnDigests.of(onDisk);
                checks.add(
                        () ->
                                assertAll(
                                        path,
                                        () ->
                                                assertTrue(
                                                        MD5.matcher(string(file, "md5")).matches(),
                                                        path + " has an MD5"),
                                        () ->
                                                assertTrue(
                                                        SHA256.matcher(string(file, "sha256"))
                                                                .matches(),
                                                        path + " has a SHA-256"),
                                        () ->
                                                assertEquals(
                                                        own.md5(),
                                                        string(file, "md5"),
                                                        path + ": MD5 recomputed from disk"),
                                        () ->
                                                assertEquals(
                                                        own.sha256(),
                                                        string(file, "sha256"),
                                                        path + ": SHA-256 recomputed from disk"),
                                        () ->
                                                assertEquals(
                                                        own.size(),
                                                        number(file, "sizeBytes"),
                                                        path + ": size"),
                                        () ->
                                                assertEquals(
                                                        Files.size(onDisk),
                                                        number(file, "sizeBytes"),
                                                        path + ": Files.size"),
                                        () ->
                                                assertEquals(
                                                        "completed",
                                                        string(file, "status"),
                                                        path + ": status")));
            }
            assertEquals(RECORDED_FILES, census, "the files the record lists");
            assertEquals(28, checks.size(), "files recomputed");
            checks.forEach(Runnable::run);
        }

        @Test
        @DisplayName("every archived log the record lists is recomputed from disk too")
        void everyListedLogMatchesTheDisk() throws IOException {
            int checked = 0;
            for (Map<String, Object> tool : objects(recordOf(world.completed()).get("tools"))) {
                Map<String, Object> execution = object(tool.get("execution"));
                for (String stream : List.of("stdout", "stderr")) {
                    Map<String, Object> log = object(execution.get(stream));
                    String path = string(log, "path");
                    OwnDigests own = OwnDigests.of(Path.of(path));
                    assertAll(
                            path,
                            () -> assertEquals(own.md5(), string(log, "md5"), path + ": MD5"),
                            () ->
                                    assertEquals(
                                            own.sha256(),
                                            string(log, "sha256"),
                                            path + ": SHA-256"));
                    checked++;
                }
            }
            assertEquals(6, checked, "two streams of each of three invocations");
        }

        @Test
        @DisplayName(
                "every regular file the run wrote under outputs/, enumerated from disk, is listed;"
                        + " what was written after the seal is named by its own event")
        void everyOutputOnDiskIsListed() throws IOException {
            Map<String, Object> record = recordOf(world.completed());
            Set<String> listed = new HashSet<>();
            for (Map<String, Object> file : objects(record.get("files"))) {
                listed.add(string(file, "path"));
            }
            List<String> atSeal = new ArrayList<>();
            for (Path file : world.outputsWhenSealed()) {
                atSeal.add(world.completed().relativize(file).toString());
                assertTrue(
                        listed.contains(file.toString()),
                        file + " was written by the run and the record omits it");
            }
            assertEquals(OUTPUTS_AT_SEAL, atSeal, "the run's outputs, from disk, when it ended");

            Map<String, String> convertedBySha = new TreeMap<>();
            for (String line :
                    Files.readAllLines(
                            world.completed().resolve("provenance").resolve("events.log"),
                            StandardCharsets.UTF_8)) {
                Map<String, Object> event = object(IndependentJson.parse(line));
                if (string(event, "type").equals("limelight.converted")) {
                    Map<String, Object> payload = object(event.get("payload"));
                    convertedBySha.put(
                            string(payload, "output.path"), string(payload, "output.sha256"));
                }
            }
            List<Path> now = GateWorld.regularFiles(world.completed().resolve("outputs"));
            int afterSeal = 0;
            for (Path file : now) {
                if (listed.contains(file.toString())) {
                    continue;
                }
                assertTrue(
                        convertedBySha.containsKey(file.toString()),
                        file + " is under outputs/, the record omits it and no event names it");
                assertEquals(
                        OwnDigests.of(file).sha256(),
                        convertedBySha.get(file.toString()),
                        file + ": the SHA-256 its limelight.converted event recorded");
                afterSeal++;
            }
            assertEquals(11, now.size(), "ten outputs of the run and one conversion: " + now);
            assertEquals(1, afterSeal, "the one conversion");
        }
    }

    // ------------------------------------------------------------------------------- item 2 --

    @Nested
    @DisplayName("gate 2: every tool's version, path and digests against the tool cache")
    class Tools {

        private void assertToolMatchesItsInstall(
                String name,
                int records,
                String version,
                String releaseTag,
                String artefact,
                List<String> capabilities)
                throws IOException {
            List<Map<String, Object>> tools = toolsNamed(recordOf(world.completed()), name);
            assertEquals(records, tools.size(), name + " records");
            Path cache = world.cacheRoot().resolve("tools");
            for (Map<String, Object> tool : tools) {
                Path executable = Path.of(string(tool, "executablePath"));
                Map.Entry<Path, Map<String, Object>> install = markerHolding(executable);
                Map<String, Object> marker = install.getValue();
                Map<String, Object> installed =
                        markerFile(marker, string(marker, "executablePath"));
                OwnDigests own = OwnDigests.of(executable);
                List<String> markerCapabilities = new ArrayList<>(strings(marker, "capabilities"));
                markerCapabilities.sort(null);
                assertAll(
                        name + " at " + executable,
                        () -> assertFalse(string(tool, "version").isBlank(), name + " version"),
                        () ->
                                assertEquals(
                                        string(marker, "version"),
                                        string(tool, "version"),
                                        name + ": the version its install recorded"),
                        () -> assertEquals(version, string(tool, "version"), name + " version"),
                        () ->
                                assertTrue(
                                        executable.startsWith(cache),
                                        executable + " is inside the tool cache " + cache),
                        () -> assertTrue(Files.isRegularFile(executable), executable + " exists"),
                        () ->
                                assertEquals(
                                        own.md5(),
                                        string(tool, "md5"),
                                        name + ": MD5, this test's own"),
                        () ->
                                assertEquals(
                                        own.sha256(),
                                        string(tool, "sha256"),
                                        name + ": SHA-256, this test's own"),
                        () ->
                                assertEquals(
                                        string(installed, "md5"),
                                        string(tool, "md5"),
                                        name + ": MD5 the install recorded"),
                        () ->
                                assertEquals(
                                        string(installed, "sha256"),
                                        string(tool, "sha256"),
                                        name + ": SHA-256 the install recorded"),
                        () -> assertTrue(bool(tool, "managed"), name + " is managed"),
                        () ->
                                assertEquals(
                                        string(marker, "artefactUrl")
                                                + " sha256:"
                                                + string(marker, "artefactSha256"),
                                        stringOrNull(tool, "artefactIdentity"),
                                        name + ": the artefact its install came from"),
                        () ->
                                assertEquals(
                                        artefact,
                                        stringOrNull(tool, "artefactIdentity"),
                                        name + " artefact"),
                        () ->
                                assertEquals(
                                        markerCapabilities,
                                        strings(tool, "capabilities"),
                                        name + ": the capabilities its install probed"),
                        () ->
                                assertEquals(
                                        capabilities,
                                        strings(tool, "capabilities"),
                                        name + " capabilities"),
                        () ->
                                assertEquals(
                                        releaseTag,
                                        stringOrNull(tool, "releaseTag"),
                                        name + " release tag"));
            }
        }

        @Test
        @DisplayName("Comet: two records, each matching its install in the tool cache")
        void comet() throws IOException {
            assertToolMatchesItsInstall(
                    "comet", 2, "2026.03.0", "v2026.03.0", COMET_ARTEFACT, COMET_CAPABILITIES);
        }

        @Test
        @DisplayName("Percolator: one record, matching its install in the tool cache")
        void percolator() throws IOException {
            assertToolMatchesItsInstall(
                    "percolator", 1, "3.07.1", null, PERCOLATOR_ARTEFACT, PERCOLATOR_CAPABILITIES);
        }

        @Test
        @DisplayName(
                "the Limelight converter, recorded in events.log after the seal, matches its"
                        + " install in the tool cache")
        void converter() throws IOException {
            List<Map<String, Object>> converted = new ArrayList<>();
            for (String line :
                    Files.readAllLines(
                            world.completed().resolve("provenance").resolve("events.log"),
                            StandardCharsets.UTF_8)) {
                Map<String, Object> event = object(IndependentJson.parse(line));
                if (string(event, "type").equals("limelight.converted")) {
                    converted.add(object(event.get("payload")));
                }
            }
            assertEquals(1, converted.size(), "one conversion");
            Map<String, Object> payload = converted.get(0);
            Path jar = Path.of(string(payload, "converter.jar.path"));
            Map<String, Object> marker = markerHolding(jar).getValue();
            Map<String, Object> installed = markerFile(marker, string(marker, "executablePath"));
            OwnDigests own = OwnDigests.of(jar);
            assertAll(
                    "the converter at " + jar,
                    () -> assertTrue(jar.startsWith(world.cacheRoot().resolve("tools")), jar + ""),
                    () -> assertEquals("2.8.1", string(payload, "tool.version")),
                    () -> assertEquals(string(marker, "version"), string(payload, "tool.version")),
                    () -> assertEquals(own.md5(), string(payload, "converter.jar.md5")),
                    () -> assertEquals(own.sha256(), string(payload, "converter.jar.sha256")),
                    () -> assertEquals(string(installed, "md5"), own.md5(), "the install's MD5"),
                    () ->
                            assertEquals(
                                    string(installed, "sha256"),
                                    own.sha256(),
                                    "the install's SHA-256"));
        }

        @Test
        @DisplayName("the walk of the tool cache found exactly the three installs")
        void threeInstalls() throws IOException {
            List<String> found = new ArrayList<>();
            for (Map<String, Object> marker : markers().values()) {
                found.add(string(marker, "tool") + " " + string(marker, "version"));
            }
            found.sort(null);
            assertEquals(
                    List.of("comet 2026.03.0", "limelight-converter 2.8.1", "percolator 3.07.1"),
                    found);
        }
    }

    // ------------------------------------------------------------------------------- item 3 --

    @Nested
    @DisplayName("gate 3: every argument array present, one Comet per spectrum file, displayable")
    class Commands {

        @Test
        @DisplayName(
                "Comet ran once per spectrum file, each naming exactly that input and its own -N"
                        + " base")
        void onePerSpectrumFile() throws IOException {
            Map<String, Object> record = recordOf(world.completed());
            Set<String> spectra = new HashSet<>();
            for (Path spectrum : world.spectra()) {
                spectra.add(spectrum.toString());
            }
            Set<String> named = new HashSet<>();
            Set<String> bases = new HashSet<>();
            List<Map<String, Object>> comets = toolsNamed(record, "comet");
            assertEquals(2, comets.size(), "two spectrum files, two Comet invocations");
            for (Map<String, Object> comet : comets) {
                List<String> argv = strings(object(comet.get("execution")), "argv");
                List<String> inputs = argv.stream().filter(spectra::contains).toList();
                List<String> base =
                        argv.stream().filter(argument -> argument.startsWith("-N")).toList();
                assertAll(
                        "the Comet invocation " + argv,
                        () -> assertFalse(argv.isEmpty(), "an argument array"),
                        () ->
                                assertEquals(
                                        string(comet, "executablePath"),
                                        argv.get(0),
                                        "the executable first"),
                        () -> assertEquals(1, inputs.size(), "exactly one input: " + argv),
                        () -> assertEquals(1, base.size(), "exactly one -N: " + argv),
                        () ->
                                assertEquals(
                                        "-P" + world.completed().resolve("parameters/comet.params"),
                                        argv.get(1),
                                        "the archived parameter file"));
                String input = inputs.get(0);
                String stem = fileName(input).replace(".mzML", "");
                assertEquals(
                        "-N" + world.completed().resolve("outputs/comet").resolve(stem),
                        base.get(0),
                        "its own base, beside its own outputs");
                assertTrue(named.add(input), input + " searched twice");
                assertTrue(bases.add(base.get(0)), base.get(0) + " used twice");
            }
            assertEquals(spectra, named, "every spectrum file searched");
        }

        @Test
        @DisplayName("Percolator's argument array names the merged PIN")
        void percolatorReadsTheMergedPin() throws IOException {
            List<Map<String, Object>> percolators =
                    toolsNamed(recordOf(world.completed()), "percolator");
            assertEquals(1, percolators.size());
            List<String> argv = strings(object(percolators.get(0).get("execution")), "argv");
            Path merged = world.completed().resolve("inputs/pin/merged.pin");
            assertTrue(Files.isRegularFile(merged), merged + " exists");
            assertEquals(merged.toString(), argv.get(argv.size() - 1), "the merged PIN: " + argv);
            assertEquals(
                    1,
                    argv.stream().filter(merged.toString()::equals).count(),
                    "named once: " + argv);
        }

        @Test
        @DisplayName(
                "the read model shows each invocation's command as exactly its recorded argument"
                        + " array, element by element")
        void displayedIsRecorded() throws IOException {
            List<Map<String, Object>> tools = objects(recordOf(world.completed()).get("tools"));
            ProvenanceView view = world.loaded().view();
            assertEquals(tools.size(), view.tools().size(), "a row per invocation");
            assertEquals(
                    tools.size(), view.timeline().executions().size(), "a stage per invocation");
            for (int index = 0; index < tools.size(); index++) {
                List<String> argv = strings(object(tools.get(index).get("execution")), "argv");
                String shown = view.tools().get(index).command();
                String staged = view.timeline().executions().get(index).command();
                assertEquals(argv, tokens(shown), "the Tools part, invocation " + (index + 1));
                assertEquals(argv, tokens(staged), "the timeline, invocation " + (index + 1));
            }
        }
    }

    // ------------------------------------------------------------------------------- item 4 --

    @Nested
    @DisplayName("gate 4: a failed and a cancelled real run open and show what happened")
    class FailedAndCancelled {

        @Test
        @DisplayName(
                "a damaged spectrum file Comet aborts on: the run is failed, its stage shows the"
                        + " exit status, and its partial outputs are marked partial")
        void failedRun()
                throws IOException,
                        InterruptedException,
                        ExecutionException,
                        TimeoutException,
                        RunNotStartedException {
            Path damaged = world.root().resolve("inputs/damaged.mzML");
            byte[] whole = Files.readAllBytes(world.spectra().get(1));
            Files.write(damaged, java.util.Arrays.copyOf(whole, 2000));
            GateWorld.Ended ended =
                    world.run(
                            GateWorld.searchModel(world.subset(), "4", ValueOrigin.USER),
                            List.of(),
                            List.of(world.spectra().get(0), damaged),
                            Optional.empty());
            assertEquals(AttemptOutcome.FAILED, ended.result().outcome());
            assertOpensAndShows(ended.directory(), "failed", "damaged", 134);
        }

        @Test
        @DisplayName(
                "a search cancelled while Comet is searching: the run is cancelled, its stage shows"
                        + " it, and the outputs Comet had begun are marked partial")
        void cancelledRun()
                throws IOException,
                        InterruptedException,
                        ExecutionException,
                        TimeoutException,
                        RunNotStartedException {
            Path proteome = RealSearch.proteome(world.root().resolve("inputs/proteome.fasta"));
            CompletableFuture<String> searching = world.launches().lineContaining("Load spectra:");
            Consumer<ActiveRun> cancelWhenSearching =
                    active -> {
                        try {
                            searching.get(GateWorld.BOUND_MINUTES, TimeUnit.MINUTES);
                        } catch (InterruptedException
                                | ExecutionException
                                | TimeoutException neverSearched) {
                            throw new AssertionError("Comet never began searching", neverSearched);
                        }
                        active.cancel();
                    };
            GateWorld.Ended ended =
                    world.run(
                            GateWorld.searchModel(proteome, "1", ValueOrigin.USER),
                            List.of(),
                            List.of(world.spectra().get(0)),
                            Optional.of(cancelWhenSearching));
            assertEquals(AttemptOutcome.CANCELLED, ended.result().outcome());
            assertOpensAndShows(ended.directory(), "cancelled", "k562_3", null);
        }

        /**
         * The run's record opens through the read model, sealed, with the status; the Comet stage
         * of the named input has that status and a non-zero exit (the given one when known); the
         * outputs of every Comet stage that did not complete -- that one's at least -- and no other
         * file are marked partial in the record, in the view and in the warnings; and this test's
         * own digests of each partial file equal the record's.
         */
        private void assertOpensAndShows(Path run, String status, String stem, Integer exitCode)
                throws IOException {
            Map<String, Object> record = recordOf(run);
            LoadedRun loaded = reader().load(run);
            ProvenanceView view = loaded.view();
            Map<String, Object> stage = null;
            for (Map<String, Object> comet : toolsNamed(record, "comet")) {
                List<String> argv = strings(object(comet.get("execution")), "argv");
                if (argv.get(argv.size() - 1).endsWith(stem + ".mzML")) {
                    stage = object(comet.get("execution"));
                }
            }
            assertTrue(stage != null, "a Comet record for " + stem);
            Map<String, Object> execution = stage;
            List<String> partial = new ArrayList<>();
            for (Map<String, Object> file : objects(record.get("files"))) {
                if (string(file, "status").equals("partial")) {
                    String path = string(file, "path");
                    partial.add(
                            string(file, "direction")
                                    + " "
                                    + string(file, "role")
                                    + " "
                                    + fileName(path));
                    OwnDigests own = OwnDigests.of(Path.of(path));
                    assertEquals(own.sha256(), string(file, "sha256"), path + ": SHA-256");
                    assertEquals(own.md5(), string(file, "md5"), path + ": MD5");
                }
            }
            List<String> shownPartial =
                    view.files().stream()
                            .filter(ProvenanceView.File::partial)
                            .map(
                                    file ->
                                            file.direction()
                                                    + " "
                                                    + file.role()
                                                    + " "
                                                    + fileName(file.path()))
                            .toList();
            List<String> warned =
                    view.warnings().stream()
                            .filter(warning -> warning.source() == WarningSource.PARTIAL_FILE)
                            .map(ProvenanceView.Warning::subject)
                            .toList();
            // The rule, stage by stage: a Comet invocation that did not complete leaves its two
            // outputs partial; one that completed leaves them complete. With two files, the
            // engine stops a sibling still searching when one fails, so the sibling is cancelled.
            List<String> expected = new ArrayList<>();
            List<String> stages = new ArrayList<>();
            for (Map<String, Object> comet : toolsNamed(record, "comet")) {
                List<String> argv = strings(object(comet.get("execution")), "argv");
                String searched = fileName(argv.get(argv.size() - 1)).replace(".mzML", "");
                String ended = string(object(comet.get("execution")), "status");
                stages.add(searched + " " + ended);
                if (!"completed".equals(ended)) {
                    expected.add("output pepxml " + searched + ".pep.xml");
                    expected.add("output pin " + searched + ".pin");
                }
            }
            assertAll(
                    run.toString(),
                    () -> assertEquals(RecordState.SEALED, view.summary().recordState()),
                    () -> assertEquals(status, string(object(record.get("run")), "status")),
                    () -> assertEquals(status, view.summary().status(), "the summary"),
                    () -> assertEquals(status, string(execution, "status"), "the Comet stage"),
                    () -> assertNotEquals(0L, number(execution, "exitCode"), "its exit"),
                    () -> {
                        if (exitCode != null) {
                            assertEquals(
                                    (long) exitCode, number(execution, "exitCode"), "its exit");
                        }
                    },
                    () ->
                            assertTrue(
                                    view.timeline().executions().stream()
                                            .anyMatch(
                                                    shown ->
                                                            shown.status().equals(status)
                                                                    && shown.exitCode()
                                                                            == number(
                                                                                    execution,
                                                                                    "exitCode")),
                                    "the timeline shows the stage and its exit status"),
                    () ->
                            assertTrue(
                                    expected.containsAll(
                                            List.of(
                                                    "output pepxml " + stem + ".pep.xml",
                                                    "output pin " + stem + ".pin")),
                                    "the stage that ended " + status + ": " + stages),
                    () -> assertEquals(expected, partial, "partial in the record: " + stages),
                    () -> assertEquals(expected, shownPartial, "partial in the view: " + stages),
                    () ->
                            assertEquals(
                                    expected.size(),
                                    warned.size(),
                                    "a partial-file warning each: " + warned));
        }
    }

    // ------------------------------------------------------------------------------- item 5 --

    @Nested
    @DisplayName(
            "gate 5: JSON and RST from one model, the report's facts asserted against the JSON")
    class Exports {

        @Test
        @DisplayName(
                "both exports equal the run's own provenance.json and provenance.rst, byte for"
                        + " byte")
        void exportsAreTheRunsOwn() throws IOException {
            assertArrayEquals(
                    Files.readAllBytes(world.completed().resolve("provenance/provenance.json")),
                    Files.readAllBytes(world.exportedJson()),
                    world.exportedJson().toString());
            assertArrayEquals(
                    Files.readAllBytes(world.completed().resolve("provenance/provenance.rst")),
                    Files.readAllBytes(world.exportedRst()),
                    world.exportedRst().toString());
        }

        @Test
        @DisplayName(
                "every fact of the exported JSON is in the exported RST, where the RST puts it")
        void everyFactIsInTheReport() throws IOException {
            Map<String, Object> json =
                    object(
                            IndependentJson.parse(
                                    Files.readString(
                                            world.exportedJson(), StandardCharsets.UTF_8)));
            IndependentRst rst =
                    IndependentRst.parse(
                            Files.readString(world.exportedRst(), StandardCharsets.UTF_8));
            Map<String, Object> run = object(json.get("run"));
            List<Map<String, Object>> files = objects(json.get("files"));
            List<Map<String, Object>> tools = objects(json.get("tools"));
            long inputs =
                    files.stream().filter(f -> string(f, "direction").equals("input")).count();

            Map<String, List<String>> summary = new LinkedHashMap<>();
            summary.put("Schema version", List.of(Long.toString(number(json, "schemaVersion"))));
            summary.put("Run ID", List.of(string(run, "runId")));
            summary.put("Project", List.of(string(run, "projectId")));
            summary.put("Status", List.of(string(run, "status")));
            summary.put("Started", List.of(string(run, "start")));
            summary.put("Ended", List.of(string(run, "end")));
            summary.put("Duration (ms)", List.of(Long.toString(number(run, "durationMillis"))));
            summary.put("Inputs", List.of(Long.toString(inputs)));
            summary.put("Outputs", List.of(Long.toString(files.size() - inputs)));
            assertEquals(summary, rst.fields().get("Summary"), "the summary");
            List<List<String>> versions = new ArrayList<>();
            versions.add(List.of("Tool", "Version"));
            for (Map<String, Object> tool : tools) {
                versions.add(List.of(string(tool, "name"), string(tool, "version")));
            }
            assertEquals(versions, rst.tables().get("Summary"), "the summary's tool versions");

            Map<String, Object> application = object(json.get("application"));
            Map<String, List<String>> environment = new LinkedHashMap<>();
            environment.put("CometGUI version", List.of(string(application, "cometGuiVersion")));
            environment.put("Build identifier", List.of(string(application, "buildIdentifier")));
            environment.put("Operating system", List.of(string(application, "osName")));
            environment.put("Operating system version", List.of(string(application, "osVersion")));
            environment.put("Architecture", List.of(string(application, "architecture")));
            environment.put("Java runtime", List.of(string(application, "jvmVersion")));
            environment.put("Default locale", List.of(string(application, "locale")));
            environment.put("Format locale", List.of(string(application, "formatLocale")));
            environment.put("Time zone", List.of(string(application, "zoneId")));
            assertEquals(
                    environment,
                    rst.fields().get("Application and environment"),
                    "the application and environment");

            List<List<String>> settings = new ArrayList<>();
            settings.add(List.of("Setting", "Value"));
            for (Map.Entry<String, Object> setting : object(json.get("settings")).entrySet()) {
                settings.add(List.of(setting.getKey(), (String) setting.getValue()));
            }
            assertTrue(settings.size() > 30, "the settings: " + settings.size());
            assertEquals(settings, rst.tables().get("Settings"), "every setting, in order");

            for (int index = 0; index < tools.size(); index++) {
                Map<String, Object> tool = tools.get(index);
                Map<String, Object> execution = object(tool.get("execution"));
                Map<String, List<String>> expected = new LinkedHashMap<>();
                expected.put("Name", List.of(string(tool, "name")));
                expected.put("Version", List.of(string(tool, "version")));
                expected.put("Release tag", optional(stringOrNull(tool, "releaseTag")));
                expected.put("Origin", List.of(bool(tool, "managed") ? "managed" : "local"));
                expected.put("Artefact", optional(stringOrNull(tool, "artefactIdentity")));
                expected.put("Stage", optional(stringOrNull(tool, "stageId")));
                expected.put("Executable", List.of(string(tool, "executablePath")));
                expected.put("MD5", List.of(string(tool, "md5")));
                expected.put("SHA-256", List.of(string(tool, "sha256")));
                expected.put("Capabilities", strings(tool, "capabilities"));
                expected.put("Working directory", List.of(string(execution, "workingDirectory")));
                expected.put("Started", List.of(string(execution, "start")));
                expected.put("Ended", List.of(string(execution, "end")));
                expected.put(
                        "Duration (ms)",
                        List.of(Long.toString(number(execution, "durationMillis"))));
                expected.put("Exit code", List.of(Long.toString(number(execution, "exitCode"))));
                expected.put("Execution status", List.of(string(execution, "status")));
                for (String stream : List.of("stdout", "stderr")) {
                    Map<String, Object> log = object(execution.get(stream));
                    String label = "stdout".equals(stream) ? "Stdout" : "Stderr";
                    expected.put(label + " log", List.of(string(log, "path")));
                    expected.put(label + " MD5", List.of(string(log, "md5")));
                    expected.put(label + " SHA-256", List.of(string(log, "sha256")));
                }
                expected.put("Command", strings(execution, "argv"));
                List<String> variables = new ArrayList<>();
                for (Map.Entry<String, Object> variable :
                        object(execution.get("environment")).entrySet()) {
                    variables.add(variable.getKey() + "=" + variable.getValue());
                }
                expected.put("Environment", variables);
                expected.put("Warnings", strings(tool, "warnings"));
                String section = "Tools/Tool " + (index + 1);
                assertEquals(
                        expected, rst.fields().get(section), section + ", " + string(tool, "name"));
            }
            assertEquals(
                    tools.size(),
                    rst.fields().keySet().stream().filter(key -> key.startsWith("Tools/")).count(),
                    "a subsection per invocation");

            List<List<String>> rows = new ArrayList<>();
            rows.add(
                    List.of(
                            "Direction",
                            "Role",
                            "Path",
                            "Size (bytes)",
                            "Modified",
                            "MD5",
                            "SHA-256",
                            "Status"));
            for (Map<String, Object> file : files) {
                rows.add(
                        List.of(
                                string(file, "direction"),
                                string(file, "role"),
                                string(file, "path"),
                                Long.toString(number(file, "sizeBytes")),
                                string(file, "modifiedAt"),
                                string(file, "md5"),
                                string(file, "sha256"),
                                string(file, "status")));
            }
            assertEquals(rows, rst.tables().get("Inputs and outputs"), "every file, in order");
            assertEquals(
                    List.of(
                            "Summary",
                            "Application and environment",
                            "Settings",
                            "Tools",
                            "Tools/Tool 1",
                            "Tools/Tool 2",
                            "Tools/Tool 3",
                            "Inputs and outputs"),
                    new ArrayList<>(rst.fields().keySet()),
                    "the report's sections");
        }

        private static List<String> optional(String value) {
            return value == null ? List.of() : List.of(value);
        }
    }

    // ------------------------------------------------------------------------------- item 6 --

    @Nested
    @DisplayName("gate 6: no seeded secret in either export or in any file the run directory holds")
    class Secrets {

        @Test
        @DisplayName(
                "each secret entered by its own path, and none of them, in any form, is in any"
                        + " file of the project or in either export")
        void noSeededSecretAnywhere() throws IOException {
            Map<String, Object> settings = object(recordOf(world.completed()).get("settings"));
            assertAll(
                    "each secret really travelled its path",
                    () ->
                            assertEquals(
                                    LimelightUploaded.Kind.REFUSED,
                                    world.refusedUpload().kind(),
                                    "the key was sent, and refused"),
                    () ->
                            assertEquals(
                                    LimelightUploaded.Kind.SUBMITTED,
                                    world.submittedUpload().kind(),
                                    "the key was sent, and accepted"),
                    () ->
                            assertEquals(
                                    "This address cannot be used as a Limelight server: it carries"
                                            + " a user name or password; Limelight takes its"
                                            + " submit key separately, never in the address.",
                                    world.refusedServerMessage(),
                                    "the address with a password was typed, and refused"),
                    () ->
                            assertEquals(
                                    "Lab threads, shared from https://scientist:[REDACTED]"
                                            + "@presets.example.org/lab with api_key=[REDACTED]",
                                    settings.get("comet.preset.01.name"),
                                    "the preset's name reached the settings, redacted"));

            Map<String, String> hits = new TreeMap<>();
            Map<String, Integer> searched = new TreeMap<>();
            Set<Path> reached = new HashSet<>();
            for (Map.Entry<String, String> seed : GateWorld.SEEDS.entrySet()) {
                List<SecretSweep.Form> forms = SecretSweep.forms(seed.getValue());
                for (Path root : List.of(world.project(), world.exports())) {
                    SecretSweep.Walk walk =
                            SecretSweep.walk(root, forms, Optional.empty(), Set.of());
                    assertEquals(List.of(), walk.unreadable(), walk.summary());
                    for (String hit : walk.hits()) {
                        hits.put(hit + ", seeded as " + seed.getKey(), seed.getKey());
                    }
                    reached.addAll(walk.searchedFiles());
                }
            }
            for (Path file : reached) {
                String kind =
                        file.startsWith(world.exports())
                                ? "exports"
                                : file.startsWith(world.completed())
                                        ? world.completed().relativize(file).getName(0).toString()
                                        : "elsewhere in the project";
                searched.merge(kind, 1, Integer::sum);
            }
            assertEquals(Map.of(), hits, "a seeded secret was written to a file");
            for (String kind :
                    List.of(
                            "exports",
                            "logs",
                            "outputs",
                            "parameters",
                            "provenance",
                            "inputs",
                            "results",
                            "run.json")) {
                assertTrue(
                        searched.getOrDefault(kind, 0) > 0,
                        "files of the run searched, by kind: " + searched + " -- none of " + kind);
            }
            for (Path file :
                    List.of(
                            world.exportedJson(),
                            world.exportedRst(),
                            world.completed().resolve("provenance/provenance.json"),
                            world.completed().resolve("provenance/provenance.rst"),
                            world.completed().resolve("provenance/events.log"),
                            world.completed().resolve("logs/limelight-upload.log"),
                            world.completed().resolve("logs/limelight-upload.1.log"),
                            world.completed().resolve("logs/limelight-converter.log"),
                            world.completed().resolve("run.json"),
                            world.conversion().file())) {
                assertTrue(reached.contains(file), "the walk did not reach " + file);
            }
            assertEquals(
                    GateWorld.regularFiles(world.completed()).size()
                            + GateWorld.regularFiles(world.exports()).size(),
                    reached.stream()
                            .filter(
                                    file ->
                                            file.startsWith(world.completed())
                                                    || file.startsWith(world.exports()))
                            .count(),
                    "every regular file of the run and both exports searched");
        }

        @Test
        @DisplayName(
                "the only environment the product gives a tool is LANG=C.UTF-8, so no secret can be"
                        + " seeded there")
        void theEnvironmentIsTheProductsOwn() throws IOException {
            for (Map<String, Object> tool : objects(recordOf(world.completed()).get("tools"))) {
                Map<String, Object> environment =
                        object(object(tool.get("execution")).get("environment"));
                assertEquals(
                        Map.of("LANG", "C.UTF-8"),
                        environment,
                        string(tool, "name") + "'s recorded environment");
            }
        }

        @Test
        @DisplayName(
                "the search's control: each seeded secret, planted in each form into a copy of the"
                        + " record, is found in that copy and named by that form")
        void theSearchFindsEveryPlantedForm() throws IOException {
            Path control = Files.createDirectories(world.root().resolve("sweep-control"));
            byte[] record =
                    Files.readAllBytes(world.completed().resolve("provenance/provenance.json"));
            int planted = 0;
            for (String seed : GateWorld.SEEDS.values()) {
                List<SecretSweep.Form> forms = SecretSweep.forms(seed);
                assertTrue(forms.size() >= 12, "the forms of a seed: " + forms);
                for (SecretSweep.Form form : forms) {
                    Path copy = control.resolve("provenance.json");
                    byte[] bytes = new byte[record.length + form.bytes().length];
                    int middle = record.length / 2;
                    System.arraycopy(record, 0, bytes, 0, middle);
                    System.arraycopy(form.bytes(), 0, bytes, middle, form.bytes().length);
                    System.arraycopy(
                            record,
                            middle,
                            bytes,
                            middle + form.bytes().length,
                            record.length - middle);
                    Files.write(copy, bytes);
                    SecretSweep.Walk walk =
                            SecretSweep.walk(control, forms, Optional.empty(), Set.of());
                    assertEquals(1, walk.searched(), walk.summary());
                    assertEquals(1, walk.hits().size(), form + ": " + walk.hits());
                    assertTrue(
                            walk.hits().get(0).startsWith(copy + " holds the secret as ")
                                    && walk.hits().get(0).contains(form.name()),
                            form + ": " + walk.hits());
                    planted++;
                }
            }
            assertTrue(planted >= 4 * 12, "forms planted: " + planted);
            Files.write(control.resolve("provenance.json"), record);
            for (String seed : GateWorld.SEEDS.values()) {
                assertEquals(
                        List.of(),
                        SecretSweep.walk(
                                        control,
                                        SecretSweep.forms(seed),
                                        Optional.empty(),
                                        Set.of())
                                .hits(),
                        "the unplanted copy is clean");
            }
        }
    }
}
