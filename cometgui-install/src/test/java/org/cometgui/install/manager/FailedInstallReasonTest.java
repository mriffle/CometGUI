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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.cache.InstallStep;
import org.cometgui.install.cache.ToolProbe;
import org.cometgui.install.registry.ArchiveMember;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A failed install says why, at the port: the offer's {@link ToolOffer#failureReason()} names the
 * install step that failed and carries the cause's own words, redacted.
 *
 * <p>Every shape is a real failure of the real pipeline -- the real downloader over a loopback
 * server, the real verifier, extractor and layout check -- and the reason is asserted word for
 * word. Where a shape needs bytes the shipped manifest does not pin (an archive cut short, an
 * archive whose member is not the pinned member), the test pins them itself in a copy of the
 * shipped Percolator 3.07.1 row, so the pipeline accepts the download and fails where the shape
 * says.
 *
 * <p>Files read outside this module: the real artefacts in {@code scratch/phase05/artefacts}, as
 * every test of {@link ToolManagerFixtures} does.
 */
class FailedInstallReasonTest {

    private static final String PERCOLATOR_ZIP =
            "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";

    private static final String PERCOLATOR_DEB =
            "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";

    private static final ToolVersion PERCOLATOR_3_07_1 = ToolVersion.parse("3.07.1");

    /** What the manifest pins for the Percolator 3.07.1 Linux archive, hand-typed. */
    private static final String PINNED_SHA256 =
            "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";

    @TempDir private Path temporary;

    private static ArtefactRecord percolator() throws IOException {
        return ToolManagerFixtures.record(ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
    }

    private static ToolOffer rowFor(List<ToolOffer> offers) {
        return offers.stream()
                .filter(offer -> offer.tool() == ToolName.PERCOLATOR)
                .filter(offer -> offer.version().equals(PERCOLATOR_3_07_1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no percolator 3.07.1 row in " + offers));
    }

    /** Installs Percolator 3.07.1 and returns its row once the install has ended. */
    private static ToolOffer installed(ToolManagerHarness harness) throws InterruptedException {
        RecordingListener listener = new RecordingListener();
        harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
        assertEquals(InstallPhase.FAILED, listener.awaitTerminal().phase());
        return rowFor(harness.manager().offers());
    }

    /**
     * The shipped manifest with the Percolator 3.07.1 Linux row replaced by one that pins {@code
     * archive} -- and, when given, {@code member} -- and fetches no companion.
     */
    private static ArtefactManifest pinning(Path archive, Optional<ArchiveMember> member)
            throws IOException {
        ArtefactManifest shipped = ToolManagerFixtures.shippedManifest();
        ArtefactRecord original = percolator();
        FileHashes hashes = new StreamingHashService().hash(archive);
        ArtefactRecord pinned =
                new ArtefactRecord(
                        original.tool(),
                        original.version(),
                        original.releaseTag(),
                        original.platform(),
                        original.kind(),
                        original.url(),
                        Files.size(archive),
                        hashes,
                        member.isPresent() ? member : original.member(),
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

    private static byte[] zipOf(String name, byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    @DisplayName(
            "bytes served that are not the pinned bytes: step 1, the verifier's SHA-256 mismatch"
                    + " with both digests -- not an availability failure")
    void sha256Mismatch() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        Path corrupted = temporary.resolve("corrupted-percolator.zip");
        byte[] bytes = Files.readAllBytes(ToolManagerFixtures.artefact(PERCOLATOR_ZIP));
        bytes[bytes.length / 2] ^= 0x5a;
        Files.write(corrupted, bytes);
        FileHashes actual = new StreamingHashService().hash(corrupted);
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(record.url(), corrupted)
                        .serve(
                                record.companions().get(0).url(),
                                ToolManagerFixtures.artefact(PERCOLATOR_DEB))) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();

            ToolOffer row = installed(harness);

            Path downloaded =
                    harness.cache()
                            .downloadDirectory(record.tool(), record.version(), record.platform())
                            .resolve("artefact-percolator-noxml-ubuntu-portable.zip");
            assertAll(
                    () -> assertEquals(ToolInstallState.FAILED, row.state()),
                    () ->
                            assertEquals(
                                    Optional.of(
                                            "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE,"
                                                    + " failed: SHA-256 mismatch, so the artefact"
                                                    + " is rejected and is never executed"
                                                    + " (R-SEC-02): expected "
                                                    + record.hashes().sha256()
                                                    + " but "
                                                    + downloaded
                                                    + " hashes to "
                                                    + actual.sha256()
                                                    + ", downloaded from "
                                                    + record.url()
                                                    + ". MD5 is recorded for provenance and"
                                                    + " decides nothing: expected "
                                                    + record.hashes().md5()
                                                    + ", actual "
                                                    + actual.md5()
                                                    + ". (after 1 transfer attempt(s))"),
                                    row.failureReason()),
                    () ->
                            assertFalse(
                                    row.failureReason().orElseThrow().contains("available"),
                                    "a corrupt download is not worded as an availability"
                                            + " failure"));
        }
    }

    @Test
    @DisplayName(
            "an artefact gone upstream (404): step 1, D-008's availability failure naming the URL"
                    + " and the pinned SHA-256 -- not a corrupt download, not a probe failure")
    void goneUpstream() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        try (ServedArtefacts served = new ServedArtefacts()) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();

            ToolOffer row = installed(harness);

            String reason = row.failureReason().orElseThrow();
            assertAll(
                    () -> assertEquals(ToolInstallState.FAILED, row.state()),
                    () ->
                            assertEquals(
                                    "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE, failed: the"
                                            + " pinned artefact is no longer available upstream:"
                                            + " HTTP 404 for "
                                            + served.servedAt(record.url())
                                            + "; expected sha-256 "
                                            + PINNED_SHA256
                                            + ". CometGUI redistributes no tool binary and holds"
                                            + " no copy (D-008), so this is an upstream"
                                            + " availability failure, not a corrupt download and"
                                            + " not a probe failure: the release asset has been"
                                            + " removed or re-tagged.",
                                    reason),
                    () ->
                            assertFalse(
                                    reason.contains("mismatch"),
                                    "never worded as a digest failure: " + reason),
                    () ->
                            assertFalse(
                                    reason.contains("PROBE"),
                                    "never worded as a probe failure: " + reason));
        }
    }

    @Test
    @DisplayName("a damaged archive: step 3, the extractor's own words")
    void damagedArchive() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        byte[] whole = Files.readAllBytes(ToolManagerFixtures.artefact(PERCOLATOR_ZIP));
        Path cut = temporary.resolve("cut-short.zip");
        Files.write(cut, Arrays.copyOf(whole, whole.length / 2));
        try (ServedArtefacts served = new ServedArtefacts().serve(record.url(), cut)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .over(pinning(cut, Optional.empty()))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();

            ToolOffer row = installed(harness);

            assertAll(
                    () -> assertEquals(ToolInstallState.FAILED, row.state()),
                    () ->
                            assertEquals(
                                    Optional.of(
                                            "Install step 3 of 8, EXTRACT_WITH_GUARDS, failed: the"
                                                    + " artefact"
                                                    + " \"artefact-percolator-noxml-ubuntu-"
                                                    + "portable.zip\""
                                                    + " was rejected because its container"
                                                    + " structure is not readable -- it has no"
                                                    + " end-of-central-directory record, so it is"
                                                    + " not a zip archive or it was truncated"),
                                    row.failureReason()));
        }
    }

