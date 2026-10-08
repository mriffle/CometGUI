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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opentest4j.AssertionFailedError;

/**
 * The real K562 Percolator 3.07.1 and 3.09 outputs ({@code D-006}; never committed) and the
 * independent counter agree with the {@code awk} counts pinned in {@link RealK562} and {@code
 * real-k562/PROVENANCE.txt} at 0, 0.005, 0.01, 0.05 and 1. The locator fails, never skips.
 */
class RealK562Test {

    @ParameterizedTest
    @EnumSource(RealK562.Table.class)
    @DisplayName("the independent counter equals awk at 0, 0.005, 0.01, 0.05 and 1")
    void counterEqualsAwk(RealK562.Table table) throws IOException {
        IndependentCounter.Tally tally = IndependentCounter.count(table.path(), RealK562.CUTOFFS);
        assertEquals(table.rows(), tally.total(), table.relative() + " rows");
        for (Map.Entry<String, IndependentCounts> awk : table.awkCounts().entrySet()) {
            assertEquals(
                    awk.getValue(),
                    tally.at(awk.getKey()),
                    table.relative() + " at " + awk.getKey());
        }
        assertEquals(Map.of(), tally.unknownByText(), table.relative() + " has no unknown q");
    }

    @Test
    @DisplayName("Phase 00's headline numbers: 3897 target PSMs, 1026 at 0.01; 2985 peptides, 603")
    void phase00Summary() {
        for (RealK562.Table psms :
                new RealK562.Table[] {
                    RealK562.Table.V3071_TARGET_PSMS, RealK562.Table.V309_TARGET_PSMS
                }) {
            assertEquals(new IndependentCounts(3897, 1026, 2871, 0), psms.awkCounts().get("0.01"));
        }
        for (RealK562.Table peptides :
                new RealK562.Table[] {
                    RealK562.Table.V3071_TARGET_PEPTIDES, RealK562.Table.V309_TARGET_PEPTIDES
                }) {
            assertEquals(
                    new IndependentCounts(2985, 603, 2382, 0), peptides.awkCounts().get("0.01"));
        }
    }

    @ParameterizedTest
    @EnumSource(RealK562.Weights.class)
    @DisplayName("both weights files are present and the pinned bytes")
    void weightsPinned(RealK562.Weights weights) {
        Path file = weights.path();
        assertEquals(RealK562.Weights.SHA256, ScratchFixtures.sha256(file));
        assertEquals("weights.txt", String.valueOf(file.getFileName()));
    }

    @Test
    @DisplayName("PROVENANCE.txt records every pinned digest against its file")
    void provenanceRecordsDigests() throws IOException {
        String record;
        try (InputStream in =
                RealK562Test.class.getResourceAsStream(
                        "/org/cometgui/results/real-k562/PROVENANCE.txt")) {
            assertTrue(in != null, "real-k562/PROVENANCE.txt is not on the test classpath");
            record = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (RealK562.Table table : RealK562.Table.values()) {
            assertTrue(
                    record.contains(table.sha256() + "  " + table.relative()),
                    table.relative() + " digest");
        }
        assertTrue(record.contains(RealK562.Weights.SHA256 + "  percolator-3.07.1/weights.txt"));
        assertTrue(record.contains(RealK562.Weights.SHA256 + "  percolator-3.09/weights.txt"));
    }

    @Test
    @DisplayName("an absent file FAILS (never skips), naming how it was made")
    void absentFails(@TempDir Path directory) {
        AssertionFailedError failed =
                assertThrows(
                        AssertionFailedError.class,
                        () -> RealK562.Table.V309_DECOY_PEPTIDES.pathUnder(directory));
        String message = failed.getMessage();
        assertTrue(message.contains("does not exist"), message);
        assertTrue(message.contains("run_scientific_path.sh"), message);
        assertTrue(message.contains("fails rather than skips"), message);
    }

    @Test
    @DisplayName("a different file FAILS on its SHA-256")
    void damagedFails(@TempDir Path directory) throws IOException {
        Files.createDirectories(directory.resolve("percolator-3.07.1"));
        Path copy = directory.resolve(RealK562.Table.V3071_TARGET_PSMS.relative());
        Files.writeString(
                copy,
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n",
                StandardCharsets.UTF_8);
        AssertionFailedError failed =
                assertThrows(
                        AssertionFailedError.class,
                        () -> RealK562.Table.V3071_TARGET_PSMS.pathUnder(directory));
        assertTrue(failed.getMessage().contains("SHA-256"), failed.getMessage());
        assertEquals(
                RealK562.Table.V3071_TARGET_PSMS.sha256(),
                failed.getExpected().getStringRepresentation());
    }
}
