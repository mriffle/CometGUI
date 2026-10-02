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

package org.cometgui.params.comet.validation;

import java.util.List;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.UnknownParameter;

/**
 * What an import left on the model: parameters the schema does not model for the selected Comet
 * version, and the parse's other warnings.
 *
 * <ul>
 *   <li>A parameter the metadata models for <em>other</em> Comet versions but not the selected one
 *       is an <strong>error</strong>: it is blocked, not ignored. Comet 2026.02.2 would only log
 *       "invalid parameter found" and carry on without it ({@code Comet.cpp} lines 536-634).
 *   <li>A parameter the metadata does not model at all is a <strong>warning</strong> ({@code
 *       R-PARAM-07}): it is kept and written back unless the user removes it.
 *   <li>Every other warning of the parse that produced the model -- a version marker that names
 *       another version or is missing ({@code R-PARAM-06}) -- is carried into the report as it is.
 * </ul>
 *
 * <p>The first two are decided from the model's current unknown parameters, with the parser's own
 * test (whether the metadata models the name for any version), so that removing one with {@link
 * CometParameters#withoutUnknown(String)} clears its finding; the parse's {@code UNKNOWN_PARAMETER}
 * and {@code NOT_IN_VERSION} diagnostics are therefore not repeated.
 */
final class ImportedRules {

    private ImportedRules() {}

    static void check(CometParameters model, Findings findings) {
        String version = model.version().text();
        for (UnknownParameter unknown : model.unknownParameters()) {
            String name = unknown.name();
            String shown = "line " + unknown.line() + ": " + name + " = " + unknown.value();
            if (model.metadata().parameter(name).isPresent()) {
                findings.addUncategorised(
                        Rule.UNAVAILABLE_IN_VERSION,
                        List.of(name),
                        shown
                                + " is a parameter of other Comet versions, not of Comet "
                                + version
                                + ", which would ignore it; remove it, or select a Comet version"
                                + " that has it");
            } else {
                findings.addUncategorised(
                        Rule.UNKNOWN_PARAMETER,
                        List.of(name),
                        shown
                                + " is not a parameter CometGUI models for Comet "
                                + version
                                + "; it is kept as imported and written back unless you remove"
                                + " it");
            }
        }
        for (Diagnostic diagnostic : model.diagnostics()) {
            Diagnostic.Code code = diagnostic.code();
            if (code == Diagnostic.Code.UNKNOWN_PARAMETER
                    || code == Diagnostic.Code.NOT_IN_VERSION) {
                continue;
            }
            findings.addUncategorised(
                    Rule.IMPORT_DIAGNOSTIC,
                    diagnostic.parameter().stream().toList(),
                    diagnostic.message());
        }
    }
}
