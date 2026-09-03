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

package org.cometgui.install.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;

/**
 * Every progress report an install produced, with a latch on the terminal one.
 *
 * <p>An install runs on another thread, so the terminal report is the only thing a test can wait
 * for -- which is also what {@code InstallProgressListener} promises a user interface: exactly one
 * terminal phase, and it is the last one.
 *
 * <p><strong>Every wait is bounded.</strong> An unbounded await here would turn a defect that stops
 * an install into a build that hangs, and PIT scores a mutant that blocks as a timeout rather than
 * as a kill.
 */
final class RecordingListener implements InstallProgressListener {

    /** How long any wait here may take before the test says so. */
    static final int TIMEOUT_SECONDS = 300;

    private final List<InstallProgress> reports = Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Consumer<InstallProgress> whileRunning;
    private volatile InstallPhase terminal;

    RecordingListener() {
        this(progress -> {});
    }

    RecordingListener(Consumer<InstallProgress> whileRunning) {
        this.whileRunning = whileRunning;
    }

    @Override
    public void onInstallProgress(InstallProgress progress) {
        reports.add(progress);
        whileRunning.accept(progress);
        if (progress.phase().isTerminal()) {
            terminal = progress.phase();
            finished.countDown();
        }
    }

    /**
     * Waits for the terminal report and answers with its phase.
     *
     * @return the phase the install ended in
     */
    InstallPhase awaitTerminal() {
        try {
            if (!finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError(
                        "no terminal install phase arrived within "
                                + TIMEOUT_SECONDS
                                + " seconds; the reports so far were "
                                + phases());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for the install to finish", interrupted);
        }
        return terminal;
    }

    /**
     * Every report, in order.
     *
     * @return the reports
     */
    List<InstallProgress> reports() {
        return List.copyOf(reports);
    }

    /**
     * Every report's phase, in order, with runs of one phase collapsed.
     *
     * @return the phases the install passed through
     */
    List<InstallPhase> phases() {
        List<InstallPhase> distinct = new ArrayList<>();
        for (InstallProgress progress : reports()) {
            if (distinct.isEmpty() || distinct.get(distinct.size() - 1) != progress.phase()) {
                distinct.add(progress.phase());
            }
        }
        return distinct;
    }

    /**
     * The largest byte count any report carried.
     *
     * @return the high-water mark, zero when nothing was transferred
     */
    long bytesTransferred() {
        long highest = 0;
        for (InstallProgress progress : reports()) {
            highest = Math.max(highest, progress.bytesTransferred());
        }
        return highest;
    }
}
