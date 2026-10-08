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

package org.cometgui.results.filtering.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The disk-backed store refused a table, or found that a raw table no longer matches the index it
 * opened with, for a named {@link IndexProblem}. Nothing is ever answered from an index that failed
 * a check.
 */
public final class ResultIndexException extends IOException {

    private static final long serialVersionUID = 1L;

    private final IndexProblem problem;

    /**
     * A refusal.
     *
     * @param problem the reason
     * @param file the raw table or index file concerned, named in the message
     * @param detail what was found, appended to the message
     */
    ResultIndexException(IndexProblem problem, Path file, String detail) {
        super(
                "The result index for "
                        + file
                        + " cannot be used: "
                        + Objects.requireNonNull(problem, "problem").description()
                        + (detail.isEmpty() ? "" : " (" + detail + ")"));
        this.problem = problem;
    }

    /**
     * Why.
     *
     * @return the reason
     */
    public IndexProblem problem() {
        return problem;
    }
}
