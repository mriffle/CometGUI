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

import static org.cometgui.ui.viewmodel.params.Sessions.METADATA;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.VisibilityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Essentials is a curated, task-ordered surface whose parameter set equals the metadata's
 * ESSENTIALS set exactly; Advanced holds every parameter of the release once, in the fourteen
 * categories (AC-PAR-04). Both are asserted for both offered releases.
 */
class EssentialsAndAdvancedTest {

    /** The Essentials order, typed by hand: the curation, not a derivation of it. */
    private static final List<String> ESSENTIALS_ORDER =
            List.of(
                    // Inputs
                    "database_name",
                    // Precursor
                    "peptide_mass_tolerance_lower",
                    "peptide_mass_tolerance_upper",
                    "peptide_mass_units",
                    "precursor_tolerance_type",
                    "isotope_error",
                    // Fragment ions
                    "fragment_bin_tol",
                    "fragment_bin_offset",
                    // Digestion
                    "search_enzyme_number",
                    "num_enzyme_termini",
                    "allowed_missed_cleavage",
                    // Static modifications
                    "add_Cterm_peptide",
                    "add_Nterm_peptide",
                    "add_Cterm_protein",
                    "add_Nterm_protein",
                    "add_G_glycine",
                    "add_A_alanine",
                    "add_S_serine",
                    "add_P_proline",
                    "add_V_valine",
                    "add_T_threonine",
                    "add_C_cysteine",
                    "add_L_leucine",
                    "add_I_isoleucine",
                    "add_N_asparagine",
                    "add_D_aspartic_acid",
                    "add_Q_glutamine",
                    "add_K_lysine",
                    "add_E_glutamic_acid",
                    "add_M_methionine",
                    "add_H_histidine",
                    "add_F_phenylalanine",
                    "add_U_selenocysteine",
                    "add_R_arginine",
                    "add_Y_tyrosine",
                    "add_W_tryptophan",
                    "add_O_pyrrolysine",
                    // Variable modifications
                    "variable_mod01",
                    "variable_mod02",
                    "variable_mod03",
                    "variable_mod04",
                    "variable_mod05",
                    "variable_mod06",
                    "variable_mod07",
                    "variable_mod08",
                    "variable_mod09",
                    "variable_mod10",
                    "variable_mod11",
                    "variable_mod12",
                    "variable_mod13",
                    "variable_mod14",
                    "variable_mod15",
                    // Decoys
                    "decoy_search",
                    "decoy_prefix",
                    // Execution
                    "num_threads",
                    // Outputs
                    "output_pepxmlfile",
                    "output_percolatorfile");

    @ParameterizedTest(
            name = "Comet {0}: Essentials is the metadata's ESSENTIALS set, in task order")
    @ValueSource(strings = {"2026.03.0", "2026.02.2"})
    void essentialsEqualsTheMetadataSet(String version) {
        ToolVersion release = ToolVersion.parse(version);
        List<EssentialsGroup> groups = startingIn(release).essentials();

        assertEquals(
                List.of(
                        "Inputs",
                        "Search preset",
                        "Precursor",
                        "Fragment ions",
                        "Digestion",
                        "Static modifications",
                        "Variable modifications",
                        "Decoys",
                        "Execution",
                        "Outputs"),
                groups.stream().map(EssentialsGroup::title).toList());
        assertEquals(
                List.of(EssentialsSection.values()),
                groups.stream().map(EssentialsGroup::section).toList());
        List<String> shown = new ArrayList<>();
        for (EssentialsGroup group : groups) {
            for (FieldViewModel field : group.fields()) {
                shown.add(field.name());
            }
        }
        assertEquals(ESSENTIALS_ORDER, shown);
        assertEquals(57, shown.size());
        assertEquals(List.of(), groups.get(1).fields());
        assertEquals(15, groups.get(6).fields().size());

        Set<String> metadataEssentials = new TreeSet<>();
        for (ParameterDefinition definition : METADATA.parametersFor(release)) {
            if (definition.visibility() == VisibilityLevel.ESSENTIALS) {
                metadataEssentials.add(definition.name());
            }
        }
        assertEquals(metadataEssentials, new TreeSet<>(shown));
    }

