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

package org.cometgui.install.archive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.install.registry.ArchiveMember;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The values the archive package passes around: what each one refuses, and what it promises.
 *
 * <p>Each of these carries a rule that something further on relies on -- a limit that cannot be
 * zero, a size that cannot be negative, a member name that cannot be blank -- and a rule with no
 * test is a rule that has never been seen to hold.
 */
class ArchiveValuesTest {

    /** {@link ExtractionLimits}: the ceilings, and the four ways they can be nonsense. */
    @Nested
    class Limits {

        @Test
        @DisplayName("the shipped limits are the numbers the constants name")
        void theShippedLimits() {
            assertEquals(
                    new ExtractionLimits(
                            ExtractionLimits.DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES,
                            ExtractionLimits.DEFAULT_MAX_ENTRY_COUNT,
                            ExtractionLimits.DEFAULT_MAX_EXPANSION_RATIO,
                            ExtractionLimits.DEFAULT_RATIO_CHECKED_ABOVE_BYTES),
                    ExtractionLimits.defaults());
        }

        @Test
        @DisplayName("a total-size ceiling of nothing is refused, naming the value")
        void aZeroTotalIsRefused() {
            assertEquals(
                    "maxTotalUncompressedBytes must be a positive number of bytes, but was: 0",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new ExtractionLimits(0L, 1, 2.0d, 1L))
                            .getMessage());
        }

