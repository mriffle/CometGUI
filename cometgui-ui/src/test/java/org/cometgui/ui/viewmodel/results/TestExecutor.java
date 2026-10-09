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

package org.cometgui.ui.viewmodel.results;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * An executor whose tasks wait until the test runs them -- oldest first, or newest first, which is
 * how a test makes an older answer arrive after a newer one.
 */
final class TestExecutor implements Executor {

    private final String name;

    private final Deque<Runnable> tasks = new ArrayDeque<>();

    private int executed;

    TestExecutor(String name) {
        this.name = name;
    }

    @Override
    public void execute(Runnable task) {
        tasks.add(Objects.requireNonNull(task, "task"));
    }

    /** How many tasks wait. */
    int pending() {
        return tasks.size();
    }

    /** How many tasks have been run in all. */
    int executed() {
        return executed;
    }

    /** Runs the oldest waiting task. */
    void runFirst() {
        run(tasks.pollFirst());
    }

    /** Runs the newest waiting task. */
    void runLast() {
        run(tasks.pollLast());
    }

    /** Runs every waiting task, oldest first, including those added meanwhile. */
    void drain() {
        while (!tasks.isEmpty()) {
            runFirst();
        }
    }

    private void run(Runnable task) {
        if (task == null) {
            throw new IllegalStateException(name + " has no task waiting");
        }
        executed++;
        task.run();
    }

    /**
     * Runs both executors until neither has a task.
     *
     * @param background the background executor
     * @param ui the interface executor
     */
    static void settle(TestExecutor background, TestExecutor ui) {
        while (background.pending() + ui.pending() > 0) {
            background.drain();
            ui.drain();
        }
    }
}
