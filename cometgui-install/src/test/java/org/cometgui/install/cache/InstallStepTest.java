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

package org.cometgui.install.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.cometgui.domain.tools.InstallPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The eight steps, pinned so that a ninth cannot arrive unnoticed. */
class InstallStepTest {

    /**
     * The specification's list, hand-typed.
     *
     * <p>Hand-typed on purpose, and this is the whole point of the test. Every other test of the
     * pipeline is driven by {@link InstallStep#values()} so that it keeps covering the pipeline as
     * it grows -- but a list derived from the enumeration cannot notice that the enumeration
     * changed. This one can: <strong>adding a step makes this test fail</strong>, which is what
     * sends whoever added it to the tests that have to cover it.
     */
    private static final List<String> THE_SPECIFICATIONS_EIGHT =
            List.of(
                    "DOWNLOAD_TO_TEMPORARY_FILE",
                    "VERIFY_SHA256",
                    "EXTRACT_WITH_GUARDS",
                    "VERIFY_EXPECTED_LAYOUT",
                    "APPLY_PLATFORM_FIXUPS",
                    "PROBE",
                    "MOVE_ATOMICALLY_INTO_CACHE",
                    "RECORD_INSTALLATION_METADATA");

    @Test
    @DisplayName("the steps are the specification's eight, in the specification's order")
    void stepsAreTheSpecificationsEightInOrder() {
        assertEquals(
                THE_SPECIFICATIONS_EIGHT,
                java.util.Arrays.stream(InstallStep.values()).map(Enum::name).toList(),
                () ->
                        "the install pipeline is the specification's numbered list under"
                                + " \"Installation shall be atomic\". If a step has been added or"
                                + " renamed, update this list AND make sure every test driven by"
                                + " InstallStep.values() still says something about the new step --"
                                + " the interruption matrix in InstallInterruptionTest above all.");
    }

    @Test
    @DisplayName("the completion marker is written last, which is what R-TOOL-04 requires")
    void theMarkerIsTheLastStep() {
        InstallStep[] steps = InstallStep.values();
        assertSame(
                InstallStep.RECORD_INSTALLATION_METADATA,
                steps[steps.length - 1],
                "R-TOOL-04: a tool directory is installed only when a completion marker WRITTEN"
                        + " LAST is present");
        assertSame(
                InstallStep.MOVE_ATOMICALLY_INTO_CACHE,
                steps[steps.length - 2],
                "the marker is written after the move, so a directory in the cache without one is"
                        + " exactly what an interrupted install looks like");
    }

    @Test
    @DisplayName("the probe runs before anything reaches the cache")
    void theProbeRunsBeforeTheMove() {
        assertEquals(
                InstallStep.PROBE.number() + 1,
                InstallStep.MOVE_ATOMICALLY_INTO_CACHE.number(),
                "R-TOOL-06: a tool that fails loadability is never offered, so it must not become a"
                        + " cache entry either");
    }

    @ParameterizedTest
    @EnumSource(InstallStep.class)
    @DisplayName("every step numbers itself from one and carries a phase that is not terminal")
    void everyStepNumbersItselfAndCarriesANonTerminalPhase(InstallStep step) {
        assertEquals(step.ordinal() + 1, step.number(), () -> step + " numbers itself wrongly");
        assertFalse(
                step.phase().isTerminal(),
                () ->
                        step
                                + " reports "
                                + step.phase()
                                + ", and a terminal phase is reported once, at the end, by the"
                                + " installer -- never by a step that is still running");
    }

    @Test
    @DisplayName("the phases are the ones the Tool Manager renders, in the order they occur")
    void phasesAreHandTyped() {
        assertEquals(
                List.of(
                        InstallPhase.DOWNLOADING,
                        InstallPhase.VERIFYING,
                        InstallPhase.EXTRACTING,
                        InstallPhase.VERIFYING,
                        InstallPhase.INSTALLING,
                        InstallPhase.PROBING,
                        InstallPhase.INSTALLING,
                        InstallPhase.INSTALLING),
                java.util.Arrays.stream(InstallStep.values()).map(InstallStep::phase).toList());
    }
}
