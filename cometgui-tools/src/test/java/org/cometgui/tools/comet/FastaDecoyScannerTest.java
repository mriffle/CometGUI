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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import org.cometgui.domain.params.FastaDecoyCensus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link FastaDecoyScanner} on CONSTRUCTED FASTA files; the real proteome subset and a decoy FASTA
 * built from it are scanned by {@link CometIndexRealBinaryTest}.
 */
class FastaDecoyScannerTest {

    @TempDir private Path directory;

    private Path file(String name, String text) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static final String MIXED =
            ">sp|P1|ONE first protein\nMKWVTF\nISLL\n"
                    + ">DECOY_sp|P1|ONE first protein\nLLSIFTVWKM\n"
                    + ">sp|P2|TWO\nMAAA\n"
                    + ">DECOY_sp|P2|TWO\nAAAM\n"
                    + ">tr|Q3|DECOY_LIKE has the prefix later\nMKK\n";

    @Test
    @DisplayName("records and decoys are counted, and the first decoy is named")
    void counts() throws IOException {
        Path fasta = file("mixed.fasta", MIXED);
        assertEquals(
                new FastaDecoyCensus(fasta, "DECOY_", 5, 2, Optional.of("DECOY_sp|P1|ONE")),
                FastaDecoyScanner.scan(fasta, "DECOY_"));
    }

    @ParameterizedTest(name = "line ending {index}")
    @ValueSource(strings = {"\n", "\r\n", "\r"})
    @DisplayName("LF, CRLF and CR line endings give the same census")
    void lineEndings(String ending) throws IOException {
        Path fasta = file("endings.fasta", MIXED.replace("\n", ending));
        FastaDecoyCensus census = FastaDecoyScanner.scan(fasta, "DECOY_");
        assertEquals(5, census.records());
        assertEquals(2, census.decoyRecords());
        assertEquals(Optional.of("DECOY_sp|P1|ONE"), census.firstDecoyAccession());
    }

    @Test
    @DisplayName("a FASTA with no decoys has a census of zero and names none")
    void noDecoys() throws IOException {
        Path fasta = file("targets.fasta", ">sp|P1|ONE\nMK\n>sp|P2|TWO\nMA\n");
        assertEquals(
                new FastaDecoyCensus(fasta, "DECOY_", 2, 0, Optional.empty()),
                FastaDecoyScanner.scan(fasta, "DECOY_"));
    }

    @Test
    @DisplayName("the accession is the first token: white space after > is skipped, not part of it")
    void accessionToken() throws IOException {
        Path fasta =
                file(
                        "tokens.fasta",
                        ">  \tDECOY_spaced description\nM\n"
                                + ">xDECOY_inside\nM\n"
                                + ">\nM\n"
                                + "> \nM\n"
                                + ">DECOY_\tTabbed\nM\n");
        FastaDecoyCensus census = FastaDecoyScanner.scan(fasta, "DECOY_");
        assertEquals(5, census.records());
        assertEquals(2, census.decoyRecords());
        assertEquals(Optional.of("DECOY_spaced"), census.firstDecoyAccession());
        assertEquals("DECOY_", FastaDecoyScanner.accession(">DECOY_\tTabbed"));
        assertEquals("", FastaDecoyScanner.accession(">"));
        assertEquals("", FastaDecoyScanner.accession(">   "));
        assertEquals("a", FastaDecoyScanner.accession("> a b"));
    }

    @Test
    @DisplayName("the prefix is compared case for case, and with the project's prefix, not another")
    void caseAndPrefix() throws IOException {
        Path fasta = file("case.fasta", ">decoy_x\nM\n>DECOY_y\nM\n>REV_z\nM\n");
        assertEquals(1, FastaDecoyScanner.scan(fasta, "DECOY_").decoyRecords());
        assertEquals(1, FastaDecoyScanner.scan(fasta, "decoy_").decoyRecords());
        FastaDecoyCensus reversed = FastaDecoyScanner.scan(fasta, "REV_");
        assertEquals("REV_", reversed.prefix());
        assertEquals(Optional.of("REV_z"), reversed.firstDecoyAccession());
    }

    @Test
    @DisplayName("a non-ASCII prefix and accession are matched and reported in UTF-8")
    void utf8() throws IOException {
        Path fasta = file("utf8.fasta", ">sp|P1\nM\n>LEURRE_α|P1\nM\n");
        FastaDecoyCensus census = FastaDecoyScanner.scan(fasta, "LEURRE_α");
        assertEquals(1, census.decoyRecords());
        assertEquals(Optional.of("LEURRE_α|P1"), census.firstDecoyAccession());
    }

    @Test
    @DisplayName("blank lines before the first record are allowed; text is not")
    void leadingContent() throws IOException {
        Path blank = file("blank.fasta", "\n  \n>sp|P1\nM\n");
        assertEquals(1, FastaDecoyScanner.scan(blank, "DECOY_").records());
        Path index = file("subset.fasta.idx", "Comet index database v5.  Comet version 2026.03\n");
        assertEquals(
                "the FASTA file "
                        + index
                        + " is not a FASTA file: its first line that is not blank does not begin"
                        + " with '>' (it begins \"Comet index database v5.  Comet version ...\")",
                assertThrows(
                                FastaScanException.class,
                                () -> FastaDecoyScanner.scan(index, "DECOY_"))
                        .getMessage());
        Path shortLine = file("short.txt", "MKWV\n>sp|P1\nM\n");
        assertEquals(
                "the FASTA file "
                        + shortLine
                        + " is not a FASTA file: its first line that is not blank does not begin"
                        + " with '>' (it begins \"MKWV\")",
                assertThrows(
                                FastaScanException.class,
                                () -> FastaDecoyScanner.scan(shortLine, "DECOY_"))
                        .getMessage());
    }

