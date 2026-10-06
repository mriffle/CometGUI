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

import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code R-RUN-04}'s policy, as a pure function: the three verdicts at and around each boundary,
 * and the exact text of each refusal.
 */
class SchemaVersionPolicyTest {

    @Test
    @DisplayName("judges equal as current, below as older, above as newer -- at the boundaries")
    void judges() {
        assertAll(
                () -> assertEquals(SchemaVerdict.CURRENT, SchemaVersionPolicy.judge(1, 1)),
                () -> assertEquals(SchemaVerdict.OLDER, SchemaVersionPolicy.judge(0, 1)),
                () -> assertEquals(SchemaVerdict.NEWER, SchemaVersionPolicy.judge(2, 1)),
                () -> assertEquals(SchemaVerdict.OLDER, SchemaVersionPolicy.judge(-5, 1)),
                () ->
                        assertEquals(
                                SchemaVerdict.NEWER, SchemaVersionPolicy.judge(Long.MAX_VALUE, 1)),
                () -> assertEquals(SchemaVerdict.CURRENT, SchemaVersionPolicy.judge(7, 7)),
                () -> assertEquals(SchemaVerdict.OLDER, SchemaVersionPolicy.judge(6, 7)),
                () -> assertEquals(SchemaVerdict.NEWER, SchemaVersionPolicy.judge(8, 7)));
    }

    @Test
    @DisplayName("the current version is read: no exception")
    void currentIsRead() {
        SchemaVersionPolicy.requireReadable("/p/project.json", 1, 1);
        assertEquals(SchemaVerdict.CURRENT, SchemaVersionPolicy.judge(1, 1));
    }

    @Test
    @DisplayName("a newer version is refused naming both versions and the document")
    void newerRefused() {
        UnsupportedSchemaVersionException thrown =
                assertThrows(
                        UnsupportedSchemaVersionException.class,
                        () -> SchemaVersionPolicy.requireReadable("/p/project.json", 2, 1));
        assertAll(
                () ->
                        assertEquals(
                                "/p/project.json declares schema version 2, and this build of"
                                        + " CometGUI reads version 1. It was written by a newer"
                                        + " CometGUI, which may have changed what a member means,"
                                        + " so it is refused before anything else in it is read;"
                                        + " the file has not been changed.",
                                thrown.getMessage()),
                () -> assertEquals("/p/project.json", thrown.document()),
                () -> assertEquals(2, thrown.found()),
                () -> assertEquals(1, thrown.current()),
                () -> assertEquals(SchemaVerdict.NEWER, thrown.verdict()));
    }

    @Test
    @DisplayName("an older version is refused naming both versions and that no migration exists")
    void olderRefused() {
        UnsupportedSchemaVersionException thrown =
                assertThrows(
                        UnsupportedSchemaVersionException.class,
                        () -> SchemaVersionPolicy.requireReadable("run.json", 0, 1));
        assertAll(
                () ->
                        assertEquals(
                                "run.json declares schema version 0, and this build of CometGUI"
                                        + " reads version 1. No migration from version 0 to version"
                                        + " 1 exists, so it is refused; the file has not been"
                                        + " changed.",
                                thrown.getMessage()),
                () -> assertEquals("run.json", thrown.document()),
                () -> assertEquals(0, thrown.found()),
                () -> assertEquals(1, thrown.current()),
                () -> assertEquals(SchemaVerdict.OLDER, thrown.verdict()));
    }

    @Test
    @DisplayName("a missing document name is refused by name")
    void nullDocument() {
        assertEquals(
                "document",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        SchemaVersionPolicy.requireReadable(
                                                Nulls.of(String.class), 1, 1))
                        .getMessage());
    }
}
