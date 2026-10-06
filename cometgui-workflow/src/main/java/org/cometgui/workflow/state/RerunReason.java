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

import java.util.Objects;

/**
 * Why a step executes in a rerun -- one entry of a {@link StepVerdict}'s reasons.
 *
 * <p>A sealed set of records, so a view can switch over them exhaustively, and each has a plain
 * {@link #describe() description} built from identifiers only, without locale-dependent formatting.
 * The two that most need telling apart are {@link NotRecorded} -- the step never succeeded in the
 * run being compared, so there is nothing to reuse -- and {@link InputChanged}, which names what
 * the user changed.
 */
public sealed interface RerunReason
        permits RerunReason.Forced,
                RerunReason.NotRecorded,
                RerunReason.InputChanged,
                RerunReason.UpstreamReExecutes,
                RerunReason.FingerprintChanged,
                RerunReason.PrerequisiteOf {

    /**
     * A short description for a preview.
     *
     * @return the description
     */
    String describe();

    /**
     * The caller asked for this step to execute whatever its fingerprint says -- for example, an
     * output it produced failed revalidation against its recorded checksum (R-RUN-02), so the
     * producing step must run again.
     */
    record Forced() implements RerunReason {
        @Override
        public String describe() {
            return "re-execution was requested";
        }
    }

    /**
     * No successful execution of this step is recorded in the run being compared: it never ran, was
     * not planned then, or did not succeed.
     */
    record NotRecorded() implements RerunReason {
        @Override
        public String describe() {
            return "no successful earlier execution is recorded";
        }
    }

    /**
     * One of the step's own declared inputs has a different digest from the recorded one.
     *
     * @param input the input that changed
     */
    record InputChanged(InputKind input) implements RerunReason {

        /**
         * Requires the input.
         *
         * @throws NullPointerException if {@code input} is {@code null}
         */
        public InputChanged {
            Objects.requireNonNull(input, "input");
        }

        @Override
        public String describe() {
            return input.id() + " changed";
        }
    }

    /**
     * A step this one reads from along a data edge re-executes, so its output -- this step's input
     * -- will be new.
     *
     * @param upstream the upstream step that re-executes
     */
    record UpstreamReExecutes(EngineStep upstream) implements RerunReason {

        /**
         * Requires the step.
         *
         * @throws NullPointerException if {@code upstream} is {@code null}
         */
        public UpstreamReExecutes {
            Objects.requireNonNull(upstream, "upstream");
        }

        @Override
        public String describe() {
            return upstream.id() + " re-executes";
        }
    }

    /**
     * The fingerprint differs from the recorded one although none of the step's own inputs changed
     * and no upstream step re-executes: the steps it reads from were planned differently, or the
     * recorded fingerprint was computed with another encoding version.
     */
    record FingerprintChanged() implements RerunReason {
        @Override
        public String describe() {
            return "its fingerprint differs from the recorded one";
        }
    }

    /**
     * A preparation step executes because a step that depends on it executes.
     *
     * @param step the dependent step that executes
     */
    record PrerequisiteOf(EngineStep step) implements RerunReason {

        /**
         * Requires the step.
         *
         * @throws NullPointerException if {@code step} is {@code null}
         */
        public PrerequisiteOf {
            Objects.requireNonNull(step, "step");
        }

        @Override
        public String describe() {
            return "needed by " + step.id();
        }
    }
}
