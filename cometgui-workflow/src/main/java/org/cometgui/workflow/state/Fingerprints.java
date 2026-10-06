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

package org.cometgui.workflow.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes the input fingerprint of every step in a plan (design decision P8-10).
 *
 * <h2>The encoding</h2>
 *
 * <p>A step's fingerprint is the SHA-256, as 64 lower-case hexadecimal characters, of this ASCII
 * text, every line ending in a single {@code \n}:
 *
 * <pre>
 *     cometgui-step-fingerprint 1
 *     step &lt;step id&gt;
 *     input &lt;input kind id&gt; &lt;input digest&gt;
 *     upstream &lt;step id&gt; &lt;upstream fingerprint&gt;
 * </pre>
 *
 * <p>There is one {@code input} line per declared input, in declared order, and then one {@code
 * upstream} line per planned {@link StepKind#RESULT} upstream step, in {@link EngineStep} order.
 *
 * <p>Each input digest is the {@link InputValue#digest()} of the step's value for that kind, whose
 * own encoding is documented on {@link InputValue}. Every element is an identifier this package
 * fixes ({@code [a-z0-9-]}) or a lower-case hex digest, so the text contains no user data and no
 * locale-dependent formatting: the same inputs give the same fingerprint on every machine, in every
 * locale, and by hand with {@code printf ... | sha256sum}.
 *
 * <p>The first line names the encoding and its version. Changing anything about the encoding means
 * changing that version, and every fingerprint with it -- a recorded run then reruns rather than
 * being matched against a fingerprint computed a different way.
 *
 * <h2>What is, and is not, in a fingerprint</h2>
 *
 * <ul>
 *   <li>The step's identity, so two steps reading the same inputs never share a fingerprint.
 *   <li>Exactly the inputs it {@link EngineStep#inputs() declares}. Nothing it does not declare can
 *       change it -- the display filters, notably, which no step declares.
 *   <li>The fingerprints of its planned upstream steps of kind {@link StepKind#RESULT}, so a change
 *       anywhere upstream along a data edge changes it. Upstream {@link StepKind#PREPARATION} steps
 *       are deliberately absent; see {@link StepKind}.
 * </ul>
 *
 * <p>A step whose declared input has no value is not fingerprinted at all: {@link #compute}
 * refuses, naming the step and the missing inputs. A fingerprint computed without one of its inputs
 * would compare equal to a later one computed the same incomplete way, and reuse a result nobody
 * could justify.
 */
public final class Fingerprints {

    /** The first line of every fingerprint's encoding: the encoding's name and version. */
    public static final String ENCODING = "cometgui-step-fingerprint 1";

    private Fingerprints() {}

    /**
     * Fingerprints every step of a plan.
     *
     * @param plan the plan
     * @param inputs the current input values
     * @return each planned step's fingerprint, in plan order; immutable
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if a planned step's declared input has no value, naming the
     *     first such step in plan order and every input it is missing
     */
    public static Map<EngineStep, StepFingerprint> compute(Plan plan, StepInputs inputs) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(inputs, "inputs");
        Map<EngineStep, StepFingerprint> fingerprints = new LinkedHashMap<>();
        for (EngineStep step : plan.steps()) {
            fingerprints.put(step, fingerprint(plan, step, inputs, fingerprints));
        }
        return Collections.unmodifiableMap(fingerprints);
    }

    private static StepFingerprint fingerprint(
            Plan plan,
            EngineStep step,
            StepInputs inputs,
            Map<EngineStep, StepFingerprint> upstreamFingerprints) {
        Map<InputKind, String> digests = new EnumMap<>(InputKind.class);
        List<String> missing = new ArrayList<>();
        StringBuilder encoding = new StringBuilder(ENCODING).append('\n');
        encoding.append("step ").append(step.id()).append('\n');
        for (InputKind kind : step.inputs()) {
            Optional<InputValue> value = inputs.get(kind);
            if (value.isEmpty()) {
                missing.add(kind.id());
            } else {
                String digest = value.get().digest();
                digests.put(kind, digest);
                encoding.append("input ").append(kind.id()).append(' ').append(digest).append('\n');
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "step "
                            + step.id()
                            + " cannot be fingerprinted without its declared inputs; missing: "
                            + String.join(", ", missing));
        }
        for (EngineStep upstream : plan.upstreamOf(step)) {
            if (upstream.kind() == StepKind.RESULT) {
                encoding.append("upstream ")
                        .append(upstream.id())
                        .append(' ')
                        .append(upstreamFingerprints.get(upstream).value())
                        .append('\n');
            }
        }
        return new StepFingerprint(step, Sha256.hex(encoding.toString()), digests);
    }
}
