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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.schema.ResidueAlphabet;
import org.cometgui.params.comet.schema.TerminalCode;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.value.VariableModification;

/**
 * {@link Rule#VARMODS_ASCOREPRO_SLOT}: AScorePro ({@code print_ascorepro_score} not 0) while a slot
 * above {@code variable_mod09} is still active after Comet merges identical slots.
 *
 * <p>Comet hands AScorePro each modified residue followed by its slot number written as decimal
 * digits ({@code CometPostAnalysis.cpp} line 920 at {@code v2026.02.2}) and registers each slot as
 * the one character {@code '0' + slot} ({@code CometSearchManager.cpp} line 3336), so a two-digit
 * slot reads as two one-digit ones. Comet 2026.03.0 refuses the combination ({@code
 * CometSearchManager.cpp} lines 1577-1603 at {@code v2026.03.0}); Comet 2026.02.2 runs it, and was
 * seen to crash in post-analysis (exit 139) when AScorePro localised a modification in {@code
 * variable_mod10}. So the rule is an error in every release that has the parameter.
 *
 * <p>Comet applies the check <em>after</em> it merges a slot into an earlier one that is identical
 * to it ({@code CometSearchManager.cpp} lines 1475-1512 at {@code v2026.03.0}, the same loop at
 * {@code v2026.02.2}): a merged slot is no longer active and draws no error. {@link
 * #activeAfterMerge(CometParameters)} models that loop, field by field, from the source -- the mass
 * difference, both neutral losses, the binary group, the maximum count after Comet caps it at
 * {@code max_variable_mods_in_peptide} (lines 1402-1414), the minimum count, the requirement code
 * (never {@code -1}: exclusive slots are never merged), the terminal distance and the terminus.
 * Comet 2026.03.0 first rewrites {@code n} (or {@code c}) at distance 0 from the protein N- (or C-)
 * terminus to {@code ^} (or {@code $}) with the distance and terminus reset (lines 1452-1471); that
 * rewrite exists only in a release that has the protein-terminus code, which is read from the
 * release's residue alphabet, not from its version number.
 */
final class AScoreProRule {

    /** The parameter that switches AScorePro on. */
    static final String ASCOREPRO = "print_ascorepro_score";

    /** The last slot AScorePro supports: slot numbers above it take two digits. */
    static final int LAST_SUPPORTED_SLOT = 9;

    private static final String SLOT_PREFIX = "variable_mod";

    private AScoreProRule() {}

    static void check(CometParameters model, Findings findings) {
        int ascore =
                model.entry(ASCOREPRO)
                        .map(entry -> ((ParameterValue.Whole) entry.value()).value())
                        .orElse(0);
        if (ascore == 0) {
            return;
        }
        for (Slot slot : activeAfterMerge(model)) {
            if (slot.number() > LAST_SUPPORTED_SLOT) {
                findings.add(
                        Rule.VARMODS_ASCOREPRO_SLOT,
                        List.of(slot.name(), ASCOREPRO),
                        ASCOREPRO
                                + " = "
                                + ascore
                                + " runs AScorePro, which supports variable_mod01 to"
                                + " variable_mod09 only, and "
                                + VariableModRules.shown(model, slot.name(), slot.modification())
                                + " is active (no lower slot is identical to it, so Comet does not"
                                + " merge it away): AScorePro cannot tell slot "
                                + slot.number()
                                + " from two one-digit slots, so it can corrupt modification"
                                + " sites; set "
                                + ASCOREPRO
                                + " to 0, or move the modification to an unused slot from"
                                + " variable_mod01 to variable_mod09");
            }
        }
    }

    /**
     * One variable-modification slot.
     *
     * @param number the slot number, 1 for {@code variable_mod01}
     * @param name the parameter name
     * @param modification its value
     */
    record Slot(int number, String name, VariableModification modification) {}

