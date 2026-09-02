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

package org.cometgui.app.uidriver;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.cometgui.app.bootstrap.CometGuiLauncher;

/**
 * The application a GUI test drives: started for real, headless, and handed to a driver.
 *
 * <h2>Started through the product's own {@code main}</h2>
 *
 * <p>{@link #launchedByMain()} runs {@link CometGuiLauncher#main(String[])} on a background thread
 * -- the same {@code public static void main} phase 16's {@code jpackage} configuration will name
 * -- and then waits for a real primary {@link Stage} to appear. Nothing is hand-built: no {@code
 * ShellView} is constructed by a test, no scene is assembled, and the composition root is the real
 * one, so what the drivers click on is what a user would click on.
 *
 * <p><strong>Waiting, not sleeping.</strong> {@code Application.launch} blocks its own thread until
 * the application exits, and until the toolkit has started there is no queue to post a task to --
 * {@code Platform.runLater} throws {@code IllegalStateException}. So the wait polls two observable
 * facts in order: whether the toolkit accepts work at all, and whether a showing stage exists. It
 * never asserts that startup took less than some time; the deadline exists so that a hung toolkit
 * fails with a diagnostic instead of hanging the build, and if it is ever hit the message says
 * which of the two facts was missing. This mirrors {@code CometGuiApplicationStartupTest}, which
 * landed with unit 7 and is the pattern this project already accepted for "the application starts".
 *
 * <h2>Or attached to an application a test started itself</h2>
 *
 * <p>{@link #showing(Stage)} wraps a stage the test already has. That is what the console flood
 * test needs: {@code R-PROC-03} is about a bounded buffer that a producer floods, and to flood the
 * one the application is showing a test has to be the thing that supplied it -- which {@code
 * CometGuiApplication(ApplicationServices, BoundedMessageLog)} exists for. The application is still
 * the real one and its {@code start} method is still what builds the window.
 *
 * <h2>One per JVM</h2>
 *
 * <p>{@code Application.launch} may be called at most once in a JVM's lifetime, and the surefire
 * configuration in cometgui-app/pom.xml runs one JVM per test class ({@code reuseForks=false}), so
 * each GUI test class starts its own application and none can see another's leftovers. That is also
 * what lets a test say "from a fresh application" and mean it.
 */
public final class RunningApplication {

    /** How long to wait for the window to appear before failing with a diagnostic. */
    public static final long STARTUP_TIMEOUT_SECONDS = 120;

    /** How long to wait for one round trip to the application thread. */
    private static final long FX_TIMEOUT_SECONDS = 30;

    /** How often to ask whether the toolkit is up yet, in milliseconds. */
    private static final long POLL_MILLIS = 25;

    private final Stage stage;

    private final Scene scene;

    private RunningApplication(Stage stage, Scene scene) {
        this.stage = stage;
        this.scene = scene;
    }

