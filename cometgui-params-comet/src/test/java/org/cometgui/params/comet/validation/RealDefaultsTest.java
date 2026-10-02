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

import static org.cometgui.params.comet.validation.Models.assertAttached;
import static org.cometgui.params.comet.validation.Models.only;
import static org.cometgui.params.comet.validation.Models.validate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.value.EnzymeTableCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Acceptance condition 4: Comet's own defaults, as the REAL 2026.02.2 binary writes them with
 * {@code -q} and {@code -p}, validate with no errors once the workflow's outputs are enforced --
 * and the one thing standing between the raw file and that is the output {@code R-CMT-01} forces
 * on.
 */
class RealDefaultsTest {

    private static void print(String what, ValidationReport report) {
        System.out.println(
                "[validation] "
                        + what
                        + ": "
                        + report.errors().size()
                        + " error(s), "
                        + report.warnings().size()
                        + " warning(s)");
        for (Finding finding : report.findings()) {
            System.out.println(
                    "[validation]   "
                            + finding.severity()
                            + " "
                            + finding.rule().id()
                            + " "
                            + finding.parameters()
                            + ": "
                            + finding.message());
        }
    }

    @Test
    @DisplayName("the -q file as Comet writes it: exactly one error, output_percolatorfile = 0")
    void rawQueryFile() {
        ValidationReport report = validate(Models.real());
        print("comet -q, as written", report);
        Finding finding = only(report);
        assertAttached(
                finding,
                Rule.WORKFLOW_OUTPUT_OFF,
                ParameterCategory.OUTPUT,
                "output_percolatorfile");
        assertTrue(finding.message().contains("Percolator"), finding.message());
        assertTrue(report.hasErrors());
    }

    @Test
    @DisplayName("the -q file with the workflow's outputs enforced: no errors and no warnings")
    void enforcedQueryFile() {
        ValidationReport report = validate(Models.enforced());
        print("comet -q, outputs enforced", report);
        assertEquals(List.of(), report.findings());
        assertFalse(report.hasErrors());
    }

    @Test
    @DisplayName("the -p file, and the schema defaults with the real table: the same")
    void otherDefaults() {
        CometParameters partial = Models.parse(Models.METADATA, ParamsFiles.defaults());
        assertEquals(
                List.of(Rule.WORKFLOW_OUTPUT_OFF),
                validate(partial).findings().stream().map(Finding::rule).toList());
        assertEquals(List.of(), validate(partial.withWorkflowEnforcedOutputs()).findings());
        CometParameters schema =
                CometParameters.defaults(
                        Models.METADATA,
                        Models.COMET,
                        EnzymeTableCodec.parse(
                                ParamsLineReader.read(ParamsFiles.complete()).enzymeRows()));
        assertEquals(
                List.of(Rule.WORKFLOW_OUTPUT_OFF),
                validate(schema).findings().stream().map(Finding::rule).toList());
        assertEquals(List.of(), validate(schema.withWorkflowEnforcedOutputs()).findings());
    }
}
