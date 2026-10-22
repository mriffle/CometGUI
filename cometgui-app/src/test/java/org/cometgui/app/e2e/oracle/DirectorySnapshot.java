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

package org.cometgui.app.e2e.oracle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.cometgui.app.testing.OwnDigests;

/**
 * What a directory tree holds at one moment, by the test's own reading of the file system (P14-7):
 * for every entry, its kind, its size, its SHA-256 (files only, from {@link OwnDigests}' own {@code
 * MessageDigest} loop), its modification time and -- where the file system publishes one -- its
 * inode. Two snapshots {@link #diff(DirectorySnapshot) diff} into what was added, removed and
 * changed.
 *
 * <p>For canonical scenario step 18, "no file was written next to the input spectra" (snapshot the
 * inputs' directory before the run and after it: the diff is empty), and for the no-XML scenario's
 * "Comet was not rerun" (snapshot the first run's Comet outputs before the compatible rerun and
 * after it: the same bytes, the same inode, the same modification time). The inode is what tells a
 * file left alone from one deleted and rewritten with the same bytes and its old timestamp put
 * back.
 *
 * <p>Symbolic links are recorded as links and never followed. The root itself is the entry {@code
 * "."}.
 *
 * @param root the directory, absolute
 * @param entries every entry under it, by its path relative to the root, with {@code /} separators,
 *     sorted
 */
public record DirectorySnapshot(Path root, Map<String, Entry> entries) {

    /** What kind of entry. */
    public enum Kind {
        /** A regular file. */
        FILE,
        /** A directory. */
        DIRECTORY,
        /** A symbolic link, not followed. */
        LINK,
        /** Anything else: a device, a socket, a pipe. */
        OTHER
    }

    /**
     * One entry.
     *
     * @param kind what it is
     * @param size its size in bytes, as the file system reports it
     * @param sha256 its SHA-256 in lower-case hexadecimal, for a regular file; empty otherwise
     * @param modified its last-modified time
     * @param inode its inode number, where the file system publishes one ({@code unix:ino}); empty
     *     otherwise
     */
    public record Entry(
            Kind kind,
            long size,
            Optional<String> sha256,
            Instant modified,
            Optional<Long> inode) {}

    /**
     * What changed between two snapshots of one tree.
     *
     * @param added entries only the later snapshot has
     * @param removed entries only the earlier snapshot has
     * @param changed entries both have that differ, each with what differed, for example {@code
     *     "a.txt: sha256, size"}
     */
    public record Diff(List<String> added, List<String> removed, List<String> changed) {

        /** The lists, copied. */
        public Diff {
            added = List.copyOf(added);
            removed = List.copyOf(removed);
            changed = List.copyOf(changed);
        }

        /**
         * Whether nothing changed.
         *
         * @return {@code true} when all three lists are empty
         */
        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
        }
    }

    /** The entries, copied and sorted. */
    public DirectorySnapshot {
        Objects.requireNonNull(root, "root");
        entries = Collections.unmodifiableMap(new TreeMap<>(entries));
    }

    /**
     * Snapshots a directory tree.
     *
     * @param root an existing directory
     * @return what it holds now
     * @throws IOException if the tree cannot be walked or a file read
     * @throws AssertionError if {@code root} is not a directory
     */
    public static DirectorySnapshot of(Path root) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new AssertionError(absolute + " is not a directory, so it cannot be snapshotted");
        }
        boolean unix = absolute.getFileSystem().supportedFileAttributeViews().contains("unix");
        Map<String, Entry> entries = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(absolute)) {
            for (Path path : (Iterable<Path>) tree::iterator) {
                BasicFileAttributes attributes =
                        Files.readAttributes(
                                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                Kind kind =
                        attributes.isSymbolicLink()
                                ? Kind.LINK
                                : attributes.isDirectory()
                                        ? Kind.DIRECTORY
                                        : attributes.isRegularFile() ? Kind.FILE : Kind.OTHER;
                Optional<String> sha256 =
                        kind == Kind.FILE
                                ? Optional.of(OwnDigests.of(path).sha256())
                                : Optional.empty();
                Optional<Long> inode =
                        unix
                                ? Optional.of(
                                        ((Number)
                                                        Files.getAttribute(
                                                                path,
                                                                "unix:ino",
                                                                LinkOption.NOFOLLOW_LINKS))
                                                .longValue())
                                : Optional.empty();
                String relative =
                        path.equals(absolute)
                                ? "."
                                : absolute.relativize(path).toString().replace('\\', '/');
                entries.put(
                        relative,
                        new Entry(
                                kind,
                                attributes.size(),
                                sha256,
                                attributes.lastModifiedTime().toInstant(),
                                inode));
            }
        }
        return new DirectorySnapshot(absolute, entries);
    }

    /**
     * What changed from this snapshot to a later one of the same tree.
     *
     * <p>Everything is compared, for directories too: a directory's modification time changes
     * whenever an entry is created in it, deleted from it or renamed in it, and never because a
     * file in it was read -- so a file written beside the inputs and then deleted again still
     * shows, as a change to {@code .} or to its directory, although it is no longer there to be
     * added.
     *
     * @param later the later snapshot
     * @return the differences, each list sorted by path
     */
    public Diff diff(DirectorySnapshot later) {
        Objects.requireNonNull(later, "later");
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        TreeSet<String> paths = new TreeSet<>(entries.keySet());
        paths.addAll(later.entries.keySet());
        for (String path : paths) {
            Entry before = entries.get(path);
            Entry after = later.entries.get(path);
            if (before == null) {
                added.add(path);
            } else if (after == null) {
                removed.add(path);
            } else {
                List<String> what = new ArrayList<>();
                if (before.kind() != after.kind()) {
                    what.add("kind");
                }
                if (before.size() != after.size()) {
                    what.add("size");
                }
                if (!before.sha256().equals(after.sha256())) {
                    what.add("sha256");
                }
                if (!before.modified().equals(after.modified())) {
                    what.add("mtime");
                }
                if (!before.inode().equals(after.inode())) {
                    what.add("inode");
                }
                if (!what.isEmpty()) {
                    changed.add(path + ": " + String.join(", ", what));
                }
            }
        }
        return new Diff(added, removed, changed);
    }

    /**
     * Fails unless a later snapshot of the same tree is identical to this one.
     *
     * @param later the later snapshot
     * @param why what the sameness proves, for the message: for example {@code "no file was written
     *     next to the input spectra"}
     * @throws AssertionError naming every added, removed and changed entry
     */
    public void assertUnchangedIn(DirectorySnapshot later, String why) {
        Diff diff = diff(later);
        if (!diff.isEmpty()) {
            throw new AssertionError(
                    root
                            + " changed, so it is not true that "
                            + why
                            + ": added "
                            + diff.added()
                            + ", removed "
                            + diff.removed()
                            + ", changed "
                            + diff.changed());
        }
    }
}
