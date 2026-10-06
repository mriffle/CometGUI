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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.domain.params.CometIndexDescription;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.schema.IndexFormats;
import org.cometgui.params.comet.schema.ResidueAlphabet;
import org.cometgui.params.comet.schema.TerminalCode;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.VariableModification;

/**
 * The specification's pre-run check "selected index and search options are compatible" (assigned to
 * Phase 08 by tier 1 on 2026-10-06), judged on the self-description of the existing {@code .idx}
 * file Comet would search.
 *
 * <p><strong>Format.</strong> {@link Rule#INDEX_FORMAT_UNREADABLE}: an index whose format the
 * selected release cannot read. Which formats a release reads is data in its version record ({@link
 * IndexFormats}, decision C-2): Comet 2026.03.0 refuses format 4 with {@code is not a v5 unified
 * index file}, Comet 2026.02.2 format 5 with {@code is not a v4 unified index file}. Nothing else
 * is judged for an index the release cannot read.
 *
 * <p><strong>Contradictions.</strong> {@link Rule#INDEX_CONTRADICTS_SEARCH}, one finding per
 * option. When Comet searches an existing index it takes the options the header records and
 * silently ignores what the parameter file says about them; both releases were run on indexes built
 * with one setting and searched with another, and every option below changed nothing in the
 * search's results, with no warning (Comet 2026.03.0 alone warns about {@code index_search_type}).
 * {@code docs/developer/comet_parameter_schema.rst} records each run. The options:
 *
 * <ul>
 *   <li>{@code index_search_type}, where it asks for a type (0 peptide, 1 fragment ion), against
 *       the index's type;
 *   <li>{@code decoy_search}, and {@code decoy_prefix} where the index records it;
 *   <li>the enzymes, {@code search_enzyme_number} and {@code search_enzyme2_number}, compared as
 *       the rows they select (name, sense, cut and no-cut residues);
 *   <li>{@code num_enzyme_termini}, {@code allowed_missed_cleavage} and {@code
 *       clip_nterm_methionine}, where the index records them;
 *   <li>{@code mass_type_parent} and {@code mass_type_fragment};
 *   <li>{@code digest_mass_range} and {@code peptide_length_range} reaching outside the index's
 *       ranges (a narrower range is applied by Comet and is not a contradiction);
 *   <li>every static modification;
 *   <li>the variable modifications, slot by slot, as Comet holds them: after it rewrites {@code n}
 *       ({@code c}) at distance 0 from the protein terminus to the protein-terminus code where the
 *       release has one, merges identical slots, and caps each slot's count at {@code
 *       max_variable_mods_in_peptide} and, for a fragment-ion index, at {@value
 *       #FRAGMENT_ION_COUNT_CAP}; and any active slot beyond the slots the index holds;
 *   <li>{@code require_variable_mod} and {@code max_variable_mods_in_peptide}.
 * </ul>
 *
 * <p><strong>Unrecorded.</strong> {@link Rule#INDEX_OPTION_UNRECORDED}, a warning: format 4 does
 * not record the enzyme termini, the missed cleavages or methionine clipping, although its peptides
 * were digested with them and Comet 2026.02.2 searches those peptides whatever the search says.
 *
 * <p>{@code ProteinModList:} is read but not compared: both releases wrote {@code 0} for an index
 * built with an existing {@code protein_modslist_file}, and searches with and without the file gave
 * identical results.
 */
final class IndexCompatibilityRule {

    /** The database parameter, to which every finding is attached as well. */
    static final String DATABASE = "database_name";

    /**
     * Comet's cap on a slot's count when it builds a fragment-ion index ({@code
     * FRAGINDEX_MAX_MODS_PER_MOD}, {@code CometSearchManager.cpp} lines 1409-1413 at {@code
     * v2026.03.0}; the same at {@code v2026.02.2}).
     */
    static final int FRAGMENT_ION_COUNT_CAP = 5;

    /** Half a unit in the sixth decimal place: the precision of the header's {@code %lf}. */
    static final BigDecimal PRINTED_PRECISION = new BigDecimal("0.0000005");

    private static final String SLOT_PREFIX = "variable_mod";

