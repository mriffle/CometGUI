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

package org.cometgui.ui.viewmodel.params;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.workflow.state.InputKind;
import org.cometgui.workflow.state.RerunPreview;

/**
 * The rerun preview ({@code R-RUN-01}) of the configuration as it is now against the session's last
 * run, as the engine computed it -- what {@link RunViewModel} shows before the next run starts.
 *
 * <p>Two cases, because a run's configuration never changes once it starts ({@code R-RUN-06}):
 *
 * <ul>
 *   <li><strong>A retry</strong> ({@link #retry()}): nothing the steps read has changed since the
 *       last run, so Run starts another attempt of that run, and {@link #preview()} is exactly the
 *       engine's own plan for it -- the steps that re-execute, those that run as prerequisites and
 *       those whose recorded results are reused, each re-hashed against the record (P8-14). When a
 *       recorded result no longer matches, the preview is the plan the engine offers instead and
 *       {@link #refusal()} says which file changed.
 *   <li><strong>A new run</strong>: {@link #changed()} names the inputs that differ. {@link
 *       #preview()} is the comparison with the last run, which gives each step's reasons; the new
 *       run executes every step, because it records its own results.
 * </ul>
 *
 * @param runId the last run's identifier
 * @param retry whether Run retries the last run rather than starting a new one
 * @param preview the engine's preview, in plan order
 * @param refusal when the last run's recorded results can no longer be reused, the engine's words
 *     naming each file that changed and the steps that must run again
 * @param changed the inputs that differ from the last run; empty exactly for a retry
 */
public record RerunOutlook(
        String runId,
        boolean retry,
        RerunPreview preview,
        Optional<String> refusal,
        Set<InputKind> changed) {

    /**
     * Validates and copies.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the run identifier is blank, or {@code retry} disagrees
     *     with {@code changed}
     */
    public RerunOutlook {
        Objects.requireNonNull(runId, "runId");
        if (runId.isBlank()) {
            throw new IllegalArgumentException("the last run has an identifier");
        }
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(refusal, "refusal");
        Set<InputKind> copy = EnumSet.noneOf(InputKind.class);
        copy.addAll(Objects.requireNonNull(changed, "changed"));
        changed = Collections.unmodifiableSet(copy);
        if (retry != changed.isEmpty()) {
            throw new IllegalArgumentException(
                    "a retry is exactly a configuration in which no input changed, but retry is "
                            + retry
                            + " with changed inputs "
                            + changed);
        }
    }

    @Override
    public Set<InputKind> changed() {
        Set<InputKind> copy = EnumSet.noneOf(InputKind.class);
        copy.addAll(changed);
        return Collections.unmodifiableSet(copy);
    }
}
