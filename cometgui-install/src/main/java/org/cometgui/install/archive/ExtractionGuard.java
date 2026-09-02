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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The one place a file is put on disk, and therefore the one place every {@code R-SEC-05} check
 * lives.
 *
 * <h2>Why this is a class and not a method on each reader</h2>
 *
 * <p>{@code R-SEC-05} requires the traversal, absolute-path, symbolic-link and decompression-bomb
 * checks to be applied <strong>uniformly to every artefact kind</strong>. Uniformity that depends
 * on each future author remembering to call a helper is the rule this project keeps finding broken,
 * so it is structural here instead: the per-format readers -- zip, tar, cpio, {@code ar}, {@code
 * xar} -- describe entries and never touch the file system, and this class is the only one in the
 * package that can create, write to or link a file. A new artefact kind therefore <em>cannot</em>
 * place a file without coming through here, and {@code GuardBypassStructureTest} fails the build if
 * another class in this package acquires a file-mutating call.
 *
 * <h2>The two ways a destination path is chosen, and the one rule they share</h2>
 *
 * <p>In <strong>whole-artefact</strong> mode the destination comes from the archive's own entry
 * name; in <strong>named-member</strong> mode it comes from the manifest, and the archive's name is
 * used only to find the member. Both go through {@link #checkedRelativePath}, so a manifest mistake
 * is caught by the same rule as an attack -- and the messages differ, because a bad archive name
 * and a bad manifest path send a reader to different files.
 *
 * <p>That distinction is what lets both of these be true at once, which is the point of the design:
 * {@code rel-3-06-05/percolator-noxml-osx-portable.zip} really does hold a member named {@code
 * ../my_build/percolator-noxml/src/percolator}; it installs correctly, because the manifest names
 * the destination; and the same entry is rejected when the same archive is unpacked whole. Taking
 * the basename would have been the weakening.
 *
 * <h2>Nothing is written through a symbolic link</h2>
 *
 * <p>A symbolic link is created only when its target resolves inside the destination, and no file
 * is ever written to a path any of whose existing components is a symbolic link. The second rule is
 * what closes the two-entry attack -- a link to somewhere else, then a file "inside" it -- which
 * the first alone does not, because the link the attacker needs can be a perfectly safe one.
 */
final class ExtractionGuard {

    /** Copy buffer. Fixed so that a bomb is stopped within one buffer of the ceiling. */
    private static final int BUFFER_BYTES = 64 * 1024;

    /** How many symbolic links one target may pass through, the figure Linux itself uses. */
    private static final int MAX_LINK_DEPTH = 40;

    /** The destination directory, canonicalised once so that {@code startsWith} means something. */
    private final Path destination;

    /** The ceilings a decompression bomb has to get past. */
    private final ExtractionLimits limits;

    /** The artefact's size on disk: the denominator of the expansion ratio. */
    private final long artefactBytes;

    /** Destination paths already used, so that the archive cannot name one twice. */
    private final Set<String> used = new LinkedHashSet<>();

    /** What has been created, in creation order. */
    private final List<PlacedFile> placed = new ArrayList<>();

    /** Every uncompressed byte that has left the container, written or discarded. */
    private long expandedBytes;

    /** Entries the container has yielded, including skipped ones. */
    private int entriesRead;

    /**
     * Creates a guard over one destination directory, creating it if it does not exist.
     *
     * @param destination the directory everything is written under
     * @param artefactBytes the artefact's size on disk
     * @param limits the decompression-bomb ceilings
     * @throws IOException if the destination cannot be created or canonicalised
     * @throws IllegalArgumentException if {@code artefactBytes} is not positive
     */
    ExtractionGuard(Path destination, long artefactBytes, ExtractionLimits limits)
            throws IOException {
        Objects.requireNonNull(destination, "destination");
        this.limits = Objects.requireNonNull(limits, "limits");
        if (artefactBytes <= 0) {
            throw new IllegalArgumentException(
                    "artefactBytes must be a positive number of bytes, because it is the"
                            + " denominator of the expansion ratio, but was: "
                            + artefactBytes);
        }
        this.artefactBytes = artefactBytes;
        Files.createDirectories(destination);
        this.destination = destination.toRealPath();
    }

    /**
     * Counts one entry the container yielded, whether or not anything is done with it.
     *
     * @param entryName the entry's name, for the message
     * @throws ExtractionRejectedException if the artefact holds more entries than the limit allows
     */
    void countEntry(String entryName) throws ExtractionRejectedException {
        entriesRead++;
        if (entriesRead > limits.maxEntryCount()) {
            throw ExtractionRejectedException.entry(
                    RejectionReason.BOMB_ENTRY_COUNT,
                    entryName,
                    " -- it is entry number "
                            + entriesRead
                            + " and this extractor reads at most "
                            + limits.maxEntryCount());
        }
    }

    /**
     * Checks a path that will place a file, and returns it in normalised, relative form.
     *
     * <p>The one path rule, applied whether the string came from an archive header or from the
     * manifest. A leading {@code ./} is dropped, because the {@code .deb} tar and the {@code .pkg}
     * cpio both carry one and the manifest does not -- but only as a whole segment, never by
     * trimming a prefix, which would also trim {@code ../}.
     *
     * @param path the path to check
     * @param entryName the archive entry the path belongs to, for the message
     * @param fromArchive {@code true} when {@code path} is the archive's own entry name, {@code
     *     false} when it is the destination the manifest declared
     * @return the path with {@code /} separators, no {@code .} segments and no trailing slash;
     *     empty when the name denoted the archive root, which only a directory entry may do
     * @throws ExtractionRejectedException if the path could place a file outside the destination
     *     directory, or is not a usable relative path
     */
    String checkedRelativePath(String path, String entryName, boolean fromArchive)
            throws ExtractionRejectedException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(entryName, "entryName");
        if (path.indexOf('\0') >= 0) {
            throw reject(RejectionReason.ENTRY_NAME_NUL, path, entryName, fromArchive);
        }
        if (path.isEmpty()) {
            throw reject(RejectionReason.ENTRY_NAME_EMPTY, path, entryName, fromArchive);
        }
        for (String segment : path.split("[/\\\\]", -1)) {
            if ("..".equals(segment)) {
                throw reject(RejectionReason.ENTRY_NAME_TRAVERSES, path, entryName, fromArchive);
            }
        }
        if (path.startsWith("/") || path.startsWith("\\") || hasDriveLetter(path)) {
            throw reject(RejectionReason.ENTRY_NAME_ABSOLUTE, path, entryName, fromArchive);
        }
        if (path.indexOf('\\') >= 0) {
            throw reject(RejectionReason.ENTRY_NAME_BACKSLASH, path, entryName, fromArchive);
        }
        return joinedSegments(path, entryName, fromArchive);
    }

    /*
     * "C:" and "c:/x" are absolute on Windows and mean nothing here, so they are refused rather
     * than treated as a relative name containing a colon.  A colon elsewhere in a name is left
     * alone: it is legal on POSIX and appears in real archives.
     */
    private static boolean hasDriveLetter(String path) {
        return path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':';
    }

    private String joinedSegments(String path, String entryName, boolean fromArchive)
            throws ExtractionRejectedException {
        String[] raw = path.split("/", -1);
        List<String> segments = new ArrayList<>(raw.length);
        for (int i = 0; i < raw.length; i++) {
            String segment = raw[i];
            if (segment.isEmpty()) {
                /*
                 * One trailing slash is how every container spells a directory, so it is dropped.
                 * An empty segment anywhere else -- "a//b" -- is two spellings of one path and is
                 * refused.  A LEADING empty segment cannot reach here: a name beginning with "/"
                 * was refused as absolute several lines above.
                 */
                if (i == raw.length - 1) {
                    continue;
                }
                throw reject(RejectionReason.ENTRY_NAME_EMPTY, path, entryName, fromArchive);
            }
            if (".".equals(segment)) {
                continue;
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    private static ExtractionRejectedException reject(
            RejectionReason reason, String path, String entryName, boolean fromArchive) {
        return fromArchive
                ? ExtractionRejectedException.entry(reason, entryName, "")
                : ExtractionRejectedException.destination(reason, path, entryName);
    }

    /**
     * Creates a directory named by the archive.
     *
     * @param entry the directory entry
     * @throws IOException if the directory cannot be created
     * @throws ExtractionRejectedException if the name could place it outside the destination
     */
    void placeDirectory(ArchiveEntry entry) throws IOException {
        String relative = checkedRelativePath(entry.name(), entry.name(), true);
        if (relative.isEmpty()) {
            return;
        }
        Path target = resolveForWriting(relative, entry.name());
        claim(relative, entry.name());
        Files.createDirectories(target);
        record(relative, ArchiveEntryType.DIRECTORY, 0L);
    }

    /**
     * Writes a file to the destination the archive named.
     *
     * @param entry the file entry
     * @param content the entry's bytes
     * @return what was written
     * @throws IOException if the file cannot be written
     * @throws ExtractionRejectedException if the name could place it outside the destination, if
     *     the archive names it twice, or if a decompression-bomb ceiling is reached
     */
    PlacedFile placeFileFromArchiveName(ArchiveEntry entry, InputStream content)
            throws IOException {
        String relative = checkedRelativePath(entry.name(), entry.name(), true);
        return writeFile(entry, relative, content);
    }

    /**
     * Writes a file to the destination the manifest declared, ignoring the name the archive uses.
     *
     * @param entry the file entry, whose name is used only in messages
     * @param installedPath the destination the manifest declares
     * @param content the entry's bytes
     * @return what was written
     * @throws IOException if the file cannot be written
     * @throws ExtractionRejectedException if the manifest's path could place it outside the
     *     destination, if two members would be written to it, or if a bomb ceiling is reached
     */
    PlacedFile placeFileAtDeclaredPath(
            ArchiveEntry entry, String installedPath, InputStream content) throws IOException {
        String relative = checkedRelativePath(installedPath, entry.name(), false);
        return writeFile(entry, relative, content);
    }

    private PlacedFile writeFile(ArchiveEntry entry, String relative, InputStream content)
            throws IOException {
        if (relative.isEmpty()) {
            throw ExtractionRejectedException.entry(
                    RejectionReason.ENTRY_NAME_EMPTY,
                    entry.name(),
                    " -- it names the destination directory itself, which is not a file");
        }
        Path target = resolveForWriting(relative, entry.name());
        claim(relative, entry.name());
        createParentDirectories(relative);
        long written;
        try (OutputStream out =
                Files.newOutputStream(
                        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            written = transfer(entry.name(), content, out);
        }
        if (written != entry.declaredSizeBytes()) {
            throw ExtractionRejectedException.entry(
                    RejectionReason.DECLARED_SIZE_MISMATCH,
                    entry.name(),
                    " -- the archive declared "
                            + entry.declaredSizeBytes()
                            + " bytes and "
                            + written
                            + " were delivered");
        }
        return record(relative, ArchiveEntryType.FILE, written);
    }

    /**
     * Copies a whole downloaded file into the destination, for the artefact kinds that are one file
     * rather than a container.
     *
     * <p>{@code BARE_EXECUTABLE} and {@code JAR} have no entry names at all, so nothing
     * attacker-controlled reaches the file system here -- and the destination still goes through
     * the same path rule, because "there is nothing to check" is how a check comes to be missing.
     *
     * @param source the downloaded file
     * @param installedPath the destination the manifest declares
     * @param subject how the file is named in a message
     * @return what was written
     * @throws IOException if the file cannot be copied
     * @throws ExtractionRejectedException if the manifest's path could place it outside the
     *     destination, or if a bomb ceiling is reached
     */
    PlacedFile copyWholeFile(Path source, String installedPath, String subject) throws IOException {
        countEntry(subject);
        String relative = checkedRelativePath(installedPath, subject, false);
        if (relative.isEmpty()) {
            throw ExtractionRejectedException.destination(
                    RejectionReason.ENTRY_NAME_EMPTY, installedPath, subject);
        }
        Path target = resolveForWriting(relative, subject);
        claim(relative, subject);
        createParentDirectories(relative);
        long size = Files.size(source);
        expand(subject, size);
        Files.copy(source, target);
        return record(relative, ArchiveEntryType.FILE, size);
    }

    /**
     * Creates a symbolic link, if and only if its target resolves inside the destination.
     *
     * @param entry the symbolic-link entry
     * @return what was created
     * @throws IOException if the link cannot be created
     * @throws ExtractionRejectedException if the name or the target could reach outside the
     *     destination directory
     */
    PlacedFile placeSymlink(ArchiveEntry entry) throws IOException {
        String relative = checkedRelativePath(entry.name(), entry.name(), true);
        if (relative.isEmpty()) {
            throw ExtractionRejectedException.entry(
                    RejectionReason.ENTRY_NAME_EMPTY,
                    entry.name(),
                    " -- it names the destination directory itself, which cannot be a link");
        }
        Path link = resolveForWriting(relative, entry.name());
        String rawTarget = entry.linkTarget();
        if (rawTarget.isEmpty() || rawTarget.indexOf('\0') >= 0) {
            throw unsafeLink(
                    entry,
                    "its target \""
                            + rawTarget
                            + "\" is empty or contains a NUL character, so it"
                            + " names nothing at all");
        }
        if (rawTarget.indexOf('\\') >= 0 || hasDriveLetter(rawTarget)) {
            throw unsafeLink(
                    entry,
                    "its target \""
                            + rawTarget
                            + "\" is spelled with a backslash or a drive letter, and this"
                            + " extractor resolves neither rather than guessing which volume was"
                            + " meant");
        }
        if (rawTarget.startsWith("/")) {
            throw unsafeLink(
                    entry,
                    "its target \""
                            + rawTarget
                            + "\" is an absolute path, which is outside \""
                            + destination
                            + "\" wherever it points");
        }
        Path resolved = resolveThroughLinks(parentDirectoryOf(relative), rawTarget, 0);
        if (resolved == null) {
            throw unsafeLink(
                    entry,
                    "its target \""
                            + rawTarget
                            + "\" climbs above the file-system root or passes through more than "
                            + MAX_LINK_DEPTH
                            + " links, so it resolves nowhere inside \""
                            + destination
                            + "\"");
        }
        if (!resolved.startsWith(destination)) {
            throw unsafeLink(
                    entry,
                    "its target \""
                            + rawTarget
                            + "\" resolves to \""
                            + resolved
                            + "\", which is not inside \""
                            + destination
                            + "\"");
        }
        claim(relative, entry.name());
        createParentDirectories(relative);
        Files.createSymbolicLink(link, Path.of(rawTarget));
        return record(relative, ArchiveEntryType.SYMLINK, 0L);
    }

    private ExtractionRejectedException unsafeLink(ArchiveEntry entry, String detail) {
        return ExtractionRejectedException.entry(
                RejectionReason.UNSAFE_SYMLINK, entry.name(), " -- " + detail);
    }

    /*
     * WHERE A LINK REALLY POINTS, NOT WHERE ITS TEXT SUGGESTS.  Normalising a target lexically is
     * wrong once any component of it is itself a link: "sub/up/../outside", with "sub/up" a link to
     * the destination's own parent, normalises to "sub/outside" -- inside -- and resolves through
     * the file system to a sibling of the destination.  That is the chain that escapes only when it
     * is followed, so every component is stepped through here and every link on the way is read.
     *
     * Returns null when the walk climbs above the file-system root, which is outside anything.
     */
    private Path resolveThroughLinks(Path base, String rawTarget, int depth) throws IOException {
        if (depth > MAX_LINK_DEPTH) {
            return null;
        }
        Path cursor = base;
        for (String segment : rawTarget.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                cursor = cursor.getParent();
                if (cursor == null) {
                    return null;
                }
                continue;
            }
            Path next = cursor.resolve(segment);
            if (Files.isSymbolicLink(next)) {
                Path linkTarget = Files.readSymbolicLink(next);
                if (linkTarget.isAbsolute()) {
                    /*
                     * Carry on from there rather than returning: the segments after this one still
                     * belong to the answer, and a diagnostic that stopped here would report
                     * "/etc" where the link really reaches "/etc/passwd".
                     */
                    cursor = linkTarget.normalize();
                } else {
                    cursor =
                            resolveThroughLinks(next.getParent(), linkTarget.toString(), depth + 1);
                    if (cursor == null) {
                        return null;
                    }
                }
            } else {
                cursor = next;
            }
        }
        return cursor;
    }

    /**
     * Reads an entry's bytes without writing them, so that a skipped member still counts towards
     * the decompression-bomb ceilings.
     *
     * <p>A bomb that is never written is still a bomb: in named-member mode the container has to be
     * read through to reach the member, and an artefact that expands to a terabyte on the way there
     * has already cost the machine the time and the heat.
     *
     * @param entry the entry being skipped
     * @param content its bytes
     * @throws IOException if the bytes cannot be read
     * @throws ExtractionRejectedException if a decompression-bomb ceiling is reached
     */
    void discard(ArchiveEntry entry, InputStream content) throws IOException {
        transfer(entry.name(), content, null);
    }

    /*
     * The single metered copy.  Every uncompressed byte that leaves a container passes through
     * here, whether it is being written or thrown away, which is what makes the bomb accounting
     * the same for every artefact kind.
     */
    private long transfer(String entryName, InputStream content, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        long total = 0;
        int read;
        while ((read = content.read(buffer)) >= 0) {
            expand(entryName, read);
            if (out != null) {
                out.write(buffer, 0, read);
            }
            total += read;
        }
        return total;
    }

    /**
     * Accounts for uncompressed bytes and enforces the two size ceilings.
     *
     * @param entryName the entry the bytes came from, for the message
     * @param bytes how many bytes
     * @throws ExtractionRejectedException if the artefact has expanded past the total-size ceiling
     *     or past the expansion ratio
     */
    void expand(String entryName, long bytes) throws ExtractionRejectedException {
        expandedBytes += bytes;
        if (expandedBytes > limits.maxTotalUncompressedBytes()) {
            throw ExtractionRejectedException.entry(
                    RejectionReason.BOMB_TOTAL_UNCOMPRESSED_SIZE,
                    entryName,
                    " -- this extractor produces at most "
                            + limits.maxTotalUncompressedBytes()
                            + " bytes from one artefact");
        }
        if (expandedBytes > limits.ratioCheckedAboveBytes()
                && expandedBytes > (long) (limits.maxExpansionRatio() * artefactBytes)) {
            throw ExtractionRejectedException.entry(
                    RejectionReason.BOMB_EXPANSION_RATIO,
                    entryName,
                    " -- this extractor expands an artefact at most "
                            + limits.maxExpansionRatio()
                            + " times, and this one is "
                            + artefactBytes
                            + " bytes on disk");
        }
    }

    /*
     * Resolves a checked relative path under the destination and refuses to write through a
     * symbolic link.  The second half is the one that closes the two-entry attack: entry one is a
     * link, entry two is a file "under" it.  Every component that already exists is examined, so a
     * link created by this same extraction is caught as surely as one that was there before.
     */
    private Path resolveForWriting(String relative, String entryName)
            throws ExtractionRejectedException {
        Path cursor = destination;
        for (String segment : relative.split("/")) {
            cursor = cursor.resolve(segment);
            if (Files.isSymbolicLink(cursor)) {
                throw ExtractionRejectedException.entry(
                        RejectionReason.WRITE_THROUGH_SYMLINK,
                        entryName,
                        " -- \"" + destination.relativize(cursor) + "\" is that link");
            }
        }
        return cursor;
    }

    /*
     * Claimed BEFORE anything is written, never after.  Claiming afterwards would make this rule
     * unreachable: Files.newOutputStream(CREATE_NEW) and Files.copy both fail on an existing file,
     * so the second write would raise FileAlreadyExistsException and the duplicate check would be
     * a branch no input could take -- a check that cannot go red.
     */
    private void claim(String relative, String entryName) throws ExtractionRejectedException {
        if (!used.add(relative)) {
            throw ExtractionRejectedException.entry(
                    RejectionReason.DUPLICATE_ENTRY_NAME,
                    entryName,
                    " -- \"" + relative + "\" has already been written by this extraction");
        }
    }

    /*
     * The directory an entry is written into, derived from the checked relative path rather than
     * from Path.getParent().  The string is the authority here -- it has already been through the
     * one path rule -- and deriving the parent from it keeps a "the parent might be null" branch
     * out of a class where every branch has to mean something.
     */
    private Path parentDirectoryOf(String relative) {
        int lastSlash = relative.lastIndexOf('/');
        return lastSlash > 0 ? destination.resolve(relative.substring(0, lastSlash)) : destination;
    }

    private void createParentDirectories(String relative) throws IOException {
        Files.createDirectories(parentDirectoryOf(relative));
    }

    private PlacedFile record(String relative, ArchiveEntryType type, long sizeBytes) {
        PlacedFile file = new PlacedFile(relative, type, sizeBytes);
        placed.add(file);
        return file;
    }

    /**
     * Checks that a file the manifest expects really came out of the artefact.
     *
     * @param expectedPath the path the manifest expects, relative to the destination
     * @param artefactName the artefact's file name, for the message
     * @throws ExtractionRejectedException if nothing was written there
     */
    void requirePlaced(String expectedPath, String artefactName)
            throws ExtractionRejectedException {
        if (!used.contains(expectedPath)) {
            throw ExtractionRejectedException.artefact(
                    RejectionReason.EXPECTED_FILE_MISSING,
                    artefactName,
                    " -- the manifest expects \""
                            + expectedPath
                            + "\" and the artefact produced "
                            + placed.size()
                            + " path(s), none of them that one");
        }
    }

    /**
     * What this guard did.
     *
     * @return the report
     */
    ExtractionReport report() {
        return new ExtractionReport(placed, entriesRead, expandedBytes, artefactBytes);
    }
}
