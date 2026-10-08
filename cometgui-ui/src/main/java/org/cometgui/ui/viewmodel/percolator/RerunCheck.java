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

package org.cometgui.ui.viewmodel.percolator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The answer of {@link PercolatorRerunPort#check}: whether Percolator can be rerun from the
 * session's last run with the Percolator half as it is now, and the preview of what that rerun
 * executes and reuses -- or why it cannot.
 *
 * @param sourceRun the run whose merged PIN the rerun would read; empty when there is none
 * @param preview the workflow's preview of the rerun, one line per step; empty when it is refused
 * @param refusal why there is no rerun to offer; empty when there is
 */
public record RerunCheck(
        Optional<String> sourceRun, List<String> preview, Optional<String> refusal) {

    /**
     * Validates and copies.
     *
     * @throws NullPointerException if a component or a line is {@code null}
     * @throws IllegalArgumentException if a possible rerun has no source or no preview, a refused
     *     one has a preview, or the refusal is blank
     */
    public RerunCheck {
        Objects.requireNonNull(sourceRun, "sourceRun");
        preview = List.copyOf(preview);
        Objects.requireNonNull(refusal, "refusal");
        if (refusal.isPresent()) {
            if (refusal.get().isBlank()) {
                throw new IllegalArgumentException("a refused rerun says why");
            }
            if (!preview.isEmpty()) {
                throw new IllegalArgumentException("a refused rerun has no preview");
            }
        } else if (sourceRun.isEmpty() || preview.isEmpty()) {
            throw new IllegalArgumentException(
                    "a rerun that can be made names its source run and previews its steps");
        }
    }

    /**
     * Percolator can be rerun.
     *
     * @param sourceRun the run whose merged PIN it reads
     * @param preview the preview lines
     * @return the answer
     */
    public static RerunCheck possible(String sourceRun, List<String> preview) {
        return new RerunCheck(Optional.of(sourceRun), preview, Optional.empty());
    }

    /**
     * There is no rerun to offer.
     *
     * @param why why, as a sentence
     * @return the answer
     */
    public static RerunCheck refused(String why) {
        return new RerunCheck(Optional.empty(), List.of(), Optional.of(why));
    }

    /**
     * Whether a rerun can be started.
     *
     * @return {@code true} when nothing refuses it
     */
    public boolean possible() {
        return refusal.isEmpty();
    }

    @Override
    public List<String> preview() {
        return List.copyOf(preview);
    }
}
