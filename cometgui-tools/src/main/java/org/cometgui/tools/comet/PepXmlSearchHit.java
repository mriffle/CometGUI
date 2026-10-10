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

package org.cometgui.tools.comet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One {@code search_hit} of a spectrum query, as Comet states it.
 *
 * <p><strong>{@link #position()} is not {@link #hitRank()}.</strong> Comet gives tied hits the same
 * {@code hit_rank}, while its PIN -- and so Percolator's {@code PSMId} -- numbers a query's hits
 * 1..n in file order (design decision P11-13); the position is that number.
 *
 * <p>The proteins are as the file states them. With a concatenated decoy search Comet writes a
 * decoy {@code protein} with the decoy prefix but <strong>drops the prefix from a decoy {@code
 * alternative_protein}</strong> (its writer prefixes only the first), where the PIN keeps it; this
 * record carries what the pepXML says (P11-14).
 *
 * @param position the hit's 1-based position among its query's {@code search_hit} elements
 * @param hitRank {@code hit_rank}
 * @param peptide {@code peptide}, the unmodified residues
 * @param peptidePrevAa {@code peptide_prev_aa}
 * @param peptideNextAa {@code peptide_next_aa}
 * @param protein {@code protein}
 * @param alternativeProteins every {@code alternative_protein}'s {@code protein}, in document order
 * @param numTotProteins {@code num_tot_proteins}
 * @param calcNeutralPepMass {@code calc_neutral_pep_mass}
 * @param massDiff {@code massdiff}
 * @param searchScores every {@code search_score}, in document order
 * @param modificationInfo the {@code modification_info}, when the hit has one
 */
public record PepXmlSearchHit(
        int position,
        int hitRank,
        String peptide,
        String peptidePrevAa,
        String peptideNextAa,
        String protein,
        List<String> alternativeProteins,
        long numTotProteins,
        PepXmlDecimal calcNeutralPepMass,
        PepXmlDecimal massDiff,
        List<PepXmlNameValue> searchScores,
        Optional<PepXmlModificationInfo> modificationInfo) {

    /**
     * Validates the hit.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PepXmlSearchHit {
        Objects.requireNonNull(peptide, "peptide");
        Objects.requireNonNull(peptidePrevAa, "peptidePrevAa");
        Objects.requireNonNull(peptideNextAa, "peptideNextAa");
        Objects.requireNonNull(protein, "protein");
        alternativeProteins = List.copyOf(alternativeProteins);
        Objects.requireNonNull(calcNeutralPepMass, "calcNeutralPepMass");
        Objects.requireNonNull(massDiff, "massDiff");
        searchScores = List.copyOf(searchScores);
        Objects.requireNonNull(modificationInfo, "modificationInfo");
    }

    @Override
    public List<String> alternativeProteins() {
        return List.copyOf(alternativeProteins);
    }

    @Override
    public List<PepXmlNameValue> searchScores() {
        return List.copyOf(searchScores);
    }

    /**
     * {@link #protein()} followed by {@link #alternativeProteins()}, as the file states them.
     *
     * @return every protein
     */
    public List<String> proteins() {
        List<String> all = new ArrayList<>(alternativeProteins.size() + 1);
        all.add(protein);
        all.addAll(alternativeProteins);
        return List.copyOf(all);
    }

    /**
     * One {@code search_score}'s text.
     *
     * @param name the score's name, such as {@code "xcorr"} or {@code "expect"}
     * @return its {@code value}, verbatim, or empty if the hit has no score of that name
     */
    public Optional<String> searchScore(String name) {
        Objects.requireNonNull(name, "name");
        for (PepXmlNameValue score : searchScores) {
            if (score.name().equals(name)) {
                return Optional.of(score.value());
            }
        }
        return Optional.empty();
    }
}
