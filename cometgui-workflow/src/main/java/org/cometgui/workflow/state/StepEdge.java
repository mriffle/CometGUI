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
 * A declared dependency: {@code downstream} must not start until {@code upstream} has finished.
 *
 * <p>An edge is <strong>required</strong> when the downstream step cannot run at all without the
 * upstream one, so that a {@link Plan} containing the downstream step must contain the upstream one
 * too. It is <strong>not required</strong> when the upstream step is needed only if it is planned:
 * Comet reads an index only when an index mode was selected, and core provenance hashes
 * Percolator's outputs only in a run that produced some. A non-required edge between two planned
 * steps orders them and carries a fingerprint exactly as a required one does; it differs only in
 * not pulling its upstream step into a plan.
 *
 * <p>Whether an edge carries data -- whether invalidation crosses it -- is not a property of the
 * edge but of its upstream step's {@link StepKind}: see {@link #isDataEdge()}.
 *
 * @param upstream the step that must finish first
 * @param downstream the step that waits for it
 * @param required whether a plan containing {@code downstream} must contain {@code upstream}
 */
public record StepEdge(EngineStep upstream, EngineStep downstream, boolean required) {

    /**
     * Checks both ends are present.
     *
     * @throws NullPointerException naming {@code upstream} or {@code downstream} if it is {@code
     *     null}
     */
    public StepEdge {
        Objects.requireNonNull(upstream, "upstream");
        Objects.requireNonNull(downstream, "downstream");
    }

    /**
     * Whether invalidation crosses this edge: true when the upstream step is a {@link
     * StepKind#RESULT} step, whose fingerprint the downstream step's fingerprint contains. An edge
     * out of a {@link StepKind#PREPARATION} step only orders.
     *
     * @return {@code true} for a data edge, {@code false} for an ordering edge
     */
    public boolean isDataEdge() {
        return upstream.kind() == StepKind.RESULT;
    }

    /**
     * The edge as {@code upstream -> downstream}, by step identifier.
     *
     * @return a readable form for messages
     */
    @Override
    public String toString() {
        return upstream.id() + " -> " + downstream.id() + (required ? "" : " (if planned)");
    }
}
