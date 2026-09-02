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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shapes each container format has that the manifest's own artefacts do not happen to use.
 *
 * <p>Upstream can repackage. A {@code .deb} whose payload is an uncompressed {@code data.tar}, a
 * {@code .pkg} whose cpio is the SVR4 flavour rather than the old ASCII one, a tar with a name
 * longer than a hundred bytes -- none of these appear in the twenty-four artefacts this project
 * mirrors today, and every one of them is a shape a released tool could take tomorrow. Code that
 * has never seen its own format's variants is code that will meet them in a scientist's install.
 */
class ReaderVariantsTest {

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

    /** tar: the GNU and ustar shapes a hundred-byte name field forced into the format. */
    @Nested
    class Tar {

        @Test
        @DisplayName("a GNU long name and long link target are read from their pseudo-entries")
        void gnuLongNames() throws IOException {
            String longName = "deep/" + "x".repeat(120) + "/tool.bin";
            byte[] tar =
                    ArchiveFixtures.tarBytesRaw(
                            List.of(
                                    new String[] {"././@LongLink", "L", "", ""},
                                    new String[] {"ignored", "0", "", ""},
                                    new String[] {"././@LongLink", "K", "", ""},
                                    new String[] {"alias", "2", "ignored", ""}),
                            List.of(
                                    longName.getBytes(StandardCharsets.UTF_8),
                                    "payload".getBytes(StandardCharsets.UTF_8),
                                    longName.getBytes(StandardCharsets.UTF_8),
                                    new byte[0]));
            ExtractionReport report =
                    unpack(ArtefactKind.TAR_GZ, ArchiveFixtures.gzip(tar), "long.tar.gz", longName);
            assertAll(
                    () ->
                            assertEquals(
                                    "[" + longName + ", alias]",
                                    report.paths().toString(),
                                    "the long name must arrive whole and with nothing added: a"
                                            + " trailing byte kept from the pseudo-entry would"
                                            + " install the tool at a path nothing else names"),
                    () -> assertEquals("payload", Files.readString(destination.resolve(longName))),
                    () ->
                            assertEquals(
                                    Path.of(longName),
                                    Files.readSymbolicLink(destination.resolve("alias")),
                                    "the long-link pseudo-entry gives the link its target"));
        }

        @Test
        @DisplayName("a ustar prefix and a name are joined with a slash")
        void ustarPrefix() throws IOException {
            byte[] tar =
                    ArchiveFixtures.tarBytesRaw(
                            List.<String[]>of(new String[] {"tool.bin", "0", "", "opt/cometgui"}),
                            List.of("payload".getBytes(StandardCharsets.UTF_8)));
            ExtractionReport report =
                    unpack(
                            ArtefactKind.TAR_GZ,
                            ArchiveFixtures.gzip(tar),
                            "prefix.tar.gz",
                            "opt/cometgui/tool.bin");
            assertEquals(List.of("opt/cometgui/tool.bin"), report.paths());
        }

        @Test
        @DisplayName("pax headers are stepped over rather than unpacked")
        void paxHeadersAreSkipped() throws IOException {
            byte[] tar =
                    ArchiveFixtures.tarBytesRaw(
                            List.of(
                                    new String[] {"PaxHeaders/tool", "x", "", ""},
                                    new String[] {"PaxHeaders.0/global", "g", "", ""},
                                    new String[] {"tool.bin", "0", "", ""}),
                            List.of(
                                    "30 mtime=1700000000.0\n".getBytes(StandardCharsets.UTF_8),
                                    "20 comment=hello\n".getBytes(StandardCharsets.UTF_8),
                                    "payload".getBytes(StandardCharsets.UTF_8)));
            ExtractionReport report =
                    unpack(
                            ArtefactKind.TAR_GZ,
                            ArchiveFixtures.gzip(tar),
                            "pax.tar.gz",
                            "tool.bin");
            assertAll(
                    () -> assertEquals(List.of("tool.bin"), report.paths()),
                    () ->
                            assertEquals(
                                    1,
                                    report.entriesRead(),
                                    "a pax header is metadata, not an entry to count"));
        }

