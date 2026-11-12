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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.run.RunLayout;

/**
 * Merges the per-file PIN files of a run into the one PIN Percolator reads ({@code R-CMT-06}).
 *
 * <ul>
 *   <li><strong>One header</strong>: the first file's.
 *   <li><strong>Every data row of every input, in input order, byte for byte</strong>: rows are
 *       copied as the text {@link PinReader} read, never split and re-joined, so the extra
 *       tab-separated protein fields of a peptide matching several proteins survive exactly.
 *   <li><strong>The same features in the same order</strong> in every input, compared with the
 *       first file's as each file is reached. A difference fails with both files and the first
 *       differing feature column named, and nothing is written.
 *   <li><strong>Written once</strong>: an existing {@code inputs/pin/merged.pin} is refused and
 *       left as it was. The rows go to a temporary file beside it, which is moved to the final name
 *       only when every input has been read, and deleted if anything fails -- so a failed merge
 *       leaves no merged file and no temporary one. (A process killed mid-merge can leave the
 *       temporary file; it never leaves a partial {@code merged.pin}.)
 *   <li><strong>LF line endings, always.</strong> Comet writes LF; a CRLF input's CRs are
 *       terminators, which {@link PinReader} drops, so the merged file never mixes the two.
 *   <li><strong>Counted after it is written.</strong> When every input has been copied and the
 *       temporary file closed, the file is read back from the disk through {@link PinReader} and
 *       its data rows counted. That count -- what was written -- must equal the number of rows read
 *       from the inputs; if it does not, the merge fails with a {@link PinMergeRowCountException}
 *       stating both counts, and no merged file is left. The record's {@link
 *       PinMergeRecord#totalRows()} is the count of the written file, not of what was read.
 *   <li><strong>Hashed once, by the one hash service</strong>, after the file is closed and moved,
 *       so the record's checksums are those of the bytes Percolator will read.
 * </ul>
 *
 * <p>Every row is checked as it is copied, by the same rules {@link CometPinValidator} applies, so
 * a merge cannot produce a file validation would refuse.
 */
public final class PinMerger {

    /**
     * How the merge opens the file it writes its rows to.
     *
     * <p>In the product this is the file system's own stream, {@link #TO_THE_FILE_SYSTEM}. It is a
     * parameter so that a test can put a stream that loses bytes between the merge and the disk,
     * and show that the merge notices: nothing else is meant to implement it.
     */
    @FunctionalInterface
    public interface Output {

        /**
         * Opens a new, empty file for writing.
         *
         * @param file the file, which exists and is empty
         * @return the stream the merged rows are written to; the merge closes it
         * @throws IOException if the file cannot be opened
         */
        OutputStream open(Path file) throws IOException;
    }

    /** The file system's own stream: what every merge outside a test writes through. */
    public static final Output TO_THE_FILE_SYSTEM = file -> Files.newOutputStream(file);

    private PinMerger() {}

    /**
     * Merges PIN files into the run's {@link RunLayout#mergedPinFile()}.
     *
     * @param run the run
     * @param pins the per-file PIN files, in input order
     * @param hashes the hash service
     * @return the merge record
     * @throws FileAlreadyExistsException if the merged file already exists
     * @throws CometOutputException if an input is missing, malformed or truncated, or the inputs'
     *     feature columns differ, naming the file or files
     * @throws PinMergeRowCountException if the file that was written does not hold exactly the rows
     *     that were read, stating both counts; no merged file is left
     * @throws IOException if the merged file cannot be written or hashed
     * @throws IllegalArgumentException if there is no input, or one file is given twice
     */
    public static PinMergeRecord merge(RunLayout run, List<Path> pins, HashService hashes)
            throws IOException {
        return merge(run, pins, hashes, TO_THE_FILE_SYSTEM);
    }

