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

/** What a rerun does with one planned step. Two outcomes for each {@link StepKind}. */
public enum RerunDecision {

    /** A {@link StepKind#RESULT} step executes again; its recorded outputs are not reused. */
    RE_EXECUTE,

    /**
     * A {@link StepKind#RESULT} step's recorded outputs are reused. They are still re-hashed
     * against the record before use (R-RUN-02, P8-14); that is the engine's job, not this one's.
     */
    REUSE,

    /** A {@link StepKind#PREPARATION} step executes, because a step that needs it does. */
    PREPARE,

    /** A {@link StepKind#PREPARATION} step does not execute: nothing that needs it does. */
    NOT_NEEDED
}
