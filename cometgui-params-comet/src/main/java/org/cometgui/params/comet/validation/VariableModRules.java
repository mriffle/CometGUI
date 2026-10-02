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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.value.VariableModification;

/**
 * Validator {@code variable_mod_tuple}, per slot, and {@code R-PARAM-10}'s cross-validation of the
 * slots against {@code max_variable_mods_in_peptide} and {@code require_variable_mod}.
 *
 * <p>Every fact is Comet 2026.02.2's, at tag {@code v2026.02.2}. A slot is <em>active</em> when its
 * mass difference is not zero; Comet ignores any other slot ({@code
 * CometSearch/CometSearchManager.cpp} lines 1366-1369), so the per-slot meaning rules apply to
 * active slots only. The residue token's length applies to every slot, because a token Comet cuts
 * short shifts every later field.
 *
 * <p><strong>Per slot.</strong>
 *
 * <ul>
 *   <li>Residues: Comet reads the token with {@code %31s} into a 32-byte buffer ({@code Comet.cpp}
 *       line 589; {@code MAX_VARMOD_AA}, {@code CometData.h} line 25), so a token of more than
 *       {@value #RESIDUE_LIMIT} characters is an error. The alphabet itself is the typed value's.
 *   <li>Count: whole numbers only ({@code \d+(,\d+)?} in the documentation's own tuple generator);
 *       a minimum above the maximum can never be met, because Comet places at most the maximum
 *       ({@code CometSearch.cpp} lines 5729-5733) and rejects a count below the minimum (line 5844
 *       and the fourteen like it). A maximum of 0 places nothing: a warning.
 *   <li>Terminal distance: {@code -2}, {@code -1} and {@code 0} upwards are documented. Comet
 *       treats any other negative value as "no constraint" ({@code CometSearch.cpp} lines 5371 and
 *       5454), so it is a warning.
 *   <li>Terminus: with a distance of 0 or more Comet compares the terminus with 0 to 3 and matches
 *       nothing else ({@code CometSearch.cpp} lines 5375-5390 and 5466-5520): an undocumented code
 *       means the modification is never applied, an error.
 *   <li>Requirement: {@code 0}, {@code 1} and {@code -1} are documented. Comet treats any positive
 *       value as required ({@code CometSearchManager.cpp} lines 1401-1402) and only {@code -1} as
 *       exclusive ({@code CometSearch.cpp} line 5881): a warning.
 *   <li>Binary group: documented as 0 or a non-zero group number, and the documentation's examples
 *       and generator use positive numbers; Comet treats any non-zero value as binary ({@code
 *       CometSearchManager.cpp} lines 1398-1399). A negative group is a warning.
 * </ul>
 *
 * <p>Each slot holds exactly one count form (a maximum, or a minimum and maximum), one requirement
 * code and one terminus: the typed value cannot express two semantics in one slot, so no rule is
 * needed for that.
 *
 * <p><strong>{@code R-PARAM-10}.</strong> {@code max_variable_mods_in_peptide} caps the modified
 * residues of a peptide across all slots (the loops of {@code CometSearch.cpp} from line 5740 break
 * above it); {@code require_variable_mod} on means only peptides carrying a variable modification
 * are scored (unmodified peptides are scored only when it is off, {@code CometSearch.cpp} line
 * 2933; set at {@code CometSearchManager.cpp} lines 543-549).
 *
 * <ul>
 *   <li>{@code require_variable_mod} on with no active slot: no peptide can qualify -- an error.
 *   <li>An active slot whose minimum count exceeds {@code max_variable_mods_in_peptide}: the slot
 *       can never be applied -- an error.
 *   <li>{@code max_variable_mods_in_peptide = 0} while a modification is required ({@code
 *       require_variable_mod} on, or an active slot marked required): nothing can qualify -- an
 *       error.
 *   <li>{@code max_variable_mods_in_peptide = 0} with active slots and nothing required: the slots
 *       have no effect -- a warning.
 * </ul>
 *
 * <p>A slot's maximum above {@code max_variable_mods_in_peptide} is not reported: it is ordinary
 * (the per-peptide cap simply applies first).
 */
final class VariableModRules {

    /** The longest residue token Comet reads, {@code %31s}. */
    static final int RESIDUE_LIMIT = 31;

    /** The per-peptide cap. */
    static final String LIMIT = "max_variable_mods_in_peptide";

    /** Whether a variable modification is required. */
    static final String REQUIRE = "require_variable_mod";

    private VariableModRules() {}

