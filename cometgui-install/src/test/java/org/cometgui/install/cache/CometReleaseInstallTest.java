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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.download.HttpDownloader;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.probe.LoadabilityProbe;
import org.cometgui.install.probe.LoaderOutputClassifier;
import org.cometgui.install.probe.StagedToolProbe;
import org.cometgui.install.probe.VersionBanner;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.ArtefactVerifier;
import org.cometgui.install.verify.VerifiedDownloader;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every Comet release the shipped manifest names for linux-x86-64, installed by the real installer
 * and identified by the real probe <strong>running the real binary</strong>.
 *
 * <p>Driven by the data: the releases are read from {@code manifests/tools.json}, so a release
 * added to the manifest is installed here without this class changing, and a release whose bytes
 * are not in the mirror fails rather than skips. What is real: the record, the bytes (the
 * gitignored mirror, fetched from the record's URL and checked by SHA-256), the verifier, the
 * eight-step installer, the extractor, the fix-ups, the cache, the marker, and stages 1 and 2 of
 * the probe -- {@code LoadabilityProbe} starting the installed file through the real process
 * service and {@code VersionBanner.comet()} reading what it printed. Only stage 3 is replaced, by a
 * prober that records the version the identity stage handed it: Comet's own capability probe lives
 * in {@code cometgui-tools}, which this module cannot see, and {@code ToolManagerWiringTest} and
 * {@code ToolManagerInstallUiTest} run it.
 *
 * <p>The transport is the one substitution, as in {@code RealArtefactInstallTest}: the routine
 * build must not depend on GitHub. {@link #theDefaultCometInstallsFromItsRealUpstreamUrl} is the
 * same install through the real {@code HttpDownloader} against the real URL, opt-in.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "comet.linux.exe is a Linux ELF binary; the other Comet builds have never been run"
                        + " anywhere in this project, which is recorded rather than covered here")
class CometReleaseInstallTest {

    /** The property that opts in to reaching the real network. */
    private static final String OPT_IN = "cometgui.install.upstream";

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /** This project's build host, hand-typed rather than read from the machine. */
    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    /**
     * The default Comet (D-010) and the digest of the bytes this project downloaded from its URL on
     * 2026-10-04, hand-typed so that the manifest and this class are two statements of one fact.
     */
    private static final String DEFAULT_SHA256 =
            "ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed";

    private static final ToolVersion DEFAULT = ToolVersion.parse("2026.03.0");

    @TempDir private Path temporary;

    @Test
    @DisplayName(
            "every Comet release the manifest names for linux-x86-64 installs from its real"
                    + " bytes, and the probe reads that release from the binary's own banner")
    void everyLinuxCometReleaseInstallsAndIdentifiesItself() throws IOException {
        List<ArtefactRecord> releases = linuxCometRows();
        List<String> installed = new ArrayList<>();
        for (ArtefactRecord record : releases) {
            Rig rig = new Rig(temporary.resolve(record.version().text()));
            rig.fetcher().serve(record.url(), Files.readAllBytes(mirrored(record)));

            Installation installation = rig.install(record);

            Path expected =
                    rig.root()
                            .resolve(
                                    "tools/comet/"
                                            + directoryName(record.version())
                                            + "/linux-x86-64/bin/comet");
            assertAll(
                    record.describe(),
                    () -> assertEquals(expected, installation.executable()),
                    () ->
                            assertEquals(
                                    record.hashes().sha256(),
                                    CacheFixtures.sha256Of(installation.executable()),
                                    "the installed file is the bytes the manifest pins"),
                    () -> assertTrue(Files.isExecutable(installation.executable())),
                    () ->
                            assertEquals(
                                    List.of(record.version()),
                                    rig.identified(),
                                    "the identity stage read this release from the binary's"
                                            + " banner, and nothing else"),
                    () ->
                            assertEquals(
                                    List.of(List.of(installation.executable().toString(), "-h")),
                                    rig.relocated(installation),
                                    "the binary was executed once, with the banner's argument"),
                    () -> assertTrue(rig.cache().verify(record).installed()));
            installed.add(record.version().text());
        }
        assertTrue(
                installed.containsAll(List.of("2026.02.2", "2026.03.0")),
                () ->
                        "D-010: the default and the release it supersedes both install: "
                                + installed);
    }

    @Test
    @DisplayName(
            "the 2026.03.0 binary installed under a record pinned as 2026.02.2 is refused by its"
                    + " own banner")
    void aBinaryThatIsNotThePinnedReleaseIsRefusedByItsBanner() throws IOException {
        ArtefactRecord genuine = defaultRow();
        ArtefactRecord mislabelled = withVersion(genuine, ToolVersion.parse("2026.02.2"));
        Rig rig = new Rig(temporary.resolve("cache"));
        rig.fetcher().serve(mislabelled.url(), Files.readAllBytes(mirrored(genuine)));

        InstallRejectedException refused =
                assertThrows(
                        InstallRejectedException.class,
                        () -> rig.install(mislabelled),
                        "the 2026.03.0 binary must be refused under a record pinning 2026.02.2:"
                                + " its SHA-256 matches, so the identity stage reading the banner"
                                + " is the only thing that can tell the two releases apart");

        assertAll(
                () -> assertEquals(InstallStep.PROBE, refused.step()),
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .contains(
                                                "reports itself as version 2026.03.0, and the"
                                                        + " manifest pins 2026.02.2"),
                                () ->
                                        "the SHA-256 matched -- these ARE the pinned bytes -- so"
                                                + " only the banner can tell the releases apart: "
                                                + refused.getMessage()),
                () -> assertEquals(List.of(), rig.identified(), "no capability stage was reached"),
                () ->
                        assertFalse(
                                rig.cache().verify(mislabelled).installed(),
                                "and nothing was installed under the wrong release"));
    }

    @Test
    @DisplayName(
            "a 2026.03.0 record whose pinned SHA-256 is one character wrong is refused before"
                    + " anything is executed")
    void aCorruptedPinIsRefusedBeforeAnythingRuns() throws IOException {
        ArtefactRecord genuine = defaultRow();
        String pinned = genuine.hashes().sha256();
        String corrupted = (pinned.charAt(0) == '0' ? "1" : "0") + pinned.substring(1);
        ArtefactRecord record =
                withHashes(genuine, new FileHashes(genuine.hashes().md5(), corrupted));
        Rig rig = new Rig(temporary.resolve("cache"));
        byte[] published = Files.readAllBytes(mirrored(genuine));
        rig.fetcher().serve(record.url(), published);

        IOException refused = assertThrows(IOException.class, () -> rig.install(record));

        assertAll(
                () ->
                        assertEquals(
                                DEFAULT_SHA256,
                                pinned,
                                "the record under test is the default Comet's, hand-checked"),
                () ->
                        assertTrue(
                                refused.getMessage().contains(corrupted),
                                () ->
                                        "the refusal names the digest that was required: "
                                                + refused.getMessage()),
                () ->
                        assertEquals(
                                List.of(),
                                rig.processes().launched(),
                                "R-SEC-02, verify before you execute: no process was started,"
                                        + " recorded by a runner that really starts them -- the"
                                        + " test above shows it does"),
                () -> assertEquals(List.of(), rig.identified()),
                () ->
                        assertFalse(
                                Files.exists(rig.root().resolve("tools")),
                                "nothing reached the installed tree"),
                () ->
                        assertEquals(
                                List.of(record.url()),
                                rig.fetcher().requested(),
                                "the genuine bytes were fetched; only the pin was wrong"));
    }

    @Test
    @EnabledIfSystemProperty(
            named = OPT_IN,
            matches = "true",
            disabledReason =
                    "reaches github.com and moves 7 077 008 bytes; run with"
                            + " -Dcometgui.install.upstream=true. The ordinary build must not"
                            + " depend on upstream being reachable.")
    @DisplayName(
            "the default Comet installs from its real upstream URL, verified by SHA-256, and"
                    + " identifies itself as 2026.03.0")
    void theDefaultCometInstallsFromItsRealUpstreamUrl() throws IOException {
        ArtefactRecord record = defaultRow();
        Path root = temporary.resolve("cache");
        RecordingRunner processes = new RecordingRunner();
        List<ToolVersion> identified = Collections.synchronizedList(new ArrayList<>());
        StreamingHashService hashes = new StreamingHashService();
        ToolCache cache = new ToolCache(root, hashes);
        try (HttpDownloader http = new HttpDownloader()) {
            VerifiedDownloader downloader =
                    new VerifiedDownloader(http, new ArtefactVerifier(hashes));
            ArtefactInstaller installer =
                    new ArtefactInstaller(
                            cache,
                            downloader::fetch,
                            new ArtefactExtractor(),
                            new PlatformFixups(
                                    HostOperatingSystem.LINUX, ScriptedXattr.NEVER_CALLED),
                            probe(processes, identified),
                            hashes,
                            Clock.systemUTC());

            Installation installation =
                    installer.install(
                            record, new RecordingInstallListener(), DownloadCancellation.never());

            assertAll(
                    () -> assertEquals("https", record.url().getScheme()),
                    () ->
                            assertEquals(
                                    DEFAULT_SHA256,
                                    CacheFixtures.sha256Of(installation.executable())),
                    () -> assertEquals(7_077_008L, Files.size(installation.executable())),
                    () -> assertEquals(List.of(DEFAULT), identified),
                    () -> assertTrue(cache.verify(record).installed()));
        }
    }

    // ----------------------------------------------------------------------- fixtures --

    /**
     * The version directory, restated from ToolVersion's documented rule rather than asked of the
     * cache: the numeric components, most significant first, trailing zero components dropped -- so
     * 2026.02.2 installs under 2026.2.2 and 2026.03.0 under 2026.3.
     */
    private static String directoryName(ToolVersion version) {
        List<Integer> components = new ArrayList<>(version.components());
        while (components.size() > 1 && components.get(components.size() - 1) == 0) {
            components.remove(components.size() - 1);
        }
        StringBuilder name = new StringBuilder();
        for (int component : components) {
            name.append(name.length() == 0 ? "" : ".").append(component);
        }
        return name.toString();
    }

    private static List<ArtefactRecord> linuxCometRows() throws IOException {
        List<ArtefactRecord> rows =
                ArtefactManifestReader.readFromClasspath().artefacts().stream()
                        .filter(record -> record.tool() == ToolName.COMET)
                        .filter(record -> record.platform().equals(LINUX))
                        .toList();
        assertFalse(rows.isEmpty(), "the shipped manifest names no linux-x86-64 Comet");
        return rows;
    }

    private static ArtefactRecord defaultRow() throws IOException {
        return linuxCometRows().stream()
                .filter(record -> record.version().equals(DEFAULT))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "manifests/tools.json no longer holds comet 2026.03.0 for"
                                                + " linux-x86-64, the default D-010 names"));
    }

    /** The mirror's copy of a record's download: {@code <releaseTag>__<file name in the URL>}. */
    private static Path mirrored(ArtefactRecord record) {
        String path = record.url().getPath();
        String fileName = record.releaseTag() + "__" + path.substring(path.lastIndexOf('/') + 1);
        Path file = repositoryRoot().resolve("scratch/phase05/artefacts").resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "the real artefact \""
                            + fileName
                            + "\" is not in the mirror at "
                            + file
                            + ". Refill it by fetching "
                            + record.url()
                            + " and checking its SHA-256 before use. This test fails rather than"
                            + " skips, because an installer suite that stops reading the real"
                            + " artefacts stops proving anything.");
        }
        return file;
    }

    private static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError("no repository root above " + Path.of("").toAbsolutePath());
        }
        return cursor;
    }

    private static ArtefactRecord withVersion(ArtefactRecord record, ToolVersion version) {
        return new ArtefactRecord(
                record.tool(),
                version,
                record.releaseTag(),
                record.platform(),
                record.kind(),
                record.url(),
                record.sizeBytes(),
                record.hashes(),
                record.member(),
                record.expectedExecutablePath(),
                record.executable(),
                record.licence(),
                record.companions(),
                record.capabilities(),
                record.advisories(),
                record.minimumHostRequirements(),
                record.minimumCometGuiVersion());
    }

    private static ArtefactRecord withHashes(ArtefactRecord record, FileHashes hashes) {
        return new ArtefactRecord(
                record.tool(),
                record.version(),
                record.releaseTag(),
                record.platform(),
                record.kind(),
                record.url(),
                record.sizeBytes(),
                hashes,
                record.member(),
                record.expectedExecutablePath(),
                record.executable(),
                record.licence(),
                record.companions(),
                record.capabilities(),
                record.advisories(),
                record.minimumHostRequirements(),
                record.minimumCometGuiVersion());
    }

    /**
     * Stages 1 and 2 of the product's probe over the real process service; stage 3 records the
     * version stage 2 identified and claims nothing.
     */
    private static StagedToolProbe probe(ProcessRunner processes, List<ToolVersion> identified) {
        return new StagedToolProbe(
                new LoadabilityProbe(
                        processes,
                        new LoaderOutputClassifier(LINUX, DEBIAN_12),
                        LINUX,
                        Duration.ofSeconds(30)),
                DEBIAN_12,
                Map.of(ToolName.COMET, VersionBanner.comet()),
                (tool, version, platform, executable) -> {
                    identified.add(version);
                    return EnumSet.noneOf(ToolCapability.class);
                },
                record -> List.of(),
                Map.of());
    }

    /** The real installer over the mirror's bytes, with the real probe. */
    private static final class Rig {

        private final Path root;
        private final FakeFetcher fetcher = new FakeFetcher();
        private final RecordingRunner processes = new RecordingRunner();
        private final List<ToolVersion> identified =
                Collections.synchronizedList(new ArrayList<>());
        private final ToolCache cache;
        private final ArtefactInstaller installer;

        Rig(Path root) {
            this.root = root;
            StreamingHashService hashes = new StreamingHashService();
            this.cache = new ToolCache(root, hashes);
            VerifiedDownloader downloader =
                    new VerifiedDownloader(fetcher, new ArtefactVerifier(hashes));
            this.installer =
                    new ArtefactInstaller(
                            cache,
                            downloader::fetch,
                            new ArtefactExtractor(),
                            new PlatformFixups(
                                    HostOperatingSystem.LINUX, ScriptedXattr.NEVER_CALLED),
                            probe(processes, identified),
                            hashes,
                            Clock.systemUTC());
        }

        Path root() {
            return root;
        }

        FakeFetcher fetcher() {
            return fetcher;
        }

        RecordingRunner processes() {
            return processes;
        }

        List<ToolVersion> identified() {
            return identified;
        }

        ToolCache cache() {
            return cache;
        }

        Installation install(ArtefactRecord record) throws IOException {
            return installer.install(
                    record, new RecordingInstallListener(), DownloadCancellation.never());
        }

        /**
         * The launches, with the staged path each was made from rewritten to where the file now
         * lives: the probe runs in staging (step 6) and the atomic move (step 7) comes after it.
         */
        List<List<String>> relocated(Installation installation) {
            List<List<String>> launches = new ArrayList<>();
            for (List<String> argv : processes.launched()) {
                List<String> rewritten = new ArrayList<>(argv);
                Path launched = Path.of(argv.get(0));
                Path relative =
                        launched.subpath(launched.getNameCount() - 2, launched.getNameCount());
                rewritten.set(0, installation.directory().resolve(relative).toString());
                launches.add(rewritten);
            }
            return launches;
        }
    }

    /** The real process service, recording every argument array it is asked to start. */
    private static final class RecordingRunner implements ProcessRunner {

        private final List<List<String>> launched = Collections.synchronizedList(new ArrayList<>());
        private final ProcessService delegate = new ProcessService(Clock.systemUTC());

        List<List<String>> launched() {
            return List.copyOf(launched);
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            launched.add(command.argv());
            return delegate.start(command, listener);
        }
    }
}
