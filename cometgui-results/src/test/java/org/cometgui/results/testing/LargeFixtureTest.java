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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;

/**
 * The large performance fixture (P10-2) and the independent counter (P10-3) agree: the counter's
 * {@code split} + {@code BigDecimal} pass over 1 000 000 PSM rows and 400 000 peptide rows gives
 * the generator's own counts, computed in Python {@code decimal} from what it assigned, at every
 * cutoff the manifest holds; and every unknown kind is found in its number and category. The
 * locator fails, never skips, when the fixture is missing or different.
 */
class LargeFixtureTest {

    @ParameterizedTest
    @ValueSource(strings = {LargeFixture.PSMS, LargeFixture.PEPTIDES})
    @DisplayName(
            "the independent counter equals the generator's manifest at every cutoff, and finds"
                    + " every unknown kind in its number and category")
    void counterEqualsManifest(String table) throws IOException {
        LargeFixture fixture = LargeFixture.locate();
        assertEquals(
                List.of("0", "0.001", "0.005", "0.01", "0.05", "0.1", "0.5", "1"),
                fixture.cutoffs(),
                "the manifest's cutoffs include the gate's 0, 0.005, 0.01 and 1");
        IndependentCounter.Tally tally =
                IndependentCounter.count(fixture.table(table), fixture.cutoffs());
        assertEquals(fixture.rows(table), tally.total(), table + " rows");
        for (String cutoff : fixture.cutoffs()) {
            assertEquals(
                    fixture.expected(table, cutoff), tally.at(cutoff), table + " at " + cutoff);
        }
        Map<String, Long> rows = new LinkedHashMap<>();
        Map<String, String> categories = new LinkedHashMap<>();
        for (LargeFixture.UnknownKind kind : fixture.unknownKinds(table)) {
            rows.put(kind.text(), kind.rows());
            categories.put(kind.text(), kind.category());
        }
        assertEquals(rows, tally.unknownByText(), table + " unknown rows by spelling");
        assertEquals(categories, tally.reasonByText(), table + " unknown categories");
        assertEquals(
                Set.of("", "NaN", "nan", "-nan", "inf", "-inf", "Infinity", "0,01", "1.5", "-0.1"),
                rows.keySet(),
                "every unknown kind the brief names is present");
    }

    @Test
    @DisplayName("a few manifest numbers, hand-typed from CONSTRUCTED.txt, so the manifest is held")
    void handTypedNumbers() {
        LargeFixture fixture = LargeFixture.locate();
        assertEquals(1_000_000L, fixture.rows(LargeFixture.PSMS));
        assertEquals(400_000L, fixture.rows(LargeFixture.PEPTIDES));
        assertEquals(
                new IndependentCounts(1_000_000, 219_777, 778_745, 1478),
                fixture.expected(LargeFixture.PSMS, "0.01"));
        assertEquals(
                new IndependentCounts(400_000, 88_111, 311_295, 594),
                fixture.expected(LargeFixture.PEPTIDES, "0.01"));
        assertEquals(
                new IndependentCounts(1_000_000, 5003, 993_519, 1478),
                fixture.expected(LargeFixture.PSMS, "0"));
        assertEquals(
                new IndependentCounts(1_000_000, 998_522, 0, 1478),
                fixture.expected(LargeFixture.PSMS, "1"));
        assertEquals(
                Set.of(250_000L),
                Set.copyOf(fixture.rowsBySource(LargeFixture.PSMS).values()),
                "four source files, 250 000 PSM rows each");
        assertEquals(4, fixture.rowsBySource(LargeFixture.PSMS).size());
        Map<String, String> categories =
                fixture.unknownKinds(LargeFixture.PSMS).stream()
                        .collect(
                                Collectors.toMap(
                                        LargeFixture.UnknownKind::label,
                                        LargeFixture.UnknownKind::category));
        assertEquals("MISSING", categories.get("empty"));
        assertEquals("UNPARSABLE", categories.get("comma-decimal"));
        assertEquals("OUT_OF_RANGE", categories.get("negative"));
    }

    @Test
    @DisplayName("the manifest records the pinned digests, and CONSTRUCTED.txt carries all three")
    void digestsRecorded() throws IOException {
        LargeFixture fixture = LargeFixture.locate();
        assertEquals(LargeFixture.PSMS_SHA256, fixture.recordedSha256(LargeFixture.PSMS));
        assertEquals(LargeFixture.PEPTIDES_SHA256, fixture.recordedSha256(LargeFixture.PEPTIDES));
        String record;
        try (InputStream in =
                LargeFixtureTest.class.getResourceAsStream(
                        "/org/cometgui/results/large-fixture/CONSTRUCTED.txt")) {
            assertTrue(in != null, "CONSTRUCTED.txt is not on the test classpath");
            record = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(record.contains(LargeFixture.PSMS_SHA256 + "  psms.tsv"), "psms.tsv digest");
        assertTrue(
                record.contains(LargeFixture.PEPTIDES_SHA256 + "  peptides.tsv"),
                "peptides.tsv digest");
        assertTrue(
                record.contains(LargeFixture.MANIFEST_SHA256 + "  manifest.json"),
                "manifest.json digest");
        assertTrue(record.contains(LargeFixture.COMMAND), "the command");
    }

    @Test
    @DisplayName("an absent fixture FAILS (never skips), naming the command that makes it")
    void absentFails(@TempDir Path directory) {
        AssertionFailedError failed =
                assertThrows(
                        AssertionFailedError.class,
                        () -> LargeFixture.at(directory.resolve("large")));
        String message = failed.getMessage();
        assertTrue(message.contains("does not exist"), message);
        assertTrue(message.contains(LargeFixture.COMMAND), message);
        assertTrue(message.contains("fails rather than skips"), message);
    }

    @Test
    @DisplayName("a different PSM table FAILS on its SHA-256, naming the command")
    void damagedFails(@TempDir Path directory) throws IOException {
        Path real = LargeFixture.locate().directory();
        Files.copy(real.resolve("manifest.json"), directory.resolve("manifest.json"));
        Files.writeString(
                directory.resolve("psms.tsv"),
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n",
                StandardCharsets.UTF_8);
        AssertionFailedError failed =
                assertThrows(AssertionFailedError.class, () -> LargeFixture.at(directory));
        String message = failed.getMessage();
        assertTrue(message.contains("psms.tsv is not the file whose SHA-256"), message);
        assertTrue(message.contains(LargeFixture.COMMAND), message);
        assertEquals(LargeFixture.PSMS_SHA256, failed.getExpected().getStringRepresentation());
    }
}
