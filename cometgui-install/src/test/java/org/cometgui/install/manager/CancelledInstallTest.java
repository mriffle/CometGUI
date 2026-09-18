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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.cache.InstallCancelledException;
import org.cometgui.install.cache.InstallStep;
import org.cometgui.install.cache.InstallationState;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.download.DownloadCancelledException;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cancelling an install, graded on the axis nothing in this phase had varied: <strong>where within
 * a step the cancellation lands.</strong>
 *
 * <p>Until phase 05 unit 8 the product answered those three places differently. The pipeline asks
 * the caller between steps and hands the same {@code DownloadCancellation} down into the transfer,
 * which honours it between chunks -- and nothing translated the {@code DownloadCancelledException}
 * that came back, so it left {@code ArtefactInstaller.install} through the {@code catch
 * (IOException)} arm and a user who pressed Cancel on a 99 MB download was told the install had
 * FAILED. {@code InstallHandle.cancel()} forbids that in as many words.
 *
 * <p>So each of the three is graded here, over the real artefacts and a real transfer:
 *
 * <ol>
 *   <li>inside the main artefact's transfer -- PDV, the 103 407 417-byte download the phase
 *       document singles out;
 *   <li>inside a <em>companion</em> transfer, which is a second fetch inside the same step --
 *       Percolator 3.07.1's {@code .deb}, the one artefact in the manifest that has one;
 *   <li>at a step boundary, which is the case unit 5 already covered, so it stays covered.
 * </ol>
 *
 * <p>This class asserts the exception that surfaced; {@link ManagedToolManagerCancellationTest}
 * asserts the phase the listener was told, which is the same defect seen from the user's side.
 */
class CancelledInstallTest {

    private static final String PDV_ZIP = "v2.7.0__PDV-2.7.0.zip";
    private static final String PERCOLATOR_ZIP =
            "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";
    private static final String PERCOLATOR_DEB =
            "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";

    /** How much of the main artefact is let through before the cancellation is tripped. */
    private static final long PART_OF_PDV = 4_000_000L;

    /** How much of the companion is let through before the cancellation is tripped. */
    private static final long PART_OF_THE_DEB = 400_000L;

    @TempDir private Path temporary;

    @Test
    @DisplayName("cancelling inside the main artefact's transfer is a cancellation, not a failure")
    void cancellingInsideTheMainTransfer() throws IOException, InterruptedException {
        ArtefactRecord pdv =
                ToolManagerFixtures.record(ToolName.PDV, "2.7.0", ToolManagerFixtures.LINUX);
        AtomicBoolean cancelled = new AtomicBoolean();
        RecordingListener listener = new RecordingListener();
        FixedProbe probe = FixedProbe.refusingToBeCalled();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(pdv.url(), ToolManagerFixtures.artefact(PDV_ZIP))
                        .cancelAfter(pdv.url(), PART_OF_PDV, () -> cancelled.set(true))) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(probe)
                            .build();

            InstallCancelledException stopped =
                    assertThrows(
                            InstallCancelledException.class,
                            () -> harness.installer().install(pdv, listener, cancelled::get));

