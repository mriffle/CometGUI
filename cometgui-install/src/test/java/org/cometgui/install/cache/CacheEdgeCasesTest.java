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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.download.DownloadReport;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.VerifiedArtefact;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The paths a happy install never takes, each driven to its own outcome.
 *
 * <p>Three of them are decisions rather than accidents -- what the installer does when the final
 * move is refused, when it is refused for a structural reason instead, and when the file system has
 * no POSIX permissions at all -- and a decision nobody can watch happen is one nobody has checked.
 */
class CacheEdgeCasesTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /**
     * How long another thread may take to pick up a lock that has been released.
     *
     * <p>Seconds, not tens of seconds, and that is deliberate: a defect that fails to release the
     * lock makes these tests <em>hang</em>, and a mutation-testing run scores a hang as a timeout
     * rather than as a kill. A short bound turns the same defect into a failing assertion.
     */
    private static final long RELEASE_DEADLINE_SECONDS = 2;

    @TempDir private Path temporary;

    @Test
    @DisplayName("a pipeline with a step nobody implemented refuses to exist, naming the step")
    void aStepWithNoActionIsRefused() {
        Set<InstallStep> incomplete =
                EnumSet.complementOf(
                        EnumSet.of(InstallStep.PROBE, InstallStep.RECORD_INSTALLATION_METADATA));

        IllegalStateException refused =
                assertThrows(
                        IllegalStateException.class,
                        () -> InstallPipeline.requireAnActionForEveryStep(incomplete));

        assertEquals(
                "every install step needs an action, and [PROBE, RECORD_INSTALLATION_METADATA] has"
                        + " none; adding a step to InstallStep means implementing it here",
                refused.getMessage());
        InstallPipeline.requireAnActionForEveryStep(EnumSet.allOf(InstallStep.class));
    }

    @Test
    @DisplayName("a move the file system cannot make atomic is re-thrown, never turned into a copy")
    void anImpossibleAtomicMoveIsRethrown() throws IOException {
        Path archive = temporary.resolve("payload.zip");
        try (FileSystem zip = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Path insideTheZip = zip.getPath("payload");
            Files.createDirectory(insideTheZip);

            AtomicMoveNotSupportedException notAtomic =
                    assertThrows(
                            AtomicMoveNotSupportedException.class,
                            () ->
                                    InstallPipeline.moveAtomically(
                                            insideTheZip,
                                            temporary.resolve("destination"),
                                            "percolator 3.07.1 linux-x86-64"));

            assertFalse(
                    Files.exists(temporary.resolve("destination")),
                    "nothing may be copied when the rename cannot be atomic: R-TOOL-04 rests on"
                            + " the move being one operation, and a copy is not");
            assertTrue(notAtomic.getMessage() != null, notAtomic::toString);
        }
    }

    @Test
    @DisplayName("a payload that has gone is a structural failure, not cache contention")
    void aMissingPayloadIsNotReportedAsContention() throws IOException {
        Path destination = temporary.resolve("tools").resolve("percolator");
        Files.createDirectories(Objects.requireNonNull(destination.getParent(), "parent"));

        assertThrows(
                NoSuchFileException.class,
                () ->
                        InstallPipeline.moveAtomically(
                                temporary.resolve("never-staged"),
                                destination,
                                "percolator 3.07.1 linux-x86-64"),
                "labelling this contention would send a reader looking for a virus scanner that is"
                        + " not there");
    }

    @Test
    @DisplayName("a refusal quotes the file system's own reason rather than only a guess")
    void aRefusalQuotesTheFileSystemsOwnReason() throws IOException {
        Path payload = Files.createDirectories(temporary.resolve("payload"));
        Files.writeString(payload.resolve("tool"), "the new install");
        Path destination = Files.createDirectories(temporary.resolve("destination"));
        Files.writeString(destination.resolve("leftover"), "something already there");

        InstallRejectedException rejected =
                assertThrows(
                        InstallRejectedException.class,
                        () ->
                                InstallPipeline.moveAtomically(
                                        payload, destination, "percolator 3.07.1 linux-x86-64"));

        assertSame(InstallFailure.CACHE_CONTENDED, rejected.failure());
        assertTrue(
                rejected.getMessage().contains("Directory not empty"),
                () ->
                        "a reader is told what the file system said, not only what this class"
                                + " guessed: "
                                + rejected.getMessage());
        assertTrue(
                rejected.getMessage().contains("does not retry"),
                () ->
                        "and that the installer deliberately does nothing about it: "
                                + rejected.getMessage());
        assertTrue(
                Files.isRegularFile(destination.resolve("leftover")),
                "and the destination is untouched");
    }

    @ParameterizedTest
    @CsvSource({
        "https://example.invalid/releases/download/t/tool.zip, artefact-tool.zip",
        "https://example.invalid/x, artefact-x",
        "https://example.invalid/a%20b/x;y.zip, artefact-x_y.zip",
        "https://example.invalid, artefact-download",
        "https://example.invalid/dir/, artefact-download"
    })
    @DisplayName("a download's file name comes from the URL and is never trusted to be one")
    void aDownloadNameIsDerivedAndSanitised(String url, String expected) {
        assertEquals(expected, InstallPipeline.downloadName("artefact", URI.create(url)));
    }

    @ParameterizedTest
    @CsvSource({
        "percolator-noxml.zip, percolator-noxml.zip",
        "..,download",
        ".,download",
        "'../../etc/passwd', .._.._etc_passwd",
        "'a b', a_b"
    })
    @DisplayName("anything outside the safe set becomes an underscore, and a bare dot is refused")
    void aFileNameIsSanitisedRatherThanTrusted(String raw, String expected) {
        assertEquals(expected, InstallPipeline.safe(raw));
    }

    @Test
    @DisplayName("every class of character is either kept or replaced, and the boundaries are hit")
    void everyCharacterClassIsDecided() {
        assertEquals(
                "azAZ09.-_____",
                InstallPipeline.safe("azAZ09.-_:{^!"),
                "letters of both cases, digits, dot, hyphen and underscore survive; a character"
                        + " below '0', between '9' and 'A', between 'Z' and 'a' and above 'z' does"
                        + " not");
        assertEquals("download", InstallPipeline.safe(""));
        assertEquals("_", InstallPipeline.safe("/"));
    }

    @Test
    @DisplayName("a file system with no POSIX permissions is not a failure: there is no bit to set")
    void aFileSystemWithoutPosixPermissionsChangesNothing() throws IOException {
        byte[] binary = CacheFixtures.bytes("a tool");
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.exe", List.of());
        Path archive = temporary.resolve("windows-shaped.zip");
        try (FileSystem zip = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Files.write(zip.getPath("comet.exe"), binary);

            FixupReport report =
                    new PlatformFixups(HostOperatingSystem.MACOS).apply(zip.getPath("/"), record);

            assertTrue(
                    report.changedNothing(),
                    "a file system that publishes neither POSIX permissions nor user-defined"
                            + " attributes -- which is the shape Windows has -- leaves both"
                            + " fix-ups with nothing to do, and that is not an error");
        }
    }

    @Test
    @DisplayName("a fixup report that only cleared a quarantine has still changed something")
    void aReportThatOnlyClearedQuarantineHasChangedSomething() {
        assertFalse(new FixupReport(List.of(), List.of("bin/tool")).changedNothing());
    }

    @Test
    @DisplayName("a directory that cannot be read is reported rather than silently half-deleted")
    void anUnreadableDirectoryStopsTheDiscard() throws IOException {
        ToolCache cache =
                new ToolCache(
                        temporary.resolve("root"),
                        path -> {
                            throw new IOException("no hashing in this test");
                        });
        Path doomed = Files.createDirectories(cache.root().resolve("tools").resolve("doomed"));
        Path unreadable = Files.createDirectories(doomed.resolve("unreadable"));
        Files.writeString(unreadable.resolve("inside"), "a file nobody may list");
        Files.setPosixFilePermissions(unreadable, EnumSet.noneOf(PosixFilePermission.class));
        try {
            assertThrows(
                    IOException.class,
                    () -> cache.discard(doomed),
                    "a recursive delete that could not finish must say so, not report success");
        } finally {
            Files.setPosixFilePermissions(
                    unreadable,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        }
    }

    @ParameterizedTest
    @CsvSource({"-1", "0"})
    @DisplayName("a marker whose artefact has no positive size is refused, naming the field")
    void anArtefactSizeThatIsNotPositiveIsRefused(long size) {
        assertEquals(
                "artefactSizeBytes must be positive, but was: " + size,
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new InstallationMarker(
                                                1,
                                                ToolName.PERCOLATOR,
                                                ToolVersion.parse("3.07.1"),
                                                LINUX,
                                                "rel-t",
                                                URI.create("https://example.invalid/a.zip"),
                                                size,
                                                hashes(),
                                                "2026-09-02T11:22:33.444Z",
                                                "bin/percolator",
                                                1,
                                                List.of(ToolCapability.XML_OUTPUT),
                                                List.of(
                                                        new RecordedFile(
                                                                "bin/percolator", 1, hashes()))))
                        .getMessage());
    }

    @ParameterizedTest
    @CsvSource({"-1", "0"})
    @DisplayName("a marker recording no installed file is refused: an install always places one")
    void anEntryCountThatIsNotPositiveIsRefused(int entryCount) {
        assertEquals(
                "payloadEntryCount must be positive, but was: " + entryCount,
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new InstallationMarker(
                                                1,
                                                ToolName.PERCOLATOR,
                                                ToolVersion.parse("3.07.1"),
                                                LINUX,
                                                "rel-t",
                                                URI.create("https://example.invalid/a.zip"),
                                                1,
                                                hashes(),
                                                "2026-09-02T11:22:33.444Z",
                                                "bin/percolator",
                                                entryCount,
                                                List.of(ToolCapability.XML_OUTPUT),
                                                List.of(
                                                        new RecordedFile(
                                                                "bin/percolator", 1, hashes()))))
                        .getMessage());
        assertEquals(
                1,
                new InstallationMarker(
                                1,
                                ToolName.PERCOLATOR,
                                ToolVersion.parse("3.07.1"),
                                LINUX,
                                "rel-t",
                                URI.create("https://example.invalid/a.zip"),
                                1,
                                hashes(),
                                "2026-09-02T11:22:33.444Z",
                                "bin/percolator",
                                1,
                                List.of(),
                                List.of(new RecordedFile("bin/percolator", 1, hashes())))
                        .payloadEntryCount(),
                "one artefact of one byte placing one file is the smallest legal install");
    }

    @Test
    @DisplayName("two names for one lock file are a known limit: the second is refused, not queued")
    void twoNamesForOneLockFileAreARecordedLimit() throws IOException, InterruptedException {
        Path locks = Files.createDirectories(temporary.resolve("locks"));
        Path alias = Files.createSymbolicLink(temporary.resolve("locks-alias"), locks);
        Path direct = locks.resolve("percolator.lock");
        Path aliased = alias.resolve("percolator.lock");

        try (InstallLock held = InstallLock.acquire(direct)) {
            assertThrows(
                    OverlappingFileLockException.class,
                    () -> InstallLock.acquire(aliased),
                    "the monitor is keyed by path text, so an alias gets its own monitor and only"
                            + " the file lock notices -- a limit, and one the class documents");
        }

        ArrayBlockingQueue<Object> outcome = new ArrayBlockingQueue<>(1);
        Thread other =
                new Thread(
                        () -> {
                            try (InstallLock lock = InstallLock.acquire(aliased)) {
                                outcome.add(lock.file());
                            } catch (IOException failed) {
                                outcome.add(failed);
                            }
                        },
                        "after-the-refusal");
        other.setDaemon(true);
        other.start();
        assertEquals(
                aliased.toAbsolutePath().normalize(),
                outcome.poll(RELEASE_DEADLINE_SECONDS, TimeUnit.SECONDS),
                "and the refused attempt released its monitor, or this thread would hang");
    }

    @Test
    @DisplayName("an install that cannot even take the lock still reports one terminal phase")
    void anInstallThatCannotTakeTheLockStillReportsFailure() throws IOException {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        ArtefactRecord record = sharedRecord();
        Files.createDirectories(
                harness.cache().lockFile(record.tool(), record.version(), record.platform()));

        assertThrows(IOException.class, () -> harness.install(record));

        assertEquals(
                List.of(InstallPhase.FAILED),
                harness.listener().phases(),
                "the listener is promised exactly one terminal report even when the install never"
                        + " started");
    }

    @Test
    @DisplayName("an install that fails after taking the lock releases it")
    void anInstallThatFailsAfterTakingTheLockReleasesIt() throws IOException, InterruptedException {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        ArtefactRecord record = sharedRecord();
        Path stagingParent = harness.cache().workingRoot().resolve("staging");
        Files.createDirectories(Objects.requireNonNull(stagingParent.getParent(), "parent"));
        Files.writeString(stagingParent, "a file where the staging directory should be");

        assertThrows(IOException.class, () -> harness.install(record));

        ArrayBlockingQueue<Object> outcome = new ArrayBlockingQueue<>(1);
        Path lockFile =
                harness.cache().lockFile(record.tool(), record.version(), record.platform());
        Thread other =
                new Thread(
                        () -> {
                            try (InstallLock lock = InstallLock.acquire(lockFile)) {
                                outcome.add(lock.file());
                            } catch (IOException failed) {
                                outcome.add(failed);
                            }
                        },
                        "after-the-failure");
        other.setDaemon(true);
        other.start();
        assertEquals(
                lockFile.toAbsolutePath().normalize(),
                outcome.poll(RELEASE_DEADLINE_SECONDS, TimeUnit.SECONDS),
                "a failed install that kept the lock would stop every later install of that"
                        + " artefact, for the life of the application");
    }

    @Test
    @DisplayName("bytes of the right length and the wrong digest are refused at step 2")
    void aSourceThatKeepsTheLengthAndChangesTheBytesIsRefused() throws IOException {
        byte[] declared = CacheFixtures.bytes("the binary the manifest pins");
        byte[] swapped = CacheFixtures.bytes("THE BINARY THE MANIFEST PINS");
        assertEquals(
                declared.length, swapped.length, "this test is about the digest, not the size");
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", declared, "comet.linux.exe", List.of());
        RecordingProbe probe = RecordingProbe.refusingToBeCalled();
        ArtefactInstaller installer =
                new ArtefactInstaller(
                        new ToolCache(
                                temporary.resolve("cache"),
                                new org.cometgui.provenance.hashing.StreamingHashService()),
                        (source, destination, expected, size, listener, cancellation) -> {
                            Files.createDirectories(
                                    Objects.requireNonNull(destination.getParent(), "parent"));
                            Files.write(destination, swapped);
                            return new VerifiedArtefact(
                                    destination,
                                    expected,
                                    List.of(
                                            new DownloadReport(
                                                    source,
                                                    destination,
                                                    200,
                                                    false,
                                                    0L,
                                                    swapped.length,
                                                    swapped.length,
                                                    swapped.length)));
                        },
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
                rejected.getMessage().contains("R-SEC-02")
                        && rejected.getMessage().contains(record.hashes().sha256()),
                () ->
                        "the diagnostic names the digest the manifest pins and the rule it is"
                                + " enforcing: "
                                + rejected.getMessage());
        assertEquals(0, probe.callCount());
    }

    @Test
    @DisplayName("a COMPANION whose bytes are not the pinned ones is refused at step 2 as well")
    void aLyingSourceIsRefusedForACompanionToo() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        byte[] artefact = Files.readAllBytes(fixture.resolve("artefact.zip"));
        byte[] wrongCompanion = CacheFixtures.bytes("not the package the manifest pins at all");
        RecordingProbe probe = RecordingProbe.refusingToBeCalled();
        StreamingHashService hashes = new StreamingHashService();
        /*
         * The artefact is served honestly and the companion is not, so the only check that can stop
         * this is step 2's loop over the companions.  A boundary asserted for the primary artefact
         * and not for its companions is a rule proved at one point on an axis it does not depend
         * on.
         */
        VerifiedArtefactSource lying =
                (source, destination, expected, size, listener, cancellation) -> {
                    Files.createDirectories(
                            Objects.requireNonNull(destination.getParent(), "parent"));
                    byte[] body = source.equals(record.url()) ? artefact : wrongCompanion;
                    Files.write(destination, body);
                    return new VerifiedArtefact(
                            destination,
                            expected,
                            List.of(
                                    new DownloadReport(
                                            source,
                                            destination,
                                            200,
                                            false,
                                            0L,
                                            body.length,
                                            body.length,
                                            body.length)));
                };
        ArtefactInstaller installer =
                new ArtefactInstaller(
                        new ToolCache(temporary.resolve("cache"), hashes),
                        lying,
                        new org.cometgui.install.archive.ArtefactExtractor(),
                        new PlatformFixups(HostOperatingSystem.LINUX),
                        probe,
                        hashes,
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
                rejected.getMessage().contains(record.companions().get(0).id()),
                () -> "the diagnostic names which download was wrong: " + rejected.getMessage());
        assertEquals(0, probe.callCount());
    }

    @Test
    @DisplayName("a finished install releases the lock, so the next one does not queue behind it")
    void aFinishedInstallReleasesTheLock() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        harness.install(record);

        Path lockFile =
                harness.cache().lockFile(record.tool(), record.version(), record.platform());
        ArrayBlockingQueue<Object> outcome = new ArrayBlockingQueue<>(1);
        Thread other =
                new Thread(
                        () -> {
                            try (InstallLock lock = InstallLock.acquire(lockFile)) {
                                outcome.add(lock.file());
                            } catch (IOException failed) {
                                outcome.add(failed);
                            }
                        },
                        "after-the-install");
        other.setDaemon(true);
        other.start();

        assertEquals(
                lockFile.toAbsolutePath().normalize(),
                outcome.poll(RELEASE_DEADLINE_SECONDS, TimeUnit.SECONDS),
                "an install that kept its lock would stop every later install of that artefact for"
                        + " the life of the application");
    }

    private ArtefactRecord sharedRecord() throws IOException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        return CacheFixtures.sharedRecord(fixture);
    }

    private static FileHashes hashes() {
        return new FileHashes(
                "9c86de1c45d2d93dae1ab43216b5864c",
                "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1");
    }
}
