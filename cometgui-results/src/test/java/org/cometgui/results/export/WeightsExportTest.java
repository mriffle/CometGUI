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

package org.cometgui.results.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.results.parser.FeatureWeights;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.results.testing.Fixtures;
import org.cometgui.results.testing.MiniJson;
import org.cometgui.results.testing.RealK562;
import org.cometgui.results.testing.ScratchFixtures;
import org.cometgui.results.testing.TestHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The learned-feature-weights export ({@code AC-RES-08}): every value in the file equals the
 * summary's exactly -- each number read back as the same {@code double} -- every per-split weight
 * equals the artefact's own text, and the split count, read from the artefact by this test's own
 * reading of it, is the file's and the sidecar's.
 */
class WeightsExportTest {

    @TempDir private Path work;

    record Weights(String name, Supplier<Path> path) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Weights> weights() {
        return Stream.of(
                new Weights("k562/3.07.1", RealK562.Weights.V3071::path),
                new Weights("k562/3.09", RealK562.Weights.V309::path),
                checkedIn(
                        "real/percolator-3.07.1/weights.txt",
                        "5370f2375d3ea1f13236f6208624c69f81fa8fb190e936e863488cfda4e7a434"),
                checkedIn(
                        "real/percolator-3.06.5/weights.txt",
                        "d24988bd722b0937a1ffa207ad7dbb6e22ef35885adacbfe21ee0ba4cbdff8c3"),
                checkedIn(
                        "constructed/weights-two-splits.txt",
                        "44042567136e69e5854085952cf6a2a3a9e14221761bfcc3c4cb04782177016f"),
                checkedIn(
                        "constructed/weights-four-splits.txt",
                        "2345706956b918d713a93fe5e523fbd31b7aceb095c68cfffbb9ff508969e276"),
                checkedIn(
                        "constructed/weights-ties.txt",
                        "86466aa30c1d4f72915a45d50f7828284b5e36750ce3a022cb65880fb6b06c92"),
                checkedIn(
                        "constructed/weights-one-split.txt",
                        "28111d5c8d8052d8ffee6be61876e1311037b05bfdefc232efa014c379c8a571"));
    }

    private static Weights checkedIn(String relative, String sha256) {
        return new Weights(relative, () -> Fixtures.verified(relative, sha256));
    }

