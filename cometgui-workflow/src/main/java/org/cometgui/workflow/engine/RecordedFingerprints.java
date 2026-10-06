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
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.cometgui.domain.run.RecordedFingerprint;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.InputKind;
import org.cometgui.workflow.state.StepFingerprint;

/**
 * Converts between the fingerprints the workflow computes and the ones {@code run.json} stores,
 * which are keyed by identifier because the domain does not know the workflow's types.
 *
 * <p>Reading back is deliberately forgiving in one direction only: a recorded step or input kind
 * this build does not know is <em>dropped</em>. A dropped step has no recorded fingerprint and so
 * re-executes; a fingerprint missing one of its inputs compares unequal and so re-executes. Nothing
 * unknown can make a step look reusable.
 */
final class RecordedFingerprints {

    private RecordedFingerprints() {}

    static RecordedFingerprint toRecorded(StepFingerprint fingerprint) {
        Map<String, String> digests = new TreeMap<>();
        for (Map.Entry<InputKind, String> entry : fingerprint.inputDigests().entrySet()) {
            digests.put(entry.getKey().id(), entry.getValue());
        }
        return new RecordedFingerprint(fingerprint.value(), digests);
    }

    static Map<EngineStep, StepFingerprint> fromRecorded(Map<String, RecordedFingerprint> stored) {
        Map<EngineStep, StepFingerprint> fingerprints = new EnumMap<>(EngineStep.class);
        for (Map.Entry<String, RecordedFingerprint> entry : stored.entrySet()) {
            Optional<EngineStep> step = stepWithId(entry.getKey());
            if (step.isPresent()) {
                fingerprints.put(
                        step.get(),
                        new StepFingerprint(
                                step.get(),
                                entry.getValue().value(),
                                digests(entry.getValue().inputDigests())));
            }
        }
        return Collections.unmodifiableMap(fingerprints);
    }

    private static Map<InputKind, String> digests(Map<String, String> stored) {
        Map<InputKind, String> digests = new EnumMap<>(InputKind.class);
        for (InputKind kind : InputKind.values()) {
            String digest = stored.get(kind.id());
            if (digest != null) {
                digests.put(kind, digest);
            }
        }
        return digests;
    }

    private static Optional<EngineStep> stepWithId(String id) {
        for (EngineStep step : EngineStep.values()) {
            if (step.id().equals(id)) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }
}
