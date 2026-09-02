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

package org.cometgui.workflow.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link StepState}.
 *
 * <p>Each predicate is asserted by collecting the states it is true of and comparing that set to
 * one written out here. That is stronger than checking a handful of cases: a predicate that starts
 * answering true for a tenth state, or stops answering true for one it used to, changes the
 * collected set and fails, and the failure message names the difference.
 */
class StepStateTest {

    @Test
    @DisplayName("the nine states are exactly the specification's, in the order it lists them")
    void constantsAreTheSpecificationsNine() {
        assertEquals(
                List.of(
                        StepState.NOT_STARTED,
                        StepState.VALIDATING,
                        StepState.READY,
                        StepState.RUNNING,
                        StepState.SUCCEEDED,
                        StepState.FAILED,
                        StepState.CANCEL_REQUESTED,
                        StepState.CANCELLED,
                        StepState.SKIPPED),
                List.of(StepState.values()));
    }

    @Test
    @DisplayName("isPending is true of exactly NOT_STARTED and READY")
    void isPendingIsTrueOfExactlyTheTwoPendingStates() {
        assertEquals(
                EnumSet.of(StepState.NOT_STARTED, StepState.READY),
                statesWhere(StepState::isPending));
    }

    @Test
    @DisplayName("isActive is true of exactly VALIDATING, RUNNING and CANCEL_REQUESTED")
    void isActiveIsTrueOfExactlyTheThreeInFlightStates() {
        assertEquals(
                EnumSet.of(StepState.VALIDATING, StepState.RUNNING, StepState.CANCEL_REQUESTED),
                statesWhere(StepState::isActive));
    }

    @Test
    @DisplayName("isTerminal is true of exactly SUCCEEDED, FAILED, CANCELLED and SKIPPED")
    void isTerminalIsTrueOfExactlyTheFourFinishedStates() {
        assertEquals(
                EnumSet.of(
                        StepState.SUCCEEDED,
                        StepState.FAILED,
                        StepState.CANCELLED,
                        StepState.SKIPPED),
                statesWhere(StepState::isTerminal));
    }

    @Test
    @DisplayName("isFailureLike is true of exactly FAILED and CANCELLED -- not SKIPPED")
    void isFailureLikeIsTrueOfExactlyFailedAndCancelled() {
        assertEquals(
                EnumSet.of(StepState.FAILED, StepState.CANCELLED),
                statesWhere(StepState::isFailureLike));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(StepState.class)
    @DisplayName("pending, active and terminal partition the nine states: exactly one holds")
    void pendingActiveAndTerminalPartitionTheStates(StepState state) {
        long held =
                Stream.of(state.isPending(), state.isActive(), state.isTerminal())
                        .filter(Boolean::booleanValue)
                        .count();
        assertEquals(
                1L,
                held,
                () ->
                        state
                                + " must be in exactly one group, but pending="
                                + state.isPending()
                                + " active="
                                + state.isActive()
                                + " terminal="
                                + state.isTerminal());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = StepState.class,
            names = {"FAILED", "CANCELLED"})
    @DisplayName("a failure-like state has ended, so it is terminal too")
    void aFailureLikeStateIsTerminal(StepState state) {
        assertTrue(state.isTerminal(), state + " is failure-like but not terminal");
    }

    @Test
    @DisplayName("CANCEL_REQUESTED is active and not failure-like: the stage may still succeed")
    void cancelRequestedIsActiveAndNotYetAFailure() {
        assertEquals(
                List.of(true, false, false, false),
                List.of(
                        StepState.CANCEL_REQUESTED.isActive(),
                        StepState.CANCEL_REQUESTED.isTerminal(),
                        StepState.CANCEL_REQUESTED.isFailureLike(),
                        StepState.CANCEL_REQUESTED.isPending()));
    }

    @Test
    @DisplayName("SKIPPED is terminal but not failure-like: the work was not needed")
    void skippedIsTerminalButNotAFailure() {
        assertEquals(
                List.of(true, false, false, false),
                List.of(
                        StepState.SKIPPED.isTerminal(),
                        StepState.SKIPPED.isFailureLike(),
                        StepState.SKIPPED.isActive(),
                        StepState.SKIPPED.isPending()));
    }

    private static Set<StepState> statesWhere(Predicate<StepState> predicate) {
        return Stream.of(StepState.values())
                .filter(predicate)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(StepState.class)));
    }
}
