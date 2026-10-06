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

package org.cometgui.domain.project;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link ProjectId} and {@link ProjectDescriptor}. */
class ProjectIdTest {

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "project-beta",
                "P",
                "7",
                "a.b_c-d",
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
            })
    @DisplayName("accepts safe identifiers, and toString is the bare value")
    void accepts(String value) {
        ProjectId id = new ProjectId(value);
        assertAll(() -> assertEquals(value, id.value()), () -> assertEquals(value, id.toString()));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", ".", "..", ".hidden", "-x", "_x", "a b", "a/b", "a\\b", "é"})
    @DisplayName("refuses anything that is not a safe path segment, quoting it")
    void refuses(String value) {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> new ProjectId(value));
        assertEquals(
                "a project id must start with a letter or digit and contain only letters, digits,"
                        + " '.', '-' and '_', but was: \""
                        + value
                        + "\"",
                thrown.getMessage());
    }

    @Test
    @DisplayName("65 characters are refused, naming the length")
    void refusesTooLong() {
        String tooLong = "a".repeat(65);
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> new ProjectId(tooLong));
        assertAll(
                () -> assertEquals(64, ProjectId.MAX_LENGTH),
                () ->
                        assertEquals(
                                "a project id must be at most 64 characters, but was 65: \""
                                        + tooLong
                                        + "\"",
                                thrown.getMessage()));
    }

    @Test
    @DisplayName("null is refused by name")
    void refusesNull() {
        assertEquals(
                "value",
                assertThrows(
                                NullPointerException.class,
                                () -> new ProjectId(Nulls.of(String.class)))
                        .getMessage());
    }

    @Test
    @DisplayName("a descriptor truncates its creation time to milliseconds and is version 1")
    void descriptorTruncates() {
        ProjectDescriptor descriptor =
                new ProjectDescriptor(
                        new ProjectId("p"), Instant.parse("2026-10-06T09:00:00.123999999Z"));
        assertAll(
                () -> assertEquals(1, ProjectDescriptor.SCHEMA_VERSION),
                () -> assertEquals(new ProjectId("p"), descriptor.id()),
                () ->
                        assertEquals(
                                Instant.parse("2026-10-06T09:00:00.123Z"), descriptor.created()));
    }

    @Test
    @DisplayName("a descriptor refuses a missing member by name")
    void descriptorRefusesNulls() {
        Instant now = Instant.parse("2026-10-06T09:00:00Z");
        assertAll(
                () ->
                        assertEquals(
                                "id",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProjectDescriptor(
                                                                Nulls.of(ProjectId.class), now))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "created",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProjectDescriptor(
                                                                new ProjectId("p"),
                                                                Nulls.of(Instant.class)))
                                        .getMessage()));
    }
}
