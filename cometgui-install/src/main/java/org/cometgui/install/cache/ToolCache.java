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

package org.cometgui.install.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Where installed tools live, and the one authority on whether a tool is installed.
 *
 * <h2>The layout</h2>
 *
 * <p>Under one root, following the specification's sketch:
 *
 * <pre>
 *   &lt;root&gt;/tools/&lt;tool&gt;/&lt;version&gt;/&lt;platform&gt;/     an installed tool
 *   &lt;root&gt;/cache/downloads/&lt;key&gt;/                  artefacts being fetched
 *   &lt;root&gt;/cache/staging/&lt;id&gt;/                     an install being built
 *   &lt;root&gt;/cache/locks/&lt;key&gt;.lock                  the R-TOOL-05 lock files
 * </pre>
 *
 * <p><strong>The root is a constructor argument and this class never looks at a home
 * directory.</strong> The real root is {@code ~/.comet-gui}, and <strong>{@code cometgui-app}'s
 * wiring decides that</strong> -- through {@link
 * org.cometgui.domain.ports.FileSystemAccess#applicationDataDirectory()}, in a later phase. Nothing
 * here, and no test of anything here, may write to a real home directory.
 *
 * <p>Everything is under one root so that {@code cache/staging} and {@code tools} are on the same
 * file system and step 7's move is a rename rather than a copy. A staging directory somewhere else
 * would turn the one atomic operation in this design into a copy that can be interrupted half way.
 *
 * <h2>Why the version directory is the normalised version</h2>
 *
 * <p>{@link ToolVersion} compares and equals on its numeric components, so {@code 3.09} and {@code
 * 3.09.0} are one version. If the directory were named from the text a version was parsed from, two
 * equal versions would map to two directories and an install made under one spelling would be
 * invisible under the other. The directory name is therefore {@link ToolVersion#toString()} -- the
 * normalised form -- and the marker records {@link ToolVersion#text()}, which is what upstream
 * calls the release and what a user interface shows. So Percolator 3.07.1 installs into {@code
 * percolator/3.7.1/} and calls itself {@code 3.07.1} everywhere a person reads it.
 *
 * <h2>{@code R-TOOL-04} is {@link #verify}</h2>
 *
 * <p>Nothing else in the product decides whether a tool is installed. {@link #verify} answers both
 * halves of the rule -- marker present, recorded checksums match -- by hashing the files the marker
 * names, every time it is asked. That is deliberate: a cached answer is a second place the truth
 * can live, and the digests are over the executable and its companions rather than over every one
 * of a 222-entry archive, so the cost is a few megabytes and not a hundred.
 */
public final class ToolCache {

    /** The directory under the root that holds installed tools. */
    public static final String TOOLS_DIRECTORY = "tools";

    /** The directory under the root that holds everything an install can throw away. */
    public static final String CACHE_DIRECTORY = "cache";

    /** Distinguishes the staging directories of two installs running in one JVM. */
    private static final AtomicInteger STAGING_SEQUENCE = new AtomicInteger();

    /** The cache root; absolute and normalised. */
    private final Path root;

    /** The project's one hasher, used to answer the second half of {@code R-TOOL-04}. */
    private final HashService hashes;

    /**
     * Creates a cache over a root directory.
     *
     * @param root where the cache lives; in production the application data directory, in a test a
     *     temporary directory. It does not have to exist yet.
     * @param hashes the project's hasher, in production {@code
     *     org.cometgui.provenance.hashing.StreamingHashService}
     * @throws NullPointerException if either argument is {@code null}
     */
    public ToolCache(Path root, HashService hashes) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.hashes = Objects.requireNonNull(hashes, "hashes");
    }

    /**
     * The cache root.
     *
     * @return the absolute, normalised root
     */
    public Path root() {
        return root;
    }

    /**
     * The directory holding every installed tool.
     *
     * @return {@code <root>/tools}
     */
    public Path toolsRoot() {
        return root.resolve(TOOLS_DIRECTORY);
    }

    /**
     * The directory holding everything an install may throw away.
     *
     * @return {@code <root>/cache}
     */
    public Path workingRoot() {
        return root.resolve(CACHE_DIRECTORY);
    }

    /**
     * Where one tool build is installed.
     *
     * @param tool which tool
     * @param version which release of it
     * @param platform which build
     * @return {@code <root>/tools/<tool>/<version>/<platform>}
     * @throws NullPointerException if any argument is {@code null}
     */
    public Path toolDirectory(ToolName tool, ToolVersion version, HostPlatform platform) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(platform, "platform");
        return toolsRoot().resolve(tool.id()).resolve(version.toString()).resolve(platform.id());
    }

    /**
     * Where a manifest record's artefact is installed.
     *
     * @param record the record
     * @return the install directory
     * @throws NullPointerException if {@code record} is {@code null}
     */
    public Path toolDirectory(ArtefactRecord record) {
        Objects.requireNonNull(record, "record");
        return toolDirectory(record.tool(), record.version(), record.platform());
    }

    /**
     * Where one tool build's artefacts are downloaded to.
     *
     * <p>Keyed by the artefact rather than by the install attempt, so that a transfer interrupted
     * by a crash is resumed by the next attempt instead of starting a 99 MB download again.
     *
     * @param tool which tool
     * @param version which release of it
     * @param platform which build
     * @return {@code <root>/cache/downloads/<key>}
     * @throws NullPointerException if any argument is {@code null}
     */
    public Path downloadDirectory(ToolName tool, ToolVersion version, HostPlatform platform) {
        return workingRoot().resolve("downloads").resolve(key(tool, version, platform));
    }

    /**
     * The lock file that serialises installs of one artefact.
     *
     * @param tool which tool
     * @param version which release of it
     * @param platform which build
     * @return {@code <root>/cache/locks/<key>.lock}
     * @throws NullPointerException if any argument is {@code null}
     */
    public Path lockFile(ToolName tool, ToolVersion version, HostPlatform platform) {
        return workingRoot().resolve("locks").resolve(key(tool, version, platform) + ".lock");
    }

    /**
     * Where one artefact's installs are staged.
     *
     * <p>Per artefact, not per attempt, and that is what makes the leftovers of a crashed install
     * safe to sweep: whoever holds that artefact's {@link InstallLock} is the only process that may
     * be building it, so it may delete everything here. A single shared staging directory would
     * make the same sweep a way to destroy another artefact's install in flight.
     *
     * @param tool which tool
     * @param version which release of it
     * @param platform which build
     * @return {@code <root>/cache/staging/<key>}
     * @throws NullPointerException if any argument is {@code null}
     */
    public Path stagingRoot(ToolName tool, ToolVersion version, HostPlatform platform) {
        return workingRoot().resolve("staging").resolve(key(tool, version, platform));
    }

    /**
     * A fresh staging directory, created, for one install attempt.
     *
     * <p>Unique per attempt and per process: two processes have no way to agree on a name, and a
     * crash leaves one behind for the next attempt to sweep. It is on the same file system as
     * {@link #toolsRoot()}, which is what makes step 7 a rename.
     *
     * @param tool which tool
     * @param version which release of it
     * @param platform which build
     * @return the created directory, empty
     * @throws IOException if it cannot be created
     */
    public Path createStagingDirectory(ToolName tool, ToolVersion version, HostPlatform platform)
            throws IOException {
        Path staging = stagingRoot(tool, version, platform);
        Files.createDirectories(staging);
        String name =
                ProcessHandle.current().pid()
                        + "-"
                        + STAGING_SEQUENCE.incrementAndGet()
                        + "-"
                        + UUID.randomUUID();
        return Files.createDirectory(staging.resolve(name));
    }

    /**
     * The identifier a download directory and a lock file are named from.
     *
     * @param tool which tool
     * @param version which release of it
     * @param platform which build
     * @return for example {@code percolator__3.7.1__linux-x86-64}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static String key(ToolName tool, ToolVersion version, HostPlatform platform) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(platform, "platform");
        return tool.id() + "__" + version + "__" + platform.id();
    }

    /**
     * Answers {@code R-TOOL-04} for one tool build.
     *
     * @param tool which tool
     * @param version which release of it
     * @param platform which build
     * @return what was found, and why
     * @throws IOException if the directory or a file in it cannot be read
     * @throws NullPointerException if any argument is {@code null}
     */
    public InstallationCheck verify(ToolName tool, ToolVersion version, HostPlatform platform)
            throws IOException {
        Path directory = toolDirectory(tool, version, platform);
        if (!Files.isDirectory(directory)) {
            return absent(
                    directory,
                    InstallationState.NOT_PRESENT,
                    "there is no directory at " + directory);
        }
        Path markerFile = directory.resolve(InstallationMarker.FILE_NAME);
        if (!Files.isRegularFile(markerFile)) {
            return absent(
                    directory,
                    InstallationState.NO_MARKER,
                    directory
                            + " holds no "
                            + InstallationMarker.FILE_NAME
                            + ", so an install was interrupted before it finished");
        }
        InstallationMarker marker;
        try {
            marker = InstallationMarker.parse(Files.readString(markerFile, StandardCharsets.UTF_8));
        } catch (MarkerFormatException unreadable) {
            return absent(
                    directory,
                    InstallationState.MARKER_UNREADABLE,
                    markerFile + " cannot be read: " + unreadable.getMessage());
        }
        if (!marker.describes(tool, version, platform)) {
            return new InstallationCheck(
                    InstallationState.MARKER_DESCRIBES_ANOTHER_ARTEFACT,
                    directory,
                    "the marker in "
                            + directory
                            + " describes "
                            + marker.describe()
                            + ", and this is the directory for "
                            + tool.id()
                            + " "
                            + version.text()
                            + " "
                            + platform.id(),
                    Optional.of(marker));
        }
        int present = countPayloadEntries(directory);
        if (present != marker.payloadEntryCount()) {
            return new InstallationCheck(
                    InstallationState.CONTENT_COUNT_MISMATCH,
                    directory,
                    "the marker in "
                            + directory
                            + " records "
                            + marker.payloadEntryCount()
                            + " installed file(s) and the directory holds "
                            + present,
                    Optional.of(marker));
        }
        for (RecordedFile recorded : marker.files()) {
            Path file = directory.resolve(recorded.path());
            if (!Files.isRegularFile(file)) {
                return new InstallationCheck(
                        InstallationState.FILE_MISSING,
                        directory,
                        "the marker in "
                                + directory
                                + " records \""
                                + recorded.path()
                                + "\" and there is no file at "
                                + file,
                        Optional.of(marker));
            }
            long size = Files.size(file);
            if (size != recorded.sizeBytes()) {
                return new InstallationCheck(
                        InstallationState.CHECKSUM_MISMATCH,
                        directory,
                        "\""
                                + recorded.path()
                                + "\" is "
                                + size
                                + " bytes and the marker in "
                                + directory
                                + " records "
                                + recorded.sizeBytes()
                                + ", so the entry is not the one that was installed",
                        Optional.of(marker));
            }
            FileHashes actual = hashes.hash(file);
            if (!actual.sha256().equals(recorded.hashes().sha256())) {
                return new InstallationCheck(
                        InstallationState.CHECKSUM_MISMATCH,
                        directory,
                        "\""
                                + recorded.path()
                                + "\" hashes to "
                                + actual.sha256()
                                + " and the marker in "
                                + directory
                                + " records "
                                + recorded.hashes().sha256()
                                + ", so the entry is not the one that was installed (R-TOOL-04)",
                        Optional.of(marker));
            }
        }
        return new InstallationCheck(
                InstallationState.INSTALLED,
                directory,
                marker.describe()
                        + " is installed at "
                        + directory
                        + ": "
                        + marker.files().size()
                        + " recorded file(s) verified, "
                        + present
                        + " file(s) present",
                Optional.of(marker));
    }

    /**
     * Answers {@code R-TOOL-04} for one manifest record.
     *
     * @param record the record
     * @return what was found, and why
     * @throws IOException if the directory or a file in it cannot be read
     * @throws NullPointerException if {@code record} is {@code null}
     */
    public InstallationCheck verify(ArtefactRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        return verify(record.tool(), record.version(), record.platform());
    }

    private static InstallationCheck absent(
            Path directory, InstallationState state, String detail) {
        return new InstallationCheck(state, directory, detail, Optional.empty());
    }

    /**
     * Counts the files an install placed in a directory, excluding the completion marker.
     *
     * <p>Everything that is not a directory counts, symbolic links included and not followed: the
     * figure has to mean the same thing when it is written and when it is checked, and following a
     * link would count what it points at instead.
     *
     * @param directory the tool directory
     * @return the number of entries
     * @throws IOException if the directory cannot be walked
     * @throws NullPointerException if {@code directory} is {@code null}
     */
    public static int countPayloadEntries(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Path marker = directory.resolve(InstallationMarker.FILE_NAME);
        int[] count = {0};
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (!file.equals(marker)) {
                            count[0]++;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        return count[0];
    }

    /**
     * Writes the completion marker, and makes it appear in one operation.
     *
     * <p>{@code R-TOOL-04}'s "written last" is only worth having if the marker itself cannot be
     * half written: a reader that saw a truncated marker would report {@link
     * InstallationState#MARKER_UNREADABLE} and discard a perfectly good install. So the bytes go to
     * a temporary name in the same directory and are renamed into place, which on every supported
     * file system is atomic.
     *
     * @param directory the tool directory the marker belongs in
     * @param marker the marker
     * @return the marker file
     * @throws IOException if it cannot be written
     * @throws NullPointerException if either argument is {@code null}
     */
    public static Path writeMarker(Path directory, InstallationMarker marker) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(marker, "marker");
        Path target = directory.resolve(InstallationMarker.FILE_NAME);
        Path temporary =
                directory.resolve(InstallationMarker.FILE_NAME + "." + UUID.randomUUID() + ".tmp");
        Files.writeString(temporary, marker.toJson(), StandardCharsets.UTF_8);
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    /**
     * Deletes a directory and everything in it, refusing anything outside this cache.
     *
     * <p>The guard is not decoration. This method is called on a path derived from a manifest
     * record, and a recursive delete that could be pointed anywhere is the worst defect an
     * installer can have; the check is cheap and it can fail, so it is tested.
     *
     * @param directory the directory to remove; doing nothing if it is not there
     * @throws IOException if it cannot be removed
     * @throws IllegalArgumentException if the directory is not inside this cache's root, naming
     *     both paths
     * @throws NullPointerException if {@code directory} is {@code null}
     */
    public void discard(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Path resolved = directory.toAbsolutePath().normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException(
                    "this cache only deletes paths beneath its own root, and "
                            + resolved
                            + " is not beneath "
                            + root);
        }
        deleteRecursively(resolved);
    }

    /**
     * Deletes a tree, following no symbolic link.
     *
     * <p>A staged extraction may contain symbolic links -- {@code R-SEC-05} allows one that stays
     * inside the destination -- and a delete that followed them would remove whatever they point
     * at.
     *
     * @param path the tree to remove; doing nothing if it is not there
     * @throws IOException if something cannot be removed
     */
    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(
                path,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException failed)
                            throws IOException {
                        // The superclass rethrows a non-null failure. Delegating rather than
                        // restating it keeps the decision in one place -- and keeps a branch out
                        // of this class that no test on this platform can take.
                        super.postVisitDirectory(directory, failed);
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    /**
     * Describes the cache by its root.
     *
     * @return a description for a log line or an exception message
     */
    @Override
    public String toString() {
        return "ToolCache[root=" + root + "]";
    }
}
