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

package org.cometgui.ui.viewmodel.params;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.workflow.steps.PreRunReport;

/**
 * The engine's answer to {@link RunEnginePort#check}: why it cannot run the configuration at all
 * (no Comet of the release installed, the project locked by another instance, a check that could
 * not be made), the pre-run check's report when it could be taken, and the rerun preview against
 * the session's last run when there is one.
 *
 * @param reasons why the engine cannot even check or run the configuration, each a sentence; empty
 *     when it could
 * @param report the pre-run check's report -- the problems with the files and the one validator's
 *     report over the model and the file-system facts, which carries the decoy blocks and the index
 *     refusal -- when it was taken
 * @param outlook the rerun preview against the session's last run, when there is one and nothing
 *     blocks the run
 */
public record EngineCheck(
        List<String> reasons, Optional<PreRunReport> report, Optional<RerunOutlook> outlook) {

    /**
     * Validates and copies.
     *
     * @throws NullPointerException naming a component or a reason that is {@code null}
     * @throws IllegalArgumentException if a reason is blank, or there is neither a reason nor a
     *     report -- an answer that says nothing
     */
    public EngineCheck {
        reasons = List.copyOf(reasons);
        for (String reason : reasons) {
            if (reason.isBlank()) {
                throw new IllegalArgumentException("a reason the engine cannot run is never blank");
            }
        }
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(outlook, "outlook");
        if (reasons.isEmpty() && report.isEmpty()) {
            throw new IllegalArgumentException(
                    "an engine check with no reason and no report says nothing about the run");
        }
    }

    /**
     * The engine cannot check the configuration at all.
     *
     * @param reason why, as a sentence
     * @return the answer
     */
    public static EngineCheck unavailable(String reason) {
        return new EngineCheck(List.of(reason), Optional.empty(), Optional.empty());
    }

    /**
     * The pre-run check was taken.
     *
     * @param report its report
     * @param outlook the rerun preview, when there is an earlier run and nothing blocks this one
     * @return the answer
     */
    public static EngineCheck checked(PreRunReport report, Optional<RerunOutlook> outlook) {
        return new EngineCheck(List.of(), Optional.of(report), outlook);
    }

    @Override
    public List<String> reasons() {
        return List.copyOf(reasons);
    }
}