        @Test
        @DisplayName("a hard link is refused: a second name for a file that may be anywhere")
        void hardLinksAreRefused() throws IOException {
            byte[] tar =
                    ArchiveFixtures.tarBytesRaw(
                            List.of(
                                    new String[] {"a.txt", "0", "", ""},
                                    new String[] {"b.txt", "1", "/etc/passwd", ""}),
                            List.of("payload".getBytes(StandardCharsets.UTF_8), new byte[0]));
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.TAR_GZ, ArchiveFixtures.gzip(tar), "hard.tar.gz");
            assertAll(
                    () -> assertEquals(RejectionReason.UNSUPPORTED_ENTRY_TYPE, rejection.reason()),
                    () -> assertEquals("b.txt", rejection.subject()));
        }

        @Test
        @DisplayName("a header field that is not octal is refused, quoting what was there")
        void aBadOctalFieldIsRefused() throws IOException {
            byte[] tar = ArchiveFixtures.tarBytes(List.of(Entry.file("a.txt", "abc")));
            System.arraycopy("99z9".getBytes(StandardCharsets.US_ASCII), 0, tar, 124, 4);
            fixTarChecksum(tar);
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.TAR_GZ, ArchiveFixtures.gzip(tar), "octal.tar.gz");
            assertAll(
                    () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                    () ->
                            assertTrue(
                                    rejection
                                            .getMessage()
                                            .endsWith(
                                                    "-- a header block holds \"99z90000003\" where"
                                                            + " an octal number belongs"),
                                    () -> "wrong message: " + rejection.getMessage()));
        }

        @Test
        @DisplayName("a header block cut short is refused, saying how many bytes were there")
        void aShortHeaderBlockIsRefused() throws IOException {
            byte[] tar = ArchiveFixtures.tarBytes(List.of(Entry.file("a.txt", "abc")));
            byte[] cut = Arrays.copyOf(tar, 1100);
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.TAR_GZ, ArchiveFixtures.gzip(cut), "short.tar.gz");
            assertAll(
                    () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                    () ->
                            assertTrue(
                                    rejection
                                            .getMessage()
                                            .endsWith(
                                                    "-- a header block is 76 bytes long and every"
                                                            + " block is 512"),
                                    () -> "wrong message: " + rejection.getMessage()));
        }

        private void fixTarChecksum(byte[] tar) {
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
    }

    /** cpio: the two SVR4 flavours, and the ways a header can stop making sense. */
    @Nested
    class Cpio {

        @Test
        @DisplayName("the SVR4 \"newc\" flavour is read, with its four-byte padding")
        void newcFlavour() throws IOException {
            byte[] cpio =
                    ArchiveFixtures.cpioNewcBytes(
                            List.of(
                                    Entry.directory("opt"),
                                    Entry.file("opt/tool.bin", "payload"),
                                    Entry.symlink("opt/alias", "tool.bin")),
                            "070701");
            ExtractionReport report =
                    unpack(
                            ArtefactKind.PKG_PAYLOAD,
                            ArchiveFixtures.pkgBytesWithPayload(
                                    ArchiveFixtures.gzip(cpio), "application/octet-stream"),
                            "newc.pkg",
                            "opt/tool.bin");
            assertAll(
                    () -> assertEquals(List.of("opt", "opt/tool.bin", "opt/alias"), report.paths()),
                    () ->
                            assertEquals(
                                    "payload",
                                    Files.readString(destination.resolve("opt/tool.bin"))));
        }

        @Test
        @DisplayName("the SVR4 checksum flavour is read the same way")
        void crcFlavour() throws IOException {
            byte[] cpio =
                    ArchiveFixtures.cpioNewcBytes(
                            List.of(Entry.file("tool.bin", "payload")), "070702");
            ExtractionReport report =
                    unpack(
                            ArtefactKind.PKG_PAYLOAD,
                            ArchiveFixtures.pkgBytesWithPayload(
                                    ArchiveFixtures.gzip(cpio), "application/octet-stream"),
                            "crc.pkg",
                            "tool.bin");
            assertEquals(List.of("tool.bin"), report.paths());
        }

        @Test
        @DisplayName("a cpio header cut off part way is refused, saying where it stopped")
        void aTruncatedHeaderIsRefused() throws IOException {
            byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
            byte[] cut = Arrays.copyOf(cpio, 40);
            ExtractionRejectedException rejection =
                    refuse(
                            ArtefactKind.PKG_PAYLOAD,
                            ArchiveFixtures.pkgBytesWithPayload(
                                    ArchiveFixtures.gzip(cut), "application/octet-stream"),
                            "cutcpio.pkg");
            assertAll(
                    () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                    () ->
                            assertTrue(
                                    rejection
                                            .getMessage()
                                            .endsWith(
                                                    "-- a 070707 header is cut off after 40 bytes,"
                                                            + " so the archive is truncated"),
                                    () -> "wrong message: " + rejection.getMessage()));
        }

        @Test
        @DisplayName("a cpio header field that is not a number is refused, quoting it")
        void aBadNumericFieldIsRefused() throws IOException {
            byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
            System.arraycopy("zzzzzz".getBytes(StandardCharsets.US_ASCII), 0, cpio, 18, 6);
            ExtractionRejectedException rejection =
                    refuse(
                            ArtefactKind.PKG_PAYLOAD,
                            ArchiveFixtures.pkgBytesWithPayload(
                                    ArchiveFixtures.gzip(cpio), "application/octet-stream"),
                            "badnum.pkg");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- a 070707 header holds \"zzzzzz\" where a base-8 number"
                                            + " belongs"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("an entry name longer than this reader will read is refused by its length")
        void anAbsurdNameLengthIsRefused() throws IOException {
            byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
            System.arraycopy("777777".getBytes(StandardCharsets.US_ASCII), 0, cpio, 59, 6);
            ExtractionRejectedException rejection =
                    refuse(
                            ArtefactKind.PKG_PAYLOAD,
                            ArchiveFixtures.pkgBytesWithPayload(
                                    ArchiveFixtures.gzip(cpio), "application/octet-stream"),
                            "hugename.pkg");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- an entry name declares 262143 bytes and this reader"
                                            + " reads at most 65536"),
                    () -> "wrong message: " + rejection.getMessage());
        }
    }

    /** {@code ar}: the header shapes a Debian package can legitimately take. */
    @Nested
    class Ar {

        @Test
        @DisplayName("an uncompressed data.tar payload is read")
        void anUncompressedPayload() throws IOException {
            byte[] deb =
                    ArchiveFixtures.debBytesWithPayload(
                            "data.tar",
                            ArchiveFixtures.tarBytes(List.of(Entry.file("tool.bin", "payload"))));
            ExtractionReport report =
                    unpack(ArtefactKind.DEB_PAYLOAD, deb, "plain.deb", "tool.bin");
            assertEquals(List.of("tool.bin"), report.paths());
        }

        @Test
        @DisplayName(
                "an odd-length member is followed by its pad byte, so the next header lines up")
        void anOddLengthMemberIsPadded() throws IOException {
            byte[] payload =
                    ArchiveFixtures.gzip(
                            ArchiveFixtures.tarBytes(List.of(Entry.file("tool.bin", "payload"))));
            byte[] deb = ArchiveFixtures.debBytesWithPayload("data.tar.gz", payload);
            ExtractionReport report = unpack(ArtefactKind.DEB_PAYLOAD, deb, "odd.deb", "tool.bin");
            assertEquals(List.of("tool.bin"), report.paths());
        }

        @Test
        @DisplayName("a member header that does not end in the ar magic is refused, quoting it")
        void aBadMemberTrailerIsRefused() throws IOException {
            byte[] deb =
                    ArchiveFixtures.debBytesWithPayload("data.tar.gz", new byte[] {1, 2, 3, 4});
            deb[8 + 58] = 'Z';
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.DEB_PAYLOAD, deb, "bad.deb");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- a member header ends \"Z\\n\" where \"`\\n\" belongs, so it"
                                            + " is not readable as ar"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a member whose declared length is not a number is refused, naming the member")
        void aBadMemberLengthIsRefused() throws IOException {
            byte[] deb =
                    ArchiveFixtures.debBytesWithPayload("data.tar.gz", new byte[] {1, 2, 3, 4});
            System.arraycopy("not a size".getBytes(StandardCharsets.US_ASCII), 0, deb, 8 + 48, 10);
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.DEB_PAYLOAD, deb, "badsize.deb");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- member \"debian-binary\" declares its length as \"not a"
                                            + " size\", which is not a number"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("an ar archive that ends in the middle of a member header is refused")
        void aCutOffMemberHeaderIsRefused() throws IOException {
            byte[] deb =
                    ArchiveFixtures.debBytesWithPayload("data.tar.gz", new byte[] {1, 2, 3, 4});
            byte[] cut = Arrays.copyOf(deb, 8 + 20);
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.DEB_PAYLOAD, cut, "cut.deb");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith("-- a member header is cut off after 20 of its 60 bytes"),
                    () -> "wrong message: " + rejection.getMessage());
        }
    }

    /** {@code xar}: the table of contents, which is XML from a file nobody here wrote. */
    @Nested
    class Xar {

        @Test
        @DisplayName("a blob stored as zlib is inflated, which is the other encoding xar uses")
        void aZlibEncodedBlob() throws IOException {
            byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
            byte[] blob = ArchiveFixtures.zlib(ArchiveFixtures.gzip(cpio));
            ExtractionReport report =
                    unpack(
                            ArtefactKind.PKG_PAYLOAD,
                            ArchiveFixtures.pkgBytesWithPayload(blob, "application/x-gzip"),
                            "zlib.pkg",
                            "tool.bin");
            assertEquals(List.of("tool.bin"), report.paths());
        }

        @Test
        @DisplayName("a package with no Payload blob is refused, saying what is missing")
        void noPayloadBlob() throws IOException {
            byte[] pkg =
                    ArchiveFixtures.pkgBytesWithToc(
                            "<xar><toc><file id=\"1\"><name>Bom</name><type>file</type>"
                                    + "<data><offset>0</offset><length>1</length></data>"
                                    + "</file></toc></xar>",
                            new byte[] {1});
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.PKG_PAYLOAD, pkg, "nopayload.pkg");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- its table of contents lists no \"Payload\" blob, so the"
                                            + " package carries no installed file tree"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a Payload entry with no data element is refused")
        void aPayloadWithNoData() throws IOException {
            byte[] pkg =
                    ArchiveFixtures.pkgBytesWithToc(
                            "<xar><toc><file id=\"1\"><name>Payload</name><type>file</type>"
                                    + "</file></toc></xar>",
                            new byte[] {1});
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.PKG_PAYLOAD, pkg, "nodata.pkg");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- its \"Payload\" entry has no <data> element, so there is"
                                            + " nothing in the heap to read"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a Payload offset that is not a number is refused, quoting it")
        void aPayloadWithABadOffset() throws IOException {
            byte[] pkg =
                    ArchiveFixtures.pkgBytesWithToc(
                            "<xar><toc><file id=\"1\"><name>Payload</name><type>file</type>"
                                    + "<data><offset>soon</offset><length>1</length></data>"
                                    + "</file></toc></xar>",
                            new byte[] {1});
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.PKG_PAYLOAD, pkg, "badoffset.pkg");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- its \"Payload\" entry gives <offset> as \"soon\", which is"
                                            + " not a number"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a table of contents that is not XML is refused, with the parser's reason")
        void aTocThatIsNotXml() throws IOException {
            byte[] pkg = ArchiveFixtures.pkgBytesWithToc("<xar><toc>", new byte[] {1});
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.PKG_PAYLOAD, pkg, "notxml.pkg");
            assertAll(
                    () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                    () ->
                            assertTrue(
                                    rejection
                                            .getMessage()
                                            .contains(
                                                    "table of contents is not readable as"
                                                            + " XML:"),
                                    () -> "wrong message: " + rejection.getMessage()));
        }

        @Test
        @DisplayName("a package whose header is cut short is refused before anything is inflated")
        void aCutOffHeaderIsRefused() throws IOException {
            byte[] pkg = ArchiveFixtures.pkgBytes(List.of(Entry.file("a.txt", "a")));
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.PKG_PAYLOAD, Arrays.copyOf(pkg, 12), "cuthdr.pkg");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith("-- it ends before offset 28, where its header belongs"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a table of contents that lies about its inflated length is refused")
        void aTocLengthMismatch() throws IOException {
            byte[] pkg = ArchiveFixtures.pkgBytes(List.of(Entry.file("a.txt", "a")));
            pkg[23] = (byte) (pkg[23] + 1);
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.PKG_PAYLOAD, pkg, "toclie.pkg");
            assertTrue(
                    rejection.getMessage().contains("its table of contents declares "),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a table of contents too large to inflate is refused by its declared length")
        void anAbsurdTocLength() throws IOException {
            byte[] pkg = ArchiveFixtures.pkgBytes(List.of(Entry.file("a.txt", "a")));
            pkg[19] = 0x7F;
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.PKG_PAYLOAD, pkg, "hugetoc.pkg");
            assertTrue(
                    rejection.getMessage().contains("and this reader inflates at most 8388608"),
                    () -> "wrong message: " + rejection.getMessage());
        }
    }

    /** zip: the central directory, which is where a zip keeps what its stream does not say. */
    @Nested
    class Zip {

        @Test
        @DisplayName("a directory recorded only in its Unix mode is created as a directory")
        void aDirectoryWithoutATrailingSlash() throws IOException {
            byte[] zip =
                    ArchiveFixtures.zipBytes(
                            List.of(
                                    new ArchiveFixtures.Entry(
                                            "opt",
                                            new byte[0],
                                            ArchiveFixtures.MODE_DIRECTORY,
                                            "",
                                            -1L),
                                    Entry.file("opt/tool.bin", "payload")),
                            false);
            ExtractionReport report = unpack(ArtefactKind.ZIP, zip, "dirmode.zip", "opt/tool.bin");
            assertAll(
                    () ->
                            assertEquals(
                                    new PlacedFile("opt", ArchiveEntryType.DIRECTORY, 0L),
                                    report.placed().get(0)),
                    () -> assertTrue(Files.isDirectory(destination.resolve("opt"))));
        }

        @Test
        @DisplayName("a central directory that lists a name twice is refused before any entry")
        void aDuplicateInTheCentralDirectory() throws IOException {
            byte[] zip =
                    ArchiveFixtures.zipBytes(
                            List.of(Entry.file("a.txt", "one"), Entry.file("a.txt", "two")));
            ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "dupcd.zip");
            assertAll(
                    () -> assertEquals(RejectionReason.DUPLICATE_ENTRY_NAME, rejection.reason()),
                    () ->
                            assertEquals(
                                    "the archive entry \"a.txt\" was rejected because the archive"
                                            + " names it twice, so one of the two would be written"
                                            + " and the other lost, with the order of the archive"
                                            + " deciding which -- the central directory lists it"
                                            + " twice",
                                    rejection.getMessage()));
        }

        @Test
        @DisplayName("a central directory that promises more records than it holds is refused")
        void aCentralDirectoryThatStopsShort() throws IOException {
            byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("a.txt", "one")));
            int endOffset = zip.length - 22;
            zip[endOffset + 8] = 3;
            zip[endOffset + 10] = 3;
            ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "shortcd.zip");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- its central directory declares 3 entries and record 2 is"
                                            + " not there"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName(
                "a Zip64 archive is read: its locator, its record and its per-entry extra field")
        void aZip64Archive() throws IOException {
            byte[] zip =
                    ArchiveFixtures.zip64Bytes(
                            List.of(
                                    Entry.file("tool.bin", "payload"),
                                    Entry.file("readme.txt", "notes")));
            ExtractionReport report = unpack(ArtefactKind.ZIP, zip, "zip64ok.zip", "tool.bin");
            assertAll(
                    () -> assertEquals(List.of("tool.bin", "readme.txt"), report.paths()),
                    () -> assertEquals(12L, report.expandedBytes()),
                    () ->
                            assertEquals(
                                    "payload", Files.readString(destination.resolve("tool.bin"))));
        }

        @Test
        @DisplayName("a Zip64 locator pointing somewhere with no Zip64 record is refused")
        void aZip64LocatorPointingAtNothing() throws IOException {
            byte[] zip = ArchiveFixtures.zip64Bytes(List.of(Entry.file("tool.bin", "payload")));
            int locatorOffset = zip.length - 22 - 20;
            zip[locatorOffset + 8] = 4;
            ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "zip64lost.zip");
            assertTrue(
                    rejection
                            .getMessage()
                            .contains(
                                    "its Zip64 locator points at offset 4, where there is no Zip64"
                                            + " end-of-central-directory record"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a Zip64 size sentinel with no Zip64 extra field behind it is refused")
        void aZip64SizeWithNoExtraField() throws IOException {
            byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("tool.bin", "payload")));
            int endOffset = zip.length - 22;
            int directoryOffset =
                    (zip[endOffset + 16] & 0xFF)
                            | (zip[endOffset + 17] & 0xFF) << 8
                            | (zip[endOffset + 18] & 0xFF) << 16
                            | (zip[endOffset + 19] & 0xFF) << 24;
            for (int index = 24; index < 28; index++) {
                zip[directoryOffset + index] = (byte) 0xFF;
            }
            ExtractionRejectedException rejection =
                    refuse(ArtefactKind.ZIP, zip, "zip64noextra.zip");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- entry \"tool.bin\" declares the Zip64 size sentinel"
                                            + " and carries no Zip64 extra field holding the real"
                                            + " size"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a central directory larger than this reader will hold is refused")
        void anAbsurdCentralDirectory() throws IOException {
            byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("tool.bin", "payload")));
            int endOffset = zip.length - 22;
            zip[endOffset + 15] = 0x40;
            ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "hugecd.zip");
            assertTrue(
                    rejection.getMessage().contains("bytes and this reader holds at most 67108864"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a central directory that starts past the end of the file is refused")
        void aCentralDirectoryPastTheEnd() throws IOException {
            byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("tool.bin", "payload")));
            int endOffset = zip.length - 22;
            zip[endOffset + 16] = (byte) (zip.length & 0xFF);
            zip[endOffset + 17] = (byte) ((zip.length >>> 8) & 0xFF);
            ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "pastend.zip");
            assertTrue(
                    rejection.getMessage().contains("which its own tables point at"),
                    () -> "wrong message: " + rejection.getMessage());
        }

        @Test
        @DisplayName("a Zip64 sentinel with no Zip64 locator behind it is refused")
        void aZip64SentinelWithNoLocator() throws IOException {
            byte[] zip = ArchiveFixtures.zipBytes(List.of(Entry.file("a.txt", "one")));
            int endOffset = zip.length - 22;
            for (int index = 12; index < 16; index++) {
                zip[endOffset + index] = (byte) 0xFF;
            }
            ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, zip, "zip64.zip");
            assertTrue(
                    rejection
                            .getMessage()
                            .endsWith(
                                    "-- its end-of-central-directory record uses the Zip64 sentinel"
                                            + " values and no Zip64 locator precedes it"),
                    () -> "wrong message: " + rejection.getMessage());
        }
    }
}
