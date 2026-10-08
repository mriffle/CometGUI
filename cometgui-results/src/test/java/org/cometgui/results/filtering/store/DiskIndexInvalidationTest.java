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

package org.cometgui.results.filtering.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.CRC32C;
import org.cometgui.domain.ports.HashService;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.testing.Fixtures;
import org.cometgui.results.testing.OpenFiles;
import org.cometgui.results.testing.ScratchFixtures;
import org.cometgui.results.testing.TestHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The disk-backed store never answers from an index or sort file it cannot trust (design decision
 * P10-4): every check of {@link DiskIndex} and {@link SortFile} is seen to fail on the defect it
 * exists to catch, with the reason named, and the store rebuilds and then answers as the raw table
 * says. Every raw table here is a copy, under this test's own directory, of the constructed
 * shuffled table (23 rows; {@code constructed/CONSTRUCTED.txt}); the checked-in fixtures are never
 * touched. The index offsets below are typed from the format documented on {@link DiskIndex}, not
 * read from its constants.
 */
class DiskIndexInvalidationTest {

    /** The shuffled table at 0.01: 23 rows, 9 passing, 7 failing, 7 unknown (hand count). */
    private static final FilterCounts AT_ONE_PERCENT = new FilterCounts(23, 9, 7, 7);

    /** A row appended to change the raw table. */
    private static final String EXTRA_ROW = "x_1_2_1\t1\t0.001\t0\tK.A.R\tp\n";

    @TempDir private Path work;

    private Path raw;
    private Path rawDirectory;
    private Path indexDirectory;
    private Path indexFile;
    private TestHasher hasher;

    @BeforeEach
    void copyTheTable() throws IOException {
        Path source =
                Fixtures.verified(
                        ResultStoreContract.SHUFFLED, ResultStoreContract.SHUFFLED_SHA256);
        rawDirectory = Files.createDirectories(work.resolve("outputs"));
        raw = rawDirectory.resolve("psms.tsv");
        Files.copy(source, raw, StandardCopyOption.COPY_ATTRIBUTES);
        indexDirectory = work.resolve("results");
        indexFile = indexDirectory.resolve("target-psms.index");
        hasher = new TestHasher();
    }

    private DiskResultStore open() throws IOException {
        return DiskResultStore.open(raw, TableKind.TARGET_PSMS, indexDirectory, hasher);
    }

    /** Opens, checks the report and the counts, closes. */
    private void expectBuilt(Optional<IndexProblem> problem, FilterCounts counts)
            throws IOException {
        try (DiskResultStore store = open()) {
            assertEquals(new DiskResultStore.IndexReport(true, problem), store.indexReport());
            assertEquals(counts, store.counts(PsmQValueFilter.DEFAULT));
        }
        try (DiskResultStore again = open()) {
            assertEquals(
                    new DiskResultStore.IndexReport(false, Optional.empty()),
                    again.indexReport(),
                    "the rebuilt index is reused");
        }
    }

    private void buildOnce() throws IOException {
        try (DiskResultStore store = open()) {
            assertEquals(
                    new DiskResultStore.IndexReport(true, Optional.of(IndexProblem.ABSENT)),
                    store.indexReport());
            assertEquals(AT_ONE_PERCENT, store.counts(PsmQValueFilter.DEFAULT));
        }
    }

