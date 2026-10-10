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

package org.cometgui.install.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.cache.ArtefactInstaller;
import org.cometgui.install.cache.InstallationCheck;
import org.cometgui.install.cache.PlatformFixups;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.cache.ToolProbe;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.probe.ProbeGatedOffers;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.ArtefactVerifier;
import org.cometgui.install.verify.VerifiedDownloader;
import org.cometgui.provenance.hashing.StreamingHashService;

/**
 * A {@link ManagedToolManager} over the shipped manifest, a real {@link ToolCache} on a temporary
 * directory, and the real download, verification, extraction and install path.
 *
 * <p>Two things are doubles and both say why: step 6's {@link ToolProbe}, because units 6 and 7
 * grade the three-stage probe against real binaries and this unit is about what the Tool Manager
 * does with its answer; and the {@link ProbeGatedOffers.LoadabilityCheck}, because "does this
 * installed binary still start" has to be answerable both ways for {@code R-TOOL-06}'s rule to be
 * observable. Everything else -- the manifest, the bytes, the transfer, the SHA-256, the
 * extraction, the atomic move, the completion marker -- is the product's own.
 */
final class ToolManagerHarness {

    /** An executor that runs each install on its own daemon thread, as production does. */
    static final class BackgroundInstalls implements Executor {

        private final AtomicInteger sequence = new AtomicInteger();
        private final List<Thread> started = new ArrayList<>();

        @Override
        public synchronized void execute(Runnable command) {
            Thread thread =
                    new Thread(command, "cometgui-install-test-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            started.add(thread);
            thread.start();
        }

        /**
         * Waits for every install thread this executor started.
         *
         * @throws InterruptedException if the wait is interrupted
         */
        synchronized void awaitAll() throws InterruptedException {
            for (Thread thread : started) {
                thread.join(300_000);
            }
        }
    }

    /**
     * An executor that runs nothing until it is told to, so a test can cancel an install before its
     * first step has run -- which is the step-boundary case, and the only one this project had.
     */
    static final class QueuedInstalls implements Executor {

        private final Deque<Runnable> queued = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable command) {
            queued.add(command);
        }

        /** Runs everything queued, on the calling thread, in order. */
        synchronized void runQueued() {
            if (queued.isEmpty()) {
                throw new AssertionError(
                        "nothing was queued: install() did not hand any work to the executor, so"
                                + " this test would pass without an install existing");
            }
            while (!queued.isEmpty()) {
                queued.removeFirst().run();
            }
        }
    }

    /** Records what the offered-set gate asked about, and answers what the test chose. */
    static final class RecordedLoadability implements ProbeGatedOffers.LoadabilityCheck {

        private final Map<String, LoaderDiagnostic> refusals = new LinkedHashMap<>();
        private final List<String> asked = new ArrayList<>();
        private boolean mustNotBeAsked;

        /**
         * Makes being asked at all the failure, for the rule that a build nobody has installed has
         * no binary to run and is therefore never asked.
         *
         * @return this
         */
        synchronized RecordedLoadability mustNotBeAsked() {
            this.mustNotBeAsked = true;
            return this;
        }

        /**
         * Makes one build refuse to start.
         *
         * @param record the build
         * @param diagnostic why it will not start
         * @return this
         */
        synchronized RecordedLoadability refusing(
                ArtefactRecord record, LoaderDiagnostic diagnostic) {
            refusals.put(record.describe(), diagnostic);
            return this;
        }

        /**
         * The builds it was asked about, in order.
         *
         * @return their descriptions
         */
        synchronized List<String> asked() {
            return List.copyOf(asked);
        }

        @Override
        public synchronized Optional<LoaderDiagnostic> refusalFor(ArtefactRecord record) {
            asked.add(record.describe());
            if (mustNotBeAsked) {
                throw new AssertionError(
                        "the offered-set gate ran a binary for "
                                + record.describe()
                                + ", and this test exists to prove it does not: nothing is"
                                + " installed, so there is no binary to run, and an artefact"
                                + " nobody has fetched has not failed to start");
            }
            return Optional.ofNullable(refusals.get(record.describe()));
        }
    }

    private final ToolCache cache;
    private final ArtefactInstaller installer;
    private final ManagedToolManager manager;
    private final RecordedLoadability loadability;

