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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.ScriptedChooser;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.cache.ArtefactInstaller;
import org.cometgui.install.cache.PlatformFixups;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.download.ArtefactFetcher;
import org.cometgui.install.manager.ManagedToolManager;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.ArtefactVerifier;
import org.cometgui.install.verify.VerifiedDownloader;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.cometgui.ui.viewmodel.ToolRowViewModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * A failed install's reason reaches all three places it is owed, word for word: the port's offer,
 * the Tool Manager's row, and the log -- for each real way an install fails.
 *
 * <p>What is real: {@link ManagedToolManager} over the real downloader (through {@link
 * LoopbackArtefactServer}, never a network host), the real verifier, extractor, layout check,
 * platform fix-ups and the real probe {@link ToolManagerWiring#probe} composes, which executes the
 * staged binary through the process service; and the real {@link ToolManagerViewModel}, with the
 * log line it writes collected where the application would append it to the console's log. Where a
 * shape needs bytes the shipped manifest does not pin -- an archive cut short, an executable that
 * exits non-zero -- the test pins them itself in a copy of the shipped row, so the pipeline accepts
 * the download and fails where the shape says.
 *
 * <p>Files read outside this module: the real artefacts in {@code scratch/phase05/artefacts}, as
 * {@link ArtefactMirror} reads them.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason = "the probe shape executes a POSIX shell script as the staged binary")
class FailedInstallReasonTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String PERCOLATOR_ZIP =
            "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";

    private static final String PERCOLATOR_DEB =
            "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";

    private static final String PERCOLATOR_ROW = "percolator-3_07_1-1";

    private static final String COMET_ROW = "comet-2026_03_0-1";

    @TempDir private Path temporary;

    /** What one failed install left in each of the three places. */
    private record Said(ToolOffer offer, Optional<String> row, List<String> log) {}

    private static ArtefactRecord percolator() throws IOException {
        return ArtefactMirror.record(ToolName.PERCOLATOR, "3.07.1", LINUX);
    }

    private static ArtefactRecord comet() throws IOException {
        return ArtefactMirror.record(ToolName.COMET, "2026.03.0", LINUX);
    }

    /** The shipped manifest with {@code original} replaced by a row pinning {@code file}. */
    private static ArtefactManifest pinning(ArtefactRecord original, Path file) throws IOException {
        ArtefactManifest shipped = ArtefactManifestReader.readFromClasspath();
        FileHashes hashes = new StreamingHashService().hash(file);
        ArtefactRecord pinned =
                new ArtefactRecord(
                        original.tool(),
                        original.version(),
                        original.releaseTag(),
                        original.platform(),
                        original.kind(),
                        original.url(),
                        Files.size(file),
                        hashes,
                        original.member(),
                        original.expectedExecutablePath(),
                        original.executable(),
                        original.licence(),
                        List.of(),
                        original.capabilities(),
                        original.advisories(),
                        original.minimumHostRequirements(),
                        original.minimumCometGuiVersion());
        List<ArtefactRecord> rows = new ArrayList<>();
        for (ArtefactRecord row : shipped.artefacts()) {
            rows.add(row.equals(original) ? pinned : row);
        }
        return new ArtefactManifest(shipped.schemaVersion(), rows);
    }

    /**
     * Installs one row through the Tool Manager view-model, on the calling thread, and collects
     * what the port, the row and the log then say.
     */
    private Said install(ArtefactManifest manifest, ArtefactFetcher fetcher, String rowKey)
            throws IOException {
        HashService hashes = new StreamingHashService();
        ToolCache cache = new ToolCache(temporary.resolve("cache"), hashes);
        ProcessService processes = new ProcessService(Clock.systemUTC());
        ArtefactInstaller installer =
                new ArtefactInstaller(
                        cache,
                        new VerifiedDownloader(fetcher, new ArtefactVerifier(hashes))::fetch,
                        new ArtefactExtractor(),
                        new PlatformFixups(LINUX.operatingSystem(), processes),
                        ToolManagerWiring.probe(manifest, LINUX, DEBIAN_12, processes),
                        hashes,
                        Clock.systemUTC());
        ManagedToolManager manager =
                new ManagedToolManager(
                        manifest,
                        LINUX,
                        DEBIAN_12,
                        installer,
                        record -> Optional.empty(),
                        Map.of(),
                        Runnable::run);
        List<String> logged = new ArrayList<>();
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(
                        manager, Runnable::run, Runnable::run, new ScriptedChooser(), logged::add);
        viewModel.refresh();
        ToolRowViewModel row = rowOf(viewModel, rowKey);
        viewModel.install(row);
        ToolOffer offer =
                manager.offers().stream()
                        .filter(candidate -> candidate.tool() == row.offer().tool())
                        .filter(candidate -> candidate.version().equals(row.offer().version()))
                        .findFirst()
                        .orElseThrow();
        return new Said(offer, rowOf(viewModel, rowKey).failureText(), List.copyOf(logged));
    }

    private static ToolRowViewModel rowOf(ToolManagerViewModel viewModel, String key) {
        return viewModel.rows().stream()
                .filter(row -> row.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row " + key + " in " + viewModel.rows()));
    }

    /** The same words in all three places, and the state FAILED. */
    private static void saidEverywhere(Said said, String name, String reason) {
        assertAll(
                () -> assertEquals(ToolInstallState.FAILED, said.offer().state()),
                () -> assertEquals(Optional.of(reason), said.offer().failureReason(), "the port"),
                () -> assertEquals(Optional.of(reason), said.row(), "the Tool Manager's row"),
                () ->
                        assertEquals(
                                List.of("The install of " + name + " did not succeed. " + reason),
                                said.log(),
                                "the log, once"));
    }

    @Test
    @DisplayName("bytes that are not the pinned bytes: step 1, the SHA-256 mismatch, everywhere")
    void sha256Mismatch() throws IOException {
        ArtefactRecord record = percolator();
        Path corrupted = temporary.resolve("corrupted.zip");
        byte[] bytes = Files.readAllBytes(ArtefactMirror.artefact(PERCOLATOR_ZIP));
        bytes[bytes.length / 2] ^= 0x5a;
        Files.write(corrupted, bytes);
        FileHashes actual = new StreamingHashService().hash(corrupted);
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer()
                        .serve(record.url(), corrupted)
                        .serve(
                                record.companions().get(0).url(),
                                ArtefactMirror.artefact(PERCOLATOR_DEB))) {
            Said said = install(ArtefactManifestReader.readFromClasspath(), served, PERCOLATOR_ROW);

            Path downloaded =
                    temporary
                            .resolve("cache/cache/downloads/percolator__3.7.1__linux-x86-64")
                            .resolve("artefact-percolator-noxml-ubuntu-portable.zip");
            saidEverywhere(
                    said,
                    "percolator 3.07.1",
                    "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE, failed: SHA-256 mismatch, so"
                            + " the artefact is rejected and is never executed (R-SEC-02):"
                            + " expected "
                            + record.hashes().sha256()
                            + " but "
                            + downloaded
                            + " hashes to "
                            + actual.sha256()
                            + ", downloaded from "
                            + record.url()
                            + ". MD5 is recorded for provenance and decides nothing: expected "
                            + record.hashes().md5()
                            + ", actual "
                            + actual.md5()
                            + ". (after 1 transfer attempt(s))");
        }
    }

    @Test
    @DisplayName("an archive cut short: step 3, the extractor's words, everywhere")
    void damagedArchive() throws IOException {
        ArtefactRecord record = percolator();
        byte[] whole = Files.readAllBytes(ArtefactMirror.artefact(PERCOLATOR_ZIP));
        Path cut = temporary.resolve("cut-short.zip");
        Files.write(cut, Arrays.copyOf(whole, whole.length / 2));
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer().serve(record.url(), cut)) {
            Said said = install(pinning(record, cut), served, PERCOLATOR_ROW);

            saidEverywhere(
                    said,
                    "percolator 3.07.1",
                    "Install step 3 of 8, EXTRACT_WITH_GUARDS, failed: the artefact"
                            + " \"artefact-percolator-noxml-ubuntu-portable.zip\" was rejected"
                            + " because its container structure is not readable -- it has no"
                            + " end-of-central-directory record, so it is not a zip archive or it"
                            + " was truncated");
        }
    }

    @Test
    @DisplayName("an executable that does not run: step 6, the real probe's words, everywhere")
    void anExecutableThatExitsNonZero() throws IOException {
        ArtefactRecord record = comet();
        Path broken = temporary.resolve("comet.linux.exe");
        Files.writeString(
                broken,
                "#!/bin/sh\necho 'comet: this build cannot start here' >&2\nexit 3\n",
                StandardCharsets.US_ASCII);
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer().serve(record.url(), broken)) {
            Said said = install(pinning(record, broken), served, COMET_ROW);

            saidEverywhere(
                    said,
                    "comet 2026.03.0",
                    "Install step 6 of 8, PROBE, failed: comet 2026.03.0 linux-x86-64 was not"
                            + " installed: the installed build was not accepted by the probe:"
                            + " comet 2026.03.0 linux-x86-64 printed no version this build"
                            + " recognises: 1 line(s) of output were searched, standard error"
                            + " first; it exited with status 3 (install step 6, PROBE)");
        }
    }

    /*
     * THE CAPABILITY STAGE, WHICH QUOTES WHAT THE TOOL PRINTED.  This stand-in answers -h with
     * Comet's banner and so passes the first two stages; asked what it can do, it prints one line
     * with no banner in it and exits 5.  The capability probe's refusal carries that exit status
     * and that line, and the line is the one place in a probe's message where text this product
     * did not write arrives -- so the token in it must be gone from all three places.  The staged
     * path in the middle of the message is the installer's own, named afresh for every attempt,
     * which is why the two halves are asserted around it.
     */
    @Test
    @DisplayName(
            "a build the capability probe cannot exercise: step 6, with the exit status and the"
                    + " tool's own words -- redacted -- everywhere")
    void aCapabilityRefusalCarriesTheExitStatusAndNoSecret() throws IOException {
        String token = "s3cr3t-T0ken-value-1234567890";
        ArtefactRecord record = comet();
        Path broken = temporary.resolve("comet.linux.exe");
        Files.writeString(
                broken,
                "#!/bin/sh\n"
                        + "if [ \"$1\" = \"-h\" ]; then\n"
                        + "  echo ' Comet version \"2026.03 rev. 0 (fa08489)\"' >&2\n"
                        + "  exit 1\n"
                        + "fi\n"
                        + "echo 'no licence from https://licence.invalid/check?token="
                        + token
                        + "' >&2\n"
                        + "exit 5\n",
                StandardCharsets.US_ASCII);
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer().serve(record.url(), broken)) {
            Said said = install(pinning(record, broken), served, COMET_ROW);

            String reason = said.offer().failureReason().orElseThrow();
            String opening =
                    "Install step 6 of 8, PROBE, failed: comet 2026.03.0 linux-x86-64 was not"
                            + " installed: the installed build was not accepted by the probe:"
                            + " Comet 2026.03.0 at "
                            + temporary.resolve("cache/cache/staging/comet__2026.3__linux-x86-64")
                            + "/";
            String closing =
                    "/payload/bin/comet never printed its version banner in answer to -p, so it"
                            + " did not run far enough to be asked what it can do; this is a"
                            + " loadability failure and must not be reported as a missing"
                            + " capability. It exited 5 saying: no licence from"
                            + " https://licence.invalid/check?token=[REDACTED] (install step 6,"
                            + " PROBE)";
            assertAll(
                    () -> saidEverywhere(said, "comet 2026.03.0", reason),
                    () -> assertEquals(opening, reason.substring(0, opening.length()), reason),
                    () ->
                            assertEquals(
                                    closing,
                                    reason.substring(reason.length() - closing.length()),
                                    reason),
                    () ->
                            assertFalse(
                                    String.join("\n", said.log()).contains(token)
                                            || said.row().orElseThrow().contains(token)
                                            || said.offer().toString().contains(token),
                                    "the token itself reached one of them"));
        }
    }

    @Test
    @DisplayName(
            "an artefact gone upstream (404): step 1, D-008's availability wording with the URL"
                    + " and the pinned SHA-256, everywhere -- never a digest or probe failure")
    void goneUpstream() throws IOException {
        ArtefactRecord record = percolator();
        try (LoopbackArtefactServer served = new LoopbackArtefactServer()) {
            Said said = install(ArtefactManifestReader.readFromClasspath(), served, PERCOLATOR_ROW);

            String reason =
                    "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE, failed: the pinned artefact"
                            + " is no longer available upstream: HTTP 404 for "
                            + served.servedAt(record.url())
                            + "; expected sha-256 "
                            + record.hashes().sha256()
                            + ". CometGUI redistributes no tool binary and holds no copy (D-008),"
                            + " so this is an upstream availability failure, not a corrupt"
                            + " download and not a probe failure: the release asset has been"
                            + " removed or re-tagged.";
            assertAll(
                    () -> saidEverywhere(said, "percolator 3.07.1", reason),
                    () -> assertFalse(reason.contains("mismatch")),
                    () -> assertFalse(reason.contains("PROBE")));
        }
    }

    @Test
    @DisplayName("a secret in the cause's words reaches neither the row nor the log, nor the port")
    void aSecretReachesNoneOfThem() throws IOException {
        String token = "s3cr3t-T0ken-value-1234567890";
        ArtefactFetcher refusing =
                request -> {
                    throw new IOException(
                            "the request to https://mirror.invalid/percolator.zip?token="
                                    + token
                                    + " was refused");
                };

        Said said = install(ArtefactManifestReader.readFromClasspath(), refusing, PERCOLATOR_ROW);

        saidEverywhere(
                said,
                "percolator 3.07.1",
                "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE, failed: the request to"
                        + " https://mirror.invalid/percolator.zip?token=[REDACTED] was refused");
        assertFalse(
                String.join("\n", said.log()).contains(token)
                        || said.row().orElseThrow().contains(token)
                        || said.offer().toString().contains(token),
                "the token itself reached one of them");
    }
}
