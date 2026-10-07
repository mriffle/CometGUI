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

package org.cometgui.results.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.results.testing.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Real Percolator 3.06.5, 3.07.1 and 3.09 output, parsed.
 *
 * <p>The files under {@code real/} were written by the real binaries over CometGUI's own synthetic
 * PIN ({@code SyntheticPin.forCapabilityProbe()}, 64 target and 64 decoy rows) with {@code --seed
 * 1}; {@code real/PROVENANCE.txt} records the binaries' SHA-256, the exact command, the date and
 * each output's SHA-256, pinned again below. Every expected number here was obtained at generation
 * time with {@code wc -l}, {@code cut} and {@code awk} and typed in by hand -- none comes from the
 * parser under test.
 */
class RealPercolatorOutputTest {

    /** The six columns every real table's header names, in this order, in all three versions. */
    static final List<String> REAL_HEADER =
            List.of("PSMId", "score", "q-value", "posterior_error_prob", "peptide", "proteinIds");

    /** One Percolator version's checked-in output and its pinned digests. */
    enum Real {
        V3071(
                "percolator-3.07.1",
                "6e782dd7bbba9f16bce1c6556bc86032e7a9804b6494694db61573d88340ed53",
                "3f9557b82119a4f9900e5964c762dd9ce504de508afdb5be67dfdcca465259e4",
                "5370f2375d3ea1f13236f6208624c69f81fa8fb190e936e863488cfda4e7a434"),
        V3065(
                "percolator-3.06.5",
                "848e26e570a2c5f736be9e00377adef43db1677796b9efa9dfc2221bb9430852",
                "2f60bf17a274b6e9f95860a8c635540938bd17fcf492e159fedd13722b52ec43",
                "d24988bd722b0937a1ffa207ad7dbb6e22ef35885adacbfe21ee0ba4cbdff8c3"),
        V309(
                "percolator-3.09",
                "44aa04692c21aa47742f406d3da23adfc032d5fba363d07aecc9aa1f2379d07b",
                "9074109fa81a2ea2362de7d69894f0b660a0204baefc9f0f13172fac29d0b36c",
                "5370f2375d3ea1f13236f6208624c69f81fa8fb190e936e863488cfda4e7a434");

        private final String directory;
        private final String targetSha256;
        private final String decoySha256;
        private final String weightsSha256;

        Real(String directory, String targetSha256, String decoySha256, String weightsSha256) {
            this.directory = directory;
            this.targetSha256 = targetSha256;
            this.decoySha256 = decoySha256;
            this.weightsSha256 = weightsSha256;
        }

        /* psms.tsv and peptides.tsv are byte-identical here (every synthetic peptide is distinct),
         * and so are the two decoy tables: PROVENANCE.txt. */
        Path psms() {
            return Fixtures.verified("real/" + directory + "/psms.tsv", targetSha256);
        }

        Path peptides() {
            return Fixtures.verified("real/" + directory + "/peptides.tsv", targetSha256);
        }

        Path decoyPsms() {
            return Fixtures.verified("real/" + directory + "/decoy-psms.tsv", decoySha256);
        }

        Path decoyPeptides() {
            return Fixtures.verified("real/" + directory + "/decoy-peptides.tsv", decoySha256);
        }

        Path weights() {
            return Fixtures.verified("real/" + directory + "/weights.txt", weightsSha256);
        }
    }

    @ParameterizedTest
    @EnumSource(Real.class)
    @DisplayName("all four real tables: 64 rows each (wc -l 65 minus the header), every q known")
    void everyRealTableParses(Real real) throws IOException {
        for (Path table :
                List.of(real.psms(), real.peptides(), real.decoyPsms(), real.decoyPeptides())) {
            ResultTable parsed = ResultTableReader.readAll(table);
            boolean decoy = String.valueOf(table.getFileName()).startsWith("decoy-");
            assertAll(
                    table.toString(),
                    () -> assertEquals(REAL_HEADER, parsed.header().columns()),
                    () -> assertEquals(new ResultTableCounts(64, 64, 0), parsed.counts()),
                    () -> assertEquals(64, parsed.rows().size()),
                    () -> assertEquals(List.of(), parsed.unknownQValueRows()),
                    () -> assertEquals(2, parsed.rows().get(0).line()),
                    () -> assertEquals(65, parsed.rows().get(63).line()),
                    () ->
                            assertTrue(
                                    parsed.rows().stream()
                                            .allMatch(row -> isOneProteinOfKind(row, decoy)),
                                    "one protein per row, decoy_ exactly in the decoy tables"));
        }
    }

    private static boolean isOneProteinOfKind(ResultRow row, boolean decoy) {
        return row.proteinIds().size() == 1
                && row.proteinIds().get(0).startsWith("decoy_") == decoy;
    }

