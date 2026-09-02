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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.registry.ArchiveMember;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Gets files out of an artefact without letting the artefact decide where they land.
 *
 * <p>This is {@code R-SEC-05}'s "once, in one class": every artefact kind the manifest can name --
 * {@code BARE_EXECUTABLE}, {@code ZIP}, {@code TAR_GZ}, {@code JAR}, {@code DEB_PAYLOAD} and {@code
 * PKG_PAYLOAD} -- is unpacked here, and every file any of them produces is placed by the one {@link
 * ExtractionGuard}. The kind is taken from the manifest field and <strong>never inferred from the
 * URL suffix</strong> ({@code R-TOOL-01}); the two switches below have no {@code default} branch,
 * so adding an artefact kind is a compilation error rather than a silent omission, and {@code
 * GuardBypassStructureTest} fails the build if a class in this package acquires the ability to
 * write a file without being the guard.
 *
 * <p>There is deliberately no {@code NSIS_PAYLOAD}. {@code D-002} option C deleted it; only a new
 * owner decision can bring it back.
 *
 * <h2>The two modes</h2>
 *
 * <dl>
 *   <dt>Named member
 *   <dd>The manifest names the member <em>and</em> the destination. The archive's own entry name is
 *       compared as text to find the member and never becomes a path. Every other entry is read
 *       through the decompression-bomb accounting and thrown away, because a bomb that is skipped
 *       has still been decompressed.
 *   <dt>Whole artefact
 *   <dd>Every entry is unpacked under the destination using its own name, which is where the
 *       traversal, absolute-path and symbolic-link guards do their work in production. PDV is the
 *       one artefact in the manifest that is installed this way.
 * </dl>
 *
 * <p>Both are needed and neither substitutes for the other. Tier 1's standing direction for this
 * phase is that the first must not become the reason the guard is never exercised, and that is why
 * {@code rel-3-06-05/percolator-noxml-osx-portable.zip} is a test case in both modes: extracted in
 * the first, rejected in the second.
 */
public final class ArtefactExtractor {

    /** The decompression-bomb ceilings this extractor runs with. */
    private final ExtractionLimits limits;

    /** Creates an extractor with the limits calibrated against the manifest's own artefacts. */
    public ArtefactExtractor() {
        this(ExtractionLimits.defaults());
    }

