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

package org.cometgui.tools.comet;

import java.nio.file.Path;
import java.util.Objects;

/**
 * What a valid pepXML file says about itself.
 *
 * @param file the pepXML file
 * @param input the spectrum file it names as its input: {@code msms_run_summary}'s {@code
 *     base_name} followed by its {@code raw_data} extension
 * @param outputBase the output base it names: {@code search_summary}'s {@code base_name}, which is
 *     Comet's {@code -N}
 * @param spectrumQueries how many {@code spectrum_query} elements it holds
 */
public record PepXmlSummary(Path file, String input, String outputBase, long spectrumQueries) {

    /**
     * Validates the summary.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the count is negative
     */
    public PepXmlSummary {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(outputBase, "outputBase");
        if (spectrumQueries < 0) {
            throw new IllegalArgumentException("a count cannot be negative: " + spectrumQueries);
        }
    }
}