    /** Edits the index file's bytes in place. */
    private void editIndex(Consumer<ByteBuffer> edit) throws IOException {
        ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(indexFile));
        edit.accept(bytes);
        Files.write(indexFile, bytes.array());
    }

    /** Recomputes both checksums of an index or sort file, as a forger would. */
    private static void reseal(ByteBuffer bytes, int headerBytes, int bodyCrcAt) {
        CRC32C body = new CRC32C();
        body.update(bytes.array(), headerBytes, bytes.capacity() - headerBytes);
        bytes.putInt(bodyCrcAt, (int) body.getValue());
        CRC32C header = new CRC32C();
        header.update(bytes.array(), 0, bodyCrcAt + 4);
        bytes.putInt(bodyCrcAt + 4, (int) header.getValue());
    }

    /* ---------------------------------------------------------------- reuse */

    @Test
    @DisplayName(
            "an unchanged raw table's index is reused: built once (no index), then reused; the"
                    + " raw table hashed once per opening, through the injected hasher")
    void reusedWhenUnchanged() throws IOException {
        buildOnce();
        assertEquals(1, hasher.calls());
        try (DiskResultStore store = open()) {
            assertEquals(
                    new DiskResultStore.IndexReport(false, Optional.empty()), store.indexReport());
            assertEquals(AT_ONE_PERCENT, store.counts(PsmQValueFilter.DEFAULT));
            assertEquals(23, store.rowCount());
        }
        assertEquals(2, hasher.calls());
        assertEquals(56L * 23 + 96, Files.size(indexFile), "96-byte header, 56 bytes a row");
    }

    /* ---------------------------------------------------------------- the raw table changed */

    @Test
    @DisplayName(
            "raw bytes changed, same size and time: rebuilt for RAW_CONTENT_CHANGED, and the counts"
                    + " are the new table's")
    void rawContentChanged() throws IOException {
        buildOnce();
        FileTime time = Files.getLastModifiedTime(raw);
        String text = Files.readString(raw, StandardCharsets.UTF_8);
        // Line 4's q-value 0.01 becomes 0.91: same length, one passing row fewer.
        String changed = text.replaceFirst("\t1\\.2\t0\\.01\t", "\t1.2\t0.91\t");
        assertFalse(changed.equals(text), "the edit took");
        Files.writeString(raw, changed, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(raw, time);
        assertEquals(text.length(), Files.size(raw));
        expectBuilt(Optional.of(IndexProblem.RAW_CONTENT_CHANGED), new FilterCounts(23, 8, 8, 7));
    }

    @Test
    @DisplayName("a row appended: rebuilt for RAW_SIZE_CHANGED, 24 rows")
    void rawSizeChanged() throws IOException {
        buildOnce();
        Files.writeString(
                raw,
                "x_1_2_1\t1\t0.001\t0\tK.A.R\tp\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        expectBuilt(Optional.of(IndexProblem.RAW_SIZE_CHANGED), new FilterCounts(24, 10, 7, 7));
    }

    @Test
    @DisplayName("only the modification time changed: rebuilt for RAW_TIME_CHANGED")
    void rawTimeChanged() throws IOException {
        buildOnce();
        FileTime time = Files.getLastModifiedTime(raw);
        Files.setLastModifiedTime(raw, FileTime.fromMillis(time.toMillis() + 1000));
        expectBuilt(Optional.of(IndexProblem.RAW_TIME_CHANGED), AT_ONE_PERCENT);
    }

    @Test
    @DisplayName(
            "a rebuilt index removes the table's sort files with it: the next sort is built again"
                    + " and is the new table's order")
    void rebuiltIndexDropsSortFiles() throws IOException {
        ResultSort byQ = ResultSort.ascending(ResultSort.Column.Q_VALUE);
        ResultQuery passing = ResultQuery.firstPage(PsmQValueFilter.DEFAULT).withSort(byQ);
        try (DiskResultStore store = open()) {
            assertEquals(3L, store.query(passing).rows().get(0).line());
        }
        Path sortFile = indexDirectory.resolve("target-psms.sort-q-value-ascending");
        assertTrue(Files.isRegularFile(sortFile));
        FileTime time = Files.getLastModifiedTime(raw);
        String text = Files.readString(raw, StandardCharsets.UTF_8);
        // Line 3's q-value 0 becomes 1: line 21, the other 0, comes first.
        Files.writeString(
                raw, text.replaceFirst("\t3\\.1\t0\t", "\t3.1\t1\t"), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(raw, time);
        try (DiskResultStore store = open()) {
            assertEquals(
                    Optional.of(IndexProblem.RAW_CONTENT_CHANGED), store.indexReport().problem());
            assertFalse(Files.exists(sortFile), "the stale sort file is gone");
            assertEquals(21L, store.query(passing).rows().get(0).line());
            assertEquals(
                    Optional.of(
                            new DiskResultStore.IndexReport(
                                    true, Optional.of(IndexProblem.ABSENT))),
                    store.sortReport(byQ));
        }
    }

    @Test
    @DisplayName(
            "the raw table changed while the store is open: queries and row reads refuse with"
                    + " RAW_CHANGED_SINCE_OPENED rather than answer from the old index")
    void rawChangedWhileOpen() throws IOException {
        try (DiskResultStore store = open()) {
            assertEquals(9, store.query(ResultQuery.firstPage(PsmQValueFilter.DEFAULT)).matching());
            Files.writeString(
                    raw,
                    "x_1_2_1\t1\t0.001\t0\tK.A.R\tp\n",
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
            ResultIndexException refused =
                    assertThrows(
                            ResultIndexException.class,
                            () -> store.query(ResultQuery.firstPage(PsmQValueFilter.DEFAULT)));
            assertEquals(IndexProblem.RAW_CHANGED_SINCE_OPENED, refused.problem());
            assertTrue(refused.getMessage().contains(raw.toString()), refused.getMessage());
            assertTrue(
                    refused.getMessage()
                            .contains("the raw table changed after its index was opened"),
                    refused.getMessage());
            assertEquals(
                    IndexProblem.RAW_CHANGED_SINCE_OPENED,
                    assertThrows(ResultIndexException.class, () -> store.row(new RowKey(2)))
                            .problem());
            assertEquals(
                    IndexProblem.RAW_CHANGED_SINCE_OPENED,
                    assertThrows(
                                    ResultIndexException.class,
                                    () ->
                                            store.positionOf(
                                                    new RowKey(3),
                                                    ResultQuery.firstPage(PsmQValueFilter.DEFAULT)))
                            .problem());
        }
    }

    @Test
    @DisplayName(
            "a forged index -- one row's q-value changed and both checksums recomputed -- passes"
                    + " the file checks, but reading that row back from the raw table refuses it")
    void forgedIndexCaughtOnReadBack() throws IOException {
        buildOnce();
        // Row 1 (line 3) has q-value 0; record it as 0.5 instead.
        editIndex(
                bytes -> {
                    bytes.putDouble(96 + 56 + 16, 0.5);
                    reseal(bytes, 96, 80);
                });
        try (DiskResultStore store = open()) {
            assertEquals(
                    new DiskResultStore.IndexReport(false, Optional.empty()), store.indexReport());
            ResultIndexException refused =
                    assertThrows(ResultIndexException.class, () -> store.row(new RowKey(3)));
            assertEquals(IndexProblem.RAW_CHANGED_SINCE_OPENED, refused.problem());
            assertTrue(refused.getMessage().contains("line 3"), refused.getMessage());
            assertThrows(
                    ResultIndexException.class,
                    () ->
                            store.query(
                                    ResultQuery.firstPage(PsmQValueFilter.parse("1"))
                                            .withCategory(Category.ALL)));
        }
    }

    @Test
    @DisplayName(
            "a forged index whose offsets point at the wrong rows (rows 0 and 1 swapped, checksums"
                    + " recomputed) is refused on read-back")
    void forgedOffsetsCaughtOnReadBack() throws IOException {
        buildOnce();
        editIndex(
                bytes -> {
                    long first = bytes.getLong(96);
                    int firstLength = bytes.getInt(96 + 8);
                    bytes.putLong(96, bytes.getLong(96 + 56));
                    bytes.putInt(96 + 8, bytes.getInt(96 + 56 + 8));
                    bytes.putLong(96 + 56, first);
                    bytes.putInt(96 + 56 + 8, firstLength);
                    reseal(bytes, 96, 80);
                });
        try (DiskResultStore store = open()) {
            assertEquals(
                    IndexProblem.RAW_CHANGED_SINCE_OPENED,
                    assertThrows(ResultIndexException.class, () -> store.row(new RowKey(2)))
                            .problem());
        }
    }

    @Test
    @DisplayName(
            "a forged index one record short (row count and checksums made consistent) is used,"
                    + " but the first pass over the raw table finds a row it does not index and"
                    + " refuses")
    void forgedShortIndexCaughtOnStreaming() throws IOException {
        buildOnce();
        byte[] whole = Files.readAllBytes(indexFile);
        ByteBuffer shorter = ByteBuffer.wrap(java.util.Arrays.copyOf(whole, whole.length - 56));
        shorter.putLong(72, 22);
        reseal(shorter, 96, 80);
        Files.write(indexFile, shorter.array());
        try (DiskResultStore store = open()) {
            assertEquals(
                    new DiskResultStore.IndexReport(false, Optional.empty()), store.indexReport());
            assertEquals(22, store.rowCount());
            ResultIndexException refused =
                    assertThrows(
                            ResultIndexException.class,
                            () ->
                                    store.query(
                                            ResultQuery.firstPage(PsmQValueFilter.DEFAULT)
                                                    .withText("human")));
            assertEquals(IndexProblem.RAW_CHANGED_SINCE_OPENED, refused.problem());
            assertTrue(refused.getMessage().contains("line 24"), refused.getMessage());
        }
    }

    /* ---------------------------------------------------------------- a damaged index */

    @Test
    @DisplayName("the index truncated by one byte, then to half: rebuilt for TRUNCATED")
    void truncatedIndex() throws IOException {
        buildOnce();
        byte[] whole = Files.readAllBytes(indexFile);
        Files.write(indexFile, java.util.Arrays.copyOf(whole, whole.length - 1));
        expectBuilt(Optional.of(IndexProblem.TRUNCATED), AT_ONE_PERCENT);
        Files.write(indexFile, java.util.Arrays.copyOf(whole, whole.length / 2));
        expectBuilt(Optional.of(IndexProblem.TRUNCATED), AT_ONE_PERCENT);
        Files.write(indexFile, java.util.Arrays.copyOf(whole, 40));
        expectBuilt(Optional.of(IndexProblem.TRUNCATED), AT_ONE_PERCENT);
    }

    @Test
    @DisplayName("an empty index file and one of junk: rebuilt for TRUNCATED and NOT_AN_INDEX")
    void notAnIndex() throws IOException {
        buildOnce();
        Files.write(indexFile, new byte[0]);
        expectBuilt(Optional.of(IndexProblem.TRUNCATED), AT_ONE_PERCENT);
        Files.writeString(indexFile, "x".repeat(4000), StandardCharsets.US_ASCII);
        expectBuilt(Optional.of(IndexProblem.NOT_AN_INDEX), AT_ONE_PERCENT);
    }

    @Test
    @DisplayName("one body byte flipped (a q-value status): rebuilt for BODY_DAMAGED")
    void bodyDamaged() throws IOException {
        buildOnce();
        editIndex(bytes -> bytes.put(96 + 56 * 5 + 12, (byte) 1));
        expectBuilt(Optional.of(IndexProblem.BODY_DAMAGED), AT_ONE_PERCENT);
    }

    @Test
    @DisplayName("one header byte flipped (the recorded raw size): rebuilt for HEADER_DAMAGED")
    void headerDamaged() throws IOException {
        buildOnce();
        editIndex(bytes -> bytes.put(31, (byte) (bytes.get(31) ^ 1)));
        expectBuilt(Optional.of(IndexProblem.HEADER_DAMAGED), AT_ONE_PERCENT);
    }

    @Test
    @DisplayName(
            "another format version, or another record size, resealed: rebuilt for OTHER_FORMAT")
    void otherFormat() throws IOException {
        buildOnce();
        editIndex(
                bytes -> {
                    bytes.putInt(8, 2);
                    reseal(bytes, 96, 80);
                });
        expectBuilt(Optional.of(IndexProblem.OTHER_FORMAT), AT_ONE_PERCENT);
        editIndex(
                bytes -> {
                    bytes.putInt(12, 64);
                    reseal(bytes, 96, 80);
                });
        expectBuilt(Optional.of(IndexProblem.OTHER_FORMAT), AT_ONE_PERCENT);
    }

    @Test
    @DisplayName(
            "the target-PSM index copied to the decoy-PSM name, opened as the decoy table: rebuilt"
                    + " for OTHER_TABLE")
    void otherTable() throws IOException {
        buildOnce();
        Files.copy(indexFile, indexDirectory.resolve("decoy-psms.index"));
        try (DiskResultStore store =
                DiskResultStore.open(raw, TableKind.DECOY_PSMS, indexDirectory, hasher)) {
            assertEquals(
                    new DiskResultStore.IndexReport(true, Optional.of(IndexProblem.OTHER_TABLE)),
                    store.indexReport());
        }
    }

    @Test
    @DisplayName(
            "a recorded SHA-256 resealed to another value, size and time unchanged: rebuilt for"
                    + " RAW_CONTENT_CHANGED -- the hash is checked, not only size and time")
    void recordedHashDiffers() throws IOException {
        buildOnce();
        editIndex(
                bytes -> {
                    bytes.put(40, (byte) (bytes.get(40) ^ 0x80));
                    reseal(bytes, 96, 80);
                });
        expectBuilt(Optional.of(IndexProblem.RAW_CONTENT_CHANGED), AT_ONE_PERCENT);
    }

    @Test
    @DisplayName("a row count resealed to one more than the body holds: rebuilt for TRUNCATED")
    void rowCountDisagrees() throws IOException {
        buildOnce();
        editIndex(
                bytes -> {
                    bytes.putLong(72, bytes.getLong(72) + 1);
                    reseal(bytes, 96, 80);
                });
        expectBuilt(Optional.of(IndexProblem.TRUNCATED), AT_ONE_PERCENT);
    }

    /* ---------------------------------------------------------------- sort files */

    @Test
    @DisplayName(
            "sort files: built on first use, reused by the next store; damaged, truncated or"
                    + " holding a row twice (resealed), rebuilt for the named reason, and the order"
                    + " is still the oracle's")
    void sortFiles() throws IOException {
        ResultSort byPeptide = ResultSort.descending(ResultSort.Column.PEPTIDE);
        ResultQuery all = ResultQuery.firstPage(PsmQValueFilter.DEFAULT).withCategory(Category.ALL);
        List<Long> expected = StoreOracle.read(raw).expect("0.01", "ALL", "", "PEPTIDE", true);
        Path sortFile = indexDirectory.resolve("target-psms.sort-peptide-descending");
        try (DiskResultStore store = open()) {
            assertEquals(expected, ResultStoreContract.allLines(store, all.withSort(byPeptide), 5));
            assertEquals(
                    Optional.of(
                            new DiskResultStore.IndexReport(
                                    true, Optional.of(IndexProblem.ABSENT))),
                    store.sortReport(byPeptide));
            assertEquals(Optional.empty(), store.sortReport(ResultSort.FILE_ORDER));
        }
        assertEquals(72 + 4L * 23, Files.size(sortFile), "72-byte header, 4 bytes a row");
        try (DiskResultStore store = open()) {
            assertEquals(expected, ResultStoreContract.allLines(store, all.withSort(byPeptide), 5));
            assertEquals(
                    Optional.of(new DiskResultStore.IndexReport(false, Optional.empty())),
                    store.sortReport(byPeptide));
        }
        record Damage(String name, IndexProblem problem, Consumer<ByteBuffer> edit) {}
        List<Damage> damages =
                List.of(
                        new Damage(
                                "body byte",
                                IndexProblem.BODY_DAMAGED,
                                bytes -> bytes.put(72 + 7, (byte) (bytes.get(72 + 7) ^ 1))),
                        new Damage(
                                "header byte",
                                IndexProblem.HEADER_DAMAGED,
                                bytes -> bytes.put(25, (byte) 1)),
                        new Damage(
                                "a row twice",
                                IndexProblem.NOT_A_PERMUTATION,
                                bytes -> {
                                    bytes.putInt(72 + 4, bytes.getInt(72));
                                    reseal(bytes, 72, 64);
                                }),
                        new Damage(
                                "a row out of range",
                                IndexProblem.NOT_A_PERMUTATION,
                                bytes -> {
                                    bytes.putInt(72 + 8, 23);
                                    reseal(bytes, 72, 64);
                                }),
                        new Damage(
                                "another column",
                                IndexProblem.OTHER_TABLE,
                                bytes -> {
                                    bytes.putInt(16, ResultSort.Column.PSM_ID.ordinal());
                                    reseal(bytes, 72, 64);
                                }),
                        new Damage(
                                "another direction",
                                IndexProblem.OTHER_TABLE,
                                bytes -> {
                                    bytes.putInt(20, 0);
                                    reseal(bytes, 72, 64);
                                }),
                        new Damage(
                                "another raw table",
                                IndexProblem.RAW_CONTENT_CHANGED,
                                bytes -> {
                                    bytes.put(33, (byte) (bytes.get(33) ^ 1));
                                    reseal(bytes, 72, 64);
                                }),
                        new Damage(
                                "a format version",
                                IndexProblem.OTHER_FORMAT,
                                bytes -> {
                                    bytes.putInt(8, 7);
                                    reseal(bytes, 72, 64);
                                }),
                        new Damage(
                                "junk",
                                IndexProblem.NOT_AN_INDEX,
                                bytes -> bytes.put(0, (byte) 'X')));
        for (Damage damage : damages) {
            ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(sortFile));
            damage.edit().accept(bytes);
            Files.write(sortFile, bytes.array());
            try (DiskResultStore store = open()) {
                assertEquals(
                        expected,
                        ResultStoreContract.allLines(store, all.withSort(byPeptide), 5),
                        damage.name());
                assertEquals(
                        Optional.of(
                                new DiskResultStore.IndexReport(
                                        true, Optional.of(damage.problem()))),
                        store.sortReport(byPeptide),
                        damage.name());
            }
        }
        byte[] whole = Files.readAllBytes(sortFile);
        for (int length : new int[] {whole.length - 4, 10}) {
            Files.write(sortFile, java.util.Arrays.copyOf(whole, length));
            try (DiskResultStore store = open()) {
                assertEquals(
                        expected, ResultStoreContract.allLines(store, all.withSort(byPeptide), 5));
                assertEquals(
                        Optional.of(
                                new DiskResultStore.IndexReport(
                                        true, Optional.of(IndexProblem.TRUNCATED))),
                        store.sortReport(byPeptide),
                        "truncated to " + length);
            }
        }
    }

    /* ------------------------------------------------------------ the raw table is read only */

    @Test
    @DisplayName(
            "gate 4: the raw table's SHA-256, size and time are equal before and after indexing,"
                    + " every sort and a text filter, and closing; nothing is written beside it,"
                    + " and the index directory holds only the index and sort files")
    void rawTableUntouched() throws IOException {
        String before = ScratchFixtures.sha256(raw);
        long size = Files.size(raw);
        FileTime time = Files.getLastModifiedTime(raw);
        try (DiskResultStore store = open()) {
            for (ResultSort.Column column : ResultSort.Column.values()) {
                for (ResultSort.Direction direction : ResultSort.Direction.values()) {
                    store.query(
                            ResultQuery.firstPage(PsmQValueFilter.DEFAULT)
                                    .withSort(new ResultSort(column, direction))
                                    .withText("human"));
                }
            }
        }
        assertEquals(before, ScratchFixtures.sha256(raw));
        assertEquals(size, Files.size(raw));
        assertEquals(time, Files.getLastModifiedTime(raw));
        try (Stream<Path> files = Files.list(rawDirectory)) {
            assertEquals(List.of(raw), files.toList(), "nothing beside the raw table");
        }
        TreeSet<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(indexDirectory)) {
            files.forEach(file -> names.add(String.valueOf(file.getFileName())));
        }
        assertEquals("target-psms.index", names.first());
        assertEquals(1 + 18, names.size(), "the index and 18 sort files (file order needs none)");
        for (String name : names) {
            assertTrue(
                    "target-psms.index".equals(name) || name.startsWith("target-psms.sort-"), name);
            assertFalse(name.endsWith(".tmp") || name.endsWith(".run"), name);
        }
    }

    @Test
    @DisplayName(
            "the index directory may not be the raw table's own: refused before anything is"
                    + " written")
    void indexBesideTheRawTableRefused() throws IOException {
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                DiskResultStore.open(
                                        raw, TableKind.TARGET_PSMS, rawDirectory, hasher));
        assertTrue(
                refused.getMessage().contains("raw table's own directory"), refused.getMessage());
        try (Stream<Path> files = Files.list(rawDirectory)) {
            assertEquals(List.of(raw), files.toList());
        }
        assertEquals(0, hasher.calls());
    }

    @Test
    @DisplayName(
            "a table the reader refuses part-way (a blank line) leaves no index and no temporary"
                    + " file behind")
    void failedBuildLeavesNothing() throws IOException {
        Files.writeString(
                raw,
                Files.readString(raw, StandardCharsets.UTF_8) + "\nx_1_2_1\t1\t0\t0\tK.A.R\tp\n",
                StandardCharsets.UTF_8);
        PercolatorOutputException refused =
                assertThrows(PercolatorOutputException.class, this::open);
        assertEquals(PercolatorOutputException.Problem.BLANK_LINE, refused.problem());
        try (Stream<Path> files = Files.list(indexDirectory)) {
            assertEquals(List.of(), files.toList());
        }
        assertFalse(
                OpenFiles.descriptorTo(indexDirectory.toString()),
                "the temporary index file is closed, not only deleted");
        assertFalse(OpenFiles.descriptorTo(raw.toString()), "the raw table is closed");
    }

    @Test
    @DisplayName(
            "close releases everything: the raw table's channel and every mapping -- the process"
                    + " (on Linux) neither holds nor maps them after close -- and the index and"
                    + " sort files can be deleted at once")
    void closeReleasesEverything() throws IOException {
        DiskResultStore store = open();
        store.query(
                ResultQuery.firstPage(PsmQValueFilter.DEFAULT)
                        .withSort(ResultSort.ascending(ResultSort.Column.SCORE)));
        Path sortFile = indexDirectory.resolve("target-psms.sort-score-ascending");
        if (OpenFiles.available()) {
            assertTrue(OpenFiles.descriptorTo(raw.toString()), "the raw table is open while open");
            assertTrue(OpenFiles.mapped(indexFile.toString()), "the index is mapped while open");
            assertTrue(OpenFiles.mapped(sortFile.toString()), "the sort file is mapped while open");
        }
        store.close();
        assertFalse(OpenFiles.descriptorTo(raw.toString()), "the raw table's channel is closed");
        assertFalse(OpenFiles.descriptorTo(indexDirectory.toString()), "no index file is open");
        assertFalse(OpenFiles.mapped(indexFile.toString()), "the index is unmapped");
        assertFalse(OpenFiles.mapped(sortFile.toString()), "the sort file is unmapped");
        try (Stream<Path> files = Files.list(indexDirectory)) {
            List<Path> all = files.toList();
            assertEquals(3, all.size(), "the index and the score column's two sort files");
            for (Path file : all) {
                Files.delete(file);
            }
        }
        Files.delete(indexDirectory);
        assertFalse(Files.exists(indexDirectory));
    }

    @Test
    @DisplayName(
            "a store closed on one thread refuses another thread's call at once, rather than"
                    + " leaving it waiting")
    void closedAcrossThreads() throws IOException {
        DiskResultStore store = open();
        store.close();
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            Future<Long> call = other.submit(store::rowCount);
            ExecutionException refused =
                    assertThrows(ExecutionException.class, () -> call.get(10, TimeUnit.SECONDS));
            assertTrue(refused.getCause() instanceof IllegalStateException, refused.toString());
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    @DisplayName(
            "the raw table changed while it was being indexed: refused with"
                    + " RAW_CHANGED_WHILE_INDEXING, and no index is left to trust")
    void changedWhileIndexing() throws IOException {
        Files.createDirectories(indexDirectory);
        RawIdentity before = RawIdentity.of(raw, hasher);
        Files.writeString(raw, EXTRA_ROW, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        ResultIndexException refused =
                assertThrows(
                        ResultIndexException.class,
                        () -> DiskIndex.build(raw, TableKind.TARGET_PSMS, before, indexFile));
        assertEquals(IndexProblem.RAW_CHANGED_WHILE_INDEXING, refused.problem());
        assertTrue(
                refused.getMessage()
                        .contains(IndexProblem.RAW_CHANGED_WHILE_INDEXING.description()),
                refused.getMessage());
        try (Stream<Path> files = Files.list(indexDirectory)) {
            assertEquals(List.of(), files.toList(), "no index, no temporary file");
        }
    }

    @Test
    @DisplayName(
            "the raw table changed while it was being hashed (a hasher that appends to it first):"
                    + " opening refuses with RAW_CHANGED_WHILE_INDEXING")
    void changedWhileHashing() {
        HashService appending =
                path -> {
                    Files.writeString(
                            path, EXTRA_ROW, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                    return hasher.hash(path);
                };
        ResultIndexException refused =
                assertThrows(
                        ResultIndexException.class,
                        () ->
                                DiskResultStore.open(
                                        raw, TableKind.TARGET_PSMS, indexDirectory, appending));
        assertEquals(IndexProblem.RAW_CHANGED_WHILE_INDEXING, refused.problem());
    }

    @Test
    @DisplayName("the text filter's bit set: one long per 64 rows, rounded up")
    void bitSetWords() {
        assertEquals(0, DiskResultStore.words(0));
        assertEquals(1, DiskResultStore.words(1));
        assertEquals(1, DiskResultStore.words(64));
        assertEquals(2, DiskResultStore.words(65));
        assertEquals(15_625, DiskResultStore.words(1_000_000));
    }
}