    /** The artefact as this test reads it: per split, the names, normalised and raw texts. */
    private static List<String[][]> splitsOf(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.startsWith("#") && !line.isBlank()) {
                lines.add(line);
            }
        }
        List<String[][]> splits = new ArrayList<>();
        for (int i = 0; i + 2 < lines.size(); i += 3) {
            splits.add(
                    new String[][] {
                        lines.get(i).split("\t", -1),
                        lines.get(i + 1).split("\t", -1),
                        lines.get(i + 2).split("\t", -1)
                    });
        }
        return splits;
    }

    private static void same(double expected, String field, String what) {
        double read = Double.parseDouble(field);
        assertTrue(
                Double.compare(expected, read) == 0,
                what + ": expected " + expected + " but the file reads back as " + read);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("weights")
    @DisplayName("every value equals the summary's; per-split weights equal the artefact's text")
    @SuppressWarnings("unchecked")
    void valuesEqualTheSummary(Weights weights) throws IOException {
        Path file = weights.path().get();
        ExportTables.Identity before = ExportTables.Identity.of(file);
        WeightsSummary summary = WeightsSummary.of(WeightsReader.read(file));
        List<String[][]> splits = splitsOf(file);
        int n = splits.size();
        RunLayout run = ExportTables.newRun(work);

        WeightsExport export =
                ExportTables.exporter(run, ExportTables.fixed()).exportWeights(summary);

        List<String> lines = Files.readAllLines(export.file(), StandardCharsets.UTF_8);
        List<String> header = new ArrayList<>(List.of("feature", "bias"));
        for (int s = 1; s <= n; s++) {
            header.add("split_" + s + "_normalised");
        }
        header.addAll(
                List.of(
                        "mean_signed",
                        "mean_absolute",
                        "standard_deviation",
                        "sign_consistency",
                        "rank"));
        for (int s = 1; s <= n; s++) {
            header.add("split_" + s + "_raw");
        }
        assertEquals(String.join("\t", header), lines.get(0));
        String[] names = splits.get(0)[0];
        assertEquals(names.length + 1, lines.size(), "one row per feature");
        for (int f = 0; f < names.length; f++) {
            String[] row = lines.get(f + 1).split("\t", -1);
            FeatureWeights expected = summary.features().get(f);
            String what = weights + " " + names[f];
            assertEquals(header.size(), row.length, what);
            assertEquals(names[f], row[0], what);
            assertEquals(Boolean.toString("m0".equals(names[f])), row[1], what);
            for (int s = 0; s < n; s++) {
                same(Double.parseDouble(splits.get(s)[1][f]), row[2 + s], what + " normalised");
                same(Double.parseDouble(splits.get(s)[2][f]), row[2 + n + 5 + s], what + " raw");
            }
            same(expected.meanSigned(), row[2 + n], what + " mean signed");
            same(expected.meanAbsolute(), row[3 + n], what + " mean absolute");
            same(expected.standardDeviation(), row[4 + n], what + " standard deviation");
            assertEquals(
                    Map.of(
                                    "ALL_POSITIVE", "all positive",
                                    "ALL_NEGATIVE", "all negative",
                                    "MIXED", "mixed",
                                    "ALL_ZERO", "all zero")
                            .get(expected.signConsistency().name()),
                    row[5 + n],
                    what);
            assertEquals(
                    expected.rank().isPresent() ? Integer.toString(expected.rank().getAsInt()) : "",
                    row[6 + n],
                    what + " rank");
        }

        Map<String, Object> sidecar =
                (Map<String, Object>)
                        MiniJson.parse(Files.readString(export.sidecar(), StandardCharsets.UTF_8));
        Map<String, Object> source = (Map<String, Object>) sidecar.get("source");
        Map<String, Object> self = (Map<String, Object>) sidecar.get("file");
        assertEquals("learned-feature-weights", sidecar.get("export"));
        assertEquals(ExportTables.RUN_ID, sidecar.get("runId"));
        assertEquals(ExportTables.VERSION, sidecar.get("cometguiVersion"));
        assertEquals("2026-10-09T12:00:00.123Z", sidecar.get("created"));
        assertEquals(BigDecimal.valueOf(n), sidecar.get("splitCount"), "read from the artefact");
        assertEquals(BigDecimal.valueOf(names.length), sidecar.get("featureCount"));
        assertEquals(before.sha256(), source.get("sha256"));
        assertEquals(ScratchFixtures.sha256(export.file()), self.get("sha256"));
        assertEquals(n, export.splitCount());
        assertEquals(names.length, export.featureCount());
        assertEquals(
                "learned-feature-weights_20261009T120000.123Z.tsv",
                String.valueOf(export.file().getFileName()));
        assertEquals(before, ExportTables.Identity.of(file), "the weights artefact changed");
        String log = Files.readString(run.eventLogFile(), StandardCharsets.UTF_8);
        assertTrue(log.contains("\"type\":\"export.written\""), log);
        assertTrue(log.contains("\"export.kind\":\"learned-feature-weights\""), log);
        assertTrue(log.contains("\"weights.split-count\":\"" + n + "\""), log);
        assertTrue(log.contains("\"weights.feature-count\":\"" + names.length + "\""), log);
        assertEquals(1, Files.readAllLines(run.eventLogFile()).size());
    }

    @Test
    @DisplayName("an artefact touched while it is read for export is refused, nothing written")
    void aTouchedArtefactIsRefused() throws IOException {
        Path file =
                Files.copy(
                        Fixtures.verified(
                                "constructed/weights-two-splits.txt",
                                "44042567136e69e5854085952cf6a2a3a9e14221761bfcc3c4cb04782177016f"),
                        work.resolve("weights.txt"));
        WeightsSummary summary = WeightsSummary.of(WeightsReader.read(file));
        TestHasher real = new TestHasher();
        HashService touching =
                path -> {
                    FileHashes hashes = real.hash(path);
                    if (path.equals(file.toAbsolutePath().normalize())) {
                        Files.setLastModifiedTime(
                                file, FileTime.from(Instant.parse("2001-01-01T00:00:00Z")));
                    }
                    return hashes;
                };
        RunLayout run = ExportTables.newRun(work);
        ResultExporter exporter =
                new ResultExporter(
                        run,
                        new RunId(ExportTables.RUN_ID),
                        ExportTables.VERSION,
                        ExportTables.fixed(),
                        touching,
                        SecretRedactor.patternsOnly());

        IOException refused =
                assertThrows(IOException.class, () -> exporter.exportWeights(summary));

        assertTrue(
                refused.getMessage().contains("changed while it was being read for export"),
                refused.getMessage());
        assertTrue(Files.notExists(run.eventLogFile()));
        try (Stream<Path> files = Files.list(run.exportsDirectory())) {
            assertEquals(0, files.count());
        }
    }

    @Test
    @DisplayName("an artefact changed since it was summarised is refused, and nothing is written")
    void aChangedArtefactIsRefused() throws IOException {
        Path file =
                Files.copy(
                        Fixtures.verified(
                                "constructed/weights-two-splits.txt",
                                "44042567136e69e5854085952cf6a2a3a9e14221761bfcc3c4cb04782177016f"),
                        work.resolve("weights.txt"));
        WeightsSummary summary = WeightsSummary.of(WeightsReader.read(file));
        String text = Files.readString(file).replace("0.1250\t", "0.1251\t");
        Files.writeString(file, text, StandardOpenOption.TRUNCATE_EXISTING);
        RunLayout run = ExportTables.newRun(work);

        IOException refused =
                assertThrows(
                        IOException.class,
                        () ->
                                ExportTables.exporter(run, ExportTables.fixed())
                                        .exportWeights(summary));

        assertTrue(
                refused.getMessage().contains("no longer holds the weights that were summarised"),
                refused.getMessage());
        assertTrue(Files.notExists(run.eventLogFile()));
        try (Stream<Path> files = Files.list(run.exportsDirectory())) {
            assertEquals(0, files.count());
        }
    }
}
