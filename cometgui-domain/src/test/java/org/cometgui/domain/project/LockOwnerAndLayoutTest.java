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

import static org.cometgui.domain.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link LockOwner} and {@link ProjectLayout}. */
class LockOwnerAndLayoutTest {

    private static final Instant STARTED = Instant.parse("2026-10-06T09:00:05.678999Z");

    @Test
    @DisplayName("an owner describes itself with pid, host and UTC start to the second")
    void describes() {
        LockOwner owner = new LockOwner(4242, "lab-pc", STARTED);
        assertAll(
                () -> assertEquals(4242, owner.pid()),
                () -> assertEquals("lab-pc", owner.host()),
                () -> assertEquals(Instant.parse("2026-10-06T09:00:05.678Z"), owner.started()),
                () ->
                        assertEquals(
                                "process 4242 on host lab-pc, since 2026-10-06T09:00:05Z",
                                owner.describe()));
    }

    @Test
    @DisplayName("the description does not depend on the default locale")
    void describesUnderAThaiLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("th-TH-u-nu-thai"));
            assertEquals(
                    "process 1 on host h, since 2026-10-06T09:00:05Z",
                    new LockOwner(1, "h", STARTED).describe());
        } finally {
            Locale.setDefault(before);
        }
    }

    @ParameterizedTest(name = "[{index}] pid {0}")
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("a pid that is not positive is refused, quoting it")
    void refusesPid(long pid) {
        assertEquals(
                "a process id must be positive, but was " + pid,
                assertThrows(IllegalArgumentException.class, () -> new LockOwner(pid, "h", STARTED))
                        .getMessage());
    }

    @Test
    @DisplayName("a blank host, a host with a control character and nulls are refused")
    void refusesHost() {
        assertAll(
                () ->
                        assertEquals(
                                "a host name must not be blank",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new LockOwner(1, " ", STARTED))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a host name must not contain a control character (at index 3)",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new LockOwner(1, "lab\npc", STARTED))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a host name must not contain a control character (at index 0)",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new LockOwner(1, "\u007fx", STARTED))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "host",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LockOwner(
                                                                1, Nulls.of(String.class), STARTED))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "started",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LockOwner(
                                                                1, "h", Nulls.of(Instant.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("pid 1 and a one-character host are the smallest accepted")
    void smallestAccepted() {
        LockOwner owner = new LockOwner(1, "h", STARTED);
        assertEquals(1, owner.pid());
    }

    @Test
    @DisplayName("the project layout names every file and directory under the normalised root")
    void layout() {
        ProjectLayout layout = new ProjectLayout(absolute("data/projects/./beta/../beta"));
        assertAll(
                () -> assertEquals(absolute("data/projects/beta"), layout.root()),
                () ->
                        assertEquals(
                                absolute("data/projects/beta/project.json"), layout.projectFile()),
                () -> assertEquals(absolute("data/projects/beta/project.lock"), layout.lockFile()),
                () -> assertEquals(absolute("data/projects/beta/runs"), layout.runsDirectory()),
                () ->
                        assertEquals(
                                absolute("data/projects/beta/presets"), layout.presetsDirectory()),
                () ->
                        assertEquals(
                                absolute("data/projects/beta/index-cache"),
                                layout.indexCacheDirectory()));
    }

    @Test
    @DisplayName("a relative project root and a null one are refused")
    void layoutRefuses() {
        assertAll(
                () ->
                        assertEquals(
                                "a project directory must be absolute: beta",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new ProjectLayout(Path.of("beta")))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "root",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new ProjectLayout(Nulls.of(Path.class)))
                                        .getMessage()));
    }
}
