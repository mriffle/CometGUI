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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import org.cometgui.app.uidriver.FxUiDriver;

/**
 * Waiting for the Run section's answers, which arrive from the engine's threads: on an observable
 * fact -- a label's text -- and never on a fixed delay. Every wait has a bound, and a bound reached
 * is a failure naming the text that was showing.
 */
final class RunSection {

    /** How long a pre-run check may take: it reads the FASTA and hashes the inputs. */
    static final Duration CHECK_BOUND = Duration.ofMinutes(2);

    /** How long a real run of the test subset may take. A bound, never a delay. */
    static final Duration RUN_BOUND = Duration.ofMinutes(5);

    /** What the engine's label shows while no answer has arrived. */
    private static final Predicate<String> UNSETTLED =
            text ->
                    text.contains("The pre-run check is running")
                            || text.contains("The pre-run check has not run yet");

    private RunSection() {}

    /**
     * Waits for the engine's half of the readiness to hold an answer rather than "checking".
     *
     * @param driver the driver
     * @return the engine label's text then
     */
    static String awaitEngineAnswer(FxUiDriver driver) {
        return awaitText(driver, "run-engine", UNSETTLED.negate(), CHECK_BOUND);
    }

    /**
     * Waits for a label's text to satisfy a condition.
     *
     * @param driver the driver
     * @param id the label
     * @param wanted the condition
     * @param bound how long to wait at most
     * @return the text that satisfied it
     */
    static String awaitText(
            FxUiDriver driver, String id, Predicate<String> wanted, Duration bound) {
        Node node = driver.node(id);
        if (!(node instanceof Labeled label)) {
            return fail("#" + id + " is not a label, so it has no text to wait for");
        }
        CompletableFuture<String> reached = new CompletableFuture<>();
        driver.onFxThread(
                () -> {
                    if (wanted.test(label.getText())) {
                        reached.complete(label.getText());
                        return;
                    }
                    label.textProperty()
                            .addListener(
                                    new ChangeListener<String>() {
                                        @Override
                                        public void changed(
                                                ObservableValue<? extends String> property,
                                                String before,
                                                String after) {
                                            if (after != null && wanted.test(after)) {
                                                label.textProperty().removeListener(this);
                                                reached.complete(after);
                                            }
                                        }
                                    });
                });
        try {
            return reached.get(bound.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timedOut) {
            return fail(
                    "#"
                            + id
                            + " did not reach the expected text within "
                            + bound
                            + "; it shows: "
                            + driver.textOf(id));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fail("interrupted waiting for #" + id, interrupted);
        } catch (ExecutionException impossible) {
            return fail("the wait for #" + id + " failed", impossible);
        }
    }

    /**
     * Whether a control is disabled.
     *
     * @param driver the driver
     * @param id the control
     * @return {@code true} if disabled
     */
    static boolean isDisabled(FxUiDriver driver, String id) {
        Node node = driver.node(id);
        return driver.callOnFxThread(node::isDisabled);
    }
}
