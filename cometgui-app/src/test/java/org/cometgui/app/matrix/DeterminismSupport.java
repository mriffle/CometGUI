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

package org.cometgui.app.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.parser.LearnedWeights;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTable;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.WeightsReader;

/**
 * What the determinism suite's two opt-in classes share (Phase 15 unit 7, gate item 3): the plan
 * they run, and the one record each run leaves for {@code scripts/matrix/determinism.py} to grade.
 *
 * <h2>The plan is the data's, not this class's</h2>
 *
 * <p>Which configurations run -- the repeats, the thread counts that should change nothing, the
 * control that must change something -- is written in {@code scripts/matrix/determinism-data.json}.
 * {@code matrix.py} turns it into a tab-separated plan file ({@value #PLAN}), one line per run:
 * {@code part scope label cometThreads seed percolatorThreads}. These classes run exactly those
 * lines and record each run's configuration beside its outputs, so the grader can check that what
 * ran is what the data asked for.
 *
 * <h2>Nothing is compared here</h2>
 *
 * <p>Each run's outputs stay in the report directory, and its record holds what the product derives
 * from them -- its parsed row counts, the rows its default filters select (each identifier, in the
 * product's order) and the learned weights as its reader reads them. The comparison, with the
 * documented tolerance per artefact, is {@code determinism.py}'s.
 */
final class DeterminismSupport {

    /** The system property naming the plan file. */
    static final String PLAN = "cometgui.determinism.plan";

    /** The system property naming the default pair as {@code <comet>/<percolator>}. */
    static final String DEFAULT_PAIR = "cometgui.determinism.defaultPair";

    private DeterminismSupport() {}

    /**
     * One planned run.
     *
     * @param part {@code synthetic} or {@code fixture}
     * @param scope {@code every} (every offered build or pair) or {@code default} (the default pair
     *     alone)
     * @param label the configuration's identifier in the data
     * @param cometThreads the Comet {@code num_threads} text, {@code -} where no Comet runs
     * @param seed the Percolator seed
     * @param percolatorThreads the Percolator thread count
     */
    record Planned(
            String part,
            String scope,
            String label,
            String cometThreads,
            int seed,
            int percolatorThreads) {

        /**
         * The Percolator settings: the product's defaults with this run's seed and threads.
         *
         * @return the settings
         */
        PercolatorSettings settings() {
            return PercolatorSettings.defaults()
                    .withRandomSeed(seed)
                    .withThreadCount(percolatorThreads);
        }

        /**
         * The configuration as recorded.
         *
         * @return name to value
         */
        Map<String, Object> configuration() {
            Map<String, Object> configuration = new LinkedHashMap<>();
            configuration.put("cometThreads", cometThreads);
            configuration.put("percolatorSeed", seed);
            configuration.put("percolatorThreads", percolatorThreads);
            return configuration;
        }
    }

