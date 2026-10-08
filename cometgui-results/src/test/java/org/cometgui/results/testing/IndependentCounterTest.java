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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The independent counter of design decision P10-3, checked against numbers no Java produced: hand
 * counts of the constructed table, the {@code awk} counts {@code parser/real/PROVENANCE.txt} pins
 * for the checked-in Percolator tables, and a table written here with every spelling the policy
 * must decide. And proved independent: its compiled bytes name no CometGUI production class.
 */
class IndependentCounterTest {

    private static final List<String> REAL_CUTOFFS =
            List.of("0", "0.01", "0.0588235", "0.1", "0.166667", "0.5", "1");

    /** The checked-in tables' SHA-256s, as parser/real/PROVENANCE.txt records them. */
    private static final Map<String, String> CHECKED_IN =
            Map.of(
                    "percolator-3.07.1/psms.tsv",
                    "6e782dd7bbba9f16bce1c6556bc86032e7a9804b6494694db61573d88340ed53",
                    "percolator-3.07.1/decoy-psms.tsv",
                    "3f9557b82119a4f9900e5964c762dd9ce504de508afdb5be67dfdcca465259e4",
                    "percolator-3.06.5/psms.tsv",
                    "848e26e570a2c5f736be9e00377adef43db1677796b9efa9dfc2221bb9430852",
                    "percolator-3.06.5/decoy-psms.tsv",
                    "2f60bf17a274b6e9f95860a8c635540938bd17fcf492e159fedd13722b52ec43",
                    "percolator-3.09/psms.tsv",
                    "44aa04692c21aa47742f406d3da23adfc032d5fba363d07aecc9aa1f2379d07b",
                    "percolator-3.09/decoy-psms.tsv",
                    "9074109fa81a2ea2362de7d69894f0b660a0204baefc9f0f13172fac29d0b36c");

    @Test
    @DisplayName(
            "constructed psms-unknown-q.tsv: hand counts at five cutoffs, and every unknown"
                    + " spelling in its own category")
    void constructedTableByHand() throws IOException {
        Path table =
                Fixtures.verified(
                        "constructed/psms-unknown-q.tsv",
                        "ea58b3b63f9031bf53b5675d117b1e7203137a0ebd2399c9def824334267b6c7");
        IndependentCounter.Tally tally =
                IndependentCounter.count(table, List.of("0", "0.005", "0.01", "0.05", "1"));
        // Known: c08 0; c01 0.001, c12 1e-3; c02 0.01, c13 0.010; c03 0.0100001; c09 1.
        assertEquals(new IndependentCounts(15, 1, 6, 8), tally.at("0"));
        assertEquals(new IndependentCounts(15, 3, 4, 8), tally.at("0.005"));
        assertEquals(new IndependentCounts(15, 5, 2, 8), tally.at("0.01"));
        assertEquals(new IndependentCounts(15, 6, 1, 8), tally.at("0.05"));
        assertEquals(new IndependentCounts(15, 7, 0, 8), tally.at("1"));
        assertEquals(
                Map.of(
                        "", IndependentCounter.MISSING,
                        "NaN", IndependentCounter.UNPARSABLE,
                        "nan", IndependentCounter.UNPARSABLE,
                        "inf", IndependentCounter.UNPARSABLE,
                        "Infinity", IndependentCounter.UNPARSABLE,
                        "0,005", IndependentCounter.UNPARSABLE,
                        "1.5", IndependentCounter.OUT_OF_RANGE,
                        "-0.01", IndependentCounter.OUT_OF_RANGE),
                tally.reasonByText());
        assertEquals(8L, tally.unknownByText().values().stream().mapToLong(Long::longValue).sum());
    }

    /*
     * Passing rows at REAL_CUTOFFS, in that order: awk -F'\t' 'NR>1 && $3+0<=c' at generation
     * time, as parser/real/PROVENANCE.txt records (64 rows each, every q-value known).
     */
    @ParameterizedTest
    @CsvSource({
        "percolator-3.07.1/psms.tsv, 0 0 17 17 27 48 64",
        "percolator-3.07.1/decoy-psms.tsv, 0 0 0 0 3 23 64",
        "percolator-3.06.5/psms.tsv, 0 0 0 0 6 57 64",
        "percolator-3.06.5/decoy-psms.tsv, 0 0 0 0 0 27 64",
        "percolator-3.09/psms.tsv, 0 0 17 17 27 48 64",
        "percolator-3.09/decoy-psms.tsv, 0 0 0 0 3 23 64"
    })
    @DisplayName(
            "checked-in real Percolator tables: equal to parser/real/PROVENANCE.txt's awk counts"
                    + " at seven cutoffs, two of them exact q-values in the files")
    void checkedInRealTables(String relative, String passingAtEachCutoff) throws IOException {
        Path table = Fixtures.verified("real/" + relative, CHECKED_IN.get(relative));
        IndependentCounter.Tally tally = IndependentCounter.count(table, REAL_CUTOFFS);
        String[] passing = passingAtEachCutoff.split(" ");
        for (int i = 0; i < REAL_CUTOFFS.size(); i++) {
            long expected = Long.parseLong(passing[i]);
            assertEquals(
                    new IndependentCounts(64, expected, 64 - expected, 0),
                    tally.at(REAL_CUTOFFS.get(i)),
                    relative + " at " + REAL_CUTOFFS.get(i));
        }
        assertEquals(Map.of(), tally.unknownByText(), relative);
    }

