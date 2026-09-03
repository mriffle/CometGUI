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
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link HostCxxRuntime}, the design decision this unit had to take: option <strong>(a)
 * -- read the host's own {@code libstdc++} -- rather than (b) derive it from glibc through a table,
 * or (c) not filter on it at all.
 *
 * <p>The reading is proved against something independent, and by <em>execution</em>: the real
 * Percolator 3.07.1 binary needs {@code GLIBCXX_3.4.29} and starts on this host, and the real 3.09
 * payload needs {@code GLIBCXX_3.4.32} and is refused by the loader with that version named. So the
 * host's version is bracketed by the loader itself, and {@link #whatIsReadIsInsideTheBracket()}
 * requires what this class reads to fall inside it. That is a check the reader cannot pass by
 * agreeing with itself.
 */
class HostCxxRuntimeTest {

    /** The 3.07.1 binary starts here, so the host provides at least this. */
    private static final GlibcVersion LOWER_BOUND = GlibcVersion.parse("3.4.29");

    /** The 3.09 payload is refused naming this, so the host provides strictly less. */
    private static final GlibcVersion UPPER_BOUND = GlibcVersion.parse("3.4.32");

    @Test
    @DisplayName("the newest GLIBCXX version in a file wins, whatever order they appear in")
    void theNewestVersionWins(@TempDir Path directory) throws IOException {
        Path library = directory.resolve("libstdc++.so.6");
        Files.write(
                library,
                ("GLIBCXX_3.4\0GLIBCXX_3.4.29\0GLIBCXX_3.4.9\0GLIBCXX_3.4.30\0GLIBCXX_3.4.21\0"
                                + "CXXABI_1.3.13\0")
                        .getBytes(StandardCharsets.ISO_8859_1));

        assertAll(
                () ->
                        assertEquals(
                                Optional.of(GlibcVersion.of(3, 4, 30)),
                                HostCxxRuntime.highestGlibcxxIn(library),
                                "3.4.9 sorts after 3.4.30 as text and before it as numbers"),
                () ->
                        assertEquals(
                                "3.4.30",
                                HostCxxRuntime.highestGlibcxxIn(library).orElseThrow().text()));
    }

