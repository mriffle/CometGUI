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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.platform.GlibcVersion;

/**
 * Which {@code GLIBCXX} symbol versions this host's C++ runtime provides, read from the library
 * itself.
 *
 * <h2>Why this class exists at all, and which of the three options phase 05 unit 6 took</h2>
 *
 * <p>The advance check needs the host's {@code GLIBCXX} version and this project has no source for
 * one: {@code org.cometgui.domain.platform.GlibcVersionSource} answers for glibc and there is no
 * equivalent for {@code libstdc++}. Three ways out were on the table.
 *
 * <ul>
 *   <li><strong>(a) read the host's {@code libstdc++.so.6} version definitions.</strong> Taken, and
 *       this class is it.
 *   <li><strong>(b) derive it from the glibc version through a table.</strong> Refused. The two
 *       version series move independently -- a container can carry any GCC runtime on any glibc --
 *       so a table would be a guess wearing a fact's clothes, and a rule that has never seen its
 *       subject is this project's signature defect.
 *   <li><strong>(c) do not filter on it, and use the manifest floor only to word the
 *       diagnostic.</strong> Rejected because it leaves the floor unusable in advance: the whole
 *       reason {@code minimumGlibcxx} exists is that in the loader failure this project executed,
 *       the {@code GLIBCXX} line is reported <em>before</em> the {@code GLIBC} one, so a check
 *       knowing only glibc says "runnable" about a build that fails on the C++ runtime.
 * </ul>
 *
 * <p><strong>The value read here is proved against something independent</strong>, and by execution
 * rather than by a second reading of the same bytes: on this project's Debian 12 host the real
 * Percolator 3.07.1 binary, which requires {@code GLIBCXX_3.4.29}, starts; the real 3.09 payload,
 * which requires {@code GLIBCXX_3.4.32}, is refused by the loader with that version named. The
 * host's provided version therefore lies in {@code [3.4.29, 3.4.32)}, and {@code
 * HostCxxRuntimeTest} asserts that what this class reads falls inside the bracket the loader itself
 * drew.
 *
 * <h2>How the library is found</h2>
 *
 * <p>By asking this process's own memory map, not by guessing a path. {@code /proc/self/maps} names
 * the {@code libc.so.6} the dynamic loader actually resolved for this JVM, and on every glibc
 * distribution {@code libstdc++.so.6} sits in that same directory -- {@code
 * /usr/lib/x86_64-linux-gnu} on Debian, {@code /lib64} on Red Hat. That is the loader's own answer
 * to "where are the shared libraries", which is exactly the question, and it needs no multiarch
 * triplet to be reconstructed from {@code os.arch}. A short list of conventional directories
 * follows it, and if none of them holds the library the answer is <strong>empty</strong>: not a
 * guess, and not a refusal.
 */
public final class HostCxxRuntime {

    /** The file name of the GNU C++ runtime's shared library on every ELF platform. */
    static final String LIBRARY_NAME = "libstdc++.so.6";

    /** Where a Linux process's own memory map is published. */
    static final Path PROCESS_MAPPINGS = Path.of("/proc/self/maps");

    /**
     * The libraries whose mapped directory is worth looking in. {@code libstdc++} is listed as well
     * as {@code libc} so that a JVM that has already loaded it answers immediately.
     */
    static final List<String> ANCHOR_LIBRARIES = List.of(LIBRARY_NAME, "libc.so.6");

    /** Conventional library directories, tried in order after the process's own map. */
    static final List<String> FALLBACK_DIRECTORIES =
            List.of("/lib64", "/usr/lib64", "/lib", "/usr/lib");

    /**
     * The largest file this class will read looking for version strings. A shared library is a few
     * megabytes; the cap is here so that being pointed at something enormous costs an unread file
     * rather than the heap.
     */
    static final long MAXIMUM_LIBRARY_BYTES = 64L * 1024 * 1024;

    /*
     * Two or three numeric components, which is every form libstdc++ uses: GLIBCXX_3.4 and
     * GLIBCXX_3.4.29 both appear in the same library.  CXXABI_1.3.13 deliberately does not match:
     * it is a different version series and comparing the two would be meaningless.
     */
    private static final Pattern GLIBCXX_VERSION =
            Pattern.compile("GLIBCXX_(\\d{1,4}\\.\\d{1,4}(?:\\.\\d{1,5})?)");

    private HostCxxRuntime() {
        throw new AssertionError("HostCxxRuntime is a utility class and is never instantiated");
    }

    /**
     * The newest {@code GLIBCXX} version this host provides.
     *
     * @return the version, or empty when no {@code libstdc++} could be found or read -- which is
     *     "not established", never "the host has none"
     */
    public static Optional<GlibcVersion> hostGlibcxx() {
        return hostLibrary().flatMap(HostCxxRuntime::readQuietly);
    }

