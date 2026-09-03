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
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.cache.ArtefactInstaller;
import org.cometgui.install.cache.InstallationCheck;
import org.cometgui.install.cache.PlatformFixups;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.download.HttpDownloader;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.probe.LoaderOutputClassifier;
import org.cometgui.install.probe.ManifestAlternatives;
import org.cometgui.install.probe.ProbeGatedOffers;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.ArtefactVerifier;
import org.cometgui.install.verify.VerifiedDownloader;
import org.cometgui.provenance.hashing.StreamingHashService;

/**
 * One {@link ManagedToolManager}, wired the way {@code cometgui-app} wires it, over the shipped
 * manifest and a temporary cache.
 *
 * <p>Everything is the real thing except two seams, and each is real in the way that matters:
 *
 * <ul>
 *   <li><strong>The transport is real.</strong> The product's own {@code HttpDownloader} fetches
 *       over real HTTP from {@link MirrorServer}, and the product's own {@code ArtefactVerifier}
 *       checks the bytes against the SHA-256 the shipped manifest pins. Only the host part of the
 *       URL is rewritten.
 *   <li><strong>The probe is scripted</strong>, because the real one needs a binary of every tool
 *       and a host that can fail to load one. It is exercised for real from {@code cometgui-app},
 *       where the tool adapters are visible; here it is {@link ScriptedProbe}, and it records every
 *       call so that "the binary was never reached" is an assertion rather than an assumption.
 * </ul>
 *
 * <p>The manifest is <strong>the shipped one</strong>, read from the class path, and not a fixture.
 * A fixture contains what the rule needs; the real manifest contains a 99 MB download carried on
 * five platforms, one tool with two builds of one version on one host, and a version that is
 * published for two platforms and not for this one -- and every one of those is a case this class's
 * tests are about.
 */
final class ToolManagerHarness implements AutoCloseable {

    /** The host every test here answers for. */
    static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /** A fixed clock, so a completion marker's timestamp is not a source of variation. */
    static final Instant INSTALLED_AT = Instant.parse("2026-09-03T09:10:11.121Z");

    private final ArtefactManifest manifest;
    private final MirrorServer mirror;
    private final HttpDownloader downloader;
    private final ToolCache cache;
    private final ScriptedProbe probe;
    private final ProbeRefusalLog refusals;
    private final FakeRegistrar registrar;
    private final ExecutorService installs;
    private final ManagedToolManager manager;

    private final HostPlatform host;

    private ToolManagerHarness(
            Path cacheRoot,
            HostPlatform host,
            HostRuntimeVersions runtimes,
            HashService hashes,
            java.util.concurrent.Executor installsOn)
            throws IOException {
        this.host = host;
        this.manifest = ArtefactManifestReader.readFromClasspath();
        this.mirror = new MirrorServer();
        this.downloader = new HttpDownloader();
        this.cache = new ToolCache(cacheRoot, hashes);
        this.probe = new ScriptedProbe();
        this.refusals = new ProbeRefusalLog();
        this.registrar = new FakeRegistrar();
        this.installs = Executors.newSingleThreadExecutor();
        VerifiedDownloader verified =
                new VerifiedDownloader(downloader, new ArtefactVerifier(hashes));
        ArtefactInstaller installer =
                new ArtefactInstaller(
                        cache,
                        (source, destination, expected, size, listener, cancellation) ->
                                verified.fetch(
                                        mirror.addressOf(source),
                                        destination,
                                        expected,
                                        size,
                                        listener,
                                        cancellation),
                        new ArtefactExtractor(),
                        PlatformFixups.forHost(host),
                        refusals.recording(probe),
                        hashes,
                        Clock.fixed(INSTALLED_AT, ZoneOffset.UTC));
        LoaderOutputClassifier classifier = new LoaderOutputClassifier(host, runtimes);
        ManifestAlternatives alternatives = new ManifestAlternatives(manifest, host, runtimes);
        this.manager =
                new ManagedToolManager(
                        manifest,
                        host,
                        new ProbeGatedOffers(runtimes, classifier, alternatives::forArtefact),
                        classifier,
                        alternatives::forArtefact,
                        refusals,
                        installer,
                        registrar,
                        installsOn == null ? installs : installsOn);
    }

    /**
     * A harness on a Debian 12 host: glibc 2.36 and GLIBCXX 3.4.30, which is what this project's
     * own machine reports and what every manifest floor was measured against.
     *
     * @param cacheRoot the temporary cache root
     * @return the harness
     * @throws IOException if the manifest or the loopback socket cannot be opened
     */
    static ToolManagerHarness onDebian12(Path cacheRoot) throws IOException {
        return new ToolManagerHarness(
                cacheRoot, LINUX, debian12(), new StreamingHashService(), null);
    }

    /**
     * A Debian 12 harness whose installs run wherever the caller says.
     *
     * @param cacheRoot the temporary cache root
     * @param installsOn where an install runs
     * @return the harness
     * @throws IOException if the manifest or the loopback socket cannot be opened
     */
    static ToolManagerHarness onDebian12(Path cacheRoot, java.util.concurrent.Executor installsOn)
            throws IOException {
        return new ToolManagerHarness(
                cacheRoot, LINUX, debian12(), new StreamingHashService(), installsOn);
    }