    @Test
    @DisplayName("a section leaves out what a release does not model, and keeps its own order")
    void sectionOfAReducedRelease() {
        List<ParameterDefinition> reduced = new ArrayList<>();
        for (ParameterDefinition definition :
                METADATA.parametersFor(ToolVersion.parse("2026.03.0"))) {
            if (!definition.name().equals("peptide_mass_units")
                    && !definition.name().equals("variable_mod02")) {
                reduced.add(definition);
            }
        }
        assertEquals(
                List.of(
                        "peptide_mass_tolerance_lower",
                        "peptide_mass_tolerance_upper",
                        "precursor_tolerance_type",
                        "isotope_error"),
                EssentialsSection.PRECURSOR.parameters(reduced));
        assertEquals(14, EssentialsSection.VARIABLE_MODIFICATIONS.parameters(reduced).size());
        assertEquals(List.of(), EssentialsSection.SEARCH_PRESET.parameters(reduced));
    }

    @ParameterizedTest(name = "Comet {0}: Advanced holds every parameter once, in 14 categories")
    @ValueSource(strings = {"2026.03.0", "2026.02.2"})
    void advancedHoldsEveryParameterOnce(String version) {
        ParameterSession session = startingIn(ToolVersion.parse(version));
        List<AdvancedCategory> groups = session.advanced();

        assertEquals(
                List.of(
                        "Database and PEFF",
                        "CPU and execution",
                        "Precursor mass and isotope handling",
                        "Digestion and enzymes",
                        "Fragment-ion scoring",
                        "Fragment-ion and peptide-index search options",
                        "Spectrum, scan and charge filters",
                        "Spectral pre-processing",
                        "Search ranges and peptide constraints",
                        "Output options",
                        "MS1 and real-time-search options",
                        "Static modifications",
                        "Variable modifications",
                        "Miscellaneous and version-specific options"),
                groups.stream().map(AdvancedCategory::title).toList());
        List<String> shown = new ArrayList<>();
        Set<String> expert = new TreeSet<>();
        for (AdvancedCategory group : groups) {
            for (FieldViewModel field : group.fields()) {
                assertEquals(group.category(), field.category(), field.name());
                shown.add(field.name());
                if (field.isExpert()) {
                    expert.add(field.name());
                }
            }
        }
        assertEquals(118, shown.size());
        assertEquals(118, new HashSet<>(shown).size());
        List<String> modelled = new ArrayList<>();
        for (ParameterEntry entry : session.model().entries()) {
            modelled.add(entry.name());
        }
        assertEquals(new TreeSet<>(modelled), new TreeSet<>(shown));
        assertEquals(
                new TreeSet<>(
                        List.of(
                                "index_search_type",
                                "compoundmods_file",
                                "spectral_library_name",
                                "spectral_library_ms_level",
                                "protein_modslist_file",
                                "fragindex_min_ions_score",
                                "fragindex_min_ions_report",
                                "fragindex_num_spectrumpeaks",
                                "fragindex_min_fragmentmass",
                                "fragindex_max_fragmentmass",
                                "fragindex_skipreadprecursors",
                                "print_expect_score",
                                "pinfile_protein_delimiter",
                                "num_results",
                                "mass_offsets")),
                expert);
        AdvancedCategory precursor = groups.get(2);
        assertEquals(ParameterCategory.PRECURSOR_MASS, precursor.category());
        assertEquals(
                List.of(
                        "peptide_mass_tolerance_upper",
                        "peptide_mass_tolerance_lower",
                        "peptide_mass_units",
                        "precursor_tolerance_type",
                        "isotope_error",
                        "mass_type_parent",
                        "mass_offsets"),
                precursor.fields().stream().map(FieldViewModel::name).toList());
        assertTrue(groups.get(13).fields().isEmpty());
        assertFalse(groups.get(12).fields().isEmpty());
    }
}
