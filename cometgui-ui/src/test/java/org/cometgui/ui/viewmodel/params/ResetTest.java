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

import static org.cometgui.ui.viewmodel.params.Sessions.C02;
import static org.cometgui.ui.viewmodel.params.Sessions.C03;
import static org.cometgui.ui.viewmodel.params.Sessions.METADATA;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Reset of a field, a category and the whole configuration: values and origins after each. */
class ResetTest {

    @ParameterizedTest(name = "Comet {0}: a field goes back to the release's default")
    @CsvSource({"2026.03.0, -1", "2026.02.2, 1"})
    void field(String release, String indexDefault) {
        ParameterSession session = startingIn(ToolVersion.parse(release));
        session.edit("allowed_missed_cleavage", "1");
        session.edit("index_search_type", "0");
        session.edit("num_threads", "4");

        assertEquals(EditOutcome.applied(), session.resetField("allowed_missed_cleavage"));
        assertEquals(EditOutcome.applied(), session.field("index_search_type").reset());

        assertEquals("2", session.model().text("allowed_missed_cleavage"));
        assertEquals(ValueOrigin.COMET_DEFAULT, session.model().origin("allowed_missed_cleavage"));
        assertEquals(indexDefault, session.model().text("index_search_type"));
        assertEquals(ValueOrigin.COMET_DEFAULT, session.model().origin("index_search_type"));
        assertEquals("4", session.model().text("num_threads"));
        assertEquals(ValueOrigin.USER, session.model().origin("num_threads"));
    }

    @Test
    @DisplayName("a category: every field in it, and nothing outside it")
    void category() {
        ParameterSession session = startingIn(C03);
        session.edit("peptide_mass_tolerance_lower", "-10.0");
        session.edit("peptide_mass_tolerance_upper", "10.0");
        session.edit("peptide_mass_units", "0");
        session.edit("isotope_error", "0");
        session.edit("allowed_missed_cleavage", "1");
        session.edit("peptide_mass_units", "x");

        List<String> reset = session.resetCategory(ParameterCategory.PRECURSOR_MASS);

        assertEquals(
                List.of(
                        "peptide_mass_tolerance_upper",
                        "peptide_mass_tolerance_lower",
                        "peptide_mass_units",
                        "precursor_tolerance_type",
                        "isotope_error",
                        "mass_type_parent",
                        "mass_offsets"),
                reset);
        assertEquals("-20.0", session.model().text("peptide_mass_tolerance_lower"));
        assertEquals("20.0", session.model().text("peptide_mass_tolerance_upper"));
        assertEquals("2", session.model().text("peptide_mass_units"));
        assertEquals("2", session.model().text("isotope_error"));
        for (String name : reset) {
            assertEquals(ValueOrigin.COMET_DEFAULT, session.model().origin(name), name);
        }
        assertEquals("2", session.field("peptide_mass_units").text());
        assertEquals(Optional.empty(), session.field("peptide_mass_units").refusal());
        assertEquals("1", session.model().text("allowed_missed_cleavage"));
        assertEquals(ValueOrigin.USER, session.model().origin("allowed_missed_cleavage"));
    }

    @Test
    @DisplayName("the output category leaves the locked outputs on")
    void outputCategory() {
        ParameterSession session = startingIn(C03);
        session.edit("output_txtfile", "1");

        List<String> reset = session.resetCategory(ParameterCategory.OUTPUT);

        assertTrue(reset.contains("output_txtfile"));
        assertTrue(
                !reset.contains("output_percolatorfile") && !reset.contains("output_pepxmlfile"));
        assertEquals("0", session.model().text("output_txtfile"));
        assertEquals("1", session.model().text("output_percolatorfile"));
        assertEquals(
                ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_percolatorfile"));
        assertEquals(ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_pepxmlfile"));
    }

    @ParameterizedTest(name = "Comet {0}: the whole configuration goes back to the release's set")
    @CsvSource({"2026.03.0", "2026.02.2"})
    void whole(String version) {
        ToolVersion release = ToolVersion.parse(version);
        ParameterSession session = startingIn(release);
        session.edit("num_threads", "4");
        session.edit("decoy_prefix", "REV_");
        session.adopt(
                session.model()
                        .withEnzymeTable(
                                session.model()
                                        .enzymeTable()
                                        .with(
                                                new EnzymeDefinition(
                                                        12,
                                                        "Glu_C",
                                                        EnzymeDefinition.Sense.AFTER_RESIDUE,
                                                        "DE",
                                                        "P"))),
                Adoption.RAW_APPLIED);
        assertEquals(13, session.model().enzymeTable().rows().size());

        session.resetAll();

        assertEquals(
                ReleaseDefaults.load(METADATA, release).withWorkflowEnforcedOutputs(),
                session.model());
        assertEquals("0", session.model().text("num_threads"));
        assertEquals(ValueOrigin.COMET_DEFAULT, session.model().origin("num_threads"));
        assertEquals("DECOY_", session.model().text("decoy_prefix"));
        assertEquals(12, session.model().enzymeTable().rows().size());
        assertEquals(
                ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_percolatorfile"));
        assertEquals(release, session.release());
    }

    @Test
    @DisplayName("after a release switch, the whole reset is the new release's set")
    void wholeAfterSwitch() {
        ParameterSession session = startingIn(C03);
        session.selectRelease(C02);
        session.resetAll();
        assertEquals(C02, session.release());
        assertEquals("1", session.model().text("index_search_type"));
        assertEquals(Optional.empty(), session.review());
    }
}
