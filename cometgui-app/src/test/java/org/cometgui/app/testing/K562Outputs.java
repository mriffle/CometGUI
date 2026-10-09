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

package org.cometgui.app.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.cometgui.results.filtering.store.TableKind;

/**
 * The real Percolator 3.07.1 output of Phase 00's K562 search, for the Results section's GUI gate
 * tests: the four tables and the weights, copied out of the gitignored {@code
 * scratch/scientific-path/percolator-3.07.1/} into a test's own directory, each held to its SHA-256
 * -- the digests {@code cometgui-results/src/test/resources/org/cometgui/results/real-k562/
 * PROVENANCE.txt} records, typed out again here.
 *
 * <p><strong>D-006</strong>: CometGUI does not redistribute spectrum or FASTA data, so this output
 * is never committed; the tests copy it into a temporary directory. A missing or changed file
 * <strong>fails</strong> the test, naming the command that makes it -- never skips it.
 *
 * <p>The counts {@link #AWK} holds are that record's {@code awk}-pinned counts, typed out: for each
 * table and cutoff, total, passing (q &lt;= cutoff), failing and unknown. The command is in that
 * record, verbatim; it was re-run on 2026-10-09 by the phase 10 unit 9 agent with the same output.
 */
public final class K562Outputs {

    /** The directory, relative to the repository root. */
    public static final String DIRECTORY = "scratch/scientific-path/percolator-3.07.1";

    /** How the directory is made. */
    public static final String REMAKE =
            "bash scripts/feasibility/run_scientific_path.sh (Phase 00; see"
                    + " docs/feasibility/scientific-path.rst)";

    /** The weights file's name in the directory. */
    public static final String WEIGHTS = "weights.txt";

    /** The weights' SHA-256. */
    public static final String WEIGHTS_SHA256 =
            "1a92c0ac17efe38ffce7caf0855f273746310b1cddb9b1e409b435fb02692208";

    /** The cutoffs {@link #AWK} has counts at, in the order of its lists. */
    public static final List<String> CUTOFFS = List.of("0", "0.005", "0.01", "0.05", "1");

    /** Each table's file name in the directory. */
    public static final Map<TableKind, String> FILES =
            Map.of(
                    TableKind.TARGET_PSMS, "psms.target.txt",
                    TableKind.DECOY_PSMS, "psms.decoy.txt",
                    TableKind.TARGET_PEPTIDES, "peptides.target.txt",
                    TableKind.DECOY_PEPTIDES, "peptides.decoy.txt");

    private static final Map<TableKind, String> SHA256 =
            Map.of(
                    TableKind.TARGET_PSMS,
                    "458b79b5a423f57406421d31ddd57b946eb8096ab6935447efd1e98e48f7fe9e",
                    TableKind.DECOY_PSMS,
                    "e1245d4f3fd128a5c7efcf89bc0b0ab0991d3f31bff437caa23bec3f437fc4c1",
                    TableKind.TARGET_PEPTIDES,
                    "579bffd2ae250ae2934eb5695bdf86376677b45589e7dbdf02f00c712312c114",
                    TableKind.DECOY_PEPTIDES,
                    "fe1db32901b00196b7c8eae2ed3194215620af44975f475be16d8155112a3fd7");

    /**
     * The {@code awk}-pinned counts: per table, per cutoff of {@link #CUTOFFS}, {@code total
     * passing failing unknown}.
     */
    public static final Map<TableKind, List<String>> AWK =
            Map.of(
                    TableKind.TARGET_PSMS,
                    List.of(
                            "3897 0 3897 0",
                            "3897 982 2915 0",
                            "3897 1026 2871 0",
                            "3897 1171 2726 0",
                            "3897 3897 0 0"),
                    TableKind.DECOY_PSMS,
                    List.of(
                            "2773 0 2773 0",
                            "2773 4 2769 0",
                            "2773 9 2764 0",
                            "2773 57 2716 0",
                            "2773 2773 0 0"),
                    TableKind.TARGET_PEPTIDES,
                    List.of(
                            "2985 0 2985 0",
                            "2985 567 2418 0",
                            "2985 603 2382 0",
                            "2985 681 2304 0",
                            "2985 2985 0 0"),
                    TableKind.DECOY_PEPTIDES,
                    List.of(
                            "2359 0 2359 0",
                            "2359 2 2357 0",
                            "2359 5 2354 0",
                            "2359 33 2326 0",
                            "2359 2359 0 0"));

    private K562Outputs() {}

    /**
     * The {@code awk}-pinned counts of a table at a cutoff, as the four texts the Results section
     * shows.
     *
     * @param kind the table
     * @param cutoff one of {@link #CUTOFFS}
     * @return total, passing, failing, unknown
     */
    public static List<String> awk(TableKind kind, String cutoff) {
        int position = CUTOFFS.indexOf(cutoff);
        if (position < 0) {
            return fail("no awk-pinned count at " + cutoff);
        }
        return List.of(AWK.get(kind).get(position).split(" "));
    }

    /**
     * Copies the four tables into a directory, each checked against its SHA-256.
     *
     * @param directory where to copy them
     * @return each table's copy
     * @throws IOException if one cannot be copied
     */
    public static Map<TableKind, Path> tables(Path directory) throws IOException {
        Map<TableKind, Path> copies = new EnumMap<>(TableKind.class);
        for (Map.Entry<TableKind, String> file : FILES.entrySet()) {
            copies.put(file.getKey(), copy(file.getValue(), SHA256.get(file.getKey()), directory));
        }
        return copies;
    }

    /**
     * Copies the weights into a directory, checked against its SHA-256.
     *
     * @param directory where to copy them
     * @return the copy
     * @throws IOException if they cannot be copied
     */
    public static Path weights(Path directory) throws IOException {
        return copy(WEIGHTS, WEIGHTS_SHA256, directory);
    }

    private static Path copy(String name, String sha256, Path directory) throws IOException {
        Path original = RealSearch.repositoryRoot().resolve(DIRECTORY).resolve(name);
        if (!Files.isRegularFile(original)) {
            return fail(
                    "the real K562 Percolator output "
                            + original
                            + " is absent (D-006: it is never committed). Make it with: "
                            + REMAKE
                            + ". This test fails rather than skips: a gate that stops reading"
                            + " the real output stops proving anything.");
        }
        assertEquals(sha256, ResultRuns.sha256(original), "the SHA-256 of " + original);
        Files.createDirectories(directory);
        Path copy = directory.resolve(name);
        Files.copy(original, copy);
        assertEquals(sha256, ResultRuns.sha256(copy), "the SHA-256 of the copy " + copy);
        return copy;
    }
}
