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
import java.util.Optional;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;

/**
 * How an attempt ended.
 *
 * <p>The outcome is decided <strong>after</strong> the run's provenance record is written. When
 * {@code provenance.json} or {@code provenance.rst} cannot be written, {@link #provenanceFailure()}
 * says why, and the attempt is {@link AttemptOutcome#FAILED} and the run state {@link
 * RunState#FAILED} whatever its steps did: a run without its record is never a success. The step
 * states stay what the steps did.
 *
 * @param attempt the attempt's number in {@code run.json}
 * @param outcome the outcome recorded for the attempt
 * @param runState the run state derived over the plan's final step states, or {@link
 *     RunState#FAILED} when the provenance record could not be written
 * @param states every planned step's final state
 * @param failures the message of every step that failed or was cancelled
 * @param manifest the provenance record, as written to {@code provenance.json} or as it would have
 *     been
 * @param finalisationErrors anything else that went wrong while recording the attempt -- an event
 *     that could not be appended, an end {@code run.json} could not take; empty when the rest of
 *     the record is whole
 * @param listenerFailures how many listener callbacks threw
 * @param provenanceFailure why the provenance record could not be written, naming the file and the
 *     error; empty when it was
 */
public record RunResult(
        int attempt,
        AttemptOutcome outcome,
        RunState runState,
        Map<EngineStep, StepState> states,
        Map<EngineStep, String> failures,
        ProvenanceManifest manifest,
        List<String> finalisationErrors,
        long listenerFailures,
        Optional<String> provenanceFailure) {

    /**
     * Validates and copies.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if a provenance failure is given with an outcome other than
     *     {@link AttemptOutcome#FAILED} or a run state other than {@link RunState#FAILED}
     */
    public RunResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(runState, "runState");
        states = Collections.unmodifiableMap(new EnumMap<>(states));
        failures = Collections.unmodifiableMap(copy(failures));
        Objects.requireNonNull(manifest, "manifest");
        finalisationErrors = List.copyOf(finalisationErrors);
        Objects.requireNonNull(provenanceFailure, "provenanceFailure");
        if (provenanceFailure.isPresent()
                && (outcome != AttemptOutcome.FAILED || runState != RunState.FAILED)) {
            throw new IllegalArgumentException(
                    "a run whose provenance record could not be written failed, but this one is "
                            + outcome.wireName()
                            + " with run state "
                            + runState);
        }
    }

    /**
     * A result whose provenance record was written.
     *
     * @param attempt as the canonical constructor
     * @param outcome as the canonical constructor
     * @param runState as the canonical constructor
     * @param states as the canonical constructor
     * @param failures as the canonical constructor
     * @param manifest as the canonical constructor
     * @param finalisationErrors as the canonical constructor
     * @param listenerFailures as the canonical constructor
     */
    public RunResult(
            int attempt,
            AttemptOutcome outcome,
            RunState runState,
            Map<EngineStep, StepState> states,
            Map<EngineStep, String> failures,
            ProvenanceManifest manifest,
            List<String> finalisationErrors,
            long listenerFailures) {
        this(
                attempt,
                outcome,
                runState,
                states,
                failures,
                manifest,
                finalisationErrors,
                listenerFailures,
                Optional.empty());
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
