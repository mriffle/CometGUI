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
import java.util.concurrent.atomic.AtomicInteger;

/** A configurable step for the engine's tests, counting how often it is called. */
final class FakeStep implements StepAction {

    /** What a fake step does when it executes. */
    @FunctionalInterface
    interface Body {
        void run(StepContext context) throws StepFailedException, IOException, InterruptedException;
    }

    private final StepDeclaration declaration;

    private final boolean validates;

    private final Body validation;

    private final Body body;

    private final AtomicInteger executions = new AtomicInteger();

    private final AtomicInteger validations = new AtomicInteger();

    FakeStep(StepDeclaration declaration, boolean validates, Body validation, Body body) {
        this.declaration = declaration;
        this.validates = validates;
        this.validation = validation;
        this.body = body;
    }

    static FakeStep doing(StepDeclaration declaration, Body body) {
        return new FakeStep(declaration, false, context -> {}, body);
    }

    static FakeStep nothing() {
        return doing(StepDeclaration.NOTHING, context -> {});
    }

    @Override
    public StepDeclaration declaration() {
        return declaration;
    }

    @Override
    public boolean validates() {
        return validates;
    }

    @Override
    public void validate(StepContext context)
            throws StepFailedException, IOException, InterruptedException {
        validations.incrementAndGet();
        validation.run(context);
    }

    @Override
    public void execute(StepContext context)
            throws StepFailedException, IOException, InterruptedException {
        executions.incrementAndGet();
        body.run(context);
    }

    int executions() {
        return executions.get();
    }

    int validations() {
        return validations.get();
    }
}
