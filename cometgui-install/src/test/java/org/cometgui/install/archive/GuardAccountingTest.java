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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The bookkeeping the guard does, and the rules that are the same on every path through it.
 *
 * <p>Everything here answers a mutant that lived through the first run of PIT over this package,
 * and each one is the same shape: a rule proved on <strong>one</strong> of the several paths that
 * are supposed to share it. The duplicate-name rule was asserted on the ordinary file path and on
 * none of the other three. The decompression budget was asserted where bytes are written and not
 * where they are skipped or where they are a link target. The record each placement returns -- the
 * thing the whole {@link ExtractionReport} is built from -- was checked against the disk and never
 * against the report.
 *
 * <p>That is the tenth shape once more, on the entry-type axis rather than the artefact-kind axis:
 * a rule can be entirely correct and entirely unheld for three of the four kinds of thing it
 * applies to.
 */
class GuardAccountingTest {

    @TempDir private Path work;

    private Path archives;

    private Path destination;

    @BeforeEach
    void createTree() throws IOException {
        archives = Files.createDirectories(work.resolve("archives"));
        destination = Files.createDirectories(work.resolve("dest"));
    }

    private ExtractionGuard guard() throws IOException {
        return new ExtractionGuard(destination, 4096L, ExtractionLimits.defaults());
    }

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /** One name, twice, on each of the four paths that place something. */
    @Nested
    class DuplicateNames {

        @Test
        @DisplayName("a directory named twice is refused, not silently created twice")
        void aDirectoryTwice() throws IOException {
            ExtractionGuard guard = guard();
            guard.placeDirectory(ArchiveEntry.directory("opt"));
            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () -> guard.placeDirectory(ArchiveEntry.directory("opt")));
            assertAll(
                    () -> assertEquals(RejectionReason.DUPLICATE_ENTRY_NAME, rejection.reason()),
                    () ->
                            assertEquals(
                                    "the archive entry \"opt\" was rejected because the archive"
                                            + " names it twice, so one of the two would be written"
                                            + " and the other lost, with the order of the archive"
                                            + " deciding which -- \"opt\" has already been written"
                                            + " by this extraction",
                                    rejection.getMessage()),
                    () ->
                            assertEquals(
                                    1,
                                    guard.report().placed().size(),
                                    "creating a directory is idempotent, so nothing but the rule"
                                            + " itself notices the second one"));
        }

