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

import static org.cometgui.params.comet.validation.Models.COMET;
import static org.cometgui.params.comet.validation.Models.COMET_2026_03_0;
import static org.cometgui.params.comet.validation.Models.METADATA;
import static org.cometgui.params.comet.validation.Models.assertAttached;
import static org.cometgui.params.comet.validation.Models.validate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.value.VariableModCodec;
import org.cometgui.params.comet.value.VariableModification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Rule#VARMOD_RESIDUE_NOT_IN_RELEASE}: a residue character the model's release does not
 * accept is reported at the slot. The codec already refuses such a token when a file is read or
 * written; this is the case it cannot see -- a model built in code, as the editor builds one, that
 * holds a {@code ^} slot for Comet 2026.02.2. Each such value is CONSTRUCTED here by reading the
 * tuple with the 2026.03.0 codec, whose alphabet has the codes, and setting it on a 2026.02.2
 * model.
 */
class ResidueAlphabetRuleTest {

    private static final VariableModCodec NEWER_CODEC =
            VariableModCodec.forVersion(METADATA, COMET_2026_03_0);

    private static CometParameters holding(ToolVersion version, String slot, String tuple) {
        VariableModification value = NEWER_CODEC.parse(slot, tuple);
        return Models.enforced(version)
                .withValue(slot, new ParameterValue.Tuple(value), ValueOrigin.USER);
    }

    @Test
    @DisplayName("^ in a 2026.02.2 model is an error at the slot, with what to write instead")
    void proteinNTerminus() {
        CometParameters model = holding(COMET, "variable_mod02", "42.010565 ^ 0 1 -1 0 0 0.0");
        assertThrows(IllegalArgumentException.class, () -> model.text("variable_mod02"));
        Finding finding = Models.only(validate(model));
        assertAttached(
                finding,
                Rule.VARMOD_RESIDUE_NOT_IN_RELEASE,
                ParameterCategory.VARIABLE_MODS,
                "variable_mod02");
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(
                "variable_mod02 (+42.010565 on protein N-terminus; max 1 per peptide; optional):"
                        + " the residue token \"^\" holds '^', which Comet 2026.02.2 does not"
                        + " accept (its residue alphabet is A-Z, n (N-terminus), c (C-terminus));"
                        + " that release would run the token without a word and never apply the"
                        + " modification, and the file cannot be written; for the protein"
                        + " N-terminus use n with terminal distance 0 from terminus 0 (fields 5"
                        + " and 6: 0 0)",
                finding.message());
    }

    @Test
    @DisplayName("$ and mixed tokens too, active or not; the same values are clean for 2026.03.0")
    void everyFormAndSlot() {
        Finding dollar =
                Models.only(
                        validate(holding(COMET, "variable_mod03", "-0.984016 $ 0 1 -1 0 0 0.0")));
        assertEquals(Rule.VARMOD_RESIDUE_NOT_IN_RELEASE, dollar.rule());
        assertTrue(
                dollar.message()
                        .endsWith(
                                "for the protein C-terminus use c with terminal distance 0 from"
                                        + " terminus 1 (fields 5 and 6: 0 1)"),
                dollar.message());
        for (String tuple :
                List.of(
                        "42.010565 n^ 0 1 -1 0 0 0.0",
                        "42.010565 K^c$ 0 1 -1 0 0 0.0",
                        "15.9949 M$ 0 3 -1 0 0 0.0",
                        "0.0 ^ 0 3 -1 0 0 0.0")) {
            for (int slot = 1; slot <= 15; slot++) {
                String name = String.format(java.util.Locale.ROOT, "variable_mod%02d", slot);
                List<Finding> older =
                        validate(holding(COMET, name, tuple))
                                .of(Rule.VARMOD_RESIDUE_NOT_IN_RELEASE);
                assertEquals(1, older.size(), name + " " + tuple);
                assertEquals(name, older.get(0).parameters().get(0));
                CometParameters newer = holding(COMET_2026_03_0, name, tuple);
                assertEquals(
                        List.of(),
                        validate(newer).of(Rule.VARMOD_RESIDUE_NOT_IN_RELEASE),
                        name + " " + tuple);
            }
        }
        assertEquals(
                List.of(),
                validate(holding(COMET_2026_03_0, "variable_mod02", "42.010565 ^ 0 1 -1 0 0 0.0"))
                        .findings());
    }

    @Test
    @DisplayName("2024.01.0 has no ^ either; letters and n, c are every release's")
    void otherReleases() {
        ToolVersion oldest = ToolVersion.parse("2024.01.0");
        CometParameters model =
                CometParameters.defaults(METADATA, oldest, Models.real().enzymeTable())
                        .withValue(
                                "variable_mod02",
                                new ParameterValue.Tuple(
                                        NEWER_CODEC.parse(
                                                "variable_mod02", "42.010565 ^ 0 1 -1 0 0 0.0")),
                                ValueOrigin.USER);
        List<Finding> found = validate(model).of(Rule.VARMOD_RESIDUE_NOT_IN_RELEASE);
        assertEquals(1, found.size(), found::toString);
        assertTrue(found.get(0).message().contains("Comet 2024.01.0 does not accept"));
        for (ToolVersion version : List.of(COMET, COMET_2026_03_0)) {
            assertEquals(
                    List.of(),
                    validate(holding(version, "variable_mod02", "42.010565 nKc 0 1 -1 0 0 0.0"))
                            .of(Rule.VARMOD_RESIDUE_NOT_IN_RELEASE));
        }
    }

    @Test
    @DisplayName("the other rules still judge such a slot, shown in words")
    void otherRulesStillApply() {
        CometParameters model =
                holding(COMET, "variable_mod02", "42.010565 ^ 0 4,2 -1 0 0 0.0")
                        .withText("max_variable_mods_in_peptide", "1", ValueOrigin.USER);
        ValidationReport report = validate(model);
        assertEquals(1, report.of(Rule.VARMOD_RESIDUE_NOT_IN_RELEASE).size(), report::toString);
        assertEquals(1, report.of(Rule.VARMOD_COUNT_REVERSED).size(), report::toString);
        assertEquals(1, report.of(Rule.VARMODS_MINIMUM_ABOVE_LIMIT).size(), report::toString);
        assertTrue(
                report.of(Rule.VARMODS_MINIMUM_ABOVE_LIMIT)
                        .get(0)
                        .message()
                        .startsWith("variable_mod02 (+42.010565 on protein N-terminus; 4 to 2"),
                report::toString);
    }
}
