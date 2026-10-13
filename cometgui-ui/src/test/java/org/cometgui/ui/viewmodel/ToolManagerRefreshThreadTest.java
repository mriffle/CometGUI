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

package org.cometgui.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.ScriptedToolManager;
import org.cometgui.ui.testing.ToolOffers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where {@link ToolManagerViewModel#refresh()} reads the port, and what it does with the answer.
 *
 * <p>Reading the port starts every installed tool to check that it still runs here, so it must not
 * happen on the interface thread. Both executors are queues here, so each test decides exactly when
 * the background read runs and when its answer reaches the "interface thread", and can see which of
 * the two the port was read from. That the real process service is never asked to start anything on
 * the real JavaFX thread is graded in {@code cometgui-app}, over the real Tool Manager and a really
 * installed converter, by {@code ToolManagerRefreshThreadUiTest}.
 */
class ToolManagerRefreshThreadTest {

    private static final Path INSTALLED_AT =
            Path.of("opt", "cometgui", "tools", "comet", "2026.2.2", "bin", "comet")
                    .toAbsolutePath();

    /** An executor that runs nothing until a test says so, in the order it was given work. */
    private static final class Queue implements Executor {

        private final List<Runnable> waiting = new ArrayList<>();

        private final String name;

        private String running = "";

        Queue(String name) {
            this.name = name;
        }

        @Override
        public void execute(Runnable work) {
            waiting.add(work);
        }

        int size() {
            return waiting.size();
        }

        /** Runs the oldest queued task, as this executor's "thread". */
        void runOldest() {
            runAt(0);
        }

        /** Runs the newest queued task, as this executor's "thread". */
        void runNewest() {
            runAt(waiting.size() - 1);
        }

        private void runAt(int index) {
            if (waiting.isEmpty()) {
                throw new AssertionError(
                        "nothing was queued on the "
                                + name
                                + " executor, so the work this test expected there ran somewhere"
                                + " else -- for a read of the port, on the thread that called"
                                + " refresh()");
            }
            Runnable work = waiting.remove(index);
            running = name;
            try {
                work.run();
            } finally {
                running = "";
            }
        }

        String running() {
            return running;
        }
    }

    /** A port that records, for every read, which queue was running it, and answers in turn. */
    private static final class WatchedToolManager implements ToolManager {

        private final ScriptedToolManager delegate;

        private final List<Queue> queues;

        private final List<String> readOn = new ArrayList<>();

        private final List<List<ToolOffer>> answers = new ArrayList<>();

        private String failure;

        WatchedToolManager(ScriptedToolManager delegate, Queue... queues) {
            this.delegate = delegate;
            this.queues = List.of(queues);
        }

        void thenAnswer(ToolOffer... offers) {
            answers.add(List.of(offers));
        }

        void failWith(String message) {
            failure = message;
        }

        @Override
        public List<ToolOffer> offers() {
            String where = "the calling thread";
            for (Queue queue : queues) {
                if (!queue.running().isEmpty()) {
                    where = queue.running();
                }
            }
            readOn.add(where);
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
            return answers.isEmpty() ? delegate.offers() : answers.remove(0);
        }

        @Override
        public List<NoManagedBuild> noManagedBuild() {
            return delegate.noManagedBuild();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            return delegate.install(tool, version, listener);
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable)
                throws ToolRegistrationException {
            return delegate.registerLocalBinary(tool, executable);
        }

        List<String> readOn() {
            return List.copyOf(readOn);
        }
    }

    private static List<String> keysOf(ToolManagerViewModel viewModel) {
        return viewModel.rows().stream().map(ToolRowViewModel::key).toList();
    }

    @Test
    @DisplayName("a refresh reads the port on the background executor, never where it was called")
    void theReadHappensOnTheBackgroundExecutor() {
        Queue ui = new Queue("ui");
        Queue background = new Queue("background");
        WatchedToolManager port =
                new WatchedToolManager(
                        new ScriptedToolManager(ToolOffers.percolatorAvailable()), ui, background);
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(port, ui, background, new Editors.ScriptedChooser());

        viewModel.refresh();
        List<String> readWhenCalled = port.readOn();
        int queuedBackground = background.size();
        background.runOldest();
        List<String> keysBeforeTheInterfaceThreadRan = keysOf(viewModel);
        String summaryBeforeTheInterfaceThreadRan = viewModel.summary();
        int queuedUi = ui.size();
        ui.runOldest();

        assertAll(
                () ->
                        assertEquals(
                                List.of(),
                                readWhenCalled,
                                "refresh() read the port on the thread that called it -- in the"
                                        + " application, the JavaFX thread"),
                () -> assertEquals(1, queuedBackground),
                () -> assertEquals(List.of("background"), port.readOn()),
                () ->
                        assertEquals(
                                List.of(),
                                keysBeforeTheInterfaceThreadRan,
                                "the rows changed on the background thread, not the interface"
                                        + " thread"),
                () ->
                        assertEquals(
                                ToolManagerViewModel.NOT_READ_YET,
                                summaryBeforeTheInterfaceThreadRan),
                () -> assertEquals(1, queuedUi),
                () -> assertEquals(List.of("percolator-3_07_1-1"), keysOf(viewModel)),
                () -> assertEquals("1 tool build on this host.", viewModel.summary()));
    }

    @Test
    @DisplayName("an older read that finishes last does not put its stale list back on screen")
    void anOlderReadThatFinishesLastIsDropped() {
        Queue ui = new Queue("ui");
        Queue background = new Queue("background");
        WatchedToolManager port = new WatchedToolManager(new ScriptedToolManager(), ui, background);
        port.thenAnswer(ToolOffers.percolatorAvailable());
        port.thenAnswer(ToolOffers.percolatorAvailable(), ToolOffers.pdvFailed());
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(port, ui, background, new Editors.ScriptedChooser());

        viewModel.refresh();
        viewModel.refresh();
        background.runOldest();
        background.runOldest();
        ui.runNewest();
        List<String> afterTheNewerAnswer = keysOf(viewModel);
        ui.runOldest();

        assertAll(
                () ->
                        assertEquals(
                                List.of("percolator-3_07_1-1", "pdv-2_7_0-1"), afterTheNewerAnswer),
                () ->
                        assertEquals(
                                List.of("percolator-3_07_1-1", "pdv-2_7_0-1"),
                                keysOf(viewModel),
                                "the first refresh's answer arrived last and replaced the newer"
                                        + " list with an older one"),
                () -> assertEquals("2 tool builds on this host.", viewModel.summary()));
    }

    @Test
    @DisplayName("an older read is dropped even when it arrives first, and the newer one applies")
    void anOlderReadArrivingFirstIsAlsoDropped() {
        Queue ui = new Queue("ui");
        Queue background = new Queue("background");
        WatchedToolManager port = new WatchedToolManager(new ScriptedToolManager(), ui, background);
        port.thenAnswer(ToolOffers.pdvFailed());
        port.thenAnswer(ToolOffers.percolatorAvailable());
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(port, ui, background, new Editors.ScriptedChooser());

        viewModel.refresh();
        background.runOldest();
        viewModel.refresh();
        ui.runOldest();
        List<String> afterTheSupersededAnswer = keysOf(viewModel);
        background.runOldest();
        ui.runOldest();

        assertAll(
                () -> assertEquals(List.of(), afterTheSupersededAnswer),
                () -> assertEquals(List.of("percolator-3_07_1-1"), keysOf(viewModel)));
    }

    @Test
    @DisplayName("a row keeps its identity and its running install across an off-thread refresh")
    void aRowKeepsItsInstallAcrossAnOffThreadRefresh() {
        Queue ui = new Queue("ui");
        Queue background = new Queue("background");
        ScriptedToolManager scripted = new ScriptedToolManager(ToolOffers.percolatorAvailable());
        WatchedToolManager port = new WatchedToolManager(scripted, ui, background);
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(port, ui, background, new Editors.ScriptedChooser());
        viewModel.refresh();
        background.runOldest();
        ui.runOldest();
        ToolRowViewModel row = viewModel.rows().get(0);

        viewModel.install(row);
        background.runOldest();
        ui.runOldest();
        scripted.report(
                new InstallProgress(
                        ToolName.PERCOLATOR,
                        ToolVersion.parse("3.07.1"),
                        InstallPhase.DOWNLOADING,
                        500_000L,
                        ToolOffers.PERCOLATOR_3_07_1_LINUX_BYTES));
        ui.runOldest();
        viewModel.refresh();
        background.runOldest();
        ui.runOldest();

        assertAll(
                () -> assertSame(row, viewModel.rows().get(0)),
                () -> assertEquals(ToolInstallState.INSTALLING, row.offer().state()),
                () -> assertTrue(row.canCancel(), "the row lost its running install"),
                () ->
                        assertEquals(
                                Optional.of("Downloading: 500,000 of 2,798,963 bytes"),
                                row.progressText()),
                () ->
                        assertEquals(
                                List.of("background", "background", "background"), port.readOn()));
    }

    @Test
    @DisplayName("a port that cannot be read leaves the rows as they were and says why")
    void anUnreadablePortIsSaid() {
        Queue ui = new Queue("ui");
        Queue background = new Queue("background");
        WatchedToolManager port =
                new WatchedToolManager(
                        new ScriptedToolManager(ToolOffers.cometInstalled(INSTALLED_AT)),
                        ui,
                        background);
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(port, ui, background, new Editors.ScriptedChooser());
        viewModel.refresh();
        background.runOldest();
        ui.runOldest();
        ToolRowViewModel before = viewModel.rows().get(0);

        port.failWith("the cache could not be listed");
        viewModel.refresh();
        background.runOldest();
        String summaryBeforeTheInterfaceThreadRan = viewModel.summary();
        ui.runOldest();

        assertAll(
                () ->
                        assertEquals(
                                "1 tool build on this host.", summaryBeforeTheInterfaceThreadRan),
                () ->
                        assertEquals(
                                "The tool list could not be read:"
                                        + " java.lang.IllegalStateException: the cache could not"
                                        + " be listed",
                                viewModel.summary()),
                () -> assertSame(before, viewModel.rows().get(0)),
                () -> assertEquals(1, viewModel.rows().size()));
    }

    @Test
    @DisplayName("a failed read that a newer refresh superseded says nothing")
    void aSupersededFailureSaysNothing() {
        Queue ui = new Queue("ui");
        Queue background = new Queue("background");
        WatchedToolManager port =
                new WatchedToolManager(
                        new ScriptedToolManager(ToolOffers.percolatorAvailable()), ui, background);
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(port, ui, background, new Editors.ScriptedChooser());
        port.failWith("the cache could not be listed");
        viewModel.refresh();
        background.runOldest();
        viewModel.refresh();
        ui.runOldest();

        assertEquals(ToolManagerViewModel.NOT_READ_YET, viewModel.summary());
    }
}
