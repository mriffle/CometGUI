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

package org.cometgui.tools.comet;

import static org.cometgui.tools.comet.PinText.HEADER;
import static org.cometgui.tools.comet.PinText.decoy;
import static org.cometgui.tools.comet.PinText.lines;
import static org.cometgui.tools.comet.PinText.target;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PinMerger} on constructed PIN files: one header, every row byte for byte in input order,
 * the feature-column check naming both files, write-once, LF output, the record's hash, and the
 * count of what was written against what was read. The real per-file PINs are merged in {@link
 * CometAdapterRealBinaryTest}.
 */
class PinMergerTest {

    @TempDir private Path directory;

    private RunLayout run;

    private final TestHashes hashes = new TestHashes();

    @BeforeEach
    void layout() throws IOException {
        run = new RunLayout(directory.resolve("run"));
        for (Path made : run.directories()) {
            Files.createDirectories(made);
        }
    }

    /**
     * A disk that changes one line on the way: the merge writes every byte, and the file does not
     * get them all (or gets some twice). Lines are counted from 0, the header.
     */
    private static class OneLineChanged extends FilterOutputStream {

        private final int line;

        private final int copies;

        private final ByteArrayOutputStream held = new ByteArrayOutputStream();

        private int current;

        /**
         * @param out the real file
         * @param line the line to change
         * @param copies how many times that line reaches the file: 0 loses it, 2 doubles it
         */
        OneLineChanged(OutputStream out, int line, int copies) {
            super(out);
            this.line = line;
            this.copies = copies;
        }

        @Override
        public void write(int value) throws IOException {
            if (current != line) {
                out.write(value);
            } else {
                held.write(value);
            }
            if (value == '\n') {
                if (current == line) {
                    for (int copy = 0; copy < copies; copy++) {
                        out.write(held.toByteArray());
                    }
                }
                current++;
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            for (int index = offset; index < offset + length; index++) {
                write(bytes[index]);
            }
        }
    }

    /** A disk that loses the last byte written to it: the final row's line terminator. */
    private static final class LastByteLost extends FilterOutputStream {

        private int pending = -1;

        LastByteLost(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int value) throws IOException {
            if (pending >= 0) {
                out.write(pending);
            }
            pending = value & 0xff;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            for (int index = offset; index < offset + length; index++) {
                write(bytes[index]);
            }
        }
    }

    private Path pin(String name, String text) throws IOException {
        return Files.writeString(
                run.cometOutputDirectory().resolve(name), text, StandardCharsets.ISO_8859_1);
    }

    private List<Path> pinDirectoryListing() throws IOException {
        try (Stream<Path> listed = Files.list(run.pinInputsDirectory())) {
            return listed.toList();
        }
    }

