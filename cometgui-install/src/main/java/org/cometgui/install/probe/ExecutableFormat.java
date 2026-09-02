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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.HostArchitecture;

/**
 * Which processor an ELF executable was built for, read from its own header.
 *
 * <h2>Why this is read rather than left to the loader</h2>
 *
 * <p>{@code R-PLAT-02} says compatibility is established by executing the binary, and every other
 * loadability verdict in this package comes from doing exactly that. Wrong architecture is the one
 * that cannot, and the reason was measured on this host rather than assumed.
 *
 * <p>Launching the real {@code comet.aarch64.linux.exe} through the process service on this x86-64
 * machine <strong>starts a process</strong>. It does not fail with {@code Exec format error}: when
 * {@code execvp} gets {@code ENOEXEC} glibc retries the file through {@code /bin/sh}, so the JVM
 * sees a successful start and the child prints {@code /tmp/comet-aarch64: 1: Syntax error:
 * Unterminated quoted string} and exits 2. A classifier keyed on the words "Exec format error"
 * would therefore never fire on Linux -- a rule that has never seen its subject -- and the shell's
 * own complaint is not a fact about the binary.
 *
 * <p>So the architecture question is answered from the file's four-byte magic and its {@code
 * e_machine} field, which are facts, and only for ELF. A Mach-O or PE file answers empty:
 * <strong>no macOS or Windows binary has ever been executed anywhere in this project</strong>, so
 * this class makes no claim about one, and an empty answer costs an advance verdict rather than
 * producing a wrong one.
 */
final class ExecutableFormat {

    /** {@code \177ELF}: the four bytes every ELF file begins with. */
    private static final byte[] ELF_MAGIC = {0x7f, 'E', 'L', 'F'};

    /** How many bytes of header are needed to reach {@code e_machine} and read it. */
    private static final int HEADER_BYTES = 20;

    /** Offset of {@code e_ident[EI_DATA]}: 1 for little-endian, 2 for big-endian. */
    private static final int ENDIANNESS_OFFSET = 5;

    /** Offset of {@code e_machine}, a two-byte field in the file's own byte order. */
    private static final int MACHINE_OFFSET = 16 + 2;

    private static final int LITTLE_ENDIAN = 1;
    private static final int BIG_ENDIAN = 2;

    /** {@code EM_X86_64}. */
    private static final int MACHINE_X86_64 = 62;

    /** {@code EM_AARCH64}. */
    private static final int MACHINE_AARCH64 = 183;

    private ExecutableFormat() {}

    /**
     * The architecture an ELF file was built for.
     *
     * @param file the file to read
     * @return the architecture, or empty when the file is not an ELF file, is too short to hold an
     *     ELF header, declares neither byte order, or names a processor this product has no
     *     constant for
     * @throws IOException if the file cannot be read
     * @throws NullPointerException if {@code file} is {@code null}
     */
    static Optional<HostArchitecture> architectureOf(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        byte[] header = new byte[HEADER_BYTES];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(header, 0, HEADER_BYTES);
        }
        if (read < HEADER_BYTES || !startsWithElfMagic(header)) {
            return Optional.empty();
        }
        return machineOf(header).flatMap(ExecutableFormat::architectureFor);
    }

    private static boolean startsWithElfMagic(byte[] header) {
        for (int index = 0; index < ELF_MAGIC.length; index++) {
            if (header[index] != ELF_MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Integer> machineOf(byte[] header) {
        int low = Byte.toUnsignedInt(header[MACHINE_OFFSET]);
        int high = Byte.toUnsignedInt(header[MACHINE_OFFSET + 1]);
        int endianness = Byte.toUnsignedInt(header[ENDIANNESS_OFFSET]);
        if (endianness == LITTLE_ENDIAN) {
            return Optional.of(low | (high << 8));
        }
        if (endianness == BIG_ENDIAN) {
            return Optional.of(high | (low << 8));
        }
        return Optional.empty();
    }

    private static Optional<HostArchitecture> architectureFor(int machine) {
        if (machine == MACHINE_X86_64) {
            return Optional.of(HostArchitecture.X86_64);
        }
        if (machine == MACHINE_AARCH64) {
            return Optional.of(HostArchitecture.AARCH64);
        }
        return Optional.empty();
    }
}
