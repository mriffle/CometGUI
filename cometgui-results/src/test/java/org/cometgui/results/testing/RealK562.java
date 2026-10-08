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

package org.cometgui.results.testing;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 00's real Comet + Percolator search of the {@code D-006} K562 fixture (two Crux K562 mzML
 * files against the UniProt human proteome), Percolator 3.07.1 and 3.09 output, in the gitignored
 * {@code scratch/scientific-path/percolator-3.07.1/} and {@code percolator-3.09/}. {@code D-006}:
 * CometGUI does not redistribute spectrum or FASTA data, so this K562-derived output is never
 * committed; the resource {@code org/cometgui/results/real-k562/PROVENANCE.txt} records its
 * provenance, digests and counts, and not the data.
 *
 * <p>Every file is held to its SHA-256 pinned here (measured 2026-10-08) before a path is returned;
 * a missing or different file fails the test, naming how it was made. Nothing skips.
 *
 * <p>The expected counts were taken with {@code mawk 1.3.4} on 2026-10-08, once per file and
 * cutoff, and typed in here -- none comes from Java:
 *
 * <pre>{@code
 * LC_ALL=C awk -F'\t' -v c="$c" 'NR>1 { t++; q=$3;
 *   if (q !~ /^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?$/ || q+0 < 0 || q+0 > 1) u++;
 *   else if (q+0 <= c+0) p++; else f++ }
 *   END { printf "%d %d %d %d\n", t, p, f, u }' "$file"
 * }</pre>
 *
 * <p>It prints total, passing, failing and unknown. The regular expression is there because {@code
 * awk}'s {@code $3+0} turns {@code nan}, {@code inf} and {@code 0,005} into numbers; over the
 * constructed {@code psms-unknown-q.tsv} a bare {@code $3+0<=0.01} counts 10 passing rows where
 * there are 5. None of the eight K562 tables has a non-numeric, empty or out-of-range q-value
 * (unknown is 0 throughout), none writes a q-value in exponent form, and none holds a q-value of
 * exactly 0, 0.005, 0.01, 0.05 or 1, so the boundary itself is exercised by the large fixture and
 * the checked-in tables, not here.
 */
public final class RealK562 {

    /** Where Phase 00's run wrote its output, relative to the repository root. */
    public static final String DIRECTORY = "scratch/scientific-path";

    /** The cutoffs the {@code awk} counts were taken at. */
    public static final List<String> CUTOFFS = List.of("0", "0.005", "0.01", "0.05", "1");

    private static final String REMAKE =
            "It is Phase 00's real K562 Comet + Percolator search (D-006: never committed),"
                    + " written by bash scripts/feasibility/run_scientific_path.sh as"
                    + " docs/feasibility/scientific-path.rst describes; a re-run need not"
                    + " reproduce these exact bytes, so re-pin and re-count with the awk command"
                    + " in org/cometgui/results/real-k562/PROVENANCE.txt if it is ever remade.";

    private RealK562() {}

    /** One real Percolator table, its digest, its row count and its {@code awk} counts. */
    public enum Table {
        /** Percolator 3.07.1 target PSMs. */
        V3071_TARGET_PSMS(
                "percolator-3.07.1/psms.target.txt",
                "458b79b5a423f57406421d31ddd57b946eb8096ab6935447efd1e98e48f7fe9e",
                3897,
                0,
                982,
                1026,
                1171,
                3897),
        /** Percolator 3.07.1 decoy PSMs. */
        V3071_DECOY_PSMS(
                "percolator-3.07.1/psms.decoy.txt",
                "e1245d4f3fd128a5c7efcf89bc0b0ab0991d3f31bff437caa23bec3f437fc4c1",
                2773,
                0,
                4,
                9,
                57,
                2773),
        /** Percolator 3.07.1 target peptides. */
        V3071_TARGET_PEPTIDES(
                "percolator-3.07.1/peptides.target.txt",
                "579bffd2ae250ae2934eb5695bdf86376677b45589e7dbdf02f00c712312c114",
                2985,
                0,
                567,
                603,
                681,
                2985),
        /** Percolator 3.07.1 decoy peptides. */
        V3071_DECOY_PEPTIDES(
                "percolator-3.07.1/peptides.decoy.txt",
                "fe1db32901b00196b7c8eae2ed3194215620af44975f475be16d8155112a3fd7",
                2359,
                0,
                2,
                5,
                33,
                2359),
        /** Percolator 3.09 target PSMs. */
        V309_TARGET_PSMS(
                "percolator-3.09/psms.target.txt",
                "bc5909cd1fa06f5173d3bcbd93b16767ccb0a79f9158669bd39ae3366bc29414",
                3897,
                0,
                982,
                1026,
                1171,
                3897),
        /** Percolator 3.09 decoy PSMs. */
        V309_DECOY_PSMS(
                "percolator-3.09/psms.decoy.txt",
                "364a583f7299a515b6452003adcdb77ddde07e461536c90741907a6d7f5ce22f",
                2773,
                0,
                4,
                9,
                57,
                2773),
        /** Percolator 3.09 target peptides. */
        V309_TARGET_PEPTIDES(
                "percolator-3.09/peptides.target.txt",
                "c5c7ed6763095d39228e02dec3b2f3924c0b370168144462aa344ba8be80036f",
                2985,
                0,
                567,
                603,
                681,
                2985),
        /** Percolator 3.09 decoy peptides. */
        V309_DECOY_PEPTIDES(
                "percolator-3.09/peptides.decoy.txt",
                "5d05777f4c48f28493d5b184c8cead85b942eec0e55973cb77f633bbee738537",
                2359,
                0,
                2,
                5,
                33,
                2359);