    /**
     * Creates an extractor with explicit limits.
     *
     * @param limits the decompression-bomb ceilings
     * @throws NullPointerException if {@code limits} is {@code null}
     */
    public ArtefactExtractor(ExtractionLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /**
     * The ceilings this extractor enforces.
     *
     * @return the limits
     */
    public ExtractionLimits limits() {
        return limits;
    }

    /**
     * Installs a manifest record's artefact, in whichever of the two modes the record declares.
     *
     * @param record the manifest record the artefact was downloaded for
     * @param artefact the downloaded file, already verified against its pinned SHA-256
     * @param destination the tool's install directory
     * @return what was written
     * @throws IOException if the artefact cannot be read or the destination cannot be written
     * @throws ExtractionRejectedException if any {@code R-SEC-05} check refuses it
     * @throws NullPointerException if any argument is {@code null}
     */
    public ExtractionReport extract(ArtefactRecord record, Path artefact, Path destination)
            throws IOException {
        Objects.requireNonNull(record, "record");
        if (record.isSingleMemberExtraction()) {
            return extractNamedMembers(
                    record.kind(),
                    artefact,
                    destination,
                    List.of(RequestedMember.of(record.member().orElseThrow())));
        }
        return extractWholeArtefact(
                record.kind(),
                artefact,
                destination,
                record.expectedExecutablePath().orElseThrow());
    }

    /**
     * Installs a companion download's members.
     *
     * <p>A companion is always named-member: it exists to take two schemas out of a Debian payload
     * or one library out of a bare download, never to unpack a package wholesale.
     *
     * @param companion the companion the artefact was downloaded for
     * @param artefact the downloaded file, already verified against its pinned SHA-256
     * @param destination the tool's install directory
     * @return what was written
     * @throws IOException if the artefact cannot be read or the destination cannot be written
     * @throws ExtractionRejectedException if any {@code R-SEC-05} check refuses it
     * @throws NullPointerException if any argument is {@code null}
     */
    public ExtractionReport extract(ArtefactCompanion companion, Path artefact, Path destination)
            throws IOException {
        Objects.requireNonNull(companion, "companion");
        List<RequestedMember> members = new ArrayList<>();
        for (ArchiveMember member : companion.members()) {
            members.add(RequestedMember.of(member));
        }
        return extractNamedMembers(companion.kind(), artefact, destination, members);
    }

    /**
     * Takes named members out of an artefact and writes them where the manifest says.
     *
     * @param kind how the artefact is unpacked, from the manifest field
     * @param artefact the downloaded file
     * @param destination the tool's install directory
     * @param members the members to take out, at least one
     * @return what was written
     * @throws IOException if the artefact cannot be read or the destination cannot be written
     * @throws ExtractionRejectedException if any {@code R-SEC-05} check refuses it, or if the
     *     artefact does not contain a member the manifest names
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if no member is named, or if a single-file artefact kind is
     *     asked for more than one
     */
    public ExtractionReport extractNamedMembers(
            ArtefactKind kind, Path artefact, Path destination, List<RequestedMember> members)
            throws IOException {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(artefact, "artefact");
        Objects.requireNonNull(destination, "destination");
        List<RequestedMember> wanted = List.copyOf(Objects.requireNonNull(members, "members"));
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException(
                    "named-member extraction needs at least one member: an extraction that takes"
                            + " nothing out of an artefact is a download with no reason to happen");
        }
        ExtractionGuard guard = new ExtractionGuard(destination, Files.size(artefact), limits);
        ArchiveReader container = containerOf(kind, artefact);
        if (container == null) {
            if (wanted.size() != 1) {
                throw new IllegalArgumentException(
                        "a "
                                + kind.id()
                                + " artefact is one file and has no members to choose between, so"
                                + " exactly one may be named, but "
                                + wanted.size()
                                + " were");
            }
            guard.copyWholeFile(
                    artefact,
                    wanted.get(0).installedPath(),
                    String.valueOf(artefact.getFileName()));
            return guard.report();
        }
        Map<String, RequestedMember> outstanding = new LinkedHashMap<>();
        for (RequestedMember member : wanted) {
            outstanding.put(matchKey(member.memberPath()), member);
        }
        try (ArchiveReader reader = container) {
            ArchiveEntry entry;
            while ((entry = reader.next()) != null) {
                guard.countEntry(entry.name());
                RequestedMember member = outstanding.remove(matchKey(entry.name()));
                if (member == null) {
                    guard.discard(entry, reader.content());
                    continue;
                }
                if (entry.type() != ArchiveEntryType.FILE) {
                    throw ExtractionRejectedException.entry(
                            RejectionReason.UNSUPPORTED_ENTRY_TYPE,
                            entry.name(),
                            " -- the manifest names it as a member to install and the artefact"
                                    + " holds it as a "
                                    + entry.type());
                }
                guard.placeFileAtDeclaredPath(entry, member.installedPath(), reader.content());
            }
        }
        if (!outstanding.isEmpty()) {
            String missing = outstanding.values().iterator().next().memberPath();
            throw ExtractionRejectedException.artefact(
                    RejectionReason.MEMBER_NOT_FOUND,
                    String.valueOf(artefact.getFileName()),
                    " -- the manifest names the member \""
                            + missing
                            + "\" and the artefact's "
                            + guard.report().entriesRead()
                            + " entries do not include it");
        }
        return guard.report();
    }