    @Test
    @DisplayName("an archive whose member is not the pinned member: step 4, the layout check")
    void layoutCheck() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        ArchiveMember pinnedMember = record.member().orElseThrow();
        byte[] impostor = new byte[(int) pinnedMember.sizeBytes()];
        Arrays.fill(impostor, (byte) 'x');
        Path archive = temporary.resolve("impostor.zip");
        Files.write(archive, zipOf(pinnedMember.path(), impostor));
        FileHashes impostorHashes = new StreamingHashService().hash(writeTo("impostor", impostor));
        try (ServedArtefacts served = new ServedArtefacts().serve(record.url(), archive)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .over(pinning(archive, Optional.empty()))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();

            ToolOffer row = installed(harness);

            assertAll(
                    () -> assertEquals(ToolInstallState.FAILED, row.state()),
                    () ->
                            assertEquals(
                                    Optional.of(
                                            "Install step 4 of 8, VERIFY_EXPECTED_LAYOUT,"
                                                    + " failed: percolator 3.07.1 linux-x86-64 was"
                                                    + " not installed: \"bin/percolator\" came out"
                                                    + " of the artefact hashing to "
                                                    + impostorHashes.sha256()
                                                    + " and the manifest records "
                                                    + pinnedMember.hashes().sha256()
                                                    + " for member \"percolator\" (install step 4,"
                                                    + " VERIFY_EXPECTED_LAYOUT)"),
                                    row.failureReason()));
        }
    }

    @Test
    @DisplayName("a build the probe refuses: step 6, the probe's own words")
    void probeRefusal() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        ToolProbe refusing =
                (artefact, staged) -> {
                    throw new IOException(
                            "percolator exited with status 127: error while loading shared"
                                    + " libraries: libboost_system.so.1.66.0");
                };
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(record.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                        .serve(
                                record.companions().get(0).url(),
                                ToolManagerFixtures.artefact(PERCOLATOR_DEB))) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(refusing)
                            .build();

            ToolOffer row = installed(harness);

            assertAll(
                    () -> assertEquals(ToolInstallState.FAILED, row.state()),
                    () ->
                            assertEquals(
                                    Optional.of(
                                            "Install step 6 of 8, PROBE, failed: percolator 3.07.1"
                                                    + " linux-x86-64 was not installed: the"
                                                    + " installed build was not accepted by the"
                                                    + " probe: percolator exited with status 127:"
                                                    + " error while loading shared libraries:"
                                                    + " libboost_system.so.1.66.0 (install step 6,"
                                                    + " PROBE)"),
                                    row.failureReason()));
        }
    }

    @Test
    @DisplayName(
            "a secret in the cause's words reaches the state redacted, by the one redactor, and"
                    + " the rest of the sentence intact")
    void aSecretIsRedacted() throws IOException, InterruptedException {
        String token = "s3cr3t-T0ken-value-1234567890";
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .fetching(
                                request -> {
                                    throw new IOException(
                                            "the request to https://mirror.invalid/percolator.zip"
                                                    + "?token="
                                                    + token
                                                    + " was refused");
                                })
                        .probing(FixedProbe.refusingToBeCalled())
                        .build();

        ToolOffer row = installed(harness);

        String reason = row.failureReason().orElseThrow();
        assertAll(
                () ->
                        assertEquals(
                                "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE, failed: the"
                                        + " request to https://mirror.invalid/percolator.zip"
                                        + "?token=[REDACTED] was refused",
                                reason),
                () -> assertFalse(reason.contains(token), "the token itself: " + reason),
                () -> assertFalse(row.toString().contains(token), "nor the offer's toString"));
    }

    @Test
    @DisplayName(
            "a later attempt replaces the failure: INSTALLING with no reason while it runs, then"
                    + " INSTALLED with none")
    void aRetryClearsTheReason() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        try (ServedArtefacts served = new ServedArtefacts()) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .build();
            ToolOffer failed = installed(harness);

            served.serve(record.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                    .serve(
                            record.companions().get(0).url(),
                            ToolManagerFixtures.artefact(PERCOLATOR_DEB));
            served.holdTransfers();
            RecordingListener again = new RecordingListener();
            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, again);
            ToolOffer running = rowFor(harness.manager().offers());
            served.release();
            assertEquals(InstallPhase.DONE, again.awaitTerminal().phase());
            ToolOffer done = rowFor(harness.manager().offers());

            assertAll(
                    () -> assertEquals(ToolInstallState.FAILED, failed.state()),
                    () -> assertEquals(ToolInstallState.INSTALLING, running.state()),
                    () -> assertEquals(Optional.empty(), running.failureReason()),
                    () -> assertEquals(ToolInstallState.INSTALLED, done.state()),
                    () -> assertEquals(Optional.empty(), done.failureReason()));
        }
    }

    @Test
    @DisplayName("the step is named by number and constant; outside the steps, it says so")
    void theReasonsShape() {
        assertAll(
                () ->
                        assertEquals(
                                "Install step 7 of 8, MOVE_ATOMICALLY_INTO_CACHE, failed: denied",
                                ManagedToolManager.reasonFor(
                                        Optional.of(InstallStep.MOVE_ATOMICALLY_INTO_CACHE),
                                        new IOException("denied"))),
                () ->
                        assertEquals(
                                "The install failed outside its 8 steps (taking or releasing the"
                                        + " install lock, or clearing what an earlier attempt"
                                        + " left): locked",
                                ManagedToolManager.reasonFor(
                                        Optional.empty(), new IOException("locked"))),
                () ->
                        assertEquals(
                                "Install step 3 of 8, EXTRACT_WITH_GUARDS, failed:"
                                        + " java.util.zip.ZipException",
                                ManagedToolManager.reasonFor(
                                        Optional.of(InstallStep.EXTRACT_WITH_GUARDS),
                                        new java.util.zip.ZipException()),
                                "an exception with no message is named by its class"),
                () ->
                        assertEquals(
                                "Install step 6 of 8, PROBE, failed: java.lang.AssertionError",
                                ManagedToolManager.reasonFor(
                                        Optional.of(InstallStep.PROBE), new AssertionError(" ")),
                                "and so is one whose message is blank"));
    }

    private Path writeTo(String name, byte[] content) throws IOException {
        return Files.write(temporary.resolve(name), content);
    }
}
