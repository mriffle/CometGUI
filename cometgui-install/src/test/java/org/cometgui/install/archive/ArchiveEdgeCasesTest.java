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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The edges each format has that a well-formed artefact never reaches.
 *
 * <p>Every case here is a branch of the readers or the guard that the manifest's own artefacts do
 * not take: a zip written on Windows, a tar with no end-of-archive block, a table of contents
 * missing an element the format says is optional. They are not hypothetical -- each is a shape some
 * archiver really produces -- and a branch nothing has ever taken is a branch nobody has checked.
 */
class ArchiveEdgeCasesTest {

    @TempDir private Path work;

    private Path archives;

    private Path destination;

    private final ArtefactExtractor extractor = new ArtefactExtractor();

    @BeforeEach
    void createTree() throws IOException {
        archives = Files.createDirectories(work.resolve("archives"));
        destination = Files.createDirectories(work.resolve("dest"));
    }

    private ExtractionReport unpack(ArtefactKind kind, byte[] bytes, String name, String expected)
            throws IOException {
        Path artefact = Files.write(archives.resolve(name), bytes);
        return extractor.extractWholeArtefact(kind, artefact, destination, expected);
    }

    private ExtractionRejectedException refuse(ArtefactKind kind, byte[] bytes, String name) {
        return assertThrows(
                ExtractionRejectedException.class, () -> unpack(kind, bytes, name, "whatever"));
    }

    @Test
    @DisplayName("a zip written on a host that records no Unix mode is read as ordinary files")
    void aZipWithNoUnixModes() throws IOException {
        Path artefact =
                ArchiveFixtures.plainZip(
                        archives,
                        "dos.zip",
                        Map.of("tool.bin", "payload", "notes/readme.txt", "notes"));
        ExtractionReport report =
                extractor.extractWholeArtefact(ArtefactKind.ZIP, artefact, destination, "tool.bin");
        assertAll(
                () -> assertEquals(2, report.entriesRead()),
                () -> assertEquals("payload", Files.readString(destination.resolve("tool.bin"))),
                () ->
                        assertEquals(
                                "notes",
                                Files.readString(destination.resolve("notes/readme.txt"))));
    }

    @Test
    @DisplayName("a Unix zip entry whose mode records no file type is read as an ordinary file")
    void aUnixZipEntryWithNoModeBits() throws IOException {
        byte[] zip =
                ArchiveFixtures.zipBytes(
                        List.of(
                                new Entry(
                                        "tool.bin",
                                        "payload".getBytes(StandardCharsets.UTF_8),
                                        0,
                                        "",
                                        -1L)));
        ExtractionReport report = unpack(ArtefactKind.ZIP, zip, "nomode.zip", "tool.bin");
        assertEquals(List.of("tool.bin"), report.paths());
    }

