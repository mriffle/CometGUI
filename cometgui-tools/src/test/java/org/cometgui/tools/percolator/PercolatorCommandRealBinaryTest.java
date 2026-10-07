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

package org.cometgui.tools.percolator;

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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.comet.CometOutputException;
import org.cometgui.tools.comet.CometPinValidator;
import org.cometgui.tools.comet.PinDecoyConfiguration;
import org.cometgui.tools.comet.PinSummary;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.RealCometSearch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The built command, run: {@code R-PERC-06} and {@code R-DEC-04} against the REAL binaries, on a
 * merged PIN made by the REAL Comet path ({@link RealCometSearch}: Comet 2026.03.0, the two K562
 * mzML, the proteome's first 1000 records, {@code decoy_search = 1}, two {@code -N} searches, then
 * the merge), through the real process service.
 *
 * <p>Every build's capabilities are the real probe's verdict on that build, run here, and the
 * builder is given nothing else: 3.07.1 (the mirror's portable zip, held to the manifest's SHA-256)
 * and 3.09 (the {@code .rpm} binary with Boost 1.66 behind its wrapper, each file held to its
 * measured SHA-256, run as a registered local binary would be). Nothing is skipped: a missing
 * fixture fails, naming how to refill it.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
class PercolatorCommandRealBinaryTest {

    static final PinDecoyConfiguration CONCATENATED =
            new PinDecoyConfiguration(1, "Comet's internal decoys, concatenated", "DECOY_");

    @TempDir private static Path scratch;

    private static RealCometSearch.Search search;
    private static Path percolator3071;
    private static Path percolator309;
    private static Set<ToolCapability> probed3071;
    private static Set<ToolCapability> probed309;

