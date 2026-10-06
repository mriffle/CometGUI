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

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.params.CometIndexDescription;
import org.cometgui.domain.params.CometIndexDescription.Enzyme;
import org.cometgui.domain.params.CometIndexDescription.VariableMod;
import org.cometgui.domain.params.FastaDecoyCensus;
import org.cometgui.domain.run.IndexMode;

/**
 * The file-system facts the validator tests judge, HAND-TYPED from what the real readers return for
 * real files. This module cannot run the readers ({@code cometgui-tools} is not a dependency), so
 * the values are typed here a second time, from the same bytes:
 *
 * <ul>
 *   <li>the index headers are the committed captures under {@code
 *       cometgui-tools/src/test/resources/fixtures/comet-index/}, written by the real pinned Comet
 *       2026.03.0 (format 5) and 2026.02.2 (format 4) binaries, which {@code
 *       CometIndexRealBinaryTest} rebuilds and {@code CometIndexHeaderReaderTest} reads field by
 *       field;
 *   <li>the censuses are what {@code FastaDecoyScanner} counts in the {@code D-006} subset (the
 *       UniProt proteome's first 1000 records, SHA-256 {@code 5005d961...}) and in the target-decoy
 *       FASTA built from it (SHA-256 {@code ff098f19...}), as {@code CometIndexRealBinaryTest}
 *       proves.
 * </ul>
 */
final class IndexDescriptions {

    /** Where the tests say the index is. */
    static final Path INDEX = Path.of("project", "index-cache", "k1", "subset.fasta.idx");

    /** Where the tests say the subset is. */
    static final Path SUBSET = Path.of("data", "subset.fasta");

    /** Where the tests say the target-decoy FASTA is. */
    static final Path TARGET_DECOY = Path.of("data", "target-decoy.fasta");

    private IndexDescriptions() {}

    /** The subset's census: 1000 records, no decoy. */
    static FastaDecoyCensus subsetCensus() {
        return new FastaDecoyCensus(SUBSET, "DECOY_", 1000, 0, Optional.empty());
    }

    /** The target-decoy FASTA's census: 2000 records, 1000 decoys. */
    static FastaDecoyCensus targetDecoyCensus() {
        return new FastaDecoyCensus(
                TARGET_DECOY, "DECOY_", 2000, 1000, Optional.of("DECOY_sp|A0A075B6H9|LV469_HUMAN"));
    }

    private static BigDecimal d(String text) {
        return new BigDecimal(text);
    }

    private static List<BigDecimal> staticMods(boolean ntermPeptide) {
        List<BigDecimal> mods = new ArrayList<>();
        for (int index = 0; index < CometIndexDescription.STATIC_MOD_COUNT; index++) {
            mods.add(d("0.000000"));
        }
        mods.set(2, d("57.021464"));
        if (ntermPeptide) {
            mods.set(26, d("1.500000"));
        }
        return mods;
    }

    private static VariableMod slot(
            String residues, String mass, String loss, int count, boolean positioned, int need) {
        return new VariableMod(
                residues,
                d(mass),
                d(loss),
                d("0.000000"),
                count,
                positioned ? OptionalInt.of(-1) : OptionalInt.empty(),
                positioned ? OptionalInt.of(0) : OptionalInt.empty(),
                need);
    }

    private static List<VariableMod> plainSlots(boolean positioned) {
        List<VariableMod> slots = new ArrayList<>();
        slots.add(slot("M", "15.994900", "0.000000", 3, positioned, 0));
        for (int number = 2; number <= 5; number++) {
            slots.add(slot("X", "0.000000", "0.000000", 3, positioned, 0));
        }
        return slots;
    }

    private static CometIndexDescription description(
            boolean newer,
            IndexMode mode,
            int decoySearch,
            int missedCleavages,
            long peptides,
            List<BigDecimal> statics,
            List<VariableMod> slots,
            int requirements,
            int maxMods) {
        return new CometIndexDescription(
                INDEX,
                newer ? 5 : 4,
                newer
                        ? "Comet index database v5.  Comet version 2026.03 rev. 0 (fa08489)"
                        : "Comet index database v4.  Comet version 2026.02 rev. 2 (6edec91)",
                newer ? "2026.03 rev. 0 (fa08489)" : "2026.02 rev. 2 (6edec91)",
                mode,
                Optional.of("subset.fasta"),
                new CometIndexDescription.MassRange(d("600.000000"), d("5000.000000")),
                new CometIndexDescription.LengthRange(5, 50),
                1,
                1,
                decoySearch,
                newer ? Optional.of("DECOY_") : Optional.empty(),
                new Enzyme("Trypsin", 1, "KR", "P"),
                new Enzyme("Cut_everywhere", 0, "-", "-"),
                newer ? OptionalInt.of(2) : OptionalInt.empty(),
                newer ? OptionalInt.of(missedCleavages) : OptionalInt.empty(),
                newer ? Optional.of(false) : Optional.empty(),
                peptides,
                statics,
                slots,
                false,
                requirements,
                maxMods);
    }

    /**
     * Comet 2026.03.0's index of the subset, built from its own {@code -q} defaults: format 5.
     *
     * @param mode {@code -i} or {@code -j}
     * @return the description
     */
    static CometIndexDescription formatFive(IndexMode mode) {
        return description(true, mode, 0, 2, 129327, staticMods(false), plainSlots(true), 0, 5);
    }

    /**
     * Comet 2026.02.2's index of the subset, built from its own {@code -q} defaults: format 4.
     *
     * @param mode {@code -i} or {@code -j}
     * @return the description
     */
    static CometIndexDescription formatFour(IndexMode mode) {
        return description(false, mode, 0, 2, 129327, staticMods(false), plainSlots(false), 0, 5);
    }

    /** The edits of the modifications capture, as name and value text pairs. */
    static final String[] MODS_EDITS = {
        "decoy_search", "1",
        "allowed_missed_cleavage", "1",
        "variable_mod02", "15.9949 W 0 3 -1 0 0 0.0",
        "variable_mod03", "79.966331 STY 0 7 -1 0 1 97.976896",
        "variable_mod04", "42.010565 n 0 1 0 0 0 0.0",
        "variable_mod06", "-17.026549 Q 0 1 0 2 0 0.0",
        "max_variable_mods_in_peptide", "6",
        "add_Nterm_peptide", "1.5"
    };

    /**
     * A release's fragment-ion index built with {@link #MODS_EDITS}: Comet merged slot 2 into slot
     * 1, capped slot 3's count at 5, wrote slot 4 as {@code ^} (2026.03.0) or kept {@code n}
     * (2026.02.2), and did not hold slot 6.
     *
     * @param newer whether it is Comet 2026.03.0's
     * @return the description
     */
    static CometIndexDescription mods(boolean newer) {
        List<VariableMod> slots =
                List.of(
                        slot("MW", "15.994900", "0.000000", 3, newer, 0),
                        slot("-", "0.000000", "0.000000", 3, newer, 0),
                        slot("STY", "79.966331", "97.976896", 5, newer, 1),
                        slot(newer ? "^" : "n", "42.010565", "0.000000", 1, newer, 0),
                        slot("X", "0.000000", "0.000000", 3, newer, 0));
        return description(
                newer, IndexMode.FRAGMENT_ION, 1, 1, 81849, staticMods(true), slots, 8, 6);
    }
}