        @Test
        @DisplayName(
                "a link over a name a file already took is refused by the rule, not by the"
                        + " file system")
        void aSymbolicLinkOverATakenName() throws IOException {
            ExtractionGuard guard = guard();
            guard.placeFileFromArchiveName(ArchiveEntry.file("alias", 5L), bytes("hello"));
            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () -> guard.placeSymlink(ArchiveEntry.symlink("alias", "real.txt")),
                            "without the claim the file system raises FileAlreadyExistsException,"
                                    + " which is not this extractor telling anyone what is wrong");
            assertAll(
                    () -> assertEquals(RejectionReason.DUPLICATE_ENTRY_NAME, rejection.reason()),
                    () -> assertEquals("alias", rejection.subject()),
                    () ->
                            assertEquals(
                                    "hello",
                                    Files.readString(destination.resolve("alias")),
                                    "the first entry keeps the name; the second is refused rather"
                                            + " than allowed to replace it"));
        }

        @Test
        @DisplayName("a link over a link is refused earlier still, by the write-through rule")
        void aSymbolicLinkOverALink() throws IOException {
            /*
             * Pinned because the two refusals arrive from different rules and a later reader will
             * wonder which.  A name already holding a LINK is caught while the path is resolved --
             * this extractor never writes through a link, and that includes replacing one -- so the
             * duplicate rule behind it is never reached for this particular collision.  The
             * duplicate rule is what catches a link over a FILE, above.
             */
            ExtractionGuard guard = guard();
            guard.placeFileFromArchiveName(ArchiveEntry.file("real.txt", 5L), bytes("hello"));
            guard.placeSymlink(ArchiveEntry.symlink("alias", "real.txt"));
            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () -> guard.placeSymlink(ArchiveEntry.symlink("alias", "real.txt")));
            assertEquals(RejectionReason.WRITE_THROUGH_SYMLINK, rejection.reason());
        }

        @Test
        @DisplayName("a whole-file copy landing twice is refused by the rule, not by the copy")
        void aWholeFileCopyTwice() throws IOException {
            Path source = Files.writeString(work.resolve("source.bin"), "abc");
            ExtractionGuard guard = guard();
            guard.copyWholeFile(source, "bin/tool", "source.bin");
            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () -> guard.copyWholeFile(source, "bin/tool", "source.bin"));
            assertAll(
                    () -> assertEquals(RejectionReason.DUPLICATE_ENTRY_NAME, rejection.reason()),
                    () -> assertEquals("source.bin", rejection.subject()));
        }
    }

    /** What each placement returns, which is what the report is made of. */
    @Nested
    class WhatIsReported {

        @Test
        @DisplayName("every placement returns the record the report is built from")
        void everyPlacementReturnsItsRecord() throws IOException {
            Path source = Files.writeString(work.resolve("source.bin"), "abc");
            ExtractionGuard guard = guard();
            PlacedFile fromArchiveName =
                    guard.placeFileFromArchiveName(ArchiveEntry.file("a.txt", 5L), bytes("hello"));
            PlacedFile fromManifest =
                    guard.placeFileAtDeclaredPath(
                            ArchiveEntry.file("upstream/name.txt", 2L), "bin/b.txt", bytes("hi"));
            PlacedFile copied = guard.copyWholeFile(source, "bin/tool", "source.bin");
            PlacedFile link = guard.placeSymlink(ArchiveEntry.symlink("alias", "a.txt"));
            ExtractionReport report = guard.report();
            assertAll(
                    () ->
                            assertEquals(
                                    new PlacedFile("a.txt", ArchiveEntryType.FILE, 5L),
                                    fromArchiveName),
                    () ->
                            assertEquals(
                                    new PlacedFile("bin/b.txt", ArchiveEntryType.FILE, 2L),
                                    fromManifest),
                    () ->
                            assertEquals(
                                    new PlacedFile("bin/tool", ArchiveEntryType.FILE, 3L), copied),
                    () -> assertEquals(new PlacedFile("alias", ArchiveEntryType.SYMLINK, 0L), link),
                    () ->
                            assertEquals(
                                    List.of(fromArchiveName, fromManifest, copied, link),
                                    report.placed(),
                                    "the report is the account of what happened; a placement that"
                                            + " returned nothing would leave it silently short"),
                    () ->
                            assertEquals(
                                    List.of("a.txt", "bin/b.txt", "bin/tool", "alias"),
                                    report.paths()));
        }

        @Test
        @DisplayName("a link in a directory that does not exist yet gets that directory made")
        void aLinkCreatesItsParentDirectory() throws IOException {
            ExtractionGuard guard = guard();
            guard.placeDirectory(ArchiveEntry.directory("deep"));
            guard.placeFileFromArchiveName(ArchiveEntry.file("deep/real.txt", 5L), bytes("hello"));
            PlacedFile link =
                    guard.placeSymlink(ArchiveEntry.symlink("deep/nested/alias", "../real.txt"));
            assertAll(
                    () -> assertEquals("deep/nested/alias", link.path()),
                    () ->
                            assertTrue(
                                    Files.isSymbolicLink(destination.resolve("deep/nested/alias")),
                                    "the link's own directory has to be made first, or the file"
                                            + " system refuses it"),
                    () ->
                            assertEquals(
                                    "hello",
                                    Files.readString(destination.resolve("deep/nested/alias"))));
        }
    }

    /** The decompression budget, on the paths where nothing is written. */
    @Nested
    class BudgetOnEveryPath {

        @Test
        @DisplayName("bytes skipped on the way to a named member still count against the budget")
        void skippedBytesAreStillCounted() throws IOException {
            /*
             * The member wanted here is three bytes.  Everything else in the archive is thrown
             * away -- and a bomb that is thrown away has still been decompressed, has still cost
             * the machine the time and the heat, and must still stop the extraction.
             */
            Path artefact =
                    ArchiveFixtures.build(
                            ArtefactKind.TAR_GZ,
                            archives,
                            "skipped.tar.gz",
                            List.of(
                                    Entry.file("huge.bin", ArchiveFixtures.compressible(400_000)),
                                    Entry.file("wanted.txt", "abc")));
            ExtractionLimits limits =
                    new ExtractionLimits(100_000L, 1000, 1_000_000.0d, Long.MAX_VALUE);
            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () ->
                                    new ArtefactExtractor(limits)
                                            .extractNamedMembers(
                                                    ArtefactKind.TAR_GZ,
                                                    artefact,
                                                    destination,
                                                    List.of(
                                                            new RequestedMember(
                                                                    "wanted.txt", "bin/wanted"))));
            assertAll(
                    () ->
                            assertEquals(
                                    RejectionReason.BOMB_TOTAL_UNCOMPRESSED_SIZE,
                                    rejection.reason()),
                    () -> assertEquals("huge.bin", rejection.subject()),
                    () ->
                            assertEquals(
                                    List.of(),
                                    DestinationSnapshot.of(destination),
                                    "nothing is installed from an artefact that was stopped"));
        }

        @Test
        @DisplayName("symbolic-link targets count against the budget as the bytes they are")
        void linkTargetsAreCounted() throws IOException {
            List<Entry> entries = new ArrayList<>();
            entries.add(Entry.file("real.txt", "x"));
            String target = "real.txt";
            for (int index = 0; index < 200; index++) {
                entries.add(Entry.symlink("link-" + index, target));
            }
            Path artefact =
                    ArchiveFixtures.build(ArtefactKind.TAR_GZ, archives, "links.tar.gz", entries);
            /*
             * One byte of file content plus two hundred eight-byte targets is 1601 bytes of
             * expansion.  The ceiling sits under that and over the file alone, so only the link
             * accounting can be what trips it.
             */
            ExtractionLimits limits =
                    new ExtractionLimits(1_000L, 1000, 1_000_000.0d, Long.MAX_VALUE);
            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () ->
                                    new ArtefactExtractor(limits)
                                            .extractWholeArtefact(
                                                    ArtefactKind.TAR_GZ,
                                                    artefact,
                                                    destination,
                                                    "real.txt"));
            assertAll(
                    () ->
                            assertEquals(
                                    RejectionReason.BOMB_TOTAL_UNCOMPRESSED_SIZE,
                                    rejection.reason()),
                    () ->
                            assertTrue(
                                    rejection.subject().startsWith("link-"),
                                    () ->
                                            "the entry that took the artefact past the ceiling was"
                                                    + " a link, and the message must say so: "
                                                    + rejection.subject()));
        }

        @Test
        @DisplayName("an artefact that expands to exactly the ceiling is accepted, not refused")
        void exactlyTheCeilingIsAllowed() throws IOException {
            Path artefact =
                    ArchiveFixtures.build(
                            ArtefactKind.TAR_GZ,
                            archives,
                            "exact.tar.gz",
                            List.of(Entry.file("a.bin", ArchiveFixtures.compressible(1000))));
            ExtractionLimits limits =
                    new ExtractionLimits(1_000L, 1000, 1_000_000.0d, Long.MAX_VALUE);
            ExtractionReport report =
                    new ArtefactExtractor(limits)
                            .extractWholeArtefact(
                                    ArtefactKind.TAR_GZ, artefact, destination, "a.bin");
            assertEquals(
                    1_000L,
                    report.expandedBytes(),
                    "the limit is the most this extractor produces, so producing exactly it is"
                            + " within the limit");
        }

        @Test
        @DisplayName("expansion to exactly the ratio floor does not start the ratio test")
        void exactlyTheRatioFloorIsAllowed() throws IOException {
            Path artefact =
                    ArchiveFixtures.build(
                            ArtefactKind.TAR_GZ,
                            archives,
                            "floor.tar.gz",
                            List.of(Entry.file("a.bin", ArchiveFixtures.compressible(1000))));
            ExtractionLimits limits = new ExtractionLimits(1_000_000L, 1000, 1.5d, 1_000L);
            ExtractionReport report =
                    new ArtefactExtractor(limits)
                            .extractWholeArtefact(
                                    ArtefactKind.TAR_GZ, artefact, destination, "a.bin");
            assertEquals(
                    1_000L,
                    report.expandedBytes(),
                    "the floor is where the ratio starts being measured, and this archive stops"
                            + " exactly on it -- a ratio of 1000:1 that is nonetheless allowed");
        }

        @Test
        @DisplayName("expansion to exactly the permitted ratio is allowed; one byte more is not")
        void exactlyTheRatioIsAllowed() throws IOException {
            /*
             * Ten times a hundred bytes is a thousand, and "at most ten times" includes ten times.
             * Driven through the guard rather than through an archive because the artefact's size
             * on disk is the denominator, and a fixture's compressed size is not a round number.
             */
            ExtractionGuard atTheLimit =
                    new ExtractionGuard(
                            destination, 100L, new ExtractionLimits(1_000_000L, 100, 10.0d, 0L));
            atTheLimit.expand("a.bin", 1_000L);
            assertEquals(1_000L, atTheLimit.report().expandedBytes());

            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () -> atTheLimit.expand("a.bin", 1L));
            assertEquals(RejectionReason.BOMB_EXPANSION_RATIO, rejection.reason());
        }

        @Test
        @DisplayName("the entry that takes the artefact one byte past the ceiling is the one named")
        void oneBytePastTheCeilingIsRefused() throws IOException {
            Path artefact =
                    ArchiveFixtures.build(
                            ArtefactKind.TAR_GZ,
                            archives,
                            "past.tar.gz",
                            List.of(Entry.file("a.bin", ArchiveFixtures.compressible(1001))));
            ExtractionLimits limits =
                    new ExtractionLimits(1_000L, 1000, 1_000_000.0d, Long.MAX_VALUE);
            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () ->
                                    new ArtefactExtractor(limits)
                                            .extractWholeArtefact(
                                                    ArtefactKind.TAR_GZ,
                                                    artefact,
                                                    destination,
                                                    "a.bin"));
            assertEquals(RejectionReason.BOMB_TOTAL_UNCOMPRESSED_SIZE, rejection.reason());
        }
    }

    /** A container that answers a read with nothing at all. */
    @Nested
    class NoProgress {

        @Test
        @DisplayName("a stream that returns no bytes and no end is refused rather than waited on")
        void aStreamThatMakesNoProgressIsRefused() throws IOException {
            ExtractionGuard guard = guard();
            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () ->
                                    guard.placeFileFromArchiveName(
                                            ArchiveEntry.file("stuck.bin", 10L), new NeverReady()),
                            "a loop that keeps asking a stalled container turns a 99 MB artefact"
                                    + " into a hang that looks like a slow disk");
            assertAll(
                    () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                    () ->
                            assertEquals(
                                    "the archive entry \"stuck.bin\" was rejected because its"
                                            + " container structure is not readable -- reading it"
                                            + " returned no bytes and did not report the end of the"
                                            + " entry, so the container is not making progress",
                                    rejection.getMessage()));
        }
    }

    /** A stream that answers every read with zero bytes without ever reaching its end. */
    private static final class NeverReady extends InputStream {

        @Override
        public int read() {
            return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return 0;
        }
    }
}
