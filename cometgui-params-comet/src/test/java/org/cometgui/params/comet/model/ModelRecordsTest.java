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

package org.cometgui.params.comet.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The small records of the model and the rules they hold. */
class ModelRecordsTest {

    @Test
    @DisplayName("a diagnostic's severity is its code's")
    void diagnosticSeverity() {
        Diagnostic error =
                Diagnostic.of(Diagnostic.Code.DUPLICATE_PARAMETER, List.of(2, 9), "x", "m");
        assertTrue(error.isError());
        assertEquals(Diagnostic.Severity.ERROR, error.severity());
        assertEquals(List.of(2, 9), error.lines());
        assertEquals(Optional.of("x"), error.parameter());
        Diagnostic warning = Diagnostic.of(Diagnostic.Code.VERSION_MISMATCH, List.of(1), null, "m");
        assertFalse(warning.isError());
        assertEquals(Optional.empty(), warning.parameter());
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new Diagnostic(
                                        Diagnostic.Severity.WARNING,
                                        Diagnostic.Code.MALFORMED_LINE,
                                        List.of(1),
                                        Optional.empty(),
                                        "m"));
        assertEquals("MALFORMED_LINE is always ERROR, not WARNING", failure.getMessage());
        for (Diagnostic.Code code : Diagnostic.Code.values()) {
            boolean warningCode =
                    code == Diagnostic.Code.VERSION_MISMATCH
                            || code == Diagnostic.Code.VERSION_MARKER_MISSING
                            || code == Diagnostic.Code.UNKNOWN_PARAMETER
                            || code == Diagnostic.Code.NOT_IN_VERSION;
            assertEquals(
                    warningCode ? Diagnostic.Severity.WARNING : Diagnostic.Severity.ERROR,
                    code.severity(),
                    code.name());
        }
    }

    @Test
    @DisplayName("an unknown parameter must be writable and readable back")
    void unknownRules() {
        UnknownParameter fine =
                new UnknownParameter(
                        "ms1_mass_range",
                        "1 2",
                        Optional.of("c"),
                        List.of("# above", "   # indented"),
                        4);
        assertEquals(List.of("# above", "   # indented"), fine.comments());
        assertBad("bad name", "1", Optional.empty(), List.of(), "is not a parameter name");
        assertBad("knob", "a#b", Optional.empty(), List.of(), "cannot be written");
        assertBad("knob", " a", Optional.empty(), List.of(), "cannot be written");
        assertBad("knob", "a", Optional.of(" c"), List.of(), "must be one line");
        assertBad("knob", "a", Optional.of("c\nd"), List.of(), "must be one line");
        assertBad("knob", "a", Optional.of("c\rd"), List.of(), "must be one line");
        assertBad("knob", "a", Optional.empty(), List.of("not a comment"), "not one comment line");
        assertBad("knob", "a", Optional.empty(), List.of("# a\nb"), "not one comment line");
        assertBad("knob", "a", Optional.empty(), List.of("# a\rb"), "not one comment line");
        assertBad("knob", "a", Optional.empty(), List.of("\n# a"), "not one comment line");
        assertBad("knob", "a", Optional.empty(), List.of("\r# a"), "not one comment line");
        assertBad("knob", "a", Optional.of("\nc"), List.of(), "must be one line");
    }

    private static void assertBad(
            String name,
            String value,
            Optional<String> inline,
            List<String> comments,
            String fragment) {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new UnknownParameter(name, value, inline, comments, 1));
        assertTrue(failure.getMessage().contains(fragment), failure.getMessage());
    }
}
