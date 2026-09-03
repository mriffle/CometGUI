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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The probe's fixture, pinned to the byte.
 *
 * <p>The two digests below are what the real Percolator 3.07.1 portable binary was run over on
 * 2026-09-03, and the outcomes recorded in {@code PercolatorRealBinaryTest} belong to <strong>these
 * exact bytes</strong>. A change to the generator that moved them would leave those outcomes
 * describing a file that no longer exists, which is why they are hand-typed here rather than
 * recomputed.
 */
class SyntheticPinTest {

    /** SHA-256 of {@code SyntheticPin.of(64, 3071)}, hand-typed. */
    private static final String SHA256_64 =
            "a6ab63363a634d43da157f237c405908f96cdb067a5d918471b129ad75f806d9";

    /** SHA-256 of {@code SyntheticPin.of(8, 3071)}, the negative control's fixture, hand-typed. */
    private static final String SHA256_8 =
            "e9df3d2b4df63f510167e831edb654563454662b20f1f06c9820d990bf42e361";

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides SHA-256", impossible);
        }
    }

    @Test
    @DisplayName("the probe's fixture is 64 target and 64 decoy rows, from a fixed seed")
    void theProbeFixtureIsPinned() {
        assertAll(
                () -> assertEquals(64, SyntheticPin.PROBE_TARGET_ROWS),
                () -> assertEquals(3071L, SyntheticPin.PROBE_SEED),
                () ->
                        assertEquals(
                                SyntheticPin.of(
                                        SyntheticPin.PROBE_TARGET_ROWS, SyntheticPin.PROBE_SEED),
                                SyntheticPin.forCapabilityProbe()));
    }

    @Test
    @DisplayName("both fixtures are byte-identical to the files the real binary was run over")
    void theBytesArePinned() {
        assertAll(
                () -> assertEquals(SHA256_64, sha256(SyntheticPin.forCapabilityProbe())),
                () -> assertEquals(SHA256_8, sha256(SyntheticPin.of(8, SyntheticPin.PROBE_SEED))),
                () -> assertEquals(10318, SyntheticPin.forCapabilityProbe().length()),
                () -> assertEquals(1331, SyntheticPin.of(8, SyntheticPin.PROBE_SEED).length()));
    }

    @Test
    @DisplayName("the generator is deterministic: the same seed twice is the same file")
    void deterministic() {
        assertAll(
                () -> assertEquals(SyntheticPin.of(64, 1L), SyntheticPin.of(64, 1L)),
                () -> assertNotEquals(SyntheticPin.of(64, 1L), SyntheticPin.of(64, 2L)),
                () ->
                        assertNotEquals(
                                SyntheticPin.of(8, SyntheticPin.PROBE_SEED),
                                SyntheticPin.of(64, SyntheticPin.PROBE_SEED)));
    }

    @Test
    @DisplayName("the header is exactly the ten columns Percolator reads, tab separated")
    void theHeader() {
        assertAll(
                () ->
                        assertEquals(
                                "SpecId\tLabel\tScanNr\tExpMass\tCalcMass\tfeat1\tfeat2\tfeat3"
                                        + "\tPeptide\tProteins",
                                SyntheticPin.HEADER),
                () ->
                        assertEquals(
                                SyntheticPin.HEADER,
                                SyntheticPin.forCapabilityProbe()
                                        .lines()
                                        .findFirst()
                                        .orElseThrow()),
                () -> assertEquals(10, SyntheticPin.HEADER.split("\t").length));
    }

    @ParameterizedTest(name = "[{index}] {0} target rows")
    @ValueSource(ints = {1, 8, 20, 64, 200})
    @DisplayName("the file holds a header plus twice the target rows, alternating target and decoy")
    void theRowsAlternate(int targetRows) {
        List<String> lines = SyntheticPin.of(targetRows, SyntheticPin.PROBE_SEED).lines().toList();

        assertEquals(targetRows * 2 + 1, lines.size());
        for (int row = 0; row < targetRows * 2; row++) {
            String[] columns = lines.get(row + 1).split("\t");
            boolean target = row % 2 == 0;
            int index = row;
            assertAll(
                    () -> assertEquals(10, columns.length, "row " + index),
                    () -> assertEquals("psm" + index, columns[0]),
                    () -> assertEquals(target ? "1" : "-1", columns[1], "label of row " + index),
                    () -> assertEquals(String.valueOf(index), columns[2]),
                    () ->
                            assertEquals(
                                    target,
                                    !columns[9].startsWith("decoy_"),
                                    "a decoy row must name a decoy protein, row " + index));
        }
    }

    @Test
    @DisplayName(
            "target and decoy rows are drawn around different means, or nothing separates them")
    void theTwoClassesAreSeparable() {
        List<String> lines = SyntheticPin.forCapabilityProbe().lines().skip(1).toList();
        double targetMean = 0;
        double decoyMean = 0;
        for (int row = 0; row < lines.size(); row++) {
            double first = Double.parseDouble(lines.get(row).split("\t")[5]);
            if (row % 2 == 0) {
                targetMean += first / SyntheticPin.PROBE_TARGET_ROWS;
            } else {
                decoyMean += first / SyntheticPin.PROBE_TARGET_ROWS;
            }
        }
        double separation = targetMean - decoyMean;

        assertTrue(
                separation > 0.5,
                "the target rows must score higher than the decoy rows or Percolator has nothing to"
                        + " learn; observed target mean "
                        + targetMean
                        + " against decoy mean "
                        + decoyMean);
    }

    @Test
    @DisplayName("every line ends in a plain newline, so the bytes are the same on every platform")
    void lineEndingsAreFixed() {
        String pin = SyntheticPin.forCapabilityProbe();

        assertAll(
                () -> assertEquals(-1, pin.indexOf('\r')),
                () -> assertTrue(pin.endsWith("\n")),
                () -> assertEquals(129, pin.split("\n", -1).length - 1));
    }

    @Test
    @DisplayName("the numbers are formatted in the root locale, not the machine's")
    void theNumbersUseADecimalPoint() {
        String row = SyntheticPin.forCapabilityProbe().lines().skip(1).findFirst().orElseThrow();

        assertAll(
                () -> assertEquals(-1, row.indexOf(',')),
                () ->
                        assertEquals(
                                "psm0\t1\t0\t1000.5\t1000.4\t1.0540\t0.5052\t-0.0032"
                                        + "\tK.VSLDLELLL.R\tsp|P00000|TEST",
                                row));
    }

    @Test
    @DisplayName("writing the fixture puts probe.pin in the directory, with those exact bytes")
    void writing(@TempDir Path directory) throws IOException {
        Path written = SyntheticPin.writeForCapabilityProbe(directory);

        assertAll(
                () -> assertEquals(directory.resolve("probe.pin"), written),
                () ->
                        assertEquals(
                                SHA256_64,
                                sha256(Files.readString(written, StandardCharsets.UTF_8))),
                () ->
                        assertEquals(
                                SHA256_8,
                                sha256(
                                        Files.readString(
                                                SyntheticPin.write(
                                                        directory, 8, SyntheticPin.PROBE_SEED),
                                                StandardCharsets.UTF_8))),
                () ->
                        assertEquals(
                                "directory",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> SyntheticPin.write(null, 8, 1L))
                                        .getMessage()));
    }

    @ParameterizedTest(name = "[{index}] {0} target rows")
    @ValueSource(ints = {0, -1, -64})
    @DisplayName("a fixture with no target rows is refused, quoting the number asked for")
    void aFixtureWithNoRows(int targetRows) {
        assertEquals(
                "a synthetic PIN needs at least one target row, but was asked for " + targetRows,
                assertThrows(
                                IllegalArgumentException.class,
                                () -> SyntheticPin.of(targetRows, SyntheticPin.PROBE_SEED))
                        .getMessage());
    }
}