    @Test
    @DisplayName("a zip that is nothing but a Zip64 end record has no room for a locator")
    void aZipTooShortForALocator() throws IOException {
        ByteBuffer end = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        end.putInt(0x06054b50);
        end.putShort((short) 0);
        end.putShort((short) 0);
        end.putShort((short) 0xFFFF);
        end.putShort((short) 0xFFFF);
        end.putInt(0);
        end.putInt(0);
        end.putShort((short) 0);
        ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, end.array(), "tiny.zip");
        assertTrue(
                rejection
                        .getMessage()
                        .endsWith(
                                "-- its end-of-central-directory record uses the Zip64 sentinel"
                                        + " values and no Zip64 locator precedes it"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a central directory shorter than one record is refused")
    void aCentralDirectoryShorterThanARecord() throws IOException {
        byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("tool.bin", "payload")));
        int endOffset = zip.length - 22;
        zip[endOffset + 12] = 4;
        zip[endOffset + 13] = 0;
        ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "stub.zip");
        assertTrue(
                rejection
                        .getMessage()
                        .endsWith(
                                "-- its central directory declares 1 entries and record 1 is not"
                                        + " there"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a Zip64 extra field that is not the size field is stepped over, then refused")
    void aZip64ExtraFieldOfAnotherKind() throws IOException {
        byte[] zip = ArchiveFixtures.zip64Bytes(List.of(Entry.file("tool.bin", "payload")));
        int marker = indexOfExtraFieldHeader(zip);
        zip[marker] = (byte) 0x99;
        zip[marker + 1] = (byte) 0x99;
        ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "otherextra.zip");
        assertTrue(
                rejection
                        .getMessage()
                        .endsWith(
                                "-- entry \"tool.bin\" declares the Zip64 size sentinel and carries"
                                        + " no Zip64 extra field holding the real size"),
                () -> "wrong message: " + rejection.getMessage());
    }

    /*
     * The extra field this fixture writes is the twelve bytes immediately after the entry name in
     * the central directory: header id 0x0001, then a length of 8, then the real size.
     */
    private static int indexOfExtraFieldHeader(byte[] zip) {
        byte[] needle = "tool.bin".getBytes(StandardCharsets.UTF_8);
        for (int index = zip.length - needle.length; index >= 0; index--) {
            if (Arrays.equals(Arrays.copyOfRange(zip, index, index + needle.length), needle)
                    && (zip[index + needle.length] & 0xFF) == 0x01
                    && (zip[index + needle.length + 1] & 0xFF) == 0x00) {
                return index + needle.length;
            }
        }
        throw new AssertionError("the Zip64 fixture no longer carries an extra field to patch");
    }

    @Test
    @DisplayName("a tar that ends on a block boundary with no end-of-archive block is accepted")
    void aTarWithNoEndBlock() throws IOException {
        byte[] tar = ArchiveFixtures.tarBytes(List.of(Entry.file("tool.bin", "payload")));
        byte[] cut = Arrays.copyOf(tar, tar.length - 1024);
        ExtractionReport report =
                unpack(ArtefactKind.TAR_GZ, ArchiveFixtures.gzip(cut), "noend.tar.gz", "tool.bin");
        assertEquals(List.of("tool.bin"), report.paths());
    }

    @Test
    @DisplayName("a GNU long name terminated by a NUL is read up to the NUL")
    void aNulTerminatedLongName() throws IOException {
        String longName = "deep/" + "y".repeat(120) + "/tool.bin";
        byte[] tar =
                ArchiveFixtures.tarBytesRaw(
                        List.of(
                                new String[] {"././@LongLink", "L", "", ""},
                                new String[] {"ignored", "0", "", ""}),
                        List.of(
                                (longName + "\u0000").getBytes(StandardCharsets.UTF_8),
                                "payload".getBytes(StandardCharsets.UTF_8)));
        ExtractionReport report =
                unpack(ArtefactKind.TAR_GZ, ArchiveFixtures.gzip(tar), "nulname.tar.gz", longName);
        assertEquals(List.of(longName), report.paths());
    }

    @Test
    @DisplayName("a tar whose header checksum is the historical signed sum is accepted")
    void aSignedTarChecksum() throws IOException {
        byte[] tar = ArchiveFixtures.tarBytes(List.of(Entry.file("tool.bin", "payload")));
        /*
         * One byte with its high bit set, in the header's trailing padding, is enough to make the
         * two historical sums differ: unsigned counts it as 255 and signed as -1.  Some archivers
         * really did write the signed one, so both are accepted -- and this is the fixture that
         * proves the second half of that "or" is live.
         */
        tar[500] = (byte) 0xFF;
        for (int index = 148; index < 156; index++) {
            tar[index] = ' ';
        }
        long signed = 0;
        for (int index = 0; index < 512; index++) {
            signed += tar[index];
        }
        byte[] field = String.format("%06o", signed).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(field, 0, tar, 148, 6);
        tar[154] = 0;
        tar[155] = ' ';
        ExtractionReport report =
                unpack(ArtefactKind.TAR_GZ, ArchiveFixtures.gzip(tar), "signed.tar.gz", "tool.bin");
        assertEquals(List.of("tool.bin"), report.paths());
    }

    @Test
    @DisplayName("a tar size field of nothing but NULs is read as zero")
    void anEmptyTarSizeField() throws IOException {
        byte[] tar = ArchiveFixtures.tarBytes(List.of(Entry.file("empty.bin", "")));
        Arrays.fill(tar, 124, 136, (byte) 0);
        fixChecksum(tar);
        ExtractionReport report =
                unpack(
                        ArtefactKind.TAR_GZ,
                        ArchiveFixtures.gzip(tar),
                        "nosize.tar.gz",
                        "empty.bin");
        assertAll(
                () -> assertEquals(List.of("empty.bin"), report.paths()),
                () -> assertEquals(0L, Files.size(destination.resolve("empty.bin"))));
    }

    @Test
    @DisplayName("a tar entry that claims more bytes than the archive holds is refused")
    void aTarEntryLongerThanTheArchive() throws IOException {
        byte[] tar = ArchiveFixtures.tarBytes(List.of(Entry.file("a.bin", "payload")));
        byte[] field = String.format("%011o", 5_000_000).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(field, 0, tar, 124, 11);
        fixChecksum(tar);
        IOException failure =
                assertThrows(
                        IOException.class,
                        () ->
                                unpack(
                                        ArtefactKind.TAR_GZ,
                                        ArchiveFixtures.gzip(tar),
                                        "toolong.tar.gz",
                                        "a.bin"));
        assertTrue(
                failure.getMessage().contains("bytes still to step over")
                        || failure.getMessage().contains("delivered"),
                () -> "wrong message: " + failure.getMessage());
    }

    private static void fixChecksum(byte[] tar) {
        for (int index = 148; index < 156; index++) {
            tar[index] = ' ';
        }
        long sum = 0;
        for (int index = 0; index < 512; index++) {
            sum += tar[index] & 0xFF;
        }
        byte[] field = String.format("%06o", sum).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(field, 0, tar, 148, 6);
        tar[154] = 0;
        tar[155] = ' ';
    }

    @Test
    @DisplayName("a cpio stream that ends without its trailer is read to its last whole entry")
    void aCpioWithNoTrailer() throws IOException {
        byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
        byte[] cut = Arrays.copyOf(cpio, cpio.length - 87);
        ExtractionReport report =
                unpack(
                        ArtefactKind.PKG_PAYLOAD,
                        ArchiveFixtures.pkgBytesWithPayload(
                                ArchiveFixtures.gzip(cut), "application/octet-stream"),
                        "notrailer.pkg",
                        "tool.bin");
        assertEquals(List.of("tool.bin"), report.paths());
    }

    @Test
    @DisplayName("a cpio entry whose mode records no file type is read as an ordinary file")
    void aCpioEntryWithNoModeBits() throws IOException {
        byte[] cpio =
                ArchiveFixtures.cpioBytes(
                        List.of(
                                new Entry(
                                        "tool.bin",
                                        "payload".getBytes(StandardCharsets.UTF_8),
                                        0,
                                        "",
                                        -1L)));
        ExtractionReport report =
                unpack(
                        ArtefactKind.PKG_PAYLOAD,
                        ArchiveFixtures.pkgBytesWithPayload(
                                ArchiveFixtures.gzip(cpio), "application/octet-stream"),
                        "nomode.pkg",
                        "tool.bin");
        assertEquals(List.of("tool.bin"), report.paths());
    }

    @Test
    @DisplayName("a cpio entry that claims more bytes than are there is refused")
    void aCpioEntryLongerThanTheArchive() throws IOException {
        byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
        byte[] cut = Arrays.copyOf(cpio, cpio.length - 100);
        System.arraycopy("00000010000".getBytes(StandardCharsets.US_ASCII), 0, cut, 65, 11);
        ExtractionRejectedException rejection =
                refuse(
                        ArtefactKind.PKG_PAYLOAD,
                        ArchiveFixtures.pkgBytesWithPayload(
                                ArchiveFixtures.gzip(cut), "application/octet-stream"),
                        "shortdata.pkg");
        assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason());
    }

