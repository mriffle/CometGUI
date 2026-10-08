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

package org.cometgui.tools.percolator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The option table: each spelling hand-typed from the runs of the real binaries on 2026-10-07, and
 * each mapped to the one capability whose probe run proves it.
 */
class PercolatorOptionTest {

    @ParameterizedTest(name = "[{index}] {0} = {1} -> {2}")
    @CsvSource({
        "XML_OUTPUT, -X, XML_OUTPUT",
        "XML_DECOY_OUTPUT, -Z, XML_DECOY_OUTPUT",
        "RESULTS_PSMS, --results-psms, PSM_TSV_OUTPUT",
        "RESULTS_PEPTIDES, --results-peptides, PEPTIDE_TSV_OUTPUT",
        "DECOY_RESULTS_PSMS, --decoy-results-psms, DECOY_OUTPUT",
        "DECOY_RESULTS_PEPTIDES, --decoy-results-peptides, DECOY_OUTPUT",
        "WEIGHTS, --weights, WEIGHTS_OUTPUT",
        "SEED, --seed, SEED_OPTION",
        "NUM_THREADS, --num-threads, THREAD_OPTION",
        "TEST_FDR, --testFDR, TEST_FDR_OPTION",
        "TRAIN_FDR, --trainFDR, TRAIN_FDR_OPTION",
        "MAX_ITERATIONS, --maxiter, MAX_ITERATIONS_OPTION",
        "NO_ANALYTICS, --no-analytics, NO_ANALYTICS_OPTION"
    })
    @DisplayName("every option's spelling and the capability that licenses it")
    void theTable(String constant, String spelling, String capability) {
        PercolatorOption option = PercolatorOption.valueOf(constant);

        assertAll(
                () -> assertEquals(spelling, option.spelling()),
                () -> assertEquals(ToolCapability.valueOf(capability), option.capability()));
    }

    @Test
    @DisplayName("thirteen options, and every Percolator capability is licensed by at least one")
    void everyCapabilityHasAnOption() {
        Set<ToolCapability> licensed = EnumSet.noneOf(ToolCapability.class);
        for (PercolatorOption option : PercolatorOption.values()) {
            licensed.add(option.capability());
        }

        assertAll(
                () -> assertEquals(13, PercolatorOption.values().length),
                () ->
                        assertEquals(
                                ToolCapability.declarableFor(ToolName.PERCOLATOR),
                                licensed,
                                "a capability no option needs would be probed for nothing; an"
                                        + " option with no capability could never be passed"),
                () ->
                        assertTrue(
                                Arrays.stream(PercolatorOption.values())
                                        .allMatch(
                                                option ->
                                                        option.capability()
                                                                .belongsTo(ToolName.PERCOLATOR))));
    }

    @Test
    @DisplayName("DECOY_OUTPUT is the only capability two options share")
    void onlyDecoyOutputIsShared() {
        List<PercolatorOption> decoyOptions =
                Arrays.stream(PercolatorOption.values())
                        .filter(option -> option.capability() == ToolCapability.DECOY_OUTPUT)
                        .toList();

        assertEquals(
                List.of(
                        PercolatorOption.DECOY_RESULTS_PSMS,
                        PercolatorOption.DECOY_RESULTS_PEPTIDES),
                decoyOptions);
    }
}
