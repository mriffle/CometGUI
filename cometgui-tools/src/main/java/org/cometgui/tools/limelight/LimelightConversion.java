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

package org.cometgui.tools.limelight;

import java.util.List;
import java.util.Objects;
import org.cometgui.tools.api.ToolRunOutcome;

/**
 * A conversion that produced a valid Limelight XML file, now at the request's output path.
 *
 * @param request what was asked for
 * @param argv the whole argument array that ran, launcher first
 * @param outcome what the converter printed and its exit code
 * @param output what the file at {@link LimelightConversionRequest#output()} holds
 */
public record LimelightConversion(
        LimelightConversionRequest request,
        List<String> argv,
        ToolRunOutcome outcome,
        LimelightXmlSummary output) {

    /**
     * Validates the conversion and copies the argument array.
     *
     * @throws NullPointerException if a component or an argument is {@code null}
     */
    public LimelightConversion {
        Objects.requireNonNull(request, "request");
        argv = List.copyOf(Objects.requireNonNull(argv, "argv"));
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(output, "output");
    }

    /**
     * The argument array that ran.
     *
     * @return the arguments, immutable
     */
    @Override
    public List<String> argv() {
        return List.copyOf(argv);
    }
}
