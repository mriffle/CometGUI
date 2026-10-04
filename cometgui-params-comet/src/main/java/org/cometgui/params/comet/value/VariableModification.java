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

package org.cometgui.params.comet.value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.params.comet.schema.TerminalCode;

/**
 * One variable modification, as one {@code variable_modNN} slot holds it: every field of Comet's
 * tuple, typed, and nothing about which slot it is in.
 *
 * <p>Meanings follow Comet's {@code variable_mod01} documentation for the 2026.02 release and,
 * where the two differ, its source at tag {@code v2026.02.2}; {@code
 * docs/developer/comet_parameter_schema.rst} cites each. Values are kept as written: the mass and
 * the neutral losses as {@link BigDecimal}s with the scale the user gave ({@code 15.994915} is not
 * rounded to Comet's own {@code 15.9949}), the residues as the token written, and the integer
 * fields as integers even where only some values are documented, so that a value read from a file
 * is written back unchanged. Whether a combination is legal is the validation package's question,
 * not this type's; the constructor refuses only what cannot be a tuple at all.
 *
 * <p>Instances are made by {@link VariableModCodec#parse(String, String)} or directly.
 *
 * @param mass the mass difference
 * @param residues the residue token: letters {@code A}-{@code Z} and the {@link TerminalCode}s --
 *     {@code n} and {@code c} for any peptide N- and C-terminus, {@code ^} and {@code $} for the
 *     protein N- and C-terminus only -- as written (for example {@code STY}, {@code nK} or {@code
 *     ^}). Which of these a Comet release accepts is its {@link
 *     org.cometgui.params.comet.schema.ResidueAlphabet}, applied by {@link VariableModCodec}; this
 *     type holds any token whose every character it can describe
 * @param binaryGroup {@code 0} for a variable modification; any other value names the binary group
 *     whose residues are all modified or all unmodified together
 * @param minimumCount the minimum count per peptide, present only when the count was written as a
 *     {@code min,max} pair
 * @param maximumCount the maximum count per peptide
 * @param terminalDistance {@code -1} for no constraint, {@code -2} for anywhere but the peptide's
 *     C-terminal residue, {@code N >= 0} for the terminal residue through the next {@code N}
 * @param terminusCode which terminus the distance counts from; see {@link #terminus()}
 * @param requirementCode {@code 0} optional, {@code 1} required, {@code -1} exclusive; see {@link
 *     #requirement()}
 * @param neutralLosses the neutral-loss field as written: one value, or two for the {@code a,b}
 *     form
 */
