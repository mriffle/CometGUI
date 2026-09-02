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

package org.cometgui.install.probe;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.tools.LoaderDiagnostic;

/**
 * What running a staged executable established about whether it starts.
 *
 * @param failure the {@code R-PLAT-03} diagnostic, present only when the build did not start
 * @param standardOutput every line it wrote to standard output, in order
 * @param standardError every line it wrote to standard error, in order
 * @param exitCode the code it exited with, absent when it never started or never finished
 */
public record LoadabilityResult(
        Optional<LoaderDiagnostic> failure,
        List<String> standardOutput,
        List<String> standardError,
        OptionalInt exitCode) {

    /**
     * Validates the result and takes immutable copies of both streams.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public LoadabilityResult {
        Objects.requireNonNull(failure, "failure");
        standardOutput = List.copyOf(Objects.requireNonNull(standardOutput, "standardOutput"));
        standardError = List.copyOf(Objects.requireNonNull(standardError, "standardError"));
        Objects.requireNonNull(exitCode, "exitCode");
    }

    /**
     * Whether the build started.
     *
     * @return {@code true} when no loader failure was established
     */
    public boolean started() {
        return failure.isEmpty();
    }

    /**
     * Everything the build printed, standard error first, which is where a loader failure and every
     * banner this project has measured actually arrive.
     *
     * @return the two streams concatenated, immutable
     */
    public List<String> output() {
        return IdentityProbe.errorFirst(standardError, standardOutput);
    }

    /**
     * The standard output lines, immutable.
     *
     * @return the lines, possibly empty
     */
    @Override
    public List<String> standardOutput() {
        return List.copyOf(standardOutput);
    }

    /**
     * The standard error lines, immutable.
     *
     * @return the lines, possibly empty
     */
    @Override
    public List<String> standardError() {
        return List.copyOf(standardError);
    }
}