    @Test
    @DisplayName("3.07.1: rows at lines 2, 18, 19 and 65 hand-checked against the file")
    void percolator3071RowsByHand() throws IOException {
        List<ResultRow> rows = ResultTableReader.readAll(Real.V3071.psms()).rows();
        assertRow(
                rows.get(0),
                2,
                "psm84",
                "0",
                0.0,
                "0.0588235",
                0.0588235,
                "0.0168819",
                "K.MLRDNSHPS.R",
                "sp|P00084|TEST");
        assertRow(
                rows.get(16),
                18,
                "psm12",
                "-0.532435",
                -0.532435,
                "0.0588235",
                0.0588235,
                "0.244053",
                "K.AKETHFMAG.R",
                "sp|P00012|TEST");
        assertRow(
                rows.get(17),
                19,
                "psm20",
                "-0.554983",
                -0.554983,
                "0.105263",
                0.105263,
                "0.272024",
                "K.MMWFLKISA.R",
                "sp|P00020|TEST");
        assertRow(
                rows.get(63),
                65,
                "psm26",
                "-1.45261",
                -1.45261,
                "0.984375",
                0.984375,
                "1",
                "K.AGNQFSKWA.R",
                "sp|P00026|TEST");
        List<ResultRow> decoys = ResultTableReader.readAll(Real.V3071.decoyPsms()).rows();
        assertRow(
                decoys.get(0),
                2,
                "psm53",
                "-0.538745",
                -0.538745,
                "0.105263",
                0.105263,
                "0.251603",
                "K.IVDCGRCVM.R",
                "decoy_sp|P00053|TEST");
        assertRow(
                decoys.get(63),
                65,
                "psm121",
                "-1.6394",
                -1.6394,
                "1",
                1.0,
                "1",
                "K.PMKFGWEKV.R",
                "decoy_sp|P00121|TEST");
    }

    @Test
    @DisplayName("3.06.5: first and last rows hand-checked")
    void percolator3065RowsByHand() throws IOException {
        List<ResultRow> rows = ResultTableReader.readAll(Real.V3065.peptides()).rows();
        assertRow(
                rows.get(0),
                2,
                "psm100",
                "0",
                0.0,
                "0.166667",
                0.166667,
                "0.0772411",
                "K.QVIAFALDQ.R",
                "sp|P00100|TEST");
        assertRow(
                rows.get(63),
                65,
                "psm8",
                "-1.45063",
                -1.45063,
                "0.828125",
                0.828125,
                "1",
                "K.NINLIHRTK.R",
                "sp|P00008|TEST");
        List<ResultRow> decoys = ResultTableReader.readAll(Real.V3065.decoyPeptides()).rows();
        assertRow(
                decoys.get(0),
                2,
                "psm67",
                "-0.15496",
                -0.15496,
                "0.25",
                0.25,
                "0.118915",
                "K.WLWAVRIHS.R",
                "decoy_sp|P00067|TEST");
        assertRow(
                decoys.get(63),
                65,
                "psm63",
                "-2.03212",
                -2.03212,
                "1",
                1.0,
                "1",
                "K.VSITRFYKN.R",
                "decoy_sp|P00063|TEST");
    }

    @Test
    @DisplayName("3.09: the same rows as 3.07.1 with 3.09's own (I-spline) PEP values")
    void percolator309RowsByHand() throws IOException {
        List<ResultRow> rows = ResultTableReader.readAll(Real.V309.psms()).rows();
        assertRow(
                rows.get(0),
                2,
                "psm84",
                "0",
                0.0,
                "0.0588235",
                0.0588235,
                "0.0635441",
                "K.MLRDNSHPS.R",
                "sp|P00084|TEST");
        assertRow(
                rows.get(16),
                18,
                "psm12",
                "-0.532435",
                -0.532435,
                "0.0588235",
                0.0588235,
                "0.11981",
                "K.AKETHFMAG.R",
                "sp|P00012|TEST");
        assertRow(
                rows.get(17),
                19,
                "psm20",
                "-0.554983",
                -0.554983,
                "0.105263",
                0.105263,
                "0.157092",
                "K.MMWFLKISA.R",
                "sp|P00020|TEST");
        List<ResultRow> decoys = ResultTableReader.readAll(Real.V309.decoyPsms()).rows();
        assertRow(
                decoys.get(0),
                2,
                "psm53",
                "-0.538745",
                -0.538745,
                "0.105263",
                0.105263,
                "0.157092",
                "K.IVDCGRCVM.R",
                "decoy_sp|P00053|TEST");
    }