        @Test
        @DisplayName("an entry ceiling of nothing is refused, naming the value")
        void aZeroEntryCountIsRefused() {
            assertEquals(
                    "maxEntryCount must be a positive number of entries, but was: -1",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new ExtractionLimits(1L, -1, 2.0d, 1L))
                            .getMessage());
        }

        @Test
        @DisplayName("a ratio ceiling of one or less is refused: every archive expands")
        void aRatioOfOneIsRefused() {
            assertEquals(
                    "maxExpansionRatio must be greater than 1, because every compressed archive"
                            + " expands, but was: 1.0",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new ExtractionLimits(1L, 1, 1.0d, 1L))
                            .getMessage());
        }

        @Test
        @DisplayName("a negative ratio floor is refused, naming the value")
        void aNegativeFloorIsRefused() {
            assertEquals(
                    "ratioCheckedAboveBytes must not be negative, but was: -1",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new ExtractionLimits(1L, 1, 2.0d, -1L))
                            .getMessage());
        }
    }

    /** {@link RejectionReason}: the vocabulary a caller tells two refusals apart with. */
    @Nested
    class Reasons {

        @ParameterizedTest
        @EnumSource(RejectionReason.class)
        @DisplayName("every reason has a clause, and it reads on from \"was rejected because\"")
        void everyReasonHasAClause(RejectionReason reason) {
            assertAll(
                    () -> assertFalse(reason.clause().isBlank(), reason::name),
                    () ->
                            assertTrue(
                                    Character.isLowerCase(reason.clause().charAt(0)),
                                    () ->
                                            reason
                                                    + " begins a sentence of its own, so it will"
                                                    + " read wrongly inside \"was rejected"
                                                    + " because\""),
                    () ->
                            assertFalse(
                                    reason.clause().endsWith("."),
                                    () -> reason + " ends its clause with a full stop"),
                    () -> assertEquals(reason, RejectionReason.fromName(reason.name())));
        }

        @Test
        @DisplayName("the three bomb ceilings are the three, and nothing else claims to be one")
        void theBombReasonsAreExactlyThree() {
            Set<RejectionReason> bombs = EnumSet.noneOf(RejectionReason.class);
            for (RejectionReason reason : RejectionReason.values()) {
                if (reason.isDecompressionBomb()) {
                    bombs.add(reason);
                }
            }
            assertEquals(
                    EnumSet.of(
                            RejectionReason.BOMB_TOTAL_UNCOMPRESSED_SIZE,
                            RejectionReason.BOMB_ENTRY_COUNT,
                            RejectionReason.BOMB_EXPANSION_RATIO),
                    bombs,
                    "three independent ceilings, so a change to one cannot silently disable the"
                            + " others");
        }

        @Test
        @DisplayName("the two symbolic-link refusals are the two, and they are different faults")
        void theSymbolicLinkReasonsAreExactlyTwo() {
            Set<RejectionReason> links = EnumSet.noneOf(RejectionReason.class);
            for (RejectionReason reason : RejectionReason.values()) {
                if (reason.isSymbolicLinkAttack()) {
                    links.add(reason);
                }
            }
            assertEquals(
                    EnumSet.of(
                            RejectionReason.UNSAFE_SYMLINK, RejectionReason.WRITE_THROUGH_SYMLINK),
                    links);
        }

        @Test
        @DisplayName("an unknown reason name is refused rather than guessed at")
        void anUnknownNameIsRefused() {
            assertAll(
                    () ->
                            assertEquals(
                                    "no rejection reason is named \"NSIS_PAYLOAD_BROKEN\"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            RejectionReason.fromName(
                                                                    "NSIS_PAYLOAD_BROKEN"))
                                            .getMessage()),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () -> RejectionReason.fromName(Nulls.of(String.class))));
        }
    }

    /**
     * {@link ArchiveEntry}: a description of what a container holds, before anything is checked.
     */
    @Nested
    class Entries {

        @Test
        @DisplayName("a link's declared size is its target's length, so links are accounted for")
        void aLinkCarriesItsTargetsLength() {
            assertEquals(11L, ArchiveEntry.symlink("a", "../../etc/x").declaredSizeBytes());
        }

        @Test
        @DisplayName("a directory and an unsupported entry carry what they should")
        void directoriesAndOthers() {
            assertAll(
                    () ->
                            assertEquals(
                                    new ArchiveEntry("d", ArchiveEntryType.DIRECTORY, 0L, ""),
                                    ArchiveEntry.directory("d")),
                    () ->
                            assertEquals(
                                    new ArchiveEntry("p", ArchiveEntryType.OTHER, 7L, ""),
                                    ArchiveEntry.other("p", 7L)),
                    () ->
                            assertEquals(
                                    new ArchiveEntry("f", ArchiveEntryType.FILE, 3L, ""),
                                    ArchiveEntry.file("f", 3L)));
        }

        @Test
        @DisplayName("a negative declared size is refused, naming the value")
        void aNegativeSizeIsRefused() {
            assertEquals(
                    "declaredSizeBytes must not be negative, but was: -1",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new ArchiveEntry("a", ArchiveEntryType.FILE, -1L, ""))
                            .getMessage());
        }

        @Test
        @DisplayName("the description refuses nulls")
        void nullsAreRefused() {
            assertAll(
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new ArchiveEntry(
                                                    Nulls.of(String.class),
                                                    ArchiveEntryType.FILE,
                                                    0L,
                                                    "")),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new ArchiveEntry(
                                                    "a", Nulls.of(ArchiveEntryType.class), 0L, "")),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new ArchiveEntry(
                                                    "a",
                                                    ArchiveEntryType.FILE,
                                                    0L,
                                                    Nulls.of(String.class))));
        }
    }

    /** {@link PlacedFile} and {@link ExtractionReport}: the account of what an extraction did. */
    @Nested
    class Reports {

        @Test
        @DisplayName("a placed file with a negative length is refused")
        void aNegativeLengthIsRefused() {
            assertEquals(
                    "sizeBytes must not be negative: -5",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new PlacedFile("a", ArchiveEntryType.FILE, -5L))
                            .getMessage());
        }

        @Test
        @DisplayName("a placed file refuses nulls")
        void placedFileNulls() {
            assertAll(
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new PlacedFile(
                                                    Nulls.of(String.class),
                                                    ArchiveEntryType.FILE,
                                                    0L)),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new PlacedFile(
                                                    "a", Nulls.of(ArchiveEntryType.class), 0L)));
        }

        @Test
        @DisplayName("a report cannot count backwards, and the message names all three counts")
        void negativeCountsAreRefused() {
            assertEquals(
                    "an extraction report counts entries and bytes, and none of them can be"
                            + " negative: entriesRead=-1 expandedBytes=2 artefactBytes=3",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new ExtractionReport(List.of(), -1, 2L, 3L))
                            .getMessage());
        }

        @Test
        @DisplayName("a report's list is copied, so a caller cannot change what happened")
        void theReportIsImmutable() {
            ExtractionReport report =
                    new ExtractionReport(
                            List.of(new PlacedFile("a", ArchiveEntryType.FILE, 1L)), 1, 1L, 1L);
            assertAll(
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () ->
                                            report.placed()
                                                    .add(
                                                            new PlacedFile(
                                                                    "b",
                                                                    ArchiveEntryType.FILE,
                                                                    1L))),
                    () -> assertEquals(Optional.empty(), report.at("nothing here")),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () -> new ExtractionReport(Nulls.of(List.class), 0, 0L, 0L)));
        }
    }

    /** {@link RequestedMember}: the two strings, and the rule that neither may say nothing. */
    @Nested
    class Members {

        @Test
        @DisplayName("a member request is built from a manifest member")
        void fromAManifestMember() {
            ArchiveMember member =
                    new ArchiveMember(
                            "../my_build/percolator-noxml/src/percolator",
                            1_471_048L,
                            new org.cometgui.domain.ports.FileHashes(
                                    "5a105e8b9d40e1329780d62ea2265d8a",
                                    "f6c627105bc22f90e0ce495ae6d69a319d222b57f39b683279e3023"
                                            + "cafe44c27"),
                            "bin/percolator");
            assertEquals(
                    new RequestedMember(
                            "../my_build/percolator-noxml/src/percolator", "bin/percolator"),
                    RequestedMember.of(member));
        }

        @Test
        @DisplayName("neither string may be blank, and each is named when it is")
        void blanksAreRefused() {
            assertAll(
                    () ->
                            assertEquals(
                                    "memberPath must not be blank",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> new RequestedMember("  ", "bin/x"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "installedPath must not be blank",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> new RequestedMember("x", ""))
                                            .getMessage()),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () -> new RequestedMember(Nulls.of(String.class), "bin/x")),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () -> new RequestedMember("x", Nulls.of(String.class))),
                    () ->
                            assertThrows(
                                    NullPointerException.class,
                                    () -> RequestedMember.of(Nulls.of(ArchiveMember.class))));
        }
    }

    /** {@link BoundedEntryStream}: a window over a shared stream that closing does not close. */
    @Nested
    class Windows {

        @Test
        @DisplayName("the window stops at its length and leaves the rest for the next entry")
        void theWindowStopsAtItsLength() throws IOException {
            java.io.ByteArrayInputStream shared =
                    new java.io.ByteArrayInputStream(
                            "abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            BoundedEntryStream window = new BoundedEntryStream(shared, 3);
            assertAll(
                    () -> assertEquals('a', window.read()),
                    () -> assertEquals('b', window.read()),
                    () -> assertEquals(1, window.available()),
                    () -> assertEquals('c', window.read()),
                    () -> assertEquals(-1, window.read()),
                    () -> assertEquals(-1, window.read(new byte[4], 0, 4)),
                    () -> assertEquals(0L, window.remaining()),
                    () -> assertEquals('d', shared.read()));
        }

        @Test
        @DisplayName("closing the window leaves the stream underneath it open")
        void closingTheWindowLeavesTheStreamOpen() throws IOException {
            java.io.ByteArrayInputStream shared =
                    new java.io.ByteArrayInputStream(
                            "abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (BoundedEntryStream window = new BoundedEntryStream(shared, 2)) {
                assertEquals(2, window.readAllBytes().length);
            }
            assertEquals('c', shared.read());
        }

        @Test
        @DisplayName("a window longer than the stream reports what it actually got")
        void aShortStreamIsVisible() throws IOException {
            java.io.ByteArrayInputStream shared =
                    new java.io.ByteArrayInputStream(
                            "ab".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            BoundedEntryStream window = new BoundedEntryStream(shared, 10);
            assertAll(
                    () -> assertEquals(2, window.readAllBytes().length),
                    () -> assertEquals(8L, window.remaining()));
        }
    }
}
