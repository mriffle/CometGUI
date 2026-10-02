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

import java.util.Map;
import java.util.Optional;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;

/**
 * Validator {@code workflow_enforced}: the Comet outputs the CometGUI workflow's downstream stages
 * read ({@code R-CMT-01}) are switched on.
 *
 * <p>Which parameters are required is the metadata's ({@code validators} names {@code
 * workflow_enforced}); which stage needs each is recorded here, so that the error -- and the
 * editor's "Required by CometGUI workflow" text -- can name it. The model operation that switches
 * them on, with origin {@code WORKFLOW_ENFORCED}, is {@link
 * CometParameters#withWorkflowEnforcedOutputs()}.
 *
 * <p>Whether a stage is enabled for a particular run, and so whether its output may be switched off
 * ({@code AC-PAR-09}), is the editor's and the workflow's (Phases 07 and 08): at the model level
 * every stage of the workflow is assumed enabled.
 */
public final class WorkflowOutputs {

    private static final Map<String, String> STAGES =
            Map.of(
                    "output_pepxmlfile",
                    "PDV, which shows the spectra, and the Limelight export both read the pepXML"
                            + " file",
                    "output_percolatorfile",
                    "Percolator rescoring reads the .pin file");

    private WorkflowOutputs() {}

    /**
     * The downstream stage that needs a parameter switched on.
     *
     * @param parameter the parameter name
     * @return the stage and what it reads, in words; empty for a parameter no stage needs
     */
    public static Optional<String> stageNeeding(String parameter) {
        return Optional.ofNullable(STAGES.get(parameter));
    }

    static void check(CometParameters model, ParameterEntry entry, Findings findings) {
        String name = entry.name();
        String stage =
                stageNeeding(name)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                name
                                                        + " is marked workflow_enforced, but no"
                                                        + " downstream stage is recorded as"
                                                        + " needing it"));
        if (!(entry.value() instanceof ParameterValue.Flag flag)) {
            throw new IllegalStateException(
                    name
                            + " is marked workflow_enforced but is of kind "
                            + entry.definition().kind()
                            + "; only an on/off flag can be required on");
        }
        if (!flag.on()) {
            findings.add(
                    Rule.WORKFLOW_OUTPUT_OFF,
                    name,
                    name
                            + " = 0, but the CometGUI workflow needs it on: "
                            + stage
                            + ". Set it to 1; the workflow sets it when it prepares a run");
        }
    }
}
