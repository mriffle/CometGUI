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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ArtefactInstaller}'s failure listener: told the step and the exception of an install that
 * fails, exactly once, before the terminal {@code FAILED} report -- and never for an install that
 * finished or was cancelled.
 *
 * <p>Every failure here is a real one, raised by the real pipeline over synthetic artefacts: a
 * transfer with nothing at the URL, an archive that is not a whole zip, a member whose bytes are
 * not the pinned bytes, a probe that refuses the build, a lock that cannot be taken, and a staging
 * directory that cannot be released after the last step.
 */
class InstallFailureListenerTest {

    private static final byte[] EXECUTABLE = CacheFixtures.bytes("#!/bin/sh\necho percolator\n");

    @TempDir private Path temporary;

    /** One call of the failure listener, with what the progress listener had seen by then. */
    private record Told(
            Optional<InstallStep> step, Throwable failure, List<InstallPhase> reportedBefore) {}

    /** Records each call, and the progress reports that preceded it. */
    private static final class Recorder implements InstallFailureListener {

        private final RecordingInstallListener progress;
        private final List<Told> told = new ArrayList<>();

        Recorder(RecordingInstallListener progress) {
            this.progress = progress;
        }

        @Override
        public void onInstallFailed(Optional<InstallStep> step, Throwable failure) {
            told.add(new Told(step, failure, progress.phases()));
        }

        List<Told> told() {
            return List.copyOf(told);
        }
    }

    private static ArtefactRecord percolatorZip(byte[] archive, byte[] pinnedMember) {
        return CacheFixtures.namedMember(
                ToolName.PERCOLATOR,
                "3.07.1",
                archive,
                "percolator",
                pinnedMember,
                "bin/percolator",
                List.of());
    }

    private static Throwable install(InstallHarness harness, ArtefactRecord record, Recorder told) {
        return assertThrows(
                Throwable.class,
                () ->
                        harness.installer()
                                .install(
                                        record,
                                        harness.listener(),
                                        DownloadCancellation.never(),
                                        told));
    }

    /**
     * The shape every failure shares: one call, the step, the very exception that was thrown, and
     * no terminal report yet when it was made -- then exactly one, FAILED, last.
     */
    private static void toldOnceBeforeFailed(
            InstallHarness harness,
            Recorder recorder,
            Throwable thrown,
            Optional<InstallStep> step) {
        List<Told> told = recorder.told();
        List<InstallPhase> phases = harness.listener().phases();
        assertAll(
                () -> assertEquals(1, told.size(), "told exactly once: " + told),
                () -> assertEquals(step, told.get(0).step(), "the step that was running"),
                () -> assertSame(thrown, told.get(0).failure(), "the exception, unchanged"),
                () ->
                        assertEquals(
                                phases.subList(0, phases.size() - 1),
                                told.get(0).reportedBefore(),
                                "told before the terminal report and after every other one"),
                () ->
                        assertEquals(
                                InstallPhase.FAILED,
                                phases.get(phases.size() - 1),
                                "and the terminal report, last, is FAILED"));
    }

