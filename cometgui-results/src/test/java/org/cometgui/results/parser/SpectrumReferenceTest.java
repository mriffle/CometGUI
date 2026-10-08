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

package org.cometgui.results.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A {@code PSMId} in Comet's {@code SpecId} shape, read from the right. Every expected value is
 * typed by hand, including the large fixture's four awkward bases (its {@code CONSTRUCTED.txt}).
 */
class SpectrumReferenceTest {

    @ParameterizedTest
    @CsvSource(
            value = {
                // The large fixture's four bases (CONSTRUCTED.txt), at real-looking numbers.
                "'/fixture/runs/run-01/outputs/comet/sample_A_1234_2_1',"
                        + " '/fixture/runs/run-01/outputs/comet/sample_A', 1234, 2, 1",
                "'/fixture/runs/run-01/outputs/comet/sample_B_7_3_1',"
                        + " '/fixture/runs/run-01/outputs/comet/sample_B', 7, 3, 1",
                "'/fixture/runs/run-01/outputs/comet/2026_10_08_sample_C_2_1_98765_4_2',"
                        + " '/fixture/runs/run-01/outputs/comet/2026_10_08_sample_C_2_1',"
                        + " 98765, 4, 2",
                "'/fixture/runs/run-01/outputs/comet/sample D_15_1_1',"
                        + " '/fixture/runs/run-01/outputs/comet/sample D', 15, 1, 1",
                // Phase 00's K562 output: a bare file name with digits and underscores.
                "'20100614_Velos1_TaGe_SA_K562_3_10234_2_1',"
                        + " '20100614_Velos1_TaGe_SA_K562_3', 10234, 2, 1",
                // Windows paths, leading zeros, the smallest base, the largest numbers.
                "'C:\\runs\\a b\\x_y_00042_02_10', 'C:\\runs\\a b\\x_y', 42, 2, 10",
                "'a_0_0_0', 'a', 0, 0, 0",
                "'__1_2_3', '_', 1, 2, 3",
                "'b_9223372036854775807_2147483647_2147483647', 'b', 9223372036854775807,"
                        + " 2147483647, 2147483647"
            },
            quoteCharacter = '\'')
    @DisplayName("read from the right: the base keeps its own underscores, digits and spaces")
    void specIds(String psmId, String base, long scan, int charge, int rank) {
        assertEquals(
                Optional.of(new SpectrumReference(base, scan, charge, rank)),
                SpectrumReference.of(psmId));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "psm84",
                "",
                "_",
                "a_1_2",
                "1_2_3",
                "_1_2_3",
                "a__2_3",
                "a_1__3",
                "a_1_2_",
                "a_x_2_1",
                "odd_x_2_1",
                "a_1_+2_1",
                "a_-1_2_1",
                "a_1_2_1 ",
                "a_1.0_2_1",
                "a_\u0661_2_1",
                "a_9223372036854775808_2_1",
                "a_1_2147483648_1",
                "a_1_2_2147483648",
                "a_1_2_99999999999999999999"
            })
    @DisplayName("not in SpecId shape: no reference, never a guess")
    void notSpecIds(String psmId) {
        assertEquals(Optional.empty(), SpectrumReference.of(psmId));
    }

    @Test
    @DisplayName("a reference needs a base and non-negative numbers; the row reads its own")
    void construction() {
        assertThrows(NullPointerException.class, () -> new SpectrumReference(null, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new SpectrumReference("", 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new SpectrumReference("a", -1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new SpectrumReference("a", 1, -2, 1));
        IllegalArgumentException rank =
                assertThrows(
                        IllegalArgumentException.class, () -> new SpectrumReference("a", 1, 2, -1));
        assertEquals(
                "a spectrum reference needs a base and non-negative numbers: base 'a', scan 1,"
                        + " charge 2, rank -1",
                rank.getMessage());
        assertThrows(NullPointerException.class, () -> SpectrumReference.of(null));
        ResultRow row =
                new ResultRow(
                        5, "s D_15_1_2", "1", 1, QValue.of("0"), "0", 0, "K.A.R", List.of("p"));
        assertEquals(Optional.of(new SpectrumReference("s D", 15, 1, 2)), row.spectrumReference());
    }
}