    /**
     * Starts the real application through {@link CometGuiLauncher#main(String[])} and waits for its
     * window.
     *
     * @return the running application, with a stage that is showing
     * @throws AssertionError if no window appears within {@link #STARTUP_TIMEOUT_SECONDS}, or if
     *     the launcher thread died on the way
     */
    public static RunningApplication launchedByMain() {
        AtomicReference<Throwable> launchFailure = new AtomicReference<>();
        Thread launcher =
                new Thread(
                        () -> CometGuiLauncher.main(new String[0]), "cometgui-gui-test-launcher");
        launcher.setDaemon(true);
        launcher.setUncaughtExceptionHandler((thread, thrown) -> launchFailure.set(thrown));
        launcher.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STARTUP_TIMEOUT_SECONDS);
        boolean toolkitAnswered = false;
        while (System.nanoTime() < deadline) {
            failIfTheLauncherDied(launchFailure);
            Optional<Stage> showing = findShowingStage();
            if (showing.isPresent()) {
                return showing(showing.orElseThrow());
            }
            toolkitAnswered |= toolkitIsUp();
            pause();
        }
        failIfTheLauncherDied(launchFailure);
        return fail(
                "the application did not show a window within "
                        + STARTUP_TIMEOUT_SECONDS
                        + "s of CometGuiLauncher.main being called. The JavaFX toolkit "
                        + (toolkitAnswered ? "did start" : "never started")
                        + ". Check the patch-module Monocle argument and the font stack in"
                        + " cometgui-app/pom.xml.");
    }

    /**
     * Wraps a stage a test has already shown, reading its scene once on the application thread.
     *
     * @param stage the stage the application is showing
     * @return the running application
     * @throws NullPointerException if {@code stage} is {@code null}
     * @throws AssertionError if the stage has no scene, or is not showing
     */
    public static RunningApplication showing(Stage stage) {
        Objects.requireNonNull(stage, "stage");
        Scene scene =
                onFxThread(
                        () -> {
                            assertTrue(stage.isShowing(), "the stage handed over is not showing");
                            Scene shown = stage.getScene();
                            assertNotNull(shown, "the stage handed over has no scene");
                            return shown;
                        });
        return new RunningApplication(stage, scene);
    }

    /**
     * The window's title, read on the application thread.
     *
     * @return what the title bar says
     */
    public String title() {
        return onFxThread(stage::getTitle);
    }

    /**
     * Whether the window is on screen, read on the application thread.
     *
     * @return {@code true} while the stage is showing
     */
    public boolean isShowing() {
        return onFxThread(stage::isShowing);
    }

    /**
     * The scene, captured once when the application was found.
     *
     * <p>Package-private deliberately: the drivers in this package need it, and a public accessor
     * handing out the field is what SpotBugs reports as {@code EI_EXPOSE_REP} at {@code
     * threshold=Low}. A test outside this package asks a driver a question instead of taking the
     * scene apart itself, which is the better shape anyway. Fixed in the code rather than by adding
     * an exclusion.
     *
     * @return the scene the window is showing
     */
    Scene scene() {
        return scene;
    }

    /** Ends the application, so that a JVM shared by nothing still exits promptly. */
    public void stop() {
        Platform.exit();
    }

    private static void failIfTheLauncherDied(AtomicReference<Throwable> launchFailure) {
        Throwable thrown = launchFailure.get();
        if (thrown != null) {
            throw new IllegalStateException("CometGuiLauncher.main failed", thrown);
        }
    }

    /**
     * Whether the toolkit accepts work yet.
     *
     * @return {@code true} once {@code Platform.runLater} no longer refuses
     */
    private static boolean toolkitIsUp() {
        try {
            Platform.runLater(() -> {});
            return true;
        } catch (IllegalStateException toolkitNotStartedYet) {
            return false;
        }
    }

    /**
     * The first showing stage, or empty while there is none.
     *
     * @return the primary stage once the application has shown it
     */
    private static Optional<Stage> findShowingStage() {
        AtomicReference<Stage> found = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        try {
            Platform.runLater(
                    () -> {
                        for (Window window : Window.getWindows()) {
                            if (window instanceof Stage candidate && candidate.isShowing()) {
                                found.compareAndSet(null, candidate);
                            }
                        }
                        done.countDown();
                    });
        } catch (IllegalStateException toolkitNotStartedYet) {
            return Optional.empty();
        }
        return await(done) ? Optional.ofNullable(found.get()) : Optional.empty();
    }

    /**
     * Computes a value on the application thread, for the two places this class needs one before a
     * driver exists.
     *
     * @param <T> the value's type
     * @param work what to compute
     * @return what it returned
     */
    private static <T> T onFxThread(Supplier<T> work) {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(
                () -> {
                    try {
                        value.set(work.get());
                    } catch (RuntimeException | Error thrown) {
                        failure.set(thrown);
                    } finally {
                        done.countDown();
                    }
                });
        if (!await(done)) {
            fail(
                    "the JavaFX application thread did not respond within "
                            + FX_TIMEOUT_SECONDS
                            + "s");
        }
        Throwable thrown = failure.get();
        if (thrown != null) {
            throw new IllegalStateException("work on the FX application thread failed", thrown);
        }
        return value.get();
    }

    private static boolean await(CountDownLatch done) {
        try {
            return done.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for the FX thread", interrupted);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for the application", interrupted);
        }
    }
}