    private final CometParameters model;

    private final CometIndexDescription index;

    private final Findings findings;

    private IndexCompatibilityRule(
            CometParameters model, CometIndexDescription index, Findings findings) {
        this.model = model;
        this.index = index;
        this.findings = findings;
    }

    static void check(CometParameters model, CometIndexDescription index, Findings findings) {
        IndexFormats formats =
                model.metadata().version(model.version()).orElseThrow().indexFormats();
        if (!formats.reads(index.formatVersion())) {
            findings.add(
                    Rule.INDEX_FORMAT_UNREADABLE,
                    List.of(DATABASE),
                    "the index "
                            + index.file()
                            + " is in format v"
                            + index.formatVersion()
                            + " (written by Comet "
                            + index.cometVersion()
                            + "), and Comet "
                            + model.version().text()
                            + " reads "
                            + formats.describe()
                            + ", so it would stop before searching; rebuild the index from its"
                            + " FASTA with Comet "
                            + model.version().text());
            return;
        }
        IndexCompatibilityRule rule = new IndexCompatibilityRule(model, index, findings);
        rule.indexType();
        rule.decoys();
        rule.enzymes();
        rule.digestion();
        rule.massTypes();
        rule.ranges();
        rule.staticMods();
        rule.variableMods();
        rule.unrecorded();
    }

    /**
     * Records one contradiction.
     *
     * @param parameter the parameter the index overrides
     * @param recorded what the index records, quoted from or rendered as its header
     * @param consequence what Comet would do with the search's value
     * @param fix the setting that would agree with the index
     */
    private void contradiction(String parameter, String recorded, String consequence, String fix) {
        findings.add(
                Rule.INDEX_CONTRADICTS_SEARCH,
                List.of(parameter, DATABASE),
                "the index "
                        + index.file()
                        + " records "
                        + recorded
                        + ", and Comet searches an existing index with what it records, so "
                        + parameter
                        + " = "
                        + model.text(parameter)
                        + " would be "
                        + consequence
                        + "; "
                        + fix
                        + ", or rebuild the index from its FASTA with this search's settings");
    }

    private void contradiction(String parameter, String recorded, String fix) {
        contradiction(parameter, recorded, "silently ignored", fix);
    }

    private int whole(String parameter) {
        return ((ParameterValue.Whole) model.value(parameter)).value();
    }

    private boolean flag(String parameter) {
        return ((ParameterValue.Flag) model.value(parameter)).on();
    }

    private static String quoted(String key, Object value) {
        return "\"" + key + ": " + value + "\"";
    }

    /**
     * {@code index_search_type} 0 or 1 against the index's type; -1 asks for none, and a release
     * without the parameter asks nothing.
     */
    private void indexType() {
        String parameter = "index_search_type";
        int recorded = index.type() == IndexMode.PEPTIDE ? 0 : 1;
        model.entry(parameter)
                .map(entry -> ((ParameterValue.Whole) entry.value()).value())
                .filter(asked -> (asked == 0 || asked == 1) && asked != recorded)
                .ifPresent(
                        asked ->
                                contradiction(
                                        parameter,
                                        quoted(
                                                "IndexSearchType",
                                                index.type() == IndexMode.PEPTIDE
                                                        ? "peptide index"
                                                        : "fragment ion index"),
                                        "set " + parameter + " to " + recorded));
    }

    private void decoys() {
        String search = "decoy_search";
        if (whole(search) != index.decoySearch()) {
            contradiction(
                    search,
                    quoted("DecoySearch", index.decoySearch()),
                    "set " + search + " to " + index.decoySearch());
        }
        String prefix = DecoyRule.PREFIX;
        String asked = ((ParameterValue.Text) model.value(prefix)).text();
        index.decoyPrefix()
                .filter(recorded -> !recorded.equals(asked))
                .ifPresent(
                        recorded ->
                                contradiction(
                                        prefix,
                                        quoted("DecoyPrefix", recorded),
                                        "set " + prefix + " to " + recorded));
    }

    private void enzymes() {
        enzyme("search_enzyme_number", "Enzyme", index.enzyme());
        enzyme("search_enzyme2_number", "Enzyme2", index.secondEnzyme());
    }

