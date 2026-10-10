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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A search hit's {@code modification_info}, as Comet states it.
 *
 * <p>The terminal values are <strong>masses of the modified terminal group, not deltas</strong>:
 * Comet writes {@code mod_nterm_mass} whenever any N-terminal modification -- static or variable,
 * peptide or protein -- applies to the hit, as the sum of all of them plus a hydrogen, and {@code
 * mod_cterm_mass} likewise plus a hydroxyl. Which declaration that sum came from is read from
 * {@link PepXmlSearchSummary#terminalModifications()}; see {@link PepXmlTerminalModification}.
 *
 * @param modifiedPeptide {@code modified_peptide}, verbatim: Comet's display form with integer
 *     masses, which is <em>not</em> a faithful statement of the modifications and is carried only
 *     so that nothing is silently dropped
 * @param ntermMass {@code mod_nterm_mass}, when present
 * @param ctermMass {@code mod_cterm_mass}, when present
 * @param residues the {@code mod_aminoacid_mass} elements, in document order
 * @param sequenceVariants the PEFF {@code aminoacid_substitution} and {@code sequence_substitution}
 *     elements, in document order
 */
public record PepXmlModificationInfo(
        String modifiedPeptide,
        Optional<PepXmlDecimal> ntermMass,
        Optional<PepXmlDecimal> ctermMass,
        List<PepXmlResidueModification> residues,
        List<PepXmlSequenceVariant> sequenceVariants) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PepXmlModificationInfo {
        Objects.requireNonNull(modifiedPeptide, "modifiedPeptide");
        Objects.requireNonNull(ntermMass, "ntermMass");
        Objects.requireNonNull(ctermMass, "ctermMass");
        residues = List.copyOf(residues);
        sequenceVariants = List.copyOf(sequenceVariants);
    }

    @Override
    public List<PepXmlResidueModification> residues() {
        return List.copyOf(residues);
    }

    @Override
    public List<PepXmlSequenceVariant> sequenceVariants() {
        return List.copyOf(sequenceVariants);
    }
}
