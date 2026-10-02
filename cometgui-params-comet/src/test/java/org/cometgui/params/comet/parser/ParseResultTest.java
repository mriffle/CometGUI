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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The all-or-nothing rule is held by the result type itself, not only by the parser. */
class ParseResultTest {

    private static final ImportedComments NO_COMMENTS =
            new ImportedComments(Optional.empty(), Map.of(), List.of(), List.of());

    private static final Diagnostic ERROR =
            Diagnostic.of(Diagnostic.Code.MALFORMED_LINE, List.of(3), null, "bad");

    private static final Diagnostic WARNING =
            Diagnostic.of(Diagnostic.Code.UNKNOWN_PARAMETER, List.of(4), "k", "unknown");

    private static CometParameters model(List<Diagnostic> diagnostics) {
        CometParameters parsed =
                new CometParamsParser(ParamsFiles.metadata(), ParamsFiles.COMET)
                        .parse(ParamsFiles.complete())
                        .model()
                        .orElseThrow();
        return CometParameters.of(
                parsed.metadata(),
                parsed.version(),
                parsed.entries(),
                parsed.enzymeTable(),
                List.of(),
                diagnostics);
    }

    @Test
    @DisplayName("a model alongside an error is refused")
    void modelWithError() {
        assertEquals(
                "a parse with errors produces no model",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ParseResult(
                                                Optional.of(model(List.of())),
                                                List.of(ERROR),
                                                NO_COMMENTS))
                        .getMessage());
    }

    @Test
    @DisplayName("no model without an error is refused")
    void noModelNoError() {
        assertEquals(
                "a parse without errors produces a model",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ParseResult(
                                                Optional.empty(), List.of(WARNING), NO_COMMENTS))
                        .getMessage());
    }

    @Test
    @DisplayName("the model must carry the same warnings")
    void sameWarnings() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ParseResult(
                                Optional.of(model(List.of())), List.of(WARNING), NO_COMMENTS));
        ParseResult result =
                new ParseResult(
                        Optional.of(model(List.of(WARNING))), List.of(WARNING), NO_COMMENTS);
        assertEquals(List.of(WARNING), result.warnings());
        assertEquals(List.of(), result.errors());
        ParseResult failed =
                new ParseResult(Optional.empty(), List.of(WARNING, ERROR), NO_COMMENTS);
        assertEquals(List.of(ERROR), failed.errors());
        assertEquals(List.of(WARNING), failed.warnings());
        assertEquals(List.of(WARNING, ERROR), failed.diagnostics());
    }
}
