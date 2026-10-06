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

package org.cometgui.workflow.storage;

import static org.cometgui.workflow.storage.RunJsonTest.replaceOnce;
import static org.cometgui.workflow.storage.StorageFixtures.PROJECT_JSON;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.stream.Stream;
import org.cometgui.domain.project.LockOwner;
import org.cometgui.domain.project.ProjectDescriptor;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.SchemaVerdict;
import org.cometgui.domain.project.UnsupportedSchemaVersionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The {@code project.json} and {@code project.lock} formats, against hand-typed documents. */
class ProjectJsonTest {

    private static final String LOCK_JSON =
            """
            {
              "schemaVersion": 1,
              "pid": 4242,
              "host": "lab-pc",
              "started": "2026-10-06T09:00:00.125Z"
            }
            """;

    @Test
    @DisplayName("the project writer produces exactly the hand-typed document")
    void projectWriter() {
        assertEquals(
                PROJECT_JSON,
                ProjectJson.render(
                        new ProjectDescriptor(
                                new ProjectId("project-beta"),
                                Instant.parse("2026-08-28T23:00:00.000999Z"))));
    }

    @Test
    @DisplayName("the project reader reads the hand-typed document")
    void projectReader() {
        ProjectDescriptor read = ProjectJson.parse(PROJECT_JSON, "project.json");
        assertAll(
                () -> assertEquals(new ProjectId("project-beta"), read.id()),
                () -> assertEquals(Instant.parse("2026-08-28T23:00:00.000Z"), read.created()));
    }

    @Test
    @DisplayName("a newer project.json is refused before any other member is read")
    void projectNewer() {
        UnsupportedSchemaVersionException thrown =
                assertThrows(
                        UnsupportedSchemaVersionException.class,
                        () ->
                                ProjectJson.parse(
                                        "{\"schemaVersion\": 3, \"projectId\": [], \"x\": 1}\n",
                                        "p/project.json"));
        assertAll(
                () -> assertEquals(SchemaVerdict.NEWER, thrown.verdict()),
                () ->
                        assertEquals(
                                "p/project.json declares schema version 3, and this build of"
                                        + " CometGUI reads version 1. It was written by a newer"
                                        + " CometGUI, which may have changed what a member means,"
                                        + " so"
                                        + " it is refused before anything else in it is read; the"
                                        + " file has not been changed.",
                                thrown.getMessage()));
    }

    @Test
    @DisplayName("an older project.json is refused")
    void projectOlder() {
        assertEquals(
                SchemaVerdict.OLDER,
                assertThrows(
                                UnsupportedSchemaVersionException.class,
                                () ->
                                        ProjectJson.parse(
                                                replaceOnce(
                                                        PROJECT_JSON,
                                                        "\"schemaVersion\": 1",
                                                        "\"schemaVersion\": -1"),
                                                "project.json"))
                        .verdict());
    }

    static Stream<Arguments> projectDamage() {
        return Stream.of(
                Arguments.of(
                        "\"projectId\": \"project-beta\"",
                        "\"projectId\": \"../x\"",
                        "projectId",
                        "\"projectId\" was refused by the model (IllegalArgumentException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "\"projectId\": \"project-beta\"",
                        "\"projectId\": 7",
                        "projectId",
                        "\"projectId\" must be a string"),
                Arguments.of(
                        "\"created\": \"2026-08-28T23:00:00.000Z\"",
                        "\"created\": \"2026-02-30T23:00:00.000Z\"",
                        "created",
                        "\"created\" must be a UTC timestamp of the form"
                                + " uuuu-MM-dd'T'HH:mm:ss.SSS'Z'"),
                Arguments.of(
                        "  \"created\": \"2026-08-28T23:00:00.000Z\"\n",
                        "  \"x\": 1\n",
                        "",
                        "the document has 1 member(s) this build does not know; its schema version"
                                + " defines exactly [schemaVersion, projectId, created]"),
                Arguments.of(
                        ",\n  \"created\": \"2026-08-28T23:00:00.000Z\"",
                        "",
                        "",
                        "the document has no member \"created\""));
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("projectDamage")
    @DisplayName("a damaged project.json is refused naming the member")
    void projectDamaged(String from, String to, String member, String problem) {
        InvalidDocumentException thrown =
                assertThrows(
                        InvalidDocumentException.class,
                        () ->
                                ProjectJson.parse(
                                        replaceOnce(PROJECT_JSON, from, to), "project.json"));
        assertAll(
                () -> assertEquals("project.json is not valid: " + problem, thrown.getMessage()),
                () -> assertEquals(member, thrown.member()));
    }

    @Test
    @DisplayName("the lock writer produces exactly the hand-typed record")
    void lockWriter() {
        assertEquals(
                LOCK_JSON,
                LockJson.render(
                        new LockOwner(4242, "lab-pc", Instant.parse("2026-10-06T09:00:00.125Z"))));
    }

    @Test
    @DisplayName("the lock reader reads the hand-typed record")
    void lockReader() {
        assertEquals(
                new LockOwner(4242, "lab-pc", Instant.parse("2026-10-06T09:00:00.125Z")),
                LockJson.parse(LOCK_JSON, "project.lock"));
    }

    @Test
    @DisplayName("a lock record with a bad pid or host is refused naming the member")
    void lockDamaged() {
        InvalidDocumentException zeroPid =
                assertThrows(
                        InvalidDocumentException.class,
                        () ->
                                LockJson.parse(
                                        replaceOnce(LOCK_JSON, "\"pid\": 4242", "\"pid\": 0"),
                                        "project.lock"));
        InvalidDocumentException blankHost =
                assertThrows(
                        InvalidDocumentException.class,
                        () ->
                                LockJson.parse(
                                        replaceOnce(
                                                LOCK_JSON,
                                                "\"host\": \"lab-pc\"",
                                                "\"host\": \"\""),
                                        "project.lock"));
        assertAll(
                () ->
                        assertEquals(
                                "project.lock is not valid: \"pid\" must be a positive process id",
                                zeroPid.getMessage()),
                () -> assertEquals("pid", zeroPid.member()),
                () -> assertEquals("host", blankHost.member()),
                () ->
                        assertEquals(
                                SchemaVerdict.NEWER,
                                assertThrows(
                                                UnsupportedSchemaVersionException.class,
                                                () ->
                                                        LockJson.parse(
                                                                replaceOnce(
                                                                        LOCK_JSON,
                                                                        "\"schemaVersion\": 1",
                                                                        "\"schemaVersion\": 2"),
                                                                "project.lock"))
                                        .verdict()));
    }
}