    /**
     * Merges PIN files as {@link #merge(RunLayout, List, HashService)} does, writing the rows
     * through {@code output}.
     *
     * @param run the run
     * @param pins the per-file PIN files, in input order
     * @param hashes the hash service
     * @param output how the file the rows are written to is opened
     * @return the merge record
     * @throws FileAlreadyExistsException if the merged file already exists
     * @throws CometOutputException as {@link #merge(RunLayout, List, HashService)}
     * @throws PinMergeRowCountException as {@link #merge(RunLayout, List, HashService)}
     * @throws IOException if the merged file cannot be written or hashed
     * @throws IllegalArgumentException if there is no input, or one file is given twice
     */
    public static PinMergeRecord merge(
            RunLayout run, List<Path> pins, HashService hashes, Output output) throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(hashes, "hashes");
        Objects.requireNonNull(output, "output");
        List<Path> inputs = List.copyOf(pins);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("a PIN merge needs at least one PIN file");
        }
        Set<Path> distinct = new HashSet<>();
        for (Path input : inputs) {
            if (!distinct.add(input.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException(
                        "the PIN file "
                                + input
                                + " is given twice; its rows would be merged twice");
            }
        }
        Path target = run.mergedPinFile();
        refuseExisting(target);
        Path temporary = Files.createTempFile(run.pinInputsDirectory(), "merged.pin.", ".part");
        List<PinMergeRecord.Input> merged = new ArrayList<>(inputs.size());
        long read = 0;
        long written;
        try {
            try (OutputStream out = new BufferedOutputStream(output.open(temporary), 65536)) {
                Path first = inputs.get(0);
                PinHeader reference = null;
                for (Path input : inputs) {
                    try (PinReader reader = PinReader.open(input)) {
                        if (reference == null) {
                            reference = reader.header();
                            writeLine(out, String.join("\t", reference.columns()));
                        } else {
                            requireSameFeatures(first, reference, input, reader.header());
                        }
                        String row = reader.nextRow();
                        while (row != null) {
                            writeLine(out, row);
                            row = reader.nextRow();
                        }
                        merged.add(new PinMergeRecord.Input(input, reader.rows()));
                        read += reader.rows();
                    }
                }
            }
            written = rowsOf(temporary);
            if (written != read) {
                throw new PinMergeRowCountException(target, inputs.size(), read, written);
            }
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
        FileHashes checksums = hashes.hash(target);
        return new PinMergeRecord(merged, written, target, checksums);
    }

    /**
     * The data rows of a PIN file, counted by reading it from the disk: what a merge wrote, as
     * distinct from what it read.
     *
     * @param pin the file, closed
     * @return its data rows
     * @throws CometOutputException if the file is not a well-formed PIN, naming the file and line
     */
    private static long rowsOf(Path pin) throws IOException {
        try (PinReader reader = PinReader.open(pin)) {
            String row = reader.nextRow();
            while (row != null) {
                row = reader.nextRow();
            }
            return reader.rows();
        }
    }

    private static void refuseExisting(Path target) throws FileAlreadyExistsException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(
                    target.toString(),
                    null,
                    "the merged PIN file already exists and is written once; it was left as it"
                            + " was");
        }
    }

    /**
     * Requires a file's feature columns to equal the reference file's, in the same order.
     *
     * @throws CometOutputException naming both files and the first differing column
     */
    static void requireSameFeatures(
            Path first, PinHeader reference, Path other, PinHeader candidate)
            throws CometOutputException {
        List<String> expected = reference.featureColumns();
        List<String> actual = candidate.featureColumns();
        if (expected.equals(actual)) {
            return;
        }
        int index = 0;
        while (index < expected.size()
                && index < actual.size()
                && expected.get(index).equals(actual.get(index))) {
            index++;
        }
        throw new CometOutputException(
                other,
                "cannot merge the PIN files "
                        + first
                        + " and "
                        + other
                        + ": their feature columns differ, first at feature column "
                        + (index + 1)
                        + " ("
                        + nameAt(expected, index)
                        + " in the first, "
                        + nameAt(actual, index)
                        + " in the second; "
                        + expected.size()
                        + " and "
                        + actual.size()
                        + " feature columns). Percolator needs every file to have the same"
                        + " features in the same order",
                null);
    }

    private static String nameAt(List<String> names, int index) {
        return index < names.size() ? "\"" + names.get(index) + "\"" : "none";
    }

    private static void writeLine(OutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.ISO_8859_1));
        out.write('\n');
    }
}
