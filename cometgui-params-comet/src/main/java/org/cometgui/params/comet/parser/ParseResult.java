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

package org.cometgui.params.comet.parser;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;

/**
 * What a parse of a {@code comet.params} text produced: a model, or none, and every finding.
 *
 * <p>All or nothing ({@code R-PARAM-08}): a text with any {@link Diagnostic.Severity#ERROR} yields
 * no model at all, so a caller that applies a parse only when it succeeded can never leave its
 * typed model half-updated. A successful parse carries its warnings both here and on the model.
 *
 * @param model the model, present exactly when no finding is an error
 * @param diagnostics every finding, ordered by the first line it concerns; findings about the file
 *     as a whole first
 * @param comments the file's comment structure as imported ({@code R-PARAM-05}), kept whether or
 *     not the parse succeeded
 */
public record ParseResult(
        Optional<CometParameters> model, List<Diagnostic> diagnostics, ImportedComments comments) {

    /**
     * Validates the all-or-nothing rule.
     *
     * @throws IllegalArgumentException if a model is present alongside an error, absent without
     *     one, or carries other diagnostics than these
     */
    public ParseResult {
        Objects.requireNonNull(model, "model");
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(comments, "comments");
        boolean failed = diagnostics.stream().anyMatch(Diagnostic::isError);
        if (failed == model.isPresent()) {
            throw new IllegalArgumentException(
                    failed
                            ? "a parse with errors produces no model"
                            : "a parse without errors produces a model");
        }
        if (model.isPresent() && !model.get().diagnostics().equals(diagnostics)) {
            throw new IllegalArgumentException("the model carries other diagnostics than these");
        }
    }

    /**
     * The findings, immutable.
     *
     * @return every diagnostic
     */
    @Override
    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    /**
     * Whether the parse produced a model.
     *
     * @return {@code true} if no finding is an error
     */
    public boolean succeeded() {
        return model.isPresent();
    }

    /**
     * The findings that stopped the parse.
     *
     * @return the errors, in order
     */
    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(Diagnostic::isError).toList();
    }

    /**
     * The findings that did not.
     *
     * @return the warnings, in order
     */
    public List<Diagnostic> warnings() {
        return diagnostics.stream().filter(d -> !d.isError()).toList();
    }
}
