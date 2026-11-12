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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.cache.ToolProbe;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A failed install's reason outlives the process that met it: closed and reopened, the application
 * still says why the last attempt failed.
 *
 * <p><strong>A restart here is a real one, as far as this module can make it.</strong> Nothing is
 * carried in memory from the first session to the second: each is its own {@link
 * ToolManagerHarness} -- a new {@link ManagedToolManager}, a new installer and a new tool cache
 * object -- and the only thing they share is the application-data directory on disk. The second
 * session is given a fetcher and a probe that fail the test if they are used, so whatever it says
 * about the failed build it read from that directory.
 *
 * <p>The file the reason is kept in is named here by hand, {@code
 * cache/failures/percolator__3.7.1__linux-x86-64.txt}, because where it lives is part of what is on
 * a scientist's disk between two runs and so is not the product's to restate.
 *
 * <p>Files read outside this module: the real artefacts in {@code scratch/phase05/artefacts}, as
 * every test of {@link ToolManagerFixtures} does.
 */
class FailedInstallReasonAfterRestartTest {

    private static final String PERCOLATOR_ZIP =
            "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";

    private static final String PERCOLATOR_DEB =
            "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";

    private static final ToolVersion PERCOLATOR_3_07_1 = ToolVersion.parse("3.07.1");

    private static final ToolVersion PERCOLATOR_3_06_5 = ToolVersion.parse("3.06.5");

    private static final String TOKEN = "s3cr3t-T0ken-value-1234567890";

    /** What the probe of {@link #refusingProbe()} says, as the installer words a step-6 refusal. */
    private static final String PROBE_REASON =
            "Install step 6 of 8, PROBE, failed: percolator 3.07.1 linux-x86-64 was not"
                    + " installed: the installed build was not accepted by the probe: percolator"
                    + " exited with status 127: error while loading shared libraries:"
                    + " libboost_system.so.1.66.0 (install step 6, PROBE)";

    @TempDir private Path temporary;

    /** The application-data directory both sessions are opened over. */
    private Path root() {
        return temporary.resolve("application data");
    }

    /** The directory the reasons are kept in between sessions, named by hand. */
    private Path failureRecords() {
        return root().resolve("cache/failures");
    }

    /** Where this build's reason is kept between sessions, named by hand. */
    private Path failureRecord() {
        return failureRecords().resolve("percolator__3.7.1__linux-x86-64.txt");
    }

    private static ArtefactRecord percolator() throws IOException {
        return ToolManagerFixtures.record(ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
    }

    private static ToolProbe refusingProbe() {
        return (artefact, staged) -> {
            throw new IOException(
                    "percolator exited with status 127: error while loading shared libraries:"
                            + " libboost_system.so.1.66.0");
        };
    }

    private static ToolOffer rowFor(List<ToolOffer> offers, ToolVersion version) {
        return offers.stream()
                .filter(offer -> offer.tool() == ToolName.PERCOLATOR)
                .filter(offer -> offer.version().equals(version))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("no percolator " + version + " row in " + offers));
    }