public record VariableModification(
        BigDecimal mass,
        String residues,
        int binaryGroup,
        OptionalInt minimumCount,
        int maximumCount,
        int terminalDistance,
        int terminusCode,
        int requirementCode,
        List<BigDecimal> neutralLosses) {

    /** The terminus a distance counts from: the documented values of the sixth field. */
    public enum Terminus {

        /** {@code 0}. */
        PROTEIN_N(0, "protein N-terminus"),

        /** {@code 1}. */
        PROTEIN_C(1, "protein C-terminus"),

        /** {@code 2}. */
        PEPTIDE_N(2, "peptide N-terminus"),

        /** {@code 3}. */
        PEPTIDE_C(3, "peptide C-terminus");

        private final int code;

        private final String words;

        Terminus(int code, String words) {
            this.code = code;
            this.words = words;
        }

        /**
         * The value written in the tuple.
         *
         * @return {@code 0} to {@code 3}
         */
        public int code() {
            return code;
        }

        /**
         * The terminus in words.
         *
         * @return for example {@code protein N-terminus}
         */
        public String words() {
            return words;
        }

        /**
         * The terminus a code names.
         *
         * @param code the value written
         * @return the terminus, or empty for a value the documentation does not define
         */
        public static Optional<Terminus> fromCode(int code) {
            for (Terminus terminus : values()) {
                if (terminus.code == code) {
                    return Optional.of(terminus);
                }
            }
            return Optional.empty();
        }
    }

    /** Whether peptides must carry the modification: the documented values of the seventh field. */
    public enum Requirement {

        /** {@code 0}: not forced to be present. */
        OPTIONAL(0, "optional"),

        /** {@code 1}: only peptides carrying the modification are analysed. */
        REQUIRED(1, "required"),

        /** {@code -1}: at most one of the set of exclusive modifications in a peptide. */
        EXCLUSIVE(-1, "exclusive");

        private final int code;

        private final String words;

        Requirement(int code, String words) {
            this.code = code;
            this.words = words;
        }

        /**
         * The value written in the tuple.
         *
         * @return {@code 0}, {@code 1} or {@code -1}
         */
        public int code() {
            return code;
        }

        /**
         * The requirement in words.
         *
         * @return for example {@code optional}
         */
        public String words() {
            return words;
        }

        /**
         * The requirement a code names.
         *
         * @param code the value written
         * @return the requirement, or empty for a value the documentation does not define
         */
        public static Optional<Requirement> fromCode(int code) {
            for (Requirement requirement : values()) {
                if (requirement.code == code) {
                    return Optional.of(requirement);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Validates presence and the two shape rules, and takes an immutable copy.
     *
     * @throws IllegalArgumentException if the residue token is empty or holds a character that is
     *     neither a letter {@code A}-{@code Z} nor a {@link TerminalCode}, or if there is not one
     *     or two neutral losses
     */
    public VariableModification {
        Objects.requireNonNull(mass, "mass");
        Objects.requireNonNull(residues, "residues");
        Objects.requireNonNull(minimumCount, "minimumCount");
        neutralLosses = List.copyOf(neutralLosses);
        String problem = residueProblem(residues);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (neutralLosses.isEmpty() || neutralLosses.size() > 2) {
            throw new IllegalArgumentException(
                    "a neutral-loss field holds one value or a pair, not " + neutralLosses.size());
        }
    }

    /**
     * The neutral-loss field as written, immutable.
     *
     * @return one value, or two for the pair form
     */
    @Override
    public List<BigDecimal> neutralLosses() {
        return List.copyOf(neutralLosses);
    }

    /**
     * Why a residue token cannot be one in any release, or {@code null} if it can. A release's own
     * alphabet, which may be narrower, is the codec's to apply.
     *
     * @param residues the token
     * @return the problem in words, or {@code null}
     */
    static String residueProblem(String residues) {
        if (residues.isEmpty()) {
            return "the residue token is empty";
        }
        for (int index = 0; index < residues.length(); index++) {
            char c = residues.charAt(index);
            if (!TerminalCode.describable(c)) {
                return "\""
                        + residues
                        + "\" holds '"
                        + c
                        + "'; a residue token is letters A-Z and the terminal codes n, c, ^ and $";
            }
        }
        return null;
    }

    /**
     * The residue letters, without the terminal codes.
     *
     * @return for example {@code K} for {@code nK} or {@code ^K}; empty for a terminal-only
     *     modification
     */
    public String residueLetters() {
        StringBuilder letters = new StringBuilder();
        for (int index = 0; index < residues.length(); index++) {
            char c = residues.charAt(index);
            if (TerminalCode.of(c).isEmpty()) {
                letters.append(c);
            }
        }
        return letters.toString();
    }

    /**
     * Whether the residue token holds a terminal code.
     *
     * @param terminal the code
     * @return {@code true} if the code's character is in the token
     */
    public boolean has(TerminalCode terminal) {
        return residues.indexOf(terminal.code()) >= 0;
    }

    /**
     * Whether the modification applies to an N-terminus: {@code n} (any peptide's) or {@code ^}
     * (the protein's) in the residue token.
     *
     * @return {@code true} if it does
     */
    public boolean nTerminal() {
        return has(TerminalCode.PEPTIDE_N) || has(TerminalCode.PROTEIN_N);
    }

    /**
     * Whether the modification applies to a C-terminus: {@code c} (any peptide's) or {@code $} (the
     * protein's) in the residue token.
     *
     * @return {@code true} if it does
     */
    public boolean cTerminal() {
        return has(TerminalCode.PEPTIDE_C) || has(TerminalCode.PROTEIN_C);
    }

    /**
     * Which N-terminus the modification applies to. Comet treats {@code n} with {@code ^} as just
     * {@code n} ({@code CometSearch/CometSearchManager.cpp} lines 1538-1563 at {@code v2026.03.0}),
     * so {@code n} wins.
     *
     * @return {@link TerminalCode#PEPTIDE_N} for {@code n}, else {@link TerminalCode#PROTEIN_N} for
     *     {@code ^}, else empty
     */
    public Optional<TerminalCode> nTerminus() {
        return terminus(TerminalCode.PEPTIDE_N, TerminalCode.PROTEIN_N);
    }

    /**
     * Which C-terminus the modification applies to; {@code c} with {@code $} is just {@code c}.
     *
     * @return {@link TerminalCode#PEPTIDE_C} for {@code c}, else {@link TerminalCode#PROTEIN_C} for
     *     {@code $}, else empty
     */
    public Optional<TerminalCode> cTerminus() {
        return terminus(TerminalCode.PEPTIDE_C, TerminalCode.PROTEIN_C);
    }

    private Optional<TerminalCode> terminus(TerminalCode anyPeptide, TerminalCode proteinOnly) {
        if (has(anyPeptide)) {
            return Optional.of(anyPeptide);
        }
        return has(proteinOnly) ? Optional.of(proteinOnly) : Optional.empty();
    }

    /**
     * Whether this is a binary modification: Comet treats any non-zero group as binary.
     *
     * @return {@code true} if {@link #binaryGroup()} is not {@code 0}
     */
    public boolean isBinary() {
        return binaryGroup != 0;
    }

    /**
     * Whether the slot is unused: Comet ignores a slot whose mass difference is zero, which is how
     * {@code comet -q} writes slots 2 to 15 ({@code 0.0 X 0 3 -1 0 0 0.0}).
     *
     * @return {@code true} if the mass difference is zero
     */
    public boolean isUnused() {
        return mass.signum() == 0;
    }

    /**
     * The terminus the distance counts from.
     *
     * @return the terminus, or empty if {@link #terminusCode()} is not a documented value
     */
    public Optional<Terminus> terminus() {
        return Terminus.fromCode(terminusCode);
    }

    /**
     * Whether peptides must carry the modification.
     *
     * @return the requirement, or empty if {@link #requirementCode()} is not a documented value
     */
    public Optional<Requirement> requirement() {
        return Requirement.fromCode(requirementCode);
    }

    /**
     * The neutral losses Comet will consider: the written values that are not zero, because Comet
     * treats a zero loss as none.
     *
     * @return zero, one or two losses
     */
    public List<BigDecimal> effectiveNeutralLosses() {
        return neutralLosses.stream().filter(loss -> loss.signum() != 0).toList();
    }

    /**
     * The modification in words, without a name.
     *
     * @return for example {@code +15.994915 on M; max 3 per peptide; optional}
     * @see #summary(String)
     */
    public String summary() {
        if (isUnused()) {
            return "unused (mass difference " + Numbers.text(mass) + ")";
        }
        List<String> parts = new ArrayList<>();
        parts.add(signed(mass) + " on " + targets() + distance());
        parts.add(count());
        parts.add(
                requirement()
                        .map(Requirement::words)
                        .orElse("requirement code " + requirementCode));
        if (isBinary()) {
            parts.add("binary group " + binaryGroup);
        }
        List<BigDecimal> losses = effectiveNeutralLosses();
        if (!losses.isEmpty()) {
            List<String> texts = losses.stream().map(Numbers::text).toList();
            parts.add(
                    (losses.size() == 1 ? "neutral loss " : "neutral losses ")
                            + String.join(" and ", texts));
        }
        return String.join("; ", parts);
    }

    /**
     * The modification in words, as the specification's variable-modification editor shows it.
     *
     * @param name what the user calls the modification, such as {@code Oxidation}
     * @return for example {@code Oxidation: +15.994915 on M; max 3 per peptide; optional}
     */
    public String summary(String name) {
        Objects.requireNonNull(name, "name");
        return name + ": " + summary();
    }

    private static String signed(BigDecimal value) {
        String text = Numbers.text(value);
        return text.startsWith("-") ? text : "+" + text;
    }

    private String targets() {
        List<String> targets = new ArrayList<>();
        if (!residueLetters().isEmpty()) {
            targets.add(residueLetters());
        }
        nTerminus().ifPresent(terminal -> targets.add(terminal.words()));
        cTerminus().ifPresent(terminal -> targets.add(terminal.words()));
        return String.join(" and ", targets);
    }

    private String distance() {
        String from = terminus().map(Terminus::words).orElse("terminus code " + terminusCode);
        if (terminalDistance == -1) {
            return "";
        }
        if (terminalDistance == -2) {
            return ", except on the peptide's C-terminal residue";
        }
        if (terminalDistance < 0) {
            return ", terminal distance code " + terminalDistance;
        }
        return terminalDistance == 0
                ? ", only at the " + from
                : ", within " + (terminalDistance + 1) + " residues of the " + from;
    }

    private String count() {
        if (minimumCount.isPresent()) {
            return minimumCount.getAsInt() + " to " + maximumCount + " per peptide";
        }
        return "max " + maximumCount + " per peptide";
    }
}
