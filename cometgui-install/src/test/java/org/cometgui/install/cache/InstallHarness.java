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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.ArtefactVerifier;
import org.cometgui.install.verify.VerifiedDownloader;
import org.cometgui.provenance.hashing.StreamingHashService;

/**
 * One installer, wired the way production wires it, over a temporary cache root.
 *
 * <p>Everything but the transport and the probe is the real thing: the real {@link
 * VerifiedDownloader} composing the real {@link ArtefactVerifier}, the real {@link
 * ArtefactExtractor} with its {@code R-SEC-05} guards, the real {@link StreamingHashService}, the
 * real {@link ToolCache}. The transport is a {@link FakeFetcher} because a test does not need a
 * server to prove an install, and the probe is a stub because probing is phase 05 units 6 and 7.
 *
 * <p><strong>The cache root is a temporary directory.</strong> Nothing here goes near a real home
 * directory, which is why {@link ToolCache} takes its root as an argument.
 */
final class InstallHarness {

    /** A fixed clock, so the marker's timestamp is a value a test can pin by hand. */
    static final Instant INSTALLED_AT = Instant.parse("2026-09-02T11:22:33.444Z");

    /** The canonical rendering of {@link #INSTALLED_AT}, hand-typed. */
    static final String INSTALLED_AT_TEXT = "2026-09-02T11:22:33.444Z";

    /** The cache root. */
    private final Path root;

    /** The cache. */
    private final ToolCache cache;

    /** What serves artefact bytes. */
    private final FakeFetcher fetcher = new FakeFetcher();

    /** What step 6 calls. */
    private final RecordingProbe probe;

    /** What step 5 does. */
    private final PlatformFixups fixups;

    /** What is under test. */
    private final ArtefactInstaller installer;

    /** Every progress report, in order. */
    private final RecordingInstallListener listener = new RecordingInstallListener();

    InstallHarness(Path root, RecordingProbe probe, HostOperatingSystem host) {
        this.root = root;
        this.probe = probe;
        this.fixups = new PlatformFixups(host);
        StreamingHashService hashes = new StreamingHashService();
        this.cache = new ToolCache(root, hashes);
        VerifiedDownloader downloader =
                new VerifiedDownloader(fetcher, new ArtefactVerifier(hashes));
        this.installer =
                new ArtefactInstaller(
                        cache,
                        downloader::fetch,
                        new ArtefactExtractor(),
                        fixups,
                        probe,
                        hashes,
                        Clock.fixed(INSTALLED_AT, ZoneOffset.UTC));
    }

    /**
     * The cache root.
     *
     * @return the root
     */
    Path root() {
        return root;
    }

    /**
     * The cache under test.
     *
     * @return the cache
     */
    ToolCache cache() {
        return cache;
    }

    /**
     * What serves artefact bytes.
     *
     * @return the fetcher
     */
    FakeFetcher fetcher() {
        return fetcher;
    }

    /**
     * What step 6 calls.
     *
     * @return the probe
     */
    RecordingProbe probe() {
        return probe;
    }

    /**
     * What step 5 does.
     *
     * @return the fix-ups
     */
    PlatformFixups fixups() {
        return fixups;
    }

    /**
     * The installer under test.
     *
     * @return the installer
     */
    ArtefactInstaller installer() {
        return installer;
    }

    /**
     * Every progress report, in order.
     *
     * @return the listener
     */
    RecordingInstallListener listener() {
        return listener;
    }

    /**
     * A harness whose probe confirms nothing and whose host is Linux.
     *
     * @param root the cache root
     * @return the harness
     */
    static InstallHarness at(Path root) {
        return new InstallHarness(root, RecordingProbe.confirming(), HostOperatingSystem.LINUX);
    }

    /**
     * Serves the record's artefact and every companion's, from the fixture bytes.
     *
     * @param record the record
     * @param artefact the artefact bytes
     * @param companionBytes the companions' bytes, in the record's own order
     * @return this harness
     */
    InstallHarness serving(ArtefactRecord record, byte[] artefact, byte[]... companionBytes) {
        fetcher.serve(record.url(), artefact);
        for (int index = 0; index < companionBytes.length; index++) {
            fetcher.serve(record.companions().get(index).url(), companionBytes[index]);
        }
        return this;
    }

    /**
     * Installs a record with no cancellation.
     *
     * @param record the record
     * @return the installation
     * @throws IOException if the install fails
     */
    Installation install(ArtefactRecord record) throws IOException {
        return installer.install(record, listener, DownloadCancellation.never());
    }

    /**
     * The tool directory a record installs into.
     *
     * @param record the record
     * @return the directory
     */
    Path directoryOf(ArtefactRecord record) {
        return cache.toolDirectory(record);
    }

    /**
     * The completion marker file of a record's install.
     *
     * @param record the record
     * @return the marker file
     */
    Path markerOf(ArtefactRecord record) {
        return directoryOf(record).resolve(InstallationMarker.FILE_NAME);
    }

    /**
     * The {@code R-TOOL-04} verdict on a record's install.
     *
     * @param record the record
     * @return the verdict
     * @throws IOException if the directory cannot be read
     */
    InstallationCheck verify(ArtefactRecord record) throws IOException {
        return cache.verify(record);
    }

    /**
     * Everything under {@code tools/}, as path to size and digest, for a before-and-after
     * comparison.
     *
     * @return the snapshot
     * @throws IOException if the tree cannot be read
     */
    Snapshot toolsSnapshot() throws IOException {
        return Snapshot.of(cache.toolsRoot());
    }

    /**
     * The staging directories that exist right now for one record.
     *
     * @param record the record
     * @return the paths, empty when every install cleaned up after itself
     * @throws IOException if the directory cannot be read
     */
    java.util.List<Path> stagingDirectories(ArtefactRecord record) throws IOException {
        Path staging = cache.stagingRoot(record.tool(), record.version(), record.platform());
        if (!Files.isDirectory(staging)) {
            return java.util.List.of();
        }
        try (var entries = Files.list(staging)) {
            return entries.sorted().toList();
        }
    }

    /**
     * The download cache directory for one record.
     *
     * @param record the record
     * @return the directory, which may not exist
     */
    Path downloadsOf(ArtefactRecord record) {
        return cache.downloadDirectory(record.tool(), record.version(), record.platform());
    }
}