    @Test
    @DisplayName("a quoted first line is cut after 40 characters, and one of exactly 40 is not")
    void quotedLength() throws IOException {
        String forty = "M".repeat(40);
        Path exact = file("exact.txt", forty + "\n>sp|P1\nM\n");
        assertEquals(
                "the FASTA file "
                        + exact
                        + " is not a FASTA file: its first line that is not blank does not begin"
                        + " with '>' (it begins \""
                        + forty
                        + "\")",
                assertThrows(FastaScanException.class, () -> FastaDecoyScanner.scan(exact, "D"))
                        .getMessage());
    }

    @Test
    @DisplayName("a line that does not begin a record after the first record is sequence")
    void sequenceAfterRecords() throws IOException {
        Path fasta = file("seq.fasta", ">sp|P1\nnot a header > DECOY_x\n\n>DECOY_y\nM");
        FastaDecoyCensus census = FastaDecoyScanner.scan(fasta, "DECOY_");
        assertEquals(2, census.records());
        assertEquals(1, census.decoyRecords());
    }

    @Test
    @DisplayName("an empty file, and one of blank lines only, hold no record and are refused")
    void empty() throws IOException {
        Path empty = file("empty.fasta", "");
        FastaScanException refused =
                assertThrows(
                        FastaScanException.class, () -> FastaDecoyScanner.scan(empty, "DECOY_"));
        assertEquals(
                "the FASTA file " + empty + " holds no FASTA record (no line begins with '>')",
                refused.getMessage());
        assertSame(empty, refused.file());
        Path blank = file("blank.fasta", "\n\n  \n");
        assertEquals(
                "the FASTA file " + blank + " holds no FASTA record (no line begins with '>')",
                assertThrows(
                                FastaScanException.class,
                                () -> FastaDecoyScanner.scan(blank, "DECOY_"))
                        .getMessage());
    }

    @Test
    @DisplayName("a missing file is refused naming it")
    void missing() {
        Path absent = directory.resolve("absent.fasta");
        FastaScanException refused =
                assertThrows(
                        FastaScanException.class, () -> FastaDecoyScanner.scan(absent, "DECOY_"));
        assertEquals("the FASTA file " + absent + " does not exist", refused.getMessage());
        assertTrue(refused.getCause() instanceof NoSuchFileException);
    }

    @Test
    @DisplayName("a directory is refused naming it")
    void aDirectory() {
        assertEquals(
                "the FASTA file " + directory + " is a directory, not a file",
                assertThrows(
                                FastaScanException.class,
                                () -> FastaDecoyScanner.scan(directory, "DECOY_"))
                        .getMessage());
    }

    @Test
    @EnabledOnOs(
            value = {OS.LINUX, OS.MAC},
            disabledReason = "POSIX permissions")
    @DisplayName("an unreadable file is refused naming it")
    void unreadable() throws IOException {
        Path fasta = file("locked.fasta", ">sp|P1\nM\n");
        Files.setPosixFilePermissions(fasta, PosixFilePermissions.fromString("---------"));
        try {
            if (Files.isReadable(fasta)) {
                throw new AssertionError(
                        "the file system ignores permissions here (running as root?), so this"
                                + " test cannot make a file unreadable");
            }
            FastaScanException refused =
                    assertThrows(
                            FastaScanException.class,
                            () -> FastaDecoyScanner.scan(fasta, "DECOY_"));
            assertEquals(
                    "the FASTA file " + fasta + " cannot be read: permission denied",
                    refused.getMessage());
            assertTrue(refused.getCause() instanceof AccessDeniedException);
        } finally {
            Files.setPosixFilePermissions(fasta, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("an empty prefix or one holding white space is refused before the file is opened")
    void badPrefix() {
        Path absent = directory.resolve("absent.fasta");
        assertEquals(
                "a decoy prefix is one non-empty token, not \"\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> FastaDecoyScanner.scan(absent, ""))
                        .getMessage());
        assertEquals(
                "a decoy prefix is one non-empty token, not \"DE COY\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> FastaDecoyScanner.scan(absent, "DE COY"))
                        .getMessage());
    }

    @Test
    @DisplayName("a large file is streamed: two hundred thousand records are counted")
    void large() throws IOException {
        Path fasta = directory.resolve("large.fasta");
        try (var out = Files.newBufferedWriter(fasta, StandardCharsets.US_ASCII)) {
            for (int record = 0; record < 200_000; record++) {
                out.write(record % 4 == 3 ? ">DECOY_" : ">sp|");
                out.write(Integer.toString(record));
                out.write("\nMKWVTFISLLLLFSSAYS\n");
            }
        }
        FastaDecoyCensus census = FastaDecoyScanner.scan(fasta, "DECOY_");
        assertEquals(200_000, census.records());
        assertEquals(50_000, census.decoyRecords());
        assertEquals(150_000, census.targetRecords());
        assertEquals(Optional.of("DECOY_3"), census.firstDecoyAccession());
    }
}
