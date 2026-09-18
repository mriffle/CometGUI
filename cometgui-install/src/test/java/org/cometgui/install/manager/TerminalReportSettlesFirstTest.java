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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * When a listener is told an install has stopped, {@code offers()} already agrees with it.
 *
 * <h2>The defect this grades</h2>
 *
 * <p>{@code ArtefactInstaller} sends exactly one terminal report, from a {@code finally} block, and
 * then returns or lets the failure propagate. {@code ManagedToolManager} used to clear its attempt
 * map <em>after</em> that call returned, so a listener that re-read {@code offers()} the moment it
 * saw {@code DONE} was told the build was still {@code INSTALLING}.
 *
 * <h2>Why every test here runs the install on the calling thread</h2>
 *
 * <p>Because that is the executor that <strong>loses</strong>. In the running application the
 * listener hands its work to the interface thread, so the report is queued and the manager finishes
 * long before anything looks -- the window closes by scheduling latency rather than by correctness,
 * and a test that only passed there would be grading the latency. These tests remove the latency:
 * the listener asks the port a question from inside the report, on the same thread, and the answer
 * has to be right.
 *
 * <p>All three terminal phases are graded, because they settle to two different answers: {@code
 * DONE} and {@code CANCELLED} leave the cache the whole truth, and {@code FAILED} leaves a row that
 * says the attempt did not succeed.
 */
class TerminalReportSettlesFirstTest {

    private static final ToolVersion PERCOLATOR_3_07_1 = ToolVersion.parse("3.07.1");

    private static final String PERCOLATOR_ZIP =
            "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";

    private static final String PERCOLATOR_DEB =
            "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";

    @TempDir private Path temporary;

    /**
     * A listener that asks the port what the row says, from inside every terminal report.
     *
     * <p>It holds the manager rather than being given the answer, so that the question is asked at
     * the instant the report is delivered and not a moment later.
     */
    private static final class AsksWhileReporting implements InstallProgressListener {

        private final List<InstallPhase> phases = new ArrayList<>();

        private final ToolManager manager;

        private InstallPhase terminal;

        private ToolInstallState stateAtTerminalReport;

        AsksWhileReporting(ToolManager manager) {
            this.manager = manager;
        }

        @Override
        public void onInstallProgress(InstallProgress progress) {
            phases.add(progress.phase());
            if (progress.phase().isTerminal()) {
                terminal = progress.phase();
                stateAtTerminalReport = percolatorRow(manager.offers()).state();
            }
        }

        InstallPhase terminal() {
            assertNotNull(terminal, () -> "no terminal report arrived at all; saw " + phases);
            return terminal;
        }

        ToolInstallState stateAtTerminalReport() {
            terminal();
            return stateAtTerminalReport;
        }
    }

    @Test
    @DisplayName("on DONE the row already says INSTALLED, asked from inside the report")
    void onDoneTheRowAlreadySaysInstalled() throws IOException {
        ArtefactRecord record = percolator();
        try (ServedArtefacts served = servingPercolator(record)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .installingOn(Runnable::run)
                            .build();
            AsksWhileReporting listener = new AsksWhileReporting(harness.manager());

            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);

            assertAll(
                    () -> assertEquals(InstallPhase.DONE, listener.terminal()),
                    () ->
                            assertEquals(
                                    ToolInstallState.INSTALLED,
                                    listener.stateAtTerminalReport(),
                                    "a listener told the install is DONE and then told the build is"
                                            + " still INSTALLING has been given two answers to one"
                                            + " question, and the first one was right"),
                    () ->
                            assertEquals(
                                    ToolInstallState.INSTALLED,
                                    percolatorRow(harness.manager().offers()).state(),
                                    "and it still says so afterwards"));
        }
    }

    @Test
    @DisplayName("on FAILED the row already says the attempt did not succeed")
    void onFailedTheRowAlreadySaysTheAttemptFailed() throws IOException {
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .fetching(
                                request -> {
                                    throw new IOException(
                                            "this test refuses every transfer, so the install fails"
                                                    + " at step 1");
                                })
                        .installingOn(Runnable::run)
                        .build();
        AsksWhileReporting listener = new AsksWhileReporting(harness.manager());

        harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);

        assertAll(
                () -> assertEquals(InstallPhase.FAILED, listener.terminal()),
                () -> assertEquals(ToolInstallState.FAILED, listener.stateAtTerminalReport()),
                () ->
                        assertEquals(
                                ToolInstallState.FAILED,
                                percolatorRow(harness.manager().offers()).state()));
    }

    @Test
    @DisplayName("on CANCELLED the row already says the build is back to not installed")
    void onCancelledTheRowAlreadySaysNotInstalled() throws IOException {
        ToolManagerHarness.QueuedInstalls queued = new ToolManagerHarness.QueuedInstalls();
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .fetching(
                                request -> {
                                    throw new AssertionError(
                                            "the install was cancelled before its first step, so"
                                                    + " nothing may be fetched: "
                                                    + request.source());
                                })
                        .installingOn(queued)
                        .build();
        AsksWhileReporting listener = new AsksWhileReporting(harness.manager());

        InstallHandle handle =
                harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
        handle.cancel();
        queued.runQueued();

        assertAll(
                () -> assertEquals(InstallPhase.CANCELLED, listener.terminal()),
                () ->
                        assertEquals(
                                ToolInstallState.NOT_INSTALLED,
                                listener.stateAtTerminalReport(),
                                "cancelling is not failing, and the row must be back where it was"
                                        + " by the time the user is told it stopped"),
                () ->
                        assertEquals(
                                ToolInstallState.NOT_INSTALLED,
                                percolatorRow(harness.manager().offers()).state()));
    }

    private static ArtefactRecord percolator() throws IOException {
        return ToolManagerFixtures.record(ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
    }

    private ServedArtefacts servingPercolator(ArtefactRecord record) throws IOException {
        ArtefactCompanion companion = record.companions().get(0);
        return new ServedArtefacts()
                .serve(record.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                .serve(companion.url(), ToolManagerFixtures.artefact(PERCOLATOR_DEB));
    }

    private static ToolOffer percolatorRow(List<ToolOffer> offers) {
        return offers.stream()
                .filter(offer -> offer.tool() == ToolName.PERCOLATOR)
                .filter(offer -> offer.version().equals(PERCOLATOR_3_07_1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no percolator 3.07.1 row in " + offers));
    }
}
