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

import java.util.ArrayList;
import java.util.List;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValueKind;

/**
 * The Essentials surface: the specification's common workflow-defining controls, in the order a
 * scientist sets up a search -- a curated, task-ordered surface, not a filter of visibility.
 *
 * <p>Each section names its parameters, in the order a scientist meets them; the static
 * modifications keep Comet's own order (termini, then residues by mass), and leave out the four
 * user-defined residues B, J, X and Z, which belong to Advanced mode. The variable-modification
 * section alone takes its parameters from the release's schema, because the number of slots is the
 * release's ({@code R-PARAM-09}): every parameter of kind {@link ValueKind#VARIABLE_MOD_TUPLE}, in
 * the release's order. {@link #SEARCH_PRESET} has no parameter of its own (a preset is a delta over
 * many), and the spectrum files of {@link #INPUTS} are run inputs, not parameters (decision P7-9).
 *
 * <p>The union of the sections is held equal to the metadata's {@code ESSENTIALS} visibility set by
 * a test, for every offered release, so the two cannot drift apart unnoticed.
 */
public enum EssentialsSection {

    /** The spectra to search and the database to search them against. */
    INPUTS(
            "Inputs",
            "The spectrum files to search and the protein sequence database to search them"
                    + " against.",
            List.of("database_name")),

    /** A starting point for the instrument and search type. */
    SEARCH_PRESET(
            "Search preset",
            "Start from an instrument and search preset; you see what it changes before it is"
                    + " applied.",
            List.of()),

    /** The precursor mass window and isotope handling. */
    PRECURSOR(
            "Precursor",
            "How far a peptide's mass may be from the measured precursor mass, in which units, and"
                    + " which isotope peaks are considered.",
            List.of(
                    "peptide_mass_tolerance_lower",
                    "peptide_mass_tolerance_upper",
                    "peptide_mass_units",
                    "precursor_tolerance_type",
                    "isotope_error")),

    /** The fragment-ion bins, which set the fragment tolerance. */
    FRAGMENT(
            "Fragment ions",
            "The fragment mass tolerance, as Comet's bin width and offset: a wide bin for"
                    + " low-resolution (ion trap) spectra, a narrow one for high-resolution"
                    + " spectra.",
            List.of("fragment_bin_tol", "fragment_bin_offset")),

    /** The enzyme and how strictly it must have cut. */
    DIGESTION(
            "Digestion",
            "The enzyme that digested the sample, how many peptide termini must be enzymatic, and"
                    + " how many cleavage sites may be missed.",
            List.of("search_enzyme_number", "num_enzyme_termini", "allowed_missed_cleavage")),

    /** Fixed mass changes on residues and termini. */
    STATIC_MODIFICATIONS(
            "Static modifications",
            "Mass changes applied to every occurrence of a residue or terminus, such as"
                    + " carbamidomethyl cysteine.",
            List.of(
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
                    "add_O_pyrrolysine")),

    /** Optional mass changes, one slot each. */
    VARIABLE_MODIFICATIONS(
            "Variable modifications",
            "Mass changes a residue or terminus may or may not carry, such as oxidised"
                    + " methionine; one slot per modification.",
            List.of()),

    /** Where decoys come from, and how they are named. */
    DECOYS(
            "Decoys",
            "Where the decoy sequences Percolator learns from come from, and the prefix that marks"
                    + " a decoy protein.",
            List.of("decoy_search", "decoy_prefix")),

    /** How many threads the search uses. */
    EXECUTION(
            "Execution",
            "How many search threads Comet runs; 0 chooses one per processor core.",
            List.of("num_threads")),

    /** The outputs the workflow requires. */
    OUTPUTS(
            "Outputs",
            "The result files the CometGUI workflow needs: pepXML and the Percolator input (PIN)"
                    + " are required.",
            List.of("output_pepxmlfile", "output_percolatorfile"));

    private final String title;

    private final String purpose;

    private final List<String> named;

    EssentialsSection(String title, String purpose, List<String> named) {
        this.title = title;
        this.purpose = purpose;
        this.named = named;
    }

    /**
     * The section heading.
     *
     * @return for example {@code Precursor}
     */
    public String title() {
        return title;
    }

    /**
     * What the section is for, in a sentence.
     *
     * @return the sentence
     */
    public String purpose() {
        return purpose;
    }

    /**
     * The section's parameters for one release, in the section's order.
     *
     * @param release every parameter the release models, as it has them, in its order
     * @return the names, those the release does not model left out
     */
    public List<String> parameters(List<ParameterDefinition> release) {
        List<String> names = new ArrayList<>();
        for (ParameterDefinition definition : release) {
            if (this == VARIABLE_MODIFICATIONS
                    && definition.kind() == ValueKind.VARIABLE_MOD_TUPLE) {
                names.add(definition.name());
            }
        }
        for (String name : named) {
            if (release.stream().anyMatch(definition -> definition.name().equals(name))) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }
}
