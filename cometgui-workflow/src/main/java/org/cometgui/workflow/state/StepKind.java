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

/**
 * Whether an engine step produces something a later run may reuse, or only prepares the ground for
 * the steps that do.
 *
 * <p>The distinction exists because the specification's <em>Stage reruns</em> paragraph cannot be
 * satisfied without it. "Validate project inputs and configuration" reads the Comet parameters
 * <em>and</em> everything else; if its fingerprint fed Comet's, changing a Percolator setting would
 * change Comet's fingerprint, and the specification says in so many words that it must not. And
 * "resolve, install and probe Percolator" must run before Percolator re-runs even when nothing
 * about Percolator's identity changed, because the run about to start needs the binary's location
 * now, not a record that it had one last week. Neither step is a cached computation; both are
 * preconditions.
 *
 * <ul>
 *   <li>A {@link #RESULT} step's outputs are reusable while its fingerprint matches the recorded
 *       one. Its fingerprint folds in the fingerprints of its {@code RESULT} upstream steps, and
 *       when it re-executes, every {@code RESULT} step downstream of it re-executes too: an edge
 *       out of a {@code RESULT} step is a <em>data</em> edge.
 *   <li>A {@link #PREPARATION} step is never reused. It executes in a run exactly when some step
 *       that depends on it executes -- whatever its own fingerprint says -- and its executing never
 *       by itself makes anything downstream re-execute: an edge out of a {@code PREPARATION} step
 *       is an <em>ordering</em> edge. Whatever a preparation step establishes that a later step's
 *       result depends on -- a tool identity, a file's hash -- is declared as that later step's own
 *       {@link InputKind input}, so it reaches the later step's fingerprint directly.
 * </ul>
 */
public enum StepKind {

    /**
     * Establishes a precondition -- a valid configuration, a resolved tool, a hashed input -- and
     * produces nothing a later run reuses.
     */
    PREPARATION,

    /** Produces outputs that a later run may reuse while the step's fingerprint is unchanged. */
    RESULT
}
