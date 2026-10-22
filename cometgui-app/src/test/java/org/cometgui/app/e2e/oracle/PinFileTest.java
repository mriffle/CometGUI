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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The PIN oracle against hand-written files whose counts were worked out by hand: two sources, one
 * of whose {@code -N} base names contains underscores, targets and decoys, and the repeated header
 * a concatenating merge would leave behind.
 */
class PinFileTest {

    private static final String HEADER =
            "SpecId\tLabel\tScanNr\tExpMass\tCalcMass\tlnrSp\tPeptide\tProteins";

    /** Three rows of {@code K562_rep_3}: two targets, one decoy. */
    private static final String SOURCE_A =
            HEADER
                    + "\n"
                    + "K562_rep_3_11188_2_1\t1\t11188\t1230.56\t1230.57\t0.0\tR.LEK.A\tsp|P1|A\n"
                    + "K562_rep_3_11188_2_2\t-1\t11188\t1230.56\t1230.56\t1.6\tK.LE.A\tdecoy_P2\n"
                    + "K562_rep_3_204_3_1\t1\t204\t900.1\t900.2\t0.0\tK.AB.C\tsp|P3|C\n";

    /** Two rows of {@code run-B}: one target, one decoy. */
    private static final String SOURCE_B =
            HEADER
                    + "\n"
                    + "run-B_7_2_1\t1\t7\t500.0\t500.0\t0.0\tK.X.Y\tsp|P4|D\n"
                    + "run-B_8_2_1\t-1\t8\t501.0\t501.0\t0.0\tK.Y.X\tdecoy_P5\n";

    @Test
    @DisplayName("a source PIN: one header, its rows, all from one base name with underscores")
    void aSourcePin() {
        PinFile pin = PinFile.parse(SOURCE_A, "a.pin");
        assertAll(
                () -> assertEquals(List.of(HEADER.split("\t")), pin.header()),
                () -> assertEquals(1, pin.headerLines()),
                () -> assertEquals(3, pin.rows()),
                () -> assertEquals(Map.of("K562_rep_3", 3L), pin.rowsBySource()),
                () -> assertEquals(2, pin.targets()),
                () -> assertEquals(1, pin.decoys()),
                () -> assertEquals(0, pin.defaultDirectionLines()),
                () -> assertEquals(List.of(), pin.malformed()));
    }

    @Test
    @DisplayName("a merged PIN: one header and 3 + 2 = 5 rows, by source, in first-seen order")
    void aMergedPin() {
        String merged = SOURCE_A + SOURCE_B.substring(HEADER.length() + 1);
        PinFile pin = PinFile.parse(merged, "merged.pin");
        Map<String, Long> bySource = new LinkedHashMap<>();
        bySource.put("K562_rep_3", 3L);
        bySource.put("run-B", 2L);
        assertAll(
                () -> assertEquals(1, pin.headerLines()),
                () -> assertEquals(5, pin.rows()),
                () ->
                        assertEquals(
                                List.copyOf(bySource.entrySet()),
                                List.copyOf(pin.rowsBySource().entrySet())),
                () -> assertEquals(3, pin.targets()),
                () -> assertEquals(2, pin.decoys()));
    }

    @Test
    @DisplayName(
            "a concatenation that kept both headers is seen to have two, and they are not rows")
    void aRepeatedHeaderIsCounted() {
        PinFile pin = PinFile.parse(SOURCE_A + SOURCE_B, "cat.pin");
        assertEquals(2, pin.headerLines());
        assertEquals(5, pin.rows());
    }

    @Test
    @DisplayName(
            "a DefaultDirection line and blank lines are not rows; a SpecId without four fields"
                    + " and a"
                    + " short row are reported as malformed")
    void oddLines() {
        String text =
                HEADER
                        + "\n"
                        + "DefaultDirection\t-\t-\t-\t-\t1\t-\t-\n"
                        + "\n"
                        + "bad_1\t1\t1\t1\t1\t1\tK.A.B\tP\n"
                        + "x_1_2_3\t-1\t1\n";
        PinFile pin = PinFile.parse(text, "odd.pin");
        assertAll(
                () -> assertEquals(1, pin.defaultDirectionLines()),
                () -> assertEquals(2, pin.rows()),
                () -> assertEquals(Map.of("x", 1L), pin.rowsBySource()),
                () ->
                        assertEquals(
                                List.of(
                                        "4: SpecId bad_1 is not base_scan_charge_rank",
                                        "5: 3 fields, not 8"),
                                pin.malformed()));
    }

    @Test
    @DisplayName("a file that does not begin with a PIN header is refused, naming it")
    void notAPin() {
        AssertionError failed =
                assertThrows(AssertionError.class, () -> PinFile.parse("PSMId\tscore\n", "x.tsv"));
        assertEquals(
                "x.tsv does not begin with a PIN header (SpecId, Label, ...): PSMId\tscore",
                failed.getMessage());
        assertThrows(AssertionError.class, () -> PinFile.parse("", "empty.pin"));
    }
}
