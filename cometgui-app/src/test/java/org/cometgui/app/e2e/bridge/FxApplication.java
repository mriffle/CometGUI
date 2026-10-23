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

package org.cometgui.app.e2e.bridge;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.cometgui.app.uidriver.Await;
import org.cometgui.app.uidriver.RemotableUiDriver;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;

/**
 * The packaged application as the bridge sees it: its first showing stage, wrapped by {@link
 * RunningApplication#showing} and driven by a {@link RobotFxUiDriver} -- the one driver (P14-3),
 * the same class Tier A's robot runs. Nothing here builds, starts or configures the application:
 * the unmodified launcher's {@code main} does that, and this only waits for its window.
 *
 * <p><strong>Waiting, not sleeping.</strong> Two observable facts, through {@link Await}: that the
 * JavaFX application thread exists (read from the thread list, without touching JavaFX, so that the
 * bridge cannot disturb the toolkit's own start), and then that a stage is showing (read on that
 * thread).
 */
final class FxApplication implements AttachedApplication {

    /** The JavaFX application thread's name, which the toolkit gives it. */
    private static final String FX_THREAD = "JavaFX Application Thread";

    /** How long one look at the window list may take on the application thread. */
    private static final long FX_ROUND_TRIP_SECONDS = 30;

    private RunningApplication application;

    private RobotFxUiDriver driver;

    @Override
    public synchronized String await(Duration bound) {
        if (application == null) {
            Await.until(
                    "the packaged application's JavaFX application thread to start",
                    FxApplication::fxThreadExists,
                    Boolean::booleanValue,
                    bound);
            Stage stage =
                    Await.until(
                                    "the packaged application to show a window",
                                    FxApplication::showingStage,
                                    Optional::isPresent,
                                    bound)
                            .orElseThrow();
            application = RunningApplication.showing(stage);
            driver = new RobotFxUiDriver(application);
        }
        return application.title();
    }

    @Override
    public synchronized RemotableUiDriver driver() {
        if (driver == null) {
            throw new IllegalStateException(
                    "the bridge is not attached to the application yet: call"
                            + " bridge.awaitApplication first");
        }
        return driver;
    }

    private static boolean fxThreadExists() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (FX_THREAD.equals(thread.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first showing stage, read on the application thread.
     *
     * @return the stage, or empty while none is showing or the toolkit does not accept work yet
     */
    private static Optional<Stage> showingStage() {
        AtomicReference<Stage> found = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        try {
            Platform.runLater(
                    () -> {
                        try {
                            for (Window window : Window.getWindows()) {
                                if (window instanceof Stage stage && stage.isShowing()) {
                                    found.compareAndSet(null, stage);
                                }
                            }
                        } finally {
                            done.countDown();
                        }
                    });
        } catch (IllegalStateException toolkitNotStartedYet) {
            return Optional.empty();
        }
        try {
            if (!done.await(FX_ROUND_TRIP_SECONDS, TimeUnit.SECONDS)) {
                return Optional.empty();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted reading the window list", interrupted);
        }
        return Optional.ofNullable(found.get());
    }
}
