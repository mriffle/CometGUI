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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import org.cometgui.domain.ports.FileHashes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FileRecord}.
 *
 * <p>The specification's hash requirements list six facts to record for every input and output
 * file. The first group below asserts all six, and the direction and status beside them, against
 * hand-typed literals -- the digests are the published RFC 1321 and NIST vectors, transcribed, not
 * computed here and not computed by CometGUI.
 *
 * <p>The partial group is {@code R-PROV-01}'s last sentence and {@code AC-PRV-06}: a file left
 * behind by a stage that failed or was cancelled must be representable <em>and marked</em>. A
 * truncated output recorded like any other is worse than no record -- its hash is real, it
 * verifies, and it describes something that is not a result.
 */
class FileRecordTest {

    private static final Instant MODIFIED = Instant.parse("2026-08-30T18:00:00Z");

    private static FileRecord record(
            FileDirection direction, String role, long sizeBytes, ProvenanceStatus status) {
        return new FileRecord(
                direction,
                role,
                ManifestFixtures.runFile("spectra.mzML"),
                sizeBytes,
                MODIFIED,
                ManifestFixtures.ABC_HASHES,
                status);
    }

    @Nested
    @DisplayName("the six facts the specification requires, plus direction and status")
    class Values {

        @Test
        @DisplayName("all eight components come back exactly as given")
        void allEightComponentsComeBack() {
            Path spectra = ManifestFixtures.runFile("spectra.mzML");

            FileRecord file =
                    new FileRecord(
                            FileDirection.INPUT,
                            "spectra",
                            spectra,
                            2147483648L,
                            MODIFIED,
                            ManifestFixtures.ABC_HASHES,
                            ProvenanceStatus.COMPLETED);

            assertAll(
                    () -> assertSame(FileDirection.INPUT, file.direction()),
                    () -> assertEquals("input", file.direction().wireName()),
                    () -> assertEquals("spectra", file.role()),
                    () -> assertEquals(spectra, file.path()),
                    () -> assertEquals(2147483648L, file.sizeBytes()),
                    () -> assertEquals(Instant.parse("2026-08-30T18:00:00Z"), file.modifiedAt()),
                    () ->
                            assertEquals(
                                    new FileHashes(
                                            "900150983cd24fb0d6963f7d28e17f72",
                                            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410"
                                                    + "ff61f20015ad"),
                                    file.hashes()),
                    () -> assertEquals("completed", file.status().wireName()));
        }

        @Test
        @DisplayName("a zero-byte file is a legal record, not a rejected one")
        void aZeroByteFileIsLegal() {
            FileRecord empty =
                    new FileRecord(
                            FileDirection.OUTPUT,
                            "pepxml",
                            ManifestFixtures.runFile("comet.pep.xml"),
                            0L,
                            MODIFIED,
                            ManifestFixtures.EMPTY_HASHES,
                            ProvenanceStatus.COMPLETED);

            assertAll(
                    () -> assertEquals(0L, empty.sizeBytes()),
                    () -> assertEquals("d41d8cd98f00b204e9800998ecf8427e", empty.hashes().md5()));
        }

        @Test
        @DisplayName("two records over the same eight facts are equal")
        void equalComponentsMeanEqualRecords() {
            FileRecord first =
                    record(FileDirection.INPUT, "spectra", 1024L, ProvenanceStatus.COMPLETED);
            FileRecord second =
                    record(FileDirection.INPUT, "spectra", 1024L, ProvenanceStatus.COMPLETED);

            assertAll(
                    () -> assertEquals(first, second),
                    () -> assertEquals(first.hashCode(), second.hashCode()),
                    () ->
                            assertNotEquals(
                                    first,
                                    record(
                                            FileDirection.OUTPUT,
                                            "spectra",
                                            1024L,
                                            ProvenanceStatus.COMPLETED)),
                    () ->
                            assertNotEquals(
                                    first,
                                    record(
                                            FileDirection.INPUT,
                                            "spectra",
                                            1024L,
                                            ProvenanceStatus.PARTIAL)));
        }
    }

    @Nested
    @DisplayName("R-PROV-01 and AC-PRV-06: a partial file is representable and marked")
    class Partial {

