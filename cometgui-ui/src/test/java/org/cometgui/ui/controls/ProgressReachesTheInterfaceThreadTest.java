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

package org.cometgui.ui.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.testing.ScriptedToolManager;
import org.cometgui.ui.testing.ToolOffers;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.cometgui.ui.viewmodel.ToolRowViewModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A progress report raised on an install thread reaches the row on the interface thread.
 *
 * <h2>Why this test exists, and why nothing else could have caught it</h2>
 *
 * <p>{@code ToolManagerViewModel} hands the port a listener that puts every report back on the
 * interface thread before it touches anything. <strong>That hop was, until this class, graded
 * nowhere.</strong> Removing it left the whole suite green, because every other test passes {@code
 * Runnable::run} as the interface thread -- a legitimate convention that makes sequences
 * deterministic, and one that makes the hop invisible by construction. The axis nothing varied was
 * <em>which thread the report arrives on</em>, and in the running application that is one of {@code
 * ToolManagerWiring.installThreads()}'s {@code cometgui-install-N} daemon threads.
 *
 * <h2>Observing the thread, and not hoping for an exception</h2>
 *
 * <p><strong>JavaFX does not throw here.</strong> Mutating a property or an observable list that a
 * live scene is watching, from another thread, is undefined by the toolkit's own contract, but it
 * raises no {@code IllegalStateException} under Monocle -- so a test that asserted "nothing was
 * thrown" would pass with the hop removed and would be worth nothing. Both tests below therefore
 * <em>capture the thread the change arrives on</em> and assert on it, and both assert first that a
 * change arrived at all, so that neither can pass vacuously if the wiring changes underneath.
 *
 * <p>Both halves of the view-model's state are covered, because they are two different hazards: a
 * mid-install report writes a <em>property</em> a row's labels are showing, and a terminal report
 * additionally rebuilds the <em>observable list</em> the pane's children are drawn from.
 */
class ProgressReachesTheInterfaceThreadTest {

    /** How long to wait for a change that should arrive within a frame. */
    private static final long TIMEOUT_SECONDS = 30;

    /** The name production gives an install thread, so a failure reads like the real one. */
    private static final String INSTALL_THREAD = "cometgui-install-1";

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    /** Where one change arrived, captured at the moment it did. */
    private static final class ArrivedOn {

        private final CountDownLatch arrived = new CountDownLatch(1);

        private final AtomicReference<String> threadName = new AtomicReference<>();

        private final AtomicReference<Boolean> interfaceThread = new AtomicReference<>();

        void record() {
            threadName.compareAndSet(null, Thread.currentThread().getName());
            interfaceThread.compareAndSet(null, Platform.isFxApplicationThread());
            arrived.countDown();
        }

        /**
         * Waits for the change and fails if none came.
         *
         * @param what the change being waited for, named in the failure
         * @throws InterruptedException if the wait is interrupted
         */
        void awaitArrival(String what) throws InterruptedException {
            assertTrue(
                    arrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    () ->
                            "no "
                                    + what
                                    + " reached the view-model at all within "
                                    + TIMEOUT_SECONDS
                                    + "s, so this test would have proved nothing about which"
                                    + " thread it arrived on");
        }

        /**
         * Asserts the change arrived on the interface thread, naming the thread it really used.
         *
         * @param what the change, named in the failure
         */
        void assertOnInterfaceThread(String what) {
            assertEquals(
                    Boolean.TRUE,
                    interfaceThread.get(),
                    () ->
                            "a progress report raised on a background install thread must reach "
                                    + what
                                    + " on the JavaFX application thread, but it arrived on \""
                                    + threadName.get()
                                    + "\". JavaFX raises no exception for this and never will:"
                                    + " mutating a property or a list a live scene is watching from"
                                    + " another thread is undefined, not refused.");
        }
    }