    @Test
    @DisplayName(
            "every spelling decided by hand: inclusive at 0.01, exponent forms, -0, and what is"
                    + " not a q-value")
    void spellingsByHand(@TempDir Path directory) throws IOException {
        List<String> q =
                List.of(
                        "0.01", // passes: the boundary is inclusive
                        "0.0100001", // fails
                        "0.00999999", // passes
                        "1e-2", // passes: 0.01 exactly
                        "1E-2", // passes
                        "9.27998e-07", // passes
                        "0", // passes
                        "-0", // passes: a decimal zero, within [0, 1]
                        "1", // fails at 0.01
                        "+0.5", // fails
                        ".5", // fails
                        "5.", // OUT_OF_RANGE
                        "1.0000001", // OUT_OF_RANGE
                        " 0.01"); // UNPARSABLE: Percolator writes no white space
        StringBuilder text =
                new StringBuilder(
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
        for (int i = 0; i < q.size(); i++) {
            text.append("s").append(i).append("\t1\t").append(q.get(i)).append("\t0\tK.A.R\tp\n");
        }
        Path table = directory.resolve("psms.tsv");
        Files.writeString(table, text, StandardCharsets.UTF_8);
        IndependentCounter.Tally tally = IndependentCounter.count(table, List.of("0.01"));
        assertEquals(new IndependentCounts(14, 7, 4, 3), tally.at("0.01"));
        assertEquals(
                Map.of(
                        "5.", IndependentCounter.OUT_OF_RANGE,
                        "1.0000001", IndependentCounter.OUT_OF_RANGE,
                        " 0.01", IndependentCounter.UNPARSABLE),
                tally.reasonByText());
        assertThrows(IllegalArgumentException.class, () -> tally.at("0.05"));
    }

    @Test
    @DisplayName("a table with no q-value column is refused, not counted as zero")
    void noQValueColumn(@TempDir Path directory) throws IOException {
        Path table = directory.resolve("weights.tsv");
        Files.writeString(table, "a\tb\n1\t2\n", StandardCharsets.UTF_8);
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> IndependentCounter.count(table, List.of("0.01")));
        assertTrue(refused.getMessage().contains("no q-value column"), refused.getMessage());
    }

    @Test
    @DisplayName(
            "independence: the counter's compiled classes name no CometGUI class outside"
                    + " org.cometgui.results.testing; the same scan finds them in a test that does")
    void usesNoProductionClass() throws IOException {
        for (Class<?> type :
                List.of(
                        IndependentCounter.class,
                        IndependentCounter.Tally.class,
                        IndependentCounts.class)) {
            assertEquals(List.of(), cometGuiReferencesOutsideTesting(type), type.getName());
        }
        // Control: a class known to call the production reader and filter must be caught.
        Class<?> control;
        try {
            control = Class.forName("org.cometgui.results.filtering.RealOutputFilteringTest");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("the control class is gone; choose another", missing);
        }
        List<String> found = cometGuiReferencesOutsideTesting(control);
        assertTrue(
                found.contains("org/cometgui/results/parser/ResultTableReader")
                        && found.contains("org/cometgui/results/filtering/PsmQValueFilter"),
                "the scan must see production classes where they are used; it saw " + found);
    }

    /** Every {@code org/cometgui/...} name in a class file outside the testing package. */
    private static List<String> cometGuiReferencesOutsideTesting(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertTrue(in != null, "no class file " + resource);
            bytes = in.readAllBytes();
        }
        String constants = new String(bytes, StandardCharsets.ISO_8859_1);
        Matcher names = Pattern.compile("org/cometgui/[A-Za-z0-9_/$]+").matcher(constants);
        List<String> found = new ArrayList<>();
        while (names.find()) {
            String name = names.group();
            if (!name.startsWith("org/cometgui/results/testing/")) {
                found.add(name);
            }
        }
        return found;
    }
}