    private void enzyme(String parameter, String key, CometIndexDescription.Enzyme recorded) {
        Optional<EnzymeDefinition> row = model.enzymeTable().byNumber(whole(parameter));
        if (row.isEmpty()) {
            // A number naming no row is enzyme_in_table.missing's error.
            return;
        }
        EnzymeDefinition selected = row.get();
        CometIndexDescription.Enzyme asked =
                new CometIndexDescription.Enzyme(
                        selected.name(),
                        selected.sense().code(),
                        written(selected.cutResidues()),
                        written(selected.noCutResidues()));
        if (!asked.equals(recorded)) {
            contradiction(
                    parameter,
                    quoted(key, recorded.text()),
                    "silently ignored (it selects " + asked.text() + ")",
                    "select the enzyme " + recorded.text());
        }
    }

    /** A residue set as Comet writes it: {@code -} for none. */
    private static String written(String residues) {
        return residues.isEmpty() ? EnzymeDefinition.NONE : residues;
    }

    private void digestion() {
        recordedWhole("num_enzyme_termini", "NumEnzymeTermini", index.enzymeTermini());
        recordedWhole("allowed_missed_cleavage", "AllowedMissedCleavage", index.missedCleavages());
        String clip = "clip_nterm_methionine";
        index.clipNtermMethionine()
                .filter(recorded -> recorded != flag(clip))
                .ifPresent(
                        recorded ->
                                contradiction(
                                        clip,
                                        quoted("ClipNtermMethionine", recorded ? 1 : 0),
                                        "set " + clip + " to " + (recorded ? 1 : 0)));
    }

    private void recordedWhole(String parameter, String key, OptionalInt recorded) {
        if (recorded.isPresent() && recorded.getAsInt() != whole(parameter)) {
            contradiction(
                    parameter,
                    quoted(key, recorded.getAsInt()),
                    "set " + parameter + " to " + recorded.getAsInt());
        }
    }

    private void massTypes() {
        String recorded =
                quoted("MassType", index.parentMassType() + " " + index.fragmentMassType());
        if (whole("mass_type_parent") != index.parentMassType()) {
            contradiction(
                    "mass_type_parent",
                    recorded,
                    "set mass_type_parent to " + index.parentMassType());
        }
        if (whole("mass_type_fragment") != index.fragmentMassType()) {
            contradiction(
                    "mass_type_fragment",
                    recorded,
                    "set mass_type_fragment to " + index.fragmentMassType());
        }
    }

    private void ranges() {
        String masses = "digest_mass_range";
        CometIndexDescription.MassRange held = index.massRange();
        ParameterValue.DecimalPair mass = (ParameterValue.DecimalPair) model.value(masses);
        if (mass.range().first().compareTo(held.low()) < 0
                || mass.range().second().compareTo(held.high()) > 0) {
            String bounds = held.low().toPlainString() + " " + held.high().toPlainString();
            contradiction(
                    masses,
                    quoted("MassRange", bounds) + " and holds no peptide outside it",
                    "silently narrowed to it",
                    "keep " + masses + " within " + bounds);
        }
        String lengths = "peptide_length_range";
        CometIndexDescription.LengthRange span = index.lengthRange();
        ParameterValue.WholeRange length = (ParameterValue.WholeRange) model.value(lengths);
        if (length.range().first() < span.shortest() || length.range().second() > span.longest()) {
            String bounds = span.shortest() + " " + span.longest();
            contradiction(
                    lengths,
                    quoted("LengthRange", bounds) + " and holds no peptide outside it",
                    "silently narrowed to it",
                    "keep " + lengths + " within " + bounds);
        }
    }

    private static boolean same(BigDecimal asked, BigDecimal recorded) {
        return asked.subtract(recorded).abs().compareTo(PRINTED_PRECISION) <= 0;
    }

