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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.cometgui.workflow.state.EngineStep;
import org.junit.jupiter.api.Test;

/** The exact sentence each kind of mismatch is reported with, hand-typed. */
class ReuseMismatchTest {

    private static final String A = "a".repeat(64);

    private static final String B = "b".repeat(64);

    private static ReuseMismatch of(
            ReuseMismatch.Kind kind, String subject, String role, String was, String now) {
        return new ReuseMismatch(
                EngineStep.RUN_COMET,
                kind,
                subject,
                role,
                Optional.ofNullable(was),
                Optional.ofNullable(now));
    }

    @Test
    void eachKindIsDescribedInItsOwnWords() {
        assertEquals(
                "/r/a.mzML (input file, role spectrum, of step run-comet) has changed since it was"
                        + " recorded: recorded SHA-256 "
                        + A
                        + ", now "
                        + B,
                of(ReuseMismatch.Kind.CHANGED, "/r/a.mzML", "input file, role spectrum", A, B)
                        .describe());
        assertEquals(
                "/r/a.pin (output file, role pin, of step run-comet) no longer exists; recorded"
                        + " SHA-256 "
                        + A,
                of(ReuseMismatch.Kind.MISSING, "/r/a.pin", "output file, role pin", A, null)
                        .describe());
        assertEquals(
                "/r/a.pin (output file, role pin, of step run-comet) is not in the recorded"
                        + " manifest",
                of(ReuseMismatch.Kind.NOT_RECORDED, "/r/a.pin", "output file, role pin", null, null)
                        .describe());
        assertEquals(
                "/r/a.pin (output file, role pin, of step run-comet) is recorded as incomplete",
                of(ReuseMismatch.Kind.NOT_COMPLETED, "/r/a.pin", "output file, role pin", A, null)
                        .describe());
        assertEquals(
                "invocation comet-02 of step run-comet is not recorded as completed",
                of(ReuseMismatch.Kind.INVOCATION_NOT_RECORDED, "comet-02", "", null, null)
                        .describe());
        assertEquals(
                "step run-comet cannot be checked: there is no readable recorded manifest at"
                        + " /r/provenance/provenance.json",
                of(ReuseMismatch.Kind.NO_MANIFEST, "/r/provenance/provenance.json", "", null, null)
                        .describe());
    }

    @Test
    void anAbsentHashIsSaidToBeNone() {
        assertEquals(
                "/r/x (input file, role x, of step run-comet) has changed since it was recorded:"
                        + " recorded SHA-256 none, now none",
                of(ReuseMismatch.Kind.CHANGED, "/r/x", "input file, role x", null, null)
                        .describe());
        assertEquals(
                "/r/x (input file, role x, of step run-comet) no longer exists; recorded SHA-256"
                        + " none",
                of(ReuseMismatch.Kind.MISSING, "/r/x", "input file, role x", null, null)
                        .describe());
    }

    @Test
    void everyComponentIsRequired() {
        Optional<String> none = Optional.empty();
        ReuseMismatch.Kind kind = ReuseMismatch.Kind.MISSING;
        EngineStep step = EngineStep.RUN_COMET;
        assertEquals(
                "step",
                assertThrows(
                                NullPointerException.class,
                                () -> new ReuseMismatch(null, kind, "s", "r", none, none))
                        .getMessage());
        assertEquals(
                "kind",
                assertThrows(
                                NullPointerException.class,
                                () -> new ReuseMismatch(step, null, "s", "r", none, none))
                        .getMessage());
        assertEquals(
                "subject",
                assertThrows(
                                NullPointerException.class,
                                () -> new ReuseMismatch(step, kind, null, "r", none, none))
                        .getMessage());
        assertEquals(
                "role",
                assertThrows(
                                NullPointerException.class,
                                () -> new ReuseMismatch(step, kind, "s", null, none, none))
                        .getMessage());
        assertEquals(
                "recordedSha256",
                assertThrows(
                                NullPointerException.class,
                                () -> new ReuseMismatch(step, kind, "s", "r", null, none))
                        .getMessage());
        assertEquals(
                "currentSha256",
                assertThrows(
                                NullPointerException.class,
                                () -> new ReuseMismatch(step, kind, "s", "r", none, null))
                        .getMessage());
    }
}
