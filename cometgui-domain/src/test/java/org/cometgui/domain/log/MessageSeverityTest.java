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

package org.cometgui.domain.log;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link MessageSeverity}.
 *
 * <p>The declaration order is the ordering every "at least" filter is built on, so it is asserted
 * as a value here rather than left as a property of the source layout. If a later phase inserts a
 * constant in the middle, this test says so before a stored filter silently changes meaning.
 */
class MessageSeverityTest {

    @Test
    @DisplayName("the constants are declared from least to most severe, with STDERR above INFO")
    void constantsAreDeclaredFromLeastToMostSevere() {
        assertEquals(
                List.of(
                        MessageSeverity.INFO,
                        MessageSeverity.STDERR,
                        MessageSeverity.WARNING,
                        MessageSeverity.ERROR),
                List.of(MessageSeverity.values()));
    }

    @ParameterizedTest(name = "[{index}] {0} atLeast {1}")
    @CsvSource({
        "INFO,INFO,true",
        "INFO,STDERR,false",
        "INFO,WARNING,false",
        "INFO,ERROR,false",
        "STDERR,INFO,true",
        "STDERR,STDERR,true",
        "STDERR,WARNING,false",
        "STDERR,ERROR,false",
        "WARNING,INFO,true",
        "WARNING,STDERR,true",
        "WARNING,WARNING,true",
        "WARNING,ERROR,false",
        "ERROR,INFO,true",
        "ERROR,STDERR,true",
        "ERROR,WARNING,true",
        "ERROR,ERROR,true"
    })
    @DisplayName("atLeast compares by declaration order, and a severity is at least itself")
    void atLeastComparesByDeclarationOrder(
            MessageSeverity severity, MessageSeverity minimum, boolean expected) {
        assertEquals(expected, severity.atLeast(minimum));
    }

    @ParameterizedTest
    @EnumSource(MessageSeverity.class)
    @DisplayName("every severity passes a filter of INFO and only ERROR passes a filter of ERROR")
    void infoAdmitsEverythingAndErrorAdmitsOnlyErrors(MessageSeverity severity) {
        assertAll(
                () -> assertTrue(severity.atLeast(MessageSeverity.INFO)),
                () ->
                        assertEquals(
                                severity == MessageSeverity.ERROR,
                                severity.atLeast(MessageSeverity.ERROR)));
    }

    @Test
    @DisplayName("STDERR is above INFO and below WARNING, because a tool's stderr is not an error")
    void stderrSitsBetweenInfoAndWarning() {
        assertAll(
                () -> assertTrue(MessageSeverity.STDERR.atLeast(MessageSeverity.INFO)),
                () -> assertFalse(MessageSeverity.STDERR.atLeast(MessageSeverity.WARNING)),
                () -> assertTrue(MessageSeverity.WARNING.atLeast(MessageSeverity.STDERR)));
    }

    @Test
    @DisplayName("a null minimum is rejected by name")
    void rejectsANullMinimum() {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () -> MessageSeverity.INFO.atLeast(Nulls.of(MessageSeverity.class)));

        assertEquals("minimum", thrown.getMessage());
    }
}
