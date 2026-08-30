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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;

/**
 * Everything both drivers do the same way: marshalling onto the JavaFX application thread, and
 * reading scene-graph state once there.
 *
 * <p>Only the four synthetic-input methods and the node lookup differ between {@link
 * TestFxUiDriver} and {@link RobotFxUiDriver}, which is the point: when the two drivers disagree
 * about what the application did, the difference is in the input mechanism and nowhere else.
 *
 * <p>Package-private, because it is an implementation detail of this package rather than something
 * a test should name.
 */
abstract class AbstractFxUiDriver implements FxUiDriver {

    /**
     * How long to wait for the application thread. Generous because a first CSS pass on a cold JVM
     * is slow; it is a deadlock detector, not a timing assumption, and no assertion depends on the
     * work taking less than any particular time.
     */
    static final long TIMEOUT_SECONDS = 60;

    private final RunningApplication application;

    /**
     * The base of a driver.
     *
     * <p>It deliberately does no argument checking: the two implementations are {@code final} and
     * check the argument before calling this, and a constructor that can throw in a class that is
     * neither final nor private is what SpotBugs reports as {@code CT_CONSTRUCTOR_THROW} at {@code
     * threshold=Low}. Fixed in the code rather than by adding an exclusion.
     *
     * @param application the application to drive, already checked for {@code null}
     */
    AbstractFxUiDriver(RunningApplication application) {
        this.application = application;
    }

    /** The application under test. */
    final RunningApplication application() {
        return application;
    }

    /**
     * The node with this identifier, or {@code null}. Called on the JavaFX application thread.
     *
     * @param id the stable identifier, without a leading {@code #}
     * @return the node, or {@code null} if the application has no node with that identifier
     */
    abstract Node lookup(String id);

    /**
     * Waits until everything already queued on the application thread has run.
     *
     * <p>This is how synthetic input is followed by an assertion without a sleep: the events the
     * robot generated are queued on the application thread ahead of this empty task, so when this
     * returns they have been delivered.
     */
    final void barrier() {
        onFxThread(() -> {});
    }

    @Override
    public final Node node(String id) {
        Node found = callOnFxThread(() -> lookup(id));
        if (found == null) {
            fail(
                    "no node with the stable identifier #"
                            + id
                            + " exists in the running application (driver: "
                            + this
                            + ")");
        }
        return found;
    }

    @Override
    public final boolean isVisible(String id) {
        return Boolean.TRUE.equals(
                callOnFxThread(
                        () -> {
                            Node found = lookup(id);
                            if (found == null || found.getScene() == null) {
                                return false;
                            }
                            for (Node above = found; above != null; above = above.getParent()) {
                                if (!above.isVisible()) {
                                    return false;
                                }
                            }
                            return true;
                        }));
    }

    @Override
    public final String focusedNodeId() {
        return callOnFxThread(
                () -> {
                    Scene scene = application.scene();
                    Node focused = scene.getFocusOwner();
                    return focused == null ? null : focused.getId();
                });
    }

    @Override
    public final String textOf(String id) {
        Node found = node(id);
        return callOnFxThread(
                () -> {
                    if (found instanceof Labeled labeled) {
                        return labeled.getText();
                    }
                    if (found instanceof TextInputControl text) {
                        return text.getText();
                    }
                    return fail(
                            "#"
                                    + id
                                    + " is a "
                                    + found.getClass().getName()
                                    + ", which has no text to read");
                });
    }

    @Override
    public final String accessibleTextOf(String id) {
        Node found = node(id);
        return callOnFxThread(found::getAccessibleText);
    }

    @Override
    public final void onFxThread(Runnable work) {
        Objects.requireNonNull(work, "work");
        if (Platform.isFxApplicationThread()) {
            /*
             * Re-entrant on purpose.  Reading state through this method is convenient enough that
             * a nested call is easy to write by accident -- node(id) inside a callOnFxThread, say
             * -- and posting a task from the application thread and then waiting for it is a
             * deadlock that presents as a test that simply never finishes.  Running inline is the
             * same work in the same place, so nothing about the semantics changes.
             */
            work.run();
            return;
        }
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
        boolean finished;
        try {
            finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for the FX thread", interrupted);
        }
        assertTrue(
                finished,
                "the JavaFX application thread did not finish the work within "
                        + TIMEOUT_SECONDS
                        + "s");
        Throwable thrown = failure.get();
        if (thrown != null) {
            throw new IllegalStateException("work on the FX application thread failed", thrown);
        }
    }

    @Override
    public final <T> T callOnFxThread(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        AtomicReference<T> value = new AtomicReference<>();
        onFxThread(() -> value.set(work.get()));
        return value.get();
    }

    /**
     * The centre of a node in screen coordinates. Called on the JavaFX application thread.
     *
     * <p>This is the one place a coordinate appears anywhere in the driver, and it is derived from
     * the node the test named rather than written down: {@code R-TEST-04} forbids locating a
     * control by pixel coordinates, not moving a pointer to one the toolkit computed.
     *
     * @param node the node to aim at
     * @return its centre, in screen coordinates
     */
    static Point2D centreOf(Node node) {
        Bounds screen = node.localToScreen(node.getBoundsInLocal());
        if (screen == null) {
            return fail(
                    "#"
                            + node.getId()
                            + " has no screen bounds, so it is not on screen and cannot be"
                            + " clicked");
        }
        return new Point2D(
                screen.getMinX() + screen.getWidth() / 2,
                screen.getMinY() + screen.getHeight() / 2);
    }
}
