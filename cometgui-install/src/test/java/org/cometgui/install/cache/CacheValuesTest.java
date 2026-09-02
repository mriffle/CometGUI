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
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The small types this package is made of: what each of them refuses, and what it says when it
 * does.
 *
 * <p>Each one is a value a diagnostic is built from, so the assertions are on whole messages
 * wherever a message is what the type produces. A guard can fire correctly while its message
 * misstates the value it rejected, and neither coverage nor a {@code startsWith} assertion notices.
 */
class CacheValuesTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final FileHashes HASHES =
            new FileHashes(
                    "9c86de1c45d2d93dae1ab43216b5864c",
                    "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1");

    @TempDir private Path temporary;

    @Test
    @DisplayName("a recorded file refuses a blank path and a negative size, naming the value")
    void recordedFileRefusesNonsense() {
        assertEquals(
                "a recorded file's path must not be blank, but was: \"  \"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new RecordedFile("  ", 1, HASHES))
                        .getMessage());
        assertEquals(
                "a recorded file's sizeBytes must not be negative, but \"bin/x\" was recorded"
                        + " as -3",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new RecordedFile("bin/x", -3, HASHES))
                        .getMessage());
        assertThrows(
                NullPointerException.class,
                () -> new RecordedFile(Nulls.of(String.class), 1, HASHES));
        assertThrows(
                NullPointerException.class,
                () -> new RecordedFile("bin/x", 1, Nulls.of(FileHashes.class)));
        assertEquals(0, new RecordedFile("bin/x", 0, HASHES).sizeBytes(), "an empty file is legal");
    }

    @ParameterizedTest
    @EnumSource(InstallationState.class)
    @DisplayName("exactly one installation state means the tool may be used")
    void exactlyOneStateMeansInstalled(InstallationState state) {
        assertEquals(
                state == InstallationState.INSTALLED,
                state.installed(),
                () ->
                        state
                                + " decides whether a scientist is offered a tool; only INSTALLED"
                                + " may");
    }

    @Test
    @DisplayName("an installation check must carry a marker exactly when it read one")
    void anInstallationCheckAgreesWithItsOwnState() {
        Path directory = temporary.resolve("entry");
        InstallationMarker marker = marker();

        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new InstallationCheck(
                                                InstallationState.CHECKSUM_MISMATCH,
                                                directory,
                                                "a digest changed",
                                                Optional.empty()))
                        .getMessage()
                        .contains("CHECKSUM_MISMATCH is only reached by reading the marker"));
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new InstallationCheck(
                                                InstallationState.NO_MARKER,
                                                directory,
                                                "no marker",
                                                Optional.of(marker)))
                        .getMessage()
                        .contains("NO_MARKER is reached before a marker has been read"));
        assertEquals(
                "an installation check says why, and this one's detail is blank",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new InstallationCheck(
                                                InstallationState.NOT_PRESENT,
                                                directory,
                                                " ",
                                                Optional.empty()))
                        .getMessage());
        InstallationCheck absent =
                new InstallationCheck(
                        InstallationState.NOT_PRESENT,
                        directory,
                        "nothing there",
                        Optional.empty());
        assertFalse(absent.installed());
        assertTrue(
                assertThrows(IllegalStateException.class, absent::requireMarker)
                        .getMessage()
                        .contains("NOT_PRESENT carries no marker: nothing there"));
    }

    @Test
    @DisplayName("an installation's executable must be inside its own directory")
    void anInstallationsExecutableMustBeInsideIt() {
        Path directory = temporary.resolve("tools").resolve("percolator");
        Path elsewhere = temporary.resolve("somewhere-else").resolve("percolator");

        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new Installation(directory, elsewhere, marker(), false))
                        .getMessage()
                        .contains("is not inside"));
        Installation installation =
                new Installation(directory, directory.resolve("bin/percolator"), marker(), true);
        assertTrue(installation.alreadyInstalled());
        assertEquals(List.of(ToolCapability.XML_OUTPUT), installation.capabilities());
        assertThrows(
                NullPointerException.class,
                () ->
                        new Installation(
                                directory, directory, Nulls.of(InstallationMarker.class), false));
    }

    @Test
    @DisplayName("a fixup report copies its lists and knows when it changed nothing")
    void aFixupReportIsImmutable() {
        List<String> mutable = new ArrayList<>(List.of("bin/tool"));
        FixupReport report = new FixupReport(mutable, List.of());

        mutable.add("something added afterwards");

        assertEquals(List.of("bin/tool"), report.madeExecutable());
        assertThrows(UnsupportedOperationException.class, () -> report.madeExecutable().add("no"));
        assertThrows(
                UnsupportedOperationException.class, () -> report.quarantineCleared().add("no"));
        assertFalse(report.changedNothing());
        assertTrue(new FixupReport(List.of(), List.of()).changedNothing());
        assertThrows(
                NullPointerException.class, () -> new FixupReport(Nulls.of(List.class), List.of()));
    }

    @Test
    @DisplayName("a rejection names the artefact, the step and the file, in one sentence")
    void aRejectionNamesEverything() {
        InstallRejectedException rejected =
                new InstallRejectedException(
                        InstallFailure.LAYOUT_INCOMPLETE,
                        InstallStep.VERIFY_EXPECTED_LAYOUT,
                        "percolator 3.07.1 linux-x86-64",
                        "bin/percolator",
                        "the manifest says \"bin/percolator\" is installed and the extraction did"
                                + " not produce it");

        assertEquals(
                "percolator 3.07.1 linux-x86-64 was not installed: the manifest says"
                        + " \"bin/percolator\" is installed and the extraction did not produce it"
                        + " (install step 4, VERIFY_EXPECTED_LAYOUT)",
                rejected.getMessage());
        assertSame(InstallFailure.LAYOUT_INCOMPLETE, rejected.failure());
        assertSame(InstallStep.VERIFY_EXPECTED_LAYOUT, rejected.step());
        assertEquals(Optional.of("bin/percolator"), rejected.path());
        assertEquals(
                Optional.of(temporary.resolve("bin/percolator")),
                rejected.resolvedAgainst(temporary));
        assertThrows(
                NullPointerException.class, () -> rejected.resolvedAgainst(Nulls.of(Path.class)));
    }

    @Test
    @DisplayName("a rejection about the artefact as a whole names no file")
    void aRejectionAboutTheWholeArtefactNamesNoFile() {
        InstallRejectedException rejected =
                new InstallRejectedException(
                        InstallFailure.CHECKSUM_MISMATCH,
                        InstallStep.VERIFY_SHA256,
                        "comet 2026.02.2 linux-x86-64",
                        null,
                        "the artefact hashes to something else",
                        new java.io.IOException("the cause"));

        assertTrue(rejected.path().isEmpty());
        assertTrue(rejected.resolvedAgainst(temporary).isEmpty());
        assertEquals("the cause", rejected.getCause().getMessage());
    }

    @Test
    @DisplayName("a cancellation says where it stopped and that nothing was written")
    void aCancellationSaysWhereItStopped() {
        InstallCancelledException stopped =
                new InstallCancelledException(
                        "pdv 2.7.0 linux-x86-64", InstallStep.EXTRACT_WITH_GUARDS);

        assertEquals(
                "the install of pdv 2.7.0 linux-x86-64 was cancelled before step 3,"
                        + " EXTRACT_WITH_GUARDS; nothing was written to the tool cache",
                stopped.getMessage());
        assertSame(InstallStep.EXTRACT_WITH_GUARDS, stopped.nextStep());
    }

    @Test
    @DisplayName("a pipeline that has not finished has no result, and says how far it got")
    void anUnfinishedPipelineHasNoResult() throws IOException, InterruptedException {
        ArtefactInstaller installer = installer();
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture());

        try (InstallPipeline pipeline =
                installer.begin(record, progress -> {}, DownloadCancellation.never())) {
            assertTrue(
                    assertThrows(IllegalStateException.class, pipeline::installation)
                            .getMessage()
                            .contains("has run 0 of 8 step(s) and has no result yet"));
            assertEquals(InstallStep.DOWNLOAD_TO_TEMPORARY_FILE, pipeline.nextStep());
            assertTrue(pipeline.hasNextStep());
            assertEquals(List.of(), pipeline.executedSteps());
            assertTrue(pipeline.toString().contains("0/8 step(s) run"), pipeline::toString);
            assertTrue(pipeline.stagingDirectory().isPresent());
            assertTrue(pipeline.fixupReport().changedNothing());
        }
    }

    @Test
    @DisplayName("a finished pipeline has no next step, and says so rather than running one")
    void aFinishedPipelineHasNoNextStep() throws IOException, InterruptedException {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache2"));
        Path fixture = fixture();
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);

        try (InstallPipeline pipeline =
                harness.installer()
                        .begin(record, harness.listener(), DownloadCancellation.never())) {
            pipeline.runToCompletion();
            assertFalse(pipeline.hasNextStep());
            assertTrue(
                    assertThrows(IllegalStateException.class, pipeline::nextStep)
                            .getMessage()
                            .contains("there is no next step for percolator 3.07.1 linux-x86-64"));
            assertThrows(IllegalStateException.class, pipeline::runNextStep);
            assertTrue(pipeline.toString().contains("8/8 step(s) run"), pipeline::toString);
        }
    }

    @Test
    @DisplayName("the installer rejects nulls and describes itself by its cache")
    void theInstallerRejectsNulls() throws IOException, InterruptedException {
        ArtefactInstaller installer = installer();
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture());

        assertThrows(
                NullPointerException.class,
                () ->
                        installer.install(
                                Nulls.of(ArtefactRecord.class), progress -> {}, () -> false));
        assertThrows(
                NullPointerException.class,
                () ->
                        installer.begin(
                                record,
                                Nulls.of(org.cometgui.domain.tools.InstallProgressListener.class),
                                () -> false));
        assertThrows(
                NullPointerException.class,
                () ->
                        installer.begin(
                                record, progress -> {}, Nulls.of(DownloadCancellation.class)));
        assertThrows(
                NullPointerException.class,
                () ->
                        new ArtefactInstaller(
                                Nulls.of(ToolCache.class), null, null, null, null, null, null));
        assertTrue(installer.toString().startsWith("ArtefactInstaller[ToolCache[root="));
        assertTrue(installer.toString().contains("fixups=PlatformFixups[host=linux]"));
        assertEquals(temporary.resolve("cache"), installer.cache().root());
    }

    private ArtefactInstaller installer() {
        StreamingHashService hashes = new StreamingHashService();
        return new ArtefactInstaller(
                new ToolCache(temporary.resolve("cache"), hashes),
                (source, destination, expected, size, listener, cancellation) -> {
                    throw new java.io.IOException("this test never downloads anything");
                },
                new ArtefactExtractor(),
                new PlatformFixups(HostOperatingSystem.LINUX),
                (record, staged) -> java.util.Set.of(),
                hashes,
                Clock.systemUTC());
    }

    private Path fixture() throws java.io.IOException {
        Path fixture = temporary.resolve("fixture");
        CacheFixtures.writeSharedFixture(fixture);
        return fixture;
    }

    private static InstallationMarker marker() {
        return new InstallationMarker(
                InstallationMarker.SCHEMA_VERSION,
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                LINUX,
                "rel-3-07-01",
                URI.create("https://example.invalid/a.zip"),
                946303,
                HASHES,
                "2026-09-02T11:22:33.444Z",
                "bin/percolator",
                1,
                List.of(ToolCapability.XML_OUTPUT),
                List.of(new RecordedFile("bin/percolator", 2538632, HASHES)));
    }
}
