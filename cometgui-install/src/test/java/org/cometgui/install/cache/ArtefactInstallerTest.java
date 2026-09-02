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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.registry.ArchiveMember;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The eight steps, end to end, over the artefact shapes the manifest actually holds.
 *
 * <p>Everything but the transport and the probe is production code: the real {@code
 * VerifiedDownloader}, the real {@code ArtefactExtractor} with its {@code R-SEC-05} guards, the
 * real {@code StreamingHashService} and the real {@link ToolCache}. The cache root is a temporary
 * directory, which is the whole reason {@link ToolCache} takes one.
 */
class ArtefactInstallerTest {

    @TempDir private Path temporary;

    @Test
    @DisplayName("a named-member archive and its companion install, and the marker records both")
    void installsANamedMemberArtefactWithItsCompanion() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);

        Installation installation = harness.install(record);

        Path directory = harness.directoryOf(record);
        assertEquals(
                temporary
                        .resolve("cache")
                        .resolve("tools")
                        .resolve("percolator")
                        .resolve("3.7.1")
                        .resolve("linux-x86-64"),
                directory,
                "the cache layout is <root>/tools/<tool>/<normalised version>/<platform>");
        assertFalse(installation.alreadyInstalled(), "this call did the work");
        assertTrue(
                Files.isRegularFile(directory.resolve(CacheFixtures.INSTALLED_BINARY)),
                "the archive's named member is installed where the manifest says");
        assertTrue(
                Files.isRegularFile(directory.resolve(CacheFixtures.INSTALLED_SCHEMA)),
                "the companion's member is installed where the manifest says");

        InstallationMarker marker = installation.marker();
        assertEquals(1, marker.schemaVersion());
        assertEquals(ToolName.PERCOLATOR, marker.tool());
        assertEquals("3.07.1", marker.version().text(), "the marker keeps upstream's spelling");
        assertEquals("linux-x86-64", marker.platform().id());
        assertEquals("rel-t", marker.releaseTag());
        assertEquals(CacheFixtures.INSTALLED_BINARY, marker.executablePath());
        assertEquals(InstallHarness.INSTALLED_AT_TEXT, marker.installedAtUtc());
        assertEquals(
                2, marker.payloadEntryCount(), "the binary and the schema, and not the marker");
        assertEquals(
                List.of(CacheFixtures.INSTALLED_BINARY, CacheFixtures.INSTALLED_SCHEMA),
                marker.files().stream().map(RecordedFile::path).toList());
        assertEquals(
                CacheFixtures.sha256Of(directory.resolve(CacheFixtures.INSTALLED_BINARY)),
                marker.recordFor(CacheFixtures.INSTALLED_BINARY).orElseThrow().hashes().sha256(),
                "the marker records the digest of the file that is actually there");
        assertEquals(
                record.member().orElseThrow().hashes().sha256(),
                marker.recordFor(CacheFixtures.INSTALLED_BINARY).orElseThrow().hashes().sha256(),
                "and that digest is the one the manifest pinned for the member");

        InstallationCheck check = harness.verify(record);
        assertEquals(InstallationState.INSTALLED, check.state(), check::detail);
        assertTrue(check.detail().contains("2 recorded file(s) verified"), check::detail);
    }

    @Test
    @DisplayName("the pipeline runs every declared step, once, in the declared order")
    void everyStepRunsExactlyOnceInOrder() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);

        try (InstallPipeline pipeline =
                harness.installer()
                        .begin(record, harness.listener(), DownloadCancellation.never())) {
            for (InstallStep expected : InstallStep.values()) {
                assertEquals(
                        expected,
                        pipeline.nextStep(),
                        "the pipeline must announce the step it is about to run");
                assertEquals(
                        expected,
                        pipeline.runNextStep(),
                        "and return the step it ran, so a caller can log or report it");
            }
            assertEquals(
                    Arrays.asList(InstallStep.values()),
                    pipeline.executedSteps(),
                    "a step that is declared and never performed is a hole in R-TOOL-04's"
                            + " guarantee, not a missing feature");
            assertFalse(pipeline.hasNextStep());
        }
    }

    @Test
    @DisplayName("a bare executable installs, and the download is the installed file")
    void installsABareExecutable() throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes("#!/bin/sh\necho comet\n");
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.linux.exe", List.of());
        InstallHarness harness =
                InstallHarness.at(temporary.resolve("cache")).serving(record, binary);

        Installation installation = harness.install(record);

        assertEquals(
                CacheFixtures.hashesOf(binary).sha256(),
                CacheFixtures.sha256Of(installation.executable()),
                "the installed file is byte for byte the artefact that was pinned");
        assertTrue(harness.verify(record).installed());
        assertEquals(1, installation.marker().payloadEntryCount());
    }

    @Test
    @DisplayName("a JAR installs and is deliberately not made executable")
    void installsAJarAndLeavesItNotExecutable() throws IOException, InterruptedException {
        byte[] jar =
                CacheFixtures.zip(
                        CacheFixtures.entry(
                                "META-INF/MANIFEST.MF",
                                CacheFixtures.bytes("Manifest-Version: 1.0\n")));
        ArtefactRecord record = CacheFixtures.jar(ToolName.PDV, "2.7.0", jar, "pdv.jar");
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache")).serving(record, jar);

        Installation installation = harness.install(record);

        assertFalse(
                Files.isExecutable(installation.executable()),
                "a JAR is launched by the bundled runtime, so R-PLAT-05 does not apply to it and"
                        + " setting the bit would be a permission nobody asked for");
        assertTrue(harness.verify(record).installed());
    }

    @Test
    @DisplayName("a whole archive unpacks and the marker counts every file it placed")
    void installsAWholeArchiveAndCountsItsEntries() throws IOException, InterruptedException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("bin/pdv", CacheFixtures.bytes("#!/bin/sh\necho pdv\n"));
        entries.put("lib/one.jar", CacheFixtures.bytes("one"));
        entries.put("lib/two.jar", CacheFixtures.bytes("two"));
        byte[] archive = CacheFixtures.zip(entries);
        ArtefactRecord record =
                CacheFixtures.wholeArchive(ToolName.PDV, "2.7.0", archive, "bin/pdv");
        InstallHarness harness =
                InstallHarness.at(temporary.resolve("cache")).serving(record, archive);

        Installation installation = harness.install(record);

        assertEquals(
                3,
                installation.marker().payloadEntryCount(),
                "a whole-archive install pins a digest for no single entry, so the count is what"
                        + " notices that files have gone");
        assertEquals(
                List.of("bin/pdv"),
                installation.marker().files().stream().map(RecordedFile::path).toList(),
                "only the paths the manifest names carry a recorded digest");
        assertTrue(harness.verify(record).installed());
    }

    @Test
    @DisplayName("installing a second time does no work and downloads nothing")
    void theSecondInstallOfACompleteEntryDoesNoWork() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);

        Installation first = harness.install(record);
        int fetchesAfterFirst = harness.fetcher().requested().size();
        Installation second = harness.install(record);

        assertFalse(first.alreadyInstalled());
        assertTrue(second.alreadyInstalled(), "R-TOOL-05's idempotence half");
        assertEquals(
                fetchesAfterFirst,
                harness.fetcher().requested().size(),
                "the second install must not fetch a byte");
        assertEquals(2, fetchesAfterFirst, "the artefact and its one companion");
        assertEquals(
                first.marker().installedAtUtc(),
                second.marker().installedAtUtc(),
                "the second install returns the marker the first wrote, not a new one");
    }

    @Test
    @DisplayName("a directory with no marker is discarded and rebuilt")
    void anEntryWithNoMarkerIsRebuilt() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        harness.install(record);
        Files.delete(harness.markerOf(record));
        assertEquals(InstallationState.NO_MARKER, harness.verify(record).state());

        Installation rebuilt = harness.install(record);

        assertFalse(rebuilt.alreadyInstalled(), "an entry with no marker is not an install");
        assertTrue(harness.verify(record).installed());
        assertEquals(4, harness.fetcher().requested().size(), "it fetched everything again");
    }

    @Test
    @DisplayName("an entry whose recorded checksum stopped matching is discarded and rebuilt")
    void anEntryWhoseChecksumStoppedMatchingIsRebuilt() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        harness.install(record);
        Path binary = harness.directoryOf(record).resolve(CacheFixtures.INSTALLED_BINARY);
        Files.writeString(binary, "#!/bin/sh\necho something else entirely\n");

        assertEquals(
                InstallationState.CHECKSUM_MISMATCH,
                harness.verify(record).state(),
                "R-TOOL-04: a marker whose recorded digest no longer matches the file makes the"
                        + " entry NOT installed");
        Installation rebuilt = harness.install(record);
        assertFalse(rebuilt.alreadyInstalled());
        assertTrue(harness.verify(record).installed());
        assertEquals(
                record.member().orElseThrow().hashes().sha256(),
                CacheFixtures.sha256Of(binary),
                "the rebuilt entry holds the pinned bytes again");
    }

    @Test
    @DisplayName("the installed executable is executable, and actually runs on this platform")
    void theInstalledExecutableActuallyRuns() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);

        Installation installation = harness.install(record);

        assertTrue(
                Files.isExecutable(installation.executable()),
                () -> installation.executable() + " must carry the R-PLAT-05 executable bit");
        ChildProcesses.Result run =
                ChildProcesses.run(
                        List.of(installation.executable().toString()),
                        temporary,
                        Duration.ofSeconds(30));
        assertEquals(0, run.exitCode(), run::describe);
        assertEquals(
                List.of("cometgui-installed-and-runnable"),
                run.standardOutput(),
                () ->
                        "the installed file was launched as a program and had to produce its own"
                                + " output: "
                                + run.describe());
    }

    @Test
    @DisplayName("step 5 is what makes the file executable: it is not executable after step 3")
    void theExecutableBitIsSetByTheFixupStepAndNotBefore()
            throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);

        try (InstallPipeline pipeline =
                harness.installer()
                        .begin(record, harness.listener(), DownloadCancellation.never())) {
            while (pipeline.nextStep() != InstallStep.APPLY_PLATFORM_FIXUPS) {
                pipeline.runNextStep();
            }
            Path staged =
                    pipeline.stagingDirectory()
                            .orElseThrow()
                            .resolve("payload")
                            .resolve(CacheFixtures.INSTALLED_BINARY);
            assertTrue(Files.isRegularFile(staged), "the member is extracted by step 3");
            assertFalse(
                    Files.isExecutable(staged),
                    "extraction does not preserve a mode, which is exactly why R-PLAT-05 exists");

            pipeline.runNextStep();

            assertTrue(Files.isExecutable(staged), "step 5 sets the bit");
            assertEquals(
                    List.of(CacheFixtures.INSTALLED_BINARY),
                    pipeline.fixupReport().madeExecutable(),
                    "and reports the file it changed, so the claim is checkable");
            assertEquals(
                    List.of(),
                    pipeline.fixupReport().quarantineCleared(),
                    "the quarantine step is macOS's and this host is not macOS");
        }
    }

    @Test
    @DisplayName("exactly one terminal phase is reported, and it is the last report")
    void exactlyOneTerminalPhaseIsReportedLast() throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);

        harness.install(record);

        List<InstallPhase> phases = harness.listener().phases();
        assertEquals(
                1,
                harness.listener().terminalReports().size(),
                () -> "InstallProgressListener promises exactly one terminal report: " + phases);
        assertEquals(
                InstallPhase.DONE,
                phases.get(phases.size() - 1),
                () -> "and that it is the last one: " + phases);
        assertTrue(
                phases.contains(InstallPhase.DOWNLOADING)
                        && phases.contains(InstallPhase.VERIFYING)
                        && phases.contains(InstallPhase.EXTRACTING)
                        && phases.contains(InstallPhase.PROBING)
                        && phases.contains(InstallPhase.INSTALLING),
                () -> "every phase a user sees must be reported: " + phases);
    }

    @Test
    @DisplayName("progress carries the byte counts, not only the phase")
    void progressCarriesTheByteCounts() throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes("a binary of a length worth reporting");
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.linux.exe", List.of());
        InstallHarness harness =
                InstallHarness.at(temporary.resolve("cache")).serving(record, binary);

        harness.install(record);

        List<InstallProgress> downloading =
                harness.listener().reports().stream()
                        .filter(report -> report.phase() == InstallPhase.DOWNLOADING)
                        .toList();
        assertFalse(downloading.isEmpty(), "the download must report something");
        InstallProgress last = downloading.get(downloading.size() - 1);
        assertEquals(
                binary.length,
                last.bytesTransferred(),
                "the last download report is the transfer's own last word, not a written 100%");
        assertEquals(binary.length, last.totalBytes());
        assertTrue(last.hasKnownTotal());
        assertEquals(
                binary.length,
                harness.listener()
                        .reports()
                        .get(harness.listener().reports().size() - 1)
                        .bytesTransferred(),
                "and the terminal report still says how much was fetched");
    }

    @Test
    @DisplayName("the marker records the MD5 as well, for provenance, and it is the file's own")
    void theMarkerRecordsTheMd5ForProvenance() throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes("a binary whose MD5 goes in the provenance record");
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.linux.exe", List.of());
        InstallHarness harness =
                InstallHarness.at(temporary.resolve("cache")).serving(record, binary);

        Installation installation = harness.install(record);

        RecordedFile recorded = installation.marker().recordFor("comet.linux.exe").orElseThrow();
        assertEquals(
                CacheFixtures.hashesOf(binary).md5(),
                recorded.hashes().md5(),
                "the Definition of Done requires MD5 and SHA-256 for every input and output");
        assertEquals(CacheFixtures.hashesOf(binary).sha256(), recorded.hashes().sha256());
        assertEquals(
                CacheFixtures.hashesOf(binary).md5(),
                installation.marker().artefactHashes().md5(),
                "and the download's own MD5 is recorded beside it");
    }

    @Test
    @DisplayName("two companions upstream named the same thing do not overwrite each other")
    void companionsNamedTheSameThingDoNotCollide() throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes("#!/bin/sh\necho comet\n");
        byte[] first = CacheFixtures.bytes("the first library, which upstream calls helper.dll");
        byte[] second = CacheFixtures.bytes("the second library, also called helper.dll!!");
        ArtefactCompanion one =
                new ArtefactCompanion(
                        "thermo-common-core-data",
                        ArtefactKind.BARE_EXECUTABLE,
                        URI.create("https://example.invalid/a/helper.dll"),
                        first.length,
                        CacheFixtures.hashesOf(first),
                        false,
                        Optional.empty(),
                        "one of the three Thermo libraries Comet needs beside it",
                        List.of(
                                new ArchiveMember(
                                        "one.dll",
                                        first.length,
                                        CacheFixtures.hashesOf(first),
                                        "one.dll")));
        ArtefactCompanion two =
                new ArtefactCompanion(
                        "thermo-common-core-raw-file-reader",
                        ArtefactKind.BARE_EXECUTABLE,
                        URI.create("https://example.invalid/b/helper.dll"),
                        second.length,
                        CacheFixtures.hashesOf(second),
                        false,
                        Optional.empty(),
                        "another of them, published under the same file name",
                        List.of(
                                new ArchiveMember(
                                        "two.dll",
                                        second.length,
                                        CacheFixtures.hashesOf(second),
                                        "two.dll")));
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.win64.exe", List.of(one, two));
        InstallHarness harness =
                InstallHarness.at(temporary.resolve("cache"))
                        .serving(record, binary, first, second);

        Installation installation = harness.install(record);

        assertEquals(
                CacheFixtures.hashesOf(first).sha256(),
                CacheFixtures.sha256Of(installation.directory().resolve("one.dll")),
                "two downloads whose URLs end in the same file name must not share a file on disk");
        assertEquals(
                CacheFixtures.hashesOf(second).sha256(),
                CacheFixtures.sha256Of(installation.directory().resolve("two.dll")));
        assertEquals(3, installation.marker().payloadEntryCount());
    }

    @Test
    @DisplayName("what the probe confirmed is recorded, and is bound to the executable's checksum")
    void probedCapabilitiesAreRecordedAndBoundToTheExecutable()
            throws IOException, InterruptedException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.confirming(
                                ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                        org.cometgui.domain.tools.HostOperatingSystem.LINUX);
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);

        Installation installation = harness.install(record);

        assertEquals(
                List.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                installation.capabilities(),
                "R-TOOL-07: what the probe confirmed is what is recorded");
        assertEquals(1, harness.probe().callCount(), "the probe ran once, on the staged directory");
        assertNotEquals(
                harness.directoryOf(record),
                harness.probe().calls().get(0),
                "and it ran BEFORE the move, so a build that fails it never becomes a cache entry");
        InstallationMarker readBack =
                InstallationMarker.parse(Files.readString(harness.markerOf(record)));
        assertEquals(
                installation.capabilities(),
                readBack.capabilities(),
                "the next start of the application reads them back rather than re-probing");
    }
}
