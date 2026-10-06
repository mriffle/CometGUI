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

package org.cometgui.domain.params;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.run.IndexMode;

/**
 * What an existing Comet {@code .idx} file says about itself: the text header Comet writes before
 * the index's protein names and binary data ({@code R-CMT-07}: "an {@code .idx} file is
 * self-describing about which mode built it, and the application shall read that rather than
 * assume").
 *
 * <p>The header records the format of the file, the Comet that wrote it, the kind of index, and the
 * search options the index was built with. When Comet searches an existing index it takes most of
 * those options from the header and silently ignores what the parameter file says about them, so
 * this description is what the validator compares with the search before Comet starts. Lines a
 * format does not have are empty here: Comet 2026.02.2's format 4 records no decoy prefix, no
 * enzyme termini, no missed cleavages and no N-terminal methionine clipping, and its variable
 * modifications carry no terminal distance or terminus.
 *
 * <p>A pure value: {@code org.cometgui.tools.comet.CometIndexHeaderReader} reads it from a file.
 * Numbers are kept as the header writes them ({@code 600.000000}), not rounded.
 *
 * @param file the index file read
 * @param formatVersion the format number of the first line, {@code 5} for {@code Comet index
 *     database v5}
 * @param firstLine the first line, verbatim
 * @param cometVersion the Comet that wrote the file, as the first line spells it, for example
 *     {@code 2026.03 rev. 0 (fa08489)}
 * @param type the kind of index ({@code IndexSearchType:}); never {@link IndexMode#NONE}
 * @param inputDatabase the FASTA the index was built from, as Comet was given it ({@code InputDB:})
 * @param massRange the peptide mass range indexed ({@code MassRange:})
 * @param lengthRange the peptide length range indexed ({@code LengthRange:})
 * @param parentMassType {@code 1} monoisotopic, {@code 0} average precursor masses ({@code
 *     MassType:}, first value)
 * @param fragmentMassType the same for fragment masses ({@code MassType:}, second value)
 * @param decoySearch the {@code decoy_search} the index was built with ({@code DecoySearch:})
 * @param decoyPrefix the {@code decoy_prefix} it was built with ({@code DecoyPrefix:}), if the
 *     format records it
 * @param enzyme the search enzyme ({@code Enzyme:})
 * @param secondEnzyme the second search enzyme ({@code Enzyme2:})
 * @param enzymeTermini {@code num_enzyme_termini} ({@code NumEnzymeTermini:}), if recorded
 * @param missedCleavages {@code allowed_missed_cleavage} ({@code AllowedMissedCleavage:}), if
 *     recorded
 * @param clipNtermMethionine {@code clip_nterm_methionine} ({@code ClipNtermMethionine:}), if
 *     recorded
 * @param peptides how many unmodified peptides the index holds ({@code NumPeptides:})
 * @param staticMods the static modifications ({@code StaticMod:}): {@value #STATIC_MOD_COUNT}
 *     values, one per residue {@code A} to {@code Z} and then the peptide N-terminus, peptide
 *     C-terminus, protein N-terminus and protein C-terminus ({@link #staticSite(int)})
 * @param variableMods the variable modifications, one per slot the index holds ({@code
 *     VariableMod:}), with each slot's requirement from {@code RequireVariableMod:}
 * @param proteinModList whether a protein modification list was in effect ({@code ProteinModList:})
 * @param requireVariableMod the first value of {@code RequireVariableMod:}: Comet's requirement
 *     flags, whose lowest bit is {@code require_variable_mod}
 * @param maxVariableModsInPeptide {@code max_variable_mods_in_peptide} ({@code
 *     MaxVariableModsInPeptide:})
 */
