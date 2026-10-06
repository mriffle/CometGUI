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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * the feature-column check naming both files, write-once, LF output, the record's hash. The real
 * per-file PINs are merged in {@link CometAdapterRealBinaryTest}.
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
