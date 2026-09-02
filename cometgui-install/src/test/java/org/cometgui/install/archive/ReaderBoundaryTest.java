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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The exact values each reader's limits and comparisons turn on.
 *
 * <p>A limit written {@code >} and a limit written {@code >=} agree everywhere except on one value,
 * and that value is the only place the difference between "at most this much" and "less than this
 * much" can be seen. Mutation testing asks for exactly those inputs, and where one can be built it
 * is here; where it genuinely cannot be reached, the argument is written beside the code rather
 * than left for a reader to reconstruct.
 */
class ReaderBoundaryTest {

    /** What {@code ZipArchiveReader} will hold in memory, restated so the boundary can be typed. */
    private static final int MAX_CENTRAL_DIRECTORY_BYTES = 64 * 1024 * 1024;

    /** What {@code PkgPayloadReader} will inflate a table of contents to. */
    private static final int MAX_TOC_BYTES = 8 * 1024 * 1024;

    /** What {@code CpioArchiveReader} will read for one name or link target. */
    private static final int MAX_NAME_BYTES = 65536;

    @TempDir private Path work;

    private Path archives;

    private Path destination;

    private final ArtefactExtractor extractor = new ArtefactExtractor();

    @BeforeEach
    void createTree() throws IOException {
        archives = Files.createDirectories(work.resolve("archives"));
        destination = Files.createDirectories(work.resolve("dest"));
    }

    private ExtractionRejectedException refuse(ArtefactKind kind, byte[] bytes, String name)
            throws IOException {
        Path artefact = Files.write(archives.resolve(name), bytes);
        return assertThrows(
                ExtractionRejectedException.class,
                () -> extractor.extractWholeArtefact(kind, artefact, destination, "whatever"));
    }

    /** tar: the two historical checksums, and where a long name ends. */
    @Nested
    class Tar {

        @Test
        @DisplayName("the unsigned sum is what is checked when the two sums differ")
        void theUnsignedSumIsLoadBearing() throws IOException {
            /*
             * A tar header may store either the unsigned or the historical signed sum of its own
             * bytes, and this reader accepts both.  That tolerance hides a defect: for an
             * all-ASCII header the two sums are equal, so a broken unsigned computation still
             * matches the signed one and nothing goes red.  One byte with the high bit set makes
             * them differ by 256, and this fixture stores the UNSIGNED sum -- so the unsigned
             * accumulator is now the only thing that can accept it.  ArchiveEdgeCasesTest holds
             * the twin that stores the signed sum.
             */
            byte[] tar = ArchiveFixtures.tarBytes(List.of(Entry.file("tool.bin", "payload")));
            tar[500] = (byte) 0xFF;
            for (int index = 148; index < 156; index++) {
                tar[index] = ' ';
            }
            long unsigned = 0;
            for (int index = 0; index < 512; index++) {
                unsigned += tar[index] & 0xFF;
            }
            byte[] field = String.format("%06o", unsigned).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(field, 0, tar, 148, 6);
            tar[154] = 0;
            tar[155] = ' ';
            Path artefact =
                    Files.write(archives.resolve("unsigned.tar.gz"), ArchiveFixtures.gzip(tar));
            ExtractionReport report =
                    extractor.extractWholeArtefact(
                            ArtefactKind.TAR_GZ, artefact, destination, "tool.bin");
            assertEquals(List.of("tool.bin"), report.paths());
        }

