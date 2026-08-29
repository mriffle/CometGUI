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

package org.cometgui.ui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Build-infrastructure proof: a JavaFX {@link Scene} containing real controls can be created,
 * styled and laid out inside {@code mvn verify} on this machine, with no display.
 *
 * <p><strong>This is not UI work.</strong> Phase 02 owns the application shell; this test exists so
 * that phase, and CI, do not discover on their first day that the build cannot run a JavaFX test at
 * all. Two things have to be true for it to pass, and neither is the default:
 *
 * <ul>
 *   <li>a Glass platform: the pinned Liberica JDK ships no Monocle and this host has no libX11, so
 *       {@code org.testfx:openjfx-monocle} is injected into {@code javafx.graphics} with the
 *       patch-module option (see cometgui-ui/pom.xml -- never on the class path, because
 *       javafx.graphics is a named system module and its platform lookup cannot see the unnamed
 *       one);
 *   <li>a font stack: the first {@code Node} in a {@code Scene} initialises CSS, which calls {@code
 *       Font.getDefault()}. With no freetype, no fontconfig and no font files that call fails with
 *       "fontFactory is null" and the Scene never gets built. The stack is fetched project-locally
 *       by scripts/fetch-fontstack.sh.
 * </ul>
 *
 * <p>The assertions are about measured text, not about "it did not throw": a Scene that reported
 * zero-width text would mean the font subsystem loaded nothing, which is exactly the failure the
 * font stack exists to prevent, and it would not be visible from an exception.
 */
class HeadlessSceneTest {

    /** How long to wait for the toolkit and for work submitted to the FX thread. */
    private static final long FX_TIMEOUT_SECONDS = 60;

    private static final String SHORT_TEXT = "Comet";

    /**
     * Long enough that no plausible font could lay it out in the same width as {@link #SHORT_TEXT}.
     */
    private static final String LONG_TEXT = "Comet to Percolator, with provenance";

    @BeforeAll
    @DisplayName("the JavaFX toolkit starts headless")
    static void startToolkit() throws InterruptedException {
        assertFontStackIsPresent();
        Platform.setImplicitExit(false);
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(
                started.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the JavaFX toolkit did not start within "
                        + FX_TIMEOUT_SECONDS
                        + "s. Check the patch-module Monocle argument in cometgui-ui/pom.xml.");
    }

    /**
     * Fails with an actionable message rather than letting the toolkit die deep inside CSS
     * initialisation with "fontFactory is null", which says nothing about what to do next.
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

    @Test
    @DisplayName("a Scene of real controls is laid out, and its text is measured with real fonts")
    void sceneWithControlsIsLaidOutAndMeasured() throws InterruptedException {
        Label statusLabel = new Label(SHORT_TEXT);
        statusLabel.setId("statusLabel");
        Label wideLabel = new Label(LONG_TEXT);
        wideLabel.setId("wideLabel");
        Button runButton = new Button("Run search");
        runButton.setId("runButton");
        VBox root = new VBox(statusLabel, wideLabel, runButton);
        AtomicReference<Scene> built = new AtomicReference<>();

        onFxThread(
                () -> {
                    Scene scene = new Scene(root, 640, 480);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    built.set(scene);
                });

        Scene scene = built.get();
        assertAll(
                () ->
                        assertEquals(
                                3,
                                scene.getRoot().getChildrenUnmodifiable().size(),
                                "the Scene must hold the three controls it was built from"),
                () ->
                        assertSame(
                                runButton,
                                scene.lookup("#runButton"),
                                "a control must be findable by its stable identifier (R-TEST-04)"),
                () ->
                        assertEquals(
                                "Run search",
                                ((Button) scene.lookup("#runButton")).getText(),
                                "the looked-up control must be the button that was added"),
                () ->
                        assertTrue(
                                statusLabel.getWidth() > 0,
                                () ->
                                        "the label laid out to width "
                                                + statusLabel.getWidth()
                                                + "; a zero width means the font subsystem"
                                                + " measured nothing"),
                () ->
                        assertTrue(
                                wideLabel.getWidth() > statusLabel.getWidth(),
                                () ->
                                        "\""
                                                + LONG_TEXT
                                                + "\" laid out to "
                                                + wideLabel.getWidth()
                                                + " and \""
                                                + SHORT_TEXT
                                                + "\" to "
                                                + statusLabel.getWidth()
                                                + "; real text measurement makes the longer string"
                                                + " wider"),
                () ->
                        assertTrue(
                                runButton.getHeight() > 0,
                                "a control with no height was never laid out"),
                () ->
                        assertTrue(
                                Font.getDefault().getSize() > 0,
                                "the default font has no size, so no font file was loaded"),
                () ->
                        assertTrue(
                                System.getProperty("javafx.runtime.version", "").startsWith("25"),
                                () ->
                                        "JavaFX must come from the pinned JDK image, but"
                                                + " javafx.runtime.version is "
                                                + System.getProperty("javafx.runtime.version")));
    }

    /** Runs the given work on the JavaFX application thread and rethrows whatever it threw. */
    private static void onFxThread(Runnable work) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        work.run();
                    } catch (RuntimeException | Error e) {
                        failure.set(e);
                    } finally {
                        done.countDown();
                    }
                });
        assertTrue(
                done.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the JavaFX application thread did not finish the work within "
                        + FX_TIMEOUT_SECONDS
                        + "s");
        Throwable thrown = failure.get();
        if (thrown != null) {
            throw new IllegalStateException("building the Scene failed on the FX thread", thrown);
        }
    }
}
