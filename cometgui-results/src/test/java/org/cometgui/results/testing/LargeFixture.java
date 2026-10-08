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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 10's large performance fixture ({@code R-RES-03}, design decision P10-2): a synthetic,
 * Percolator-shaped target PSM table of 1 000 000 rows and peptide table of 400 000 rows, written
 * by {@code scripts/fixtures/large-results-fixture.py} into the gitignored {@code
 * scratch/phase10/large/}, with a {@code manifest.json} of the counts that generator computed from
 * the values it assigned (Python {@code decimal}). The resource {@code
 * org/cometgui/results/large-fixture/CONSTRUCTED.txt} records it.
 *
 * <p>All three files are held to the SHA-256s pinned here before a path is returned. A missing or
 * different file fails the test, naming {@link #COMMAND}; nothing skips.
 */
public final class LargeFixture {

    /** The command, from the repository root, that writes the fixture this class pins. */
    public static final String COMMAND = "python3 scripts/fixtures/large-results-fixture.py";

    /** Where the command writes it, relative to the repository root. */
    public static final String DIRECTORY = "scratch/phase10/large";

    /** The PSM table's name in the manifest. */
    public static final String PSMS = "psms";

    /** The peptide table's name in the manifest. */
    public static final String PEPTIDES = "peptides";

    /** {@code psms.tsv} at the default size and seed: 1 000 000 rows, 147 155 274 bytes. */
    static final String PSMS_SHA256 =
            "4f7aaecd0164bc22be51de1c60dfed3e70ddf65a4283f38a1912c76ece8cfcf4";

    /** {@code peptides.tsv} at the default size and seed: 400 000 rows, 58 593 259 bytes. */
    static final String PEPTIDES_SHA256 =
            "bdda5589063d55c9c210f8eba7cd64f1489b95e8fa51fe4a9dccea9255e01e44";

    /** {@code manifest.json} at the default size and seed: 8105 bytes. */
    static final String MANIFEST_SHA256 =
            "4298cc2b05b3d86108a9dadf4ae832284a5aff1ca046f49b5c89d2a6ec832d85";

    private static final String REMAKE =
            "Make it again from the repository root with: "
                    + COMMAND
                    + " (about 15 s; then "
                    + COMMAND
                    + " --self-check).";

    private final Path directory;
    private final Map<String, Object> manifest;

    private LargeFixture(Path directory, Map<String, Object> manifest) {
        this.directory = directory;
        this.manifest = manifest;
    }

    /**
     * The fixture in the repository's {@code scratch/phase10/large/}, every file verified.
     *
     * @return the fixture
     * @throws org.opentest4j.AssertionFailedError if a file is missing or not the pinned bytes
     */
    public static LargeFixture locate() {
        return at(ScratchFixtures.repositoryRoot().resolve(DIRECTORY));
    }

    /**
     * The fixture in a given directory, every file verified against the default-size pins.
     *
     * @param directory the directory
     * @return the fixture
     * @throws org.opentest4j.AssertionFailedError if a file is missing or not the pinned bytes
     */
    public static LargeFixture at(Path directory) {
        Path manifestFile =
                ScratchFixtures.verified(
                        directory.resolve("manifest.json"), MANIFEST_SHA256, REMAKE);
        ScratchFixtures.verified(directory.resolve("psms.tsv"), PSMS_SHA256, REMAKE);
        ScratchFixtures.verified(directory.resolve("peptides.tsv"), PEPTIDES_SHA256, REMAKE);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed =
                    (Map<String, Object>)
                            MiniJson.parse(Files.readString(manifestFile, StandardCharsets.UTF_8));
            return new LargeFixture(directory, parsed);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /**
     * The directory holding the fixture.
     *
     * @return it
     */
    public Path directory() {
        return directory;
    }

    /**
     * The PSM table, {@code psms.tsv}.
     *
     * @return its path
     */
    public Path psms() {
        return directory.resolve("psms.tsv");
    }

    /**
     * The peptide table, {@code peptides.tsv}.
     *
     * @return its path
     */
    public Path peptides() {
        return directory.resolve("peptides.tsv");
    }

    /**
     * A table by its manifest name.
     *
     * @param table {@link #PSMS} or {@link #PEPTIDES}
     * @return its path
     */
    public Path table(String table) {
        return directory.resolve(text(tableEntry(table), "file"));
    }

    /**
     * The cutoffs the manifest counts at, as decimal text.
     *
     * @return {@code 0, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1} at the default arguments
     */
    public List<String> cutoffs() {
        List<String> cutoffs = new ArrayList<>();
        for (Object cutoff : list(manifest, "cutoffs")) {
            cutoffs.add((String) cutoff);
        }
        return List.copyOf(cutoffs);
    }

    /**
     * A table's data rows, as the manifest records them.
     *
     * @param table {@link #PSMS} or {@link #PEPTIDES}
     * @return the row count
     */
    public long rows(String table) {
        return number(tableEntry(table), "rows");
    }

    /**
     * The generator's expected counts for a table at a cutoff.
     *
     * @param table {@link #PSMS} or {@link #PEPTIDES}
     * @param cutoff one of {@link #cutoffs()}
     * @return the counts the generator computed from what it assigned
     */
    public IndependentCounts expected(String table, String cutoff) {
        for (Object element : list(tableEntry(table), "counts")) {
            Map<String, Object> counts = map(element);
            if (cutoff.equals(text(counts, "cutoff"))) {
                return new IndependentCounts(
                        number(counts, "total"),
                        number(counts, "passing"),
                        number(counts, "failing"),
                        number(counts, "unknown"));
            }
        }
        throw new IllegalArgumentException("the manifest has no count at " + cutoff);
    }

    /**
     * Every unknown q-value kind the generator wrote into a table.
     *
     * @param table {@link #PSMS} or {@link #PEPTIDES}
     * @return the kinds, in the manifest's order
     */
    public List<UnknownKind> unknownKinds(String table) {
        List<UnknownKind> kinds = new ArrayList<>();
        for (Object element : list(tableEntry(table), "unknown_by_kind")) {
            Map<String, Object> kind = map(element);
            kinds.add(
                    new UnknownKind(
                            text(kind, "label"),
                            text(kind, "text"),
                            text(kind, "category"),
                            number(kind, "rows")));
        }
        return List.copyOf(kinds);
    }

    /**
     * How many rows of a table carry each source file's {@code -N} base at the start of their
     * {@code PSMId}.
     *
     * @param table {@link #PSMS} or {@link #PEPTIDES}
     * @return rows by source base, in the manifest's order
     */
    public Map<String, Long> rowsBySource(String table) {
        Map<String, Long> rows = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry :
                map(tableEntry(table).get("rows_by_source")).entrySet()) {
            rows.put(entry.getKey(), ((BigDecimal) entry.getValue()).longValueExact());
        }
        return rows;
    }

    /**
     * The SHA-256 the manifest records for a table.
     *
     * @param table {@link #PSMS} or {@link #PEPTIDES}
     * @return the digest
     */
    public String recordedSha256(String table) {
        return text(tableEntry(table), "sha256");
    }

    /**
     * One kind of unknown q-value in the fixture.
     *
     * @param label the generator's name for it, such as {@code comma-decimal}
     * @param text the field exactly as written, such as {@code 0,01}
     * @param category the specification's category the generator put it in: {@code MISSING}, {@code
     *     UNPARSABLE} or {@code OUT_OF_RANGE}
     * @param rows how many rows carry it
     */
    public record UnknownKind(String label, String text, String category, long rows) {}

    private Map<String, Object> tableEntry(String table) {
        Map<String, Object> tables = map(manifest.get("tables"));
        if (!tables.containsKey(table)) {
            throw new IllegalArgumentException("the manifest has no table " + table);
        }
        return map(tables.get(table));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> object, String member) {
        return (List<Object>) object.get(member);
    }

    private static String text(Map<String, Object> object, String member) {
        return (String) object.get(member);
    }

    private static long number(Map<String, Object> object, String member) {
        return ((BigDecimal) object.get(member)).longValueExact();
    }
}
