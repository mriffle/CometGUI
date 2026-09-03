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

package org.cometgui.app.tools;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.cometgui.app.config.FfmGlibcVersionSource;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.download.HttpDownloader;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.ArtefactVerifier;
import org.cometgui.install.verify.VerifiedDownloader;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The four managed tools installed from an empty cache and probed, driven through {@code
 * ToolManager} alone.
 *
 * <p>This is gate items 1, 2 and 7 at the level where they mean something. Every part is the real
 * one: the shipped {@code manifests/tools.json}; the product's own downloader over real HTTP; the
 * product's own {@code R-SEC-02} verification against the pinned SHA-256; the real extractor with
 * its {@code R-SEC-05} guards; the real atomic install with its completion marker; and unit 6's and
 * unit 7's real three-stage probe, which <strong>executes the binaries</strong> -- Comet's
 * parameter query, Percolator's functional XML probe over a 64 target plus 64 decoy synthetic PIN,
 * and a JVM launch for the Limelight converter. The only thing swapped is the host part of the
 * artefact URLs, so that the suite does not depend on GitHub being reachable.
 *
 * <p>It lives in {@code cometgui-app} because this is the only module that can see both halves:
 * {@code cometgui-install} declares the probe seams and {@code cometgui-tools} implements them, and
 * neither depends on the other.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the artefacts are read from this phase's Linux artefact mirror and the binaries"
                        + " are executed; no other platform's build has ever been run anywhere in"
                        + " this project")
class ToolManagerEndToEndTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /** Generous: a 103407417-byte transfer, a 222-entry extraction and four real probe runs. */
    private static final int INSTALL_TIMEOUT_SECONDS = 600;

    @TempDir private Path work;

    /** One Tool Manager, wired exactly as {@link ToolManagerWiring} wires the real one. */
    private static final class Wiring implements AutoCloseable {

        private final ArtefactManifest manifest;
        private final MirrorHttpServer mirror = new MirrorHttpServer();
        private final HttpDownloader downloader = new HttpDownloader();
        private final RecordingProcessRunner processes =
                new RecordingProcessRunner(new ProcessService(Clock.systemUTC()));
        private final ExecutorService installs = Executors.newSingleThreadExecutor();
        private final ToolManager manager;

        Wiring(Path cacheRoot) throws IOException {
            this.manifest = ArtefactManifestReader.readFromClasspath();
            StreamingHashService hashes = new StreamingHashService();
            VerifiedDownloader verified =
                    new VerifiedDownloader(downloader, new ArtefactVerifier(hashes));
            this.manager =
                    ToolManagerWiring.toolManager(
                            manifest,
                            LINUX,
                            HostRuntimeVersions.detect(new FfmGlibcVersionSource()),
                            processes,
                            hashes,
                            (source, destination, expected, size, listener, cancellation) ->
                                    verified.fetch(
                                            mirror.addressOf(source),
                                            destination,
                                            expected,
                                            size,
                                            listener,
                                            cancellation),
                            cacheRoot,
                            Clock.systemUTC(),
                            Duration.ofMinutes(5),
                            installs);
        }

        MirrorHttpServer mirror() {
            return mirror;
        }

        RecordingProcessRunner processes() {
            return processes;
        }

        ToolManager manager() {
            return manager;
        }

        ArtefactRecord recordOf(ToolName tool) {
            return manifest.select(LINUX, tool).get(0).artefact();
        }

        InstallPhase install(ToolName tool, ToolVersion version) {
            CountDownLatch finished = new CountDownLatch(1);
            List<InstallPhase> terminal = new ArrayList<>();
            InstallProgressListener listener =
                    (InstallProgress progress) -> {
                        if (progress.phase().isTerminal()) {
                            terminal.add(progress.phase());
                            finished.countDown();
                        }
                    };
            manager.install(tool, version, listener);
            try {
                if (!finished.await(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError(
                            "the install of "
                                    + tool.id()
                                    + " "
                                    + version.text()
                                    + " did not finish within "
                                    + INSTALL_TIMEOUT_SECONDS
                                    + " seconds");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for an install", interrupted);
            }
            return terminal.get(0);
        }

        List<String> describeOffers() {
            List<String> rows = new ArrayList<>();
            for (ToolOffer offer : manager.offers()) {
                rows.add(
                        offer.tool().id()
                                + " "
                                + offer.version().text()
                                + " "
                                + offer.state().name()
                                + " "
                                + capabilityIds(offer));
            }
            return rows;
        }

        ToolOffer offerOf(ToolName tool, ToolOrigin origin) {
            for (ToolOffer offer : manager.offers()) {
                if (offer.tool() == tool && offer.origin() == origin) {
                    return offer;
                }
            }
            throw new AssertionError("no " + origin + " offer of " + tool.id());
        }

        @Override
        public void close() throws IOException {
            installs.shutdownNow();
            downloader.close();
            mirror.close();
        }
    }

    /**
     * How many files an install directory holds, not counting the completion marker.
     *
     * @param directory the tool's install directory
     * @return the payload entry count
     * @throws IOException if the tree cannot be walked
     */
    private static long payloadEntriesUnder(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> tree = Files.walk(directory)) {
            return tree.filter(Files::isRegularFile)
                    .filter(file -> !nameOf(file).startsWith(".cometgui-install"))
                    .count();
        }
    }

    /**
     * A path's own directory, refusing a root path rather than dereferencing null.
     *
     * @param path the path
     * @return its parent
     */
    private static Path directoryOf(Path path) {
        return java.util.Objects.requireNonNull(path.getParent(), () -> path + " has no directory");
    }

    /**
     * A path's last element, refusing an empty path rather than dereferencing null.
     *
     * @param path the path
     * @return its file name
     */
    private static String nameOf(Path path) {
        return java.util.Objects.requireNonNull(path.getFileName(), "an empty path has no name")
                .toString();
    }

    private static List<String> capabilityIds(ToolOffer offer) {
        List<String> ids = new ArrayList<>();
        for (DeclaredCapability declared : offer.capabilities()) {
            ids.add(declared.capability().id());
        }
        ids.sort(String::compareTo);
        return ids;
    }

    /*
     * GATE ITEM 1, from an empty cache and through the port alone.  The capability sets below are
     * what the real probes WATCHED each binary do on this host, hand-typed: Comet's three come from
     * the parameter files it writes for -p and -q, Percolator's two from running it over a 64+64
     * synthetic PIN with -X and then with -X -Z and reading the documents, and the two JAR tools
     * have no capability in ToolCapability at all, so an empty set is the honest answer rather than
     * a gap.
     */
    @Test
    @DisplayName("all four managed tools install from an empty cache and are probed")
    void allFourToolsInstallAndAreProbed() throws IOException {
        try (Wiring wiring = new Wiring(work.resolve("cache"))) {
            for (ToolName tool : ToolName.values()) {
                wiring.mirror().serving(wiring.recordOf(tool));
            }

            List<InstallPhase> outcomes = new ArrayList<>();
            for (ToolName tool : ToolName.values()) {
                ArtefactRecord record = wiring.recordOf(tool);
                outcomes.add(wiring.install(tool, record.version()));
            }

            assertAll(
                    () ->
                            assertEquals(
                                    List.of(
                                            InstallPhase.DONE,
                                            InstallPhase.DONE,
                                            InstallPhase.DONE,
                                            InstallPhase.DONE),
                                    outcomes,
                                    "Comet, Percolator, PDV and the Limelight converter, in"
                                            + " ToolName order"),
                    () ->
                            assertEquals(
                                    List.of(
                                            "comet 2026.02.2 INSTALLED [COMPLETE_PARAMS_QUERY,"
                                                    + " PEPXML_OUTPUT, PIN_OUTPUT]",
                                            "percolator 3.07.1 INSTALLED [XML_DECOY_OUTPUT,"
                                                    + " XML_OUTPUT]",
                                            "percolator 3.06.5 NOT_INSTALLED [XML_DECOY_OUTPUT,"
                                                    + " XML_OUTPUT]",
                                            "percolator 3.09 UNAVAILABLE_ON_THIS_PLATFORM []",
                                            "pdv 2.7.0 INSTALLED []",
                                            "limelight-converter 2.8.1 INSTALLED []"),
                                    wiring.describeOffers(),
                                    "3.06.5 is the one row still carrying the MANIFEST's claims,"
                                            + " because nothing has probed it; the four installed"
                                            + " rows carry what was observed"),
                    () ->
                            assertTrue(
                                    wiring.processes().executables().stream()
                                            .anyMatch(name -> name.endsWith("/bin/comet")),
                                    () ->
                                            "Comet was executed by the probe: "
                                                    + wiring.processes().executables()),
                    () ->
                            assertTrue(
                                    wiring.processes().executables().stream()
                                            .anyMatch(name -> name.endsWith("/bin/percolator")),
                                    () ->
                                            "and so was Percolator: "
                                                    + wiring.processes().executables()),
                    /*
                     * PDV IS THE ONE MULTI-ENTRY ARCHIVE THE PRODUCT INSTALLS, and "INSTALLED" on
                     * its own would not say the whole 103407417-byte archive was unpacked: the
                     * completion marker pins the executable and the companions the manifest names,
                     * which for PDV is a 1343276-byte JAR.  Counting what was placed is what says
                     * the other 216 files arrived, and it is the only row in this product where
                     * the R-SEC-05 guards run in production.
                     *
                     * 217 FILES, NOT 222 ENTRIES.  The archive holds 222 entries of which five are
                     * directories, and a directory is not a file the guards placed -- measured
                     * from the artefact rather than assumed, because "222 entries" is the figure
                     * the phase record carries and it is the count of entries.
                     */
                    () ->
                            assertEquals(
                                    1343276L,
                                    Files.size(
                                            wiring.offerOf(ToolName.PDV, ToolOrigin.MANAGED)
                                                    .installedPath()
                                                    .orElseThrow()),
                                    "the installed JAR is the member of the archive, at its own"
                                            + " length"),
                    () ->
                            assertEquals(
                                    217L,
                                    payloadEntriesUnder(
                                            directoryOf(
                                                    directoryOf(
                                                            wiring.offerOf(
                                                                            ToolName.PDV,
                                                                            ToolOrigin.MANAGED)
                                                                    .installedPath()
                                                                    .orElseThrow()))),
                                    "every file of the 222-entry archive is on disk -- 217 of"
                                            + " them, the other five entries being directories --"
                                            + " so the whole transfer and extraction really"
                                            + " happened"));
        }
    }

    /*
     * GATE ITEM 2, both halves.  The artefact is the real one with a single byte flipped, so its
     * LENGTH still matches the manifest and only the SHA-256 can have rejected it -- and the
     * recorder sits at the one seam R-PROC-02 allows a process to start through, so an empty log is
     * proof that nothing was executed rather than evidence that nothing was noticed.
     */
    @Test
    @DisplayName("a corrupted artefact is rejected and no process is launched at all")
    void aCorruptedArtefactLaunchesNothing() throws IOException {
        try (Wiring wiring = new Wiring(work.resolve("corrupt-cache"))) {
            ArtefactRecord percolator = wiring.recordOf(ToolName.PERCOLATOR);
            wiring.mirror().servingCorrupted(percolator);

            InstallPhase outcome = wiring.install(ToolName.PERCOLATOR, percolator.version());

            assertAll(
                    () -> assertEquals(InstallPhase.FAILED, outcome),
                    () ->
                            assertEquals(
                                    List.of(),
                                    wiring.processes().launched(),
                                    "R-SEC-02: SHA-256 verification is mandatory BEFORE an"
                                            + " executable is launched, and nothing was launched"),
                    () ->
                            assertEquals(
                                    ToolInstallState.FAILED,
                                    wiring.offerOf(ToolName.PERCOLATOR, ToolOrigin.MANAGED)
                                            .state()));
        }
    }

    /*
     * GATE ITEM 7.  The binary registered is the one this test just installed -- the real 3.07.1
     * portable build, verified against the manifest's SHA-256 four steps earlier -- so the
     * capability set beside it was probed by running that exact file.  The refusal is graded
     * against a stub that prints the banner of a release CometGUI will not accept, and the message
     * names BOTH numbers, which is what a scientist needs before going to look for another build.
     */
    @Test
    @DisplayName("the real 3.07.1 binary registers, a 3.04 is refused naming both versions")
    void localBinaryRegistration() throws IOException, ToolRegistrationException {
        try (Wiring wiring = new Wiring(work.resolve("local-cache"))) {
            ArtefactRecord percolator = wiring.recordOf(ToolName.PERCOLATOR);
            wiring.mirror().serving(percolator);
            assertEquals(
                    InstallPhase.DONE, wiring.install(ToolName.PERCOLATOR, percolator.version()));
            Path installed =
                    wiring.offerOf(ToolName.PERCOLATOR, ToolOrigin.MANAGED)
                            .installedPath()
                            .orElseThrow();
            Path tooOld = aBinaryReportingVersion("3.04");

            ToolOffer registered =
                    wiring.manager().registerLocalBinary(ToolName.PERCOLATOR, installed);

            assertAll(
                    () -> assertEquals(ToolOrigin.LOCAL, registered.origin()),
                    () -> assertEquals(ToolInstallState.INSTALLED, registered.state()),
                    () -> assertEquals("3.07.1", registered.version().text()),
                    () ->
                            assertEquals(
                                    List.of("XML_DECOY_OUTPUT", "XML_OUTPUT"),
                                    capabilityIds(registered),
                                    "probed by running the file that was registered, not inherited"
                                            + " from a manifest row"),
                    () ->
                            assertEquals(
                                    java.util.OptionalLong.empty(),
                                    registered.downloadSizeBytes(),
                                    "a local binary was never downloaded"),
                    () ->
                            assertEquals(
                                    "The file at "
                                            + tooOld
                                            + " is Percolator 3.04, and CometGUI requires"
                                            + " Percolator 3.05 or newer.",
                                    assertThrows(
                                                    ToolRegistrationException.class,
                                                    () ->
                                                            wiring.manager()
                                                                    .registerLocalBinary(
                                                                            ToolName.PERCOLATOR,
                                                                            tooOld))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "CometGUI can register a local binary for percolator only, and"
                                            + " the file at "
                                            + installed
                                            + " was offered as comet. Percolator is the one tool"
                                            + " with a documented local-binary remedy, because a"
                                            + " managed XML-capable build does not exist for every"
                                            + " platform.",
                                    assertThrows(
                                                    ToolRegistrationException.class,
                                                    () ->
                                                            wiring.manager()
                                                                    .registerLocalBinary(
                                                                            ToolName.COMET,
                                                                            installed))
                                            .getMessage(),
                                    "a tool with no registrar is told so rather than accepted and"
                                            + " lost"));
        }
    }

    /**
     * A file that answers {@code --help} with a Percolator banner of a chosen version.
     *
     * <p>On standard error, which is where the real binaries print it -- a stub printing to
     * standard output would pass a probe the real thing would fail, which is the wrong way round.
     *
     * @param version the version to claim
     * @return the executable
     * @throws IOException if it cannot be written
     */
    private Path aBinaryReportingVersion(String version) throws IOException {
        Path binary = work.resolve("elsewhere").resolve("percolator");
        Files.createDirectories(directoryOf(binary));
        Files.writeString(
                binary,
                "#!/bin/sh\n"
                        + "echo \"Percolator version "
                        + version
                        + ", Build Date Jan 01 2020 00:00:00\" 1>&2\n",
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwxr-xr-x"));
        return binary;
    }
}
