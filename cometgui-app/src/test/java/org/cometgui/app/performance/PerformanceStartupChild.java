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

package org.cometgui.app.performance;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.cometgui.app.bootstrap.CometGuiLauncher;

/**
 * The application started in a fresh JVM for performance (a), the way a user starts it: {@link
 * CometGuiLauncher#main} on its own thread. The JavaFX application thread is asked, every two
 * milliseconds, whether a stage is showing whose scene's root is the shell; the first time one is,
 * this prints {@code FIRST-WINDOW} with the stage's title, the root's identifier and the scene's
 * size -- printed from the application thread itself, so the window is shown <em>and</em> the
 * thread that serves the user is free -- and halts the JVM. {@link PerformanceStartup} times the
 * whole of it, JVM start included, from outside.
 *
 * <p>If no such window shows within two minutes it prints {@code NO-WINDOW} and halts with status
 * 3; if the launcher throws, {@code LAUNCH-FAILED} and status 4.
 */
public final class PerformanceStartupChild {

    private static final long BOUND_NANOS = TimeUnit.MINUTES.toNanos(2);

    private static final AtomicReference<String> FOUND = new AtomicReference<>();

    private PerformanceStartupChild() {}

    /**
     * Starts the application and reports its first usable window.
     *
     * @param args none
     * @throws InterruptedException if a wait is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        Thread launcher =
                new Thread(() -> CometGuiLauncher.main(new String[0]), "cometgui-launcher");
        launcher.setDaemon(true);
        launcher.setUncaughtExceptionHandler(
                (thread, thrown) -> {
                    System.out.println("LAUNCH-FAILED " + thrown);
                    System.out.flush();
                    Runtime.getRuntime().halt(4);
                });
        launcher.start();
        long deadline = System.nanoTime() + BOUND_NANOS;
        while (System.nanoTime() - deadline < 0) {
            if (askOnce()) {
                System.out.flush();
                Runtime.getRuntime().halt(0);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
        }
        System.out.println("NO-WINDOW within two minutes");
        System.out.flush();
        Runtime.getRuntime().halt(3);
    }

    /** One question to the application thread; true once the line has been printed. */
    private static boolean askOnce() throws InterruptedException {
        CountDownLatch answered = new CountDownLatch(1);
        try {
            Platform.runLater(
                    () -> {
                        try {
                            for (Window window : Window.getWindows()) {
                                Scene scene = window.getScene();
                                if (window instanceof Stage stage
                                        && stage.isShowing()
                                        && scene != null
                                        && scene.getRoot() != null
                                        && "shell-root".equals(scene.getRoot().getId())
                                        && FOUND.get() == null) {
                                    String line =
                                            String.format(
                                                    Locale.ROOT,
                                                    "FIRST-WINDOW title=%s root=%s size=%.0fx%.0f",
                                                    stage.getTitle(),
                                                    scene.getRoot().getId(),
                                                    scene.getWidth(),
                                                    scene.getHeight());
                                    System.out.println(line);
                                    System.out.flush();
                                    FOUND.set(line);
                                }
                            }
                        } finally {
                            answered.countDown();
                        }
                    });
        } catch (IllegalStateException toolkitNotStartedYet) {
            return false;
        }
        return answered.await(BOUND_NANOS, TimeUnit.NANOSECONDS) && FOUND.get() != null;
    }
}
