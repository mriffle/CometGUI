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

package org.cometgui.install.probe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.install.archive.ArtefactMirror;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ExecutableFormat}, against <strong>real artefacts of two architectures</strong>:
 * Comet 2026.02.2 publishes an x86-64 and an {@code aarch64} Linux build, and both are in this
 * phase's mirror. That is what makes the wrong-architecture verdict a check that can go red on this
 * machine rather than a branch nobody has reached.
 */
class ExecutableFormatTest {

    @Test
    @DisplayName("the two real Comet Linux builds are read as the two architectures they are")
    void realArtefactsOfBothArchitectures() throws IOException {
        assertAll(
                () ->
                        assertEquals(
                                Optional.of(HostArchitecture.X86_64),
                                ExecutableFormat.architectureOf(
                                        ArtefactMirror.artefact("v2026.02.2__comet.linux.exe"))),
                () ->
                        assertEquals(
                                Optional.of(HostArchitecture.AARCH64),
                                ExecutableFormat.architectureOf(
                                        ArtefactMirror.artefact(
                                                "v2026.02.2__comet.aarch64.linux.exe"))),
                () ->
                        assertEquals(
                                Optional.of(HostArchitecture.X86_64),
                                ExecutableFormat.architectureOf(StagedBinaries.percolator3071())));
    }

    @Test
    @DisplayName("a file that is not an ELF file answers empty rather than guessing")
    void nonElfFilesAnswerEmpty(@TempDir Path directory) throws IOException {
        Path script = directory.resolve("script.sh");
        Files.writeString(script, "#!/bin/sh\nexit 0\n");
        Path machO = directory.resolve("percolator");
        Files.write(
                machO, prefixed(new byte[] {(byte) 0xcf, (byte) 0xfa, (byte) 0xed, (byte) 0xfe}));
        Path portableExecutable = directory.resolve("percolator.exe");
        Files.write(portableExecutable, prefixed(new byte[] {'M', 'Z'}));

        assertAll(
                () -> assertEquals(Optional.empty(), ExecutableFormat.architectureOf(script)),
                () ->
                        assertEquals(
                                Optional.empty(),
                                ExecutableFormat.architectureOf(machO),
                                "no macOS binary has ever been executed anywhere in this project,"
                                        + " so this class claims nothing about a Mach-O file"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                ExecutableFormat.architectureOf(portableExecutable)));
    }

    @Test
    @DisplayName("a file too short to hold an ELF header answers empty")
    void aTruncatedFileAnswersEmpty(@TempDir Path directory) throws IOException {
        Path empty = directory.resolve("empty");
        Files.write(empty, new byte[0]);
        Path truncated = directory.resolve("truncated");
        Files.write(truncated, Arrays.copyOf(elfHeader(1, 62), 19));

        assertAll(
                () -> assertEquals(Optional.empty(), ExecutableFormat.architectureOf(empty)),
                () ->
                        assertEquals(
                                Optional.empty(),
                                ExecutableFormat.architectureOf(truncated),
                                "one byte short of e_machine is one byte short of an answer"),
                () ->
                        assertEquals(
                                Optional.of(HostArchitecture.X86_64),
                                ExecutableFormat.architectureOf(
                                        write(directory, "exact", elfHeader(1, 62))),
                                "and exactly twenty bytes is enough"));
    }

    @Test
    @DisplayName("the machine field is read in the file's own byte order, both ways")
    void bothByteOrdersAreRead(@TempDir Path directory) throws IOException {
        assertAll(
                () ->
                        assertEquals(
                                Optional.of(HostArchitecture.AARCH64),
                                ExecutableFormat.architectureOf(
                                        write(directory, "little", elfHeader(1, 183)))),
                () ->
                        assertEquals(
                                Optional.of(HostArchitecture.AARCH64),
                                ExecutableFormat.architectureOf(
                                        write(directory, "big", elfHeader(2, 183))),
                                "a big-endian ELF spells 183 the other way round, and reading it"
                                        + " little-endian would answer 46848"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                ExecutableFormat.architectureOf(
                                        write(directory, "neither", elfHeader(0, 183))),
                                "an ELF declaring neither byte order says nothing about its"
                                        + " machine field"));
    }

