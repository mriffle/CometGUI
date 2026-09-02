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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every attack, against every container kind, with the exact message each one produces.
 *
 * <h2>Why this is a matrix and not a list of tests</h2>
 *
 * <p>Phase 05's tenth shape: a rule graded on one axis and not on the others. A traversal test
 * written only for zip leaves the identical rule switchable off for tar, cpio, the Debian payload
 * and the macOS payload with nothing at all going red -- and that shape has already cost this phase
 * two rework rounds. So the attack is described once and run against every kind, and {@link
 * #everyMultiEntryKindIsGraded()} fails if a kind is added to {@link ArtefactKind} without joining
 * the matrix.
 *
 * <h2>What each case proves</h2>
 *
 * <p>Three things, because the first two are not enough on their own. The rejection
 * <em>reason</em>, so that two different faults cannot be reported as one. The <em>whole
 * message</em>, hand-typed, because a guard that fires correctly while its diagnostic misstates
 * what it rejected sends a reader to the wrong place with the authority of the system behind it.
 * And the state of the <em>disk outside the destination</em>, because an exception is not evidence
 * that nothing was written -- a guard can throw after it has already written.
 */
class AttackMatrixTest {

    /** Every rejection this matrix produced, so that the coverage assertion is not a promise. */
    private static final Set<RejectionReason> GRADED = EnumSet.noneOf(RejectionReason.class);

    /** Every kind this matrix was run against. */
    private static final Set<ArtefactKind> KINDS_GRADED = EnumSet.noneOf(ArtefactKind.class);

    /**
     * The unsafe-symbolic-link clause, hand-typed.
     *
     * <p>Typed out here rather than read from {@code RejectionReason.clause()}, because an expected
     * value taken from the code under test cannot fail.
     */
    private static final String UNSAFE_LINK =
            " was rejected because it is a symbolic link whose target does not resolve to a place"
                    + " inside the destination directory, so following it would read or write"
                    + " somewhere this extraction does not own";

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
    @DisplayName("a \"..\" entry is rejected, whatever the container")
    void pathTraversal(ArtefactKind kind) throws IOException {
        assertRejected(
                kind,
                List.of(Entry.file("ok.txt", "fine"), Entry.file("../escape.txt", "pwned")),
                RejectionReason.ENTRY_NAME_TRAVERSES,
                "the archive entry \"../escape.txt\" was rejected because its name has a \"..\""
                        + " segment, which would place it outside the destination directory",
                "escape.txt");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a \"..\" in the MIDDLE of an entry name is rejected too, whatever the container")
    void pathTraversalAfterASegment(ArtefactKind kind) throws IOException {
        /*
         * The same rule, graded over the axis it does not depend on: WHERE the ".." sits.  An
         * injection that rejected only a leading ".." passed every other traversal test in this
         * matrix, because every fixture in it began with one -- which is the tenth shape exactly,
         * and the reason this case exists.
         */
        assertRejected(
                kind,
                List.of(Entry.file("ok.txt", "fine"), Entry.file("sub/../../escape.txt", "pwned")),
                RejectionReason.ENTRY_NAME_TRAVERSES,
                "the archive entry \"sub/../../escape.txt\" was rejected because its name has a"
                        + " \"..\" segment, which would place it outside the destination"
                        + " directory",
                "escape.txt");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a POSIX absolute entry is rejected, whatever the container")
    void absolutePosixPath(ArtefactKind kind) throws IOException {
        assertRejected(
                kind,
                List.of(Entry.file("ok.txt", "fine"), Entry.file("/etc/cometgui-pwned", "pwned")),
                RejectionReason.ENTRY_NAME_ABSOLUTE,
                "the archive entry \"/etc/cometgui-pwned\" was rejected because its name is an"
                        + " absolute path, and an archive does not get to choose the volume or the"
                        + " root a file is written under",
                null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a Windows drive-letter entry is rejected, whatever the container")
    void absoluteWindowsDrivePath(ArtefactKind kind) throws IOException {
        assertRejected(
                kind,
                List.of(Entry.file("ok.txt", "fine"), Entry.file("C:\\Windows\\pwned.dll", "x")),
                RejectionReason.ENTRY_NAME_ABSOLUTE,
                "the archive entry \"C:\\Windows\\pwned.dll\" was rejected because its name is an"
                        + " absolute path, and an archive does not get to choose the volume or the"
                        + " root a file is written under",
                null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a UNC entry is rejected, whatever the container")
    void uncPath(ArtefactKind kind) throws IOException {
        assertRejected(
                kind,
                List.of(Entry.file("ok.txt", "fine"), Entry.file("\\\\server\\share\\x", "x")),
                RejectionReason.ENTRY_NAME_ABSOLUTE,
                "the archive entry \"\\\\server\\share\\x\" was rejected because its name is an"
                        + " absolute path, and an archive does not get to choose the volume or the"
                        + " root a file is written under",
                null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a backslash separator is rejected, whatever the container")
    void backslashSeparator(ArtefactKind kind) throws IOException {
        assertRejected(
                kind,
                List.of(Entry.file("ok.txt", "fine"), Entry.file("sub\\pwned.txt", "x")),
                RejectionReason.ENTRY_NAME_BACKSLASH,
                "the archive entry \"sub\\pwned.txt\" was rejected because its name uses a"
                        + " backslash as a separator, and accepting two spellings of one path is"
                        + " how a rejected name gets in under a second name",
                null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a symbolic link pointing out of the destination is rejected, whatever the kind")
    void escapingSymlink(ArtefactKind kind) throws IOException {
        assertRejected(
                kind,
                List.of(Entry.file("ok.txt", "fine"), Entry.symlink("escape", "../outside/pwned")),
                RejectionReason.UNSAFE_SYMLINK,
                "the archive entry \"escape\""
                        + UNSAFE_LINK
                        + " -- its target \"../outside/pwned\" resolves to \""
                        + work.toRealPath().resolve("outside/pwned")
                        + "\", which is not inside \""
                        + destination.toRealPath()
                        + "\"",
                "outside/pwned");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a symbolic link to an absolute path is rejected, whatever the kind")
    void absoluteSymlink(ArtefactKind kind) throws IOException {
        assertRejected(
                kind,
                List.of(Entry.file("ok.txt", "fine"), Entry.symlink("escape", "/etc/passwd")),
                RejectionReason.UNSAFE_SYMLINK,
                "the archive entry \"escape\""
                        + UNSAFE_LINK
                        + " -- its target \"/etc/passwd\" is an absolute path, which is outside"
                        + " \""
                        + destination.toRealPath()
                        + "\" wherever it points",
                null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a chain that escapes only when it is followed is rejected, whatever the kind")
    void escapingSymlinkChain(ArtefactKind kind) throws IOException {
        /*
         * "sub/up" is a link to the destination itself, safe on its own and accepted.  The third
         * entry's target then normalises LEXICALLY to "sub/outside" -- inside -- while resolving
         * through the file system to a sibling of the destination.  A guard that normalises text
         * instead of following links accepts this, which is the whole reason the fixture exists.
         */
        assertRejected(
                kind,
                List.of(
                        Entry.directory("sub"),
                        Entry.symlink("sub/up", ".."),
                        Entry.symlink("escape", "sub/up/../outside")),
                RejectionReason.UNSAFE_SYMLINK,
                "the archive entry \"escape\""
                        + UNSAFE_LINK
                        + " -- its target \"sub/up/../outside\" resolves to \""
                        + work.toRealPath().resolve("outside")
                        + "\", which is not inside \""
                        + destination.toRealPath()
                        + "\"",
                "outside");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a file written through a link that is itself safe is rejected, whatever the kind")
    void writeThroughSafeSymlink(ArtefactKind kind) throws IOException {
        assertRejected(
                kind,
                List.of(
                        Entry.directory("sub"),
                        Entry.symlink("link", "sub"),
                        Entry.file("link/pwned.txt", "x")),
                RejectionReason.WRITE_THROUGH_SYMLINK,
                "the archive entry \"link/pwned.txt\" was rejected because its own path passes"
                        + " through a symbolic link, and this extractor never writes through one --"
                        + " \"link\" is that link",
                null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a name used twice is rejected, whatever the container")
    void duplicateEntryName(ArtefactKind kind) throws IOException {
        /*
         * A zip catches this earlier than the others and says so: its central directory lists both
         * names before any entry is read, so the refusal names that table rather than the write
         * that did not happen.  The two messages are different on purpose -- one diagnosis per
         * fault -- and both are pinned here rather than papered over with a common prefix.
         */
        assertRejected(
                kind,
                List.of(Entry.file("same.txt", "first"), Entry.file("same.txt", "second")),
                RejectionReason.DUPLICATE_ENTRY_NAME,
                "the archive entry \"same.txt\" was rejected because the archive names it twice, so"
                        + " one of the two would be written and the other lost, with the order of"
                        + " the archive deciding which -- "
                        + (kind == ArtefactKind.ZIP
                                ? "the central directory lists it twice"
                                : "\"same.txt\" has already been written by this extraction"),
                null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("a FIFO is rejected, whatever the container")
    void unsupportedEntryType(ArtefactKind kind) throws IOException {
        assertRejected(
                kind,
                List.of(Entry.file("ok.txt", "fine"), Entry.fifo("pipe")),
                RejectionReason.UNSUPPORTED_ENTRY_TYPE,
                "the archive entry \"pipe\" was rejected because it is neither a regular file, a"
                        + " directory nor a symbolic link, and this extractor creates nothing else"
                        + " -- a hard link, device node, socket or FIFO is a second name for"
                        + " something that may be anywhere on this machine",
                null);
    }

    @Test
    @DisplayName("a NUL in an entry name is rejected -- constructible in a zip only")
    void nulInEntryName() throws IOException {
        assertRejected(
                ArtefactKind.ZIP,
                List.of(Entry.file("ok.txt", "fine"), Entry.file("pwned\u0000.txt", "x")),
                RejectionReason.ENTRY_NAME_NUL,
                "the archive entry \"pwned\u0000.txt\" was rejected because its name contains a NUL"
                        + " character, which truncates the name for anything that reads it as a C"
                        + " string",
                null);
    }

    @Test
    @DisplayName("the matrix graded every multi-entry kind and every attack the gate names")
    void everyMultiEntryKindIsGraded() {
        Set<ArtefactKind> expected =
                EnumSet.copyOf(ArchiveFixtures.MULTI_ENTRY_KINDS.stream().toList());
        List<String> missingAttacks = new ArrayList<>();
        if (!GRADED.contains(RejectionReason.ENTRY_NAME_TRAVERSES)) {
            missingAttacks.add("traversal");
        }
        if (!GRADED.contains(RejectionReason.ENTRY_NAME_ABSOLUTE)) {
            missingAttacks.add("absolute path");
        }
        if (GRADED.stream().noneMatch(RejectionReason::isSymbolicLinkAttack)) {
            missingAttacks.add("unsafe symlink");
        }
        assertAll(
                () ->
                        assertEquals(
                                expected,
                                KINDS_GRADED,
                                "every multi-entry artefact kind must be graded over every attack;"
                                        + " a kind added to ArtefactKind without a fixture here is"
                                        + " a kind whose guards nothing exercises"),
                () ->
                        assertEquals(
                                List.of(),
                                missingAttacks,
                                "the gate names traversal, absolute path, symlink and bomb; the"
                                        + " bomb is graded in DecompressionBombTest"));
    }

    /**
     * Builds the attack in one container kind, runs it whole-artefact, and grades the rejection.
     *
     * @param kind the container to build
     * @param entries the attack
     * @param expectedReason the reason the guard must give
     * @param expectedMessage the whole message, hand-typed, or {@code null} when the caller checks
     *     it itself because it embeds a temporary directory
     * @param escapeTarget a path relative to the work directory that must not exist afterwards, or
     *     {@code null}
     * @return the rejection, for a caller that wants to say more about it
     * @throws IOException if the fixture cannot be written
     */
    private ExtractionRejectedException assertRejected(
            ArtefactKind kind,
            List<Entry> entries,
            RejectionReason expectedReason,
            String expectedMessage,
            String escapeTarget)
            throws IOException {
        Path artefact =
                ArchiveFixtures.build(kind, archives, "attack-" + kind.id() + ".bin", entries);
        List<String> before = DestinationSnapshot.outside(work, destination);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> extractor.extractWholeArtefact(kind, artefact, destination, "ok.txt"),
                        () -> kind + " accepted an attack it must refuse");
        GRADED.add(rejection.reason());
        KINDS_GRADED.add(kind);
        assertAll(
                () -> assertEquals(expectedReason, rejection.reason(), kind::id),
                () -> {
                    if (expectedMessage != null) {
                        assertEquals(expectedMessage, rejection.getMessage(), kind::id);
                    }
                },
                () ->
                        DestinationSnapshot.assertNothingOutside(
                                work, destination, before, "unpacking a hostile " + kind.id()),
                () -> {
                    if (escapeTarget != null) {
                        DestinationSnapshot.assertAbsent(work.resolve(escapeTarget));
                        DestinationSnapshot.assertAbsent(work.getParent().resolve(escapeTarget));
                    }
                });
        return rejection;
    }
}
