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

import java.util.List;

/**
 * Hand-typed PIN text in the shape Comet 2026.03.0 writes, for the tests that grade the PIN rules
 * on constructed files. The real files are graded in {@link CometAdapterRealBinaryTest}.
 */
final class PinText {

    /** The 28 header columns Comet 2026.03.0 writes, typed from a real file. */
    static final List<String> COLUMNS =
            List.of(
                    "SpecId",
                    "Label",
                    "ScanNr",
                    "ExpMass",
                    "CalcMass",
                    "lnrSp",
                    "deltLCn",
                    "deltCn",
                    "lnExpect",
                    "Xcorr",
                    "Sp",
                    "IonFrac",
                    "Mass",
                    "PepLen",
                    "Charge1",
                    "Charge2",
                    "Charge3",
                    "Charge4",
                    "Charge5",
                    "Charge6",
                    "enzN",
                    "enzC",
                    "enzInt",
                    "lnNumSP",
                    "dM",
                    "absdM",
                    "Peptide",
                    "Proteins");

    /** Its 23 feature columns. */
    static final List<String> FEATURES = COLUMNS.subList(3, 26);

    /** The header line, without a terminator. */
    static final String HEADER = String.join("\t", COLUMNS);

    /** The 23 feature values of a real row (k562_3, scan 11188, rank 1). */
    static final String FEATURE_VALUES =
            "1230.569506\t1228.585130\t0.000000\t1.000000\t0.147510\t3.788944\t0.522000"
                    + "\t36.660000\t0.2000\t1230.569506\t11\t0\t1\t0\t0\t0\t0\t1\t1\t1\t3.828641"
                    + "\t0.001615\t0.001615";

    private PinText() {}

    /**
     * One data row with the real feature values.
     *
     * @param specId the SpecId
     * @param label 1 or -1, or anything a test wants to try
     * @param scan the ScanNr
     * @param peptide the peptide
     * @param proteins one or more proteins; each after the first is one more field
     * @return the row, without a terminator
     */
    static String row(
            String specId, String label, String scan, String peptide, String... proteins) {
        return specId
                + "\t"
                + label
                + "\t"
                + scan
                + "\t"
                + FEATURE_VALUES
                + "\t"
                + peptide
                + "\t"
                + String.join("\t", proteins);
    }

    /**
     * A target row.
     *
     * @param scan the scan number
     * @return the row
     */
    static String target(int scan) {
        return row(
                "run_" + scan + "_2_1",
                "1",
                Integer.toString(scan),
                "K.AHNSVNK.M",
                "sp|Q96T58|MINT_HUMAN");
    }

    /**
     * A decoy row naming two proteins: one field more than the header.
     *
     * @param scan the scan number
     * @return the row
     */
    static String decoy(int scan) {
        return row(
                "run_" + scan + "_3_1",
                "-1",
                Integer.toString(scan),
                "K.EWFAKGCENCEFHK.S",
                "DECOY_sp|Q06730|ZN33A_HUMAN",
                "DECOY_sp|Q06732|ZN33B_HUMAN");
    }

    /**
     * Lines joined with LF, each terminated.
     *
     * @param lines the lines
     * @return the file's text
     */
    static String lines(String... lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            text.append(line).append('\n');
        }
        return text.toString();
    }
}
