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

package org.cometgui.domain.tools;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link ProbeFailureKind}.
 *
 * <p>The stage of every kind is pinned by a hand-typed table, because the whole value of the field
 * is that a classifier cannot report a loader failure as a capability verdict. In particular {@code
 * CAPABILITY_ABSENT} must be the only kind that is a statement about what a build can do: the
 * 8-plus-8 false negative recorded in phase 05's work log is an {@code EXECUTION_FAILED}, and
 * reporting it as an absent capability is the defect.
 */
class ProbeFailureKindTest {

    @Test
    @DisplayName("the ten failure kinds are the ten that exist")
    void theTenArePinned() {
        List<String> names = new ArrayList<>();
        for (ProbeFailureKind kind : ProbeFailureKind.values()) {
            names.add(kind.name());
        }

        assertEquals(
                List.of(
                        "MISSING_SHARED_OBJECT",
                        "MISSING_SYMBOL_VERSION",
                        "WRONG_ARCHITECTURE",
                        "MACOS_QUARANTINE",
                        "MISSING_WINDOWS_RUNTIME_DLL",
                        "NOT_EXECUTABLE",
                        "TIMED_OUT",
                        "UNPARSEABLE_VERSION",
                        "CAPABILITY_ABSENT",
                        "EXECUTION_FAILED"),
                names);
    }

    @ParameterizedTest(name = "[{index}] {0} occurs at {1}")
    @CsvSource({
        "MISSING_SHARED_OBJECT, LOADABILITY",
        "MISSING_SYMBOL_VERSION, LOADABILITY",
        "WRONG_ARCHITECTURE, LOADABILITY",
        "MACOS_QUARANTINE, LOADABILITY",
        "MISSING_WINDOWS_RUNTIME_DLL, LOADABILITY",
        "NOT_EXECUTABLE, LOADABILITY",
        "TIMED_OUT, LOADABILITY",
        "EXECUTION_FAILED, LOADABILITY",
        "UNPARSEABLE_VERSION, IDENTITY",
        "CAPABILITY_ABSENT, CAPABILITY"
    })
    @DisplayName("every kind's stage is what the hand-typed table says")
    void stagesArePinned(String kind, String stage) {
        assertEquals(ProbeStage.valueOf(stage), ProbeFailureKind.valueOf(kind).stage());
    }

    @Test
    @DisplayName("the five loader failures the specification names are loadability failures")
    void theFiveLoaderFailuresAreLoadabilityFailures() {
        assertAll(
                () -> assertTrue(ProbeFailureKind.MISSING_SHARED_OBJECT.isLoadabilityFailure()),
                () -> assertTrue(ProbeFailureKind.MISSING_SYMBOL_VERSION.isLoadabilityFailure()),
                () -> assertTrue(ProbeFailureKind.WRONG_ARCHITECTURE.isLoadabilityFailure()),
                () -> assertTrue(ProbeFailureKind.MACOS_QUARANTINE.isLoadabilityFailure()),
                () ->
                        assertTrue(
                                ProbeFailureKind.MISSING_WINDOWS_RUNTIME_DLL
                                        .isLoadabilityFailure()));
    }

    @Test
    @DisplayName("CAPABILITY_ABSENT is the only kind that is a capability verdict")
    void onlyOneKindIsACapabilityVerdict() {
        List<String> capabilityVerdicts = new ArrayList<>();
        for (ProbeFailureKind kind : ProbeFailureKind.values()) {
            if (kind.stage() == ProbeStage.CAPABILITY) {
                capabilityVerdicts.add(kind.name());
            }
        }

        assertEquals(List.of("CAPABILITY_ABSENT"), capabilityVerdicts);
    }

    @Test
    @DisplayName("an unexplained failure and a timeout are not capability verdicts")
    void theAmbiguousKindsFallToTheEarliestStage() {
        assertAll(
                () -> assertTrue(ProbeFailureKind.EXECUTION_FAILED.isLoadabilityFailure()),
                () -> assertTrue(ProbeFailureKind.TIMED_OUT.isLoadabilityFailure()),
                () -> assertFalse(ProbeFailureKind.CAPABILITY_ABSENT.isLoadabilityFailure()),
                () -> assertFalse(ProbeFailureKind.UNPARSEABLE_VERSION.isLoadabilityFailure()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(ProbeFailureKind.class)
    @DisplayName("isLoadabilityFailure agrees with the stage for every kind")
    void theConvenienceAgreesWithTheField(ProbeFailureKind kind) {
        assertEquals(kind.stage() == ProbeStage.LOADABILITY, kind.isLoadabilityFailure());
    }
}