    /*
     * An unreadable library is the same answer as an absent one -- undetermined -- and this is the
     * one place that IOException is turned into that answer, so that hostGlibcxx() can be called
     * from a constructor without every caller handling a failure it cannot act on.
     */
    private static Optional<GlibcVersion> readQuietly(Path library) {
        try {
            return highestGlibcxxIn(library);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Where this host's C++ runtime library is, if it is anywhere this class looks.
     *
     * @return the library, or empty
     */
    public static Optional<Path> hostLibrary() {
        return locateIn(searchDirectories());
    }

    /**
     * The directories to look in, most authoritative first: the ones this process has already
     * mapped a C or C++ runtime out of, then the conventional ones.
     *
     * @return the directories, in order and without repeats
     */
    static List<Path> searchDirectories() {
        List<Path> directories = new ArrayList<>(mappedDirectories(readMappings()));
        for (String fallback : FALLBACK_DIRECTORIES) {
            directories.add(Path.of(fallback));
        }
        return dedupe(directories);
    }

    /*
     * A host that publishes no /proc/self/maps -- macOS, Windows, a container without procfs -- is
     * not an error here.  It means the first source of directories has nothing to say and the
     * conventional list is all there is.
     */
    private static List<String> readMappings() {
        try {
            return Files.readAllLines(PROCESS_MAPPINGS, StandardCharsets.ISO_8859_1);
        } catch (IOException noProcfs) {
            return List.of();
        }
    }

    /**
     * The directories of the anchor libraries this process has mapped.
     *
     * <p>Pure over the text, so that every branch of it is reachable from a test on any host: a
     * line naming no file, a relative name, an anchor library, something else entirely.
     *
     * @param mappingLines the lines of a {@code /proc/self/maps} file
     * @return the directories, in the order they were mapped and without repeats
     * @throws NullPointerException if {@code mappingLines} is {@code null}
     */
    static List<Path> mappedDirectories(List<String> mappingLines) {
        Objects.requireNonNull(mappingLines, "mappingLines");
        List<Path> directories = new ArrayList<>();
        for (String line : mappingLines) {
            mappedFile(line)
                    .filter(HostCxxRuntime::isAnchor)
                    .map(Path::getParent)
                    .ifPresent(directories::add);
        }
        return dedupe(directories);
    }

    /*
     * A maps line is "address perms offset dev inode  pathname", with the pathname absent for
     * anonymous mappings and present -- possibly containing spaces -- for file-backed ones.  The
     * path therefore starts at the first '/' after the inode field, and a line whose last field is
     * not an absolute path (an anonymous mapping, "[heap]", "[stack]") names no file.
     */
    private static Optional<Path> mappedFile(String line) {
        int slash = line.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        String candidate = line.substring(slash).strip();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        Path path = Path.of(candidate);
        return path.getParent() == null ? Optional.empty() : Optional.of(path);
    }

    private static boolean isAnchor(Path mapped) {
        Path name = mapped.getFileName();
        return name != null && ANCHOR_LIBRARIES.contains(name.toString());
    }

    private static List<Path> dedupe(List<Path> paths) {
        Set<Path> seen = new LinkedHashSet<>(paths);
        return List.copyOf(seen);
    }

    /**
     * The first of these directories that holds a readable {@code libstdc++.so.6}.
     *
     * @param directories the directories to try, in order
     * @return the library, or empty when none of them holds one
     * @throws NullPointerException if {@code directories} is {@code null}
     */
    static Optional<Path> locateIn(List<Path> directories) {
        for (Path directory : Objects.requireNonNull(directories, "directories")) {
            Path candidate = directory.resolve(LIBRARY_NAME);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * The newest {@code GLIBCXX} version named anywhere in a library file.
     *
     * @param library the library to read
     * @return the newest version it names, or empty when it names none
     * @throws IOException if the file cannot be read
     * @throws NullPointerException if {@code library} is {@code null}
     */
    public static Optional<GlibcVersion> highestGlibcxxIn(Path library) throws IOException {
        return highestGlibcxxIn(library, MAXIMUM_LIBRARY_BYTES);
    }

    /**
     * The newest {@code GLIBCXX} version named in a library file no larger than a stated cap.
     *
     * @param library the library to read
     * @param maximumBytes the largest file to read; a larger one answers empty rather than being
     *     loaded
     * @return the newest version it names, or empty
     * @throws IOException if the file cannot be read
     * @throws NullPointerException if {@code library} is {@code null}
     */
    static Optional<GlibcVersion> highestGlibcxxIn(Path library, long maximumBytes)
            throws IOException {
        Objects.requireNonNull(library, "library");
        if (Files.size(library) > maximumBytes) {
            return Optional.empty();
        }
        /*
         * ISO-8859-1 maps every byte to exactly one character and never throws, so the regex sees
         * the file's bytes rather than a decoder's opinion of them.  The version names live in the
         * dynamic string table and are plain ASCII.
         */
        String content = new String(Files.readAllBytes(library), StandardCharsets.ISO_8859_1);
        Matcher matcher = GLIBCXX_VERSION.matcher(content);
        Optional<GlibcVersion> highest = Optional.empty();
        while (matcher.find()) {
            GlibcVersion found = GlibcVersion.parse(matcher.group(1));
            if (highest.isEmpty() || found.compareTo(highest.get()) > 0) {
                highest = Optional.of(found);
            }
        }
        return highest;
    }
}
