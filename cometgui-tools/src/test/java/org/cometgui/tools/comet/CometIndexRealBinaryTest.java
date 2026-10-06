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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cometgui.domain.params.FastaDecoyCensus;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The readers against what the REAL pinned Comet binaries write: both releases build a fragment-ion
 * ({@code -i}) and a peptide ({@code -j}) index, and {@link CometIndexHeaderReader} reads each
 * whole file into exactly what its bytes say; the committed captures ({@link IndexHeaders}) are the
 * same bytes; and {@link FastaDecoyScanner} counts the real proteome subset and a decoy FASTA built
 * from it.
 *
 * <p>Each binary is the mirror's ({@code scratch/phase05/artefacts}), checked against the SHA-256
 * the artefact manifest pins before it runs, and run through {@link ToolRunner} over {@link
 * ProcessService}. Each index is built as the workflow will build one (design decision P8-8): in a
 * directory of its own, with {@code -D} naming a symbolic link to the FASTA inside it, so that the
 * index lands there and nothing is written beside the FASTA -- which this test checks.
 *
 * <p>Inputs, never committed ({@code D-006}): the UniProt human proteome {@code
 * scratch/fixture/UP000005640_9606.fasta}, held to its SHA-256; its first 1000 records, held to
 * theirs ({@code 5005d961...}, the validation corpus's subset); and a target-decoy FASTA built here
 * from the subset -- each record followed by {@code DECOY_} and its reversed sequence, 60 residues
 * a line -- held to its own. Missing or changed inputs FAIL this test; nothing is skipped. Comet's
 * threads are bounded ({@code num_threads = 4}); each build takes about a second.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class CometIndexRealBinaryTest {

    /** The proteome, from the D-006 local fixture. */
    static final String PROTEOME = "scratch/fixture/UP000005640_9606.fasta";

    static final String PROTEOME_SHA256 =
            "2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0";

    /** Its first 1000 records: the validation corpus's subset. */
    static final String SUBSET_SHA256 =
            "5005d9614b20f944915616201389cc7529d209a0f039278cae29cb356192558f";

    /** The subset with a reversed DECOY_ record after each record. */
    static final String DECOY_SHA256 =
            "ff098f19bba637aa33a736a01ad59cf35911292c9fb7598324ed02a7d5b07a99";

    /**
     * One pinned release, typed out from the artefact manifest (this module cannot read it).
     *
     * @param release the release
     * @param file the mirror's file name
     * @param sha256 the manifest's SHA-256
     */
    record Release(String release, String file, String sha256) {

        @Override
        public String toString() {
            return "Comet " + release;
        }
    }

    static final List<Release> RELEASES =
            List.of(
                    new Release(
                            IndexHeaders.NEWER,
                            "v2026.03.0__comet.linux.exe",
                            "ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed"),
                    new Release(
                            IndexHeaders.OLDER,
                            "v2026.02.2__comet.linux.exe",
                            "af515b6ed5a17efafff7277a6a9c73cee97e26d38f3c9b2a8da16adaa44e6d9e"));

    private static final Map<String, Map<IndexMode, Path>> INDEXES = new HashMap<>();

    private static final Map<String, Path> MODS = new HashMap<>();

    private static Path subset;

    private static Path decoys;

    private static Set<String> besideTheFasta;

    static Stream<Arguments> builds() {
        List<Arguments> builds = new ArrayList<>();
        for (Release release : RELEASES) {
            for (IndexMode mode : List.of(IndexMode.FRAGMENT_ION, IndexMode.PEPTIDE)) {
                builds.add(Arguments.of(release, mode));
            }
        }
        return builds.stream();
    }

    private static ToolRunner runner() {
        return new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(120));
    }

    @BeforeAll
    static void buildEverything(@TempDir Path scratch) throws IOException {
        Path root = UpstreamArtefacts.repositoryRoot();
        Path proteome = root.resolve(PROTEOME);
        assertTrue(
                Files.isRegularFile(proteome),
                PROTEOME
                        + " does not exist. It is a local, gitignored input (D-006); refill it with"
                        + " python3 scripts/feasibility/fetch_ephemeral_input.py, which fetches by"
                        + " checksum.");
        assertEquals(PROTEOME_SHA256, UpstreamArtefacts.sha256(proteome), PROTEOME);
        Path inputs = Files.createDirectories(scratch.resolve("inputs"));
        subset = inputs.resolve("subset.fasta");
        Files.write(subset, firstRecords(proteome, 1000));
        assertEquals(SUBSET_SHA256, UpstreamArtefacts.sha256(subset), "the first 1000 records");
        decoys = inputs.resolve("target-decoy.fasta");
        Files.write(decoys, withReversedDecoys(Files.readAllBytes(subset)));
        assertEquals(DECOY_SHA256, UpstreamArtefacts.sha256(decoys), "the target-decoy FASTA");

        for (Release release : RELEASES) {
            Path binary =
                    UpstreamArtefacts.executableCopy(
                            release.file(),
                            scratch.resolve("bin-" + release.release()).resolve("comet"));
            assertEquals(
                    release.sha256(),
                    UpstreamArtefacts.sha256(binary),
                    "the staged Comet is not the bytes the manifest pins");
            Path dump = Files.createDirectories(scratch.resolve("q-" + release.release()));
            ToolRunOutcome written =
                    runner().run(new ToolCommand(List.of(binary.toString(), "-q"), dump, Map.of()));
            assertTrue(written.exitedZero(), written.joinedOutput());
            String params =
                    edited(
                            Files.readString(
                                    dump.resolve(CometCapabilityProbe.WRITTEN_PARAMETERS_FILE),
                                    StandardCharsets.ISO_8859_1),
                            Map.of(
                                    "database_name", "subset.fasta",
                                    "spectral_library_name", "",
                                    "num_threads", "4"));
            Map<IndexMode, Path> built = new EnumMap<>(IndexMode.class);
            for (IndexMode mode : List.of(IndexMode.FRAGMENT_ION, IndexMode.PEPTIDE)) {
                Path cache =
                        Files.createDirectories(
                                scratch.resolve(
                                        "cache-" + release.release() + "-" + mode.wireName()));
                Files.createSymbolicLink(cache.resolve("subset.fasta"), subset);
                Files.writeString(
                        cache.resolve("comet.params"), params, StandardCharsets.ISO_8859_1);
                ToolRunOutcome outcome =
                        runner().run(
                                        new ToolCommand(
                                                List.of(
                                                        binary.toString(),
                                                        "-Pcomet.params",
                                                        mode.buildFlag().orElseThrow(),
                                                        "-Dsubset.fasta"),
                                                cache,
                                                Map.of()));
                assertTrue(outcome.exitedZero(), outcome.joinedOutput());
                built.put(mode, cache.resolve("subset.fasta.idx"));
            }
            INDEXES.put(release.release(), built);
            Map<String, String> modsEdits = new LinkedHashMap<>();
            modsEdits.put("database_name", "subset.fasta");
            modsEdits.put("spectral_library_name", "");
            modsEdits.put("num_threads", "4");
            for (String[] edit : IndexHeaders.MODS_EDITS) {
                modsEdits.put(edit[0], edit[1]);
            }
            Path modsCache =
                    Files.createDirectories(
                            scratch.resolve("cache-" + release.release() + "-mods"));
            Files.createSymbolicLink(modsCache.resolve("subset.fasta"), subset);
            Files.writeString(
                    modsCache.resolve("comet.params"),
                    edited(
                            Files.readString(
                                    dump.resolve(CometCapabilityProbe.WRITTEN_PARAMETERS_FILE),
                                    StandardCharsets.ISO_8859_1),
                            modsEdits),
                    StandardCharsets.ISO_8859_1);
            ToolRunOutcome mods =
                    runner().run(
                                    new ToolCommand(
                                            List.of(
                                                    binary.toString(),
                                                    "-Pcomet.params",
                                                    "-i",
                                                    "-Dsubset.fasta"),
                                            modsCache,
                                            Map.of()));
            assertTrue(mods.exitedZero(), mods.joinedOutput());
            MODS.put(release.release(), modsCache.resolve("subset.fasta.idx"));
        }
        try (Stream<Path> listed = Files.list(inputs)) {
            besideTheFasta =
                    listed.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    /** The first records of a FASTA, through the line before the next record's header. */
    static byte[] firstRecords(Path fasta, int records) throws IOException {
        String text = Files.readString(fasta, StandardCharsets.ISO_8859_1);
        int end = -1;
        for (int record = 0; record < records; record++) {
            end = text.indexOf("\n>", end + 1);
            assertTrue(end > 0, fasta + " has fewer than " + records + " records");
        }
        return (text.substring(0, end) + "\n").getBytes(StandardCharsets.ISO_8859_1);
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

    /** A parameter file with some declarations' values replaced, each of which must be there. */
    static String edited(String params, Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        Map<String, String> pending = new HashMap<>(values);
        for (String line : params.split("\n", -1)) {
            String name = line.contains(" = ") ? line.substring(0, line.indexOf(" = ")) : "";
            if (pending.containsKey(name)) {
                out.append(name).append(" = ").append(pending.remove(name));
            } else {
                out.append(line);
            }
            out.append('\n');
        }
        assertEquals(Map.of(), pending, "declarations the -q file does not have");
        return out.substring(0, out.length() - 1);
    }

    private static byte[] headerOf(Path index) throws IOException {
        byte[] head;
        try (InputStream in = Files.newInputStream(index)) {
            head = in.readNBytes(CometIndexHeaderReader.HEADER_LIMIT);
        }
        String text = new String(head, StandardCharsets.ISO_8859_1);
        int end = text.indexOf("\n\n");
        assertTrue(end > 0, index + " has no empty line ending its header");
        return Arrays.copyOf(head, end + 2);
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("builds")
    @DisplayName("the committed capture is the header the real binary writes, byte for byte")
    void captureIsReal(Release release, IndexMode mode) throws IOException {
        Path index = INDEXES.get(release.release()).get(mode);
        assertTrue(
                Files.size(index) > CometIndexHeaderReader.HEADER_LIMIT,
                "a real index is larger than the reader's limit, so the limit is exercised");
        assertArrayEquals(
                IndexHeaders.bytes(release.release(), mode),
                headerOf(index),
                release + " " + mode + ": the header differs from the committed capture");
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("builds")
    @DisplayName("the reader reads each whole real index into exactly what its header says")
    void readerOnRealIndexes(Release release, IndexMode mode) throws IOException {
        Path index = INDEXES.get(release.release()).get(mode);
        assertEquals(
                IndexHeaders.expected(release.release(), index, mode),
                CometIndexHeaderReader.read(index));
    }

    static Stream<Release> releases() {
        return RELEASES.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    @DisplayName("the modifications capture is the real header too, and reads as typed")
    void modsCaptureIsReal(Release release) throws IOException {
        Path index = MODS.get(release.release());
        assertArrayEquals(
                IndexHeaders.bytes(release.release(), IndexHeaders.MODS), headerOf(index));
        assertEquals(
                IndexHeaders.mods(release.release(), index), CometIndexHeaderReader.read(index));
    }

    @Test
    @DisplayName("-D naming a link inside the cache leaves nothing beside the FASTA")
    void nothingBesideTheFasta() {
        assertEquals(Set.of("subset.fasta", "target-decoy.fasta"), besideTheFasta);
    }

    @Test
    @DisplayName("the real subset holds 1000 records and no decoy")
    void subsetCensus() throws IOException {
        assertEquals(
                new FastaDecoyCensus(subset, "DECOY_", 1000, 0, Optional.empty()),
                FastaDecoyScanner.scan(subset, "DECOY_"));
    }

    @Test
    @DisplayName("the target-decoy FASTA holds 2000 records, 1000 of them decoys")
    void decoyCensus() throws IOException {
        assertEquals(
                new FastaDecoyCensus(
                        decoys,
                        "DECOY_",
                        2000,
                        1000,
                        Optional.of("DECOY_sp|A0A075B6H9|LV469_HUMAN")),
                FastaDecoyScanner.scan(decoys, "DECOY_"));
    }

    @Test
    @DisplayName("the real subset, as a CRLF copy, has the same census")
    void crlfCensus(@TempDir Path directory) throws IOException {
        Path crlf = directory.resolve("subset-crlf.fasta");
        Files.writeString(
                crlf,
                Files.readString(subset, StandardCharsets.ISO_8859_1).replace("\n", "\r\n"),
                StandardCharsets.ISO_8859_1);
        assertEquals(1000, FastaDecoyScanner.scan(crlf, "DECOY_").records());
        assertTrue(
                Pattern.compile("\r\n")
                        .matcher(Files.readString(crlf, StandardCharsets.ISO_8859_1))
                        .find());
    }
}