    /** The parameter holding one static modification of the header, by its position. */
    private Optional<String> staticParameter(int position) {
        if (position < CometIndexDescription.STATIC_RESIDUES.length()) {
            String prefix = "add_" + CometIndexDescription.STATIC_RESIDUES.charAt(position) + "_";
            return model.entries().stream()
                    .map(ParameterEntry::name)
                    .filter(name -> name.startsWith(prefix))
                    .findFirst();
        }
        String name =
                List.of(
                                "add_Nterm_peptide",
                                "add_Cterm_peptide",
                                "add_Nterm_protein",
                                "add_Cterm_protein")
                        .get(position - CometIndexDescription.STATIC_RESIDUES.length());
        return model.entry(name).map(ParameterEntry::name);
    }

    private void staticMods() {
        List<BigDecimal> recorded = index.staticMods();
        for (int position = 0; position < recorded.size(); position++) {
            Optional<String> parameter = staticParameter(position);
            if (parameter.isEmpty()) {
                continue;
            }
            BigDecimal asked = ((ParameterValue.Decimal) model.value(parameter.get())).value();
            BigDecimal held = recorded.get(position);
            if (!same(asked, held)) {
                contradiction(
                        parameter.get(),
                        "the static modification "
                                + held.toPlainString()
                                + " on "
                                + CometIndexDescription.staticSite(position)
                                + " (\"StaticMod:\")",
                        "set " + parameter.get() + " to " + held.toPlainString());
            }
        }
    }

    private static String slotName(int number) {
        return String.format(Locale.ROOT, "%s%02d", SLOT_PREFIX, number);
    }

    private static String render(int number, CometIndexDescription.VariableMod slot) {
        StringBuilder text =
                new StringBuilder("slot ")
                        .append(number)
                        .append(" of \"VariableMod:\", ")
                        .append(slot.residues())
                        .append(':')
                        .append(slot.mass().toPlainString())
                        .append(':')
                        .append(slot.neutralLoss().toPlainString())
                        .append(':')
                        .append(slot.secondNeutralLoss().toPlainString())
                        .append(':')
                        .append(slot.maximumCount());
        if (slot.terminalDistance().isPresent()) {
            text.append(':')
                    .append(slot.terminalDistance().getAsInt())
                    .append(':')
                    .append(slot.terminus().getAsInt());
        }
        return text.append(", requirement ").append(slot.requirement()).toString();
    }

    private void variableMods() {
        List<CometIndexDescription.VariableMod> held = index.variableMods();
        List<AScoreProRule.Slot> survivors = AScoreProRule.activeAfterMerge(model);
        ResidueAlphabet alphabet = VariableModRules.alphabet(model);
        int cap = AScoreProRule.cap(model);
        for (int number = 1; number <= held.size(); number++) {
            CometIndexDescription.VariableMod recorded = held.get(number - 1);
            int wanted = number;
            Optional<AScoreProRule.Slot> asked =
                    survivors.stream().filter(slot -> slot.number() == wanted).findFirst();
            boolean agrees =
                    asked.isPresent()
                            ? recorded.isActive() && agrees(asked.get(), recorded, alphabet, cap)
                            : !recorded.isActive();
            if (!agrees) {
                contradiction(
                        slotName(number),
                        render(number, recorded),
                        "set " + slotName(number) + " to what the index records");
            }
        }
        for (AScoreProRule.Slot slot : survivors) {
            if (slot.number() > held.size()) {
                contradiction(
                        slot.name(),
                        "only " + held.size() + " variable-modification slots (\"VariableMod:\")",
                        "switch "
                                + slot.name()
                                + " off, or move the modification into a slot from "
                                + slotName(1)
                                + " to "
                                + slotName(held.size()));
            }
        }
        String require = "require_variable_mod";
        boolean recordedRequire = (index.requireVariableMod() & 1) == 1;
        if (recordedRequire != flag(require)) {
            contradiction(
                    require,
                    quoted("RequireVariableMod", index.requireVariableMod() + " ...")
                            + " (its lowest bit is "
                            + require
                            + ")",
                    "set " + require + " to " + (recordedRequire ? 1 : 0));
        }
        String limit = VariableModRules.LIMIT;
        if (cap != index.maxVariableModsInPeptide()) {
            contradiction(
                    limit,
                    quoted("MaxVariableModsInPeptide", index.maxVariableModsInPeptide()),
                    "set " + limit + " to " + index.maxVariableModsInPeptide());
        }
    }