    private static ToolRunner runner() {
        return new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(10));
    }

    @BeforeAll
    static void searchAndProbe() throws IOException {
        search = RealCometSearch.fastaSearch(scratch.resolve("comet"), RealCometSearch.NEWER);
        percolator3071 =
                PercolatorRealBinaryTest.stage(
                        scratch.resolve("percolator 3.07.1"),
                        PercolatorRealBinaryTest.ZIP_3071,
                        PercolatorRealBinaryTest.SHA256_3071);
        percolator309 =
                PercolatorRealBinaryTest.fixture309(
                        PercolatorRealBinaryTest.WRAPPER_309,
                        PercolatorRealBinaryTest.SHA256_WRAPPER_309);
        PercolatorRealBinaryTest.fixture309(
                PercolatorRealBinaryTest.BINARY_309, PercolatorRealBinaryTest.SHA256_BINARY_309);
        PercolatorRealBinaryTest.fixture309(
                PercolatorRealBinaryTest.BOOST_FILESYSTEM_309,
                PercolatorRealBinaryTest.SHA256_BOOST_FILESYSTEM_309);
        PercolatorRealBinaryTest.fixture309(
                PercolatorRealBinaryTest.BOOST_SYSTEM_309,
                PercolatorRealBinaryTest.SHA256_BOOST_SYSTEM_309);
        PercolatorCapabilityProbe probe = new PercolatorCapabilityProbe(runner());
        probed3071 =
                probe.probe(
                        ToolName.PERCOLATOR,
                        PercolatorRealBinaryTest.V3071,
                        PercolatorRealBinaryTest.HOST,
                        percolator3071);
        probed309 =
                probe.probe(
                        ToolName.PERCOLATOR,
                        PercolatorRealBinaryTest.V309,
                        PercolatorRealBinaryTest.HOST,
                        percolator309);
    }

    private static Map<PercolatorOption, String> defaults() {
        Map<PercolatorOption, String> values = new EnumMap<>(PercolatorOption.class);
        values.put(PercolatorOption.SEED, "1");
        values.put(PercolatorOption.NUM_THREADS, "3");
        values.put(PercolatorOption.TEST_FDR, "0.01");
        values.put(PercolatorOption.TRAIN_FDR, "0.01");
        values.put(PercolatorOption.MAX_ITERATIONS, "10");
        return values;
    }

    /** One run: the PIN check, the built command, run, timed. */
    private record Ran(PercolatorCommand built, ToolRunOutcome outcome, Path out, double seconds) {}

    private static Ran run(
            String label, Path executable, Set<ToolCapability> probed, boolean xmlNeeded)
            throws IOException, PercolatorRefusedException {
        Path pin = search.run().mergedPinFile();
        PercolatorPinCheck.check(pin, CONCATENATED);
        Path out = Files.createDirectories(scratch.resolve(label + "/outputs/percolator"));
        PercolatorCommand built =
                PercolatorCommands.build(
                        new PercolatorRequest(executable, pin, out, probed, xmlNeeded, defaults()));
        long started = System.nanoTime();
        ToolRunOutcome outcome = runner().run(built.command());
        double seconds = (System.nanoTime() - started) / 1.0e9;
        System.out.printf(
                Locale.ROOT,
                "PERCOLATOR %s on the real merged PIN in %.2f s, exit %s%n  argv %s%n  wrote %s%n",
                label,
                seconds,
                outcome.exitCode(),
                built.command().displayString(),
                listingWithSizes(out));
        assertTrue(outcome.exitedZero(), built.command() + "\n" + outcome.joinedOutput());
        return new Ran(built, outcome, out, seconds);
    }

    private static List<String> listing(Path directory) throws IOException {
        try (Stream<Path> listed = Files.list(directory)) {
            return listed.map(path -> directory.relativize(path).toString()).sorted().toList();
        }
    }

    private static List<String> listingWithSizes(Path directory) throws IOException {
        try (Stream<Path> listed = Files.list(directory)) {
            return listed.sorted()
                    .map(
                            path -> {
                                try {
                                    return directory.relativize(path)
                                            + " "
                                            + Files.size(path)
                                            + " bytes, "
                                            + Files.readAllLines(path, StandardCharsets.ISO_8859_1)
                                                    .size()
                                            + " lines";
                                } catch (IOException unreadable) {
                                    throw new AssertionError(unreadable);
                                }
                            })
                    .toList();
        }
    }

    private static List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
    }

    /** The argument array every run here must have, hand-typed around the run's own paths. */
    private static List<String> expectedArgv(Path executable, Path out, boolean withXml) {
        List<String> argv =
                new ArrayList<>(
                        List.of(
                                executable.toString(),
                                "--results-psms",
                                out + "/psms.tsv",
                                "--results-peptides",
                                out + "/peptides.tsv",
                                "--decoy-results-psms",
                                out + "/decoy-psms.tsv",
                                "--decoy-results-peptides",
                                out + "/decoy-peptides.tsv",
                                "--weights",
                                out + "/weights.txt"));
        if (withXml) {
            argv.addAll(List.of("-X", out + "/pout.xml"));
        }
        argv.addAll(
                List.of(
                        "--seed",
                        "1",
                        "--num-threads",
                        "3",
                        "--testFDR",
                        "0.01",
                        "--trainFDR",
                        "0.01",
                        "--maxiter",
                        "10",
                        search.run().mergedPinFile().toString()));
        return argv;
    }

    /** The PSM and peptide tables' header, typed from what 3.07.1 and 3.09 wrote. */
    private static final String TABLE_HEADER =
            "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds";

    /** The weights file's feature row: Percolator drops ExpMass and CalcMass and adds m0. */
    private static final String WEIGHTS_FEATURES =
            "lnrSp\tdeltLCn\tdeltCn\tlnExpect\tXcorr\tSp\tIonFrac\tMass\tPepLen\tCharge1\tCharge2"
                    + "\tCharge3\tCharge4\tCharge5\tCharge6\tenzN\tenzC\tenzInt\tlnNumSP\tdM\tabsdM"
                    + "\tm0";

    /*
     * WHAT EVERY RUN ON THIS PIN WRITES, measured on 2026-10-07 with 3.07.1 and 3.09 alike: every
     * target row of the PIN in the PSM table (3285 = 1807 + 1478) and every decoy row in the decoy
     * PSM table (3187 = 1747 + 1440) -- the tables are counted against the PIN, an independent
     * count, not against a number read back from the same file; 2482 target and 2399 decoy
     * peptides; and a weights file of three comment lines and three cross-validation splits of
     * three lines each.
     */
    private static void assertTheTables(Path out) throws IOException {
        List<String> psms = lines(out.resolve("psms.tsv"));
        List<String> peptides = lines(out.resolve("peptides.tsv"));
        List<String> decoyPsms = lines(out.resolve("decoy-psms.tsv"));
        List<String> decoyPeptides = lines(out.resolve("decoy-peptides.tsv"));
        List<String> weights = lines(out.resolve("weights.txt"));
        assertAll(
                () -> assertEquals(TABLE_HEADER, psms.get(0)),
                () -> assertEquals(TABLE_HEADER, peptides.get(0)),
                () -> assertEquals(TABLE_HEADER, decoyPsms.get(0)),
                () -> assertEquals(TABLE_HEADER, decoyPeptides.get(0)),
                () -> assertEquals(1807 + 1478, psms.size() - 1),
                () -> assertEquals(1747 + 1440, decoyPsms.size() - 1),
                () -> assertEquals(2482, peptides.size() - 1),
                () -> assertEquals(2399, decoyPeptides.size() - 1),
                () -> assertEquals(3 + 3 * 3, weights.size()),
                () -> assertTrue(weights.get(0).startsWith("# "), weights.get(0)),
                () ->
                        assertEquals(
                                List.of(3, 6, 9),
                                indexesOf(weights, WEIGHTS_FEATURES),
                                "one feature row per split, three splits"));
    }

    private static List<Integer> indexesOf(List<String> lines, String line) {
        List<Integer> found = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).equals(line)) {
                found.add(index);
            }
        }
        return found;
    }

    private static List<String> xmlFilesUnder(Path directory) throws IOException {
        try (Stream<Path> walked = Files.walk(directory)) {
            return walked.map(Path::toString).filter(name -> name.endsWith(".xml")).toList();
        }
    }

    @Test
    @DisplayName("the real probe's verdicts the builder is given: 3.07.1 all, 3.09 no XML")
    void theProbedSets() {
        Set<ToolCapability> withoutXml = EnumSet.copyOf(PercolatorRealBinaryTest.EVERY_CAPABILITY);
        withoutXml.remove(ToolCapability.XML_OUTPUT);
        withoutXml.remove(ToolCapability.XML_DECOY_OUTPUT);

        assertAll(
                () -> assertEquals(PercolatorRealBinaryTest.EVERY_CAPABILITY, probed3071),
                () -> assertEquals(withoutXml, probed309));
    }

    @Test
    @DisplayName("the real merged PIN passes the check before launch: 23 features, its counts")
    void theRealMergedPinPasses() throws IOException, PercolatorRefusedException {
        Path merged = search.run().mergedPinFile();
        PinSummary summary = PercolatorPinCheck.check(merged, CONCATENATED);

        assertAll(
                () -> assertEquals(merged, summary.file()),
                () -> assertEquals(23, summary.featureColumns().size()),
                () -> assertEquals("ExpMass", summary.featureColumns().get(0)),
                () -> assertEquals("absdM", summary.featureColumns().get(22)),
                () -> assertEquals(1807 + 1478, summary.targets()),
                () -> assertEquals(1747 + 1440, summary.decoys()),
                () -> assertEquals(6472, search.merge().totalRows()));
    }

    @Test
    @DisplayName("3.07.1, Limelight wanted: -X in the argv, every artefact written and parsed")
    void percolator3071WithXml() throws IOException, PercolatorRefusedException {
        Ran ran = run("3.07.1 xml", percolator3071, probed3071, true);
        Path out = ran.out();
        PoutDocument pout = PoutDocument.read(out.resolve("pout.xml"));
        int psmRows = lines(out.resolve("psms.tsv")).size() - 1;
        int peptideRows = lines(out.resolve("peptides.tsv")).size() - 1;

        assertAll(
                () ->
                        assertEquals(
                                expectedArgv(percolator3071, out, true),
                                ran.built().command().argv()),
                () -> assertEquals(out, ran.built().command().workingDirectory()),
                () -> assertEquals(Map.of("LANG", "C.UTF-8"), ran.built().command().environment()),
                () -> assertEquals(List.of(), ran.built().notEmitted()),
                () ->
                        assertEquals(
                                List.of(
                                        "decoy-peptides.tsv",
                                        "decoy-psms.tsv",
                                        "peptides.tsv",
                                        "pout.xml",
                                        "psms.tsv",
                                        "weights.txt"),
                                listing(out),
                                "exactly the artefacts asked for, and nothing else"),
                () ->
                        assertEquals(
                                List.of(
                                        out.resolve("psms.tsv"),
                                        out.resolve("peptides.tsv"),
                                        out.resolve("decoy-psms.tsv"),
                                        out.resolve("decoy-peptides.tsv"),
                                        out.resolve("weights.txt"),
                                        out.resolve("pout.xml")),
                                List.copyOf(ran.built().artefacts().values())),
                () -> assertTheTables(out),
                () -> assertEquals("http://per-colator.com/percolator_out/15", pout.namespace()),
                () -> assertEquals("percolator_output", pout.rootElement()),
                () -> assertEquals(3285, pout.psmCount()),
                () -> assertEquals(psmRows, pout.psmCount(), "one psm per PSM-table row"),
                () -> assertEquals(2482, pout.peptideCount()),
                () -> assertEquals(peptideRows, pout.peptideCount()),
                () ->
                        assertEquals(
                                Set.of(),
                                pout.psmDecoyValues(),
                                "no -Z: targets only, so no decoy attribute at all"));
    }

    @Test
    @DisplayName("3.09, Limelight wanted: NO XML option in the argv, the run succeeds, no .xml")
    void percolator309WithoutXml() throws IOException, PercolatorRefusedException {
        Ran ran = run("3.09 no xml", percolator309, probed309, true);

        assertAll(
                () ->
                        assertEquals(
                                expectedArgv(percolator309, ran.out(), false),
                                ran.built().command().argv()),
                () -> assertFalse(ran.built().command().argv().contains("-X")),
                () -> assertFalse(ran.built().command().argv().contains("-Z")),
                () -> assertFalse(ran.built().writesXml(), "a 3.09 run expects no XML"),
                () ->
                        assertEquals(
                                List.of(PercolatorOption.XML_OUTPUT),
                                ran.built().notEmitted().stream().map(NotEmitted::option).toList(),
                                "and says why: XML_OUTPUT was not probed"),
                () ->
                        assertEquals(
                                List.of(
                                        "decoy-peptides.tsv",
                                        "decoy-psms.tsv",
                                        "peptides.tsv",
                                        "psms.tsv",
                                        "weights.txt"),
                                listing(ran.out())),
                () -> assertEquals(List.of(), xmlFilesUnder(ran.out())),
                () -> assertTheTables(ran.out()));
    }

    @Test
    @DisplayName("3.07.1, Limelight NOT wanted: no -X although the build is capable")
    void percolator3071WithoutLimelight() throws IOException, PercolatorRefusedException {
        Ran ran = run("3.07.1 no limelight", percolator3071, probed3071, false);

        assertAll(
                () -> assertTrue(probed3071.contains(ToolCapability.XML_OUTPUT)),
                () ->
                        assertEquals(
                                expectedArgv(percolator3071, ran.out(), false),
                                ran.built().command().argv()),
                () -> assertEquals(List.of(), ran.built().notEmitted()),
                () ->
                        assertEquals(
                                List.of(
                                        "decoy-peptides.tsv",
                                        "decoy-psms.tsv",
                                        "peptides.tsv",
                                        "psms.tsv",
                                        "weights.txt"),
                                listing(ran.out())),
                () -> assertEquals(List.of(), xmlFilesUnder(ran.out())),
                () -> assertTheTables(ran.out()));
    }

    /*
     * REPRODUCED, NOT CONSTRUCTED: Comet 2026.02.2 builds a fragment-ion index with the run's own
     * decoy_search = 1 and searches both files against it, and writes no decoy row (Phase 08's
     * finding, measured again here: 118 and 80 target rows).  Comet validation already refuses each
     * per-file PIN; the check before launch must refuse the MERGED one too, because a rerun reuses
     * a preserved merged PIN without Comet validation.
     */
    @Test
    @DisplayName("the REAL zero-decoy PIN (2026.02.2, fragment-ion index) is refused before launch")
    void theRealZeroDecoyPinIsRefused() throws IOException {
        RealCometSearch.Search older =
                RealCometSearch.fragmentIndexSearch(
                        scratch.resolve("comet 2026.02.2 index"), RealCometSearch.OLDER);
        Path merged = older.run().mergedPinFile();

        CometOutputException atComet =
                assertThrows(
                        CometOutputException.class,
                        () ->
                                CometPinValidator.validate(
                                        older.run().pinFile("k562_3"), CONCATENATED));
        PercolatorRefusedException refused =
                assertThrows(
                        PercolatorRefusedException.class,
                        () -> PercolatorPinCheck.check(merged, CONCATENATED));

        assertAll(
                () ->
                        assertEquals(
                                List.of(118L, 80L),
                                older.merge().inputs().stream()
                                        .map(input -> input.rows())
                                        .toList()),
                () -> assertEquals(198, older.merge().totalRows()),
                () ->
                        assertEquals(
                                "the PIN file "
                                        + older.run().pinFile("k562_3")
                                        + " holds 118 target rows and no decoy row (Label -1), so"
                                        + " Percolator would have no negative examples; the decoy"
                                        + " configuration was decoy_search = 1 (Comet's internal"
                                        + " decoys, concatenated), decoy_prefix = \"DECOY_\"",
                                atComet.getMessage()),
                () ->
                        assertEquals(
                                "Percolator was not started: the PIN file "
                                        + merged
                                        + " holds 198 target rows and no decoy row (Label -1), so"
                                        + " Percolator would have no negative examples; the decoy"
                                        + " configuration was decoy_search = 1 (Comet's internal"
                                        + " decoys, concatenated), decoy_prefix = \"DECOY_\"",
                                refused.getMessage()),
                () -> assertEquals(Optional.of(merged), refused.file()));
    }
}
