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

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;

/**
 * One managed artefact: a tool build for a platform, where to get it, what it weighs, what is in it
 * and what is known about it.
 *
 * <p>This is the record the specification's <em>Managed artefact manifest</em> section describes,
 * and it exists so that <strong>no URL is ever constructed anywhere in the product</strong>. A
 * download URL, an expected digest, an artefact kind or an install path assembled in code is a fact
 * about upstream that lives outside the file that is supposed to hold every such fact.
 *
 * <h2>Exactly one of two extraction modes</h2>
 *
 * <p>An artefact is unpacked one of two ways and never both:
 *
 * <ul>
 *   <li><strong>Named member.</strong> {@link #member()} is present: the manifest names one member
 *       of the archive and the destination to write it to, and the archive's own entry name never
 *       places a file. This is forced by a real upstream artefact -- {@code
 *       rel-3-06-05/percolator-noxml-osx-portable.zip} holds a single member named {@code
 *       ../my_build/percolator-noxml/src/percolator}, which a correct traversal guard rejects and
 *       whose basename a correct implementation must not quietly take instead.
 *   <li><strong>Whole artefact.</strong> {@link #member()} is empty and {@link
 *       #expectedExecutablePath()} is present: the whole download goes into the install directory
 *       -- every entry for an archive, the single file for {@link ArtefactKind#BARE_EXECUTABLE} and
 *       {@link ArtefactKind#JAR} -- and afterwards the executable or JAR must be found at that
 *       path. PDV is the one multi-entry archive the product installs, so it is where the
 *       traversal, symlink and decompression-bomb guards actually run in production.
 * </ul>
 *
 * <p>A record declaring both modes, or neither, is rejected: the two answer the same question, so a
 * record carrying both has two answers and a record carrying neither has none.
 *
 * <h2>What a capability claim here is worth</h2>
 *
 * <p>Each capability arrives as a {@link DeclaredCapability}, carrying how it was established. No
 * Windows or macOS binary has ever been executed anywhere in this project, so no non-Linux row may
 * claim {@code observed-by-execution}. {@code R-TOOL-07} then makes the probe the final authority
 * on the host; these declarations exist so the Tool Manager can say what is known before the probe
 * has run, and so a fabricated row is visible as one.
 *
 * @param tool which tool this is a build of
 * @param version which release, as upstream names it
 * @param releaseTag the upstream release tag the artefact was published under
 * @param platform the operating system and architecture the artefact is built for -- not
 *     necessarily the host's; see {@link org.cometgui.domain.tools.ArtefactExecutability}
 * @param kind how the download is unpacked, chosen from this field and never from the URL suffix
 * @param url where it is fetched from, https and pinned ({@code D-008}: nothing is redistributed)
 * @param sizeBytes the download's length
 * @param hashes its MD5 and SHA-256; {@code R-SEC-02} makes the SHA-256 the trust mechanism and the
 *     MD5 a provenance record, never the other way round
 * @param member the one member to take out of the archive, in named-member mode; empty otherwise
 * @param expectedExecutablePath where the executable or JAR must be found once the whole artefact
 *     is unpacked, in whole-artefact mode; empty otherwise
 * @param executable whether the installed file needs the executable bit -- true for a binary, false
 *     for a JAR the bundled runtime launches
 * @param licence what upstream says this artefact is licensed under
 * @param companions the second downloads installed with it, possibly none
 * @param capabilities what the build is claimed to do, each with its evidence; possibly none, and
 *     none is honest where nothing has been established
 * @param advisories the caveats {@code R-PERC-11} requires shown at selection time and recorded in
 *     provenance
 * @param minimumHostRequirements what the machine must already have ({@code R-TOOL-03})
 * @param minimumCometGuiVersion the oldest CometGUI that understands this record
 */
