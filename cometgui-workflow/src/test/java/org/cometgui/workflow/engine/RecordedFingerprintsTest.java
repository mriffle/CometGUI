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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.TreeMap;
import org.cometgui.domain.run.RecordedFingerprint;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.InputKind;
import org.cometgui.workflow.state.StepFingerprint;
import org.junit.jupiter.api.Test;

/** The conversion between the workflow's fingerprints and the ones {@code run.json} stores. */
class RecordedFingerprintsTest {

    private static final String F = "f".repeat(64);

    private static final String S = "5".repeat(64);

    private static final String P = "9".repeat(64);

    @Test
    void aFingerprintIsStoredUnderItsIdentifiers() {
        StepFingerprint fingerprint =
                new StepFingerprint(
                        EngineStep.RUN_COMET,
                        F,
                        Map.of(InputKind.SPECTRUM_FILES, S, InputKind.COMET_PARAMETERS, P));
        assertEquals(
                new RecordedFingerprint(F, Map.of("spectrum-files", S, "comet-parameters", P)),
                RecordedFingerprints.toRecorded(fingerprint));
    }

    @Test
    void readingBackDropsWhatThisBuildDoesNotKnowSoThatItCanOnlyRerun() {
        Map<String, RecordedFingerprint> stored = new TreeMap<>();
        stored.put(
                "run-comet",
                new RecordedFingerprint(F, Map.of("spectrum-files", S, "a-future-input", P)));
        stored.put("a-future-step", new RecordedFingerprint(F, Map.of()));
        stored.put("merge-pin", new RecordedFingerprint(P, Map.of()));
        assertEquals(
                Map.of(
                        EngineStep.RUN_COMET,
                        new StepFingerprint(
                                EngineStep.RUN_COMET, F, Map.of(InputKind.SPECTRUM_FILES, S)),
                        EngineStep.MERGE_PIN,
                        new StepFingerprint(EngineStep.MERGE_PIN, P, Map.of())),
                RecordedFingerprints.fromRecorded(stored));
    }
}
