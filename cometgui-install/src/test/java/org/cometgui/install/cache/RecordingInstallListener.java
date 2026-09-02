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

package org.cometgui.install.cache;

import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;

/**
 * Keeps every progress report, so that the contract can be asserted rather than assumed.
 *
 * <p>{@link InstallProgressListener} promises exactly one terminal report and that it is the last
 * one. A listener that discarded the reports would make that promise untestable.
 */
final class RecordingInstallListener implements InstallProgressListener {

    /** Every report, in the order it arrived. */
    private final List<InstallProgress> reports = new ArrayList<>();

    @Override
    public void onInstallProgress(InstallProgress progress) {
        reports.add(progress);
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
     * The phases reported, in order.
     *
     * @return the phases
     */
    List<InstallPhase> phases() {
        return reports.stream().map(InstallProgress::phase).toList();
    }

    /**
     * The reports that carry a terminal phase.
     *
     * @return the terminal reports; the contract says there is exactly one and it is last
     */
    List<InstallProgress> terminalReports() {
        return reports.stream().filter(report -> report.phase().isTerminal()).toList();
    }
}
