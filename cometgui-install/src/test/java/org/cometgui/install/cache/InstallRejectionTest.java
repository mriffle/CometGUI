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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.archive.ExtractionRejectedException;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.ArtefactVerificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every way an install can be refused, graded over the axes the rules do not depend on.
 *
 * <p>Two of this phase's rules are asserted here and both are graded rather than sampled.
 *
 * <ul>
 *   <li><strong>A corrupted download is never executed</strong> ({@code R-SEC-02}, exit gate item
 *       2). Over <em>every artefact kind</em>, because a rule proved for one kind can be switched
 *       off for the others with nothing going red. The probe -- the only route from this installer
 *       to a process -- fails the test if it is entered at all.
 *   <li><strong>A failed install leaves the tool cache untouched.</strong> Over <em>which step
 *       failed</em>, and over <em>whether another tool was already installed</em>, proved by
 *       comparing a description of the whole {@code tools} subtree before and after rather than by
 *       believing the exception.
 * </ul>
 */
class InstallRejectionTest {

    @TempDir private Path temporary;

    /** The artefact shapes the manifest really holds, so that a rule is proved over all of them. */
    static List<org.junit.jupiter.params.provider.Arguments> everyArtefactKind() {
        byte[] binary = CacheFixtures.bytes("#!/bin/sh\necho tool\n");
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", binary));
        Map<String, byte[]> whole = new LinkedHashMap<>();
        whole.put("bin/pdv", binary);
        whole.put("lib/one.jar", CacheFixtures.bytes("one"));
        byte[] wholeArchive = CacheFixtures.zip(whole);
        byte[] jar =
                CacheFixtures.zip(
                        CacheFixtures.entry(
                                "META-INF/MANIFEST.MF",
                                CacheFixtures.bytes("Manifest-Version: 1.0\n")));
        return List.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "BARE_EXECUTABLE",
                        CacheFixtures.bareExecutable(
                                ToolName.COMET, "2026.02.2", binary, "comet.linux.exe", List.of()),
                        binary),
                org.junit.jupiter.params.provider.Arguments.of(
                        "ZIP named member",
                        CacheFixtures.namedMember(
                                ToolName.PERCOLATOR,
                                "3.07.1",
                                archive,
                                "percolator",
                                binary,
                                "bin/percolator",
                                List.of()),
                        archive),
                org.junit.jupiter.params.provider.Arguments.of(
                        "ZIP whole artefact",
                        CacheFixtures.wholeArchive(ToolName.PDV, "2.7.0", wholeArchive, "bin/pdv"),
                        wholeArchive),
                org.junit.jupiter.params.provider.Arguments.of(
                        "JAR",
                        CacheFixtures.jar(
                                ToolName.LIMELIGHT_CONVERTER, "2.8.1", jar, "converter.jar"),
                        jar));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyArtefactKind")
    @DisplayName("a corrupted download is rejected and the tool is never executed")
    void aCorruptedDownloadNeverReachesTheProbe(String kind, ArtefactRecord record, byte[] artefact)
            throws IOException, InterruptedException {
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusingToBeCalled(),
                        HostOperatingSystem.LINUX);
        harness.fetcher().serve(record.url(), corrupt(artefact));
        Snapshot before = harness.toolsSnapshot();

        ArtefactVerificationException rejected =
                assertThrows(ArtefactVerificationException.class, () -> harness.install(record));

        assertEquals(
                record.hashes().sha256(),
                rejected.expectedSha256(),
                () -> kind + ": the rejection names the digest the manifest pinned");
        assertTrue(
                rejected.actualSha256().isPresent()
                        && !rejected.actualSha256().orElseThrow().equals(record.hashes().sha256()),
                () -> kind + ": and the digest the bytes really had -- " + rejected.getMessage());
        assertEquals(
                0,
                harness.probe().callCount(),
                () -> kind + ": nothing may execute an artefact that failed its checksum");
        assertEquals(
                before,
                harness.toolsSnapshot(),
                () -> kind + ": the tool cache must be exactly as it was");
        assertEquals(
                InstallPhase.FAILED,
                harness.listener().phases().get(harness.listener().phases().size() - 1),
                () -> kind + ": a checksum failure is a failure, and is reported as one");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyArtefactKind")
    @DisplayName("a corrupted download leaves no tool directory, whatever kind it is")
    void aCorruptedDownloadLeavesNoDirectory(String kind, ArtefactRecord record, byte[] artefact)
            throws IOException, InterruptedException {
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusingToBeCalled(),
                        HostOperatingSystem.LINUX);
        harness.fetcher().serve(record.url(), corrupt(artefact));

        assertThrows(ArtefactVerificationException.class, () -> harness.install(record));

        assertEquals(
                InstallationState.NOT_PRESENT,
                harness.verify(record).state(),
                () -> kind + ": no directory, so nothing to mistake for an install");
        assertEquals(List.of(), harness.stagingDirectories(record), () -> kind + ": staging swept");
    }

    @Test
    @DisplayName("a companion whose bytes do not match its recorded digest fails the install")
    void aCompanionWhoseBytesDoNotMatchItsRecordedDigestFailsTheInstall()
            throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes("#!/bin/sh\necho percolator\n");
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", binary));
        byte[] declaredSchema =
                CacheFixtures.bytes("<?xml version=\"1.0\"?><xs:schema id=\"right\"/>\n");
        byte[] actualSchema =
                CacheFixtures.bytes("<?xml version=\"1.0\"?><xs:schema id=\"wrong\"/>\n");
        byte[] companionArchive =
                CacheFixtures.zip(CacheFixtures.entry("usr/share/x.xsd", actualSchema));
        ArtefactRecord record =
                CacheFixtures.namedMember(
                        ToolName.PERCOLATOR,
                        "3.07.1",
                        archive,
                        "percolator",
                        binary,
                        "bin/percolator",
                        List.of(
                                CacheFixtures.zipCompanion(
                                        "xsd",
                                        companionArchive,
                                        List.of(
                                                CacheFixtures.member(
                                                        "usr/share/x.xsd",
                                                        declaredSchema,
                                                        "share/x.xsd")))));
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusingToBeCalled(),
                        HostOperatingSystem.LINUX);
        harness.fetcher()
                .serve(record.url(), archive)
                .serve(record.companions().get(0).url(), companionArchive);
        Snapshot before = harness.toolsSnapshot();

        InstallRejectedException rejected =
                assertThrows(InstallRejectedException.class, () -> harness.install(record));

        assertSame(InstallFailure.MEMBER_DIGEST_MISMATCH, rejected.failure());
        assertSame(InstallStep.VERIFY_EXPECTED_LAYOUT, rejected.step());
        assertEquals("share/x.xsd", rejected.path().orElseThrow());
        assertTrue(
                rejected.getMessage().contains(CacheFixtures.hashesOf(actualSchema).sha256())
                        && rejected.getMessage()
                                .contains(CacheFixtures.hashesOf(declaredSchema).sha256()),
                () ->
                        "the diagnostic names both digests, the one that came out and the one the"
                                + " manifest records: "
                                + rejected.getMessage());
        assertEquals(0, harness.probe().callCount(), "and nothing was executed");
        assertEquals(before, harness.toolsSnapshot(), "and the cache is untouched");
    }

    @Test
    @DisplayName("a corrupted COMPANION download is rejected too, and nothing is executed")
    void aCorruptedCompanionDownloadNeverReachesTheProbe()
            throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusingToBeCalled(),
                        HostOperatingSystem.LINUX);
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        ArtefactCompanion companion = record.companions().get(0);
        harness.fetcher()
                .serve(
                        companion.url(),
                        corrupt(Files.readAllBytes(fixture.resolve("companion.zip"))));
        Snapshot before = harness.toolsSnapshot();

        ArtefactVerificationException rejected =
                assertThrows(ArtefactVerificationException.class, () -> harness.install(record));

        assertEquals(
                companion.hashes().sha256(),
                rejected.expectedSha256(),
                "R-SEC-02 is not only about the primary artefact: a companion is a download too");
        assertEquals(companion.url(), rejected.source());
        assertEquals(0, harness.probe().callCount());
        assertEquals(before, harness.toolsSnapshot());
        assertEquals(InstallationState.NOT_PRESENT, harness.verify(record).state());
    }

    @Test
    @DisplayName("the archive member the manifest names is checked against its recorded digest too")
    void theNamedMemberIsCheckedAgainstItsRecordedDigest()
            throws IOException, InterruptedException {
        byte[] declared =
                CacheFixtures.bytes("#!/bin/sh\necho the binary the manifest describes\n");
        byte[] actual = CacheFixtures.bytes("#!/bin/sh\necho a different binary entirely!!!!!!\n");
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", actual));
        ArtefactRecord record =
                CacheFixtures.namedMember(
                        ToolName.PERCOLATOR,
                        "3.07.1",
                        archive,
                        "percolator",
                        declared,
                        "bin/percolator",
                        List.of());
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusingToBeCalled(),
                        HostOperatingSystem.LINUX);
        harness.fetcher().serve(record.url(), archive);

        InstallRejectedException rejected =
                assertThrows(InstallRejectedException.class, () -> harness.install(record));

        assertSame(InstallFailure.MEMBER_DIGEST_MISMATCH, rejected.failure());
        assertEquals("bin/percolator", rejected.path().orElseThrow());
        assertTrue(
                rejected.getMessage().contains(CacheFixtures.hashesOf(actual).sha256()),
                rejected::getMessage);
        assertEquals(0, harness.probe().callCount());
    }

    @Test
    @DisplayName("an expected path the artefact supplied as a directory is a layout failure")
    void anExpectedPathThatIsNotAFileIsALayoutFailure() throws IOException, InterruptedException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("bin/pdv/", new byte[0]);
        entries.put("bin/pdv/inner", CacheFixtures.bytes("inner"));
        byte[] archive = CacheFixtures.zip(entries);
        ArtefactRecord record =
                CacheFixtures.wholeArchive(ToolName.PDV, "2.7.0", archive, "bin/pdv");
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusingToBeCalled(),
                        HostOperatingSystem.LINUX);
        harness.fetcher().serve(record.url(), archive);

        InstallRejectedException rejected =
                assertThrows(InstallRejectedException.class, () -> harness.install(record));

        assertSame(InstallFailure.LAYOUT_INCOMPLETE, rejected.failure());
        assertSame(InstallStep.VERIFY_EXPECTED_LAYOUT, rejected.step());
        assertTrue(
                rejected.getMessage().contains("bin/pdv"),
                () -> "the diagnostic names the path it wanted: " + rejected.getMessage());
        assertEquals(0, harness.probe().callCount());
    }

    @Test
    @DisplayName("a probe refusal stops the install before anything reaches the cache")
    void aProbeRefusalStopsTheInstallBeforeTheMove() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusing(
                                "error while loading shared libraries: libboost_filesystem.so"),
                        HostOperatingSystem.LINUX);
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        Snapshot before = harness.toolsSnapshot();

        InstallRejectedException rejected =
                assertThrows(InstallRejectedException.class, () -> harness.install(record));

        assertSame(InstallFailure.PROBE_FAILED, rejected.failure());
        assertSame(InstallStep.PROBE, rejected.step());
        assertTrue(
                rejected.getMessage().contains("libboost_filesystem.so"),
                () ->
                        "the probe's own diagnostic is what a scientist has to read, so it survives"
                                + " into the message: "
                                + rejected.getMessage());
        assertInstanceOf(IOException.class, rejected.getCause());
        assertEquals(before, harness.toolsSnapshot(), "R-TOOL-06: it never becomes a cache entry");
        assertEquals(InstallationState.NOT_PRESENT, harness.verify(record).state());
    }

    @ParameterizedTest(name = "failing at {0}")
    @ValueSource(strings = {"checksum", "extraction", "member digest", "probe"})
    @DisplayName("a failed install leaves the tool cache byte for byte as it was")
    void aFailedInstallLeavesTheToolCacheUntouched(String failure)
            throws IOException, InterruptedException {
        Path root = temporary.resolve("cache");

        /*
         * A TOOL THAT IS ALREADY INSTALLED, in a separate installer over the same cache root.  The
         * axis being graded is "whether a previous install existed": an empty cache would satisfy
         * "unchanged" for the wrong reason, and it would not show that a failure leaves ANOTHER
         * tool's entry alone.
         */
        byte[] comet = CacheFixtures.bytes("#!/bin/sh\necho comet\n");
        ArtefactRecord installed =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", comet, "comet.linux.exe", List.of());
        InstallHarness setup =
                new InstallHarness(root, RecordingProbe.confirming(), HostOperatingSystem.LINUX);
        setup.fetcher().serve(installed.url(), comet);
        setup.install(installed);
        assertTrue(setup.verify(installed).installed());

        InstallHarness harness =
                new InstallHarness(
                        root,
                        "probe".equals(failure)
                                ? RecordingProbe.refusing("this build does not load here")
                                : RecordingProbe.refusingToBeCalled(),
                        HostOperatingSystem.LINUX);
        Snapshot before = harness.toolsSnapshot();
        assertFalse(
                before.isEmpty(), "the cache must hold something for this test to mean anything");

        ArtefactRecord failing = failingRecord(harness, failure);
        assertThrows(IOException.class, () -> harness.install(failing));

        assertEquals(
                before,
                harness.toolsSnapshot(),
                () ->
                        "an install that failed at the "
                                + failure
                                + " step changed the tool cache; nothing but step 7 may write"
                                + " there");
        assertEquals(
                InstallationState.NOT_PRESENT,
                harness.verify(failing).state(),
                () -> "and left no directory for the artefact it failed on: " + failure);
        assertTrue(
                harness.verify(installed).installed(),
                () -> "and did not disturb the tool that was already there: " + failure);
        assertEquals(
                List.of(),
                harness.stagingDirectories(failing),
                () -> "and discarded everything it staged: " + failure);
    }

    private ArtefactRecord failingRecord(InstallHarness harness, String failure) {
        byte[] binary = CacheFixtures.bytes("#!/bin/sh\necho percolator\n");
        byte[] other = CacheFixtures.bytes("#!/bin/sh\necho not the same binary at all\n");
        switch (failure) {
            case "checksum" -> {
                byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", binary));
                ArtefactRecord record =
                        CacheFixtures.namedMember(
                                ToolName.PERCOLATOR,
                                "3.07.1",
                                archive,
                                "percolator",
                                binary,
                                "bin/percolator",
                                List.of());
                harness.fetcher().serve(record.url(), corrupt(archive));
                return record;
            }
            case "extraction" -> {
                byte[] archive = CacheFixtures.zip(CacheFixtures.entry("../escape", binary));
                ArtefactRecord record =
                        CacheFixtures.wholeArchive(
                                ToolName.PERCOLATOR, "3.07.1", archive, "bin/percolator");
                harness.fetcher().serve(record.url(), archive);
                return record;
            }
            case "member digest" -> {
                byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", other));
                ArtefactRecord record =
                        CacheFixtures.namedMember(
                                ToolName.PERCOLATOR,
                                "3.07.1",
                                archive,
                                "percolator",
                                binary,
                                "bin/percolator",
                                List.of());
                harness.fetcher().serve(record.url(), archive);
                return record;
            }
            default -> {
                byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", binary));
                ArtefactRecord record =
                        CacheFixtures.namedMember(
                                ToolName.PERCOLATOR,
                                "3.07.1",
                                archive,
                                "percolator",
                                binary,
                                "bin/percolator",
                                List.of());
                harness.fetcher().serve(record.url(), archive);
                return record;
            }
        }
    }

    @Test
    @DisplayName("an archive that tries to escape its destination is refused, and by name")
    void anEscapingArchiveIsRefused() throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes("#!/bin/sh\necho escape\n");
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("../escape", binary));
        ArtefactRecord record =
                CacheFixtures.wholeArchive(
                        ToolName.PERCOLATOR, "3.07.1", archive, "bin/percolator");
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusingToBeCalled(),
                        HostOperatingSystem.LINUX);
        harness.fetcher().serve(record.url(), archive);

        ExtractionRejectedException rejected =
                assertThrows(ExtractionRejectedException.class, () -> harness.install(record));

        assertTrue(
                rejected.getMessage().contains("../escape"),
                () ->
                        "R-SEC-05's own diagnostic reaches the caller unchanged: "
                                + rejected.getMessage());
        assertFalse(
                Files.exists(temporary.resolve("cache").resolve("escape")),
                "and nothing was written outside the staging destination");
    }

    @Test
    @DisplayName("a cancelled install is cancelled, not failed, and leaves the cache alone")
    void aCancelledInstallIsNotAFailure() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusingToBeCalled(),
                        HostOperatingSystem.LINUX);
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        AtomicBoolean cancelled = new AtomicBoolean();
        harness.fetcher().before(() -> cancelled.set(true));
        Snapshot before = harness.toolsSnapshot();

        InstallCancelledException stopped =
                assertThrows(
                        InstallCancelledException.class,
                        () ->
                                harness.installer()
                                        .install(record, harness.listener(), cancelled::get));

        assertSame(
                InstallStep.VERIFY_SHA256,
                stopped.nextStep(),
                "it stopped at the next step boundary after the cancellation was seen");
        assertEquals(
                InstallPhase.CANCELLED,
                harness.listener().phases().get(harness.listener().phases().size() - 1),
                "a user who cancelled has not encountered an error");
        assertEquals(1, harness.listener().terminalReports().size());
        assertEquals(before, harness.toolsSnapshot());
        assertEquals(List.of(), harness.stagingDirectories(record));
    }

    @Test
    @DisplayName("a source that hands over the wrong bytes is refused by the installer's own check")
    void aSourceThatLiesIsRefusedAtStepTwo() throws IOException, InterruptedException {
        byte[] declared = CacheFixtures.bytes("#!/bin/sh\necho what the manifest pins\n");
        byte[] handedOver = CacheFixtures.bytes("#!/bin/sh\necho what the source produced!!\n");
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", declared, "comet.linux.exe", List.of());
        ToolCache cache =
                new ToolCache(
                        temporary.resolve("cache"),
                        new org.cometgui.provenance.hashing.StreamingHashService());
        RecordingProbe probe = RecordingProbe.refusingToBeCalled();
        /*
         * A SOURCE THAT LIES.  VerifiedDownloader cannot produce this, which is the point: step 2
         * is the installer's own R-SEC-02 boundary, and a boundary with no way to go red is a check
         * that has never been shown to work. The interface exists so that this test can exist.
         */
        VerifiedArtefactSource lying =
                (source, destination, expected, expectedSizeBytes, listener, cancellation) -> {
                    Files.createDirectories(
                            Objects.requireNonNull(destination.getParent(), "parent"));
                    Files.write(destination, handedOver);
                    return new org.cometgui.install.verify.VerifiedArtefact(
                            destination,
                            expected,
                            List.of(
                                    new org.cometgui.install.download.DownloadReport(
                                            source,
                                            destination,
                                            200,
                                            false,
                                            0L,
                                            handedOver.length,
                                            handedOver.length,
                                            handedOver.length)));
                };
        ArtefactInstaller installer =
                new ArtefactInstaller(
                        cache,
                        lying,
                        new org.cometgui.install.archive.ArtefactExtractor(),
                        new PlatformFixups(HostOperatingSystem.LINUX),
                        probe,
                        new org.cometgui.provenance.hashing.StreamingHashService(),
                        java.time.Clock.systemUTC());

        InstallRejectedException rejected =
                assertThrows(
                        InstallRejectedException.class,
                        () ->
                                installer.install(
                                        record,
                                        new RecordingInstallListener(),
                                        DownloadCancellation.never()));

        assertSame(InstallFailure.CHECKSUM_MISMATCH, rejected.failure());
        assertSame(InstallStep.VERIFY_SHA256, rejected.step());
        assertTrue(
                rejected.getMessage().contains(String.valueOf(handedOver.length))
                        && rejected.getMessage().contains(String.valueOf(declared.length)),
                () ->
                        "the diagnostic names the length that arrived and the length the manifest"
                                + " pins: "
                                + rejected.getMessage());
        assertEquals(0, probe.callCount());
    }

    @Test
    @DisplayName(
            "a final move the file system refuses is reported as contention, and never retried")
    void aRefusedFinalMoveIsReportedAsCacheContention() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        Path parent =
                Objects.requireNonNull(
                        harness.directoryOf(record).getParent(), "the tool directory has a parent");
        Files.createDirectories(parent);
        Set<PosixFilePermission> writable = Files.getPosixFilePermissions(parent);
        Files.setPosixFilePermissions(
                parent,
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        try {
            InstallRejectedException rejected =
                    assertThrows(InstallRejectedException.class, () -> harness.install(record));

            assertSame(InstallFailure.CACHE_CONTENDED, rejected.failure());
            assertSame(InstallStep.MOVE_ATOMICALLY_INTO_CACHE, rejected.step());
            assertTrue(
                    rejected.getMessage().contains(harness.directoryOf(record).toString()),
                    () -> "the diagnostic names the directory: " + rejected.getMessage());
            assertTrue(
                    rejected.getMessage().contains("virus scanner")
                            && rejected.getMessage().contains("does not retry"),
                    () ->
                            "and says what causes it and what this installer deliberately does not"
                                    + " do about it: "
                                    + rejected.getMessage());
            assertEquals(
                    1,
                    harness.probe().callCount(),
                    "the probe had already run: the refusal is the move, not the artefact");
        } finally {
            Files.setPosixFilePermissions(parent, writable);
        }
        assertEquals(List.of(), harness.stagingDirectories(record), "and staging was discarded");
    }

    private static byte[] corrupt(byte[] bytes) {
        byte[] damaged = bytes.clone();
        damaged[damaged.length / 2] ^= 0x5a;
        return damaged;
    }
}