public record ArtefactRecord(
        ToolName tool,
        ToolVersion version,
        String releaseTag,
        HostPlatform platform,
        ArtefactKind kind,
        URI url,
        long sizeBytes,
        FileHashes hashes,
        Optional<ArchiveMember> member,
        Optional<String> expectedExecutablePath,
        boolean executable,
        ArtefactLicence licence,
        List<ArtefactCompanion> companions,
        List<DeclaredCapability> capabilities,
        List<ToolAdvisory> advisories,
        MinimumHostRequirements minimumHostRequirements,
        ToolVersion minimumCometGuiVersion) {

    /**
     * Validates the record and takes defensive, immutable copies of its three lists.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the record declares both extraction modes or neither, if
     *     the release tag is blank, if the size is not positive, if the URL is not an absolute
     *     https URL, if a whole-artefact path is not a relative path inside the install directory,
     *     if a capability or a gated capability belongs to another tool, if a capability, an
     *     advisory identifier or a companion identifier appears twice, or if two files would be
     *     installed at the same path -- naming the field
     */
    public ArtefactRecord {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        releaseTag = ArtefactValues.requiredText(releaseTag, "releaseTag");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(kind, "kind");
        url = ArtefactValues.downloadUrl(url, "url");
        sizeBytes = ArtefactValues.positiveSize(sizeBytes, "sizeBytes");
        Objects.requireNonNull(hashes, "hashes");
        Objects.requireNonNull(member, "member");
        expectedExecutablePath = checkedWholeArtefactPath(expectedExecutablePath);
        requireExactlyOneExtractionMode(member, expectedExecutablePath);
        Objects.requireNonNull(licence, "licence");
        companions = checkedCompanions(companions, tool);
        capabilities = checkedCapabilities(capabilities, tool);
        advisories = checkedAdvisories(advisories);
        Objects.requireNonNull(minimumHostRequirements, "minimumHostRequirements");
        Objects.requireNonNull(minimumCometGuiVersion, "minimumCometGuiVersion");
        requireDistinctInstallPaths(member, expectedExecutablePath, companions);
    }

    private static Optional<String> checkedWholeArtefactPath(Optional<String> path) {
        Objects.requireNonNull(path, "expectedExecutablePath");
        return path.map(
                value -> ArtefactValues.installRelativePath(value, "expectedExecutablePath"));
    }

    private static void requireExactlyOneExtractionMode(
            Optional<ArchiveMember> member, Optional<String> expectedExecutablePath) {
        if (member.isPresent() && expectedExecutablePath.isPresent()) {
            throw new IllegalArgumentException(
                    "a record declares one extraction mode, and this one declares both: \"member\""
                            + " names a single member to take out of the archive and"
                            + " \"expectedExecutablePath\" unpacks the whole artefact, so a record"
                            + " carrying both has two answers to one question");
        }
        if (member.isEmpty() && expectedExecutablePath.isEmpty()) {
            throw new IllegalArgumentException(
                    "a record declares one extraction mode, and this one declares neither: give"
                            + " \"member\" to name a single member and where it is installed, or"
                            + " \"expectedExecutablePath\" to unpack the whole artefact and say"
                            + " where the executable ends up");
        }
    }

    private static List<ArtefactCompanion> checkedCompanions(
            List<ArtefactCompanion> companions, ToolName tool) {
        List<ArtefactCompanion> copy =
                List.copyOf(Objects.requireNonNull(companions, "companions"));
        Set<String> identifiers = new LinkedHashSet<>();
        for (ArtefactCompanion companion : copy) {
            companion.requireGatesCapabilityOf(tool);
            if (!identifiers.add(companion.id())) {
                throw new IllegalArgumentException(
                        "companions names the companion \"" + companion.id() + "\" more than once");
            }
        }
        return copy;
    }

    private static List<DeclaredCapability> checkedCapabilities(
            List<DeclaredCapability> capabilities, ToolName tool) {
        List<DeclaredCapability> copy =
                List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        Set<ToolCapability> seen = EnumSet.noneOf(ToolCapability.class);
        for (DeclaredCapability declared : copy) {
            declared.capability().requireBelongsTo(tool);
            if (!seen.add(declared.capability())) {
                throw new IllegalArgumentException(
                        "capabilities declares " + declared.capability().id() + " more than once");
            }
        }
        return copy;
    }

    private static List<ToolAdvisory> checkedAdvisories(List<ToolAdvisory> advisories) {
        List<ToolAdvisory> copy = List.copyOf(Objects.requireNonNull(advisories, "advisories"));
        Set<String> identifiers = new LinkedHashSet<>();
        for (ToolAdvisory advisory : copy) {
            if (!identifiers.add(advisory.id())) {
                throw new IllegalArgumentException(
                        "advisories declares \"" + advisory.id() + "\" more than once");
            }
        }
        return copy;
    }

    /*
     * Two files installed at one path is one file installed and one lost, and which of the two
     * survives depends on the order the installer happens to write them in.  The executable and
     * every companion member are checked together, because the collision that matters is between
     * an artefact and its own companions.
     */
    private static void requireDistinctInstallPaths(
            Optional<ArchiveMember> member,
            Optional<String> expectedExecutablePath,
            List<ArtefactCompanion> companions) {
        Set<String> destinations = new LinkedHashSet<>();
        destinations.add(
                member.map(ArchiveMember::installedPath)
                        .orElseGet(() -> expectedExecutablePath.orElseThrow()));
        for (ArtefactCompanion companion : companions) {
            for (String destination : companion.installedPaths()) {
                if (!destinations.add(destination)) {
                    throw new IllegalArgumentException(
                            "two files would be installed at \"" + destination + "\"");
                }
            }
        }
    }

    /**
     * Where the executable or JAR ends up, relative to the tool's install directory.
     *
     * <p>One accessor for both extraction modes, because every caller downstream -- the installer,
     * the completion marker, the prober, the Tool Manager -- wants the same answer and none of them
     * should have to know which mode this record uses.
     *
     * @return the member's install path in named-member mode, or the expected executable path in
     *     whole-artefact mode; never {@code null}, because exactly one of the two is present
     */
    public String executablePath() {
        return member.map(ArchiveMember::installedPath)
                .orElseGet(() -> expectedExecutablePath.orElseThrow());
    }

    /**
     * Whether this artefact is unpacked by taking one named member out of it.
     *
     * @return {@code true} in named-member mode, {@code false} in whole-artefact mode
     */
    public boolean isSingleMemberExtraction() {
        return member.isPresent();
    }

    /**
     * How this record is named in a diagnostic: the tool, the version as upstream writes it, and
     * the platform.
     *
     * <p>Every part of it is either a constant of this product or a version string that has already
     * parsed as two to four numeric components, so putting it in a message discloses nothing a
     * manifest reader would rather not disclose.
     *
     * @return for example {@code percolator 3.07.1 linux-x86-64}
     */
    public String describe() {
        return tool.id() + " " + version.text() + " " + platform.id();
    }

    /**
     * The companions installed with this artefact, immutable and in manifest order.
     *
     * @return the companions, possibly empty
     */
    @Override
    public List<ArtefactCompanion> companions() {
        return List.copyOf(companions);
    }

    /**
     * What the build is claimed to do, immutable and in manifest order.
     *
     * @return the declared capabilities, possibly empty
     */
    @Override
    public List<DeclaredCapability> capabilities() {
        return List.copyOf(capabilities);
    }

    /**
     * The caveats to show at selection time, immutable and in manifest order.
     *
     * @return the advisories, possibly empty
     */
    @Override
    public List<ToolAdvisory> advisories() {
        return List.copyOf(advisories);
    }

    /**
     * Every file this artefact installs, in the order the manifest declares them: the executable
     * first, then each companion's members.
     *
     * @return the install paths, relative to the tool's install directory, none repeated
     */
    public List<String> installedPaths() {
        List<String> paths = new ArrayList<>();
        paths.add(executablePath());
        for (ArtefactCompanion companion : companions) {
            paths.addAll(companion.installedPaths());
        }
        return List.copyOf(paths);
    }
}
