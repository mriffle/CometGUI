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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The q-value counter against a hand-written Percolator table, every count worked out by hand and
 * typed here: rows exactly at the cutoff written three ways, just above and just below it, an
 * exponent form far below it, and four kinds of unknown. At 0.01 the inclusive rule passes five
 * rows and a strict one would pass two, so the counts tell {@code <=} from {@code <}.
 */
class QValueCountsTest {

    private static final String HEADER =
            "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n";

    /** Ten rows; the second protein column of row e is the multi-protein form Percolator writes. */
    private static final String TABLE =
            HEADER
                    + "a\t1.0\t0.01\t0.1\tK.A.B\tP1\n"
                    + "b\t0.9\t0.010\t0.1\tK.B.C\tP2\n"
                    + "c\t0.8\t1e-2\t0.1\tK.C.D\tP3\n"
                    + "d\t0.7\t0.0100001\t0.1\tK.D.E\tP4\n"
                    + "e\t0.6\t0.0099999\t0.1\tK.E.F\tP5\tP6\n"
                    + "f\t0.5\t9.27998e-07\t0.1\tK.F.G\tP7\n"
                    + "g\t0.4\tNaN\t0.1\tK.G.H\tP8\n"
                    + "h\t0.3\t\t0.1\tK.H.I\tP9\n"
                    + "i\t0.2\t1.5\t0.1\tK.I.J\tP10\n"
                    + "j\t0.1\t0,01\t0.1\tK.J.K\tP11\n";

    @TempDir private Path directory;

    private Path table() throws IOException {
        Path file = directory.resolve("percolator.target.psms.txt");
        Files.writeString(file, TABLE, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName(
            "at 0.01: 10 rows, 5 passing (a, b, c at exactly 0.01; e; f), 1 failing (d), 4 unknown")
    void atTheDefaultCutoff() throws IOException {
        QValueCounts counts = QValueCounts.of(table(), "0.01");
        assertAll(
                () -> assertEquals(10, counts.total()),
                () -> assertEquals(5, counts.passing(), "a strict < would give 2"),
                () -> assertEquals(1, counts.failing()),
                () -> assertEquals(4, counts.unknown()),
                () -> assertEquals(List.of("a", "b", "c", "e", "f"), counts.passingIds()));
    }

    @Test
    @DisplayName("at 0.0099999: the row at exactly the cutoff passes, and the three at 0.01 fail")
    void atTheCutoffOfRowE() throws IOException {
        QValueCounts counts = QValueCounts.of(table(), "0.0099999");
        assertEquals(new QValueCounts(10, 2, 4, 4, List.of("e", "f")), counts);
    }

    @Test
    @DisplayName("at 0.0000009: below every row but f's 9.27998e-07, so f alone fails too")
    void belowTheExponentForm() throws IOException {
        assertEquals(
                new QValueCounts(10, 0, 6, 4, List.of()), QValueCounts.of(table(), "0.0000009"));
        assertEquals(
                new QValueCounts(10, 1, 5, 4, List.of("f")),
                QValueCounts.of(table(), "0.000000927998"));
    }

    @Test
    @DisplayName("at 1: every known row passes, and the unknown ones still do not")
    void atOne() throws IOException {
        assertEquals(
                new QValueCounts(10, 6, 0, 4, List.of("a", "b", "c", "d", "e", "f")),
                QValueCounts.of(table(), "1"));
    }
}
