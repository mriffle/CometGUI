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

package org.cometgui.ui.viewmodel.results;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * The tables and weights files the Results view-model tests open: the copies of cometgui-results'
 * checked-in fixtures in this module's test resources ({@code FIXTURES.txt} there), and the two
 * scratch fixtures, each held to its pinned SHA-256 first. A scratch fixture that is missing or
 * different fails the test, saying how it is made; nothing skips.
 */
final class ResultsFixtures {

    /** The copies, by name, and their SHA-256s, as {@code FIXTURES.txt} records them. */
    private static final Map<String, String> COPIES =
            Map.of(
                    "psms-shuffled.tsv",
                    "3754547ea1ca3ff35b67913a8249b98831e924c0a84029c2a9769fc581a6f5d4",
                    "psms-unknown-q.tsv",
                    "ea58b3b63f9031bf53b5675d117b1e7203137a0ebd2399c9def824334267b6c7",
                    "weights-ties.txt",
                    "86466aa30c1d4f72915a45d50f7828284b5e36750ce3a022cb65880fb6b06c92",
                    "weights-two-splits.txt",
                    "44042567136e69e5854085952cf6a2a3a9e14221761bfcc3c4cb04782177016f",
                    "weights-four-splits.txt",
                    "2345706956b918d713a93fe5e523fbd31b7aceb095c68cfffbb9ff508969e276",
                    "real-3.07.1-psms.tsv",
                    "6e782dd7bbba9f16bce1c6556bc86032e7a9804b6494694db61573d88340ed53",
                    "real-3.07.1-peptides.tsv",
                    "6e782dd7bbba9f16bce1c6556bc86032e7a9804b6494694db61573d88340ed53",
                    "real-3.07.1-decoy-psms.tsv",
                    "3f9557b82119a4f9900e5964c762dd9ce504de508afdb5be67dfdcca465259e4",
                    "real-3.07.1-weights.txt",
                    "5370f2375d3ea1f13236f6208624c69f81fa8fb190e936e863488cfda4e7a434");

    /** Phase 00's real K562 PSM table (3.07.1), as cometgui-results' RealK562 pins it. */
    static final String K562_PSMS = "scratch/scientific-path/percolator-3.07.1/psms.target.txt";

    private static final String K562_PSMS_SHA256 =
            "458b79b5a423f57406421d31ddd57b946eb8096ab6935447efd1e98e48f7fe9e";

    /** Phase 00's real K562 peptide table (3.07.1). */
    static final String K562_PEPTIDES =
            "scratch/scientific-path/percolator-3.07.1/peptides.target.txt";

    private static final String K562_PEPTIDES_SHA256 =
            "579bffd2ae250ae2934eb5695bdf86376677b45589e7dbdf02f00c712312c114";

    /** Phase 00's real K562 weights (3.07.1). */
    static final String K562_WEIGHTS = "scratch/scientific-path/percolator-3.07.1/weights.txt";

    private static final String K562_WEIGHTS_SHA256 =
            "1a92c0ac17efe38ffce7caf0855f273746310b1cddb9b1e409b435fb02692208";

    private static final String K562_REMAKE =
            "It is Phase 00's real K562 Comet + Percolator search (D-006: never committed),"
                    + " written by bash scripts/feasibility/run_scientific_path.sh; see"
                    + " cometgui-results' org/cometgui/results/real-k562/PROVENANCE.txt.";

    /** The large fixture's PSM table. */
    static final String LARGE_PSMS = "scratch/phase10/large/psms.tsv";

    private static final String LARGE_PSMS_SHA256 =
            "4f7aaecd0164bc22be51de1c60dfed3e70ddf65a4283f38a1912c76ece8cfcf4";

    private static final String LARGE_REMAKE =
            "Make it from the repository root with: python3"
                    + " scripts/fixtures/large-results-fixture.py (about 15 s).";

    private ResultsFixtures() {}

    /**
     * A copy in this module's test resources, held to its SHA-256.
     *
     * @param name the file's name
     * @return its path
     */
    static Path copy(String name) {
        String sha256 = COPIES.get(name);
        if (sha256 == null) {
            throw new IllegalArgumentException("no fixture copy is called " + name);
        }
        URL resource = ResultsFixtures.class.getResource(name);
        assertTrue(resource != null, () -> "the test resource " + name + " is missing");
        Path file;
        try {
            file = Path.of(resource.toURI());
        } catch (URISyntaxException impossible) {
            throw new IllegalStateException(impossible);
        }
        assertEquals(sha256, sha256(file), () -> name + " is not the pinned copy");
        return file;
    }

    /** Phase 00's real K562 target PSMs, verified. */
    static Path k562Psms() {
        return scratch(K562_PSMS, K562_PSMS_SHA256, K562_REMAKE);
    }

    /** Phase 00's real K562 target peptides, verified. */
    static Path k562Peptides() {
        return scratch(K562_PEPTIDES, K562_PEPTIDES_SHA256, K562_REMAKE);
    }

    /** Phase 00's real K562 weights, verified. */
    static Path k562Weights() {
        return scratch(K562_WEIGHTS, K562_WEIGHTS_SHA256, K562_REMAKE);
    }

    /** The large fixture's PSM table, verified. */
    static Path largePsms() {
        return scratch(LARGE_PSMS, LARGE_PSMS_SHA256, LARGE_REMAKE);
    }

    private static Path scratch(String relative, String sha256, String remake) {
        Path file = repositoryRoot().resolve(relative);
        assertTrue(
                Files.isRegularFile(file),
                () ->
                        "the fixture "
                                + file
                                + " does not exist; it is gitignored scratch. "
                                + remake
                                + " This test fails rather than skips.");
        assertEquals(
                sha256,
                sha256(file),
                () -> "the fixture " + file + " is not the pinned file. " + remake);
        return file;
    }

    /** The nearest directory at or above the working directory that holds {@code manifests/}. */
    static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError("no repository root above " + Path.of("").toAbsolutePath());
        }
        return cursor;
    }

    static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 20];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