    @Test
    @DisplayName("a package whose payload is a bare cpio, with no compression at all, is read")
    void aRawCpioPayload() throws IOException {
        byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
        ExtractionReport report =
                unpack(
                        ArtefactKind.PKG_PAYLOAD,
                        ArchiveFixtures.pkgBytesWithPayload(cpio, "application/octet-stream"),
                        "raw.pkg",
                        "tool.bin");
        assertEquals(List.of("tool.bin"), report.paths());
    }

    @Test
    @DisplayName("a payload of one byte is refused rather than sniffed out of bounds")
    void aOneBytePayload() throws IOException {
        ExtractionRejectedException rejection =
                refuse(
                        ArtefactKind.PKG_PAYLOAD,
                        ArchiveFixtures.pkgBytesWithPayload(
                                new byte[] {0x1F}, "application/octet-stream"),
                        "onebyte.pkg");
        assertEquals(RejectionReason.UNSUPPORTED_COMPRESSION, rejection.reason());
    }

    @Test
    @DisplayName("a payload of exactly two bytes is long enough to be recognised as gzip")
    void aTwoBytePayload() throws IOException {
        /*
         * Two bytes is exactly what the gzip magic number takes, so this is the shortest payload
         * the sniff can form an opinion about; one byte shorter and it must not try.
         */
        ExtractionRejectedException rejection =
                refuse(
                        ArtefactKind.PKG_PAYLOAD,
                        ArchiveFixtures.pkgBytesWithPayload(
                                new byte[] {0x1F, (byte) 0x8B}, "application/octet-stream"),
                        "twobyte.pkg");
        assertAll(
                () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                () ->
                        assertTrue(
                                rejection
                                        .getMessage()
                                        .contains(
                                                "its payload begins with the gzip marker and is not"
                                                        + " a readable gzip stream"),
                                () ->
                                        "two bytes of gzip magic is recognised AS gzip and fails as"
                                                + " a truncated one, rather than being reported as"
                                                + " unrecognisable: "
                                                + rejection.getMessage()));
    }

