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

package org.cometgui.provenance.manifest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LogRecord}.
 *
 * <p>A log record is two facts, and the test that matters is that it cannot be built without the
 * second one: a path with no checksum records where a file was, which is worth nothing once anyone
 * can edit it. {@link org.cometgui.domain.ports.FileHashes} makes a half-hashed log
 * unrepresentable, so what is left to prove here is that the record keeps what it was given and
 * refuses a path that means nothing to a later reader.
 */
class LogRecordTest {

    @Test
    @DisplayName("keeps the path and the digests it was given")
    void keepsWhatItWasGiven() {
        Path log = ManifestFixtures.runFile("comet.stdout.log");

        LogRecord record = new LogRecord(log, ManifestFixtures.ABC_HASHES);

        assertAll(
                () -> assertEquals(log, record.path()),
                () -> assertEquals("900150983cd24fb0d6963f7d28e17f72", record.hashes().md5()),
                () ->
                        assertEquals(
                                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                                record.hashes().sha256()));
    }

    @Test
    @DisplayName("a relative path is rejected, naming the field and the value")
    void aRelativePathIsRejected() {
        Path relative = Path.of("logs", "comet.stdout.log");

        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new LogRecord(relative, ManifestFixtures.ABC_HASHES));

        assertEquals("path must be absolute, but was: " + relative, thrown.getMessage());
    }

    @Test
    @DisplayName("a null path or a null digest pair is rejected, naming the field")
    void nullsAreRejected() {
        Path log = ManifestFixtures.runFile("comet.stdout.log");

        assertAll(
                () ->
                        assertEquals(
                                "path",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LogRecord(
                                                                null, ManifestFixtures.ABC_HASHES))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "hashes",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new LogRecord(log, null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("two records over the same file and digests are equal")
    void equalComponentsMeanEqualRecords() {
        Path log = ManifestFixtures.runFile("comet.stdout.log");
        LogRecord first = new LogRecord(log, ManifestFixtures.ABC_HASHES);
        LogRecord second = new LogRecord(log, ManifestFixtures.ABC_HASHES);

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.hashCode(), second.hashCode()),
                () -> assertNotEquals(first, new LogRecord(log, ManifestFixtures.EMPTY_HASHES)));
    }
}
