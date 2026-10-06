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

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;

/**
 * How an attempt ended.
 *
 * @param attempt the attempt's number in {@code run.json}
 * @param outcome the outcome recorded for the attempt
 * @param runState the run state derived over the plan's final step states
 * @param states every planned step's final state
 * @param failures the message of every step that failed or was cancelled
 * @param manifest the provenance record written to {@code provenance.json}
 * @param finalisationErrors anything that went wrong while recording the attempt -- an event that
 *     could not be appended, a document that could not be written; empty when the record is whole
 * @param listenerFailures how many listener callbacks threw
 */
public record RunResult(
        int attempt,
        AttemptOutcome outcome,
        RunState runState,
        Map<EngineStep, StepState> states,
        Map<EngineStep, String> failures,
        ProvenanceManifest manifest,
        List<String> finalisationErrors,
        long listenerFailures) {

    /**
     * Validates and copies.
     *
     * @throws NullPointerException naming a component that is {@code null}
     */
    public RunResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(runState, "runState");
        states = Collections.unmodifiableMap(new EnumMap<>(states));
        failures = Collections.unmodifiableMap(copy(failures));
        Objects.requireNonNull(manifest, "manifest");
        finalisationErrors = List.copyOf(finalisationErrors);
    }

    private static Map<EngineStep, String> copy(Map<EngineStep, String> failures) {
        Map<EngineStep, String> copied = new EnumMap<>(EngineStep.class);
        copied.putAll(failures);
        return copied;
    }

    @Override
    public Map<EngineStep, StepState> states() {
        return Collections.unmodifiableMap(states);
    }

    @Override
    public Map<EngineStep, String> failures() {
        return Collections.unmodifiableMap(failures);
    }

    @Override
    public List<String> finalisationErrors() {
        return List.copyOf(finalisationErrors);
    }
}