    /**
     * The fields Comet compares to decide that two slots are the same modification, as Comet holds
     * them when it compares: masses as {@code double}s, the maximum count capped, and the terminal
     * fields after the protein-terminus rewrite.
     */
    private record MergeKey(
            double mass,
            double neutralLoss,
            double secondNeutralLoss,
            int binaryGroup,
            int maximumCount,
            int minimumCount,
            int requirement,
            int distance,
            int terminus) {}

    /**
     * The slots that are still active when Comet has merged every slot identical to a lower one, in
     * slot order.
     *
     * @param model the model
     * @return the surviving active slots
     */
    static List<Slot> activeAfterMerge(CometParameters model) {
        List<Slot> slots = new ArrayList<>();
        for (ParameterEntry entry : model.entries()) {
            if (entry.definition().kind() == ValueKind.VARIABLE_MOD_TUPLE) {
                VariableModification mod = ((ParameterValue.Tuple) entry.value()).modification();
                if (!mod.isUnused()) {
                    slots.add(new Slot(number(entry.name()), entry.name(), mod));
                }
            }
        }
        slots.sort(Comparator.comparingInt(Slot::number));
        int cap = cap(model);
        ResidueAlphabet alphabet = VariableModRules.alphabet(model);
        Set<MergeKey> seen = new HashSet<>();
        List<Slot> survivors = new ArrayList<>();
        for (Slot slot : slots) {
            MergeKey key = key(slot.modification(), cap, alphabet);
            boolean merged = key.requirement() != -1 && !seen.add(key);
            if (!merged) {
                survivors.add(slot);
            }
        }
        return survivors;
    }

    private static int number(String name) {
        return Integer.parseInt(name.substring(SLOT_PREFIX.length()));
    }

    /**
     * The per-peptide cap Comet applies to each slot's maximum count: {@code
     * max_variable_mods_in_peptide}, or, when that is negative and Comet ignores it, the release's
     * default.
     */
    private static int cap(CometParameters model) {
        int limit = ((ParameterValue.Whole) model.value(VariableModRules.LIMIT)).value();
        if (limit >= 0) {
            return limit;
        }
        return Integer.parseInt(model.definition(VariableModRules.LIMIT).defaultValue());
    }

    private static MergeKey key(VariableModification mod, int cap, ResidueAlphabet alphabet) {
        List<BigDecimal> losses = mod.neutralLosses();
        int distance = mod.terminalDistance();
        int terminus = mod.terminusCode();
        if (rewrittenToProteinTerminus(mod, alphabet)) {
            distance = -1;
            terminus = 0;
        }
        return new MergeKey(
                asDouble(mod.mass()),
                asDouble(losses.get(0)),
                losses.size() > 1 ? asDouble(losses.get(1)) : 0.0,
                mod.binaryGroup(),
                Math.min(mod.maximumCount(), cap),
                mod.minimumCount().orElse(0),
                mod.requirementCode(),
                distance,
                terminus);
    }

    /**
     * Whether the release rewrites this slot to its protein-terminus code with no position rule
     * left: distance 0 from the protein N- (or C-) terminus, and a token of nothing but {@code n}
     * and {@code ^} (or {@code c} and {@code $}), in a release whose alphabet has the code.
     */
    private static boolean rewrittenToProteinTerminus(
            VariableModification mod, ResidueAlphabet alphabet) {
        if (mod.terminalDistance() != 0 || mod.terminusCode() < 0 || mod.terminusCode() > 1) {
            return false;
        }
        boolean nTerminus = mod.terminusCode() == 0;
        char from = (nTerminus ? TerminalCode.PEPTIDE_N : TerminalCode.PEPTIDE_C).code();
        char to = (nTerminus ? TerminalCode.PROTEIN_N : TerminalCode.PROTEIN_C).code();
        if (!alphabet.accepts(to)) {
            return false;
        }
        return mod.residues().chars().allMatch(c -> c == from || c == to);
    }

    /** A decimal as Comet's {@code %lf} reads it: correctly rounded to the nearest double. */
    private static double asDouble(BigDecimal value) {
        return Double.parseDouble(value.toString());
    }
}
