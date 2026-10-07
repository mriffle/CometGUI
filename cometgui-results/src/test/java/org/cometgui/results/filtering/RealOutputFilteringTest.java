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

package org.cometgui.results.filtering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.results.testing.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The display filters over real Percolator 3.06.5, 3.07.1 and 3.09 output ({@code
 * real/PROVENANCE.txt}). The expected counts were taken at generation time with {@code awk -F'\t'
 * 'NR>1 && $3+0<=c'} over each file and typed in here, independently of the parser and the filter.
 *
 * <p>Phase 09 gate item 9: parsing and filtering never touch a raw file. Every real file's SHA-256
 * is pinned; it is checked before and after every file is parsed, streamed and filtered under
 * several cutoffs, with its size and modification time.
 */
class RealOutputFilteringTest {

    private static final Map<String, String> REAL = new LinkedHashMap<>();

    static {
        String v3071Targets = "6e782dd7bbba9f16bce1c6556bc86032e7a9804b6494694db61573d88340ed53";
        String v3071Decoys = "3f9557b82119a4f9900e5964c762dd9ce504de508afdb5be67dfdcca465259e4";
        String v3065Targets = "848e26e570a2c5f736be9e00377adef43db1677796b9efa9dfc2221bb9430852";
        String v3065Decoys = "2f60bf17a274b6e9f95860a8c635540938bd17fcf492e159fedd13722b52ec43";
        String v309Targets = "44aa04692c21aa47742f406d3da23adfc032d5fba363d07aecc9aa1f2379d07b";
        String v309Decoys = "9074109fa81a2ea2362de7d69894f0b660a0204baefc9f0f13172fac29d0b36c";
        REAL.put("real/percolator-3.07.1/psms.tsv", v3071Targets);
        REAL.put("real/percolator-3.07.1/peptides.tsv", v3071Targets);
        REAL.put("real/percolator-3.07.1/decoy-psms.tsv", v3071Decoys);
        REAL.put("real/percolator-3.07.1/decoy-peptides.tsv", v3071Decoys);
        REAL.put("real/percolator-3.06.5/psms.tsv", v3065Targets);
        REAL.put("real/percolator-3.06.5/peptides.tsv", v3065Targets);
        REAL.put("real/percolator-3.06.5/decoy-psms.tsv", v3065Decoys);
        REAL.put("real/percolator-3.06.5/decoy-peptides.tsv", v3065Decoys);
        REAL.put("real/percolator-3.09/psms.tsv", v309Targets);
        REAL.put("real/percolator-3.09/peptides.tsv", v309Targets);
        REAL.put("real/percolator-3.09/decoy-psms.tsv", v309Decoys);
        REAL.put("real/percolator-3.09/decoy-peptides.tsv", v309Decoys);
    }

    private static final Map<String, String> WEIGHTS =
            Map.of(
                    "real/percolator-3.07.1/weights.txt",
                    "5370f2375d3ea1f13236f6208624c69f81fa8fb190e936e863488cfda4e7a434",
                    "real/percolator-3.06.5/weights.txt",
                    "d24988bd722b0937a1ffa207ad7dbb6e22ef35885adacbfe21ee0ba4cbdff8c3",
                    "real/percolator-3.09/weights.txt",
                    "5370f2375d3ea1f13236f6208624c69f81fa8fb190e936e863488cfda4e7a434");

    private static final List<String> CUTOFFS =
            List.of("0", "0.01", "0.0588235", "0.1", "0.166667", "0.5", "1");

    /*
     * Rows at or below each cutoff of CUTOFFS, in that order, counted with awk at generation time
     * (real/PROVENANCE.txt).
     */
    @ParameterizedTest
    @CsvSource({
        "percolator-3.07.1, psms.tsv, 0 0 17 17 27 48 64",
        "percolator-3.07.1, decoy-psms.tsv, 0 0 0 0 3 23 64",
        "percolator-3.06.5, psms.tsv, 0 0 0 0 6 57 64",
        "percolator-3.06.5, decoy-psms.tsv, 0 0 0 0 0 27 64",
        "percolator-3.09, psms.tsv, 0 0 17 17 27 48 64",
        "percolator-3.09, decoy-psms.tsv, 0 0 0 0 3 23 64"
    })
    @DisplayName(
            "passing counts at seven cutoffs equal awk's; 0.0588235 and 0.166667 are exact"
                    + " q-values in the files, so the boundary is inclusive on real data")
    void realCounts(String version, String table, String passingAtEachCutoff) throws IOException {
        String relative = "real/" + version + "/" + table;
        Path psms = Fixtures.verified(relative, REAL.get(relative));
        String peptideRelative = relative.replace("psms", "peptides");
        Path peptides = Fixtures.verified(peptideRelative, REAL.get(peptideRelative));
        String[] passing = passingAtEachCutoff.split(" ");
        for (int index = 0; index < CUTOFFS.size(); index++) {
            long expectedPassing = Long.parseLong(passing[index]);
            FilterCounts expected = new FilterCounts(64, expectedPassing, 64 - expectedPassing, 0);
            String cutoff = CUTOFFS.get(index);
            FilterTally streamed = PsmQValueFilter.parse(cutoff).tally();
            ResultTableReader.forEach(psms, streamed);
            assertEquals(expected, streamed.counts(), relative + " at " + cutoff);
            assertEquals(
                    expected,
                    PeptideQValueFilter.parse(cutoff)
                            .count(ResultTableReader.readAll(peptides).rows()),
                    peptideRelative + " at " + cutoff);
        }
    }

    @Test
    @DisplayName(
            "gate item 9: every raw file byte-identical, same size and time, after parse + filter")
    void rawFilesUntouched() throws IOException {
        Map<Path, String> files = new LinkedHashMap<>();
        Map<Path, Long> sizes = new LinkedHashMap<>();
        Map<Path, FileTime> times = new LinkedHashMap<>();
        Map<String, String> all = new LinkedHashMap<>(REAL);
        all.putAll(WEIGHTS);
        for (Map.Entry<String, String> entry : all.entrySet()) {
            Path file = Fixtures.verified(entry.getKey(), entry.getValue());
            files.put(file, entry.getValue());
            sizes.put(file, Files.size(file));
            times.put(file, Files.getLastModifiedTime(file));
        }
        for (Path file : files.keySet()) {
            if (file.toString().endsWith(".tsv")) {
                for (String cutoff : CUTOFFS) {
                    ResultTableReader.forEach(file, PsmQValueFilter.parse(cutoff).tally());
                    PeptideQValueFilter.parse(cutoff).count(ResultTableReader.readAll(file).rows());
                }
            } else {
                WeightsReader.read(file);
            }
        }
        for (Map.Entry<Path, String> entry : files.entrySet()) {
            Path file = entry.getKey();
            assertEquals(entry.getValue(), Fixtures.sha256(file), file + " changed");
            assertEquals(sizes.get(file), Files.size(file), file + " size");
            assertEquals(times.get(file), Files.getLastModifiedTime(file), file + " time");
        }
        assertEquals(15, files.size());
    }
}