    /**
     * A harness on a 64-bit ARM Linux machine, which upstream publishes a Comet build for and no
     * Percolator build of any version for.
     *
     * @param cacheRoot the temporary cache root
     * @return the harness
     * @throws IOException if the manifest or the loopback socket cannot be opened
     */
    static ToolManagerHarness onLinuxAarch64(Path cacheRoot) throws IOException {
        return new ToolManagerHarness(
                cacheRoot,
                new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.AARCH64),
                debian12(),
                new StreamingHashService(),
                null);
    }

    /**
     * A harness on a host with a chosen C library version and no readable C++ runtime.
     *
     * @param cacheRoot the temporary cache root
     * @param glibc the C library version this host reports
     * @return the harness
     * @throws IOException if the manifest or the loopback socket cannot be opened
     */
    static ToolManagerHarness onHostWithGlibc(Path cacheRoot, String glibc) throws IOException {
        return new ToolManagerHarness(
                cacheRoot,
                LINUX,
                new HostRuntimeVersions(Optional.of(GlibcVersion.parse(glibc)), Optional.empty()),
                new StreamingHashService(),
                null);
    }

    /**
     * A harness whose hashing service refuses to read anything, so that a cache entry which cannot
     * be verified is a case a test can produce.
     *
     * @param cacheRoot the temporary cache root
     * @param hashes the hashing service to use
     * @return the harness
     * @throws IOException if the manifest or the loopback socket cannot be opened
     */
    static ToolManagerHarness hashingWith(Path cacheRoot, HashService hashes) throws IOException {
        return new ToolManagerHarness(cacheRoot, LINUX, debian12(), hashes, null);
    }

    /**
     * What this project's own host reports.
     *
     * @return the runtimes
     */
    static HostRuntimeVersions debian12() {
        return new HostRuntimeVersions(
                Optional.of(GlibcVersion.parse("2.36")), Optional.of(GlibcVersion.parse("3.4.30")));
    }

    ManagedToolManager manager() {
        return manager;
    }

    ArtefactManifest manifest() {
        return manifest;
    }

    MirrorServer mirror() {
        return mirror;
    }

    ScriptedProbe probe() {
        return probe;
    }

    ProbeRefusalLog refusals() {
        return refusals;
    }

    FakeRegistrar registrar() {
        return registrar;
    }

    ToolCache cache() {
        return cache;
    }

    /**
     * The record the Tool Manager would install for one tool and version on this host.
     *
     * @param tool the tool
     * @param version the version, as upstream spells it
     * @return the record
     */
    HostPlatform host() {
        return host;
    }

    ArtefactRecord recordOf(ToolName tool, String version) {
        List<org.cometgui.install.registry.ArtefactSelection> selected =
                manifest.select(host, tool, ToolVersion.parse(version));
        if (selected.isEmpty()) {
            throw new AssertionError(
                    "the shipped manifest no longer offers "
                            + tool.id()
                            + " "
                            + version
                            + " on "
                            + host.id());
        }
        return selected.get(0).artefact();
    }

    /**
     * Installs one build and waits for the terminal report.
     *
     * @param tool the tool
     * @param version the version, as upstream spells it
     * @return the phase the install ended in
     */
    InstallPhase install(ToolName tool, String version) {
        RecordingListener listener = new RecordingListener();
        manager.install(tool, ToolVersion.parse(version), listener);
        return listener.awaitTerminal();
    }

    /**
     * Installs one build, letting the caller act on every progress report.
     *
     * @param tool the tool
     * @param version the version, as upstream spells it
     * @param listener what to record and do while it runs
     * @return the handle, so a test can cancel through it
     */
    InstallHandle installWith(ToolName tool, String version, RecordingListener listener) {
        return manager.install(tool, ToolVersion.parse(version), listener);
    }

    /**
     * The {@code R-TOOL-04} verdict on one build.
     *
     * @param record the record
     * @return the verdict
     * @throws IOException if the directory cannot be read
     */
    InstallationCheck verify(ArtefactRecord record) throws IOException {
        return cache.verify(record);
    }

    /**
     * The one offer naming a tool and a version, for the assertions that are about a single row.
     *
     * @param tool the tool
     * @param version the version, as upstream spells it
     * @return the offer
     */
    ToolOffer offerOf(ToolName tool, String version) {
        ToolVersion wanted = ToolVersion.parse(version);
        for (ToolOffer offer : manager.offers()) {
            if (offer.tool() == tool && offer.version().equals(wanted)) {
                return offer;
            }
        }
        throw new AssertionError(
                "no offer names " + tool.id() + " " + version + " among " + describeOffers());
    }

    /**
     * Every offer as {@code tool version STATE}, which is what most assertions here compare.
     *
     * @return the descriptions, in offer order
     */
    List<String> describeOffers() {
        return manager.offers().stream()
                .map(
                        offer ->
                                offer.tool().id()
                                        + " "
                                        + offer.version().text()
                                        + " "
                                        + offer.state().name())
                .toList();
    }

    @Override
    public void close() throws IOException {
        installs.shutdownNow();
        try {
            if (!installs.awaitTermination(Duration.ofSeconds(30).toSeconds(), TimeUnit.SECONDS)) {
                throw new AssertionError("an install thread would not stop within 30 seconds");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        downloader.close();
        mirror.close();
    }
}
