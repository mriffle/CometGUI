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
 * Observable workflow state and the engine's declared dependency graph: the stepper's stages, the
 * explicit state of a stage or step, the run state derived from them, and the pure rules that
 * decide which engine steps a run executes.
 *
 * <p>The whole package is pure data and derivation. There is no engine here, no process, no file
 * and no thread: a step does not know how to run itself, a run state is a function of step states
 * rather than a field somebody assigns, and a rerun preview is a function of a plan, the current
 * inputs and what an earlier run recorded. That is what lets the stepper and the preview be tested
 * with nothing behind them, and what lets the engine ({@code org.cometgui.workflow.engine}) carry
 * out decisions made here rather than make its own.
 *
 * <h2>The stepper's model (phase 02)</h2>
 *
 * <ul>
 *   <li>{@link org.cometgui.workflow.state.WorkflowStage} -- the eight user-facing stages the
 *       specification's <em>Information Architecture</em> draws, and the edges between them.
 *   <li>{@link org.cometgui.workflow.state.StepState} -- the nine explicit step states of the
 *       specification's <em>Workflow state model</em>, and the groupings the UI draws.
 *   <li>{@link org.cometgui.workflow.state.RunState} -- the run state derived from the stage
 *       states, with its precedence written down and tested; and, since phase 08, the same
 *       derivation over a plan's engine steps.
 * </ul>
 *
 * <h2>The engine's declared graph (phase 08, R-RUN-01)</h2>
 *
 * <ul>
 *   <li>{@link org.cometgui.workflow.state.EngineStep} -- the seventeen steps of the
 *       specification's <em>Canonical workflow DAG</em>, each with its identifier, its stepper
 *       stage, its {@link org.cometgui.workflow.state.StepKind kind}, whether it is optional, the
 *       phase that implements it and the {@link org.cometgui.workflow.state.InputKind inputs} it
 *       reads.
 *   <li>{@link org.cometgui.workflow.state.StepGraph} -- the edges, declared once with a reason
 *       each, validated acyclic.
 *   <li>{@link org.cometgui.workflow.state.Plan} -- the steps one run executes or reuses.
 *   <li>{@link org.cometgui.workflow.state.StepInputs}, {@link
 *       org.cometgui.workflow.state.InputValue} and {@link
 *       org.cometgui.workflow.state.Fingerprints} -- each step's input fingerprint, over a
 *       documented, locale-independent encoding.
 *   <li>{@link org.cometgui.workflow.state.RerunPreview} -- which planned steps re-execute, which
 *       are reused, and why ({@link org.cometgui.workflow.state.StepVerdict}, {@link
 *       org.cometgui.workflow.state.RerunReason}).
 *   <li>{@link org.cometgui.workflow.state.StageProjection} -- engine-step states drawn as stepper
 *       stage states; a stage with no planned step has no state at all.
 * </ul>
 *
 * <p><strong>The stages are not the engine's steps.</strong> Each engine step maps onto exactly one
 * stage, and several steps share most stages; the mapping is declared on {@code EngineStep}, and
 * neither model is derived from the other.
 *
 * <p>The dependency on {@code org.cometgui.domain} points one way only. {@code WorkflowStage}
 * implements {@link org.cometgui.domain.run.StageTag} so that a console message can be tagged with
 * a stage, and a file input carries a {@link org.cometgui.domain.ports.FileHashes} from the one
 * hasher; the domain does not know this package exists.
 */
package org.cometgui.workflow.state;
