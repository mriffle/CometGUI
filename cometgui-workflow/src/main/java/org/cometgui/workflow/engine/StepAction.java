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

package org.cometgui.workflow.engine;

import java.io.IOException;

/**
 * What one engine step does. The engine owns when a step runs, its state, its provenance and its
 * cancellation; the action owns only the work.
 *
 * <p>An action is called on an engine worker thread, never on the JavaFX application thread. It
 * runs tools only through its {@link StepContext}, which is the one way a step reaches the process
 * service, so that every invocation is logged, cancellable and recorded.
 *
 * <p>An action signals failure by throwing. It does not record its own state or provenance.
 */
public interface StepAction {

    /**
     * The files this step reads and writes and the invocations it makes, as they will be when it
     * runs. Called before the step executes and when the step is considered for reuse; it must not
     * depend on anything the step itself computes.
     *
     * @return the declaration
     */
    StepDeclaration declaration();

    /**
     * Whether this step has a validation phase, shown as {@code VALIDATING} before {@code READY}.
     *
     * @return {@code false} unless overridden
     */
    default boolean validates() {
        return false;
    }

    /**
     * Checks this step's preconditions. Called only when {@link #validates()} is {@code true},
     * before {@link #execute}.
     *
     * @param context the step's context
     * @throws StepFailedException if a precondition does not hold; the step fails with the message
     * @throws IOException if a file could not be read
     * @throws InterruptedException if interrupted
     */
    default void validate(StepContext context)
            throws StepFailedException, IOException, InterruptedException {}

    /**
     * Does the step's work.
     *
     * @param context the step's context
     * @throws StepFailedException if the work failed; the step fails with the message
     * @throws IOException if a file could not be read or written
     * @throws InterruptedException if interrupted
     */
    void execute(StepContext context) throws StepFailedException, IOException, InterruptedException;
}
