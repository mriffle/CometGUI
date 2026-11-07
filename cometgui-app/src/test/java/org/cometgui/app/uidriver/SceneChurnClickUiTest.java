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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;

/**
 * The TestFX driver never reads the scene graph off the JavaFX application thread (Phase 15 unit 9,
 * the flakiness policy).
 *
 * <p><strong>The defect this pins.</strong> {@code ResultsCountsUiTest.theSyntheticRun} once erred
 * with {@code IndexOutOfBoundsException: Index 0 out of bounds for length 0} inside {@code
 * Parent.lookupAll}, called from TestFX's {@code FxRobot.clickOn("#id")} on the test thread: TestFX
 * resolves a selector by walking every {@code Parent}'s child list from the calling thread, and the
 * application thread emptied one of those lists between the walk's size check and its {@code
 * get(0)}. Phase 07's intermittent {@code bounds are not visible in Scene} is the same shape:
 * TestFX reads the node's scene bounds from the test thread too, while the application thread may
 * be laying it out. Any application that changes its scene graph asynchronously -- a table filled
 * by a background query, a section built on arrival -- is a window for both.
 *
 * <p><strong>How it is reproduced on purpose.</strong> The scene holds a pane with a long child
 * list whose last child the application thread removes and re-adds without pause for the whole
 * test: the window the defect needs is open all the time instead of for a few milliseconds of a
 * real application's life. A click on an unrelated button, a hundred times, must arrive a hundred
 * times. With the selector walk on the test thread this fails on the first few clicks with the same
 * exception from the same frame as the original.
 *
 * <p>Every assertion is a count of clicks the button received, or the words of a refusal: nothing
 * here asserts that anything was fast.
 */
class SceneChurnClickUiTest {

    /** How many clicks the churn must not disturb. */
    private static final int CLICKS = 100;

    /**
     * How many children the churning pane holds besides the one that comes and goes: the walk over
     * its child list must take long enough for the last child to be removed during it.
     */
    private static final int STEADY_CHILDREN = 200;

    /**
     * How many children each of those holds: a walk visits every one, so they lengthen the time the
     * churning list is being walked without making the change to it any more expensive.
     */
    private static final int GRANDCHILDREN = 100;

    private static final String TARGET = "churn-target";

    private static final String OUTSIDE = "churn-outside";

    private static final String JUMPER = "churn-jumper";

    private static final AtomicInteger CLICKED = new AtomicInteger();

    private static final AtomicInteger JUMPER_CLICKED = new AtomicInteger();

    private static final AtomicBoolean JUMPING = new AtomicBoolean();

    private static final AtomicInteger JUMPS = new AtomicInteger();

    private static VBox jumping;

    private static final AtomicBoolean CHURNING = new AtomicBoolean();

    private static final AtomicInteger CHURNS = new AtomicInteger();

    private static Pane churn;

    private static Region flicker;

    private static FxUiDriver driver;

    @BeforeAll
    static void showAChurningScene() throws TimeoutException {
        Stage primary = FxToolkit.registerPrimaryStage();
        FxToolkit.setupStage(
                stage -> {
                    List<Node> steady = new ArrayList<>();
                    for (int i = 0; i < STEADY_CHILDREN; i++) {
                        Pane child = new Pane();
                        for (int j = 0; j < GRANDCHILDREN; j++) {
                            child.getChildren().add(new Region());
                        }
                        steady.add(child);
                    }
                    flicker = new Region();
                    churn = new Pane();
                    churn.getChildren().setAll(steady);
                    churn.setMouseTransparent(true);
                    churn.setMinSize(100, 40);
                    churn.setPrefSize(100, 40);
                    churn.setMaxSize(100, 40);
                    Button target = new Button("Click me");
                    target.setId(TARGET);
                    target.setOnAction(event -> CLICKED.incrementAndGet());
                    Button outside = new Button("Out of sight");
                    outside.setId(OUTSIDE);
                    outside.setManaged(false);
                    outside.relocate(10_000, 10_000);
                    Button jumper = new Button("Jump");
                    jumper.setId(JUMPER);
                    jumper.setOnAction(event -> JUMPER_CLICKED.incrementAndGet());
                    jumping = new VBox(jumper);
                    stage.setScene(new Scene(new VBox(churn, target, outside, jumping), 400, 300));
                });
        FxToolkit.showStage();
        driver = new TestFxUiDriver(RunningApplication.showing(primary));
    }

