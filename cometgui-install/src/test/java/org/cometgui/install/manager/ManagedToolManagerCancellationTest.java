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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.cache.InstallationState;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The same three cancellations as {@link CancelledInstallTest}, seen from where a scientist sees
 * them: through {@link org.cometgui.domain.tools.ToolManager#install}, with the handle the port
 * hands back and the phase the listener is told.
 *
 * <p>{@code InstallHandle.cancel()} promises three things and all three are asserted here: it
 * returns immediately, it is safe on an install that has already finished, and it results in {@link
 * InstallPhase#CANCELLED} -- <em>"never {@link InstallPhase#FAILED} -- a user who cancelled has not
 * encountered an error"</em>. The row afterwards must not say FAILED either: nothing was written to
 * the cache, so the build is where it was before.
 */
class ManagedToolManagerCancellationTest {

    private static final String PDV_ZIP = "v2.7.0__PDV-2.7.0.zip";
    private static final String PERCOLATOR_ZIP =
            "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";
    private static final String PERCOLATOR_DEB =
            "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";
    private static final ToolVersion PDV_2_7_0 = ToolVersion.parse("2.7.0");
    private static final ToolVersion PERCOLATOR_3_07_1 = ToolVersion.parse("3.07.1");
    private static final long PART_OF_PDV = 4_000_000L;
    private static final long PART_OF_THE_DEB = 400_000L;

    @TempDir private Path temporary;

    private static ToolOffer rowFor(List<ToolOffer> offers, ToolName tool, ToolVersion version) {
        return offers.stream()
                .filter(offer -> offer.tool() == tool)
                .filter(offer -> offer.version().equals(version))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + tool.id() + " in " + offers));
    }

    @Test
    @DisplayName("cancelling the 99 MB PDV transfer reports CANCELLED, and the row is unchanged")
    void cancellingInsideTheMainTransferReportsCancelled()
            throws IOException, InterruptedException {
        ArtefactRecord pdv =
                ToolManagerFixtures.record(ToolName.PDV, "2.7.0", ToolManagerFixtures.LINUX);
        AtomicReference<InstallHandle> handle = new AtomicReference<>();
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(pdv.url(), ToolManagerFixtures.artefact(PDV_ZIP))
                        .cancelAfter(pdv.url(), PART_OF_PDV, () -> handle.get().cancel())) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();
            /*
             * Held until the handle is in hand: install() returns as soon as the install has
             * started, and a test that raced the transfer against its own assignment would cancel
             * nothing on a fast machine and pass.
             */
            served.holdTransfers();

            handle.set(harness.manager().install(ToolName.PDV, PDV_2_7_0, listener));
            served.release();
            InstallProgress terminal = listener.awaitTerminal();

            ToolOffer row = rowFor(harness.manager().offers(), ToolName.PDV, PDV_2_7_0);
            assertAll(
                    () ->
                            assertEquals(
                                    InstallPhase.CANCELLED,
                                    terminal.phase(),
                                    "never FAILED: a user who cancelled has not encountered an"
                                            + " error"),
                    () -> assertEquals(1, listener.terminalReports().size()),
                    () ->
                            assertTrue(
                                    listener.bytesDownloaded() >= PART_OF_PDV,
                                    () ->
                                            "the transfer had reported "
                                                    + listener.bytesDownloaded()
                                                    + " byte(s) before it stopped"),
                    () ->
                            assertTrue(
                                    listener.bytesDownloaded() < pdv.sizeBytes(),
                                    () ->
                                            "and stopped short of the whole "
                                                    + pdv.sizeBytes()
                                                    + "-byte artefact"),
                    () ->
                            assertEquals(
                                    ToolInstallState.NOT_INSTALLED,
                                    row.state(),
                                    "the row is where it was before the user pressed the button,"
                                            + " and in particular does not say FAILED"),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(pdv).state(),
                                    "R-TOOL-04: nothing in the cache reports itself installed"),
                    () -> assertEquals(List.of(), harness.stagingDirectories(pdv)));
        }
    }

    @Test
    @DisplayName("cancelling inside a companion transfer reports CANCELLED too")
    void cancellingInsideACompanionTransferReportsCancelled()
            throws IOException, InterruptedException {
        ArtefactRecord percolator =
                ToolManagerFixtures.record(
                        ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
        ArtefactCompanion companion = percolator.companions().get(0);
        AtomicReference<InstallHandle> handle = new AtomicReference<>();
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(percolator.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                        .serve(companion.url(), ToolManagerFixtures.artefact(PERCOLATOR_DEB))
                        .cancelAfter(
                                companion.url(), PART_OF_THE_DEB, () -> handle.get().cancel())) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();
            served.holdTransfers();

            handle.set(harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener));
            served.release();
            InstallProgress terminal = listener.awaitTerminal();

            ToolOffer row =
                    rowFor(harness.manager().offers(), ToolName.PERCOLATOR, PERCOLATOR_3_07_1);
            assertAll(
                    () -> assertEquals(InstallPhase.CANCELLED, terminal.phase()),
                    () -> assertEquals(1, listener.terminalReports().size()),
                    () ->
                            assertEquals(
                                    List.of(percolator.url(), companion.url()),
                                    served.requested(),
                                    "the main artefact came whole and the cancellation landed in"
                                            + " the second transfer of the same step"),
                    () ->
                            assertTrue(
                                    served.trippedAt(companion.url()).orElseThrow()
                                            >= PART_OF_THE_DEB),
                    () -> assertEquals(ToolInstallState.NOT_INSTALLED, row.state()),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(percolator).state()),
                    () -> assertEquals(List.of(), harness.stagingDirectories(percolator)));
        }
    }

    @Test
    @DisplayName("cancelling before the first step reports CANCELLED and fetches nothing")
    void cancellingAtAStepBoundaryReportsCancelled() throws IOException, InterruptedException {
        ArtefactRecord percolator =
                ToolManagerFixtures.record(
                        ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
        RecordingListener listener = new RecordingListener();
        ToolManagerHarness.QueuedInstalls queued = new ToolManagerHarness.QueuedInstalls();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(percolator.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .installingOn(queued)
                            .build();

            InstallHandle handle =
                    harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            handle.cancel();
            queued.runQueued();
            InstallProgress terminal = listener.awaitTerminal();

            ToolOffer row =
                    rowFor(harness.manager().offers(), ToolName.PERCOLATOR, PERCOLATOR_3_07_1);
            assertAll(
                    () -> assertEquals(InstallPhase.CANCELLED, terminal.phase()),
                    () -> assertEquals(List.of(InstallPhase.CANCELLED), listener.phases()),
                    () -> assertEquals(List.of(), served.requested(), "nothing was fetched"),
                    () -> assertEquals(ToolInstallState.NOT_INSTALLED, row.state()),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(percolator).state()),
                    () -> assertEquals(List.of(), harness.stagingDirectories(percolator)));
        }
    }

    @Test
    @DisplayName("cancelling an install that has already finished does nothing and is not an error")
    void cancellingAFinishedInstallDoesNothing() throws IOException, InterruptedException {
        ArtefactRecord percolator =
                ToolManagerFixtures.record(
                        ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(percolator.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                        .serve(
                                percolator.companions().get(0).url(),
                                ToolManagerFixtures.artefact(PERCOLATOR_DEB))) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .build();

            InstallHandle handle =
                    harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            assertEquals(InstallPhase.DONE, listener.awaitTerminal().phase());
            handle.cancel();

            ToolOffer row =
                    rowFor(harness.manager().offers(), ToolName.PERCOLATOR, PERCOLATOR_3_07_1);
            assertAll(
                    () -> assertEquals(ToolInstallState.INSTALLED, row.state()),
                    () -> assertTrue(harness.verify(percolator).installed()),
                    () -> assertEquals(1, listener.terminalReports().size()));
        }
    }
}
