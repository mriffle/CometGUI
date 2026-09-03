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

package org.cometgui.tools.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * What one probe run of a tool produced: both streams, separately, and the exit code if it
 * finished.
 *
 * <p><strong>Both streams, and standard error first when they are searched together.</strong>
 * Percolator prints its version banner and every diagnostic on <em>standard error</em> and leaves
 * standard output empty for a {@code -X} run; Comet prints its banner on standard error for {@code
 * -h} and on standard output for {@code -q}. A probe that reads standard output alone sees an empty
 * string and concludes the binary said nothing, which is the assumption this phase's work log names
 * as one a later agent re-introduces.
 *
 * <p><strong>The exit code is optional because a run that never finished has none.</strong> A
 * timeout is not exit code 1: "we asked and it never answered" and "it answered no" are different
 * facts, and {@code R-TOOL-08} turns on the difference -- absent positive evidence a capability is
 * absent, but a probe that got no answer at all has not established absence either.
 *
 * @param exitCode the process exit code, or empty if the run was cancelled after the timeout
 * @param standardOutput the lines written to standard output, in order, capped
 * @param standardError the lines written to standard error, in order, capped
 */
public record ToolRunOutcome(
        OptionalInt exitCode, List<String> standardOutput, List<String> standardError) {

    /**
     * Validates the outcome and takes immutable copies of both line lists.
     *
     * @throws NullPointerException if any component, or any line, is {@code null}
     */
    public ToolRunOutcome {
        Objects.requireNonNull(exitCode, "exitCode");
        standardOutput = copyOf(standardOutput, "standardOutput");
        standardError = copyOf(standardError, "standardError");
    }

    private static List<String> copyOf(List<String> lines, String field) {
        List<String> copy = new ArrayList<>(Objects.requireNonNull(lines, field));
        for (int index = 0; index < copy.size(); index++) {
            if (copy.get(index) == null) {
                throw new NullPointerException(field + "[" + index + "] must not be null");
            }
        }
        return List.copyOf(copy);
    }

    /**
     * Whether the run was cancelled because it exceeded its timeout.
     *
     * @return {@code true} when there is no exit code
     */
    public boolean timedOut() {
        return exitCode.isEmpty();
    }

    /**
     * Whether the run finished with exit code zero.
     *
     * <p>Never on its own a capability verdict. The real {@code comet.linux.exe} answers {@code -h}
     * with exit <strong>1</strong> and a correct banner, and the real Percolator leaves a
     * <em>zero-byte</em> output file behind when it aborts, so both directions of "exit code 0
     * proves nothing" have been observed in this project's own tools.
     *
     * @return {@code true} when the run finished and its exit code was zero
     */
    public boolean exitedZero() {
        return exitCode.isPresent() && exitCode.getAsInt() == 0;
    }

    /**
     * Both streams, standard error first.
     *
     * @return every line the run printed, error stream first, immutable
     */
    public List<String> errorFirst() {
        List<String> joined = new ArrayList<>(standardError.size() + standardOutput.size());
        joined.addAll(standardError);
        joined.addAll(standardOutput);
        return List.copyOf(joined);
    }

    /**
     * The lines of both streams joined for a diagnostic, error stream first.
     *
     * @return the joined text, one line per element, never {@code null}
     */
    public String joinedOutput() {
        return String.join(System.lineSeparator(), errorFirst());
    }
}