            DownloadCancelledException transfer =
                    assertInstanceOf(DownloadCancelledException.class, stopped.getCause());
            assertAll(
                    () ->
                            assertEquals(
                                    "the install of pdv 2.7.0 linux-x86-64 was cancelled"
                                            + " during step 1, DOWNLOAD_TO_TEMPORARY_FILE;"
                                            + " nothing was written to the tool cache",
                                    stopped.getMessage()),
                    () -> assertTrue(stopped.cancelledDuringThatStep()),
                    () -> assertEquals(InstallStep.DOWNLOAD_TO_TEMPORARY_FILE, stopped.nextStep()),
                    () ->
                            assertTrue(
                                    transfer.bytesTransferred() >= PART_OF_PDV,
                                    () ->
                                            "the transfer had moved "
                                                    + transfer.bytesTransferred()
                                                    + " byte(s), which must be at least the"
                                                    + " "
                                                    + PART_OF_PDV
                                                    + " the listener let through"),
                    () ->
                            assertTrue(
                                    transfer.bytesTransferred() < pdv.sizeBytes(),
                                    () ->
                                            "and well short of the whole "
                                                    + pdv.sizeBytes()
                                                    + "-byte artefact: a cancellation that only"
                                                    + " fired after the download finished would"
                                                    + " grade nothing"),
                    () ->
                            assertEquals(
                                    List.of(InstallPhase.DOWNLOADING, InstallPhase.CANCELLED),
                                    listener.phases().stream().distinct().toList(),
                                    "and the listener was told CANCELLED, which is the whole"
                                            + " defect: before the translation this read"
                                            + " [DOWNLOADING, FAILED]"),
                    () -> assertEquals(1, listener.terminalReports().size()),
                    () -> assertEquals(0, probe.callCount(), "nothing was executed"),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(pdv).state(),
                                    "R-TOOL-04: nothing in the cache reports itself installed"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    harness.stagingDirectories(pdv),
                                    "and no staging directory survived"));
        }
    }

    @Test
    @DisplayName("cancelling inside a companion's transfer is a cancellation too")
    void cancellingInsideACompanionTransfer() throws IOException, InterruptedException {
        ArtefactRecord percolator =
                ToolManagerFixtures.record(
                        ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
        ArtefactCompanion companion = percolator.companions().get(0);
        AtomicBoolean cancelled = new AtomicBoolean();
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(percolator.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                        .serve(companion.url(), ToolManagerFixtures.artefact(PERCOLATOR_DEB))
                        .cancelAfter(companion.url(), PART_OF_THE_DEB, () -> cancelled.set(true))) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();

            InstallCancelledException stopped =
                    assertThrows(
                            InstallCancelledException.class,
                            () ->
                                    harness.installer()
                                            .install(percolator, listener, cancelled::get));

            DownloadCancelledException transfer =
                    assertInstanceOf(DownloadCancelledException.class, stopped.getCause());
            assertAll(
                    () ->
                            assertEquals(
                                    "the install of percolator 3.07.1 linux-x86-64 was"
                                            + " cancelled during step 1,"
                                            + " DOWNLOAD_TO_TEMPORARY_FILE; nothing was written"
                                            + " to the tool cache",
                                    stopped.getMessage()),
                    () -> assertTrue(stopped.cancelledDuringThatStep()),
                    () ->
                            assertEquals(
                                    List.of(percolator.url(), companion.url()),
                                    served.requested(),
                                    "the main artefact was fetched whole and the cancellation"
                                            + " landed in the second transfer of the same step"),
                    () ->
                            assertTrue(
                                    served.trippedAt(percolator.url()).isEmpty(),
                                    "nothing tripped during the main transfer"),
                    () ->
                            assertTrue(
                                    transfer.bytesTransferred() >= PART_OF_THE_DEB,
                                    () ->
                                            "the companion transfer had moved "
                                                    + transfer.bytesTransferred()
                                                    + " byte(s)"),
                    () ->
                            assertTrue(
                                    transfer.bytesTransferred() < companion.sizeBytes(),
                                    () ->
                                            "and short of the whole "
                                                    + companion.sizeBytes()
                                                    + "-byte companion"),
                    () ->
                            assertEquals(
                                    List.of(InstallPhase.DOWNLOADING, InstallPhase.CANCELLED),
                                    listener.phases().stream().distinct().toList(),
                                    "CANCELLED, not FAILED, for a cancellation inside the second"
                                            + " transfer of the step"),
                    () -> assertEquals(1, listener.terminalReports().size()),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(percolator).state()),
                    () -> assertEquals(List.of(), harness.stagingDirectories(percolator)));
        }
    }

    @Test
    @DisplayName("cancelling at a step boundary still stops before step 1, and says so")
    void cancellingAtAStepBoundary() throws IOException, InterruptedException {
        ArtefactRecord percolator =
                ToolManagerFixtures.record(
                        ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(percolator.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();

            InstallCancelledException stopped =
                    assertThrows(
                            InstallCancelledException.class,
                            () ->
                                    harness.installer()
                                            .install(percolator, listener, alwaysCancelled()));

            assertAll(
                    () ->
                            assertEquals(
                                    "the install of percolator 3.07.1 linux-x86-64 was"
                                            + " cancelled before step 1,"
                                            + " DOWNLOAD_TO_TEMPORARY_FILE; nothing was written"
                                            + " to the tool cache",
                                    stopped.getMessage()),
                    () ->
                            assertEquals(
                                    false,
                                    stopped.cancelledDuringThatStep(),
                                    "this one stopped at the boundary, not inside the step"),
                    () -> assertEquals(null, stopped.getCause(), "and no transfer was under way"),
                    () -> assertEquals(List.of(), served.requested(), "nothing was fetched at all"),
                    () ->
                            assertEquals(
                                    List.of(InstallPhase.CANCELLED),
                                    listener.phases(),
                                    "one report, and it is the terminal CANCELLED: no step ran"),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(percolator).state()),
                    () -> assertEquals(List.of(), harness.stagingDirectories(percolator)));
        }
    }

    @Test
    @DisplayName("an install nobody cancels still finishes, so the flag is not the reason")
    void anInstallNobodyCancelsStillFinishes() throws IOException, InterruptedException {
        ArtefactRecord percolator =
                ToolManagerFixtures.record(
                        ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
        ArtefactCompanion companion = percolator.companions().get(0);
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(percolator.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                        .serve(companion.url(), ToolManagerFixtures.artefact(PERCOLATOR_DEB))) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .build();

            harness.installer().install(percolator, listener, DownloadCancellation.never());

            assertAll(
                    () -> assertTrue(harness.verify(percolator).installed()),
                    () ->
                            assertEquals(
                                    InstallPhase.DONE, listener.terminalReports().get(0).phase()));
        }
    }

    private static DownloadCancellation alwaysCancelled() {
        return () -> true;
    }
}