    @Test
    @DisplayName("nothing at the URL: step 1, the transfer's own exception")
    void step1() {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        ArtefactRecord record =
                percolatorZip(
                        CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE)),
                        EXECUTABLE);
        Recorder recorder = new Recorder(harness.listener());

        Throwable thrown = install(harness, record, recorder);

        assertAll(
                () -> assertEquals("nothing is served at " + record.url(), thrown.getMessage()),
                () ->
                        toldOnceBeforeFailed(
                                harness,
                                recorder,
                                thrown,
                                Optional.of(InstallStep.DOWNLOAD_TO_TEMPORARY_FILE)));
    }

    @Test
    @DisplayName("an archive cut short: step 3, the extractor's own exception")
    void step3() {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        byte[] whole = CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE));
        byte[] cut = java.util.Arrays.copyOf(whole, whole.length / 2);
        ArtefactRecord record = percolatorZip(cut, EXECUTABLE);
        harness.serving(record, cut);
        Recorder recorder = new Recorder(harness.listener());

        Throwable thrown = install(harness, record, recorder);

        toldOnceBeforeFailed(
                harness, recorder, thrown, Optional.of(InstallStep.EXTRACT_WITH_GUARDS));
    }

    @Test
    @DisplayName("a member that is not the pinned bytes: step 4, the layout check's rejection")
    void step4() {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        byte[] other = CacheFixtures.bytes("#!/bin/sh\necho somethingelse\n");
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", other));
        ArtefactRecord record = percolatorZip(archive, EXECUTABLE);
        harness.serving(record, archive);
        Recorder recorder = new Recorder(harness.listener());

        Throwable thrown = install(harness, record, recorder);

        assertAll(
                () ->
                        assertEquals(
                                InstallFailure.MEMBER_DIGEST_MISMATCH,
                                ((InstallRejectedException) thrown).failure()),
                () ->
                        toldOnceBeforeFailed(
                                harness,
                                recorder,
                                thrown,
                                Optional.of(InstallStep.VERIFY_EXPECTED_LAYOUT)));
    }

    @Test
    @DisplayName("a probe that refuses the build: step 6, the probe's rejection")
    void step6() {
        InstallHarness harness =
                new InstallHarness(
                        temporary.resolve("cache"),
                        RecordingProbe.refusing("percolator exited with status 3"),
                        HostOperatingSystem.LINUX);
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE));
        ArtefactRecord record = percolatorZip(archive, EXECUTABLE);
        harness.serving(record, archive);
        Recorder recorder = new Recorder(harness.listener());

        Throwable thrown = install(harness, record, recorder);

        assertAll(
                () ->
                        assertEquals(
                                InstallFailure.PROBE_FAILED,
                                ((InstallRejectedException) thrown).failure()),
                () ->
                        toldOnceBeforeFailed(
                                harness, recorder, thrown, Optional.of(InstallStep.PROBE)));
    }

    @Test
    @DisplayName("an Error inside a step is a failure too, told with the step")
    void anErrorIsToldToo() {
        AssertionError error = new AssertionError("the probe died");
        ToolProbe dying =
                (record, staged) -> {
                    throw error;
                };
        InstallHarness harness = harnessWith(dying);
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE));
        ArtefactRecord record = percolatorZip(archive, EXECUTABLE);
        harness.serving(record, archive);
        Recorder recorder = new Recorder(harness.listener());

        Throwable thrown = install(harness, record, recorder);

        assertAll(
                () -> assertSame(error, thrown, "rethrown unchanged"),
                () ->
                        toldOnceBeforeFailed(
                                harness, recorder, thrown, Optional.of(InstallStep.PROBE)));
    }

    @Test
    @DisplayName("a lock that cannot be taken: no step, the lock's own exception")
    void beforeStep1() throws IOException {
        Path root = temporary.resolve("cache");
        InstallHarness harness = InstallHarness.at(root);
        Files.writeString(
                Files.createDirectories(root.resolve("cache")).resolve("locks"),
                "a file, not a directory");
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE));
        ArtefactRecord record = percolatorZip(archive, EXECUTABLE);
        harness.serving(record, archive);
        Recorder recorder = new Recorder(harness.listener());

        Throwable thrown = install(harness, record, recorder);

        toldOnceBeforeFailed(harness, recorder, thrown, Optional.empty());
    }

    @Test
    @DisplayName(
            "a staging directory that cannot be released after step 8: no step, the release's own"
                    + " exception")
    void afterTheLastStep() throws IOException {
        List<Path> trapped = new ArrayList<>();
        ToolProbe trapping =
                (record, staged) -> {
                    Path trap =
                            Files.createDirectories(
                                    Objects.requireNonNull(staged.getParent(), "staging")
                                            .resolve("trap"));
                    Files.writeString(trap.resolve("held"), "cannot be deleted");
                    Files.setPosixFilePermissions(
                            trap,
                            EnumSet.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_EXECUTE));
                    trapped.add(trap);
                    return Set.of(ToolCapability.XML_OUTPUT);
                };
        InstallHarness harness = harnessWith(trapping);
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE));
        ArtefactRecord record = percolatorZip(archive, EXECUTABLE);
        harness.serving(record, archive);
        Recorder recorder = new Recorder(harness.listener());
        try {
            Throwable thrown = install(harness, record, recorder);

            assertAll(
                    () -> assertEquals(1, trapped.size(), "the probe ran, so every step began"),
                    () -> toldOnceBeforeFailed(harness, recorder, thrown, Optional.empty()));
        } finally {
            for (Path trap : trapped) {
                Files.setPosixFilePermissions(
                        trap,
                        EnumSet.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE));
            }
        }
    }

    @Test
    @DisplayName("an install that finished is not a failure, and nothing is told")
    void notToldOnSuccess() throws IOException {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE));
        ArtefactRecord record = percolatorZip(archive, EXECUTABLE);
        harness.serving(record, archive);
        Recorder recorder = new Recorder(harness.listener());

        harness.installer()
                .install(record, harness.listener(), DownloadCancellation.never(), recorder);

        assertAll(
                () -> assertEquals(List.of(), recorder.told()),
                () ->
                        assertEquals(
                                InstallPhase.DONE,
                                harness.listener().terminalReports().get(0).phase()));
    }

    @Test
    @DisplayName("an install the user cancelled is not a failure, and nothing is told")
    void notToldOnCancel() {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE));
        ArtefactRecord record = percolatorZip(archive, EXECUTABLE);
        harness.serving(record, archive);
        Recorder recorder = new Recorder(harness.listener());

        assertThrows(
                InstallCancelledException.class,
                () ->
                        harness.installer()
                                .install(record, harness.listener(), () -> true, recorder));

        assertAll(
                () -> assertEquals(List.of(), recorder.told()),
                () -> assertEquals(List.of(InstallPhase.CANCELLED), harness.listener().phases()));
    }

    @Test
    @DisplayName("the three-argument install tells nobody and still fails the same way")
    void theShortFormStillFails() {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE));
        ArtefactRecord record = percolatorZip(archive, EXECUTABLE);

        IOException thrown = assertThrows(IOException.class, () -> harness.install(record));

        assertAll(
                () -> assertEquals("nothing is served at " + record.url(), thrown.getMessage()),
                () ->
                        assertEquals(
                                List.of(InstallPhase.DOWNLOADING, InstallPhase.FAILED),
                                harness.listener().phases()));
    }

    @Test
    @DisplayName("a null failure listener is refused by name")
    void nullRefused() {
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        byte[] archive = CacheFixtures.zip(CacheFixtures.entry("percolator", EXECUTABLE));
        ArtefactRecord record = percolatorZip(archive, EXECUTABLE);
        InstallFailureListener absent = Nulls.of(InstallFailureListener.class);

        assertEquals(
                "failures",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        harness.installer()
                                                .install(
                                                        record,
                                                        harness.listener(),
                                                        DownloadCancellation.never(),
                                                        absent))
                        .getMessage());
    }

    private InstallHarness harnessWith(ToolProbe probe) {
        return InstallHarness.probingWith(temporary.resolve("cache"), probe);
    }
}
