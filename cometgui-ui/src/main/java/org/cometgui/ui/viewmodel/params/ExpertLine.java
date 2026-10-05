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

import java.util.List;
import java.util.Objects;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.parser.ParamsHighlighting;

/**
 * One line of the Expert draft as the editor shows it: the model's classification of the line (its
 * kind and the spans to colour) and every diagnostic of the draft's parse that names it.
 *
 * @param line the line, classified by the model
 * @param diagnostics the parse's diagnostics naming this line, in the parse's order
 */
public record ExpertLine(ParamsHighlighting.Line line, List<Diagnostic> diagnostics) {

    /** Validates presence and takes an immutable copy. */
    public ExpertLine {
        Objects.requireNonNull(line, "line");
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * The line's state in words, so that it is never conveyed by colour alone (exit gate item 7).
     *
     * @return {@code Error}, {@code Warning}, or empty when nothing names the line
     */
    public String stateText() {
        if (diagnostics.stream().anyMatch(Diagnostic::isError)) {
            return FieldState.ERROR.words();
        }
        return diagnostics.isEmpty() ? "" : FieldState.WARNING.words();
    }
}