        private final String relative;
        private final String sha256;
        private final long rows;
        private final long[] passing;

        Table(String relative, String sha256, long rows, long... passing) {
            this.relative = relative;
            this.sha256 = sha256;
            this.rows = rows;
            this.passing = passing.clone();
        }

        /**
         * The file, verified, in this repository's {@code scratch/scientific-path/}.
         *
         * @return its path
         * @throws org.opentest4j.AssertionFailedError if it is missing or not the pinned bytes
         */
        public Path path() {
            return pathUnder(ScratchFixtures.repositoryRoot().resolve(DIRECTORY));
        }

        /**
         * The file, verified, under another directory laid out the same way.
         *
         * @param directory the directory standing for {@code scratch/scientific-path}
         * @return its path
         * @throws org.opentest4j.AssertionFailedError if it is missing or not the pinned bytes
         */
        public Path pathUnder(Path directory) {
            return ScratchFixtures.verified(directory.resolve(relative), sha256, REMAKE);
        }

        /**
         * The file's path relative to {@code scratch/scientific-path}.
         *
         * @return for example {@code percolator-3.07.1/psms.target.txt}
         */
        public String relative() {
            return relative;
        }

        /**
         * The pinned SHA-256.
         *
         * @return the digest
         */
        public String sha256() {
            return sha256;
        }

        /**
         * The data rows, {@code wc -l} minus the header.
         *
         * @return the row count
         */
        public long rows() {
            return rows;
        }

        /**
         * The {@code awk} counts at every one of {@link #CUTOFFS}.
         *
         * @return counts by cutoff, in {@link #CUTOFFS}' order
         */
        public Map<String, IndependentCounts> awkCounts() {
            Map<String, IndependentCounts> counts = new LinkedHashMap<>();
            for (int i = 0; i < CUTOFFS.size(); i++) {
                counts.put(
                        CUTOFFS.get(i),
                        new IndependentCounts(rows, passing[i], rows - passing[i], 0));
            }
            return counts;
        }
    }

    /** One real Percolator weights file and its digest (3.07.1 and 3.09 wrote identical bytes). */
    public enum Weights {
        /** Percolator 3.07.1's {@code --weights} file: three splits. */
        V3071("percolator-3.07.1/weights.txt"),
        /** Percolator 3.09's: byte-identical to 3.07.1's. */
        V309("percolator-3.09/weights.txt");

        /** Both files' SHA-256. */
        public static final String SHA256 =
                "1a92c0ac17efe38ffce7caf0855f273746310b1cddb9b1e409b435fb02692208";

        private final String relative;

        Weights(String relative) {
            this.relative = relative;
        }

        /**
         * The file, verified, in this repository's {@code scratch/scientific-path/}.
         *
         * @return its path
         * @throws org.opentest4j.AssertionFailedError if it is missing or not the pinned bytes
         */
        public Path path() {
            return ScratchFixtures.verified(
                    ScratchFixtures.repositoryRoot().resolve(DIRECTORY).resolve(relative),
                    SHA256,
                    REMAKE);
        }
    }
}