    @Test
    @DisplayName("the magic is what decides: a non-ELF file with a valid header shape is refused")
    void theMagicIsWhatDecides(@TempDir Path directory) throws IOException {
        byte[] disguised = elfHeader(1, 62);
        disguised[0] = 'M';
        disguised[1] = 'Z';
        disguised[2] = 0;
        disguised[3] = 0;

        assertEquals(
                Optional.empty(),
                ExecutableFormat.architectureOf(write(directory, "disguised", disguised)),
                "everything after the first four bytes says x86-64 ELF; the four bytes say it is"
                        + " not an ELF file, and they are what this class reads");
    }

    @Test
    @DisplayName("the high byte of the machine field is part of it, in both byte orders")
    void theHighByteOfTheMachineFieldCounts(@TempDir Path directory) throws IOException {
        byte[] little = elfHeader(1, 62);
        little[19] = 1;
        byte[] big = elfHeader(2, 62);
        big[18] = 1;

        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(),
                                ExecutableFormat.architectureOf(write(directory, "l318", little)),
                                "0x013e is 318 and is not EM_X86_64; dropping the high byte would"
                                        + " read it as 62 and report an x86-64 build"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                ExecutableFormat.architectureOf(write(directory, "b318", big))));
    }

    @Test
    @DisplayName("a processor this product has no constant for answers empty, not a wrong one")
    void anUnknownMachineAnswersEmpty(@TempDir Path directory) throws IOException {
        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(),
                                ExecutableFormat.architectureOf(
                                        write(directory, "i386", elfHeader(1, 3))),
                                "EM_386 is a 32-bit x86 build, which is not one of the two"
                                        + " architectures this product ships for"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                ExecutableFormat.architectureOf(
                                        write(directory, "riscv", elfHeader(1, 243)))));
    }

    @Test
    @DisplayName("a file that cannot be read is an IOException, never a guessed architecture")
    void anUnreadableFileThrows(@TempDir Path directory) throws IOException {
        Path unreadable = write(directory, "unreadable", elfHeader(1, 62));
        assertTrue(unreadable.toFile().setReadable(false, false), "could not clear the read bit");

        assertThrows(
                IOException.class,
                () -> ExecutableFormat.architectureOf(unreadable),
                "an empty answer here would mean \"not an ELF file\", and the loadability probe"
                        + " would then launch a binary whose architecture was never established;"
                        + " the failure has to reach the caller so it becomes a refusal");
    }

    @Test
    @DisplayName("a file that is not there is an IOException too, not an empty answer")
    void anAbsentFileThrows(@TempDir Path directory) {
        assertThrows(
                IOException.class,
                () -> ExecutableFormat.architectureOf(directory.resolve("gone")));
    }

    @Test
    @DisplayName("the reader rejects a null path by name")
    void nullArgumentsAreRejectedByName() {
        assertEquals(
                "file",
                assertThrows(
                                NullPointerException.class,
                                () -> ExecutableFormat.architectureOf(Nulls.of(Path.class)))
                        .getMessage());
    }

    private static Path write(Path directory, String name, byte[] content) throws IOException {
        Path file = directory.resolve(name);
        Files.write(file, content);
        return file;
    }

    /** Twenty bytes of ELF header: the magic, the byte order at index 5, e_machine at 18. */
    private static byte[] elfHeader(int endianness, int machine) {
        byte[] header = new byte[20];
        header[0] = 0x7f;
        header[1] = 'E';
        header[2] = 'L';
        header[3] = 'F';
        header[4] = 2;
        header[5] = (byte) endianness;
        if (endianness == 2) {
            header[18] = (byte) (machine >> 8);
            header[19] = (byte) (machine & 0xff);
        } else {
            header[18] = (byte) (machine & 0xff);
            header[19] = (byte) (machine >> 8);
        }
        return header;
    }

    private static byte[] prefixed(byte[] magic) {
        byte[] content = "not an ELF file at all".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(magic, 0, content, 0, magic.length);
        return content;
    }
}