    @Test
    @DisplayName("one header, every row in input order byte for byte, counts and hash recorded")
    void merges() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1), target(2)));
        Path second = pin("b.pin", lines(HEADER, decoy(5), target(5)));
        PinMergeRecord record = PinMerger.merge(run, List.of(first, second), hashes);

        String expected = lines(HEADER, target(1), decoy(1), target(2), decoy(5), target(5));
        byte[] written = Files.readAllBytes(run.mergedPinFile());
        assertArrayEquals(expected.getBytes(StandardCharsets.ISO_8859_1), written);
        assertEquals(
                new PinMergeRecord(
                        List.of(
                                new PinMergeRecord.Input(first, 3),
                                new PinMergeRecord.Input(second, 2)),
                        5,
                        run.mergedPinFile(),
                        TestHashes.of(written)),
                record);
        assertEquals(List.of(run.mergedPinFile()), hashes.hashed());
        assertArrayEquals(written, hashes.contents().get(0));
        assertEquals(List.of(run.mergedPinFile()), pinDirectoryListing());
    }

    @Test
    @DisplayName("hand-counted: one header line and 3 + 2 data rows; multi-protein tabs kept")
    void handCounted() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1), target(2)));
        Path second = pin("b.pin", lines(HEADER, decoy(5), target(5)));
        PinMerger.merge(run, List.of(first, second), hashes);
        List<String> merged = Files.readAllLines(run.mergedPinFile(), StandardCharsets.ISO_8859_1);
        assertEquals(6, merged.size());
        assertEquals(1, merged.stream().filter(line -> line.startsWith("SpecId\t")).count());
        assertEquals(
                "run_1_3_1\t-1\t1\t1230.569506\t1228.585130\t0.000000\t1.000000\t0.147510"
                        + "\t3.788944\t0.522000\t36.660000\t0.2000\t1230.569506\t11\t0\t1\t0\t0"
                        + "\t0\t0\t1\t1\t1\t3.828641\t0.001615\t0.001615\tK.EWFAKGCENCEFHK.S"
                        + "\tDECOY_sp|Q06730|ZN33A_HUMAN\tDECOY_sp|Q06732|ZN33B_HUMAN",
                merged.get(2));
        assertEquals(29, merged.get(2).split("\t", -1).length);
    }

    @Test
    @DisplayName("a CRLF input never makes a mixed file: the merge is LF throughout")
    void crlfInput() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        Path second = pin("b.pin", lines(HEADER, decoy(5), target(5)).replace("\n", "\r\n"));
        PinMerger.merge(run, List.of(first, second), hashes);
        String merged = Files.readString(run.mergedPinFile(), StandardCharsets.ISO_8859_1);
        assertEquals(lines(HEADER, target(1), decoy(1), decoy(5), target(5)), merged);
        assertFalse(merged.contains("\r"));
    }

    @Test
    @DisplayName("a header-only input contributes zero rows and is recorded")
    void headerOnly() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        Path second = pin("b.pin", lines(HEADER));
        PinMergeRecord record = PinMerger.merge(run, List.of(first, second), hashes);
        assertEquals(
                List.of(new PinMergeRecord.Input(first, 2), new PinMergeRecord.Input(second, 0)),
                record.inputs());
        assertEquals(2, record.totalRows());
    }

    @Test
    @DisplayName("one renamed feature column fails naming both files and the column")
    void renamedColumn() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        Path second =
                pin("b.pin", lines(HEADER.replace("\tXcorr\t", "\tXCorr\t"), target(5), decoy(5)));
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () -> PinMerger.merge(run, List.of(first, second), hashes));
        assertEquals(
                "cannot merge the PIN files "
                        + first
                        + " and "
                        + second
                        + ": their feature columns differ, first at feature column 7 (\"Xcorr\""
                        + " in the first, \"XCorr\" in the second; 23 and 23 feature columns)."
                        + " Percolator needs every file to have the same features in the same"
                        + " order",
                refused.getMessage());
        assertEquals(second, refused.file());
        assertFalse(Files.exists(run.mergedPinFile()));
        assertEquals(List.of(), pinDirectoryListing());
        assertEquals(List.of(), hashes.hashed());
    }

    @Test
    @DisplayName("two swapped feature columns fail naming both files and the first of them")
    void swappedColumns() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        Path second = pin("b.pin", lines(HEADER.replace("deltLCn\tdeltCn", "deltCn\tdeltLCn")));
        Path third = pin("c.pin", lines(HEADER, target(9), decoy(9)));
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () -> PinMerger.merge(run, List.of(first, third, second), hashes));
        assertEquals(
                "cannot merge the PIN files "
                        + first
                        + " and "
                        + second
                        + ": their feature columns differ, first at feature column 4 (\"deltLCn\""
                        + " in the first, \"deltCn\" in the second; 23 and 23 feature columns)."
                        + " Percolator needs every file to have the same features in the same"
                        + " order",
                refused.getMessage());
        assertEquals(List.of(), pinDirectoryListing());
    }

    @Test
    @DisplayName("a missing or extra feature column fails, naming none on the shorter side")
    void differentCount() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        Path second = pin("b.pin", lines(HEADER.replace("\tabsdM\t", "\tabsdM\textra\t")));
        assertEquals(
                "cannot merge the PIN files "
                        + first
                        + " and "
                        + second
                        + ": their feature columns differ, first at feature column 24 (none in"
                        + " the first, \"extra\" in the second; 23 and 24 feature columns)."
                        + " Percolator needs every file to have the same features in the same"
                        + " order",
                assertThrows(
                                CometOutputException.class,
                                () -> PinMerger.merge(run, List.of(first, second), hashes))
                        .getMessage());
        Path third = pin("c.pin", lines(HEADER.replace("\tdM\tabsdM\t", "\tdM\t")));
        assertEquals(
                "cannot merge the PIN files "
                        + first
                        + " and "
                        + third
                        + ": their feature columns differ, first at feature column 23 (\"absdM\""
                        + " in the first, none in the second; 23 and 22 feature columns)."
                        + " Percolator needs every file to have the same features in the same"
                        + " order",
                assertThrows(
                                CometOutputException.class,
                                () -> PinMerger.merge(run, List.of(first, third), hashes))
                        .getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("every input and the merged file are closed, after success and after a mismatch")
    void closesTheFiles() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        Path second = pin("b.pin", lines(HEADER, decoy(5), target(5)));
        Path renamed = pin("c.pin", lines(HEADER.replace("\tSp\t", "\tSP\t"), target(6)));
        PinMerger.merge(run, List.of(first, second), hashes);
        assertEquals(List.of(), OpenFiles.to(first, second, run.mergedPinFile()));
        RunLayout other = new RunLayout(directory.resolve("other"));
        Files.createDirectories(other.pinInputsDirectory());
        assertThrows(
                CometOutputException.class,
                () -> PinMerger.merge(other, List.of(first, renamed), hashes));
        assertEquals(List.of(), OpenFiles.to(first, renamed));
    }

    @Test
    @DisplayName("an existing merged PIN is refused and left byte for byte as it was")
    void writeOnce() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        Files.writeString(run.mergedPinFile(), "earlier");
        FileAlreadyExistsException refused =
                assertThrows(
                        FileAlreadyExistsException.class,
                        () -> PinMerger.merge(run, List.of(first), hashes));
        assertEquals(
                run.mergedPinFile()
                        + ": the merged PIN file already exists and is written once; it was left"
                        + " as it was",
                refused.getMessage());
        assertEquals("earlier", Files.readString(run.mergedPinFile()));
        assertEquals(List.of(run.mergedPinFile()), pinDirectoryListing());
    }

    @Test
    @DisplayName("a broken row in a later input leaves no merged file and no temporary file")
    void brokenLaterInput() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        String whole = lines(HEADER, target(5), decoy(5));
        Path second = pin("b.pin", whole.substring(0, whole.length() - 9));
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () -> PinMerger.merge(run, List.of(first, second), hashes));
        assertEquals(
                "the PIN file "
                        + second
                        + " ends without a line terminator: line 3 is incomplete, so the file is"
                        + " truncated",
                refused.getMessage());
        assertEquals(List.of(), pinDirectoryListing());
        Path missing = run.cometOutputDirectory().resolve("absent.pin");
        assertEquals(
                "the PIN file " + missing + " does not exist: Comet did not write it",
                assertThrows(
                                CometOutputException.class,
                                () -> PinMerger.merge(run, List.of(first, missing), hashes))
                        .getMessage());
        assertEquals(List.of(), pinDirectoryListing());
    }

    @Test
    @DisplayName("a row lost between the merge and the disk fails the merge, stating both counts")
    void aRowLostOnTheWayToTheDisk() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1), target(2)));
        Path second = pin("b.pin", lines(HEADER, decoy(5), target(5)));
        // Line 4 of the merged file is the second input's first row, decoy(5): it never arrives.
        List<Path> written = new ArrayList<>();
        List<String> onDisk = new ArrayList<>();
        PinMerger.Output lossy =
                file -> {
                    written.add(file);
                    return new OneLineChanged(Files.newOutputStream(file), 4, 0) {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            onDisk.add(Files.readString(file, StandardCharsets.ISO_8859_1));
                        }
                    };
                };
        PinMergeRowCountException lost =
                assertThrows(
                        PinMergeRowCountException.class,
                        () -> PinMerger.merge(run, List.of(first, second), hashes, lossy));
        assertEquals(
                "the merged PIN file "
                        + run.mergedPinFile()
                        + " was not written completely: 5 data rows were read from the 2 per-file"
                        + " PINs, but the file that was written holds 4. The merged file was"
                        + " discarded, so Percolator is not run on an incomplete PIN",
                lost.getMessage());
        assertEquals(5, lost.rowsRead());
        assertEquals(4, lost.rowsWritten());
        assertEquals(run.mergedPinFile(), lost.output());
        // The row was really lost: the file the merge wrote, as the disk held it when it was
        // closed, is the merge of the inputs without that one row.
        assertEquals(1, written.size(), "the merge opened one file to write");
        assertEquals(
                List.of(lines(HEADER, target(1), decoy(1), target(2), target(5))),
                onDisk,
                "what reached the disk");
        assertFalse(Files.exists(run.mergedPinFile()), "no merged file is left");
        assertEquals(List.of(), pinDirectoryListing(), "nor the temporary file");
        assertEquals(List.of(), hashes.hashed(), "and nothing was hashed");
    }

    @Test
    @DisplayName("a row written twice fails the merge too: 5 read, 6 written")
    void aRowWrittenTwice() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1), target(2)));
        Path second = pin("b.pin", lines(HEADER, decoy(5), target(5)));
        PinMergeRowCountException doubled =
                assertThrows(
                        PinMergeRowCountException.class,
                        () ->
                                PinMerger.merge(
                                        run,
                                        List.of(first, second),
                                        hashes,
                                        file ->
                                                new OneLineChanged(
                                                        Files.newOutputStream(file), 1, 2)));
        assertEquals(5, doubled.rowsRead());
        assertEquals(6, doubled.rowsWritten());
        assertEquals(
                "the merged PIN file "
                        + run.mergedPinFile()
                        + " was not written completely: 5 data rows were read from the 2 per-file"
                        + " PINs, but the file that was written holds 6. The merged file was"
                        + " discarded, so Percolator is not run on an incomplete PIN",
                doubled.getMessage());
        assertEquals(List.of(), pinDirectoryListing());
        assertEquals(List.of(), hashes.hashed());
    }

    @Test
    @DisplayName("one row of one file, lost: the message counts in the singular")
    void theOnlyRowLost() throws IOException {
        Path only = pin("a.pin", lines(HEADER, target(1)));
        PinMergeRowCountException lost =
                assertThrows(
                        PinMergeRowCountException.class,
                        () ->
                                PinMerger.merge(
                                        run,
                                        List.of(only),
                                        hashes,
                                        file ->
                                                new OneLineChanged(
                                                        Files.newOutputStream(file), 1, 0)));
        assertEquals(
                "the merged PIN file "
                        + run.mergedPinFile()
                        + " was not written completely: 1 data row was read from the 1 per-file"
                        + " PIN, but the file that was written holds 0. The merged file was"
                        + " discarded, so Percolator is not run on an incomplete PIN",
                lost.getMessage());
        assertEquals(List.of(), pinDirectoryListing());
    }

    @Test
    @DisplayName("a written file cut short within its last row is refused and not left behind")
    void theLastRowCutShort() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        CometOutputException cut =
                assertThrows(
                        CometOutputException.class,
                        () ->
                                PinMerger.merge(
                                        run,
                                        List.of(first),
                                        hashes,
                                        file -> new LastByteLost(Files.newOutputStream(file))));
        String message = cut.getMessage();
        String prefix = "the PIN file " + run.pinInputsDirectory().resolve("merged.pin.");
        String suffix =
                ".part ends without a line terminator: line 3 is incomplete, so the file is"
                        + " truncated";
        assertEquals(prefix, message.substring(0, prefix.length()));
        assertEquals(suffix, message.substring(message.length() - suffix.length()));
        assertFalse(Files.exists(run.mergedPinFile()));
        assertEquals(List.of(), pinDirectoryListing());
        assertEquals(List.of(), hashes.hashed());
    }

    @Test
    @DisplayName("the product's own output writes every row: the record's total is the file's")
    void theRecordCountsTheWrittenFile() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1), target(2)));
        Path second = pin("b.pin", lines(HEADER, decoy(5), target(5)));
        PinMergeRecord record =
                PinMerger.merge(run, List.of(first, second), hashes, PinMerger.TO_THE_FILE_SYSTEM);
        List<String> merged = Files.readAllLines(run.mergedPinFile(), StandardCharsets.ISO_8859_1);
        assertEquals(5, merged.size() - 1, "data rows in the file, counted here");
        assertEquals(5, record.totalRows());
        assertThrows(
                NullPointerException.class,
                () -> PinMerger.merge(run, List.of(first), hashes, null));
    }

    @Test
    @DisplayName("no input, the same input twice, and nulls are refused")
    void refusals() throws IOException {
        Path first = pin("a.pin", lines(HEADER, target(1), decoy(1)));
        assertEquals(
                "a PIN merge needs at least one PIN file",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> PinMerger.merge(run, List.of(), hashes))
                        .getMessage());
        Path again = run.cometOutputDirectory().resolve("x/../a.pin");
        assertEquals(
                "the PIN file " + again + " is given twice; its rows would be merged twice",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> PinMerger.merge(run, List.of(first, again), hashes))
                        .getMessage());
        assertThrows(
                NullPointerException.class,
                () -> PinMerger.merge(Nulls.of(RunLayout.class), List.of(first), hashes));
        assertThrows(
                NullPointerException.class,
                () -> PinMerger.merge(run, List.of(first), Nulls.of(TestHashes.class)));
        assertEquals(List.of(), pinDirectoryListing());
    }

    @Test
    @DisplayName("a merge record's total must be its inputs' sum")
    void recordArithmetic() {
        FileHashes some = TestHashes.of(new byte[0]);
        Path out = TestPaths.absolute("m.pin");
        List<PinMergeRecord.Input> inputs =
                List.of(
                        new PinMergeRecord.Input(TestPaths.absolute("a.pin"), 3),
                        new PinMergeRecord.Input(TestPaths.absolute("b.pin"), 4));
        assertEquals(7, new PinMergeRecord(inputs, 7, out, some).totalRows());
        assertEquals(
                "a merge of 7 input rows cannot hold 8",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PinMergeRecord(inputs, 8, out, some))
                        .getMessage());
        assertEquals(
                "a merge has at least one input",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PinMergeRecord(List.of(), 0, out, some))
                        .getMessage());
        assertEquals(
                "a row count cannot be negative: -1",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PinMergeRecord.Input(TestPaths.absolute("a.pin"), -1))
                        .getMessage());
        assertEquals(0, new PinMergeRecord.Input(TestPaths.absolute("a.pin"), 0).rows());
    }
}
