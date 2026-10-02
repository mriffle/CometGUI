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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.VersionRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What an import leaves on the model: an unknown parameter is a warning ({@code R-PARAM-07}), a
 * parameter of another Comet version is blocked ({@code R-PARAM-07} with the specification's
 * "blocked rather than ignored"), and the parse's other warnings are carried into the report. Every
 * file is the REAL {@code -q} output with a CONSTRUCTED edit.
 */
class ImportedRulesTest {

    private static CometParameters importing(String text) {
        return Models.parse(Models.METADATA, text).withWorkflowEnforcedOutputs();
    }

    @Test
    @DisplayName("an unknown parameter is a warning naming it and its line; removing it clears it")
    void unknown() {
        CometParameters model =
                importing(ParamsFiles.completeWith("decoy_search", "my_option = 3\n"));
        ValidationReport report = Models.validate(model);
        Finding finding = Models.only(report);
        assertEquals(Rule.UNKNOWN_PARAMETER, finding.rule());
        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(List.of("my_option"), finding.parameters());
        assertEquals(Optional.empty(), finding.category());
        int line = model.unknownParameters().get(0).line();
        assertEquals(
                "line "
                        + line
                        + ": my_option = 3 is not a parameter CometGUI models for Comet 2026.02.2;"
                        + " it is kept as imported and written back unless you remove it",
                finding.message());
        assertFalse(report.hasErrors());
        assertEquals(
                1,
                model.diagnostics().stream()
                        .filter(d -> d.code() == Diagnostic.Code.UNKNOWN_PARAMETER)
                        .count());
        assertEquals(List.of(), Models.validate(model.withoutUnknown("my_option")).findings());
    }

    @Test
    @DisplayName("a parameter of other Comet versions is an error: blocked, not ignored")
    void otherVersion() {
        CuratedMetadata later =
                Models.redefine(
                        "max_duplicate_proteins",
                        d ->
                                Models.withVersions(
                                        d,
                                        new VersionRange(
                                                ToolVersion.parse("2027.01.0"), Optional.empty())));
        CometParameters model =
                Models.parse(later, ParamsFiles.complete()).withWorkflowEnforcedOutputs();
        assertEquals(
                List.of(Diagnostic.Code.NOT_IN_VERSION),
                model.diagnostics().stream().map(Diagnostic::code).toList());
        Finding finding = Models.only(Models.validate(model));
        assertEquals(Rule.UNAVAILABLE_IN_VERSION, finding.rule());
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(List.of("max_duplicate_proteins"), finding.parameters());
        assertEquals(Optional.empty(), finding.category());
        assertTrue(
                finding.message()
                        .endsWith(
                                ": max_duplicate_proteins = 10 is a parameter of other Comet"
                                        + " versions, not of Comet 2026.02.2, which would ignore"
                                        + " it; remove it, or select a Comet version that has"
                                        + " it"),
                finding.message());
        assertTrue(Models.validate(model).hasErrors());
        assertEquals(
                List.of(),
                Models.validate(model.withoutUnknown("max_duplicate_proteins")).findings());
    }

    @Test
    @DisplayName("a version marker naming another version is carried in, unchanged")
    void versionMismatch() {
        String text = ParamsFiles.complete();
        CometParameters model =
                importing("# comet_version 2025.01 rev. 0" + text.substring(text.indexOf('\n')));
        Diagnostic diagnostic = model.diagnostics().get(0);
        assertEquals(Diagnostic.Code.VERSION_MISMATCH, diagnostic.code());
        Finding finding = Models.only(Models.validate(model));
        assertEquals(Rule.IMPORT_DIAGNOSTIC, finding.rule());
        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(diagnostic.message(), finding.message());
        assertEquals(List.of(), finding.parameters());
        assertEquals(Optional.empty(), finding.category());
    }

    @Test
    @DisplayName("a missing version marker is carried in too")
    void markerMissing() {
        String text = ParamsFiles.complete();
        CometParameters model = importing(text.substring(text.indexOf('\n') + 1));
        assertEquals(
                List.of(Rule.IMPORT_DIAGNOSTIC),
                Models.validate(model).findings().stream().map(Finding::rule).toList());
    }
}