    /** Whether an active slot of the search, with what Comet merges into it, is the index's. */
    private boolean agrees(
            AScoreProRule.Slot slot,
            CometIndexDescription.VariableMod recorded,
            ResidueAlphabet alphabet,
            int cap) {
        VariableModification mod = slot.modification();
        Set<Character> residues = new TreeSet<>();
        for (VariableModification merged : slot.merged()) {
            for (char c : indexedResidues(merged, alphabet).toCharArray()) {
                residues.add(c);
            }
        }
        Set<Character> held = new TreeSet<>();
        for (char c : recorded.residues().toCharArray()) {
            held.add(c);
        }
        List<BigDecimal> losses = mod.neutralLosses();
        BigDecimal second = losses.size() > 1 ? losses.get(1) : BigDecimal.ZERO;
        int count = Math.min(mod.maximumCount(), cap);
        if (index.type() == IndexMode.FRAGMENT_ION) {
            count = Math.min(count, FRAGMENT_ION_COUNT_CAP);
        }
        boolean rewritten = AScoreProRule.rewrittenToProteinTerminus(mod, alphabet);
        int distance = rewritten ? -1 : mod.terminalDistance();
        int terminus = rewritten ? 0 : mod.terminusCode();
        return residues.equals(held)
                && same(mod.mass(), recorded.mass())
                && same(losses.get(0), recorded.neutralLoss())
                && same(second, recorded.secondNeutralLoss())
                && count == recorded.maximumCount()
                && mod.requirementCode() == recorded.requirement()
                && (recorded.terminalDistance().isEmpty()
                        || (distance == recorded.terminalDistance().getAsInt()
                                && terminus == recorded.terminus().getAsInt()));
    }

    /**
     * A slot's residue token as Comet holds it: {@code n} ({@code c}) at distance 0 from the
     * protein N- (C-) terminus becomes the protein-terminus code, in a release whose alphabet has
     * it ({@code CometSearchManager.cpp} lines 1452-1471 at {@code v2026.03.0}).
     */
    static String indexedResidues(VariableModification mod, ResidueAlphabet alphabet) {
        if (mod.terminalDistance() != 0 || mod.terminusCode() < 0 || mod.terminusCode() > 1) {
            return mod.residues();
        }
        boolean nTerminus = mod.terminusCode() == 0;
        char from = (nTerminus ? TerminalCode.PEPTIDE_N : TerminalCode.PEPTIDE_C).code();
        char to = (nTerminus ? TerminalCode.PROTEIN_N : TerminalCode.PROTEIN_C).code();
        if (!alphabet.accepts(to)) {
            return mod.residues();
        }
        return mod.residues().replace(from, to);
    }

    /**
     * The digestion options the index does not record although its peptides were built with them.
     */
    private void unrecorded() {
        List<String> missing = new ArrayList<>();
        if (index.enzymeTermini().isEmpty()) {
            missing.add("num_enzyme_termini");
        }
        if (index.missedCleavages().isEmpty()) {
            missing.add("allowed_missed_cleavage");
        }
        if (index.clipNtermMethionine().isEmpty()) {
            missing.add("clip_nterm_methionine");
        }
        if (missing.isEmpty()) {
            return;
        }
        List<String> settings = new ArrayList<>();
        for (String parameter : missing) {
            settings.add(parameter + " = " + model.text(parameter));
        }
        List<String> parameters = new ArrayList<>(missing);
        parameters.add(DATABASE);
        findings.add(
                Rule.INDEX_OPTION_UNRECORDED,
                parameters,
                "the index "
                        + index.file()
                        + " (format v"
                        + index.formatVersion()
                        + ", written by Comet "
                        + index.cometVersion()
                        + ") does not record "
                        + String.join(", ", missing)
                        + ", although its peptides were digested with them and Comet searches the"
                        + " peptides it holds; CometGUI cannot check them against this search's "
                        + String.join(", ", settings)
                        + ", so make sure the index was built with the same, or rebuild it from"
                        + " its FASTA");
    }
}
