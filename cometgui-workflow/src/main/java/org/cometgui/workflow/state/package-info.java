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

/**
 * Observable workflow state: the stepper's stages, the explicit state of one stage, and the run
 * state derived from them.
 *
 * <p>Three types, and the whole package is pure data and derivation. There is no engine here, no
 * process, no file and no thread: a stage does not know how to run itself, and a run state is a
 * function of the stage states rather than a field somebody assigns. That is what lets the Run
 * screen's stage stepper be built and tested in phase 02 with nothing behind it, and it is what
 * lets phase 08 put an engine behind it without the UI's model of a run changing.
 *
 * <ul>
 *   <li>{@link org.cometgui.workflow.state.WorkflowStage} -- the eight user-facing stages the
 *       specification's <em>Information Architecture</em> draws, and the edges between them.
 *   <li>{@link org.cometgui.workflow.state.StepState} -- the nine explicit step states of the
 *       specification's <em>Workflow state model</em>, and the groupings the UI draws.
 *   <li>{@link org.cometgui.workflow.state.RunState} -- the run state derived from the stage
 *       states, with its precedence written down and tested.
 * </ul>
 *
 * <p><strong>These stages are not the specification's <em>Canonical workflow DAG</em>.</strong>
 * That DAG has seventeen finer-grained engine steps and belongs to phase 08, along with the stage
 * invalidation rules of {@code R-RUN-01} and the mapping from an engine step to the stepper stage
 * it is drawn under. Confusing the two models is the mistake this note exists to prevent.
 *
 * <p>The dependency on {@code org.cometgui.domain} points one way only. {@code WorkflowStage}
 * implements {@link org.cometgui.domain.run.StageTag} so that a console message can be tagged with
 * a stage; the domain does not know this package exists.
 */
package org.cometgui.workflow.state;
