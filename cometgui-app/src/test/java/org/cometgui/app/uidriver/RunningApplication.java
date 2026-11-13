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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;

/**
 * The application a GUI test drives: started for real, headless, and handed to a driver.
 *
 * <h2>Attached to an application the test started</h2>
 *
 * <p>{@link #showing(Stage)} wraps a stage that is already showing. A GUI test starts the real
 * {@code CometGuiApplication} -- its {@code start} method is what builds the window, over the real
 * composition root -- with the few things a test must own handed to it: the application-data
 * directory, so that no test reads or writes this machine's ({@code ParameterEditorApp.launchOver},
 * Phase 16 unit 4), the file chooser, and where a test needs it the console's log ({@code
 * R-PROC-03}: to flood the buffer the application is showing, a test has to be the thing that
 * supplied it). Nothing else is hand-built: no {@code ShellView} is constructed by a test and no
 * scene is assembled, so what the drivers click on is what a user would click on. Inside the
 * packaged application the end-to-end bridge wraps the stage the product's own {@code main} showed.
 *
 * <p>This class does not start the application through {@code CometGuiLauncher.main} itself: that
 * reads the machine's application-data directory. The two tests that must go through {@code main}
 * -- {@code CometGuiApplicationStartupTest}, which is the test of it, and the opt-in {@code
 * PerformanceStartupChild}, which times it -- do so themselves.
 *
 * <p><strong>Waiting, not sleeping.</strong> {@link #awaitToolListRead()} waits on an observable
 * fact through {@link Await}, the harness's one wait primitive (P14-8), so this class holds no
 * pause of its own. The deadline exists so that a hung application fails with a diagnostic instead
 * of hanging the build; nothing asserts that anything took less than some time.
 *
 * <h2>One per JVM</h2>
 *
 * <p>{@code Application.launch} may be called at most once in a JVM's lifetime, and the surefire
 * configuration in cometgui-app/pom.xml runs one JVM per test class ({@code reuseForks=false}), so
 * each GUI test class starts its own application and none can see another's leftovers. That is also
 * what lets a test say "from a fresh application" and mean it.
 */
public final class RunningApplication {

    /** How long to wait for a starting application before failing with a diagnostic. */
    public static final long STARTUP_TIMEOUT_SECONDS = 120;

    /** How long to wait for one round trip to the application thread. */
    private static final long FX_TIMEOUT_SECONDS = 30;

    /** How often to look again, through {@link Await}: the class's one poll interval. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(25);

    private final Stage stage;

    private final Scene scene;

    private RunningApplication(Stage stage, Scene scene) {
        this.stage = stage;
        this.scene = scene;
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

    /**
     * Waits until the Tool Manager section has read its tool list, and returns what its summary
     * then says.
     *
     * <p>The application asks for the list as it starts, but the list is read off the interface
     * thread -- reading it starts every installed tool -- and arrives a moment after the window
     * does. A test that walks or reads the Tool Manager's rows waits for that first, or it walks a
     * section that has not been filled yet and proves less than it says.
     *
     * @return the summary line once it no longer says the list has not been read
     * @throws AssertionError if the summary is missing, or still says the list has not been read
     *     after {@link #STARTUP_TIMEOUT_SECONDS}
     */
    public String awaitToolListRead() {
        return Await.until(
                "the Tool Manager's summary to stop saying \""
                        + ToolManagerViewModel.NOT_READ_YET
                        + "\" after the window appeared (if it never does, the application never"
                        + " read its tool list)",
                this::toolManagerSummary,
                said -> !ToolManagerViewModel.NOT_READ_YET.equals(said),
                Duration.ofSeconds(STARTUP_TIMEOUT_SECONDS),
                POLL_INTERVAL);
    }

    private String toolManagerSummary() {
        return onFxThread(
                () -> {
                    Node summary = scene.lookup("#" + UiIds.TOOL_MANAGER_SUMMARY);
                    assertNotNull(summary, "the Tool Manager section has no summary line");
                    return ((Label) summary).getText();
                });
    }

    /** Ends the application, so that a JVM shared by nothing still exits promptly. */
    public void stop() {
        Platform.exit();
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
}
