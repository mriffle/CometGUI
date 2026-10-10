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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The live control path's value types: what each requires and how it describes itself. */
class PdvWindowValuesTest {

    @Test
    @DisplayName("an answer describes itself as status and stripped body")
    void answers() {
        assertAll(
                () ->
                        assertEquals(
                                "200 selected",
                                new PdvControlAnswer(200, " selected\n").describe()),
                () ->
                        assertThrows(
                                NullPointerException.class, () -> new PdvControlAnswer(200, null)));
    }

    @Test
    @DisplayName("a selection carries PDV's answer exactly when it was answered")
    void selections() {
        PdvControlAnswer answer = new PdvControlAnswer(404, "unknown ref");
        assertAll(
                () ->
                        assertEquals(
                                "a selection carries PDV's answer exactly when it was answered:"
                                        + " ANSWERED",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvSelectOutcome(
                                                                PdvSelectOutcome.Kind.ANSWERED,
                                                                "r",
                                                                Optional.empty(),
                                                                "m"))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PdvSelectOutcome(
                                                PdvSelectOutcome.Kind.FAILED,
                                                "r",
                                                Optional.of(answer),
                                                "m")),
                () ->
                        assertEquals(
                                Optional.of(answer),
                                new PdvSelectOutcome(
                                                PdvSelectOutcome.Kind.ANSWERED,
                                                "r",
                                                Optional.of(answer),
                                                "m")
                                        .answer()),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new PdvSelectOutcome(
                                                PdvSelectOutcome.Kind.NOT_OPEN,
                                                null,
                                                Optional.empty(),
                                                "m")));
    }

    @Test
    @DisplayName("only FAILED and CLOSED are final")
    void phases() {
        assertAll(
                () -> assertFalse(PdvWindowState.Phase.STARTING.isFinal()),
                () -> assertFalse(PdvWindowState.Phase.READY.isFinal()),
                () -> assertTrue(PdvWindowState.Phase.FAILED.isFinal()),
                () -> assertTrue(PdvWindowState.Phase.CLOSED.isFinal()),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new PdvWindowState(
                                                PdvWindowState.Phase.READY,
                                                OptionalInt.empty(),
                                                null)));
    }

    @Test
    @DisplayName("a shutdown has an exit status exactly when the process ended")
    void shutdowns() {
        assertAll(
                () ->
                        assertEquals(
                                "a shutdown has an exit status exactly when the process ended",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PdvWindowShutdown(
                                                                "x", false, OptionalInt.of(0)))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new PdvWindowShutdown("x", true, OptionalInt.empty())),
                () -> assertTrue(new PdvWindowShutdown("x", true, OptionalInt.of(143)).exited()));
    }
}
