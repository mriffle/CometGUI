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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Reading Percolator's {@code peptide} field, in the forms Comet's PIN writer produces. */
class PercolatorPeptideTest {

    @Test
    @DisplayName("flanks, residues and a residue's mass")
    void plainAndModified() {
        PercolatorPeptide peptide = PercolatorPeptide.parse("R.GGHM[15.9949]DDGK.-").orElseThrow();
        assertAll(
                () -> assertEquals("R", peptide.previous()),
                () -> assertEquals("GGHMDDGK", peptide.residues()),
                () -> assertEquals("-", peptide.next()),
                () -> assertEquals(Map.of(4, "15.9949"), peptide.byPosition()),
                () -> assertEquals(Optional.empty(), peptide.nTerminal()),
                () -> assertEquals(Optional.empty(), peptide.cTerminal()));
    }

    @Test
    @DisplayName("n[...] before the first residue and c[...] after the last")
    void terminals() {
        PercolatorPeptide peptide =
                PercolatorPeptide.parse("-.n[42.0106]M[15.9949]PEPK[-1.5]c[14.0157].A")
                        .orElseThrow();
        assertAll(
                () -> assertEquals("MPEPK", peptide.residues()),
                () -> assertEquals(Optional.of("42.0106"), peptide.nTerminal()),
                () -> assertEquals(Optional.of("14.0157"), peptide.cTerminal()),
                () -> assertEquals(Map.of(1, "15.9949", 5, "-1.5"), peptide.byPosition()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "PEPTIDEK",
                "R.PEPTIDEK",
                "RR.PEPTIDEK.A",
                "R..A",
                "R.pEPTIDEK.A",
                "R.PEP[x]TIDEK.A",
                "R.PEP[15.9949TIDEK.A",
                "R.n[42.01PEP.A",
                "R.n[]PEP.A",
                "R.n[42.0106].A",
                "R.PEPc[x].A",
                "R.PEPc[1.0]K.A",
                "R.PE PK.A",
                "R.PEP[+.5]K.A",
                "R.PEP[1.]K.A"
            })
    @DisplayName("anything else is not a Percolator peptide")
    void refused(String text) {
        assertEquals(Optional.empty(), PercolatorPeptide.parse(text), text);
    }
}
