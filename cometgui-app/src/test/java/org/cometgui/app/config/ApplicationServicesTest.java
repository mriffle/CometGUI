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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.cometgui.app.testing.FakeEnvironment;
import org.cometgui.app.testing.Nulls;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.platform.GlibcVersionSource;
import org.cometgui.domain.ports.Downloader;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.FileSystemAccess;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunIdSource;
import org.cometgui.domain.run.RunId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The composition root: what {@link ApplicationServices#forThisHost()} actually wires, and how the
 * three seams that have no implementation yet behave.
 *
 * <p>The application-data assertion is the interesting one. It does not check a shape; it predicts
 * the exact path from the environment cometgui-app/pom.xml puts into the forked JVM ({@code
 * XDG_DATA_HOME}, pointed at the project-local font stack) and requires the real reader, the real
 * filesystem access and the real wiring to agree with that prediction. A fake anywhere in that
 * chain would fail it.
 */
class ApplicationServicesTest {

    private static final HashService A_HASH_SERVICE =
            path ->
                    new FileHashes(
                            "0".repeat(FileHashes.MD5_LENGTH),
                            "0".repeat(FileHashes.SHA256_LENGTH));

    private static final ProcessRunner A_PROCESS_RUNNER =
            (command, listener) -> {
                throw new IOException("not a real runner");
            };

    private static final Downloader A_DOWNLOADER =
            (source, destination, listener) -> {
                throw new IOException("not a real downloader");
            };

    private static ApplicationServices withOptionalSeams(
            ProcessRunner processRunner, HashService hashService, Downloader downloader) {
        return new ApplicationServices(
                Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC),
                FakeEnvironment.linux64(),
                new PlatformFileSystemAccess(FakeEnvironment.linux64()),
                new ClockRunIdSource(Clock.systemUTC()),
                Optional::empty,
                processRunner,
                hashService,
                downloader);
    }

    @Test
    @DisplayName("forThisHost wires the real implementation of every seam it can")
    void wiresTheRealImplementations() {
        ApplicationServices services = ApplicationServices.forThisHost();

        assertAll(
                () -> assertEquals(Clock.systemUTC(), services.clock()),
                () -> assertInstanceOf(SystemEnvironmentReader.class, services.environment()),
                () -> assertInstanceOf(PlatformFileSystemAccess.class, services.fileSystem()),
                () -> assertInstanceOf(ClockRunIdSource.class, services.runIds()),
                () -> assertInstanceOf(FfmGlibcVersionSource.class, services.glibcVersions()),
                () -> assertEquals(Optional.empty(), services.processRunner()),
                () -> assertEquals(Optional.empty(), services.hashService()),
                () -> assertEquals(Optional.empty(), services.downloader()));
    }

    @Test
    @DisplayName("the wired glibc probe reads this host's real version")
    void theWiredProbeReadsThisHost() {
        Optional<GlibcVersion> detected =
                ApplicationServices.forThisHost().glibcVersions().detect();

        assertTrue(detected.isPresent(), "the wired probe read nothing on a glibc host");
        assertTrue(
                detected.orElseThrow().isAtLeast(GlibcVersion.of(2, 14, 0)),
                "read " + detected.orElseThrow() + ", below the lowest managed tool's floor");
    }

    @Test
    @DisplayName("the wired data directory is what this JVM's environment implies, and is not made")
    void theWiredDataDirectoryFollowsTheRealEnvironment() {
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        assertNotNull(
                xdgDataHome,
                "XDG_DATA_HOME is not set; surefire in cometgui-app/pom.xml must pass it");

        Path directory = ApplicationServices.forThisHost().fileSystem().applicationDataDirectory();

        assertAll(
                () -> assertEquals(Path.of(xdgDataHome).resolve("cometgui"), directory),
                () ->
                        assertFalse(
                                Files.exists(directory),
                                "building the composition root must create no directory, but "
                                        + directory
                                        + " exists"));
    }

    @Test
    @DisplayName(
            "the wired run-ID source produces identifiers RunId accepts, and never repeats one")
    void theWiredRunIdSourceWorks() {
        RunIdSource runIds = ApplicationServices.forThisHost().runIds();

        RunId first = runIds.newRunId();
        RunId second = runIds.newRunId();

        assertAll(
                () -> assertTrue(first.value().startsWith("run-"), first.value()),
                () -> assertEquals(first.value(), new RunId(first.value()).value()),
                () -> assertTrue(first.value().endsWith("-1"), first.value()),
                () -> assertTrue(second.value().endsWith("-2"), second.value()),
                () -> assertNotEquals(first, second, "two run ids must never be equal"));
    }

    @Test
    @DisplayName("the three seams later phases own are absent, and say which phase owns them")
    void absentSeamsAreModelledExplicitly() {
        ApplicationServices services = ApplicationServices.forThisHost();

        assertAll(
                () -> assertEquals(Optional.empty(), services.processRunner()),
                () -> assertEquals(Optional.empty(), services.hashService()),
                () -> assertEquals(Optional.empty(), services.downloader()),
                () ->
                        assertEquals(
                                "the process runner is not wired yet:"
                                        + " org.cometgui.domain.ports.ProcessRunner is delivered by"
                                        + " phase 03",
                                assertThrows(
                                                IllegalStateException.class,
                                                services::requireProcessRunner)
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the hash service is not wired yet:"
                                        + " org.cometgui.domain.ports.HashService is delivered by"
                                        + " phase 04",
                                assertThrows(
                                                IllegalStateException.class,
                                                services::requireHashService)
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the downloader is not wired yet:"
                                        + " org.cometgui.domain.ports.Downloader is delivered by"
                                        + " phase 05",
                                assertThrows(
                                                IllegalStateException.class,
                                                services::requireDownloader)
                                        .getMessage()));
    }

    @Test
    @DisplayName("once a seam is supplied, both accessors hand back exactly that instance")
    void suppliedSeamsAreHandedBack() {
        ApplicationServices services =
                withOptionalSeams(A_PROCESS_RUNNER, A_HASH_SERVICE, A_DOWNLOADER);

        assertAll(
                () -> assertEquals(Optional.of(A_PROCESS_RUNNER), services.processRunner()),
                () -> assertSame(A_PROCESS_RUNNER, services.requireProcessRunner()),
                () -> assertEquals(Optional.of(A_HASH_SERVICE), services.hashService()),
                () -> assertSame(A_HASH_SERVICE, services.requireHashService()),
                () -> assertEquals(Optional.of(A_DOWNLOADER), services.downloader()),
                () -> assertSame(A_DOWNLOADER, services.requireDownloader()));
    }

    @Test
    @DisplayName("one seam supplied does not make the other two appear")
    void seamsAreIndependent() {
        ApplicationServices services = withOptionalSeams(null, A_HASH_SERVICE, null);

        assertAll(
                () -> assertEquals(Optional.empty(), services.processRunner()),
                () -> assertSame(A_HASH_SERVICE, services.requireHashService()),
                () -> assertEquals(Optional.empty(), services.downloader()),
                () -> assertThrows(IllegalStateException.class, services::requireProcessRunner),
                () -> assertThrows(IllegalStateException.class, services::requireDownloader));
    }

    @Test
    @DisplayName("the five required seams are rejected when null, each naming itself")
    void rejectsMissingRequiredSeams() {
        Clock clock = Clock.systemUTC();
        FakeEnvironment environment = FakeEnvironment.linux64();
        PlatformFileSystemAccess files = new PlatformFileSystemAccess(environment);
        RunIdSource runIds = new ClockRunIdSource(clock);
        GlibcVersionSource glibc = Optional::empty;

        assertAll(
                () ->
                        assertEquals(
                                "clock",
                                nullPointerFrom(
                                        () ->
                                                services(
                                                        Nulls.of(Clock.class),
                                                        environment,
                                                        files,
                                                        runIds,
                                                        glibc))),
                () ->
                        assertEquals(
                                "environment",
                                nullPointerFrom(
                                        () ->
                                                services(
                                                        clock,
                                                        Nulls.of(EnvironmentReader.class),
                                                        files,
                                                        runIds,
                                                        glibc))),
                () ->
                        assertEquals(
                                "fileSystem",
                                nullPointerFrom(
                                        () ->
                                                services(
                                                        clock,
                                                        environment,
                                                        Nulls.of(FileSystemAccess.class),
                                                        runIds,
                                                        glibc))),
                () ->
                        assertEquals(
                                "runIds",
                                nullPointerFrom(
                                        () ->
                                                services(
                                                        clock,
                                                        environment,
                                                        files,
                                                        Nulls.of(RunIdSource.class),
                                                        glibc))),
                () ->
                        assertEquals(
                                "glibcVersions",
                                nullPointerFrom(
                                        () ->
                                                services(
                                                        clock,
                                                        environment,
                                                        files,
                                                        runIds,
                                                        Nulls.of(GlibcVersionSource.class)))));
    }

    private static ApplicationServices services(
            Clock clock,
            EnvironmentReader environment,
            FileSystemAccess fileSystem,
            RunIdSource runIds,
            GlibcVersionSource glibcVersions) {
        return new ApplicationServices(
                clock, environment, fileSystem, runIds, glibcVersions, null, null, null);
    }

    private static String nullPointerFrom(Executable construction) {
        return assertThrows(NullPointerException.class, construction).getMessage();
    }
}
