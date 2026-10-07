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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The probe's minimal reading of a table and a weights file, at the boundaries the probe's own
 * tests do not reach through a whole run.
 *
 * <p>The rows are hand-typed in the shape the real binaries wrote on 2026-10-07; the header is the
 * one all three builds printed.
 */
class ProbeArtefactsTest {

    private static final String HEADER =
            "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds";
    private static final String TARGET = "psm96\t0\t0.05\t0.0333279\tK.RWQPYKKPE.R\tsp|P00096|TEST";
    private static final String DECOY =
            "psm53\t-0.538745\t0.105263\t0.157092\tK.IVDCGRCVM.R\tdecoy_sp|P00053|TEST";

    @Test
    @DisplayName("the six columns, hand-typed from the header every real build printed")
    void theColumns() {
        assertEquals(
                List.of(
                        "PSMId",
                        "score",
                        "q-value",
                        "posterior_error_prob",
                        "peptide",
                        "proteinIds"),
                ProbeArtefacts.RESULT_COLUMNS);
    }

    @Test
    @DisplayName("a table is its header plus exactly the expected rows, of the expected kind")
    void tables() {
        assertAll(
                () -> assertTrue(ProbeArtefacts.isResultTable(List.of(HEADER, TARGET), 1, false)),
                () -> assertTrue(ProbeArtefacts.isResultTable(List.of(HEADER, DECOY), 1, true)),
                () ->
                        assertTrue(
                                ProbeArtefacts.isResultTable(List.of(HEADER), 0, false),
                                "zero rows expected and zero written is the expected table"),
                () -> assertFalse(ProbeArtefacts.isResultTable(List.of(), 0, false), "no header"),
                () -> assertFalse(ProbeArtefacts.isResultTable(List.of(HEADER, DECOY), 1, false)),
                () -> assertFalse(ProbeArtefacts.isResultTable(List.of(HEADER, TARGET), 1, true)),
                () -> assertFalse(ProbeArtefacts.isResultTable(List.of(HEADER, TARGET), 2, false)),
                () ->
                        assertFalse(
                                ProbeArtefacts.isResultTable(
                                        List.of(HEADER, TARGET, TARGET), 1, false)),
                () ->
                        assertTrue(
                                ProbeArtefacts.isResultTable(
                                        List.of(HEADER, TARGET + "\tsp|P00097|TEST"), 1, false),
                                "a row naming a second protein is wider than the header, and fine"),
                () ->
                        assertFalse(
                                ProbeArtefacts.isResultTable(
                                        List.of(
                                                HEADER,
                                                TARGET.substring(0, TARGET.lastIndexOf('\t'))),
                                        1,
                                        false),
                                "a row with no protein column cannot be judged"),
                () ->
                        assertFalse(
                                ProbeArtefacts.isResultTable(
                                        List.of(HEADER.replace("peptide", "sequence"), TARGET),
                                        1,
                                        false),
                                "every one of the six columns is required, by name"));
    }

    @Test
    @DisplayName("the protein column is found by name, wherever it is")
    void theProteinColumnIsFoundByName() {
        String reordered = "proteinIds\tPSMId\tscore\tq-value\tposterior_error_prob\tpeptide";

        assertAll(
                () ->
                        assertTrue(
                                ProbeArtefacts.isResultTable(
                                        List.of(
                                                reordered,
                                                "decoy_sp|P00053|TEST\tpsm53\t0\t0.1\t0.1\tK.A.R"),
                                        1,
                                        true)),
                () ->
                        assertFalse(
                                ProbeArtefacts.isResultTable(
                                        List.of(
                                                reordered,
                                                "sp|P00053|TEST\tpsm53\t0\t0.1\t0.1\tK.A.R"),
                                        1,
                                        true)));
    }

    @Test
    @DisplayName("a file that is absent, or is not UTF-8, is not a table and not weights")
    void unreadableFiles(@TempDir Path directory) throws IOException {
        Path absent = directory.resolve("absent.tsv");
        Path binary = Files.write(directory.resolve("binary.tsv"), new byte[] {(byte) 0xC3, 0x28});

        assertAll(
                () -> assertFalse(ProbeArtefacts.isResultFile(absent, 0, false)),
                () -> assertFalse(ProbeArtefacts.isWeightsFile(absent)),
                () -> assertFalse(ProbeArtefacts.isResultFile(binary, 0, false)),
                () -> assertFalse(ProbeArtefacts.isWeightsFile(binary)));
    }

    @Test
    @DisplayName("weights: the layout the real 3.07.1 binary wrote, and one bin of it, both count")
    void weights(@TempDir Path directory) throws IOException {
        Path real =
                Files.writeString(
                        directory.resolve("weights.txt"),
                        "# This file contains the weights from each cross validation bin from"
                                + " percolator training\n"
                                + "# First line is the feature names, followed by normalized"
                                + " weights, and the raw weights of bin 1\n"
                                + "# This is repeated for the other bins\n"
                                + "feat1\tfeat2\tfeat3\tm0\n"
                                + "0\t0.2495\t0.0000\t-0.9101\n"
                                + "0.0000\t0.2302\t0.0000\t-0.9576\n"
                                + "feat1\tfeat2\tfeat3\tm0\n"
                                + "0.367\t0.0000\t0.0000\t-0.2914\n"
                                + "0.4167\t0.0000\t0.0000\t-0.4876\n"
                                + "feat1\tfeat2\tfeat3\tm0\n"
                                + "0.323\t0.0000\t0.0000\t-0.4826\n"
                                + "0.3672\t0.0000\t0.0000\t-0.6555\n");
        Path oneBin =
                Files.writeString(
                        directory.resolve("one.txt"),
                        "feat3\tfeat1\tfeat2\n1\t2\t3\n-1\t-2.5e-3\t0\n");
        Path secondBinWrong =
                Files.writeString(
                        directory.resolve("two.txt"),
                        "feat1\tfeat2\tfeat3\n1\t2\t3\n1\t2\t3\nfeat1\tfeat2\n1\t2\n1\t2\n");

        assertAll(
                () -> assertTrue(ProbeArtefacts.isWeightsFile(real)),
                () -> assertTrue(ProbeArtefacts.isWeightsFile(oneBin), "names, not positions"),
                () ->
                        assertFalse(
                                ProbeArtefacts.isWeightsFile(secondBinWrong),
                                "every bin is checked, not the first"));
    }
}
