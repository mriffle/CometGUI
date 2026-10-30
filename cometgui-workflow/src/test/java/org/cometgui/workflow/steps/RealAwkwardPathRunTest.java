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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two failure paths of the specification's list -- "paths containing Unicode" and "long paths on
 * supported platforms" -- through the real workflow with the real Comet 2026.03.0 and the real
 * Percolator 3.07.1 (Phase 14, gate item 4).
 *
 * <h2>Unicode</h2>
 *
 * <p>The project, both spectrum files, the database and both executables live under names with
 * spaces, accented Latin, a CJK segment and a circled digit. The run must succeed end to end, the
 * Comet outputs take the files' own natural base names, the merged PIN holds the summed rows of its
 * two sources (counted here), Percolator writes one PSM row per target row of it, and the record
 * names every input by its exact path. <strong>This class fails, never skips, when the JVM's file
 * name encoding is not UTF-8</strong>: in that state the JVM cannot spell these names at all, and a
 * skipped test is a check that cannot go red. The module's surefire fork is given {@code
 * LANG=C.UTF-8} for exactly that reason, as {@code cometgui-process}'s is.
 *
 * <h2>Long paths, and the limits measured on this Comet</h2>
 *
 * <p>Linux allows a path of 4095 bytes and a name of 255 ({@code PATH_MAX} 4096 and {@code
 * NAME_MAX} 255 on this host's ext4). Comet 2026.03.0's linux/x86-64 build was measured, before
 * this class was written, to accept a spectrum file path of 4031 bytes, but to <strong>truncate its
 * {@code -N} output base and its {@code -P} parameter-file path at 511 bytes</strong> and to refuse
 * a {@code database_name} of 511 bytes or more. So the long-path run puts its spectrum files at a
 * depth of about 4 000 bytes through directories whose names are exactly 255 bytes long, its
 * database at about 500 bytes, and nests the project so that the longest {@code -N} base Comet is
 * given is within a few bytes of 511 -- and asserts each of those lengths, so the claim "near the
 * limits" is measured rather than described. Beyond those limits the product has no check of its
 * own -- the pre-run check holds {@code database_name} to 4095 bytes, so a 511-byte database passes
 * it and Comet refuses it, and a {@code -N} base over 511 bytes is truncated by Comet, which exits
 * 0 having written its outputs under a shorter name -- and the Phase 14 unit 8a report records that
 * as a defect for the orchestrator. This class proves only the envelope that works. Windows'
 * 260-character {@code MAX_PATH} case cannot be exercised on this host and is not claimed.
 *
 * <p>Files read outside this module: {@code scratch/phase05/artefacts} (both binaries), {@code
 * scratch/fixture} (both K562 mzML and the proteome), and {@code manifests/tools.json}, as {@link
 * RealComet} and {@link RealPercolator} document.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
class RealAwkwardPathRunTest {

    /** Linux's longest path in bytes, without its terminating NUL. */
    private static final int LINUX_PATH_MAX = 4095;

    /** Linux's longest file name in bytes. */
    private static final int LINUX_NAME_MAX = 255;

    /** The longest {@code -N} base and {@code -P} path Comet 2026.03.0 keeps whole, measured. */
    private static final int COMET_KEEPS = 511;

    /** The longest {@code database_name} Comet 2026.03.0 opens, measured. */
    private static final int COMET_DATABASE_KEEPS = 510;

    /**
     * Fails, naming the encoding and the remedy, unless this JVM spells file names in UTF-8. Called
     * first by every test here: an assertion, so a runner without a UTF-8 locale is red.
     */
    private static void requireUtf8FileNames() {
        assertEquals(
                "UTF-8",
                System.getProperty("sun.jnu.encoding"),
                "this JVM's file-name encoding (sun.jnu.encoding) is not UTF-8, so it cannot spell"
                        + " a non-ASCII path at all; fork the tests with LANG=C.UTF-8 or"
                        + " LC_ALL=C.UTF-8 in the environment (the system property cannot fix"
                        + " it, because the JVM reads the locale before properties apply)");
    }

    /** Where one run's tools, inputs and project are, and what it searched. */
    private record Staged(
            Path comet, Path percolator, List<Path> spectra, Path fasta, Path project) {}

    /** Stages the real tools and inputs at the given places. */
    private static Staged stage(
            Path tools, Path inputs, List<String> spectrumNames, Path fasta, Path project)
            throws IOException {
        Path comet = RealComet.stageComet(RealComet.NEWER, tools.resolve("comet"));
        Path percolator = RealPercolator.stage3071(tools.resolve("percolator"));
        Path lf = Files.createDirectories(tools.resolve("lf-copies"));
        List<Path> canonical = RealComet.spectra(lf);
        Files.createDirectories(inputs);
        List<Path> spectra = new ArrayList<>();
        for (int index = 0; index < canonical.size(); index++) {
            spectra.add(Files.move(canonical.get(index), inputs.resolve(spectrumNames.get(index))));
        }
        Files.createDirectories(RealComet.parentOf(fasta));
        RealComet.subset(fasta);
        return new Staged(comet, percolator, List.copyOf(spectra), fasta, project);
    }

    /** The real Comet and Percolator over the staged files, Limelight off. */
    private static Ran run(Staged staged)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        ToolOffer offer =
                RealPercolator.offer(
                        "3.07.1",
                        ToolOrigin.MANAGED,
                        staged.percolator(),
                        RealPercolator.probe("3.07.1", staged.percolator()),
                        RealPercolator.ADVISORIES_3071);
        PercolatorChoice choice =
                new PercolatorChoice(
                        new PercolatorSelection(
                                offer, staged.percolator(), RealComet.sha256(staged.percolator())),
                        PercolatorSettings.defaults(),
                        Set.of(),
                        PercolatorResolver.resolve(List.of(offer), Set.of()));
        SearchRequest request =
                new SearchRequest(
                                RealComet.model(
                                        RealComet.NEWER,
                                        staged.fasta(),
                                        DecoySource.COMET_INTERNAL_CONCATENATED,
                                        4),
                                staged.spectra(),
                                RealComet.selection(RealComet.NEWER, staged.comet()),
                                IndexMode.NONE)
                        .withPercolator(choice);
        try (RealProject project = RealProject.create(staged.project())) {
            PreparedRun prepared = project.prepare(request);
            RunResult result = project.run(prepared);
            return new Ran(
                    prepared,
                    result,
                    RunEvidence.manifest(prepared.layout()),
                    project.runner().launchesOf(staged.comet()).stream()
                            .map(launch -> launch.command().argv())
                            .toList());
        }
    }

    private record Ran(
            PreparedRun prepared,
            RunResult result,
            ProvenanceManifest manifest,
            List<List<String>> cometArgv) {}

    /**
     * What every successful run here must show, whatever its paths: success; the outputs named by
     * each file's natural base; a merged PIN holding the summed data rows of its two sources,
     * counted here; one Percolator PSM row per target row of it; and the record naming every input
     * by its exact path.
     */
    private static void assertARealRunSucceeded(Staged staged, Ran ran, List<String> bases)
            throws IOException {
        Path out = ran.prepared().layout().cometOutputDirectory();
        Path merged = ran.prepared().layout().mergedPinFile();
        List<String> expectedListing = new ArrayList<>();
        for (String base : bases) {
            expectedListing.add(base + ".pep.xml");
            expectedListing.add(base + ".pin");
        }
        assertAll(
                "the run, and what Comet wrote",
                () ->
                        assertEquals(
                                AttemptOutcome.SUCCEEDED,
                                ran.result().outcome(),
                                () -> "the run failed: " + ran.result().failures()),
                () ->
                        assertEquals(
                                new TreeSet<>(expectedListing),
                                new TreeSet<>(RunEvidence.listing(out)),
                                "Comet wrote each file's outputs under its own natural base"));
        long first = dataRows(out.resolve(bases.get(0) + ".pin"));
        long second = dataRows(out.resolve(bases.get(1) + ".pin"));
        Path psms = ran.prepared().percolatorOutputDirectory().orElseThrow().resolve("psms.tsv");
        assertAll(
                () -> assertTrue(first > 0 && second > 0, "both PINs hold rows"),
                () ->
                        assertEquals(
                                first + second,
                                dataRows(merged),
                                "the merged PIN holds the summed data rows of its sources"),
                () ->
                        assertEquals(
                                targetRows(merged),
                                dataRows(psms),
                                "Percolator read the merged PIN by its path: one PSM row per"
                                        + " target row"),
                () ->
                        assertEquals(
                                new TreeSet<>(
                                        List.of(
                                                staged.spectra().get(0).toString(),
                                                staged.spectra().get(1).toString(),
                                                staged.fasta().toString())),
                                inputsNamed(ran.manifest(), Set.of("spectrum", "fasta")),
                                "the record names every input by its exact path"),
                () ->
                        assertEquals(
                                List.of(
                                        staged.spectra().get(0).toString(),
                                        staged.spectra().get(1).toString()),
                                ran.cometArgv().stream()
                                        .map(argv -> argv.get(argv.size() - 1))
                                        .sorted(
                                                (left, right) ->
                                                        Integer.compare(
                                                                staged.spectra()
                                                                        .indexOf(Path.of(left)),
                                                                staged.spectra()
                                                                        .indexOf(Path.of(right))))
                                        .toList(),
                                "each Comet process was handed its spectrum file's exact path"),
                () ->
                        assertEquals(
                                List.of("comet", "comet"),
                                cometRecords(ran.manifest()),
                                "and the record holds both Comet executions"));
    }

    @Test
    @DisplayName(
            "Unicode: project, spectrum files, database and tools under non-ASCII names with"
                    + " spaces -- the real Comet and Percolator run succeeds and the record names"
                    + " every path exactly")
    void unicodePaths(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        requireUtf8FileNames();
        Path root = directory.toRealPath().resolve("données protéomiques").resolve("試料 ü");
        List<String> names = List.of("échantillon K562 ③.mzML", "k562 – répétition Ω.mzML");
        Staged staged =
                stage(
                        root.resolve("outils ö"),
                        root.resolve("entrées été"),
                        names,
                        root.resolve("base de données").resolve("protéome Ünïcode.fasta"),
                        root.resolve("projet été"));
        Ran ran = run(staged);
        assertARealRunSucceeded(staged, ran, List.of("échantillon K562 ③", "k562 – répétition Ω"));
        assertTrue(
                ran.prepared().layout().root().startsWith(root.resolve("projet été")),
                "the run is inside the non-ASCII project");
    }

    @Test
    @DisplayName(
            "long paths: spectrum files at about 4000 bytes through 255-byte names, a 500-byte"
                    + " database, and a -N base within a few bytes of Comet's 511 -- the real run"
                    + " succeeds")
    void longPaths(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        Path root = directory.toRealPath();
        String base = "k".repeat(119) + "_3";
        String otherBase = "k".repeat(119) + "_4";
        List<String> names = List.of(base + ".mzML", otherBase + ".mzML");
        String longest = "n".repeat(LINUX_NAME_MAX);
        Path inputs = root.resolve("in");
        while (inputs.toString().length() + 1 + LINUX_NAME_MAX + 1 + names.get(0).length() + 1
                <= LINUX_PATH_MAX - 40) {
            inputs = inputs.resolve(longest);
        }
        /*
         * One shorter directory takes the first spectrum file's path to exactly
         * LINUX_PATH_MAX - 40 bytes. Without it the length depended on the temporary directory's
         * own, which JUnit draws at random: one character shorter, and the path came out at 3998,
         * under the 4000 asserted below (seen once in the e2e gate's failure-path run).
         */
        int pad = LINUX_PATH_MAX - 40 - inputs.toString().length() - names.get(0).length() - 2;
        if (pad > 0) {
            inputs = inputs.resolve("p".repeat(pad));
        }
        Path fasta = within(root.resolve("db"), 500 - "/db.fasta".length()).resolve("db.fasta");
        /*
         * The run directory is <project>/runs/<20261023T070115Z-run-0001>/: the longest -N base
         * is <run>/outputs/comet/<base> and the parameter file <run>/parameters/comet.params,
         * whose suffixes after <project> are 46 + base and 55 bytes. The project is nested so that
         * the -N base lands within 8 bytes of Comet's 511.
         */
        int project =
                COMET_KEEPS
                        - 4
                        - "/runs/20261023T070115Z-run-0001/outputs/comet/".length()
                        - base.length();
        Staged staged =
                stage(
                        root.resolve("tools"),
                        inputs,
                        names,
                        fasta,
                        within(root.resolve("p"), project));
        Ran ran = run(staged);
        String nBase = ran.prepared().layout().cometOutputBase(base).toString();
        String params = ran.prepared().layout().cometParamsFile().toString();
        String spectrum = staged.spectra().get(0).toString();
        assertAll(
                "the run is as near the limits as this class claims",
                () ->
                        assertTrue(
                                spectrum.length() >= 4000 && spectrum.length() <= LINUX_PATH_MAX,
                                () -> "the spectrum file's path is " + spectrum.length()),
                () ->
                        assertTrue(
                                Path.of(spectrum).getNameCount() > 10
                                        && namesOf(Path.of(spectrum)).contains(LINUX_NAME_MAX),
                                "it passes through directories whose names are exactly 255"),
                () ->
                        assertTrue(
                                nBase.length() > COMET_KEEPS - 8 && nBase.length() <= COMET_KEEPS,
                                () -> "the -N base is " + nBase.length() + ": " + nBase),
                () ->
                        assertTrue(
                                params.length() <= COMET_KEEPS,
                                () -> "the -P path is " + params.length()),
                () ->
                        assertTrue(
                                fasta.toString().length() >= 490
                                        && fasta.toString().length() <= COMET_DATABASE_KEEPS,
                                () -> "the database path is " + fasta.toString().length()));
        assertARealRunSucceeded(staged, ran, List.of(base, otherBase));
    }

    /** A directory under {@code start} whose path is exactly {@code length} bytes, created. */
    private static Path within(Path start, int length) throws IOException {
        Path at = start;
        while (at.toString().length() + 1 + 100 < length) {
            at = at.resolve("d".repeat(99));
        }
        int remaining = length - at.toString().length() - 1;
        if (remaining > 0) {
            at = at.resolve("e".repeat(remaining));
        }
        assertEquals(length, at.toString().length(), "the constructed directory's length");
        return Files.createDirectories(at);
    }

    private static List<Integer> namesOf(Path path) {
        List<Integer> lengths = new ArrayList<>();
        for (Path name : path) {
            lengths.add(name.toString().getBytes(StandardCharsets.UTF_8).length);
        }
        return lengths;
    }

    /** The data rows of a tab-separated file with one header: its lines but the first. */
    private static long dataRows(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.skip(1).filter(line -> !line.isEmpty()).count();
        }
    }

    /** The PIN rows whose second column, Label, is 1. */
    private static long targetRows(Path pin) throws IOException {
        try (Stream<String> lines = Files.lines(pin, StandardCharsets.UTF_8)) {
            return lines.skip(1).filter(line -> "1".equals(line.split("\t", 3)[1])).count();
        }
    }

    private static Set<String> inputsNamed(ProvenanceManifest manifest, Set<String> roles) {
        Set<String> paths = new TreeSet<>();
        for (FileRecord file : manifest.files()) {
            if (file.direction() == FileDirection.INPUT && roles.contains(file.role())) {
                paths.add(file.path().toString());
            }
        }
        return paths;
    }

    private static List<String> cometRecords(ProvenanceManifest manifest) {
        List<String> names = new ArrayList<>();
        for (ToolRecord tool : manifest.tools()) {
            if ("comet".equals(tool.name())) {
                names.add(tool.name());
            }
        }
        return names;
    }
}