    @Test
    @DisplayName("a data element with no encoding is read as stored bytes")
    void aDataElementWithNoEncoding() throws IOException {
        byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
        byte[] payload = ArchiveFixtures.gzip(cpio);
        byte[] pkg =
                ArchiveFixtures.pkgBytesWithToc(
                        "<xar><toc><file id=\"1\"><name>Payload</name><type>file</type>"
                                + "<data><offset>0</offset><length>"
                                + payload.length
                                + "</length></data></file></toc></xar>",
                        payload);
        ExtractionReport report =
                unpack(ArtefactKind.PKG_PAYLOAD, pkg, "noencoding.pkg", "tool.bin");
        assertEquals(List.of("tool.bin"), report.paths());
    }

    @Test
    @DisplayName("a data element with no length is refused, quoting the empty value")
    void aDataElementWithNoLength() throws IOException {
        byte[] pkg =
                ArchiveFixtures.pkgBytesWithToc(
                        "<xar><toc><file id=\"1\"><name>Payload</name><type>file</type>"
                                + "<data><offset>0</offset></data></file></toc></xar>",
                        new byte[] {1});
        ExtractionRejectedException rejection = refuse(ArtefactKind.PKG_PAYLOAD, pkg, "nolen.pkg");
        assertTrue(
                rejection
                        .getMessage()
                        .endsWith(
                                "-- its \"Payload\" entry gives <length> as \"\", which is not a"
                                        + " number"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a table of contents whose file elements have no name is stepped over")
    void aFileElementWithNoName() throws IOException {
        byte[] pkg =
                ArchiveFixtures.pkgBytesWithToc(
                        "<xar><toc><file id=\"1\"><type>file</type></file></toc></xar>",
                        new byte[] {1});
        ExtractionRejectedException rejection = refuse(ArtefactKind.PKG_PAYLOAD, pkg, "noname.pkg");
        assertTrue(
                rejection.getMessage().contains("lists no \"Payload\" blob"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a compressed table of contents larger than this reader will hold is refused")
    void anAbsurdCompressedTocLength() throws IOException {
        byte[] pkg = ArchiveFixtures.pkgBytes(List.of(Entry.file("a.txt", "a")));
        pkg[11] = 0x7F;
        ExtractionRejectedException rejection =
                refuse(ArtefactKind.PKG_PAYLOAD, pkg, "hugectoc.pkg");
        assertTrue(
                rejection.getMessage().contains("and this reader inflates at most 8388608"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a link target spelled with a drive letter is refused with no backslash in it")
    void aDriveLetterLinkTarget() throws IOException {
        ExtractionGuard guard =
                new ExtractionGuard(work.resolve("d"), 1024L, ExtractionLimits.defaults());
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("link", "C:/Windows")));
        assertTrue(
                rejection
                        .getMessage()
                        .endsWith(
                                "-- its target \"C:/Windows\" is spelled with a backslash or a"
                                        + " drive letter, and this extractor resolves neither"
                                        + " rather than guessing which volume was meant"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a link target with \".\" and empty segments resolves the same as one without")
    void aLinkTargetWithRedundantSegments() throws IOException {
        Path target = Files.createDirectories(work.resolve("d"));
        ExtractionGuard guard = new ExtractionGuard(target, 1024L, ExtractionLimits.defaults());
        guard.placeFileFromArchiveName(
                ArchiveEntry.file("real.txt", 5L),
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        PlacedFile link = guard.placeSymlink(ArchiveEntry.symlink("alias", ".//real.txt"));
        assertAll(
                () -> assertEquals("alias", link.path()),
                () -> assertEquals("hello", Files.readString(target.resolve("alias"))));
    }

    @Test
    @DisplayName("a window asked for no bytes returns none rather than the end of the stream")
    void aWindowAskedForNothing() throws IOException {
        BoundedEntryStream window =
                new BoundedEntryStream(
                        new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), 3);
        assertAll(
                () -> assertEquals(0, window.read(new byte[4], 0, 0)),
                () -> assertEquals(3L, window.remaining()));
    }

    @Test
    @DisplayName("a tar that stops between an entry's bytes and its padding is refused")
    void aTarThatStopsBeforeItsPadding() throws IOException {
        byte[] tar = ArchiveFixtures.tarBytes(List.of(Entry.file("tool.bin", "payload")));
        byte[] cut = Arrays.copyOf(tar, 512 + 7);
        IOException failure =
                assertThrows(
                        IOException.class,
                        () ->
                                unpack(
                                        ArtefactKind.TAR_GZ,
                                        ArchiveFixtures.gzip(cut),
                                        "nopad.tar.gz",
                                        "tool.bin"));
        assertEquals(
                "the archive ended before 505 bytes could be stepped over", failure.getMessage());
    }

    @Test
    @DisplayName("a tar name that fills its hundred-byte field with no NUL is read whole")
    void aTarNameFillingItsField() throws IOException {
        String name = "d/" + "z".repeat(98);
        byte[] tar =
                ArchiveFixtures.tarBytesRaw(
                        List.<String[]>of(new String[] {name, "0", "", ""}),
                        List.of("payload".getBytes(StandardCharsets.UTF_8)));
        ExtractionReport report =
                unpack(ArtefactKind.TAR_GZ, ArchiveFixtures.gzip(tar), "fullname.tar.gz", name);
        assertAll(
                () -> assertEquals(100, name.length()),
                () -> assertEquals(List.of(name), report.paths()));
    }

    @Test
    @DisplayName("a zip whose Zip64 sentinel is only in the directory offset is still followed")
    void aZip64SentinelInTheOffsetAlone() throws IOException {
        byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("tool.bin", "payload")));
        int endOffset = zip.length - 22;
        for (int index = 16; index < 20; index++) {
            zip[endOffset + index] = (byte) 0xFF;
        }
        ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "offsetsentinel.zip");
        assertTrue(
                rejection.getMessage().contains("no Zip64 locator precedes it"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a central directory record with the wrong signature is refused")
    void aCentralDirectoryRecordWithABadSignature() throws IOException {
        byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("tool.bin", "payload")));
        int endOffset = zip.length - 22;
        int directoryOffset =
                (zip[endOffset + 16] & 0xFF)
                        | (zip[endOffset + 17] & 0xFF) << 8
                        | (zip[endOffset + 18] & 0xFF) << 16
                        | (zip[endOffset + 19] & 0xFF) << 24;
        zip[directoryOffset] = 0x00;
        ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "badsig.zip");
        assertTrue(
                rejection.getMessage().contains("record 1 is not there"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a Zip64 extra field too short to hold a size is stepped over, then refused")
    void aZip64ExtraFieldTooShort() throws IOException {
        byte[] zip = ArchiveFixtures.zip64Bytes(List.of(Entry.file("tool.bin", "payload")));
        int marker = indexOfExtraFieldHeader(zip);
        zip[marker + 2] = 4;
        ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "shortextra.zip");
        assertTrue(
                rejection.getMessage().contains("carries no Zip64 extra field"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a payload that starts like gzip but is not is refused by its bytes")
    void aPayloadThatOnlyLooksLikeGzip() throws IOException {
        ExtractionRejectedException rejection =
                refuse(
                        ArtefactKind.PKG_PAYLOAD,
                        ArchiveFixtures.pkgBytesWithPayload(
                                new byte[] {0x1F, 0x00, 0x00, 0x00, 0x00, 0x00},
                                "application/octet-stream"),
                        "nearlygzip.pkg");
        assertTrue(
                rejection.getMessage().contains("begins with bytes 1f0000000000"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a cpio entry whose name is nothing but its terminator is refused as empty")
    void aCpioEntryWithAnEmptyName() throws IOException {
        byte[] cpio =
                ArchiveFixtures.cpioBytes(
                        List.of(
                                new Entry(
                                        "",
                                        "payload".getBytes(StandardCharsets.UTF_8),
                                        ArchiveFixtures.MODE_FILE,
                                        "",
                                        -1L)));
        ExtractionRejectedException rejection =
                refuse(
                        ArtefactKind.PKG_PAYLOAD,
                        ArchiveFixtures.pkgBytesWithPayload(
                                ArchiveFixtures.gzip(cpio), "application/octet-stream"),
                        "emptyname.pkg");
        assertEquals(RejectionReason.ENTRY_NAME_EMPTY, rejection.reason());
    }

    @Test
    @DisplayName("a window over a stream that ends early reports the end, not a byte")
    void aWindowOverAStreamThatEndsEarly() throws IOException {
        BoundedEntryStream window =
                new BoundedEntryStream(
                        new ByteArrayInputStream("a".getBytes(StandardCharsets.UTF_8)), 5);
        assertAll(
                () -> assertEquals('a', window.read()),
                () -> assertEquals(-1, window.read()),
                () -> assertEquals(4L, window.remaining()));
    }

    @Test
    @DisplayName("each of a report's three counts is refused on its own when it is negative")
    void everyReportCountIsChecked() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new ExtractionReport(List.of(), 1, -1L, 1L)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new ExtractionReport(List.of(), 1, 1L, -1L)));
    }
}
