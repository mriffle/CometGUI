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

package org.cometgui.tools.pdv;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code spectra_ref} of every PSM row of an mzTab {@link PdvMzTabExporter} wrote, keyed on the
 * row's decoy flag and Percolator {@code PSMId} -- how a PSM selected in the Results section
 * becomes the {@code /select?ref=} PDV is sent.
 *
 * <h2>Why the key has two parts</h2>
 *
 * <p>With Comet's {@code decoy_search = 2} a target and a decoy row can carry one {@code PSMId}
 * (phase 11, unit 5), and the Results section shows the two in different tables. So the key is (the
 * {@value PdvMzTabExporter#DECOY_COLUMN} flag, the {@value PdvMzTabExporter#PSM_ID_COLUMN}), and a
 * key that occurs twice is refused: it would make one selection ambiguous.
 *
 * <h2>What is read</h2>
 *
 * <p>The document's one {@code PSH} line, whose columns are found by name -- {@code spectra_ref},
 * the decoy flag and the {@code PSMId} -- and every {@code PSM} line. The reference is kept exactly
 * as written: PDV looks a selection up by the verbatim {@code spectra_ref} string. Nothing is
 * recomputed. A document that is not of that shape is refused naming the line, never partly
 * indexed.
 */
public final class PdvMzTabRefs {

    private final Map<String, String> targets;

    private final Map<String, String> decoys;

    private PdvMzTabRefs(Map<String, String> targets, Map<String, String> decoys) {
        this.targets = targets;
        this.decoys = decoys;
    }

    /**
     * Reads the references of an mzTab.
     *
     * @param mzTab an mzTab {@link PdvMzTabExporter} wrote
     * @return the references
     * @throws IOException if it cannot be read, or is not of the shape described above: no {@code
     *     PSH} line or more than one, a column missing, a {@code PSM} line before the {@code PSH}
     *     or with a different number of fields, a decoy flag other than {@code 0} or {@code 1}, an
     *     empty or {@code null} reference or {@code PSMId}, or a key that occurs twice
     */
    public static PdvMzTabRefs read(Path mzTab) throws IOException {
        Objects.requireNonNull(mzTab, "mzTab");
        Map<String, String> targets = new HashMap<>();
        Map<String, String> decoys = new HashMap<>();
        int[] columns = null;
        int width = 0;
        long number = 0;
        try (BufferedReader in = Files.newBufferedReader(mzTab, StandardCharsets.UTF_8)) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                number++;
                String[] fields = line.split("\t", -1);
                if (fields[0].equals("PSH")) {
                    if (columns != null) {
                        throw refused(mzTab, number, "a second PSH line");
                    }
                    columns = columns(mzTab, number, List.of(fields));
                    width = fields.length;
                } else if (fields[0].equals("PSM")) {
                    if (columns == null) {
                        throw refused(mzTab, number, "a PSM line before the PSH line");
                    }
                    if (fields.length != width) {
                        throw refused(
                                mzTab,
                                number,
                                "a PSM line of "
                                        + fields.length
                                        + " fields under a PSH line of "
                                        + width);
                    }
                    put(mzTab, number, fields, columns, targets, decoys);
                }
            }
        }
        if (columns == null) {
            throw refused(mzTab, number, "no PSH line");
        }
        return new PdvMzTabRefs(targets, decoys);
    }

    private static int[] columns(Path mzTab, long number, List<String> header) throws IOException {
        int[] found = new int[3];
        List<String> wanted =
                List.of(
                        "spectra_ref",
                        PdvMzTabExporter.DECOY_COLUMN,
                        PdvMzTabExporter.PSM_ID_COLUMN);
        for (int index = 0; index < wanted.size(); index++) {
            found[index] = header.indexOf(wanted.get(index));
            if (found[index] < 0) {
                throw refused(mzTab, number, "a PSH line without the column " + wanted.get(index));
            }
        }
        return found;
    }

    private static void put(
            Path mzTab,
            long number,
            String[] fields,
            int[] columns,
            Map<String, String> targets,
            Map<String, String> decoys)
            throws IOException {
        String ref = fields[columns[0]];
        String flag = fields[columns[1]];
        String psmId = fields[columns[2]];
        if (ref.isEmpty() || ref.equals(PdvMzTabExporter.NULL)) {
            throw refused(mzTab, number, "a PSM line without a spectra_ref");
        }
        if (psmId.isEmpty() || psmId.equals(PdvMzTabExporter.NULL)) {
            throw refused(mzTab, number, "a PSM line without a PSMId");
        }
        Map<String, String> table;
        if ("1".equals(flag)) {
            table = decoys;
        } else if ("0".equals(flag)) {
            table = targets;
        } else {
            throw refused(mzTab, number, "the decoy flag \"" + flag + "\", which is not 0 or 1");
        }
        if (table.putIfAbsent(psmId, ref) != null) {
            throw refused(
                    mzTab,
                    number,
                    "a second "
                            + ("1".equals(flag) ? "decoy" : "target")
                            + " row for the PSM "
                            + psmId);
        }
    }

    private static IOException refused(Path mzTab, long number, String what) {
        return new IOException(
                mzTab
                        + " is not an mzTab whose PSMs can be selected in PDV: line "
                        + number
                        + " holds "
                        + what
                        + ".");
    }

    /**
     * The {@code spectra_ref} of a PSM.
     *
     * @param decoy whether the PSM is a row of Percolator's decoy table
     * @param psmId its Percolator {@code PSMId}
     * @return its reference, verbatim; empty if the mzTab has no such row
     */
    public Optional<String> spectraRef(boolean decoy, String psmId) {
        Objects.requireNonNull(psmId, "psmId");
        return Optional.ofNullable((decoy ? decoys : targets).get(psmId));
    }

    /**
     * How many rows were read.
     *
     * @return target rows plus decoy rows
     */
    public int size() {
        return targets.size() + decoys.size();
    }
}
