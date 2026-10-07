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

package org.cometgui.tools.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.OutputBaseNames;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.comet.CometCapabilityProbe;
import org.cometgui.tools.comet.CometIndexCommand;
import org.cometgui.tools.comet.CometSearchCommands;
import org.cometgui.tools.comet.PinMergeRecord;
import org.cometgui.tools.comet.PinMerger;
import org.cometgui.tools.process.ProcessService;

/**
 * A merged PIN made by the REAL Comet path, for the tests that run Percolator on one (design
 * decision P9-13: never a hand-typed PIN): the pinned Comet binary from the mirror, the two K562
 * mzML files of {@code scratch/fixture} with their line endings repaired, the UniProt proteome's
 * first 1000 records, {@code decoy_search = 1}, one {@code -N} search per file through {@link
 * CometSearchCommands} and the process service, then {@link PinMerger}.
 *
 * <p>Every input is held to its SHA-256 before use, and a missing one FAILS with how to refill it;
 * nothing skips. The hashes are the ones {@code tools.comet}'s {@code CometAdapterRealBinaryTest}
 * and {@code CometIndexRealBinaryTest} pin, typed again here: those constants and their FASTA and
 * parameter-file helpers are package-private test code in another package, and this module does not
 * publish a test jar, so they are deliberately repeated -- test scaffolding, not a second parser.
 *
 * <p>{@link #fragmentIndexSearch} is Phase 08's finding made reproducible: Comet 2026.02.2
 * searching a fragment-ion index it built with {@code decoy_search = 1} writes <strong>no decoy
 * row</strong>.
 */
public final class RealCometSearch {

    /** Comet 2026.03.0, the mirror's file and the artefact manifest's SHA-256. */
    public static final Release NEWER =
            new Release(
                    "2026.03.0",
                    "v2026.03.0__comet.linux.exe",
                    "ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed");

    /** Comet 2026.02.2, the mirror's file and the artefact manifest's SHA-256. */
    public static final Release OLDER =
            new Release(
                    "2026.02.2",
                    "v2026.02.2__comet.linux.exe",
                    "af515b6ed5a17efafff7277a6a9c73cee97e26d38f3c9b2a8da16adaa44e6d9e");

    private static final String MZML_3 = "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML";
    private static final String MZML_3_FETCHED =
            "cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7";
    private static final String MZML_3_LF =
            "a562f6e642b2880bece3134c43aa9581124788926bd1b9305c2db0bb506954da";
    private static final String MZML_4 = "scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML";
    private static final String MZML_4_FETCHED =
            "d30aa5af4b15e1c927616fce4dacfd68c6249e6cc2b7461913481c12d8408cfa";
    private static final String MZML_4_LF =
            "602aad75e18257feabdb94ece0fa82e19e5f17153eee4fb89875976920855c82";
    private static final String PROTEOME = "scratch/fixture/UP000005640_9606.fasta";
    private static final String PROTEOME_SHA256 =
            "2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0";
    private static final String SUBSET_SHA256 =
            "5005d9614b20f944915616201389cc7529d209a0f039278cae29cb356192558f";

    private static final String REFILL =
            " It is a local, gitignored input (D-006); refill it with python3"
                    + " scripts/feasibility/fetch_ephemeral_input.py, which fetches by checksum."
                    + " This test fails rather than skips: a Percolator run on a hand-typed PIN"
                    + " would prove nothing about the real one.";

    private RealCometSearch() {}

    /**
     * One pinned Comet release.
     *
     * @param release the release
     * @param file the mirror's file name
     * @param sha256 the artefact manifest's SHA-256
     */
    public record Release(String release, String file, String sha256) {}

    /**
     * A finished search.
     *
     * @param run the run, whose {@link RunLayout#mergedPinFile()} is the merged PIN
     * @param merge the merge's record
     * @param searches the two searches' outcomes, in input order
     */
    public record Search(RunLayout run, PinMergeRecord merge, List<ToolRunOutcome> searches) {

        /**
         * Validates the record.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Search {
            java.util.Objects.requireNonNull(run, "run");
            java.util.Objects.requireNonNull(merge, "merge");
            searches = List.copyOf(searches);
        }

        @Override
        public List<ToolRunOutcome> searches() {
            return List.copyOf(searches);
        }
    }

    private static ToolRunner runner() {
        return new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(5));
    }

    /**
     * Searches both K562 files against the FASTA subset, as the workflow does with no index.
     *
     * @param root an empty directory for the binary, the inputs and the run
     * @param release the Comet release
     * @return the search, merged
     * @throws IOException if a file cannot be written
     */
    public static Search fastaSearch(Path root, Release release) throws IOException {
        return search(root, release, false);
    }

    /**
     * Builds a fragment-ion index with the run's own parameters, {@code decoy_search = 1} included,
     * and searches both files against it with {@code -D}.
     *
     * @param root an empty directory for the binary, the inputs, the index and the run
     * @param release the Comet release
     * @return the search, merged
     * @throws IOException if a file cannot be written
     */
    public static Search fragmentIndexSearch(Path root, Release release) throws IOException {
        return search(root, release, true);
    }

