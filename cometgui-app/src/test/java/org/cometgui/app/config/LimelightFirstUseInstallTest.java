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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.limelight.LimelightConverterIdentity;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.workflow.limelight.LimelightConverterStatus;
import org.cometgui.workflow.limelight.LimelightProvisioning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 12 {@code AC-LL-01}, {@code R-LL-01}'s install half: the Limelight converter installed on
 * first use, from an EMPTY tool cache, by the product's own Tool Manager over the shipped manifest
 * -- the real downloader, SHA-256 check, atomic install and identity probe -- fetching the real
 * pinned v2.8.1 JAR from the gitignored artefact mirror through a loopback server, exactly as
 * {@code PdvFirstUseUiTest} installs PDV. Reached through {@link LimelightProvisioning}, the
 * workflow's one way to the converter; nothing here downloads.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason = "the install and the converter's identity probe have run on Linux only")
class LimelightFirstUseInstallTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    /** The pinned JAR's SHA-256, typed out, so a manifest edited to match other bytes is seen. */
    private static final String PINNED_SHA256 =
            "843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1";

    private static final String MIRRORED = "v2.8.1__cometPercolator2LimelightXML.jar";

    private static final ToolVersion VERSION = ToolVersion.parse("2.8.1");

    @TempDir private Path scratch;

    /** Every progress report of one install, and its end. */
    private static final class Progress {

        private final List<InstallPhase> phases = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch ended = new CountDownLatch(1);

        void report(InstallProgress progress) {
            phases.add(progress.phase());
            if (progress.phase().isTerminal()) {
                ended.countDown();
            }
        }

        InstallPhase awaitEnd() throws InterruptedException {
            assertTrue(
                    ended.await(5, TimeUnit.MINUTES), () -> "the install never ended: " + phases);
            return phases.get(phases.size() - 1);
        }
    }

    private static ToolManager managed(Path cache, LoopbackArtefactServer served)
            throws IOException {
        return ToolManagerWiring.toolManager(
                LINUX,
                DEBIAN_12,
                cache,
                new ProcessService(Clock.systemUTC()),
                Clock.systemUTC(),
                ToolManagerWiring.installThreads(),
                served);
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private static List<Path> filesUnder(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    @DisplayName(
            "AC-LL-01: from an empty cache the converter installs on first use through the Tool"
                    + " Manager, with the pinned SHA-256, and is usable; an installed JAR that no"
                    + " longer verifies is not offered as installed")
    void firstUseInstallsThePinnedConverter() throws IOException, InterruptedException {
        Path cache = scratch.toRealPath().resolve("cometgui-data");
        ArtefactRecord record = ArtefactMirror.record(ToolName.LIMELIGHT_CONVERTER, "2.8.1", LINUX);
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer()
                        .serve(record.url(), ArtefactMirror.artefact(MIRRORED))) {
            LimelightProvisioning provisioning = new LimelightProvisioning(managed(cache, served));
            LimelightConverterStatus before = provisioning.status();
            List<Path> cachedBefore = filesUnder(cache);

            Progress progress = new Progress();
            long asked = System.nanoTime();
            InstallHandle handle = provisioning.install(VERSION, progress::report);
            Duration returnedAfter = Duration.ofNanos(System.nanoTime() - asked);
            InstallPhase end = progress.awaitEnd();
            LimelightConverterStatus after = provisioning.status();
            Path jar = after.jar().orElseThrow();

            assertAll(
                    () ->
                            assertEquals(
                                    LimelightConverterStatus.State.NOT_INSTALLED, before.state()),
                    () -> assertEquals(OptionalLong.of(2_762_075L), before.downloadBytes()),
                    () -> assertEquals(List.of(), cachedBefore, "the cache starts empty"),
                    () -> assertTrue(handle != null),
                    () ->
                            assertTrue(
                                    returnedAfter.compareTo(Duration.ofSeconds(5)) < 0,
                                    () -> "install returned after " + returnedAfter),
                    () -> assertEquals(InstallPhase.DONE, end, () -> progress.phases.toString()),
                    () -> assertTrue(progress.phases.contains(InstallPhase.DOWNLOADING)),
                    () -> assertTrue(progress.phases.contains(InstallPhase.VERIFYING)),
                    () -> assertEquals(List.of(record.url()), served.requested()),
                    () -> assertEquals(LimelightConverterStatus.State.INSTALLED, after.state()),
                    () -> assertEquals(Optional.of(VERSION), after.version()),
                    () -> assertTrue(jar.startsWith(cache), jar::toString),
                    () -> assertEquals(PINNED_SHA256, sha256(jar)),
                    () -> assertEquals(PINNED_SHA256, record.hashes().sha256()),
                    () ->
                            assertEquals(
                                    VERSION,
                                    new LimelightConverterIdentity(
                                                    new ToolRunner(
                                                            new ProcessService(Clock.systemUTC()),
                                                            Duration.ofMinutes(2)),
                                                    JavaRuntime.ofThisApplication())
                                            .identify(ToolName.LIMELIGHT_CONVERTER, LINUX, jar),
                                    "the installed JAR runs and reports the pinned version"));

            jar.toFile().setWritable(true);
            Files.write(jar, new byte[] {0}, StandardOpenOption.APPEND);
            LimelightConverterStatus tampered = provisioning.status();
            assertAll(
                    () -> assertNotEquals(PINNED_SHA256, sha256(jar)),
                    () ->
                            assertNotEquals(
                                    LimelightConverterStatus.State.INSTALLED,
                                    tampered.state(),
                                    tampered::explanation),
                    () -> assertEquals(Optional.empty(), tampered.installation()));
        }
    }

    @Test
    @DisplayName(
            "the install is cancellable part-way, leaves nothing installed, and can be started"
                    + " again")
    void theInstallIsCancellable() throws IOException, InterruptedException {
        Path cache = scratch.toRealPath().resolve("cometgui-data");
        ArtefactRecord record = ArtefactMirror.record(ToolName.LIMELIGHT_CONVERTER, "2.8.1", LINUX);
        AtomicReference<InstallHandle> handle = new AtomicReference<>();
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer()
                        .serve(record.url(), ArtefactMirror.artefact(MIRRORED))
                        .trippingAfter(record.url(), 1_000_000L, () -> handle.get().cancel())) {
            LimelightProvisioning provisioning = new LimelightProvisioning(managed(cache, served));
            Progress progress = new Progress();
            handle.set(provisioning.install(VERSION, progress::report));

            InstallPhase end = progress.awaitEnd();

            assertAll(
                    () ->
                            assertEquals(
                                    InstallPhase.CANCELLED, end, () -> progress.phases.toString()),
                    () -> assertTrue(served.trippedAt(record.url()).isPresent()),
                    () ->
                            assertEquals(
                                    LimelightConverterStatus.State.NOT_INSTALLED,
                                    provisioning.status().state()),
                    () ->
                            assertFalse(
                                    filesUnder(cache).stream()
                                            .anyMatch(file -> file.toString().endsWith(".jar")),
                                    "no JAR is in the cache"));
        }
    }
}
