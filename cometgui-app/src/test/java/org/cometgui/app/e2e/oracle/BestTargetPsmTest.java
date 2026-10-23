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

package org.cometgui.app.e2e.oracle;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The step-25 PSM chooser against a hand-written Percolator table, the answer worked out by hand:
 * the highest score overall is modified (a), the next is above the cutoff (b), the next has an
 * unusable q-value (c), so the choice is d -- whose score ties with e's and comes first -- and the
 * eligible rows are d, e and f.
 */
class BestTargetPsmTest {

    private static final String TABLE =
            "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n"
                    + "a\t9.0\t0.001\t0.1\tK.AM[15.9949]B.C\tP1\n"
                    + "b\t8.0\t0.0100001\t0.1\tK.BCD.E\tP2\n"
                    + "c\t7.5\tNaN\t0.1\tK.CDE.F\tP3\n"
                    + "d\t7.0\t0.01\t0.1\tK.DEF.G\tP4\tP5\n"
                    + "e\t7.00\t1e-3\t0.1\tR.EFG.H\tP6\n"
                    + "f\t-1.5\t0.002\t0.1\t-.FGH.-\tP7\n"
                    + "\n";

    @TempDir private Path directory;

    private Path table(String text) throws IOException {
        Path file = directory.resolve("psms.tsv");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("at 0.01: d, the first of the two best unmodified eligible rows; 3 eligible")
    void chooses() throws IOException {
        BestTargetPsm best = BestTargetPsm.of(table(TABLE), "0.01");
        assertAll(
                () -> assertEquals("d", best.psmId()),
                () -> assertEquals("7.0", best.score()),
                () -> assertEquals("0.01", best.qValue(), "a q-value equal to the cutoff passes"),
                () -> assertEquals("K.DEF.G", best.peptide()),
                () -> assertEquals("DEF", best.sequence()),
                () -> assertEquals(3, best.candidates()));
    }

    @Test
    @DisplayName("at 0.002 e and f are eligible and e scores higher; at 0.0001 none is")
    void cutoffs() throws IOException {
        Path file = table(TABLE);
        BestTargetPsm best = BestTargetPsm.of(file, "0.002");
        assertAll(
                () -> assertEquals("e", best.psmId(), "e: 1e-3 and 0.002 pass, e scores higher"),
                () -> assertEquals(2, best.candidates()));
        assertThrows(AssertionError.class, () -> BestTargetPsm.of(file, "0.0001"));
    }

    @Test
    @DisplayName("a table without a needed column is refused, naming it")
    void columns() throws IOException {
        Path file = table("PSMId\tscore\tq-value\tproteinIds\na\t1\t0.001\tP1\n");
        AssertionError refused =
                assertThrows(AssertionError.class, () -> BestTargetPsm.of(file, "0.01"));
        assertEquals(
                file + " has no peptide column: [PSMId, score, q-value, proteinIds]",
                refused.getMessage());
    }
}
