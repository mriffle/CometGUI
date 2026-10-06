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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.OutputBaseNames;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The adapter against the REAL pinned Comet 2026.03.0: two spectrum files searched one command
 * each, from an input directory proven read-only, through {@link ToolRunner} over {@link
 * ProcessService}; their pepXML and PIN files validated; the PINs merged (gate item 3); the
 * feature-column check on real PINs with a column renamed and two swapped; {@code R-DEC-04} on a
 * real PIN with its decoys removed; and an index built through {@link CometIndexCommand} and
 * searched through {@link CometSearchCommands#databaseOverride}.
 *
 * <p>Inputs, never committed ({@code D-006}), each held to its SHA-256 -- a missing or changed
 * input FAILS this test, nothing skips: the binary from the mirror against the artefact manifest's
 * hash; the two K562 mzML files of {@code scratch/fixture} against the hashes {@code
 * scripts/feasibility/fetch_ephemeral_input.py} fetches them by, then their CRLF-to-LF repaired
 * private copies against {@code docs/feasibility/scientific-path.rst}'s; the proteome's first 1000
 * records against the validation corpus's subset hash. The parameter file is the binary's own
 * {@code -q} output with {@code num_threads = 4}, {@code decoy_search = 1} and {@code
 * output_percolatorfile = 1}. Phase 00's {@code .pep.xml}/{@code .pin} leftovers in {@code
 * scratch/fixture} are never read. Each search takes about 1.4 s.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class CometAdapterRealBinaryTest {

    static final String BINARY = "v2026.03.0__comet.linux.exe";

    static final String BINARY_SHA256 =
            "ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed";

    static final String MZML_3 = "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML";

    static final String MZML_3_FETCHED =
            "cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7";

    static final String MZML_3_LF =
            "a562f6e642b2880bece3134c43aa9581124788926bd1b9305c2db0bb506954da";

    static final String MZML_4 = "scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML";

    static final String MZML_4_FETCHED =
            "d30aa5af4b15e1c927616fce4dacfd68c6249e6cc2b7461913481c12d8408cfa";

    static final String MZML_4_LF =
            "602aad75e18257feabdb94ece0fa82e19e5f17153eee4fb89875976920855c82";

    static final PinDecoyConfiguration CONCATENATED =
            new PinDecoyConfiguration(1, "Comet's internal decoys, concatenated", "DECOY_");

    @TempDir private static Path scratch;

    private static Path binary;

    private static Path inputs;

    private static Path fasta;

    private static List<OutputBase> spectra;

    private static RunLayout run;

    private static List<ToolCommand> commands;

    private static final List<ToolRunOutcome> OUTCOMES = new ArrayList<>();

    private static Map<String, Long> inputsBefore;

    private static Map<String, Long> inputsAfter;

    private static List<String> outputsWritten;

    private static ToolRunner runner() {
        return new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(120));
    }

    @BeforeAll
    static void search() throws IOException {
        Path root = UpstreamArtefacts.repositoryRoot();
        binary = UpstreamArtefacts.executableCopy(BINARY, scratch.resolve("bin/comet"));
        assertEquals(BINARY_SHA256, UpstreamArtefacts.sha256(binary), "the staged Comet");

        inputs = Files.createDirectories(scratch.resolve("read-only inputs"));
        Path k3 = lfCopy(root.resolve(MZML_3), MZML_3_FETCHED, inputs.resolve("k562_3.mzML"));
        assertEquals(MZML_3_LF, UpstreamArtefacts.sha256(k3), "the LF-repaired K562_3");
        Path k4 = lfCopy(root.resolve(MZML_4), MZML_4_FETCHED, inputs.resolve("k562_4.mzML"));
        assertEquals(MZML_4_LF, UpstreamArtefacts.sha256(k4), "the LF-repaired K562_4");
        Path proteome = root.resolve(CometIndexRealBinaryTest.PROTEOME);
        assertTrue(
                Files.isRegularFile(proteome),
                proteome
                        + " does not exist. It is a local, gitignored input (D-006); refill it with"
                        + " python3 scripts/feasibility/fetch_ephemeral_input.py.");
        assertEquals(
                CometIndexRealBinaryTest.PROTEOME_SHA256,
                UpstreamArtefacts.sha256(proteome),
                "the proteome");
        fasta = inputs.resolve("subset.fasta");
        Files.write(fasta, CometIndexRealBinaryTest.firstRecords(proteome, 1000));
        assertEquals(
                CometIndexRealBinaryTest.SUBSET_SHA256,
                UpstreamArtefacts.sha256(fasta),
                "the first 1000 records");

        makeReadOnly(inputs);
        inputsBefore = listing(inputs);

        run = new RunLayout(scratch.resolve("project one/runs/20261006T120000Z-run-0001"));
        for (Path directory : run.directories()) {
            Files.createDirectories(directory);
        }
        Files.writeString(run.cometParamsFile(), parameters(), StandardCharsets.ISO_8859_1);

        spectra = OutputBaseNames.derive(List.of(k3, k4));
        commands = CometSearchCommands.databaseFromParameterFile(binary, run).commands(spectra);
        for (ToolCommand command : commands) {
            ToolRunOutcome outcome = runner().run(command);
            assertTrue(outcome.exitedZero(), command + "\n" + outcome.joinedOutput());
            OUTCOMES.add(outcome);
        }
        inputsAfter = listing(inputs);
        try (Stream<Path> listed = Files.list(run.cometOutputDirectory())) {
            outputsWritten =
                    listed.map(path -> run.cometOutputDirectory().relativize(path).toString())
                            .sorted()
                            .toList();
        }
    }

    @AfterAll
    static void writable() throws IOException {
        if (inputs != null) {
            Files.setPosixFilePermissions(inputs, PosixFilePermissions.fromString("rwx------"));
        }
    }

    /** Copies a fetched mzML held to its hash, dropping every CR byte. */
    private static Path lfCopy(Path fetched, String sha256, Path copy) throws IOException {
        assertTrue(
                Files.isRegularFile(fetched),
                fetched
                        + " does not exist. It is a local, gitignored input (D-006); refill it with"
                        + " python3 scripts/feasibility/fetch_ephemeral_input.py.");
        assertEquals(sha256, UpstreamArtefacts.sha256(fetched), "the fetched " + fetched);
        byte[] bytes = Files.readAllBytes(fetched);
        ByteArrayOutputStream repaired = new ByteArrayOutputStream(bytes.length);
        for (byte value : bytes) {
            if (value != '\r') {
                repaired.write(value);
            }
        }
        return Files.write(copy, repaired.toByteArray());
    }

    /** Removes write permission and proves a write is refused; never passes on a writable one. */
    private static void makeReadOnly(Path directory) throws IOException {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"));
        Path probe = directory.resolve("probe");
        assertThrows(
                AccessDeniedException.class,
                () -> Files.createFile(probe),
                "the input directory accepted a write after its write permission was removed --"
                        + " running as root, or a file system ignoring modes -- so it cannot"
                        + " prove containment");
    }

    private static Map<String, Long> listing(Path directory) throws IOException {
        Map<String, Long> sizes = new TreeMap<>();
        try (Stream<Path> listed = Files.list(directory)) {
            for (Path path : listed.toList()) {
                sizes.put(directory.relativize(path).toString(), Files.size(path));
            }
        }
        return sizes;
    }

    /** The binary's own -q parameter file, with the test's values. */
    private static String parameters() throws IOException {
        Path dump = Files.createDirectories(scratch.resolve("q"));
        ToolRunOutcome written =
                runner().run(new ToolCommand(List.of(binary.toString(), "-q"), dump, Map.of()));
        assertTrue(written.exitedZero(), written.joinedOutput());
        Map<String, String> values = new TreeMap<>();
        values.put("database_name", fasta.toString());
        values.put("spectral_library_name", "");
        values.put("num_threads", "4");
        values.put("decoy_search", "1");
        values.put("output_pepxmlfile", "1");
        values.put("output_percolatorfile", "1");
        return CometIndexRealBinaryTest.edited(
                Files.readString(
                        dump.resolve(CometCapabilityProbe.WRITTEN_PARAMETERS_FILE),
                        StandardCharsets.ISO_8859_1),
                values);
    }

    @Test
    @DisplayName("each real command, element by element: -P, -N into the run, one input")
    void argumentArrays() {
        String runRoot = scratch + "/project one/runs/20261006T120000Z-run-0001";
        assertEquals(
                List.of(
                        scratch + "/bin/comet",
                        "-P" + runRoot + "/parameters/comet.params",
                        "-N" + runRoot + "/outputs/comet/k562_3",
                        scratch + "/read-only inputs/k562_3.mzML"),
                commands.get(0).argv());
        assertEquals(
                List.of(
                        scratch + "/bin/comet",
                        "-P" + runRoot + "/parameters/comet.params",
                        "-N" + runRoot + "/outputs/comet/k562_4",
                        scratch + "/read-only inputs/k562_4.mzML"),
                commands.get(1).argv());
        assertEquals(Path.of(runRoot), commands.get(0).workingDirectory());
        assertEquals(Map.of("LANG", "C.UTF-8"), commands.get(1).environment());
    }

    @Test
    @DisplayName("two pepXML and two PIN files inside the run; the read-only inputs untouched")
    void containment() {
        assertEquals(
                List.of("k562_3.pep.xml", "k562_3.pin", "k562_4.pep.xml", "k562_4.pin"),
                outputsWritten);
        assertEquals(inputsBefore, inputsAfter);
        assertEquals(
                List.of("k562_3.mzML", "k562_4.mzML", "subset.fasta"),
                List.copyOf(inputsAfter.keySet()));
    }

    @Test
    @DisplayName("each real pepXML names its own input and -N base; 728 and 607 spectrum queries")
    void pepXml() throws IOException {
        PepXmlSummary first = CometPepXmlValidator.validate(run, spectra.get(0));
        assertEquals(728, first.spectrumQueries());
        assertEquals(inputs + "/k562_3.mzML", first.input());
        assertEquals(run.cometOutputBase("k562_3").toString(), first.outputBase());
        PepXmlSummary second = CometPepXmlValidator.validate(run, spectra.get(1));
        assertEquals(607, second.spectrumQueries());
        assertEquals(inputs + "/k562_4.mzML", second.input());
        // the first file's pepXML is not the second input's
        CometOutputException crossed =
                assertThrows(
                        CometOutputException.class,
                        () ->
                                CometPepXmlValidator.validate(
                                        run.pepXmlFile("k562_3"),
                                        spectra.get(1).input(),
                                        run.cometOutputBase("k562_4")));
        assertTrue(
                crossed.getMessage().contains(" names its input \"" + inputs + "/k562_3.mzML\""));
    }

    @Test
    @DisplayName("each real PIN: Comet's 23 features, its target and decoy counts")
    void pins() throws IOException {
        PinSummary first = CometPinValidator.validate(run.pinFile("k562_3"), CONCATENATED);
        assertEquals(PinText.FEATURES, first.featureColumns());
        assertEquals(1807, first.targets());
        assertEquals(1747, first.decoys());
        assertEquals(3554, first.rows());
        PinSummary second = CometPinValidator.validate(run.pinFile("k562_4"), CONCATENATED);
        assertEquals(PinText.FEATURES, second.featureColumns());
        assertEquals(1478, second.targets());
        assertEquals(1440, second.decoys());
        assertEquals(2918, second.rows());
    }

    /** How many of a file's lines have each field count. */
    private static Map<Integer, Long> fieldCounts(Path pin) throws IOException {
        Map<Integer, Long> counts = new TreeMap<>();
        for (String line : Files.readAllLines(pin, StandardCharsets.ISO_8859_1)) {
            counts.merge(line.split("\t", -1).length, 1L, Long::sum);
        }
        return counts;
    }

    @Test
    @DisplayName("Comet writes each further protein as one more tab-separated field")
    void multiProteinRows() throws IOException {
        assertEquals(Map.of(28, 3528L, 29, 25L, 30, 2L), fieldCounts(run.pinFile("k562_3")));
        assertEquals(Map.of(28, 2894L, 29, 22L, 30, 3L), fieldCounts(run.pinFile("k562_4")));
        String specIdPrefix = run.cometOutputBase("k562_3") + "_";
        for (String line :
                Files.readAllLines(run.pinFile("k562_3"), StandardCharsets.ISO_8859_1)
                        .subList(1, 3555)) {
            assertTrue(line.startsWith(specIdPrefix), "SpecId carries the -N base: " + line);
        }
    }

    @Test
    @DisplayName("gate 3: the merged real PIN has one header and 3554 + 2918 = 6472 rows")
    void merged() throws IOException {
        TestHashes hashes = new TestHashes();
        PinMergeRecord record =
                PinMerger.merge(run, List.of(run.pinFile("k562_3"), run.pinFile("k562_4")), hashes);
        List<String> lines = Files.readAllLines(run.mergedPinFile(), StandardCharsets.ISO_8859_1);
        long headers = lines.stream().filter(line -> line.startsWith("SpecId\t")).count();
        assertEquals(1, headers);
        assertEquals(PinText.HEADER, lines.get(0));
        // Hand-checked arithmetic: each file's own data rows, then their sum.
        long first = Files.readAllLines(run.pinFile("k562_3")).size() - 1;
        long second = Files.readAllLines(run.pinFile("k562_4")).size() - 1;
        assertEquals(3554, first);
        assertEquals(2918, second);
        assertEquals(6472, first + second);
        assertEquals(6472, lines.size() - 1);
        assertEquals(
                List.of(
                        new PinMergeRecord.Input(run.pinFile("k562_3"), 3554),
                        new PinMergeRecord.Input(run.pinFile("k562_4"), 2918)),
                record.inputs());
        assertEquals(6472, record.totalRows());

        byte[] three = Files.readAllBytes(run.pinFile("k562_3"));
        byte[] four = Files.readAllBytes(run.pinFile("k562_4"));
        int fourHeaderEnd = indexOf(four, (byte) '\n') + 1;
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(three);
        expected.write(four, fourHeaderEnd, four.length - fourHeaderEnd);
        byte[] written = Files.readAllBytes(run.mergedPinFile());
        assertArrayEquals(expected.toByteArray(), written, "every row byte for byte, in order");
        assertEquals(-1, indexOf(written, (byte) '\r'));
        assertEquals(TestHashes.of(written), record.hashes());
        assertEquals(run.mergedPinFile(), record.output());

        PinSummary mergedSummary = CometPinValidator.validate(run.mergedPinFile(), CONCATENATED);
        assertEquals(1807 + 1478, mergedSummary.targets());
        assertEquals(1747 + 1440, mergedSummary.decoys());
    }

    private static int indexOf(byte[] bytes, byte value) {
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == value) {
                return index;
            }
        }
        return -1;
    }

    /** A copy of the real k562_4 PIN with its header line edited. */
    private static Path editedHeader(Path directory, String from, String to) throws IOException {
        String text = Files.readString(run.pinFile("k562_4"), StandardCharsets.ISO_8859_1);
        int end = text.indexOf('\n');
        String header = text.substring(0, end);
        assertTrue(header.contains(from), header);
        return Files.writeString(
                directory.resolve("k562_4 edited.pin"),
                header.replace(from, to) + text.substring(end),
                StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("gate 3: a real PIN with one column renamed fails naming both files")
    void renamedRealColumn(@TempDir Path directory) throws IOException {
        RunLayout other = new RunLayout(directory.resolve("run"));
        Files.createDirectories(other.pinInputsDirectory());
        Path edited = editedHeader(directory, "\tlnExpect\t", "\tlnEValue\t");
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () ->
                                PinMerger.merge(
                                        other,
                                        List.of(run.pinFile("k562_3"), edited),
                                        new TestHashes()));
        assertEquals(
                "cannot merge the PIN files "
                        + run.pinFile("k562_3")
                        + " and "
                        + edited
                        + ": their feature columns differ, first at feature column 6"
                        + " (\"lnExpect\" in the first, \"lnEValue\" in the second; 23 and 23"
                        + " feature columns). Percolator needs every file to have the same"
                        + " features in the same order",
                refused.getMessage());
        try (Stream<Path> listed = Files.list(other.pinInputsDirectory())) {
            assertEquals(List.of(), listed.toList());
        }
    }

    @Test
    @DisplayName("gate 3: a real PIN with two columns swapped fails naming both files")
    void swappedRealColumns(@TempDir Path directory) throws IOException {
        RunLayout other = new RunLayout(directory.resolve("run"));
        Files.createDirectories(other.pinInputsDirectory());
        Path edited = editedHeader(directory, "\tXcorr\tSp\t", "\tSp\tXcorr\t");
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () ->
                                PinMerger.merge(
                                        other,
                                        List.of(run.pinFile("k562_3"), edited),
                                        new TestHashes()));
        assertEquals(
                "cannot merge the PIN files "
                        + run.pinFile("k562_3")
                        + " and "
                        + edited
                        + ": their feature columns differ, first at feature column 7 (\"Xcorr\""
                        + " in the first, \"Sp\" in the second; 23 and 23 feature columns)."
                        + " Percolator needs every file to have the same features in the same"
                        + " order",
                refused.getMessage());
        assertEquals(edited, refused.file());
    }

    @Test
    @DisplayName("R-DEC-04: the real PIN with its decoy rows removed fails naming the config")
    void realPinWithoutDecoys(@TempDir Path directory) throws IOException {
        List<String> kept = new ArrayList<>();
        for (String line : Files.readAllLines(run.pinFile("k562_3"), StandardCharsets.ISO_8859_1)) {
            if (!line.split("\t", -1)[1].equals("-1")) {
                kept.add(line);
            }
        }
        Path targetsOnly =
                Files.writeString(
                        directory.resolve("targets only.pin"),
                        String.join("\n", kept) + "\n",
                        StandardCharsets.ISO_8859_1);
        assertEquals(
                "the PIN file "
                        + targetsOnly
                        + " holds 1807 target rows and no decoy row (Label -1), so Percolator"
                        + " would have no negative examples; the decoy configuration was"
                        + " decoy_search = 1 (Comet's internal decoys, concatenated), decoy_prefix"
                        + " = \"DECOY_\"",
                assertThrows(
                                CometOutputException.class,
                                () -> CometPinValidator.validate(targetsOnly, CONCATENATED))
                        .getMessage());
    }

    @Test
    @DisplayName("a truncated real PIN and a truncated real pepXML fail naming the file")
    void truncatedRealOutputs(@TempDir Path directory) throws IOException {
        byte[] pin = Files.readAllBytes(run.pinFile("k562_4"));
        Path cutPin =
                Files.write(directory.resolve("cut.pin"), Arrays.copyOf(pin, pin.length - 40));
        assertEquals(
                "the PIN file "
                        + cutPin
                        + " ends without a line terminator: line 2919 is incomplete, so the file"
                        + " is truncated",
                assertThrows(
                                CometOutputException.class,
                                () -> CometPinValidator.validate(cutPin, CONCATENATED))
                        .getMessage());
        byte[] pepXml = Files.readAllBytes(run.pepXmlFile("k562_4"));
        Path cutXml =
                Files.write(
                        directory.resolve("cut.pep.xml"), Arrays.copyOf(pepXml, pepXml.length / 2));
        assertTrue(
                assertThrows(
                                CometOutputException.class,
                                () ->
                                        CometPepXmlValidator.validate(
                                                cutXml,
                                                spectra.get(1).input(),
                                                run.cometOutputBase("k562_4")))
                        .getMessage()
                        .startsWith("the pepXML file " + cutXml + " is not well-formed XML: "));
    }

    @Test
    @DisplayName("an index built through CometIndexCommand, then searched with -D")
    void indexFlow(@TempDir Path directory) throws IOException {
        Path cache = Files.createDirectories(directory.resolve("index cache/key"));
        CometIndexCommand build =
                new CometIndexCommand(
                        binary, run.cometParamsFile(), IndexMode.FRAGMENT_ION, cache, fasta);
        build.linkDatabase();
        ToolRunOutcome built = runner().run(build.command());
        assertTrue(built.exitedZero(), built.joinedOutput());
        assertTrue(Files.size(build.indexFile()) > 0, "the index is in the cache");
        assertEquals(
                "Comet index database v5.  Comet version 2026.03 rev. 0 (fa08489)",
                Files.readAllLines(build.indexFile(), StandardCharsets.ISO_8859_1).get(0));
        try (Stream<Path> listed = Files.list(cache)) {
            assertEquals(
                    List.of("subset.fasta", "subset.fasta.idx"),
                    listed.map(path -> cache.relativize(path).toString()).sorted().toList());
        }
        assertEquals(inputsBefore, listing(inputs));

        RunLayout indexed = new RunLayout(directory.resolve("indexed run"));
        for (Path made : indexed.directories()) {
            Files.createDirectories(made);
        }
        Files.copy(run.cometParamsFile(), indexed.cometParamsFile());
        CometSearchCommands search =
                CometSearchCommands.databaseOverride(binary, indexed, build.indexFile());
        ToolCommand command = search.command(spectra.get(0));
        assertEquals("-D" + build.indexFile(), command.argv().get(2));
        ToolRunOutcome searched = runner().run(command);
        assertTrue(searched.exitedZero(), searched.joinedOutput());
        assertEquals(728, CometPepXmlValidator.validate(indexed, spectra.get(0)).spectrumQueries());
        PinSummary pin = CometPinValidator.validate(indexed.pinFile("k562_3"), CONCATENATED);
        assertEquals(118, pin.targets());
        assertEquals(96, pin.decoys());
    }
}