    private static ServedArtefacts servingPercolator() throws IOException {
        ArtefactRecord record = percolator();
        return new ServedArtefacts()
                .serve(record.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                .serve(
                        record.companions().get(0).url(),
                        ToolManagerFixtures.artefact(PERCOLATOR_DEB));
    }

    /** One session in which the install of Percolator 3.07.1 is refused by the probe. */
    private ToolOffer aSessionWhoseInstallFails() throws IOException, InterruptedException {
        try (ServedArtefacts served = servingPercolator()) {
            ToolManagerHarness first =
                    ToolManagerHarness.at(root()).fetching(served).probing(refusingProbe()).build();
            RecordingListener listener = new RecordingListener();
            first.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            assertEquals(InstallPhase.FAILED, listener.awaitTerminal().phase());
            return rowFor(first.manager().offers(), PERCOLATOR_3_07_1);
        }
    }

    /**
     * The application opened again over the same directory: a manager that has installed nothing,
     * fetches nothing and probes nothing.
     */
    private ToolManagerHarness reopened() throws IOException {
        return ToolManagerHarness.at(root()).probing(FixedProbe.refusingToBeCalled()).build();
    }

    private static List<String> names(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> String.valueOf(entry.getFileName())).sorted().toList();
        }
    }

    @Test
    @DisplayName(
            "closed and reopened, the row of a build whose install failed still says why, word"
                    + " for word, and no other row does")
    void theReasonSurvivesARestart() throws IOException, InterruptedException {
        ToolOffer before = aSessionWhoseInstallFails();

        List<ToolOffer> after = reopened().manager().offers();

        ToolOffer row = rowFor(after, PERCOLATOR_3_07_1);
        ToolOffer neighbour = rowFor(after, PERCOLATOR_3_06_5);
        assertAll(
                () -> assertEquals(ToolInstallState.FAILED, before.state(), "before the restart"),
                () -> assertEquals(Optional.of(PROBE_REASON), before.failureReason()),
                () -> assertEquals(ToolInstallState.FAILED, row.state(), "after the restart"),
                () -> assertEquals(Optional.of(PROBE_REASON), row.failureReason()),
                () -> assertEquals(before, row, "the whole offer, not only its reason"),
                () ->
                        assertEquals(
                                PROBE_REASON,
                                Files.readString(failureRecord(), StandardCharsets.UTF_8),
                                "what is kept on disk between the two sessions"),
                () ->
                        assertEquals(
                                List.of("percolator__3.7.1__linux-x86-64.txt"),
                                names(failureRecords()),
                                "one record, for the one build that failed, and no temporary"
                                        + " file beside it"),
                () ->
                        assertEquals(
                                ToolInstallState.NOT_INSTALLED,
                                neighbour.state(),
                                "a failure is recorded against the build that failed, not"
                                        + " against the tool"),
                () -> assertEquals(Optional.empty(), neighbour.failureReason()));
    }

    @Test
    @DisplayName(
            "a secret in the cause never reaches the disk: the record holds the redacted reason,"
                    + " and the reopened row shows the same")
    void aSecretNeverReachesTheDisk() throws IOException, InterruptedException {
        ToolManagerHarness first =
                ToolManagerHarness.at(root())
                        .fetching(
                                request -> {
                                    throw new IOException(
                                            "the request to https://mirror.invalid/percolator.zip"
                                                    + "?token="
                                                    + TOKEN
                                                    + " was refused");
                                })
                        .probing(FixedProbe.refusingToBeCalled())
                        .build();
        RecordingListener listener = new RecordingListener();
        first.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
        assertEquals(InstallPhase.FAILED, listener.awaitTerminal().phase());

        ToolOffer row = rowFor(reopened().manager().offers(), PERCOLATOR_3_07_1);

        String redacted =
                "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE, failed: the request to"
                        + " https://mirror.invalid/percolator.zip?token=[REDACTED] was refused";
        String onDisk = Files.readString(failureRecord(), StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(redacted, onDisk),
                () -> assertFalse(onDisk.contains(TOKEN), "the token itself is on disk"),
                () -> assertEquals(Optional.of(redacted), row.failureReason()),
                () -> assertFalse(row.toString().contains(TOKEN), "nor in the offer"));
    }

    @Test
    @DisplayName(
            "a record that was rewritten outside the application is redacted on the way in, and"
                    + " a blank one is no failure at all")
    void aRecordFromOutsideIsRedactedOnTheWayIn() throws IOException {
        Files.createDirectories(failureRecords());
        Files.writeString(
                failureRecord(),
                "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE, failed: the request to"
                        + " https://mirror.invalid/percolator.zip?token="
                        + TOKEN
                        + " was refused",
                StandardCharsets.UTF_8);
        ToolOffer rewritten = rowFor(reopened().manager().offers(), PERCOLATOR_3_07_1);

        Files.writeString(failureRecord(), " \n", StandardCharsets.UTF_8);
        ToolOffer blank = rowFor(reopened().manager().offers(), PERCOLATOR_3_07_1);

        assertAll(
                () -> assertEquals(ToolInstallState.FAILED, rewritten.state()),
                () ->
                        assertEquals(
                                Optional.of(
                                        "Install step 1 of 8, DOWNLOAD_TO_TEMPORARY_FILE, failed:"
                                                + " the request to"
                                                + " https://mirror.invalid/percolator.zip"
                                                + "?token=[REDACTED] was refused"),
                                rewritten.failureReason()),
                () -> assertFalse(rewritten.toString().contains(TOKEN)),
                () -> assertEquals(ToolInstallState.NOT_INSTALLED, blank.state()),
                () -> assertEquals(Optional.empty(), blank.failureReason()));
    }

    @Test
    @DisplayName(
            "after the restart a new attempt that succeeds removes the record, and the session"
                    + " after that shows an installed build with no reason")
    void aLaterAttemptThatSucceedsRemovesTheRecord() throws IOException, InterruptedException {
        aSessionWhoseInstallFails();
        try (ServedArtefacts served = servingPercolator()) {
            ToolManagerHarness second =
                    ToolManagerHarness.at(root())
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .build();
            ToolOffer shown = rowFor(second.manager().offers(), PERCOLATOR_3_07_1);
            served.holdTransfers();
            RecordingListener again = new RecordingListener();
            second.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, again);
            boolean keptWhileRunning = Files.exists(failureRecord());
            ToolOffer running = rowFor(second.manager().offers(), PERCOLATOR_3_07_1);
            served.release();
            assertEquals(InstallPhase.DONE, again.awaitTerminal().phase());
            ToolOffer done = rowFor(second.manager().offers(), PERCOLATOR_3_07_1);

            ToolOffer third = rowFor(reopened().manager().offers(), PERCOLATOR_3_07_1);

            assertAll(
                    () -> assertEquals(Optional.of(PROBE_REASON), shown.failureReason()),
                    () ->
                            assertFalse(
                                    keptWhileRunning,
                                    "the record of the earlier attempt was still on disk while"
                                            + " the new one ran"),
                    () -> assertEquals(ToolInstallState.INSTALLING, running.state()),
                    () -> assertEquals(Optional.empty(), running.failureReason()),
                    () -> assertEquals(ToolInstallState.INSTALLED, done.state()),
                    () -> assertEquals(Optional.empty(), done.failureReason()),
                    () -> assertFalse(Files.exists(failureRecord()), "after the install"),
                    () -> assertEquals(ToolInstallState.INSTALLED, third.state()),
                    () -> assertEquals(Optional.empty(), third.failureReason()));
        }
    }

    @Test
    @DisplayName(
            "after the restart a new attempt that is cancelled leaves a build that is simply not"
                    + " installed, in that session and in the next")
    void aLaterAttemptThatIsCancelledLeavesNoReason() throws IOException, InterruptedException {
        aSessionWhoseInstallFails();
        ToolManagerHarness.QueuedInstalls queued = new ToolManagerHarness.QueuedInstalls();
        ToolManagerHarness second =
                ToolManagerHarness.at(root())
                        .probing(FixedProbe.refusingToBeCalled())
                        .installingOn(queued)
                        .build();
        ToolOffer shown = rowFor(second.manager().offers(), PERCOLATOR_3_07_1);
        RecordingListener again = new RecordingListener();
        InstallHandle handle =
                second.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, again);
        handle.cancel();
        queued.runQueued();
        assertEquals(InstallPhase.CANCELLED, again.awaitTerminal().phase());
        ToolOffer cancelled = rowFor(second.manager().offers(), PERCOLATOR_3_07_1);

        ToolOffer third = rowFor(reopened().manager().offers(), PERCOLATOR_3_07_1);

        assertAll(
                () -> assertEquals(ToolInstallState.FAILED, shown.state()),
                () -> assertEquals(ToolInstallState.NOT_INSTALLED, cancelled.state()),
                () -> assertEquals(Optional.empty(), cancelled.failureReason()),
                () -> assertFalse(Files.exists(failureRecord())),
                () -> assertEquals(ToolInstallState.NOT_INSTALLED, third.state()),
                () -> assertEquals(Optional.empty(), third.failureReason()));
    }

    @Test
    @DisplayName("an installed build outranks a failure recorded before it")
    void anInstalledBuildOutranksAnEarlierRecord() throws IOException, InterruptedException {
        try (ServedArtefacts served = servingPercolator()) {
            ToolManagerHarness first =
                    ToolManagerHarness.at(root())
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .build();
            RecordingListener listener = new RecordingListener();
            first.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            assertEquals(InstallPhase.DONE, listener.awaitTerminal().phase());
        }
        Files.createDirectories(failureRecords());
        Files.writeString(failureRecord(), PROBE_REASON, StandardCharsets.UTF_8);

        ToolOffer row = rowFor(reopened().manager().offers(), PERCOLATOR_3_07_1);

        assertAll(
                () -> assertEquals(ToolInstallState.INSTALLED, row.state()),
                () -> assertEquals(Optional.empty(), row.failureReason()));
    }

    /*
     * A CACHE THAT CANNOT KEEP THE REASON.  A regular file sits where the directory of records
     * would be made, so the record can be neither written, read nor removed -- each of the three
     * meets an I/O failure of its own.  None of them may cost the session what it already knows or
     * stop the install the user asked for: the reason is held for the session, the next session
     * offers the install rather than failing to draw its rows, and a new attempt goes ahead.
     */
    @Test
    @DisplayName(
            "a cache that cannot keep the reason: this session still says why, the next offers"
                    + " the install, and a new attempt is not stopped by it")
    void aCacheThatCannotKeepTheReason() throws IOException, InterruptedException {
        Path inTheWay = failureRecords();
        Files.createDirectories(root().resolve("cache"));
        Files.writeString(inTheWay, "not a directory", StandardCharsets.UTF_8);

        ToolOffer before = aSessionWhoseInstallFails();
        List<ToolOffer> after = reopened().manager().offers();
        ToolOffer done;
        try (ServedArtefacts served = servingPercolator()) {
            ToolManagerHarness third =
                    ToolManagerHarness.at(root())
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .build();
            RecordingListener again = new RecordingListener();
            third.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, again);
            assertEquals(InstallPhase.DONE, again.awaitTerminal().phase());
            done = rowFor(third.manager().offers(), PERCOLATOR_3_07_1);
        }

        ToolOffer row = rowFor(after, PERCOLATOR_3_07_1);
        assertAll(
                () -> assertEquals(ToolInstallState.FAILED, before.state()),
                () -> assertEquals(Optional.of(PROBE_REASON), before.failureReason()),
                () -> assertEquals(ToolInstallState.NOT_INSTALLED, row.state()),
                () -> assertEquals(Optional.empty(), row.failureReason()),
                () -> assertEquals(ToolInstallState.INSTALLED, done.state()),
                () ->
                        assertEquals(
                                "not a directory",
                                Files.readString(inTheWay, StandardCharsets.UTF_8),
                                "and what was in the way is untouched"));
    }
}
