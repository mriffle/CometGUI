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

package org.cometgui.install.registry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.archive.ArtefactMirror;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Every Windows Comet row's {@code requiredHostLibraries} is what its own binary's import table
 * says it is.
 *
 * <p>The list is a statement about bytes -- which libraries {@code comet.win64.exe} asks the
 * Windows loader for that Windows itself does not supply -- so it is held to the bytes: the pinned
 * binary is read from the artefact mirror, its SHA-256 is checked against the row, its PE import
 * directory is parsed, and what is left after the operating system's own libraries are set aside
 * must be exactly the row's list. Until Phase 16 the 2026.02.2 row listed nothing although its
 * binary imports the same four Visual C++ runtime libraries as 2026.03.0's, and nothing could
 * notice: the only check was a list typed beside one row.
 *
 * <p><strong>Every Windows Comet row the manifest names, not a list of releases typed
 * here.</strong> A release added later is held to its own binary the day its row arrives, and until
 * its binary is in the mirror this test fails saying how to put it there.
 *
 * <p><strong>The classification is total.</strong> An import is either one of the operating
 * system's own, named in {@link #SUPPLIED_BY_WINDOWS} by hand, or it is a required host library. A
 * binary that began importing something new is therefore red until someone decides which it is,
 * rather than green because nobody looked.
 *
 * <p>Files read outside this module: the real artefacts in {@code scratch/phase05/artefacts},
 * through {@link ArtefactMirror}, which fails rather than skips when one is absent.
 */
class WindowsCometImportTableTest {

    /**
     * The imports Windows itself answers, on every supported Windows: the kernel and socket
     * libraries, the .NET Framework's loader, and the Universal C Runtime's API sets (part of the
     * operating system since Windows 10). The Visual C++ runtime -- {@code MSVCP140*.dll}, {@code
     * VCRUNTIME140*.dll} -- is not among them: it arrives with a redistributable the user may not
     * have, which is the whole reason a row declares it.
     */
    private static final List<String> SUPPLIED_BY_WINDOWS =
            List.of("kernel32.dll", "ws2_32.dll", "mscoree.dll");

    /** The prefix of the Universal C Runtime's API-set libraries. */
    private static final String UNIVERSAL_C_RUNTIME = "api-ms-win-crt-";

    private static boolean suppliedByWindows(String library) {
        String name = library.toLowerCase(Locale.ROOT);
        return SUPPLIED_BY_WINDOWS.contains(name) || name.startsWith(UNIVERSAL_C_RUNTIME);
    }

    /** The mirror's copy of a record's download: {@code <releaseTag>__<file name in the URL>}. */
    private static Path mirrored(ArtefactRecord record) {
        String path = record.url().getPath();
        return ArtefactMirror.artefact(
                record.releaseTag() + "__" + path.substring(path.lastIndexOf('/') + 1));
    }

    @Test
    @DisplayName(
            "every Windows Comet row lists exactly the libraries its binary imports that Windows"
                    + " does not supply")
    void everyWindowsCometRowListsWhatItsBinaryImports() throws IOException {
        List<Executable> assertions = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        for (ArtefactRecord record : ArtefactManifestReader.readFromClasspath().artefacts()) {
            if (record.tool() != ToolName.COMET
                    || record.platform().operatingSystem() != HostOperatingSystem.WINDOWS) {
                continue;
            }
            rows.add(record.describe());
            Path binary = mirrored(record);
            String digest = ArtefactMirror.sha256(binary);
            List<String> imports = PortableExecutable.importedLibraries(binary);
            List<String> required =
                    imports.stream().filter(name -> !suppliedByWindows(name)).sorted().toList();
            assertions.add(
                    () ->
                            assertEquals(
                                    record.hashes().sha256(),
                                    digest,
                                    record.describe()
                                            + ": the mirror's copy is not the bytes the row pins,"
                                            + " so its import table says nothing about the row"));
            assertions.add(
                    () ->
                            assertTrue(
                                    imports.stream().anyMatch("KERNEL32.dll"::equalsIgnoreCase),
                                    record.describe()
                                            + ": every Windows program imports KERNEL32.dll, so a"
                                            + " table without it was not read: "
                                            + imports));
            assertions.add(
                    () ->
                            assertEquals(
                                    required,
                                    record
                                            .minimumHostRequirements()
                                            .requiredHostLibraries()
                                            .stream()
                                            .sorted()
                                            .toList(),
                                    record.describe()
                                            + ": requiredHostLibraries against the import table of "
                                            + binary.getFileName()
                                            + ", which names "
                                            + imports));
        }
        assertions.add(
                () ->
                        assertTrue(
                                rows.size() >= 2,
                                "the manifest names a Windows build of each Comet release,"
                                        + " 2026.02.2 and 2026.03.0 at least, and this test found "
                                        + rows));
        assertAll(assertions);
    }

