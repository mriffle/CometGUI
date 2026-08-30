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

package org.cometgui.ui.testing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;

/**
 * Starting the headless JavaFX toolkit, and running work on its application thread.
 *
 * <p>The recipe is {@code HeadlessSceneTest}'s, which phase 01 proved on this machine and
 * cometgui-ui/pom.xml configures: Monocle patched into {@code javafx.graphics}, the project-local
 * font stack on {@code LD_LIBRARY_PATH} and {@code FONTCONFIG_PATH}. It is collected here because
 * three view test classes need it and three copies of a sixty-second timeout would eventually
 * become three different timeouts.
 *
 * <p>Surefire runs one JVM per test class ({@code reuseForks=false} in cometgui-ui/pom.xml), so
 * {@link #start()} starts the toolkit at most once per test class. It is idempotent anyway, because
 * a second {@code Platform.startup} throws.
 */
public final class FxToolkit {

    /** How long to wait for the toolkit and for work submitted to the application thread. */
    public static final long TIMEOUT_SECONDS = 60;

    /**
     * The lock {@link #start()} holds. A private object rather than the class's own monitor: a
     * static {@code synchronized} method publishes its lock to anything that can name the class,
     * and SpotBugs reports that at {@code threshold=Low}.
     */
    private static final Object LOCK = new Object();

    private static boolean started;

    private FxToolkit() {}

    /**
     * Starts the toolkit if it is not already running, failing with an actionable message if the
     * project-local font stack is missing.
     *
     * @throws InterruptedException if interrupted while waiting for the toolkit
     */
    public static void start() throws InterruptedException {
        synchronized (LOCK) {
            if (started) {
                return;
            }
            assertFontStackIsPresent();
            Platform.setImplicitExit(false);
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(ready::countDown);
            assertTrue(
                    ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "the JavaFX toolkit did not start within "
                            + TIMEOUT_SECONDS
                            + "s. Check the patch-module Monocle argument in cometgui-ui/pom.xml.");
            started = true;
        }
    }

    /**
     * Runs work on the JavaFX application thread and waits for it, rethrowing whatever it threw.
     *
     * @param work what to run
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if the work threw, with the cause attached
     */
    public static void onFxThread(Runnable work) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        work.run();
                    } catch (RuntimeException | Error thrown) {
                        failure.set(thrown);
                    } finally {
                        done.countDown();
                    }
                });
        assertTrue(
                done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the JavaFX application thread did not finish the work within "
                        + TIMEOUT_SECONDS
                        + "s");
        Throwable thrown = failure.get();
        if (thrown != null) {
            throw new IllegalStateException("work on the FX application thread failed", thrown);
        }
    }

    /**
     * Computes a value on the JavaFX application thread and returns it.
     *
     * @param <T> the value's type
     * @param work what to compute
     * @return what {@code work} returned
     * @throws InterruptedException if interrupted while waiting
     */
    public static <T> T callOnFxThread(Supplier<T> work) throws InterruptedException {
        AtomicReference<T> value = new AtomicReference<>();
        onFxThread(() -> value.set(work.get()));
        return value.get();
    }

    /**
     * Waits until everything already queued on the application thread has run.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public static void drainFxQueue() throws InterruptedException {
        onFxThread(() -> {});
    }

    /**
     * Fails with the command that fixes it, rather than letting the toolkit die inside CSS
     * initialisation with "fontFactory is null".
     */
    private static void assertFontStackIsPresent() {
        String root = System.getProperty("cometgui.fontstackRoot");
        assertTrue(
                root != null && !root.isBlank(),
                "cometgui.fontstackRoot is not set; surefire in cometgui-ui/pom.xml must pass it");
        Path font = Path.of(root).resolve("usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        assertTrue(
                Files.isReadable(font),
                () ->
                        "the project-local font stack is missing ("
                                + font
                                + "). Run: bash scripts/fetch-fontstack.sh");
    }
}
