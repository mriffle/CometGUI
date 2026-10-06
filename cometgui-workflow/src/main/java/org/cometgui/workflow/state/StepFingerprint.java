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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One step's input fingerprint, together with the digest of each of its own inputs.
 *
 * <p>This is what a run records for a step that succeeded, and what {@link RerunPreview} compares
 * against. The fingerprint alone decides <em>whether</em> a step can be reused; the per-input
 * digests say <em>why</em> it cannot, which is what the preview shows ("comet-parameters changed").
 *
 * @param step the step
 * @param value the fingerprint: 64 lower-case hexadecimal characters
 * @param inputDigests the digest of each input kind the step declares
 */
public record StepFingerprint(EngineStep step, String value, Map<InputKind, String> inputDigests) {

    /**
     * Validates every component; a recorded fingerprint arrives from a file, so it is checked
     * rather than trusted.
     *
     * @throws NullPointerException naming a component, a key or a value that is {@code null}
     * @throws IllegalArgumentException if the fingerprint or an input digest is not a SHA-256 in
     *     hexadecimal, naming which
     */
    public StepFingerprint {
        Objects.requireNonNull(step, "step");
        value = Sha256.requireDigest(value, "fingerprint of " + step.id());
        Objects.requireNonNull(inputDigests, "inputDigests");
        Map<InputKind, String> digests = new EnumMap<>(InputKind.class);
        for (Map.Entry<InputKind, String> entry : inputDigests.entrySet()) {
            InputKind kind = Objects.requireNonNull(entry.getKey(), "inputDigests has a null key");
            digests.put(
                    kind,
                    Sha256.requireDigest(
                            entry.getValue(), "digest of " + kind.id() + " for " + step.id()));
        }
        inputDigests = Collections.unmodifiableMap(digests);
    }
}