    /**
     * Just enough of the PE format to list the libraries a Windows executable imports: the import
     * directory and the delay-load import directory of a PE32 or PE32+ file, read by hand.
     */
    private static final class PortableExecutable {

        private static final int IMPORT_DIRECTORY = 1;
        private static final int DELAY_IMPORT_DIRECTORY = 13;

        private final ByteBuffer bytes;
        private final List<int[]> sections = new ArrayList<>();

        private PortableExecutable(byte[] content) {
            this.bytes = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        }

        static List<String> importedLibraries(Path file) throws IOException {
            return new PortableExecutable(Files.readAllBytes(file)).librariesOf(file);
        }

        private List<String> librariesOf(Path file) {
            require(bytes.getShort(0) == 0x5A4D, file, "it does not start with MZ");
            int header = bytes.getInt(0x3C);
            require(bytes.getInt(header) == 0x00004550, file, "it has no PE signature");
            int sectionCount = bytes.getShort(header + 6) & 0xFFFF;
            int optionalSize = bytes.getShort(header + 20) & 0xFFFF;
            int optional = header + 24;
            int magic = bytes.getShort(optional) & 0xFFFF;
            require(magic == 0x20B || magic == 0x10B, file, "it is neither PE32 nor PE32+");
            int directories = optional + (magic == 0x20B ? 112 : 96);
            int directoryCount = bytes.getInt(directories - 4);
            int sectionTable = optional + optionalSize;
            for (int index = 0; index < sectionCount; index++) {
                int entry = sectionTable + 40 * index;
                sections.add(
                        new int[] {
                            bytes.getInt(entry + 12),
                            Math.max(bytes.getInt(entry + 8), bytes.getInt(entry + 16)),
                            bytes.getInt(entry + 20)
                        });
            }
            List<String> libraries = new ArrayList<>();
            require(directoryCount > IMPORT_DIRECTORY, file, "it has no import directory");
            /* Import descriptors are 20 bytes with the name at 12; delay-load ones 32, at 4. */
            libraries.addAll(names(bytes.getInt(directories + 8 * IMPORT_DIRECTORY), 20, 12));
            if (directoryCount > DELAY_IMPORT_DIRECTORY) {
                libraries.addAll(
                        names(bytes.getInt(directories + 8 * DELAY_IMPORT_DIRECTORY), 32, 4));
            }
            return List.copyOf(libraries);
        }

        /* The names of one descriptor table, which ends at a descriptor naming nothing. */
        private List<String> names(int tableAddress, int descriptorSize, int nameField) {
            List<String> names = new ArrayList<>();
            if (tableAddress == 0) {
                return names;
            }
            int descriptor = offsetOf(tableAddress);
            while (bytes.getInt(descriptor + nameField) != 0) {
                names.add(textAt(offsetOf(bytes.getInt(descriptor + nameField))));
                descriptor += descriptorSize;
            }
            return names;
        }

        /* A relative virtual address as an offset in the file, through the section it is in. */
        private int offsetOf(int address) {
            for (int[] section : sections) {
                if (address >= section[0] && address < section[0] + section[1]) {
                    return section[2] + (address - section[0]);
                }
            }
            throw new AssertionError(
                    "relative virtual address " + address + " is in no section of the file");
        }

        private String textAt(int offset) {
            int end = offset;
            while (bytes.get(end) != 0) {
                end++;
            }
            byte[] text = new byte[end - offset];
            bytes.get(offset, text);
            return new String(text, StandardCharsets.US_ASCII);
        }

        private static void require(boolean condition, Path file, String otherwise) {
            if (!condition) {
                throw new AssertionError(file + " is not a Windows executable: " + otherwise);
            }
        }
    }
}
