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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What each container reader does with bytes that are not the container it expects.
 *
 * <p>Two families. A <strong>truncated</strong> artefact -- the shape a failed download leaves --
 * must be refused rather than half-unpacked; the specification's installation test list names it
 * outright. And a container using a <strong>codec the Java runtime has no decoder for</strong> must
 * be refused by name: this project adds no dependency to gain one, and a wrong guess about a
 * container is worse than a clear refusal.
 */
class MalformedArchiveTest {

    @TempDir private Path work;

    private Path archives;

    private Path destination;

    private final ArtefactExtractor extractor = new ArtefactExtractor();

    static List<ArtefactKind> multiEntryKinds() {
        return ArchiveFixtures.MULTI_ENTRY_KINDS;
    }

    @BeforeEach
    void createTree() throws IOException {
        archives = Files.createDirectories(work.resolve("archives"));
        destination = Files.createDirectories(work.resolve("dest"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a truncated artefact is refused, whatever the container")
    void truncatedArtefact(ArtefactKind kind) throws IOException {
        Path whole =
                ArchiveFixtures.build(
                        kind,
                        archives,
                        "whole-" + kind.id(),
                        List.of(
                                Entry.file("a.bin", ArchiveFixtures.compressible(60_000)),
                                Entry.file("b.bin", ArchiveFixtures.compressible(60_000))));
        byte[] bytes = Files.readAllBytes(whole);
        Path cut =
                Files.write(
                        archives.resolve("cut-" + kind.id()),
                        Arrays.copyOf(bytes, bytes.length / 2));
        IOException failure =
                assertThrows(
                        IOException.class,
                        () -> extractor.extractWholeArtefact(kind, cut, destination, "a.bin"),
                        () -> kind + " accepted an artefact that stops half way through");
        assertTrue(
                failure.getMessage() != null && !failure.getMessage().isBlank(),
                () -> kind + " refused a truncated artefact with no message at all");
    }

    @Test
    @DisplayName("a zip with no end-of-central-directory record is refused")
    void aZipWithNoEndRecord() throws IOException {
        Path artefact =
                Files.write(
                        archives.resolve("not.zip"),
                        "PK and then nothing at all".getBytes(StandardCharsets.UTF_8));
        ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, artefact);
        assertAll(
                () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                () ->
                        assertEquals(
                                "the artefact \"not.zip\" was rejected because its container"
                                        + " structure is not readable -- it has no"
                                        + " end-of-central-directory record, so it is not a zip"
                                        + " archive or it was truncated",
                                rejection.getMessage()));
    }

    @Test
    @DisplayName("a zip whose two tables describe different archives is refused")
    void aZipWhoseTablesDisagree() throws IOException {
        Path artefact =
                ArchiveFixtures.build(
                        ArtefactKind.ZIP,
                        archives,
                        "confused.zip",
                        List.of(Entry.file("a.txt", "a"), Entry.file("b.txt", "b")));
        byte[] bytes = Files.readAllBytes(artefact);
        /*
         * The end record's two "total entries" fields sit at offsets 8 and 10 of its 22 bytes.
         * Telling the reader there is one entry while the stream carries two is the zip-confusion
         * shape: two tables, two answers, and a consumer that sees whichever one its reader used.
         */
        int endOffset = bytes.length - 22;
        bytes[endOffset + 8] = 1;
        bytes[endOffset + 10] = 1;
        Path confused = Files.write(archives.resolve("confused-patched.zip"), bytes);
        ExtractionRejectedException rejection = refuse(ArtefactKind.ZIP, confused);
        String expected =
                "the artefact \"confused-patched.zip\" was rejected because its container"
                        + " structure is not readable -- the entry stream carries \"b.txt\" and"
                        + " the central directory does not, so the archive's two tables describe"
                        + " different contents";
        assertAll(
                () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                () -> assertEquals(expected, rejection.getMessage()));
    }

    @Test
    @DisplayName("a tar header whose checksum does not match the bytes is refused")
    void aTarWithABadChecksum() throws IOException {
        byte[] tar = ArchiveFixtures.tarBytes(List.of(Entry.file("a.txt", "abc")));
        tar[0] = 'X';
        Path artefact = Files.write(archives.resolve("bad.tar.gz"), ArchiveFixtures.gzip(tar));
        ExtractionRejectedException rejection = refuse(ArtefactKind.TAR_GZ, artefact);
        String opening =
                "the artefact \"bad.tar.gz\" was rejected because its container structure is not"
                        + " readable -- a header block's stored checksum is ";
        String closing = ", so the archive is truncated, spliced or not a tar at all";
        assertAll(
                () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                () ->
                        assertTrue(
                                rejection.getMessage().startsWith(opening),
                                () -> "wrong message: " + rejection.getMessage()),
                () ->
                        assertTrue(
                                rejection.getMessage().endsWith(closing),
                                () -> "wrong message: " + rejection.getMessage()));
    }

    @Test
    @DisplayName("a Debian package that is not an ar archive is refused")
    void aDebThatIsNotAnArArchive() throws IOException {
        Path artefact =
                Files.write(
                        archives.resolve("fake.deb"),
                        "definitely not a package".getBytes(StandardCharsets.UTF_8));
        ExtractionRejectedException rejection = refuse(ArtefactKind.DEB_PAYLOAD, artefact);
        assertEquals(
                "the artefact \"fake.deb\" was rejected because its container structure is not"
                        + " readable -- it does not begin \"!<arch>\", so it is not a Debian"
                        + " package at all",
                rejection.getMessage());
    }

    @Test
    @DisplayName("a Debian package with no data member names the members it does have")
    void aDebWithNoPayload() throws IOException {
        byte[] bytes = ArchiveFixtures.debBytesWithPayload("nothing.bin", new byte[] {1, 2, 3});
        Path artefact = Files.write(archives.resolve("empty.deb"), bytes);
        ExtractionRejectedException rejection = refuse(ArtefactKind.DEB_PAYLOAD, artefact);
        assertEquals(
                "the artefact \"empty.deb\" was rejected because its container structure is not"
                        + " readable -- it holds no \"data.tar\" member; its members are"
                        + " [debian-binary, control.tar.gz, nothing.bin], and the payload is the"
                        + " only one this reader wants",
                rejection.getMessage());
    }

    @Test
    @DisplayName("a Debian payload compressed with xz is refused by name, not half-read")
    void aDebCompressedWithXz() throws IOException {
        byte[] bytes = ArchiveFixtures.debBytesWithPayload("data.tar.xz", new byte[] {(byte) 0xFD});
        Path artefact = Files.write(archives.resolve("xz.deb"), bytes);
        ExtractionRejectedException rejection = refuse(ArtefactKind.DEB_PAYLOAD, artefact);
        String expected =
                "the artefact \"xz.deb\" was rejected because it is compressed with a codec the"
                        + " Java runtime cannot decode, and this project adds no dependency to gain"
                        + " one -- its payload member is \"data.tar.xz\", and this extractor"
                        + " decodes \"data.tar\" and \"data.tar.gz\" only, because the Java"
                        + " runtime ships no xz, zstd or bzip2 decoder and this project adds no"
                        + " dependency to gain one";
        assertAll(
                () -> assertEquals(RejectionReason.UNSUPPORTED_COMPRESSION, rejection.reason()),
                () -> assertEquals(expected, rejection.getMessage()));
    }

    @Test
    @DisplayName("a flat package that is not a xar archive is refused")
    void aPkgThatIsNotXar() throws IOException {
        Path artefact =
                Files.write(
                        archives.resolve("fake.pkg"),
                        "not!a package at all, but long enough".getBytes(StandardCharsets.UTF_8));
        ExtractionRejectedException rejection = refuse(ArtefactKind.PKG_PAYLOAD, artefact);
        assertEquals(
                "the artefact \"fake.pkg\" was rejected because its container structure is not"
                        + " readable -- it begins \"not!\" where \"xar!\" belongs, so it is not a"
                        + " macOS flat package",
                rejection.getMessage());
    }

    @Test
    @DisplayName("a flat package whose payload is neither gzip nor cpio is refused by its bytes")
    void aPkgWithAnUnreadablePayload() throws IOException {
        byte[] bytes =
                ArchiveFixtures.pkgBytesWithPayload(
                        new byte[] {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD, 0, 0},
                        "application/octet-stream");
        Path artefact = Files.write(archives.resolve("zstd.pkg"), bytes);
        ExtractionRejectedException rejection = refuse(ArtefactKind.PKG_PAYLOAD, artefact);
        String expected =
                "the artefact \"zstd.pkg\" was rejected because it is compressed with a codec the"
                        + " Java runtime cannot decode, and this project adds no dependency to gain"
                        + " one -- its payload begins with bytes 28b52ffd0000, which is neither a"
                        + " gzip stream nor a cpio archive, and this extractor decodes no other"
                        + " payload compression";
        assertAll(
                () -> assertEquals(RejectionReason.UNSUPPORTED_COMPRESSION, rejection.reason()),
                () -> assertEquals(expected, rejection.getMessage()));
    }

    @Test
    @DisplayName("a flat package whose blob is encoded with an unsupported style is refused")
    void aPkgWithAnUnsupportedEncoding() throws IOException {
        byte[] bytes =
                ArchiveFixtures.pkgBytesWithPayload(new byte[] {1, 2, 3, 4}, "application/x-bzip2");
        Path artefact = Files.write(archives.resolve("bzip2.pkg"), bytes);
        ExtractionRejectedException rejection = refuse(ArtefactKind.PKG_PAYLOAD, artefact);
        assertAll(
                () -> assertEquals(RejectionReason.UNSUPPORTED_COMPRESSION, rejection.reason()),
                () ->
                        assertTrue(
                                rejection
                                        .getMessage()
                                        .contains(
                                                "its payload is encoded as"
                                                        + " \"application/x-bzip2\""),
                                () -> "wrong message: " + rejection.getMessage()));
    }

    @Test
    @DisplayName("a cpio stream with a bad magic number is refused, naming what it found")
    void aCpioWithBadMagic() throws IOException {
        byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("a.txt", "a")));
        cpio[0] = '9';
        byte[] bytes =
                ArchiveFixtures.pkgBytesWithPayload(
                        ArchiveFixtures.gzip(cpio), "application/octet-stream");
        Path artefact = Files.write(archives.resolve("badcpio.pkg"), bytes);
        ExtractionRejectedException rejection = refuse(ArtefactKind.PKG_PAYLOAD, artefact);
        assertEquals(
                "the artefact \"badcpio.pkg\" was rejected because its container structure is not"
                        + " readable -- an entry header begins \"970707\" where a cpio magic number"
                        + " -- 070707, 070701 or 070702 -- belongs",
                rejection.getMessage());
    }

    private ExtractionRejectedException refuse(ArtefactKind kind, Path artefact) {
        return assertThrows(
                ExtractionRejectedException.class,
                () -> extractor.extractWholeArtefact(kind, artefact, destination, "a.txt"));
    }
}
