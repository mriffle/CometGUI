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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * The synthetic PIN the functional capability probe runs Percolator over.
 *
 * <h2>Why the fixture is 64 target and 64 decoy rows</h2>
 *
 * <p>{@code R-PERC-02} fixes the size and says why: "the fixture shall be large enough that a
 * capable binary does not abort on <em>median decoy score &lt;= score at 1% FDR</em> -- 64 target
 * and 64 decoy rows is proven sufficient, 8 and 8 is not, and an over-small fixture makes the probe
 * report a false negative."
 *
 * <p>That was re-established here, by execution, and the measurement is stronger than the sentence
 * it confirms. Fifty seeds of this generator were run through the real Percolator 3.07.1 portable
 * binary at both sizes on 2026-09-03: at 8 plus 8 rows <strong>ten of the fifty aborted</strong>
 * with that error, exit 1, leaving a zero-byte output file; at 64 plus 64 rows <strong>none of the
 * fifty aborted</strong>. So the false negative is not a certainty at 8 plus 8 -- it is a <em>coin
 * toss</em>, which is worse: a probe built on an under-sized fixture would call a fully capable
 * Percolator incapable about one time in five, intermittently, and the Limelight stage would be
 * offered or withheld depending on the draw.
 *
 * <h2>Determinism, and why not {@link java.util.Random}</h2>
 *
 * <p>The stream is SplitMix64 written out here, with Box-Muller for the normal deviates, so the
 * bytes this class produces are fixed by the algorithm rather than by a library. {@code
 * java.util.Random}'s defaults are wrong twice over: an unseeded instance is seeded from the clock,
 * which would make the probe's fixture -- and therefore its verdict -- different on every run; and
 * even seeded, its algorithm is a JDK implementation detail that {@code java.util.Random}'s own
 * documentation only pins for {@code next(int)}, so a hand-typed digest of the generated file could
 * not be relied on to survive a runtime upgrade. {@code SyntheticPinTest} pins the SHA-256 of both
 * fixtures by hand, which is the check that would notice if this ever stopped being true.
 *
 * <h2>The columns</h2>
 *
 * <p>The layout is {@code scripts/feasibility/noxml_sweep.py}'s {@code make_pin}, which is the
 * shape phase 00 ran Percolator over: a header, then alternating target and decoy rows, with three
 * features drawn around means that separate the two classes. Java's random stream is not Python's,
 * so the numbers differ from that script's and the fixture had to be run against the real binary
 * rather than assumed to transfer -- which is how the ten-in-fifty measurement above came to be
 * made.
 */
public final class SyntheticPin {

    /** Target rows in the probe's fixture; the same number of decoy rows follows from it. */
    public static final int PROBE_TARGET_ROWS = 64;

    /**
     * The seed the probe's fixture is generated from.
     *
     * <p>Arbitrary, fixed, and chosen from the fifty-seed sweep described on this class: at 64 plus
     * 64 rows every one of the fifty succeeded, so no seed is better than another there; at 8 plus
     * 8 rows this one reproduces the documented abort, which is what lets {@code
     * PercolatorCapabilityProbeRealBinaryTest}'s negative control demonstrate the false negative
     * with the fixture size as the only difference between the two runs.
     */
    public static final long PROBE_SEED = 3071L;

    /** The tab-separated header row, exactly as Percolator expects to read it. */
    public static final String HEADER =
            "SpecId\tLabel\tScanNr\tExpMass\tCalcMass\tfeat1\tfeat2\tfeat3\tPeptide\tProteins";

    private static final String AMINO_ACIDS = "ACDEFGHIKLMNPQRSTVWY";
    private static final int PEPTIDE_LENGTH = 9;
    private static final double TARGET_MEAN = 1.0;
    private static final double DECOY_MEAN = -0.3;

    private long state;

    private SyntheticPin(long seed) {
        this.state = seed;
    }