    @Test
    @DisplayName("two-component and three-component version names are both read")
    void bothVersionShapesAreRead(@TempDir Path directory) throws IOException {
        Path onlyShort = directory.resolve("short");
        Files.write(onlyShort, "GLIBCXX_3.4".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(
                Optional.of(GlibcVersion.of(3, 4, 0)), HostCxxRuntime.highestGlibcxxIn(onlyShort));
    }

    @Test
    @DisplayName("the spelling kept is the first of two versions that are numerically equal")
    void theFirstOfTwoEqualVersionsIsKept(@TempDir Path directory) throws IOException {
        Path library = directory.resolve("libstdc++.so.6");
        Files.write(library, "GLIBCXX_3.4.0\0GLIBCXX_3.4\0".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(
                "3.4.0",
                HostCxxRuntime.highestGlibcxxIn(library).orElseThrow().text(),
                "3.4 and 3.4.0 are one version numerically and two spellings, and the spelling is"
                        + " what the R-PLAT-03 diagnostic prints after GLIBCXX_; replacing an equal"
                        + " version would change what a user reads");
    }

    @Test
    @DisplayName("a line that is nothing but a path still names its directory")
    void aLineThatIsOnlyAPath() {
        assertEquals(
                List.of("/lib64"),
                asText(HostCxxRuntime.mappedDirectories(List.of("/lib64/libstdc++.so.6"))),
                "the path starts at the FIRST '/', including when that is the first character");
    }

    @Test
    @DisplayName("a file naming no GLIBCXX version answers empty, and CXXABI is not one")
    void noVersionsIsEmpty(@TempDir Path directory) throws IOException {
        Path library = directory.resolve("libstdc++.so.6");
        Files.write(
                library,
                "CXXABI_1.3.13 GLIBC_2.38 GLIBCXX_ nothing".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(Optional.empty(), HostCxxRuntime.highestGlibcxxIn(library));
    }

    @Test
    @DisplayName("the size cap bites one byte over and not at the cap itself")
    void theSizeCapBitesAtItsBoundary(@TempDir Path directory) throws IOException {
        byte[] content = "GLIBCXX_3.4.30".getBytes(StandardCharsets.ISO_8859_1);
        Path library = directory.resolve("libstdc++.so.6");
        Files.write(library, content);

        assertAll(
                () ->
                        assertEquals(
                                Optional.of(GlibcVersion.of(3, 4, 30)),
                                HostCxxRuntime.highestGlibcxxIn(library, content.length),
                                "a file of exactly the cap is read"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                HostCxxRuntime.highestGlibcxxIn(library, content.length - 1L),
                                "one byte over the cap is not"));
    }

    @Test
    @DisplayName("a file that is not there is an IOException, not an empty answer")
    void anUnreadableFileThrows(@TempDir Path directory) {
        assertThrows(
                IOException.class,
                () -> HostCxxRuntime.highestGlibcxxIn(directory.resolve("absent")));
    }

    @Test
    @DisplayName(
            "mapped directories come from the anchor libraries only, in order, without repeats")
    void mappedDirectoriesAreReadFromTheMap() {
        List<String> maps =
                List.of(
                        "55a0-55a1 r--p 00000000 00:00 0",
                        "7f00-7f01 r--p 00000000 08:01 100  [heap]",
                        "7f02-7f03 r--p 00000000 08:01 101"
                                + "  /usr/lib/x86_64-linux-gnu/libc.so.6",
                        "7f04-7f05 r-xp 00001000 08:01 101"
                                + "  /usr/lib/x86_64-linux-gnu/libc.so.6",
                        "7f06-7f07 r--p 00000000 08:01 102" + "  /opt/java/lib/server/libjvm.so",
                        "7f08-7f09 r--p 00000000 08:01 103  /lib64/libstdc++.so.6",
                        "7f0a-7f0b rw-p 00000000 00:00 0  [stack]");

        assertEquals(
                List.of("/usr/lib/x86_64-linux-gnu", "/lib64"),
                asText(HostCxxRuntime.mappedDirectories(maps)),
                "libjvm.so is mapped and is not an anchor; the two anchors are, each once");
    }

    /** Compared as text so that no absolute path is written as a literal handed to a file API. */
    private static List<String> asText(List<Path> directories) {
        return directories.stream().map(Path::toString).toList();
    }

    @Test
    @DisplayName("a map with nothing file-backed contributes no directories")
    void anEmptyMapContributesNothing() {
        assertAll(
                () -> assertEquals(List.of(), HostCxxRuntime.mappedDirectories(List.of())),
                () ->
                        assertEquals(
                                List.of(),
                                HostCxxRuntime.mappedDirectories(
                                        List.of(
                                                "7f0a-7f0b rw-p 00000000 00:00 0",
                                                "7f0c-7f0d rw-p 00000000 00:00 0  [vdso]",
                                                "7f0e-7f0f rw-p 00000000 08:01 9  libc.so.6"))),
                () ->
                        assertEquals(
                                "mappingLines",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        HostCxxRuntime.mappedDirectories(
                                                                Nulls.of(List.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("an unreadable library and an absent memory map are both \"not established\"")
    void whatCannotBeReadIsUndetermined(@TempDir Path directory) throws IOException {
        Path aDirectory = Files.createDirectories(directory.resolve("nota.so"));

        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(),
                                HostCxxRuntime.readQuietly(aDirectory),
                                "a library that cannot be read leaves the version undetermined"
                                        + " rather than failing an install"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                HostCxxRuntime.readQuietly(directory.resolve("absent"))),
                () ->
                        assertEquals(
                                List.of(),
                                HostCxxRuntime.readMappings(directory.resolve("no-such-maps")),
                                "a host that publishes no memory map -- macOS, Windows, a"
                                        + " container without procfs -- "
                                        + "contributes no directories"));
    }

    @Test
    @DisplayName("a mapped path that is the root itself names no file")
    void theRootNamesNoFile() {
        assertEquals(
                List.of(),
                HostCxxRuntime.mappedDirectories(List.of("7f00-7f01 r--p 00000000 08:01 101  /")),
                "the root is a directory and not a library, and it has no parent to take");
    }

    @Test
    @DisplayName("the search takes the first directory that actually holds the library")
    void locateTakesTheFirstDirectoryThatHasIt(@TempDir Path root) throws IOException {
        Path empty = Files.createDirectories(root.resolve("empty"));
        Path first = Files.createDirectories(root.resolve("first"));
        Path second = Files.createDirectories(root.resolve("second"));
        Files.writeString(first.resolve("libstdc++.so.6"), "GLIBCXX_3.4.30");
        Files.writeString(second.resolve("libstdc++.so.6"), "GLIBCXX_3.4.32");

        assertAll(
                () ->
                        assertEquals(
                                Optional.of(first.resolve("libstdc++.so.6")),
                                HostCxxRuntime.locateIn(List.of(empty, first, second))),
                () ->
                        assertEquals(
                                Optional.empty(),
                                HostCxxRuntime.locateIn(List.of(empty)),
                                "nowhere to look is not a guess"),
                () ->
                        assertEquals(
                                "directories",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> HostCxxRuntime.locateIn(Nulls.of(List.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the search list starts with this process's own mapped directories")
    void theSearchListPrefersWhatIsMapped() {
        List<Path> directories = HostCxxRuntime.searchDirectories();

        assertAll(
                () ->
                        assertTrue(
                                directories.containsAll(
                                        HostCxxRuntime.FALLBACK_DIRECTORIES.stream()
                                                .map(Path::of)
                                                .toList()),
                                directories.toString()),
                () ->
                        assertEquals(
                                directories.size(),
                                directories.stream().distinct().count(),
                                "a directory listed twice would be searched twice"));
    }

    @Test
    @EnabledOnOs(
            value = OS.LINUX,
            disabledReason =
                    "the host C++ runtime is read from an ELF libstdc++ found through"
                            + " /proc/self/maps; on another platform this unit's advance check"
                            + " reports UNDETERMINED and the probe decides, which is the"
                            + " documented residue")
    @DisplayName("this host's libstdc++ is found and its version is inside the executed bracket")
    void whatIsReadIsInsideTheBracket() {
        Path library =
                HostCxxRuntime.hostLibrary()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "no libstdc++.so.6 was found on this "
                                                        + "Linux host; the advance check would"
                                                        + " then answer UNDETERMINED "
                                                        + "for every GLIBCXX floor"));
        GlibcVersion read = HostCxxRuntime.hostGlibcxx().orElseThrow();

        assertAll(
                () -> assertTrue(Files.isRegularFile(library), library.toString()),
                () ->
                        assertTrue(
                                read.isAtLeast(LOWER_BOUND),
                                () ->
                                        "the real Percolator 3.07.1 binary needs GLIBCXX_3.4.29 and"
                                                + " starts on this host, so "
                                                + "the host provides at least"
                                                + " that, but this class read "
                                                + read.text()),
                () ->
                        assertTrue(
                                !read.isAtLeast(UPPER_BOUND),
                                () ->
                                        "the real Percolator 3.09 payload is refused by the loader"
                                                + " naming GLIBCXX_3.4.32, so the host provides"
                                                + " strictly less than that, but this class read "
                                                + read.text()));
    }

    @Test
    @DisplayName("the utility class cannot be instantiated, even by reflection")
    void theUtilityClassIsNotInstantiable() throws ReflectiveOperationException {
        var constructor = HostCxxRuntime.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertEquals(
                "HostCxxRuntime is a utility class and is never instantiated",
                assertThrows(
                                java.lang.reflect.InvocationTargetException.class,
                                constructor::newInstance)
                        .getCause()
                        .getMessage());
    }

    @Test
    @DisplayName("the library reader rejects a null path by name")
    void nullArgumentsAreRejectedByName() {
        assertEquals(
                "library",
                assertThrows(
                                NullPointerException.class,
                                () -> HostCxxRuntime.highestGlibcxxIn(Nulls.of(Path.class)))
                        .getMessage());
    }
}