    @Test
    @DisplayName("a mid-install report reaches the row's property on the interface thread")
    void aMidInstallReportReachesThePropertyOnTheInterfaceThread() throws InterruptedException {
        ScriptedToolManager manager = new ScriptedToolManager(ToolOffers.percolatorAvailable());
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(
                        manager, Platform::runLater, Runnable::run, new Editors.ScriptedChooser());
        showInALiveScene(viewModel);
        ToolRowViewModel row = viewModel.rows().get(0);
        ArrivedOn arrived = new ArrivedOn();
        FxToolkit.onFxThread(
                () -> {
                    row.progressProperty().addListener((property, was, now) -> arrived.record());
                    viewModel.install(row);
                });

        reportFromAnInstallThread(manager, InstallPhase.DOWNLOADING, 1_048_576L);

        arrived.awaitArrival("progress report");
        arrived.assertOnInterfaceThread("the row's progress property");
    }

    @Test
    @DisplayName("a terminal report rebuilds the row list on the interface thread")
    void aTerminalReportRebuildsTheRowListOnTheInterfaceThread() throws InterruptedException {
        ScriptedToolManager manager = new ScriptedToolManager(ToolOffers.percolatorAvailable());
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(
                        manager, Platform::runLater, Runnable::run, new Editors.ScriptedChooser());
        showInALiveScene(viewModel);
        /*
         * The view this test listens on is HELD, here, until the last assertion. rows() builds a
         * new unmodifiable view per call and that view observes the list behind it only WEAKLY,
         * as its own javadoc warns. A listener added to a view nobody kept was dropped by any
         * garbage collection between registering it and the terminal report, and the rebuild then
         * reached nobody: this test failed with "no row-list rebuild reached the view-model" under
         * heap pressure, with the view-model having rebuilt the list correctly on the right thread.
         */
        ObservableList<ToolRowViewModel> rows = viewModel.rows();
        ToolRowViewModel row = rows.get(0);
        ArrivedOn arrived = new ArrivedOn();
        FxToolkit.onFxThread(
                () -> {
                    viewModel.install(row);
                    /*
                     * Registered AFTER the install has started, because install() reads the port
                     * and replaces the list itself.  What this listener must see is the rebuild the
                     * TERMINAL report causes, on whichever thread that report was delivered to.
                     */
                    rows.addListener(
                            (ListChangeListener<ToolRowViewModel>) change -> arrived.record());
                });

        reportFromAnInstallThread(manager, InstallPhase.DONE, 2_798_963L);

        arrived.awaitArrival("row-list rebuild");
        arrived.assertOnInterfaceThread("the pane's row list");
        /*
         * Not decoration: a use of the view after the wait is what keeps it strongly reachable
         * for the whole wait, which a local variable the compiler can see is dead would not.
         */
        assertEquals(
                List.of(row),
                FxToolkit.callOnFxThread(() -> List.copyOf(rows)),
                "the terminal report rebuilt the list, and the row it rebuilt it with must be the"
                        + " same row, kept by key");
    }

    /** Puts the view-model on screen, so that a live scene is watching everything it publishes. */
    private static void showInALiveScene(ToolManagerViewModel viewModel)
            throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    Scene scene = new Scene(new ToolManagerPane(viewModel), 1024, 768);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    viewModel.refresh();
                });
    }

    /**
     * Raises one report on a real background thread named as production names them.
     *
     * @param manager the port the install was started on
     * @param phase the phase to report
     * @param bytes how many bytes to say have moved
     * @throws InterruptedException if the wait for that thread is interrupted
     */
    private static void reportFromAnInstallThread(
            ScriptedToolManager manager, InstallPhase phase, long bytes)
            throws InterruptedException {
        Thread installThread =
                new Thread(
                        () ->
                                manager.report(
                                        new InstallProgress(
                                                ToolName.PERCOLATOR,
                                                ToolVersion.parse("3.07.1"),
                                                phase,
                                                bytes,
                                                2_798_963L)),
                        INSTALL_THREAD);
        installThread.setDaemon(true);
        installThread.start();
        installThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertTrue(
                !installThread.isAlive(),
                "the install thread never finished raising its report, so nothing was delivered");
    }
}