        @Test
        @DisplayName("an output left behind by a cancelled stage records as partial")
        void aCancelledStageLeavesAPartialOutput() {
            FileRecord truncated =
                    record(FileDirection.OUTPUT, "pepxml", 8388608L, ProvenanceStatus.PARTIAL);

            assertAll(
                    () -> assertSame(ProvenanceStatus.PARTIAL, truncated.status()),
                    () -> assertEquals("partial", truncated.status().wireName()),
                    () -> assertEquals(8388608L, truncated.sizeBytes()),
                    () -> assertNotEquals(ProvenanceStatus.COMPLETED, truncated.status()));
        }

        @Test
        @DisplayName("a failed and a cancelled file record are distinguishable from each other")
        void failedAndCancelledAreDistinguishable() {
            FileRecord failed =
                    record(FileDirection.OUTPUT, "pepxml", 32L, ProvenanceStatus.FAILED);
            FileRecord cancelled =
                    record(FileDirection.OUTPUT, "pepxml", 32L, ProvenanceStatus.CANCELLED);

            assertAll(
                    () -> assertEquals("failed", failed.status().wireName()),
                    () -> assertEquals("cancelled", cancelled.status().wireName()),
                    () -> assertNotEquals(failed, cancelled));
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Rejections {

        @Test
        @DisplayName("a blank role is rejected, naming the field and printing the value")
        void aBlankRoleIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    record(
                                            FileDirection.INPUT,
                                            "  \t ",
                                            1024L,
                                            ProvenanceStatus.COMPLETED));

            assertEquals("role must not be blank, but was: \"  \t \"", thrown.getMessage());
        }

        @Test
        @DisplayName("an empty role is rejected too")
        void anEmptyRoleIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    record(
                                            FileDirection.INPUT,
                                            "",
                                            1024L,
                                            ProvenanceStatus.COMPLETED));

            assertEquals("role must not be blank, but was: \"\"", thrown.getMessage());
        }

        @Test
        @DisplayName("a relative path is rejected: it means nothing to a later reader")
        void aRelativePathIsRejected() {
            Path relative = Path.of("runs", "spectra.mzML");

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new FileRecord(
                                            FileDirection.INPUT,
                                            "spectra",
                                            relative,
                                            1024L,
                                            MODIFIED,
                                            ManifestFixtures.ABC_HASHES,
                                            ProvenanceStatus.COMPLETED));

            assertEquals("path must be absolute, but was: " + relative, thrown.getMessage());
        }

        @Test
        @DisplayName("a negative size is rejected, printing the value")
        void aNegativeSizeIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    record(
                                            FileDirection.INPUT,
                                            "spectra",
                                            -1L,
                                            ProvenanceStatus.COMPLETED));

            assertEquals("sizeBytes must not be negative, but was: -1", thrown.getMessage());
        }

        @Test
        @DisplayName("every reference component is required, and the message names it")
        void everyReferenceComponentIsRequired() {
            Path spectra = ManifestFixtures.runFile("spectra.mzML");

            assertAll(
                    () ->
                            assertEquals(
                                    "direction",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new FileRecord(
                                                                    null,
                                                                    "spectra",
                                                                    spectra,
                                                                    1L,
                                                                    MODIFIED,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "role",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new FileRecord(
                                                                    FileDirection.INPUT,
                                                                    null,
                                                                    spectra,
                                                                    1L,
                                                                    MODIFIED,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "path",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new FileRecord(
                                                                    FileDirection.INPUT,
                                                                    "spectra",
                                                                    null,
                                                                    1L,
                                                                    MODIFIED,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "modifiedAt",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new FileRecord(
                                                                    FileDirection.INPUT,
                                                                    "spectra",
                                                                    spectra,
                                                                    1L,
                                                                    null,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "hashes",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new FileRecord(
                                                                    FileDirection.INPUT,
                                                                    "spectra",
                                                                    spectra,
                                                                    1L,
                                                                    MODIFIED,
                                                                    null,
                                                                    ProvenanceStatus.COMPLETED))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "status",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new FileRecord(
                                                                    FileDirection.INPUT,
                                                                    "spectra",
                                                                    spectra,
                                                                    1L,
                                                                    MODIFIED,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    null))
                                            .getMessage()));
        }
    }
}
