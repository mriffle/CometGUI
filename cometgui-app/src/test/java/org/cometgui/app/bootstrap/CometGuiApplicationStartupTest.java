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

package org.cometgui.app.bootstrap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.view.ShellView;
import org.cometgui.ui.viewmodel.SectionId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The startup smoke test: the real application, started the way a user starts it.
 *
 * <p>This is phase 02 exit-gate item 1's "the application starts". It does not build a {@link
 * ShellView} and look at it -- {@code ShellViewTest} in cometgui-ui already does that. It calls
 * {@link CometGuiLauncher#main(String[])}, the same {@code public static void main} phase 16's
 * {@code jpackage} will name, on a background thread, and then waits for a real primary {@link
 * Stage} to appear in {@link Window#getWindows()}. Everything asserted afterwards is read off that
 * stage: nothing is passed in, and nothing about the wiring is faked.
 *
 * <p>Headless, through the Monocle recipe cometgui-app/pom.xml configures. Surefire runs one JVM
 * per test class, which matters here twice: {@link Application#launch} may be called at most once
 * in a JVM's lifetime, and the toolkit this test starts must not leak into another test class's.
 *
 * <p>The window this test finds is created by the real composition root, so the host-baseline
 * banner reflects this actual machine -- 64-bit Linux with glibc 2.36, which is supported, so the
 * banner must be present in the scene and hidden. The blocking and warning cases are {@code
 * CometGuiApplicationBaselineTest}'s, where the host can be chosen.
 */
class CometGuiApplicationStartupTest {

    private static final long TIMEOUT_SECONDS = 90;

    private static final AtomicReference<Throwable> LAUNCH_FAILURE = new AtomicReference<>();

    private static Stage stage;

    @BeforeAll
    @DisplayName("the real application starts through its own main method")
    static void launchTheApplication() throws InterruptedException {
        Thread launcher =
                new Thread(() -> CometGuiLauncher.main(new String[0]), "cometgui-launcher");
        launcher.setDaemon(true);
        launcher.setUncaughtExceptionHandler((thread, thrown) -> LAUNCH_FAILURE.set(thrown));
        launcher.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            failIfTheLauncherDied();
            Optional<Stage> showing = findShowingStage();
            if (showing.isPresent()) {
                stage = showing.orElseThrow();
                return;
            }
            Thread.sleep(100);
        }
        failIfTheLauncherDied();
        fail(
                "no JavaFX stage was showing "
                        + TIMEOUT_SECONDS
                        + "s after CometGuiLauncher.main was called. Check the Monocle"
                        + " patch-module argument and the font stack in cometgui-app/pom.xml.");
    }

    @AfterAll
    static void stopTheApplication() {
        if (stage != null) {
            Platform.exit();
        }
    }

    private static void failIfTheLauncherDied() {
        Throwable thrown = LAUNCH_FAILURE.get();
        if (thrown != null) {
            throw new IllegalStateException("CometGuiLauncher.main failed", thrown);
        }
    }

    /**
     * The first showing stage, or empty while the toolkit has not started yet.
     *
     * @return the primary stage once the application has shown it
     * @throws InterruptedException if interrupted while waiting for the FX thread
     */
    private static Optional<Stage> findShowingStage() throws InterruptedException {
        AtomicReference<Stage> found = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        try {
            Platform.runLater(
                    () -> {
                        for (Window window : Window.getWindows()) {
                            if (window instanceof Stage candidate && candidate.isShowing()) {
                                found.set(candidate);
                            }
                        }
                        done.countDown();
                    });
        } catch (IllegalStateException toolkitNotStartedYet) {
            return Optional.empty();
        }
        if (!done.await(5, TimeUnit.SECONDS)) {
            return Optional.empty();
        }
        return Optional.ofNullable(found.get());
    }

    private static <T> T onFxThread(java.util.function.Supplier<T> work)
            throws InterruptedException {
        AtomicReference<T> value = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(
                () -> {
                    try {
                        value.set(work.get());
                    } finally {
                        done.countDown();
                    }
                });
        assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the FX thread did not respond");
        return value.get();
    }

    @Test
    @DisplayName("a primary stage is showing, titled CometGUI")
    void theWindowIsShowing() throws InterruptedException {
        assertAll(
                () -> assertTrue(onFxThread(stage::isShowing), "the stage is not showing"),
                () -> assertEquals("CometGUI", onFxThread(stage::getTitle)),
                () ->
                        assertEquals(
                                CometGuiApplication.WINDOW_TITLE,
                                onFxThread(stage::getTitle),
                                "the title must be the constant later phases identify it by"));
    }

    @Test
    @DisplayName("its scene's root is the shell, by the shell's own stable identifier")
    void theSceneRootIsTheShell() throws InterruptedException {
        Scene scene = onFxThread(stage::getScene);
        assertNotNull(scene, "the stage has no scene");
        Parent root = onFxThread(scene::getRoot);

        assertAll(
                () -> assertEquals("shell-root", root.getId()),
                () ->
                        assertEquals(
                                UiIds.SHELL_ROOT,
                                root.getId(),
                                "the id must be the one UiIds publishes"),
                () -> assertInstanceOf(ShellView.class, root),
                () -> assertEquals(1280.0, scene.getWidth(), 0.5),
                () -> assertEquals(800.0, scene.getHeight(), 0.5));
    }

    @Test
    @DisplayName("the AtlantaFX Primer Light stylesheet is the user agent stylesheet")
    void theThemeIsApplied() throws InterruptedException {
        assertEquals(
                "/atlantafx/base/theme/primer-light.css",
                onFxThread(Application::getUserAgentStylesheet));
    }

    @Test
    @DisplayName("every section is in the scene, found by its stable identifier")
    void everySectionIsReachableInTheScene() throws InterruptedException {
        Scene scene = onFxThread(stage::getScene);

        for (SectionId section : SectionId.displayOrder()) {
            Node pane = onFxThread(() -> scene.lookup("#" + UiIds.sectionPane(section)));
            Node entry = onFxThread(() -> scene.lookup("#" + UiIds.navigationEntry(section)));
            assertAll(
                    section.id(),
                    () -> assertNotNull(pane, "no pane with id " + UiIds.sectionPane(section)),
                    () ->
                            assertNotNull(
                                    entry, "no entry with id " + UiIds.navigationEntry(section)));
        }
    }

    @Test
    @DisplayName("this host is supported, so the banner slot exists and is hidden")
    void theBaselineBannerReflectsThisHost() throws InterruptedException {
        Scene scene = onFxThread(stage::getScene);
        Node banner = onFxThread(() -> scene.lookup("#" + UiIds.HOST_BASELINE_BANNER));

        assertNotNull(banner, "the host-baseline banner slot is missing from the shell");
        assertAll(
                () ->
                        assertFalse(
                                banner.isVisible(),
                                "this host is 64-bit Linux with glibc 2.36, which meets the"
                                        + " startup baseline, so no banner may be shown -- it said:"
                                        + " "
                                        + ((Label) banner).getText()),
                () ->
                        assertTrue(
                                ((Label) banner)
                                        .getText()
                                        .startsWith("Host baseline satisfied: Host baseline OK:"),
                                "the hidden slot still carries the report: "
                                        + ((Label) banner).getText()));
    }
}
