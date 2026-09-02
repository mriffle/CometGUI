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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * No artefact kind can put a file on disk without coming through the guard, proved two ways.
 *
 * <h2>By behaviour</h2>
 *
 * <p>{@link #everyArtefactKindPlacesThroughTheGuard()} walks {@link ArtefactKind#values()} -- so a
 * kind added to the enumeration joins the test automatically -- builds a real artefact of that
 * kind, and asks for a member to be installed at a traversing path. Every kind must refuse it. A
 * new kind with no fixture fails here; a new kind that quietly wrote files itself would pass its
 * own extraction and fail this.
 *
 * <h2>By structure</h2>
 *
 * <p>{@link #onlyTheGuardCanWriteToDisk()} reads the compiled classes of the package and walks each
 * one's constant pool, which is the list of every method the class can call. Any reference to a
 * file-mutating call from a class other than {@link ExtractionGuard} fails the build. That is the
 * part a behavioural test cannot give: it holds for code paths no test happens to take, and it
 * holds for a class written next year by someone who never read {@code R-SEC-05}.
 */
class GuardBypassStructureTest {

    /** The one class in the package that may create, write, link, copy or delete a file. */
    private static final String THE_GUARD = "ExtractionGuard";

    /** Calls that put something on disk, by owning class and method name. */
    private static final Map<String, Set<String>> FILE_MUTATING_CALLS =
            Map.of(
                    "java/nio/file/Files",
                            Set.of(
                                    "newOutputStream",
                                    "newBufferedWriter",
                                    "createFile",
                                    "createDirectory",
                                    "createDirectories",
                                    "createSymbolicLink",
                                    "createLink",
                                    "createTempFile",
                                    "createTempDirectory",
                                    "copy",
                                    "move",
                                    "delete",
                                    "deleteIfExists",
                                    "write",
                                    "writeString",
                                    "setAttribute",
                                    "setPosixFilePermissions",
                                    "setLastModifiedTime",
                                    "setOwner"),
                    "java/io/FileOutputStream", Set.of("<init>"),
                    "java/io/FileWriter", Set.of("<init>"),
                    "java/io/RandomAccessFile", Set.of("<init>"),
                    "java/nio/channels/FileChannel", Set.of("open"),
                    "java/io/File",
                            Set.of(
                                    "delete",
                                    "mkdir",
                                    "mkdirs",
                                    "renameTo",
                                    "createNewFile",
                                    "deleteOnExit",
                                    "setWritable",
                                    "setExecutable",
                                    "setReadable"));

    @TempDir private Path work;

    @Test
    @DisplayName("every artefact kind places files through the guard, and none of them past it")
    void everyArtefactKindPlacesThroughTheGuard() throws IOException {
        Path archives = Files.createDirectories(work.resolve("archives"));
        List<String> refused = new ArrayList<>();
        for (ArtefactKind kind : ArtefactKind.values()) {
            Path artefact = artefactOf(kind, archives);
            Path destination = Files.createDirectories(work.resolve("dest-" + kind.id()));
            List<String> before = DestinationSnapshot.outside(work, destination);
            ExtractionRejectedException rejection =
                    assertThrows(
                            ExtractionRejectedException.class,
                            () ->
                                    new ArtefactExtractor()
                                            .extractNamedMembers(
                                                    kind,
                                                    artefact,
                                                    destination,
                                                    List.of(
                                                            new RequestedMember(
                                                                    "payload.txt",
                                                                    "../escape.txt"))),
                            () ->
                                    kind.id()
                                            + " placed a file at a traversing destination, so it"
                                            + " does not go through the guard");
            assertAll(
                    () -> assertEquals(RejectionReason.ENTRY_NAME_TRAVERSES, rejection.reason()),
                    () ->
                            assertEquals(
                                    "the manifest's install path \"../escape.txt\", for the"
                                            + " artefact member \""
                                            + memberNameOf(kind, artefact)
                                            + "\", was rejected because its name has a \"..\""
                                            + " segment, which would place it outside the"
                                            + " destination directory",
                                    rejection.getMessage()),
                    () -> DestinationSnapshot.assertAbsent(work.resolve("escape.txt")),
                    () ->
                            DestinationSnapshot.assertNothingOutside(
                                    work, destination, before, "extracting a " + kind.id()));
            refused.add(kind.id());
        }
        assertEquals(
                List.of("BARE_EXECUTABLE", "ZIP", "TAR_GZ", "JAR", "DEB_PAYLOAD", "PKG_PAYLOAD"),
                refused,
                "every artefact kind the manifest can name must have been exercised here; a kind"
                        + " added to ArtefactKind without joining this list is a kind whose"
                        + " destination guard nothing proves");
    }

    @Test
    @DisplayName("only ExtractionGuard can create, write, link, copy or delete a file")
    void onlyTheGuardCanWriteToDisk() throws IOException {
        Map<String, List<String>> offenders = new TreeMap<>();
        List<String> inspected = new ArrayList<>();
        for (Path classFile : compiledClasses()) {
            String simpleName = String.valueOf(classFile.getFileName()).replace(".class", "");
            inspected.add(simpleName);
            List<String> calls = fileMutatingCallsIn(classFile);
            if (!calls.isEmpty() && !simpleName.startsWith(THE_GUARD)) {
                offenders.put(simpleName, calls);
            }
        }
        assertAll(
                () ->
                        assertEquals(
                                Map.of(),
                                offenders,
                                "R-SEC-05 puts extraction in one place; a class in this package"
                                        + " other than "
                                        + THE_GUARD
                                        + " that can write to disk is a way for an artefact kind to"
                                        + " place a file without any of the checks"),
                () ->
                        assertTrue(
                                inspected.contains(THE_GUARD),
                                () ->
                                        "the compiled classes were not found, so this test proved"
                                                + " nothing; it inspected "
                                                + inspected),
                () ->
                        assertTrue(
                                inspected.size() >= 15,
                                () ->
                                        "the package has more classes than this test found, so it"
                                                + " is reading the wrong directory; it inspected "
                                                + inspected));
    }

    @Test
    @DisplayName("the guard itself does hold the file-mutating calls, so the scan is not vacuous")
    void theScanFindsTheGuardsOwnCalls() throws IOException {
        Path guard =
                compiledClasses().stream()
                        .filter(
                                path ->
                                        "ExtractionGuard.class"
                                                .equals(String.valueOf(path.getFileName())))
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError("ExtractionGuard.class is not there"));
        List<String> calls = fileMutatingCallsIn(guard);
        assertAll(
                () ->
                        assertTrue(
                                calls.contains("java/nio/file/Files.newOutputStream"),
                                () -> "the scan sees no write in the guard: " + calls),
                () ->
                        assertTrue(
                                calls.contains("java/nio/file/Files.createSymbolicLink"),
                                () -> "the scan sees no link in the guard: " + calls),
                () ->
                        assertTrue(
                                calls.contains("java/nio/file/Files.createDirectories"),
                                () ->
                                        "the scan sees no directory creation in the guard: "
                                                + calls));
    }

    private static String memberNameOf(ArtefactKind kind, Path artefact) {
        return switch (kind) {
            case BARE_EXECUTABLE, JAR -> String.valueOf(artefact.getFileName());
            case ZIP, TAR_GZ, DEB_PAYLOAD, PKG_PAYLOAD -> "payload.txt";
        };
    }

    private static Path artefactOf(ArtefactKind kind, Path archives) throws IOException {
        return switch (kind) {
            case BARE_EXECUTABLE, JAR ->
                    Files.write(
                            archives.resolve("single-" + kind.id() + ".bin"),
                            "content".getBytes(StandardCharsets.UTF_8));
            case ZIP, TAR_GZ, DEB_PAYLOAD, PKG_PAYLOAD ->
                    ArchiveFixtures.build(
                            kind,
                            archives,
                            "container-" + kind.id() + ".bin",
                            List.of(Entry.file("payload.txt", "content")));
        };
    }

    /**
     * Every compiled class of the package, read off the classpath rather than from a list.
     *
     * @return the class files
     * @throws IOException if the directory cannot be read
     */
    private static List<Path> compiledClasses() throws IOException {
        URL located =
                GuardBypassStructureTest.class
                        .getClassLoader()
                        .getResource("org/cometgui/install/archive/ExtractionGuard.class");
        if (located == null || !"file".equals(located.getProtocol())) {
            throw new AssertionError(
                    "the compiled classes of org.cometgui.install.archive are not on the classpath"
                            + " as files, so this structural check cannot run and must not pass:"
                            + " found "
                            + located);
        }
        Path directory = Path.of(java.net.URI.create(located.toString())).getParent();
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> String.valueOf(path.getFileName()).endsWith(".class"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Every file-mutating method one compiled class can call, from its constant pool.
     *
     * @param classFile the compiled class
     * @return the calls, as {@code owner.name}, in the order the pool lists them
     * @throws IOException if the class cannot be read
     */
    private static List<String> fileMutatingCallsIn(Path classFile) throws IOException {
        ConstantPool pool = ConstantPool.read(classFile);
        List<String> found = new ArrayList<>();
        for (String call : pool.methodReferences()) {
            int dot = call.lastIndexOf('.');
            String owner = call.substring(0, dot);
            String method = call.substring(dot + 1);
            if (FILE_MUTATING_CALLS.getOrDefault(owner, Set.of()).contains(method)) {
                found.add(call);
            }
        }
        return found;
    }

    /**
     * As much of a class file's constant pool as it takes to list the methods it calls.
     *
     * <p>Written out rather than borrowed: the project adds no dependency for this, and the format
     * is four tags deep. A method reference points at a class entry and a name-and-type entry, each
     * of which points at a UTF-8 entry, so resolving the three gives {@code owner.name} for every
     * call the class can make.
     */
    private record ConstantPool(
            Map<Integer, String> utf8,
            Map<Integer, Integer> classNameIndex,
            Map<Integer, int[]> nameAndType,
            List<int[]> methodRefs) {

        static ConstantPool read(Path classFile) throws IOException {
            Map<Integer, String> utf8 = new LinkedHashMap<>();
            Map<Integer, Integer> classNameIndex = new LinkedHashMap<>();
            Map<Integer, int[]> nameAndType = new LinkedHashMap<>();
            List<int[]> methodRefs = new ArrayList<>();
            try (InputStream raw = Files.newInputStream(classFile);
                    DataInputStream in = new DataInputStream(raw)) {
                if (in.readInt() != 0xCAFEBABE) {
                    throw new AssertionError(classFile + " is not a class file");
                }
                in.readUnsignedShort();
                in.readUnsignedShort();
                int count = in.readUnsignedShort();
                int index = 1;
                while (index < count) {
                    int tag = in.readUnsignedByte();
                    int slots = 1;
                    switch (tag) {
                        case 1 -> utf8.put(index, in.readUTF());
                        case 7, 8, 16, 19, 20 -> {
                            int reference = in.readUnsignedShort();
                            if (tag == 7) {
                                classNameIndex.put(index, reference);
                            }
                        }
                        case 3, 4, 9, 11, 17, 18 -> {
                            int first = in.readUnsignedShort();
                            int second = in.readUnsignedShort();
                            if (tag == 11) {
                                methodRefs.add(new int[] {first, second});
                            }
                        }
                        case 10 ->
                                methodRefs.add(
                                        new int[] {in.readUnsignedShort(), in.readUnsignedShort()});
                        case 12 ->
                                nameAndType.put(
                                        index,
                                        new int[] {in.readUnsignedShort(), in.readUnsignedShort()});
                        case 5, 6 -> {
                            in.readLong();
                            slots = 2;
                        }
                        case 15 -> {
                            in.readUnsignedByte();
                            in.readUnsignedShort();
                        }
                        default ->
                                throw new AssertionError(
                                        "unknown constant pool tag "
                                                + tag
                                                + " at entry "
                                                + index
                                                + " of "
                                                + classFile);
                    }
                    index += slots;
                }
            }
            return new ConstantPool(utf8, classNameIndex, nameAndType, methodRefs);
        }

        List<String> methodReferences() {
            List<String> calls = new ArrayList<>();
            for (int[] reference : methodRefs) {
                Integer ownerName = classNameIndex.get(reference[0]);
                int[] signature = nameAndType.get(reference[1]);
                if (ownerName == null || signature == null) {
                    continue;
                }
                String owner = utf8.get(ownerName);
                String method = utf8.get(signature[0]);
                if (owner != null && method != null) {
                    calls.add(owner + "." + method);
                }
            }
            return calls;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d method reference(s)", methodRefs.size());
        }
    }
}