    /**
     * The planned runs of one part, in plan order.
     *
     * @param part {@code synthetic} or {@code fixture}
     * @return the runs
     * @throws IOException if the plan cannot be read
     */
    static List<Planned> plan(String part) throws IOException {
        String file = System.getProperty(PLAN, "");
        assertFalse(
                file.isBlank(),
                "the determinism suite needs -D" + PLAN + "=FILE (scripts/matrix/run-matrix.sh)");
        List<Planned> runs = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(file), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            assertEquals(6, fields.length, () -> "a plan line has six fields: " + line);
            if (fields[0].equals(part)) {
                runs.add(
                        new Planned(
                                fields[0],
                                fields[1],
                                fields[2],
                                fields[3],
                                Integer.parseInt(fields[4]),
                                Integer.parseInt(fields[5])));
            }
        }
        assertFalse(runs.isEmpty(), "the plan names no " + part + " run");
        return runs;
    }

    /**
     * The default pair the data names.
     *
     * @return {@code comet} and {@code percolator} versions
     */
    static String[] defaultPair() {
        String pair = System.getProperty(DEFAULT_PAIR, "");
        String[] versions = pair.split("/", -1);
        assertEquals(2, versions.length, () -> "-D" + DEFAULT_PAIR + "=COMET/PERCOLATOR: " + pair);
        return versions;
    }

    /**
     * Where one run's files go: {@code <report>/runs/<part>/<key>/<label>/}, created empty.
     *
     * @param part the part
     * @param key the build or pair
     * @param label the configuration
     * @return the directory
     * @throws IOException if it exists already or cannot be created
     */
    static Path runDirectory(String part, String key, String label) throws IOException {
        Path directory = MatrixSupport.report().resolve("runs").resolve(part).resolve(key);
        Files.createDirectories(directory);
        Path run = directory.resolve(label);
        assertFalse(Files.exists(run), run + " exists: every run starts in a fresh directory");
        return Files.createDirectory(run);
    }

    /**
     * What the product derives from Percolator's tables and weights: per table its parsed rows, the
     * rows its default filter selects and their identifiers in the table's order (a PSM's without
     * the directory Comet prefixes it with); the weights as its reader reads them.
     *
     * @param tables table file name to file; a missing table is recorded as absent
     * @param weights the weights file
     * @return the derived values
     * @throws IOException if a file cannot be read
     * @throws PercolatorOutputException if the weights cannot be parsed
     */
    static Map<String, Object> derived(Map<String, Path> tables, Path weights)
            throws IOException, PercolatorOutputException {
        Map<String, Object> derived = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : tables.entrySet()) {
            String name = entry.getKey();
            Path file = entry.getValue();
            Map<String, Object> table = new LinkedHashMap<>();
            if (file == null || !Files.isRegularFile(file)) {
                table.put("present", false);
                derived.put(name, table);
                continue;
            }
            ResultTable parsed = ResultTableReader.readAll(file);
            boolean peptides = name.contains("peptides");
            List<String> selected = new ArrayList<>();
            for (ResultRow row : parsed.rows()) {
                boolean passes =
                        peptides
                                ? PeptideQValueFilter.DEFAULT.passes(row)
                                : PsmQValueFilter.DEFAULT.passes(row);
                if (passes) {
                    selected.add(
                            peptides
                                    ? row.peptide()
                                    : VersionMatrixPairs.withoutDirectory(row.psmId()));
                }
            }
            table.put("present", true);
            table.put("rows", parsed.rows().size());
            table.put("passingDefaultCutoff", selected.size());
            table.put("selected", selected);
            derived.put(name, table);
        }
        LearnedWeights learned = WeightsReader.read(weights);
        Map<String, Object> weightValues = new LinkedHashMap<>();
        weightValues.put("splits", learned.splitCount());
        weightValues.put("features", learned.featureNames());
        derived.put("weights", weightValues);
        return derived;
    }

    /**
     * Writes one run's record to {@code <report>/records/<part>__<key>__<label>.json}.
     *
     * @param planned the planned run
     * @param key the build or pair
     * @param directory the run's directory
     * @param values everything else recorded
     * @throws IOException if it cannot be written
     */
    static void record(Planned planned, String key, Path directory, Map<String, Object> values)
            throws IOException {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("part", planned.part());
        document.put("key", key);
        document.put("label", planned.label());
        document.put("configuration", planned.configuration());
        document.put("directory", MatrixSupport.report().relativize(directory).toString());
        document.putAll(values);
        Path records = Files.createDirectories(MatrixSupport.report().resolve("records"));
        Path file =
                records.resolve(
                        MatrixSupport.Row.fileName(
                                planned.part() + "__" + key + "__" + planned.label()));
        assertTrue(!Files.exists(file), file + " written twice");
        Files.writeString(file, MatrixSupport.Json.write(document) + "\n", StandardCharsets.UTF_8);
    }
}
