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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one path rule, exercised directly, on both of the strings that can reach it.
 *
 * <p>{@link AttackMatrixTest} proves the rule fires through every container. This proves the rule
 * itself: what it accepts, what it normalises, and that the same rule is applied to the manifest's
 * own install path -- so that a mistake in {@code manifests/tools.json} is caught by the check an
 * attack is caught by, with a message that says which of the two files to go and look at.
 */
class ExtractionGuardTest {

    @TempDir private Path work;

    private ExtractionGuard guardOver(Path destination, long artefactBytes) throws IOException {
        return new ExtractionGuard(destination, artefactBytes, ExtractionLimits.defaults());
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
        "'bin/percolator','bin/percolator'",
        "'./usr/share/x.xsd','usr/share/x.xsd'",
        "'usr/./share/x.xsd','usr/share/x.xsd'",
        "'usr/share/','usr/share'",
        "'a','a'"
    })
    @DisplayName("what the path rule accepts, and the form it hands on")
    void acceptedPaths(String raw, String normalised) throws IOException {
        assertEquals(
                normalised,
                guardOver(work.resolve("dest"), 1024L).checkedRelativePath(raw, raw, true));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../x",
                "a/../../x",
                "..",
                "a/..",
                "/etc/x",
                "\\\\server\\share",
                "C:/x",
                "c:x",
                "C:",
                "\u0000leading",
                "a:b",
                "a\\b",
                "a//b",
                ""
            })
    @DisplayName("what the path rule refuses, whichever string it arrives in")
    void refusedPaths(String raw) throws IOException {
        /*
         * "a:b" is a legal file name on POSIX and is refused anyway.  On Windows a name whose
         * second character is a colon is drive-relative -- Path.resolve("a:b") does not mean "a
         * file called a:b inside this directory" there -- so accepting it would make one manifest
         * behave differently on two platforms.  No entry in any artefact this product installs
         * contains a colon, a backslash or a NUL: checked across all 24 mirrored artefacts.
         */
        ExtractionGuard guard = guardOver(work.resolve("dest"), 1024L);
        assertAll(
                () ->
                        assertThrows(
                                ExtractionRejectedException.class,
                                () -> guard.checkedRelativePath(raw, "entry", true),
                                () -> "accepted as an archive name: \"" + raw + "\""),
                () ->
                        assertThrows(
                                ExtractionRejectedException.class,
                                () -> guard.checkedRelativePath(raw, "entry", false),
                                () -> "accepted as a manifest install path: \"" + raw + "\""));
    }

    @Test
    @DisplayName("a bad manifest path is reported against the manifest, not against the archive")
    void aBadManifestPathNamesTheManifest() throws IOException {
        ExtractionGuard guard = guardOver(work.resolve("dest"), 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.checkedRelativePath("../escape", "percolator", false));
        assertAll(
                () -> assertEquals(RejectionReason.ENTRY_NAME_TRAVERSES, rejection.reason()),
                () -> assertEquals("../escape", rejection.subject()),
                () ->
                        assertEquals(
                                "the manifest's install path \"../escape\", for the artefact member"
                                        + " \"percolator\", was rejected because its name has a"
                                        + " \"..\" segment, which would place it outside the"
                                        + " destination directory",
                                rejection.getMessage()));
    }

    @Test
    @DisplayName("a name that reduces to nothing may be a directory and may not be a file")
    void theArchiveRootIsADirectoryAndNotAFile() throws IOException {
        Path destination = work.resolve("dest");
        ExtractionGuard guard = guardOver(destination, 1024L);
        guard.placeDirectory(ArchiveEntry.directory("."));
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () ->
                                guard.placeFileFromArchiveName(
                                        ArchiveEntry.file(".", 0L), bytes("")));
        assertAll(
                () -> assertEquals(List.of(), guard.report().paths()),
                () -> assertEquals(RejectionReason.ENTRY_NAME_EMPTY, rejection.reason()),
                () ->
                        assertEquals(
                                "the archive entry \".\" was rejected because its name is empty or"
                                        + " has an empty segment, so it does not name a file inside"
                                        + " the destination directory -- it names the destination"
                                        + " directory itself, which is not a file",
                                rejection.getMessage()));
    }

    @Test
    @DisplayName("a link named for the destination itself is refused")
    void theArchiveRootCannotBeALink() throws IOException {
        ExtractionGuard guard = guardOver(work.resolve("dest"), 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("./", "elsewhere")));
        assertEquals(
                "the archive entry \"./\" was rejected because its name is empty or has an empty"
                        + " segment, so it does not name a file inside the destination directory --"
                        + " it names the destination directory itself, which cannot be a link",
                rejection.getMessage());
    }

    @Test
    @DisplayName(
            "a link with an empty target is refused, and says so rather than saying it escapes")
    void anEmptyLinkTargetIsRefused() throws IOException {
        Path destination = work.resolve("dest");
        ExtractionGuard guard = guardOver(destination, 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("link", "")));
        assertEquals(
                "the archive entry \"link\" was rejected because it is a symbolic link whose target"
                        + " does not resolve to a place inside the destination directory, so"
                        + " following it would read or write somewhere this extraction does not own"
                        + " -- its target \"\" is empty or contains a NUL character, so it names"
                        + " nothing at all",
                rejection.getMessage());
    }

    @Test
    @DisplayName("a link whose target is spelled for Windows is refused rather than guessed at")
    void aWindowsShapedLinkTargetIsRefused() throws IOException {
        ExtractionGuard guard = guardOver(work.resolve("dest"), 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("link", "C:\\Windows")));
        assertTrue(
                rejection
                        .getMessage()
                        .endsWith(
                                " -- its target \"C:\\Windows\" is spelled with a backslash or a"
                                        + " drive letter, and this extractor resolves neither"
                                        + " rather than guessing which volume was meant"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a safe link is created, pointing where the archive said")
    void aSafeLinkIsCreated() throws IOException {
        Path destination = work.resolve("dest");
        ExtractionGuard guard = guardOver(destination, 1024L);
        guard.placeFileFromArchiveName(ArchiveEntry.file("real.txt", 5L), bytes("hello"));
        PlacedFile link = guard.placeSymlink(ArchiveEntry.symlink("alias.txt", "real.txt"));
        assertAll(
                () -> assertEquals(new PlacedFile("alias.txt", ArchiveEntryType.SYMLINK, 0L), link),
                () ->
                        assertTrue(
                                Files.isSymbolicLink(destination.resolve("alias.txt")),
                                "the link must be a link, not a copy"),
                () ->
                        assertEquals(
                                Path.of("real.txt"),
                                Files.readSymbolicLink(destination.resolve("alias.txt"))),
                () -> assertEquals("hello", Files.readString(destination.resolve("alias.txt"))));
    }

    @Test
    @DisplayName("bytes that disagree with the declared length are refused, naming both counts")
    void aDeclaredSizeMismatchIsRefused() throws IOException {
        Path destination = work.resolve("dest");
        ExtractionGuard guard = guardOver(destination, 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () ->
                                guard.placeFileFromArchiveName(
                                        ArchiveEntry.file("short.txt", 99L), bytes("hello")));
        assertAll(
                () -> assertEquals(RejectionReason.DECLARED_SIZE_MISMATCH, rejection.reason()),
                () ->
                        assertEquals(
                                "the archive entry \"short.txt\" was rejected because the bytes"
                                        + " delivered disagree with the length the archive declared"
                                        + " for it, so the archive's own table cannot be trusted"
                                        + " about the rest of it -- the archive declared 99 bytes"
                                        + " and 5 were delivered",
                                rejection.getMessage()));
    }

    @Test
    @DisplayName("a destination whose size cannot be a denominator is a programming error")
    void aZeroLengthArtefactIsRejected() {
        IllegalArgumentException rejection =
                assertThrows(
                        IllegalArgumentException.class, () -> guardOver(work.resolve("dest"), 0L));
        assertEquals(
                "artefactBytes must be a positive number of bytes, because it is the denominator of"
                        + " the expansion ratio, but was: 0",
                rejection.getMessage());
    }

    @Test
    @DisplayName("the guard rejects null arguments")
    void nullsAreRejected() {
        assertAll(
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new ExtractionGuard(
                                                Nulls.of(Path.class),
                                                1L,
                                                ExtractionLimits.defaults())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new ExtractionGuard(
                                                work.resolve("d"),
                                                1L,
                                                Nulls.of(ExtractionLimits.class))));
    }

    @Test
    @DisplayName("the report accounts for everything, including bytes that were thrown away")
    void theReportCountsDiscardedBytesToo() throws IOException {
        Path destination = work.resolve("dest");
        ExtractionGuard guard = guardOver(destination, 4096L);
        guard.countEntry("a.txt");
        guard.discard(ArchiveEntry.file("a.txt", 5L), bytes("12345"));
        guard.countEntry("b.txt");
        guard.placeFileFromArchiveName(ArchiveEntry.file("b.txt", 3L), bytes("xyz"));
        ExtractionReport report = guard.report();
        assertAll(
                () -> assertEquals(2, report.entriesRead()),
                () -> assertEquals(8L, report.expandedBytes()),
                () -> assertEquals(4096L, report.artefactBytes()),
                () -> assertEquals(List.of("b.txt"), report.paths()),
                () ->
                        assertEquals(
                                new PlacedFile("b.txt", ArchiveEntryType.FILE, 3L),
                                report.at("b.txt").orElseThrow()),
                () -> assertTrue(report.at("a.txt").isEmpty()));
    }

    @Test
    @DisplayName("an expected file the artefact never produced is refused by name")
    void requirePlacedNamesWhatIsMissing() throws IOException {
        ExtractionGuard guard = guardOver(work.resolve("dest"), 4096L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.requirePlaced("bin/tool", "thing.zip"));
        assertEquals(
                "the artefact \"thing.zip\" was rejected because unpacking the whole artefact did"
                        + " not produce it, so the installed tool would have no executable to run"
                        + " -- the manifest expects \"bin/tool\" and the artefact produced 0"
                        + " path(s), none of them that one",
                rejection.getMessage());
    }

    @Test
    @DisplayName("a whole-file copy still goes through the destination rule")
    void copyingAWholeFileIsGuardedToo() throws IOException {
        Path source = Files.writeString(work.resolve("source.bin"), "abc");
        ExtractionGuard guard = guardOver(work.resolve("dest"), 3L);
        assertAll(
                () ->
                        assertThrows(
                                ExtractionRejectedException.class,
                                () -> guard.copyWholeFile(source, "../escape", "source.bin")),
                () ->
                        assertThrows(
                                ExtractionRejectedException.class,
                                () -> guard.copyWholeFile(source, "./", "source.bin")));
    }

    @Test
    @DisplayName("an entry the guard refused leaves no file behind at the name it was aiming at")
    void arefusedEntryWritesNothing() throws IOException {
        Path destination = Files.createDirectories(work.resolve("dest"));
        Path artefact =
                ArchiveFixtures.build(
                        ArtefactKind.ZIP,
                        Files.createDirectories(work.resolve("archives")),
                        "hostile.zip",
                        List.of(Entry.file("../escape.txt", "pwned")));
        assertThrows(
                ExtractionRejectedException.class,
                () ->
                        new ArtefactExtractor()
                                .extractWholeArtefact(
                                        ArtefactKind.ZIP, artefact, destination, "x"));
        assertAll(
                () -> DestinationSnapshot.assertAbsent(work.resolve("escape.txt")),
                () -> DestinationSnapshot.assertAbsent(destination.resolve("escape.txt")),
                () -> assertEquals(List.of(), DestinationSnapshot.of(destination)));
    }

    @Test
    @DisplayName("a link whose target contains a NUL is refused for that, not for escaping")
    void aNulLinkTargetIsRefused() throws IOException {
        ExtractionGuard guard = guardOver(work.resolve("dest"), 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("link", "sub\u0000/x")));
        assertTrue(
                rejection.getMessage().contains("is empty or contains a NUL character"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a link target that climbs above the file-system root resolves nowhere")
    void aLinkTargetAboveTheRootIsRefused() throws IOException {
        Path destination = work.resolve("dest");
        ExtractionGuard guard = guardOver(destination, 1024L);
        String target = "../".repeat(64) + "etc";
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("link", target)));
        assertAll(
                () -> assertEquals(RejectionReason.UNSAFE_SYMLINK, rejection.reason()),
                () ->
                        assertTrue(
                                rejection
                                        .getMessage()
                                        .contains(
                                                "climbs above the file-system root or passes"
                                                        + " through more than 40 links"),
                                () -> "wrong message: " + rejection.getMessage()),
                () ->
                        assertFalse(
                                rejection.getMessage().contains("null"),
                                "a diagnostic that says a target resolves to \"null\" sends a"
                                        + " reader nowhere"));
    }

    @Test
    @DisplayName("a link target whose very first character is a NUL is refused for that")
    void aLinkTargetBeginningWithANul() throws IOException {
        ExtractionGuard guard = guardOver(work.resolve("dest"), 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("link", "\u0000elsewhere")));
        assertTrue(
                rejection.getMessage().contains("is empty or contains a NUL character"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a link target whose very first character is a backslash is refused for that")
    void aLinkTargetBeginningWithABackslash() throws IOException {
        ExtractionGuard guard = guardOver(work.resolve("dest"), 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("link", "\\\\server")));
        assertTrue(
                rejection.getMessage().contains("is spelled with a backslash or a drive letter"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a chain of exactly as many links as the limit allows still resolves")
    void aChainOfExactlyTheLimit() throws IOException {
        Path destination = Files.createDirectories(work.resolve("dest"));
        /*
         * Forty is the limit, so forty must work and forty-one must not.  Testing only the refusal
         * would leave "refuse everything" passing; this is the half that says the limit is a limit
         * rather than a prohibition.
         */
        for (int index = 0; index < 40; index++) {
            Files.createSymbolicLink(
                    destination.resolve("hop" + index), Path.of("hop" + (index + 1)));
        }
        Files.createDirectory(destination.resolve("hop40"));
        ExtractionGuard guard = guardOver(destination, 1024L);
        PlacedFile link = guard.placeSymlink(ArchiveEntry.symlink("entry", "hop0"));
        assertAll(
                () -> assertEquals(new PlacedFile("entry", ArchiveEntryType.SYMLINK, 0L), link),
                () ->
                        assertEquals(
                                Path.of("hop0"),
                                Files.readSymbolicLink(destination.resolve("entry")),
                                "the link is created and points where the archive said"),
                /*
                 * Deliberately not asserted by following the chain: Linux applies its own limit of
                 * forty links and would refuse a forty-first traversal itself, so an assertion
                 * through the file system here would be measuring the kernel rather than this
                 * guard.  What is proved is that the guard resolved the chain and accepted it.
                 */
                () -> assertEquals(1, guard.report().placed().size()));
    }

    @Test
    @DisplayName("a link chain longer than the kernel's own limit resolves nowhere")
    void anEndlessLinkChainIsRefused() throws IOException {
        Path destination = Files.createDirectories(work.resolve("dest"));
        for (int index = 0; index < 45; index++) {
            Files.createSymbolicLink(
                    destination.resolve("link" + index), Path.of("link" + (index + 1)));
        }
        Files.createDirectory(destination.resolve("link45"));
        ExtractionGuard guard = guardOver(destination, 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("end", "link0/x")));
        assertTrue(
                rejection.getMessage().contains("passes through more than 40 links"),
                () -> "wrong message: " + rejection.getMessage());
    }

    @Test
    @DisplayName("a link that passes through an absolute link already on disk is refused")
    void aChainThroughAnAbsoluteLinkIsRefused() throws IOException {
        Path destination = Files.createDirectories(work.resolve("dest"));
        /*
         * Built from the file system's own root rather than written out, so that the test carries
         * no absolute path of its own and reads the same on any platform.
         */
        Path elsewhere =
                java.nio.file.FileSystems.getDefault()
                        .getRootDirectories()
                        .iterator()
                        .next()
                        .resolve("etc");
        Files.createSymbolicLink(destination.resolve("system"), elsewhere);
        ExtractionGuard guard = guardOver(destination, 1024L);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () -> guard.placeSymlink(ArchiveEntry.symlink("creds", "system/passwd")));
        assertAll(
                () -> assertEquals(RejectionReason.UNSAFE_SYMLINK, rejection.reason()),
                () ->
                        assertTrue(
                                rejection
                                        .getMessage()
                                        .contains(
                                                "resolves to \""
                                                        + elsewhere.resolve("passwd")
                                                        + "\""),
                                () -> "wrong message: " + rejection.getMessage()));
    }

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