public record CometIndexDescription(
        Path file,
        int formatVersion,
        String firstLine,
        String cometVersion,
        IndexMode type,
        Optional<String> inputDatabase,
        MassRange massRange,
        LengthRange lengthRange,
        int parentMassType,
        int fragmentMassType,
        int decoySearch,
        Optional<String> decoyPrefix,
        Enzyme enzyme,
        Enzyme secondEnzyme,
        OptionalInt enzymeTermini,
        OptionalInt missedCleavages,
        Optional<Boolean> clipNtermMethionine,
        long peptides,
        List<BigDecimal> staticMods,
        List<VariableMod> variableMods,
        boolean proteinModList,
        int requireVariableMod,
        int maxVariableModsInPeptide) {

    /** How many static modifications the header records: 26 residues and four termini. */
    public static final int STATIC_MOD_COUNT = 30;

    /** The residues of the first 26 static modifications, in the header's order. */
    public static final String STATIC_RESIDUES = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** The four termini after the residues, in the header's order. */
    public static final List<String> STATIC_TERMINI =
            List.of(
                    "peptide N-terminus",
                    "peptide C-terminus",
                    "protein N-terminus",
                    "protein C-terminus");

    /**
     * A mass range.
     *
     * @param low the lower bound
     * @param high the upper bound
     */
    public record MassRange(BigDecimal low, BigDecimal high) {

        /**
         * Validates the range.
         *
         * @throws NullPointerException naming a bound that is {@code null}
         */
        public MassRange {
            Objects.requireNonNull(low, "low");
            Objects.requireNonNull(high, "high");
        }
    }

    /**
     * A peptide length range.
     *
     * @param shortest the shortest length
     * @param longest the longest length
     */
    public record LengthRange(int shortest, int longest) {}

    /**
     * An enzyme as the header writes it: {@code Trypsin [1 KR P]}.
     *
     * @param name the enzyme's name in the parameter file's enzyme table
     * @param sense {@code 1} cuts after the cut residues, {@code 0} before them
     * @param cutResidues the residues it cuts at, {@code -} for none
     * @param noCutResidues the residues that block a cut, {@code -} for none
     */
    public record Enzyme(String name, int sense, String cutResidues, String noCutResidues) {

        /**
         * Validates the enzyme.
         *
         * @throws NullPointerException naming a component that is {@code null}
         * @throws IllegalArgumentException if a text is empty
         */
        public Enzyme {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(cutResidues, "cutResidues");
            Objects.requireNonNull(noCutResidues, "noCutResidues");
            if (name.isEmpty() || cutResidues.isEmpty() || noCutResidues.isEmpty()) {
                throw new IllegalArgumentException(
                        "an enzyme has a name and two residue sets, each written; got \""
                                + name
                                + "\" ["
                                + sense
                                + " "
                                + cutResidues
                                + " "
                                + noCutResidues
                                + "]");
            }
        }

        /**
         * The enzyme as the header writes it.
         *
         * @return for example {@code Trypsin [1 KR P]}
         */
        public String text() {
            return name + " [" + sense + " " + cutResidues + " " + noCutResidues + "]";
        }
    }

    /**
     * One variable-modification slot of the index.
     *
     * @param residues the residue token, as Comet holds it after merging identical slots
     * @param mass the mass difference; zero for an unused slot
     * @param neutralLoss the first neutral loss
     * @param secondNeutralLoss the second neutral loss
     * @param maximumCount the most modified residues of this slot per peptide
     * @param terminalDistance the terminal distance, if the format records it
     * @param terminus the terminus the distance counts from, if the format records it
     * @param requirement the slot's requirement code: {@code 0} optional, {@code 1} required,
     *     {@code -1} exclusive
     */
    public record VariableMod(
            String residues,
            BigDecimal mass,
            BigDecimal neutralLoss,
            BigDecimal secondNeutralLoss,
            int maximumCount,
            OptionalInt terminalDistance,
            OptionalInt terminus,
            int requirement) {

        /**
         * Validates the slot.
         *
         * @throws NullPointerException naming a component that is {@code null}
         * @throws IllegalArgumentException if the residue token is empty, or only one of the
         *     terminal distance and the terminus is recorded
         */
        public VariableMod {
            Objects.requireNonNull(residues, "residues");
            Objects.requireNonNull(mass, "mass");
            Objects.requireNonNull(neutralLoss, "neutralLoss");
            Objects.requireNonNull(secondNeutralLoss, "secondNeutralLoss");
            Objects.requireNonNull(terminalDistance, "terminalDistance");
            Objects.requireNonNull(terminus, "terminus");
            if (residues.isEmpty()) {
                throw new IllegalArgumentException("a variable modification names its residues");
            }
            if (terminalDistance.isPresent() != terminus.isPresent()) {
                throw new IllegalArgumentException(
                        "a slot records both its terminal distance and its terminus, or neither");
            }
        }

        /**
         * Whether the slot is in use: Comet ignores a slot whose mass difference is zero.
         *
         * @return {@code true} if the mass difference is not zero
         */
        public boolean isActive() {
            return mass.signum() != 0;
        }
    }

    /**
     * Validates the description and takes immutable copies of the lists.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the format number is below 1, the first line or the Comet
     *     version is blank, the type is {@link IndexMode#NONE}, there are not {@value
     *     #STATIC_MOD_COUNT} static modifications, or there is no variable-modification slot
     */
    public CometIndexDescription {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(firstLine, "firstLine");
        Objects.requireNonNull(cometVersion, "cometVersion");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(inputDatabase, "inputDatabase");
        Objects.requireNonNull(massRange, "massRange");
        Objects.requireNonNull(lengthRange, "lengthRange");
        Objects.requireNonNull(decoyPrefix, "decoyPrefix");
        Objects.requireNonNull(enzyme, "enzyme");
        Objects.requireNonNull(secondEnzyme, "secondEnzyme");
        Objects.requireNonNull(enzymeTermini, "enzymeTermini");
        Objects.requireNonNull(missedCleavages, "missedCleavages");
        Objects.requireNonNull(clipNtermMethionine, "clipNtermMethionine");
        if (formatVersion < 1) {
            throw new IllegalArgumentException(
                    "an index format number is 1 or more, not " + formatVersion);
        }
        if (firstLine.isBlank() || cometVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "an index names its format and the Comet that wrote it: \""
                            + firstLine
                            + "\", \""
                            + cometVersion
                            + "\"");
        }
        if (type == IndexMode.NONE) {
            throw new IllegalArgumentException(
                    "an index is a fragment-ion or a peptide index, not " + type);
        }
        staticMods = List.copyOf(staticMods);
        if (staticMods.size() != STATIC_MOD_COUNT) {
            throw new IllegalArgumentException(
                    "an index records "
                            + STATIC_MOD_COUNT
                            + " static modifications, not "
                            + staticMods.size());
        }
        variableMods = List.copyOf(variableMods);
        if (variableMods.isEmpty()) {
            throw new IllegalArgumentException("an index records its variable-modification slots");
        }
    }

    /**
     * The static modifications, immutable.
     *
     * @return {@value #STATIC_MOD_COUNT} masses in the header's order
     */
    @Override
    public List<BigDecimal> staticMods() {
        return List.copyOf(staticMods);
    }

    /**
     * The variable-modification slots, immutable.
     *
     * @return the slots, the first being {@code variable_mod01}
     */
    @Override
    public List<VariableMod> variableMods() {
        return List.copyOf(variableMods);
    }

    /**
     * Where one static modification applies, in words.
     *
     * @param index the position in {@link #staticMods()}
     * @return for example {@code C} or {@code peptide N-terminus}
     * @throws IndexOutOfBoundsException if the position is not one of the {@value
     *     #STATIC_MOD_COUNT}
     */
    public static String staticSite(int index) {
        if (index < STATIC_RESIDUES.length()) {
            return String.valueOf(STATIC_RESIDUES.charAt(index));
        }
        return STATIC_TERMINI.get(index - STATIC_RESIDUES.length());
    }
}