        @Test
        @DisplayName(
                "a long name that is nothing but its terminator is empty, not a name with a"
                        + " NUL in it")
        void aLongNameThatBeginsWithItsTerminator() throws IOException {
            byte[] tar =
                    ArchiveFixtures.tarBytesRaw(
                            List.of(
                                    new String[] {"././@LongLink", "L", "", ""},
                                    new String[] {"ignored", "0", "", ""}),
                            List.of(
                                    "\u0000discarded".getBytes(StandardCharsets.UTF_8),
                                    "payload".getBytes(StandardCharsets.UTF_8)));
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.TAR_GZ, ArchiveFixtures.gzip(tar), "nulfirst.tar.gz");
            assertEquals(
                    RejectionReason.ENTRY_NAME_EMPTY,
                    rejection.reason(),
                    "the name ends at the first NUL, so a body starting with one names nothing --"
                            + " reporting a NUL inside a name instead would be describing a"
                            + " different fault");
        }
    }

    /** cpio: padding between entries, and the longest name it will read. */
    @Nested
    class Cpio {

        @Test
        @DisplayName("an entry after a link whose target needs padding is still found")
        void paddingAfterALinkIsStepped() throws IOException {
            /*
             * The SVR4 flavour pads every entry's data to four bytes.  A link target of five
             * characters therefore owes three bytes of padding, and a reader that does not step
             * over them starts the next header three bytes early and sees nonsense.  The real
             * .pkg this product installs happens to align, which is why nothing noticed.
             */
            byte[] cpio =
                    ArchiveFixtures.cpioNewcBytes(
                            List.of(
                                    Entry.file("real.txt", "x"),
                                    Entry.symlink("alias", "real."),
                                    Entry.file("after.txt", "found me")),
                            "070701");
            Path artefact =
                    Files.write(
                            archives.resolve("padded.pkg"),
                            ArchiveFixtures.pkgBytesWithPayload(
                                    ArchiveFixtures.gzip(cpio), "application/octet-stream"));
            ExtractionReport report =
                    extractor.extractWholeArtefact(
                            ArtefactKind.PKG_PAYLOAD, artefact, destination, "after.txt");
            assertAll(
                    () -> assertEquals(List.of("real.txt", "alias", "after.txt"), report.paths()),
                    () ->
                            assertEquals(
                                    "found me",
                                    Files.readString(destination.resolve("after.txt"))));
        }

        @Test
        @DisplayName("a name of exactly the longest this reader reads is read, not refused")
        void aNameOfExactlyTheLimit() throws IOException {
            byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
            byte[] field =
                    String.format("%06o", MAX_NAME_BYTES).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(field, 0, cpio, 59, 6);
            ExtractionRejectedException rejection =
                    refuse(
                            ArtefactKind.PKG_PAYLOAD,
                            ArchiveFixtures.pkgBytesWithPayload(
                                    ArchiveFixtures.gzip(cpio), "application/octet-stream"),
                            "exactname.pkg");
            assertTrue(
                    rejection.getMessage().contains("an entry name declares 65536 bytes and only"),
                    () ->
                            "exactly the limit is within the limit, so the refusal must come from"
                                    + " the archive being too short rather than from the ceiling: "
                                    + rejection.getMessage());
        }
    }

    /** zip: the two tables, their sizes, and the offsets they point at. */
    @Nested
    class Zip {

        @Test
        @DisplayName("a central directory of exactly the largest this reader holds is read")
        void aCentralDirectoryOfExactlyTheLimit() throws IOException {
            byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("tool.bin", "payload")));
            int endOffset = zip.length - 22;
            ByteBuffer.wrap(zip)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(endOffset + 12, MAX_CENTRAL_DIRECTORY_BYTES);
            ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "exactcd.zip");
            assertTrue(
                    rejection.getMessage().contains("which its own tables point at"),
                    () ->
                            "exactly the limit is within the limit, so this must fail on the file"
                                    + " ending rather than on the ceiling: "
                                    + rejection.getMessage());
        }

        @Test
        @DisplayName("a Zip64 locator sitting at the very start of the file is still read")
        void aZip64LocatorAtOffsetZero() throws IOException {
            /*
             * The locator is looked for twenty bytes before the end record.  Here the end record
             * begins at offset twenty, so the locator begins at zero -- the exact value that tells
             * "there is no room for a locator" apart from "the locator is right at the start".
             */
            ByteBuffer file = ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN);
            file.putInt(0x07064b50);
            file.putInt(0);
            file.putLong(0);
            file.putInt(1);
            file.putInt(0x06054b50);
            file.putShort((short) 0);
            file.putShort((short) 0);
            file.putShort((short) 0xFFFF);
            file.putShort((short) 0xFFFF);
            file.putInt(-1);
            file.putInt(-1);
            file.putShort((short) 0);
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.ZIP, file.array(), "locatorat0.zip");
            assertEquals(
                    "the artefact \"locatorat0.zip\" was rejected because its container structure"
                            + " is not readable -- it ends before offset 56, which its own"
                            + " tables point at",
                    rejection.getMessage(),
                    "the locator at offset zero is READ: it says the Zip64 record lives at the"
                            + " start of the file, so the reader goes there and runs out of file."
                            + " A reader that treated offset zero as having no room for a locator"
                            + " would refuse with a different sentence entirely.");
        }

        @Test
        @DisplayName("a central directory that ends exactly where its last record does is read")
        void aCentralDirectoryEndingExactlyOnItsLastRecord() throws IOException {
            /*
             * One record, no name, no extra field, no comment: the directory is exactly the
             * forty-six bytes of the header, so the check for "is there room for another header"
             * lands on its own boundary.  The entry is then refused for having no name, which is
             * the reader getting far enough to see it rather than deciding the table was short.
             */
            byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("", "payload")), true, "");
            ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "emptyname.zip");
            assertEquals(RejectionReason.ENTRY_NAME_EMPTY, rejection.reason());
        }

        @Test
        @DisplayName("a table pointing past the end of the file says which offset it pointed at")
        void aTablePointingPastTheEnd() throws IOException {
            byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("tool.bin", "payload")));
            int endOffset = zip.length - 22;
            ByteBuffer buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
            int directorySize = buffer.getInt(endOffset + 12);
            buffer.putInt(endOffset + 16, zip.length - directorySize + 8);
            ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "pastend.zip");
            assertEquals(
                    "the artefact \"pastend.zip\" was rejected because its container structure is"
                            + " not readable -- it ends before offset "
                            + (zip.length + 8)
                            + ", which its own tables point at",
                    rejection.getMessage(),
                    "the offset in the message is the sum of where the read started and how much"
                            + " it wanted; a message that stated either alone would send a reader"
                            + " to the wrong place");
        }
    }

    /** xar: how large a table of contents this reader will inflate. */
    @Nested
    class Xar {

        @Test
        @DisplayName("an uncompressed table of contents of exactly the limit is inflated")
        void anUncompressedTocOfExactlyTheLimit() throws IOException {
            byte[] pkg = ArchiveFixtures.pkgBytes(List.of(Entry.file("a.txt", "a")));
            ByteBuffer.wrap(pkg).order(ByteOrder.BIG_ENDIAN).putLong(16, MAX_TOC_BYTES);
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.PKG_PAYLOAD, pkg, "exacttocu.pkg");
            assertTrue(
                    rejection.getMessage().contains("bytes and inflates to"),
                    () ->
                            "exactly the limit is within the limit, so this must fail on the"
                                    + " declared length not matching: "
                                    + rejection.getMessage());
        }

        @Test
        @DisplayName("a compressed table of contents of exactly the limit is read")
        void aCompressedTocOfExactlyTheLimit() throws IOException {
            byte[] pkg = ArchiveFixtures.pkgBytes(List.of(Entry.file("a.txt", "a")));
            ByteBuffer.wrap(pkg).order(ByteOrder.BIG_ENDIAN).putLong(8, MAX_TOC_BYTES);
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.PKG_PAYLOAD, pkg, "exacttocc.pkg");
            assertTrue(
                    !rejection.getMessage().contains("this reader inflates at most"),
                    () ->
                            "exactly the limit is within the limit, so the ceiling must not be"
                                    + " what refused it: "
                                    + rejection.getMessage());
        }
    }
}
