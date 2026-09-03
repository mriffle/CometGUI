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

package org.cometgui.tools.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What one probe run produced, and the three questions a caller may ask of it. */
class ToolRunOutcomeTest {

    @Test
    @DisplayName(
            "standard error comes first, because that is where every banner in this project is")
    void errorFirst() {
        ToolRunOutcome outcome =
                new ToolRunOutcome(
                        OptionalInt.of(0),
                        List.of("out one", "out two"),
                        List.of("err one", "err two"));

        assertEquals(List.of("err one", "err two", "out one", "out two"), outcome.errorFirst());
    }

    /*
     * The expected value is hand-typed rather than built from System.lineSeparator().  Written
     * the other way -- "first" + System.lineSeparator() + "second" -- it computed the expectation
     * from the same ambient default as the code under test, so it passed on every platform
     * whatever separator joinedOutput() chose, and the choice was unobservable.  That is the same
     * shape as an unpinned locale: a protection nothing can see is indistinguishable from an
     * absent one.
     */
    @Test
    @DisplayName("the joined output keeps that order, every line, and a fixed newline")
    void joinedOutput() {
        ToolRunOutcome outcome =
                new ToolRunOutcome(OptionalInt.of(1), List.of("second"), List.of("first"));

        assertAll(
                () -> assertEquals("first\nsecond", outcome.joinedOutput()),
                () ->
                        assertEquals(
                                -1,
                                outcome.joinedOutput().indexOf('\r'),
                                "a carriage return here would make every probe's refusal message,"
                                        + " and every test that pins one, differ on Windows"));
    }

    @ParameterizedTest(name = "[{index}] exit {0} -> timedOut={1} exitedZero={2}")
    @CsvSource({"0, false, true", "1, false, false", "127, false, false", "-1, false, false"})
    @DisplayName("a finished run has an exit code, and only zero is zero")
    void finishedRuns(int exitCode, boolean timedOut, boolean exitedZero) {
        ToolRunOutcome outcome = new ToolRunOutcome(OptionalInt.of(exitCode), List.of(), List.of());

        assertAll(
                () -> assertEquals(timedOut, outcome.timedOut()),
                () -> assertEquals(exitedZero, outcome.exitedZero()));
    }

    @Test
    @DisplayName("a run that never finished has no exit code, and is not a failed run")
    void aTimedOutRunIsNotAFailedRun() {
        ToolRunOutcome outcome =
                new ToolRunOutcome(OptionalInt.empty(), List.of(), List.of("half a line"));

        assertAll(
                () -> assertTrue(outcome.timedOut()),
                () -> assertFalse(outcome.exitedZero()),
                () -> assertEquals(OptionalInt.empty(), outcome.exitCode()),
                () -> assertEquals(List.of("half a line"), outcome.errorFirst()));
    }

    @Test
    @DisplayName("both line lists are copied, so a caller cannot change a recorded outcome")
    void theListsAreCopied() {
        List<String> mutableOut = new ArrayList<>(List.of("one"));
        List<String> mutableErr = new ArrayList<>(List.of("two"));
        ToolRunOutcome outcome = new ToolRunOutcome(OptionalInt.of(0), mutableOut, mutableErr);

        mutableOut.add("three");
        mutableErr.add("four");

        assertAll(
                () -> assertEquals(List.of("one"), outcome.standardOutput()),
                () -> assertEquals(List.of("two"), outcome.standardError()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> outcome.standardOutput().add("five")));
    }

    @Test
    @DisplayName("a null line is rejected by name and index, not stored as a hole in the output")
    void aNullLineIsRejected() {
        assertAll(
                () ->
                        assertEquals(
                                "standardOutput[1] must not be null",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ToolRunOutcome(
                                                                OptionalInt.of(0),
                                                                Arrays.asList("one", null),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "standardError[0] must not be null",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ToolRunOutcome(
                                                                OptionalInt.of(0),
                                                                List.of(),
                                                                Arrays.asList((String) null)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("every component is required")
    void everyComponentIsRequired() {
        assertAll(
                () ->
                        assertEquals(
                                "exitCode",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ToolRunOutcome(
                                                                null, List.of(), List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "standardOutput",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ToolRunOutcome(
                                                                OptionalInt.of(0), null, List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "standardError",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ToolRunOutcome(
                                                                OptionalInt.of(0), List.of(), null))
                                        .getMessage()));
    }
}
