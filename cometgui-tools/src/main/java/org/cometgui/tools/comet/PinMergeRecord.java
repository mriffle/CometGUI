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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;

/**
 * What a PIN merge did, for the provenance record ({@code R-CMT-06}: "recording the merge (inputs,
 * row counts, output hash)").
 *
 * @param inputs each merged file with its data-row count, in merge order
 * @param totalRows the merged file's data rows: the sum of the inputs'
 * @param output the merged file
 * @param hashes the merged file's MD5 and SHA-256, computed after it was closed and in place
 */
public record PinMergeRecord(List<Input> inputs, long totalRows, Path output, FileHashes hashes) {

    /**
     * One merged file.
     *
     * @param file the PIN file
     * @param rows its data rows, every one of which is in the merged file
     */
    public record Input(Path file, long rows) {

        /**
         * Validates the entry.
         *
         * @throws NullPointerException if {@code file} is {@code null}
         * @throws IllegalArgumentException if {@code rows} is negative
         */
        public Input {
            Objects.requireNonNull(file, "file");
            if (rows < 0) {
                throw new IllegalArgumentException("a row count cannot be negative: " + rows);
            }
        }
    }

    /**
     * Validates the record: at least one input, and a total that is the inputs' sum.
     *
     * @throws NullPointerException if a component or an input is {@code null}
     * @throws IllegalArgumentException if there is no input or the total is not the sum
     */
    public PinMergeRecord {
        inputs = List.copyOf(inputs);
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(hashes, "hashes");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("a merge has at least one input");
        }
        long sum = 0;
        for (Input input : inputs) {
            sum += input.rows();
        }
        if (sum != totalRows) {
            throw new IllegalArgumentException(
                    "a merge of " + sum + " input rows cannot hold " + totalRows);
        }
    }

    /**
     * Each merged file with its data-row count.
     *
     * @return the inputs, in merge order, immutable
     */
    @Override
    public List<Input> inputs() {
        return List.copyOf(inputs);
    }
}
