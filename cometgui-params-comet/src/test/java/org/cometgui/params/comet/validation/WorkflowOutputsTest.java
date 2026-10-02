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
import static org.cometgui.params.comet.validation.Models.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValidatorId;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code R-CMT-01}: the outputs the workflow's downstream stages read are required on, reported as
 * an error naming the stage when off, and switched on by a model operation whose origin is {@code
 * WORKFLOW_ENFORCED}.
 */
class WorkflowOutputsTest {

    private static final String PEPXML = "output_pepxmlfile";

    private static final String PIN = "output_percolatorfile";

    @Test
    @DisplayName("the metadata requires exactly pepXML and PIN, and each has a stage")
    void metadata() {
        Set<String> enforced =
                Models.METADATA.parameters().stream()
                        .filter(p -> p.validators().contains(ValidatorId.WORKFLOW_ENFORCED))
                        .map(ParameterDefinition::name)
                        .collect(Collectors.toSet());
        assertEquals(Set.of(PEPXML, PIN), enforced);
        assertTrue(WorkflowOutputs.stageNeeding(PEPXML).orElseThrow().contains("PDV"));
        assertTrue(WorkflowOutputs.stageNeeding(PEPXML).orElseThrow().contains("Limelight"));
        assertTrue(WorkflowOutputs.stageNeeding(PIN).orElseThrow().contains("Percolator"));
        assertEquals(Optional.empty(), WorkflowOutputs.stageNeeding("output_txtfile"));
    }

    @Test
    @DisplayName("each output switched off is an error naming the stage that needs it")
    void offIsAnError() {
        Finding pepxml = only(validate(with(PEPXML, "0")));
        assertAttached(pepxml, Rule.WORKFLOW_OUTPUT_OFF, ParameterCategory.OUTPUT, PEPXML);
        assertEquals(
                PEPXML
                        + " = 0, but the CometGUI workflow needs it on: PDV, which shows the"
                        + " spectra, and the Limelight export both read the pepXML file. Set it to"
                        + " 1; the workflow sets it when it prepares a run",
                pepxml.message());
        Finding pin = only(validate(with(PIN, "0")));
        assertAttached(pin, Rule.WORKFLOW_OUTPUT_OFF, ParameterCategory.OUTPUT, PIN);
        assertTrue(pin.message().contains("Percolator rescoring reads the .pin file"));
        assertEquals(2, validate(with(PIN, "0", PEPXML, "0")).errors().size());
    }

    @Test
    @DisplayName("enforcing switches both on as the application's, and changes nothing else")
    void enforcing() {
        CometParameters raw = Models.real();
        CometParameters enforced = raw.withWorkflowEnforcedOutputs();
        for (String name : List.of(PEPXML, PIN)) {
            assertEquals(new ParameterValue.Flag(true), enforced.value(name), name);
            assertEquals(ValueOrigin.WORKFLOW_ENFORCED, enforced.origin(name), name);
            assertEquals("1", enforced.text(name));
        }
        assertEquals(new ParameterValue.Flag(false), raw.value(PIN));
        assertEquals(ValueOrigin.IMPORTED, raw.origin(PIN));
        assertNotEquals(raw, enforced);
        for (ParameterEntry entry : raw.entries()) {
            if (!Set.of(PEPXML, PIN).contains(entry.name())) {
                assertEquals(entry, enforced.entry(entry.name()).orElseThrow(), entry.name());
            }
        }
        assertEquals(raw.enzymeTable(), enforced.enzymeTable());
        assertEquals(enforced, enforced.withWorkflowEnforcedOutputs());
        String written = new CanonicalParamsWriter(ParamsFiles.build()).write(enforced);
        assertTrue(written.contains("\noutput_percolatorfile = 1 "), written);
        assertTrue(written.contains("\noutput_pepxmlfile = 1 "), written);
    }

    @Test
    @DisplayName("CONSTRUCTED metadata marking a non-flag is refused by the model and the rule")
    void notAFlag() {
        CuratedMetadata bad =
                Models.redefine(
                        "num_threads",
                        d -> Models.withValidators(d, List.of(ValidatorId.WORKFLOW_ENFORCED)));
        CometParameters model =
                CometParameters.defaults(bad, Models.COMET, Models.real().enzymeTable());
        IllegalStateException byModel =
                assertThrows(IllegalStateException.class, model::withWorkflowEnforcedOutputs);
        assertEquals(
                "num_threads is marked workflow_enforced but is of kind INTEGER; only an on/off"
                        + " flag can be switched on by the workflow",
                byModel.getMessage());
        IllegalStateException byRule =
                assertThrows(
                        IllegalStateException.class,
                        () -> CometValidator.standard().validate(model));
        assertEquals(
                "num_threads is marked workflow_enforced, but no downstream stage is recorded as"
                        + " needing it",
                byRule.getMessage());
    }

    @Test
    @DisplayName("CONSTRUCTED metadata giving a required output another kind is refused")
    void requiredOutputNotAFlag() {
        CuratedMetadata bad = Models.redefine(PEPXML, d -> Models.withKind(d, ValueKind.INTEGER));
        CometParameters model =
                CometParameters.defaults(bad, Models.COMET, Models.real().enzymeTable());
        IllegalStateException refused =
                assertThrows(
                        IllegalStateException.class,
                        () -> CometValidator.standard().validate(model));
        assertEquals(
                PEPXML
                        + " is marked workflow_enforced but is of kind INTEGER; only an on/off"
                        + " flag can be required on",
                refused.getMessage());
        assertThrows(IllegalStateException.class, model::withWorkflowEnforcedOutputs);
    }

    @Test
    @DisplayName("CONSTRUCTED metadata marking a flag no stage needs is refused by the rule")
    void noStage() {
        CuratedMetadata bad =
                Models.redefine(
                        "output_sqtfile",
                        d -> Models.withValidators(d, List.of(ValidatorId.WORKFLOW_ENFORCED)));
        CometParameters model =
                CometParameters.defaults(bad, Models.COMET, Models.real().enzymeTable());
        IllegalStateException refused =
                assertThrows(
                        IllegalStateException.class,
                        () -> CometValidator.standard().validate(model));
        assertEquals(
                "output_sqtfile is marked workflow_enforced, but no downstream stage is recorded"
                        + " as needing it",
                refused.getMessage());
        assertEquals(
                ValueOrigin.WORKFLOW_ENFORCED,
                model.withWorkflowEnforcedOutputs().origin("output_sqtfile"));
    }
}
