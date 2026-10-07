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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.results.parser.PercolatorOutputException.Problem;
import org.cometgui.results.testing.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The weights reader: split count read from the file (R-PERC-09, phase 09 gate item 8).
 *
 * <p>Percolator always writes three cross-validation bins, so the two- and four-split files are
 * CONSTRUCTED by hand ({@code constructed/CONSTRUCTED.txt}; each file's own comment lines say so
 * too). The real three-split files are tested in {@link RealPercolatorOutputTest}. The malformed
 * files are written inline below, each a copy of the real layout with one defect.
 */
class WeightsReaderTest {

    static final String TWO_SPLITS_SHA256 =
            "44042567136e69e5854085952cf6a2a3a9e14221761bfcc3c4cb04782177016f";
    static final String FOUR_SPLITS_SHA256 =
            "2345706956b918d713a93fe5e523fbd31b7aceb095c68cfffbb9ff508969e276";

    private static final String COMMENT = "# comment\n";
    private static final String HEADER = "feat1\tfeat2\tm0\n";
    private static final String NORMALISED = "0.5\t-0.25\t1\n";
    private static final String RAW = "1.5\t-0.75\t2\n";
    private static final String SPLIT = HEADER + NORMALISED + RAW;

    @TempDir private Path directory;

    private Path write(String content) throws IOException {
        Path file = directory.resolve("weights.txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private PercolatorOutputException refused(String content) throws IOException {
        Path file = write(content);
        PercolatorOutputException refusal =
                assertThrows(PercolatorOutputException.class, () -> WeightsReader.read(file));
        assertEquals(file, refusal.file());
        assertTrue(refusal.getMessage().contains(file.toString()), refusal.getMessage());
        return refusal;
    }

    @Nested
    @DisplayName("the split count is read from the file")
    class SplitCount {

        @Test
        @DisplayName("constructed: two splits, five features, values hand-checked")
        void twoSplits() throws IOException {
            LearnedWeights weights =
                    WeightsReader.read(
                            Fixtures.verified(
                                    "constructed/weights-two-splits.txt", TWO_SPLITS_SHA256));
            assertAll(
                    () -> assertEquals(2, weights.splitCount()),
                    () ->
                            assertEquals(
                                    List.of("lnrSp", "deltLCn", "Xcorr", "Charge2", "m0"),
                                    weights.featureNames()),
                    () -> assertEquals(3, weights.comments().size()),
                    () -> assertTrue(weights.comments().get(0).startsWith("# CONSTRUCTED BY HAND")),
                    () -> assertEquals(1, weights.splits().get(0).number()),
                    () -> assertEquals(2, weights.splits().get(1).number()),
                    () ->
                            assertEquals(
                                    List.of(0.1250, -0.0300, 1.5000, 0.2000, -2.5000),
                                    weights.splits().get(0).normalised()),
                    () ->
                            assertEquals(
                                    List.of(0.0500, -0.6000, 3.1000, 0.4000, -4.2000),
                                    weights.splits().get(0).raw()),
                    () ->
                            assertEquals(
                                    List.of(0.1300, -0.0250, 1.4500, -0.1000, -2.4000),
                                    weights.splits().get(1).normalised()),
                    () ->
                            assertEquals(
                                    List.of(0.0520, -0.5000, 3.0000, -0.2000, -4.1000),
                                    weights.splits().get(1).raw()),
                    () -> assertEquals(List.of(0.2, -0.1), weights.normalisedWeightsOf("Charge2")),
                    () -> assertEquals(List.of(3.1, 3.0), weights.rawWeightsOf("Xcorr")),
                    () -> assertEquals(List.of(0.125, 0.13), weights.normalisedWeightsOf("lnrSp")),
                    () -> assertEquals(List.of(0.05, 0.052), weights.rawWeightsOf("lnrSp")));
        }

        @Test
        @DisplayName("constructed: four splits, Percolator's own spellings of zero, exponents")
        void fourSplits() throws IOException {
            LearnedWeights weights =
                    WeightsReader.read(
                            Fixtures.verified(
                                    "constructed/weights-four-splits.txt", FOUR_SPLITS_SHA256));
            assertAll(
                    () -> assertEquals(4, weights.splitCount()),
                    () -> assertEquals(List.of("feat_a", "feat_b", "m0"), weights.featureNames()),
                    () -> assertEquals(4, weights.splits().get(3).number()),
                    () ->
                            assertEquals(
                                    List.of(0.0, -2.25, 3.5), weights.splits().get(0).normalised()),
                    () -> assertEquals(List.of(0.0, -5.0, 6.0), weights.splits().get(0).raw()),
                    () ->
                            assertEquals(
                                    List.of(0.5, -0.25, 0.001),
                                    weights.splits().get(1).normalised()),
                    () -> assertEquals(List.of(1.5, -0.75, 20.0), weights.splits().get(1).raw()),
                    () ->
                            assertEquals(
                                    List.of(-0.0, 0.125, -1.0),
                                    weights.splits().get(2).normalised()),
                    () ->
                            assertEquals(
                                    List.of(2.25, -3.375, 0.1875), weights.splits().get(3).raw()),
                    () ->
                            assertEquals(
                                    List.of(3.5, 0.001, -1.0, 0.0625),
                                    weights.normalisedWeightsOf(LearnedWeights.BIAS_FEATURE)));
        }

        @Test
        @DisplayName("one split, no comments: one")
        void oneSplit() throws IOException {
            LearnedWeights weights = WeightsReader.read(write(SPLIT));
            assertEquals(1, weights.splitCount());
            assertEquals(List.of(), weights.comments());
            assertEquals(List.of(0.5, -0.25, 1.0), weights.splits().get(0).normalised());
        }

        @Test
        @DisplayName("CRLF line endings are read like LF")
        void crlf() throws IOException {
            LearnedWeights weights =
                    WeightsReader.read(write((COMMENT + SPLIT + SPLIT).replace("\n", "\r\n")));
            assertEquals(2, weights.splitCount());
            assertEquals(List.of(1.5, -0.75, 2.0), weights.splits().get(1).raw());
        }

        @Test
        @DisplayName("an unknown feature name is refused by the accessors")
        void unknownFeature() throws IOException {
            LearnedWeights weights = WeightsReader.read(write(SPLIT));
            IllegalArgumentException refusal =
                    assertThrows(
                            IllegalArgumentException.class, () -> weights.rawWeightsOf("feat9"));
            assertEquals(
                    "the weights in " + weights.file() + " name no feature 'feat9'",
                    refusal.getMessage());
            assertThrows(IllegalArgumentException.class, () -> weights.normalisedWeightsOf("x"));
        }
    }

    @Nested
    @DisplayName("refusals, each with its own message naming the file")
    class Refusals {

        @Test
        @DisplayName("missing file")
        void missing() {
            Path file = directory.resolve("absent-weights.txt");
            PercolatorOutputException refusal =
                    assertThrows(PercolatorOutputException.class, () -> WeightsReader.read(file));
            assertEquals(Problem.MISSING_FILE, refusal.problem());
            assertEquals(
                    "The Percolator weights file " + file + " does not exist",
                    refusal.getMessage());
            assertTrue(refusal.getCause() instanceof NoSuchFileException);
        }

        @Test
        @DisplayName("a directory: unreadable")
        void unreadable() {
            PercolatorOutputException refusal =
                    assertThrows(
                            PercolatorOutputException.class, () -> WeightsReader.read(directory));
            assertEquals(Problem.UNREADABLE, refusal.problem());
            assertTrue(
                    refusal.getMessage()
                            .startsWith(
                                    "The Percolator weights file "
                                            + directory
                                            + " could not be read as UTF-8 text: "),
                    refusal.getMessage());
        }

        @Test
        @DisplayName("not UTF-8: unreadable")
        void notUtf8() throws IOException {
            Path file = directory.resolve("latin1.txt");
            Files.write(file, new byte[] {'f', (byte) 0xE9, '\n'});
            PercolatorOutputException refusal =
                    assertThrows(PercolatorOutputException.class, () -> WeightsReader.read(file));
            assertEquals(Problem.UNREADABLE, refusal.problem());
        }

        @Test
        @DisplayName("empty file")
        void empty() throws IOException {
            PercolatorOutputException refusal = refused("");
            assertEquals(Problem.EMPTY_FILE, refusal.problem());
            assertEquals(
                    "The Percolator weights file " + refusal.file() + " is empty",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("comments only: zero splits")
        void zeroSplits() throws IOException {
            PercolatorOutputException refusal = refused(COMMENT + COMMENT);
            assertEquals(Problem.NO_SPLITS, refusal.problem());
            assertEquals(
                    "The Percolator weights file "
                            + refusal.file()
                            + " holds no cross-validation split: it has only comment lines",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("a header without any rows")
        void headerWithoutRows() throws IOException {
            PercolatorOutputException refusal = refused(COMMENT + SPLIT + HEADER);
            assertEquals(Problem.HEADER_WITHOUT_ROWS, refusal.problem());
            assertEquals(
                    "Split 2 of the Percolator weights file "
                            + refusal.file()
                            + " (header at line 5) lacks its normalised- and raw-weights rows;"
                            + " every split is a header of feature names followed by a"
                            + " normalised-weights row and a raw-weights row",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("a header with its normalised row but no raw row")
        void headerWithoutRawRow() throws IOException {
            PercolatorOutputException refusal = refused(HEADER + NORMALISED);
            assertEquals(Problem.HEADER_WITHOUT_ROWS, refusal.problem());
            assertTrue(
                    refusal.getMessage()
                            .startsWith(
                                    "Split 1 of the Percolator weights file "
                                            + refusal.file()
                                            + " (header at line 1) lacks its raw-weights row;"),
                    refusal.getMessage());
        }

        @Test
        @DisplayName("a weight row narrower than its header")
        void narrowRow() throws IOException {
            PercolatorOutputException refusal = refused(COMMENT + HEADER + "0.5\t-0.25\n" + RAW);
            assertEquals(Problem.ROW_WIDTH, refusal.problem());
            assertEquals(
                    "Line 3 of the Percolator weights file "
                            + refusal.file()
                            + " (split 1, normalised weights) has 2 values, but the split's header"
                            + " names 3 features",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("a weight row wider than its header")
        void wideRow() throws IOException {
            PercolatorOutputException refusal =
                    refused(SPLIT + HEADER + NORMALISED + "1\t2\t3\t4\n");
            assertEquals(Problem.ROW_WIDTH, refusal.problem());
            assertEquals(
                    "Line 6 of the Percolator weights file "
                            + refusal.file()
                            + " (split 2, raw weights) has 4 values, but the split's header names 3"
                            + " features",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("a value that is not a number, naming the feature")
        void notANumber() throws IOException {
            PercolatorOutputException refusal = refused(HEADER + NORMALISED + "1.5\tabc\t2\n");
            assertEquals(Problem.NOT_A_NUMBER, refusal.problem());
            assertEquals(
                    "Line 3 of the Percolator weights file "
                            + refusal.file()
                            + " (split 1, raw weights) holds 'abc' for the feature 'feat2',"
                            + " which is not a finite decimal number",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("NaN and infinity are not finite numbers")
        void notFinite() throws IOException {
            assertEquals(Problem.NOT_A_NUMBER, refused(HEADER + "nan\t0\t0\n" + RAW).problem());
            assertEquals(
                    Problem.NOT_A_NUMBER, refused(HEADER + NORMALISED + "0\tinf\t0\n").problem());
            assertEquals(
                    Problem.NOT_A_NUMBER, refused(HEADER + NORMALISED + "0\t0\t1e999\n").problem());
        }

        @Test
        @DisplayName("a split naming different features from split 1, naming the split")
        void splitMismatch() throws IOException {
            PercolatorOutputException refusal =
                    refused(COMMENT + SPLIT + SPLIT + "feat1\tfeat3\tm0\n" + NORMALISED + RAW);
            assertEquals(Problem.SPLIT_MISMATCH, refusal.problem());
            assertEquals(
                    "Split 3 of the Percolator weights file "
                            + refusal.file()
                            + " (line 8) names the features [feat1, feat3, m0], but split 1 names"
                            + " [feat1, feat2, m0]; every split must name the same features in the"
                            + " same order",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("the same features in another order is a mismatch")
        void splitOrderMismatch() throws IOException {
            assertEquals(
                    Problem.SPLIT_MISMATCH,
                    refused(SPLIT + "feat2\tfeat1\tm0\n" + NORMALISED + RAW).problem());
        }

        @Test
        @DisplayName("a feature named twice")
        void repeatedFeature() throws IOException {
            PercolatorOutputException refusal = refused("feat1\tfeat1\tm0\n" + NORMALISED + RAW);
            assertEquals(Problem.BAD_FEATURE_NAME, refusal.problem());
            assertEquals(
                    "Line 1 of the Percolator weights file "
                            + refusal.file()
                            + " (split 1's feature names) names the feature 'feat1' twice:"
                            + " [feat1, feat1, m0]",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("an empty feature name")
        void emptyFeature() throws IOException {
            PercolatorOutputException refusal = refused(SPLIT + "feat1\t\tm0\n" + NORMALISED + RAW);
            assertEquals(Problem.BAD_FEATURE_NAME, refusal.problem());
            assertEquals(
                    "Line 4 of the Percolator weights file "
                            + refusal.file()
                            + " (split 2's feature names) has an empty feature name: [feat1, , m0]",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("a blank line")
        void blankLine() throws IOException {
            PercolatorOutputException refusal = refused(COMMENT + SPLIT + "\n" + SPLIT);
            assertEquals(Problem.BLANK_LINE, refusal.problem());
            assertEquals(
                    "Line 5 of the Percolator weights file " + refusal.file() + " is blank",
                    refusal.getMessage());
        }
    }

    @Nested
    @DisplayName("the model's own invariants")
    class Model {

        @Test
        @DisplayName("a split's two rows must be as wide as each other, and numbered from 1")
        void splitInvariants() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WeightsSplit(1, List.of(1.0, 2.0), List.of(1.0)));
            IllegalArgumentException refusal =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new WeightsSplit(0, List.of(1.0), List.of(1.0)));
            assertEquals("split 0 has 1 normalised and 1 raw weights", refusal.getMessage());
            assertEquals(1, new WeightsSplit(1, List.of(1.0), List.of(2.0)).number());
        }

        @Test
        @DisplayName("weights need a split, numbered in order, as wide as the features")
        void weightsInvariants() {
            Path file = Path.of("w.txt");
            List<String> names = List.of("a", "m0");
            WeightsSplit one = new WeightsSplit(1, List.of(1.0, 2.0), List.of(3.0, 4.0));
            WeightsSplit two = new WeightsSplit(2, List.of(1.0, 2.0), List.of(3.0, 4.0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LearnedWeights(file, List.of(), names, List.of()));
            IllegalArgumentException outOfOrder =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new LearnedWeights(file, List.of(), names, List.of(two, one)));
            assertEquals(
                    "split number 2 at position 1 has 2 weights for 2 features",
                    outOfOrder.getMessage());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LearnedWeights(file, List.of(), List.of("a"), List.of(one)));
            assertEquals(
                    2, new LearnedWeights(file, List.of(), names, List.of(one, two)).splitCount());
        }
    }
}
