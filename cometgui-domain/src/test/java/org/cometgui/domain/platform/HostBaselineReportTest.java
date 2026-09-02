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

package org.cometgui.domain.platform;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link HostBaselineReport} and the blocking flags of {@link HostBaselineOutcome}. */
class HostBaselineReportTest {

    @Test
    @DisplayName("keeps its outcome and its message")
    void keepsItsComponents() {
        HostBaselineReport report =
                new HostBaselineReport(HostBaselineOutcome.SUPPORTED, "Host baseline OK.");

        assertAll(
                () -> assertEquals(HostBaselineOutcome.SUPPORTED, report.outcome()),
                () -> assertEquals("Host baseline OK.", report.message()));
    }

    @ParameterizedTest
    @EnumSource(HostBaselineOutcome.class)
    @DisplayName("blocking() is the outcome's own flag, for every outcome")
    void blockingFollowsTheOutcome(HostBaselineOutcome outcome) {
        assertEquals(outcome.blocking(), new HostBaselineReport(outcome, "a message").blocking());
    }

    @Test
    @DisplayName("exactly the two outcomes that make a run impossible are blocking")
    void onlyImpossibleHostsBlock() {
        assertAll(
                () -> assertEquals(false, HostBaselineOutcome.SUPPORTED.blocking()),
                () -> assertEquals(true, HostBaselineOutcome.NOT_64_BIT.blocking()),
                () -> assertEquals(false, HostBaselineOutcome.ARCHITECTURE_UNDETERMINED.blocking()),
                () -> assertEquals(true, HostBaselineOutcome.GLIBC_TOO_OLD.blocking()),
                () -> assertEquals(false, HostBaselineOutcome.GLIBC_UNDETERMINED.blocking()));
    }

    @Test
    @DisplayName("a null component is rejected by name")
    void rejectsNullComponents() {
        assertAll(
                () ->
                        assertEquals(
                                "outcome",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new HostBaselineReport(null, "a message"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "message",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new HostBaselineReport(
                                                                HostBaselineOutcome.SUPPORTED,
                                                                null))
                                        .getMessage()));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\n"})
    @DisplayName("a blank message is rejected: an outcome with no explanation is not actionable")
    void rejectsABlankMessage(String blank) {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new HostBaselineReport(HostBaselineOutcome.NOT_64_BIT, blank));

        assertEquals(
                "a host baseline report for NOT_64_BIT must carry a message", thrown.getMessage());
    }

    @Test
    @DisplayName("two reports with the same outcome and message are equal")
    void equalComponentsMeanEqualReports() {
        HostBaselineReport first =
                new HostBaselineReport(HostBaselineOutcome.GLIBC_TOO_OLD, "too old");
        HostBaselineReport second =
                new HostBaselineReport(HostBaselineOutcome.GLIBC_TOO_OLD, "too old");

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.hashCode(), second.hashCode()),
                () ->
                        assertEquals(
                                "HostBaselineReport[outcome=GLIBC_TOO_OLD, message=too old]",
                                first.toString()));
    }
}
