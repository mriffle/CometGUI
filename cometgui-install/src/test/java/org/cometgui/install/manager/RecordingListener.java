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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;

/**
 * Every progress report, in order, with a way to wait for the terminal one.
 *
 * <p>The waiting is the point: {@code install} returns as soon as the install has started, so a
 * test that read the reports straight afterwards would be reading an empty list and calling it a
 * result. The latch counts down on the first terminal phase, which is also the contract ({@code
 * InstallProgressListener}: exactly one report carries a terminal phase and it is last).
 */
final class RecordingListener implements InstallProgressListener {

    /** Every report, in the order it arrived. */
    private final List<InstallProgress> reports = new ArrayList<>();

    /** Counted down by the first terminal report. */
    private final CountDownLatch finished = new CountDownLatch(1);

    @Override
    public synchronized void onInstallProgress(InstallProgress progress) {
        reports.add(progress);
        if (progress.phase().isTerminal()) {
            finished.countDown();
        }
    }

    /**
     * Waits for the terminal report.
     *
     * @return the terminal report
     * @throws InterruptedException if the wait is interrupted
     * @throws AssertionError if none arrives within a minute
     */
    InstallProgress awaitTerminal() throws InterruptedException {
        if (!finished.await(300, TimeUnit.SECONDS)) {
            throw new AssertionError(
                    "no terminal install phase arrived within 300 seconds; the phases seen were "
                            + phases());
        }
        return terminalReports().get(0);
    }

    /**
     * Every report, in order.
     *
     * @return the reports
     */
    synchronized List<InstallProgress> reports() {
        return List.copyOf(reports);
    }

    /**
     * The phases reported, in order.
     *
     * @return the phases
     */
    synchronized List<InstallPhase> phases() {
        return reports.stream().map(InstallProgress::phase).toList();
    }

    /**
     * The reports carrying a terminal phase; the contract says there is exactly one, and last.
     *
     * @return the terminal reports
     */
    synchronized List<InstallProgress> terminalReports() {
        return reports.stream().filter(report -> report.phase().isTerminal()).toList();
    }

    /**
     * The largest byte count any {@code DOWNLOADING} report carried.
     *
     * @return the count, or zero when nothing was ever reported as downloading
     */
    synchronized long bytesDownloaded() {
        return reports.stream()
                .filter(report -> report.phase() == InstallPhase.DOWNLOADING)
                .mapToLong(InstallProgress::bytesTransferred)
                .max()
                .orElse(0L);
    }
}