    @AfterAll
    static void stopChurning() {
        CHURNING.set(false);
        JUMPING.set(false);
    }

    @Test
    @DisplayName(
            "a hundred clicks on a button all arrive while the application thread removes and"
                    + " re-adds a node elsewhere in the scene without pause")
    void clicksArriveWhileTheSceneChanges() {
        CHURNING.set(true);
        Platform.runLater(SceneChurnClickUiTest::churnOnce);
        try {
            for (int click = 1; click <= CLICKS; click++) {
                driver.clickOn(TARGET);
                assertEquals(click, CLICKED.get(), "clicks the button received");
            }
        } finally {
            CHURNING.set(false);
            driver.onFxThread(() -> {});
        }
        assertTrue(
                CHURNS.get() > CLICKS,
                "the scene must have changed many times while the clicks were made, or nothing was"
                        + " exercised; it changed "
                        + CHURNS.get()
                        + " times");
    }

    @Test
    @DisplayName(
            "a hundred clicks on a button all arrive while the application thread, in each of its"
                    + " tasks, moves the button out of its window and back")
    void clicksArriveWhileTheNodeMovesWithinOneTask() {
        /*
         * Phase 07's "bounds are not visible in Scene", reproduced on purpose.  A layout pass moves
         * nodes in several steps inside one task on the application thread; anything reading the
         * scene on that thread sees only where each task left it, here always in the window.  A
         * reader on another thread can see a node between two steps -- here, out of the window --
         * and TestFX's selector click read the node's bounds from the test thread.
         */
        JUMPING.set(true);
        Platform.runLater(SceneChurnClickUiTest::jumpOnce);
        try {
            for (int click = 1; click <= CLICKS; click++) {
                driver.clickOn(JUMPER);
                assertEquals(click, JUMPER_CLICKED.get(), "clicks the jumping button received");
            }
        } finally {
            JUMPING.set(false);
            driver.onFxThread(() -> {});
        }
        assertTrue(
                JUMPS.get() > CLICKS,
                "the button must have jumped many times while the clicks were made, or nothing was"
                        + " exercised; it jumped "
                        + JUMPS.get()
                        + " times");
    }

    @Test
    @DisplayName(
            "a node lying wholly outside its window is refused by name rather than clicked where"
                    + " it is not")
    void aNodeOutsideItsWindowIsRefused() {
        int before = CLICKED.get();
        AssertionError refused = assertThrows(AssertionError.class, () -> driver.clickOn(OUTSIDE));
        assertTrue(
                refused.getMessage().contains("#" + OUTSIDE)
                        && refused.getMessage().contains("not visible in its window"),
                "the refusal names the node and why: " + refused.getMessage());
        assertEquals(before, CLICKED.get(), "nothing else was clicked instead");
    }

    /**
     * Removes the churning pane's last child if it is there, adds it back if it is not, and queues
     * itself again: a walk that read the list's length with that child present and reaches the end
     * after it has gone asks for an index the list no longer has.
     */
    private static void churnOnce() {
        if (!CHURNING.get()) {
            return;
        }
        if (!churn.getChildren().remove(flicker)) {
            churn.getChildren().add(flicker);
        }
        CHURNS.incrementAndGet();
        Platform.runLater(SceneChurnClickUiTest::churnOnce);
    }

    /**
     * Moves the jumping button's box far out of the window and back in one task, and queues itself
     * again: between tasks the button is always where it was laid out.
     */
    private static void jumpOnce() {
        if (!JUMPING.get()) {
            return;
        }
        for (int step = 0; step < 50; step++) {
            jumping.setTranslateY(100_000);
            jumping.setTranslateY(0);
        }
        JUMPS.incrementAndGet();
        Platform.runLater(SceneChurnClickUiTest::jumpOnce);
    }
}