    private ToolManagerHarness(
            Path root,
            ArtefactManifest manifest,
            HostPlatform host,
            HostRuntimeVersions versions,
            org.cometgui.install.download.ArtefactFetcher fetcher,
            ToolProbe probe,
            RecordedLoadability loadability,
            Map<ToolName, LocalBinaryRegistrar> registrars,
            Executor installs) {
        StreamingHashService hashes = new StreamingHashService();
        this.cache = new ToolCache(root, hashes);
        this.installer =
                new ArtefactInstaller(
                        cache,
                        new VerifiedDownloader(fetcher, new ArtefactVerifier(hashes))::fetch,
                        new ArtefactExtractor(),
                        new PlatformFixups(
                                HostOperatingSystem.LINUX,
                                (command, listener) -> {
                                    throw new AssertionError(
                                            "the Linux fix-ups started a process: " + command);
                                }),
                        probe,
                        hashes,
                        Clock.systemUTC());
        this.loadability = loadability;
        this.manager =
                new ManagedToolManager(
                        manifest, host, versions, installer, loadability, registrars, installs);
    }

    /**
     * A builder, because a test names only the two or three things it varies.
     *
     * @param root the cache root, a temporary directory
     * @return the builder
     */
    static Builder at(Path root) {
        return new Builder(root);
    }

    /** What the tests vary. */
    static final class Builder {

        private final Path root;
        private HostPlatform host = ToolManagerFixtures.LINUX;
        private HostRuntimeVersions versions = ToolManagerFixtures.DEBIAN_12;
        private org.cometgui.install.download.ArtefactFetcher fetcher =
                request -> {
                    throw new IOException(
                            "no fetcher was given to this harness, and "
                                    + request.source()
                                    + " was asked for");
                };
        private ToolProbe probe = FixedProbe.answering();
        private RecordedLoadability loadability = new RecordedLoadability();
        private Map<ToolName, LocalBinaryRegistrar> registrars = Map.of();
        private Executor installs = new BackgroundInstalls();
        private ArtefactManifest manifest;

        private Builder(Path root) {
            this.root = root;
        }

        Builder on(HostRuntimeVersions hostVersions) {
            this.versions = hostVersions;
            return this;
        }

        Builder platform(HostPlatform hostPlatform) {
            this.host = hostPlatform;
            return this;
        }

        Builder fetching(org.cometgui.install.download.ArtefactFetcher artefactFetcher) {
            this.fetcher = artefactFetcher;
            return this;
        }

        Builder probing(ToolProbe toolProbe) {
            this.probe = toolProbe;
            return this;
        }

        Builder loadability(RecordedLoadability check) {
            this.loadability = check;
            return this;
        }

        Builder registering(ToolName tool, LocalBinaryRegistrar registrar) {
            this.registrars = Map.of(tool, registrar);
            return this;
        }

        /**
         * Registrars for more than one tool, for the rule that a local row sits with its own tool
         * and with no other.
         *
         * @param toolRegistrars the registrars, by tool
         * @return this builder
         */
        Builder registeringAll(Map<ToolName, LocalBinaryRegistrar> toolRegistrars) {
            this.registrars = Map.copyOf(toolRegistrars);
            return this;
        }

        /**
         * A manifest other than the shipped one, for a rule the shipped data cannot reach.
         *
         * @param artefacts the manifest
         * @return this builder
         */
        Builder over(ArtefactManifest artefacts) {
            this.manifest = artefacts;
            return this;
        }

        Builder installingOn(Executor executor) {
            this.installs = executor;
            return this;
        }

        ToolManagerHarness build() throws IOException {
            return new ToolManagerHarness(
                    root,
                    manifest == null ? ToolManagerFixtures.shippedManifest() : manifest,
                    host,
                    versions,
                    fetcher,
                    probe,
                    loadability,
                    registrars,
                    installs);
        }
    }

    /**
     * The port under test.
     *
     * @return the manager
     */
    ManagedToolManager manager() {
        return manager;
    }

    /**
     * The cache the manager answers from.
     *
     * @return the cache
     */
    ToolCache cache() {
        return cache;
    }

    /**
     * The installer the manager drives.
     *
     * @return the installer
     */
    ArtefactInstaller installer() {
        return installer;
    }

    /**
     * What the offered-set gate asked about.
     *
     * @return the recorded loadability check
     */
    RecordedLoadability loadability() {
        return loadability;
    }

    /**
     * The {@code R-TOOL-04} verdict on one record.
     *
     * @param record the record
     * @return the verdict
     * @throws IOException if the directory cannot be read
     */
    InstallationCheck verify(ArtefactRecord record) throws IOException {
        return cache.verify(record);
    }

    /**
     * The staging directories that exist right now for one record.
     *
     * @param record the record
     * @return the paths, empty when every attempt cleaned up after itself
     * @throws IOException if the directory cannot be read
     */
    List<Path> stagingDirectories(ArtefactRecord record) throws IOException {
        Path staging = cache.stagingRoot(record.tool(), record.version(), record.platform());
        if (!Files.isDirectory(staging)) {
            return List.of();
        }
        try (var entries = Files.list(staging)) {
            return entries.sorted().toList();
        }
    }
}