    /**
     * The fixture the capability probe uses: {@value #PROBE_TARGET_ROWS} target and {@value
     * #PROBE_TARGET_ROWS} decoy rows.
     *
     * @return the whole file's text, ending in a newline
     */
    public static String forCapabilityProbe() {
        return of(PROBE_TARGET_ROWS, PROBE_SEED);
    }

    /**
     * A fixture of a chosen size and seed.
     *
     * @param targetRows how many target rows; the same number of decoy rows is written, so the file
     *     holds twice this many. Must be at least one
     * @param seed the generator seed
     * @return the whole file's text, ending in a newline
     * @throws IllegalArgumentException if {@code targetRows} is not positive
     */
    public static String of(int targetRows, long seed) {
        if (targetRows < 1) {
            throw new IllegalArgumentException(
                    "a synthetic PIN needs at least one target row, but was asked for "
                            + targetRows);
        }
        SyntheticPin random = new SyntheticPin(seed);
        StringBuilder pin = new StringBuilder(targetRows * 160);
        pin.append(HEADER).append('\n');
        for (int row = 0; row < targetRows * 2; row++) {
            boolean target = row % 2 == 0;
            double mean = target ? TARGET_MEAN : DECOY_MEAN;
            double first = random.gaussian(mean, 1.0);
            double second = random.gaussian(mean * 0.7, 1.0);
            double third = random.gaussian(0.0, 0.3);
            String peptide = random.peptide();
            String protein =
                    String.format(
                            Locale.ROOT, target ? "sp|P%05d|TEST" : "decoy_sp|P%05d|TEST", row);
            pin.append(
                    String.format(
                            Locale.ROOT,
                            "psm%d\t%d\t%d\t1000.5\t1000.4\t%.4f\t%.4f\t%.4f\tK.%s.R\t%s\n",
                            row,
                            target ? 1 : -1,
                            row,
                            first,
                            second,
                            third,
                            peptide,
                            protein));
        }
        return pin.toString();
    }

    /**
     * Writes the capability probe's fixture into a directory.
     *
     * @param directory where to write it
     * @return the file written, named {@code probe.pin}
     * @throws IOException if it cannot be written
     * @throws NullPointerException if {@code directory} is {@code null}
     */
    public static Path writeForCapabilityProbe(Path directory) throws IOException {
        return write(directory, PROBE_TARGET_ROWS, PROBE_SEED);
    }

    /**
     * Writes a fixture of a chosen size and seed into a directory.
     *
     * @param directory where to write it
     * @param targetRows how many target rows
     * @param seed the generator seed
     * @return the file written, named {@code probe.pin}
     * @throws IOException if it cannot be written
     * @throws NullPointerException if {@code directory} is {@code null}
     */
    public static Path write(Path directory, int targetRows, long seed) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Path pin = directory.resolve("probe.pin");
        Files.writeString(pin, of(targetRows, seed), StandardCharsets.UTF_8);
        return pin;
    }

    /*
     * SplitMix64, written out rather than taken from a library so that the bytes this class
     * produces are fixed by the algorithm.  The three constants are the published ones.
     */
    private long next() {
        state += 0x9E3779B97F4A7C15L;
        long mixed = state;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    /* Strictly between 0 and 1: Box-Muller takes the logarithm of it, and log(0) is not finite. */
    private double uniform() {
        return ((next() >>> 11) + 1L) * 0x1.0p-53;
    }

    private double gaussian(double mean, double standardDeviation) {
        double first = uniform();
        double second = uniform();
        return mean
                + standardDeviation
                        * Math.sqrt(-2.0 * Math.log(first))
                        * Math.cos(2.0 * Math.PI * second);
    }

    private String peptide() {
        StringBuilder sequence = new StringBuilder(PEPTIDE_LENGTH);
        for (int index = 0; index < PEPTIDE_LENGTH; index++) {
            sequence.append(AMINO_ACIDS.charAt((int) ((next() >>> 1) % AMINO_ACIDS.length())));
        }
        return sequence.toString();
    }
}