    private static Search search(Path root, Release release, boolean fragmentIndex)
            throws IOException {
        Path repository = UpstreamArtefacts.repositoryRoot();
        Path binary = UpstreamArtefacts.executableCopy(release.file(), root.resolve("bin/comet"));
        assertEquals(
                release.sha256(),
                UpstreamArtefacts.sha256(binary),
                "the staged Comet " + release.release() + " is not the bytes the manifest pins");
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        Path k3 = lfCopy(repository.resolve(MZML_3), MZML_3_FETCHED, inputs.resolve("k562_3.mzML"));
        assertEquals(MZML_3_LF, UpstreamArtefacts.sha256(k3), "the LF-repaired K562_3");
        Path k4 = lfCopy(repository.resolve(MZML_4), MZML_4_FETCHED, inputs.resolve("k562_4.mzML"));
        assertEquals(MZML_4_LF, UpstreamArtefacts.sha256(k4), "the LF-repaired K562_4");
        Path proteome = repository.resolve(PROTEOME);
        assertTrue(Files.isRegularFile(proteome), proteome + " does not exist." + REFILL);
        assertEquals(PROTEOME_SHA256, UpstreamArtefacts.sha256(proteome), "the proteome");
        Path fasta = Files.write(inputs.resolve("subset.fasta"), firstRecords(proteome, 1000));
        assertEquals(SUBSET_SHA256, UpstreamArtefacts.sha256(fasta), "the first 1000 records");

        RunLayout run = new RunLayout(root.resolve("project one/runs/20261007T120000Z-run-0001"));
        for (Path directory : run.directories()) {
            Files.createDirectories(directory);
        }
        Files.writeString(
                run.cometParamsFile(),
                parameters(binary, root, fasta),
                StandardCharsets.ISO_8859_1);
        CometSearchCommands commands;
        if (fragmentIndex) {
            Path cache = Files.createDirectories(root.resolve("index cache"));
            CometIndexCommand index =
                    new CometIndexCommand(
                            binary, run.cometParamsFile(), IndexMode.FRAGMENT_ION, cache, fasta);
            index.linkDatabase();
            ToolRunOutcome built = runner().run(index.command());
            assertTrue(built.exitedZero(), built.joinedOutput());
            commands = CometSearchCommands.databaseOverride(binary, run, index.indexFile());
        } else {
            commands = CometSearchCommands.databaseFromParameterFile(binary, run);
        }
        List<OutputBase> spectra = OutputBaseNames.derive(List.of(k3, k4));
        List<ToolRunOutcome> outcomes = new ArrayList<>();
        for (ToolCommand command : commands.commands(spectra)) {
            ToolRunOutcome outcome = runner().run(command);
            assertTrue(outcome.exitedZero(), command + "\n" + outcome.joinedOutput());
            outcomes.add(outcome);
        }
        PinMergeRecord merge =
                PinMerger.merge(
                        run,
                        List.of(run.pinFile("k562_3"), run.pinFile("k562_4")),
                        RealCometSearch::hashes);
        return new Search(run, merge, outcomes);
    }

    /** The binary's own {@code -q} parameter file with the search's values. */
    private static String parameters(Path binary, Path root, Path fasta) throws IOException {
        Path dump = Files.createDirectories(root.resolve("q"));
        ToolRunOutcome written =
                runner().run(
                                new ToolCommand(
                                        List.of(
                                                binary.toString(),
                                                CometCapabilityProbe.COMPLETE_PARAMETERS_ARGUMENT),
                                        dump,
                                        Map.of()));
        assertTrue(written.exitedZero(), written.joinedOutput());
        Map<String, String> values = new TreeMap<>();
        values.put("database_name", fasta.toString());
        values.put("spectral_library_name", "");
        values.put("num_threads", "4");
        values.put("decoy_search", "1");
        values.put("output_pepxmlfile", "1");
        values.put("output_percolatorfile", "1");
        return edited(
                Files.readString(
                        dump.resolve(CometCapabilityProbe.WRITTEN_PARAMETERS_FILE),
                        StandardCharsets.ISO_8859_1),
                values);
    }

    /** A parameter file with some declarations' values replaced, each of which must be there. */
    private static String edited(String params, Map<String, String> values) {
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

    /** The first records of a FASTA, through the line before the next record's header. */
    private static byte[] firstRecords(Path fasta, int records) throws IOException {
        String text = Files.readString(fasta, StandardCharsets.ISO_8859_1);
        int end = -1;
        for (int record = 0; record < records; record++) {
            end = text.indexOf("\n>", end + 1);
            assertTrue(end > 0, fasta + " has fewer than " + records + " records");
        }
        return (text.substring(0, end) + "\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Copies a fetched mzML held to its hash, dropping every CR byte. */
    private static Path lfCopy(Path fetched, String sha256, Path copy) throws IOException {
        assertTrue(Files.isRegularFile(fetched), fetched + " does not exist." + REFILL);
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

    /**
     * Both digests of a file, for the merge record.
     *
     * @param file the file
     * @return its MD5 and SHA-256
     * @throws IOException if it cannot be read
     */
    public static FileHashes hashes(Path file) throws IOException {
        try {
            String md5 =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("MD5")
                                            .digest(Files.readAllBytes(file)));
            return new FileHashes(md5, UpstreamArtefacts.sha256(file));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides MD5", impossible);
        }
    }
}