    static void checkSlot(CometParameters model, ParameterEntry entry, Findings findings) {
        String name = entry.name();
        VariableModification mod = ((ParameterValue.Tuple) entry.value()).modification();
        String shown = name + " = " + model.text(name);
        if (mod.residues().length() > RESIDUE_LIMIT) {
            findings.add(
                    Rule.VARMOD_RESIDUES_TOO_LONG,
                    name,
                    shown
                            + ": the residue token has "
                            + mod.residues().length()
                            + " characters, and Comet reads at most "
                            + RESIDUE_LIMIT
                            + "; list each residue once");
        }
        if (mod.isUnused()) {
            return;
        }
        int maximum = mod.maximumCount();
        OptionalInt minimum = mod.minimumCount();
        if (maximum < 0 || minimum.orElse(0) < 0) {
            findings.add(
                    Rule.VARMOD_COUNT_NEGATIVE,
                    name,
                    shown + ": a modification count cannot be negative; use 0 or more");
        } else if (minimum.orElse(0) > maximum) {
            findings.add(
                    Rule.VARMOD_COUNT_REVERSED,
                    name,
                    shown
                            + ": the minimum count "
                            + minimum.getAsInt()
                            + " is above the maximum "
                            + maximum
                            + ", so the modification can never be applied; give the smaller"
                            + " count first");
        } else if (maximum == 0) {
            findings.add(
                    Rule.VARMOD_COUNT_ZERO,
                    name,
                    shown
                            + ": a maximum count of 0 means the modification is never placed;"
                            + " use 1 or more, or set the mass difference to 0 to switch the slot"
                            + " off");
        }
        int distance = mod.terminalDistance();
        if (distance < -2) {
            findings.add(
                    Rule.VARMOD_DISTANCE_UNDOCUMENTED,
                    name,
                    shown
                            + ": terminal distance "
                            + distance
                            + " is not a documented value, and Comet treats it as -1 (no"
                            + " constraint); use -2, -1 or 0 and above");
        }
        if (distance >= 0 && mod.terminus().isEmpty()) {
            findings.add(
                    Rule.VARMOD_TERMINUS_UNDOCUMENTED,
                    name,
                    shown
                            + ": terminus code "
                            + mod.terminusCode()
                            + " is not one Comet knows, so with a terminal distance of "
                            + distance
                            + " the modification is never applied; use 0 (protein N), 1 (protein"
                            + " C), 2 (peptide N) or 3 (peptide C)");
        }
        // Reached only for an undocumented code, never 0 or 1, so "> 0" and ">= 0" (or "> 1")
        // choose the same words: PIT's boundary mutant on the next comparison is equivalent.
        if (mod.requirement().isEmpty()) {
            findings.add(
                    Rule.VARMOD_REQUIREMENT_UNDOCUMENTED,
                    name,
                    shown
                            + ": requirement code "
                            + mod.requirementCode()
                            + " is not documented; Comet treats it as "
                            + (mod.requirementCode() > 0 ? "1 (required)" : "0 (optional)")
                            + ", so write that instead");
        }
        if (mod.binaryGroup() < 0) {
            findings.add(
                    Rule.VARMOD_BINARY_GROUP_NEGATIVE,
                    name,
                    shown
                            + ": binary group "
                            + mod.binaryGroup()
                            + " is negative; Comet treats any non-zero group as binary, but"
                            + " documents positive group numbers, so use one");
        }
    }

    static void checkLimits(CometParameters model, Findings findings) {
        List<ParameterEntry> active = new ArrayList<>();
        for (ParameterEntry entry : model.entries()) {
            if (entry.definition().kind() == ValueKind.VARIABLE_MOD_TUPLE
                    && !modification(entry).isUnused()) {
                active.add(entry);
            }
        }
        boolean requireOn =
                model.entry(REQUIRE).map(e -> ((ParameterValue.Flag) e.value()).on()).orElse(false);
        if (requireOn && active.isEmpty()) {
            findings.add(
                    Rule.VARMODS_REQUIRED_WITHOUT_SLOT,
                    REQUIRE,
                    REQUIRE
                            + " = 1 requires every peptide to carry a variable modification, but"
                            + " no variable_mod slot is active (every mass difference is 0), so"
                            + " nothing can be identified; configure a modification or set it to"
                            + " 0");
        }
        Optional<Integer> limit =
                model.entry(LIMIT).map(e -> ((ParameterValue.Whole) e.value()).value());
        // A negative limit is the bounds rule's error, and Comet ignores it and keeps its default
        // (CometSearchManager.cpp lines 534-538), so it caps nothing here.
        limit.filter(cap -> cap >= 0)
                .ifPresent(cap -> checkCap(model, cap, active, requireOn, findings));
    }

    private static void checkCap(
            CometParameters model,
            int cap,
            List<ParameterEntry> active,
            boolean requireOn,
            Findings findings) {
        for (ParameterEntry slot : active) {
            OptionalInt minimum = modification(slot).minimumCount();
            if (minimum.isPresent() && minimum.getAsInt() > cap) {
                findings.add(
                        Rule.VARMODS_MINIMUM_ABOVE_LIMIT,
                        List.of(slot.name(), LIMIT),
                        slot.name()
                                + " = "
                                + model.text(slot.name())
                                + " needs at least "
                                + minimum.getAsInt()
                                + " modified residues, but "
                                + LIMIT
                                + " = "
                                + cap
                                + " allows at most "
                                + cap
                                + " in a peptide, so the slot can never apply; lower its"
                                + " minimum or raise the limit");
            }
        }
        if (cap != 0 || active.isEmpty()) {
            return;
        }
        List<String> required = new ArrayList<>();
        required.add(LIMIT);
        if (requireOn) {
            required.add(REQUIRE);
        }
        for (ParameterEntry slot : active) {
            if (modification(slot).requirementCode() > 0) {
                required.add(slot.name());
            }
        }
        if (required.size() > 1) {
            findings.add(
                    Rule.VARMODS_REQUIRED_BUT_NONE_ALLOWED,
                    required,
                    LIMIT
                            + " = 0 allows no variable modification in a peptide, but one is"
                            + " required by "
                            + String.join(" and ", required.subList(1, required.size()))
                            + ", so nothing can be identified; raise the limit");
            return;
        }
        List<String> slots = new ArrayList<>();
        slots.add(LIMIT);
        active.forEach(slot -> slots.add(slot.name()));
        findings.add(
                Rule.VARMODS_NONE_ALLOWED,
                slots,
                LIMIT
                        + " = 0 allows no variable modification in a peptide, so the active"
                        + " slots "
                        + slots.subList(1, slots.size())
                        + " have no effect; raise the limit, or switch the slots off");
    }

    private static VariableModification modification(ParameterEntry entry) {
        return ((ParameterValue.Tuple) entry.value()).modification();
    }
}
