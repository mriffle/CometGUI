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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The directory snapshot sees each kind of change it exists to see -- bytes, size, a deleted and
 * re-created file with its old timestamp put back (the inode), an added, a removed, a file written
 * and deleted again -- and nothing when nothing changed. Every modification time is set by the test
 * to a fixed instant first, so no assertion depends on the clock's resolution.
 */
class DirectorySnapshotTest {

    /** SHA-256 of the three bytes {@code abc} (FIPS 180-2's first example). */
    private static final String ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** SHA-256 of nothing. */
    private static final String EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** SHA-256 of {@code abd}. */
    private static final String ABD =
            "a52d159f262b2c6ddb724a61840befc36eb30c88877a4030b65cbe86298449c9";

    private static final FileTime THEN = FileTime.from(Instant.parse("2026-01-01T00:00:00Z"));

    @TempDir private Path root;

    @BeforeEach
    void aSmallTree() throws IOException {
        Files.writeString(root.resolve("a.txt"), "abc", StandardCharsets.US_ASCII);
        Files.createDirectories(root.resolve("sub"));
        Files.write(root.resolve("sub/b.txt"), new byte[0]);
        for (String path : List.of("a.txt", "sub/b.txt", "sub", ".")) {
            Files.setLastModifiedTime(root.resolve(path), THEN);
        }
    }

    @Test
    @DisplayName("the snapshot holds every entry with its kind, size, own SHA-256 and time")
    void theSnapshotHoldsEveryEntry() throws IOException {
        DirectorySnapshot snapshot = DirectorySnapshot.of(root);
        DirectorySnapshot.Entry a = snapshot.entries().get("a.txt");
        DirectorySnapshot.Entry b = snapshot.entries().get("sub/b.txt");
        assertAll(
                () ->
                        assertEquals(
                                List.of(".", "a.txt", "sub", "sub/b.txt"),
                                List.copyOf(snapshot.entries().keySet())),
                () -> assertEquals(DirectorySnapshot.Kind.FILE, a.kind()),
                () -> assertEquals(3, a.size()),
                () -> assertEquals(Optional.of(ABC), a.sha256()),
                () -> assertEquals(Instant.parse("2026-01-01T00:00:00Z"), a.modified()),
                () -> assertEquals(Optional.of(EMPTY), b.sha256()),
                () ->
                        assertEquals(
                                DirectorySnapshot.Kind.DIRECTORY,
                                snapshot.entries().get("sub").kind()),
                () -> assertEquals(Optional.empty(), snapshot.entries().get("sub").sha256()));
    }

    @Test
    @DisplayName(
            "reading every file changes nothing: two snapshots of an untouched tree do not differ")
    void anUntouchedTreeDoesNotDiffer() throws IOException {
        DirectorySnapshot before = DirectorySnapshot.of(root);
        Files.readAllBytes(root.resolve("a.txt"));
        DirectorySnapshot after = DirectorySnapshot.of(root);
        assertTrue(before.diff(after).isEmpty(), () -> before.diff(after).toString());
        before.assertUnchangedIn(after, "nothing was written");
    }

    @Test
    @DisplayName(
            "new bytes of the same length, with the old time put back, are a sha256 change and"
                    + " nothing else")
    void newBytesAreSeen() throws IOException {
        DirectorySnapshot before = DirectorySnapshot.of(root);
        Files.writeString(root.resolve("a.txt"), "abd", StandardCharsets.US_ASCII);
        Files.setLastModifiedTime(root.resolve("a.txt"), THEN);
        DirectorySnapshot after = DirectorySnapshot.of(root);
        assertEquals(Optional.of(ABD), after.entries().get("a.txt").sha256());
        assertEquals(
                new DirectorySnapshot.Diff(List.of(), List.of(), List.of("a.txt: sha256")),
                before.diff(after));
    }

    @Test
    @EnabledOnOs(
            value = {OS.LINUX, OS.MAC},
            disabledReason = "inodes are read through the unix attribute view")
    @DisplayName(
            "the same bytes rewritten as a new file, with the old times put back, are an inode"
                    + " change"
                    + " and nothing else")
    void aRewrittenFileIsSeenByItsInode() throws IOException {
        DirectorySnapshot before = DirectorySnapshot.of(root);
        Path replacement = root.resolve("a.txt.new");
        Files.writeString(replacement, "abc", StandardCharsets.US_ASCII);
        Files.move(
                replacement,
                root.resolve("a.txt"),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        Files.setLastModifiedTime(root.resolve("a.txt"), THEN);
        Files.setLastModifiedTime(root, THEN);
        DirectorySnapshot after = DirectorySnapshot.of(root);
        assertNotEquals(
                before.entries().get("a.txt").inode(), after.entries().get("a.txt").inode());
        assertEquals(
                new DirectorySnapshot.Diff(List.of(), List.of(), List.of("a.txt: inode")),
                before.diff(after));
    }

    @Test
    @DisplayName("an added and a removed file are named, and so is the change to their directories")
    void addedAndRemovedAreSeen() throws IOException {
        DirectorySnapshot before = DirectorySnapshot.of(root);
        Files.writeString(root.resolve("c.txt"), "new", StandardCharsets.US_ASCII);
        Files.delete(root.resolve("sub/b.txt"));
        DirectorySnapshot.Diff diff = before.diff(DirectorySnapshot.of(root));
        assertAll(
                () -> assertEquals(List.of("c.txt"), diff.added()),
                () -> assertEquals(List.of("sub/b.txt"), diff.removed()),
                () -> assertEquals(2, diff.changed().size(), diff::toString),
                () -> assertTrue(diff.changed().get(0).startsWith(".: "), diff::toString),
                () -> assertTrue(diff.changed().get(1).startsWith("sub: "), diff::toString));
    }

    @Test
    @DisplayName(
            "a file written beside the inputs and deleted again still shows, in its directory's"
                    + " time")
    void aWrittenAndDeletedFileStillShows() throws IOException {
        DirectorySnapshot before = DirectorySnapshot.of(root);
        Path scratch = root.resolve("sub/scratch.tmp");
        Files.writeString(scratch, "x", StandardCharsets.US_ASCII);
        Files.delete(scratch);
        DirectorySnapshot after = DirectorySnapshot.of(root);
        AssertionError failed =
                assertThrows(
                        AssertionError.class,
                        () ->
                                before.assertUnchangedIn(
                                        after, "no file was written next to the inputs"));
        assertTrue(
                failed.getMessage()
                        .startsWith(
                                root.toAbsolutePath().normalize()
                                        + " changed, so it is not true that no file was written"
                                        + " next"
                                        + " to the inputs: added [], removed [], changed [sub:"
                                        + " mtime"),
                failed::getMessage);
    }

    @Test
    @DisplayName("a path that is not a directory cannot be snapshotted")
    void notADirectory() {
        AssertionError failed =
                assertThrows(
                        AssertionError.class, () -> DirectorySnapshot.of(root.resolve("a.txt")));
        assertEquals(
                root.resolve("a.txt").toAbsolutePath().normalize()
                        + " is not a directory, so it cannot be snapshotted",
                failed.getMessage());
    }
}