    /**
     * Unpacks a whole artefact under the destination, using the artefact's own entry names.
     *
     * @param kind how the artefact is unpacked, from the manifest field
     * @param artefact the downloaded file
     * @param destination the tool's install directory
     * @param expectedPath where the executable or JAR must be found afterwards
     * @return what was written
     * @throws IOException if the artefact cannot be read or the destination cannot be written
     * @throws ExtractionRejectedException if any {@code R-SEC-05} check refuses an entry, or if the
     *     expected file was not produced
     * @throws NullPointerException if any argument is {@code null}
     */
    public ExtractionReport extractWholeArtefact(
            ArtefactKind kind, Path artefact, Path destination, String expectedPath)
            throws IOException {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(artefact, "artefact");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(expectedPath, "expectedPath");
        String artefactName = String.valueOf(artefact.getFileName());
        ExtractionGuard guard = new ExtractionGuard(destination, Files.size(artefact), limits);
        ArchiveReader container = containerOf(kind, artefact);
        if (container == null) {
            guard.copyWholeFile(artefact, expectedPath, artefactName);
            return guard.report();
        }
        String expected = guard.checkedRelativePath(expectedPath, artefactName, false);
        try (ArchiveReader reader = container) {
            ArchiveEntry entry;
            while ((entry = reader.next()) != null) {
                guard.countEntry(entry.name());
                place(guard, entry, reader.content());
            }
        }
        guard.requirePlaced(expected, artefactName);
        return guard.report();
    }

    private static void place(ExtractionGuard guard, ArchiveEntry entry, InputStream content)
            throws IOException {
        switch (entry.type()) {
            case FILE -> guard.placeFileFromArchiveName(entry, content);
            case DIRECTORY -> guard.placeDirectory(entry);
            case SYMLINK -> {
                guard.expand(entry.name(), entry.declaredSizeBytes());
                guard.placeSymlink(entry);
            }
            case OTHER ->
                    throw ExtractionRejectedException.entry(
                            RejectionReason.UNSUPPORTED_ENTRY_TYPE,
                            entry.name(),
                            " -- a hard link, device node, socket or FIFO is a second name for"
                                    + " something that may be anywhere on this machine");
        }
    }

    /*
     * THE KIND DECIDES HOW THE ARTEFACT IS OPENED, AND THE MANIFEST DECIDES THE KIND (R-TOOL-01).
     * One switch expression with no default branch, so adding a constant to ArtefactKind stops this
     * compiling -- a stronger guarantee than any test, and the test exists as well.
     *
     * Null means the artefact is not a container.  BARE_EXECUTABLE and JAR are single files: a jar
     * is run by the bundled runtime rather than unpacked, and a bare executable has nothing inside
     * it, so for both of them the download IS the installed file -- which is also why neither has
     * an attacker-controlled name anywhere in its extraction.
     */
    private static ArchiveReader containerOf(ArtefactKind kind, Path artefact) throws IOException {
        return switch (kind) {
            case BARE_EXECUTABLE, JAR -> null;
            case ZIP -> new ZipArchiveReader(artefact);
            case TAR_GZ ->
                    new TarArchiveReader(
                            new GZIPInputStream(
                                    new BufferedInputStream(Files.newInputStream(artefact))),
                            String.valueOf(artefact.getFileName()));
            case DEB_PAYLOAD -> new DebPayloadReader(artefact);
            case PKG_PAYLOAD -> new PkgPayloadReader(artefact);
        };
    }

    /**
     * The form a member name is matched in, which is text handling and never path handling.
     *
     * <p>The {@code .deb} tar and the {@code .pkg} cpio both spell their entries {@code
     * ./usr/share/...} while the manifest writes {@code usr/share/...}, so a leading {@code .}
     * segment is dropped from both sides before they are compared. It is dropped <strong>as a
     * segment</strong>: trimming whatever an archive happens to begin with would also trim {@code
     * ../}, and this project has a real upstream artefact whose only member begins exactly that
     * way. Nothing here decides where a file goes -- {@link ExtractionGuard} does that, from the
     * manifest's own string.
     *
     * @param path a member name, from an archive header or from the manifest
     * @return the name with {@code .} segments and a trailing slash removed and nothing else
     *     changed
     */
    static String matchKey(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/", -1)) {
            if (!segment.isEmpty() && !".".equals(segment)) {
                segments.add(segment);
            }
        }
        return String.join("/", segments);
    }
}
