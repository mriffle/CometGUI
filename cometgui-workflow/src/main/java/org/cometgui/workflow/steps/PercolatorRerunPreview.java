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

package org.cometgui.workflow.steps;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.run.RunId;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.RerunDecision;
import org.cometgui.workflow.state.RerunPreview;
import org.cometgui.workflow.state.RerunReason;
import org.cometgui.workflow.state.StepVerdict;

/**
 * What a compatible-version Percolator rerun will do, before anything is created: shown to the
 * scientist before the rerun starts ({@code R-RUN-01}), in the vocabulary of every other rerun
 * preview.
 *
 * <p>{@link #preview()} is the new run's own {@link RerunPreview} over its plan: {@code
 * run-percolator}, {@code parse-percolator} and {@code finalise-provenance} execute (nothing of
 * them is recorded in a run that does not exist yet), and {@code validate-configuration} and {@code
 * resolve-percolator} are prepared for them. The Comet steps are not in that plan at all: they are
 * {@linkplain Plan#provided() provided} by the source run, and {@link #reusedFromSource()} names
 * them -- not executed, their results taken from the source run's record after it was checked.
 *
 * @param source the run whose Comet results are reused
 * @param preview the new run's preview over its own plan
 * @param mergedPin the source's merged PIN, which the new run copies
 * @param mergedPinSha256 its SHA-256, as the source recorded it and as it was re-hashed just now
 */
public record PercolatorRerunPreview(
        RunId source, RerunPreview preview, Path mergedPin, String mergedPinSha256) {

    /**
     * Validates presence.
     *
     * @throws NullPointerException naming a component that is {@code null}
     */
    public PercolatorRerunPreview {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(mergedPin, "mergedPin");
        Objects.requireNonNull(mergedPinSha256, "mergedPinSha256");
    }

    /**
     * The new run's plan.
     *
     * @return the steps it executes, with the reused ones as its provided steps
     */
    public Plan plan() {
        return preview.plan();
    }

    /**
     * The steps whose results come from the source run: not executed in the new run.
     *
     * @return the Comet result steps, in step order
     */
    public Set<EngineStep> reusedFromSource() {
        return preview.plan().provided();
    }

    /**
     * The steps the new run executes -- every step of its plan.
     *
     * @return in step order
     */
    public Set<EngineStep> executed() {
        return preview.executed();
    }

    /**
     * The preview as text, one line per step in step order: every step the new run executes, with
     * its decision and reasons, and every step whose result is reused from the source.
     *
     * @return the lines
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(
                "Percolator reruns in a new run on the merged PIN of run "
                        + source
                        + " ("
                        + mergedPin
                        + ", SHA-256 "
                        + mergedPinSha256
                        + ", re-hashed and unchanged); Comet is not run.");
        for (EngineStep step : EngineStep.values()) {
            if (reusedFromSource().contains(step)) {
                lines.add(step.id() + ": not executed -- its result is reused from run " + source);
            } else if (plan().contains(step)) {
                StepVerdict verdict = preview.verdict(step);
                List<String> reasons = new ArrayList<>();
                for (RerunReason reason : verdict.reasons()) {
                    reasons.add(reason.describe());
                }
                lines.add(
                        step.id()
                                + ": "
                                + word(verdict.decision())
                                + (reasons.isEmpty() ? "" : " -- " + String.join("; ", reasons)));
            }
        }
        return List.copyOf(lines);
    }

    private static String word(RerunDecision decision) {
        return switch (decision) {
            case RE_EXECUTE -> "executes";
            case PREPARE -> "prepares";
            case REUSE -> "reused";
            case NOT_NEEDED -> "not needed";
        };
    }
}