    @Test
    @DisplayName(
            "3.07.1 against 3.09: one header, and only posterior_error_prob differs -- 41 target"
                    + " and 16 decoy rows (counted with cut and awk)")
    void theOnlyDifferenceBetween3071And309IsPep() throws IOException {
        assertOnlyPepDiffers(Real.V3071.psms(), Real.V309.psms(), 41);
        assertOnlyPepDiffers(Real.V3071.decoyPsms(), Real.V309.decoyPsms(), 16);
    }

    private static void assertOnlyPepDiffers(Path older, Path newer, int differingPeps)
            throws IOException {
        ResultTable first = ResultTableReader.readAll(older);
        ResultTable second = ResultTableReader.readAll(newer);
        assertEquals(first.header().columns(), second.header().columns());
        int differing = 0;
        for (int index = 0; index < 64; index++) {
            ResultRow a = first.rows().get(index);
            ResultRow b = second.rows().get(index);
            assertEquals(a.psmId(), b.psmId());
            assertEquals(a.scoreText(), b.scoreText());
            assertEquals(a.qValue(), b.qValue());
            assertEquals(a.peptide(), b.peptide());
            assertEquals(a.proteinIds(), b.proteinIds());
            if (!a.posteriorErrorProbabilityText().equals(b.posteriorErrorProbabilityText())) {
                differing++;
            }
        }
        assertEquals(differingPeps, differing);
    }

    @Test
    @DisplayName("the real weights, three splits read from each file, values hand-checked")
    void realWeights() throws IOException {
        List<String> features = List.of("feat1", "feat2", "feat3", "m0");
        LearnedWeights v3071 = WeightsReader.read(Real.V3071.weights());
        assertAll(
                () -> assertEquals(3, v3071.splitCount()),
                () -> assertEquals(features, v3071.featureNames()),
                () ->
                        assertEquals(
                                "# This file contains the weights from each cross validation bin"
                                        + " from percolator training",
                                v3071.comments().get(0)),
                () -> assertEquals(3, v3071.comments().size()),
                () ->
                        assertEquals(
                                List.of(0.0, 0.3346, 0.0, -0.8517),
                                v3071.splits().get(0).normalised()),
                () -> assertEquals(List.of(0.0, 0.3087, 0.0, -0.9154), v3071.splits().get(0).raw()),
                () ->
                        assertEquals(
                                List.of(0.0, 0.2630, 0.0, -1.1708),
                                v3071.splits().get(1).normalised()),
                () -> assertEquals(List.of(0.0, 0.3286, 0.0, -1.1204), v3071.splits().get(2).raw()),
                () ->
                        assertEquals(
                                List.of(0.3346, 0.2630, 0.3562),
                                v3071.normalisedWeightsOf("feat2")),
                () -> assertEquals(List.of(-0.9154, -1.2209, -1.1204), v3071.rawWeightsOf("m0")));
        LearnedWeights v3065 = WeightsReader.read(Real.V3065.weights());
        assertAll(
                () -> assertEquals(3, v3065.splitCount()),
                () -> assertEquals(features, v3065.featureNames()),
                () ->
                        assertEquals(
                                List.of(0.686, 0.0, 0.0, -1.1747),
                                v3065.splits().get(0).normalised()),
                () -> assertEquals(List.of(0.7787, 0.0, 0.0, -1.5415), v3065.splits().get(0).raw()),
                () ->
                        assertEquals(
                                List.of(0.0, 0.4483, 0.0, -0.5701), v3065.splits().get(2).raw()));
        LearnedWeights v309 = WeightsReader.read(Real.V309.weights());
        assertEquals(v3071.splits(), v309.splits(), "the two files are byte-identical");
        assertEquals(3, v309.splitCount());
    }

    private static void assertRow(
            ResultRow row,
            long line,
            String psmId,
            String scoreText,
            double score,
            String qText,
            double q,
            String pepText,
            String peptide,
            String protein) {
        assertAll(
                psmId,
                () -> assertEquals(line, row.line()),
                () -> assertEquals(psmId, row.psmId()),
                () -> assertEquals(scoreText, row.scoreText()),
                () -> assertEquals(score, row.score()),
                () -> assertEquals(qText, row.qValue().text()),
                () -> assertTrue(row.qValue().isKnown()),
                () -> assertEquals(q, row.qValue().value()),
                () -> assertEquals(pepText, row.posteriorErrorProbabilityText()),
                () -> assertEquals(Double.parseDouble(pepText), row.posteriorErrorProbability()),
                () -> assertEquals(peptide, row.peptide()),
                () -> assertEquals(List.of(protein), row.proteinIds()),
                () -> assertFalse(Double.isNaN(row.score())));
    }
}
