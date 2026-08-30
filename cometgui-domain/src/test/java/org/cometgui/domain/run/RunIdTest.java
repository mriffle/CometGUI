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

package org.cometgui.domain.run;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link RunId}.
 *
 * <p>A run id becomes a directory name, so the rejected inputs below are the ones that would make
 * it a path traversal, a hidden directory or a name the filesystem cannot hold. Each is asserted by
 * the message it produces, not by the fact that something was thrown.
 */
class RunIdTest {

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "run-20260830T120000Z-a1b2c3",
                "R",
                "9",
                "run_1.2-3",
                "0123456789012345678901234567890123456789012345678901234567890123"
            })
    @DisplayName("accepts the identifiers a run-id source produces")
    void acceptsWellFormedIdentifiers(String value) {
        RunId runId = new RunId(value);

        assertAll(
                () -> assertEquals(value, runId.value()),
                () -> assertEquals(value, runId.toString()));
    }

    @Test
    @DisplayName("the length limit is 64 characters, and 64 is allowed")
    void theLengthLimitIsSixtyFour() {
        String longest = "a".repeat(RunId.MAX_LENGTH);

        assertAll(
                () -> assertEquals(64, RunId.MAX_LENGTH),
                () -> assertEquals(longest, new RunId(longest).value()));
    }

    @Test
    @DisplayName("one character too many is rejected, naming the length and the text")
    void rejectsAnIdentifierThatIsTooLong() {
        String tooLong = "a".repeat(RunId.MAX_LENGTH + 1);

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> new RunId(tooLong));

        assertEquals(
                "a run id must be at most 64 characters, but was 65: \"" + tooLong + "\"",
                thrown.getMessage());
    }

    @Test
    @DisplayName("a null identifier is rejected by name")
    void rejectsNull() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class, () -> new RunId(null));

        assertEquals("value", thrown.getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a blank identifier is rejected before anything else")
    void rejectsBlank(String blank) {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> new RunId(blank));

        assertEquals("a run id must not be blank", thrown.getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                ".",
                "..",
                ".hidden",
                "-leading-dash",
                "_leading-underscore",
                "run 1",
                "run/1",
                "run\\1",
                "run:1",
                "run*1",
                "run\u00e91"
            })
    @DisplayName("anything that is not a safe path segment is rejected, quoting it")
    void rejectsUnsafeIdentifiers(String unsafe) {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> new RunId(unsafe));

        assertEquals(
                "a run id must start with a letter or digit and contain only letters, digits, '.',"
                        + " '-' and '_', but was: \""
                        + unsafe
                        + "\"",
                thrown.getMessage());
    }

    @Test
    @DisplayName("two identifiers with the same text are equal")
    void equalTextMeansEqualIdentifiers() {
        assertAll(
                () -> assertEquals(new RunId("run-1"), new RunId("run-1")),
                () -> assertEquals(new RunId("run-1").hashCode(), new RunId("run-1").hashCode()),
                () -> assertNotEquals(new RunId("run-1"), new RunId("run-2")));
    }
}
